// Real API against a dedicated audit server/database. Never point this at production.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { createHash, randomUUID } from 'node:crypto';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_PACKAGE_PATH);
const api = process.env.CONTRATO_AUDIT_API || 'http://localhost:18082';
const front = process.env.FRONTEND_TEST_URL || 'http://localhost:5180';
assert.equal(new URL(api).hostname, 'localhost');
assert.equal(new URL(api).port, '18082', 'Use only the dedicated audit server');
let perfil;
async function request(method, path, body) {
  const response = await fetch(api + path, { method, headers: {
    'Content-Type': 'application/json', ...(perfil ? { 'X-Perfil-Id': String(perfil) } : {}),
  }, body: body === undefined ? undefined : JSON.stringify(body) });
  assert.ok(response.ok, `${method} ${path}: ${response.status} ${response.ok ? '' : await response.text()}`);
  const text = await response.text();
  return text ? JSON.parse(text) : undefined;
}
const pessoa = (nome, cpf, rua) => ({ nome, nomeIndicador: null, nacionalidade: 'brasileira',
  estadoCivil: 'UNIAO_ESTAVEL', profissao: 'Professor', rg: '00123-X', rgOrgaoExpedidor: 'SSP/SP',
  cpf, enderecoRua: rua, enderecoNumero: 15, enderecoComplemento: null, enderecoBairro: 'Centro',
  enderecoCep: '00100-000', enderecoCidade: 'Cidade', enderecoEstado: 'SP' });
perfil = (await request('POST', '/perfis', { nome: `Auditoria ${randomUUID()}` })).id;
await request('PUT', `/perfis/${perfil}`, { ...pessoa(`Carteira de auditoria ${perfil}`, '00000000001', 'Rua Perfil'),
  nomeCompleto: 'Pessoa diferente de Edson' });
const d = pessoa('Devedor fictício auditoria', '00000000002', 'Rua A');
const f = pessoa('Fiador fictício auditoria', '00000000003', 'Rua Fiador A');
await request('POST', '/clientes', d);
await request('POST', '/clientes', f);
const clientes = await request('GET', '/clientes');
const devedor = clientes.find(c => c.nome === d.nome).id;
const fiador = clientes.find(c => c.nome === f.nome).id;
const loans = `/clientes/${devedor}/emprestimos`;
await request('POST', loans, { valorEmprestado: 3751.50, quantidadeParcelas: 3, jurosCobrado: 0,
  formaPagamento: 'PIX', frequenciaPagamento: 'MENSAL', tipoContrato: 'FISICO', fiadorId: fiador });
const loan = (await request('GET', loans + '/detalhes'))[0].id;
const contratos = `${loans}/${loan}/contratos`;
const browser = await chromium.launch({ headless: true, channel: 'msedge' });
const context = await browser.newContext({ acceptDownloads: true });
const page = await context.newPage();
const pageErrors = [], calls = [];
page.on('pageerror', e => pageErrors.push(e.message));
await context.addInitScript(id => localStorage.setItem('perfil_ativo_id', String(id)), perfil);
// Transparent proxy only: every business response comes from the real Java API.
await context.route(/\/api\/|localhost:8080\//, async route => {
  const req = route.request(), url = new URL(req.url());
  const path = url.pathname.replace(/^\/api/, '');
  calls.push({ path, method: req.method(), perfil: req.headers()['x-perfil-id'] });
  const response = await route.fetch({ url: api + path + url.search });
  await route.fulfill({ response });
});
const dialog = page.getByRole('dialog', { name: 'Dados do contrato', exact: true });
const section = dialog.getByRole('region', { name: 'Contratos gerados' });
async function open() {
  await page.getByRole('button', { name: 'Dados do contrato', exact: true }).first().click();
  await dialog.waitFor();
}
async function close() {
  await dialog.getByRole('button', { name: 'Fechar', exact: true }).click();
  await dialog.waitFor({ state: 'hidden' });
}
async function generate(accept = true) {
  page.once('dialog', async confirm => {
    assert.equal(confirm.type(), 'confirm');
    assert.equal(confirm.message(), 'Gerar um novo contrato com os dados exibidos atualmente?');
    await (accept ? confirm.accept() : confirm.dismiss());
  });
  await section.getByRole('button', { name: 'Gerar contrato', exact: true }).click();
}
async function download(id) {
  const row = section.getByRole('listitem').filter({ hasText: `Contrato #${id}` });
  const pending = page.waitForEvent('download');
  await row.getByRole('button', { name: 'Baixar Word', exact: true }).click();
  const result = await pending;
  assert.equal(await result.failure(), null);
  const bytes = await readFile(await result.path());
  const meta = (await request('GET', contratos)).find(c => c.id === id);
  assert.equal(result.suggestedFilename(), meta.nomeArquivo);
  assert.ok(bytes.length > 0);
  assert.equal(createHash('sha256').update(bytes).digest('hex'), meta.hashDocumento);
  return bytes;
}
async function downloadPdf(id) {
  const row = section.getByRole('listitem').filter({ hasText: `Contrato #${id}` });
  const pending = page.waitForEvent('download');
  await row.getByRole('button', { name: 'Baixar PDF', exact: true }).click();
  const result = await pending;
  assert.equal(await result.failure(), null);
  assert.equal(result.suggestedFilename(), `contrato-emprestimo-${loan}-${id}.pdf`);
  const bytes = await readFile(await result.path());
  assert.ok(bytes.length > 0);
  assert.equal(bytes.subarray(0,5).toString(), '%PDF-');
  return bytes;
}
try {
  await page.goto(`${front}/emprestimo?selectedCliente=${devedor}&sortOrder=asc`);
  await open();
  await dialog.getByRole('heading', { name: 'Credor', exact: true }).waitFor();
  await section.getByText('Nenhum contrato gerado.', { exact: true }).waitFor();
  await generate();
  await section.getByRole('status').filter({ hasText: 'gerado. O documento foi salvo' }).waitFor();
  const one = (await request('GET', contratos))[0];
  const bytesOne = await download(one.id);
  assert.equal(one.pdfDisponivel, true);
  const pdfOne = await downloadPdf(one.id);
  await close();
  d.enderecoRua = 'Rua B'; f.enderecoRua = 'Rua Fiador B';
  await request('PUT', `/clientes/${devedor}`, d);
  await request('PUT', `/clientes/${fiador}`, f);
  await open();
  await dialog.getByText(/Rua B/).first().waitFor();
  assert.deepEqual(await download(one.id), bytesOne);
  assert.deepEqual(await downloadPdf(one.id), pdfOne);
  await generate(false);
  assert.equal((await request('GET', contratos)).length, 1);
  await generate();
  await section.getByRole('status').filter({ hasText: 'gerado. O documento foi salvo' }).waitFor();
  const history = await request('GET', contratos);
  assert.equal(history.length, 2);
  assert.equal(history[1].id, one.id);
  assert.notDeepEqual(await download(history[0].id), bytesOne);
  assert.notDeepEqual(await downloadPdf(history[0].id), pdfOne);
  assert.deepEqual(await download(one.id), bytesOne);
  assert.deepEqual(await downloadPdf(one.id), pdfOne);
  await close();
  await request('PUT', `/clientes/${devedor}`, { ...d, cpf: null });
  await open();
  await dialog.getByRole('alert').filter({ hasText: 'Dados do devedor incompletos: cpf.' }).waitFor();
  assert.equal(await section.getByRole('button', { name: 'Gerar contrato', exact: true }).count(), 0);
  assert.deepEqual(await download(one.id), bytesOne);
  assert.deepEqual(await downloadPdf(one.id), pdfOne);
  assert.deepEqual(pageErrors, []);
  const posts = calls.filter(c => c.method === 'POST');
  assert.equal(posts.length, 2);
  assert.ok(posts.every(c => c.path === contratos && c.perfil === String(perfil)));
  await mkdir('target/contrato-qa', { recursive: true });
  await mkdir('target/artifacts', { recursive: true });
  await writeFile('target/artifacts/contrato-teste-gerado.docx', bytesOne);
  await page.screenshot({ path: 'target/contrato-qa/historico-browser.png', fullPage: true });
  await writeFile('target/contrato-qa/browser-audit.json', JSON.stringify({ passed: true,
    contracts: history.map(c => c.id), bytes: bytesOne.length, confirmations: 3,
    canceledGeneration: true, historicalBlobUnchanged: true, historicalPdfBlobUnchanged: true,
    pdfBytes: pdfOne.length, pdfSha256: createHash('sha256').update(pdfOne).digest('hex'), incompleteDataError: true,
    pageErrors, calls }, null, 2));
  console.log('PASS: real POST/history/Blob, confirmation+cancel, immutable historic bytes, SHA-256, incomplete data error, X-Perfil-Id.');
} catch (error) {
  console.error(JSON.stringify({ calls, pageErrors, text: await page.locator('body').innerText() }, null, 2));
  throw error;
} finally { await context.close(); await browser.close(); }

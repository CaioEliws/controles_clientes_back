import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_PACKAGE_PATH);
const api = 'http://localhost:18082', front = 'http://localhost:5173', out = 'target/rg-qa';
let perfil;
async function req(method, path, body) {
  const response = await fetch(api + path, { method, headers: { 'Content-Type': 'application/json', ...(perfil ? { 'X-Perfil-Id': String(perfil) } : {}) }, body: body === undefined ? undefined : JSON.stringify(body) });
  assert.ok(response.ok, `${method} ${path}: ${response.status} ${response.ok ? '' : await response.text()}`);
  const text = await response.text(); return text ? JSON.parse(text) : undefined;
}
await mkdir(out, { recursive: true });
perfil = (await req('POST', '/perfis', { nome: `Auditoria isolada RG ${Date.now()}` })).id;
const nome = 'IRENE TESTE RG';
await req('POST', '/clientes', { nome, nomeIndicador: 'Teste', enderecoRua: 'Rua A', enderecoBairro: 'Centro', enderecoNumero: 1, rg: '52779723', cpf: '00000000002', sexoDocumento: 'FEMININO', nacionalidade: 'BRASIL', estadoCivil: 'SOLTEIRO', profissao: 'Vendedora', rgOrgaoExpedidor: 'SSP/SP', enderecoEstado: 'SP' });
const cliente = (await req('GET', '/clientes'))[0];
const browser = await chromium.launch({ headless: true, channel: 'msedge' });
const context = await browser.newContext({ viewport: { width: 1280, height: 960 } });
const page = await context.newPage(), errors = [], checks = [], payloads = [];
page.on('pageerror', (e) => errors.push(e.message));
await context.addInitScript((id) => localStorage.setItem('perfil_ativo_id', String(id)), perfil);
await context.route(/\/api\/|localhost:8080\//, async (route) => {
  const request = route.request(), url = new URL(request.url()), path = url.pathname.replace(/^\/api/, '');
  if (request.method() === 'PUT') payloads.push(request.postDataJSON());
  await route.fulfill({ response: await route.fetch({ url: api + path + url.search }) });
});
async function edit() {
  await page.goto(front + '/clientes');
  await page.getByRole('row').filter({ hasText: nome }).getByRole('button', { name: 'Editar', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Editar Cliente', exact: true });
  await dialog.getByLabel('Sexo para o documento', { exact: true }).locator('option[value="FEMININO"]').waitFor({ state: 'attached' });
  return dialog;
}
const order = ['Nome completo', 'Sexo para o documento', 'Estado civil', 'Nacionalidade', 'Profissão', 'Quem indicou?', 'CPF', 'RG', 'Órgão expedidor / UF emissora', 'CEP', 'Estado / UF', 'Cidade', 'Bairro', 'Rua', 'Número', 'Complemento'];
try {
  let dialog = await edit();
  assert.deepEqual(await dialog.locator('label').allTextContents(), order);
  await dialog.screenshot({ path: `${out}/modal-desktop.png` });
  for (const [entrada, esperado] of [['409614713','40.961.471-3'], ['52779723x','52.779.723-X'], ['52779723','52779723'], ['52.779.723','52.779.723'], ['1234567890123','1234567890123']]) {
    await dialog.getByLabel('RG', { exact: true }).fill(entrada);
    await dialog.getByLabel('CPF', { exact: true }).click();
    assert.equal(await dialog.getByLabel('RG', { exact: true }).inputValue(), esperado);
    await dialog.getByRole('button', { name: 'Salvar alterações', exact: true }).click();
    await dialog.waitFor({ state: 'hidden' });
    assert.equal((await req('GET', `/clientes/${cliente.id}`)).rg, esperado);
    assert.equal(payloads.at(-1).rg, esperado);
    dialog = await edit();
    assert.equal(await dialog.getByLabel('RG', { exact: true }).inputValue(), esperado);
    checks.push(`Salvar/GET/reabrir: ${entrada} -> ${esperado}`);
  }
  const desktop = await dialog.getByLabel('Nome completo', { exact: true }).boundingBox();
  const sexo = await dialog.getByLabel('Sexo para o documento', { exact: true }).boundingBox();
  assert.ok(Math.abs(desktop.y - sexo.y) < 2 && sexo.x > desktop.x + desktop.width);
  await dialog.getByLabel('RG', { exact: true }).fill('1234X5678');
  await dialog.getByLabel('CPF', { exact: true }).click();
  await dialog.getByText('RG deve conter de 7 a 13 caracteres significativos: dígitos e, opcionalmente, X no final.').waitFor();
  const antes = payloads.length;
  await dialog.getByRole('button', { name: 'Salvar alterações', exact: true }).click();
  assert.equal(payloads.length, antes);
  await dialog.getByLabel('RG', { exact: true }).fill('52.779.723');
  await dialog.getByLabel('CPF', { exact: true }).click();
  checks.push('RG inválido mostra erro junto ao campo e bloqueia envio; valor corrigido é aceito');
  await page.setViewportSize({ width: 390, height: 740 });
  await page.waitForFunction(() => {
    const el = document.querySelector('[role="dialog"]');
    return el && el.getBoundingClientRect().height <= 709 && el.getBoundingClientRect().top >= 15;
  });
  const field = await dialog.getByLabel('Nome completo', { exact: true }).boundingBox();
  const mobileSexo = await dialog.getByLabel('Sexo para o documento', { exact: true }).boundingBox();
  assert.ok(mobileSexo.y > field.y && Math.abs(mobileSexo.x - field.x) < 2);
  const save = await dialog.getByRole('button', { name: 'Salvar alterações', exact: true }).boundingBox();
  const title = await dialog.getByRole('heading', { name: 'Editar Cliente', exact: true }).boundingBox();
  assert.ok(save.y >= 0 && save.y + save.height <= 740 && title.y >= 0);
  assert.equal(await dialog.evaluate((el) => el.scrollWidth <= el.clientWidth), true);
  await dialog.screenshot({ path: `${out}/modal-mobile.png` });
  await dialog.getByLabel('Complemento', { exact: true }).scrollIntoViewIfNeeded();
  const saveAfter = await dialog.getByRole('button', { name: 'Salvar alterações', exact: true }).boundingBox();
  assert.equal(saveAfter.y, save.y);
  checks.push('Ordem solicitada; desktop duas colunas; mobile uma coluna; sem overflow horizontal; título e ações visíveis durante scroll interno');
  assert.deepEqual(errors, []);
  await writeFile(`${out}/navegador.json`, JSON.stringify({ status: 'PASS', checks, payloads, errors }, null, 2));
  console.log(JSON.stringify({ status: 'PASS', checks }, null, 2));
} catch (error) {
  await page.screenshot({ path: `${out}/falha.png`, fullPage: true }).catch(() => {});
  throw error;
} finally { await browser.close(); }

import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
const { chromium } = createRequire(import.meta.url)('C:/Users/caioe/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const pasta = '.auditoria/importacao-clientes-csv';
await mkdir(pasta, { recursive: true });
const browser = await chromium.launch({ headless: true, channel: 'msedge' });
const context = await browser.newContext({ viewport: { width: 1280, height: 1000 }, acceptDownloads: true });
await context.addInitScript(() => localStorage.setItem('perfil_ativo_id', '1'));
const chamadas = [];
const resumo = { totalLinhas: 3, criados: 1, existentes: 1, atualizados: 0, ignorados: 1, conflitos: 1, erros: 0, detalhesTruncados: false,
  detalhes: [{ linha: 2, nome: 'João', cpf: '***.***.000-**', status: 'CRIADO', motivo: 'Cliente novo.', camposConflitantes: [], camposPreenchidos: ['nome'] },
    { linha: 3, nome: 'Maria', cpf: '***.***.000-**', status: 'CONFLITO', motivo: 'Valores atuais preservados nos campos: enderecoRua.', camposConflitantes: ['enderecoRua'], camposPreenchidos: ['profissao'] },
    { linha: 4, nome: 'Outra', cpf: null, status: 'IGNORADO', motivo: 'CPF ausente; revisão manual necessária.', camposConflitantes: [], camposPreenchidos: [] }] };
await context.route('**/*', async route => {
  const url = new URL(route.request().url());
  if (url.host === '127.0.0.1:5191' && !url.pathname.startsWith('/api/')) return route.continue();
  const path = url.pathname.replace(/^\/api/, '');
  let data = [];
  if (path.startsWith('/import')) {
    chamadas.push({ path, method: route.request().method(), perfil: route.request().headers()['x-perfil-id'] });
    if (path.startsWith('/import/clientes')) data = { ...resumo, previa: path.endsWith('/preview') };
    else data = { totalLinhas: 1, linhasImportadas: 1, linhasIgnoradas: 0, linhasDuplicadas: 0, erros: [] };
  } else if (path === '/perfis') data = [{ id: 1, nome: 'Perfil de teste' }];
  else if (path.startsWith('/perfis/')) data = { id: 1, nome: 'Perfil de teste' };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) });
});
const page = await context.newPage();
const file = { name: 'clientes_extraidos.csv', mimeType: 'text/csv', buffer: Buffer.from('nomeCompleto;cpf;statusExtracao\nJoão;00000000002;OK\nMaria;00000000003;OK\nOutra;;REVISAR') };
try {
  await page.goto('http://127.0.0.1:5191/backup');
  await page.getByLabel('Selecionar CSV', { exact: true }).setInputFiles(file);
  await page.getByRole('button', { name: 'Pré-visualizar CSV', exact: true }).click();
  await page.getByText('Confira antes de importar', { exact: true }).waitFor();
  assert.equal(chamadas.filter(c => c.path === '/import/clientes').length, 0);
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
  assert.equal(chamadas.filter(c => c.path === '/import/clientes').length, 0);
  await page.getByLabel('Selecionar CSV', { exact: true }).setInputFiles(file);
  await page.getByRole('button', { name: 'Pré-visualizar CSV', exact: true }).click();
  await page.getByText('Confira antes de importar', { exact: true }).waitFor();
  await page.evaluate(() => localStorage.setItem('perfil_ativo_id', '2'));
  await page.getByRole('button', { name: 'Confirmar importação', exact: true }).click();
  await page.getByText('O perfil foi alterado.', { exact: false }).waitFor();
  assert.equal(chamadas.filter(c => c.path === '/import/clientes').length, 0);
  await page.evaluate(() => localStorage.setItem('perfil_ativo_id', '1'));
  await page.getByRole('button', { name: 'Pré-visualizar CSV', exact: true }).click();
  await page.getByText('Confira antes de importar', { exact: true }).waitFor();
  await page.screenshot({ path: `${pasta}/preview-desktop.png`, fullPage: true });
  await page.getByRole('button', { name: 'Confirmar importação', exact: true }).click();
  await page.getByText('Importação concluída', { exact: true }).waitFor();
  assert.equal(chamadas.filter(c => c.path === '/import/clientes').length, 1);
  assert.ok(chamadas.every(c => c.perfil === '1'));
  assert.equal(chamadas.filter(c => c.path === '/import').length, 0);
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Baixar relatório', exact: true }).click();
  await (await download).saveAs(`${pasta}/resultado_importacao_clientes.csv`);
  await page.screenshot({ path: `${pasta}/resultado-desktop.png`, fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: `${pasta}/resultado-mobile.png`, fullPage: true });
  const excede = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  assert.equal(excede, false);
  await page.setViewportSize({ width: 1280, height: 1000 });
  await page.locator('input[accept=".xlsx"]').setInputFiles({ name: 'backup.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from('fixture') });
  await page.getByRole('button', { name: 'Importar clientes + empréstimos', exact: true }).click();
  await page.getByText('1 linha(s) importada(s)', { exact: false }).waitFor();
  assert.equal(chamadas.filter(c => c.path === '/import').length, 1);
  await writeFile(`${pasta}/ui-testes.json`, JSON.stringify({ sucesso: true, chamadas, overflowMobile: excede }, null, 2));
  console.log('UI aprovada: prévia sem gravação, cancelar, troca de perfil, confirmação única, relatório, mobile e import antigo separado. API mockada; HTTP real coberto pelos testes backend.');
} finally { await browser.close(); }

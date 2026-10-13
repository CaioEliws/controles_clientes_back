// Atualiza somente o RG informado explicitamente pelo usuário, pelo modal real.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { writeFile } from 'node:fs/promises';
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_PACKAGE_PATH);
const consultar = async () => {
  const response = await fetch('http://localhost:8080/clientes/1624', { headers: {'X-Perfil-Id':'1'} });
  assert.ok(response.ok); return response.json();
};
const antes = await consultar();
assert.match(antes.nome, /Irene/i);
const browser = await chromium.launch({ headless: true, channel: 'msedge' });
try {
  const context = await browser.newContext({ viewport: {width:1280,height:960} });
  await context.addInitScript(() => localStorage.setItem('perfil_ativo_id','1'));
  // Limita apenas a listagem exibida no contexto de teste ao cadastro alvo.
  await context.route(/\/api\/clientes$|localhost:8080\/clientes$/, async route => {
    const response = await route.fetch();
    const body = await response.json();
    await route.fulfill({response, json:body.filter(c => c.id === 1624)});
  });
  const page = await context.newPage();
  const abrir = async () => {
    await page.goto('http://localhost:5173/clientes');
    await page.getByRole('row').filter({hasText:antes.nome}).getByRole('button',{name:'Editar',exact:true}).click();
    const dialog = page.getByRole('dialog',{name:'Editar Cliente',exact:true});
    await dialog.getByLabel('Sexo para o documento',{exact:true}).locator('option[value="FEMININO"]').waitFor({state:'attached'});
    return dialog;
  };
  let dialog = await abrir();
  await dialog.getByLabel('RG',{exact:true}).fill('52.779.723');
  await dialog.getByRole('button',{name:'Salvar alterações',exact:true}).click();
  await dialog.waitFor({state:'hidden'});
  const depois = await consultar();
  assert.deepEqual(depois,{...antes,rg:'52.779.723'});
  dialog = await abrir();
  assert.equal(await dialog.getByLabel('RG',{exact:true}).inputValue(),'52.779.723');
  await dialog.screenshot({path:'target/rg-qa/irene-reaberta.png'});
  await writeFile('target/rg-qa/irene-confirmacao.json', JSON.stringify({status:'PASS',clienteId:1624,rg:'52.779.723',outrosCamposPreservados:true,salvarGetReabrir:true},null,2));
  console.log('PASS: Irene salva e reaberta com 52.779.723; demais campos preservados.');
} finally { await browser.close(); }

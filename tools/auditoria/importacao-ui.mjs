import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile,writeFile} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)('C:/Users/caioe/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const saved=JSON.parse(await readFile('.auditoria/evidencias/persistencia-antes-restart.json','utf8'));
const browser=await chromium.launch({headless:true,channel:'msedge'}),context=await browser.newContext();
await context.addInitScript(id=>localStorage.setItem('perfil_ativo_id',String(id)),saved.perfil);
let resultado;
await context.route(/\/api\/|localhost:8080\//,async route=>{
 const url=new URL(route.request().url()),path=url.pathname.replace(/^\/api/,'');
 // CDP omits multipart file bytes; relay the identical fixture as a binary upload.
 const response=path==='/import'
  ? await context.request.post('http://localhost:18082/import',{headers:{'X-Perfil-Id':String(saved.perfil)},multipart:{file:{name:'invalido.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:await readFile('.auditoria/evidencias/importacao-ui/invalido.xlsx')}}})
  : await route.fetch({url:'http://localhost:18082'+path+url.search});
 if(path==='/import') resultado=await response.json();
 await route.fulfill({response});
});
const page=await context.newPage();
try {
 await page.goto('http://localhost:5180/backup');await page.locator('input[type=file]').setInputFiles('.auditoria/evidencias/importacao-ui/invalido.xlsx');
 await page.getByRole('button',{name:'Importar Arquivo',exact:true}).click();await page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/import'));await page.waitForLoadState('networkidle');
 const text=await page.locator('body').innerText();await page.screenshot({path:'.auditoria/evidencias/importacao-ui/tela.png',fullPage:true});
 await writeFile('.auditoria/evidencias/importacao-ui/resultado.json',JSON.stringify({resultado,text},null,2));
 assert.equal(resultado.linhasImportadas,0);assert.equal(resultado.linhasIgnoradas,1);assert.ok(text.includes('0 linha(s) importada(s)'));
 assert.ok(text.includes('1 ignorada(s)'));assert.ok(text.includes(resultado.erros[0].motivo));
 console.log('PASS: importação de linha inválida exibe contagens e motivo; não informa sucesso completo.');
} finally {await browser.close();}

import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const {chromium}=createRequire(import.meta.url)('C:/Users/caioe/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const api='http://localhost:18082',front='http://localhost:5180',out='.auditoria/fechamento/smoke-final';
await mkdir(out,{recursive:true});
const checks=[],calls=[],errors=[];
const browser=await chromium.launch({headless:true,channel:'msedge'});
const context=await browser.newContext({acceptDownloads:true});
const saved=JSON.parse(await readFile('.auditoria/evidencias/persistencia-antes-restart.json','utf8'));
async function req(path,perfil=saved.perfil,method='GET',body) {
 const r=await fetch(api+path,{method,headers:{'X-Perfil-Id':String(perfil),'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body)});
 assert.ok(r.ok,`${path}: ${r.status} ${await r.clone().text()}`);return r;
}
for(const [format,key] of [['download','word'],['download-pdf','pdf'],['download-pdf-assinado','signed']]) {
 const bytes=Buffer.from(await (await req(saved.contracts+'/'+saved.id+'/'+format)).arrayBuffer());
 assert.equal(createHash('sha256').update(bytes).digest('hex'),saved[key]);
}
assert.equal((await (await req(saved.contracts)).json())[0].id,saved.id);
assert.ok((await (await req('/clientes')).json()).some(c=>c.id===saved.cliente));
assert.ok((await (await req(saved.contracts.replace('/'+saved.loan+'/contratos','/detalhes'))).json()).some(e=>e.id===saved.loan));
checks.push('Reinício: clientes, empréstimos, histórico e SHA-256 dos três documentos preservados');
const empty=(await (await req('/perfis',saved.perfil,'POST',{nome:'Smoke vazio '+Date.now()})).json()).id;
await context.addInitScript(id=>{if(!localStorage.getItem('perfil_ativo_id'))localStorage.setItem('perfil_ativo_id',String(id));},saved.perfil);
await context.route(/\/api\/|localhost:8080\//,async route=>{
 const r=route.request(),url=new URL(r.url()),path=url.pathname.replace(/^\/api/,'');
 const response=await route.fetch({url:api+path+url.search});calls.push({method:r.method(),path,status:response.status()});await route.fulfill({response});
});
const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
try {
 for(const perfil of [empty,saved.perfil]) {
  await page.goto(front+'/configuracoes');await page.locator('select').first().selectOption(String(perfil));
  await page.waitForFunction(id=>localStorage.getItem('perfil_ativo_id')===String(id),perfil);
  assert.equal(await page.evaluate(()=>localStorage.getItem('perfil_ativo_id')),String(perfil));
  for(const path of ['/','/clientes','/emprestimo','/parcelas','/documentos','/simulacao','/relatorio-parcelas','/backup','/configuracoes']) {
   const response=await page.goto(front+path);assert.equal(response.status(),200);await page.waitForLoadState('networkidle');
   assert.ok((await page.locator('body').innerText()).trim().length>10);assert.equal(await page.getByRole('alert').count(),0,`${perfil} ${path}`);
   await page.screenshot({path:out+'/'+perfil+'-'+(path.slice(1)||'home')+'.png',fullPage:true});
  }
  await page.reload();assert.equal(await page.evaluate(()=>localStorage.getItem('perfil_ativo_id')),String(perfil));
 }
 checks.push('18 páginas carregadas: nove rotas com Perfil vazio e nove com Perfil contendo dados; troca e persistência');
 assert.deepEqual(await (await req('/relatorios/inadimplentes',empty)).json(),[]);
 assert.equal((await req('/relatorios/inadimplentes')).status,200);
 assert.equal((await req('/relatorios/clientes/'+saved.cliente)).status,200);
 checks.push('Relatórios HTTP 200: carteira vazia, carteira populada e histórico do cliente');
 for(const [name,perfil] of [['zero',empty],['um',saved.perfil]]) {
  const response=await req('/export',perfil);const bytes=Buffer.from(await response.arrayBuffer());assert.equal(bytes.subarray(0,2).toString(),'PK');await writeFile(out+'/export-'+name+'.xlsx',bytes);
 }
 for(const nome of ['Cliente vários','Fiador vários']) await req('/clientes',empty,'POST',{nome,enderecoRua:'Rua A',enderecoBairro:'Centro',enderecoNumero:1});
 const pessoas=await (await req('/clientes',empty)).json();
 for(let i=0;i<3;i++) await req('/clientes/'+pessoas.find(c=>c.nome==='Cliente vários').id+'/emprestimos',empty,'POST',{
  valorEmprestado:100+i,quantidadeParcelas:2,jurosCobrado:0.5,formaPagamento:'PIX',frequenciaPagamento:'MENSAL',tipoContrato:'FISICO',fiadorId:pessoas.find(c=>c.nome==='Fiador vários').id});
 await writeFile(out+'/export-varios.xlsx',Buffer.from(await (await req('/export',empty)).arrayBuffer()));
 checks.push('Exportações HTTP reais: XLSX para zero, um e três empréstimos');
 assert.deepEqual(errors,[]);assert.equal(calls.filter(c=>c.status>=400).length,0);
 await writeFile(out+'/resultado.json',JSON.stringify({status:'PASS',checks,calls,errors},null,2));console.log(JSON.stringify({status:'PASS',checks},null,2));
} catch(e) {await page.screenshot({path:out+'/falha.png',fullPage:true}).catch(()=>{});throw e;} finally {await browser.close();}

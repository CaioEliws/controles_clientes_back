// Browser regression test against a dedicated API/database. Never use production.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_PACKAGE_PATH);
const api = 'http://localhost:18082', front = 'http://localhost:5180';
let perfil;
async function req(method,path,body) {
  const response=await fetch(api+path,{method,headers:{'Content-Type':'application/json',...(perfil?{'X-Perfil-Id':String(perfil)}:{})},body:body===undefined?undefined:JSON.stringify(body)});
  assert.ok(response.ok,`${method} ${path}: ${response.status} ${response.ok?'':await response.text()}`);
  const text=await response.text(); return text?JSON.parse(text):undefined;
}
const pessoa=(nome,cpf,rua)=>({nome,nacionalidade:'brasileira',estadoCivil:'UNIAO_ESTAVEL',profissao:'Professor',rg:'00123-X',rgOrgaoExpedidor:'SSP/SP',cpf,
  enderecoRua:rua,enderecoNumero:15,enderecoComplemento:null,enderecoBairro:'Centro',enderecoCep:'00100-000',enderecoCidade:'Cidade',enderecoEstado:'SP'});
perfil=(await req('POST','/perfis',{nome:`Exclusão auditoria ${randomUUID()}`})).id;
await req('POST','/clientes',pessoa('Devedor fictício exclusão','00000000002','Rua A'));
await req('POST','/clientes',pessoa('Fiador fictício exclusão','00000000003','Rua F'));
const clientes=await req('GET','/clientes');
const devedor=clientes.find(c=>c.nome==='Devedor fictício exclusão').id, fiador=clientes.find(c=>c.nome==='Fiador fictício exclusão').id;
const loans=`/clientes/${devedor}/emprestimos`;
await req('POST',loans,{valorEmprestado:3751.5,quantidadeParcelas:3,jurosCobrado:0,formaPagamento:'PIX',frequenciaPagamento:'MENSAL',tipoContrato:'FISICO',fiadorId:fiador});
const loan=(await req('GET',loans+'/detalhes'))[0].id, contratos=`${loans}/${loan}/contratos`;
const ids=[]; for(let i=0;i<3;i++) ids.push((await req('POST',contratos)).id);
const financialBefore=await req('GET',loans+'/detalhes');
const browser=await chromium.launch({headless:true,channel:'msedge'});
const context=await browser.newContext({viewport:{width:1280,height:900}}), page=await context.newPage();
const calls=[],errors=[],checks=[];
let simulateFailure=false, holdDelete;
page.on('pageerror',e=>errors.push(e.message));
await context.addInitScript(id=>localStorage.setItem('perfil_ativo_id',String(id)),perfil);
await context.route(/\/api\/|localhost:8080\//,async route=>{
  const request=route.request(),url=new URL(request.url()),path=url.pathname.replace(/^\/api/,'');
  calls.push({method:request.method(),path,perfil:request.headers()['x-perfil-id']});
  if(request.method()==='DELETE' && simulateFailure) {
    simulateFailure=false; await route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({message:'Falha simulada de exclusão'})}); return;
  }
  if(request.method()==='DELETE' && holdDelete) await holdDelete;
  await route.fulfill({response:await route.fetch({url:api+path+url.search})});
});
const deletes=()=>calls.filter(c=>c.method==='DELETE').length;
const confirm=()=>page.getByRole('alertdialog',{name:'Excluir contrato?',exact:true});
async function documentos() {
  await page.goto(front+'/documentos');
  await page.getByRole('combobox',{name:'Cliente / Devedor'}).click();
  await page.getByRole('button',{name:/Devedor fictício exclusão/}).click();
  await page.getByLabel('Empréstimo',{exact:true}).selectOption(String(loan));
  await page.getByRole('region',{name:'Documentos gerados'}).getByRole('button',{name:'Excluir contrato #'+ids[0],exact:true}).waitFor();
}
async function openModal() {
  await page.goto(`${front}/emprestimo?selectedCliente=${devedor}&sortOrder=asc`);
  await page.getByRole('button',{name:'Dados do contrato',exact:true}).first().click();
  const dialog=page.getByRole('dialog',{name:'Dados do contrato',exact:true});
  await dialog.getByRole('region',{name:'Contratos gerados'}).waitFor(); return dialog;
}
async function cancel(scope,id) {
  const before=deletes(); await scope.getByRole('button',{name:'Excluir contrato #'+id,exact:true}).click();
  await confirm().getByRole('button',{name:'Cancelar',exact:true}).click();
  await confirm().waitFor({state:'hidden'}); assert.equal(deletes(),before);
}
try {
  await mkdir('target/exclusao-qa',{recursive:true});
  await documentos(); const docs=page.getByRole('region',{name:'Documentos gerados'});
  await cancel(docs,ids[1]); checks.push('Documentos: cancelar não chama DELETE');
  await docs.getByRole('button',{name:'Excluir contrato #'+ids[1],exact:true}).click();
  await confirm().screenshot({path:'target/exclusao-qa/documentos-confirmacao.png'});
  simulateFailure=true;
  await confirm().getByRole('button',{name:'Excluir contrato',exact:true}).click();
  await confirm().getByRole('alert').getByText('Não foi possível excluir o contrato.',{exact:true}).waitFor();
  assert.equal((await req('GET',contratos)).length,3); checks.push('Documentos: erro mantém registro e confirmação');
  await confirm().getByRole('button',{name:'Cancelar',exact:true}).click();
  await docs.getByRole('button',{name:'Excluir contrato #'+ids[1],exact:true}).click();
  let release; holdDelete=new Promise(resolve=>{release=resolve;});
  const before=deletes(); await confirm().getByRole('button',{name:'Excluir contrato',exact:true}).click();
  const busy=confirm().getByRole('button',{name:'Excluindo...',exact:true}); await busy.waitFor();
  assert.equal(await busy.isDisabled(),true); release(); holdDelete=undefined;
  await confirm().waitFor({state:'hidden'});
  await docs.getByRole('button',{name:'Excluir contrato #'+ids[1],exact:true}).waitFor({state:'hidden'});
  await page.getByRole('status').getByText('Contrato excluído com sucesso.',{exact:true}).waitFor();
  assert.equal(deletes(),before+1); assert.deepEqual((await req('GET',contratos)).map(c=>c.id),[ids[2],ids[0]]);
  checks.push('Documentos: loading, DELETE único, toast e remoção persistida');
  await documentos(); assert.equal(await docs.getByText('Contrato #'+ids[1],{exact:true}).count(),0);
  let dialog=await openModal(), history=dialog.getByRole('region',{name:'Contratos gerados'});
  await history.getByRole('button',{name:'Excluir contrato #'+ids[2],exact:true}).waitFor();
  assert.equal(await history.getByText('Contrato #'+ids[1],{exact:true}).count(),0);
  await cancel(history,ids[2]); checks.push('Modal: cancelar não chama DELETE e reflete exclusão da página');
  await history.getByRole('button',{name:'Excluir contrato #'+ids[2],exact:true}).click();
  await confirm().screenshot({path:'target/exclusao-qa/modal-confirmacao.png'});
  await confirm().getByRole('button',{name:'Excluir contrato',exact:true}).click();
  await confirm().waitFor({state:'hidden'});
  await history.getByRole('button',{name:'Excluir contrato #'+ids[2],exact:true}).waitFor({state:'hidden'});
  assert.equal(await dialog.isVisible(),true); assert.deepEqual((await req('GET',contratos)).map(c=>c.id),[ids[0]]);
  await dialog.getByRole('button',{name:'Fechar',exact:true}).click();
  dialog=await openModal(); history=dialog.getByRole('region',{name:'Contratos gerados'});
  await history.getByRole('button',{name:'Excluir contrato #'+ids[0],exact:true}).waitFor();
  assert.equal(await history.getByText('Contrato #'+ids[2],{exact:true}).count(),0);
  checks.push('Modal: exclusão real sem fechar modal, persistência ao reabrir');
  await documentos(); assert.equal(await docs.getByText('Contrato #'+ids[2],{exact:true}).count(),0);
  await page.setViewportSize({width:390,height:844});
  await docs.screenshot({path:'target/exclusao-qa/documentos-mobile.png'});
  const bounds=await docs.boundingBox(); assert.ok(bounds.x+ bounds.width<=390);
  await docs.getByRole('button',{name:'Excluir contrato #'+ids[0],exact:true}).click();
  await confirm().getByRole('button',{name:'Excluir contrato',exact:true}).click();
  await docs.getByText('Nenhum documento gerado para este empréstimo.',{exact:true}).waitFor();
  assert.deepEqual(await req('GET',contratos),[]); checks.push('Último contrato: histórico vazio; mobile sem estouro');
  assert.deepEqual(await req('GET',loans+'/detalhes'),financialBefore);
  for(const id of ids) for(const format of ['download','download-pdf']) {
    const response=await fetch(`${api}${contratos}/${id}/${format}`,{headers:{'X-Perfil-Id':String(perfil)}});
    assert.equal(response.status,400);
  }
  assert.ok(calls.filter(c=>c.method==='DELETE').every(c=>c.perfil===String(perfil)));
  assert.deepEqual(errors,[]);
  checks.push('Empréstimo intacto; downloads indisponíveis; header escopado; sem erros JavaScript');
  await writeFile('target/exclusao-qa/resultado.json',JSON.stringify({perfil,devedor,fiador,emprestimoId:loan,contratos:ids,checks,calls,errors},null,2));
  console.log(JSON.stringify({status:'PASS',checks},null,2));
} catch(error) {
  await page.screenshot({path:'target/exclusao-qa/falha.png',fullPage:true}).catch(()=>{});
  throw error;
} finally { await browser.close(); }

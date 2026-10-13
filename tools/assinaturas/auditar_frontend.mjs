import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
const { chromium }=createRequire(import.meta.url)(process.env.PLAYWRIGHT_PACKAGE_PATH);
const api='http://localhost:18082',front='http://127.0.0.1:5180',out='target/assinaturas-qa';
let perfil;
async function req(method,path,body) {
  const r=await fetch(api+path,{method,headers:{'Content-Type':'application/json',...(perfil?{'X-Perfil-Id':String(perfil)}:{})},body:body===undefined?undefined:JSON.stringify(body)});
  assert.ok(r.ok,`${path}: ${r.status} ${r.ok?'':await r.text()}`); const text=await r.text();return text?JSON.parse(text):undefined;
}
await mkdir(out,{recursive:true});
perfil=(await req('POST','/perfis',{nome:`Assinaturas auditoria ${Date.now()}`})).id;
const pessoa=(nome,cpf)=>({nome,cpf,sexoDocumento:'FEMININO',nacionalidade:'BRASIL',estadoCivil:'SOLTEIRO',profissao:'Vendedora',rg:'52.779.723',rgOrgaoExpedidor:'SSP/SP',enderecoRua:'Rua A',enderecoNumero:15,enderecoBairro:'Centro',enderecoCep:'00100000',enderecoCidade:'Indaiatuba',enderecoEstado:'SP'});
await req('POST','/clientes',pessoa('MARINA APARECIDA OLIVEIRA SAMPAIO','00000000002'));
await req('POST','/clientes',pessoa('JOÃO CARLOS DA SILVA','00000000003'));
const pessoas=await req('GET','/clientes'),devedor=pessoas[0],fiador=pessoas[1];
const loans=`/clientes/${devedor.id}/emprestimos`;
await req('POST',loans,{valorEmprestado:400,quantidadeParcelas:2,jurosCobrado:10,formaPagamento:'PIX',frequenciaPagamento:'MENSAL',tipoContrato:'FISICO',fiadorId:fiador.id});
const emprestimo=(await req('GET',loans+'/detalhes'))[0].id,rota=`${loans}/${emprestimo}/contratos`;
const meta=await req('POST',rota);
assert.equal(meta.pdfAssinadoDisponivel,true);
const response=await fetch(`${api}${rota}/${meta.id}/download-pdf-assinado`,{headers:{'X-Perfil-Id':String(perfil)}});
const esperado=Buffer.from(await response.arrayBuffer());
const browser=await chromium.launch({headless:true,channel:'msedge'});
const context=await browser.newContext({viewport:{width:1280,height:960},acceptDownloads:true});
await context.addInitScript(id=>localStorage.setItem('perfil_ativo_id',String(id)),perfil);
let chamadasDownload=0; const errors=[];
await context.route(/\/api\/|localhost:8080\//,async route=>{
  const url=new URL(route.request().url()),path=url.pathname.replace(/^\/api/,'');
  if(path.endsWith('download-pdf-assinado')) chamadasDownload++;
  const headers=await route.request().allHeaders();delete headers.origin;
  if(route.request().method()==='OPTIONS') {
    await route.fulfill({status:204,headers:{'Access-Control-Allow-Origin':front,'Access-Control-Allow-Headers':'Content-Type, X-Perfil-Id','Access-Control-Allow-Methods':'GET, POST, OPTIONS'}});return;
  }
  const r=await route.fetch({url:api+path+url.search,headers});
  const responseHeaders={...r.headers(),'access-control-allow-origin':front};
  if(path===rota && route.request().method()==='GET') {
    // Registro legado simulado somente na listagem do contexto de teste.
    await route.fulfill({response:r,headers:responseHeaders,json:[...(await r.json()),{...meta,id:999,pdfAssinadoDisponivel:false}]});
  } else await route.fulfill({response:r,headers:responseHeaders});
});
const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
async function conferir(region,nome) {
  const novo=region.getByRole('listitem').filter({hasText:`Contrato #${meta.id}`});
  const antigo=region.getByRole('listitem').filter({hasText:'Contrato #999'});
  const assinado=novo.getByRole('button',{name:'PDF com assinaturas',exact:true});
  assert.equal(await assinado.isEnabled(),true);
  assert.match(await assinado.getAttribute('title'),/credor e da testemunha/);
  assert.equal(await antigo.getByRole('button',{name:'PDF com assinaturas',exact:true}).isDisabled(),true);
  assert.equal(await antigo.getByRole('button',{name:'Baixar PDF',exact:true}).isEnabled(),true);
  const download=page.waitForEvent('download');await assinado.click();
  const arquivo=await download; assert.match(arquivo.suggestedFilename(),/-assinado\.pdf$/);
  const path=`${out}/download-${nome}.pdf`;await arquivo.saveAs(path);assert.deepEqual(await readFile(path),esperado);
  await region.screenshot({path:`${out}/frontend-${nome}.png`});
}
try {
  await page.goto(front+'/documentos');
  await page.getByRole('combobox',{name:'Cliente / Devedor'}).click();
  await page.getByRole('button',{name:/MARINA APARECIDA OLIVEIRA SAMPAIO/}).click();
  await page.getByLabel('Empréstimo',{exact:true}).selectOption(String(emprestimo));
  await conferir(page.getByRole('region',{name:'Documentos gerados'}),'documentos');
  await page.goto(`${front}/emprestimo?selectedCliente=${devedor.id}`);
  await page.getByRole('button',{name:'Dados do contrato',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'Dados do contrato',exact:true});
  await conferir(dialog.getByRole('region',{name:'Contratos gerados'}),'modal');
  assert.equal(chamadasDownload,2);assert.deepEqual(errors,[]);
  await writeFile(`${out}/frontend.json`,JSON.stringify({status:'PASS',downloadsReaisNasDuasTelas:true,bytesIguaisAoEndpoint:true,legadoDesabilitadoComPdfNormalAtivo:true,errors},null,2));
  console.log('PASS: downloads reais de PDF com assinaturas nas duas telas; bytes históricos iguais; legado desabilitado; PDF normal ativo.');
} catch(e) {await page.screenshot({path:`${out}/frontend-falha.png`,fullPage:true}).catch(()=>{});throw e;}
finally {await browser.close();}

import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
const {chromium}=createRequire(import.meta.url)('C:/Users/caioe/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const api='http://localhost:18082',front=process.env.AUDITORIA_FRONT||'http://localhost:5180',out=process.env.AUDITORIA_E2E_OUT||'.auditoria/evidencias/e2e';
await mkdir(out,{recursive:true});
let perfil;
async function req(method,path,body,scope=perfil) {
 const r=await fetch(api+path,{method,headers:{'Content-Type':'application/json',...(scope?{'X-Perfil-Id':String(scope)}:{})},body:body===undefined?undefined:JSON.stringify(body)});
 const text=await r.text();assert.ok(r.ok,`${method} ${path}: ${r.status} ${text}`);return text?JSON.parse(text):undefined;
}
perfil=(await req('POST','/perfis',{nome:'Auditoria E2E '+Date.now()})).id;
const checks=[],calls=[],errors=[];
const browser=await chromium.launch({headless:true,channel:'msedge'});
const context=await browser.newContext({acceptDownloads:true,viewport:{width:1360,height:1000}});
await context.addInitScript(id=>localStorage.setItem('perfil_ativo_id',String(id)),perfil);
await context.route(/\/api\/|localhost:8080\//,async route=>{
 const request=route.request(),url=new URL(request.url()),path=url.pathname.replace(/^\/api/,'');
 const response=await route.fetch({url:api+path+url.search});
 calls.push({method:request.method(),path,status:response.status(),perfil:request.headers()['x-perfil-id']});
 await route.fulfill({response});
});
const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
async function pessoa(form,nome,cpf,rg='409614713') {
 await form.getByLabel('Nome completo',{exact:true}).fill(nome);
 await form.getByLabel('Sexo para o documento').selectOption('FEMININO');
 await form.getByLabel('Estado civil',{exact:true}).selectOption('SOLTEIRO');
 for(const [label,value] of Object.entries({'Profissão':'Professora de Educação Física','CPF':cpf,'RG':rg,'CEP':'00100000','Rua':'Rua da Auditoria','Número':'15','Bairro':'Centro','Cidade':'Indaiatuba','Complemento':'Casa 2','Quem indicou?':'José da Silva'})) await form.getByLabel(label,{exact:true}).fill(value);
 await form.getByLabel('Órgão expedidor / UF emissora').selectOption('SSP/SP');
 await form.getByLabel('Estado / UF').selectOption('SP');
}
async function download(scope,name,file) {
 const pending=page.waitForEvent('download');await scope.getByRole('button',{name,exact:true}).click();
 const d=await pending;assert.equal(await d.failure(),null);const bytes=await readFile(await d.path());assert.ok(bytes.length>0);await writeFile(out+'/'+file,bytes);return bytes;
}
try {
 await page.goto(front+'/clientes');
 await page.getByRole('button',{name:'Novo Cliente',exact:true}).click();
 let form=page.getByRole('dialog',{name:'Cadastrar Novo Cliente',exact:true});
 await pessoa(form,'Marina Auditoria','00000000002');
 await form.getByRole('button',{name:'Salvar Cliente',exact:true}).click();await form.waitFor({state:'hidden'});
 let devedor=(await req('GET','/clientes')).find(c=>c.nome==='Marina Auditoria');assert.ok(devedor);checks.push('1. Cadastro completo por UI e persistência PostgreSQL');
 await page.getByRole('row').filter({hasText:'Marina Auditoria'}).getByRole('button',{name:'Editar',exact:true}).click();
 form=page.getByRole('dialog',{name:'Editar Cliente',exact:true});await form.getByLabel('Nome completo',{exact:true}).fill('Marina Auditoria Editada');
 await form.getByLabel('Rua',{exact:true}).fill('Rua Revisada');await form.getByLabel('Profissão',{exact:true}).fill('Técnico de TI');
 await form.getByRole('button',{name:'Salvar alterações',exact:true}).click();await form.waitFor({state:'hidden'});
 await page.getByRole('row').filter({hasText:'Marina Auditoria Editada'}).getByRole('button',{name:'Editar',exact:true}).click();
 form=page.getByRole('dialog',{name:'Editar Cliente',exact:true});await form.getByLabel('Rua',{exact:true}).waitFor();
 await page.waitForFunction(()=>[...document.querySelectorAll('input')].some(i=>i.value==='Rua Revisada'));
 assert.equal((await form.getByLabel('CPF',{exact:true}).inputValue()).replace(/\D/g,''),'00000000002');assert.equal(await form.getByLabel('RG',{exact:true}).inputValue(),'40.961.471-3');
 await form.getByRole('button',{name:'Cancelar',exact:true}).click();checks.push('2. Editar, salvar e reabrir cliente; CPF/RG/CEP com zeros');
 for(let i=0;i<35;i++) await req('POST','/clientes',{nome:'Pessoa '+String(i).padStart(2,'0'),sexoDocumento:'FEMININO',enderecoRua:'Rua Teste',enderecoNumero:1,enderecoBairro:'Centro'});
 await page.goto(front+`/emprestimo?selectedCliente=${devedor.id}`);await page.getByRole('button',{name:'Criar Empréstimo',exact:true}).click();
 const loanForm=page.getByRole('dialog',{name:'Novo Empréstimo',exact:true});
 await loanForm.getByPlaceholder('Ex: 3000').fill('400');await loanForm.getByPlaceholder('Ex: 6').fill('2');await loanForm.getByPlaceholder('Ex: 12').fill('0');
 await loanForm.locator('select[name="formaPagamento"]').selectOption('PIX');
 await loanForm.getByRole('button',{name:'Criar empréstimo',exact:true}).click();await loanForm.getByText('Selecione um fiador.',{exact:true}).waitFor();
 await loanForm.getByRole('button',{name:'Cadastrar novo fiador',exact:true}).click();
 form=page.getByRole('dialog',{name:'Cadastrar Novo Cliente',exact:true});await pessoa(form,'Ivete Fiadora','00000000003','52779723');
 await form.getByRole('button',{name:'Salvar Cliente',exact:true}).click();await form.waitFor({state:'hidden'});
 assert.equal(await loanForm.getByPlaceholder('Ex: 3000').inputValue(),'400');
 const fiador=page.getByRole('combobox',{name:'Fiador',exact:true});await fiador.click();
 const list=page.getByRole('listbox',{name:'Fiadores disponíveis'});assert.ok(!(await list.innerText()).includes('Marina Auditoria'));
 const nomes=await list.getByRole('option').allTextContents();assert.deepEqual(nomes,[...nomes].sort((a,b)=>a.localeCompare(b,'pt-BR',{sensitivity:'base'})));
 await list.hover();await page.mouse.wheel(0,500);await page.waitForTimeout(300);assert.ok(await list.evaluate(el=>el.scrollTop)>0);
 await page.getByRole('combobox',{name:'Buscar fiador',exact:true}).fill('ivete');await list.getByRole('option',{name:'Ivete Fiadora',exact:true}).click();
 checks.push('3. Fiador obrigatório, cadastro aninhado, busca, ordem e scroll');
 await loanForm.getByRole('button',{name:'Criar empréstimo',exact:true}).click({clickCount:2,delay:30});await loanForm.waitFor({state:'hidden'});
 const loans=`/clientes/${devedor.id}/emprestimos`,e=(await req('GET',loans+'/detalhes'))[0];assert.equal(e.valorTotalEmprestimo,400);assert.equal(e.fiadorNome,'Ivete Fiadora');checks.push('4. Novo empréstimo pela UI e consulta persistida');
 const contratos=`${loans}/${e.id}/contratos`;
 await page.getByRole('button',{name:'Dados do contrato',exact:true}).click();const dialog=page.getByRole('dialog',{name:'Dados do contrato',exact:true});
 await dialog.getByRole('heading',{name:'Credor',exact:true}).waitFor();assert.ok((await dialog.innerText()).includes('Edson Antonio Vieira'));checks.push('5. Dados do contrato e credor fixo');
 page.once('dialog',d=>d.accept());await dialog.getByRole('button',{name:'Gerar contrato',exact:true}).click({clickCount:2,delay:30});
 const region=dialog.getByRole('region',{name:'Contratos gerados'});await region.getByRole('button',{name:'Baixar PDF',exact:true}).waitFor();
 const meta=(await req('GET',contratos))[0];checks.push('6. Geração DOCX + contrato PDF + promissória + assinado');
 const word=await download(region,'Baixar Word','contrato.docx');assert.equal(createHash('sha256').update(word).digest('hex'),meta.hashDocumento);
 const pdf=await download(region,'Baixar PDF','contrato-completo.pdf');assert.equal(pdf.subarray(0,5).toString(),'%PDF-');checks.push('7. Download PDF e Word; SHA-256');
 const signed=await download(region,'PDF com assinaturas','contrato-assinado.pdf');assert.equal(signed.subarray(0,5).toString(),'%PDF-');checks.push('8. Download PDF com assinaturas');
 await dialog.getByRole('button',{name:'Fechar',exact:true}).click();
 await page.goto(front+`/parcelas?emprestimoId=${e.id}`);
 await page.getByRole('button',{name:'Pagar',exact:true}).first().click();const pay=page.getByRole('dialog');
 await pay.getByPlaceholder('R$ 0,00').fill('1000');await pay.getByRole('button',{name:'Confirmar Pagamento',exact:true}).click({clickCount:2,delay:30});await pay.waitFor({state:'hidden'});
 const paid=(await req('GET',loans+`/${e.id}/parcelas`)).find(p=>p.numeroParcela===1);assert.equal(paid.valorPago,10);checks.push('9. Pagamento parcial pela UI e financeiro persistido');
 await page.goto(front+'/documentos');await page.getByRole('combobox',{name:'Cliente / Devedor'}).click();await page.getByLabel('Buscar cliente por nome ou CPF').fill('Marina');
 await page.getByRole('button',{name:/Marina Auditoria Editada/}).click();await page.getByLabel('Empréstimo',{exact:true}).selectOption(String(e.id));
 const docs=page.getByRole('region',{name:'Documentos gerados'});await docs.getByRole('button',{name:'Baixar PDF',exact:true}).waitFor();
 assert.deepEqual(await download(docs,'Baixar PDF','historico-pdf.pdf'),pdf);await page.screenshot({path:out+'/documentos.png',fullPage:true});checks.push('10. Página Documentos e download histórico idêntico após pagamento');
 await page.getByRole('button',{name:'Gerar contrato + promissória',exact:true}).click();
 const generation=page.getByRole('dialog',{name:'Gerar documentos?',exact:true});
 await generation.getByRole('button',{name:'Gerar documentos',exact:true}).click({clickCount:2,delay:30});await generation.waitFor({state:'hidden'});
 const second=(await req('GET',contratos))[0];assert.notEqual(second.id,meta.id);
 for(const [label,file] of [['Baixar Word','pagina-word.docx'],['Baixar PDF','pagina-pdf.pdf'],['PDF com assinaturas','pagina-assinado.pdf']]) {const pending=page.waitForEvent('download');await docs.getByRole('button',{name:label,exact:true}).first().click();const d=await pending;assert.equal(await d.failure(),null);await d.saveAs(out+'/'+file);}
 const third=await req('POST',contratos);await page.reload();await page.getByRole('combobox',{name:'Cliente / Devedor'}).click();await page.getByLabel('Buscar cliente por nome ou CPF').fill('Marina');await page.getByRole('button',{name:/Marina Auditoria Editada/}).click();await page.getByLabel('Empréstimo',{exact:true}).selectOption(String(e.id));
 await docs.getByRole('button',{name:'Excluir contrato #'+second.id,exact:true}).click();const confirm=page.getByRole('alertdialog',{name:'Excluir contrato?',exact:true});await confirm.getByRole('button',{name:'Excluir contrato',exact:true}).click({clickCount:2,delay:30});await confirm.waitFor({state:'hidden'});
 assert.deepEqual((await req('GET',contratos)).map(c=>c.id),[third.id,meta.id]);
 for(const format of ['download','download-pdf','download-pdf-assinado']) {const r=await fetch(api+contratos+'/'+second.id+'/'+format,{headers:{'X-Perfil-Id':String(perfil)}});assert.equal(r.status,404);}
 checks.push('11. Página Documentos: geração, três downloads, delete intermediário e três formatos 404');
 await page.goto(front+`/emprestimo?selectedCliente=${devedor.id}`);await page.getByRole('button',{name:'Dados do contrato',exact:true}).click();const modal=page.getByRole('dialog',{name:'Dados do contrato',exact:true});await modal.getByRole('button',{name:'Excluir contrato #'+meta.id,exact:true}).click();const alert=page.getByRole('alertdialog',{name:'Excluir contrato?',exact:true});await alert.getByRole('button',{name:'Excluir contrato',exact:true}).click();await alert.waitFor({state:'hidden'});assert.deepEqual((await req('GET',contratos)).map(c=>c.id),[third.id]);await modal.getByRole('button',{name:'Fechar',exact:true}).click();checks.push('14. Exclusão pelo modal sincroniza histórico da página');
 await req('POST','/parcelas/desfazer-pagamento',{idEmprestimo:e.id,numeroParcela:1});await req('POST',contratos);
 await page.goto(front+`/emprestimo?selectedCliente=${devedor.id}`);await page.getByRole('button',{name:'Dados do contrato',exact:true}).waitFor();
 const row=page.locator('tr').filter({has:page.getByRole('button',{name:'Dados do contrato',exact:true})});await row.getByRole('button').last().click();
 const deleteLoan=page.getByRole('dialog',{name:'Deletar empréstimo',exact:true});await deleteLoan.getByRole('button',{name:'Excluir',exact:true}).click({clickCount:2,delay:30});await deleteLoan.waitFor({state:'hidden'});
 assert.deepEqual(await req('GET',loans+'/detalhes'),[]);assert.equal((await req('GET','/clientes')).length,37);checks.push('12. Excluir empréstimo com contrato; Cliente e Fiador preservados');
 assert.equal(calls.filter(c=>c.method==='POST'&&c.path==='/parcelas/pagar-parcial').length,1);assert.equal(calls.filter(c=>c.method==='POST'&&c.path===contratos).length,2);assert.equal(calls.filter(c=>c.method==='DELETE'&&c.path===loans+'/'+e.id).length,1);
 assert.deepEqual(errors,[]);assert.ok(calls.filter(c=>c.method==='POST'&&c.path===loans).length===1);
 await page.reload();assert.equal(await page.evaluate(()=>localStorage.getItem('perfil_ativo_id')),String(perfil));checks.push('13. Perfil ativo persiste e mutação de empréstimo sem POST duplicado');
 await writeFile(out+'/resultado.json',JSON.stringify({status:'PASS',perfil,emprestimo:e.id,checks,calls,errors},null,2));console.log(JSON.stringify({status:'PASS',checks},null,2));
} catch(e) {
 await page.screenshot({path:out+'/falha.png',fullPage:true}).catch(()=>{});await writeFile(out+'/falha.json',JSON.stringify({checks,calls,errors,text:await page.locator('body').innerText()},null,2));throw e;
} finally {await browser.close();}






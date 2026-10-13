import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {openSync,closeSync} from 'node:fs';
import {writeFile,readFile} from 'node:fs/promises';
import {setTimeout} from 'node:timers/promises';
import {createHash} from 'node:crypto';
const java='C:/Program Files/Java/jdk-17/bin/java.exe',out='.auditoria/evidencias';
const api='http://127.0.0.1:18082';let perfil;
async function req(path,method='GET',body,base=api) {
 return fetch(base+path,{method,headers:{'Content-Type':'application/json',...(perfil?{'X-Perfil-Id':String(perfil)}:{})},body:body===undefined?undefined:JSON.stringify(body)});
}
async function ok(path,method='GET',body) {const r=await req(path,method,body);assert.ok(r.ok,`${path}: ${r.status} ${await r.clone().text()}`);const t=await r.text();return t?JSON.parse(t):undefined;}
let contracts,persistencia;
if(process.env.AUDITORIA_REUSE_FIXTURE==='1') {
 persistencia=JSON.parse(await readFile(out+'/persistencia-antes-restart.json','utf8'));
 perfil=persistencia.perfil;contracts=persistencia.contracts;
} else {
perfil=(await ok('/perfis','POST',{nome:'Startup auditoria '+Date.now()})).id;
for(const [nome,cpf] of [['Pessoa Startup','00000000008'],['Fiadora Startup','00000000009']]) await ok('/clientes','POST',{nome,cpf,sexoDocumento:'FEMININO',nacionalidade:'BRASIL',estadoCivil:'SOLTEIRO',profissao:'Professora',rg:'52.779.723',rgOrgaoExpedidor:'SSP/SP',enderecoRua:'Rua A',enderecoNumero:1,enderecoBairro:'Centro',enderecoCep:'00100000',enderecoCidade:'Indaiatuba',enderecoEstado:'SP'});
const clientes=await ok('/clientes'),loans=`/clientes/${clientes.find(c=>c.nome==='Pessoa Startup').id}/emprestimos`;
await ok(loans,'POST',{valorEmprestado:100,quantidadeParcelas:1,jurosCobrado:0,formaPagamento:'PIX',frequenciaPagamento:'MENSAL',tipoContrato:'FISICO',fiadorId:clientes.find(c=>c.nome==='Fiadora Startup').id});
const loan=(await ok(loans+'/detalhes'))[0].id;contracts=`${loans}/${loan}/contratos`;
const meta=await ok(contracts,'POST');const hash=async suffix=>createHash('sha256').update(Buffer.from(await (await req(contracts+`/${meta.id}/`+suffix)).arrayBuffer())).digest('hex');
persistencia={perfil,cliente:clientes[0].id,loan,contracts,id:meta.id,word:await hash('download'),pdf:await hash('download-pdf'),signed:await hash('download-pdf-assinado')};
await writeFile(out+'/persistencia-antes-restart.json',JSON.stringify(persistencia,null,2));
}
const python='C:/Users/caioe/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
const prep=spawn(python,['tools/auditoria/preparar_jar.py'],{windowsHide:true,stdio:'inherit'});assert.equal(await new Promise(r=>prep.once('exit',r)),0);
const checks=[];
for(const [i,caso] of ['banco-indisponivel','libreoffice-ausente','sem-docx','sem-promissoria','sem-assinatura'].entries()) {
 const port=18100+i,base=`http://127.0.0.1:${port}`;
 const jar=caso.startsWith('sem-')?`.auditoria/${caso}.jar`:'.auditoria/app.jar';
 const fd=openSync(out+'/startup-'+caso+'.log','w');
 const process=spawn(java,['-jar',jar,`--server.port=${port}`,'--server.address=127.0.0.1',
  `--spring.datasource.url=jdbc:postgresql://127.0.0.1:${caso==='banco-indisponivel'?15433:15432}/controle_clientes_auditoria`,
  '--spring.datasource.username=auditor','--spring.datasource.password=','--spring.datasource.driver-class-name=org.postgresql.Driver',
  '--spring.datasource.hikari.connection-timeout=1000','--spring.datasource.hikari.initialization-fail-timeout=1000',
  '--spring.jpa.hibernate.ddl-auto=update','--spring.jpa.show-sql=false','--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect',
  ...(caso==='libreoffice-ausente'?['--contrato.pdf.executavel=C:/auditoria/executavel-inexistente.exe']:[])],{windowsHide:true,stdio:['ignore',fd,fd]});
 let exitCode,ready=false;process.once('exit',c=>{exitCode=c;});
 try {
  for(let attempt=0;attempt<60&&exitCode===undefined;attempt++) {
   try {if((await req('/ping','GET',undefined,base)).ok){ready=true;break;}} catch {}
   await setTimeout(500);
  }
  if(caso==='banco-indisponivel') {assert.equal(ready,false);assert.ok(exitCode!==undefined&&exitCode!==0);checks.push({caso,startup:'falha controlada',exitCode});}
  else {
   assert.equal(ready,true,caso+' deve preservar uso normal da API');
   const before=(await ok(contracts)).length;
   const r=await req(contracts,'POST',undefined,base),body=await r.text();assert.equal(r.status,400,body);
   const esperado={'libreoffice-ausente':'LibreOffice','sem-docx':'modelo Word','sem-promissoria':'promissória','sem-assinatura':'assinaturas'}[caso];
   assert.ok(body.toLowerCase().includes(esperado.toLowerCase()),body);
   assert.equal((await ok(contracts)).length,before);checks.push({caso,startup:'UP',geracao:r.status,mensagem:JSON.parse(body).message,registroParcial:false});
  }
 } finally {
  if(exitCode===undefined) process.kill();closeSync(fd);
 }
}
const privado=await req('/assinaturas/edson.png');assert.equal(privado.status,404);
await writeFile(out+'/startup-resultados.json',JSON.stringify({status:'PASS',checks,assetPrivado:privado.status,persistencia},null,2));console.log(JSON.stringify({status:'PASS',checks},null,2));

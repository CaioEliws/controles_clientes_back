import assert from 'node:assert/strict';
import {spawn,execFileSync} from 'node:child_process';
import {openSync,closeSync} from 'node:fs';
import {mkdir,writeFile} from 'node:fs/promises';
const out='.auditoria/fechamento/health';await mkdir(out,{recursive:true});
const java='C:/Program Files/Java/jdk-17/bin/java.exe',python='C:/Users/caioe/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe';
const casos={'sem-docx':['templates/contrato-confissao-divida-v1.docx','contratoTemplate'],'sem-promissoria':['templates/promissoria-v1.pdf','promissoriaTemplate'],'sem-edson':['assinaturas/edson.png','assinaturaEdson'],'sem-ivani':['assinaturas/ivani.png','assinaturaIvani'],'sem-lo':[null,'libreOffice']};
execFileSync(python,['-c',`from zipfile import ZipFile
import json
for name,(entry,_) in json.loads(${JSON.stringify(JSON.stringify(casos))}).items():
 if entry:
  with ZipFile('target/controle_clientes-0.0.1-SNAPSHOT.jar') as src,ZipFile('${out}/'+name+'.jar','w') as dst:
   for info in src.infolist():
    if info.filename!='BOOT-INF/classes/'+entry: dst.writestr(info,src.read(info.filename))
`],{windowsHide:true});
const checks=[];const main=await fetch('http://localhost:18082/health');assert.equal(main.status,200);assert.equal((await main.json()).status,'UP');checks.push({caso:'normal',status:'UP',http:200});
for(const [i,[caso,[resource,key]]]of Object.entries(casos).entries()){
const port=18110+i,fd=openSync(out+'/'+caso+'.log','w');const jar=resource?out+'/'+caso+'.jar':'target/controle_clientes-0.0.1-SNAPSHOT.jar';
const process=spawn(java,['-jar',jar,`--server.port=${port}`,'--server.address=127.0.0.1','--spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/controle_clientes_auditoria','--spring.datasource.username=auditor','--spring.datasource.password=','--spring.jpa.hibernate.ddl-auto=validate',...(caso==='sem-lo'?['--contrato.pdf.executavel=executavel-inexistente-fechamento']:[])],{windowsHide:true,stdio:['ignore',fd,fd]});
try{let ready=false;for(let n=0;n<50;n++){try{if((await fetch(`http://localhost:${port}/ping`)).ok){ready=true;break;}}catch{}await new Promise(r=>setTimeout(r,350));}assert.ok(ready,caso);const inicio=Date.now(),r=await fetch(`http://localhost:${port}/health`),body=await r.json();assert.equal(r.status,200);assert.equal(body.status,'DEGRADED');assert.equal(body.api,'UP');assert.equal(body.database,'UP');assert.equal(body[key],'DOWN');assert.ok(Object.values(body).every(v=>['UP','DOWN','DEGRADED'].includes(v)));checks.push({caso,http:r.status,body,ms:Date.now()-inicio});}finally{process.kill();await new Promise(resolve=>process.once('exit',resolve));closeSync(fd);}
}
await writeFile(out+'/resultado.json',JSON.stringify({status:'PASS',checks},null,2));console.log('HEALTH PASS '+checks.length+' cenários reais');

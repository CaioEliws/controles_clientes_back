import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
const out='.auditoria/fechamento/restore',api='http://localhost:18082',dest='http://localhost:18083';await mkdir(out,{recursive:true});
for(let i=0;i<50;i++){try{if((await fetch(api+'/ping')).ok)break;}catch{}await new Promise(r=>setTimeout(r,400));}
const f=JSON.parse(await readFile('.auditoria/fechamento/frontend/resultado.json')),pg='C:/Program Files/PostgreSQL/18/bin/',database='controle_clientes_fechamento_restore_'+Date.now(),source='controle_clientes_auditoria';
function command(bin,args){return execFileSync(bin,args,{encoding:'utf8',windowsHide:true,...(bin==='powershell.exe'?{stdio:'ignore'}:{})});}
function sql(db,q){return command(pg+'psql.exe',['-h','127.0.0.1','-p','15432','-U','auditor','-d',db,'-At','-v','ON_ERROR_STOP=1','-c',q]).trim();}
async function req(base,path,method='GET',body){const r=await fetch(base+path,{method,headers:{'Content-Type':'application/json','X-Perfil-Id':String(f.scope)},body:body===undefined?undefined:JSON.stringify(body)});assert.ok(r.ok,`${path}: ${r.status} ${await r.clone().text()}`);return r;}
const loans=`/clientes/${f.cliente}/emprestimos`,contracts=loans+'/'+f.emprestimo+'/contratos';
const atual=await(await req(api,loans+'/'+f.emprestimo+'/parcelas')).json();
if(atual.find(p=>p.numeroParcela===1).valorPago>0)await req(api,'/parcelas/desfazer-pagamento','POST',{idEmprestimo:f.emprestimo,numeroParcela:1});
await req(api,'/parcelas/pagar-parcial','POST',{idEmprestimo:f.emprestimo,numeroParcela:1,valorPago:10});
const meta=await (await req(api,contracts,'POST')).json();
const hashes={};for(const format of ['download','download-pdf','download-pdf-assinado']){const bytes=Buffer.from(await(await req(api,contracts+'/'+meta.id+'/'+format)).arrayBuffer());hashes[format]=createHash('sha256').update(bytes).digest('hex');await writeFile(out+'/'+format+(format==='download'?'.docx':'.pdf'),bytes);}
const before={cliente:await(await req(api,'/clientes/'+f.cliente)).json(),loans:await(await req(api,loans+'/detalhes')).json(),parcelas:await(await req(api,loans+'/'+f.emprestimo+'/parcelas')).json(),contratos:await(await req(api,contracts)).json()};
assert.equal(before.parcelas.find(p=>p.numeroParcela===1).valorPago,10);
const tables=['perfil','clientes','emprestimos','parcelas','parcela_observacoes','contratos_gerados'];
const counts=Object.fromEntries(tables.map(t=>[t,Number(sql(source,'select count(*) from '+t))]));
const snapshotQuery=`select json_agg(row_to_json(x) order by id) from (select id,hash_documento,hash_pdf_final,hash_pdf_assinado,md5(snapshot_json) as snapshot,md5(promissoria_snapshot_json) as promissoria,md5(docx) as docx,md5(pdf_final) as pdf,md5(pdf_final_assinado) as assinado from contratos_gerados) x`;
const databaseDocs=sql(source,snapshotQuery),dump=process.cwd().replaceAll('\\','/')+'/'+out+'/backup-'+new Date().toISOString().replaceAll(':','-')+'.dump';
command(pg+'pg_dump.exe',['-h','127.0.0.1','-p','15432','-U','auditor','-Fc','-d',source,'-f',dump]);
command(pg+'createdb.exe',['-h','127.0.0.1','-p','15432','-U','auditor',database]);
command(pg+'pg_restore.exe',['-h','127.0.0.1','-p','15432','-U','auditor','--exit-on-error','--no-owner','-d',database,dump]);
for(const t of tables)assert.equal(Number(sql(database,'select count(*) from '+t)),counts[t]);assert.equal(sql(database,snapshotQuery),databaseDocs);
command('powershell.exe',['-NoProfile','-File','tools/fechamento/server.ps1','-Port','18083','-Database',database]);
try{let ready=false;for(let i=0;i<50;i++){try{ready=(await fetch(dest+'/ping')).ok;}catch{}if(ready)break;await new Promise(r=>setTimeout(r,400));}assert.ok(ready);
const after={cliente:await(await req(dest,'/clientes/'+f.cliente)).json(),loans:await(await req(dest,loans+'/detalhes')).json(),parcelas:await(await req(dest,loans+'/'+f.emprestimo+'/parcelas')).json(),contratos:await(await req(dest,contracts)).json()};assert.deepEqual(after,before);
for(const format of Object.keys(hashes)){const bytes=Buffer.from(await(await req(dest,contracts+'/'+meta.id+'/'+format)).arrayBuffer());assert.equal(createHash('sha256').update(bytes).digest('hex'),hashes[format]);}
const health=await(await req(dest,'/health')).json();assert.equal(health.status,'UP');await writeFile(out+'/resultado.json',JSON.stringify({status:'PASS',database,source,dump,counts,hashes,fixture:f,contrato:meta.id,health,checks:['6 tabelas: contagens iguais','Todos snapshots/promissórias/hashes/bytes iguais no SQL','Cliente/empréstimo/parcelas/pagamento parcial/histórico idênticos via API restaurada','DOCX/PDF/PDF assinado: SHA-256 idênticos','Health UP na API restaurada']},null,2));console.log('RESTORE PASS '+database);
}finally{command('powershell.exe',['-NoProfile','-File','tools/fechamento/server.ps1','-Mode','Stop','-Port','18083']);}

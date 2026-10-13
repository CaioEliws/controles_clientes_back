import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile,mkdir} from 'node:fs/promises';
const {chromium}=createRequire(import.meta.url)('C:/Users/caioe/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fase=process.env.VOLUME_FASE||'antes',api='http://127.0.0.1:18084',front='http://localhost:5180',out='.auditoria/fechamento';await mkdir(out,{recursive:true});
const home=process.env.VOLUME_ROTA==='home';
for(let i=0;i<50;i++){try{if((await fetch(api+'/ping')).ok)break;}catch{}await new Promise(r=>setTimeout(r,400));}
const browser=await chromium.launch({headless:true,channel:'msedge'}),context=await browser.newContext();const calls=[];
await context.addInitScript(()=>localStorage.setItem('perfil_ativo_id','1'));
await context.route(/\/api\/|localhost:8080\//,async route=>{const u=new URL(route.request().url()),path=u.pathname.replace(/^\/api/,'');calls.push(path);const response=await route.fetch({url:api+path+u.search});await route.fulfill({response});});
const page=await context.newPage();try{const inicio=Date.now();await page.goto(front+(home?'/':'/clientes'));await page.waitForLoadState('networkidle');const text=await page.locator('body').innerText();if(home){assert.ok(text.includes('100.000'));await page.locator('.recharts-wrapper').waitFor();}else{assert.ok(text.includes('500'));assert.ok(text.includes('1.000')||text.includes('1000'));}const emprestimosRequests=calls.filter(p=>p.includes('/emprestimos')).length;if(fase==='antes')assert.equal(emprestimosRequests,500);else assert.equal(emprestimosRequests,home?1:0);await writeFile(out+'/volume-'+(home?'home':'ui')+'-'+fase+'.json',JSON.stringify({status:'PASS',clientes:500,emprestimos:1000,emprestimosRequests,totalRequests:calls.length,ms:Date.now()-inicio,calls,text},null,2));console.log(fase+': '+emprestimosRequests+' requisições de empréstimos');}finally{await browser.close();}


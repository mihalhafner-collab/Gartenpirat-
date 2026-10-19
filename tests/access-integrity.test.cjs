/* Actual mobile assets in Chromium; synthetic bridge, no Android/Drive/device claim. */
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url'),{chromium}=require('./browser.cjs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']}),results=[];
 async function check(name,fn){try{await fn();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.message});}console.log(results.at(-1));}
 try{
  const page=await browser.newPage({viewport:{width:426,height:852}}),errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.clock.setFixedTime(new Date('2026-10-18T12:00:00Z'));
  await page.addInitScript(()=>{
   window.fixture={master:{customers:Array.from({length:50},(_,i)=>({id:'qa_c'+i,name:'Synthetischer Kunde '+i,active:1,billing_mode:'direct'})),orders:[],photos:[],invoices:[],worklogs:Array.from({length:3000},(_,i)=>({id:'qa_w'+i,customer_id:'qa_c'+i%50,billing_customer_id:'qa_c'+i%50,work_date:i===1?'2025-12-31':'2026-10-18',activity:'Synthetische Arbeit '+i,hours:2,notes:'Originalnotiz',break_minutes:0,kilometers:0,billed:0,customer_visible:1,updated_at:'t1'}))},pending:[]};window.writes=[];window.failSave=false;window.ownerShareRequest={};window.shareHandoffs=0;
   window.AndroidData={getState:()=>JSON.stringify(fixture),getSyncStatus:()=>JSON.stringify({configured:false}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),getAppInfo:()=>JSON.stringify({versionName:'fixture',versionCode:67}),getOwnerSettings:()=>JSON.stringify({}),getInvoiceShareHandoff:()=>JSON.stringify(ownerShareRequest),sharePreparedInvoice:()=>{shareHandoffs++;return 'OK';},saveWorklog:raw=>{if(failSave)return 'ERROR: Synthetischer Speicherfehler';const p=JSON.parse(raw);writes.push(p);const accepted={...p};delete accepted._edit_base;delete accepted._edit_changes;fixture.pending.push({client_id:'edit-'+writes.length,kind:'worklog',payload:accepted});return p.id;}};window.AndroidSpeech={start:()=>{}};
  });
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);
  await page.waitForFunction(()=>document.getElementById('entry-billing-customer')&&typeof openWorklogDetail==='function');
  await check('Year filters use calendar year; ISO week still groups across Dec 31',async()=>{
   await page.evaluate(()=>{openPage('worklogs');document.getElementById('worklog-period').value='year';render();});
   assert.match(await page.locator('#worklog-filter-summary').innerText(),/2999/);
   await page.evaluate(()=>{openPage('weeks');document.getElementById('week-period').value='year';render();});
   assert.equal(await page.locator('.week-worklog[data-edit-weeklog="qa_w1"]').count(),0);
   assert.equal(await page.evaluate(()=>weekInfo('2025-12-31').year),2026);
  });
  await check('Stale opened form preserves remotely changed note and local draft',async()=>{
   await page.evaluate(()=>openWorklogDetail('qa_w0'));await page.locator('[data-work-window-edit]').click();await page.locator('#edit-worklog-activity').fill('Lokaler Entwurf');
   await page.evaluate(()=>{fixture.master.worklogs[0].notes='Remote Nachtrag';fixture.master.worklogs[0].updated_at='t2';load();});
   const before=await page.evaluate(()=>writes.length);await page.locator('[data-work-window-save]').click();assert.equal(await page.evaluate(()=>writes.length),before);assert.equal(await page.locator('#edit-worklog-activity').inputValue(),'Lokaler Entwurf');assert.match(await page.locator('#worklog-detail-status').innerText(),/inzwischen geändert/);
   await page.evaluate(()=>cancelWorklogDetail());
  });
  await check('Fresh save includes immutable baseline and only changed form fields',async()=>{
   await page.evaluate(()=>openWorklogDetail('qa_w0'));await page.locator('[data-work-window-edit]').click();await page.locator('#edit-worklog-activity').fill('Kontrolliert geändert');await page.locator('[data-work-window-save]').click();const p=await page.evaluate(()=>writes.at(-1));assert.equal(p._edit_base.notes,'Remote Nachtrag');assert.equal(p._edit_changes.activity,'Kontrolliert geändert');assert.equal(Object.hasOwn(p._edit_changes,'notes'),false);
  });
  await check('Addendum persists on accept; failed save retains input without duplicate append',async()=>{
   await page.evaluate(()=>{openWorklogDetail('qa_w2');document.getElementById('worklog-addendum-text').value='Neuer Nachtrag';document.getElementById('worklog-addendum-add').click();});assert.equal(await page.locator('#worklog-addendum-text').inputValue(),'');assert.match(await page.evaluate(()=>writes.at(-1).notes),/Neuer Nachtrag/);
   await page.evaluate(()=>{failSave=true;document.getElementById('worklog-addendum-text').value='Erhaltener Nachtrag';document.getElementById('worklog-addendum-add').click();document.getElementById('worklog-addendum-add').click();});assert.equal(await page.locator('#worklog-addendum-text').inputValue(),'Erhaltener Nachtrag');assert.equal((await page.locator('#edit-worklog-notes').inputValue()).split('Erhaltener Nachtrag').length-1,1);await page.evaluate(()=>{failSave=false;document.getElementById('worklog-addendum-add').click();});assert.equal(await page.locator('#worklog-addendum-text').inputValue(),'');
  });
  await check('320/360/426px and enlarged text keep paid record identity readable',async()=>{
   await page.evaluate(()=>{openPage('worklogs');document.getElementById('worklog-period').value='all';render();});
   for(const width of [320,360,426])for(const scale of [1,1.5]){
    await page.setViewportSize({width,height:900});await page.evaluate(scale=>document.documentElement.style.fontSize=16*scale+'px',scale);
    const button=page.locator('#worklog-list [data-personal-paid]').first();if(await button.getAttribute('aria-pressed')!=='true')await button.click();const box=await page.locator('#worklog-list .worklog-entry-identity strong').first().boundingBox();assert(box.width>=100,JSON.stringify({width,scale,box}));assert(box.height<100);const menu=await page.locator('#worklog-list .design-record-menu').first().boundingBox(),hours=await page.locator('#worklog-list .worklog-entry-hours').first().boundingBox();assert(menu.y>=hours.y+hours.height||menu.x>=hours.x+hours.width||menu.x+menu.width<=hours.x,'Actions overlap the duration');assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),JSON.stringify(await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,bad:[...document.querySelectorAll('body *')].filter(e=>e.getBoundingClientRect().right>innerWidth+1&&e.getBoundingClientRect().width>0).slice(0,8).map(e=>({tag:e.tagName,id:e.id,cls:e.className,right:e.getBoundingClientRect().right,html:e.outerHTML.slice(0,220)}))}))));
   }
   await page.evaluate(()=>document.documentElement.style.fontSize='16px');await page.setViewportSize({width:320,height:900});await page.screenshot({path:'outputs/access-integrity-320-paid.png'});
  });
  await check('3000 records render 50 work rows without hidden billing rows; billing paginates',async()=>{
   const bench=await page.evaluate(()=>{openPage('worklogs');const times=[];for(let i=0;i<5;i++){const start=performance.now();render();times.push(performance.now()-start);}times.sort((a,b)=>a-b);return{records:worklogs().length,median_ms:times[2],visible_rows:document.querySelectorAll('#worklog-list .worklog-entry').length,hidden_billing_rows:document.querySelectorAll('#billing-group-list .billing-work-row').length,dom_nodes:document.querySelectorAll('*').length};});assert.equal(bench.records,3000);assert.equal(bench.visible_rows,50);assert.equal(bench.hidden_billing_rows,0);assert(bench.dom_nodes<15000);results.push({name:'Synthetic render measurement',result:'MEASURED',...bench});
   await page.evaluate(()=>openPage('billing-page'));assert.equal(await page.locator('.billing-work-row').count(),50);await page.locator('#billing-load-more').click();assert.equal(await page.locator('.billing-work-row').count(),100);
  });
  await check('Owner UI adapter is connected and sharing requires device action',async()=>{
   await page.evaluate(()=>{fixture.master.invoices.push({id:'qa_i',customer_id:'qa_c0',invoice_number:'QA-1',status:'Gestellt',issued_at:'2026-10-18',amount:90});ownerShareRequest={invoice_id:'qa_i',channel:'share'};load();openPage('invoices');});
   assert.match(await page.locator('#invoice-share-handoff p').innerText(),/QA-1/);assert.equal(await page.evaluate(()=>shareHandoffs),0);await page.locator('#invoice-share-handoff button').click();assert.equal(await page.evaluate(()=>shareHandoffs),1);
  });
  await check('No browser runtime errors',async()=>assert.deepEqual(errors,[]));
 }finally{fs.writeFileSync('outputs/access-integrity-tests.json',JSON.stringify({actualBrowser:true,nativeBridge:'synthetic',results},null,2));await browser.close();}
 if(results.some(r=>r.result==='FAIL'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});

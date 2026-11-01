'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs'),fixtureSource=require('./ui-fixture-source.cjs');
const baseline=process.argv.includes('--baseline'),url=pathToFileURL(path.resolve('app/src/main/assets/mobile.html')).href;
const results=[],samples=[],errors=[];
async function measure(browser,setup){
 const page=await browser.newPage({viewport:{width:360,height:840},hasTouch:true});page.setDefaultTimeout(10000);page.setDefaultNavigationTimeout(30000);
 await page.addInitScript({content:fixtureSource('daily-usability.test.cjs')});
 await page.addInitScript(()=>{
  window.qaBootProbe={dom:null,frames:[]};
  document.addEventListener('DOMContentLoaded',()=>qaBootProbe.dom=performance.now(),{once:true});
  function frame(){
   const main=document.querySelector('main.app'),body=document.body;
   const complete=body?.dataset.simpleReady==='true'&&body?.dataset.revierReady==='true'&&(window.HGStartup?document.documentElement.dataset.startup==='ready':!document.documentElement.dataset.startup);
   if(main){const s=getComputedStyle(main);qaBootProbe.frames.push({at:performance.now(),complete,visible:s.display!=='none'&&s.visibility==='visible'&&main.getClientRects().length>0,labels:[...document.querySelectorAll('.bottom .nav')].map(n=>n.textContent.trim())});}
   if(!complete)requestAnimationFrame(frame);else qaBootProbe.ready=performance.now();
  }
  requestAnimationFrame(frame);
 });
 page.on('pageerror',e=>errors.push(e.message));if(setup)await setup(page);
 return page;
}
// Opt-in repeatable scale measurement; never connects to a device or native store.
async function performanceRun(browser){
 const count=40,rows={},bridge={},failures=[];
 const add=(key,n)=>(rows[key]||(rows[key]=[])).push(n);
 for(let sample=0;sample<count;sample++){
  const page=await browser.newPage({viewport:{width:360,height:840}});
  page.on('pageerror',e=>failures.push(e.message));
  await page.addInitScript({content:fixtureSource('daily-usability.test.cjs')});
  await page.addInitScript(seed=>{
   const base=seed.customers[0],work=seed.worklogs[0],order=seed.orders[0];
   const master={...seed,customers:Array.from({length:300},(_,i)=>({...base,id:'qa_customer_'+i,name:'QA Kunde '+i})),orders:Array.from({length:300},(_,i)=>({...order,id:'qa_order_'+i,customer_id:'qa_customer_'+i})),worklogs:Array.from({length:5000},(_,i)=>({...work,id:'qa_work_'+i,customer_id:'qa_customer_'+(i%300),order_id:'qa_order_'+(i%300)})),photos:Array.from({length:3000},(_,i)=>({id:'qa_photo_'+i,customer_id:'qa_customer_'+(i%300),caption:'QA Foto '+i,test_record:true})),invoices:Array.from({length:500},(_,i)=>({id:'qa_invoice_'+i,customer_id:'qa_customer_'+(i%300),invoice_number:'QA-R-'+i,status:'Entwurf',amount:10,test_record:true}))};
   const status={configured:false,syncing:false,pending:0,review:0,drive_role:'backup_only'};
   window.qaPerf={master,counts:{getState:0,getSyncStatus:0,ownerCall:0,getWorkSession:0},durations:{},sizes:{}};
   const measure=(label,fn)=>{const start=performance.now();let raw;try{return raw=fn();}finally{qaPerf.counts[label]++;(qaPerf.durations[label]||=[]).push(performance.now()-start);(qaPerf.sizes[label]||=[]).push(typeof raw==='string'?new TextEncoder().encode(raw).length:0);}};
   const snapshot=()=>({master,pending:[],sync_status:{...status},work_session:{state:'IDLE'}});
   AndroidData.getState=()=>measure('getState',()=>JSON.stringify(snapshot()));
   AndroidData.getSyncStatus=()=>measure('getSyncStatus',()=>{JSON.parse(JSON.stringify(snapshot()));return JSON.stringify(status);});
   AndroidData.getWorkSession=()=>measure('getWorkSession',()=>JSON.stringify({state:'IDLE'}));
   AndroidData.ownerCall=(name,raw)=>measure('ownerCall',()=>{let data={};if(name==='get_revier_overview')data={tour:{stops:[]},due_visits:[],counts:{}};if(name==='get_day_tour')data={stops:[]};if(name==='get_settings')data={settings:{},record_hash:'a'.repeat(64)};if(name==='get_business_settings')data={settings:master.business_settings,record_hash:'a'.repeat(64)};return JSON.stringify({ok:true,data});});
   AndroidData.saveCustomer=raw=>{const row=JSON.parse(raw),id=row.id||'qa_saved';master.customers=master.customers.filter(x=>x.id!==id);master.customers.push({...row,id,test_record:true});return id;};
   window.qaPerfFrame=()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
   window.qaPerfReady=new Promise(resolve=>document.addEventListener('hg:startup-ready',()=>{qaPerf.startup=performance.now();resolve();},{once:true}));
  },require('./synthetic-fixtures.cjs').profile());
  await page.goto(url);await page.evaluate(()=>qaPerfReady);await page.evaluate(()=>qaPerfFrame());
  add('App-Start',await page.evaluate(()=>qaPerf.startup));
  async function step(label,fn){const ms=await page.evaluate(async source=>{const start=performance.now();await (0,eval)('('+source+')')();await qaPerfFrame();return performance.now()-start;},fn.toString());add(label,ms);}
  await step('Heute → Büro',()=>openPage('revier-office'));
  await step('Büro → Heute',()=>openPage('home'));
  await step('Kunden öffnen',()=>openPage('customers'));
  await step('Suche',async()=>{el('customer-search').value='QA Kunde 29';el('customer-search').dispatchEvent(new Event('input',{bubbles:true}));await new Promise(resolve=>setTimeout(resolve,130));});
  await page.evaluate(()=>{el('customer-search').value='';openPage('customer-edit');const f=el('customer-form');f.reset();f.elements.id.value='';f.elements.name.value='QA Gespeichert';f.elements.name.dispatchEvent(new Event('input',{bubbles:true}));});
  await step('Speichern',()=>el('customer-form').requestSubmit());
  assert.equal(await page.evaluate(()=>qaPerf.master.customers.filter(x=>x.name==='QA Gespeichert').length),1);
  const stats=await page.evaluate(()=>({durations:qaPerf.durations,sizes:qaPerf.sizes,counts:qaPerf.counts}));
  for(const [key,values]of Object.entries(stats.durations)){bridge[key]||={durations:[],sizes:[]};bridge[key].durations.push(...values);bridge[key].sizes.push(...stats.sizes[key]);}
  await page.close();
 }
 const summary=values=>{const sorted=values.slice().sort((a,b)=>a-b);return{samples:values.length,mean:values.reduce((a,b)=>a+b,0)/values.length,p95:sorted[Math.ceil(sorted.length*.95)-1],slowest_5_percent_mean:sorted.slice(-Math.max(1,Math.ceil(sorted.length*.05))).reduce((a,b)=>a+b,0)/Math.max(1,Math.ceil(sorted.length*.05))};};
 assert.deepEqual(failures,[]);
 const report={format:'gartenpirat.synthetic-performance.v1',environment:'Cloud Chromium; JavaScript bridge doubles, not Android/SQLite/Pixel',source_commit:require('node:child_process').execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),phase:process.env.HG_PERFORMANCE_PHASE||'before',fixture:{customers:300,worklogs:5000,photos:3000,invoices:500},warmups:0,viewport:{width:360,height:840},timing:'performance.now; action to second animation frame; search includes 130ms debounce wait; startup navigation to hg:startup-ready; fresh page per sample, warm browser asset cache',operations:Object.fromEntries(Object.entries(rows).map(([k,v])=>[k,summary(v)])),bridge:Object.fromEntries(Object.entries(bridge).map(([k,v])=>[k,{duration_ms:summary(v.durations),response_bytes:summary(v.sizes)}])),pixel:'NOT_EXECUTED'};
 fs.writeFileSync(process.env.HG_PERFORMANCE_REPORT,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(report));
}
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 if(process.env.HG_PERFORMANCE_REPORT){try{await performanceRun(browser);}finally{await browser.close();}return;}
 async function check(name,task){try{await task();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.stack});}console.log(results.at(-1));}
 try{
  await check('Five cold bundled-page starts use the same synthetic fixture and record every visible intermediate frame',async()=>{
   for(let i=0;i<5;i++){
    const page=await measure(browser);try{await page.goto(url);await page.waitForFunction(()=>qaBootProbe.ready>0);const data=await page.evaluate(()=>({navigation_to_ready_ms:qaBootProbe.ready,dom_to_ready_ms:qaBootProbe.ready-qaBootProbe.dom,visible_unready_frames:qaBootProbe.frames.filter(f=>f.visible&&!f.complete),first_visible_frame:qaBootProbe.frames.find(f=>f.visible),writes:writes.length,drafts:HGFormDrafts.list().length,styles:[...document.styleSheets].map(s=>s.href||'inline')}));samples.push(data);assert.equal(data.writes,0);assert.equal(data.drafts,0);if(!baseline){assert.equal(data.visible_unready_frames.length,0);assert.deepEqual(data.first_visible_frame.labels,['Heute','Kunden','Büro']);assert.equal(await page.locator('#app-startup-status').isVisible(),false);}}finally{await page.close();}
   }
  });
  if(!baseline){
   await check('Performance diagnostics retain only bounded numeric samples without persisting content',async()=>{
    const page=await measure(browser);try{
     await page.goto(url);await page.waitForFunction(()=>document.documentElement.dataset.startup==='ready');
     const result=await page.evaluate(()=>{const before=HGDraftStorage.getItem('mobile-state');for(let i=0;i<140;i++)render();const snapshot=HGPerformance.snapshot();snapshot.render[0]=-1;return{snapshot:HGPerformance.snapshot(),sameDraftStorage:before===HGDraftStorage.getItem('mobile-state'),writes:writes.length};});
     assert.equal(result.snapshot.render.length,128);assert(result.sameDraftStorage);assert.equal(result.writes,0);
     for(const [key,rows]of Object.entries(result.snapshot)){assert(['app-start','page-change','render'].includes(key));assert(rows.every(n=>typeof n==='number'&&n>=0));}
    }finally{await page.close();}
   });
   await check('Offline operation and missing backup configuration do not block presentation or write business rows',async()=>{
    const page=await measure(browser,async page=>{await page.context().setOffline(true);await page.addInitScript(()=>{AndroidData.getSyncStatus=()=>JSON.stringify({configured:false,drive_role:'backup_only',business_sync_allowed:false});});});
    try{await page.goto(url);await page.waitForFunction(()=>document.documentElement.dataset.startup==='ready');assert.equal(await page.evaluate(()=>writes.length),0);assert.deepEqual(await page.locator('.bottom .nav').allTextContents(),['Heute','Kunden','Büro']);}finally{await page.close();}
   });
   await check('A local read error remains visible in the finished UI without becoming a startup or Drive gate',async()=>{
    const page=await measure(browser,async page=>{await page.addInitScript(()=>{AndroidData.getState=()=>{throw Error('QA local read unavailable')};AndroidData.getSyncStatus=()=>{throw Error('QA backup status unavailable')};});});
    try{await page.goto(url);await page.waitForFunction(()=>document.documentElement.dataset.startup==='ready');assert.equal(await page.evaluate(()=>hgDataReadError),true);assert.equal(await page.evaluate(()=>writes.length),0);}finally{await page.close();}
   });
   await check('A retained work draft survives restart and conscious recovery from a stylesheet error with the same edit ID',async()=>{
    const page=await measure(browser);try{
     await page.goto(url);await page.waitForFunction(()=>document.documentElement.dataset.startup==='ready');await page.evaluate(()=>openPage('work-entry'));await page.locator('#activity').fill('QA erhaltener Startentwurf');const id=await page.evaluate(()=>HGFormDrafts.list('work_entry')[0].session_id);
     await page.route('**/field-ui.css',r=>r.abort());await page.reload();await page.waitForFunction(()=>document.documentElement.dataset.startup==='error');assert(await page.evaluate(id=>HGFormDrafts.list('work_entry').some(d=>d.session_id===id),id));
     if(await page.locator('dialog[open]').count()){await page.evaluate(()=>document.getElementById('app-startup-retry').click());assert.match(await page.locator('#app-startup-message').textContent(),/offenen Dialog/);await page.locator('#field-entry-later').click();}
     await page.unroute('**/field-ui.css');page.once('dialog',d=>d.accept());await Promise.all([page.waitForEvent('load',{timeout:30000}),page.locator('#app-startup-retry').click()]);await page.waitForFunction(()=>document.documentElement.dataset.startup==='ready');
     assert.equal(await page.locator('#activity').inputValue(),'QA erhaltener Startentwurf');assert(await page.evaluate(id=>HGFormDrafts.list('work_entry').some(d=>d.session_id===id),id));assert.equal(await page.evaluate(()=>writes.length),0);
    }finally{await page.close();}
   });
   await check('An initial modal remains operable and no partially initialized page is exposed while waiting for it',async()=>{
    const page=await measure(browser,async page=>{await page.addInitScript(()=>document.addEventListener('DOMContentLoaded',()=>setTimeout(()=>{const d=document.createElement('dialog');d.id='qa-start-dialog';d.innerHTML='<p>QA Startdialog</p><button type="button">Weiter</button>';d.querySelector('button').onclick=()=>d.close();document.body.append(d);d.showModal();},0),{once:true}));});
    try{await page.goto(url);await page.waitForSelector('#qa-start-dialog[open]');assert(await page.locator('#qa-start-dialog').isVisible());assert.equal(await page.locator('main.app').isVisible(),false);await page.locator('#qa-start-dialog button').click();await page.waitForFunction(()=>document.documentElement.dataset.startup==='ready');assert.equal(await page.evaluate(()=>writes.length),0);}finally{await page.close();}
   });
   await check('A delayed module can finish after the slow-start notice without reload, fallback or business writes',async()=>{
    let release,requests=0;const page=await measure(browser,async page=>{await page.route('**/revier-solo-ui.js',async route=>{await new Promise(resolve=>release=resolve);await route.fallback();});page.on('request',r=>{if(r.url().endsWith('/mobile.html'))requests++;});});
    const navigation=page.goto(url);try{await page.locator('#app-startup-status').waitFor({state:'visible'});await page.waitForFunction(()=>document.getElementById('app-startup-message').textContent.includes('startet noch'),{},{timeout:12000});assert.equal(await page.locator('main.app').isVisible(),false);assert(await page.locator('#app-startup-retry').isVisible());release();await navigation;await page.waitForFunction(()=>document.documentElement.dataset.startup==='ready');assert.equal(requests,1);assert.equal(await page.evaluate(()=>writes.length),0);}finally{release?.();await navigation.catch(()=>{});await page.close();}
   });
   for(const asset of ['simple-forms.js','field-ui.css','startup-ui.js','modern-ui.css','equipment-photo-ui.js'])await check('Missing '+asset+' keeps the legacy view inaccessible and offers conscious recovery',async()=>{
    let count=0;const page=await measure(browser,async page=>{await page.route('**/'+asset,r=>r.abort());page.on('request',r=>{if(r.url().endsWith('/mobile.html'))count++;});});
    try{await page.goto(url);await page.waitForFunction(()=>document.documentElement.dataset.startup==='error');assert.equal(await page.locator('main.app').isVisible(),false);assert(await page.locator('#app-startup-retry').isVisible());assert.equal(await page.evaluate(()=>writes.length),0);await page.waitForTimeout(100);assert.equal(count,1);if(asset!=='startup-ui.js'){page.once('dialog',d=>d.dismiss());await page.locator('#app-startup-retry').click();assert.equal(count,1);}}finally{await page.close();}
   });
   await check('A critical initializer exception fails visibly without showing the old controls',async()=>{
    const page=await measure(browser,async page=>{await page.route('**/revier-solo-ui.js',r=>r.fulfill({contentType:'application/javascript',body:'throw new Error("QA expected startup initializer failure")'}));});
    try{await page.goto(url);await page.waitForFunction(()=>document.documentElement.dataset.startup==='error');assert.equal(await page.locator('main.app').isVisible(),false);assert(await page.locator('#app-startup-retry').isVisible());assert.equal(await page.evaluate(()=>writes.length),0);}finally{await page.close();}
   });
  }
 }finally{await browser.close();}
 const artifact={classification:'SYNTHETIC_TEST',scope:'Cloud Chromium bundled-page timing only; no native/Pixel measurement',base_source_commit:'9f3112ec8ade7796b442840ec169606ac83619bc',mode:baseline?'before':'after',samples,results,errors};
 fs.mkdirSync('outputs',{recursive:true});fs.writeFileSync(baseline?'outputs/startup-baseline-results.json':'outputs/startup-after-results.json',JSON.stringify(artifact,null,2)+'\n');
 if(results.some(r=>r.result==='FAIL'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1});

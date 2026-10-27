'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs'),fixtureSource=require('./ui-fixture-source.cjs');
const baseline=process.argv.includes('--baseline'),url=pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href;
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
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 async function check(name,task){try{await task();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.stack});}console.log(results.at(-1));}
 try{
  await check('Five cold bundled-page starts use the same synthetic fixture and record every visible intermediate frame',async()=>{
   for(let i=0;i<5;i++){
    const page=await measure(browser);try{await page.goto(url);await page.waitForFunction(()=>qaBootProbe.ready>0);const data=await page.evaluate(()=>({navigation_to_ready_ms:qaBootProbe.ready,dom_to_ready_ms:qaBootProbe.ready-qaBootProbe.dom,visible_unready_frames:qaBootProbe.frames.filter(f=>f.visible&&!f.complete),first_visible_frame:qaBootProbe.frames.find(f=>f.visible),writes:writes.length,drafts:HGFormDrafts.list().length,styles:[...document.styleSheets].map(s=>s.href||'inline')}));samples.push(data);assert.equal(data.writes,0);assert.equal(data.drafts,0);if(!baseline){assert.equal(data.visible_unready_frames.length,0);assert.deepEqual(data.first_visible_frame.labels,['Heute','Kunden','Büro']);const before=JSON.parse(fs.readFileSync('docs/verification/startup-before-2026-10-27.json','utf8')).samples[0];assert.deepEqual(data.styles.filter(s=>s!=='inline'),before.styles.filter(s=>s!=='inline'));assert.equal(await page.locator('#app-startup-status').isVisible(),false);}}finally{await page.close();}
   }
  });
  if(!baseline){
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
   for(const asset of ['simple-forms.js','field-ui.css','startup-ui.js'])await check('Missing '+asset+' keeps the legacy view inaccessible and offers conscious recovery',async()=>{
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
 fs.writeFileSync(baseline?'docs/verification/startup-before-2026-10-27.json':'outputs/startup-after-results.json',JSON.stringify(artifact,null,2)+'\n');
 if(results.some(r=>r.result==='FAIL'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1});

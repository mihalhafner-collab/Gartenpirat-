'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs');
(async()=>{
 const results=[],browser=await chromium.launch({headless:true,channel:'chrome'});
 const check=async(name,test)=>{try{await test();results.push({name,result:'PASS'});}catch(error){results.push({name,result:'FAIL',error:error.message});}console.log(results.at(-1));};
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},isMobile:true,hasTouch:true}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{
   const master={customers:[{id:'qa_c1',name:'Kunde Muster',city:'QA Beispielort',active:1}],orders:[],invoices:[],photos:[],worklogs:[]};
   window.removalFixture={master,pending:[]};window.speechCalls=0;window.syncCalls=0;window.backupCalls=0;window.writeCalls=0;
   window.AndroidData={openFullBackup:()=>backupCalls++,getState:()=>JSON.stringify(removalFixture),getSyncStatus:()=>JSON.stringify({drive_role:'backup_only',storage_contract_version:3,configured:false,pending:0}),getAppInfo:()=>JSON.stringify({versionName:'1.38.1'}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),synchronize:()=>{syncCalls++;},saveWorklog:()=>{writeCalls++;throw new Error('No write permitted in removal smoke test');}};
   window.AndroidSpeech={start:()=>{speechCalls++;}};
  });
  await check('Legacy voice shortcut stays retired; the requested botanical mascot is packaged',async()=>{
   assert(!/id=["']pirate-speak["']/.test(fs.readFileSync('android/app/src/main/assets/mobile.html','utf8')));
   assert(fs.existsSync('android/app/src/main/assets/gartenpirat.png'));
   assert(fs.existsSync('android/app/src/main/assets/gartenpirat.js'));
   assert(fs.existsSync('android/app/src/main/assets/hafner-logo.png'));
  });
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);
  await page.waitForFunction(()=>document.body.dataset.fieldReady==='true'&&document.body.dataset.todayReady==='true'&&document.body.dataset.revierReady==='true');
  await check('App initializes fully without removed-element event listener failures',async()=>{
   assert.deepEqual(errors,[]);assert.equal(await page.locator('.pirate,#pirate-speak').count(),0);
   assert.deepEqual(await page.locator('.bottom .nav').allTextContents(),['Heute','Büro']);
   assert.equal(await page.evaluate(()=>typeof window.onDriveSync),'function');
   assert.equal(await page.evaluate(()=>typeof window.acceptNativeSpeech),'function');
  });
  await check('Start timer and manual work entry remain reachable',async()=>{
   assert.equal(await page.locator('[data-session-action="start"]').count(),1);
   await page.locator('#home .quick').click();assert.equal(await page.evaluate(()=>currentPage),'work-entry');
   assert.equal(await page.locator('#entry').isVisible(),true);
  });
  await check('Normal speech button and native speech parsing remain operational',async()=>{
   await page.locator('#entry [data-section=notes] summary').click();await page.locator('#speak').click();assert.equal(await page.evaluate(()=>speechCalls),1);
   await page.evaluate(()=>acceptNativeSpeech('Heute Kunde Muster von 8:00 bis 12:00 30 Minuten Pause Tätigkeit Hecke schneiden Material drei Säcke Mulch, 15 km'));
   assert.equal(await page.locator('#customer').inputValue(),'qa_c1');
   assert.equal(await page.locator('#start').inputValue(),'08:00');assert.equal(await page.locator('#end').inputValue(),'12:00');
   assert.equal(await page.locator('#pause').inputValue(),'30');assert.equal(await page.evaluate(()=>calculateHours()),3.5);
   assert.equal(await page.locator('#entry-kilometers').inputValue(),'15');assert.match(await page.locator('#activity').inputValue(),/Hecke schneiden/);
  });
  await check('Full-backup entry and compatibility callback remain safe after shortcut removal',async()=>{
   await page.evaluate(()=>openPage('sync-page'));await page.locator('#sync').click();
   assert.equal(await page.evaluate(()=>syncCalls),0);assert.equal(await page.evaluate(()=>backupCalls),1);
   await page.evaluate(()=>onDriveSync({ok:true,state:removalFixture,sync:{configured:true,pending:0,last_sync:'2026-10-12T18:00:00+02:00'}}));
assert.match(await page.locator('#sync-status').textContent(),/Sicherungsstatus offen/);assert.match(await page.locator('#sync-detail').textContent(),/nicht bestätigt/);
   assert.deepEqual(errors,[]);assert.equal(await page.evaluate(()=>writeCalls),0);
  });
  await check('Home remains clear at 360 and 412 pixels; current release screenshot captured',async()=>{
   await page.evaluate(()=>openPage('home'));
   for(const width of [360,412]){await page.setViewportSize({width,height:915});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));}
   await page.screenshot({path:'outputs/Gartenpirat-entfernt-v1.38.1-Heute.png'});
   assert.deepEqual(errors,[]);
  });
 }finally{await browser.close();}
 fs.writeFileSync('outputs/Gartenpirat-entfernt-v1.38.1-Tests.json',JSON.stringify({actualBrowser:true,nativeBridge:'isolated synthetic fixture',productiveWrites:0,results},null,2));
 if(results.some(row=>row.result==='FAIL'))process.exitCode=1;
})().catch(error=>{console.error(error);process.exitCode=1;});

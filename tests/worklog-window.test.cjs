/* Actual Chromium + actual mobile assets, isolated in-memory Java bridge fixture.
 * No native-device, Drive, or productive CRUD claim is made by this suite. */
'use strict';
const assert=require('node:assert/strict');
const path=require('node:path');
const fs=require('node:fs');
const {pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs');
const results=[];
async function main(){
 const browser=await chromium.launch({headless:true,channel:'chrome'});
 try{
  const page=await browser.newPage({viewport:{width:426,height:852}}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{
   const master={customers:[{id:'qa_cus_test',name:'Testkunde',active:1}],orders:[],worklogs:[{id:'qa_wrk_test',customer_id:'qa_cus_test',work_date:'2026-10-10',hours:3,activity:'Hecke pflegen',break_minutes:0,kilometers:0,billed:0,customer_visible:1},{id:'qa_wrk_second',customer_id:'qa_cus_test',work_date:'2026-10-09',hours:2,activity:'Rasen pflegen',break_minutes:0,kilometers:0,billed:0,customer_visible:1}],photos:[],invoices:[]},pending=[];
   window.testWrites=0;
   window.AndroidData={getState:()=>JSON.stringify({master,pending}),getSyncStatus:()=>JSON.stringify({configured:false,pending:pending.length}),saveWorklog:raw=>{const payload=JSON.parse(raw);if(!payload.activity)return 'ERROR: Tätigkeit fehlt.';window.testWrites++;pending.push({client_id:'test_action_'+window.testWrites,kind:'worklog',payload});return payload.id;}};
   window.AndroidSpeech={start:()=>{}};
  });
  await page.goto(pathToFileURL(path.resolve(__dirname,'../android/app/src/main/assets/mobile.html')).href);
  await page.waitForFunction(()=>typeof openWorklogDetail==='function');
  async function check(name,fn){try{await fn();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.message});}console.log(results.at(-1).result+' '+name+(results.at(-1).error?' '+results.at(-1).error:''));}
  await check('Deferred script initialises after dynamically created worklog page',async()=>{
   await page.locator('.bottom [data-open="more-page"]').click();await page.locator('#more-page [data-open="worklogs"]').click();
   await page.locator('#worklog-list [data-edit-worklog="qa_wrk_test"]').first().click();
   assert.equal(await page.locator('#worklog-detail').getAttribute('data-work-mode'),'overview');
   assert.equal(await page.locator('.worklog-window-summary dl > div').count(),12);
   assert.equal(await page.locator('.worklog-window-actions').count(),1);
   assert.equal(await page.evaluate(()=>testWrites),0);
  });
  await check('Read, edit, overview and list: each Back moves exactly one step',async()=>{
   await page.locator('[data-work-window-edit]').click();assert.equal(await page.locator('#worklog-detail').getAttribute('data-work-mode'),'edit');
   await page.evaluate(()=>handleAndroidBack());assert.equal(await page.locator('#worklog-detail').getAttribute('data-work-mode'),'overview');
   await page.evaluate(()=>handleAndroidBack());assert.equal(await page.evaluate(()=>currentPage),'worklogs');
   assert.equal(await page.evaluate(()=>testWrites),0);
  });
  await check('Explicit reachable footer save preserves worklog ID and relation',async()=>{
   await page.evaluate(()=>openWorklogDetail('qa_wrk_test'));await page.locator('[data-work-window-edit]').click();
   await page.locator('#edit-worklog-activity').fill('Testnachtrag');await page.locator('[data-work-window-save]').click();
   assert.equal(await page.locator('#worklog-detail').getAttribute('data-work-mode'),'overview');
   const data=await page.evaluate(()=>JSON.parse(AndroidData.getState()).pending.at(-1).payload);
   assert.equal(data.id,'qa_wrk_test');assert.equal(data.customer_id,'qa_cus_test');assert.equal(data.activity,'Testnachtrag');
   assert((await page.locator('.worklog-window-summary').innerText()).includes('Testnachtrag'));
  });
  await check('Back saves through the existing channel without committing while typing',async()=>{
   await page.locator('[data-work-window-edit]').click();await page.locator('#edit-worklog-activity').fill('Automatisch gesichert');
   await page.evaluate(()=>handleAndroidBack());assert.equal(await page.locator('#worklog-detail').getAttribute('data-work-mode'),'overview');
   assert.equal(await page.evaluate(()=>JSON.parse(AndroidData.getState()).pending.at(-1).payload.activity),'Automatisch gesichert');
  });
  await check('Invalid input prevents leaving edit mode and displays useful error',async()=>{
   await page.locator('[data-work-window-edit]').click();const writes=await page.evaluate(()=>testWrites);
   await page.locator('#edit-worklog-activity').fill('');await page.locator('[data-work-window-save]').click();
   assert.equal(await page.locator('#worklog-detail').getAttribute('data-work-mode'),'edit');assert.equal(await page.evaluate(()=>testWrites),writes);
   assert((await page.locator('#worklog-detail-status').innerText()).includes('Tätigkeit'));
   await page.locator('#edit-worklog-activity').fill('Automatisch gesichert');await page.locator('[data-work-window-save]').click();
  });
  await check('Footer remains reachable without overflow at 360px and 426px',async()=>{
   for(const width of [360,426]){await page.setViewportSize({width,height:800});
    const r=await page.locator('[data-work-window-edit]').boundingBox();assert(r.height>=48);assert(r.x>=0&&r.x+r.width<=width&&r.y+r.height<=800);
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
   }
  });
  await check('No browser runtime errors',async()=>assert.deepEqual(errors,[]));
  await check('Separate detail pages keep record selection hidden and save the selected ID',async()=>{
   await page.evaluate(()=>handleAndroidBack());await page.evaluate(()=>openWorklogDetail('qa_wrk_second'));await page.locator('[data-work-window-edit]').click();
   assert.equal(await page.locator('#edit-worklog-select').isVisible(),false);
   assert.equal(await page.locator('.select-search[data-for="edit-worklog-select"]').isVisible(),false);
   assert.equal(await page.locator('#edit-worklog-activity').inputValue(),'Rasen pflegen');
   await page.locator('#edit-worklog-activity').fill('Zweiter Testnachtrag');await page.locator('[data-work-window-save]').click();
   const last=await page.evaluate(()=>JSON.parse(AndroidData.getState()).pending.at(-1).payload);
   assert.equal(last.id,'qa_wrk_second');assert.equal(last.activity,'Zweiter Testnachtrag');
   const bad=await page.evaluate(()=>JSON.parse(AndroidData.getState()).pending.some(a=>a.payload.id==='qa_wrk_test'&&a.payload.activity==='Rasen pflegen'));
   assert.equal(bad,false,'Record selection must not overwrite another worklog ID');
  });
  fs.writeFileSync(path.resolve(__dirname,'../outputs/Worklog-Window-Tests.json'),JSON.stringify({actualBrowser:true,nativeBridge:'isolated in-memory fixture',results},null,2));
 }finally{await browser.close();}
 console.log('RESULT '+results.filter(r=>r.result==='PASS').length+' passed, '+results.filter(r=>r.result==='FAIL').length+' failed');
 if(results.some(r=>r.result==='FAIL'))process.exitCode=1;
}
main().catch(e=>{console.error(e);process.exitCode=1;});

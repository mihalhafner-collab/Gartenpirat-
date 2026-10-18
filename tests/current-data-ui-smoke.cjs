/* Read-only browser check with the bundled current data, never accesses Drive or native storage. */
'use strict';
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs');
(async()=>{
 const master=JSON.parse(fs.readFileSync('android/app/src/main/assets/initial-data.json','utf8'));
 const browser=await chromium.launch({headless:true,channel:'chrome'});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(master=>{const state={master,pending:[]};window.AndroidData={getState:()=>JSON.stringify(state),getSyncStatus:()=>JSON.stringify({configured:false}),getAppInfo:()=>JSON.stringify({versionName:'1.33.0'}),getWorkSession:()=>JSON.stringify({state:'IDLE'})};window.AndroidSpeech={start:()=>{}};},master);
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);
  await page.waitForSelector('#design-metrics',{timeout:30000});
  const first=master.customers.find(c=>c.id);assert(first);
  await page.evaluate(id=>openCustomer(id),first.id);assert.equal(await page.evaluate(()=>currentPage),'customer-profile-page');
  await page.evaluate(()=>openPage('worklogs'));assert(await page.locator('.worklog-entry').count()>0);
  assert.deepEqual(errors,[]);
  fs.writeFileSync('outputs/Current-Data-UI-v1.33.json',JSON.stringify({readOnly:true,dataSource:'bundled initial-data.json',browser:true,native:false,result:'PASS',checks:['dashboard metrics','customer profile','worklog list','no runtime errors']},null,2));
  console.log('PASS readonly current-data dashboard/profile/worklogs/runtime checks');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});

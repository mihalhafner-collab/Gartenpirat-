'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'chrome'}),results=[];
 const check=async(name,fn)=>{await fn();results.push({name,result:'PASS'});console.log(name+' PASS');};
 try{
  const page=await browser.newPage({viewport:{width:412,height:915}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{
   window.fixture={customers:[{id:'c1',name:'Synthetischer Kunde',active:1}],orders:[],invoices:[],photos:[],worklogs:[{id:'w1',customer_id:'c1',work_date:'2026-10-18',activity:'Synthetische Gartenarbeit',hours:2,billed:0}]};
   window.writes=[];window.AndroidData={getState:()=>JSON.stringify({master:fixture,pending:[]}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),getSyncStatus:()=>JSON.stringify({configured:false}),getAppInfo:()=>JSON.stringify({versionName:'1.46.1'}),saveWorklog:()=>writes.push('worklog'),saveInvoice:()=>writes.push('invoice'),synchronize:()=>writes.push('sync')};
   window.AndroidPhone={dial:()=>{}};window.AndroidSpeech={start:()=>{}};
  });
  const assets=path.resolve(process.env.HG_TEST_APP_ASSETS||'android/app/src/main/assets');
  const load=async()=>{await page.goto(pathToFileURL(path.join(assets,'mobile.html')).href);await page.waitForSelector('#workday-recent [data-personal-paid]');};
  await load();const before=await page.evaluate(()=>JSON.stringify(fixture));
  await check('Saved work is personally marked and Today shows bezahlt without a business write',async()=>{
   await page.locator('#workday-recent [data-personal-paid]').click();
   assert.equal(await page.locator('#workday-recent .personal-payment-label').innerText(),'bezahlt');
   await page.evaluate(()=>openPage('worklogs'));
   assert.equal(await page.locator('#worklog-list [data-personal-paid]').getAttribute('aria-pressed'),'true');
   assert.equal(await page.evaluate(()=>JSON.stringify(fixture)),before);assert.deepEqual(await page.evaluate(()=>writes),[]);
   await page.setViewportSize({width:320,height:650});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
   await page.setViewportSize({width:412,height:915});await page.evaluate(()=>openPage('home'));await page.screenshot({path:'outputs/Heute-bb-candidate.png'});
  });
  await check('Personal mark survives reload and is removable from saved work',async()=>{
   await load();assert.equal(await page.locator('#workday-recent .personal-payment-label').innerText(),'bezahlt');
   await page.evaluate(()=>openPage('worklogs'));await page.locator('#worklog-list [data-personal-paid]').click();
   assert.equal(await page.locator('#workday-recent .personal-payment-label').count(),0);
   assert.equal(await page.evaluate(()=>JSON.stringify(fixture)),before);assert.deepEqual(await page.evaluate(()=>writes),[]);
  });
  await check('Storage failure keeps the previous mark and reports that saving failed',async()=>{
   await page.evaluate(()=>{const original=Storage.prototype.setItem;Storage.prototype.setItem=function(k,v){if(k==='hausgarten.overview.personal-paid.v1')throw Error('Synthetic quota failure');return original.call(this,k,v);};});
   await page.locator('#worklog-list [data-personal-paid]').click();
   assert.equal(await page.locator('#worklog-list [data-personal-paid]').getAttribute('aria-pressed'),'false');
   assert((await page.locator('#mobile-toast').innerText()).includes('nicht gespeichert'));
  });
  await check('Navigation clears old input focus and preserves a work draft; entry has no payment control',async()=>{
   await page.evaluate(()=>openPage('work-entry'));await page.locator('#activity').fill('Entwurf behalten');await page.locator('#activity').focus();
   assert.equal(await page.locator('#work-entry [data-personal-paid]').count(),0);
   await page.evaluate(()=>openPage('home'));assert.notEqual(await page.evaluate(()=>document.activeElement.id),'activity');
   await page.evaluate(()=>openPage('work-entry'));assert.equal(await page.locator('#activity').inputValue(),'Entwurf behalten');
   assert.equal(await page.evaluate(()=>JSON.stringify(fixture)),before);assert.deepEqual(await page.evaluate(()=>writes),[]);assert.deepEqual(errors,[]);
  });
 }finally{fs.mkdirSync('outputs',{recursive:true});fs.writeFileSync('outputs/personal-payment-tests.json',JSON.stringify({results},null,2));await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});

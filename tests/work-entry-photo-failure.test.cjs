'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs');

(async()=>{
 const results=[];const browser=await chromium.launch({headless:true,channel:'chrome'});
 const check=async(name,task)=>{try{await task();results.push({name,result:'PASS'});}catch(error){results.push({name,result:'FAIL',error:error.stack});}console.log(results.at(-1));};
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},isMobile:true,hasTouch:true}),errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{
   if(!sessionStorage.getItem('qa.work.photo.initialized')){for(const key of ['qa.work.photo.state','qa.work.photo.workWrites','qa.work.photo.photoWrites','qa.work.photo.staged'])localStorage.removeItem(key);sessionStorage.setItem('qa.work.photo.initialized','1');}
   const master={customers:[{id:'qa_c1',name:'Fotokunde',active:1}],orders:[],worklogs:[],photos:[],invoices:[]},pending=JSON.parse(localStorage.getItem('qa.work.photo.state')||'[]');
   window.workWrites=JSON.parse(localStorage.getItem('qa.work.photo.workWrites')||'[]');window.photoWrites=JSON.parse(localStorage.getItem('qa.work.photo.photoWrites')||'[]');
   const persist=()=>{localStorage.setItem('qa.work.photo.state',JSON.stringify(pending));localStorage.setItem('qa.work.photo.workWrites',JSON.stringify(workWrites));localStorage.setItem('qa.work.photo.photoWrites',JSON.stringify(photoWrites));};
   window.AndroidData={
    getState:()=>JSON.stringify({master,pending}),getSyncStatus:()=>JSON.stringify({configured:false,pending:pending.length}),getAppInfo:()=>JSON.stringify({versionName:'1.45.0'}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),
    saveWorklog:raw=>{const row={...JSON.parse(raw),id:'qa_w1'};workWrites.push(row);pending.push({kind:'worklog',payload:row});persist();return 'qa_w1';},
    stagePendingWorkPhoto:raw=>{const row=JSON.parse(raw);localStorage.setItem('qa.work.photo.staged',JSON.stringify(row));return JSON.stringify(row);},
    getPendingWorkPhoto:()=>localStorage.getItem('qa.work.photo.staged')||'',
    clearPendingWorkPhoto:()=>{localStorage.removeItem('qa.work.photo.staged');return 'OK';},
    savePhoto:raw=>{const row=JSON.parse(raw);photoWrites.push(row);if(photoWrites.length===1){persist();return'ERROR: Isolierter Foto-Speicherfehler'}pending.push({kind:'photo',payload:row});persist();return row.id;},synchronize:()=>{}
   };
   window.AndroidSpeech={start:()=>{}};window.AndroidPhone={dial:()=>{},openMaps:()=>{}};
  });
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href,{waitUntil:'domcontentloaded',timeout:30000});
  await page.waitForSelector('#design-metrics');
  await check('A photo failure after a successful work save preserves a retry without duplicating the work record',async()=>{
   await page.evaluate(()=>openPage('work-entry'));
   await page.locator('#customer').selectOption('qa_c1');
   await page.locator('#entry-hours').fill('1.5');
   await page.locator('#activity').fill('Hecke schneiden');
   await page.locator('#entry [data-section="notes"] > summary').click();
   await page.locator('#photo').setInputFiles({name:'hecke.png',mimeType:'image/png',buffer:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO/a00sAAAAASUVORK5CYII=','base64')});
   await page.locator('#entry button[type="submit"]').click();
   await page.locator('#work-photo-recovery:not([hidden])').waitFor();
   assert.equal(await page.evaluate(()=>currentPage),'work-entry');
   assert.equal(await page.evaluate(()=>workWrites.length),1);
   assert.equal(await page.evaluate(()=>photoWrites.length),1);
   assert.match(await page.locator('#mobile-toast').innerText(),/Arbeitsaufzeichnung gespeichert.*Foto ist noch nicht gespeichert/i);
   assert.equal(await page.locator('#activity').inputValue(),'Hecke schneiden');
   assert.equal(await page.locator('#photo').evaluate(input=>input.files.length),1);
   assert.equal(await page.locator('#entry button[type="submit"]').isDisabled(),true);
   assert.equal(await page.evaluate(()=>Boolean(localStorage.getItem('qa.work.photo.staged'))),true);
   await page.reload({waitUntil:'domcontentloaded',timeout:30000});
   await page.locator('#work-photo-recovery:not([hidden])').waitFor();
   assert.equal(await page.evaluate(()=>currentPage),'work-entry');
   assert.match(await page.locator('#work-photo-recovery').innerText(),/Neustart wiederhergestellt/i);
   assert.equal(await page.evaluate(()=>workWrites.length),1);
   assert.equal(await page.evaluate(()=>photoWrites.length),1);
   await page.locator('#retry-work-photo').click();
   await page.waitForFunction(()=>currentPage==='home');
   assert.equal(await page.evaluate(()=>workWrites.length),1);
   assert.equal(await page.evaluate(()=>photoWrites.length),2);
   assert.equal(await page.locator('#activity').inputValue(),'');
   assert.equal(await page.locator('#entry details.more[open]').count(),0);
   assert.equal(await page.locator('#work-photo-recovery').isHidden(),true);
   assert.equal(await page.evaluate(()=>localStorage.getItem('qa.work.photo.staged')),null);
  });
  await check('The failed photo was linked to the one successfully stored work record',async()=>{
   const saved=await page.evaluate(()=>photoWrites);assert.equal(saved.length,2);for(const payload of saved){assert.equal(payload.worklog_id,'qa_w1');assert.equal(payload.customer_id,'qa_c1');assert.equal(payload.role,'documentation');assert.equal(payload.mime_type,'image/jpeg');}
  });
  await check('No runtime errors occurred',()=>assert.deepEqual(errors,[]));
 }finally{await Promise.race([browser.close(),new Promise(resolve=>setTimeout(resolve,5000))]);}
 fs.writeFileSync('outputs/Work-entry-photo-failure-tests.json',JSON.stringify({browser:true,nativeBridge:'isolated synthetic fixture',results},null,2));
 if(results.some(row=>row.result==='FAIL'))process.exitCode=1;
})().then(()=>process.exit(process.exitCode||0)).catch(error=>{console.error(error);process.exit(1);});

'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs');

(async()=>{
 const results=[];let mediaKey='';const browser=await chromium.launch({headless:true,channel:'chrome'});
 const check=async(name,task)=>{try{await task();results.push({name,result:'PASS'});}catch(error){results.push({name,result:'FAIL',error:error.stack});}console.log(results.at(-1));};
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},isMobile:true,hasTouch:true}),errors=[];page.setDefaultTimeout(10000);
  page.on('pageerror',error=>errors.push(error.message));page.on('dialog',dialog=>dialog.accept());
  await page.addInitScript(()=>{
   if(!sessionStorage.getItem('qa.work.photo.initialized')){for(const key of ['qa.work.photo.state','qa.work.photo.workWrites','qa.work.photo.photoWrites','qa.work.photo.staged'])localStorage.removeItem(key);sessionStorage.setItem('qa.work.photo.initialized','1');}
   const master={customers:[{id:'qa_c1',name:'Fotokunde',active:1},{id:'qa_c2',name:'QA Rechnungsempfänger',active:1}],orders:[{id:'qa_o1',customer_id:'qa_c1',title:'QA Auftrag',status:'Offen',billing_customer_id:'qa_c2'}],worklogs:[],photos:[],invoices:[]},pending=JSON.parse(localStorage.getItem('qa.work.photo.state')||'[]');
   window.workWrites=JSON.parse(localStorage.getItem('qa.work.photo.workWrites')||'[]');window.photoWrites=JSON.parse(localStorage.getItem('qa.work.photo.photoWrites')||'[]');
   const persist=()=>{localStorage.setItem('qa.work.photo.state',JSON.stringify(pending));localStorage.setItem('qa.work.photo.workWrites',JSON.stringify(workWrites));localStorage.setItem('qa.work.photo.photoWrites',JSON.stringify(photoWrites));};
   window.AndroidData={
    getState:()=>JSON.stringify({master,pending}),getSyncStatus:()=>JSON.stringify({configured:false,pending:pending.length}),getAppInfo:()=>JSON.stringify({versionName:'1.45.0'}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),
    saveWorklog:raw=>{const args=JSON.parse(raw),existing=workWrites.find(row=>row._request_id===args._request_id);if(existing)return existing.id;const row={...args,id:workWrites.length?'qa_w'+(workWrites.length+1):'qa_w1'};workWrites.push(row);pending.push({kind:'worklog',payload:row});persist();return row.id;},
    stagePendingWorkPhoto:raw=>{const row=JSON.parse(raw);localStorage.setItem('qa.work.photo.staged',JSON.stringify(row));return JSON.stringify(row);},
    getPendingWorkPhoto:()=>localStorage.getItem('qa.work.photo.staged')||'',
    clearPendingWorkPhoto:()=>{localStorage.removeItem('qa.work.photo.staged');return 'OK';},
    savePhoto:raw=>{const row=JSON.parse(raw);photoWrites.push(row);if(photoWrites.length===1){persist();return'ERROR: Isolierter Foto-Speicherfehler'}pending.push({kind:'photo',payload:row});persist();return row.id;},synchronize:()=>{}
   };
   document.addEventListener('DOMContentLoaded',()=>{const input=document.createElement('input');input.name='qa_dynamic';input.id='qa-dynamic';document.getElementById('entry').append(input);});
   window.AndroidSpeech={start:()=>{}};window.AndroidPhone={dial:()=>{},openMaps:()=>{}};
  });
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href,{waitUntil:'domcontentloaded',timeout:30000});
  await page.waitForFunction(()=>document.body.dataset.fieldReady==='true'&&document.body.dataset.todayReady==='true');
  await check('A photo failure after a successful work save preserves a retry without duplicating the work record',async()=>{
   await page.evaluate(()=>openPage('work-entry'));
   await page.locator('#customer').selectOption('qa_c1');
   await page.locator('#entry-hours').fill('1.5');
   await page.locator('#activity').fill('Hecke schneiden');
   await page.locator('#entry [data-section="notes"] > summary').click();
   await page.locator('#photo').setInputFiles({name:'hecke.png',mimeType:'image/png',buffer:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO/a00sAAAAASUVORK5CYII=','base64')});
   await page.locator('#entry button[type="submit"]').click();
   await page.locator('#work-photo-recovery:not([hidden])').waitFor();
   await page.waitForFunction(()=>photoWrites.length===1);
   assert.equal(await page.evaluate(()=>currentPage),'work-entry');
   assert.equal(await page.evaluate(()=>workWrites.length),1);
   assert.equal(await page.evaluate(()=>photoWrites.length),1);
   assert.match(await page.locator('#mobile-toast').innerText(),/Arbeitsaufzeichnung gespeichert.*Foto ist noch nicht gespeichert/i);
   assert.equal(await page.locator('#activity').inputValue(),'Hecke schneiden');
   assert.equal(await page.locator('#photo').evaluate(input=>input.files.length),1);
   assert.equal(await page.locator('#entry button[type="submit"]').isDisabled(),true);
   mediaKey=await page.evaluate(()=>HGFormDrafts.list('work_entry')[0].values.media_key);
   assert(await page.evaluate(async()=>{const drafts=HGFormDrafts.list('work_entry');if(drafts.length!==1||drafts[0].context.saved_work?.id!=='qa_w1')return false;const batch=await GartenpiratPhotoStore.customerDraft(drafts[0].values.media_key);return batch.items[0].file instanceof Blob&&batch.items[0].file.size===68&&batch.items[0].submitted===true&&!!batch.items[0].prepared_payload?.data&&batch.source.customer_id==='qa_c1';}));
   await page.reload({waitUntil:'domcontentloaded',timeout:30000});
   await page.locator('#work-photo-recovery:not([hidden])').waitFor();
   assert.equal(await page.evaluate(()=>currentPage),'work-entry');
   assert.match(await page.locator('#work-photo-recovery').innerText(),/lokal gespeichert/i);
   assert.equal(await page.evaluate(()=>workWrites.length),1);
   assert.equal(await page.evaluate(()=>photoWrites.length),1);
   await page.locator('#retry-work-photo').click();
   await page.waitForFunction(()=>currentPage==='home');
   assert.equal(await page.evaluate(()=>workWrites.length),1);
   assert.equal(await page.evaluate(()=>photoWrites.length),2);
   assert.equal(await page.locator('#activity').inputValue(),'');
   assert.equal(await page.locator('#entry details.more[open]').count(),0);
   assert.equal(await page.locator('#work-photo-recovery').isHidden(),true);
   assert.equal(await page.evaluate(()=>HGFormDrafts.list('work_entry').length),0);
   assert.equal(await page.evaluate(key=>GartenpiratPhotoStore.customerDraft(key),mediaKey),null);
  });
  await check('The failed photo was linked to the one successfully stored work record',async()=>{
   const saved=await page.evaluate(()=>photoWrites);assert.equal(saved.length,2);for(const payload of saved){assert.equal(payload.worklog_id,'qa_w1');assert.equal(payload.customer_id,'qa_c1');assert.equal(payload.role,'documentation');assert.equal(payload.mime_type,'image/jpeg');}
  });
  await check('Untouched manual work defaults and navigation never create an empty edit or business record',async()=>{
   await page.evaluate(()=>{openPage('work-entry');openPage('home');handleAndroidBack();openPage('home');});
   assert.equal(await page.evaluate(()=>HGFormDrafts.list('work_entry').length),0);
   assert.equal(await page.evaluate(()=>workWrites.length),1);
  });
  await check('Two independent manual work edits survive reload with recipient, future date and a dynamic field',async()=>{
   await page.evaluate(()=>openPage('work-entry'));
   await page.locator('#customer').selectOption('qa_c1');await page.locator('#order').selectOption('qa_o1',{force:true});
   await page.locator('#entry-billing-customer').selectOption('qa_c2',{force:true});await page.locator('#date').fill('2027-05-18');
   await page.locator('#entry-hours').fill('2.5');await page.locator('#activity').fill('QA erste Arbeit');
   await page.evaluate(()=>{const input=document.getElementById('qa-dynamic');input.value='QA dynamischer Wert';input.dispatchEvent(new Event('input',{bubbles:true}));});
   await page.getByRole('button',{name:'Neue Arbeitserfassung',exact:true}).click();
   assert.equal(await page.locator('#activity').inputValue(),'');
   assert.equal(await page.locator('#customer').isDisabled(),false);
   await page.locator('#customer').selectOption('qa_c1');await page.locator('#entry-hours').fill('1');await page.locator('#activity').fill('QA zweite Arbeit');
   await page.evaluate(()=>openPage('home'));await page.reload({waitUntil:'domcontentloaded'});
   await page.waitForFunction(()=>document.getElementById('entry-billing-customer'));
   assert.equal(await page.evaluate(()=>HGFormDrafts.list('work_entry').length),2);
   // A dynamic field contributed by an existing feature is restored by its owning feature.
   await page.evaluate(async()=>{const edit=HGFormDrafts.list('work_entry').find(e=>e.values.fields.some(f=>f.value==='QA erste Arbeit'));window.qaOriginalEdit=HGFormDrafts.clone(edit);await resumeWorkEntryEdit(edit);});
   assert.equal(await page.locator('#activity').inputValue(),'QA erste Arbeit');assert.equal(await page.locator('#date').inputValue(),'2027-05-18');assert.equal(await page.locator('#entry-billing-customer').inputValue(),'qa_c2');assert.equal(await page.locator('#order').inputValue(),'qa_o1');assert.equal(await page.locator('#qa-dynamic').inputValue(),'QA dynamischer Wert');
  });
  await check('Explicit discard removes only its edit and mirror and cannot be revived by a late callback',async()=>{
   await page.evaluate(()=>{const edit=HGFormDrafts.list('work_entry').find(e=>e.values.fields.some(f=>f.value==='QA erste Arbeit'));localStorage.setItem('hausgarten.fullbackup.formdrafts.v1',JSON.stringify({formatVersion:1,pages:{'work-entry':{forms:[{session_id:edit.session_id,key:'entry',fields:edit.values.fields}]}}}));});
   await page.getByRole('button',{name:'Änderungen verwerfen',exact:true}).click();
   assert.equal(await page.evaluate(()=>HGFormDrafts.list('work_entry').length),1);
   assert(await page.evaluate(()=>{try{HGFormDrafts.put(window.qaOriginalEdit);return false;}catch(error){return error.message==='FORM_DRAFT_DISCARDED'&&!JSON.parse(localStorage.getItem('hausgarten.fullbackup.formdrafts.v1')).pages['work-entry'];}}));
   await page.reload({waitUntil:'domcontentloaded'});await page.waitForFunction(()=>document.getElementById('activity').value==='QA zweite Arbeit');assert.equal(await page.evaluate(()=>HGFormDrafts.list('work_entry').length),1);assert.equal(await page.evaluate(()=>workWrites.length),1);
   await page.getByRole('button',{name:'Änderungen verwerfen',exact:true}).click();
  });
  await check('Original photo bytes and captured context survive a reload before the first business save',async()=>{
   await page.evaluate(()=>openPage('work-entry'));await page.locator('#customer').selectOption('qa_c1');await page.locator('#activity').fill('QA Fotoentwurf');await page.locator('#entry-hours').fill('1');
   await page.locator('#entry [data-section="notes"] > summary').click();
   const bytes=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO/a00sAAAAASUVORK5CYII=','base64');
   await page.locator('#photo').setInputFiles({name:'qa_original.png',mimeType:'image/png',buffer:bytes});
   await page.waitForFunction(()=>HGFormDrafts.list('work_entry').some(e=>e.values.photo_durable));
   mediaKey=await page.evaluate(()=>HGFormDrafts.list('work_entry')[0].values.media_key);
   await page.locator('#photo').dispatchEvent('cancel');await page.reload({waitUntil:'domcontentloaded'});await page.waitForFunction(()=>document.getElementById('photo').files.length===1);
   assert.deepEqual(Buffer.from(await page.evaluate(async key=>[...new Uint8Array(await(await GartenpiratPhotoStore.customerDraft(key)).items[0].file.arrayBuffer())],mediaKey)),bytes);
   assert.equal(await page.evaluate(async key=>(await GartenpiratPhotoStore.customerDraft(key)).source.customer_id,mediaKey),'qa_c1');assert.equal(await page.evaluate(()=>workWrites.length),1);
   await page.getByRole('button',{name:'Änderungen verwerfen',exact:true}).click();await page.waitForFunction(async key=>await GartenpiratPhotoStore.customerDraft(key)===null,mediaKey);
   await page.reload({waitUntil:'domcontentloaded'});await page.waitForFunction(()=>document.getElementById('entry-billing-customer'));assert.equal(await page.evaluate(()=>HGFormDrafts.list('work_entry').length),0);
  });
  await check('Lost work response survives a second independent edit and replays the original action exactly once',async()=>{
   await page.evaluate(()=>openPage('work-entry'));await page.locator('#customer').selectOption('qa_c1');await page.locator('#entry-hours').fill('1');await page.locator('#activity').fill('QA verlorene Antwort');
   await page.evaluate(()=>{const actual=AndroidData.saveWorklog;AndroidData.saveWorklog=raw=>{actual(raw);throw Error('QA lost response after commit');};});
   await page.locator('#entry button[type="submit"]').click();await page.waitForFunction(()=>HGFormDrafts.list('work_entry').some(e=>e.pending));
   const action=await page.evaluate(()=>HGFormDrafts.list('work_entry')[0].pending);
   assert.equal(await page.evaluate(()=>workWrites.length),2);
   await page.getByRole('button',{name:'Neue Arbeitserfassung',exact:true}).click();assert.equal(await page.locator('#activity').isDisabled(),false);
   await page.locator('#customer').selectOption('qa_c1');await page.locator('#activity').fill('QA unabhängig');await page.evaluate(()=>openPage('home'));
   await page.reload({waitUntil:'domcontentloaded'});await page.waitForFunction(()=>document.getElementById('entry-billing-customer'));
   await page.evaluate(async()=>resumeWorkEntryEdit(HGFormDrafts.list('work_entry').find(e=>e.pending)));
   assert.equal(await page.locator('#activity').isDisabled(),true);
   assert.deepEqual(await page.evaluate(()=>HGFormDrafts.list('work_entry').find(e=>e.pending).pending),action);
   await page.locator('#entry button[type="submit"]').click();await page.waitForFunction(()=>currentPage==='home');
   assert.equal(await page.evaluate(()=>workWrites.length),2);assert.equal(await page.evaluate(()=>HGFormDrafts.list('work_entry').length),1);
   assert.equal(await page.evaluate(()=>HGFormDrafts.list('work_entry')[0].values.fields.find(f=>f.name==='activity').value),'QA unabhängig');
  });
  await check('No runtime errors occurred',()=>assert.deepEqual(errors,[]));
 }finally{await Promise.race([browser.close(),new Promise(resolve=>setTimeout(resolve,5000))]);}
 fs.writeFileSync('outputs/Work-entry-photo-failure-tests.json',JSON.stringify({browser:true,nativeBridge:'isolated synthetic fixture',results},null,2));
 if(results.some(row=>row.result==='FAIL'))process.exitCode=1;
})().then(()=>process.exit(process.exitCode||0)).catch(error=>{console.error(error);process.exit(1);});

'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs');
const results=[];
fs.mkdirSync('outputs',{recursive:true});
async function check(name,task){try{await task();results.push({name,result:'PASS'});}catch(error){results.push({name,result:'FAIL',error:error.stack});}console.log(results.at(-1));}
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'chrome'});
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},isMobile:true,hasTouch:true});
  page.setDefaultTimeout(12000);const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.addInitScript(()=>{
   const pixel='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';
   const initial=[
    {id:'qa_p1',customer_id:'qa_c1',caption:'Profilbild',role:'profile',original_name:'profil.png',mime_type:'image/png',data:pixel,thumbnail_data:pixel,created_at:'2026-10-10T08:00:00Z'},
    {id:'qa_p2',customer_id:'qa_c1',caption:'Garten vorher',role:'documentation',original_name:'garten.png',mime_type:'image/png',data:pixel,thumbnail_data:pixel,created_at:'2026-10-11T08:00:00Z'},
    {id:'qa_p3',customer_id:'qa_c1',worklog_id:'qa_w1',caption:'Belegfoto aus Drive',role:'documentation',original_name:'beleg.png',mime_type:'image/png',content_sha256:'a'.repeat(64),storage_name:'a'.repeat(64)+'.png',byte_size:68,created_at:'2026-10-12T08:00:00Z'}
   ];
   let photos;try{photos=JSON.parse(localStorage.getItem('qa.customer.photos')||'null')||initial;}catch(_){photos=initial;}
   const master={customers:[{id:'qa_c1',name:'Fotokunde',street:'Gartenweg 1',postal_code:'9560',city:'QA Beispielort',active:1},{id:'qa_c2',name:'Anderer QA Kunde',active:1}],orders:[{id:'qa_order_A',customer_id:'qa_c1',title:'QA Auftrag A',status:'Geplant'},{id:'qa_order_B',customer_id:'qa_c2',title:'QA Auftrag B',status:'Geplant'}],worklogs:[],photos,invoices:[]},pending=[];
   let removed=null,photoSequence=Number(localStorage.getItem('qa.photo.sequence')||'0');const receipts=JSON.parse(localStorage.getItem('qa.photo.receipts')||'{}');window.photoWrites=JSON.parse(localStorage.getItem('qa.photo.writes')||'[]');window.photoStorageFailure=false;window.photoLoseNextReply=false;window.photoDeletes=[];window.photoUndo=[];window.photoLoads=[];window.fullscreenStates=[];
   const persist=()=>{localStorage.setItem('qa.customer.photos',JSON.stringify(master.photos));localStorage.setItem('qa.photo.receipts',JSON.stringify(receipts));localStorage.setItem('qa.photo.writes',JSON.stringify(photoWrites));localStorage.setItem('qa.photo.sequence',String(photoSequence));};
   window.AndroidData={
    getState:()=>JSON.stringify({master,pending}),getSyncStatus:()=>JSON.stringify({configured:true,pending:pending.length}),getAppInfo:()=>JSON.stringify({versionName:'1.46.0'}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),
    savePhoto:raw=>{const row=JSON.parse(raw),request=row._photo_request_id;window.photoWrites.push(JSON.parse(raw));if(request&&receipts[request]){persist();return receipts[request].raw===raw?receipts[request].id:'ERROR: QA replay payload changed';}if(photoStorageFailure){persist();return'ERROR: QA Gerätespeicher voll';}row.id='qa_added_'+(++photoSequence);row.created_at=new Date().toISOString();master.photos.push(row);pending.push({kind:'photo',payload:{...row,data:undefined}});if(request)receipts[request]={raw,id:row.id};persist();if(photoLoseNextReply){photoLoseNextReply=false;return'ERROR: QA Antwort nach lokalem Commit unterbrochen';}return row.id;},
    getPhotoData:id=>{window.photoLoads.push(id);return id==='qa_p3'?pixel:'ERROR: Original fehlt';},
    removeEntity:(kind,id)=>{if(kind!=='photo')return 'ERROR: falscher Typ';const index=master.photos.findIndex(row=>row.id===id);if(index<0)return 'ERROR: unbekannt';removed=master.photos.splice(index,1)[0];window.photoDeletes.push(id);persist();return id;},
    undoRemoveEntity:(kind,id)=>{if(kind!=='photo'||!removed||removed.id!==id)return 'ERROR: unbekannt';master.photos.push(removed);window.photoUndo.push(id);removed=null;persist();return id;},
    saveCustomer:()=>'',saveOrder:()=>'',saveWorklog:()=>'',synchronize:()=>{}
   };
   window.AndroidGardenPhotos={setFullscreen:value=>fullscreenStates.push(!!value)};
   window.AndroidSpeech={start:()=>{}};window.AndroidPhone={dial:()=>{},openMaps:()=>{}};
  });
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href,{waitUntil:'domcontentloaded',timeout:30000});
  await page.waitForFunction(()=>document.body.dataset.fieldReady==='true'&&document.body.dataset.todayReady==='true');await page.evaluate(()=>openCustomer('qa_c1'));await page.locator('[data-customer-tab="photos"]').click();
  await check('Existing customer photos and the profile role render in the customer account',async()=>{
   assert.equal(await page.locator('[data-design-action="photo"]').count(),3);
   assert.equal(await page.locator('.design-photo-role').count(),1);
   assert((await page.locator('#design-profile-content').innerText()).includes('3 Fotos'));
  });
  await check('Photo viewer provides full screen, navigation and deliberate zoom',async()=>{
   await page.locator('[data-design-action="photo"][data-id="qa_p1"]').click();
   assert.equal(await page.locator('#design-photo-view').evaluate(dialog=>dialog.open),true);
   assert.equal(await page.locator('#design-photo-view [data-photo-counter]').innerText(),'1 / 3');
   assert.deepEqual(await page.evaluate(()=>fullscreenStates),[true]);
   await page.locator('#design-photo-view img').dblclick();
   assert.equal(await page.locator('#design-photo-view [data-photo-zoom]').innerText(),'250 %');
   await page.locator('#design-photo-view [data-photo-next]').click();
   assert.equal(await page.locator('#design-photo-view [data-photo-counter]').innerText(),'2 / 3');
   assert.equal(await page.locator('#design-photo-view [data-photo-caption]').innerText(),'Garten vorher');
   await page.keyboard.press('Escape');
   await page.waitForFunction(()=>!document.getElementById('design-photo-view').open&&fullscreenStates.at(-1)===false);
   assert.equal(await page.locator('#design-photo-view').evaluate(dialog=>dialog.open),false);
   assert.equal((await page.evaluate(()=>fullscreenStates)).at(-1),false);
  });
  await check('Multiple gallery photos remain assigned to exactly the opened customer',async()=>{
   await page.locator('[data-design-action="add-photos"]').click();
   assert.equal(await page.locator('#design-customer-photo-add').evaluate(dialog=>dialog.open),true);
   await page.locator('#design-customer-photo-caption').fill('Beet im Oktober');
   await page.locator('#design-customer-photo-profile').check();
   const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=','base64');
   await page.locator('#design-customer-photo-files').setInputFiles([
    {name:'beet-a.png',mimeType:'image/png',buffer:png},{name:'beet-b.png',mimeType:'image/png',buffer:png}
   ]);
   await page.waitForFunction(()=>photoWrites.length===2);
   const writes=await page.evaluate(()=>photoWrites);
   assert.deepEqual(writes.map(row=>row.customer_id),['qa_c1','qa_c1']);
   assert.deepEqual(writes.map(row=>row.role),['profile','documentation']);
   assert(writes.every(row=>row.mime_type==='image/png'&&String(row.data).split(',')[1]===png.toString('base64')));
   await page.waitForFunction(()=>document.querySelectorAll('[data-design-action="photo"]').length===5);
   assert((await page.locator('#design-profile-content').innerText()).includes('5 Fotos'));
  });
  await check('A valid provider image without a MIME declaration is decoded instead of rejected',async()=>{
   await page.waitForFunction(()=>!document.getElementById('design-customer-photo-add').open);
   await page.locator('[data-design-action="add-photos"]').click();
   const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=','base64');
   await page.evaluate(base64=>{const bytes=Uint8Array.from(atob(base64),character=>character.charCodeAt(0)),file=new File([bytes],'cloud-provider-image',{type:''}),transfer=new DataTransfer(),input=document.getElementById('design-customer-photo-files');transfer.items.add(file);input.files=transfer.files;input.dispatchEvent(new Event('change',{bubbles:true}));},png.toString('base64'));
   await page.waitForFunction(()=>photoWrites.length===3);
   assert.equal((await page.evaluate(()=>photoWrites.at(-1))).customer_id,'qa_c1');
  });
  await check('Stored customer photos survive an application reload',async()=>{
   await page.reload({waitUntil:'domcontentloaded',timeout:30000});await page.waitForFunction(()=>document.body.dataset.fieldReady==='true'&&document.body.dataset.todayReady==='true');await page.evaluate(()=>openCustomer('qa_c1'));await page.locator('[data-customer-tab="photos"]').click();
   assert.equal(await page.locator('[data-design-action="photo"]').count(),6);
  });
  await check('A synced metadata-only original loads on demand and linked photos can enter recoverable trash',async()=>{
   await page.locator('[data-design-action="photo"][data-id="qa_p3"]').click();
   await page.waitForFunction(()=>photoLoads.includes('qa_p3'));
   assert.deepEqual(await page.evaluate(()=>photoLoads),['qa_p3']);
   assert((await page.locator('#design-photo-view [data-photo-status]').innerText()).includes('zugeordnet'));
   assert.equal(await page.locator('#design-photo-view .design-photo-delete').isDisabled(),false);
   await page.keyboard.press('Escape');
  });
  await check('Deleting one photo is confirmed, targeted and reversible',async()=>{
   await page.locator('[data-design-action="photo"][data-id="qa_p2"]').click();page.once('dialog',dialog=>dialog.accept());
   await page.locator('#design-photo-view .photo-more summary').click();await page.locator('#design-photo-view .design-photo-delete').click();
   assert.deepEqual(await page.evaluate(()=>photoDeletes),['qa_p2']);
   assert.equal(await page.locator('[data-design-action="photo"][data-id="qa_p2"]').count(),0);
   await page.locator('.undo-toast-button').click();
   assert.deepEqual(await page.evaluate(()=>photoUndo),['qa_p2']);
   assert.equal(await page.locator('[data-design-action="photo"][data-id="qa_p2"]').count(),1);
  });
  await check('A failed native undo is reported and never presented as restored',async()=>{
   await page.evaluate(()=>{AndroidData.undoRemoveEntity=()=> 'ERROR: Abgleich bereits bestätigt';});
   await page.locator('[data-design-action="photo"][data-id="qa_p1"]').click();page.once('dialog',dialog=>dialog.accept());
   await page.locator('#design-photo-view .photo-more summary').click();await page.locator('#design-photo-view .design-photo-delete').click();await page.locator('.undo-toast-button').click();
   assert((await page.locator('#mobile-toast').innerText()).includes('Abgleich bereits bestätigt'));
   assert.equal(await page.locator('[data-design-action="photo"][data-id="qa_p1"]').count(),0);
  });
  const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=','base64');
  async function orderPhotoEntry(){await page.evaluate(()=>openPage('orders'));const details=page.locator('#order-photo-card').locator('..');if(await details.evaluate(node=>node.tagName==='DETAILS'&&!node.open))await details.locator('summary').click();await page.locator('#order-photo-order').selectOption('qa_order_A');}
  await check('The retained order entry imports 25 images sequentially without a ten-photo cap',async()=>{
   await orderPhotoEntry();const before=await page.evaluate(()=>({photos:photos().length,writes:photoWrites.length}));await page.locator('#order-photo-caption').fill('QA 25 eindeutige Aktionen');await page.locator('#order-photo').setInputFiles(Array.from({length:25},(_,i)=>({name:'qa_order_'+i+'.png',mimeType:'image/png',buffer:png})));await page.locator('#order-photo-save').click();
   await page.waitForFunction(async n=>photos().length===n+25&&!(await GartenpiratPhotoStore.customerDraft('order-photo-entry-v1')),before.photos);await page.waitForFunction(()=>!document.getElementById('order-photo-save').disabled);const writes=await page.evaluate(n=>photoWrites.slice(n),before.writes);assert.equal(writes.length,25);assert.equal(new Set(writes.map(row=>row._photo_request_id)).size,25);assert(writes.every(row=>row.customer_id==='qa_c1'&&row.order_id==='qa_order_A'&&row.role==='documentation'&&row.mime_type==='image/jpeg'));assert.match(await page.locator('#order-photo-draft-status').innerText(),/25 Foto.*lokal bestätigt/);
  });
  await check('Order selection is frozen and a lost post-commit reply retries the exact photo action after reload',async()=>{
   await orderPhotoEntry();const before=await page.evaluate(()=>({photos:photos().length,writes:photoWrites.length}));await page.locator('#order-photo-caption').fill('QA unveränderter Text');await page.locator('#order-photo').setInputFiles({name:'qa_lost_reply.png',mimeType:'image/png',buffer:png});await page.locator('#order-photo-order').selectOption('qa_order_B');await page.locator('#order-photo-save').click();await page.waitForFunction(()=>!document.getElementById('order-photo-save').disabled);assert.equal(await page.evaluate(()=>photoWrites.length),before.writes);assert.match(await page.locator('#order-photo-draft-status').innerText(),/ursprünglichen Auftrag/);assert.equal(await page.locator('#order-photo').evaluate(input=>input.files.length),1);
   await page.locator('#order-photo-order').selectOption('qa_order_A');await page.evaluate(()=>photoLoseNextReply=true);await page.locator('#order-photo-save').click();await page.waitForFunction(()=>!document.getElementById('order-photo-retry').hidden&&!document.getElementById('order-photo-save').disabled);const staged=await page.evaluate(()=>GartenpiratPhotoStore.customerDraft('order-photo-entry-v1'));assert.equal(staged.items.length,1);assert.equal(staged.customer_id,'qa_c1');assert.equal(staged.order_id,'qa_order_A');assert.equal(staged.items[0].payload.caption,'QA unveränderter Text');assert(staged.items[0].payload.data.startsWith('data:image/jpeg'));assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('qa.customer.photos')).length),before.photos+1);
   await page.reload({waitUntil:'domcontentloaded',timeout:30000});await page.waitForFunction(()=>document.body.dataset.fieldReady==='true'&&document.body.dataset.todayReady==='true');await orderPhotoEntry();await page.locator('#order-photo-order').selectOption('qa_order_B');await page.locator('#order-photo-caption').fill('QA soll nicht umhängen');await page.locator('#order-photo-retry').waitFor({state:'visible'});await page.locator('#order-photo-retry').click();await page.waitForFunction(async()=>!(await GartenpiratPhotoStore.customerDraft('order-photo-entry-v1'))&&!document.getElementById('order-photo-save').disabled);assert.equal(await page.evaluate(()=>photos().length),before.photos+1);const calls=await page.evaluate(n=>photoWrites.slice(n),before.writes);assert.equal(calls.length,2);assert.deepEqual(calls[0],calls[1]);assert.equal(calls[1].customer_id,'qa_c1');assert.equal(calls[1].order_id,'qa_order_A');
  });
  await check('Native storage failure retains every order-photo draft and unrelated work input',async()=>{
   await orderPhotoEntry();await page.evaluate(()=>{document.getElementById('activity').value='QA unabhängige begonnene Arbeit';photoStorageFailure=true;});const before=await page.evaluate(()=>({photos:photos().length,writes:photoWrites.length}));await page.locator('#order-photo').setInputFiles([{name:'qa_space_1.png',mimeType:'image/png',buffer:png},{name:'qa_space_2.png',mimeType:'image/png',buffer:png}]);await page.locator('#order-photo-save').click();await page.waitForFunction(()=>!document.getElementById('order-photo-save').disabled&&!document.getElementById('order-photo-retry').hidden);const staged=await page.evaluate(()=>GartenpiratPhotoStore.customerDraft('order-photo-entry-v1'));assert.equal(staged.items.length,2);assert(staged.items.every(item=>item.payload.data));assert.equal(await page.evaluate(()=>photos().length),before.photos);assert.equal(await page.locator('#activity').inputValue(),'QA unabhängige begonnene Arbeit');await page.evaluate(()=>photoStorageFailure=false);await page.locator('#order-photo-retry').click();await page.waitForFunction(async()=>!(await GartenpiratPhotoStore.customerDraft('order-photo-entry-v1'))&&!document.getElementById('order-photo-save').disabled);await page.waitForFunction(()=>document.querySelector('#order-photo-draft-status [role=status]').textContent.startsWith('2 Foto(s) lokal bestätigt.'));assert.equal(await page.evaluate(()=>photos().length),before.photos+2);const calls=await page.evaluate(n=>photoWrites.slice(n),before.writes);assert.equal(calls.length,4);assert.deepEqual(calls.slice(0,2),calls.slice(2));assert.equal(await page.locator('#activity').inputValue(),'QA unabhängige begonnene Arbeit');
  });
  await check('Draft-storage quota failure makes no native write and keeps the selected file for explicit retry',async()=>{
   await orderPhotoEntry();const before=await page.evaluate(()=>photoWrites.length);await page.locator('#order-photo').setInputFiles({name:'qa_quota.png',mimeType:'image/png',buffer:png});await page.evaluate(()=>{window.qaOriginalPhotoStore=GartenpiratPhotoStore;window.GartenpiratPhotoStore={...qaOriginalPhotoStore,customerDraft:async(key,value)=>{if(value!==undefined&&value!==null)throw new DOMException('QA Fotoentwurfspeicher voll','QuotaExceededError');return qaOriginalPhotoStore.customerDraft(key,value);}};});await page.locator('#order-photo-save').click();await page.waitForFunction(()=>!document.getElementById('order-photo-save').disabled);assert.equal(await page.evaluate(()=>photoWrites.length),before);assert.equal(await page.locator('#order-photo').evaluate(input=>input.files.length),1);assert.match(await page.locator('#order-photo-draft-status').innerText(),/Fotoentwurfspeicher voll/);assert.equal(await page.locator('#activity').inputValue(),'QA unabhängige begonnene Arbeit');await page.evaluate(()=>window.GartenpiratPhotoStore=qaOriginalPhotoStore);await page.locator('#order-photo-save').click();await page.waitForFunction(async()=>!(await GartenpiratPhotoStore.customerDraft('order-photo-entry-v1'))&&!document.getElementById('order-photo-save').disabled);await page.waitForFunction(()=>document.querySelector('#order-photo-draft-status [role=status]').textContent.startsWith('1 Foto(s) lokal bestätigt.'));assert.equal(await page.evaluate(()=>photoWrites.length),before+1);
  });
  await check('The concrete 50 MiB per-file import bound preserves its draft and supports deliberate removal',async()=>{
   await orderPhotoEntry();const before=await page.evaluate(()=>photoWrites.length);await page.evaluate(()=>{const file=new File([new Uint8Array(50*1024*1024+1)],'qa_too_large.png',{type:'image/png'}),transfer=new DataTransfer(),input=document.getElementById('order-photo');transfer.items.add(file);input.files=transfer.files;input.dispatchEvent(new Event('change',{bubbles:true}));});await page.locator('#order-photo-save').click();await page.waitForFunction(()=>!document.getElementById('order-photo-save').disabled&&!document.getElementById('order-photo-retry').hidden);const staged=await page.evaluate(()=>GartenpiratPhotoStore.customerDraft('order-photo-entry-v1'));assert.equal(staged.items.length,1);assert.equal(await page.evaluate(async()=>{const batch=await GartenpiratPhotoStore.customerDraft('order-photo-entry-v1');return batch.items[0].file.size;}),50*1024*1024+1);assert.equal(await page.evaluate(()=>photoWrites.length),before);assert.match(await page.locator('#order-photo-draft-status').innerText(),/50 MB/);page.once('dialog',dialog=>dialog.accept());await page.locator('#order-photo-draft-status').getByRole('button',{name:'Diesen Entwurf entfernen'}).click();await page.waitForFunction(async()=>!(await GartenpiratPhotoStore.customerDraft('order-photo-entry-v1')));assert.equal(await page.evaluate(()=>photoWrites.length),before);
  });
  await check('Order original without MIME preserves exact PNG filename and frozen quality after lost commit reply',async()=>{
   await orderPhotoEntry();const before=await page.evaluate(()=>({photos:photos().length,writes:photoWrites.length}));await page.evaluate(base64=>{window.qaOrderPhotoQuality='original';AndroidData.getOwnerSettings=()=>JSON.stringify({photo_quality:qaOrderPhotoQuality});photoLoseNextReply=true;const transfer=new DataTransfer();transfer.items.add(new File([Uint8Array.from(atob(base64),c=>c.charCodeAt(0))],'qa_order_original.png',{type:''}));const input=document.getElementById('order-photo');input.files=transfer.files;input.dispatchEvent(new Event('change',{bubbles:true}));},png.toString('base64'));await page.locator('#order-photo-save').click();await page.waitForFunction(()=>photoWrites.some(row=>row.original_name==='qa_order_original.png'&&row._photo_quality==='original')&&!document.getElementById('order-photo-save').disabled&&!document.getElementById('order-photo-retry').hidden);const first=await page.evaluate(()=>photoWrites.find(row=>row.original_name==='qa_order_original.png')),staged=await page.evaluate(()=>GartenpiratPhotoStore.customerDraft('order-photo-entry-v1'));assert.equal(staged.items[0].prepared_payload._photo_request_id,first._photo_request_id);assert.equal(staged.items[0].submitted,true);assert.deepEqual(Buffer.from(first.data.split(',')[1],'base64'),png);assert.equal(first.mime_type,'image/png');assert.equal(first.original_name,'qa_order_original.png');assert.equal(first._photo_quality,'original');await page.evaluate(()=>qaOrderPhotoQuality='optimized');await page.locator('#order-photo-retry').click();await page.waitForFunction(request=>photoWrites.filter(row=>row._photo_request_id===request).length===2&&!document.getElementById('order-photo-save').disabled&&document.querySelector('#order-photo-draft-status [role=status]').textContent.startsWith('1 Foto(s) lokal bestätigt.'),first._photo_request_id);assert.equal(await page.evaluate(()=>GartenpiratPhotoStore.customerDraft('order-photo-entry-v1')),null);const calls=await page.evaluate(request=>photoWrites.filter(row=>row._photo_request_id===request),first._photo_request_id);assert.equal(calls.length,2);assert.deepEqual(calls[0],calls[1]);assert.equal(await page.evaluate(()=>photos().length),before.photos+1);assert(await page.evaluate(request=>JSON.parse(localStorage.getItem('qa.customer.photos')).some(row=>row._photo_request_id===request),first._photo_request_id));
  });
  await check('Camera input is explicit and the customer photo UI has no runtime errors',async()=>{
   await page.evaluate(()=>openCustomer('qa_c1'));await page.locator('[data-customer-tab="photos"]').click();
   await page.locator('[data-design-action="add-photos"]').click();
   assert.equal(await page.locator('#design-customer-photo-files').getAttribute('multiple'),'');
   assert.equal(await page.locator('#design-customer-photo-camera').getAttribute('capture'),'environment');
   assert.deepEqual(errors,[]);
  });
  fs.writeFileSync('outputs/Customer-Photos-v1.46-Tests.json',JSON.stringify({browser:true,nativeBridge:'isolated fixture',realCamera:false,results},null,2));
  if(results.some(row=>row.result==='FAIL'))process.exitCode=1;
 }finally{await Promise.race([browser.close(),new Promise(resolve=>setTimeout(resolve,5000))]);}
})().then(()=>process.exit(process.exitCode||0)).catch(error=>{console.error(error);process.exit(1);});

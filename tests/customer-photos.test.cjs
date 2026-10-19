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
   const master={customers:[{id:'qa_c1',name:'Fotokunde',street:'Gartenweg 1',postal_code:'9560',city:'QA Beispielort',active:1}],orders:[],worklogs:[],photos,invoices:[]},pending=[];
   let removed=null;window.photoWrites=[];window.photoDeletes=[];window.photoUndo=[];window.photoLoads=[];window.fullscreenStates=[];
   const persist=()=>localStorage.setItem('qa.customer.photos',JSON.stringify(master.photos));
   window.AndroidData={
    getState:()=>JSON.stringify({master,pending}),getSyncStatus:()=>JSON.stringify({configured:true,pending:pending.length}),getAppInfo:()=>JSON.stringify({versionName:'1.46.0'}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),
    savePhoto:raw=>{const row=JSON.parse(raw);row.id='qa_saved-'+(master.photos.length+1);row.created_at=new Date().toISOString();window.photoWrites.push(row);master.photos.push(row);pending.push({kind:'photo',payload:{...row,data:undefined}});persist();return row.id;},
    getPhotoData:id=>{window.photoLoads.push(id);return id==='qa_p3'?pixel:'ERROR: Original fehlt';},
    removeEntity:(kind,id)=>{if(kind!=='photo')return 'ERROR: falscher Typ';const index=master.photos.findIndex(row=>row.id===id);if(index<0)return 'ERROR: unbekannt';removed=master.photos.splice(index,1)[0];window.photoDeletes.push(id);persist();return id;},
    undoRemoveEntity:(kind,id)=>{if(kind!=='photo'||!removed||removed.id!==id)return 'ERROR: unbekannt';master.photos.push(removed);window.photoUndo.push(id);removed=null;persist();return id;},
    saveCustomer:()=>'',saveOrder:()=>'',saveWorklog:()=>'',synchronize:()=>{}
   };
   window.AndroidGardenPhotos={setFullscreen:value=>fullscreenStates.push(!!value)};
   window.AndroidSpeech={start:()=>{}};window.AndroidPhone={dial:()=>{},openMaps:()=>{}};
  });
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href,{waitUntil:'domcontentloaded',timeout:30000});
  await page.waitForSelector('#design-metrics');await page.evaluate(()=>openCustomer('qa_c1'));await page.locator('[data-customer-tab="photos"]').click();
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
   await page.reload({waitUntil:'domcontentloaded',timeout:30000});await page.waitForSelector('#design-metrics');await page.evaluate(()=>openCustomer('qa_c1'));await page.locator('[data-customer-tab="photos"]').click();
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
  await check('Camera input is explicit and the customer photo UI has no runtime errors',async()=>{
   await page.locator('[data-design-action="add-photos"]').click();
   assert.equal(await page.locator('#design-customer-photo-files').getAttribute('multiple'),'');
   assert.equal(await page.locator('#design-customer-photo-camera').getAttribute('capture'),'environment');
   assert.deepEqual(errors,[]);
  });
  fs.writeFileSync('outputs/Customer-Photos-v1.46-Tests.json',JSON.stringify({browser:true,nativeBridge:'isolated fixture',realCamera:false,results},null,2));
  if(results.some(row=>row.result==='FAIL'))process.exitCode=1;
 }finally{await Promise.race([browser.close(),new Promise(resolve=>setTimeout(resolve,5000))]);}
})().then(()=>process.exit(process.exitCode||0)).catch(error=>{console.error(error);process.exit(1);});

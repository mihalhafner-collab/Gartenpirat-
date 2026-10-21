'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs');
const root=path.resolve(__dirname,'..'),url=pathToFileURL(path.join(root,'android/app/src/main/assets/mobile.html')).href;
(async()=>{
 const results=[];let browser;const errors=[],external=[];
 async function check(name,fn){try{await fn();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.stack});}console.log(JSON.stringify(results.at(-1)));}
 browser=await chromium.launch({channel:'chrome',headless:true});
 try{
  const context=await browser.newContext({viewport:{width:412,height:915},isMobile:true,hasTouch:true});
  await context.addInitScript(()=>{
   const order={id:'qa_qa-order',customer_id:'qa_qa-customer',billing_customer_id:'qa_qa-customer',title:'Testauftrag',status:'Geplant',customer_visible:1,description:'Test'};
   const master={customers:[{id:'qa_qa-customer',name:'QA Kunde',active:1}],orders:[order],worklogs:[],invoices:[],photos:[]};
   const pending=[{client_id:'qa-failed',kind:'order',payload:{...order,title:'Offen'}}];
   window.qaCalls=[];window.qaSaved=[];window.qaBlockSave=false;window.qaSync=0;
   const state=()=>({master,pending,sync_queue:{total:1,pending_upload:0,review:1,items:[{client_id:'qa-failed',state:'sync_conflict',error:'Parallel geändert; bitte prüfen.'}]}});
   window.AndroidData={getState:()=>JSON.stringify(state()),getSyncStatus:()=>JSON.stringify({configured:true,syncing:false,review:1,pending:0}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),getAppInfo:()=>JSON.stringify({versionName:'1.45.0'}),
    synchronize:()=>{qaSync++;},savePhoto:()=>qaCalls.push('business-photo'),saveOrder:()=>qaCalls.push('normal-order'),
    inspectOrder:id=>JSON.stringify({id,token:'test-token',order:{...order,title:'Offen'},current:order,customers:master.customers,checks:[{label:'Auftragsbezeichnung',ok:true,message:'In Ordnung'},{label:'Kundenzuordnung',ok:true,message:'In Ordnung'}],reasons:['Parallel geändert; bitte prüfen.']}),
    saveReviewedOrder:raw=>{const request=JSON.parse(raw);if(qaBlockSave)return 'ERROR: Der Datenstand hat sich geändert. Bitte erneut prüfen.';qaSaved.push(request);return JSON.stringify({id:request.id,saved:true});}
   };
   window.AndroidGardenPhotos={preparePicker:()=>{},setFullscreen:()=>{}};window.AndroidSpeech={start:()=>{}};window.AndroidPhone={dial:()=>{},openMaps:()=>{}};
  });
  let page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(/^https?:/.test(r.url())&&!r.url().startsWith('http://127.0.0.1:18766/'))external.push(r.url());});
  await page.goto(url);await page.waitForFunction(()=>document.body.dataset.fieldReady==='true');await page.evaluate(()=>openPage('settings-page'));await page.locator('#setting-garden-shortcut').check();await page.evaluate(()=>openPage('home'));await page.waitForSelector('#gp-launcher');
  const png=Buffer.from(await page.evaluate(()=>{const c=document.createElement('canvas');c.width=600;c.height=900;const x=c.getContext('2d');x.fillStyle='#d4e1b6';x.fillRect(0,0,600,900);x.fillStyle='#244c38';x.font='30px sans-serif';x.fillText('Nur synthetisches Testbild',40,450);return [...Uint8Array.from(atob(c.toDataURL().split(',')[1]),c=>c.charCodeAt(0))];}));
  const ready=()=>page.waitForFunction(()=>document.getElementById('gp-own-photo')?.naturalWidth>0);
  const launch=async()=>{await page.evaluate(()=>Gartenpirat.close());await page.locator('#gp-launcher').click();};
  const choose=async()=>{const [picker]=await Promise.all([page.waitForEvent('filechooser'),page.locator('[data-gp-action=choose-photo]').first().click()]);await picker.setFiles({name:'privat-test.png',mimeType:'image/png',buffer:png});await ready();};
  const options=async()=>{if(!await page.locator('.gp-photo-options').isVisible())await page.locator('[data-gp-action=photo-options]').first().click();};
  await check('Local photo is an explicit save, not an automatic business upload',async()=>{
   await launch();await choose();assert.equal(await page.evaluate(async()=>await GartenpiratPhotoStore.get()),null);await options();await page.locator('#gp-photo-auto').check();
   await page.locator('[data-gp-action=save-photo]').click();await page.waitForFunction(()=>document.querySelector('.gp-photo-message')?.textContent.startsWith('Gespeichert.'));
   const record=await page.evaluate(async()=>{const r=await GartenpiratPhotoStore.get();return {size:r.image.size,auto:r.autoShow};});assert.equal(record.size,png.length);assert.equal(record.auto,true);assert.deepEqual(await page.evaluate(()=>qaCalls),[]);assert.equal(await page.evaluate(()=>qaSync),0);
   await page.screenshot({path:path.join(root,'outputs/Gartenpirat-Bildoptionen.png')});
  });
  await check('Stored bytes and auto-open preference survive page/app-view restart',async()=>{
   await page.reload();await page.waitForFunction(()=>document.body.dataset.fieldReady==='true');await page.evaluate(()=>openPage('settings-page'));await page.locator('#setting-garden-shortcut').check();await page.evaluate(()=>openPage('home'));await page.waitForSelector('#gp-launcher');await page.locator('#gp-launcher').click();await ready();assert.equal(await page.locator('#gp-own-photo').evaluate(i=>i.naturalWidth),600);
   assert(await page.locator('#gp-dialog').evaluate(d=>d.classList.contains('gp-photo-mode')));
   await page.keyboard.press('Escape');assert.equal(await page.locator('.gp-options button').count(),4);
   await page.locator('[data-gp-action=random]').click();assert(await page.locator('.gp-portrait').isVisible());
  });
  await check('Full-screen image does not replace botanical menu or trap Back',async()=>{
   await launch();await ready();await options();await page.locator('button[data-gp-action=back]').filter({hasText:'Zum Gartenpirat'}).click();
   await page.locator('[data-gp-action=lexicon]').click();await page.locator('#gp-query').fill('Lavendel');assert(await page.locator('.gp-plant-card').count()>0);
   await page.evaluate(()=>Gartenpirat.close());assert.equal(await page.locator('#gp-dialog').getAttribute('open'),null);
  });
  await check('Auto-display can be turned off while keeping stored image',async()=>{
   await launch();await ready();await options();await page.locator('#gp-photo-auto').uncheck();await page.locator('[data-gp-action=save-photo]').click();await page.waitForFunction(()=>document.querySelector('.gp-photo-message').textContent.startsWith('Gespeichert.'));
   await page.reload();await page.locator('#gp-launcher').click();await page.waitForSelector('[data-gp-action=stored-photo]');assert.equal(await page.locator('#gp-own-photo').count(),0);
   await page.locator('[data-gp-action=stored-photo]').click();await ready();assert.equal(await page.locator('#gp-own-photo').evaluate(i=>i.naturalWidth),600);
  });
  await check('Storage failure retains previous image and its old preference',async()=>{
   await options();await page.locator('#gp-photo-auto').check();
   await page.evaluate(()=>{const old=GartenpiratPhotoStore;window.GartenpiratPhotoStore={get:old.get,put:async()=>{throw new DOMException('Full','QuotaExceededError');}};});
   await page.locator('[data-gp-action=save-photo]').click();await page.waitForFunction(()=>document.querySelector('.gp-photo-message').textContent.startsWith('Speichern nicht möglich'));
   await page.reload();await page.locator('#gp-launcher').click();await page.waitForSelector('[data-gp-action=stored-photo]');assert.equal((await page.evaluate(async()=>await GartenpiratPhotoStore.get())).autoShow,false);
   await page.evaluate(()=>Gartenpirat.close());
  });
  await check('Inspection shows exact reason and checks without saving anything',async()=>{
   await page.evaluate(()=>openPage('orders'));await page.locator('#sr-orders [data-review-order]').click();await page.waitForSelector('#order-sync-review[open]');
   assert(await page.locator('.sr-checks').innerText().then(t=>t.includes('Kundenzuordnung')));assert(await page.locator('.sr-reasons').innerText().then(t=>t.includes('Parallel geändert')));assert.deepEqual(await page.evaluate(()=>qaSaved),[]);
   await page.screenshot({path:path.join(root,'outputs/Auftrag-Pruefen.png')});
   await page.locator('#order-sync-review input[name=title]').fill('Nach Prüfung gespeichert');await page.locator('#order-sync-review select[name=status]').selectOption('In Arbeit');await page.locator('#order-sync-review [type=submit]').click();
   assert.equal(await page.locator('#order-sync-review').getAttribute('open'),null);const saved=await page.evaluate(()=>qaSaved);assert.equal(saved.length,1);assert.equal(saved[0].order.title,'Nach Prüfung gespeichert');assert.equal(saved[0].order.status,'In Arbeit');assert.equal(saved[0].token,'test-token');
  });
  await check('Stale review keeps edits visible and lets user decide later',async()=>{
   await page.locator('#sr-orders [data-review-order]').click();await page.locator('#order-sync-review input[name=title]').fill('Entwurf erhalten');await page.evaluate(()=>qaBlockSave=true);
   await page.locator('#order-sync-review [type=submit]').click();assert(await page.locator('#sr-message').innerText().then(t=>t.includes('erneut prüfen')));assert.equal(await page.locator('#order-sync-review input[name=title]').inputValue(),'Entwurf erhalten');
   page.once('dialog',d=>d.accept());await page.locator('#order-sync-review [data-sr-close]').first().click();assert.equal((await page.evaluate(()=>qaSaved)).length,1);
  });
  async function gesture(points,{cancel=false,multi=false,target='body'}={}){
   await page.evaluate(({points,cancel,multi,target})=>{
    const node=document.querySelector(target);const send=(type,x,y,count=1)=>{const e=new Event(type,{bubbles:true,cancelable:true});Object.defineProperty(e,'touches',{value:type==='touchend'||type==='touchcancel'?[]:Array.from({length:count},(_,i)=>({identifier:i,clientX:x+i,clientY:y}))});node.dispatchEvent(e);};
    send('touchstart',points[0][0],points[0][1]);for(const [x,y] of points.slice(1))send('touchmove',x,y,multi?2:1);send(cancel?'touchcancel':'touchend',points.at(-1)[0],points.at(-1)[1]);
   },{points,cancel,multi,target});
  }
  const finish=()=>page.evaluate(()=>onDriveSync({ok:false,error:'Isolierter Test',sync:{syncing:false,configured:true}}));
  await check('One deliberate downward pull triggers one sync; repeated pull while busy is ignored',async()=>{
   await page.evaluate(()=>{openPage('home');window.scrollTo(0,0);});await page.waitForTimeout(100);
   await gesture([[160,100],[162,145],[164,195]]);assert.equal(await page.evaluate(()=>qaSync),1);
   await gesture([[160,100],[162,195]]);assert.equal(await page.evaluate(()=>qaSync),1);await finish();
  });
  await check('Horizontal, short, multitouch and cancelled gestures never synchronize',async()=>{
   const before=await page.evaluate(()=>qaSync);
   await gesture([[160,100],[300,190]]);await gesture([[160,100],[160,140]]);await gesture([[160,100],[160,190]],{cancel:true});await gesture([[160,100],[160,190]],{multi:true});
   assert.equal(await page.evaluate(()=>qaSync),before);
  });
  await check('Open review dialog and working draft block pull-to-refresh',async()=>{
   const before=await page.evaluate(()=>qaSync);await page.evaluate(()=>{openPage('orders');window.scrollTo(0,0);});await page.locator('#sr-orders [data-review-order]').click();
   await gesture([[160,100],[160,200]]);assert.equal(await page.evaluate(()=>qaSync),before);
   await page.locator('#order-sync-review [data-sr-close]').first().click();await page.evaluate(()=>{openPage('work-entry');document.getElementById('activity').value='Entwurf behalten';window.scrollTo(0,0);});
   await gesture([[160,100],[160,200]]);assert.equal(await page.evaluate(()=>qaSync),before);assert.equal(await page.locator('#activity').inputValue(),'Entwurf behalten');
  });
  await check('Cancellation resets gesture and status; retry remains possible',async()=>{
   await page.evaluate(()=>{openPage('home');window.scrollTo(0,0);});await page.waitForTimeout(80);const before=await page.evaluate(()=>qaSync);
   await gesture([[160,100],[160,200]]);assert.equal(await page.evaluate(()=>qaSync),before+1);
   await page.evaluate(()=>onDriveSync({ok:false,cancelled:true,error:'Auswahl abgebrochen',sync:{configured:false,syncing:false}}));
   assert(await page.locator('#pull-refresh').innerText().then(t=>t.includes('abgebrochen')));
   await gesture([[160,100],[160,200]]);assert.equal(await page.evaluate(()=>qaSync),before+2);await finish();
  });
  await check('Pull refresh recovers after a background synchronization finishes without a foreground callback',async()=>{
   await page.evaluate(()=>{openPage('home');window.scrollTo(0,0);onDriveSyncStarted({syncing:true});});
   const before=await page.evaluate(()=>qaSync);
   await gesture([[160,100],[160,200]]);assert.equal(await page.evaluate(()=>qaSync),before+1);await finish();
  });
  await check('Gartenpirat photo and order review are independent and make no hidden business writes',async()=>{
   assert.deepEqual(await page.evaluate(()=>qaCalls),[]);assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
  });
  await check('Stored photo survives an actual browser process restart in the same profile',async()=>{
   const profile=fs.mkdtempSync(path.join(require('node:os').tmpdir(),'qa_photo_profile_'));let persistent;
   const boot=async()=>{
    persistent=await chromium.launchPersistentContext(profile,{channel:'chrome',headless:true,viewport:{width:412,height:915}});
    await persistent.addInitScript(()=>{const master={customers:[],orders:[],worklogs:[],invoices:[],photos:[]};window.AndroidData={getState:()=>JSON.stringify({master,pending:[]}),getSyncStatus:()=>JSON.stringify({configured:false,pending:0}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),getAppInfo:()=>JSON.stringify({versionName:'1.45.0'})};window.AndroidSpeech={start:()=>{}};window.AndroidPhone={};});
    const p=persistent.pages()[0];await p.goto(url);await p.waitForFunction(()=>document.body.dataset.fieldReady==='true');await p.evaluate(()=>openPage('settings-page'));await p.locator('#setting-garden-shortcut').check();await p.evaluate(()=>openPage('home'));await p.waitForSelector('#gp-launcher');return p;
   };
   try{
    let p=await boot();await p.evaluate(async bytes=>{await GartenpiratPhotoStore.put(new File([new Uint8Array(bytes)],'neustart.png',{type:'image/png'}),true);},[...png]);await persistent.close();persistent=null;
    p=await boot();await p.locator('#gp-launcher').click();await p.waitForFunction(()=>document.getElementById('gp-own-photo')?.naturalWidth===600);
    assert.equal((await p.evaluate(async()=>await GartenpiratPhotoStore.get())).autoShow,true);
   }finally{await persistent?.close();}
  });
  await context.close();
 }finally{await browser?.close();}
 fs.writeFileSync(path.join(root,'outputs/Garden-followup-tests.json'),JSON.stringify({method:'real browser/IndexedDB/image decoding; Android bridges mocked; synthetic gesture events',device_acceptance:'NOT_PERFORMED',results},null,2));
 if(results.some(r=>r.result!=='PASS'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});

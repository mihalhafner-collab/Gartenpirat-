/* Real operative assets in Cloud Chromium; isolated bridge, no device/account writes. */
'use strict';
const assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs'),{pathToFileURL}=require('node:url'),{chromium}=require('./browser.cjs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']}),results=[];
 async function check(name,run){try{await run();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.stack});}console.log(results.at(-1));}
 try{
  const page=await browser.newPage({viewport:{width:360,height:900}}),errors=[];page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(10000);
  await page.addInitScript(()=>{
   window.fixture={master:{customers:[{id:'c',name:'QA Testkunde',active:1,updated_at:'t1'}],orders:[{id:'o',customer_id:'c',title:'QA Testauftrag',updated_at:'t1'}],worklogs:[],invoices:[],photos:[],offers:[],service_catalog:[]},pending:[]};
   window.archive=Array.from({length:65},(_,i)=>({archive_key:'order:old-'+i,record_hash:'archive-hash-'+i,entry:{record:{id:'old-'+i,customer_id:'c',title:i===0?'<script>QA untrusted</script>':'QA Papierkorb '+i},archived_at:'2026-10-18T12:00:00Z'}}));
   window.calls=[];window.block=false;window.stale=false;window.failDelete=false;window.failRestore=false;window.retryCount=0;
   window.items=[{client_id:'queued',kind:'order',entity_id:'o',state:'sync_pending',next_action:'retry_sync',message:'Lokal gespeichert · Übertragung ausstehend.'},{client_id:'review',kind:'customer',entity_id:'c',state:'sync_conflict',next_action:'compare_versions',message:'QA Versionsprüfung'}];
   window.statusFixture={configured:true,state:'retry_wait',title:'Erneuter Versuch folgt',detail:'Auf diesem Gerät gesichert. Automatischer Versuch folgt.',last_error:'QA Verbindung unterbrochen',last_error_code:'TRANSFER_FAILED',phase:'reading',retry_count:2,last_sync_attempt:'2026-10-18T12:00:00Z',next_retry:1792328400000,transport:'android_provider',confirmation_scope:'provider_readback',cloud_confirmed:false,pending:1,review:1};
   const ok=data=>JSON.stringify({ok:true,data}),bad=message=>JSON.stringify({ok:false,error:{code:'WRITE_CONFLICT',message}});
   window.AndroidData={getState:()=>JSON.stringify({...fixture,sync_queue:{items,total:items.length,pending_upload:1,review:1}}),getSyncStatus:()=>JSON.stringify(statusFixture),getSyncDetails:()=>JSON.stringify({items}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),getAppInfo:()=>JSON.stringify({versionName:'fixture'}),getOwnerSettings:()=>JSON.stringify({}),retryDrive:()=>retryCount++,synchronize:()=>retryCount++,connectDrive:()=>{},ownerCall:(name,raw)=>{
    const a=JSON.parse(raw);calls.push({name,args:a});
    if(name==='preview_delete')return ok({allowed:!block,message:block?'Zugeordnete Fotos zuerst prüfen':'Im Papierkorb wiederherstellbar',expected_record_hash:'current-order-hash',updated_at:stale?'t2':'t1'});
    if(name==='delete_entity'){
     if(failDelete)return bad('Neue Version – nichts gelöscht');const row=fixture.master.orders.find(x=>x.id===a.entity_id);if(!row)return bad('Nicht vorhanden');archive.unshift({archive_key:a.entity+':'+a.entity_id,record_hash:'new-archive-hash',entry:{record:row,archived_at:'2026-10-18T13:00:00Z'}});fixture.master.orders=fixture.master.orders.filter(x=>x.id!==row.id);return ok({entity_id:row.id,local_state:'local_saved',cloud_confirmed:false});
    }
    if(name==='list_archive'){let rows=archive.filter(x=>(!a.entity||x.archive_key.split(':')[0]===a.entity)&&(!a.entity_id||x.archive_key.split(':')[1]===a.entity_id));return ok({archive:rows.slice(a.offset||0,(a.offset||0)+(a.limit||20)),total:rows.length});}
    if(name==='restore_entity'){
     if(failRestore)return bad('Archivversion geändert – Original bleibt erhalten');const x=archive.find(x=>x.archive_key===a.entity+':'+a.entity_id);if(!x||a.expected_record_hash!==x.record_hash)return bad('Hash falsch');fixture.master.orders.push(x.entry.record);archive=archive.filter(y=>y!==x);return ok({local_state:'local_saved',cloud_confirmed:false});
    }
    if(name==='get_settings')return ok({settings:{},record_hash:'settings-hash'});
    return bad('Synthetisch nicht benötigter Pfad');
   }};
   window.AndroidSpeech={start:()=>{}};window.AndroidPhone={dial:()=>{},openMaps:()=>{}};window.AndroidGardenPhotos={setFullscreen:()=>{}};
  });
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);await page.waitForFunction(()=>window.TrashUI&&document.getElementById('trash-page'));await page.evaluate(()=>openPage('sync-page'));
  await check('One visible sync action with shared retry status and separate reviews',async()=>{
   assert(await page.locator('#sync').isVisible());assert.equal(await page.locator('#sync').count(),1);assert.match(await page.locator('#sync-status').innerText(),/Erneuter Versuch folgt/);assert.match(await page.locator('#sync-transfer-details').innerText(),/1 Übertragungen · 1 Prüfungen/);assert.equal(await page.locator('#sync-reviews').evaluate(x=>x.open),false);
   await page.locator('#sync').click();assert.equal(await page.evaluate(()=>retryCount),1);assert.equal(await page.evaluate(()=>statusFixture.cloud_confirmed),false);
  });
  await check('Transport diagnostics contain phase and error without claiming a server receipt',async()=>{
   await page.locator('#sync-technical summary').click();assert.match(await page.locator('[data-sync-diagnostics]').innerText(),/TRANSFER_FAILED/);assert.match(await page.locator('[data-sync-diagnostics]').innerText(),/Drive lesen/);assert.doesNotMatch(await page.locator('#sync-status').innerText(),/Vollständig/);
  });
  await check('Blocked relationships never enqueue a delete',async()=>{
   await page.evaluate(()=>block=true);assert.equal(await page.evaluate(()=>TrashUI.remove('order','o')),false);assert.equal(await page.evaluate(()=>calls.filter(x=>x.name==='delete_entity').length),0);assert.match(await page.locator('#mobile-toast').innerText(),/Fotos zuerst prüfen/);
  });
  await check('A changed displayed version is rejected before sending deletion',async()=>{
   await page.evaluate(()=>{block=false;stale=true;});assert.equal(await page.evaluate(()=>TrashUI.remove('order','o')),false);assert.equal(await page.evaluate(()=>calls.filter(x=>x.name==='delete_entity').length),0);
  });
  await check('Failed CAS retains data and retry uses the same request ID',async()=>{
   await page.evaluate(()=>{stale=false;failDelete=true;});assert.equal(await page.evaluate(()=>TrashUI.remove('order','o')),false);assert.equal(await page.evaluate(()=>fixture.master.orders.length),1);const request=await page.evaluate(()=>calls.filter(x=>x.name==='delete_entity').at(-1).args.request_id);
   await page.evaluate(()=>failDelete=false);assert.equal(await page.evaluate(()=>TrashUI.remove('order','o')),true);assert.equal(await page.evaluate(()=>calls.filter(x=>x.name==='delete_entity').at(-1).args.request_id),request);assert.equal(await page.evaluate(()=>fixture.master.orders.length),0);assert.match(await page.locator('#mobile-toast').innerText(),/Papierkorb/);
  });
  await check('Undo uses filtered archive hash and restore rather than dropping a queue action',async()=>{
   await page.locator('.undo-toast-button').click();assert.equal(await page.evaluate(()=>fixture.master.orders[0].id),'o');const a=await page.evaluate(()=>calls.filter(x=>x.name==='restore_entity').at(-1).args);assert.equal(a.expected_record_hash,'new-archive-hash');assert.equal(a.entity_id,'o');assert.equal(await page.evaluate(()=>calls.filter(x=>x.name==='list_archive'&&x.args.entity_id==='o').length),1);
  });
  await check('Trash pagination keeps 65 records accessible in bounded views',async()=>{
   await page.evaluate(()=>TrashUI.open());assert.equal(await page.locator('[data-trash-restore]').count(),30);await page.locator('#trash-more').click();assert.equal(await page.locator('[data-trash-restore]').count(),60);await page.locator('#trash-more').click();assert.equal(await page.locator('[data-trash-restore]').count(),65);assert.equal(await page.locator('#trash-content script').count(),0);assert.match(await page.locator('#trash-content').innerText(),/<script>QA untrusted/);
  });
  await check('Restore conflict keeps archive visible and enables retry',async()=>{
   await page.evaluate(()=>failRestore=true);await page.locator('[data-trash-restore="0"]').click();assert.equal(await page.locator('[data-trash-restore]').count(),65);assert.equal(await page.locator('[data-trash-restore="0"]').isEnabled(),true);assert.match(await page.locator('#mobile-toast').innerText(),/Archivversion geändert/);await page.evaluate(()=>failRestore=false);
  });
  await check('Restore succeeds with original ID and no unrelated removal',async()=>{
   const id=await page.evaluate(()=>archive[0].entry.record.id);await page.locator('[data-trash-restore="0"]').click();assert(await page.evaluate(id=>fixture.master.orders.some(x=>x.id===id),id));assert.equal(await page.evaluate(()=>archive.length),64);assert.equal(await page.locator('[data-trash-restore]').count(),30);
  });
  await check('Failed deletion can only be withdrawn with original action and current hashes',async()=>{
   await page.evaluate(()=>{items=[{client_id:'failed-delete',kind:'archive',entity_id:'o',state:'sync_conflict',next_action:'compare_versions',message:'QA neue Version'}];AndroidData.getSyncConflict=()=>JSON.stringify({conflicts:[{resolution_available:true,allowed_resolutions:['keep_current'],action_hash:'original-action-hash',current_hash:'current-record-hash',current:{id:'o',title:'QA neu'},action:{kind:'archive',payload:{entity:'order',id:'o',_archive_record:{id:'o',title:'QA alt'},_record_base:{id:'o',title:'QA alt'}}}}]});AndroidData.resolveSyncConflict=raw=>{window.withdrawal=JSON.parse(raw);return JSON.stringify({ok:true});};openPage('sync-page');});
   await page.locator('#sync-reviews').evaluate(d=>d.open=true);await page.locator('[data-sync-conflict]').click();assert.equal(await page.locator('[data-resolution="apply_proposed"]').count(),0);assert.match(await page.locator('#sync-conflict-review').innerText(),/Löschauftrag zurücknehmen/);page.once('dialog',d=>d.accept());await page.locator('[data-resolution="keep_current"]').click();const a=await page.evaluate(()=>withdrawal);assert.equal(a.resolution,'keep_current');assert.equal(a.expected_action_hash,'original-action-hash');assert.equal(a.expected_current_hash,'current-record-hash');
  });
  await check('Permission action remains clear and does not assert connectivity',async()=>{
   await page.evaluate(()=>{openPage('sync-page');syncInfo={...statusFixture,state:'action_required',title:'Drive-Freigabe prüfen',detail:'Bestehenden Drive-Ordner erneut auswählen.',last_error_code:'PERMISSION_REQUIRED'};syncLabel();});assert.match(await page.locator('#sync-title').innerText(),/Freigabe prüfen/);assert.match(await page.locator('#sync-detail').innerText(),/Ordner erneut/);
  });
  await check('Sync and trash remain readable at 320/360/426 and enlarged text',async()=>{
   for(const width of [320,360,426]){await page.setViewportSize({width,height:900});await page.evaluate(()=>document.documentElement.style.fontSize='24px');for(const target of ['sync','trash']){await page.evaluate(target=>target==='trash'?TrashUI.open():openPage('sync-page'),target);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));const button=page.locator(target==='sync'?'#sync':'[data-trash-restore="0"]');assert((await button.boundingBox()).height>=44);}}
   assert.deepEqual(errors,[]);
  });
  fs.mkdirSync('outputs',{recursive:true});fs.writeFileSync('outputs/sync-trash-results.json',JSON.stringify({environment:'Cloud Chromium',bridge:'synthetic',device:false,results},null,2));if(results.some(x=>x.result==='FAIL'))process.exitCode=1;
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});

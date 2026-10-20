/* Real operative assets in Cloud Chromium; isolated bridge, no device/account writes. */
'use strict';
const assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs'),{pathToFileURL}=require('node:url'),{chromium}=require('./browser.cjs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']}),results=[];
 async function check(name,run){try{await run();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.stack});}console.log(results.at(-1));}
 try{
  const page=await browser.newPage({viewport:{width:360,height:900}}),errors=[];page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(10000);
  await page.addInitScript(()=>{
   window.fixture={master:{customers:[{id:'qa_c',name:'QA Testkunde',active:1,updated_at:'t1'}],orders:[{id:'qa_o',customer_id:'qa_c',title:'QA Testauftrag',updated_at:'t1'}],worklogs:[],invoices:[],photos:[],offers:[],service_catalog:[]},pending:[]};
   window.archive=Array.from({length:65},(_,i)=>({archive_key:'order:qa_old-'+i,record_hash:'archive-hash-'+i,entry:{record:{id:'qa_old-'+i,customer_id:'qa_c',title:i===0?'<script>QA untrusted</script>':'QA Papierkorb '+i},archived_at:'2026-10-18T12:00:00Z'}}));
   window.calls=[];window.block=false;window.stale=false;window.failDelete=false;window.failRestore=false;window.failPurge=false;window.retryCount=0;
   window.items=[{client_id:'queued',kind:'order',entity_id:'qa_o',state:'sync_pending',next_action:'retry_sync',message:'Lokal gespeichert · Übertragung ausstehend.'},{client_id:'review',kind:'customer',entity_id:'qa_c',state:'sync_conflict',next_action:'compare_versions',message:'QA Versionsprüfung'}];
   window.statusFixture={configured:true,state:'retry_wait',title:'Erneuter Versuch folgt',detail:'Auf diesem Gerät gesichert. Automatischer Versuch folgt.',last_error:'QA Verbindung unterbrochen',last_error_code:'TRANSFER_FAILED',phase:'reading',retry_count:2,last_sync_attempt:'2026-10-18T12:00:00Z',next_retry:1792328400000,transport:'android_provider',confirmation_scope:'provider_readback',cloud_confirmed:false,pending:1,review:1};
   const ok=data=>JSON.stringify({ok:true,data}),bad=message=>JSON.stringify({ok:false,error:{code:'WRITE_CONFLICT',message}});
   window.AndroidData={getState:()=>JSON.stringify({...fixture,sync_queue:{items,total:items.length,pending_upload:1,review:1}}),getSyncStatus:()=>JSON.stringify(statusFixture),getSyncDetails:()=>JSON.stringify({items}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),getAppInfo:()=>JSON.stringify({versionName:'fixture'}),getOwnerSettings:()=>JSON.stringify({}),retryDrive:()=>retryCount++,synchronize:()=>retryCount++,connectDrive:()=>{},ownerCall:(name,raw)=>{
    const a=JSON.parse(raw);calls.push({name,args:a});
    if(name==='preview_delete')return ok({allowed:!block,message:block?'Zugeordnete Fotos zuerst prüfen':'Im Papierkorb wiederherstellbar',expected_record_hash:'current-order-hash',updated_at:stale?'t2':'t1'});
    if(name==='delete_entity'){
     if(failDelete)return bad('Neue Version – nichts gelöscht');const group=a.entity==='photo'?'photos':'orders',row=fixture.master[group].find(x=>x.id===a.entity_id);if(!row)return bad('Nicht vorhanden');archive.unshift({archive_key:a.entity+':'+a.entity_id,record_hash:'new-archive-hash',entry:{record:row,archived_at:'2026-10-18T13:00:00Z'}});fixture.master[group]=fixture.master[group].filter(x=>x.id!==row.id);return ok({entity_id:row.id,local_state:'local_saved',cloud_confirmed:false});
    }
    if(name==='preview_purge'){const x=archive.find(x=>x.archive_key===a.entity+':'+a.entity_id);return x?ok({allowed:true,expected_record_hash:x.record_hash}):bad('Nicht im Papierkorb');}
    if(name==='preview_empty_trash')return ok({total:archive.length,count:archive.length,blocked:[],preview_hash:'empty-'+archive.map(x=>x.record_hash).join('|')});
    if(name==='empty_trash'){if(failPurge)return bad('Papierkorb wurde geändert');if(a.confirmed!==true||a.expected_preview_hash!=='empty-'+archive.map(x=>x.record_hash).join('|'))return bad('Ungültige Vorschau');const deleted=archive.length;archive=[];return ok({deleted,cloud_confirmed:false});}
    if(name==='purge_entity'){if(failPurge)return bad('Archiv wurde geändert');const x=archive.find(x=>x.archive_key===a.entity+':'+a.entity_id);if(!x||x.record_hash!==a.expected_record_hash||a.confirmed!==true)return bad('Ungültiger Löschvergleich');archive=archive.filter(v=>v!==x);return ok({local_state:'local_saved'});}
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
   assert(await page.locator('#sync').isVisible());assert.equal(await page.locator('#sync').count(),1);assert.match(await page.locator('#sync-status').innerText(),/Prüfung erforderlich/);assert.match(await page.locator('#sync-transfer-details').innerText(),/Bitte 1 Änderung/);assert.equal(await page.locator('#sync-reviews').evaluate(x=>x.open),false);
   await page.locator('#sync').click();assert.equal(await page.evaluate(()=>retryCount),1);assert.equal(await page.evaluate(()=>statusFixture.cloud_confirmed),false);
  });
  await check('Transport diagnostics contain phase and error without claiming a server receipt',async()=>{
   await page.locator('#sync-technical summary').click();assert.match(await page.locator('[data-sync-diagnostics]').innerText(),/TRANSFER_FAILED/);assert.match(await page.locator('[data-sync-diagnostics]').innerText(),/Drive lesen/);assert.doesNotMatch(await page.locator('#sync-status').innerText(),/Vollständig/);
  });
  await check('Blocked relationships never enqueue a delete',async()=>{
   await page.evaluate(()=>block=true);assert.equal(await page.evaluate(()=>TrashUI.remove('order','qa_o')),false);assert.equal(await page.evaluate(()=>calls.filter(x=>x.name==='delete_entity').length),0);assert.match(await page.locator('#mobile-toast').innerText(),/Fotos zuerst prüfen/);
  });
  await check('A changed displayed version is rejected before sending deletion',async()=>{
   await page.evaluate(()=>{block=false;stale=true;});assert.equal(await page.evaluate(()=>TrashUI.remove('order','qa_o')),false);assert.equal(await page.evaluate(()=>calls.filter(x=>x.name==='delete_entity').length),0);
  });
  await check('Failed CAS retains data and retry uses the same request ID',async()=>{
   await page.evaluate(()=>{stale=false;failDelete=true;});assert.equal(await page.evaluate(()=>TrashUI.remove('order','qa_o')),false);assert.equal(await page.evaluate(()=>fixture.master.orders.length),1);const request=await page.evaluate(()=>calls.filter(x=>x.name==='delete_entity').at(-1).args.request_id);
   await page.evaluate(()=>failDelete=false);assert.equal(await page.evaluate(()=>TrashUI.remove('order','qa_o')),true);assert.equal(await page.evaluate(()=>calls.filter(x=>x.name==='delete_entity').at(-1).args.request_id),request);assert.equal(await page.evaluate(()=>fixture.master.orders.length),0);assert.match(await page.locator('#mobile-toast').innerText(),/Papierkorb/);
  });
  await check('Undo uses filtered archive hash and restore rather than dropping a queue action',async()=>{
   await page.locator('.undo-toast-button').click();assert.equal(await page.evaluate(()=>fixture.master.orders[0].id),'qa_o');const a=await page.evaluate(()=>calls.filter(x=>x.name==='restore_entity').at(-1).args);assert.equal(a.expected_record_hash,'new-archive-hash');assert.equal(a.entity_id,'qa_o');assert.equal(await page.evaluate(()=>calls.filter(x=>x.name==='list_archive'&&x.args.entity_id==='qa_o').length),1);
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
  await check('Permanent removal requires confirmation; cancel preserves the original archive',async()=>{
   const x=await page.evaluate(()=>archive[0]);const [entity,entity_id]=x.archive_key.split(':');const before=await page.evaluate(()=>archive.length);page.once('dialog',d=>d.dismiss());assert.equal(await page.evaluate(a=>TrashUI.purge(a.entity,a.entity_id,a.expected),{entity,entity_id,expected:x.record_hash}),false);assert.equal(await page.evaluate(()=>archive.length),before);assert.equal(await page.evaluate(()=>calls.filter(x=>x.name==='purge_entity').length),0);
  });
  await check('Stale trash view cannot permanently remove a changed archive',async()=>{
   const x=await page.evaluate(()=>archive[0]);const [entity,id]=x.archive_key.split(':');assert.equal(await page.evaluate(a=>TrashUI.purge(a.entity,a.id,'stale-view'),{entity,id}),false);assert.equal(await page.evaluate(()=>calls.filter(x=>x.name==='purge_entity').length),0);assert.match(await page.locator('#mobile-toast').innerText(),/Inzwischen.*geändert/);
  });
  await check('Purge failure keeps restore available and retry retains its request ID',async()=>{
   await page.evaluate(()=>{failPurge=true;TrashUI.open();});const before=await page.evaluate(()=>archive.length);page.once('dialog',d=>d.accept());await page.locator('[data-trash-purge="0"]').click();assert.equal(await page.evaluate(()=>archive.length),before);assert(await page.locator('[data-trash-restore="0"]').isEnabled());const first=await page.evaluate(()=>calls.filter(x=>x.name==='purge_entity').at(-1).args);assert.equal(first.confirmed,true);
   await page.evaluate(()=>failPurge=false);page.once('dialog',d=>d.accept());await page.locator('[data-trash-purge="0"]').click();assert.equal(await page.evaluate(()=>archive.length),before-1);assert.equal(await page.evaluate(()=>calls.filter(x=>x.name==='purge_entity').at(-1).args.request_id),first.request_id);assert.match(await page.locator('#mobile-toast').innerText(),/Endgültig/);
  });
  await check('Settings are grouped on their existing page; More contains navigation',async()=>{
   assert.equal(await page.locator('#settings-page #photo-quality').count(),1);assert.equal(await page.locator('#more-page #photo-quality').count(),0);assert.equal(await page.locator('#settings-page [data-business-editor]').count(),1);
  });
  await check('More groups all existing destinations and preserves native handlers',async()=>{
   await page.waitForSelector('[data-more-group="data"]',{state:'attached'});
   assert.equal(await page.locator('#more-page [data-more-group]').count(),4);
   assert.equal(await page.locator('[data-more-group="work"] [data-open="orders"]').count(),1);
   assert.equal(await page.locator('[data-more-group="billing"] [data-open="invoices"]').count(),1);
   assert.equal(await page.locator('[data-more-group="media"] [data-open-garden-menu]').count(),1);
   assert.equal(await page.locator('[data-more-group="data"] [data-open-trash]').count(),1);
   assert.equal(await page.locator('#sync-page [data-open-trash]').count(),0);
  });
  await check('More labels stay readable at narrow widths and 150 percent font',async()=>{
   for(const width of [320,360,426]){await page.setViewportSize({width,height:1000});await page.evaluate(()=>{document.documentElement.style.fontSize='24px';openPage('more-page');});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));const labels=await page.locator('#more-page .more-destinations button strong').evaluateAll(nodes=>nodes.map(n=>{const r=n.getBoundingClientRect();return{width:r.width,height:r.height,text:n.textContent};}));assert(labels.length>=9);assert(labels.every(x=>x.width>=140&&x.height<130&&x.text.trim().length));for(const b of await page.locator('#more-page .more-destinations button').all())assert((await b.boundingBox()).height>=44);}
   await page.setViewportSize({width:320,height:1000});await page.screenshot({path:'outputs/more75-320.png',fullPage:true});await page.evaluate(()=>document.documentElement.style.fontSize='16px');
  });
  await check('105 unassigned photos remain reachable with bounded lazy thumbnails',async()=>{
   await page.evaluate(()=>{fixture.master.photos=Array.from({length:105},(_,i)=>({id:'qa_p'+i,customer_id:'',caption:'QA Foto '+i,updated_at:'t1',thumbnail_data:'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j8S0AAAAASUVORK5CYII='}));load();openPage('more-page');});await page.locator('#more-page .more-destinations button').filter({has:page.locator('strong', {hasText:/^Fotos$/})}).click();await page.locator('#photo-library-scope').selectOption('unassigned');assert.equal(await page.locator('#photo-library-results [data-design-action="photo"]').count(),30);await page.locator('[data-library-next]').click();assert.equal(await page.locator('#photo-library-results [data-design-action="photo"]').count(),60);assert.equal(await page.locator('#photo-library-results img:not([loading="lazy"])').count(),0);assert.match(await page.locator('#photo-library-count').innerText(),/105/);
  });
  await check('Linked photo can be moved to trash through the single More menu',async()=>{
   await page.evaluate(()=>{fixture.master.photos[0].customer_id='qa_c';fixture.master.photos[0].order_id='qa_o';load();PhotoLibraryUI.refresh();});await page.locator('#photo-library-scope').selectOption('all');await page.locator('[data-design-action="photo"][data-id="qa_p0"]').click();assert(await page.locator('.design-photo-delete').isEnabled());assert.equal(await page.locator('.design-photo-delete').isVisible(),false);await page.locator('.photo-more summary').click();await page.screenshot({path:'outputs/trash-photo-menu360.png'});page.once('dialog',d=>d.accept());await page.locator('.design-photo-delete').click();assert.equal(await page.evaluate(()=>fixture.master.photos.some(x=>x.id==='qa_p0')),false);assert.equal(await page.evaluate(()=>archive.some(x=>x.archive_key==='photo:qa_p0'&&x.entry.record.customer_id==='qa_c')),true);
  });
  await check('Failed deletion can only be withdrawn with original action and current hashes',async()=>{
   await page.evaluate(()=>{items=[{client_id:'failed-delete',kind:'archive',entity_id:'qa_o',state:'sync_conflict',next_action:'compare_versions',message:'QA neue Version'}];AndroidData.getSyncConflict=()=>JSON.stringify({conflicts:[{resolution_available:true,allowed_resolutions:['keep_current'],action_hash:'original-action-hash',current_hash:'current-record-hash',current:{id:'qa_o',title:'QA neu'},action:{kind:'archive',payload:{entity:'order',id:'qa_o',_archive_record:{id:'qa_o',title:'QA alt'},_record_base:{id:'qa_o',title:'QA alt'}}}}]});AndroidData.resolveSyncConflict=raw=>{window.withdrawal=JSON.parse(raw);return JSON.stringify({ok:true});};openPage('sync-page');});
   await page.locator('#sync-reviews').evaluate(d=>d.open=true);await page.locator('[data-sync-conflict]').click();assert.equal(await page.locator('[data-resolution="apply_proposed"]').count(),0);assert.match(await page.locator('#sync-conflict-review').innerText(),/Löschauftrag zurücknehmen/);page.once('dialog',d=>d.accept());await page.locator('[data-resolution="keep_current"]').click();const a=await page.evaluate(()=>withdrawal);assert.equal(a.resolution,'keep_current');assert.equal(a.expected_action_hash,'original-action-hash');assert.equal(a.expected_current_hash,'current-record-hash');
  });
  await check('Permission action remains clear and does not assert connectivity',async()=>{
   await page.evaluate(()=>{openPage('sync-page');syncInfo={...statusFixture,state:'action_required',title:'Drive-Freigabe prüfen',detail:'Bestehenden Drive-Ordner erneut auswählen.',last_error_code:'PERMISSION_REQUIRED'};syncLabel();});assert.match(await page.locator('#sync-title').innerText(),/Verbindung freigeben/);assert.match(await page.locator('#sync-detail').innerText(),/Verbindung erneut freigeben/);
  });
  await check('Sync and trash remain readable at 320/360/426 and enlarged text',async()=>{
   for(const width of [320,360,426]){await page.setViewportSize({width,height:900});await page.evaluate(()=>document.documentElement.style.fontSize='24px');for(const target of ['sync','trash']){await page.evaluate(target=>target==='trash'?TrashUI.open():openPage('sync-page'),target);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));const button=page.locator(target==='sync'?'#sync':'[data-trash-restore="0"]');assert((await button.boundingBox()).height>=44);}}
   await page.screenshot({path:'outputs/trash320.png'});assert.deepEqual(errors,[]);
  });
  await check('Empty trash covers every page, supports cancel and durable retry, and preserves active data',async()=>{
   await page.evaluate(()=>{archive=Array.from({length:136},(_,i)=>({archive_key:'worklog:qa_trash-'+i,record_hash:'trash-hash-'+i,entry:{record:{id:'qa_trash-'+i,activity:'QA alte Arbeit '+i},archived_at:'2026-07-31'}}));TrashUI.open();});
   const active=await page.evaluate(()=>JSON.stringify(fixture.master));assert.equal(await page.locator('[data-trash-restore]').count(),30);assert(await page.locator('#trash-empty').isEnabled());
   page.once('dialog',d=>{assert.match(d.message(),/136/);d.dismiss();});assert.equal(await page.evaluate(()=>TrashUI.empty()),false);assert.equal(await page.evaluate(()=>archive.length),136);
   await page.evaluate(()=>failPurge=true);page.once('dialog',d=>d.accept());assert.equal(await page.evaluate(()=>TrashUI.empty()),false);const first=await page.evaluate(()=>calls.filter(x=>x.name==='empty_trash').at(-1).args.request_id);assert.equal(await page.evaluate(()=>archive.length),136);
   await page.evaluate(()=>failPurge=false);page.once('dialog',d=>d.accept());assert.equal(await page.evaluate(()=>TrashUI.empty()),true);assert.equal(await page.evaluate(()=>calls.filter(x=>x.name==='empty_trash').at(-1).args.request_id),first);assert.equal(await page.locator('[data-trash-restore]').count(),0);assert(await page.locator('#trash-empty').isDisabled());assert.equal(await page.evaluate(()=>JSON.stringify(fixture.master)),active);
  });
  fs.mkdirSync('outputs',{recursive:true});fs.writeFileSync('outputs/sync-trash-results.json',JSON.stringify({environment:'Cloud Chromium',bridge:'synthetic',device:false,results},null,2));if(results.some(x=>x.result==='FAIL'))process.exitCode=1;
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});

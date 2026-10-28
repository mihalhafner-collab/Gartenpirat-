'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url'),{chromium}=require('./browser.cjs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']}),results=[];
 const check=async(name,run)=>{try{await run();results.push({name,result:'PASS'});}catch(error){results.push({name,result:'FAIL',error:error.stack});}console.log(results.at(-1));};
 try{
  const page=await browser.newPage({viewport:{width:360,height:900}}),errors=[];page.on('pageerror',error=>errors.push(error.message));page.setDefaultTimeout(8000);
  await page.addInitScript(()=>{
   const image='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j8S0AAAAASUVORK5CYII=';
   window.master={customers:[{id:'qa_z',name:'Zeta QA',active:1},{id:'qa_10',name:'Ähren 10 QA',active:1},{id:'qa_2',name:'Ähren 2 QA',active:1},{id:'qa_m',name:'Müller QA',street:'QA Grauenrain',phone:'+43 (664) 123-4567',active:1,updated_at:'qa_t1'}],orders:[{id:'qa_o',customer_id:'qa_m',title:'QA Rasen',status:'Geplant',updated_at:'qa_t1'}],worklogs:[{id:'qa_w',customer_id:'qa_m',activity:'QA Rasen',work_date:'2026-10-21',hours:1,updated_at:'qa_t1'}],photos:Array.from({length:6},(_,i)=>({id:'qa_p'+i,customer_id:'qa_m',caption:'QA Garten '+i,original_name:'QA-Datei-'+i+'.jpg',storage_name:'a'.repeat(64)+'.jpg',content_sha256:'a'.repeat(64),thumbnail_data:image,updated_at:'qa_t1'})),invoices:[{id:'qa_i',customer_id:'qa_m',invoice_number:'QA-R-17',status:'Gestellt',issued_at:'2026-10-21',amount:10,updated_at:'qa_t1'}],offers:[{id:'qa_f',customer_id:'qa_m',title:'QA Pflege',offer_number:'QA-KV-0042',document_type:'fixed_price',status:'draft',positions:[],updated_at:'qa_t1'}],service_catalog:[{id:'qa_s',title:'QA Baustein',category:'QA',unit:'h',default_price:1,active:true,updated_at:'qa_t1'}],appointments:[{id:'qa_a',customer_id:'qa_m',title:'QA Einsatz',status:'tentative',starts_at:'2099-10-21T08:00:00Z',ends_at:'2099-10-21T09:00:00Z',timezone:'Europe/Vienna',updated_at:'qa_t1'}]};
   window.calls=[];window.readFailure=false;window.statusFailure=false;window.failSave=false;window.archive=[];
   const groups={customer:'customers',order:'orders',worklog:'worklogs',photo:'photos',invoice:'invoices',offer:'offers',service_template:'service_catalog',appointment:'appointments'};
   const okay=data=>JSON.stringify({ok:true,data}),bad=message=>JSON.stringify({ok:false,error:{code:'WRITE_CONFLICT',message}});
   window.AndroidData={getState:()=>readFailure?'ERROR: QA unavailable':JSON.stringify({master,pending:[],work_session:{state:'IDLE'}}),getSyncStatus:()=>{if(statusFailure)throw Error('QA status unavailable');return JSON.stringify({configured:false});},getWorkSession:()=>JSON.stringify({state:'IDLE'}),getAppInfo:()=>JSON.stringify({versionName:'qa'}),getOwnerSettings:()=>JSON.stringify({}),getPhotoData:()=>image,saveCustomer:raw=>{if(failSave)return'ERROR: QA not saved';const row=JSON.parse(raw),id=row.id||'qa_new';master.customers=master.customers.filter(x=>x.id!==id);master.customers.push({...row,id,updated_at:'qa_t2'});return id;},ownerCall:(name,raw)=>{
    const a=JSON.parse(raw);calls.push({name,args:a});
    if(name==='preview_delete'){const row=master[groups[a.entity]]?.find(row=>row.id===a.entity_id);return okay({allowed:!!row&&!row.issued_at,expected_record_hash:'qa_hash_'+a.entity_id,updated_at:row?.updated_at||'',message:row?.issued_at?'Wichtiger Beleg bleibt geschützt.':'Im Papierkorb wiederherstellbar.'});}
    if(name==='delete_entity'){const rows=master[groups[a.entity]],row=rows.find(row=>row.id===a.entity_id);if(!row||a.expected_record_hash!=='qa_hash_'+row.id)return bad('QA version changed');archive.push({archive_key:a.entity+':'+row.id,record_hash:'qa_archive_'+row.id,entry:{record:row,archived_at:'2026-10-21'}});master[groups[a.entity]]=rows.filter(x=>x!==row);return okay({entity_id:row.id});}
    if(name==='list_archive')return okay({archive:archive.slice(a.offset||0,(a.offset||0)+(a.limit||30)),total:archive.length});
    if(name==='preview_purge')return okay({allowed:true,expected_record_hash:'qa_archive_'+a.entity_id});
    if(name==='get_revier_overview')return okay({tour:{stops:[]},due_visits:[]});
    if(name==='get_settings')return okay({settings:{},record_hash:'a'.repeat(64)});
    if(name==='get_business_settings')return okay({settings:{},record_hash:'b'.repeat(64)});
    if(name==='list_offers')return okay({offers:master.offers.map(offer=>({offer})),total:master.offers.length});
    if(name==='get_offer')return okay({offer:master.offers.find(x=>x.id===a.offer_id),record_hash:'qa_offer_hash',context_hash:'qa_context'});
    if(name==='search_offer_templates')return okay({entries:master.service_catalog.map(entry=>({entry})),total:master.service_catalog.length});
    if(name==='get_offer_template')return okay({entry:master.service_catalog.find(x=>x.id===a.entry_id),record_hash:'qa_catalog_hash'});
    if(name==='list_appointments')return okay({appointments:master.appointments.map(appointment=>({appointment})),total:master.appointments.length,integration:{}});
    if(name==='get_appointment')return okay({appointment:master.appointments.find(x=>x.id===a.appointment_id),record_hash:'qa_appointment_hash'});
    if(name==='list_backups')return okay({backups:[]});
    if(name==='get_invoice')return JSON.stringify({ok:true,data:master.invoices.find(x=>x.id===a.invoice_id),entity_hash:'qa_invoice_hash'});
    return okay({});
   }};window.AndroidSpeech={start(){}};
  });
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);await page.waitForFunction(()=>window.HGRecordActions&&document.body.dataset.fieldReady==='true');await page.waitForTimeout(300);
  const close=async()=>{await page.evaluate(()=>{document.querySelectorAll('dialog[open]').forEach(d=>d.close());});};
  const hold=async(selector)=>{await page.locator(selector).first().dispatchEvent('pointerdown',{pointerId:1,isPrimary:true,button:0,clientX:70,clientY:200});await page.waitForTimeout(620);await page.locator('body').dispatchEvent('pointerup',{pointerId:1});};
  await check('One full state build per load preserves the complete embedded sync status',async()=>{
   const result=await page.evaluate(()=>{
    const read=AndroidData.getState,status=AndroidData.getSyncStatus;let builds=0,statusReads=0;
    const expected={configured:false,pending:0,review:0,locally_committed:0,local_cleanup_pending:true,background_work_pending:true,background_work_error_code:'qa_background',duplicate_files:2,warning:'QA Warnung bleibt erhalten'};
    AndroidData.getState=()=>{builds++;return JSON.stringify({...JSON.parse(read()),sync_status:expected});};AndroidData.getSyncStatus=()=>{builds++;statusReads++;return JSON.stringify(expected);};
    try{load();const got={...syncInfo};delete got.pending_total;delete got.full_backup;return{builds,statusReads,got,expected};}finally{AndroidData.getState=read;AndroidData.getSyncStatus=status;}
   });assert.equal(result.builds,1);assert.equal(result.statusReads,0);assert.deepEqual(result.got,result.expected);
  });
  await check('Unchanged Today → Office → Today reuses sorted data, overview and existing DOM nodes',async()=>{
   const result=await page.evaluate(()=>{
    openPage('home');openPage('revier-office');openPage('home');
    const reads=AndroidData.getState,status=AndroidData.getSyncStatus,owner=AndroidData.ownerCall,order=window.HGDataOrder;
    let fullBuilds=0,ownerReads=0,recalculations=0;const ownerNames=[];
    AndroidData.getState=()=>{fullBuilds++;return reads();};AndroidData.getSyncStatus=()=>{fullBuilds++;return status();};AndroidData.ownerCall=(...args)=>{ownerReads++;ownerNames.push(args[0]);return owner(...args);};window.HGDataOrder={...order,sorted(...args){recalculations++;return order.sorted(...args);}};
    const overview=document.querySelector('#revier-today button'),office=document.querySelector('#revier-office .revier-content').firstChild;
    const cache=customers(),logs=worklogs();
    try{openPage('revier-office');openPage('home');return{fullBuilds,ownerReads,ownerNames,recalculations,sameCustomers:cache===customers(),sameLogs:logs===worklogs(),sameOffice:office===document.querySelector('#revier-office .revier-content').firstChild,sameOverview:overview===document.querySelector('#revier-today button')};}
    finally{AndroidData.getState=reads;AndroidData.getSyncStatus=status;AndroidData.ownerCall=owner;window.HGDataOrder=order;}
   });assert.deepEqual(result,{fullBuilds:0,ownerReads:0,ownerNames:[],recalculations:0,sameCustomers:true,sameLogs:true,sameOffice:true,sameOverview:true});
  });
  await check('Create, edit, delete and native notification invalidate cached visible and hidden pages',async()=>{
   await page.evaluate(()=>{openPage('customers');master.customers.push({id:'qa_cache',name:'QA Cache neu',active:1});load();});assert(await page.locator('[data-customer-open="qa_cache"]').isVisible());
   await page.evaluate(()=>{openPage('revier-office');master.customers.find(x=>x.id==='qa_cache').name='QA Cache geändert';load();openPage('customers');});assert.match(await page.locator('[data-customer-open="qa_cache"]').innerText(),/geändert/);
   await page.evaluate(()=>{master.customers=master.customers.filter(x=>x.id!=='qa_cache');load();});assert.equal(await page.locator('[data-customer-open="qa_cache"]').count(),0);
   await page.evaluate(()=>{master.customers.push({id:'qa_notification',name:'QA Native Änderung',active:1});document.activeElement?.blur();onLocalBusinessChanged();});await page.waitForSelector('[data-customer-open="qa_notification"]');
   await page.evaluate(()=>{master.customers=master.customers.filter(x=>x.id!=='qa_notification');load();});assert.equal(await page.locator('[data-customer-open="qa_notification"]').count(),0);
  });
  await check('Natural alphabetic ordering includes all customer selectors and new accounts',async()=>{
   assert.deepEqual(await page.evaluate(()=>customers().map(x=>x.id)),['qa_2','qa_10','qa_m','qa_z']);await page.evaluate(()=>{AndroidData.saveCustomer(JSON.stringify({name:'Aachen QA',active:1}));load();});assert.equal(await page.evaluate(()=>customers()[0].id),'qa_new');
  });
  for(const [type,id,prepare,selector]of [
   ['customer','qa_m',()=>openPage('customers'),'#customer-list [data-customer-open="qa_m"]'],
   ['order','qa_o',()=>openPage('orders'),'#order-list [data-edit-order="qa_o"]'],
   ['worklog','qa_w',()=>openPage('worklogs'),'#worklog-list [data-edit-worklog="qa_w"]'],
   ['invoice','qa_i',()=>openPage('invoices'),'#invoice-list [data-invoice="qa_i"]'],
   ['photo','qa_p0',()=>{openPage('photo-library-page');PhotoLibraryUI.refresh();},'#photo-library-results [data-id="qa_p0"]'],
   ['offer','qa_f',()=>{openPage('offers-page');OfferUI.refresh();},'[data-offer-open="qa_f"]'],
   ['service_template','qa_s',()=>OfferUI.editTemplate('qa_s'),'[data-catalog-edit="qa_s"]'],
   ['appointment','qa_a',()=>PlanningUI.open(),'[data-appointment-open="qa_a"]']
  ])await check('Long hold opens exact '+type+' context without a write',async()=>{await close();await page.evaluate(prepare);const before=await page.evaluate(()=>calls.filter(x=>/^(delete|archive|purge|update|create)/.test(x.name)).length);await hold(selector);assert(await page.locator('#record-action-dialog').evaluate(d=>d.open));assert.equal(await page.locator('#record-action-dialog').getAttribute('data-action-type'),type);assert.equal(await page.locator('#record-action-dialog').getAttribute('data-action-id'),id);assert.equal(await page.evaluate(()=>calls.filter(x=>/^(delete|archive|purge|update|create)/.test(x.name)).length),before);if(type==='invoice')assert(await page.locator('#record-action-dialog [data-record-delete]').isDisabled());await page.evaluate(()=>handleAndroidBack());assert.equal(await page.locator('#record-action-dialog').evaluate(d=>d.open),false);});
  await check('Scrolling and multiple fingers cancel long hold',async()=>{
   await close();await page.evaluate(()=>openPage('customers'));const target=page.locator('[data-customer-open="qa_m"]').first();await target.dispatchEvent('pointerdown',{pointerId:1,isPrimary:true,clientX:20,clientY:100});for(const y of [105,110,115])await target.dispatchEvent('pointermove',{pointerId:1,clientX:20,clientY:y});await page.waitForTimeout(620);assert.equal(await page.locator('#record-action-dialog').evaluate(d=>d.open),false);await target.dispatchEvent('pointerup',{pointerId:1});
   await target.dispatchEvent('pointerdown',{pointerId:1,isPrimary:true,clientX:20,clientY:100});await target.dispatchEvent('pointerdown',{pointerId:2,isPrimary:false,clientX:40,clientY:100});await page.waitForTimeout(620);assert.equal(await page.locator('#record-action-dialog').evaluate(d=>d.open),false);for(const pointerId of [1,2])await target.dispatchEvent('pointerup',{pointerId});
  });
  await check('Visible actions and keyboard context use the same stable record ID',async()=>{
   await page.evaluate(()=>{openPage('photo-library-page');PhotoLibraryUI.refresh();});await page.locator('#photo-library-results .entity-context-open').first().click();assert.equal(await page.locator('#record-action-dialog').getAttribute('data-action-id'),'qa_p0');await close();await page.locator('#photo-library-results [data-id="qa_p1"]').press('Shift+F10');assert.equal(await page.locator('#record-action-dialog').getAttribute('data-action-id'),'qa_p1');await close();
  });
  await check('Global search covers German aliases, multiple words, filenames, document numbers and catalog',async()=>{
   await page.evaluate(()=>openPage('more-page'));await page.locator('[data-global-search]').click();
   for(const [query,id]of [['mueller grauenrain','qa_m'],['436641234567','qa_m'],['QA-Datei-3','qa_p3'],['QA-KV-0042','qa_f'],['Baustein','qa_s']]){await page.locator('#global-search-input').fill(query);await page.waitForTimeout(210);assert(await page.locator('[data-search-result][data-record-id="'+id+'"]').count());}
   await page.locator('#global-search-input').fill('QA');await page.waitForTimeout(210);await page.evaluate(()=>{master.customers.push({id:'qa_live',name:'Abel QA',active:1});load();});assert(await page.locator('[data-search-result][data-record-id="qa_live"]').count());await close();
  });
  await check('Status read failure cannot replace valid business data; failed data read retains last confirmed view',async()=>{
   await page.evaluate(()=>{statusFailure=true;master.customers.push({id:'qa_status',name:'QA Status retained',active:1});load();});assert(await page.evaluate(()=>customers().some(x=>x.id==='qa_status')));assert.equal(await page.evaluate(()=>hgDataReadError),false);
   const before=await page.evaluate(()=>customers().map(x=>x.id));assert.equal(await page.evaluate(()=>{readFailure=true;return load();}),false);assert.deepEqual(await page.evaluate(()=>customers().map(x=>x.id)),before);assert.match(await page.locator('#mobile-toast').innerText(),/bisherige Ansicht/);await page.evaluate(()=>{readFailure=statusFailure=false;load();});
  });
  await check('Photo originals in the viewer are bounded and released when closing',async()=>{
   await page.evaluate(()=>openStoredPhoto('qa_p0',master.photos.map(x=>x.id)));for(let i=0;i<5;i++){await page.locator('[data-photo-next]').click();await page.waitForTimeout(60);assert(await page.evaluate(()=>photos().filter(x=>x._viewerOriginal).length<=2));}await page.locator('.design-photo-close').click();await page.waitForFunction(()=>photos().filter(x=>x._viewerOriginal).length===0);assert.equal(await page.evaluate(()=>photos().filter(x=>x._viewerOriginal).length),0);
  });
  await check('Context sheets remain reachable at narrow widths and enlarged text',async()=>{
   for(const width of [320,360,426]){await page.setViewportSize({width,height:900});await page.evaluate(()=>{document.documentElement.style.fontSize='24px';HGRecordActions.show({type:'offer',id:'qa_f'});});assert(await page.locator('#record-action-dialog').evaluate(d=>d.scrollWidth<=d.clientWidth+1));for(const b of await page.locator('#record-action-dialog button').all())assert((await b.boundingBox()).height>=48);await close();}
   assert.deepEqual(errors,[]);
  });
  fs.writeFileSync('outputs/qa-context-search-storage.json',JSON.stringify({classification:'SYNTHETIC_TEST',device:false,results},null,2));if(results.some(x=>x.result==='FAIL'))process.exitCode=1;
 }finally{await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});

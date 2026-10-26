'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url'),acorn=require('acorn'),{chromium}=require('./browser.cjs');
const fixtureSource=require('./ui-fixture-source.cjs'),crypto=require('node:crypto');
function bridge(){const source=fs.readFileSync(path.join(__dirname,'planning-ui.test.cjs'),'utf8'),ast=acorn.parse(source,{ecmaVersion:2022}),found=[];function visit(n){if(!n||typeof n!=='object')return;if(n.type==='CallExpression'&&n.callee?.property?.name==='addInitScript')found.push(n.arguments[0]);for(const v of Object.values(n))if(Array.isArray(v))v.forEach(visit);else if(v&&typeof v==='object')visit(v);}visit(ast);assert.equal(found.length,1);return '('+source.slice(found[0].start,found[0].end)+')();';}
async function filledCoreViews(browser,check){
 const assets=path.resolve('android/app/src/main/assets'),fingerprints=()=>Object.fromEntries(fs.readdirSync(assets).filter(name=>/\.(?:html|js|css)$/.test(name)).sort().map(name=>[name,crypto.createHash('sha256').update(fs.readFileSync(path.join(assets,name))).digest('hex')]));
 const evidence={classification:'SYNTHETIC_TEST',environment:'Cloud Chromium, real bundled operative assets, existing synthetic native fixtures; no Android/TalkBack/system-inset claim',font_percent:200,widths:[320,360,426],assets_before:fingerprints(),observations:[]};
 const page=await browser.newPage({viewport:{width:360,height:900},hasTouch:true}),errors=[];page.setDefaultTimeout(8000);page.on('pageerror',error=>errors.push(error.message));page.on('dialog',dialog=>dialog.dismiss());
 try{
  await page.clock.setFixedTime(new Date('2027-05-18T08:00:00Z'));
  await page.addInitScript({content:fixtureSource('offers-photo-library.test.cjs')});
  await page.addInitScript(()=>{window.qaRs13Documents=AndroidData.ownerCall;window.master=fixture.master;});
  await page.addInitScript({content:fixtureSource('revier-solo-ui.test.cjs')});
  await page.addInitScript(()=>{
   const day='2027-05-18',copy=value=>JSON.parse(JSON.stringify(value));window.qaRs13NativeWrites=[];
   Object.assign(fixture.master.orders[0],{description:'QA vorhandener Auftrag mit Rasenpflege und Heckenschnitt',location:'QA Einsatzweg1',scheduled_date:day,billing_customer_id:'qa_c1',customer_visible:1});
   fixture.master.worklogs=[{id:'qa_rs13_work',customer_id:'qa_c1',order_id:'qa_o1',work_date:day,activity:'QA vorhandene Pflegearbeit',hours:2.5,break_minutes:15,kilometers:0,billed:0,customer_visible:1}];
   fixture.master.offers=[{id:'qa_rs13_offer',customer_id:'qa_c1',order_id:'qa_o1',title:'QA gespeichertes Pflegeangebot',document_type:'time_material',status:'draft',issue_date:day,valid_until:'2027-06-18',execution_window:'QA Mai',tax_mode:'small_business',positions:[{title:'QA Gartenpflege',description:'QA Rasen und Hecke',quantity:2.5,unit:'h',price:52,tax_rate:0}],_hash:'qa_rs13_offer_hash'}];
   fixture.master.invoices=[{id:'qa_rs13_invoice',customer_id:'qa_c1',status:'Entwurf',invoice_date:day,service_start:day,service_end:day,amount:130,tax_mode:'small_business',positions:[{title:'QA Gartenpflege',quantity:2.5,unit:'h',price:52,tax_rate:0}],notes:'QA vorhandene Rechnungsnotiz'}];
   fixture.master.photos=fixture.master.photos.slice(0,6);
   fixture.master.appointments=Array.from({length:4},(_,index)=>({id:'qa_rs13_appointment_'+index,customer_id:'qa_c1',order_id:'qa_o1',title:'QA geplanter Pflegeeinsatz '+(index+1),location:'QA Einsatzweg1',timezone:'Europe/Vienna',starts_at:day+'T'+String(10+index).padStart(2,'0')+':00:00Z',ends_at:day+'T'+String(11+index).padStart(2,'0')+':00:00Z',status:'tentative',weather_profile:'lawn',updated_at:'qa_rs13_appointment_hash'}));
   window.qaRs13Timer={id:'qa_rs13_session',state:'RUNNING',customer_id:'qa_c1',order_id:'qa_o1',property_id:'qa_property_1',visit_id:'qa_visit_1',activity:'QA laufende Pflege',work_date:day,hours:1.25,worked_ms:4500000,break_minutes:15,clock_review_required:false};
   const revierRead=AndroidData.ownerCall,documentsRead=qaRs13Documents,good=data=>JSON.stringify({ok:true,data}),entry=appointment=>({appointment:copy(appointment),record_hash:appointment.updated_at,weather:{severity:'unknown',label:'QA Wetter ungeprüft'},calendar:{state:'not_connected'}});
   AndroidData.getWorkSession=()=>JSON.stringify(qaRs13Timer);
   for(const name of ['saveCustomer','saveOrder','saveWorklog','saveInvoice','savePhoto','startWorkSession','pauseWorkSession','resumeWorkSession','stopWorkSession'])AndroidData[name]=raw=>{qaRs13NativeWrites.push({name,raw});throw Error('QA_RS13_READ_ONLY_NATIVE_WRITE');};
   AndroidData.ownerCall=(name,raw)=>{
    if(/^(create_|update_|save_|record_|correct_|delete_|purge_|archive_|request_|finalize_|convert_|start_|pause_|resume_|stop_|set_|move_|generate_|plan_|accept_|decline_)/.test(name)){qaRs13NativeWrites.push({name,args:JSON.parse(raw)});throw Error('QA_RS13_READ_ONLY_OWNER_WRITE');}
    const args=JSON.parse(raw);
    if(name==='get_capabilities')return good({runtime:{capabilities:{local_payment_ledger_v1:false}}});
    if(name==='list_appointments')return good({appointments:fixture.master.appointments.slice(args.offset||0,(args.offset||0)+(args.limit||30)).map(entry),total:fixture.master.appointments.length,integration:{calendar_configured:false,weather_configured:false}});
    if(name==='get_appointment')return good(entry(fixture.master.appointments.find(row=>row.id===args.appointment_id)));
    if(name==='get_planning_settings')return good({settings:{weather_enabled:false},integration:{calendar_configured:false,weather_configured:false}});
    if(['get_revier_overview','get_property','get_visit','get_day_tour','list_properties','list_visits','list_inquiries','list_care_plans','list_materials','list_equipment','get_full_backup_status'].includes(name))return revierRead(name,raw);
    return documentsRead(name,raw);
   };
  });
  await page.goto(pathToFileURL(path.join(assets,'mobile.html')).href);await page.waitForFunction(()=>document.body.dataset.revierReady==='true'&&document.body.dataset.fieldReady==='true'&&window.PlanningUI&&window.OfferUI);
  const views=[
   {name:'Heute',scope:'#home',ready:'#planning-home [data-appointment-open]',text:'QA geplanter Pflegeeinsatz',open:()=>{openPage('home');TodayUI.invalidate();}},
   {name:'Kundenliste',scope:'#customers',ready:'[data-customer-open="qa_c1"]',text:'QA Hofer Gartenkunde',open:()=>openPage('customers')},
   {name:'Objektakte',scope:'#revier-property-detail',ready:'[data-revier-edit=property]',text:'QA Nordobjekt',open:()=>RevierUI.openProperty('qa_property_1')},
   {name:'Planung',scope:'#planning-page',ready:'#planning-rows [data-appointment-open]',text:'QA geplanter Pflegeeinsatz',open:()=>PlanningUI.open()},
   {name:'Auftragsdetails',scope:'#direct-edit-dialog[open]',ready:'[data-order-window-edit]',text:'QA vorhandener Auftrag',open:()=>{openPage('orders');const select=document.getElementById('order-edit-select');select.value='qa_o1';select.dispatchEvent(new Event('change',{bubbles:true}));openDirectEditWindow('order-management-card','Auftrag im Überblick');}},
   {name:'Timer',scope:'#workday-timer',ready:'[data-session-action=pause]',text:'QA Hofer Gartenkunde',open:()=>{openPage('home');TodayUI.render();}},
   {name:'Fotos',scope:'#photo-library-page',ready:'#photo-library-results [data-design-action=photo]',text:'6 Fotos',open:()=>{openPage('photo-library-page');PhotoLibraryUI.refresh();}},
   {name:'Angebot',scope:'#offers-page',ready:'#offer-edit',text:'QA gespeichertes Pflegeangebot',open:()=>OfferUI.open('','qa_rs13_offer')},
   {name:'Rechnung',scope:'.invoice-preview-dialog[open]',ready:'.invoice-draft-read',text:'QA vorhandene Rechnungsnotiz',open:()=>{openPage('invoices');openInvoiceRecord('qa_rs13_invoice');}}
  ];
  for(const width of evidence.widths)for(const view of views)await check('RS13 filled '+view.name+' '+width+'px at200percent: names focus48px and no horizontal loss',async()=>{
   await page.setViewportSize({width,height:900});await page.evaluate(()=>{for(const dialog of document.querySelectorAll('dialog[open]'))dialog.close();document.documentElement.style.fontSize='200%';});await page.evaluate(view.open);await page.locator(view.scope).waitFor({state:'visible'});await page.locator(view.scope+' '+view.ready).first().waitFor({state:'visible'});
   const scope=page.locator(view.scope);assert((await scope.innerText()).includes(view.text),'Filled content missing: '+view.name);await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
   // A modal makes the underlying page inert. Measure the actual operable
   // surface, while retaining any inert background scroll extent as evidence.
   const bounds=await page.evaluate(selector=>{const root=document.querySelector(selector),r=root.getBoundingClientRect(),modal=root.matches('dialog:modal'),overflow=[...(modal?root:document.body).querySelectorAll('*')].filter(element=>{const box=element.getBoundingClientRect(),style=getComputedStyle(element);return box.width>0&&box.height>0&&style.visibility!=='hidden'&&(box.left< -1||box.right>innerWidth+1);}).slice(0,12).map(element=>{const box=element.getBoundingClientRect();return{tag:element.tagName,id:element.id,className:element.className,left:box.left,right:box.right,width:box.width};});return{viewport:innerWidth,page:document.documentElement.scrollWidth,modal,scopeLeft:r.left,scopeRight:r.right,scopeWidth:root.clientWidth,scopeScroll:root.scrollWidth,overflow};},view.scope);const horizontalLoss=(!bounds.modal&&bounds.page>width+1)||bounds.scopeScroll>bounds.scopeWidth+1||bounds.scopeLeft< -1||bounds.scopeRight>width+1;if(horizontalLoss){evidence.observations.push({view:view.name,width,font_percent:200,filled:true,result:'FAIL',bounds});await page.screenshot({path:'outputs/qa-rs13-'+view.name+'-'+width+'-overflow.png'});}assert(!horizontalLoss,view.name+' horizontal loss on operable surface '+JSON.stringify(bounds));
   const controls=scope.locator('button,a[href],input:not([type=hidden]),select,textarea,summary,[role=button]'),active=[];
   for(let index=0;index<await controls.count();index++){const target=controls.nth(index);if(!await target.isVisible()||await target.isDisabled())continue;const description=await target.evaluate(element=>{const ids=String(element.getAttribute('aria-labelledby')||'').split(/\s+/).filter(Boolean),name=(ids.map(id=>document.getElementById(id)?.textContent||'').join(' ')||element.getAttribute('aria-label')||[...(element.labels||[])].map(label=>label.innerText).join(' ')||element.innerText||element.getAttribute('alt')||element.getAttribute('title')||(['button','submit','reset'].includes(element.type)?element.value:'')).replace(/\s+/g,' ').trim();const label=['checkbox','radio'].includes(element.type)?element.labels?.[0]:null,r=(label||element).getBoundingClientRect();return{id:element.id,tag:element.tagName,name,width:r.width,height:r.height,readOnly:!!element.readOnly};});assert(description.name,view.name+' unnamed active control '+JSON.stringify(description));assert(description.width>=47.5&&description.height>=47.5,view.name+' active target below48px '+JSON.stringify(description));await target.scrollIntoViewIfNeeded();const box=await target.boundingBox();assert(box.x>=-1&&box.x+box.width<=width+1,view.name+' active control outside horizontal viewport '+JSON.stringify(description));active.push({index,...description});}
   if(!bounds.modal)for(const target of await page.locator('.bottom button,.revier-globalbar button').all()){if(!await target.isVisible()||await target.isDisabled())continue;const box=await target.boundingBox();assert(box.width>=47.5&&box.height>=47.5&&box.x>=-1&&box.x+box.width<=width+1,view.name+' navigation target must remain reachable and48px');assert((await target.getAttribute('aria-label')||await target.innerText()).trim(),view.name+' navigation needs an accessible name');}
   assert(active.length>0,view.name+' has no checked active controls');const first=controls.nth(active.find(row=>row.tag==='BUTTON')?.index??active[0].index);await first.focus();await page.keyboard.press('Tab');const focused=await page.evaluate(selector=>{const element=document.activeElement,style=getComputedStyle(element);return{active:document.querySelector(selector).contains(element),id:element.id,tag:element.tagName,visible:element.matches(':focus-visible'),outline:parseFloat(style.outlineWidth)||0,shadow:style.boxShadow};},view.scope);assert(focused.active&&focused.visible&&(focused.outline>=1||focused.shadow!=='none'),view.name+' missing visible keyboard focus '+JSON.stringify(focused));assert.deepEqual(await page.evaluate(()=>qaRs13NativeWrites),[],'Read-only matrix must not mutate business data');evidence.observations.push({view:view.name,width,font_percent:200,filled:true,result:'PASS',controls:active.length,bounds,focus:focused});
  });
  await check('RS13 filled matrix retains all synthetic relations and causes no native writes or script errors',async()=>{assert.deepEqual(await page.evaluate(()=>({customers:fixture.master.customers.length,orders:fixture.master.orders.length,worklogs:fixture.master.worklogs.length,photos:fixture.master.photos.length,offers:fixture.master.offers.length,invoices:fixture.master.invoices.length,properties:qaRevier.properties.length,appointments:fixture.master.appointments.length,writes:qaRs13NativeWrites})),{customers:1,orders:1,worklogs:1,photos:6,offers:1,invoices:1,properties:1,appointments:4,writes:[]});assert.deepEqual(errors,[]);evidence.assets_after=fingerprints();assert.deepEqual(evidence.assets_after,evidence.assets_before,'Production asset files changed during RS13 run');});
 }finally{await page.close();fs.writeFileSync('outputs/qa-rs13-filled-core-views.json',JSON.stringify(evidence,null,2));}
}
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']}),page=await browser.newPage({viewport:{width:360,height:640},hasTouch:true}),results=[],errors=[],startup={};
 page.on('pageerror',e=>errors.push(e.message));async function check(name,fn){try{await fn();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.stack});}console.log(results.at(-1));}
 async function screenshot(name){const file='outputs/'+name+'.png';await page.screenshot({path:file,fullPage:true});}
 async function fits(selector){const b=await page.locator(selector).first().boundingBox(),nav=await page.locator('.bottom').boundingBox();assert(b&&b.y>=0&&b.y+b.height<=nav.y+1,selector+' must be above navigation: '+JSON.stringify({b,nav}));}
 async function accessible(scope){const violations=await page.locator(scope).evaluate(root=>{const visible=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width&&r.height&&s.visibility!=='hidden'&&s.display!=='none';};return [...root.querySelectorAll('button,summary,[role=button]')].filter(visible).map(e=>{const r=e.getBoundingClientRect();return r.width<47.9||r.height<47.9?{id:e.id,text:e.textContent.trim(),width:r.width,height:r.height}:null;}).filter(Boolean);});assert.deepEqual(violations,[]);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));}
 try{
  await page.clock.setFixedTime(new Date('2026-10-22T07:00:00Z'));await page.addInitScript({content:bridge()});await page.addInitScript(()=>{
   fixture.master.appointments=fixture.master.appointments.slice(0,4).map((a,i)=>({...a,id:'qa_overview_'+i,title:'QA Einsatz '+i,starts_at:'2026-10-22T'+String(8+i*2).padStart(2,'0')+':00:00Z',ends_at:'2026-10-22T'+String(9+i*2).padStart(2,'0')+':00:00Z',location:'QA Garten '+i}));
   AndroidData.getWorkSession=()=>JSON.stringify(timer);
   // Existing appointments are separate from Revier care occurrences. This
   // fixture has four actual appointments and no care visits or manual tour.
   const owner=AndroidData.ownerCall,ok=data=>JSON.stringify({ok:true,data});AndroidData.ownerCall=(name,raw)=>{const args=JSON.parse(raw);if(name==='get_revier_overview')return ok({date:args.date,due_visits:[],visits:[],open_inquiries:0});if(name==='get_day_tour')return ok({tour:{date:args.date,visit_ids:[],record_hash:'a'.repeat(64)},stops:[]});if(name==='get_full_backup_status')return ok({enabled:false,jobs:[],last_external_verified_at:null,last_external_captured_at:null});return owner(name,raw);};
  });
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);await page.waitForFunction(()=>document.body.dataset.fieldReady==='true'&&document.body.dataset.todayReady==='true');
  const startupState=()=>{const appointment=document.querySelector('#planning-home [data-appointment-open="qa_overview_0"]')?.getBoundingClientRect(),dock=document.getElementById('today-action-dock')?.getBoundingClientRect(),nav=document.querySelector('.bottom')?.getBoundingClientRect();return{fieldReady:document.body.dataset.fieldReady,todayReady:document.body.dataset.todayReady,revierReady:document.body.dataset.revierReady||null,rootButtons:document.querySelectorAll('.bottom .nav').length,appointment:appointment?{y:appointment.y,height:appointment.height}:null,dock:dock?{y:dock.y,height:dock.height}:null,dockMeasured:document.documentElement.style.getPropertyValue('--today-dock-height'),nav:nav?{y:nav.y,height:nav.height}:null};};
  startup.before_full_ui_ready=await page.evaluate(startupState);
  // fieldReady precedes Revier's navigation/layout installation. Wait for that
  // actual module and its ResizeObserver measurement, never for the fit assertion.
  await page.waitForFunction(()=>{const dock=document.getElementById('today-action-dock');return document.body.dataset.revierReady==='true'&&document.body.classList.contains('revier-solo')&&document.querySelectorAll('.bottom .nav').length===4&&dock&&document.documentElement.style.getPropertyValue('--today-dock-height')===Math.ceil(dock.getBoundingClientRect().height)+'px';});
  await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));startup.after_full_ui_ready=await page.evaluate(startupState);console.log('OVERVIEW_STARTUP '+JSON.stringify(startup));
  await check('97 Today360x640 keeps work controls above navigation and the populated carousel reachable by scrolling',async()=>{
   assert.equal(await page.locator('#home .folder-grid,#home .hero,#home>.brand').count(),0);assert.equal(await page.locator('#app-drawer .brand').count(),1);
   assert.equal(await page.locator('#planning-home [data-appointment-open]').count(),1);assert.equal(await page.locator('#home .today-header h1').innerText(),'Heute');assert(!(await page.locator('#revier-today').innerText()).includes('Lokale Planung derzeit nicht lesbar'));await fits('#workday-timer [data-session-action=start]');await fits('#field-today-actions .quick');const appointment=page.locator('#planning-home [data-appointment-open="qa_overview_0"]');await appointment.evaluate(element=>element.scrollIntoView({block:'center',behavior:'instant'}));await fits('#planning-home [data-appointment-open="qa_overview_0"]');const row=await appointment.boundingBox(),dock=await page.locator('#today-action-dock').boundingBox();assert(row.y+row.height<=dock.y+1,'Scrolled appointment must also clear the fixed work-action dock');assert.equal(await page.locator('#design-metrics [data-design-metric]').count(),2);await accessible('#home');await screenshot('qa-97-today-360-100');
  });
  await check('97 original carousel reaches every upcoming appointment and respects its bounds',async()=>{
   for(let i=1;i<4;i++){await page.locator('[data-home-browse="1"]').click();assert.equal(await page.locator('#planning-home [data-appointment-open]').getAttribute('data-appointment-open'),'qa_overview_'+i);}
   assert(await page.locator('[data-home-browse="1"]').isDisabled());for(let i=0;i<3;i++)await page.locator('[data-home-browse="-1"]').click();assert(await page.locator('[data-home-browse="-1"]').isDisabled());
  });
  await check('97 carousel refreshes an expired selection without skipping the next appointment',async()=>{
   await page.clock.setFixedTime(new Date('2026-10-22T09:30:00Z'));await page.locator('[data-home-browse="1"]').click();assert.equal(await page.locator('#planning-home [data-appointment-open]').getAttribute('data-appointment-open'),'qa_overview_1');
   await page.clock.setFixedTime(new Date('2026-10-22T20:00:00Z'));await page.locator('[data-home-browse="1"]').click();assert.equal(await page.locator('#planning-home [data-appointment-open]').count(),0);assert.match(await page.locator('#planning-home').innerText(),/Keine bevorstehenden Termine/);
   await page.clock.setFixedTime(new Date('2026-10-22T07:00:00Z'));await page.evaluate(()=>TodayUI.render());
  });
  await check('97 planning has three control rows and first appointment above navigation',async()=>{
   await page.evaluate(()=>PlanningUI.open());assert.equal(await page.locator('.planning-range,.planning-modebar,.planning-week-strip').count(),3);assert.equal(await page.locator('#planning-tools').evaluate(d=>d.open),false);
   await fits('#planning-rows [data-appointment-open]');await fits('#planning-new');await accessible('#planning-page');await screenshot('qa-97-planning-360-100');
   await page.locator('#planning-tools>summary').click();assert(await page.locator('#planning-suggest').isVisible());assert(await page.locator('#planning-customer-filter').isVisible());assert(await page.locator('.planning-actual-hours').isVisible());await page.locator('#planning-tools>summary').click();
  });
  await check('97 timer survives Today planning Today using the unchanged session bridge',async()=>{
   await page.evaluate(()=>openPage('home'));await page.locator('[data-session-action=start]').click();await page.locator('#work-location-customer').selectOption('qa_c');await page.locator('#work-location-start').click();const before=await page.evaluate(()=>({...timer,starts}));assert.equal(before.state,'RUNNING');assert.equal(before.starts,1);
   await page.evaluate(()=>{timer.worked_ms=150000;PlanningUI.open();});await page.evaluate(()=>openPage('home'));assert.equal(await page.evaluate(()=>timer.id),before.id);assert.equal(await page.evaluate(()=>starts),1);assert.match(await page.locator('.timer-state strong').innerText(),/00:02:30/);assert(await page.locator('[data-session-action=stop]').isVisible());await fits('[data-session-action=stop]');await fits('#field-today-actions .quick');assert.equal(await page.locator('#today-action-dock [data-session-action=photo]').count(),1);
  });
  await check('97 active timer keeps photo and manual capture directly reachable in the lower dock',async()=>{
   await page.evaluate(()=>{window.qaCameraOriginal=window.openCustomerDocumentationCamera;window.qaCameraArgs=null;window.openCustomerDocumentationCamera=(...args)=>{qaCameraArgs=args;return true;};});
   await page.locator('[data-session-action=photo]').click();const captured=await page.evaluate(()=>qaCameraArgs);assert.equal(captured[0],'qa_c');assert.equal(captured[1].session_id,await page.evaluate(()=>timer.id));assert.equal(await page.locator('.today-timer-more').evaluate(d=>d.open),false);
   await page.evaluate(()=>{window.openCustomerDocumentationCamera=qaCameraOriginal;delete window.qaCameraOriginal;});await page.locator('#field-today-actions .quick').click();assert(await page.locator('#field-quick-entry').isVisible());await page.evaluate(()=>handleAndroidBack());assert.equal(await page.evaluate(()=>timer.state),'RUNNING');assert.equal(await page.evaluate(()=>starts),1);
  });
  await check('Revier active timer and tour retain scrollable appointment time and customer at616px height',async()=>{
   const previous=await page.evaluate(()=>fixture.master.worklogs);await page.setViewportSize({width:360,height:616});
   try{
    await page.evaluate(()=>{fixture.master.worklogs=[{id:'qa_recent_overview',customer_id:'qa_c',work_date:'2026-10-22',activity:'QA gespeicherte Arbeit',hours:1,billed:0}];load();openPage('home');scrollTo(0,0);});
    await page.locator('#planning-home').scrollIntoViewIfNeeded();
    for(const selector of ['#planning-home .today-time','#planning-home .today-customer']){const b=await page.locator(selector).boundingBox(),dock=await page.locator('#today-action-dock').boundingBox();assert(b&&b.y>=0&&b.y+b.height<=dock.y+1,selector+' must remain readable above fixed actions: '+JSON.stringify({b,dock}));}
    const ordered=await page.evaluate(()=>document.querySelector('#today-recent').getBoundingClientRect().top>=document.querySelector('#planning-home').getBoundingClientRect().bottom);assert(ordered,'recent work follows appointment overview');
   }finally{await page.evaluate(previous=>{fixture.master.worklogs=previous;load();TodayUI.render();},previous);await page.setViewportSize({width:360,height:640});}
  });
  await check('97 customer renaming refreshes Today appointments and the active timer',async()=>{
   const previousName=await page.evaluate(()=>fixture.master.customers.find(c=>c.id==='qa_c').name);await page.evaluate(()=>{fixture.master.customers.find(c=>c.id==='qa_c').name='QA Neuer Anzeigename';load();openPage('home');});
   assert.match(await page.locator('#planning-home .today-customer').first().innerText(),/QA Neuer Anzeigename/);assert.match(await page.locator('.timer-location-context').innerText(),/QA Neuer Anzeigename/);await page.evaluate(name=>{fixture.master.customers.find(c=>c.id==='qa_c').name=name;load();TodayUI.render();},previousName);
  });
  await check('97 planning draft survives overview changes',async()=>{
   await page.evaluate(()=>PlanningUI.open());await page.locator('#planning-new').click();await page.locator('#appointment-form [name=title]').fill('QA retained overview draft');await page.evaluate(()=>openPage('home'));
   assert.equal(await page.evaluate(()=>HGFormDrafts.list('appointment')[0].data.title),'QA retained overview draft');
  });
  await check('97 150 and200percent text preserve touch targets text and horizontal bounds',async()=>{
   for(const percent of [150,200]){
    await page.evaluate(percent=>{document.documentElement.style.fontSize=percent+'%';openPage('home');scrollTo(0,0);},percent);await accessible('#home');await page.locator('[data-session-action=stop]').evaluate(e=>e.scrollIntoView({block:'center'}));await fits('[data-session-action=stop]');await screenshot('qa-97-today-360-'+percent);
    await page.evaluate(()=>PlanningUI.open());await accessible('#planning-page');await fits('#planning-new');await screenshot('qa-97-planning-360-'+percent);
    const clipped=await page.locator('#home .today-customer,#home .today-place,#planning-page .planning-customer,#planning-page .planning-what').evaluateAll(nodes=>nodes.filter(e=>e.getBoundingClientRect().width>0&&e.scrollWidth>e.clientWidth+1&&['hidden','clip'].includes(getComputedStyle(e).overflowX)).map(e=>e.textContent));assert.deepEqual(clipped,[]);
   }
  });
  await check('97 idle start and manual actions remain readable at large fonts',async()=>{
   const previous=await page.evaluate(()=>({...timer}));
   for(const width of [320,360])for(const scale of [1.5,2]){
    await page.setViewportSize({width,height:640});await page.evaluate(scale=>{document.documentElement.style.fontSize=16*scale+'px';timer={state:'IDLE'};openPage('home');TodayUI.render();},scale);await accessible('#home');
    for(const selector of ['[data-session-action=start] b','#field-today-actions .quick strong']){const wholeWords=await page.locator(selector).evaluate(e=>{const text=e.firstChild;return e.textContent.split(' ').filter(Boolean).every(word=>{const offset=e.textContent.indexOf(word),range=document.createRange();range.setStart(text,offset);range.setEnd(text,offset+word.length);return range.getClientRects().length===1;});});assert(wholeWords,selector+' must not split a word');}
   }
   await page.evaluate(previous=>{timer=previous;TodayUI.render();},previous);
  });
  await check('97 Today details are bounded to the selected appointment and warm updates stay responsive',async()=>{
   await page.setViewportSize({width:360,height:640});await page.evaluate(()=>{document.documentElement.style.fontSize='16px';timer={state:'IDLE'};openPage('home');});
   const result=await page.evaluate(()=>{const raw=AndroidData.ownerCall,counts={};AndroidData.ownerCall=(name,args)=>{counts[name]=(counts[name]||0)+1;return raw(name,args);};try{PlanningUI.refresh();const cold={...counts};for(const k of Object.keys(counts))delete counts[k];const samples=[];for(let i=0;i<25;i++){const start=performance.now();TodayUI.render();samples.push(performance.now()-start);}samples.sort((a,b)=>a-b);return{classification:'SYNTHETIC_TEST',fixture:'qa_4_today_appointments',cold_calls:cold,warm_calls:counts,samples_ms:samples,p50_ms:samples[12],p95_ms:samples[23],native_latency_measured:false};}finally{AndroidData.ownerCall=raw;}});
   assert.equal(result.cold_calls.get_appointment,1);assert.equal(result.warm_calls.get_appointment||0,0);assert(result.p95_ms<150);fs.writeFileSync('outputs/qa-97-today-performance.json',JSON.stringify(result,null,2));
   await page.evaluate(()=>document.querySelector('#design-metrics').scrollIntoView({block:'end'}));await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));const metric=await page.locator('#design-metrics').boundingBox(),dock=await page.locator('#today-action-dock').boundingBox();assert(metric.y+metric.height<=dock.y+1,'bottom content must scroll clear of fixed controls');
  });
  await check('97 overview exposes no script errors or duplicate critical IDs',async()=>{assert.deepEqual(errors,[]);for(const id of ['planning-home','workday-timer','sync-status','planning-body'])assert.equal(await page.locator('#'+id).count(),1);});
  await filledCoreViews(browser,check);
 }finally{await browser.close();}
 fs.writeFileSync('outputs/qa-97-overview-accessibility.json',JSON.stringify({classification:'SYNTHETIC_TEST',viewport:[360,640],font_percent:[100,150,200],startup,results},null,2));
 console.log(JSON.stringify({checks:results.length,failed:results.filter(r=>r.result==='FAIL').length}));if(results.some(r=>r.result==='FAIL'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});

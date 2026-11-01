'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs'),fixtureSource=require('./ui-fixture-source.cjs'),fillOptional=require('./form-controls.cjs');
(async()=>{
 const results=[],errors=[],browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 const page=await browser.newPage({viewport:{width:360,height:840},hasTouch:true});page.setDefaultTimeout(8000);page.setDefaultNavigationTimeout(30000);page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
 async function check(name,task){try{await task();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.stack});}console.log(results.at(-1));}
 try{
  await page.addInitScript({content:fixtureSource('daily-usability.test.cjs')});await page.addInitScript({content:fixtureSource('revier-solo-ui.test.cjs')});
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);await page.waitForFunction(()=>document.documentElement.dataset.startup==='ready'&&document.body.dataset.simpleReady==='true');
  await check('Installing and repeatedly enhancing the whole UI never writes or creates a draft',async()=>{
   assert.equal(await page.evaluate(()=>writes.length+qaRevier.writes.length),0);assert.equal(await page.evaluate(()=>HGFormDrafts.list().length),0);
   const identity=await page.evaluate(()=>{const fields=[...document.querySelectorAll('input,select,textarea')];HGSimpleForms.enhance();HGSimpleForms.enhance();return fields.every(f=>f.isConnected&&[...document.querySelectorAll('input,select,textarea')].includes(f));});assert(identity);
   await page.waitForTimeout(100);assert.equal(await page.evaluate(()=>writes.length+qaRevier.writes.length),0);assert.equal(await page.evaluate(()=>HGFormDrafts.list().length),0);
  });
  await check('All existing menu destinations remain reachable, with the two established bottom tabs',async()=>{
   assert.deepEqual(await page.locator('.bottom .nav').allTextContents(),['Heute','Kunden','Büro']);
   const ids=await page.locator('#more-page [data-open]').evaluateAll(nodes=>[...new Set(nodes.map(n=>n.dataset.open))]);
   for(const id of ids){await page.evaluate(id=>openPage(id),id);assert.equal(await page.evaluate(()=>currentPage),id,id);}
   assert.equal(await page.evaluate(()=>writes.length+qaRevier.writes.length),0);await page.evaluate(()=>openPage('home'));
  });
  await check('Reopening billing refreshes the locally read groups without creating a draft',async()=>{
   await page.evaluate(()=>{window.qaBillingOriginal=window.LocalInvoiceUI;window.qaBillingLabel='QA Erste Abrechnung';window.LocalInvoiceUI={...LocalInvoiceUI,billingContext:()=>({groups:[{billing_customer_id:'qa_recipient',billing_customer_name:qaBillingLabel,hours:0,work_records:[]}]})};openPage('billing-page');});
   try{
    assert.equal(await page.locator('#billing-group-list h3').innerText(),'QA Erste Abrechnung');
    await page.evaluate(()=>{qaBillingLabel='QA Neu gelesene Abrechnung';openPage('billing-page');});
    assert.equal(await page.locator('#billing-group-list h3').innerText(),'QA Neu gelesene Abrechnung');
    assert.equal(await page.evaluate(()=>writes.length+qaRevier.writes.length),0);
   }finally{await page.evaluate(()=>{window.LocalInvoiceUI=qaBillingOriginal;delete window.qaBillingOriginal;delete window.qaBillingLabel;openPage('home');});}
  });
  await check('Capture shows original clock fields, optional notes, a header Back and separate Discard/Save',async()=>{
   await page.evaluate(()=>openPage('work-entry'));for(const id of ['start','end'])assert(await page.locator('#'+id).isVisible());
   await page.screenshot({path:'outputs/simple-work-capture-360.png'});
   for(const selector of ['#new-work-cancel','#entry>.actions>[type=submit]']){const box=await page.locator(selector).boundingBox();assert(box.y>=0&&box.y+box.height<=840,selector+' '+JSON.stringify(box));}
   assert.equal(await page.locator('#entry [name=notes]').isVisible(),false);assert(await page.locator('#field-entry-later').isVisible());
   assert.equal(await page.locator('#entry>.actions>#new-work-cancel').innerText(),'Verwerfen');assert(await page.locator('#entry>.actions>[type=submit]').isVisible());
   await fillOptional.reveal(page.locator('#entry [data-time-mode=duration]'));await page.locator('#entry [data-time-mode=duration]').click();assert(await page.locator('#entry-hours').isVisible());await page.locator('#entry-hours').fill('2.5');
   await fillOptional(page.locator('#entry [name=notes]'),'QA Zusatznotiz');await page.locator('#activity').fill('QA Testarbeit');await page.locator('#field-entry-later').click();
   assert.equal(await page.evaluate(()=>writes.length+qaRevier.writes.length),0);await page.evaluate(()=>openPage('work-entry'));assert.equal(await page.locator('#entry-hours').inputValue(),'2.5');assert.equal(await page.locator('#entry [name=notes]').inputValue(),'QA Zusatznotiz');
   await page.locator('#new-work-cancel').click();assert.equal(await page.evaluate(()=>HGFormDrafts.list('work_entry').length),0);
  });
  await check('Visit date, activity and duration choices change only original fields and retain the original draft',async()=>{
   await page.evaluate(()=>openPage('revier-visits'));await page.locator('[data-revier-new=visit]').click();await page.waitForSelector('#revier-form.simple-form');
   await page.locator('#revier-form [name=title]').locator('..').getByRole('button',{name:'Hecke',exact:true}).click();assert.equal(await page.locator('#revier-form [name=title]').inputValue(),'Hecke schneiden');
   await page.locator('#revier-form [name=planned_minutes]').locator('..').getByRole('button',{name:'2 Std.',exact:true}).click();assert.equal(await page.locator('#revier-form [name=planned_minutes]').inputValue(),'120');
   await page.locator('#revier-form [name=date]').locator('..').getByRole('button',{name:'Morgen',exact:true}).click();const date=await page.locator('#revier-form [name=date]').inputValue();assert.match(date,/^\d{4}-\d{2}-\d{2}$/);
   await fillOptional(page.locator('#revier-form [name=notes]'),'QA Nur ein Entwurf');await page.locator('[data-revier-editor-later]').click();assert.equal(await page.evaluate(()=>writes.length+qaRevier.writes.length),0);
   assert.equal(await page.evaluate(()=>HGFormDrafts.list().find(d=>d.kind==='visit').values.notes),'QA Nur ein Entwurf');
   await page.locator('[data-revier-restore-draft]').first().click();await page.waitForSelector('#revier-form.simple-form');assert.equal(await page.locator('#revier-form [name=date]').inputValue(),date);assert(await page.locator('#revier-form [name=notes]').isVisible());
  });
  await check('Locked fields also lock choices, and stale choice callbacks cannot alter a frozen field',async()=>{
   const field=page.locator('#revier-form [name=planned_minutes]'),button=field.locator('..').getByRole('button',{name:'1 Std.',exact:true});await field.evaluate(f=>f.disabled=true);await page.waitForFunction(()=>document.querySelector('[name=planned_minutes]')._simpleChoices.querySelector('button').disabled);
   await button.dispatchEvent('click');assert.equal(await field.inputValue(),'120');await field.evaluate(f=>f.disabled=false);await page.waitForFunction(()=>!document.querySelector('[name=planned_minutes]')._simpleChoices.querySelector('button').disabled);
  });
  await check('Invalid optional fields reveal their section and remain in the original form',async()=>{
   await page.locator('#revier-form [name=notes]').evaluate(f=>{f.closest('details').open=false;f.setCustomValidity('QA Bitte prüfen');f.dispatchEvent(new Event('invalid',{bubbles:false,cancelable:true}));});
   assert(await page.locator('#revier-form [name=notes]').isVisible());assert.equal(await page.locator('#revier-form [name=notes]').evaluate(f=>f.form.id),'revier-form');await page.locator('#revier-form [name=notes]').evaluate(f=>f.setCustomValidity(''));
   await page.locator('#revier-form [name=notes]').evaluate(f=>{f.closest('details').open=false;f.setAttribute('aria-invalid','true');});await page.waitForFunction(()=>document.querySelector('#revier-form [name=notes]').closest('details').open);await page.locator('#revier-form [name=notes]').evaluate(f=>f.removeAttribute('aria-invalid'));
  });
  await check('Narrow screens and enlarged text retain reachable actions without horizontal overflow',async()=>{
   for(const width of [320,360,412])for(const font of [16,24]){await page.setViewportSize({width,height:840});await page.evaluate(font=>document.documentElement.style.fontSize=font+'px',font);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));for(const selector of ['[data-revier-editor-discard]','#revier-form [type=submit]']){const b=page.locator(selector);await b.scrollIntoViewIfNeeded();const r=await b.boundingBox();assert(r.width>=48&&r.height>=48&&r.x>=0&&r.x+r.width<=width+1);}}
   await page.setViewportSize({width:360,height:840});await page.evaluate(()=>document.documentElement.style.fontSize='16px');await page.screenshot({path:'outputs/simple-visit-form-360.png'});
  });
  await check('Explicit discard removes the retained visit draft, including after reload',async()=>{
   await page.locator('[data-revier-editor-discard]').click();assert.equal(await page.evaluate(()=>HGFormDrafts.list().filter(d=>d.kind==='visit').length),0);await page.reload();await page.waitForFunction(()=>document.documentElement.dataset.startup==='ready'&&document.body.dataset.simpleReady==='true');assert.equal(await page.evaluate(()=>HGFormDrafts.list().filter(d=>d.kind==='visit').length),0);assert.deepEqual(errors,[]);
  });
  await check('Latest backup failure or running attempt never hides behind an earlier successful readback',async()=>{
   const cases=await page.evaluate(()=>{const base={last_local_verified_at:'2026-10-28T10:02:00Z',last_local_captured_at:'2026-10-28T10:00:00Z'};const show=b=>HGBusinessFeedback.sync({drive_role:'backup_only',full_backup:b});return [show({...base,state:'FAILED',latest_job:{phase:'FAILED',last_error_code:'FINANCIAL_NUMBER_COLLISION'},jobs:[{phase:'CAPTURING'}]}),show({...base,state:'RUNNING',latest_job:{running:true}}),show({...base,state:'VERIFIED'}),show({state:'NOT_SAVED'}),show({...base,jobs:[{phase:'PROVIDER_VERIFIED',last_error_code:'BACKUP_LOCAL_READBACK_FAILED'}]}),show({...base,clock_review_required:true})];});
   assert.deepEqual(cases.map(c=>c.tone),['error','busy','ok','busy','error','error']);assert.match(cases[0].detail,/Frühere Sicherung/);assert.match(cases[0].title,/nicht abgeschlossen/);
  });
  await check('Customer list accepts native thumbnail and explicit profile role, but rejects foreign URIs and documentation photos',async()=>{
   const data='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j9d8AAAAASUVORK5CYII=';
   for(const item of [{src:data,caption:'Profilbild',expected:1},{src:'content://at.hausgarten.service.photothumbnails/'+'a'.repeat(64)+'.jpg',caption:'Profilbild',expected:1},{src:data,caption:'QA Garten',role:'profile',expected:1},{src:'https://invalid.example/qa.jpg',role:'profile',expected:0},{src:'content://foreign.authority/'+'a'.repeat(64)+'.jpg',role:'profile',expected:0},{src:data,caption:'Profilbild',role:'documentation',expected:0}]){
    await page.evaluate(t=>{master.photos=[{id:'qa_profile_regression',customer_id:'qa_c1',caption:t.caption,role:t.role,thumbnail_data:t.src}];load();openPage('customers');},item);assert.equal(await page.locator('[data-customer-open="qa_c1"] img').count(),item.expected);
   }
  });
  await check('List filters combine with search, show counts and reset without writes',async()=>{
   await page.evaluate(()=>{master.customers=[{id:'qa_c1',name:'QA Direkt',active:1},{id:'qa_c2',name:'QA Archiv',active:0,billing_mode:'via_customer'}];master.orders=[{id:'qa_o1',title:'QA Garten',customer_id:'qa_c1',status:'Geplant'},{id:'qa_o2',title:'QA Garten',customer_id:'qa_c2',status:'Erledigt'}];master.invoices=[{id:'qa_i1',invoice_number:'QA-1',customer_id:'qa_c1',status:'Entwurf'},{id:'qa_i2',invoice_number:'QA-2',customer_id:'qa_c2',status:'Bezahlt'}];load();window.qaFilterWrites=writes.length+qaRevier.writes.length;openPage('customers');});
   await page.locator('#customers-filters [data-list-filter=active]').selectOption('archived');assert.equal(await page.locator('#customer-list [data-customer]').count(),1);assert.match(await page.locator('#customers-filters [role=status]').innerText(),/1 Treffer.*Archiviert/);
   await page.locator('#customers-filters [data-list-filter=billing_mode]').selectOption('direct');assert.equal(await page.locator('#customer-list [data-customer]').count(),0);
   await page.locator('#customers-filters button').click();assert.equal(await page.locator('#customer-list [data-customer]').count(),2);
   for(const [id,search,row] of [['orders','order-search','[data-edit-order]'],['invoices','invoice-search','[data-invoice]']]){
    await page.evaluate(id=>openPage(id),id);await page.locator('#'+id+'-filters [data-list-filter=customer_id]').selectOption('qa_c2');assert.equal(await page.locator('#'+id+' '+row).count(),1);
    await page.locator('#'+search).fill('nichtvorhanden');await page.waitForTimeout(200);assert.equal(await page.locator('#'+id+' '+row).count(),0);
    await page.locator('#'+id+'-filters button').click();assert.equal(await page.locator('#'+id+' '+row).count(),2);assert.equal(await page.locator('#'+search).inputValue(),'');
    await page.locator('#'+id+'-filters [data-list-filter=status]').selectOption(id==='orders'?'Erledigt':'Bezahlt');assert.equal(await page.locator('#'+id+' '+row).count(),1);await page.locator('#'+id+'-filters button').click();
   }
   await page.evaluate(()=>{qaRevier.equipment=Array.from({length:65},(_,i)=>({id:'qa_filter_equipment_'+i,name:'QA Gerät '+i,condition:i<60?'ready':'defective',record_hash:'e'.repeat(64)}));openPage('revier-equipment');});
   await page.locator('[data-equipment-condition]').selectOption('defective');assert.equal(await page.locator('[data-revier-list=equipment] [data-revier-detail]').count(),5);assert.match(await page.locator('#revier-equipment .revier-state').innerText(),/5.*Defekt/);
   await page.locator('[data-equipment-reset]').click();assert.equal(await page.locator('[data-revier-list=equipment] [data-revier-detail]').count(),40);await page.locator('[data-revier-more=equipment]').click();assert.equal(await page.locator('[data-revier-list=equipment] [data-revier-detail]').count(),65);
   assert.equal(await page.evaluate(()=>writes.length+qaRevier.writes.length-qaFilterWrites),0);
   const access=await page.locator('#list-filter-access').boundingBox(),nav=await page.locator('.bottom').boundingBox();assert(access.y>=nav.y&&access.y+access.height<=840);
   await page.setViewportSize({width:360,height:380});await page.evaluate(()=>openPage('invoices'));assert.equal(await page.locator('.bottom').evaluate(n=>getComputedStyle(n).position),'static');await page.setViewportSize({width:360,height:840});
  });
  await check('All 17 main destinations retain content at 200 percent text on 320 and 360 pixel widths',async()=>{
   const ids=await page.locator('#more-page [data-open]').evaluateAll(nodes=>[...new Set(['home','customers','revier-office',...nodes.map(n=>n.dataset.open)])]);
   for(const width of [320,360])for(const font of [16,32]){await page.setViewportSize({width,height:840});await page.evaluate(font=>document.documentElement.style.fontSize=font+'px',font);for(const id of ids){await page.evaluate(id=>openPage(id),id);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),id+' '+width+' '+font);}}
   await page.setViewportSize({width:320,height:840});await page.evaluate(()=>{document.documentElement.style.fontSize='32px';openPage('invoices');});assert(await page.locator('#invoice-readiness').isVisible());await page.screenshot({path:'outputs/invoice-readiness-200-percent.png'});
   await page.setViewportSize({width:360,height:840});await page.evaluate(()=>document.documentElement.style.fontSize='16px');
  });
  await check('Saving a customer without a photo writes once and reloads the complete state only once',async()=>{
   await page.evaluate(()=>{openPage('customer-edit');const f=el('customer-form');f.reset();f.elements.id.value='';f.elements.name.value='QA Save Read Count';f.elements.name.dispatchEvent(new Event('input',{bubbles:true}));window.qaReadCount=0;window.qaWriteBefore=writes.length;const original=AndroidData.getState;AndroidData.getState=()=>{qaReadCount++;return original();};});
   await page.locator('#customer-form button[type=submit]').click();await page.waitForFunction(()=>currentPage==='customers');
   const result=await page.evaluate(()=>({reads:qaReadCount,writes:writes.length-qaWriteBefore,drafts:HGFormDrafts.list('customer').length}));assert.deepEqual(result,{reads:1,writes:1,drafts:0});assert.deepEqual(errors,[]);
  });
 }finally{await browser.close();}
 fs.writeFileSync('outputs/simple-forms-results.json',JSON.stringify({scope:'synthetic Cloud Chromium, not physical Pixel',results},null,2));if(results.some(r=>r.result==='FAIL'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1});

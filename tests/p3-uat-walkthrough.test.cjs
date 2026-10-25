/* Additional cognitive walkthrough of the operative UI, synthetic bridges only.
 * Reuse the existing fixtures verbatim; do not implement a second business model.
 * Physical permissions/accounts and human timing are deliberately not simulated.
 */
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url'),{chromium}=require('./browser.cjs');
const root=path.resolve(__dirname,'..'),results=[];
function fixture(name){
 const s=fs.readFileSync(path.join(__dirname,name),'utf8'),needle='await page.addInitScript(',at=s.indexOf(needle);
 assert(at>=0,'Existing isolated fixture must exist: '+name);
 const tail=s.slice(at+needle.length),end=tail.match(name==='work-location.test.cjs'?/\n {1,3}\},\{sample\}\);/:/\}\);(?=\n\s*await page\.goto)/);
 assert(end,'Explicit fixture boundary must match: '+name);
 return tail.slice(0,end.index+end[0].indexOf('}')+1);
}
(async()=>{
 const browser=await chromium.launch({headless:true});
 async function scene(name,source,run,arg){
  const page=await browser.newPage({viewport:{width:360,height:900},isMobile:true,hasTouch:true}),errors=[];
  page.setDefaultTimeout(8000);page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
  try{
   await page.addInitScript({content:'('+fixture(source)+')('+(arg?JSON.stringify(arg):'')+');'});
   await page.goto(pathToFileURL(path.join(root,'android/app/src/main/assets/mobile.html')).href);
   await page.waitForFunction(()=>document.body.dataset.usabilityVersion==='1.41'&&document.body.dataset.revierReady==='true');
   await run(page);assert.deepEqual(errors,[]);results.push({name,result:'PASS',fixture:source});
  }catch(error){results.push({name,result:'FAIL',error:error.stack,fixture:source});}
  finally{await page.waitForTimeout(400);await page.close();console.log(results.at(-1));}
 }
 try{
  await scene('UAT 7–12,17–19: name/address, deferred contact, exact customer, inactive remains findable','daily-usability.test.cjs',async p=>{
   await p.locator('.bottom-inner [data-open=revier-properties]').click();await p.locator('#revier-properties [data-open=customers]').click();await p.locator('#add-customer').click();
   for(const [key,value]of Object.entries({name:'QA UAT Garten',street:'QA Gartenweg 1',postal_code:'0000',city:'QA Beispielort'}))await p.locator('#customer-form [name='+key+']').fill(value);
   await p.locator('#customer-form button[type=submit]').click();
   assert.equal(await p.evaluate(()=>writes.at(-1).row.phone),'');assert.equal(await p.evaluate(()=>writes.at(-1).row.email),'');
   await p.locator('#customer-search').fill('QA UAT Garten');const newId=await p.evaluate(()=>writes.find(x=>x.kind==='customer').row.id);await p.locator('[data-customer-open="'+newId+'"]').click();
   await p.getByRole('button',{name:'Kundendaten bearbeiten',exact:true}).click();
   await p.locator('#customer-form [name=phone]').fill('+430000000');await p.locator('#customer-form button[type=submit]').click();
   assert.equal(await p.evaluate(()=>writes.at(-1).row.phone),'+430000000');
   await p.evaluate(id=>editCustomer(id),newId);await p.locator('#customer-form [name=email]').fill('qa-uat@example.invalid');
   await p.locator('#customer-form button[type=submit]').click();assert.equal(await p.evaluate(()=>writes.at(-1).row.email),'qa-uat@example.invalid');
   await p.evaluate(id=>editCustomer(id),newId);await p.locator('#customer-form [name=active]').uncheck();await p.locator('#customer-form button[type=submit]').click();
   assert.equal(await p.evaluate(()=>writes.at(-1).row.active),0);await p.evaluate(()=>openPage('customers'));
   await p.locator('#customer-search').fill('QA UAT Garten');assert.equal(await p.locator('[data-customer-open=qa-new]').count(),1);
  });
  await scene('UAT 20–29: manual start without GPS, pause/resume, explicit completion with optional fields','work-location.test.cjs',async p=>{
   await p.locator('[data-session-action=start]').click();assert.equal(await p.evaluate(()=>locationRequests.length),0);
   await p.locator('#work-location-customer').selectOption('qa_c1');await p.locator('#work-location-start').click();
   await p.waitForFunction(()=>!document.querySelector('#work-location-dialog').open);
   assert.equal(await p.evaluate(()=>timerCalls[0].customer_id),'qa_c1');
   await p.locator('[data-session-action=pause]').click();assert.equal(await p.evaluate(()=>JSON.parse(AndroidData.getWorkSession()).state),'PAUSED');
   await p.locator('[data-session-action=resume]').click();assert.equal(await p.evaluate(()=>JSON.parse(AndroidData.getWorkSession()).state),'RUNNING');
   await p.locator('[data-session-action=stop]').click();assert.equal(await p.evaluate(()=>currentPage),'work-entry');
   assert.equal(await p.locator('#entry-hours').inputValue(),'1.50');await p.locator('#activity').fill('QA UAT Gartenpflege');
   await p.locator('#entry [data-section=material] > summary').click();await p.locator('#entry [name=material]').fill('QA Mulch');
   await p.locator('#entry [name=kilometers]').fill('12');await p.locator('#entry [data-section=notes] > summary').click();
   await p.locator('#entry [name=notes]').fill('QA UAT Hinweis');assert(await p.locator('#photo').isVisible());
   await p.locator('#entry button[type=submit]').click();const saved=await p.evaluate(()=>savedWorks);
   assert.equal(saved.length,1);assert.equal(saved[0].customer_id,'qa_c1');assert.equal(saved[0].material,'QA Mulch');assert.equal(saved[0].kilometers,12);assert.equal(saved[0].notes,'QA UAT Hinweis');
  },{sample:require('./synthetic-fixtures.cjs').profile('minimal').customers.map((c,i)=>({...c,id:'qa_c'+(i+1)}))});
  await scene('UAT 13–16,38–42,57: customer work/photos/documents/appointment context and accessible navigation','p3-customer-hub.test.cjs',async p=>{
   await p.evaluate(()=>openCustomer('qa_c1'));assert.match(await p.locator('.customer-hub-summary').innerText(),/120 m²/);
   assert.match(await p.locator('#design-profile-content').innerText(),/QA Letzter Einsatz/);
   await p.locator('[data-customer-tab=history]').click();assert.equal(await p.locator('.design-documentation-entry').count(),1);
   await p.locator('[data-customer-tab=photos]').click();assert.equal(await p.locator('#customer-photo-library [data-design-action=photo]').count(),1);
   await p.locator('[data-customer-tab=documents]').click();assert.equal(await p.locator('[data-customer-document]').count(),2);
   assert.match(await p.locator('#customer-documents').innerText(),/Unverbindlicher Kostenvoranschlag/);
   await p.locator('[data-customer-new-appointment]').click();assert.equal(await p.locator('#appointment-form [name=customer_id]').inputValue(),'qa_c1');
   assert.equal(await p.evaluate(()=>writes.length),0);
  });
  await scene('UAT 46–51: create, exact customer, edit and complete the existing order entity','design-v139.test.cjs',async p=>{
   await p.evaluate(()=>openPage('orders'));await p.locator('#add-order').click();await p.locator('#order-edit-customer').selectOption('qa_c1');
   await p.locator('#order-edit-title').fill('QA UAT Auftrag');await p.waitForTimeout(150);assert.equal(await p.locator('#order-edit-title').inputValue(),'QA UAT Auftrag');await p.locator('[data-order-window-save]').click();
   const created=await p.evaluate(()=>writes.at(-1));assert(created,JSON.stringify(await p.evaluate(()=>({customer:document.getElementById('order-edit-customer').value,title:document.getElementById('order-edit-title').value,status:document.getElementById('mobile-toast')?.textContent,open:document.getElementById('direct-edit-dialog')?.open}))));assert.equal(created.kind,'order');assert.equal(created.row.customer_id,'qa_c1');assert.equal(created.row.title,'QA UAT Auftrag');
   await p.locator('#order-list [data-edit-order=qa_o1]').first().click();await p.locator('[data-order-window-edit]').click();
   await p.locator('#order-edit-title').fill('QA UAT Korrektur');await p.locator('#order-edit-status').selectOption('Erledigt');await p.locator('[data-order-window-save]').click();
   const corrected=await p.evaluate(()=>writes.at(-1).row);assert.equal(corrected.id,'qa_o1');assert.equal(corrected.customer_id,'qa_c1');assert.equal(corrected.status,'Erledigt');
   assert.equal(await p.evaluate(()=>writes.filter(w=>w.kind==='invoice').length),0);
  });
  await scene('UAT 60–72: all four conscious document types, confirmed prices and separate finalization','offers-photo-library.test.cjs',async p=>{
   for(const type of ['fixed_price','time_material','estimate_binding','estimate_nonbinding']){
    await p.evaluate(()=>OfferUI.openForCustomer('qa_c1'));await p.locator('[data-offer-field=title]').fill('QA UAT '+type);
    await p.locator('[data-offer-field=document_type]').selectOption(type);
    if(type.startsWith('estimate_')){assert.equal(await p.locator('[data-offer-field=binding_confirmed]').isChecked(),false);await p.locator('[data-offer-field=binding_confirmed]').check();}
    await p.locator('[data-offer-step-open="1"]').click();await p.locator('#offer-template').selectOption('qa_svc_hour');await p.locator('[data-line-field=quantity]').fill('2');
    assert.equal(await p.locator('[data-line-field=price]').inputValue(),'50');
    if(type==='fixed_price'){
     await p.locator('[data-line-field=price]').fill('55');await p.locator('#offer-add-line').click();const own=p.locator('[data-offer-line]').last();
     for(const [field,value]of Object.entries({title:'QA ausdrückliche Eigenposition',unit:'h',quantity:'1',price:'5'}))await own.locator('[data-line-field='+field+']').fill(value);
    }
    await p.locator('[data-offer-step-open="2"]').click();await p.locator('[data-offer-field=customer_type]').selectOption('b2b');
    await p.locator('[data-offer-field=valid_until]').fill('2026-11-20');await p.locator('[data-offer-field=execution_window]').fill('QA November');await p.locator('[data-offer-field=scope_confirmed]').check();
    await p.locator('#offer-form button[type=submit]').click();const args=await p.evaluate(()=>calls.filter(c=>c.name==='create_offer_draft').at(-1).args);
    assert.equal(args.document_type,type);assert.equal(args.customer_id,'qa_c1');assert.equal(args.positions[0].price,type==='fixed_price'?55:50);if(type==='fixed_price'){assert.equal(args.positions.length,2);assert.equal(args.positions[0].catalog_entry_id,undefined);assert.equal(args.positions[0].price_source,'user_confirmed');assert.equal(args.positions[1].price,5);}
    assert.equal(await p.evaluate(()=>fixture.master.invoices.length),0);await p.locator('#offer-close').click();
   }
   assert.equal(await p.evaluate(()=>calls.filter(c=>c.name==='finalize_offer'||/share|send/.test(c.name)).length),0);
  });
  await scene('UAT 110: all web settings, advanced information hidden and three deliberate photo qualities','p3-business-actions.test.cjs',async p=>{
   await p.locator('#business-backup-card').waitFor({state:'attached'});await p.evaluate(()=>openPage('settings-page'));await p.locator('#business-backup-card').waitFor({state:'visible'});
   await p.locator('#settings-page #photo-quality').waitFor({state:'attached'});
   await p.evaluate(()=>{const more=document.getElementById('more-page');for(const id of ['business-settings-card','photo-storage-settings'])more.append(document.getElementById(id));});
   await p.waitForFunction(()=>['business-settings-card','photo-storage-settings'].every(id=>document.getElementById(id)?.parentElement.id==='settings-page'));
   assert.equal(await p.locator('#settings-sync-local').isVisible(),false);assert.equal(await p.locator('#settings-sync-transfer').isVisible(),false);
   const settings=await p.locator('#settings-page input:not([type=hidden]),#settings-page select,#settings-page textarea').evaluateAll(nodes=>nodes.map(n=>({id:n.id,name:n.name,type:n.type,options:n.options?[...n.options].map(o=>o.value):undefined,label:n.labels?.[0]?.textContent||n.getAttribute('aria-label')||n.name})));
   assert(settings.length>=1);assert(settings.every(x=>x.id||x.name));assert(!settings.some(s=>s.id==='setting-side-swipe'));const quality=settings.find(s=>s.id==='photo-quality');assert.deepEqual(quality.options,['optimized','high','original']);
   fs.writeFileSync(path.join(root,'outputs/p3-uat-settings.json'),JSON.stringify({classification:'SYNTHETIC_TEST',scope:'operative WebView controls; native controls separately compiled/executed',settings},null,2));
   await p.evaluate(()=>{window.driveSettingsOpened=0;AndroidData.connectDrive=()=>driveSettingsOpened++;});await p.locator('#settings-drive').click();assert.equal(await p.evaluate(()=>driveSettingsOpened),1);
   await p.locator('.settings-advanced > summary').click();assert.equal(await p.locator('#settings-sync-local').isVisible(),true);
  });
 }finally{await browser.close();}
 fs.writeFileSync(path.join(root,'outputs/p3-uat-walkthrough.json'),JSON.stringify({classification:'SYNTHETIC_TEST',method:'Cloud Chromium cognitive walkthrough; existing isolated bridges',physical_device:false,results},null,2));
 if(results.some(r=>r.result!=='PASS'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});

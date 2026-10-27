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
  await check('Capture shows original clock fields, optional notes, a header Back and separate Discard/Save',async()=>{
   await page.evaluate(()=>openPage('work-entry'));for(const id of ['start','end'])assert(await page.locator('#'+id).isVisible());
   await page.screenshot({path:'docs/verification/simple-work-capture-360.png'});
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
   await page.setViewportSize({width:360,height:840});await page.evaluate(()=>document.documentElement.style.fontSize='16px');await page.screenshot({path:'docs/verification/simple-visit-form-360.png'});
  });
  await check('Explicit discard removes the retained visit draft, including after reload',async()=>{
   await page.locator('[data-revier-editor-discard]').click();assert.equal(await page.evaluate(()=>HGFormDrafts.list().filter(d=>d.kind==='visit').length),0);await page.reload();await page.waitForFunction(()=>document.documentElement.dataset.startup==='ready'&&document.body.dataset.simpleReady==='true');assert.equal(await page.evaluate(()=>HGFormDrafts.list().filter(d=>d.kind==='visit').length),0);assert.deepEqual(errors,[]);
  });
 }finally{await browser.close();}
 fs.writeFileSync('outputs/simple-forms-results.json',JSON.stringify({scope:'synthetic Cloud Chromium, not physical Pixel',results},null,2));if(results.some(r=>r.result==='FAIL'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1});

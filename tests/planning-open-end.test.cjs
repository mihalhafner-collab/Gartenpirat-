'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url'),{chromium}=require('./browser.cjs'),acorn=require(process.env.HG_ACORN_MODULE||'acorn');
function bridge(){const code=fs.readFileSync(path.join(__dirname,'planning-ui.test.cjs'),'utf8'),ast=acorn.parse(code,{ecmaVersion:2022}),found=[];function visit(n){if(!n||typeof n!=='object')return;if(n.type==='CallExpression'&&n.callee?.property?.name==='addInitScript')found.push(n.arguments[0]);for(const v of Object.values(n))if(Array.isArray(v))v.forEach(visit);else if(v&&typeof v==='object')visit(v);}visit(ast);assert.equal(found.length,1);return '('+code.slice(found[0].start,found[0].end)+')();';}
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']}),results=[];
 const page=await browser.newPage({viewport:{width:360,height:900}}),errors=[];page.on('pageerror',e=>errors.push(e.message));page.setDefaultTimeout(8000);
 const check=async(name,run)=>{try{await run();results.push({name,result:'PASS'});}catch(error){results.push({name,result:'FAIL',error:error.stack});}console.log(results.at(-1));};
 const planningOptions=async()=>{const panel=page.locator('#appointment-form .planning-more-options');if(!await panel.evaluate(d=>d.open))await panel.locator(':scope>summary').click();};
 const field=name=>page.locator('#appointment-form [name='+name+']'),save=()=>page.locator('#appointment-form button[type=submit]').click();let id;
 try{
  await page.addInitScript({content:bridge()});await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);await page.waitForFunction(()=>window.PlanningUI&&document.body.dataset.fieldReady==='true');
  await page.evaluate(()=>{fixture.master.appointments=[];load();PlanningUI.open('qa_c');});
  await check('New appointment explicitly leaves duration open and stores only its confirmed start',async()=>{
   await page.locator('#planning-new').click();assert(await field('end_open').isChecked());assert(await page.locator('#planning-duration').isHidden());assert(await field('duration').isDisabled());
   await field('title').fill('QA Besichtigung ohne bekannte Dauer');await field('local_start').fill('2099-06-01T08:30');await save();
   const request=await page.evaluate(()=>calls.filter(x=>x.name==='create_appointment').at(-1).args);assert.equal(request.ends_at,'');assert.equal(request.starts_at,'2099-06-01T06:30:00.000Z');assert.equal(request.order_id,'');assert(!('duration' in request)&&!('end_open' in request));id=await page.evaluate(()=>fixture.master.appointments.at(-1).id);
  });
  await check('Open duration is clearly visible on agenda, Today and saved overview',async()=>{
   assert.match(await page.locator('#planning-rows').innerText(),/Ende offen/);await page.evaluate(()=>openPage('home'));assert.match(await page.locator('#planning-home').innerText(),/Ende offen/);
   await page.evaluate(id=>PlanningUI.show(id),id);assert.match(await page.locator('.planning-facts').innerText(),/Ende offen/);assert.match(await page.locator('.planning-facts').innerText(),/Noch offen/);assert.doesNotMatch(await page.locator('#planning-collisions').innerText(),/Keine Überschneidung/);
  });
  await check('1.5 hours stores exactly 90 minutes and retains the original start and record ID',async()=>{
   await page.locator('#planning-edit').click();await field('end_open').uncheck();assert(await page.locator('#planning-duration').isVisible());assert.equal(await field('duration').inputValue(),'');await field('duration').fill('1.5');await save();
   const record=await page.evaluate(id=>fixture.master.appointments.find(x=>x.id===id),id);assert.equal((Date.parse(record.ends_at)-Date.parse(record.starts_at))/60000,90);assert.equal(record.starts_at,'2099-06-01T06:30:00.000Z');
   const request=await page.evaluate(()=>calls.filter(x=>x.name==='update_appointment').at(-1).args);assert.equal(request.appointment_id,id);assert.equal(request.expected_record_hash,'v1');
  });
  await check('Hours can be hidden and restored without losing the entered duration or notes',async()=>{
   await page.evaluate(id=>PlanningUI.edit(id),id);assert.equal(await field('duration').inputValue(),'1.5');await planningOptions();await field('notes').fill('QA Notiz erhalten');await field('end_open').check();await field('end_open').uncheck();assert.equal(await field('duration').inputValue(),'1.5');assert.equal(await field('notes').inputValue(),'QA Notiz erhalten');
   await field('end_open').check();await save();assert.equal(await page.evaluate(id=>fixture.master.appointments.find(x=>x.id===id).ends_at,id),'');
  });
  await check('Existing 35-minute appointments keep exact times when editing unrelated text',async()=>{
   await page.evaluate(()=>{fixture.master.appointments.push({id:'qa_35',customer_id:'qa_c',order_id:'',title:'QA 35 Minuten',starts_at:'2099-06-01T10:00:00Z',ends_at:'2099-06-01T10:35:00Z',timezone:'Europe/Vienna',status:'tentative',weather_profile:'general',updated_at:'v1',test_record:true});load();PlanningUI.edit('qa_35');});
   assert.equal(await field('duration').inputValue(),'0.5833');await planningOptions();await field('notes').fill('QA Nur Text geändert');await save();assert.equal(await page.evaluate(()=>fixture.master.appointments.find(x=>x.id==='qa_35').ends_at),'2099-06-01T10:35:00Z');
  });
  await check('Possible overlaps after an open end are visible without declaring a free interval',async()=>{
   await page.evaluate(()=>PlanningUI.show('qa_35'));const warning=await page.locator('#planning-collisions').innerText();assert.match(warning,/Mögliche Terminkollision/);assert.match(warning,/Endzeit offen/);assert.doesNotMatch(warning,/Keine Überschneidung/);
  });
  await check('Invalid fixed hours send no write and retain the open editor',async()=>{
   await page.evaluate(id=>PlanningUI.edit(id),id);await field('end_open').uncheck();await field('duration').fill('0');const count=await page.evaluate(()=>calls.filter(x=>x.name==='update_appointment').length);await save();assert.equal(await page.evaluate(()=>calls.filter(x=>x.name==='update_appointment').length),count);assert.match(await page.locator('#planning-message').innerText(),/5 Minuten/);assert(await page.locator('#appointment-form').isVisible());
  });
  await check('Rejected saves retain open-end choice and the same retry request',async()=>{
   await field('end_open').check();await planningOptions();await field('notes').fill('QA Konfliktentwurf');await page.evaluate(()=>fail=true);await save();const first=await page.evaluate(()=>calls.filter(x=>x.name==='update_appointment').at(-1).args);await save();const second=await page.evaluate(()=>calls.filter(x=>x.name==='update_appointment').at(-1).args);assert.equal(first.request_id,second.request_id);assert.equal(second.updates.ends_at,undefined);assert(await field('end_open').isChecked());assert.equal(await field('notes').inputValue(),'QA Konfliktentwurf');await page.evaluate(()=>fail=false);await save();
  });
  await check('Open-ended recurring appointments preserve blank ends in their create request',async()=>{
   await page.evaluate(()=>PlanningUI.edit('','qa_c'));await field('title').fill('QA Offene Serie');await field('local_start').fill('2099-06-02T08:30');await planningOptions();await page.locator('.planning-repeat summary').click();await field('occurrences').fill('2');await save();const args=await page.evaluate(()=>calls.filter(x=>x.name==='create_appointment_series').at(-1).args);assert.equal(args.ends_at,'');assert.equal(args.occurrences,2);
  });
  await check('Saved drafts retain open end and previous hours across page reload',async()=>{
   await page.evaluate(()=>PlanningUI.edit('','qa_c'));await field('title').fill('QA Stundenentwurf');await field('end_open').uncheck();await field('duration').fill('2.25');await field('end_open').check();await page.locator('#planning-page>.page-head>.back').click();await page.reload();await page.waitForFunction(()=>window.PlanningUI&&document.body.dataset.fieldReady==='true');await page.evaluate(()=>PlanningUI.open('qa_c'));await page.locator('[data-planning-resume]').filter({hasText:'QA Stundenentwurf'}).click();assert(await field('end_open').isChecked());await field('end_open').uncheck();assert.equal(await field('duration').inputValue(),'2.25');
  });
  await check('Open-end controls and save buttons stay reachable on small screens and at 150 percent text',async()=>{
   for(const width of [320,360,426])for(const size of [16,24]){await page.setViewportSize({width,height:900});await page.evaluate(size=>document.documentElement.style.fontSize=size+'px',size);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));const target=await page.locator('.planning-open-end').boundingBox();assert(target.height>=48);for(const button of await page.locator('.planning-save-actions button').all()){const box=await button.boundingBox();assert(box.height>=48&&box.y>=0&&box.y+box.height<=901);}}
   await page.setViewportSize({width:360,height:900});await page.evaluate(()=>document.documentElement.style.fontSize='16px');await field('end_open').check();await page.locator('.planning-open-end').scrollIntoViewIfNeeded();await page.screenshot({path:'outputs/qa-planning-open-end-360.png'});await field('end_open').uncheck();await field('duration').scrollIntoViewIfNeeded();await page.screenshot({path:'outputs/qa-planning-hours-360.png'});assert.deepEqual(errors,[]);
  });
 }finally{await browser.close();}
 fs.writeFileSync('outputs/qa-planning-open-end-browser.json',JSON.stringify({classification:'SYNTHETIC_TEST',device:false,results},null,2));if(results.some(x=>x.result==='FAIL'))process.exitCode=1;
})().catch(error=>{console.error(error);process.exitCode=1;});

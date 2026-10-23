'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url'),acorn=require('acorn'),{chromium}=require('./browser.cjs');
function bridge(){const source=fs.readFileSync(path.join(__dirname,'planning-ui.test.cjs'),'utf8'),ast=acorn.parse(source,{ecmaVersion:2022}),found=[];function visit(n){if(!n||typeof n!=='object')return;if(n.type==='CallExpression'&&n.callee?.property?.name==='addInitScript')found.push(n.arguments[0]);for(const v of Object.values(n))if(Array.isArray(v))v.forEach(visit);else if(v&&typeof v==='object')visit(v);}visit(ast);assert.equal(found.length,1);return '('+source.slice(found[0].start,found[0].end)+')();';}
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']}),page=await browser.newPage({viewport:{width:360,height:640},hasTouch:true}),results=[],errors=[];
 page.on('pageerror',e=>errors.push(e.message));async function check(name,fn){try{await fn();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.stack});}console.log(results.at(-1));}
 async function screenshot(name){const file='outputs/'+name+'.png';await page.screenshot({path:file,fullPage:true});}
 async function fits(selector){const b=await page.locator(selector).first().boundingBox(),nav=await page.locator('.bottom').boundingBox();assert(b&&b.y>=0&&b.y+b.height<=nav.y+1,selector+' must be above navigation: '+JSON.stringify({b,nav}));}
 async function accessible(scope){const violations=await page.locator(scope).evaluate(root=>{const visible=e=>{const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width&&r.height&&s.visibility!=='hidden'&&s.display!=='none';};return [...root.querySelectorAll('button,summary,[role=button]')].filter(visible).map(e=>{const r=e.getBoundingClientRect();return r.width<47.9||r.height<47.9?{id:e.id,text:e.textContent.trim(),width:r.width,height:r.height}:null;}).filter(Boolean);});assert.deepEqual(violations,[]);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));}
 try{
  await page.clock.setFixedTime(new Date('2026-10-22T07:00:00Z'));await page.addInitScript({content:bridge()});await page.addInitScript(()=>{
   fixture.master.appointments=fixture.master.appointments.slice(0,4).map((a,i)=>({...a,id:'qa_overview_'+i,title:'QA Einsatz '+i,starts_at:'2026-10-22T'+String(8+i*2).padStart(2,'0')+':00:00Z',ends_at:'2026-10-22T'+String(9+i*2).padStart(2,'0')+':00:00Z',location:'QA Garten '+i}));
   AndroidData.getWorkSession=()=>JSON.stringify(timer);
  });
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);await page.waitForFunction(()=>document.body.dataset.fieldReady==='true'&&document.body.dataset.todayReady==='true');
  await check('97 Today360x640 restores95 carousel and puts work controls above lower navigation',async()=>{
   assert.equal(await page.locator('#home .folder-grid,#home .hero,#home>.brand').count(),0);assert.equal(await page.locator('#app-drawer .brand').count(),1);
   assert.equal(await page.locator('#planning-home [data-appointment-open]').count(),1);assert.equal(await page.locator('#home .today-header h1').innerText(),'Heute');await fits('#workday-timer [data-session-action=start]');await fits('#field-today-actions .quick');await fits('#planning-home [data-appointment-open="qa_overview_0"]');assert.equal(await page.locator('#design-metrics [data-design-metric]').count(),2);await accessible('#home');await screenshot('qa-97-today-360-100');
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
  await check('97 active timer and recent work leave appointment time and customer visible at616px height',async()=>{
   const previous=await page.evaluate(()=>fixture.master.worklogs);await page.setViewportSize({width:360,height:616});
   try{
    await page.evaluate(()=>{fixture.master.worklogs=[{id:'qa_recent_overview',customer_id:'qa_c',work_date:'2026-10-22',activity:'QA gespeicherte Arbeit',hours:1,billed:0}];load();openPage('home');scrollTo(0,0);});
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
   assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('hausgarten.planning.draft.v1')).data.title),'QA retained overview draft');
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
 }finally{await browser.close();}
 fs.writeFileSync('outputs/qa-97-overview-accessibility.json',JSON.stringify({classification:'SYNTHETIC_TEST',viewport:[360,640],font_percent:[100,150,200],results},null,2));
 console.log(JSON.stringify({checks:results.length,failed:results.filter(r=>r.result==='FAIL').length}));if(results.some(r=>r.result==='FAIL'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});

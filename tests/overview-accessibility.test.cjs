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
  await check('96 Today360x640 shows start and two daily appointments without scrolling',async()=>{
   assert.equal(await page.locator('#home .folder-grid,#home .hero,#home>.brand').count(),0);assert.equal(await page.locator('#app-drawer .brand').count(),1);
   assert.equal(await page.locator('#planning-home [data-appointment-open]').count(),3);await fits('#workday-timer [data-session-action=start]');await fits('#planning-home [data-appointment-open="qa_overview_1"]');await accessible('#home');await screenshot('qa-96-today-360-100');
  });
  await check('96 all daily appointments remain reachable with one expand action',async()=>{
   await page.locator('[data-today-all]').click();assert.equal(await page.locator('#planning-home [data-appointment-open]').count(),4);await page.locator('[data-today-all]').click();assert.equal(await page.locator('#planning-home [data-appointment-open]').count(),3);
  });
  await check('96 planning has three control rows and first appointment above navigation',async()=>{
   await page.evaluate(()=>PlanningUI.open());assert.equal(await page.locator('.planning-range,.planning-modebar,.planning-week-strip').count(),3);assert.equal(await page.locator('#planning-tools').evaluate(d=>d.open),false);
   await fits('#planning-rows [data-appointment-open]');await fits('#planning-new');await accessible('#planning-page');await screenshot('qa-96-planning-360-100');
   await page.locator('#planning-tools>summary').click();assert(await page.locator('#planning-suggest').isVisible());assert(await page.locator('#planning-customer-filter').isVisible());assert(await page.locator('.planning-actual-hours').isVisible());await page.locator('#planning-tools>summary').click();
  });
  await check('96 timer survives Today planning Today using the unchanged session bridge',async()=>{
   await page.evaluate(()=>openPage('home'));await page.locator('[data-session-action=start]').click();await page.locator('#work-location-customer').selectOption('qa_c');await page.locator('#work-location-start').click();const before=await page.evaluate(()=>({...timer,starts}));assert.equal(before.state,'RUNNING');assert.equal(before.starts,1);
   await page.evaluate(()=>{timer.worked_ms=150000;PlanningUI.open();});await page.evaluate(()=>openPage('home'));assert.equal(await page.evaluate(()=>timer.id),before.id);assert.equal(await page.evaluate(()=>starts),1);assert.match(await page.locator('.timer-state strong').innerText(),/00:02:30/);assert(await page.locator('[data-session-action=stop]').isVisible());
  });
  await check('96 planning draft survives overview changes',async()=>{
   await page.evaluate(()=>PlanningUI.open());await page.locator('#planning-new').click();await page.locator('#appointment-form [name=title]').fill('QA retained overview draft');await page.evaluate(()=>openPage('home'));
   assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('hausgarten.planning.draft.v1')).data.title),'QA retained overview draft');
  });
  await check('96 150 and200percent text preserve touch targets text and horizontal bounds',async()=>{
   for(const percent of [150,200]){
    await page.evaluate(percent=>{document.documentElement.style.fontSize=percent+'%';openPage('home');scrollTo(0,0);},percent);await accessible('#home');await page.locator('[data-session-action=stop]').evaluate(e=>e.scrollIntoView({block:'center'}));await fits('[data-session-action=stop]');await screenshot('qa-96-today-360-'+percent);
    await page.evaluate(()=>PlanningUI.open());await accessible('#planning-page');await fits('#planning-new');await screenshot('qa-96-planning-360-'+percent);
    const clipped=await page.locator('#home .today-customer,#home .today-place,#planning-page .planning-customer,#planning-page .planning-what').evaluateAll(nodes=>nodes.filter(e=>e.getBoundingClientRect().width>0&&e.scrollWidth>e.clientWidth+1&&['hidden','clip'].includes(getComputedStyle(e).overflowX)).map(e=>e.textContent));assert.deepEqual(clipped,[]);
   }
  });
  await check('96 overview exposes no script errors or duplicate critical IDs',async()=>{assert.deepEqual(errors,[]);for(const id of ['planning-home','workday-timer','sync-status','planning-body'])assert.equal(await page.locator('#'+id).count(),1);});
 }finally{await browser.close();}
 fs.writeFileSync('outputs/qa-96-overview-accessibility.json',JSON.stringify({classification:'SYNTHETIC_TEST',viewport:[360,640],font_percent:[100,150,200],results},null,2));
 console.log(JSON.stringify({checks:results.length,failed:results.filter(r=>r.result==='FAIL').length}));if(results.some(r=>r.result==='FAIL'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});

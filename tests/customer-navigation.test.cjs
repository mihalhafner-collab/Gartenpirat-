'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs'),fixtureSource=require('./ui-fixture-source.cjs'),seed=require('./synthetic-fixtures.cjs').profile();
const baseline=process.argv.includes('--baseline'),samples=[],checks=[],errors=[];
const files=['revier-solo-ui.js','revier-solo-ui.css','mobile.html','navigation-status.js'];
const evidence={classification:'SYNTHETIC_TEST',fixture:'qa_customer_navigation',source_commit:require('node:child_process').execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),scope:'Bundled-page Cloud Chromium; no Pixel/native performance assertion; no productive data',assets:Object.fromEntries(files.map(n=>[n,crypto.createHash('sha256').update(fs.readFileSync('android/app/src/main/assets/'+n)).digest('hex')]))};
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:360,height:840},hasTouch:true});page.setDefaultNavigationTimeout(30000);page.setDefaultTimeout(10000);page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.dismiss());
  await page.addInitScript({content:fixtureSource('daily-usability.test.cjs')});
  await page.addInitScript(seed=>{
   master={...seed,customers:Array.from({length:150},(_,i)=>({...seed.customers[0],id:'qa_c'+(i+1),name:i?'QA Kunde '+String(i).padStart(3,'0'):'QA Gartenkunde',active:1,city:'QA Beispielort'})),orders:[],invoices:[],photos:[],worklogs:Array.from({length:600},(_,i)=>({...seed.worklogs[0],id:'qa_navigation_work_'+i,customer_id:'qa_c'+(i%150+1),activity:'QA Gartenpflege',work_date:'2026-10-27',hours:1}))};
   window.qaReads=[];window.qaTourResponse='valid';
   const day=()=>new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Vienna',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
   const tour=()=>({tour:{id:'qa_navigation_tour',date:day(),visit_ids:[]},stops:[]});
   AndroidData.ownerCall=(name,raw)=>{
    qaReads.push({name,args:JSON.parse(raw)});const good=data=>JSON.stringify({ok:true,data});
    if(name==='get_revier_overview'){const data={date:day(),due_visits:[],visits:[],open_inquiries:0};if(qaTourResponse==='valid')data.tour=tour();if(qaTourResponse==='other-day'){data.tour=tour();data.tour.tour.date='2000-01-01';}if(qaTourResponse==='malformed')data.tour={stops:'invalid'};return good(data);}
    if(name==='get_day_tour')return good(tour());
    if(name==='get_full_backup_status')return good({requires_setup:true});
    if(name.startsWith('get_')||name.startsWith('list_')||name.startsWith('search_')||name.startsWith('preview_')||name==='integration_health')return good({settings:{},integration:{},items:[],total:0,offers:[],templates:[]});
    writes.push({unexpected_owner_tool:name});return JSON.stringify({ok:false,error:{code:'QA_UNEXPECTED_WRITE',message:'QA navigation cannot write'}});
   };
  },seed);
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);await page.waitForFunction(()=>document.documentElement.dataset.startup==='ready');
  const cdp=await page.context().newCDPSession(page);await cdp.send('Profiler.enable');await cdp.send('Profiler.start');
  for(let i=0;i<12;i++)for(const id of ['revier-office','home']){
   const sample=await page.evaluate(async id=>{qaReads.length=0;const start=performance.now();document.querySelector('.bottom [data-open="'+id+'"]').click();const handler=performance.now()-start;await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));return {target:id,handler_ms:handler,to_frame_ms:performance.now()-start,reads:qaReads.map(r=>r.name),active:currentPage,writes:writes.length};},id);
   assert.equal(sample.active,id);assert.equal(sample.writes,0);samples.push(sample);
  }
  const {profile}=await cdp.send('Profiler.stop'),counts=new Map();for(const id of profile.samples||[])counts.set(id,(counts.get(id)||0)+1);
  evidence.profile_top=profile.nodes.filter(n=>counts.has(n.id)).map(n=>({function:n.callFrame.functionName,url:n.callFrame.url.split('/').pop(),line:n.callFrame.lineNumber+1,samples:counts.get(n.id)})).sort((a,b)=>b.samples-a.samples).slice(0,12);
  if(!baseline){
   async function check(name,fn){await fn();checks.push({name,result:'PASS'});console.log('PASS '+name);}
   await check('Three root destinations and one current tab',async()=>{assert.deepEqual(await page.locator('.bottom .nav').allTextContents(),['Heute','Kunden','Büro']);await page.locator('.bottom [data-open=customers]').click();assert.equal(await page.evaluate(()=>currentPage),'customers');assert.equal(await page.locator('.bottom [aria-current=page]').count(),1);assert.equal(await page.locator('.bottom [data-open=customers]').getAttribute('aria-current'),'page');});
   await check('Identical choices preserve option nodes and selections; actual new data refreshes them',async()=>{
    const result=await page.evaluate(()=>{
     master.orders=[{id:'qa_nav_order1',customer_id:'qa_c1',title:'QA Erstauftrag',status:'Geplant',test_record:true},{id:'qa_nav_order2',customer_id:'qa_c1',title:'QA Zweitauftrag',status:'Geplant',test_record:true}];load();el('customer').value='qa_c1';ordersForCustomer();el('order').value='qa_nav_order2';const c=el('customer').options[1],o=el('order').options[1];openPage('revier-office');openPage('home');const retained=c===el('customer').options[1]&&o===el('order').options[1]&&el('order').value==='qa_nav_order2';
     master.customers[0].name='QA Geänderter Name';master.customers.push({...master.customers[0],id:'qa_nav_new',name:'QA Neu vorhanden'});load();const refreshed=el('customer').querySelector('[value=qa_c1]').textContent==='QA Geänderter Name'&&!!el('customer').querySelector('[value=qa_nav_new]');master.customers.pop();master.customers[0].name='QA Gartenkunde';load();el('customer').value='qa_c2';ordersForCustomer();const contextCleared=el('order').value===''&&!el('order').querySelector('[value=qa_nav_order2]');return{retained,refreshed,contextCleared};
    });assert.deepEqual(result,{retained:true,refreshed:true,contextCleared:true});
   });
   await check('Customer search, profile and Back preserve list context without writes',async()=>{await page.locator('.bottom [data-open=customers]').click();await page.locator('#customer-search').fill('QA Gartenkunde');await page.locator('[data-customer-open=qa_c1]').click();assert.equal(await page.evaluate(()=>currentPage),'customer-profile-page');assert.equal(await page.locator('.bottom [data-open=customers]').getAttribute('aria-current'),'page');await page.evaluate(()=>handleAndroidBack());assert.equal(await page.evaluate(()=>currentPage),'customers');assert.equal(await page.locator('#customer-search').inputValue(),'QA Gartenkunde');assert.equal(await page.locator('[data-customer-open]').count(),1);assert.equal(await page.evaluate(()=>writes.length),0);});
   await check('Root Back returns to Today without a tab loop',async()=>{await page.locator('.bottom [data-open=revier-office]').click();await page.evaluate(()=>handleAndroidBack());assert.equal(await page.evaluate(()=>currentPage),'home');assert.equal(await page.evaluate(()=>handleAndroidBack()),false);});
   await check('Contextual customer selection retains its property return path',async()=>{await page.evaluate(()=>{openPage('revier-properties');openPage('customers');openCustomer('qa_c1');handleAndroidBack();handleAndroidBack();});assert.equal(await page.evaluate(()=>currentPage),'revier-properties');await page.locator('.bottom [data-open=home]').click();});
   await check('Office navigation does not fetch the unrelated tour overview',async()=>{await page.evaluate(()=>qaReads.length=0);await page.locator('.bottom [data-open=revier-office]').click();assert.equal(await page.evaluate(()=>qaReads.some(r=>r.name==='get_revier_overview'||r.name==='get_day_tour')),false);});
   await check('Today reuses the tour in the same complete overview response',async()=>{await page.evaluate(()=>{qaTourResponse='valid';qaReads.length=0;openPage('home');});assert.equal(await page.evaluate(()=>qaReads.filter(r=>r.name==='get_revier_overview').length),1);assert.equal(await page.evaluate(()=>qaReads.filter(r=>r.name==='get_day_tour').length),0);});
   await check('Absent older tour response falls back to the existing local read',async()=>{await page.evaluate(()=>{openPage('revier-office');qaTourResponse='absent';qaReads.length=0;openPage('home');});assert.equal(await page.evaluate(()=>qaReads.filter(r=>r.name==='get_day_tour').length),1);assert.equal(await page.locator('#revier-today [data-today-read-error]').count(),0);});
   await check('Malformed present tour is a visible read error, not an empty success',async()=>{await page.evaluate(()=>{openPage('revier-office');qaTourResponse='malformed';qaReads.length=0;openPage('home');});assert(await page.locator('#revier-today [data-today-read-error]').isVisible());assert.equal(await page.evaluate(()=>qaReads.some(r=>r.name==='get_day_tour')),false);await page.evaluate(()=>{qaTourResponse='valid';RevierUI.refresh();});assert.equal(await page.locator('#revier-today [data-today-read-error]').count(),0);});
   await check('A present tour from another date is rejected rather than mixed into Today',async()=>{await page.evaluate(()=>{openPage('revier-office');qaTourResponse='other-day';openPage('home');});assert(await page.locator('#revier-today [data-today-read-error]').isVisible());await page.evaluate(()=>{qaTourResponse='valid';RevierUI.refresh();});});
   await check('A signalled business change is read once when navigation already consumes it',async()=>{const reads=await page.evaluate(async()=>{document.activeElement?.blur();let count=0;const previous=AndroidData.getState;AndroidData.getState=()=>{count++;return previous();};onLocalBusinessChanged();openPage('revier-office');await new Promise(r=>setTimeout(r,180));AndroidData.getState=previous;return count;});assert.equal(reads,1);});
   await check('Narrow and enlarged navigation remains readable and reachable',async()=>{for(const width of [320,360,426]){await page.setViewportSize({width,height:900});for(const size of ['16px','32px']){await page.evaluate(size=>document.documentElement.style.fontSize=size,size);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));for(const b of await page.locator('.bottom .nav').all()){const box=await b.boundingBox();assert(box.width>=48&&box.height>=48);}}}});
   assert.equal(await page.evaluate(()=>writes.length),0);assert.equal(await page.evaluate(()=>HGFormDrafts.list().length),0);
  }
  assert.deepEqual(errors,[]);evidence.samples=samples;evidence.checks=checks;evidence.errors=errors;
  const stats=id=>{const rows=samples.filter(s=>s.target===id),sorted=rows.map(s=>s.to_frame_ms).sort((a,b)=>a-b);return{samples:rows.length,median_to_frame_ms:sorted[Math.floor(sorted.length/2)],p95_to_frame_ms:sorted.at(-1),owner_reads_per_visit:rows.map(r=>r.reads.length)};};evidence.summary={today:stats('home'),office:stats('revier-office')};
  fs.mkdirSync('outputs',{recursive:true});fs.writeFileSync(baseline?'docs/verification/customer-navigation-before-2026-10-27.json':'outputs/customer-navigation-after.json',JSON.stringify(evidence,null,2));console.log(JSON.stringify({baseline,summary:evidence.summary,profile:evidence.profile_top}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});

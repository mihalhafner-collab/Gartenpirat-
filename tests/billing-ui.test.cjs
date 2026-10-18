'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'chrome'}),results=[];
 try{
  const page=await browser.newPage({viewport:{width:412,height:915},isMobile:true,hasTouch:true}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.addInitScript(()=>{
   window.master={customers:[{id:'direct',name:'QA Müller'},{id:'payer',name:'QA Brennter'},{id:'site',name:'QA Kirschnerhof',billing_mode:'via_customer',default_billing_customer_id:'payer'},{id:'other',name:'QA Hofer',billing_mode:'via_customer',default_billing_customer_id:'payer'}],orders:[],photos:[],invoices:[],worklogs:[{id:'a',customer_id:'site',billing_customer_id:'payer',work_date:'2026-10-13',activity:'Hecke',hours:6.5},{id:'b',customer_id:'other',billing_customer_id:'payer',work_date:'2026-10-12',activity:'Pflege',hours:7},{id:'c',customer_id:'direct',work_date:'2026-10-13',activity:'Rasen',hours:2}]};
   window.writes=[];window.pending=[];
   function save(kind,raw){const row=JSON.parse(raw);row.id||='qa_new_'+writes.length;writes.push({kind,row});pending.push({client_id:'qa_'+writes.length,kind,payload:row});return row.id;}
   window.AndroidData={getState:()=>JSON.stringify({master,pending}),getSyncStatus:()=>JSON.stringify({configured:false,pending:pending.length}),getAppInfo:()=>JSON.stringify({versionName:'1.42.0'}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),saveCustomer:r=>save('customer',r),saveOrder:r=>save('order',r),saveWorklog:r=>save('worklog',r),synchronize:()=>{}};
   window.AndroidPhone={dial:()=>{},openMaps:()=>{}};window.AndroidSpeech={start:()=>{}};
  });
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);await page.waitForSelector('#billing-page',{state:'attached'});
  async function test(name,fn){try{await fn();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.message});}console.log(results.at(-1));}
  await test('No business writes during UI initialization',async()=>{assert.deepEqual(errors,[]);assert.equal(await page.evaluate(()=>writes.length),0);});
  await test('Profile uses stable ID and shows recipient',async()=>{await page.evaluate(()=>openCustomer('site'));assert.equal(await page.locator('#customer-billing-summary').innerText(),'Abrechnung über: QA Brennter');});
  await test('Customer mode and existing recipient editable',async()=>{await page.evaluate(()=>editCustomer('site'));assert.equal(await page.locator('#customer-billing-mode').inputValue(),'via_customer');assert.equal(await page.locator('#customer-billing-customer').inputValue(),'payer');await page.locator('#customer-form [name=notes]').fill('QA Nachtrag');await page.locator('#customer-form button[type=submit]').click();assert.equal(await page.evaluate(()=>writes.at(-1).row.default_billing_customer_id),'payer');});
  await test('New work auto-fill and individual override persist actual site',async()=>{await page.evaluate(()=>{openPage('work-entry');document.getElementById('customer').value='site';document.getElementById('customer').dispatchEvent(new Event('change',{bubbles:true}));});assert.equal(await page.locator('#entry-billing-customer').inputValue(),'payer');await page.locator('[data-picker-for="entry-billing-customer"]').click();await page.locator('.design-picker-option[data-value="site"]').click();await page.locator('#activity').fill('QA Ausnahme');await page.locator('#entry-hours').fill('2');await page.evaluate(()=>document.getElementById('entry').requestSubmit());await page.waitForFunction(()=>writes.some(w=>w.kind==='worklog'));const row=await page.evaluate(()=>writes.find(w=>w.kind==='worklog').row);assert.equal(row.customer_id,'site');assert.equal(row.billing_customer_id,'site');});
  await test('Existing work recipient can be edited and saved',async()=>{await page.evaluate(()=>openWorklogDetail('a'));assert.equal(await page.locator('#edit-billing-customer').inputValue(),'payer');await page.evaluate(()=>{document.getElementById('edit-billing-customer').value='site';document.getElementById('edit-billing-customer').dispatchEvent(new Event('change',{bubbles:true}));saveWorklogDetail(true);});assert.equal(await page.evaluate(()=>writes.at(-1).row.billing_customer_id),'site');assert.equal(await page.evaluate(()=>master.customers.find(c=>c.id==='site').default_billing_customer_id),'payer');});
  await test('Group totals retain both actual sites and dated work rows',async()=>{await page.evaluate(()=>{pending=[];load();openPage('billing-page');});const card=page.locator('#billing-group-list .card').filter({has:page.locator('h3',{hasText:'QA Brennter'})});assert.equal(await card.locator('.billing-total').innerText(),'13,5 h');assert((await card.innerText()).includes('QA Kirschnerhof'));assert((await card.innerText()).includes('QA Hofer'));assert.equal(await card.locator('[data-edit-worklog]').count(),2);});
  await test('Invoice preparation uses recipient and keeps sites',async()=>{const draft=await page.evaluate(()=>InvoiceFlow.fromWorklogs(worklogs().filter(w=>['a','b'].includes(w.id)).map(w=>({...w,work_customer_name:customerName(w.customer_id)}))));assert.equal(draft.customer_id,'payer');assert(draft.positions[0].description.includes('QA Hofer'));assert(draft.positions[1].description.includes('QA Kirschnerhof'));assert.deepEqual(draft.worklog_ids,['b','a']);});
  await test('Proven external invoice position excluded, cancelled invoice not treated as proof',async()=>{
    await page.evaluate(()=>{master.invoices=[{id:'qa_invoice',customer_id:'payer',sevdesk_id:'qa_external',status:'Gestellt',worklog_ids:[],positions:[{worklog_id:'a'}]}];load();});
    assert.equal(await page.locator('#billing-group-list [data-edit-worklog="a"]').count(),0);
    await page.evaluate(()=>{master.invoices[0].status='Storniert';load();});assert.equal(await page.locator('#billing-group-list [data-edit-worklog="a"]').count(),1);
    await page.evaluate(()=>{master.invoices=[];load();});
  });
  await test('Small screens have no horizontal overflow',async()=>{for(const width of [320,360,412,1280]){await page.setViewportSize({width,height:915});for(const id of ['billing-page','work-entry']){await page.evaluate(id=>openPage(id),id);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));}}assert.deepEqual(errors,[]);});
  await test('Individual draft recipient survives render and reload without a write',async()=>{
    await page.evaluate(()=>{openPage('work-entry');document.getElementById('customer').value='site';ordersForCustomer();document.getElementById('entry-billing-customer').value='site';document.getElementById('entry-billing-customer').dispatchEvent(new Event('change',{bubbles:true}));document.getElementById('activity').value='QA Entwurf';document.getElementById('entry-hours').value='2';document.getElementById('activity').dispatchEvent(new Event('input',{bubbles:true}));render();});
    assert.equal(await page.locator('#entry-billing-customer').inputValue(),'site');
    await page.waitForFunction(()=>JSON.parse(localStorage.getItem('hausgarten.entry.draft.v1')).activity==='QA Entwurf');
    await page.reload();await page.waitForSelector('#billing-page',{state:'attached'});
    assert.equal(await page.locator('#entry-billing-customer').inputValue(),'site');assert.equal(await page.evaluate(()=>writes.length),0);
    await page.evaluate(()=>document.getElementById('entry').reset());
  });
  await page.setViewportSize({width:412,height:915});await page.evaluate(()=>openPage('billing-page'));await page.screenshot({path:'outputs/Billing-v1.42.png'});
 }finally{await browser.close();}
 fs.writeFileSync('outputs/Billing-UI-v1.42.json',JSON.stringify({method:'actual_browser_with_isolated_bridge',results},null,2));
 if(results.some(r=>r.result==='FAIL'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});

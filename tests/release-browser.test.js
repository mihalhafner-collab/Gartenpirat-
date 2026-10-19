/* Actual browser workflows against the disposable laptop server and Android UI fixture.
 * AndroidData in the mobile fixture is a mock: this is NOT a real-device test.
 */
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {chromium}=require('./browser.cjs');
const results=[];
const laptopUrl=process.env.QA_LAPTOP_URL||'http://127.0.0.1:18765';
let diagnosticPage;
async function check(name, task){try{await task();results.push({name,result:'PASS'});}catch(error){const diagnostic=diagnosticPage?await diagnosticPage.evaluate(()=>({notice:document.querySelector('#notice')?.textContent,editorOpen:document.querySelector('#editor-dialog')?.open,fields:[...document.querySelectorAll('#editor-form input,#editor-form select')].map(e=>({name:e.name,value:e.value,valid:e.validity.valid,message:e.validationMessage}))})):null;const failure={name,result:'FAIL',error:error.stack,diagnostic};results.push(failure);console.log(JSON.stringify(failure));}}
async function main(){
  const browser=await chromium.launch({headless:true,channel:'chrome'});
  const context=await browser.newContext({viewport:{width:1365,height:900}});
  context.setDefaultTimeout(12000);
  const page=await context.newPage();
  diagnosticPage=page;
  const errors=[]; page.on('pageerror',error=>errors.push(error.message));
  await page.goto(laptopUrl); await page.waitForSelector('.customer-card',{state:'attached'});
  const priorOffline=await page.request.get(laptopUrl+'/api/worklogs').then(r=>r.json());
  for(const row of priorOffline.filter(w=>w.id!=='wrk_qa'))await page.request.delete(laptopUrl+'/api/worklogs/'+row.id);
  await page.evaluate(()=>loadAll());
  await check('Desktop startup and all navigation views',async()=>{
    for(const name of ['customers','orders','worklogs','photos','invoices','customer','dashboard']){
      await page.locator(`#navigation [data-view="${name}"]`).click();
      assert(await page.locator(`#view-${name}`).isVisible());
    }
    assert.deepEqual(errors,[]);
  });
  await check('Customer CRUD through visible form',async()=>{
    await page.locator('#navigation [data-view="customers"]').click();
    await page.locator('#view-customers [data-new="customer"]').click();
    await page.locator('#editor-form [name="name"]').fill('QA Browser Kunde');
    await page.locator('#editor-form [name="phone"]').fill('+431234567');
    await page.locator('#editor-form button[type="submit"]').click();
    await page.waitForFunction(()=>!document.querySelector('#editor-dialog').open);
    const card=page.locator('.customer-card').filter({hasText:'QA Browser Kunde'});
    await card.locator('[data-edit-customer]').click();
    await page.locator('#editor-form [name="name"]').fill('QA Browser Kunde geändert');
    await page.locator('#editor-form button[type="submit"]').click();
    await page.waitForFunction(()=>!document.querySelector('#editor-dialog').open);
    await page.locator('.customer-card').filter({hasText:'QA Browser Kunde geändert'}).waitFor({state:'visible'});
    page.once('dialog',dialog=>dialog.accept());
    await page.locator('.customer-card').filter({hasText:'QA Browser Kunde geändert'}).locator('[data-remove]').click();
    await page.locator('.customer-card').filter({hasText:'QA Browser Kunde geändert'}).waitFor({state:'detached'});
  });
  await check('Customer, order, worklog options and calculated save',async()=>{
    await page.evaluate(()=>openEditor('worklog',{customer_id:'qa_cus_qa',order_id:'qa_ord_qa'}));
    await page.locator('#editor-form [name="start_time"]').fill('08:00');
    await page.locator('#editor-form [name="end_time"]').fill('14:30');
    await page.locator('#editor-form [name="break_minutes"]').fill('60');
    await page.locator('#editor-form [name="activity"]').fill('Browser Arbeitsablauf');
    assert((await page.locator('#worklog-duration-preview').innerText()).includes('5,5'));
    await page.locator('#editor-form button[type="submit"]').click();
    await page.waitForFunction(()=>!document.querySelector('#editor-dialog').open);
    const rows=await page.request.get(laptopUrl+'/api/worklogs').then(r=>r.json());
    const row=rows.find(r=>r.activity==='Browser Arbeitsablauf'); assert.equal(row.hours,5.5); assert.equal(row.order_id,'qa_ord_qa');
    await page.evaluate(()=>loadAll());
    await page.evaluate(id=>openEditor('worklog',state.worklogs.find(w=>w.id===id)),row.id);
    await page.locator('#editor-form [name="activity"]').fill('Browser Nachtrag');
    await page.locator('#editor-form button[type="submit"]').click();
    await page.waitForFunction(()=>!document.querySelector('#editor-dialog').open);
    await page.request.delete(laptopUrl+'/api/worklogs/'+row.id);
    await page.evaluate(()=>loadAll());
  });
  await check('Order CRUD through form and customer relation',async()=>{
    await page.evaluate(()=>openEditor('order',{customer_id:'qa_cus_qa'}));
    await page.locator('#editor-form [name="title"]').fill('QA Neuer Auftrag');
    await page.locator('#editor-form [name="location"]').fill('Testort');
    await page.locator('#editor-form button[type="submit"]').click();
    await page.waitForFunction(()=>!document.querySelector('#editor-dialog').open);
    const row=await page.request.get(laptopUrl+'/api/orders').then(r=>r.json()).then(rows=>rows.find(r=>r.title==='QA Neuer Auftrag'));
    assert.equal(row.customer_id,'qa_cus_qa');
    await page.evaluate(row=>openEditor('order',row),row);
    await page.locator('#editor-form [name="description"]').fill('Besichtigung geändert');
    await page.locator('#editor-form button[type="submit"]').click();
    await page.waitForFunction(()=>!document.querySelector('#editor-dialog').open);
    await page.evaluate(()=>showView('orders'));
    const card=page.locator('.order-card').filter({hasText:'QA Neuer Auftrag'});
    await card.filter({hasText:'Besichtigung geändert'}).waitFor();
    page.once('dialog',dialog=>dialog.accept());await card.locator('[data-remove]').click();await card.waitFor({state:'detached'});
  });
  await check('Archive selection preserved when editing historical work',async()=>{
    await page.evaluate(()=>{state.customers.find(c=>c.id==='qa_cus_qa').active=0;openEditor('worklog',state.worklogs.find(w=>w.id==='wrk_qa'));});
    assert.equal(await page.locator('#worklog-customer').inputValue(),'qa_cus_qa');
    await page.locator('#editor-dialog [data-close]').first().click();await page.evaluate(()=>loadAll());
  });
  await check('Multiple photo upload, compression and linked previews',async()=>{
    const prior=await page.request.get(laptopUrl+'/api/photos').then(r=>r.json());
    for(const row of prior)await page.request.delete(laptopUrl+'/api/photos/'+row.id);
    await page.evaluate(()=>loadAll());
    await page.evaluate(()=>openEditor('photo',{customer_id:'qa_cus_qa',order_id:'qa_ord_qa'}));
    const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jD1sAAAAASUVORK5CYII=','base64');
    await page.locator('#editor-form [name="files"]').setInputFiles([{name:'qa1.png',mimeType:'image/png',buffer:png},{name:'qa2.png',mimeType:'image/png',buffer:png}]);
    await page.locator('#editor-form button[type="submit"]').click();
    await page.waitForFunction(()=>!document.querySelector('#editor-dialog').open);
    const rows=await page.request.get(laptopUrl+'/api/photos').then(r=>r.json());assert.equal(rows.length,2);
    assert(rows.every(r=>r.customer_id==='qa_cus_qa'&&r.order_id==='qa_ord_qa'));
    await page.evaluate(()=>loadAll());await page.evaluate(()=>showView('photos'));assert.equal(await page.locator('.photo-card img').count(),2);
    for(const row of rows)await page.request.delete(laptopUrl+'/api/photos/'+row.id);
    await page.evaluate(()=>loadAll());
  });
  await check('Address search, worklog filters, reset and customer history',async()=>{
    await page.locator('#navigation [data-view="customers"]').click();
    await page.locator('#customer-search').fill('Gartenstraße');await page.waitForTimeout(200);
    assert.equal(await page.locator('.customer-card').count(),1);
    await page.locator('[data-customer-detail="qa_cus_qa"]').first().click();
    assert((await page.locator('#customer-detail-content').innerText()).includes('Hecke pflegen'));
    await page.locator('#customer-detail-dialog [data-close]').click();
    await page.locator('#navigation [data-view="worklogs"]').click();
    await page.locator('#worklog-search').fill('NO MATCH');assert((await page.locator('#worklog-summary').innerText()).startsWith('0'));
    await page.locator('#clear-filters').click(); assert((await page.locator('#worklog-summary').innerText()).startsWith('1'));
  });
  await check('Invoice draft preview and editor',async()=>{
    await page.locator('#navigation [data-view="invoices"]').click();
    await page.locator('[data-invoice-layout="inv_qa"]').click();
    assert(await page.locator('#invoice-layout-dialog').isVisible());
    assert((await page.locator('#invoice-layout-dialog').innerText()).includes('150'));
    await page.locator('#invoice-layout-dialog [data-close]').click();
    await page.locator('[data-edit-invoice="inv_qa"]').click(); assert(await page.locator('#editor-dialog').isVisible());
    await page.locator('#editor-dialog [data-close]').first().click();
  });
  await check('Laptop mobile widths: no document overflow and menu navigation',async()=>{
    for(const width of [360,412,480]){
      await page.setViewportSize({width,height:915});
      for(const name of ['dashboard','customers','orders','worklogs','invoices']){
        await page.evaluate(name=>showView(name),name);
        const overflow=await page.evaluate(()=>[...document.querySelectorAll('body *')].filter(e=>e.getBoundingClientRect().right>innerWidth+1 && getComputedStyle(e).display!=='none').slice(0,12).map(e=>({tag:e.tagName,id:e.id,class:e.className,right:e.getBoundingClientRect().right})));
        assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'horizontal overflow '+name+' '+width+' '+JSON.stringify(overflow));
      }
    }
    await page.screenshot({path:path.join(__dirname,'../outputs/QA-Web-Mobile.png'),fullPage:true});
  });
  await check('Offline create survives reload and synchronizes',async()=>{
    try{
    await context.setOffline(true);
    await page.evaluate(()=>saveEntity('worklog',{id:'qa_wrk_qa_offline',customer_id:'qa_cus_qa',order_id:'qa_ord_qa',activity:'Offline QA',hours:2,work_date:'2026-10-08'}));
    assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('haus-garten-queue')).length),1);
    await page.reload();await page.waitForTimeout(700);
    assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('haus-garten-queue')).length),1);
    assert.equal(await page.evaluate(()=>state.dashboard.unbilled_hours),5);
    assert(await page.evaluate(()=>state.dashboard.recent.some(w=>w.id==='qa_wrk_qa_offline')));
    await context.setOffline(false);await page.evaluate(()=>synchronize());
    await page.waitForFunction(()=>JSON.parse(localStorage.getItem('haus-garten-queue')).length===0);
    assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('haus-garten-queue')).length),0);
    const rows=await page.request.get(laptopUrl+'/api/worklogs').then(r=>r.json());
    assert.equal(rows.filter(w=>w.id==='qa_wrk_qa_offline').length,1);
    }finally{await context.setOffline(false);}
  });
  await check('Corrupted offline queue is preserved and clearly reported',async()=>{
    await page.evaluate(()=>localStorage.setItem('haus-garten-queue','{damaged'));
    await page.reload();await page.waitForTimeout(600);
    assert.equal(await page.evaluate(()=>localStorage.getItem('haus-garten-queue')),'{damaged');
    assert((await page.locator('#online-text').innerText()).includes('prüfen'));
    assert.deepEqual(errors,[]);
    await page.evaluate(()=>localStorage.setItem('haus-garten-queue','[]'));await page.evaluate(()=>loadAll());
  });
  await check('Unsaved customer edits are protected on close',async()=>{
    await page.evaluate(()=>openEditor('customer',state.customers.find(c=>c.id==='qa_cus_qa')));
    await page.locator('#editor-form [name="name"]').fill('Nicht gespeichert');
    page.once('dialog',dialog=>dialog.dismiss());await page.locator('#editor-dialog [data-close]').first().click();
    assert(await page.locator('#editor-dialog').isVisible());
    page.once('dialog',dialog=>dialog.accept());await page.locator('#editor-dialog [data-close]').first().click();
    assert(!(await page.locator('#editor-dialog').isVisible()));
  });
  await check('New changes survive synchronization already in progress',async()=>{
    let resume,started;const gate=new Promise(resolve=>resume=resolve),began=new Promise(resolve=>started=resolve);
    await page.route('**/api/sync',async route=>{started();await gate;await route.continue();});
    await page.evaluate(()=>queueAction('worklog',{id:'qa_wrk_race_a',customer_id:'qa_cus_qa',order_id:'qa_ord_qa',activity:'Race A',hours:1,work_date:'2026-10-08'}));
    const sync=page.evaluate(()=>synchronize());await began;
    await page.evaluate(()=>queueAction('worklog',{id:'qa_wrk_race_b',customer_id:'qa_cus_qa',order_id:'qa_ord_qa',activity:'Race B',hours:1,work_date:'2026-10-08'}));
    resume();await sync;await page.unroute('**/api/sync');
    assert.equal(await page.evaluate(()=>readQueue().length),1);
    await page.evaluate(()=>synchronize());assert.equal(await page.evaluate(()=>readQueue().length),0);
    await page.request.delete(laptopUrl+'/api/worklogs/wrk_race_a');await page.request.delete(laptopUrl+'/api/worklogs/wrk_race_b');
  });
  const mobile=await context.newPage();const mobileErrors=[];mobile.on('pageerror',e=>mobileErrors.push(e.message));
  const mobileUrl=process.env.QA_MOBILE_URL;
  if(mobileUrl){
    await mobile.setViewportSize({width:412,height:915});await mobile.goto(mobileUrl);await mobile.waitForTimeout(800);
    await check('Android UI fixture navigation and one-step Back',async()=>{
      for(const name of ['worklogs','weeks','customers','orders','invoices','sync-page']){
        const previous=await mobile.evaluate(()=>currentPage);
        await mobile.evaluate(name=>openPage(name),name); assert(await mobile.locator('#'+name).isVisible());
        await mobile.evaluate(()=>handleAndroidBack());
        assert.equal(await mobile.evaluate(()=>currentPage),previous);
      }
      assert.deepEqual(mobileErrors,[]);
    });
    await check('Android edit existing customer with additional field',async()=>{
      await mobile.evaluate(()=>openCustomer('cus_demo'));
      await mobile.locator('[data-design-action="edit"]').click();
      await mobile.locator('#customer-form [name="name"]').fill('Demo Gartenkunde geändert');
      await mobile.evaluate(()=>addCustomField('Torcode','123'));
      await mobile.locator('#customer-form button[type="submit"]').click();await mobile.waitForTimeout(100);
      assert.deepEqual(mobileErrors,[]);
      assert(await mobile.evaluate(()=>customers().some(c=>c.name==='Demo Gartenkunde geändert')));
      assert.equal(await mobile.evaluate(()=>customers().find(c=>c.id==='cus_demo').custom_fields.Torcode),'123');
    });
    await check('Android worklog detail, autosave and long-press actions',async()=>{
      await mobile.evaluate(()=>openWorklogDetail('wrk_demo'));
      assert.equal(await mobile.locator('#worklog-detail').getAttribute('data-work-mode'),'overview');
      await mobile.locator('[data-work-window-edit]').click();
      await mobile.locator('#edit-worklog-card [data-section="notes"] summary').click();
      await mobile.locator('#edit-worklog-notes').fill('QA Autosave');
      await mobile.waitForTimeout(1000);await mobile.evaluate(()=>handleAndroidBack());
      assert(await mobile.evaluate(()=>worklogs().some(w=>w.notes==='QA Autosave')));
      assert.equal(await mobile.locator('#worklog-detail').getAttribute('data-work-mode'),'overview');
      await mobile.evaluate(()=>handleAndroidBack());
      await mobile.evaluate(()=>openPage('worklogs'));
      const row=mobile.locator('[data-edit-worklog="wrk_demo"]').filter({visible:true}).first();
      await row.dispatchEvent('pointerdown',{pointerId:1,pointerType:'touch',clientX:80,clientY:350});
      await mobile.waitForTimeout(650);await row.dispatchEvent('pointerup',{pointerId:1,pointerType:'touch'});
      assert(await mobile.locator('#record-action-dialog').isVisible());
      await mobile.evaluate(()=>handleAndroidBack());assert(!(await mobile.locator('#record-action-dialog').isVisible()));
    });
    await check('Android small widths and edit reachability',async()=>{
      for(const width of [360,412]){await mobile.setViewportSize({width,height:915});
        for(const name of ['home','customers','orders','worklogs','weeks','invoices','work-entry']){
          await mobile.evaluate(name=>openPage(name),name);
          assert(await mobile.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Android overflow '+name);
        }
      }
      await mobile.screenshot({path:path.join(__dirname,'../outputs/QA-Android-Eingabe.png'),fullPage:true});
    });
    await check('Android time boundaries and ISO calendar weeks',async()=>{
      const result=await mobile.evaluate(()=>({hours:effectiveWorkHours('08:00','14:30',60),week:weekInfo('2021-01-01'),invalid:['12:00|08:00|0','08:00|08:00|0','08:00|12:00|241','08:00|12:00|-1'].map(s=>{const[a,b,c]=s.split('|');try{effectiveWorkHours(a,b,Number(c));return false}catch{return true}})}));
      assert.equal(result.hours,5.5);assert.equal(result.week.key,'2020-53');assert(result.invalid.every(Boolean));
    });
    await check('Android input fields reachable by touch after scrolling',async()=>{
      await mobile.setViewportSize({width:360,height:800});await mobile.evaluate(()=>openPage('work-entry'));
      await mobile.locator('#entry [data-section="time"] summary').click();
      for(const selector of ['#date','#start','#end','#pause','#activity']){
        await mobile.locator(selector).click();assert(await mobile.locator(selector).evaluate(e=>document.activeElement===e));
      }
      await mobile.locator('#entry [data-section="material"] summary').click();
      await mobile.locator('#entry [data-section="notes"] summary').click();
      for(const selector of ['[name="kilometers"]','[name="material"]','[name="notes"]']){
        await mobile.locator('#entry '+selector).click();assert(await mobile.locator('#entry '+selector).evaluate(e=>document.activeElement===e));
      }
    });
    await check('Android order overview, editor save and Back hierarchy',async()=>{
      await mobile.evaluate(()=>openPage('orders'));
      await mobile.locator('[data-edit-order="ord_demo"]').first().click();
      await mobile.locator('[data-order-window-edit]').click();
      await mobile.locator('#order-edit-description').fill('QA Auftragsnachtrag');
      await mobile.locator('[data-order-window-save]').click();
      assert(await mobile.evaluate(()=>orders().some(o=>o.id==='ord_demo'&&o.description==='QA Auftragsnachtrag')));
      await mobile.locator('[data-edit-order="ord_demo"]').first().click();
      await mobile.locator('[data-order-window-edit]').click();
      await mobile.evaluate(()=>handleAndroidBack());
      assert(await mobile.locator('.order-window-summary').isVisible());
      await mobile.evaluate(()=>handleAndroidBack());
      assert(!(await mobile.locator('#direct-edit-dialog').isVisible()));
      assert.equal(await mobile.evaluate(()=>currentPage),'orders');
    });
  }
  fs.writeFileSync(path.join(__dirname,'../outputs/QA-Browser-results.json'),JSON.stringify({results,desktopErrors:errors,androidMockErrors:mobileErrors},null,2));
  console.log(JSON.stringify(results,null,2));await browser.close();
  if(results.some(r=>r.result==='FAIL'))process.exitCode=1;
}
main().catch(e=>{console.error(e);process.exit(1)});

/* Real installed APK, real Java bridge and SQLite. No browser/native mocks. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const root = path.resolve(__dirname, '..');
const adbPath = path.join(root, 'android-toolchain/sdk/platform-tools/adb.exe');
const serial = 'emulator-5580', avd = 'HausGarten_QA_Pixel10Pro_API35';
const app = 'at.hausgarten.service';
const run = new Date().toISOString().replace(/[:.]/g, '-');
const out = path.join(root, 'outputs/android-emulator', run);
fs.mkdirSync(out, {recursive:true});
const results = [], errors = [];
const report = {
  run, status:'BLOCKED', device:serial, expectedAvd:avd, apk:'1.27.1 / 41',
  environment:'Real Android emulator, installed APK; synthetic data only',
  results, untested:[
    'Real Google Drive account/provider, cloud roundtrip, concurrent Laptop/Pixel edits',
    'Google Photos provider, real microphone/speech recognition, physical Pixel hardware',
    'Actual calls, Maps navigation, email/WhatsApp delivery, banking, invoice issuance/PDF',
    'All remaining UI/edge cases: this is a baseline, not full production acceptance'
  ]
};
let browser, page, forwardPort, originalAirplane;
let createdCustomer, createdOrder, createdWorklog;
const tag = 'Emulator QA '+run;
function adb(args, binary=false) {
  return execFileSync(adbPath, ['-s',serial,...args], {
    encoding:binary?null:'utf8', timeout:45000, windowsHide:true,
    maxBuffer:24*1024*1024
  });
}
function saveReport() {
  fs.writeFileSync(path.join(out,'report.json'), JSON.stringify(report,null,2));
  fs.writeFileSync(path.join(root,'outputs/QA-Android-Emulator-results.json'), JSON.stringify(report,null,2));
}
function capture(name) {
  const safe = name.replace(/[^a-zA-Z0-9-]/g,'_');
  for(const [suffix,args,binary] of [
    ['png',['exec-out','screencap','-p'],true],
    ['log',['logcat','-d','-t','350','AndroidRuntime:E','chromium:W','*:S'],false]
  ]) try { fs.writeFileSync(path.join(out,safe+'.'+suffix),adb(args,binary)); } catch (_) { /* Preserve original failure. */ }
}
async function check(name, method, fn, dependency=true) {
  if(!dependency) { results.push({name,method,result:'BLOCKED',reason:'Required previous step failed'}); saveReport(); return false; }
  const start=Date.now();
  try { await fn(); results.push({name,method,result:'PASS',elapsedMs:Date.now()-start}); }
  catch(error) { results.push({name,method,result:'FAIL',error:error.stack,elapsedMs:Date.now()-start}); capture(name); }
  saveReport();
  const success=results.at(-1).result==='PASS'; console.log((success?'PASS ':'FAIL ')+name);
  return success;
}
async function connect() {
  const pid=adb(['shell','pidof',app]).trim().split(/\s+/)[0];
  assert(/^\d+$/.test(pid), 'App is not running');
  const sockets=adb(['shell','cat','/proc/net/unix']);
  const socket=sockets.split('\n').map(line=>line.trim().split(/\s+/).at(-1))
    .find(value=>value==='@webview_devtools_remote_'+pid);
  assert(socket, 'No inspectable WebView. Check installed WebView version/debuggable APK; do not weaken release security.');
  if(forwardPort) { adb(['forward','--remove','tcp:'+forwardPort]); forwardPort=null; }
  forwardPort=adb(['forward','tcp:0','localabstract:'+socket.slice(1)]).trim();
  assert(/^\d+$/.test(forwardPort));
  let playwright;
  try { playwright=require('playwright'); }
  catch (_) { playwright=require(process.env.HG_PLAYWRIGHT_MODULE || 'playwright'); }
  browser=await playwright.chromium.connectOverCDP('http://127.0.0.1:'+forwardPort,{timeout:45000});
  page=browser.contexts().flatMap(context=>context.pages()).find(p=>p.url().includes('/android_asset/mobile.html'));
  assert(page,'Installed APK WebView not found; no browser replacement is allowed');
  page.setDefaultTimeout(15000);
  page.on('pageerror',error=>errors.push(error.message));
  await page.waitForFunction(()=>typeof AndroidData!=='undefined' && document.querySelector('#add-order'));
}
async function rows(type) { return page.evaluate(type=>({customers:customers,orders:orders,worklogs:worklogs,photos:photos}[type])(),type); }
async function bridge(method,payload) {
  return page.evaluate(({method,payload})=>AndroidData[method](JSON.stringify(payload)),{method,payload});
}
async function nav(name) { await page.locator('.bottom [data-open="'+name+'"]').click(); await page.locator('#'+name+'.active').waitFor(); }
async function back() { adb(['shell','input','keyevent','4']); }
async function hideKeyboard() {
  if(/mInputShown=true|mIsInputViewShown=true/.test(adb(['shell','dumpsys','input_method']))) {
    await back(); await page.waitForTimeout(250);
  }
}
async function longPress(locator) {
  await locator.scrollIntoViewIfNeeded();
  const rect=await locator.boundingBox(), width=await page.evaluate(()=>innerWidth);
  assert(rect && width>0);
  adb(['shell','uiautomator','dump','/data/local/tmp/hausgarten-qa-window.xml']);
  const xml=adb(['shell','cat','/data/local/tmp/hausgarten-qa-window.xml']);
  fs.writeFileSync(path.join(out,'longpress-window.xml'),xml);
  const node=(xml.match(/<node\b[^>]*class="android\.webkit\.WebView"[^>]*>/g)||[])
    .find(node=>/bounds="\[\d+,\d+\]\[\d+,\d+\]"/.test(node));
  assert(node,'Native WebView bounds unavailable');
  const coords=node.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/).slice(1).map(Number);
  const scale=(coords[2]-coords[0])/width;
  const x=Math.round(coords[0]+(rect.x+rect.width/2)*scale);
  const y=Math.round(coords[1]+(rect.y+rect.height/2)*scale);
  assert(x>coords[0]&&x<coords[2]&&y>coords[1]&&y<coords[3], 'Touch outside observed WebView bounds');
  adb(['shell','input','swipe',String(x),String(y),String(x),String(y),'750']);
}
(async()=>{
  try {
    assert.equal(adb(['get-state']).trim(),'device');
    assert(adb(['emu','avd','name']).split(/\r?\n/).includes(avd),'Refusing another emulator or physical phone');
    assert.equal(adb(['shell','getprop','sys.boot_completed']).trim(),'1');
    await connect();
    const safety=await page.evaluate(()=>({
      raw:JSON.parse(AndroidData.getState()), sync:JSON.parse(AndroidData.getSyncStatus()),
      version:JSON.parse(AndroidData.getAppInfo())
    }));
    assert.equal(safety.sync.configured,false,'Test stopped: a Drive folder is configured');
    assert.deepEqual(safety.raw.master.customers.map(c=>c.id),['cus_emulator_qa'],'Only synthetic master data permitted');
    assert.deepEqual(safety.raw.master.orders.map(o=>o.id),['ord_emulator_qa']);
    for(const key of ['worklogs','photos','invoices']) assert.equal((safety.raw.master[key]||[]).length,0);
    assert(safety.raw.pending.every(action=>['customer','order','worklog','photo','delete'].includes(action.kind)), 'Unexpected pending action: inspect test data before continuing');
    assert.equal(safety.version.versionName,'1.27.1'); assert.equal(safety.version.versionCode,41);
    report.actualVersion=safety.version; report.safetyVerified=true; report.status='RUNNING'; saveReport();

    await check('Installed APK startup and primary navigation','Android WebView UI',async()=>{
      for(const name of ['customers','orders','worklogs','home']) await nav(name);
      assert.deepEqual(errors,[]);
      capture('startup');
    });
    const customerOK=await check('Customer create/read/update and stable ID','Android UI + native SQLite readback',async()=>{
      await nav('customers'); await page.locator('#add-customer').click();
      await page.locator('#customer-form [name="name"]').fill(tag);
      await page.locator('#customer-form [name="street"]').fill('Testweg 99');
      await page.locator('#customer-form [name="city"]').fill('Feldkirchen');
      await hideKeyboard(); await page.locator('#customer-form button[type="submit"]').click();
      await page.locator('#customers.active').waitFor();
      createdCustomer=(await rows('customers')).find(c=>c.name===tag); assert(createdCustomer?.id);
      await page.locator('[data-customer-open="'+createdCustomer.id+'"]').click();
      await page.locator('#customer-form [name="notes"]').fill('Änderung mit Umlauten: Größe & Pflege');
      await hideKeyboard(); await page.locator('#customer-form button[type="submit"]').click();
      await page.locator('#customers.active').waitFor();
      const saved=(await rows('customers')).find(c=>c.id===createdCustomer.id);
      assert.equal(saved.notes,'Änderung mit Umlauten: Größe & Pflege');
    });
    const orderOK=await check('Order create and customer relation','Android UI + native SQLite readback',async()=>{
      await nav('orders'); await page.locator('#add-order').click();
      await page.locator('#order-edit-customer').selectOption(createdCustomer.id);
      await page.locator('#order-edit-title').fill(tag+' Gartenpflege');
      await page.locator('#order-edit-location').fill('Testweg 99');
      await hideKeyboard(); await page.locator('[data-order-window-save]').click();
      await page.waitForFunction(()=>!document.querySelector('#direct-edit-dialog').open);
      createdOrder=(await rows('orders')).find(o=>o.title===tag+' Gartenpflege');
      assert(createdOrder?.id); assert.equal(createdOrder.customer_id,createdCustomer.id);
    },customerOK);
    originalAirplane=adb(['shell','settings','get','global','airplane_mode_on']).trim();
    assert(['0','1'].includes(originalAirplane),'Cannot safely restore airplane mode');
    adb(['shell','cmd','connectivity','airplane-mode','enable']);
    const workOK=await check('Offline worklog create, 5.5 h calculation, relation and defaults','Android UI + native SQLite readback',async()=>{
      assert.equal(adb(['shell','settings','get','global','airplane_mode_on']).trim(),'1');
      await nav('home'); await page.locator('#home [data-open="work-entry"]').first().click();
      await page.locator('#customer').selectOption(createdCustomer.id);
      await page.locator('#order').selectOption(createdOrder.id);
      await page.locator('#date').fill('2026-10-09');
      await page.locator('#start').fill('08:00'); await page.locator('#end').fill('14:30');
      await page.locator('#pause').fill('60'); await page.locator('#activity').fill(tag+' Hecke pflegen');
      assert.equal(await page.locator('#entry-hours').inputValue(),'5.5');
      await hideKeyboard(); await page.locator('#entry button[type="submit"]').click();
      await page.locator('#home.active').waitFor();
      createdWorklog=(await rows('worklogs')).find(w=>w.activity===tag+' Hecke pflegen');
      assert(createdWorklog?.id); assert.equal(createdWorklog.customer_id,createdCustomer.id);
      assert.equal(createdWorklog.order_id,createdOrder.id); assert.equal(createdWorklog.hours,5.5);
      assert.equal(createdWorklog.break_minutes,60); assert.equal(createdWorklog.kilometers,0);
      assert.equal(createdWorklog.billed,0); assert.equal(createdWorklog.customer_visible,1);
      assert.equal(createdWorklog.location,'Testweg 99');
    },orderOK);
    await check('Edit autosave and Android Back goes one step','Android UI + actual BACK key + native SQLite readback',async()=>{
      await nav('worklogs'); await page.locator('#worklog-list [data-edit-worklog="'+createdWorklog.id+'"]').click();
      await page.locator('[data-work-window-edit]').click();
      await page.locator('#edit-worklog-activity').fill(tag+' Tätigkeit ergänzt');
      await hideKeyboard(); await back();
      await page.waitForFunction(()=>document.querySelector('#worklog-detail').dataset.workMode==='overview');
      assert.equal((await rows('worklogs')).find(w=>w.id===createdWorklog.id).activity,tag+' Tätigkeit ergänzt');
      await back(); await page.locator('#worklogs.active').waitFor();
    },workOK);
    await check('Search, no-match state and filter reset','Android WebView UI',async()=>{
      await nav('worklogs');
      const details=page.locator('#worklog-search').locator('xpath=ancestor::details[1]');
      if(await details.count() && !await details.getAttribute('open')) await details.locator('summary').click();
      await page.locator('#worklog-search').fill('NO_MATCH_'+run);
      assert.equal(await page.locator('#worklog-list [data-edit-worklog]').count(),0);
      await hideKeyboard(); await page.locator('#worklog-filter-reset').click();
      await page.locator('#worklog-list [data-edit-worklog="'+createdWorklog.id+'"]').waitFor();
    },workOK);
    await check('Native long press offers edit/delete and BACK closes popup','Real Android touch + WebView UI',async()=>{
      await nav('worklogs'); await hideKeyboard();
      await longPress(page.locator('#worklog-list [data-edit-worklog="'+createdWorklog.id+'"]'));
      await page.locator('#record-action-dialog[open]').waitFor();
      assert(await page.locator('#record-action-dialog [data-record-edit]').isEnabled());
      assert(await page.locator('#record-action-dialog [data-record-delete]').isEnabled());
      capture('longpress'); await back();
      await page.waitForFunction(()=>!document.querySelector('#record-action-dialog').open);
      await page.locator('#worklogs.active').waitFor();
    },workOK);
    await check('Delete and undo preserve record and its ID','Real native bridge + actual Undo button; delete confirmation UI not covered',async()=>{
      const result=await page.evaluate(id=>removeRecordWithUndo('worklog',id),createdWorklog.id);
      assert.equal(result,true); assert(!(await rows('worklogs')).some(w=>w.id===createdWorklog.id));
      await page.locator('#mobile-toast .undo-toast-button').click();
      assert((await rows('worklogs')).some(w=>w.id===createdWorklog.id));
    },workOK);
    await check('Invalid references, dates, times and negative inputs rejected','Real Java bridge validation; not form UI',async()=>{
      const before=await page.evaluate(()=>JSON.parse(AndroidData.getState()).pending.length);
      const base={customer_id:'cus_emulator_qa',order_id:'ord_emulator_qa',_require_order:true,work_date:'2026-10-09',activity:'Validation probe',hours:1};
      for(const invalid of [
        {customer_id:'missing'}, {order_id:'missing'}, {order_id:''}, {activity:''},
        {work_date:'2026-02-30'}, {hours:-1}, {kilometers:-1}, {break_minutes:-1},
        {start_time:'15:00',end_time:'08:00'}, {start_time:'08:00',end_time:'09:00',break_minutes:60}
      ]) assert(String(await bridge('saveWorklog',{...base,...invalid})).startsWith('ERROR:'),JSON.stringify(invalid));
      assert.equal(await page.evaluate(()=>JSON.parse(AndroidData.getState()).pending.length),before);
    });
    await check('Queued IDs unique and only one latest worklog action','Native SQLite getState readback',async()=>{
      const pending=await page.evaluate(()=>JSON.parse(AndroidData.getState()).pending);
      assert.equal(new Set(pending.map(a=>a.id)).size,pending.length);
      const actions=pending.filter(a=>a.kind==='worklog'&&a.payload.id===createdWorklog.id);
      assert.equal(actions.length,1); assert.equal(actions[0].payload.order_id,createdOrder.id);
    },workOK);
    adb(['shell','cmd','connectivity','airplane-mode',originalAirplane==='1'?'enable':'disable']);
    originalAirplane=undefined;
    await check('Force-stop/relaunch retains offline rows and IDs','Android process restart + native SQLite readback',async()=>{
      const before=(await rows('worklogs')).find(w=>w.id===createdWorklog.id);
      await browser.close(); browser=null;
      adb(['shell','am','force-stop',app]); adb(['shell','am','start','-n',app+'/.MainActivity']);
      // New process creates its own debugging socket; poll only this emulator.
      for(let attempt=0;attempt<30;attempt++) {
        try { const pid=adb(['shell','pidof',app]).trim(); if(pid&&adb(['shell','cat','/proc/net/unix']).includes('@webview_devtools_remote_'+pid)) break; } catch (_) {}
        await new Promise(resolve=>setTimeout(resolve,1000));
      }
      await connect();
      const after=(await rows('worklogs')).find(w=>w.id===createdWorklog.id); assert.deepEqual(after,before);
    },workOK);
    await check('Current portrait pages have no horizontal overflow or JS errors','Actual emulator viewport; no desktop viewport simulation',async()=>{
      report.viewport=await page.evaluate(()=>({width:innerWidth,height:innerHeight,dpr:devicePixelRatio}));
      for(const name of ['home','customers','orders','worklogs']) {
        await nav(name);
        assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),name+' horizontal overflow');
      }
      assert.deepEqual(errors,[]); capture('completed');
    });
    report.status=results.every(test=>test.result==='PASS')?'BASELINE_PASSED':'NEEDS_ATTENTION';
    report.note='Only listed executed tests count. External Drive/provider/device acceptance remains open.';
  } catch(error) { report.blocker=error.message; report.status=report.safetyVerified?'NEEDS_ATTENTION':'BLOCKED'; console.error(error.message); }
  finally {
    if(originalAirplane!==undefined) try { adb(['shell','cmd','connectivity','airplane-mode',originalAirplane==='1'?'enable':'disable']); }
      catch(error) { report.restoreError=error.message; report.status='NEEDS_ATTENTION'; }
    if(browser) try { await browser.close(); } catch (_) {}
    if(forwardPort) try { adb(['forward','--remove','tcp:'+forwardPort]); } catch (_) {}
    report.jsErrors=errors; saveReport();
    console.log('Report: '+path.join(out,'report.json'));
    process.exitCode=report.status==='BASELINE_PASSED'?0:report.status==='BLOCKED'?2:1;
  }
})();

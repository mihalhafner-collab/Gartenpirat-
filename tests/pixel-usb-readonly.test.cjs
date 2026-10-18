/* Real Pixel/WebView/native bridge. Read-only business-data checks, no CRUD. */
'use strict';
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const {chromium} = require('C:/Users/Michael/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root = path.resolve(__dirname, '..');
const adbPath = path.join(root, 'android-toolchain/sdk/platform-tools/adb.exe');
const serial = '58151FDCH005U8';
const app = 'at.hausgarten.service';
const expectedVersion = process.env.PIXEL_TEST_VERSION || '1.28.1';
const expectedCode = Number(process.env.PIXEL_TEST_VERSION_CODE || 43);
const runId = new Date().toISOString().replace(/[:.]/g, '-');
const out = path.join(root, 'outputs/pixel-usb', runId);
const report = {runId, status:'RUNNING', realDevice:true, realNativeBridge:true, businessWrites:false, results:[], limitations:[
  'No productive CRUD, invoice sending, deletion, or fake work records.',
  'No OpenAI live tunnel until runtime credential and ChatGPT connection are configured.',
  'USB charging is not a Doze/endurance test. No phone reboot or force-idle.',
  'Target API is checked from the installed package; actual execution alone does not prove all Android-17 behaviours.',
  'Native Back key is tested separately from an edge gesture; no claim about gestures unless executed.'
]};
fs.mkdirSync(out, {recursive:true});
let browser, page, port;
const pageErrors = [];
function adb(args, binary=false) {
  return execFileSync(adbPath, ['-s',serial,...args], {encoding:binary?null:'utf8', windowsHide:true, timeout:30000, maxBuffer:16*1024*1024});
}
function save() { fs.writeFileSync(path.join(out,'report.json'),JSON.stringify(report,null,2)); }
async function check(name, method, fn) {
  const at = Date.now();
  try { const info=await fn(); report.results.push({name,method,result:info?.skip?'SKIP':'PASS',...(info?.skip?{reason:info.skip}:{}),elapsedMs:Date.now()-at}); }
  catch (error) { report.results.push({name,method,result:'FAIL',error:error.message,elapsedMs:Date.now()-at}); }
  console.log(report.results.at(-1).result+' '+name); save();
}
async function state() { return page.evaluate(()=>JSON.parse(AndroidData.getState())); }
function ledgerHash(data) {
  const ledger = {master:{}, pending:data.pending};
  for (const key of ['customers','orders','worklogs','photos','invoices']) ledger.master[key]=data.master[key];
  return crypto.createHash('sha256').update(JSON.stringify(ledger)).digest('hex');
}
async function nav(name) {
  let button=page.locator('.bottom [data-open="'+name+'"]');
  if(!await button.count()) {
    await page.locator('.bottom [data-open="more-page"]').click();
    button=page.locator('#more-page [data-open="'+name+'"]').first();
  }
  await button.click(); await page.locator('#'+name+'.active').waitFor();
}
async function back() { adb(['shell','input','keyevent','4']); await page.waitForTimeout(350); }
function nativeNodes() {
  adb(['shell','uiautomator','dump','/data/local/tmp/hausgarten-usb-window.xml']);
  const xml=adb(['shell','cat','/data/local/tmp/hausgarten-usb-window.xml']);
  // Never retain or output password-field text, including masked characters.
  return [...xml.matchAll(/<node\b([^>]+)>/g)].map(match=>{
    const attrs=Object.fromEntries([...match[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(m=>[m[1],m[2]]));
    if(attrs.password==='true') attrs.text='';
    return attrs;
  });
}
function tapNode(node) {
  const bounds=node?.bounds?.match(/^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$/);
  assert(bounds,'Observed native bounds required');
  const [x1,y1,x2,y2]=bounds.slice(1).map(Number); assert(x2>x1&&y2>y1,'Native element not visible');
  adb(['shell','input','tap',String(Math.round((x1+x2)/2)),String(Math.round((y1+y2)/2))]);
}
async function nativeButton(text) {
  for(let attempt=0;attempt<5;attempt++) {
    const button=nativeNodes().find(node=>node.text.toLocaleLowerCase('de-DE')===text.toLocaleLowerCase('de-DE')&&node.clickable==='true');
    if(button&&button.bounds!=='[0,0][0,0]') return button;
    adb(['shell','input','swipe','640','2100','640','850','350']);
    await page.waitForTimeout(250);
  }
  throw Error('Native button not reachable: '+text);
}
async function main() {
  try {
    assert.equal(adb(['get-state']).trim(),'device');
    adb(['shell','input','keyevent','224']); // Wake only, never bypass a device lock.
    assert(adb(['shell','dumpsys','window','policy']).includes('showing=false'),'Please unlock the Pixel to test its UI.');
    report.deviceModel=adb(['shell','getprop','ro.product.model']).trim(); assert.equal(report.deviceModel,'Pixel 10 Pro');
    report.android=adb(['shell','getprop','ro.build.version.release']).trim();
    report.api=Number(adb(['shell','getprop','ro.build.version.sdk']).trim()); assert.equal(report.api,37);
    const pid=adb(['shell','pidof',app]).trim(); assert(/^\d+$/.test(pid));
    const socket='webview_devtools_remote_'+pid;
    assert(adb(['shell','cat','/proc/net/unix']).includes('@'+socket));
    port=adb(['forward','tcp:0','localabstract:'+socket]).trim();
    browser=await chromium.connectOverCDP('http://127.0.0.1:'+port);
    page=browser.contexts().flatMap(c=>c.pages()).find(p=>p.url().includes('/android_asset/mobile.html'));
    assert(page,'Installed APK WebView missing'); page.setDefaultTimeout(10000);
    page.on('pageerror',error=>pageErrors.push(error.message));
    const initial=await state(); report.beforeHash=ledgerHash(initial);
    report.masterCounts=Object.fromEntries(['customers','orders','worklogs','photos','invoices'].map(k=>[k,(initial.master[k]||[]).length]));
    report.pendingCount=initial.pending.length;
    await check('Installed version and native bridge','Actual APK / Java bridge',async()=>{
      report.appVersion=await page.evaluate(()=>JSON.parse(AndroidData.getAppInfo()));
      assert.equal(report.appVersion.versionName,expectedVersion); assert.equal(report.appVersion.versionCode,expectedCode);
      assert((await page.locator('.brand p').innerText()).includes(expectedVersion));
      const pkg=adb(['shell','dumpsys','package',app]);
      const target=pkg.match(/targetSdk=(\d+)/);report.targetSdk=Number(target?.[1]);assert.equal(report.targetSdk,37);
    });
    for(const name of ['home','customers','orders','worklogs','weeks','invoices','sync-page']) {
      await check('Open '+name+' without horizontal overflow','Real Android WebView UI via CDP',async()=>{
        await nav(name);
        const size=await page.evaluate(()=>({width:innerWidth,scroll:document.documentElement.scrollWidth,height:innerHeight,dpr:devicePixelRatio}));
        report.viewport=size; assert(size.scroll<=size.width+1);
      });
    }
    await check('Customer search and reset','Real WebView UI, no form save',async()=>{
      await nav('customers'); const initialCount=await page.locator('#customers [data-customer-open]').count(); assert(initialCount>0);
      await page.locator('#customer-search').fill('__USB_READONLY_NO_MATCH__');
      assert.equal(await page.locator('#customers [data-customer-open]').count(),0);
      await page.locator('#customer-search').fill('');
      assert.equal(await page.locator('#customers [data-customer-open]').count(),initialCount);
    });
    await check('Existing customer opens, native Back returns one step','Real WebView + real Android Back key',async()=>{
      await page.locator('#customers [data-customer-open]').first().click();
      await page.locator('#customer-profile-page.active').waitFor();
      assert(await page.locator('[data-design-action="edit"]').isVisible());
      await back(); await page.locator('#customers.active').waitFor();
    });
    await check('Pixel edge Back gesture returns one step','ADB touchscreen edge swipe on real Android 17',async()=>{
      report.navigationMode=adb(['shell','settings','get','secure','navigation_mode']).trim();
      if(report.navigationMode!=='2')return{skip:'Phone is not in gesture-navigation mode; no gesture claim.'};
      await page.locator('#customers [data-customer-open]').first().click();await page.locator('#customer-profile-page.active').waitFor();
      const size=adb(['shell','wm','size']).match(/Physical size:\s*(\d+)x(\d+)/);assert(size,'Physical display size required');
      const y=String(Math.round(Number(size[2])*0.5)),end=String(Math.round(Number(size[1])*0.30));
      adb(['shell','input','swipe','8',y,end,y,'400']);await page.locator('#customers.active').waitFor();
    });
    await check('Existing order overview and editor return one step','Real order UI / native Back, no field changes',async()=>{
      await nav('orders');await page.locator('#order-list [data-edit-order]').first().click();
      assert(await page.locator('.order-window-summary').isVisible());
      await page.locator('[data-order-window-edit]').click();assert(await page.locator('[data-order-window-save]').isVisible());
      await back();assert(await page.locator('.order-window-summary').isVisible());
      await back();assert(!(await page.locator('#direct-edit-dialog').isVisible()));
    });
    await check('Existing worklog overview and fields','Actual installed APK, read only',async()=>{
      await nav('home'); await page.locator('#workday-recent [data-edit-worklog]').first().click();
      await page.locator('#worklog-detail.active').waitFor();
      assert.equal(await page.locator('#worklog-detail').getAttribute('data-work-mode'),'overview');
      assert.equal(await page.locator('.worklog-window-summary dl > div').count(),12);
      assert(await page.locator('[data-work-window-edit]').isVisible());
    });
    await check('Worklog editor, save control, Back to overview','Real editor opened without changing any fields',async()=>{
      await page.locator('[data-work-window-edit]').click();
      assert.equal(await page.locator('#worklog-detail').getAttribute('data-work-mode'),'edit');
      assert(await page.locator('[data-work-window-save]').isVisible());
      await back();
      assert.equal(await page.locator('#worklog-detail').getAttribute('data-work-mode'),'overview');
      await back(); await page.locator('#home.active').waitFor();
    });
    await check('Drive folder preserved and missing credential honestly shown','Real Java status bridge',async()=>{
      await nav('sync-page'); report.drive=await page.evaluate(()=>JSON.parse(AndroidData.getSyncStatus()));
      report.tunnel=await page.evaluate(()=>JSON.parse(AndroidData.getPixelTunnelStatus()));
      assert.equal(report.drive.configured,true); assert.equal(report.tunnel.battery_exempt,true);
      assert.equal(typeof report.tunnel.configured,'boolean'); assert.equal(typeof report.tunnel.running,'boolean');
      assert.equal(typeof report.tunnel.write_enabled,'boolean');
      const rect=await page.locator('#pixel-tunnel-setup').boundingBox(); assert(rect.height>=48);
    });
    await check('Native setup reachable from Drive','Real UI bridge / native Activity',async()=>{
      await page.locator('#pixel-tunnel-setup').click(); await page.waitForTimeout(300);
      assert(adb(['shell','dumpsys','window']).includes(app+'/.PixelTunnelActivity'));
      assert(nativeNodes().some(node=>node.text==='ChatGPT auf dem Pixel'));
    });
    await check('Protected key input opens keyboard; first Back only dismisses it','Real native password-field focus, no text entered',async()=>{
      const field=nativeNodes().find(node=>node.password==='true'&&node.class==='android.widget.EditText');
      assert(field,'Native protected key field required');tapNode(field);await page.waitForTimeout(450);
      const shown=adb(['shell','dumpsys','input_method']);
      assert(/mInputShown=true/.test(shown),'Native keyboard should be visible');
      await back();
      assert(adb(['shell','dumpsys','window']).includes(app+'/.PixelTunnelActivity'));
      assert(!/mInputShown=true/.test(adb(['shell','dumpsys','input_method'])));
    });
    await check('Missing runtime key is rejected without starting tunnel','Observed native button / real Android tap',async()=>{
      if(report.tunnel.configured)return {skip:'A credential is already saved. Never remove it or disrupt the live tunnel for a missing-key test.'};
      tapNode(await nativeButton('Verbindung starten')); await page.waitForTimeout(250);
      const nodes=nativeNodes();
      assert(nodes.some(node=>node.text==='Verbindung noch nicht eingerichtet'));
      tapNode(nodes.find(node=>node.text==='OK'&&node.clickable==='true'));
      // The general app filter also includes its bound WebView sandbox services.
      // Verify the specific tunnel component plus its real native running state.
      const services=adb(['shell','dumpsys','activity','services',app+'/.PixelTunnelService']);
      assert(!services.includes('ServiceRecord'));
      const status=await page.evaluate(()=>JSON.parse(AndroidData.getPixelTunnelStatus()));
      assert.equal(status.configured,false);assert.equal(status.running,false);
    });
    await check('Native setup Back returns to Drive, not home','Real Android Back key',async()=>{
      await back(); await page.locator('#sync-page.active').waitFor();
      assert(adb(['shell','dumpsys','window']).includes(app+'/.MainActivity'));
    });
    await check('No business-data mutation during tests','Real native dataset compared by SHA-256',async()=>{
      report.afterHash=ledgerHash(await state()); assert.equal(report.afterHash,report.beforeHash);
    });
    await check('No WebView runtime errors during tested navigation','Actual pageerror events',async()=>assert.deepEqual(pageErrors,[]));
    report.status=report.results.some(r=>r.result==='FAIL')?'FAIL':'READONLY_DEVICE_PASSED';
    report.liveTunnel='NOT_TESTED_RUNTIME_KEY_MISSING';
  }catch(error) {report.status='BLOCKED';report.error=error.message;}
  finally {
    save(); if(browser)await browser.close();
    if(port)try{adb(['forward','--remove','tcp:'+port]);}catch(_){}
    console.log(JSON.stringify({report:path.join(out,'report.json'),status:report.status,passed:report.results.filter(r=>r.result==='PASS').length,failed:report.results.filter(r=>r.result==='FAIL').length}));
  }
  if(report.status!=='READONLY_DEVICE_PASSED')process.exitCode=1;
}
main();

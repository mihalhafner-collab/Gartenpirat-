'use strict';
const privateDiagnostics=require('./private-diagnostics.cjs');
privateDiagnostics.requireManualContext();
if (!(process.argv[2] || process.env.HG_TEST_DEVICE_SERIAL) || process.env.HG_TEST_DEVICE_CONFIRMED !== '1') {
  throw Error('Manual device test requires an explicitly selected device and HG_TEST_DEVICE_CONFIRMED=1 after user consent.');
}

const assert=require('node:assert/strict');
const path=require('node:path');
const {execFileSync}=require('node:child_process');
const {chromium}=require('./browser.cjs');
const adbPath=path.resolve(__dirname,'../android-toolchain/sdk/platform-tools/adb.exe');
const serial=process.argv[2] || process.env.HG_TEST_DEVICE_SERIAL,pkg='at.hausgarten.service.qa';
const adb=args=>execFileSync(adbPath,['-s',serial,...args],{encoding:'utf8',windowsHide:true,timeout:30000});
(async()=>{let browser,port;try{
  const packages=adb(['shell','pm','list','packages',pkg]);assert(packages.includes('package:'+pkg),'Getrennte QA-App fehlt.');
  assert.equal(adb(['shell','pm','clear',pkg]).trim(),'Success','QA-Daten konnten nicht zurückgesetzt werden.');
  adb(['shell','am','start','-n',pkg+'/at.hausgarten.service.MainActivity']);await new Promise(r=>setTimeout(r,1800));
  const pid=adb(['shell','pidof',pkg]).trim();assert(pid,'QA-App läuft nicht.');
  port=adb(['forward','tcp:0','localabstract:webview_devtools_remote_'+pid]).trim();browser=await chromium.connectOverCDP('http://127.0.0.1:'+port);
  const page=browser.contexts().flatMap(c=>c.pages()).find(p=>p.url().includes('/android_asset/mobile.html'));assert(page,'Mobile Oberfläche fehlt.');
  await page.locator('#workday-timer').waitFor();
  const initial=await page.evaluate(()=>JSON.parse(AndroidData.getWorkSession()));assert(['IDLE','FINALIZED'].includes(initial.state),'QA-Sitzung ist nicht leer.');
  await page.locator('[data-session-action="start"]').click();await page.waitForTimeout(1100);assert.equal((await page.evaluate(()=>JSON.parse(AndroidData.getWorkSession()))).state,'RUNNING');
  await page.locator('[data-session-action="pause"]').click();const paused=await page.evaluate(()=>JSON.parse(AndroidData.getWorkSession()));assert.equal(paused.state,'PAUSED');assert(paused.worked_ms>=900);
  await page.locator('[data-session-action="resume"]').click();await page.waitForTimeout(600);await page.locator('[data-session-action="stop"]').click();const stopped=await page.evaluate(()=>JSON.parse(AndroidData.getWorkSession()));assert.equal(stopped.state,'STOPPED_PENDING_REVIEW');
  await page.locator('[data-session-action="complete"]').click();assert(await page.locator('#work-entry').evaluate(e=>e.classList.contains('active')));assert.equal(await page.locator('#work-session-id').inputValue(),stopped.id);assert.equal(Number(await page.locator('#entry-hours').inputValue()),Number(stopped.hours));
  privateDiagnostics.log(JSON.stringify({passed:8,package:pkg,states:['IDLE','RUNNING','PAUSED','RUNNING','STOPPED_PENDING_REVIEW'],review_page:true,productive_app_untouched:true}));
}finally{if(browser)await browser.close();if(port)try{adb(['forward','--remove','tcp:'+port]);}catch{}}})().catch(error=>{privateDiagnostics.log(error.stack||error);process.exitCode=1;});

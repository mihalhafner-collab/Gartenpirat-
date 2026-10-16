const path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('C:/Users/Michael/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
(async()=>{
  const browser=await chromium.launch({headless:true,channel:'chrome'});
  const page=await browser.newPage({viewport:{width:412,height:915},deviceScaleFactor:2,isMobile:true,hasTouch:true});
  await page.addInitScript(()=>{
    const empty={master:{customers:[],orders:[],worklogs:[],invoices:[],photos:[]},pending:[]};
    window.AndroidData={getState:()=>JSON.stringify(empty),getSyncStatus:()=>JSON.stringify({configured:true,last_sync:new Date().toISOString(),pending:0}),getAppInfo:()=>JSON.stringify({versionName:'1.29.0'}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),synchronize:()=>{},saveWorklog:()=>'',saveCustomer:()=>'',saveOrder:()=>''};
    window.AndroidSpeech={start:()=>{}};
  });
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);
  await page.waitForSelector('#workday-timer .timer-start');
  await page.screenshot({path:'outputs/Neue-Oberflaeche-v1.29.0.png',fullPage:true});
  await browser.close();
})().catch(error=>{console.error(error);process.exitCode=1});

'use strict';
// Use the installed browser tooling; never depend on a developer's private path.
const playwright=require(process.env.HG_PLAYWRIGHT_MODULE||'playwright');
const fs=require('node:fs'),path=require('node:path'),{fileURLToPath}=require('node:url');
if(process.env.HG_BROWSER_EXECUTABLE_PATH){
 const launch=playwright.chromium.launch.bind(playwright.chromium);
 playwright.chromium.launch=options=>launch({...options,channel:undefined,executablePath:process.env.HG_BROWSER_EXECUTABLE_PATH});
 const persistent=playwright.chromium.launchPersistentContext.bind(playwright.chromium);
 playwright.chromium.launchPersistentContext=(directory,options)=>persistent(directory,{...options,channel:undefined,executablePath:process.env.HG_BROWSER_EXECUTABLE_PATH});
}
// Cloud Chromium disallows file: navigation. Serve the SAME public assets through
// a test route, modeling Android's bundled asset access even when network is off.
// This is a browser boundary double, not Android WebView/device acceptance.
if(process.env.HG_TEST_LOCAL_ASSETS==='1'){
 const origin='http://127.0.0.1:18766',root=path.resolve(__dirname,'..');
 const roots=[path.join(root,'static'),path.join(root,'android/app/src/main/assets'),...(process.env.HG_TEST_APP_ASSETS?[path.resolve(process.env.HG_TEST_APP_ASSETS)]:[])];
 const types={'.html':'text/html','.js':'application/javascript','.css':'text/css','.json':'application/json','.jpg':'image/jpeg','.png':'image/png','.svg':'image/svg+xml'};
 function wrap(page){
  if(page.__localAssets)return page;page.__localAssets=true;
  page.route(origin+'/**',async route=>{
   const file=path.resolve(decodeURIComponent(new URL(route.request().url()).pathname));
   if(!roots.some(base=>file.startsWith(base+path.sep))||!fs.existsSync(file)||!fs.statSync(file).isFile())return route.fulfill({status:404,body:'Missing test asset'});
   return route.fulfill({status:200,body:fs.readFileSync(file),contentType:types[path.extname(file)]||'application/octet-stream'});
  });
  const goto=page.goto.bind(page);page.goto=(url,...args)=>goto(url.startsWith('file:')?origin+fileURLToPath(url):url,...args);return page;
 }
 const launch=playwright.chromium.launch.bind(playwright.chromium);
 playwright.chromium.launch=async options=>{
  const browser=await launch(options),newPage=browser.newPage.bind(browser),newContext=browser.newContext.bind(browser);
  browser.newPage=async(...args)=>wrap(await newPage(...args));
  browser.newContext=async(...args)=>{const context=await newContext(...args);context.on('page',wrap);return context;};
  return browser;
 };
 const persistent=playwright.chromium.launchPersistentContext.bind(playwright.chromium);
 playwright.chromium.launchPersistentContext=async(...args)=>{const context=await persistent(...args);context.on('page',wrap);context.pages().forEach(wrap);return context;};
}
module.exports=playwright;

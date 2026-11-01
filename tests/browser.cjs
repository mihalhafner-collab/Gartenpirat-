'use strict';
// Use the installed browser tooling; never depend on a developer's private path.
const playwright=require(process.env.HG_PLAYWRIGHT_MODULE||'playwright');
const synthetic=require('./synthetic-fixtures.cjs');
if(!synthetic.profile('minimal').customers[0].test_record)throw Error('Synthetic fixture contract required');
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
 const roots=[path.join(root,'app/src/main/assets'),...(process.env.HG_TEST_APP_ASSETS?[path.resolve(process.env.HG_TEST_APP_ASSETS)]:[])];
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
const os=require('node:os');
const originalPersistent=playwright.chromium.launchPersistentContext.bind(playwright.chromium);
playwright.chromium.launchPersistentContext=async(directory,options)=>{
 const temporary=path.resolve(directory),canonical=fs.realpathSync(temporary),temp=fs.realpathSync(os.tmpdir());
 if(!canonical.startsWith(temp+path.sep)||!path.basename(temporary).startsWith('qa_'))throw Error('Persistent QA profile must be a private synthetic OS-temp directory.');
 const context=await originalPersistent(temporary,options);
 context.on('page',screenshotProof);context.pages().forEach(screenshotProof);
 return context;
};
// Screenshots are test artifacts only when their exact bytes have fixture provenance.
function screenshotProof(page){
 if(page.__qaScreenshotProof)return page;page.__qaScreenshotProof=true;
 const screenshot=page.screenshot.bind(page);
 page.screenshot=async options=>{const result=await screenshot(options);if(options&&options.path){const file=path.resolve(options.path);fs.writeFileSync(file+'.privacy.json',JSON.stringify({classification:'SYNTHETIC_TEST',fixture:'qa_browser_fixture',sha256:require('node:crypto').createHash('sha256').update(fs.readFileSync(file)).digest('hex')}));}return result;};return page;
}
const launchForProof=playwright.chromium.launch.bind(playwright.chromium);
playwright.chromium.launch=async options=>{const b=await launchForProof(options),np=b.newPage.bind(b),nc=b.newContext.bind(b);b.newPage=async(...args)=>screenshotProof(await np(...args));b.newContext=async(...args)=>{const c=await nc(...args);c.on('page',screenshotProof);c.pages().forEach(screenshotProof);return c;};return b;};
module.exports=playwright;

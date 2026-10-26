'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),{execFileSync}=require('node:child_process'),{pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs'),fixtureSource=require('./ui-fixture-source.cjs');
const assets=path.resolve('android/app/src/main/assets'),results=[];
async function check(name,fn){try{await fn();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.stack});}console.log(results.at(-1));}
(async()=>{
 const html=fs.readFileSync(path.join(assets,'mobile.html'),'utf8');
 await check('CSP precedes all scripts, blocks network connections and external active resources',()=>{
  assert(html.indexOf('Content-Security-Policy')>html.indexOf('charset='));assert(html.indexOf('Content-Security-Policy')<html.indexOf('<script'));
  for(const rule of ["connect-src 'none'","object-src 'none'","frame-src 'none'","base-uri 'none'","form-action 'none'"])assert(html.includes(rule));
  const csp=html.match(/http-equiv="Content-Security-Policy" content="([^"]+)"/)[1];assert(!/https?:|\*/.test(csp));assert(csp.includes('content://at.hausgarten.service.photothumbnails'));assert(!/default-src[^;]*content:/.test(csp));
 });
 await check('Main assets contain no JavaScript network clients',()=>{for(const n of fs.readdirSync(assets).filter(n=>n.endsWith('.js')&&n!=='plant-catalog.js'))assert(!/\bfetch\s*\(|XMLHttpRequest|new\s+WebSocket|EventSource/.test(fs.readFileSync(path.join(assets,n),'utf8')),n);});
 await check('Native WebView disables general and cross-origin files, retains private photo provider access',()=>{const j=fs.readFileSync('android/app/src/main/java/at/hausgarten/service/MainActivity.java','utf8');for(const name of ['setAllowFileAccess','setAllowFileAccessFromFileURLs','setAllowUniversalAccessFromFileURLs'])assert(j.includes(name+'(false)'));assert(j.includes('setAllowContentAccess(true)'));assert(!/file:\/\/\/(?!android_asset\/)/.test(j));});
 await check('Native image decoding keeps its independent renderer network fence',()=>{const j=fs.readFileSync('android/app/src/main/java/at/hausgarten/service/MainActivity.java','utf8');assert(j.includes('setBlockNetworkLoads(true)'));assert(!j.includes('setBlockNetworkLoads(false)'));assert(j.includes('setBlockNetworkImage(false)'));});
 await check('Preview inherits CSP and restricts image provider to its own isolated package',()=>{const tmp=fs.mkdtempSync(path.join(os.tmpdir(),'qa_csp_preview_'));try{execFileSync('python3',['tools/build_ui_preview_assets.py','--output',tmp]);const text=fs.readFileSync(path.join(tmp,'mobile.html'),'utf8');assert(text.includes("connect-src 'none'"));assert(text.includes('content://at.hausgarten.service.preview.photothumbnails'));assert(!text.includes('content://at.hausgarten.service.photothumbnails'));}finally{fs.rmSync(tmp,{recursive:true});}});
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});
 try{
  const page=await browser.newPage({viewport:{width:426,height:852}}),errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(/Content Security Policy|Refused to/.test(m.text()))errors.push(m.text());});await page.clock.setFixedTime(new Date('2026-10-18T12:00:00Z'));await page.addInitScript({content:fixtureSource('access-integrity.test.cjs')});await page.goto(pathToFileURL(path.join(assets,'mobile.html')).href);await page.waitForFunction(()=>document.body.dataset.revierReady==='true');
  await check('Real bundled UI initializes with CSP and no script errors or policy violations',async()=>{assert.deepEqual(errors,[]);assert.equal(await page.locator('.bottom .nav').count(),4);assert(await page.evaluate(()=>typeof HGBusinessActions==='object'&&typeof PlanningUI==='object'));});
  await check('CSP permits actual bundled plant image decoding',async()=>{const image=await page.evaluate(async()=>{const image=new Image;image.src='plant-images/cymbopogon-citratus-1.jpg';await image.decode();return {complete:image.complete,width:image.naturalWidth,height:image.naturalHeight};});assert(image.complete&&image.width>=120&&image.height>=120);});
  await check('Browser rejects attempted external connection and image without relaxing CSP',async()=>{
   const blocked=await page.evaluate(async()=>{const seen=[];const listen=e=>seen.push(e.effectiveDirective);document.addEventListener('securitypolicyviolation',listen);try{await fetch('https://network.qa.invalid/blocked');}catch(_){}await new Promise(resolve=>{const image=new Image;image.onload=image.onerror=resolve;image.src='https://network.qa.invalid/blocked.png';});await new Promise(resolve=>setTimeout(resolve,20));document.removeEventListener('securitypolicyviolation',listen);return seen;});assert(blocked.includes('connect-src'));assert(blocked.includes('img-src')||blocked.includes('default-src'));
  });
 }finally{await browser.close();}
 fs.writeFileSync('outputs/webview-hardening-tests.json',JSON.stringify({classification:'SYNTHETIC_TEST',actualBrowser:true,nativeDevice:false,results},null,2));if(results.some(x=>x.result==='FAIL'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});

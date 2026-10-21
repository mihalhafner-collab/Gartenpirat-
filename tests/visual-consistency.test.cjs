'use strict';
// Presentation checks using the existing isolated bridge and bundled assets.
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs'),fixtureSource=require('./ui-fixture-source.cjs');
const assets=path.resolve('android/app/src/main/assets'),results=[];
async function check(name,fn){try{await fn();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.stack});}console.log(results.at(-1));}
const tokens=Object.fromEntries([...fs.readFileSync(path.join(assets,'tokens.css'),'utf8').matchAll(/--hg-([\w-]+):\s*(#[0-9a-f]{6})/g)].map(m=>[m[1],m[2]]));
function luminance(hex){const rgb=hex.slice(1).match(/../g).map(v=>parseInt(v,16)/255).map(v=>v<=.04045?v/12.92:((v+.055)/1.055)**2.4);return rgb.reduce((sum,v,i)=>sum+v*[.2126,.7152,.0722][i],0);}
(async()=>{
 await check('Tokens load first, own the only green definition and replace root aliases',()=>{
  const html=fs.readFileSync(path.join(assets,'mobile.html'),'utf8');assert.equal(html.match(/<link rel="stylesheet" href="([^"]+)"/)[1],'tokens.css');assert(html.indexOf('tokens.css')>html.indexOf('Content-Security-Policy'));
  for(const name of fs.readdirSync(assets).filter(n=>/\.css$|^mobile\.html$/.test(n))){const text=fs.readFileSync(path.join(assets,name),'utf8');assert(!/\bInter\b/.test(text),name);if(name!=='tokens.css')assert(!/--hg-green\s*:/.test(text),name);for(const m of text.matchAll(/--green\s*:([^;}]+)/g))assert.equal(m[1],'var(--hg-green)',name);}
  const css=fs.readFileSync(path.join(assets,'tokens.css'),'utf8');assert(!/@import|url\(/.test(css));assert.equal(tokens.green,'#164d38');assert.equal(tokens['green-btn'],'#23744e');
 });
 await check('Primary, secondary, information, warning, danger and body token pairs exceed 4.5:1',()=>{
  for(const [fg,bg]of [['#ffffff',tokens['green-btn']],[tokens.green,tokens.sage],[tokens['info-text'],tokens['info-bg']],[tokens['warn-text'],tokens['warn-bg']],[tokens.danger,tokens['danger-bg']],[tokens.muted,tokens.surface],[tokens.text,tokens.bg]]){const a=luminance(fg),b=luminance(bg);assert((Math.max(a,b)+.05)/(Math.min(a,b)+.05)>=4.5,fg+' / '+bg);}
 });
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']});try{
  const page=await browser.newPage({viewport:{width:360,height:800}}),errors=[];page.on('pageerror',e=>errors.push(e.message));await page.addInitScript({content:fixtureSource('daily-usability.test.cjs')});await page.goto(pathToFileURL(path.join(assets,'mobile.html')).href);await page.waitForFunction(()=>document.body.dataset.fieldReady==='true');
  await check('Five existing views load tokens without horizontal overflow at 320/360/426px and 150% font',async()=>{
   for(const width of [320,360,426])for(const size of [16,24]){await page.setViewportSize({width,height:800});await page.evaluate(size=>document.documentElement.style.fontSize=size+'px',size);for(const id of ['home','more-page','status-page','customers','work-entry']){await page.evaluate(id=>openPage(id),id);assert.equal(await page.evaluate(()=>getComputedStyle(document.documentElement).getPropertyValue('--hg-green').trim()),tokens.green);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),id+' '+width+'/'+size);}}
   assert.deepEqual(errors,[]);
  });
  await check('Today actions scroll below the timer, keep 48px targets clear of tabs, and metrics use a 1px separator',async()=>{
   for(const width of [320,360,426])for(const size of [16,24]){await page.setViewportSize({width,height:800});await page.evaluate(size=>{document.documentElement.style.fontSize=size+'px';openPage('home');scrollTo(0,0);},size);
    assert.equal(await page.locator('#field-today-actions').evaluate(d=>getComputedStyle(d).position),'static');const timer=await page.locator('#workday-timer').boundingBox(),dock=await page.locator('#field-today-actions').boundingBox();assert(dock.y>=timer.y+timer.height);await page.locator('#field-today-actions').scrollIntoViewIfNeeded();const nav=await page.locator('.bottom').boundingBox();for(const b of await page.locator('#field-today-actions button').all()){const r=await b.boundingBox();assert(r.width>=48&&r.height>=48&&r.y>=0&&r.y+r.height<=nav.y+1);assert(await b.evaluate(b=>document.elementFromPoint(b.getBoundingClientRect().x+b.offsetWidth/2,b.getBoundingClientRect().y+b.offsetHeight/2)?.closest('button')===b));}
    assert.equal(await page.locator('#workday-timer [data-session-action=start] small').innerText(),'Zeit läuft ab jetzt');assert.equal(await page.locator('#design-metrics').evaluate(n=>getComputedStyle(n).gap),'0px');assert.equal(await page.locator('#design-metrics .design-metric').nth(1).evaluate(n=>getComputedStyle(n).borderLeftWidth),'1px');
   }
   await page.setViewportSize({width:360,height:800});await page.evaluate(()=>{document.documentElement.style.fontSize='16px';openPage('home');scrollTo(0,0);});await page.screenshot({path:'outputs/grafik-heute-G3-360.png'});
  });
  await check('Existing fixture weather causes appear in the same hint',async()=>{
   const weatherPage=await browser.newPage({viewport:{width:360,height:800}});try{await weatherPage.addInitScript({content:fixtureSource('planning-ui.test.cjs')});await weatherPage.goto(pathToFileURL(path.join(assets,'mobile.html')).href);await weatherPage.waitForFunction(()=>document.body.dataset.fieldReady==='true');await weatherPage.evaluate(()=>PlanningUI.open());assert.match(await weatherPage.locator('#planning-body .planning-weather-reason').first().innerText(),/Regen im Arbeitszeitraum/);}finally{await weatherPage.close();}
  });
 }finally{await browser.close();}
 fs.writeFileSync('outputs/visual-consistency-tests.json',JSON.stringify({classification:'SYNTHETIC_TEST',nativeDevice:false,results},null,2));if(results.some(x=>x.result==='FAIL'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});

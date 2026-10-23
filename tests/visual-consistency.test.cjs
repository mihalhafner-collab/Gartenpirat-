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
  await check('96 Today actions belong to Now and remain reachable above tabs; weekly hours stay last',async()=>{
   for(const width of [320,360,426])for(const size of [16,24]){await page.setViewportSize({width,height:800});await page.evaluate(size=>{document.documentElement.style.fontSize=size+'px';openPage('home');scrollTo(0,0);},size);
    assert.equal(await page.locator('#field-today-actions').evaluate(d=>getComputedStyle(d).position),'static');assert.equal(await page.locator('#workday-timer #field-today-actions').count(),1);
    for(const button of await page.locator('#field-today-actions button').all()){await button.evaluate(e=>e.scrollIntoView({block:'center'}));const r=await button.boundingBox(),nav=await page.locator('.bottom').boundingBox();assert(r.width>=48&&r.height>=48&&r.y>=0&&r.y+r.height<=nav.y+1);assert(await button.evaluate(b=>document.elementFromPoint(b.getBoundingClientRect().x+b.offsetWidth/2,b.getBoundingClientRect().y+b.offsetHeight/2)?.closest('button')===b));}
    assert.equal(await page.locator('#workday-timer [data-session-action=start]').locator('b').innerText(),'Arbeit starten');assert.equal(await page.locator('#home .today-week').evaluate(n=>getComputedStyle(n).borderLeftWidth),'1px');
   }
   await page.setViewportSize({width:360,height:800});await page.evaluate(()=>{document.documentElement.style.fontSize='16px';openPage('home');scrollTo(0,0);});await page.screenshot({path:'outputs/grafik-heute-G3-360.png'});
  });
  await check('Existing fixture weather causes appear in the same hint',async()=>{
   const weatherPage=await browser.newPage({viewport:{width:360,height:800}});try{await weatherPage.addInitScript({content:fixtureSource('planning-ui.test.cjs')});await weatherPage.goto(pathToFileURL(path.join(assets,'mobile.html')).href);await weatherPage.waitForFunction(()=>document.body.dataset.fieldReady==='true');await weatherPage.evaluate(()=>PlanningUI.open());assert.match(await weatherPage.locator('#planning-body .planning-weather-reason').first().innerText(),/Regen im Arbeitszeitraum/);}finally{await weatherPage.close();}
  });
  await check('Main tabs have no Back, nested pages one header Back, and visible controls remain 48px clear of tabs',async()=>{
   for(const width of [320,360,426])for(const size of [16,24]){await page.setViewportSize({width,height:800});await page.evaluate(size=>document.documentElement.style.fontSize=size+'px',size);
    for(const [name,id]of [['heute','home'],['mehr','more-page'],['status','status-page'],['kunden','customers'],['arbeit','worklogs'],['erfassung','work-entry']]){
     await page.evaluate(id=>{openPage(id);scrollTo(0,0);},id);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),id+' '+width+'/'+size);
     const dialog=page.locator('dialog[open]'),modal=await dialog.count(),scope=modal?dialog.last():page.locator('#'+id);
     if(id!=='work-entry'){const back=page.locator('#'+id+'>.page-head .back:visible');assert.equal(await back.count(),['home','customers','more-page'].includes(id)?0:1,id);assert.equal(await page.locator('.app-context-back:visible').count(),0);}
     for(const b of await scope.locator('button:visible').all()){const r=await b.boundingBox();assert(r.width>=47.9&&r.height>=47.9,id+' target '+await b.innerText()+' '+JSON.stringify(r));}
     if(!modal){const nav=await page.locator('.bottom').boundingBox();for(const bar of await scope.locator('.daily-list-tools:visible,.worklog-window-actions:visible,.order-window-actions:visible').all()){const r=await bar.boundingBox();assert(r.y+r.height<=nav.y+1,id+' toolbar');}}
     await page.screenshot({path:'outputs/grafik-'+name+'-'+width+'-'+size+'.png'});
    }
   }
  });
  await check('The final work row in a long synthetic list scrolls clear of both toolbar and tabs',async()=>{
   for(const width of [320,360,426])for(const size of [16,24]){await page.setViewportSize({width,height:800});await page.evaluate(async size=>{document.documentElement.style.fontSize=size+'px';const original=master.worklogs[0];master.worklogs=Array.from({length:25},(_,i)=>({...original,id:'qa_layout_work_'+i}));load();openPage('worklogs');scrollTo(0,document.documentElement.scrollHeight);await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));},size);
    const last=page.locator('#worklog-list .worklog-entry').last(),r=await last.boundingBox(),bar=await page.locator('#daily-tools-worklogs').boundingBox();assert(r.y>=0&&r.y+r.height<=bar.y+1);assert(await last.evaluate(n=>{const r=n.getBoundingClientRect();return n.contains(document.elementFromPoint(r.x+r.width/2,r.y+r.height/2));}));assert.equal(await page.evaluate(()=>writes.length),0);
   }
  });
  await check('Calendar attention uses one written chip and separates error, weather and Pixel delivery',async()=>{
   const statusPage=await browser.newPage({viewport:{width:360,height:800}});try{
    await statusPage.addInitScript({content:fixtureSource('planning-ui.test.cjs')});await statusPage.addInitScript(()=>{const owner=AndroidData.ownerCall;AndroidData.ownerCall=(name,raw)=>{const result=JSON.parse(owner(name,raw));if(name==='get_planning_settings'&&result.ok)result.data.integration={...result.data.integration,calendar_configured:false,calendar_connected:false,calendar_state:'authorization_required',calendar_setup_error_code:'CALENDAR_CONSENT_CANCELLED',weather_configured:true,notification_state:'ready'};return JSON.stringify(result);};});
    await statusPage.goto(pathToFileURL(path.join(assets,'mobile.html')).href);await statusPage.waitForFunction(()=>document.body.dataset.fieldReady==='true');await statusPage.evaluate(()=>openPage('status-page'));const card=statusPage.locator('#status-content .status-card').nth(1);assert.equal(await card.locator('.status-chip').count(),1);assert.equal(await card.getAttribute('data-tone'),'attention');assert.equal((await card.innerText()).split('Die Google-Freigabe wurde nicht abgeschlossen.').length-1,1);assert.equal(await card.locator('.status-line').count(),2);assert.equal(await card.locator('.status-line').nth(0).innerText(),'Wetterwarnungen: EIN');assert.equal(await card.locator('.status-line').nth(1).innerText(),'Pixel-Meldungen: bereit');assert(await card.evaluate(n=>getComputedStyle(n).borderLeftWidth===getComputedStyle(n).borderRightWidth));assert.doesNotMatch(await card.innerText(),/CALENDAR_/);assert.equal(await statusPage.locator('#status-content details[open]').count(),0);assert.equal(await statusPage.evaluate(()=>calls.filter(c=>/^(create|update|request|cancel|archive)/.test(c.name)).length),0);
    await statusPage.screenshot({path:'outputs/grafik-status-freigabe-360.png'});
   }finally{await statusPage.close();}
  });
  await check('More icons and search presentation preserve exact menu targets, order and original search dialog',async()=>{
   await page.setViewportSize({width:360,height:800});await page.evaluate(()=>{document.documentElement.style.fontSize='16px';openPage('more-page');scrollTo(0,0);});
   const before=JSON.parse(fs.readFileSync(path.join(__dirname,'fixtures/menu-presentation-baseline.json'),'utf8')).entries,buttons=page.locator('#more-page .more-destinations button');const after=await buttons.evaluateAll(rows=>rows.map(b=>({label:b.querySelector('strong')?.textContent||b.textContent,targets:[...b.attributes].filter(a=>a.name.startsWith('data-')).map(a=>[a.name,a.value]).sort()})));assert.deepEqual(after,before);
   // Reproduce a late offers component after the billing group already exists.
   await page.evaluate(()=>{const nav=document.querySelector('#more-page .more-destinations');nav.append(document.querySelector('[data-more-destination=offers]'));});
   await page.waitForFunction(expected=>JSON.stringify([...document.querySelectorAll('#more-page .more-destinations button')].map(b=>({label:b.querySelector('strong')?.textContent||b.textContent,targets:[...b.attributes].filter(a=>a.name.startsWith('data-')).map(a=>[a.name,a.value]).sort()})))===JSON.stringify(expected),before);
   assert.equal(await buttons.count(),before.length);
   for(const b of await buttons.all()){assert.equal(await b.locator(':scope>.more-icon').count(),1);assert.equal(await b.locator('.more-icon').getAttribute('stroke'),'currentColor');assert.equal(await b.locator('.more-icon').getAttribute('stroke-width'),'2');const r=await b.locator('.more-icon').boundingBox();assert.equal(r.width,24);assert.equal(r.height,24);}
   const search=page.locator('#more-page [data-global-search]');assert.equal(await search.locator('small').innerText(),'Kunden, Arbeiten, Fotos, Dokumente');await search.click();assert(await page.locator('#global-search-dialog').isVisible());await page.evaluate(()=>handleAndroidBack());assert.equal(await page.locator('#global-search-dialog').isVisible(),false);assert.equal(await page.evaluate(()=>currentPage),'more-page');assert.equal(await page.evaluate(()=>writes.length),0);await page.screenshot({path:'outputs/grafik-mehr-G6-360.png'});
  });
 }finally{await browser.close();}
 fs.writeFileSync('outputs/visual-consistency-tests.json',JSON.stringify({classification:'SYNTHETIC_TEST',nativeDevice:false,results},null,2));if(results.some(x=>x.result==='FAIL'))process.exitCode=1;
})().catch(e=>{console.error(e);process.exitCode=1;});

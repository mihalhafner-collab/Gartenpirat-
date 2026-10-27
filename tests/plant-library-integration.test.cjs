'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs');
const root=path.resolve(__dirname,'..'),assets=path.join(root,'android/app/src/main/assets');
(async()=>{
 const results=[];let browser;
 const test=async(name,fn)=>{try{await fn();results.push({name,result:'PASS'});}catch(error){results.push({name,result:'FAIL',error:error.stack});}console.log(JSON.stringify(results.at(-1)));};
 try{
  browser=await chromium.launch({headless:true});
  const context=await browser.newContext({viewport:{width:360,height:640},isMobile:true,hasTouch:true});
  const page=await context.newPage(),errors=[],external=[];
  page.on('pageerror',e=>errors.push(e.message));
  page.on('request',r=>{if(/^https?:/.test(r.url())&&!r.url().startsWith('http://127.0.0.1:18766/'))external.push(r.url());});
  await page.addInitScript(()=>{
   const master={customers:[],orders:[],worklogs:[],invoices:[],photos:[]};
   window.qaPlantWrites=0;
   window.AndroidData={getState:()=>JSON.stringify({master,pending:[]}),getSyncStatus:()=>JSON.stringify({configured:false,pending:0}),getAppInfo:()=>JSON.stringify({versionName:'qa'}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),synchronize:()=>{qaPlantWrites++;},saveWorklog:()=>{qaPlantWrites++;throw Error('No writes in botanical tests');}};
   let selected;
   Object.defineProperty(window,'GartenpiratCatalog',{configurable:true,get:()=>selected,set:value=>{
    const original=value.plants[0];
    value.plants=[{...original,id:'qa-plant',name:'QA Profil',scientificName:'QA alter Name',acceptedName:'QA akzeptierter Name',alternativeNames:['QA akzeptierter Name','QA alter Name'],taxonScope:'species_aggregate',cultivationScope:'IDENTIFICATION_AND_PREVENTION_REFERENCE_NO_CULTIVATION_ADVICE',care:'',planting:'',propagation:[],poison:[],contact:[],rhsWarnings:['QA Warnung <script>qaPlantWrites++</script>'],safetyNotice:'Keine Verzehrfreigabe. Fehlende Giftangaben bedeuten nicht ungiftig.',scopeWarnings:['QA Quellenklima ist keine örtliche Frostgarantie.'],regionalRestrictions:{checkedAt:'2026-10-23',EU:{effectiveDate:'2027-08-02',restrictionsSummary:'QA Beschränkungen erst ab Wirksamkeit.'},CH:{annex:'2.2',notice:'Inverkehrbringen und Einfuhr beschränkt; keine allgemeine Entfernungspflicht bestehender Pflanzen.'},regionalRules:'Örtliche Vorschriften gesondert prüfen.'},images:original.images.map(i=>({...i,changes:'QA dokumentierte Bildbearbeitung.'}))}];
    selected=value;
   }});
  });
  await page.goto(pathToFileURL(path.join(assets,'mobile.html')).href);
  await page.waitForFunction(()=>document.documentElement.dataset.startup==='ready'&&document.body.dataset.fieldReady==='true');
  await page.evaluate(()=>{openPage('settings-page');});
  await page.locator('#setting-garden-shortcut').check();
  await page.evaluate(()=>openPage('home'));
  await page.getByRole('button',{name:'Gartenpirat öffnen',exact:true}).click();
  await page.locator('[data-gp-action=lexicon]').click();
  await test('Accepted and historical botanical names resolve to the same stable entry',async()=>{
   for(const query of ['QA akzeptierter Name','QA alter Name']){await page.locator('#gp-query').fill(query);assert.equal(await page.locator('.gp-plant-card').count(),1);assert.equal(await page.locator('.gp-plant-card').getAttribute('data-gp-plant'),'qa-plant');}
   await page.locator('.gp-plant-card').click();assert((await page.locator('.gp-taxonomy').innerText()).includes('QA akzeptierter Name'));
  });
  await test('Aggregate, climate, identification-only scope and missing care are visible',async()=>{
   const text=await page.locator('.gp-portrait').innerText();
   for(const value of ['Artengruppe','keine Anbauempfehlung','keine örtliche Frostgarantie','In der Quelle nicht angegeben'])assert(text.includes(value),value);
  });
  await test('EU effective date and Swiss annex2.2 remain visibly separate',async()=>{
   const text=await page.locator('.gp-regional-law').innerText();
   for(const value of ['Europäische Union','02.08.2027','Schweiz','Anhang 2.2','keine allgemeine Entfernungspflicht','23.10.2026'])assert(text.includes(value),value);
  });
  await test('Unknown toxicity and source warnings are preserved and escaped',async()=>{
   const text=await page.locator('.gp-safety').innerText();assert(text.includes('Fehlende Giftangaben bedeuten nicht ungiftig'));assert(text.includes('QA Warnung <script>'));
   assert.equal(await page.locator('.gp-safety script').count(),0);assert.equal(await page.evaluate(()=>qaPlantWrites),0);
  });
  await test('Preview has motif, author, exact license, source and transformation',async()=>{
   for(const figure of await page.locator('.gp-gallery figure').all()){
    const text=await figure.innerText();assert(text.includes('QA dokumentierte Bildbearbeitung.'));assert(text.includes('Bildquelle'));
    assert.equal(await figure.locator('figcaption a').count(),2);
    for(const link of await figure.locator('figcaption a').all()){const b=await link.boundingBox();assert(b.height>=48,b.height);}
   }
  });
  await test('360x640 with150 and200percent text has no horizontal portrait overflow',async()=>{
   for(const scale of [1.5,2]){
    await page.evaluate(scale=>document.documentElement.style.fontSize=(scale*100)+'%',scale);
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
    assert(await page.locator('#gp-content').evaluate(n=>n.scrollWidth<=n.clientWidth+1));
    for(const selector of ['.gp-back','.gp-close']){const b=await page.locator(selector).boundingBox();assert(b.width>=48&&b.height>=48,selector+JSON.stringify(b));}
   }
  });
  await test('Fullscreen retains transformation and48px controls with200percent text',async()=>{
   await page.locator('[data-gp-image="0"]').click();
   assert((await page.locator('#gp-image-caption').innerText()).includes('QA dokumentierte Bildbearbeitung.'));
   for(const button of await page.locator('.gp-image-toolbar button:visible').all()){
    const b=await button.boundingBox();assert(b.width>=48&&b.height>=48,JSON.stringify(b));assert(b.x>=0&&b.y>=0&&b.x+b.width<=361&&b.y+b.height<=641,JSON.stringify(b));
   }
   assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
   await page.locator('[data-gp-action=image-next]').click();assert.equal(await page.locator('#gp-image-count').innerText(),'Bild 2 von 2');
   await page.evaluate(()=>handleAndroidBack());assert.equal(await page.locator('.gp-portrait h3').innerText(),'QA Profil');
  });
  await test('Offline catalogue and image return keep the same entry without business or external calls',async()=>{
   await context.setOffline(true);
   const decoded=await page.locator('.gp-gallery img').evaluateAll(async images=>{await Promise.all(images.map(i=>i.decode()));return images.every(i=>i.naturalWidth>0);});assert(decoded);
   await page.locator('.gp-back').click();assert.equal(await page.locator('#gp-query').inputValue(),'QA alter Name');
   assert.deepEqual(errors,[]);assert.deepEqual(external,[]);assert.equal(await page.evaluate(()=>qaPlantWrites),0);
  });
  await context.close();
 }finally{if(browser)await browser.close();}
 fs.writeFileSync(path.join(root,'outputs/qa-plant-library-ui.json'),JSON.stringify({classification:'SYNTHETIC_TEST',scope:'Source Chromium with synthetic editorial metadata; no native APK or real photo review claim',results},null,2));
 process.exitCode=results.some(r=>r.result==='FAIL')?1:0;
})().catch(error=>{console.error(error);process.exitCode=1;});

'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm');
const {pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs');
const root=path.resolve(__dirname,'..'),assets=path.resolve(process.env.HG_TEST_APP_ASSETS||path.join(root,'android/app/src/main/assets'));
const catalog=JSON.parse(fs.readFileSync(path.join(assets,'plant-catalog.json'),'utf8'));
(async()=>{
 const results=[];let browser;const closeWithin=promise=>Promise.race([promise,new Promise(resolve=>setTimeout(resolve,5000))]);
 async function test(name,fn){try{await fn();results.push({name,result:'PASS'});}catch(e){results.push({name,result:'FAIL',error:e.stack});}console.log(JSON.stringify(results.at(-1)));}
 await test('Catalog has broad DACH garden coverage and unique stable botanical IDs',()=>{
  assert.equal(catalog.schemaVersion,2);assert(catalog.plants.length>=181);assert.equal(new Set(catalog.plants.map(p=>p.id)).size,catalog.plants.length);assert.equal(new Set(catalog.plants.map(p=>p.scientificName.toLocaleLowerCase('de'))).size,catalog.plants.length);
  assert(new Set(catalog.plants.map(p=>p.category)).size>=10);
  for(const name of ['Lavandula angustifolia','Malus domestica','Hydrangea macrophylla','Taxus baccata'])assert(catalog.plants.some(p=>p.scientificName===name),name);
 });
 await test('Exactly 21 Austrian Union-list neophytes carry explicit status, official evidence and two licensed offline images',()=>{
  const neophytes=catalog.plants.filter(p=>p.neophyte?.unionConcern);
  assert.equal(neophytes.length,21);assert(catalog.neophyteReference?.officialListUrl.startsWith('https://www.neobiota-austria.at/'));
  for(const p of neophytes){
   assert(p.neophyte.isNeophyte&&p.neophyte.invasive&&p.neophyte.austriaOccurrence,p.id);
   for(const key of ['austriaStatus','establishmentStatus','statusLabel','recognition','spread','impacts','control','disposal','legalNotes','dataNote'])assert(p.neophyte[key]&&p.neophyte[key].length,p.id+' '+key);assert(p.growth.length&&p.care.length,p.id+' practical profile');
   assert(p.sources.some(s=>s.url.startsWith('https://www.neobiota-austria.at/')),p.id);assert(p.sources.some(s=>s.url.startsWith('https://environment.ec.europa.eu/')),p.id);
   assert.equal(p.images.length,2,p.id);
  }
  const butterflyBush=catalog.plants.find(p=>p.id==='buddleja-davidii');assert(butterflyBush.neophyte?.isNeophyte&&butterflyBush.neophyte?.invasive);assert.equal(butterflyBush.neophyte.unionConcern,false);assert.equal(butterflyBush.neophyte.establishmentStatus,'established');assert(butterflyBush.sources.some(s=>s.url.includes('/sommerflieder')));
 });
 await test('Database adapter exactly matches canonical JSON; all sources and image rights traceable',()=>{
  const sandbox={window:{}};vm.runInNewContext(fs.readFileSync(path.join(assets,'plant-catalog.js'),'utf8'),sandbox);
  assert.deepEqual(JSON.parse(JSON.stringify(sandbox.window.GartenpiratCatalog)),catalog);
  for(const p of catalog.plants){
   assert(p.family&&p.planting&&p.care,p.id);if(!p.neophyte)assert(p.light.length&&p.water.length,p.id);assert.equal(p.images.length,2,p.id);
   assert(p.sources.every(s=>/^https:\/\/(plants\.ces\.ncsu\.edu|www\.rhs\.org\.uk|gizbonn\.de|www\.neobiota-austria\.at|environment\.ec\.europa\.eu)\//.test(s.url)),p.id);
   for(const pic of p.images){const image=fs.readFileSync(path.join(assets,pic.path));assert(pic.author&&pic.source.startsWith('https://')&&pic.licenseUrl.startsWith('https://'));assert(/^(?:CC0|Public domain|Public Domain Mark|CC BY(?:-SA)? [1-4]\.[0-9])/.test(pic.license)&&!/NC|ND/.test(pic.license),pic.license);assert(!image.includes(Buffer.from('Exif\0\0')),pic.path);}
  }
  for(const file of ['plant-catalog.json','plant-catalog.js'])assert.deepEqual(fs.readFileSync(path.join(root,'static',file)),fs.readFileSync(path.join(assets,file)),file);
  const sw=fs.readFileSync(path.join(root,'static/sw.js'),'utf8');for(const p of catalog.plants)for(const pic of p.images)assert(sw.includes('/'+pic.path),pic.path);
 });
 browser=await chromium.launch({headless:true,channel:'chrome'});
 try{
  const context=await browser.newContext({viewport:{width:412,height:915},isMobile:true,hasTouch:true});
  const page=await context.newPage(),errors=[],external=[];
  page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(/^https?:/.test(r.url())&&!r.url().startsWith('http://127.0.0.1:18766/'))external.push(r.url());});
  await page.addInitScript(()=>{
   const master={customers:[{id:'qa_gp_customer',name:'QA Pflanzenkunde',city:'Testort',active:1}],orders:[],worklogs:[],invoices:[],photos:[]};
   window.gpWrites=[];window.gpSync=0;window.gpSpeech=0;
   window.AndroidData={getState:()=>JSON.stringify({master,pending:[]}),getSyncStatus:()=>JSON.stringify({configured:false,pending:0}),getAppInfo:()=>JSON.stringify({versionName:'1.44.0'}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),synchronize:()=>{gpSync++;},saveCustomer:()=>{gpWrites.push('customer');throw Error('No business write permitted');},saveWorklog:()=>{gpWrites.push('worklog');throw Error('No business write permitted');}};
   window.AndroidSpeech={start:()=>gpSpeech++};window.AndroidPhone={dial:()=>{},openMaps:()=>{}};
  });
  await page.goto(pathToFileURL(path.join(assets,'mobile.html')).href);await page.waitForSelector('#gp-launcher');
  const launch=()=>page.getByRole('button',{name:'Gartenpirat öffnen',exact:true}).click();
  await test('Labelled launcher appears once and menu includes the dedicated Austrian neophyte area',async()=>{
   assert.equal(await page.locator('#gp-launcher').count(),1);await launch();
   assert.deepEqual(await page.locator('.gp-options strong').allTextContents(),['Zufällige Pflanze','Pflanzenlexikon','Neophyten in Österreich','Eigenes Foto öffnen']);
   assert.equal(await page.evaluate(()=>Gartenpirat.neophyteCount),22);assert.equal(await page.evaluate(()=>Gartenpirat.unionNeophyteCount),21);
   await page.screenshot({path:path.join(root,'outputs/Gartenpirat-menu.png')});
  });
  await test('Random portraits include two offline pictures, care, source and toxicity sections; no immediate repeats',async()=>{
   await page.locator('[data-gp-action=random]').click();let last;
   for(let i=0;i<10;i++){const name=await page.locator('.gp-portrait h3').innerText();assert(name&&name!==last);last=name;assert.equal(await page.locator('.gp-gallery img').count(),2);await page.waitForFunction(()=>[...document.querySelectorAll('.gp-gallery img')].every(i=>i.complete&&i.naturalWidth>0));assert.equal(await page.locator('.gp-gallery img').evaluateAll(images=>images.every(i=>i.complete&&i.naturalWidth>0)),true);assert((await page.locator('.gp-portrait').innerText()).includes('Giftigkeit & Umgang'));if(i===0)await page.screenshot({path:path.join(root,'outputs/Gartenpirat-portrait.png')});await page.locator('[data-gp-action=random]').click();}
   await page.locator('.gp-back').click();assert.equal(await page.locator('.gp-options').count(),1);
  });
  await test('Neophyte area separates established, invasive and legally regulated profiles and exposes sourced status fields',async()=>{
   await page.locator('[data-gp-action=neophytes]').click();assert.equal(await page.locator('#gp-neophyte').inputValue(),'all');assert.equal(await page.locator('.gp-plant-card').count(),22);assert((await page.locator('.gp-neophyte-note').innerText()).includes('amtliche'));
   await page.locator('#gp-neophyte').selectOption('established');assert.equal(await page.locator('.gp-plant-card').count(),13);
   await page.locator('#gp-neophyte').selectOption('union');assert.equal(await page.locator('.gp-plant-card').count(),21);
   await page.locator('#gp-query').fill('Sommerflieder');assert.equal(await page.locator('.gp-plant-card').count(),0);await page.locator('#gp-neophyte').selectOption('invasive');assert.equal(await page.locator('.gp-plant-card').count(),1);assert((await page.locator('.gp-plant-card small').innerText()).toLocaleLowerCase('de').includes('invasiver neophyt'));
   await page.locator('#gp-query').fill('Götterbaum');assert.equal(await page.locator('.gp-plant-card').count(),1);await page.locator('.gp-plant-card').click();assert.equal(await page.locator('.gp-portrait h3').innerText(),'Götterbaum');
   const panel=await page.locator('.gp-neophyte-panel').innerText();for(const heading of ['Status in Österreich','Erkennungsmerkmale','Ausbreitung','Ökologische Auswirkungen','Kontrolle','Entsorgung','Rechtlicher Hinweis','Datenumfang'])assert(panel.includes(heading),heading);
   assert.equal(await page.locator('.gp-source a').count()>=3,true);
   await page.locator('.gp-back').click();await page.locator('.gp-back').click();assert.equal(await page.locator('.gp-options').count(),1);
  });
  await test('Plant images open full-screen, navigate, zoom and Android Back returns to the same portrait',async()=>{
   await page.evaluate(()=>{Gartenpirat.close();openPage('home');});await launch();await page.locator('[data-gp-action=lexicon]').click();await page.locator('#gp-query').fill('Götterbaum');await page.locator('.gp-plant-card').click();await page.locator('[data-gp-image="0"]').click();
   assert(await page.locator('#gp-dialog').evaluate(node=>node.classList.contains('gp-image-mode')));assert.equal(await page.locator('#gp-image-count').innerText(),'Bild 1 von 2');
   await page.setViewportSize({width:320,height:568});const toolbar=await page.locator('.gp-image-toolbar').boundingBox();assert(toolbar.x>=0&&toolbar.x+toolbar.width<=320);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));await page.setViewportSize({width:412,height:915});
   await page.locator('[data-gp-action=image-zoom-in]').click();assert((await page.locator('#gp-full-image').getAttribute('style')).includes('scale(1.5)'));
   await page.locator('[data-gp-action=image-next]').click();assert.equal(await page.locator('#gp-image-count').innerText(),'Bild 2 von 2');
   await page.evaluate(()=>handleAndroidBack());assert.equal(await page.locator('.gp-portrait h3').innerText(),'Götterbaum');assert.equal(await page.locator('#gp-dialog').evaluate(node=>node.classList.contains('gp-image-mode')),false);
   await page.locator('.gp-back').click();
  });
  await test('Lexicon searches scientific and German names, combines filters and handles zero results',async()=>{
   await page.evaluate(()=>Gartenpirat.close());await launch();await page.locator('[data-gp-action=lexicon]').click();await page.locator('#gp-query').fill('Lavandula angustifolia');assert.equal(await page.locator('.gp-plant-card').count(),1);
   await page.locator('#gp-query').fill('Lavendel');assert.equal(await page.locator('.gp-plant-card').count(),1);
   await page.locator('.gp-plant-card').click();assert.equal(await page.locator('.gp-portrait h3').innerText(),'Echter Lavendel');
   await page.locator('.gp-back').click();assert.equal(await page.locator('#gp-query').inputValue(),'Lavendel');
   await page.locator('#gp-query').fill('');await page.locator('#gp-category').selectOption('Kräuter');await page.locator('#gp-light').selectOption('Sonne');
   assert((await page.locator('.gp-plant-card').count())>0);const names=await page.locator('.gp-plant-card strong').allTextContents();for(const name of names){const p=catalog.plants.find(p=>p.name===name);assert.equal(p.category,'Kräuter');assert(p.light.some(l=>l.startsWith('Sonne')));}
   await page.locator('#gp-query').fill('zzznichtvorhanden');assert.equal(await page.locator('.gp-empty').count(),1);
   await page.locator('#gp-query').fill('');await page.locator('#gp-category').selectOption('');await page.locator('#gp-light').selectOption('');
   await page.screenshot({path:path.join(root,'outputs/Gartenpirat-lexikon.png')});
  });
  await test('Android Back traverses portrait, lexicon and menu one step without navigating the business page',async()=>{
   const original=await page.evaluate(()=>currentPage);await page.locator('.gp-plant-card').first().click();
   await page.evaluate(()=>handleAndroidBack());assert.equal(await page.locator('#gp-query').count(),1);
   await page.evaluate(()=>handleAndroidBack());assert.equal(await page.locator('.gp-options').count(),1);
   await page.evaluate(()=>handleAndroidBack());assert.equal(await page.locator('#gp-dialog').getAttribute('open'),null);assert.equal(await page.evaluate(()=>currentPage),original);
  });
  await test('Launcher stays out of work entry while an unsaved draft survives a trip to Today and the botanical dialog',async()=>{
   await page.evaluate(()=>{Gartenpirat.close();openPage('work-entry');});await page.locator('#activity').fill('QA Entwurf bleibt erhalten');assert.equal(await page.locator('#gp-launcher').isVisible(),false);
   await page.evaluate(()=>openPage('home'));await launch();await page.locator('[data-gp-action=random]').click();await page.locator('.gp-close').click();await page.evaluate(()=>openPage('work-entry'));assert.equal(await page.locator('#activity').inputValue(),'QA Entwurf bleibt erhalten');assert.deepEqual(await page.evaluate(()=>gpWrites),[]);
  });
  await test('Mouse drag persists preferred position; collision avoidance protects business controls and never opens menu',async()=>{
   await page.evaluate(()=>openPage('home'));
   const before=await page.locator('#gp-launcher').boundingBox();await page.mouse.move(before.x+35,before.y+35);await page.mouse.down();await page.mouse.move(42,180,{steps:15});await page.mouse.up();
   assert.equal(await page.locator('#gp-dialog').getAttribute('open'),null);const moved=await page.locator('#gp-launcher').boundingBox();assert(Math.hypot(moved.x-before.x,moved.y-before.y)>30);
   const preferred=await page.evaluate(()=>localStorage.getItem('hausgarten.gartenpirat.position.v1'));assert(preferred);await page.reload({waitUntil:'domcontentloaded',timeout:60000});await page.waitForSelector('#gp-launcher');const after=await page.locator('#gp-launcher').boundingBox();assert.equal(await page.evaluate(()=>localStorage.getItem('hausgarten.gartenpirat.position.v1')),preferred);assert(after.x>=0&&after.y>=0&&after.x+after.width<=412);assert(await page.evaluate(()=>[...document.querySelectorAll('.page.active button')].filter(b=>b.id!=='gp-launcher').every(b=>{const r=b.getBoundingClientRect();if(!r.width||!r.height||r.top<0||r.bottom>innerHeight)return true;return !document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)?.closest('#gp-launcher');})));
  });
  await test('Keyboard move, position reset, resizing and touch cancellation remain usable',async()=>{
   await page.evaluate(()=>new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r))));const b=await page.locator('#gp-launcher').boundingBox();await page.locator('#gp-launcher').focus();await page.keyboard.press('ArrowRight');const n=await page.locator('#gp-launcher').boundingBox();assert(n.x>=b.x);
   await page.locator('#gp-launcher').dispatchEvent('pointerdown',{pointerId:75,button:0,clientX:n.x+5,clientY:n.y+5});
   await page.locator('#gp-launcher').dispatchEvent('pointercancel',{pointerId:75});
   for(const [width,height] of [[320,568],[915,412],[412,915]]){await page.setViewportSize({width,height});await page.waitForFunction(()=>{const p=document.getElementById('gp-launcher').getBoundingClientRect();return p.x>=0&&p.y>=0&&p.right<=innerWidth+1&&p.bottom<=innerHeight+1;});const p=await page.locator('#gp-launcher').boundingBox();assert(p.x>=0&&p.y>=0&&p.x+p.width<=width+1&&p.y+p.height<=height+1);}
   await launch();await page.locator('[data-gp-action=reset-position]').click();assert.equal(await page.evaluate(()=>localStorage.getItem('hausgarten.gartenpirat.position.v1')),null);await page.locator('.gp-close').click();
  });
  await test('Launcher is hidden for a foreign editor modal and returns to Today at the same position',async()=>{
   await page.evaluate(()=>openPage('home'));const before=await page.locator('#gp-launcher').boundingBox();
   await page.evaluate(()=>{const d=document.createElement('dialog');d.id='qa-preserved-editor';d.innerHTML='<label>Entwurf<input value="Unverändert"></label>';document.body.appendChild(d);d.showModal();});
   await page.waitForFunction(()=>document.getElementById('gp-launcher').hidden);assert.notEqual(await page.locator('#gp-launcher').evaluate(x=>x.parentElement.id),'qa-preserved-editor');assert.equal(await page.evaluate(()=>Gartenpirat.open()),false);assert.equal(await page.locator('#gp-dialog').getAttribute('open'),null);assert.equal(await page.locator('#qa-preserved-editor input').inputValue(),'Unverändert');
   await page.evaluate(()=>document.getElementById('qa-preserved-editor').close());await page.waitForFunction(()=>!document.getElementById('gp-launcher').hidden);const after=await page.locator('#gp-launcher').boundingBox();assert(Math.abs(after.x-before.x)<3&&Math.abs(after.y-before.y)<3);await launch();assert.equal(await page.locator('#gp-dialog').getAttribute('open'),'');await page.locator('.gp-close').click();
  });
  await test('Launcher appears only on Today; small screens do not overflow and no upload or business write occurs',async()=>{
   await context.setOffline(true);
   const otherPages=await page.locator('section.page[id]').evaluateAll(nodes=>nodes.map(node=>node.id).filter(id=>id!=='home'));
   for(const id of otherPages){await page.evaluate(id=>{Gartenpirat.close();openPage(id);},id);assert.equal(await page.locator('#gp-launcher').isVisible(),false,id);}
   await page.evaluate(()=>openPage('home'));assert.equal(await page.locator('#gp-launcher').isVisible(),true);
   await launch();await page.locator('[data-gp-action=lexicon]').click();
   for(const width of [320,360,412,1280]){await page.setViewportSize({width,height:915});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));}
   assert.deepEqual(external,[]);assert.deepEqual(await page.evaluate(()=>gpWrites),[]);assert.deepEqual(errors,[]);
  });
  await test('All 362 packaged botanical images decode while offline',async()=>{
   await context.setOffline(true);
   const paths=catalog.plants.flatMap(p=>p.images.map(image=>image.path));
   const failed=await page.evaluate(async paths=>{const failed=[];for(const src of paths){const image=new Image();image.src=src;try{await image.decode();if(!image.naturalWidth||!image.naturalHeight)failed.push(src);}catch(_){failed.push(src);}}return failed;},paths);
   assert.deepEqual(failed,[]);
  });
  await closeWithin(context.close());
 }finally{if(browser)await closeWithin(browser.close());}
 fs.writeFileSync(path.join(root,'outputs/Gartenpirat-tests.json'),JSON.stringify({method:'actual_browser_with_isolated_business_bridge_and_offline_assets',asset_origin:process.env.HG_TEST_APP_ASSETS?'extracted_operative_candidate_APK':'source_assets',plant_count:catalog.plants.length,catalog_sha256:require('node:crypto').createHash('sha256').update(fs.readFileSync(path.join(assets,'plant-catalog.json'))).digest('hex'),results},null,2));
 process.exit(results.some(r=>r.result==='FAIL')?1:0);
})().catch(error=>{console.error(error);process.exitCode=1;});

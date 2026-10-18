'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs');
const root=path.resolve(__dirname,'..'),assets=path.join(root,'android/app/src/main/assets');
(async()=>{
 const results=[];let browser;
 async function check(name,fn){try{await fn();results.push({name,result:'PASS'});}catch(error){results.push({name,result:'FAIL',error:error.stack});}console.log(JSON.stringify(results.at(-1)));}
 browser=await chromium.launch({headless:true,channel:'chrome'});
 try{
  const context=await browser.newContext({viewport:{width:412,height:915},isMobile:true,hasTouch:true});
  const page=await context.newPage(),errors=[],external=[];
  page.on('pageerror',e=>errors.push(e.message));page.on('request',r=>{if(/^https?:/.test(r.url())&&!r.url().startsWith('http://127.0.0.1:18766/'))external.push(r.url());});
  await page.addInitScript(()=>{
   const master={customers:[{id:'qa_photo_customer',name:'QA Foto',city:'Testort',active:1}],orders:[],worklogs:[],invoices:[],photos:[]};
   window.photoBridgeCalls=[];window.photoWrites=[];window.photoBlobURLs=[];
   window.AndroidData={getState:()=>JSON.stringify({master,pending:[]}),getSyncStatus:()=>JSON.stringify({configured:false,pending:0}),getAppInfo:()=>JSON.stringify({versionName:'1.44.0'}),getWorkSession:()=>JSON.stringify({state:'IDLE'}),saveWorklog:()=>photoWrites.push('worklog'),savePhoto:()=>photoWrites.push('photo'),synchronize:()=>photoWrites.push('sync')};
   window.AndroidSpeech={start:()=>{}};window.AndroidPhone={dial:()=>{},openMaps:()=>{}};
   window.AndroidGardenPhotos={preparePicker:()=>photoBridgeCalls.push('picker'),setFullscreen:enabled=>photoBridgeCalls.push(enabled?'fullscreen:on':'fullscreen:off')};
   const create=URL.createObjectURL.bind(URL),revoke=URL.revokeObjectURL.bind(URL);
   URL.createObjectURL=file=>{const url=create(file);photoBlobURLs.push(url);return url;};
   URL.revokeObjectURL=url=>{photoBlobURLs=photoBlobURLs.filter(item=>item!==url);revoke(url);};
  });
  await page.goto(pathToFileURL(path.join(assets,'mobile.html')).href);await page.waitForSelector('#gp-launcher');
  const png=Buffer.from(await page.evaluate(()=>{
   const c=document.createElement('canvas');c.width=800;c.height=1100;const ctx=c.getContext('2d');
   ctx.fillStyle='#f1e8cd';ctx.fillRect(0,0,800,1100);
   ctx.fillStyle='#233f2b';ctx.fillRect(0,760,800,340);
   ctx.strokeStyle='#487349';ctx.lineWidth=22;ctx.beginPath();ctx.moveTo(400,770);ctx.lineTo(410,280);ctx.stroke();
   for(const [x,y,angle] of [[320,630,-.5],[500,500,.5],[320,400,-.5],[480,290,.5]]){ctx.save();ctx.translate(x,y);ctx.rotate(angle);ctx.fillStyle='#5a874c';ctx.beginPath();ctx.ellipse(0,0,130,55,0,0,Math.PI*2);ctx.fill();ctx.restore();}
   ctx.fillStyle='#f1e8cd';ctx.font='bold 46px sans-serif';ctx.fillText('SYNTHETISCHES TESTFOTO',56,910);
   return [...Uint8Array.from(atob(c.toDataURL('image/png').split(',')[1]),x=>x.charCodeAt(0))];
  }));
  const fixture={name:'garten-test.png',mimeType:'image/png',buffer:png};
  const launch=async()=>{await page.evaluate(()=>{Gartenpirat.close();openPage('home');});await page.locator('#gp-launcher').click();};
  const choose=async(file=fixture)=>{if(await page.locator('.gp-photo-options').count()&&!await page.locator('.gp-photo-options').isVisible())await page.locator('[data-gp-action=photo-options]').first().click();const [picker]=await Promise.all([page.waitForEvent('filechooser'),page.locator('[data-gp-action=choose-photo]').first().click()]);await picker.setFiles(file);};
  const ready=()=>page.waitForFunction(()=>document.querySelector('#gp-own-photo')?.naturalWidth===800);

  await check('Photo menu option opens the real browser file chooser and prepares the scoped native picker',async()=>{
   await launch();assert.deepEqual(await page.locator('.gp-options strong').allTextContents(),['Zufällige Pflanze','Pflanzenlexikon','Neophyten in Österreich','Eigenes Foto öffnen']);
   await page.screenshot({path:path.join(root,'outputs/Gartenpirat-Foto-Menue.png')});
   await choose();await ready();assert((await page.evaluate(()=>photoBridgeCalls)).includes('picker'));
  });
  await check('Selected image fills the viewport without cropping and requests native immersive display',async()=>{
   await ready();assert.equal(await page.locator('#gp-dialog').getAttribute('class'),'gp-dialog gp-photo-mode');
   const dialog=await page.locator('#gp-dialog').boundingBox();assert.equal(dialog.x,0);assert.equal(dialog.y,0);assert.equal(Math.round(dialog.width),412);assert.equal(Math.round(dialog.height),915);
   assert.equal(await page.locator('#gp-own-photo').evaluate(image=>getComputedStyle(image).objectFit),'contain');
   assert((await page.evaluate(()=>photoBridgeCalls)).includes('fullscreen:on'));
   assert.equal(await page.locator('.gp-browser-fullscreen').count(),0);
   await page.screenshot({path:path.join(root,'outputs/Gartenpirat-Foto-Vollbild.png')});
  });
  await check('Android Back restores the menu and system bars; both botanical features still work',async()=>{
   await page.evaluate(()=>handleAndroidBack());assert.equal(await page.locator('.gp-options button').count(),4);
   assert.equal((await page.evaluate(()=>photoBridgeCalls)).at(-1),'fullscreen:off');
   assert.deepEqual(await page.evaluate(()=>photoBlobURLs),[]);
   await page.locator('[data-gp-action=random]').click();assert.equal(await page.locator('.gp-gallery img').count(),2);await page.evaluate(()=>handleAndroidBack());
   await page.locator('[data-gp-action=lexicon]').click();await page.locator('#gp-query').fill('Lavendel');assert.equal(await page.locator('.gp-plant-card').count(),1);await page.evaluate(()=>handleAndroidBack());
  });
  await check('Opening and closing a photo preserves the existing unsaved work record',async()=>{
   await page.evaluate(()=>{Gartenpirat.close();openPage('work-entry');});await page.locator('#activity').fill('QA Foto-Entwurf erhalten');
   await page.evaluate(()=>openPage('home'));await page.locator('#gp-launcher').click();await choose();await ready();await page.locator('.gp-photo-close').click();
   await page.locator('.gp-close').click();await page.evaluate(()=>openPage('work-entry'));assert.equal(await page.locator('#activity').inputValue(),'QA Foto-Entwurf erhalten');
  });
  await check('Cancellation leaves the menu or previous photo intact; selecting the same file again works',async()=>{
   await launch();await choose([]);assert.equal(await page.locator('.gp-options button').count(),4);assert.equal(await page.locator('#gp-own-photo').count(),0);
   await choose();await ready();const previous=await page.locator('#gp-own-photo').getAttribute('src');await choose([]);
   assert.equal(await page.locator('#gp-own-photo').getAttribute('src'),previous);
   await choose();await ready();assert.notEqual(await page.locator('#gp-own-photo').getAttribute('src'),previous);
   assert.equal((await page.evaluate(()=>photoBlobURLs)).length,1);
  });
  await check('Empty, unsupported, corrupt and oversized files show recoverable errors',async()=>{
   await launch();
   const oversize=path.join(root,'outputs','synthetic-oversize-'+process.pid+'.jpg');fs.writeFileSync(oversize,Buffer.alloc(0),{flag:'wx'});fs.truncateSync(oversize,40*1024*1024+1);
   try{for(const [file,expected] of [
    [{name:'leer.jpg',mimeType:'image/jpeg',buffer:Buffer.alloc(0)},'leer'],
    [{name:'kein-bild.svg',mimeType:'image/svg+xml',buffer:Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"/>')},'Bilddatei'],
    [{name:'kaputt.jpg',mimeType:'image/jpeg',buffer:Buffer.from('not a JPEG')},'nicht öffnen'],
    [oversize,'40 MB']
   ]){
    await choose(file);await page.waitForFunction(text=>document.querySelector('.gp-photo-message')?.textContent.includes(text),expected);
    assert.equal(await page.locator('.gp-options button').count(),4);assert.deepEqual(await page.evaluate(()=>photoBlobURLs),[]);
   }
   }finally{fs.unlinkSync(oversize);}
   await choose();await ready();
  });
  await check('Fullscreen picture remains fitted after rotation and Escape frees all image resources',async()=>{
   await launch();await choose();await ready();
   for(const [width,height] of [[915,412],[320,568],[412,915]]){
    await page.setViewportSize({width,height});const image=await page.locator('#gp-own-photo').boundingBox();assert.equal(Math.round(image.width),width);assert.equal(Math.round(image.height),height);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
   }
   await page.keyboard.press('Escape');assert.equal(await page.locator('.gp-options button').count(),4);
   assert.deepEqual(await page.evaluate(()=>photoBlobURLs),[]);assert.equal((await page.evaluate(()=>photoBridgeCalls)).at(-1),'fullscreen:off');
  });
  await check('Closing during decoding cannot reopen the dialog or leak temporary image URLs',async()=>{
   await page.evaluate(bytes=>{
    const file=new File([new Uint8Array(bytes)],'pending.png',{type:'image/png'}),dt=new DataTransfer();dt.items.add(file);
    const input=document.getElementById('gp-photo-input');input.files=dt.files;input.dispatchEvent(new Event('change'));
    Gartenpirat.close();
   },[...png]);
   await page.waitForTimeout(150);assert.equal(await page.locator('#gp-dialog').getAttribute('open'),null);assert.deepEqual(await page.evaluate(()=>photoBlobURLs),[]);
  });
  await check('Browser fallback displays the image without native bridge, uploads or app-data writes',async()=>{
   await page.setViewportSize({width:412,height:915});await page.evaluate(()=>{delete window.AndroidGardenPhotos;});await launch();await choose();await ready();
   assert.equal(await page.locator('#gp-dialog').evaluate(d=>Math.round(d.getBoundingClientRect().width)),412);
   await page.locator('.gp-photo-close').click();assert.deepEqual(await page.evaluate(()=>photoBlobURLs),[]);
   assert.deepEqual(await page.evaluate(()=>photoWrites),[]);assert.deepEqual(external,[]);assert.deepEqual(errors,[]);
  });
  await context.close();
 }finally{await browser?.close();}
 const report={method:'actual_file_chooser_and_decoding_in_browser; native_bridge_mocked',native_google_photos_device_test:'NOT_PERFORMED',results};
 fs.writeFileSync(path.join(root,'outputs/Gartenpirat-Foto-Tests.json'),JSON.stringify(report,null,2));
 if(results.some(r=>r.result==='FAIL'))process.exitCode=1;
})().catch(error=>{console.error(error);process.exitCode=1;});

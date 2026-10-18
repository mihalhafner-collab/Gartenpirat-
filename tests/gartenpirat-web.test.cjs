'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {chromium}=require('./browser.cjs');
(async()=>{
 const browser=await chromium.launch({headless:true,channel:'chrome'}),results=[],closeWithin=promise=>Promise.race([promise,new Promise(resolve=>setTimeout(resolve,5000))]);
 try{
  const context=await browser.newContext(),page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('requestfailed',r=>console.log('REQUEST_FAILED '+r.url()+' '+r.failure()?.errorText));
  await page.goto(process.env.GP_QA_URL,{waitUntil:'networkidle'});
  await page.waitForSelector('#gp-launcher');
  await page.waitForFunction(()=>!!navigator.serviceWorker.controller,{},{timeout:120000});
  console.log('OFFLINE_INSTALL_COMPLETE');
  assert.equal(await page.locator('#gp-launcher').isVisible(),true);await page.evaluate(()=>showView('customers'));await page.waitForFunction(()=>document.getElementById('gp-launcher').hidden);assert.equal(await page.locator('#gp-launcher').isVisible(),false);await page.evaluate(()=>showView('dashboard'));await page.waitForFunction(()=>!document.getElementById('gp-launcher').hidden);
  results.push({name:'Web launcher is visible only on the dashboard view',result:'PASS'});
  await page.locator('#gp-launcher').click();assert.equal(await page.locator('.gp-options button').count(),4);
  results.push({name:'Web app uses the same botanical widget and catalog',result:'PASS'});
  await page.locator('[data-gp-action=lexicon]').click();await page.locator('#gp-query').fill('Lavendel');assert.equal(await page.locator('.gp-plant-card').count(),1);
  await page.locator('.gp-plant-card').click();await page.waitForFunction(()=>[...document.querySelectorAll('.gp-gallery img')].every(i=>i.complete&&i.naturalWidth>0));
  await page.locator('[data-gp-image="0"]').click();assert.equal(await page.locator('#gp-image-count').innerText(),'Bild 1 von 2');
  await page.locator('[data-gp-action=image-next]').click();assert.equal(await page.locator('#gp-image-count').innerText(),'Bild 2 von 2');
  await page.evaluate(()=>handleAndroidBack());assert.equal(await page.locator('.gp-portrait h3').innerText(),'Echter Lavendel');
  results.push({name:'Plant pictures open full-screen, navigate and return to the same portrait',result:'PASS'});
  await page.screenshot({path:path.join(__dirname,'../outputs/Gartenpirat-web.png')});
  await context.setOffline(true);await page.reload({waitUntil:'domcontentloaded',timeout:120000});await page.waitForSelector('#gp-launcher');await page.locator('#gp-launcher').click();await page.locator('[data-gp-action=random]').click();
  await page.waitForFunction(()=>document.querySelectorAll('.gp-gallery img').length===2&&[...document.querySelectorAll('.gp-gallery img')].every(i=>i.complete&&i.naturalWidth>0));
  results.push({name:'After HTTP installation the portrait, data and pictures survive an offline reload',result:'PASS'});
  await page.evaluate(()=>Gartenpirat.close());await page.locator('#gp-launcher').click();
  const fixture=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=64;c.height=96;return [...Uint8Array.from(atob(c.toDataURL('image/png').split(',')[1]),c=>c.charCodeAt(0))];});
  await page.locator('#gp-photo-input').setInputFiles({name:'web-offline.png',mimeType:'image/png',buffer:Buffer.from(fixture)});
  await page.waitForFunction(()=>document.querySelector('#gp-own-photo')?.naturalWidth===64);
  assert.equal(await page.locator('#gp-dialog').evaluate(d=>d.getBoundingClientRect().width),await page.evaluate(()=>innerWidth));
  await page.locator('.gp-photo-close').click();assert.equal(await page.locator('.gp-options button').count(),4);
  results.push({name:'Offline HTTP app also opens a selected private photo full-screen and returns to its menu',result:'PASS'});
  assert.deepEqual(errors,[]);results.push({name:'No page errors in actual HTTP/service-worker flow',result:'PASS'});
  fs.writeFileSync(path.join(__dirname,'../outputs/Gartenpirat-web-tests.json'),JSON.stringify({results},null,2));console.log(JSON.stringify({results}));
 }finally{await closeWithin(browser.close());}
})().then(()=>process.exit(0)).catch(e=>{console.error(e);process.exit(1);});

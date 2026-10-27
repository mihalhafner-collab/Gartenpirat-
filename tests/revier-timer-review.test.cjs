'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {pathToFileURL}=require('node:url'),{chromium}=require('./browser.cjs');
const fixtureSource=require('./ui-fixture-source.cjs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']}),results=[];
 try{
  const page=await browser.newPage({viewport:{width:320,height:740},hasTouch:true});
  await page.addInitScript({content:fixtureSource('timer-completion.test.cjs')});
  await page.addInitScript(()=>{
   timerFixture.review_required=true;timerFixture.review_reason='DEVICE_RESTART';timerFixture.revision_hash='a'.repeat(64);
   window.reviewCalls=[];window.reviewFail=true;
   AndroidData.reviewWorkSession=(id,json)=>{
    const input=JSON.parse(json);reviewCalls.push({id,input});if(reviewFail)return 'ERROR: WRITE_CONFLICT · Sitzung erneut prüfen';
    timerFixture={...timerFixture,state:'STOPPED_PENDING_REVIEW',worked_ms:input.worked_minutes*60000,hours:input.worked_minutes/60,break_minutes:input.break_minutes,review_required:false,revision_hash:'b'.repeat(64)};
    return JSON.stringify(timerFixture);
   };
  });
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);
  await page.waitForFunction(()=>document.documentElement.dataset.startup==='ready'&&document.body.dataset.todayReady==='true');
  const check=async(name,run)=>{await run();results.push({name,result:'PASS'});console.log('PASS '+name);};
  await check('Unverified session shows review instead of pause/stop and opens without keyboard focus',async()=>{
   assert.equal(await page.locator('#workday-timer [data-session-action="pause"]').count(),0);
   await page.locator('[data-session-action="review"]').click();assert(await page.locator('#timer-review-dialog').isVisible());
   assert.equal(await page.evaluate(()=>document.activeElement.id),'timer-review-title');
   assert.equal(await page.locator('#timer-review-work').inputValue(),'');
  });
  await check('Conflict preserves entered times and reason without producing a work record',async()=>{
   await page.locator('#timer-review-work').fill('150');await page.locator('#timer-review-break').fill('15');
   await page.locator('#timer-review-reason').fill('QA überprüfter Referenztag');await page.locator('#timer-review-dialog [name=confirmed]').check();
   await page.locator('#timer-review-dialog button[type=submit]').click();
   assert((await page.locator('#timer-review-dialog [role=alert]').innerText()).includes('WRITE_CONFLICT'));
   assert.equal(await page.locator('#timer-review-work').inputValue(),'150');assert.equal(await page.evaluate(()=>savedTimerWorks.length),0);
  });
  await check('200percent text at320/360/426 keeps correction controls scrollable and48px',async()=>{
   await page.evaluate(()=>document.documentElement.style.fontSize='200%');
   for(const width of [320,360,426]){
    await page.setViewportSize({width,height:740});await page.locator('#timer-review-dialog button[type=submit]').scrollIntoViewIfNeeded();
    const geometry=await page.locator('#timer-review-dialog').evaluate(e=>({width:e.clientWidth,scroll:e.scrollWidth,viewport:innerWidth,controls:[...e.querySelectorAll('button')].map(x=>({width:x.getBoundingClientRect().width,height:x.getBoundingClientRect().height}))}));
    assert(geometry.width<=width&&geometry.scroll<=geometry.width+1);assert(geometry.controls.every(x=>x.width>=48&&x.height>=48));
   }
  });
  await check('Retry keeps action ID; successful correction remains a reviewed session until explicit completion',async()=>{
   await page.evaluate(()=>reviewFail=false);await page.locator('#timer-review-dialog button[type=submit]').click();
   assert.equal(await page.locator('#timer-review-dialog').isVisible(),false);
   const calls=await page.evaluate(()=>reviewCalls);assert.equal(calls.length,2);assert.deepEqual(calls[0],calls[1]);
   assert.equal(calls[0].input.worked_minutes,150);assert.equal(calls[0].input.break_minutes,15);
   assert.equal(await page.evaluate(()=>savedTimerWorks.length),0);assert.equal(await page.evaluate(()=>timerFixture.worked_ms),9000000);
  });
  await check('Changed session revision preserves correction draft and requires a fresh confirmation',async()=>{
   await page.evaluate(()=>{timerFixture.review_required=true;timerFixture.revision_hash='c'.repeat(64);TodayUI.render();});
   await page.locator('[data-session-action="review"]').click();
   assert.equal(await page.locator('#timer-review-work').inputValue(),'150');
   assert.equal(await page.locator('#timer-review-reason').inputValue(),'QA überprüfter Referenztag');
   assert.equal(await page.locator('#timer-review-dialog [name=confirmed]').isChecked(),false);
   assert((await page.locator('#timer-review-dialog [role=alert]').innerText()).includes('Eingaben bleiben erhalten'));
   await page.locator('[data-review-close]').click();assert.equal(await page.evaluate(()=>reviewCalls.length),2);
  });
 }finally{await browser.close();fs.mkdirSync('outputs/integration-ui',{recursive:true});fs.writeFileSync('outputs/integration-ui/revier-timer-review.json',JSON.stringify({environment:'Cloud Chromium with synthetic bridge; no native SQLite or Pixel evidence',results},null,2));}
})().catch(error=>{console.error(error);process.exitCode=1;});

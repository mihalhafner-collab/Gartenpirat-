/* Compare presentation against the immutable public pre-P3 source, same Chromium
 * and synthetic bridges. No historical runtime data, no baseline regeneration. */
'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),os=require('node:os'),path=require('node:path'),cp=require('node:child_process'),crypto=require('node:crypto');
const {pathToFileURL}=require('node:url'),root=path.resolve(__dirname,'..');
const baseline=require('./fixtures/p3-ux-baseline.json'),temp=fs.mkdtempSync(path.join(os.tmpdir(),'qa_p3_public_before_'));
process.env.HG_TEST_APP_ASSETS=temp;
const {chromium}=require('./browser.cjs');
function fixture(name){const s=fs.readFileSync(path.join(__dirname,name),'utf8'),needle='await page.addInitScript(',tail=s.slice(s.indexOf(needle)+needle.length),end=tail.match(/\n {1,3}\}\);/);assert(end);return tail.slice(0,end.index+end[0].indexOf('}')+1);}
(async()=>{let browser;try{
 for(const relative of cp.execFileSync('git',['ls-tree','-r','--name-only',baseline.source_commit,'--','android/app/src/main/assets'],{cwd:root,encoding:'utf8'}).trim().split('\n')){
  const bytes=cp.execFileSync('git',['show',baseline.source_commit+':'+relative],{cwd:root,maxBuffer:32*1024*1024});
  if(baseline.source_hashes[relative])assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'),baseline.source_hashes[relative]);
  const target=path.join(temp,path.relative('android/app/src/main/assets',relative));fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,bytes);
 }
 browser=await chromium.launch({headless:true});const results=[];
 for(const phase of ['before','after']){
  const page=await browser.newPage({viewport:{width:360,height:900}});
  await page.addInitScript({content:'('+fixture('offers-photo-library.test.cjs')+')();'});
  const target=phase==='before'?path.join(temp,'mobile.html'):path.join(root,'android/app/src/main/assets/mobile.html');
  await page.goto(pathToFileURL(target).href);await page.waitForFunction(()=>!!window.OfferUI&&!!document.getElementById('settings-page'));
  await page.evaluate(()=>openPage('settings-page'));
  const visibleTechnical=await page.locator('#settings-sync-local').isVisible();
  await page.evaluate(()=>OfferUI.openForCustomer('qa_c1'));
  const visibleOfferControls=await page.locator('#offer-form input:not([type=hidden]),#offer-form select,#offer-form textarea').evaluateAll(nodes=>nodes.filter(n=>n.getClientRects().length&&!n.disabled).length);
  await page.locator('#offer-close').click();await page.evaluate(()=>{openCustomer('qa_c1');});
  const customerAreas=await page.locator('.customer-hub-tabs button').allTextContents();
  results.push({phase,visibleTechnical,visibleOfferControls,customerAreas});await page.waitForTimeout(300);await page.close();
 }
 assert.equal(results[0].visibleTechnical,true);assert.equal(results[1].visibleTechnical,false);
 assert(results[1].visibleOfferControls<results[0].visibleOfferControls,'Guidance must actually reduce simultaneous decisions');
 assert(!results[0].customerAreas.includes('Dokumente'));assert(results[1].customerAreas.includes('Dokumente'));
 fs.writeFileSync(path.join(root,'outputs/p3-ux-comparison.json'),JSON.stringify({classification:'SYNTHETIC_TEST',source_commit:baseline.source_commit,method:'same Cloud Chromium, viewport and isolated fixture; visible controls, no human timing claim',results},null,2));
 console.log('PASS immutable pre/post UX comparison: '+JSON.stringify(results));
 }finally{if(browser)await browser.close();fs.rmSync(temp,{recursive:true,force:true});}})().catch(e=>{console.error(e);process.exitCode=1;});

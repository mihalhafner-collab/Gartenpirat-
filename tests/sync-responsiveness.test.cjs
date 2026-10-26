/* Reproduce P3 startup I/O and status-only rerenders with synthetic stores. */
'use strict';
const assert=require('node:assert/strict'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs');
(async()=>{const browser=await chromium.launch({headless:true});try{
 const page=await browser.newPage({viewport:{width:360,height:900}}),errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(master=>{
  window.qaReads=0;window.qaCalls=[];window.qaBackupOpened=0;window.qaPolls=[];window.qaPhase='reading';
  const interval=window.setInterval;window.setInterval=(fn,ms,...args)=>{if(ms===5000)qaPolls.push(fn);return interval(fn,ms,...args);};
  window.AndroidData={openFullBackup:()=>qaBackupOpened++,getState:()=>{qaReads++;return JSON.stringify({master,pending:[]});},
   getWorkSession:()=>JSON.stringify({state:'IDLE'}),getAppInfo:()=>JSON.stringify({versionName:'qa'}),
   getSyncStatus:()=>JSON.stringify({drive_role:'backup_only',storage_contract_version:3,configured:false,syncing:false,phase:qaPhase,last_sync:'2026-10-20T07:00:00Z',pending:0,review:0}),
   ownerCall:(name)=>{qaCalls.push(name);const meta={created_at:'2026-10-20T08:00:00Z',sha256:'d'.repeat(64),byte_size:33524508};
    const data=name==='list_backups'?{backups:[{backup_id:'qa_backup',metadata:meta}]}:name==='inspect_backup'?{metadata:meta,state_hash:'c'.repeat(64),collections:{customers:1},restore_mode:'add_missing_without_overwrite'}:name==='get_file_metadata'?{sha256:meta.sha256,byte_size:meta.byte_size}:{};
    return JSON.stringify({ok:true,data});}};window.AndroidSpeech={start(){}};
 },require('./synthetic-fixtures.cjs').profile('edge_cases'));
 await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);
 await page.waitForSelector('#full-backup-card',{state:'attached'});await page.waitForTimeout(350);
 const initial=await page.evaluate(()=>({reads:qaReads,backup:qaCalls.filter(n=>['inspect_backup','get_file_metadata'].includes(n))}));
 assert.deepEqual(initial.backup,[],'Hidden settings must never read/decode/hash complete backups during startup or sync');
 await page.evaluate(()=>openPage('customers'));await page.locator('#customer-search').fill('QA');
 const before=await page.evaluate(()=>qaReads);
 await page.evaluate(()=>{for(const phase of ['reading','media','journal','publishing']){qaPhase=phase;qaPolls.forEach(f=>f());}});
 assert.equal(await page.evaluate(()=>qaReads),before,'Transport phases must not reload all business collections');
 assert.equal(await page.locator('#customer-search').inputValue(),'QA');
 await page.evaluate(()=>openPage('settings-page'));await page.waitForTimeout(80);
 assert.equal(await page.evaluate(()=>qaCalls.filter(n=>n==='inspect_backup').length),0,'Opening settings lists metadata without full backup reads');
 await page.locator('#full-backup-card [data-open-full-backup]').click();assert.equal(await page.evaluate(()=>qaBackupOpened),1);assert.equal(await page.evaluate(()=>qaCalls.filter(n=>['create_backup','restore_backup','inspect_backup','request_sync'].includes(n)).length),0);assert.equal(await page.locator('[data-backup-check],#settings-drive,#sync-direct-connect').count(),0);
 assert.deepEqual(errors,[]);console.log('PASS synthetic startup: no hidden full-backup I/O; sync phases: no global reload; single native full-backup entry; no legacy business transfer or archive decoding');
 }finally{await browser.close();}})().catch(e=>{console.error(e);process.exitCode=1;});

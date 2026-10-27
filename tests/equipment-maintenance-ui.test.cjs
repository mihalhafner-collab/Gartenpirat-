'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),{pathToFileURL}=require('node:url');
const {chromium}=require('./browser.cjs'),fixtureSource=require('./ui-fixture-source.cjs');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--no-sandbox']}),results=[],errors=[];
 try{
  const page=await browser.newPage({viewport:{width:360,height:800},hasTouch:true});page.setDefaultTimeout(8000);page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
  await page.addInitScript({content:fixtureSource('daily-usability.test.cjs')});
  await page.addInitScript(()=>{
   const copy=v=>JSON.parse(JSON.stringify(v)),today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Vienna',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date()),good=data=>JSON.stringify({ok:true,data}),bad=(code,message,known=false)=>JSON.stringify({ok:false,error:{code,message,...(known?{commit_state:'not_committed'}:{})}});
   const shift=(day,n)=>new Date(Date.parse(day+'T12:00:00Z')+n*86400000).toISOString().slice(0,10);
   const initial={today,device:{id:'qa_maintenance_equipment',name:'QA Gartenschere',condition:'defective',record_hash:'a'.repeat(64),maintenance_plans:[]},events:[],writes:[],calls:[],receipts:{},revision:1,loseDone:false,loseBeforeDone:false,failRead:false};
   window.qaGear=JSON.parse(sessionStorage.getItem('qa_gear_state')||'null')||initial;
   const previous=AndroidData.ownerCall;
   AndroidData.ownerCall=(name,raw)=>{
    const args=JSON.parse(raw),s=qaGear;s.calls.push({name,args:copy(args)});
    if(name==='list_equipment')return good({items:[copy(s.device)],equipment:[copy(s.device)],total:1});
    if(name==='get_equipment_maintenance')return s.failRead?bad('READ_UNAVAILABLE','QA Wartungsverlauf nicht lesbar'):good({record:copy(s.device),equipment:copy(s.device),plans:copy(s.device.maintenance_plans),items:copy(s.events.slice().reverse().slice(args.offset||0,(args.offset||0)+(args.limit||20))),total:s.events.length});
    if(name==='inspect_edit_action'){
     const receipt=s.receipts[args.original_args.request_id];
     if(!receipt)return good({state:'unknown',business_write_performed:false});
     if(receipt.fingerprint!==JSON.stringify({name:args.original_tool,args:args.original_args}))return bad('REPLAY_MISMATCH','QA andere Aktion',true);
     return good({state:'confirmed',entity_id:s.device.id,result:copy(receipt.result),business_write_performed:false});
    }
    if(!['save_equipment_maintenance_plan','record_equipment_maintenance'].includes(name))return previous(name,raw);
    const fingerprint=JSON.stringify({name,args}),receipt=s.receipts[args.request_id];
    if(receipt)return receipt.fingerprint===fingerprint?good({...copy(receipt.result),replayed:true}):bad('REPLAY_MISMATCH','QA andere Aktion',true);
    if(args.expected_record_hash!==s.device.record_hash)return bad('WRITE_CONFLICT','QA Gerät wurde inzwischen geändert',true);
    if(name==='record_equipment_maintenance'&&s.loseBeforeDone){s.loseBeforeDone=false;return bad('NETWORK_UNAVAILABLE','QA Antwort vor Commit verloren');}
    let plan=s.device.maintenance_plans.find(p=>p.id===args.plan_id);
    if(name==='save_equipment_maintenance_plan'){
     if(args.operation==='configure'){
      if(plan){Object.assign(plan,{title:args.title,interval_value:args.interval_value,interval_unit:args.interval_unit,next_due_on:shift(plan.anchor_on,args.interval_value*(args.interval_unit==='weeks'?7:1))});}else{plan={id:'qa_maintenance_plan',title:args.title,interval_value:args.interval_value,interval_unit:args.interval_unit,anchor_on:args.starts_on,next_due_on:shift(args.starts_on,args.interval_value*(args.interval_unit==='weeks'?7:1))};s.device.maintenance_plans.push(plan);}
     }else if(args.operation==='bring_forward')plan.next_due_on=args.next_due_on;
     else{plan.interval_value=args.interval_value;plan.interval_unit=args.interval_unit;plan.next_due_on=shift(plan.anchor_on,args.interval_value*(args.interval_unit==='weeks'?7:1));}
    }else{
     s.events.push({id:'qa_maintenance_event_'+s.events.length,title:plan.title,performed_on:args.performed_on,reason:args.reason,notes:args.notes});
     if(args.shorter_interval_value){plan.interval_value=args.shorter_interval_value;plan.interval_unit=args.shorter_interval_unit;}
     plan.last_completed_on=args.performed_on;plan.anchor_on=args.performed_on;plan.next_due_on=shift(args.performed_on,plan.interval_value*(plan.interval_unit==='weeks'?7:1));
    }
    s.device.record_hash=(++s.revision).toString(16).padStart(64,'0');
    const result={record:copy(s.device),equipment:copy(s.device),local_saved:true};s.writes.push({name,args:copy(args)});s.receipts[args.request_id]={fingerprint,result:copy(result)};
    if(name==='record_equipment_maintenance'&&s.loseDone){s.loseDone=false;return bad('NETWORK_UNAVAILABLE','QA Commitantwort verloren');}
    return good(result);
   };
  });
  const ready=async()=>page.waitForFunction(()=>document.body.dataset.revierReady==='true');
  const gear=async()=>{await page.evaluate(()=>openPage('revier-equipment'));await page.locator('#revier-equipment [data-revier-detail=equipment]').click();};
  const check=async(name,run)=>{try{await run();results.push({name,result:'PASS'});console.log('PASS '+name);}catch(error){console.error('UI_STATE '+JSON.stringify(await page.evaluate(()=>({currentPage,visible:[...document.querySelectorAll('.page')].filter(n=>getComputedStyle(n).display!=='none').map(n=>n.id),drafts:HGFormDrafts.list().map(d=>({kind:d.kind,pending:!!d.pending})),alert:document.querySelector('#revier-editor [role=alert]')?.textContent,ancestors:(()=>{const out=[];let n=document.querySelector('[data-revier-editor-discard]');while(n){const css=getComputedStyle(n),r=n.getBoundingClientRect();out.push({tag:n.tagName,id:n.id,cls:n.className,display:css.display,visibility:css.visibility,width:r.width,height:r.height});n=n.parentElement;}return out;})()}))));throw error;}};
  const submit=async()=>{await page.locator('#revier-form [type=submit]').click();await page.waitForFunction(()=>currentPage==='revier-equipment-detail');};
  await page.goto(pathToFileURL(path.resolve('android/app/src/main/assets/mobile.html')).href);await ready();await gear();
  await check('Opening, abandoning and discarding a plan never creates a business row or resurrects a discarded draft',async()=>{
   await page.locator('[data-revier-maintenance-new]').click();await page.evaluate(()=>handleAndroidBack());assert.equal(await page.evaluate(()=>HGFormDrafts.list().length),0);
   await page.locator('[data-revier-maintenance-new]').click();await page.locator('#revier-field-title').fill('QA verwerfen');const draft=await page.evaluate(()=>HGFormDrafts.list().find(e=>e.kind==='maintenance_plan'));assert(draft);
   await page.locator('[data-revier-editor-discard]').click();assert.equal(await page.evaluate(()=>HGFormDrafts.list().length),0);
   assert.equal(await page.evaluate(d=>{try{HGFormDrafts.put(d);return true;}catch(_){return false;}},draft),false);
   await page.evaluate(()=>sessionStorage.setItem('qa_gear_state',JSON.stringify(qaGear)));await page.reload();await ready();await gear();assert.equal(await page.evaluate(()=>HGFormDrafts.list().length),0);assert.equal(await page.evaluate(()=>qaGear.writes.length),0);
  });
  await check('Own interval and existing text phrases save offline on the original equipment',async()=>{
   await page.context().setOffline(true);await page.locator('[data-revier-maintenance-new]').click();assert.equal(await page.locator('#revier-maintenance-phrases option').count(),57);
   await page.locator('#revier-field-title').fill('Klinge geschärft.');await page.locator('#revier-field-interval_value').fill('2');await page.locator('#revier-field-interval_unit').selectOption('weeks');await submit();
   assert.equal(await page.evaluate(()=>qaGear.device.maintenance_plans.length),1);assert.equal(await page.evaluate(()=>qaGear.writes[0].args.equipment_id),'qa_maintenance_equipment');assert.equal(await page.evaluate(()=>qaGear.events.length),0);
   assert.equal(await page.evaluate(()=>qaGear.device.condition),'defective');assert.equal(await page.evaluate(()=>HGFormDrafts.list().length),0);await page.locator('.revier-maintenance').scrollIntoViewIfNeeded();await page.screenshot({path:'outputs/qa-equipment-maintenance-360.png',fullPage:false});
  });
  await check('Today done requires explicit save, allows date and reason, and can shorten the next interval',async()=>{
   await page.locator('[data-revier-maintenance-done]').click();assert.equal(await page.evaluate(()=>qaGear.events.length),0);assert.equal(await page.locator('#revier-field-performed_on').inputValue(),await page.evaluate(()=>qaGear.today));
   await page.locator('#revier-form summary').click();await page.locator('[name=shorten_interval]').check();await page.locator('#revier-field-reason').fill('Erneutes Nachschärfen früher nötig');await page.locator('#revier-field-shorter_interval_value').fill('1');await page.locator('#revier-field-shorter_interval_unit').selectOption('weeks');await submit();
   assert.equal(await page.evaluate(()=>qaGear.events.length),1);assert.equal(await page.evaluate(()=>qaGear.writes.at(-1).args.shorter_interval_value),1);assert.equal(await page.evaluate(()=>qaGear.device.condition),'defective');await page.context().setOffline(false);
  });
  await check('One earlier date leaves the standing interval intact; permanent shortening sends different arguments',async()=>{
   await page.locator('[data-revier-maintenance-adjust]').click();const earlier=await page.evaluate(()=>new Date(Date.parse(qaGear.today+'T12:00:00Z')+2*86400000).toISOString().slice(0,10));await page.locator('#revier-field-next_due_on').fill(earlier);await page.locator('#revier-field-reason').fill('Fehlschnitte festgestellt');await submit();
   assert.equal(await page.evaluate(()=>qaGear.device.maintenance_plans[0].interval_unit),'weeks');assert.equal(await page.evaluate(()=>Object.hasOwn(qaGear.writes.at(-1).args,'interval_value')),false);
   await page.locator('[data-revier-maintenance-adjust]').click();await page.locator('#revier-field-operation').selectOption('shorten');assert.equal(await page.locator('#revier-field-next_due_on').count(),0);await page.locator('#revier-field-interval_value').fill('3');await page.locator('#revier-field-interval_unit').selectOption('days');await page.locator('#revier-field-reason').fill('QA stärker beansprucht');await submit();assert.equal(await page.evaluate(()=>qaGear.writes.at(-1).args.operation),'shorten');assert.equal(await page.evaluate(()=>Object.hasOwn(qaGear.writes.at(-1).args,'next_due_on')),false);
  });
  await check('Lost commit reply survives reload and is resolved by read-back with the original ID, without a second write',async()=>{
   const before=await page.evaluate(()=>qaGear.events.length);await page.evaluate(()=>qaGear.loseDone=true);await page.locator('[data-revier-maintenance-done]').click();await page.locator('#revier-form [type=submit]').click();await page.waitForFunction(()=>document.querySelector('#revier-form [role=alert]').textContent.includes('Commitantwort'));
   const pending=await page.evaluate(()=>HGFormDrafts.list().find(e=>e.kind==='maintenance_done').pending);assert.equal(await page.locator('#revier-field-performed_on').isDisabled(),true);assert.equal(await page.evaluate(()=>qaGear.events.length),before+1);
   await page.evaluate(()=>sessionStorage.setItem('qa_gear_state',JSON.stringify(qaGear)));await page.reload();await ready();await page.evaluate(()=>RevierUI.restoreDraft());assert.deepEqual(await page.evaluate(()=>HGFormDrafts.list().find(e=>e.kind==='maintenance_done').pending),pending);
   await page.locator('#revier-form [type=submit]').click();await page.waitForFunction(()=>!HGFormDrafts.list().some(e=>e.kind==='maintenance_done'));
   assert.equal(await page.evaluate(()=>qaGear.events.length),before+1);assert.equal(await page.evaluate(id=>qaGear.writes.filter(w=>w.args.request_id===id).length,pending.args.request_id),1);assert(await page.evaluate(()=>qaGear.calls.some(c=>c.name==='inspect_edit_action')));await gear();
  });
  await check('A post-commit view read failure does not undo a completed maintenance',async()=>{
   const before=await page.evaluate(()=>qaGear.events.length);await page.locator('[data-revier-maintenance-done]').click();await page.evaluate(()=>qaGear.failRead=true);await submit();assert.equal(await page.evaluate(()=>qaGear.events.length),before+1);assert.equal(await page.evaluate(()=>HGFormDrafts.list().length),0);assert(await page.locator('#revier-equipment-detail .revier-maintenance').count()===0);assert(await page.locator('#revier-equipment-detail [role=alert]').allTextContents().then(rows=>rows.some(s=>s.includes('nicht lesbar'))));await page.evaluate(()=>qaGear.failRead=false);await gear();
  });
  await check('Write conflict preserves input and requires explicit comparison before saving against the new hash',async()=>{
   await page.locator('[data-revier-maintenance-adjust]').click();await page.locator('#revier-field-operation').selectOption('shorten');await page.locator('#revier-field-interval_value').fill('1');await page.locator('#revier-field-reason').fill('QA Konfliktgrund');await page.evaluate(()=>qaGear.device.record_hash='f'.repeat(64));const count=await page.evaluate(()=>qaGear.writes.length);
   await page.locator('#revier-form [type=submit]').click();await page.waitForFunction(()=>!!document.querySelector('[data-revier-compare]'));assert.equal(await page.locator('#revier-field-reason').inputValue(),'QA Konfliktgrund');assert.equal(await page.evaluate(()=>qaGear.writes.length),count);
   await page.locator('[data-revier-compare]').click();await page.locator('[data-revier-compare-confirm]').check();await page.locator('[data-revier-compare-accept]').click();assert.equal(await page.evaluate(()=>qaGear.writes.length),count);await submit();assert.equal(await page.evaluate(()=>qaGear.writes.length),count+1);
  });
  await check('Maintenance forms remain usable at narrow widths and enlarged text',async()=>{
   await page.locator('[data-revier-maintenance-done]').click();await page.evaluate(()=>document.documentElement.style.fontSize='200%');for(const width of [320,360,426]){await page.setViewportSize({width,height:900});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));}await page.evaluate(()=>document.documentElement.style.fontSize='16px');await page.locator('[data-revier-editor-discard]').click();assert.deepEqual(errors,[]);
  });
  await check('Editing the existing interval preserves equipment, plan identity and completed history',async()=>{
   const before=await page.evaluate(()=>({id:qaGear.device.maintenance_plans[0].id,events:JSON.stringify(qaGear.events),anchor:qaGear.device.maintenance_plans[0].anchor_on}));await page.locator('[data-revier-maintenance-edit]').click();assert.equal(await page.locator('#revier-field-starts_on').count(),0);await page.locator('#revier-field-interval_value').fill('2');await page.locator('#revier-field-reason').fill('QA Intervall korrigiert');await submit();assert.equal(await page.evaluate(()=>qaGear.device.maintenance_plans.length),1);assert.equal(await page.evaluate(()=>qaGear.device.maintenance_plans[0].id),before.id);assert.equal(await page.evaluate(()=>qaGear.device.maintenance_plans[0].anchor_on),before.anchor);assert.equal(await page.evaluate(()=>JSON.stringify(qaGear.events)),before.events);assert.equal(await page.evaluate(()=>Object.hasOwn(qaGear.writes.at(-1).args,'starts_on')),false);
  });
  await check('An unknown current-session commit is read first, then explicitly retried with the same ID and arguments',async()=>{
   const before=await page.evaluate(()=>qaGear.writes.length);await page.evaluate(()=>qaGear.loseBeforeDone=true);await page.locator('[data-revier-maintenance-done]').click();await page.locator('#revier-form [type=submit]').click();await page.waitForFunction(()=>document.querySelector('#revier-form [role=alert]').textContent.includes('vor Commit'));
   const pending=await page.evaluate(()=>HGFormDrafts.list().find(e=>e.kind==='maintenance_done').pending);await submit();assert.equal(await page.evaluate(()=>qaGear.writes.length),before+1);
   const calls=await page.evaluate(id=>qaGear.calls.filter(c=>c.name==='record_equipment_maintenance'&&c.args.request_id===id),pending.args.request_id);assert.equal(calls.length,2);assert.deepEqual(calls[0].args,calls[1].args);assert.deepEqual(calls[0].args,pending.args);
   const sequence=await page.evaluate(id=>qaGear.calls.filter(c=>c.args.request_id===id||c.args.original_args?.request_id===id).map(c=>c.name),pending.args.request_id);assert.deepEqual(sequence,['record_equipment_maintenance','inspect_edit_action','record_equipment_maintenance']);assert.equal(await page.evaluate(()=>HGFormDrafts.list().length),0);assert.deepEqual(errors,[]);
  });
  await check('An unknown restored action stays read-only and cannot replay automatically',async()=>{
   const before=await page.evaluate(()=>qaGear.writes.length);await page.evaluate(()=>qaGear.loseBeforeDone=true);await page.locator('[data-revier-maintenance-done]').click();await page.locator('#revier-form [type=submit]').click();await page.waitForFunction(()=>document.querySelector('#revier-form [role=alert]').textContent.includes('vor Commit'));
   const pending=await page.evaluate(()=>{const edit=HGFormDrafts.list().find(e=>e.kind==='maintenance_done');edit.restore_review_required=true;HGFormDrafts.put(edit);RevierUI.restoreDraft(edit.session_id);return edit.pending;});await page.locator('#revier-form [type=submit]').click();await page.waitForFunction(()=>document.querySelector('#revier-form [role=alert]').textContent.length>0);assert.equal(await page.evaluate(()=>qaGear.writes.length),before);assert.equal(await page.evaluate(id=>qaGear.calls.filter(c=>c.name==='record_equipment_maintenance'&&c.args.request_id===id).length,pending.args.request_id),1);assert.deepEqual(await page.evaluate(()=>HGFormDrafts.list().find(e=>e.kind==='maintenance_done').pending),pending);
  });
 }finally{await browser.close();fs.mkdirSync('outputs/integration-ui',{recursive:true});fs.writeFileSync('outputs/integration-ui/equipment-maintenance.json',JSON.stringify({scope:'Synthetic browser bridge doubles; real domain rules covered separately in JVM; no native/physical or APK acceptance',results,errors},null,2));}
})().catch(e=>{console.error(e);process.exitCode=1;});

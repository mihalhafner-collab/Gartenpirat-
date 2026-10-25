'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm');
const fixtures=JSON.parse(fs.readFileSync('tests/fixtures/ui-owner-client-before.json','utf8'));
const baselineOnly=process.argv.includes('--baseline-only');let checks=0;
for(const fixture of fixtures){
 const bridge={reply:'',calls:[],ownerCall(name,args){this.calls.push([name,args]);return this.reply;}};
 const context=vm.createContext({window:{},AndroidData:bridge});
 const before=vm.runInContext('('+fixture.before+')',context);
 let after;
 if(!baselineOnly){vm.runInContext(fs.readFileSync('android/app/src/main/assets/owner-client.js','utf8'),context);after=context.window.HGOwnerClient.create(fixture.fallback);}
 const request={request_id:'qa_same_request',expected_record_hash:'a'.repeat(64),updates:{notes:'QA Entwurf'}};
 const values=[{ok:true,data:{id:'qa_response'}},{ok:true,data:null},{ok:false,error:{code:'WRITE_CONFLICT',message:'QA stale hash'}},{ok:false},{ok:false,error:{message:''}},{ok:false,error:{message:'QA permission denied'}}];
 for(const value of values){bridge.reply=JSON.stringify(value);let old,newer,errorOld,errorNew;
  try{old=before('qa_operation',request);}catch(e){errorOld={name:e.name,message:e.message};}
  const expectedError=value.ok?undefined:{name:'Error',message:value.error?.message||fixture.fallback};assert.deepEqual(errorOld,expectedError);checks++;
  const oldTrace=bridge.calls.at(-1);
  if(!baselineOnly){try{newer=after('qa_operation',request);}catch(e){errorNew={name:e.name,message:e.message};}assert.deepEqual(errorNew,errorOld);assert.equal(JSON.stringify(newer),JSON.stringify(old));assert.deepEqual(bridge.calls.at(-1),oldTrace);checks+=3;}
 }
 if(!baselineOnly){bridge.reply='invalid-json';let oldError,newError;try{before('qa_operation');}catch(e){oldError=e.name;}try{after('qa_operation');}catch(e){newError=e.name;}assert.equal(oldError,newError);checks++;
  const state={id:'qa_draft',nested:{value:1}};const cloned=context.window.HGOwnerClient.clone(state);cloned.nested.value=2;assert.equal(state.nested.value,1);checks++;
 }
}
console.log('PASS '+checks+' '+(baselineOnly?'pre-refactoring UI adapter observations':'UI request/error/clone equivalence assertions; request IDs and hashes unchanged'));

// Actual early runtime facade plus existing edit envelopes; native and OS durability
// are tested separately. This port models atomic CAS and immutable owner receipts.
if(!baselineOnly){
 const crypto=require('node:crypto'),runtime=fs.readFileSync('android/app/src/main/assets/runtime-compat.js','utf8'),owner=fs.readFileSync('android/app/src/main/assets/owner-client.js','utf8'),FORM='hausgarten.form.sessions.v1',MIRROR='hausgarten.fullbackup.formdrafts.v1';let boundaryChecks=0;
 const copy=value=>JSON.parse(JSON.stringify(value)),hash=value=>crypto.createHash('sha256').update(JSON.stringify(value)).digest('hex');
 function port(){
  const p={context:{installationId:'00000000-0000-0000-0000-000000000001',restoreEpoch:'00000000-0000-0000-0000-000000000002'},review:false,rows:new Map(),writes:[],reads:[],ownerCalls:[],orders:new Map(),receipts:new Map(),sequence:0,failWrite:false,malformedAck:false,loseOwnerOnce:false};
  p.state=key=>{const row=p.rows.get(key),valid=row&&JSON.stringify(row.context)===JSON.stringify(p.context),value=valid?row.value:null,revision=valid?row.revision:0;return {key,initialized:!!valid,present:!!valid&&value!==null,value,revision,record_hash:hash([key,p.context,revision,value])};};
  p.list=()=>({formatVersion:1,context:copy(p.context),review_required:p.review,states:p.review?[]:[...p.rows.keys()].sort().map(p.state).filter(row=>row.initialized),record_hash:hash([...p.rows.keys()].sort().map(p.state).filter(row=>row.initialized))});
  p.seed=(key,value)=>p.rows.set(key,{value,revision:++p.sequence,context:copy(p.context)});
  const good=data=>JSON.stringify({ok:true,data}),bad=code=>JSON.stringify({ok:false,error:{code,message:'QA isolated draft refusal'}});
  p.bridge={getBrowserDraftState(raw){const args=JSON.parse(raw);p.reads.push(copy(args));if(args.action==='list')return good(p.list());const state=p.review?{key:args.key,initialized:false,present:false,value:null,record_hash:hash(['qa_review',args.key,p.context])}:p.state(args.key);return good({formatVersion:1,context:copy(p.context),review_required:p.review,...state});},writeBrowserDraftState(raw){const args=JSON.parse(raw);p.writes.push(copy(args));if(p.failWrite){p.failWrite=false;return bad('EDITOR_STORAGE_SAVE_FAILED');}if(p.review)return bad('EDITOR_STORAGE_RESTORE_REVIEW_REQUIRED');if(JSON.stringify(args.context)!==JSON.stringify(p.context))return bad('EDITOR_STORAGE_CONTEXT_CHANGED');if(args.mutations.some(row=>row.expected_record_hash!==p.state(row.key).record_hash))return bad('EDITOR_STORAGE_CONFLICT');for(const row of args.mutations)p.seed(row.key,row.value);const result={...p.list(),states:args.mutations.map(row=>p.state(row.key)),replayed:false};if(p.malformedAck){p.malformedAck=false;result.states=[];}return good(result);},saveOrder(raw){const args=JSON.parse(raw);p.ownerCalls.push(raw);let receipt=p.receipts.get(args._request_id);if(receipt){assert.equal(receipt.raw,raw);return receipt.id;}const id='qa_order_'+(p.orders.size+1);p.orders.set(id,copy(args));p.receipts.set(args._request_id,{raw,id});if(p.loseOwnerOnce){p.loseOwnerOnce=false;throw Error('QA owner response lost after commit');}return id;}};return p;
 }
 function rawStore(){const values=new Map();return {values,failSet:false,getItem(key){return values.has(String(key))?values.get(String(key)):null;},setItem(key,value){if(this.failSet)throw Error('QA cache quota');values.set(String(key),String(value));},removeItem(key){values.delete(String(key));}};}
 function boot(p,storage=rawStore(),native=true){const bridge={...p.bridge};if(!native){delete bridge.getBrowserDraftState;delete bridge.writeBrowserDraftState;}const sandbox={crypto:crypto.webcrypto,localStorage:storage,AndroidData:bridge};sandbox.window=sandbox;const context=vm.createContext(sandbox);vm.runInContext(runtime,context);vm.runInContext(owner,context);return {context,D:context.HGDraftStorage,HG:context.HGFormDrafts,storage,p};}
 function checkBoundary(name,run){run();boundaryChecks++;console.log('PASS draft boundary '+name);}
 checkBoundary('native pending survives stale LS cache and view restart; original action creates one order and keeps independent draft',()=>{
  const p=port(),storage=rawStore(),a=boot(p,storage),first=a.HG.start('order',{},{}),other=a.HG.start('order',{},{});first.values={title:'qa_first_order'};other.values={title:'qa_independent_order'};a.HG.put(first);a.HG.put(other);const stale=storage.getItem(FORM);p.loseOwnerOnce=true;assert.throws(()=>a.HG.nativeSave(first,'saveOrder',{title:'qa_first_order'}),/QA owner response lost/);const frozen=copy(JSON.parse(p.state(FORM).value).entries[first.session_id].pending);storage.setItem(FORM,stale);const b=boot(p,storage),restored=b.HG.list('order'),pending=restored.find(row=>row.session_id===first.session_id);assert.deepEqual(copy(pending.pending),frozen);assert.equal(restored.find(row=>row.session_id===other.session_id).values.title,'qa_independent_order');assert.equal(b.HG.nativeSave(pending,'saveOrder',{title:'qa_must_not_replace_frozen'}),'qa_order_1');assert.equal(p.orders.size,1);assert.equal(p.ownerCalls.length,2);assert.equal(p.ownerCalls[0],p.ownerCalls[1]);assert.equal(b.HG.list('order').find(row=>row.session_id===other.session_id).values.title,'qa_independent_order');
 });
 checkBoundary('native prewrite failure prevents every business call and preserves prior native/cache draft',()=>{
  const p=port(),a=boot(p),edit=a.HG.start('order',{},{});edit.values={title:'qa_preserved'};a.HG.put(edit);const nativeBefore=p.state(FORM).value,cacheBefore=a.storage.getItem(FORM);p.failWrite=true;assert.throws(()=>a.HG.nativeSave(edit,'saveOrder',{title:'qa_preserved'}),error=>error.code==='EDITOR_STORAGE_SAVE_FAILED');assert.equal(p.ownerCalls.length,0);assert.equal(p.state(FORM).value,nativeBefore);assert.equal(a.storage.getItem(FORM),cacheBefore);
 });
 checkBoundary('explicit read CAS is not silently rebased by backup snapshot or list',()=>{
  const p=port();p.seed(FORM,'qa_original');const a=boot(p);assert.equal(a.D.getItem(FORM),'qa_original');p.seed(FORM,'qa_changed_elsewhere');const snapshot=a.D.snapshot({[FORM]:'qa_stale_cache',qa_other:'qa_keep'});assert.equal(snapshot.storage[FORM],'qa_changed_elsewhere');assert.equal(snapshot.editor_state.record_hash,p.list().record_hash);assert.throws(()=>a.D.setItem(FORM,'qa_old_editor'),error=>error.code==='EDITOR_STORAGE_CONFLICT');assert.equal(p.state(FORM).value,'qa_changed_elsewhere');
 });
 checkBoundary('initialized values and tombstones require an explicit read before first write',()=>{
  const p=port();p.seed(FORM,'qa_existing');p.seed(MIRROR,null);const a=boot(p),before=p.writes.length;for(let i=0;i<2;i++)assert.throws(()=>a.D.setItem(FORM,'qa_blind'),error=>error.code==='EDITOR_STORAGE_READ_REQUIRED');assert.throws(()=>a.D.setItem(MIRROR,'qa_resurrect'),error=>error.code==='EDITOR_STORAGE_READ_REQUIRED');assert.equal(p.writes.length,before);a.D.getItem(FORM);a.D.setItem(FORM,'qa_explicit');assert.equal(p.state(FORM).value,'qa_explicit');
 });
 checkBoundary('native tombstone defeats stale LS after restart and is removed from backup overlay',()=>{
  const p=port(),storage=rawStore();p.seed(FORM,'qa_old');const a=boot(p,storage);assert.equal(a.D.getItem(FORM),'qa_old');a.D.removeItem(FORM);storage.setItem(FORM,'qa_stale_after_delete');const b=boot(p,storage),writes=p.writes.length;assert.equal(b.D.getItem(FORM),null);assert.equal(storage.getItem(FORM),null);const captured=b.D.snapshot({[FORM]:'qa_stale_again'});assert.equal(Object.hasOwn(captured.storage,FORM),false);assert.equal(p.writes.length,writes);assert.equal(p.state(FORM).initialized,true);assert.equal(p.state(FORM).present,false);
 });
 checkBoundary('existing envelope discard and mirror cleanup are one native atomic batch',()=>{
  const p=port(),a=boot(p),edit=a.HG.start('order',{},{});edit.values={title:'qa_discard_only_this'};a.HG.put(edit);a.D.getItem(MIRROR);a.D.setItem(MIRROR,JSON.stringify({formatVersion:1,pages:{orders:{forms:[{session_id:edit.session_id,key:'qa_order_form',fields:[]}]}}}));const before=p.writes.length;a.HG.remove(edit,{discard:true,formId:'qa_order_form',page:'orders'});assert.equal(p.writes.length,before+1);assert.deepEqual(p.writes.at(-1).mutations.map(row=>row.key).sort(),[FORM,MIRROR].sort());const state=JSON.parse(p.state(FORM).value);assert.equal(state.entries[edit.session_id],undefined);assert.equal(state.removed[edit.session_id].discarded,true);assert.equal(JSON.parse(p.state(MIRROR).value).pages.orders,undefined);
 });
 checkBoundary('legacy protected draft imports once and native ACK survives later LS quota failure',()=>{
  const p=port(),storage=rawStore();storage.setItem(FORM,'qa_legacy');const a=boot(p,storage);assert.equal(a.D.getItem(FORM),'qa_legacy');assert.equal(p.writes.length,1);storage.failSet=true;a.D.setItem(FORM,'qa_native_primary');assert.equal(p.state(FORM).value,'qa_native_primary');assert.equal(a.D.getItem(FORM),'qa_native_primary');const b=boot(p,storage);assert.equal(b.D.getItem(FORM),'qa_native_primary');assert.equal(p.writes.length,2);
 });
 checkBoundary('malformed write ACK never starts a business action and native committed pending remains recoverable',()=>{
  const p=port(),a=boot(p),edit=a.HG.start('order',{},{});edit.values={title:'qa_ack_unknown'};a.HG.put(edit);const oldCache=a.storage.getItem(FORM);p.malformedAck=true;assert.throws(()=>a.HG.nativeSave(edit,'saveOrder',{title:'qa_ack_unknown'}),error=>error.code==='BROWSER_DRAFT_RESPONSE_INVALID');assert.equal(p.ownerCalls.length,0);assert.equal(a.storage.getItem(FORM),oldCache);const b=boot(p,a.storage);assert.equal(b.HG.list('order').find(row=>row.session_id===edit.session_id).pending.tool,'saveOrder');
 });
 checkBoundary('restore context refuses stale writes and review exposes raw review source without import or mutation',()=>{
  const p=port(),storage=rawStore();p.seed(FORM,'qa_native_before_restore');const a=boot(p,storage);a.D.getItem(FORM);p.context.restoreEpoch='00000000-0000-0000-0000-000000000003';assert.throws(()=>a.D.setItem(FORM,'qa_old_context'),error=>error.code==='EDITOR_STORAGE_CONTEXT_CHANGED');p.review=true;storage.setItem(FORM,'qa_raw_review_source');const writes=p.writes.length;assert.equal(a.D.getItem(FORM),'qa_raw_review_source');assert.throws(()=>a.D.setItem(FORM,'qa_not_approved'),error=>/RESTORE_REVIEW_REQUIRED/.test(error.code));assert.equal(a.D.snapshot({[FORM]:'qa_raw_review_source'}).editor_state.review_required,true);assert.equal(p.writes.length,writes);assert.equal(p.state(FORM).initialized,false);
 });
 checkBoundary('transactions reject async and mixed storage before any native mutation; ordinary LS fallback remains available',()=>{
  const p=port(),a=boot(p),before=p.writes.length;assert.throws(()=>a.D.transaction(async()=>a.D.setItem(FORM,'qa_async')),error=>/ASYNC_TRANSACTION/.test(error.code));assert.throws(()=>a.D.transaction(()=>a.D.transaction(()=>Promise.resolve('qa_async'))),error=>/ASYNC_TRANSACTION/.test(error.code));a.D.getItem(FORM);assert.throws(()=>a.D.transaction(()=>{a.D.setItem(FORM,'qa_mixed');a.D.setItem('qa_unprotected','qa_value');}),error=>/MIXED_TRANSACTION/.test(error.code));assert.equal(p.writes.length,before);a.D.setItem('qa_unprotected','qa_value');assert.equal(a.storage.getItem('qa_unprotected'),'qa_value');const legacy=boot(port(),rawStore(),false),method=legacy.storage.setItem;legacy.D.setItem(FORM,'qa_legacy_browser');assert.equal(legacy.D.getItem(FORM),'qa_legacy_browser');assert.equal(legacy.storage.setItem,method);
 });
 console.log('PASS '+boundaryChecks+' native draft boundary scenarios; isolated synchronous port, no SQLite/device claim');
}

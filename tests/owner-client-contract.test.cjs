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

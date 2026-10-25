/* Stateless native-owner adapter. Drafts, versions and request IDs belong to each workflow. */
(()=>{
 'use strict';
 function create(fallback){
  return function call(name,args={}){
   const response=JSON.parse(AndroidData.ownerCall(name,JSON.stringify(args)));
   if(!response.ok){const error=new Error(response.error?.message||fallback);error.code=response.error?.code||'';error.field=response.error?.details?.field||response.error?.field||'';error.rejected=/^(WRITE_CONFLICT|INVALID_INPUT|INVALID_PARAMS|REFERENCE_NOT_FOUND|RELATION_MISMATCH|DOCUMENT_INVALID|VALIDATION_FAILED|OFFER_|PRICE_|CUSTOMER_|ORDER_)/.test(error.code);throw error;}
   return response.data;
  };
 }
 const clone=value=>JSON.parse(JSON.stringify(value));
 window.HGOwnerClient=Object.freeze({create,clone});
 // One durable edit envelope per workflow. No business writes or DOM event handlers here.
 const key='hausgarten.form.sessions.v1',mirror='hausgarten.fullbackup.formdrafts.v1';
 const canonical=value=>JSON.stringify(value,(_,v)=>v&&typeof v==='object'&&!Array.isArray(v)?Object.fromEntries(Object.keys(v).sort().map(k=>[k,v[k]])):v);
 function read(){const value=JSON.parse(localStorage.getItem(key)||'{"formatVersion":1,"entries":{},"removed":{}}');if(value.formatVersion!==1||!value.entries||!value.removed)throw Error('FORM_DRAFT_UNREADABLE');return value;}
 function write(value){const raw=JSON.stringify(value);localStorage.setItem(key,raw);if(localStorage.getItem(key)!==raw)throw Error('FORM_DRAFT_NOT_SAVED');}
 function start(kind,baseline,context={}){return {formatVersion:1,session_id:'edit_'+crypto.randomUUID(),kind,baseline:clone(baseline),values:clone(baseline),context:clone(context),dirty:false};}
 function put(edit){const all=read();if(all.removed[edit.session_id])throw Error('FORM_DRAFT_DISCARDED');edit.dirty=canonical(edit.values)!==canonical(edit.baseline);if(edit.dirty||edit.pending)all.entries[edit.session_id]=clone(edit);else {delete all.entries[edit.session_id];const raw=localStorage.getItem(mirror);if(raw){const m=JSON.parse(raw);for(const [id,page]of Object.entries(m.pages||{})){page.forms=(page.forms||[]).filter(f=>f.session_id!==edit.session_id);if(!page.forms.length)delete m.pages[id];}localStorage.setItem(mirror,JSON.stringify(m));}}write(all);return edit;}
 function list(kind){return Object.values(read().entries).filter(edit=>!kind||edit.kind===kind).map(clone);}
 function remove(edit,{discard=false,formId='',page=''}={}){if(edit.pending&&(discard||!edit.confirmed))throw Error('FORM_ACTION_UNRESOLVED');const all=read();delete all.entries[edit.session_id];const mediaKey=edit.context?.media_key||edit.values?.media_key;all.removed[edit.session_id]={discarded:discard,...(discard&&mediaKey?{media_key:mediaKey,cleanup_pending:true}:{})};write(all);if(formId||page){const raw=localStorage.getItem(mirror);if(raw){const m=JSON.parse(raw);for(const [id,row]of Object.entries(m.pages||{})){if(page&&id!==page)continue;row.forms=(row.forms||[]).filter(f=>f.session_id?f.session_id!==edit.session_id:f.key!==formId);if(!row.forms.length)delete m.pages[id];}localStorage.setItem(mirror,JSON.stringify(m));}}if(discard)cleanup().catch(()=>{});}
 function freeze(edit,tool,args){if(!edit.pending){edit.pending={tool,args:clone(args)};put(edit);}else put(edit);return clone(edit.pending);}
 function confirmed(edit,result){const candidate={...edit,pending:null,confirmed:clone(result)};try{put(candidate);edit.pending=null;edit.confirmed=clone(result);return true;}catch(_){edit.confirmed=clone(result);return false;}}
 function nativeSave(edit,method,payload){if(edit.confirmed?.id)return edit.confirmed.id;const action=freeze(edit,method,{...clone(payload),_request_id:'edit_save_'+crypto.randomUUID()});let id;
  try{id=String(AndroidData[action.tool](JSON.stringify(action.args)));}catch(error){throw error;}
  if(id.startsWith('ERROR:')){edit.pending=null;put(edit);const error=new Error(id.replace(/^ERROR:\s*/,''));error.rejected=true;throw error;}
  if(!/^[A-Za-z0-9_-]{1,200}$/.test(id))throw Error('FORM_COMMIT_RESULT_UNKNOWN');
  confirmed(edit,{id});return id;
 }
 function removed(id){return !!read().removed[id];}
 function mediaDiscarded(mediaKey){const all=read();if(Object.values(all.entries).some(e=>(e.context?.media_key||e.values?.media_key)===mediaKey))return false;return Object.values(all.removed).some(e=>e.discarded&&e.media_key===mediaKey);}
 async function cleanup(){if(!window.GartenpiratPhotoStore)return;const all=read();for(const [id,entry]of Object.entries(all.removed)){if(!entry.cleanup_pending||!mediaDiscarded(entry.media_key))continue;await GartenpiratPhotoStore.customerDraft(entry.media_key,null);const latest=read();if(latest.removed[id]){latest.removed[id].cleanup_pending=false;write(latest);}}}
 function fields(form){return [...form.querySelectorAll('input,textarea,select')].filter(e=>!['file','button','submit','password'].includes(e.type)).map((e,i)=>({id:e.id,name:e.name,ordinal:i,type:e.type,value:e.value,...(['checkbox','radio'].includes(e.type)?{checked:e.checked}:{}),...(e.multiple?{selected:[...e.selectedOptions].map(o=>o.value)}:{})}));}
 function applyFields(form,values){const current=[...form.querySelectorAll('input,textarea,select')].filter(e=>!['file','button','submit','password'].includes(e.type));for(const value of values){const e=current[value.ordinal];if(!e||e.id!==value.id||e.name!==value.name||e.type!==value.type)throw Error('FORM_SHAPE_CHANGED');if(value.selected)for(const option of e.options)option.selected=value.selected.includes(option.value);else e.value=value.value;if('checked'in value)e.checked=value.checked;}}
 window.HGFormDrafts=Object.freeze({start,put,list,remove,freeze,confirmed,nativeSave,removed,mediaDiscarded,cleanup,fields,applyFields,canonical,clone,key});
})();

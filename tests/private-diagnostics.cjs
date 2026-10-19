'use strict';
// Only explicitly authorized manual diagnostics may touch an owner device.
// Automated suites use synthetic-fixtures.cjs and never import this adapter.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');
const {privateRoot,businessPath}=require('./runtime-paths.cjs');
function requireManualContext(){
 if(process.env.HG_TEST_DEVICE_CONFIRMED!=='1')throw Error('Explicit manual device consent required.');
 return privateRoot();
}
function destination(original){
 const root=requireManualContext(),folder=businessPath(path.join(root,'diagnostics'));
 fs.mkdirSync(folder,{recursive:true,mode:0o700});
 const ext=path.extname(String(original)).toLowerCase();
 return businessPath(path.join(folder,'diagnostic-'+crypto.createHash('sha256').update(String(original)).digest('hex')+ext));
}
function technical(value){
 const text=JSON.stringify(value),out={format:'hausgarten.technical_report.v1',report_sha256:crypto.createHash('sha256').update(text).digest('hex')};
 for(const key of ['success','updated','readOnly','version_code','pending_count','passed','failed']){
  if(typeof value?.[key]==='boolean'||typeof value?.[key]==='number')out[key]=value[key];
 }
 if(value?.counts&&typeof value.counts==='object')out.counts=Object.fromEntries(Object.entries(value.counts).filter(([k,v])=>/^[a-z_]+$/.test(k)&&typeof v==='number'));
 return out;
}
function writeFile(original,data,options){
 const target=destination(original);
 if(path.extname(target)==='.json')data=JSON.stringify(technical(JSON.parse(String(data))),null,2);
 fs.writeFileSync(target,data,typeof options==='string'?{encoding:options,mode:0o600}:{...options,mode:0o600});
 return target;
}
function log(value){
 try{console.log(JSON.stringify(technical(typeof value==='string'?JSON.parse(value):value)));}
 catch{console.log(JSON.stringify({diagnostic_sha256:crypto.createHash('sha256').update(String(value)).digest('hex')}));}
}
module.exports={requireManualContext,destination,writeFile,log,technical};

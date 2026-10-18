/* Private device-only media. Deliberately separate from business state and Drive. */
(() => {
 'use strict';
 let opening;
 function database(){
  if(!opening)opening=new Promise((resolve,reject)=>{
   if(!window.indexedDB){reject(new Error('Auf diesem Gerät ist der Bildspeicher nicht verfügbar.'));return;}
   const request=indexedDB.open('hausgarten-gartenpirat-media',1);
   request.onupgradeneeded=()=>request.result.createObjectStore('photo');
   request.onerror=()=>reject(request.error);
   request.onblocked=()=>reject(new Error('Bitte andere geöffnete App-Fenster schließen und erneut versuchen.'));
   request.onsuccess=()=>{const db=request.result;db.onversionchange=()=>{db.close();opening=null;};resolve(db);};
  }).catch(error=>{opening=null;throw error;});
  return opening;
 }
 async function get(){
  const db=await database();return new Promise((resolve,reject)=>{
   const tx=db.transaction('photo','readonly'),request=tx.objectStore('photo').get('selected');
   let value=null;request.onsuccess=()=>{value=request.result||null;};
   tx.oncomplete=()=>resolve(value);tx.onabort=tx.onerror=()=>reject(tx.error||new Error('Bild konnte nicht gelesen werden.'));
  });
 }
 async function put(file,autoShow){
  // Materialize cloud/file-picker bytes now; no temporary provider permission is needed later.
  const bytes=await file.arrayBuffer();
  const image=new Blob([bytes],{type:file.type});
  const record={image,name:file.name||'Mein Foto',autoShow:!!autoShow,savedAt:new Date().toISOString()};
  const db=await database();
  await new Promise((resolve,reject)=>{
   const tx=db.transaction('photo','readwrite');tx.objectStore('photo').put(record,'selected');
   tx.oncomplete=resolve;tx.onabort=tx.onerror=()=>reject(tx.error||new Error('Bild konnte nicht gespeichert werden.'));
  });
  return record;
 }
 async function customerDraft(customerId,value){
  const db=await database(),key='customer-draft:'+customerId;
  return new Promise((resolve,reject)=>{const tx=db.transaction('photo',value===undefined?'readonly':'readwrite'),store=tx.objectStore('photo');let found=null;
   if(value===undefined){const r=store.get(key);r.onsuccess=()=>found=r.result||null;}else if(value===null)store.delete(key);else store.put(value,key);
   tx.oncomplete=()=>resolve(value===undefined?found:value);tx.onabort=tx.onerror=()=>reject(tx.error||new Error('Fotoentwurf konnte nicht dauerhaft gesichert werden.'));
  });
 }
 window.GartenpiratPhotoStore=Object.freeze({get,put,customerDraft});
})();

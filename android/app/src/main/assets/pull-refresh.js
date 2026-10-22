/* One vertical pull at the top of a list. Never refresh an open editor. */
(() => {
 'use strict';if(window.__pixelFirstPullRefresh)return;window.__pixelFirstPullRefresh=true;
 const indicator=document.createElement('div');indicator.id='pull-refresh';indicator.className='pull-refresh';indicator.setAttribute('role','status');indicator.setAttribute('aria-live','polite');
 indicator.innerHTML='<span class="pull-refresh-icon">↻</span><span class="pull-refresh-text">Zum Aktualisieren ziehen</span>';document.body.appendChild(indicator);
 const label=indicator.querySelector('.pull-refresh-text');
 let gesture=null,busy=false,requested=false,hideTimer=0,watchdog=0;
 const blocked=target=>!!target.closest('input,textarea,select,button,a,dialog,nav,aside,.direct-edit-content,[data-swipe-item]');
 const scrolledAncestor=target=>{for(let node=target;node&&node!==document.body;node=node.parentElement){if(node.scrollTop>0&&node.scrollHeight>node.clientHeight+1&&/auto|scroll/.test(getComputedStyle(node).overflowY))return true;}return false;};
 const safe=()=>!document.querySelector('dialog[open]')&&typeof currentPage!=='undefined'&&['home','customers','orders','worklogs','weeks','invoices','photos','sync','settings'].includes(currentPage);
 function show(text,mode=''){clearTimeout(hideTimer);label.textContent=text;indicator.className='pull-refresh visible'+(mode?' '+mode:'');}
 function hide(delay=0){clearTimeout(hideTimer);hideTimer=setTimeout(()=>{if(!requested)indicator.className='pull-refresh';},delay);}
 function cancel(){gesture=null;indicator.style.removeProperty('transform');if(!requested)hide();}
 function finish(result){
  const visible=requested;requested=false;busy=!!result?.sync?.syncing;clearTimeout(watchdog);
  if(!visible)return;
  if(result?.cancelled)show('Ordnerauswahl abgebrochen · lokal gespeicherte Daten bleiben erhalten');
  else if(result?.ok)show(result.confirmation_scope==='provider_readback'?'Drive-App aktualisiert · Cloud-Upload separat':'Abgleich abgeschlossen','ready');
  else if(result?.journal_errors?.length)show('Abgleich beendet · offene Änderungen bitte prüfen');
  else show('Aktualisierung nicht abgeschlossen · lokale Daten bleiben erhalten');
  hide(result?.ok?2200:3600);
 }
 document.addEventListener('touchstart',event=>{
  cancel();
  if(busy&&!requested){try{busy=!!JSON.parse(AndroidData.getSyncStatus()).syncing;}catch{}}
  if(busy||window.scrollY>0||event.touches.length!==1||blocked(event.target)||scrolledAncestor(event.target)||!safe())return;
  const touch=event.touches[0];gesture={id:touch.identifier,x:touch.clientX,y:touch.clientY,distance:0,target:event.target};
 },{passive:true});
 document.addEventListener('touchmove',event=>{
  if(!gesture)return;
  const touch=event.touches[0];
  if(event.touches.length!==1||touch.identifier!==gesture.id||window.scrollY>0||scrolledAncestor(gesture.target)||!safe()){cancel();return;}
  const dx=touch.clientX-gesture.x,dy=touch.clientY-gesture.y;
  if(dy < -8||(Math.abs(dx)>12&&Math.abs(dx)>Math.abs(dy))){cancel();return;}
  gesture.distance=Math.max(0,Math.min(120,dy));
  if(gesture.distance<8)return;
  if(Math.abs(dy)<Math.abs(dx)*1.3){cancel();return;}
  const ready=gesture.distance>=72;
  indicator.style.transform='translate(-50%,'+Math.min(8,-80+gesture.distance)+'px)';
  show(ready?'Loslassen zum Aktualisieren':'Zum Aktualisieren ziehen',ready?'ready':'');
 },{passive:true});
 document.addEventListener('touchcancel',cancel,{passive:true});
 document.addEventListener('touchend',event=>{
  if(!gesture)return;
  const distance=gesture.distance;cancel();
  if(event.touches.length||distance<72||busy||!safe())return;
  requested=true;busy=true;show('Daten werden aktualisiert …','syncing');
  try{AndroidData.synchronize();}
  catch{finish({ok:false});return;}
  watchdog=setTimeout(()=>{
   if(!requested)return;
   try{const status=JSON.parse(AndroidData.getSyncStatus());if(!status.syncing){finish({ok:false,cancelled:!status.configured,sync:status});return;}}catch{}
   show('Der Abgleich dauert länger. Du kannst weiterarbeiten.','syncing');
  },90000);
 },{passive:true});
 const started=window.onDriveSyncStarted,finished=window.onDriveSync;
 window.onDriveSyncStarted=function(status){busy=true;cancel();if(typeof started==='function')started.apply(this,arguments);if(requested)show('Daten werden aktualisiert …','syncing');};
 window.onDriveSync=function(result){try{if(typeof finished==='function')finished.apply(this,arguments);}finally{finish(result);}};
})();

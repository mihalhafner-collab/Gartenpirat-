/* One gesture owner for existing business actions. Never invents IDs or cascades. */
(()=>{
 'use strict';
 const groups={customer:'customers',order:'orders',worklog:'worklogs',photo:'photos',invoice:'invoices',offer:'offers',service_template:'service_catalog',appointment:'appointments'};
 const labels={customer:'Kunde',order:'Auftrag',worklog:'Arbeit',photo:'Foto',invoice:'Rechnung',offer:'Angebot',service_template:'Baustein',appointment:'Termin'};
 const selectors=[['[data-record-type][data-record-id]',null,null],['[data-action-photo]','photo','actionPhoto'],['[data-design-action="photo"][data-id]','photo','id'],['[data-edit-worklog]','worklog','editWorklog'],['[data-edit-weeklog]','worklog','editWeeklog'],['[data-edit-order]','order','editOrder'],['[data-invoice]','invoice','invoice'],['[data-customer-open]','customer','customerOpen'],['[data-offer-open]','offer','offerOpen'],['[data-catalog-edit]','service_template','catalogEdit'],['[data-appointment-open]','appointment','appointmentOpen']];
 let timer=0,start=null,suppressed=null;const pointers=new Set();
 const call=window.HGOwnerClient.create('Aktion nicht verfügbar.');
 function reference(target){
  if(!target?.closest||target.closest('input,textarea,select,[contenteditable="true"],[data-call],[data-maps]'))return null;
  const trash=target.closest('[data-trash-record]');if(trash){const key=trash.dataset.trashRecord,i=key.indexOf(':');return{type:key.slice(0,i),id:key.slice(i+1),trash:true,hash:trash.dataset.trashHash,node:trash};}
  const candidates=[];for(const [selector,type,key]of selectors){const node=target.closest(selector);if(node){const ref={type:type||node.dataset.recordType,id:key?node.dataset[key]:node.dataset.recordId,node};if(groups[ref.type]&&ref.id)candidates.push(ref);}}
  return candidates.find(ref=>!candidates.some(other=>other!==ref&&ref.node.contains(other.node)&&ref.node!==other.node))||null;
 }
 function rowFor(ref){return merged(groups[ref.type],ref.type).find(row=>String(row.id)===String(ref.id));}
 function close(){document.getElementById('record-action-dialog')?.close();}
 function edit(ref){
  close();if(ref.type==='offer')return OfferUI.open('',ref.id);
  if(ref.type==='service_template')return OfferUI.editTemplate(ref.id);
  if(ref.type==='appointment')return PlanningUI.edit(ref.id);
 }
 function refresh(ref){if(ref.type==='offer'||ref.type==='service_template')OfferUI.refresh?.();if(ref.type==='appointment')PlanningUI.refresh?.();if(ref.type==='photo')PhotoLibraryUI.refresh?.();}
 function preview(ref){try{return call(ref.trash?'preview_purge':'preview_delete',{entity:ref.type,entity_id:ref.id});}catch(_){return null;}}
 function show(ref){
  if(!ref||!groups[ref.type])return false;
  const row=ref.trash?null:rowFor(ref);if(!ref.trash&&!row)return false;
  if(!ref.trash&&['customer','order','worklog','invoice','photo'].includes(ref.type)){
   window.showRecordActionSheet?.(ref);
   const dialog=document.getElementById('record-action-dialog'),guard=preview(ref);
   if(ref.type==='invoice'&&dialog?.querySelector('[data-record-edit]')?.disabled){const open=dialog.querySelector('[data-record-edit]');open.removeAttribute('data-record-edit');open.dataset.contextView='';open.disabled=false;open.textContent='Ansehen';open.onclick=()=>{close();window.openInvoiceRecord?.(ref.id);};}
   if(guard?.allowed===false&&dialog){dialog.querySelector('[data-record-delete]').disabled=true;let note=dialog.querySelector('.record-action-hint');if(note)note.textContent=guard.message||'Wichtiger Beleg oder verknüpfte Daten bleiben geschützt.';}
   return true;
  }
  let dialog=document.getElementById('record-action-dialog');if(!dialog){dialog=document.createElement('dialog');dialog.id='record-action-dialog';dialog.className='worklog-preview-dialog';document.body.append(dialog);}
  const guard=preview(ref),title=row?.title||row?.name||ref.node?.querySelector('strong')?.textContent||labels[ref.type],locked=guard?.allowed===false;
  dialog.dataset.actionType=ref.type;dialog.dataset.actionId=ref.id;
  dialog.innerHTML='<header class="worklog-preview-head"><div><small>'+esc(labels[ref.type])+(ref.trash?' · Papierkorb':'')+'</small><h2>'+esc(title)+'</h2></div><button type="button" class="worklog-preview-close" data-context-close aria-label="Schließen">×</button></header><p class="record-action-hint">'+esc(locked?guard.message||'Verknüpfte Daten bleiben geschützt.':ref.trash?'Endgültiges Löschen wird erneut geprüft und vorher gesichert.':ref.type==='service_template'?'Bestehende Dokumente und ihre Preise bleiben erhalten.':'Entfernen verschiebt diesen Eintrag zunächst in den Papierkorb.')+'</p><div class="worklog-preview-actions"><button type="button" class="secondary" data-context-close>Schließen</button><button type="button" class="secondary" data-context-edit>'+ (ref.trash?'Wiederherstellen':ref.type==='offer'&&!['draft','validated'].includes(row.status)?'Ansehen':'Bearbeiten')+'</button><button type="button" class="danger-button" data-context-delete '+(locked?'disabled':'')+'>'+ (ref.trash?'Endgültig löschen':'In den Papierkorb')+'</button></div>';
  dialog.querySelectorAll('[data-context-close]').forEach(button=>button.onclick=close);
  dialog.querySelector('[data-context-edit]').onclick=()=>{if(ref.trash){if(TrashUI.restore(ref.type,ref.id,ref.hash)){close();TrashUI.open();}}else edit(ref);};
  dialog.querySelector('[data-context-delete]').onclick=()=>{
   if(ref.trash){if(TrashUI.purge(ref.type,ref.id,ref.hash)){close();TrashUI.open();}return;}
   if(!confirm(labels[ref.type]+' in den Papierkorb verschieben? Verknüpfungen und wichtige Belege werden geprüft.'))return;
   if(TrashUI.remove(ref.type,ref.id,row)){close();refresh(ref);}
  };
  if(!dialog.open)dialog.showModal();dialog.querySelector('[data-context-close]').focus({preventScroll:true});return true;
 }
 function cancel(){clearTimeout(timer);timer=0;start=null;}
 document.addEventListener('pointerdown',event=>{
  pointers.add(event.pointerId);cancel();if(pointers.size>1||event.isPrimary===false||event.button>0)return;
  const ref=reference(event.target);if(!ref||event.target.closest('.entity-context-open,.design-record-menu,[data-record-edit],[data-record-delete],[data-trash-restore],[data-trash-purge]'))return;
  start={x:event.clientX,y:event.clientY,ref};timer=setTimeout(()=>{const current=start;if(!current)return;cancel();suppressed={type:ref.type,id:ref.id,until:Date.now()+1400};show(ref);},560);
 },{passive:true});
 document.addEventListener('pointermove',event=>{if(start&&Math.hypot(event.clientX-start.x,event.clientY-start.y)>10)cancel();},{passive:true});
 for(const name of ['pointerup','pointercancel'])document.addEventListener(name,event=>{pointers.delete(event.pointerId);cancel();},{passive:true});
 document.addEventListener('scroll',cancel,{passive:true,capture:true});window.addEventListener('blur',()=>{pointers.clear();cancel();});
 document.addEventListener('contextmenu',event=>{const ref=reference(event.target);if(!ref)return;event.preventDefault();event.stopImmediatePropagation();cancel();suppressed={type:ref.type,id:ref.id,until:Date.now()+1400};show(ref);},true);
 document.addEventListener('click',event=>{const ref=reference(event.target);if(ref&&suppressed&&Date.now()<suppressed.until&&ref.type===suppressed.type&&ref.id===suppressed.id){event.preventDefault();event.stopImmediatePropagation();suppressed=null;}},true);
 document.addEventListener('keydown',event=>{if(event.key==='ContextMenu'||event.shiftKey&&event.key==='F10'){const ref=reference(event.target);if(ref){event.preventDefault();cancel();show(ref);}}});
 function decorate(){
  const selector=selectors.map(row=>row[0]).join(',');
  for(const node of document.querySelectorAll(selector)){
   if(node.closest('#record-action-dialog')||node.matches('img')||node.closest('.entity-menu-row')||node.closest('.design-record-row,.customer-card')?.querySelector('.design-record-menu'))continue;
   const ref=reference(node);if(!ref||ref.node!==node||!node.parentElement||node.tagName==='DIALOG')continue;
   // Keep existing payment controls beside their record. Their refresh relies
   // on the original direct parent and must not create a second payment toggle.
   const content=node.parentElement.classList.contains('personal-payment-record')?node.parentElement:node;
   const wrapper=document.createElement('div');wrapper.className='entity-menu-row';content.before(wrapper);wrapper.append(content);
   const button=document.createElement('button');button.type='button';button.className='entity-context-open secondary';button.textContent='⋮';button.setAttribute('aria-label',labels[ref.type]+' – Aktionen');button.onclick=event=>{event.preventDefault();event.stopPropagation();show(ref);};wrapper.append(button);
  }
 }
 function init(){
  let queued=false;const queue=()=>{if(queued)return;queued=true;requestAnimationFrame(()=>{queued=false;decorate();});};
  new MutationObserver(queue).observe(document.body,{childList:true,subtree:true});queue();
  const back=window.handleAndroidBack;window.handleAndroidBack=()=>{if(document.getElementById('record-action-dialog')?.open){close();return true;}return back();};
 }
 window.HGRecordActions=Object.freeze({show,reference});
 document.addEventListener('DOMContentLoaded',()=>setTimeout(init,240),{once:true});
})();

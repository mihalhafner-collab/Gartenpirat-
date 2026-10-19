/* Recoverable deletion uses the operative owner journal and its version checks. */
(()=>{
 'use strict';
 const get=id=>document.getElementById(id),names={customer:'Kunde',order:'Auftrag',worklog:'Arbeit',photo:'Foto',invoice:'Rechnungsentwurf',offer:'Angebotsentwurf',service_template:'Leistungsbaustein'},groups={customer:'customers',order:'orders',worklog:'worklogs',photo:'photos',invoice:'invoices',offer:'offers',service_template:'service_catalog'},requests=new Map();
 let offset=0,entries=[],busy=false;
 const call=window.HGOwnerClient.create("Speicherung nicht bestätigt.");
 function request(key){if(!requests.has(key)){const saved=localStorage.getItem('hausgarten.trash.request.'+key),id=saved||'trash-'+crypto.randomUUID();requests.set(key,id);try{localStorage.setItem('hausgarten.trash.request.'+key,id);}catch(_){}}return requests.get(key);}
 function empty(){
  if(busy)return false;
  try{
   const preview=call('preview_empty_trash');
   if(!preview.count){window.showUndoToast?.(preview.total?'Die verbliebenen Einträge sind noch mit aktiven Daten oder geschützten Belegen verknüpft.':'Der Papierkorb ist leer.');return false;}
   const rest=preview.blocked||[];
   if(!confirm(preview.count+' Einträge endgültig löschen?\n\nDiese Einträge können nicht wiederhergestellt werden. Aktive Daten bleiben erhalten. Nicht mehr verwendete Fotodateien werden nach geprüftem Drive-Abgleich entfernt.'+(rest.length?'\n\n'+rest.length+' geschützte Einträge bleiben im Papierkorb.':'')+'\n\nHistorische Sicherungen bleiben separat erhalten.'))return false;
   busy=true;const result=call('empty_trash',{request_id:request('empty:'+preview.preview_hash),expected_preview_hash:preview.preview_hash,confirmed:true});
   load();render();window.showUndoToast?.(result.deleted+' Einträge endgültig entfernt.'+(rest.length?' '+rest.length+' geschützte Einträge bleiben erhalten.':'')+' Drive-Abgleich und Dateibereinigung folgen.');return true;
  }catch(e){window.showUndoToast?.(e.message+' Papierkorb bleibt verfügbar.');return false;}finally{busy=false;}
 }
 function purge(entity,id,expected){
  if(busy)return false;
  try{
   const preview=call('preview_purge',{entity,entity_id:id});if(!preview.allowed)throw new Error(preview.message);
   if(preview.expected_record_hash!==expected)throw new Error('Papierkorb wurde inzwischen geändert. Erneut öffnen und prüfen.');
   if(!confirm('Diesen Eintrag endgültig löschen? Er kann in der App nicht wiederhergestellt werden. Nicht mehr verwendete Fotodateien werden nach bestätigtem Abgleich entfernt. Historische Sicherungen bleiben getrennt erhalten.'))return false;
   busy=true;call('purge_entity',{request_id:request('purge:'+entity+':'+id+':'+expected),entity,entity_id:id,expected_record_hash:expected,confirmed:true});load();window.showUndoToast?.('Endgültig aus dem Papierkorb entfernt. Dateibereinigung folgt nach dem Abgleich.');return true;
  }catch(e){window.showUndoToast?.(e.message);return false;}finally{busy=false;}
 }
 function restore(entity,id,expected){
  if(busy)return false;busy=true;
  try{
   const row=expected?{record_hash:expected}:call('list_archive',{entity,entity_id:id,limit:1}).archive[0];
   if(!row)throw new Error('Der Eintrag ist nicht mehr im Papierkorb. Aktuellen Stand prüfen.');
   call('restore_entity',{request_id:request('restore:'+entity+':'+id+':'+row.record_hash),entity,entity_id:id,expected_record_hash:row.record_hash});
   load();window.showUndoToast?.('Wiederhergestellt. Abgleich folgt automatisch.');return true;
  }catch(e){window.showUndoToast?.(e.message);return false;}finally{busy=false;}
 }
 function remove(entity,id,shown){
  if(busy)return false;busy=true;
  try{
   if(typeof AndroidData.ownerCall==='function'){
    const preview=call('preview_delete',{entity,entity_id:id});if(!preview.allowed)throw new Error(preview.message);
    const visible=shown||merged(groups[entity],entity).find(x=>x.id===id);
    if(!visible||String(visible.updated_at||'')!==preview.updated_at)throw new Error('Der Eintrag wurde inzwischen geändert. Ansicht aktualisieren und erneut prüfen.');
    call('delete_entity',{request_id:request('delete:'+entity+':'+id+':'+preview.expected_record_hash),entity,entity_id:id,expected_record_hash:preview.expected_record_hash,...(preview.expected_photo_links_hash?{expected_photo_links_hash:preview.expected_photo_links_hash}:{})});
   }else{
    const result=AndroidData.removeEntity(entity,id);if(!result||String(result).startsWith('ERROR:'))throw new Error(String(result||'Nicht gespeichert').replace(/^ERROR:\s*/,''));
   }
   load();window.showUndoToast?.('Im Papierkorb. Abgleich folgt automatisch.',()=>{
    if(typeof AndroidData.ownerCall==='function')restore(entity,id);
    else{const r=AndroidData.undoRemoveEntity(entity,id);if(String(r).startsWith('ERROR:'))window.showUndoToast?.(String(r).slice(6));else{load();window.showUndoToast?.('Wiederhergestellt.');}}
   });return true;
  }catch(e){window.showUndoToast?.(e.message);return false;}finally{busy=false;}
 }
 function render(more=false){
  const body=get('trash-content');if(!body)return;
  try{
   const result=call('list_archive',{limit:30,offset:more?offset:0});if(!more)entries=[];entries.push(...result.archive);offset=entries.length;
   body.innerHTML='<section class="card"><p>'+Number(result.total)+' Einträge im Papierkorb</p><button type="button" class="danger-button" id="trash-empty" '+(!result.total?'disabled':'')+'>Papierkorb leeren</button><p class="sub">Löscht alle freigegebenen Einträge endgültig. Vorher wird die vollständige Menge geprüft.</p><details class="workflow-options"><summary>Was bleibt erhalten?</summary><p>Aktive Daten, geschützte Belege und historische Sicherungen bleiben erhalten. Einzelne Einträge können weiterhin wiederhergestellt werden.</p></details></section>'+entries.map((x,i)=>{const r=x.entry.record,type=x.archive_key.split(':')[0];return '<article class="card"><strong>'+esc(r.name||r.title||r.activity||r.caption||r.invoice_number||names[type]||'Eintrag')+'</strong><p>'+esc(names[type]||'Eintrag')+' · '+esc(x.entry.archived_at?new Date(x.entry.archived_at).toLocaleString('de-AT'):'Datum unbekannt')+'</p><div class="trash-actions"><button type="button" class="secondary" data-trash-restore="'+i+'">Wiederherstellen</button><button type="button" class="danger-button" data-trash-purge="'+i+'">Endgültig löschen</button></div></article>';}).join('')+(offset<result.total?'<button type="button" class="secondary" id="trash-more">Weitere 30 anzeigen</button>':'');
   get('trash-empty').onclick=empty;
   body.querySelectorAll('[data-trash-restore]').forEach(b=>b.onclick=()=>{const x=entries[Number(b.dataset.trashRestore)],split=x.archive_key.indexOf(':');b.disabled=true;if(restore(x.archive_key.slice(0,split),x.archive_key.slice(split+1),x.record_hash))render();else b.disabled=false;});
   body.querySelectorAll('[data-trash-purge]').forEach(b=>b.onclick=()=>{const x=entries[Number(b.dataset.trashPurge)],split=x.archive_key.indexOf(':');b.disabled=true;if(purge(x.archive_key.slice(0,split),x.archive_key.slice(split+1),x.record_hash))render();else b.disabled=false;});
   get('trash-more')?.addEventListener('click',()=>render(true));
  }catch(e){body.textContent=e.message+' Gespeicherte Originale bleiben erhalten.';}
 }
 function open(){openPage('trash-page');render();}
 function installWorkSelection(){
  const list=get('worklog-list');if(!list||typeof AndroidData.ownerCall!=='function')return;
  const chosen=new Map();let selecting=false;
  const stamp=row=>{if(!row)return '';const fields={};for(const key of Object.keys(row).sort())if(!key.startsWith('_')&&!['pending','client_id','customer_name','order_title'].includes(key))fields[key]=row[key];return JSON.stringify(fields);};
  const bar=document.createElement('section');bar.id='worklog-selection';bar.className='card';
  bar.innerHTML='<button type="button" class="secondary" data-select-start>Mehrere auswählen</button><div data-select-controls hidden><p role="status" data-select-status></p><p class="sub">Arbeiten zum Auswählen antippen. Höchstens 200 pro Vorgang; Fotos bleiben erhalten.</p><div class="trash-actions"><button type="button" class="secondary" data-select-visible>Angezeigte auswählen</button><button type="button" class="danger-button" data-select-trash>In den Papierkorb</button><button type="button" class="secondary" data-select-cancel>Abbrechen</button></div></div>';
  list.before(bar);
  const button=key=>bar.querySelector('[data-select-'+key+']');
  function refresh(){
   list.classList.toggle('worklog-selecting',selecting);button('start').hidden=selecting;button('controls').hidden=!selecting;
   button('status').textContent=chosen.size+' ausgewählt';button('trash').disabled=!chosen.size||busy;
   for(const row of list.querySelectorAll('[data-edit-worklog]')){const marked=chosen.has(row.dataset.editWorklog);row.classList.toggle('worklog-selected',marked);if(selecting)row.setAttribute('aria-pressed',String(marked));else row.removeAttribute('aria-pressed');}
  }
  function select(id){
   if(chosen.has(id))chosen.delete(id);
   else{if(chosen.size>=200){window.showUndoToast?.('Höchstens 200 Arbeiten pro Vorgang auswählen.');return;}const row=worklogs().find(x=>x.id===id);if(row)chosen.set(id,stamp(row));}
  }
  function archive(){
   if(busy||!chosen.size)return false;
   try{
    const records=new Map(worklogs().map(x=>[x.id,x]));for(const [id,base] of chosen)if(stamp(records.get(id))!==base)throw new Error('Eine ausgewählte Arbeit wurde geändert. Nichts entfernt; Auswahl neu treffen.');
    const ids=[...chosen.keys()],preview=call('preview_archive_work_records',{work_record_ids:ids});
    if(!preview.allowed)throw new Error(preview.entries.find(x=>!x.allowed)?.message||'Eine ausgewählte Arbeit ist geschützt. Nichts entfernt.');
    const linked=preview.entries.reduce((n,x)=>n+Number(x.related_photos||0),0);
    if(!confirm(ids.length+' ausgewählte Arbeiten in den Papierkorb verschieben? Alle bleiben dort wiederherstellbar.'+(linked?' Zugehörige Fotos bleiben in der Fotobibliothek erhalten.':'')))return false;
    busy=true;refresh();call('archive_work_records',{request_id:request('work-batch:'+preview.preview_hash),work_record_ids:ids,expected_preview_hash:preview.preview_hash,confirmed:true});
    chosen.clear();selecting=false;load();window.showUndoToast?.(ids.length+' Arbeiten im Papierkorb. Dort einzeln wiederherstellbar.');return true;
   }catch(e){window.showUndoToast?.(e.message);return false;}finally{busy=false;refresh();}
  }
  button('start').onclick=()=>{selecting=true;refresh();};
  button('cancel').onclick=()=>{chosen.clear();selecting=false;refresh();};
  button('visible').onclick=()=>{for(const row of list.querySelectorAll('[data-edit-worklog]'))if(!chosen.has(row.dataset.editWorklog))select(row.dataset.editWorklog);refresh();};
  button('trash').onclick=archive;
  window.addEventListener('click',event=>{const row=event.target.closest('#worklog-list [data-edit-worklog]');if(!selecting||!row)return;event.preventDefault();event.stopImmediatePropagation();select(row.dataset.editWorklog);refresh();},true);
  new MutationObserver(refresh).observe(list,{childList:true});refresh();
  window.WorklogArchiveUI=Object.freeze({archive,refresh});
 }
 document.addEventListener('DOMContentLoaded',()=>setTimeout(()=>{
  const page=document.createElement('section');page.id='trash-page';page.className='page';page.innerHTML='<div class="page-head"><button type="button" class="back">‹</button><h2>Papierkorb</h2></div><div id="trash-content"></div>';document.querySelector('main.app').append(page);
  for(const host of [document.querySelector('#more-page .more-destinations')])if(host){const b=document.createElement('button');b.type='button';b.className='secondary';b.textContent='Papierkorb öffnen';b.dataset.openTrash='';b.onclick=open;host.append(b);}
  window.removeRecordWithUndo=remove;
  installWorkSelection();
 },0),{once:true});
 window.TrashUI=Object.freeze({open,remove,restore,purge,empty});
})();

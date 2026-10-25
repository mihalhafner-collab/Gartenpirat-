/* Recoverable deletion uses the operative owner journal and its version checks. */
(()=>{
 'use strict';
 const get=id=>document.getElementById(id),names={appointment:'Termin',customer:'Kunde',order:'Auftrag',worklog:'Arbeit',photo:'Foto',invoice:'Rechnungsentwurf',offer:'Angebotsentwurf',service_template:'Leistungsbaustein'},groups={appointment:'appointments',customer:'customers',order:'orders',worklog:'worklogs',photo:'photos',invoice:'invoices',offer:'offers',service_template:'service_catalog'},requests=new Map();
 let offset=0,entries=[],busy=false,filter='';
 const cleanupText=()=>syncInfo.mode==='backup_only'?'Nicht mehr benötigte Dateien werden lokal geprüft und bereinigt.':'Dateibereinigung folgt nach dem bestätigten Legacy-Abgleich.';
 const call=window.HGOwnerClient.create("Speicherung nicht bestätigt.");
 function request(key){if(!requests.has(key)){const saved=localStorage.getItem('hausgarten.trash.request.'+key),id=saved||'trash-'+crypto.randomUUID();localStorage.setItem('hausgarten.trash.request.'+key,id);if(localStorage.getItem('hausgarten.trash.request.'+key)!==id)throw Error('Aktionskennung konnte nicht gesichert werden.');requests.set(key,id);}return requests.get(key);}
 function revierAction(entity,id,hash,archived,purge=false){const actionKey=(purge?'purge:':'archive:')+entity+':'+id;let edit=HGFormDrafts.list('revier_archive').find(e=>e.context?.action_key===actionKey);if(!edit){edit=HGFormDrafts.start('revier_archive',{}, {action_key:actionKey});HGFormDrafts.freeze(edit,purge?'purge_revier_entity':'set_revier_archived',{entity,entity_id:id,expected_record_hash:hash,...(purge?{}:{archived}),confirmed:true,request_id:'revier_archive_'+crypto.randomUUID()});}try{const result=edit.restore_review_required?HGFormDrafts.inspectRestored(edit):call(edit.pending.tool,edit.pending.args);edit.pending=null;edit.confirmed=result;try{HGFormDrafts.remove(edit);}catch(_){window.showUndoToast?.('Lokal gespeichert. Technische Entwurfsbereinigung ausstehend.');}try{load();}catch(_){}return true;}catch(error){if(error.rejected){edit.pending=null;HGFormDrafts.put(edit);}window.showUndoToast?.(HGBusinessFeedback.error(error)+' Andere Vorgänge bleiben möglich.');return false;}}
 function archiveRevier(entity,row){if(!confirm('Diesen Eintrag archivieren? Geschäftsdaten und Beziehungen bleiben erhalten. Ein laufender Einsatz wird dadurch nicht beendet.'))return false;const saved=revierAction(entity,row.id,row.record_hash,true);if(saved)open();return saved;}
 function renderRevierArchive(start=0,append=false){const body=get('trash-content');if(!body)return;let host=get('revier-trash');if(!host){host=document.createElement('section');host.id='revier-trash';host.className='card';body.append(host);}try{const result=call('list_revier_archive',{offset:start,limit:30});if(!append)host.innerHTML='<h3>Archivierte Revier-Einträge</h3><p>Objekte, Anfragen, Pflege, Besuche, Material und Geräte. Endgültiges Löschen prüft Beziehungen. Historische vollständige Sicherungen bleiben getrennt erhalten.</p>';const rows=Array.isArray(result.items)?result.items:[];if(!rows.length){host.insertAdjacentHTML('beforeend','<p>Keine archivierten Revier-Einträge.</p>');return;}for(const item of rows){const row=item.record,section=document.createElement('article'),title=document.createElement('h4');title.textContent=row.name||row.title||'Revier-Eintrag';section.append(title);for(const [label,purge]of [['Wiederherstellen',false],['Endgültig löschen',true]]){const button=document.createElement('button');button.type='button';button.className='secondary';button.textContent=label;button.onclick=()=>{if(purge&&!confirm('Diesen archivierten Eintrag samt nicht mehr benötigter Inhaltshistorie endgültig entfernen? Verknüpfte Geschäftsdatensätze und gemeinsam verwendete Dateien werden geschützt. Historische Sicherungen bleiben erhalten.'))return;if(revierAction(item.entity,row.id,item.record_hash,false,purge))render();};section.append(button);}host.append(section);}host.querySelector('[data-revier-trash-more]')?.remove();if(result.total>start+rows.length){const more=document.createElement('button');more.type='button';more.className='secondary';more.dataset.revierTrashMore='true';more.textContent='Weitere archivierte Revier-Einträge';more.onclick=()=>renderRevierArchive(start+rows.length,true);host.append(more);}}catch(error){host.textContent=HGBusinessFeedback.error(error)+' Revier-Archiv erneut öffnen.';}}
 function empty(){
  if(busy)return false;
  try{
   const preview=call('preview_empty_trash');
   if(!preview.count){window.showUndoToast?.(preview.total?'Die verbliebenen Einträge sind noch mit aktiven Daten oder geschützten Belegen verknüpft.':'Der Papierkorb ist leer.');return false;}
   const rest=preview.blocked||[];
   if(!confirm(preview.count+' Einträge aus dem gesamten Papierkorb endgültig löschen?\n\nDiese Einträge werden aus dem Papierkorb entfernt. Eine spätere Übernahme aus der Löschsicherung muss einzeln geprüft werden. Aktive Daten bleiben erhalten. Geschützte Originaldateien bleiben bis zum erfüllten lokalen Lösch- und Sicherungsnachweis erhalten.'+(rest.length?'\n\n'+rest.length+' geschützte Einträge bleiben im Papierkorb.':'')+'\n\nBetroffene Einträge und Originaldateien werden vor dem Löschen auf diesem Gerät gesichert. Historische Sicherungen bleiben separat erhalten.'))return false;
   busy=true;const result=call('empty_trash',{request_id:request('empty:'+preview.preview_hash),expected_preview_hash:preview.preview_hash,confirmed:true});
   load();render();window.showUndoToast?.(result.deleted+' Einträge endgültig entfernt.'+(rest.length?' '+rest.length+' geschützte Einträge bleiben erhalten.':'')+(result.deletion_backup_id?' Löschsicherung auf diesem Gerät erstellt.':'')+' '+cleanupText());return true;
  }catch(e){window.showUndoToast?.(e.message+' Papierkorb bleibt verfügbar.');return false;}finally{busy=false;}
 }
 function purge(entity,id,expected){
  if(busy)return false;
  try{
   const preview=call('preview_purge',{entity,entity_id:id});if(!preview.allowed)throw new Error(preview.message);
   if(preview.expected_record_hash!==expected)throw new Error('Papierkorb wurde inzwischen geändert. Erneut öffnen und prüfen.');
   if(!confirm('Diesen Eintrag aus dem aktiven Bestand und Papierkorb entfernen? Eine vollständige lokale Löschsicherung bleibt nach bestehender Aufbewahrung erhalten. '+cleanupText()+' Historische vollständige Sicherungen bleiben getrennt erhalten.'))return false;
   busy=true;const result=call('purge_entity',{request_id:request('purge:'+entity+':'+id+':'+expected),entity,entity_id:id,expected_record_hash:expected,confirmed:true});load();window.showUndoToast?.('Endgültig aus dem Papierkorb entfernt.'+(result.deletion_backup_id?' Löschsicherung auf diesem Gerät erstellt.':'')+' '+cleanupText());return true;
  }catch(e){window.showUndoToast?.(HGBusinessFeedback.error(e));return false;}finally{busy=false;}
 }
 function restore(entity,id,expected){
  if(busy)return false;busy=true;
  try{
   const row=expected?{record_hash:expected}:call('list_archive',{entity,entity_id:id,limit:1}).archive[0];
   if(!row)throw new Error('Der Eintrag ist nicht mehr im Papierkorb. Aktuellen Stand prüfen.');
   call('restore_entity',{request_id:request('restore:'+entity+':'+id+':'+row.record_hash),entity,entity_id:id,expected_record_hash:row.record_hash});
   load();window.showUndoToast?.('Wiederhergestellt. Abgleich folgt automatisch.');return true;
  }catch(e){window.showUndoToast?.(HGBusinessFeedback.error(e));return false;}finally{busy=false;}
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
  }catch(e){window.showUndoToast?.(HGBusinessFeedback.error(e));return false;}finally{busy=false;}
 }
 function render(more=false){
  const body=get('trash-content');if(!body)return;
  try{
   const result=call('list_archive',{limit:30,offset:more?offset:0,...(filter?{entity:filter}:{})});if(!more)entries=[];entries.push(...result.archive);offset=entries.length;
   body.innerHTML='<section class="card"><p>'+Number(result.total)+' Einträge'+(filter?' dieser Kategorie':' im Papierkorb')+'</p><button type="button" class="danger-button" id="trash-empty" '+(!result.total&&!filter?'disabled':'')+'>Gesamten Papierkorb leeren</button><p class="sub">Leert alle Kategorien nach vollständiger Prüfung und Bestätigung. Aktive Daten bleiben erhalten.</p><details class="workflow-options"><summary>Was bleibt erhalten?</summary><p>Aktive Daten, geschützte Belege und historische Sicherungen bleiben erhalten. Einzelne Einträge können weiterhin wiederhergestellt werden.</p></details></section>'+entries.map((x,i)=>{const r=x.entry.record,type=x.archive_key.split(':')[0];return '<article class="card" data-trash-record="'+esc(x.archive_key)+'" data-trash-hash="'+esc(x.record_hash)+'"><strong>'+esc(r.name||r.title||r.activity||r.caption||r.invoice_number||names[type]||'Eintrag')+'</strong><p>'+esc(names[type]||'Eintrag')+' · '+esc(x.entry.archived_at?new Date(x.entry.archived_at).toLocaleString('de-AT'):'Datum unbekannt')+'</p><div class="trash-actions"><button type="button" class="secondary" data-trash-restore="'+i+'">Wiederherstellen</button><button type="button" class="danger-button" data-trash-purge="'+i+'">Endgültig löschen</button></div></article>';}).join('')+(offset<result.total?'<button type="button" class="secondary" id="trash-more">Weitere 30 anzeigen</button>':'');
   get('trash-empty').onclick=empty;
   body.querySelectorAll('[data-trash-restore]').forEach(b=>b.onclick=()=>{const x=entries[Number(b.dataset.trashRestore)],split=x.archive_key.indexOf(':');b.disabled=true;if(restore(x.archive_key.slice(0,split),x.archive_key.slice(split+1),x.record_hash))render();else b.disabled=false;});
   body.querySelectorAll('[data-trash-purge]').forEach(b=>b.onclick=()=>{const x=entries[Number(b.dataset.trashPurge)],split=x.archive_key.indexOf(':');b.disabled=true;if(purge(x.archive_key.slice(0,split),x.archive_key.slice(split+1),x.record_hash))render();else b.disabled=false;});
   get('trash-more')?.addEventListener('click',()=>render(true));renderRevierArchive();
  }catch(e){body.textContent=HGBusinessFeedback.error(e)+' Bestehenden Papierkorb erneut lesen. Andere Archivbereiche bleiben erreichbar.';renderRevierArchive();}
 }
 function open(){openPage('trash-page');render();}
 function installWorkSelection(){
  const list=get('worklog-list');if(!list||typeof AndroidData.ownerCall!=='function')return;
  const chosen=new Map();let selecting=false;
  const stamp=row=>{if(!row)return '';const fields={};for(const key of Object.keys(row).sort())if(!key.startsWith('_')&&!['pending','client_id','customer_name','order_title'].includes(key))fields[key]=row[key];return JSON.stringify(fields);};
  const bar=document.createElement('section');bar.id='worklog-selection';bar.className='card';
  bar.innerHTML='<button type="button" class="secondary" data-select-start>Mehrere in Papierkorb</button><div data-select-controls hidden><p role="status" data-select-status></p><p class="sub">Arbeiten zum Auswählen antippen. Höchstens 200 pro Vorgang; Fotos bleiben erhalten.</p><div class="trash-actions"><button type="button" class="secondary" data-select-visible>Angezeigte auswählen</button><button type="button" class="danger-button" data-select-trash>In den Papierkorb</button><button type="button" class="secondary" data-select-cancel>Abbrechen</button></div></div>';
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
   }catch(e){window.showUndoToast?.(HGBusinessFeedback.error(e));return false;}finally{busy=false;refresh();}
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
  const page=document.createElement('section');page.id='trash-page';page.className='page';page.innerHTML='<div class="page-head"><button type="button" class="back">‹</button><h2>Papierkorb</h2></div><section class="card trash-filter"><label for="trash-kind">Datenart</label><select id="trash-kind"><option value="">Alle Kategorien</option>'+Object.entries(names).map(([id,name])=>'<option value="'+esc(id)+'">'+esc(name)+'</option>').join('')+'</select><button type="button" class="secondary" id="trash-refresh">Neu laden</button></section><div id="trash-content"></div>';document.querySelector('main.app').append(page);get('trash-kind').onchange=()=>{filter=get('trash-kind').value;render();};get('trash-refresh').onclick=()=>render();
  for(const host of [document.querySelector('#more-page .more-destinations')])if(host){const b=document.createElement('button');b.type='button';b.className='secondary';b.textContent='Papierkorb öffnen';b.dataset.openTrash='';b.onclick=open;host.append(b);}
  window.removeRecordWithUndo=remove;
  installWorkSelection();
 },0),{once:true});
 window.TrashUI=Object.freeze({archiveRevier,changeRevierArchive:(entity,row,purge)=>{if(!confirm(purge?'Diesen archivierten Eintrag und nicht mehr benötigte Inhaltshistorie endgültig entfernen? Geschützte Beziehungen und historische Sicherungen bleiben erhalten.':'Diesen Eintrag bewusst wiederherstellen?'))return false;const ok=revierAction(entity,row.id,row.record_hash,false,purge);if(ok)open();return ok;},resumeRevierAction:edit=>{const args=edit.pending?.args;if(args){revierAction(args.entity,args.entity_id,args.expected_record_hash,args.archived,edit.pending.tool==='purge_revier_entity');open();}},open,remove,restore,purge,empty});
})();

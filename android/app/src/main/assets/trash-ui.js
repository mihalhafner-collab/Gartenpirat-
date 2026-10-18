/* Recoverable deletion uses the operative owner journal and its version checks. */
(()=>{
 'use strict';
 const get=id=>document.getElementById(id),names={customer:'Kunde',order:'Auftrag',worklog:'Arbeit',photo:'Foto',invoice:'Rechnungsentwurf',offer:'Angebotsentwurf',service_template:'Leistungsbaustein'},groups={customer:'customers',order:'orders',worklog:'worklogs',photo:'photos',invoice:'invoices',offer:'offers',service_template:'service_catalog'},requests=new Map();
 let offset=0,entries=[];
 function call(name,args={}){const r=JSON.parse(AndroidData.ownerCall(name,JSON.stringify(args)));if(!r.ok)throw new Error(r.error?.message||'Speicherung nicht bestätigt.');return r.data;}
 function request(key){if(!requests.has(key))requests.set(key,'trash-'+crypto.randomUUID());return requests.get(key);}
 function restore(entity,id,expected){
  try{
   const row=expected?{record_hash:expected}:call('list_archive',{entity,entity_id:id,limit:1}).archive[0];
   if(!row)throw new Error('Der Eintrag ist nicht mehr im Papierkorb. Aktuellen Stand prüfen.');
   call('restore_entity',{request_id:request('restore:'+entity+':'+id+':'+row.record_hash),entity,entity_id:id,expected_record_hash:row.record_hash});
   load();window.showUndoToast?.('Wiederhergestellt. Abgleich folgt automatisch.');return true;
  }catch(e){window.showUndoToast?.(e.message);return false;}
 }
 function remove(entity,id,shown){
  try{
   if(typeof AndroidData.ownerCall==='function'){
    const preview=call('preview_delete',{entity,entity_id:id});if(!preview.allowed)throw new Error(preview.message);
    const visible=shown||merged(groups[entity],entity).find(x=>x.id===id);
    if(!visible||String(visible.updated_at||'')!==preview.updated_at)throw new Error('Der Eintrag wurde inzwischen geändert. Ansicht aktualisieren und erneut prüfen.');
    call('delete_entity',{request_id:request('delete:'+entity+':'+id+':'+preview.expected_record_hash),entity,entity_id:id,expected_record_hash:preview.expected_record_hash});
   }else{
    const result=AndroidData.removeEntity(entity,id);if(!result||String(result).startsWith('ERROR:'))throw new Error(String(result||'Nicht gespeichert').replace(/^ERROR:\s*/,''));
   }
   load();window.showUndoToast?.('Im Papierkorb. Abgleich folgt automatisch.',()=>{
    if(typeof AndroidData.ownerCall==='function')restore(entity,id);
    else{const r=AndroidData.undoRemoveEntity(entity,id);if(String(r).startsWith('ERROR:'))window.showUndoToast?.(String(r).slice(6));else{load();window.showUndoToast?.('Wiederhergestellt.');}}
   });return true;
  }catch(e){window.showUndoToast?.(e.message);return false;}
 }
 function render(more=false){
  const body=get('trash-content');if(!body)return;
  try{
   const result=call('list_archive',{limit:30,offset:more?offset:0});if(!more)entries=[];entries.push(...result.archive);offset=entries.length;
   body.innerHTML='<section class="card"><p>Gelöschte Einträge bleiben wiederherstellbar. Kunden und Bausteine werden deaktiviert; verknüpfte Historie und Originaldateien bleiben erhalten. Es wird nichts automatisch dauerhaft gelöscht.</p><p>'+Number(result.total)+' Einträge</p></section>'+entries.map((x,i)=>{const r=x.entry.record,type=x.archive_key.split(':')[0];return '<article class="card"><strong>'+esc(r.name||r.title||r.activity||r.caption||r.invoice_number||names[type]||'Eintrag')+'</strong><p>'+esc(names[type]||'Eintrag')+' · '+esc(x.entry.archived_at?new Date(x.entry.archived_at).toLocaleString('de-AT'):'Datum unbekannt')+'</p><button type="button" class="secondary" data-trash-restore="'+i+'">Wiederherstellen</button></article>';}).join('')+(offset<result.total?'<button type="button" class="secondary" id="trash-more">Weitere 30 anzeigen</button>':'');
   body.querySelectorAll('[data-trash-restore]').forEach(b=>b.onclick=()=>{const x=entries[Number(b.dataset.trashRestore)],split=x.archive_key.indexOf(':');b.disabled=true;if(restore(x.archive_key.slice(0,split),x.archive_key.slice(split+1),x.record_hash))render();else b.disabled=false;});
   get('trash-more')?.addEventListener('click',()=>render(true));
  }catch(e){body.textContent=e.message+' Gespeicherte Originale bleiben erhalten.';}
 }
 function open(){openPage('trash-page');render();}
 document.addEventListener('DOMContentLoaded',()=>setTimeout(()=>{
  const page=document.createElement('section');page.id='trash-page';page.className='page';page.innerHTML='<div class="page-head"><button type="button" class="back">‹</button><h2>Papierkorb</h2></div><div id="trash-content"></div>';document.querySelector('main.app').append(page);
  for(const host of [document.querySelector('#more-page .more-destinations'),get('sync-page')])if(host){const b=document.createElement('button');b.type='button';b.className='secondary';b.textContent='Papierkorb öffnen';b.dataset.openTrash='';b.onclick=open;host.append(b);}
  window.removeRecordWithUndo=remove;
 },0),{once:true});
 window.TrashUI=Object.freeze({open,remove,restore});
})();

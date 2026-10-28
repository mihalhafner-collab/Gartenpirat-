/* Optional device identity photo; existing local draft/media/owner boundaries only. */
(()=>{
 'use strict';
 const pending=new Map();let previewUrl='';
 const escape=s=>String(s||'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const live=edit=>!HGFormDrafts.removed(edit.session_id);
 const key=edit=>edit.context?.media_key||'';
 const currentSelection=(edit,selection)=>live(edit)&&HGFormDrafts.list().some(d=>d.session_id===edit.session_id&&d.context?.photo_selection===selection);
 const request=()=>crypto.randomUUID().replaceAll('-','');
 function release(){if(previewUrl){URL.revokeObjectURL(previewUrl);previewUrl='';}}
 function image(photo,large=false){const name=photo?.thumbnail_storage_name;if(!/^[a-f0-9]{64}\.jpg$/.test(name||''))return '<span class="equipment-photo-placeholder" aria-label="Noch kein Gerätefoto">'+(large?'Foto':'')+'</span>';return '<img class="equipment-photo-thumb" src="content://at.hausgarten.service.photothumbnails/'+name+'" alt="Gerätefoto" loading="lazy" decoding="async">';}
 function markup(edit){return '<section class="equipment-photo-editor" aria-label="Gerätefoto"><div data-equipment-photo-preview>'+image(edit.values.profile_photo,true)+'</div><div><strong>Gerätefoto</strong><p class="sub" data-equipment-photo-message>'+(edit.values.profile_photo_selected?escape(edit.values.profile_photo_selected):'Optional · zum Wiedererkennen')+'</p><div class="revier-actions"><button type="button" class="secondary" data-equipment-photo-source="camera">Aufnehmen</button><button type="button" class="secondary" data-equipment-photo-source="gallery">Auswählen</button></div></div><input type="file" id="equipment-photo-camera" accept="image/*" capture="environment" hidden><input type="file" id="equipment-photo-gallery" accept="image/*" hidden></section>';}
 function bind(edit,form,retain){
  if(edit.kind!=='equipment')return;
  const message=text=>{if(document.querySelector('#revier-form')===form)form.querySelector('[data-equipment-photo-message]').textContent=text;};
  const show=file=>{if(document.querySelector('#revier-form')!==form||!live(edit))return;release();previewUrl=URL.createObjectURL(file);const img=document.createElement('img');img.className='equipment-photo-thumb';img.alt='Ausgewähltes Gerätefoto · noch nicht gespeichert';img.src=previewUrl;form.querySelector('[data-equipment-photo-preview]').replaceChildren(img);};
  const stage=file=>{
   if(!file||edit.pending||edit.saving||!live(edit)||document.querySelector('#revier-form')!==form)return;
   if(file.size<1||file.size>20*1024*1024){message('Bitte ein Foto bis 20 MB auswählen.');return;}
   if(file.type&&!['image/jpeg','image/png','image/webp','image/gif','image/heic','image/heif'].includes(file.type)){message('Bitte ein unterstütztes Foto auswählen.');return;}
   edit.context.media_key=key(edit)||'equipment_'+edit.session_id;const selection=request();edit.context.photo_selection=selection;edit.values.profile_photo_selected=file.name||'Gerätefoto';delete edit.values.profile_photo_upload_id;delete edit.values.profile_photo_draft;
   try{retain(edit);}catch(error){message(error.message);return;}
   const previous=pending.get(edit.session_id)||Promise.resolve();const saving=previous.catch(()=>{}).then(async()=>{
    if(!currentSelection(edit,selection))return;
    const action_context=await GartenpiratPhotoStore.actionContext();
    if(!currentSelection(edit,selection))return;
    await GartenpiratPhotoStore.customerDraft(key(edit),{formatVersion:1,session_id:edit.session_id,selection,file,name:file.name||'geraet.jpg',action_context});
    if(!live(edit)){await HGFormDrafts.cleanup();return;}
    if(!currentSelection(edit,selection))return;
    show(file);message('Foto ausgewählt · mit dem Gerät speichern');
   });pending.set(edit.session_id,saving);const done=()=>{if(pending.get(edit.session_id)===saving)pending.delete(edit.session_id);};saving.then(done,done);saving.catch(error=>message('Fotoentwurf noch nicht gesichert: '+error.message));
  };
  form.querySelectorAll('[data-equipment-photo-source]').forEach(button=>{button.disabled=!!edit.pending;button.onclick=()=>{if(edit.pending||edit.saving)return;const source=button.dataset.equipmentPhotoSource;if(source==='gallery')window.AndroidGardenPhotos?.preparePicker?.();form.querySelector('#equipment-photo-'+source).click();};});
  form.querySelectorAll('.equipment-photo-editor input[type=file]').forEach(input=>input.onchange=()=>{const file=input.files?.[0];input.value='';stage(file);});
  if(key(edit))Promise.resolve().then(()=>GartenpiratPhotoStore.customerDraft(key(edit))).then(value=>{if(value?.file&&value.selection===edit.context.photo_selection)show(value.file);}).catch(()=>message('Fotoentwurf zum Fortsetzen erneut prüfen.'));
 }
 async function prepare(edit){
  if(edit.kind!=='equipment'||edit.pending||!key(edit))return;
  await pending.get(edit.session_id);if(!live(edit))throw Error('Diese Bearbeitung wurde verworfen.');
  const value=await GartenpiratPhotoStore.customerDraft(key(edit));if(!value?.file||value.selection!==edit.context.photo_selection)throw Error('Der ausgewählte Fotoentwurf ist noch nicht vollständig lesbar.');
  if(!currentSelection(edit,value.selection))throw Error('Diese Fotoauswahl ist nicht mehr aktuell.');
  const context=value.action_context;
  if(!context?.installation_id||!context.restore_epoch||value._restore_action_review_required)throw Error('Fotoauswahl stammt aus einem anderen Gerätestand. Bitte das Foto erneut bewusst auswählen.');
  return {media_key:key(edit),session_id:edit.session_id,selection:value.selection,installation_id:context.installation_id,restore_epoch:context.restore_epoch};
 }
 async function saved(edit){if(key(edit))await GartenpiratPhotoStore.customerDraft(key(edit),null);release();}
 document.addEventListener('error',event=>{const image=event.target;if(!image.matches?.('.equipment-photo-thumb'))return;const placeholder=document.createElement('span');placeholder.className='equipment-photo-placeholder';placeholder.setAttribute('aria-label','Fotovorschau nicht verfügbar');image.replaceWith(placeholder);},true);
 window.EquipmentPhotoUI=Object.freeze({markup,bind,prepare,saved,image,release});
})();

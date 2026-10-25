/* Daily navigation and reachability. Existing forms and native save APIs remain authoritative. */
(() => {
  'use strict';
  const get=id=>document.getElementById(id);
  function formState(){return JSON.stringify([...get('customer-form').elements].filter(x=>x.name||x.classList.contains('custom-name')||x.classList.contains('custom-value')).map(x=>[x.name||x.className,x.type==='checkbox'?x.checked:x.value]));}
  function install(){
    if(get('settings-page'))return;
    const page=document.createElement('section');page.id='settings-page';page.className='page';
    page.innerHTML='<div class="page-head"><button type="button" class="back" aria-label="Zurück">‹</button><h2>Einstellungen</h2></div>'+
      '<section class="card settings-section" aria-labelledby="settings-operation-title"><h3 id="settings-operation-title">Bedienung</h3></section>'+
      '<section class="card settings-section"><h3>Kalender & Wetter</h3><p id="settings-planning-state" class="sub" role="status">Verbindung beim Öffnen prüfen.</p><button type="button" class="secondary" id="settings-planning">Kalender & Wetter einrichten</button></section>'+
      '<section class="card settings-section" aria-labelledby="settings-data-title"><h3 id="settings-data-title">Google Drive</h3><div id="settings-sync-state" class="settings-state" role="status" aria-live="polite"><strong id="settings-sync-summary">Drive wird geprüft</strong><span id="settings-sync-detail">Der lokale und der übertragene Stand werden getrennt angezeigt.</span></div><details class="settings-advanced"><summary>Erweitert / Diagnose</summary><dl class="settings-diagnostics"><div><dt>Lokale Änderungen</dt><dd id="settings-sync-local">Wird geprüft</dd></div><div><dt>Übertragungsstand</dt><dd id="settings-sync-transfer">Wird geprüft</dd></div><div><dt>Letzter erfolgreicher Stand</dt><dd id="settings-sync-last">Wird geprüft</dd></div></dl></details><button type="button" class="secondary" id="settings-drive">Google Drive EIN / AUS</button><button type="button" class="secondary" data-open="sync-page">Synchronisierung öffnen</button><p class="sub">Die Synchronisierung gleicht deine Arbeit ab. Eine vollständige Sicherung findest du separat unter Sicherung.</p></section>'+
      '<section class="card settings-section" aria-labelledby="settings-connection-title"><h3 id="settings-connection-title">ChatGPT-Steuerung</h3><p id="settings-connection-state" class="sub" role="status" aria-live="polite">Verbindungsstatus wird geprüft.</p><button type="button" class="secondary" id="settings-connection">ChatGPT EIN / AUS</button><p class="sub">Die Freigabe und das Ein- oder Ausschalten erfolgen in der bestehenden Verbindung.</p></section>'+
      '<footer class="settings-section settings-about" aria-labelledby="settings-about-title"><span id="settings-about-title">App-Information</span><p id="settings-version" class="sub">Versionsangabe wird geprüft.</p></footer>';
    document.querySelector('main.app').append(page);
    const more=document.createElement('button');more.type='button';more.dataset.open='settings-page';more.innerHTML='<span><strong>Einstellungen</strong><small>Bedienung, Daten und Verbindung</small></span><b aria-hidden="true">›</b>';get('more-page').querySelector('.more-destinations').append(more);
    const shortcut=document.createElement('button');shortcut.type='button';shortcut.className='daily-settings';shortcut.dataset.open='settings-page';shortcut.setAttribute('aria-label','Einstellungen öffnen');shortcut.textContent='⚙';document.querySelector('.brand').append(shortcut);
    const diagnosis=page.querySelector('.settings-advanced');diagnosis.insertAdjacentHTML('beforeend','<h4>Fehlermeldungen</h4><pre class="settings-error-details"></pre>');diagnosis.ontoggle=()=>{if(diagnosis.open)diagnosis.querySelector('pre.settings-error-details').textContent=HGBusinessFeedback.diagnostics().join('\n\n')||'Keine technischen Fehlermeldungen in dieser Sitzung.';};
    get('settings-drive').onclick=()=>{if(typeof AndroidData.connectDrive==='function')AndroidData.connectDrive();else window.showUndoToast?.('Bitte die App aktualisieren, um die Google-Drive-Verbindung einzurichten.');};
    get('settings-planning').onclick=()=>AndroidData.connectPlanning?.();
    get('settings-connection').onclick=()=>get('pixel-tunnel-setup')?.click();
    // Move, never duplicate, the existing inputs and handlers into reachable toolbars.
    for(const [id,searchId,addId,label] of [['customers','customer-search','add-customer','+ Kunde'],['worklogs','worklog-search','design-add-work','+ Arbeit'],['orders','order-search','add-order','+ Auftrag']]){
      const toolbar=document.createElement('div');toolbar.className='daily-list-tools';toolbar.setAttribute('aria-label',label.slice(2)+' suchen oder hinzufügen');toolbar.id='daily-tools-'+id;
      if(id==='orders'){const back=document.createElement('button');back.type='button';back.className='secondary daily-toolbar-back';back.textContent='‹';back.setAttribute('aria-label','Eine Ansicht zurück');back.onclick=()=>window.handleAndroidBack();toolbar.append(back);}const search=get(searchId),add=get(addId);if(search){search.setAttribute('aria-label',search.placeholder||'Suchen');toolbar.append(search);}if(add){add.className='primary daily-add';add.textContent=label;toolbar.append(add);}get(id).append(toolbar);get(id).classList.add('daily-list-page');
    }
    get('add-worklog').hidden=true;
    let customerEdit=null;
    const customerForm=get('customer-form');
    function customerDraft(){if(!customerEdit)customerEdit=HGFormDrafts.start('customer',HGFormDrafts.fields(customerForm),{base:editingCustomer?HGOwnerClient.clone(editingCustomer):null,parent:window.orderDraftForCustomer||null});customerForm.dataset.editSession=customerEdit.session_id;customerEdit.values=HGFormDrafts.fields(customerForm);HGFormDrafts.put(customerEdit);return customerEdit;}
    function retainCustomer(){try{customerDraft();return true;}catch(_){window.showUndoToast?.('Kundenentwurf konnte nicht gesichert werden. Eingaben bleiben geöffnet.');return false;}}
    for(const event of ['input','change'])customerForm.addEventListener(event,retainCustomer);
    let photoSelection=Promise.resolve(null);
    async function stageCustomerPhoto(){const file=get('customer-profile-photo')?.files?.[0];if(!file)return null;const edit=customerDraft(),mediaKey='profile_'+edit.session_id;photoSelection=Promise.resolve().then(async()=>{const old=await GartenpiratPhotoStore.customerDraft(mediaKey);const value={formatVersion:1,edit_id:edit.session_id,customer_id:edit.context.base?.id||'',parent:edit.context.parent?.session_id||'',file,name:file.name||'profilbild.jpg',request_id:'profile_photo_'+crypto.randomUUID()};await GartenpiratPhotoStore.customerDraft(mediaKey,value);edit.context.media_key=mediaKey;HGFormDrafts.put(edit);return value;});return photoSelection;}
    get('customer-profile-photo')?.addEventListener('change',()=>{if(!get('customer-profile-photo').files.length)return;stageCustomerPhoto().then(()=>window.showUndoToast?.('Profilfotoentwurf lokal gesichert.')).catch(()=>window.showUndoToast?.('Profilfoto noch nicht vollständig gesichert. Kundendaten können getrennt gespeichert werden.'));});
    window.customerPhotoForSave=async()=>{try{await photoSelection;const edit=customerDraft();if(edit.context.media_key)return {key:edit.context.media_key,value:await GartenpiratPhotoStore.customerDraft(edit.context.media_key)};if(get('customer-profile-photo')?.files?.[0])return {key:'profile_'+edit.session_id,value:await stageCustomerPhoto()};return null;}catch(error){return {error};}};
    window.saveCustomerProfilePhoto=async(customerId,media)=>{if(!media)return true;if(media.error){window.showUndoToast?.('Kunde lokal gespeichert. Profilfotoentwurf prüfen; die Kundenspeicherung wird nicht wiederholt.');return false;}const value=media.value;if(!value?.file)throw Error('Profilfotoentwurf ohne Dateiinhalte.');const identity='profile_edit_'+value.request_id;let task=HGFormDrafts.list('profile_photo').find(edit=>edit.session_id===identity)||HGFormDrafts.start('profile_photo',{},{});task.session_id=identity;task.values={customer_id:customerId,media_key:media.key,request_id:value.request_id};HGFormDrafts.put(task);return window.retryCustomerProfilePhoto(task);};
    window.retryCustomerProfilePhoto=async task=>{try{if(task.restore_review_required&&task.pending){const proof=HGFormDrafts.inspectRestored(task);task.confirmed={photo_id:proof.entity_id};}if(!task.confirmed){const value=await GartenpiratPhotoStore.customerDraft(task.values.media_key);if(!value?.file)throw Error('Datei noch nicht lesbar. Den Fotoentwurf gezielt erneut prüfen.');if(!value.prepared_payload&&!value.prepared_image){const prepared=await preparePhotoSource(value.file,undefined,value.name||'profilbild');value.prepared_image=prepared.data;value.prepared_metadata={original_name:prepared.original_name,mime_type:prepared.mime_type,_photo_quality:prepared._photo_quality};await GartenpiratPhotoStore.customerDraft(task.values.media_key,value);}const meta={customer_id:task.values.customer_id,original_name:(value.name||'profilbild').replace(/\.[^.]+$/,'')+'.jpg',caption:'Profilbild',role:'profile',mime_type:'image/jpeg',customer_visible:0,_photo_request_id:task.values.request_id,...(value.prepared_metadata||{})};HGFormDrafts.freeze(task,'savePhoto',meta);const result=await GartenpiratPhotoStore.commitPhoto(task.values.media_key,value,value,value.prepared_payload||{...task.pending.args,data:value.prepared_image});if(String(result).startsWith('ERROR:'))throw Error(String(result).replace(/^ERROR:\s*/,''));if(typeof result!=='string'||!/^[A-Za-z0-9_-]{1,200}$/.test(result)||['null','undefined','OK','ERROR'].includes(result))throw Error('Foto-Speicherergebnis ungeklärt. Dieselbe Fotoaktion erneut prüfen.');HGFormDrafts.confirmed(task,{photo_id:result});}await GartenpiratPhotoStore.customerDraft(task.values.media_key,null);HGFormDrafts.remove(task);window.showUndoToast?.('Profilfoto lokal gespeichert.');return true;}catch(error){window.showUndoToast?.('Kunde lokal gespeichert. Profilfoto ausstehend: '+error.message);return false;}};

    window.currentCustomerEdit=()=>customerDraft();
    window.customerFormSaved=()=>{if(customerEdit){customerEdit.pending=null;HGFormDrafts.remove(customerEdit,{formId:'customer-form',page:'customer-edit'});}customerEdit=null;};
    window.resumeCustomerEdit=edit=>{customerEdit=edit;const custom=edit.values.filter(f=>!f.name&&!f.id);get('custom-fields').replaceChildren();for(let i=0;i<custom.length;i+=2)if(custom[i+1])addCustomField(custom[i].value,custom[i+1].value);HGFormDrafts.applyFields(customerForm,edit.values);editingCustomer=HGOwnerClient.clone(edit.context.base);window.orderDraftForCustomer=edit.context.parent;window.openPage('customer-edit');customerForm.dataset.editSession=edit.session_id;HGFormDrafts.syncLock(edit);};
    const discard=document.createElement('button');discard.type='button';discard.className='secondary';discard.id='customer-discard';discard.textContent='Änderungen verwerfen';customerForm.append(discard);
    discard.onclick=()=>{try{const edit=customerDraft();if(edit.pending){window.showUndoToast?.('Zuerst das Ergebnis derselben Kundenspeicherung prüfen.');return;}if(edit.dirty&&!confirm('Nur diese ungespeicherten Kundenänderungen verwerfen? Gespeicherte Kunden bleiben erhalten.'))return;HGFormDrafts.remove(edit,{discard:true,formId:'customer-form',page:'customer-edit'});const base=edit.context.base;customerEdit=null;if(base){openCustomer(base.id);}else window.handleAndroidBack();}catch(_){window.showUndoToast?.('Verwerfen konnte noch nicht bestätigt werden. Eingaben bleiben erhalten.');}};
    const previousOpen=window.openPage;
    window.openPage=function(id,...rest){
      const before=currentPage,previousFocus=document.activeElement;
      if(id!==before&&before==='customer-edit'&&customerEdit&&!retainCustomer())return false;
      previousOpen(id,...rest);
      if(currentPage===before)return;
      if(previousFocus===document.activeElement&&previousFocus?.matches('input,textarea,select'))previousFocus.blur();
      if(currentPage==='customer-edit'){if(!customerEdit||customerEdit.confirmed)customerEdit=HGFormDrafts.start('customer',HGFormDrafts.fields(customerForm),{base:editingCustomer?HGOwnerClient.clone(editingCustomer):null,parent:window.orderDraftForCustomer||null});customerForm.dataset.editSession=customerEdit.session_id;}
      else if(before==='customer-edit')customerEdit=null;
      if(currentPage==='settings-page'){document.querySelectorAll('.nav').forEach(x=>x.classList.toggle('active',x.dataset.open==='more-page'));updateSettings();}
      return true;
    };
    // Modal cancellation uses existing cancel/close handlers, including editor validation.
    const previousBack=window.handleAndroidBack;
    window.handleAndroidBack=function(){
      const dialogs=[...document.querySelectorAll('dialog[open]')],dialog=dialogs.at(-1);
      if(dialog){const event=new Event('cancel',{cancelable:true});if(dialog.dispatchEvent(event))dialog.close();return true;}
      return previousBack();
    };
    document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!document.querySelector('dialog[open]')){event.preventDefault();window.handleAndroidBack();}});
    function updateSettings(){
      const pending=Math.max(0,Number(syncInfo.pending??state.sync_queue?.pending_upload??state.pending?.length??0)||0),review=Math.max(0,Number(syncInfo.review??state.sync_queue?.review??0)||0),local=Math.max(pending+review,Number(syncInfo.pending_total??state.pending?.length??0)||0);
      const sourceTitle=get('sync-title')?.textContent||get('sync-status')?.textContent||'Drive wird geprüft',sourceDetail=get('sync-detail')?.textContent||'Noch keine Statusdetails verfügbar.';
      get('settings-sync-summary').textContent=sourceTitle;get('settings-sync-detail').textContent=sourceDetail+(syncInfo.background_work_pending?' Lokale Geschäftsdaten bleiben gespeichert. Hintergrundarbeit ausstehend; Sicherung öffnen oder erneut versuchen.':'');
      const syncState=get('settings-sync-state');syncState.className='settings-state';
      if(syncInfo.last_error)syncState.classList.add('is-error');else if(review||syncInfo.user_action_required||syncInfo.state==='action_required')syncState.classList.add('is-pending');else if(syncInfo.syncing||pending||!syncInfo.configured)syncState.classList.add('is-busy');else if(syncInfo.last_sync)syncState.classList.add('is-ok');
      get('settings-sync-local').textContent=local?local+' lokale Änderung'+(local===1?'':'en')+' · '+pending+' zur Übertragung · '+review+' zur Prüfung':'Keine lokalen Änderungen in der Warteschlange';
      let transfer='Drive noch nicht eingerichtet';
      if(syncInfo.last_error)transfer='Fehler · lokal gespeicherte Daten bleiben erhalten';
      else if(syncInfo.syncing)transfer='Abgleich läuft';
      else if(review)transfer=review+' Änderung'+(review===1?'':'en')+' benötigt Prüfung';
      else if(pending)transfer=pending+' Änderung'+(pending===1?' wartet':'en warten')+' auf Drive';
      else if(syncInfo.cloud_confirmed)transfer='Online in Google Drive bestätigt';
      else if(syncInfo.last_sync&&syncInfo.confirmation_scope==='provider_readback')transfer='An Drive-App übergeben und zurückgelesen';
      else if(syncInfo.last_sync)transfer='Drive-Abgleich von der App bestätigt';
      else if(syncInfo.configured)transfer='Eingerichtet · erster Abgleich ausstehend';
      get('settings-sync-transfer').textContent=transfer;
      let last='Noch kein erfolgreicher Abgleich';
      if(syncInfo.last_sync){const date=new Date(syncInfo.last_sync);last=Number.isNaN(date.getTime())?'Erfolgreicher Zeitpunkt nicht lesbar':date.toLocaleString('de-AT',{dateStyle:'medium',timeStyle:'short'});}
      get('settings-sync-last').textContent=last;
      if(currentPage==='settings-page')try{const result=JSON.parse(AndroidData.ownerCall('get_planning_settings','{}'));if(result.ok){const info=result.data.integration||{},s=result.data.settings||{};get('settings-planning-state').textContent=(info.calendar_configured?'Google Kalender aktiv':info.calendar_connected?'Google Kalender pausiert':'Google Kalender nicht verbunden')+' · Wetter '+(s.weather_enabled?'EIN':'AUS');}}catch(_){}
      let connection=get('pixel-tunnel-state')?.textContent||'Verbindung noch nicht eingerichtet.';
      try{const info=JSON.parse(AndroidData.getPixelTunnelStatus());connection=HGBusinessFeedback.connection(info);}catch(_){}
      get('settings-connection-state').textContent=connection;
      try{const info=JSON.parse(AndroidData.getAppInfo());get('settings-version').textContent='Haus & Garten App · Version '+(info.versionName||'unbekannt')+(info.versionCode?' · Build '+info.versionCode:'');}catch(_){get('settings-version').textContent='Versionsangabe derzeit nicht verfügbar.';}
    }
    // Background service may finish a sync without an Activity callback. Refresh safe root screens only.
    let lastRefresh='',lastBusinessRefresh=JSON.stringify([syncInfo.last_sync||'',syncInfo.pending||0,syncInfo.review||0,syncInfo.pending_total||0]);
    function refresh(){
      if(document.hidden)return;
      try{const native=JSON.parse(AndroidData.getSyncStatus()),signature=JSON.stringify(native);
        if(signature!==lastRefresh){lastRefresh=signature;const businessRevision=JSON.stringify([native.last_sync||'',native.pending||0,native.review||0,native.pending_total||0]),dataChanged=businessRevision!==lastBusinessRefresh;syncInfo={...syncInfo,...native};if(dataChanged&&['home','customers','worklogs','orders','sync-page','settings-page','more-page'].includes(currentPage)&&!document.querySelector('dialog[open]')){lastBusinessRefresh=businessRevision;load();}else syncLabel();updateSettings();}
        else if(currentPage==='settings-page')updateSettings();
      }catch(_){}
    }
    document.addEventListener('visibilitychange',refresh);setInterval(refresh,5000);
    document.body.dataset.usabilityVersion='1.41';updateSettings();
  }
  document.addEventListener('DOMContentLoaded',()=>setTimeout(install,120),{once:true});
})();

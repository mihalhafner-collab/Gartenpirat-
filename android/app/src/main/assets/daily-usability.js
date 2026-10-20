/* Daily navigation and reachability. Existing forms and native save APIs remain authoritative. */
(() => {
  'use strict';
  const get=id=>document.getElementById(id), roots=['home','customers','worklogs'];
  const preference='hausgarten.ui.side_swipe.v1';
  let swipePreference=false;
  try{swipePreference=localStorage.getItem(preference)==='true';}catch(_){/* Keep the safe in-memory default. */}
  function swipeEnabled(){return swipePreference;}
  function formState(){return JSON.stringify([...get('customer-form').elements].filter(x=>x.name||x.classList.contains('custom-name')||x.classList.contains('custom-value')).map(x=>[x.name||x.className,x.type==='checkbox'?x.checked:x.value]));}
  function install(){
    if(get('settings-page'))return;
    const page=document.createElement('section');page.id='settings-page';page.className='page';
    page.innerHTML='<div class="page-head"><button type="button" class="back" aria-label="Zurück">‹</button><h2>Einstellungen</h2></div>'+
      '<section class="card settings-section" aria-labelledby="settings-operation-title"><h3 id="settings-operation-title">Bedienung</h3><label class="check"><input id="setting-side-swipe" type="checkbox">Seitlich zwischen Heute, Kunden und Arbeiten wischen</label><p class="sub">Im freien Seitenbereich wischen. Die Zurück-Geste am Bildschirmrand und Aktionen an Einträgen bleiben getrennt.</p><p id="settings-preference-state" class="settings-note" role="status" aria-live="polite">Diese Einstellung wird nur auf diesem Gerät gespeichert.</p></section>'+
      '<section class="card settings-section" aria-labelledby="settings-data-title"><h3 id="settings-data-title">Google Drive</h3><div id="settings-sync-state" class="settings-state" role="status" aria-live="polite"><strong id="settings-sync-summary">Drive wird geprüft</strong><span id="settings-sync-detail">Der lokale und der übertragene Stand werden getrennt angezeigt.</span></div><details class="settings-advanced"><summary>Erweitert / Diagnose</summary><dl class="settings-diagnostics"><div><dt>Lokale Änderungen</dt><dd id="settings-sync-local">Wird geprüft</dd></div><div><dt>Übertragungsstand</dt><dd id="settings-sync-transfer">Wird geprüft</dd></div><div><dt>Letzter erfolgreicher Stand</dt><dd id="settings-sync-last">Wird geprüft</dd></div></dl></details><button type="button" class="secondary" data-open="sync-page">Synchronisierung öffnen</button><p class="sub">Die Synchronisierung gleicht deine Arbeit ab. Eine vollständige Sicherung findest du separat unter Sicherung.</p></section>'+
      '<section class="card settings-section" aria-labelledby="settings-connection-title"><h3 id="settings-connection-title">ChatGPT-Steuerung</h3><p id="settings-connection-state" class="sub" role="status" aria-live="polite">Verbindungsstatus wird geprüft.</p><button type="button" class="secondary" id="settings-connection">ChatGPT EIN / AUS</button><p class="sub">Die Freigabe und das Ein- oder Ausschalten erfolgen in der bestehenden Verbindung.</p></section>'+
      '<section class="card settings-section settings-about" aria-labelledby="settings-about-title"><h3 id="settings-about-title">App-Information</h3><p id="settings-version" class="sub">Versionsangabe wird geprüft.</p></section>';
    document.querySelector('main.app').append(page);
    const more=document.createElement('button');more.type='button';more.dataset.open='settings-page';more.innerHTML='<span><strong>Einstellungen</strong><small>Bedienung, Daten und Verbindung</small></span><b aria-hidden="true">›</b>';get('more-page').querySelector('.more-destinations').append(more);
    const shortcut=document.createElement('button');shortcut.type='button';shortcut.className='daily-settings';shortcut.dataset.open='settings-page';shortcut.setAttribute('aria-label','Einstellungen öffnen');shortcut.textContent='⚙';document.querySelector('#home .brand').append(shortcut);
    get('setting-side-swipe').checked=swipeEnabled();
    get('setting-side-swipe').addEventListener('change',event=>{
      const previous=swipePreference,wanted=event.target.checked;
      try{localStorage.setItem(preference,String(wanted));swipePreference=wanted;get('settings-preference-state').textContent='Gespeichert. Die Einstellung gilt auch nach dem nächsten App-Start.';get('settings-preference-state').classList.remove('is-error');}
      catch(_){swipePreference=previous;event.target.checked=previous;get('settings-preference-state').textContent='Nicht gespeichert. Die bisherige Einstellung bleibt aktiv.';get('settings-preference-state').classList.add('is-error');window.showUndoToast('Einstellung konnte nicht dauerhaft gespeichert werden.');}
    });
    const diagnosis=document.createElement('details');diagnosis.innerHTML='<summary>Erweitert / Diagnose · Fehlermeldungen</summary><pre class="settings-error-details"></pre>';page.append(diagnosis);diagnosis.ontoggle=()=>{if(diagnosis.open)diagnosis.querySelector('pre').textContent=HGBusinessFeedback.diagnostics().join('\n\n')||'Keine technischen Fehlermeldungen in dieser Sitzung.';};
    get('settings-connection').onclick=()=>get('pixel-tunnel-setup')?.click();
    // Move, never duplicate, the existing inputs and handlers into reachable toolbars.
    for(const [id,searchId,addId,label] of [['customers','customer-search','add-customer','+ Kunde'],['worklogs','worklog-search','design-add-work','+ Arbeit'],['orders','order-search','add-order','+ Auftrag']]){
      const toolbar=document.createElement('div');toolbar.className='daily-list-tools';toolbar.setAttribute('aria-label',label.slice(2)+' suchen oder hinzufügen');toolbar.id='daily-tools-'+id;
      const search=get(searchId),add=get(addId);if(search){search.setAttribute('aria-label',search.placeholder||'Suchen');toolbar.append(search);}if(add){add.className='primary daily-add';add.textContent=label;toolbar.append(add);}get(id).append(toolbar);get(id).classList.add('daily-list-page');
    }
    get('add-worklog').hidden=true;
    let initialCustomer='',customerTouched=false;
    for(const event of ['input','change'])get('customer-form').addEventListener(event,()=>{customerTouched=true;});
    window.customerFormSaved=()=>{initialCustomer=formState();customerTouched=false;};
    const previousOpen=window.openPage;
    window.openPage=function(id,...rest){
      const before=currentPage,previousFocus=document.activeElement;
      if(id!==before&&before==='customer-edit'&&customerTouched&&initialCustomer!==formState()&&!confirm('Ungespeicherte Kundendaten verwerfen? Mit Abbrechen bleibst du bei deiner Eingabe.'))return;
      previousOpen(id,...rest);
      if(currentPage===before)return;
      if(previousFocus===document.activeElement&&previousFocus?.matches('input,textarea,select'))previousFocus.blur();
      if(currentPage==='customer-edit'){initialCustomer=formState();customerTouched=false;setTimeout(()=>{if(currentPage==='customer-edit'&&!customerTouched)initialCustomer=formState();},0);}
      if(before==='customers'||currentPage==='customers'){get('customer-search').value='';get('customer-search').dispatchEvent(new Event('input',{bubbles:true}));}
      if(currentPage==='settings-page'){document.querySelectorAll('.nav').forEach(x=>x.classList.toggle('active',x.dataset.open==='more-page'));updateSettings();}
    };
    // Modal cancellation uses existing cancel/close handlers, including editor validation.
    const previousBack=window.handleAndroidBack;
    window.handleAndroidBack=function(){
      const dialogs=[...document.querySelectorAll('dialog[open]')],dialog=dialogs.at(-1);
      if(dialog){const event=new Event('cancel',{cancelable:true});if(dialog.dispatchEvent(event))dialog.close();return true;}
      return previousBack();
    };
    document.addEventListener('keydown',event=>{if(event.key==='Escape'&&!document.querySelector('dialog[open]')){event.preventDefault();window.handleAndroidBack();}});
    let gesture=null;
    const excluded='input,textarea,select,button,a,summary,dialog,.swipe-shell,[data-swipe-item],.customer-card,.worklog-entry,.order-list-item,.invoice-entry,.bottom,.daily-list-tools';
    document.addEventListener('touchstart',event=>{
      gesture=null;if(!swipeEnabled()||!roots.includes(currentPage)||event.touches.length!==1||event.target.closest(excluded))return;
      const touch=event.touches[0];if(touch.clientX<32||touch.clientX>innerWidth-32)return;
      gesture={x:touch.clientX,y:touch.clientY,time:performance.now(),page:currentPage};
    },{passive:true});
    document.addEventListener('touchmove',event=>{
      if(!gesture||event.touches.length!==1)return;
      const t=event.touches[0],dx=t.clientX-gesture.x,dy=t.clientY-gesture.y;
      if(Math.abs(dy)>30&&Math.abs(dy)>Math.abs(dx))gesture=null;
      else if(Math.abs(dx)>35&&Math.abs(dx)>Math.abs(dy)*1.6)event.preventDefault();
    },{passive:false});
    document.addEventListener('touchend',event=>{
      const start=gesture;gesture=null;if(!start||!swipeEnabled()||start.page!==currentPage||performance.now()-start.time>800||event.changedTouches.length!==1)return;
      const t=event.changedTouches[0],dx=t.clientX-start.x,dy=t.clientY-start.y;if(Math.abs(dx)<80||Math.abs(dx)<Math.abs(dy)*1.6)return;
      const next=roots[roots.indexOf(currentPage)+(dx<0?1:-1)];if(next)openPage(next);
    },{passive:true});
    document.addEventListener('touchcancel',()=>gesture=null,{passive:true});
    function updateSettings(){
      const pending=Math.max(0,Number(syncInfo.pending??state.sync_queue?.pending_upload??state.pending?.length??0)||0),review=Math.max(0,Number(syncInfo.review??state.sync_queue?.review??0)||0),local=Math.max(pending+review,Number(syncInfo.pending_total??state.pending?.length??0)||0);
      const sourceTitle=get('sync-title')?.textContent||get('sync-status')?.textContent||'Drive wird geprüft',sourceDetail=get('sync-detail')?.textContent||'Noch keine Statusdetails verfügbar.';
      get('settings-sync-summary').textContent=sourceTitle;get('settings-sync-detail').textContent=sourceDetail;
      const syncState=get('settings-sync-state');syncState.className='settings-state';
      if(syncInfo.last_error)syncState.classList.add('is-error');else if(syncInfo.syncing||review||pending||!syncInfo.configured)syncState.classList.add('is-pending');else if(syncInfo.last_sync)syncState.classList.add('is-ok');
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
      let connection=get('pixel-tunnel-state')?.textContent||'Verbindung noch nicht eingerichtet.';
      try{const info=JSON.parse(AndroidData.getPixelTunnelStatus());connection=HGBusinessFeedback.connection(info);}catch(_){}
      get('settings-connection-state').textContent=connection;
      try{const info=JSON.parse(AndroidData.getAppInfo());get('settings-version').textContent='Haus & Garten App · Version '+(info.versionName||'unbekannt')+(info.versionCode?' · Build '+info.versionCode:'');}catch(_){get('settings-version').textContent='Versionsangabe derzeit nicht verfügbar.';}
    }
    // Background service may finish a sync without an Activity callback. Refresh safe root screens only.
    let lastRefresh='';
    function refresh(){
      if(document.hidden)return;
      try{const native=JSON.parse(AndroidData.getSyncStatus()),signature=JSON.stringify(native);
        if(signature!==lastRefresh){lastRefresh=signature;syncInfo={...syncInfo,...native};if(['home','customers','worklogs','orders','sync-page','settings-page','more-page'].includes(currentPage)&&!document.querySelector('dialog[open]'))load();else syncLabel();updateSettings();}
        else if(currentPage==='settings-page')updateSettings();
      }catch(_){}
    }
    document.addEventListener('visibilitychange',refresh);setInterval(refresh,5000);
    document.body.dataset.usabilityVersion='1.41';updateSettings();
  }
  document.addEventListener('DOMContentLoaded',()=>setTimeout(install,120),{once:true});
})();

/* Daily navigation and reachability. Existing forms and native save APIs remain authoritative. */
(() => {
  'use strict';
  const get=id=>document.getElementById(id), roots=['home','customers','worklogs'];
  const preference='hausgarten.ui.side_swipe.v1';
  function swipeEnabled(){try{return localStorage.getItem(preference)==='true';}catch(_){return false;}}
  function formState(){return JSON.stringify([...get('customer-form').elements].filter(x=>x.name||x.classList.contains('custom-name')||x.classList.contains('custom-value')).map(x=>[x.name||x.className,x.type==='checkbox'?x.checked:x.value]));}
  function install(){
    if(get('settings-page'))return;
    const page=document.createElement('section');page.id='settings-page';page.className='page';
    page.innerHTML='<div class="page-head"><button type="button" class="back" aria-label="Zurück">‹</button><h2>Einstellungen</h2></div><section class="card"><h3>Bedienung</h3><label class="check"><input id="setting-side-swipe" type="checkbox">Seitlich zwischen Heute, Kunden und Arbeiten wischen</label><p class="sub">Im freien Seitenbereich wischen. Die Zurück-Geste am Bildschirmrand und Aktionen an Einträgen bleiben getrennt.</p></section><section class="card"><h3>Drive & Daten</h3><p id="settings-sync-state" class="sub" role="status"></p><button type="button" class="secondary" data-open="sync-page">Drive-Abgleich & ausstehende Änderungen</button><p class="sub">Der bestehende Drive-Ordner bleibt erhalten. Lokale Eingaben bleiben bei fehlender Verbindung gespeichert. Sicherungen der Änderungsdateien werden vor dem Überschreiben angelegt.</p></section><section class="card"><h3>ChatGPT</h3><p class="sub">Die vorhandene Pixel-Verbindung bleibt unverändert.</p><button type="button" class="secondary" id="settings-connection">Verbindung & Akku-Einstellungen</button></section><p id="settings-version" class="sub"></p>';
    document.querySelector('main.app').append(page);
    const more=document.createElement('button');more.type='button';more.dataset.open='settings-page';more.innerHTML='<span><strong>Einstellungen</strong><small>Bedienung, Drive und ChatGPT</small></span><b aria-hidden="true">›</b>';get('more-page').querySelector('.more-destinations').append(more);
    const shortcut=document.createElement('button');shortcut.type='button';shortcut.className='daily-settings';shortcut.dataset.open='settings-page';shortcut.setAttribute('aria-label','Einstellungen öffnen');shortcut.textContent='⚙';document.querySelector('#home .brand').append(shortcut);
    get('setting-side-swipe').checked=swipeEnabled();
    get('setting-side-swipe').addEventListener('change',event=>{try{localStorage.setItem(preference,String(event.target.checked));}catch(_){event.target.checked=false;window.showUndoToast('Einstellung konnte nicht dauerhaft gespeichert werden.');}});
    get('settings-connection').onclick=()=>get('pixel-tunnel-setup')?.click();
    // Move, never duplicate, the existing inputs and handlers into reachable toolbars.
    for(const [id,searchId,addId,label] of [['customers','customer-search','add-customer','+ Kunde'],['worklogs','worklog-search','design-add-work','+ Arbeit'],['orders','order-search','add-order','+ Auftrag']]){
      const toolbar=document.createElement('div');toolbar.className='daily-list-tools';toolbar.setAttribute('aria-label',label.slice(2)+' suchen oder hinzufügen');toolbar.id='daily-tools-'+id;
      const search=get(searchId),add=get(addId);if(search){search.setAttribute('aria-label',search.placeholder||'Suchen');toolbar.append(search);}if(add){add.className='primary daily-add';add.textContent=label;toolbar.append(add);}get(id).append(toolbar);get(id).classList.add('daily-list-page');
    }
    get('add-worklog').hidden=true;
    get('customer-form').elements.active.closest('label').hidden=true;
    let initialCustomer='',customerTouched=false;
    for(const event of ['input','change'])get('customer-form').addEventListener(event,()=>{customerTouched=true;});
    window.customerFormSaved=()=>{initialCustomer=formState();customerTouched=false;};
    const previousOpen=window.openPage;
    window.openPage=function(id,...rest){
      const before=currentPage;
      if(id!==before&&before==='customer-edit'&&customerTouched&&initialCustomer!==formState()&&!confirm('Ungespeicherte Kundendaten verwerfen? Mit Abbrechen bleibst du bei deiner Eingabe.'))return;
      previousOpen(id,...rest);
      if(currentPage===before)return;
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
      get('settings-sync-state').textContent=get('sync-status').textContent;
      try{get('settings-version').textContent='Haus & Garten App · Version '+JSON.parse(AndroidData.getAppInfo()).versionName;}catch(_){}
    }
    // Background service may finish a sync without an Activity callback. Refresh safe root screens only.
    let lastRefresh='';
    function refresh(){
      if(document.hidden)return;
      try{const native=JSON.parse(AndroidData.getSyncStatus()),signature=JSON.stringify(native);
        if(signature!==lastRefresh){lastRefresh=signature;syncInfo={...syncInfo,...native};if(['home','customers','worklogs','orders','sync-page','settings-page','more-page'].includes(currentPage)&&!document.querySelector('dialog[open]'))load();else syncLabel();updateSettings();}
      }catch(_){}
    }
    document.addEventListener('visibilitychange',refresh);setInterval(refresh,5000);
    document.body.dataset.usabilityVersion='1.41';updateSettings();
  }
  document.addEventListener('DOMContentLoaded',()=>setTimeout(install,120),{once:true});
})();

/* Focused daily UI on top of the existing app. Never rewrites stored records. */
(() => {
  'use strict';
  const get=id=>document.getElementById(id);
  window.hasWorkEntryDraft=function(){
    const form=get('entry');if(!form)return false;
    return ['customer_id','order_id','activity','material','notes','location','start_time','end_time','_session_id'].some(name=>String(form.elements[name]?.value||'').trim())||Number(form.elements.hours?.value)>0||Number(form.elements.kilometers?.value)>0||Number(form.elements.break_minutes?.value)>0||!!get('photo')?.files?.length;
  };
  const icons={home:'<path d="m3 10 9-7 9 7v11h-6v-7H9v7H3z"/>',worklogs:'<rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 3h6v4H9zM9 11h6M9 15h6"/>',customers:'<circle cx="12" cy="7" r="4"/><path d="M4 21v-3a8 8 0 0 1 16 0v3"/>',orders:'<rect x="3" y="6" width="18" height="15" rx="2"/><path d="M8 6V3h8v3M3 12h18M10 12v3h4v-3"/>'};
  function configureNavigation(){
    const nav=document.querySelector('.bottom-inner');
    nav.innerHTML=[['home','Heute'],['customers','Kunden'],['worklogs','Arbeiten'],['more-page','Mehr']].map(([id,label])=>`<button class="nav" type="button" data-open="${id}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">${icons[id]||'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>'}</svg>${label}</button>`).join('');
    const more=document.createElement('section');more.id='more-page';more.className='page';more.innerHTML='<div class="page-head"><button type="button" class="back" aria-label="Zurück">‹</button><h2>Mehr</h2></div><div class="card more-destinations">'+[['orders','Projekte & Aufträge','Planung und zugehörige Arbeiten'],['weeks','Wochenstunden','Stunden und Notizen im Überblick'],['invoices','Rechnungen','Entwürfe und ausgestellte Rechnungen'],['sync-page','Drive & Verbindung','Abgleich, ChatGPT und Verbindungsstatus']].map(([id,title,sub])=>`<button type="button" data-open="${id}"><span><strong>${title}</strong><small>${sub}</small></span><b aria-hidden="true">›</b></button>`).join('')+'</div>';document.querySelector('main.app').append(more);
    const sync=get('sync-status');sync.setAttribute('role','button');sync.tabIndex=0;sync.setAttribute('aria-label','Drive-Abgleich öffnen');sync.dataset.open='sync-page';sync.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();openPage('sync-page');}});
    const grid=document.querySelector('#home .folder-grid');
    const drive=document.createElement('button');drive.type='button';drive.className='folder';drive.dataset.open='sync-page';drive.innerHTML='<span class="folder-icon" aria-hidden="true">↻</span><strong>Drive-Abgleich</strong><small>Speicherung & Status</small>';grid.append(drive);
    // Four routine destinations are already in the bottom bar. Keep home shortcuts to history and administration.
    grid.querySelectorAll('button').forEach(button=>button.hidden=true);
    document.querySelector('#home .hero h2').textContent='Dein Arbeitstag';
    document.querySelector('#home .quick strong').textContent='+ Arbeit nachtragen';
    document.querySelector('#home .quick small').textContent='Kunde · Stunden · Tätigkeit';
    const brand=document.querySelector('#home .brand');
    if(brand&&!get('home-dayline')){const line=document.createElement('p');line.id='home-dayline';line.className='home-dayline';line.textContent=new Date().toLocaleDateString('de-AT',{weekday:'long',day:'numeric',month:'long'});brand.after(line);}
  }
  function navigationState(){const section=currentPage==='customer-edit'||currentPage==='customer-profile-page'?'customers':currentPage==='worklog-detail'||currentPage==='work-entry'?'worklogs':['orders','weeks','invoices','offers-page','sync-page'].includes(currentPage)?'more-page':currentPage;document.querySelectorAll('.nav').forEach(button=>{const active=button.dataset.open===section;button.classList.toggle('active',active);active?button.setAttribute('aria-current','page'):button.removeAttribute('aria-current');});}
  function compactFilters(){const panel=get('worklog-filter-panel');if(!panel||panel.querySelector('.workday-filters'))return;const details=document.createElement('details');details.className='more workday-filters';const summary=document.createElement('summary');summary.textContent='Filter · Zeitraum, Kunde, Abrechnung';details.append(summary);const grid=panel.querySelector('.form-grid'),reset=get('worklog-filter-reset');panel.append(details);if(grid)details.append(grid);if(reset)details.append(reset);}
  function compactCustomers(){document.querySelectorAll('.customer-work-section').forEach(section=>{if(section.closest('details'))return;const details=document.createElement('details');details.className='customer-history';const summary=document.createElement('summary');const count=section.querySelector('.customer-work-heading span')?.textContent||'0';summary.textContent=`Arbeitsverlauf · ${count} Einträge`;details.append(summary);section.before(details);details.append(section);});}
  function groupCustomerForm(){
    const form=get('customer-form'),grid=form.querySelector('.form-grid');
    const groups=[['Name & Kontakt',['name','company','phone','email']],['Adresse',['street','postal_code','city']],['Weitere Kundendaten',['first_name','last_name','country','garden_area','sevdesk_customer_number']]];
    for(const [title,names] of groups){const isExtra=title==='Weitere Kundendaten',section=document.createElement(isExtra?'details':'fieldset');section.className=isExtra?'more workday-customer-extra':'customer-form-section';const heading=document.createElement(isExtra?'summary':'legend');heading.textContent=title;const body=document.createElement('div');body.className='form-grid';section.append(heading,body);grid.before(section);for(const name of names){const field=form.elements[name];if(field?.parentElement)body.append(field.parentElement);}}
    grid.remove();
    form.querySelectorAll('input,textarea').forEach(field=>{const label=field.previousElementSibling;if(label?.tagName==='LABEL'){field.id||='customer-field-'+field.name;label.htmlFor=field.id;}});
  }
  function configureWorkPhotoCamera(){
    const input=get('photo');
    if(!input||get('work-photo-camera-shortcut'))return;
    const button=document.createElement('button');
    button.type='button';
    button.id='work-photo-camera-shortcut';
    button.className='secondary inline-add';
    button.textContent='Kamera öffnen';
    button.setAttribute('aria-label','Foto für diesen Arbeitseinsatz aufnehmen');
    input.before(button);
    button.addEventListener('click',()=>{
      if(!get('customer')?.value){
        get('customer')?.focus();
        window.showUndoToast?.('Bitte zuerst einen Kunden auswählen.');
        return;
      }
      input.click();
    });
  }
  function entryForm(){
    configureWorkPhotoCamera();
    const form=get('entry'),hours=form.elements.hours,block=form.querySelectorAll('.entry-block')[1];
    const location=document.createElement('input'),locationLabel=document.createElement('label');location.id='entry-location';location.name='location';locationLabel.htmlFor=location.id;locationLabel.textContent='Arbeitsort';get('date').after(locationLabel,location);
    const hoursLabel=hours.previousElementSibling;hours.id='entry-hours';hours.step='0.01';hoursLabel.htmlFor=hours.id;hoursLabel.textContent='Arbeitsdauer (Stunden)';hours.placeholder='z. B. 5,5 – oder Uhrzeiten unten';block.querySelector('h3').after(hoursLabel,hours);
    hours.addEventListener('input',()=>{if(!get('start').value&&!get('end').value){const duration=Number(hours.value);get('hours-result').textContent=duration>0?`Arbeitszeit: ${duration.toLocaleString('de-AT')} Stunden`:'Arbeitsdauer oder Uhrzeiten eingeben';get('hours-result').setAttribute('role','status');}});
    const add=get('work-entry-add-customer');if(add){add.className='secondary inline-add';add.textContent='+ Neuer Kunde';}
    const order=get('order'),anchor=order.closest('.select-search-wrap')||order,button=document.createElement('button');button.type='button';button.id='work-entry-add-order';button.className='secondary inline-add';button.textContent='+ Neuer Auftrag';anchor.after(button);
    button.addEventListener('click',()=>{const customer=get('customer').value;if(!customer){get('customer').focus();window.showUndoToast('Wähle zuerst den Kunden für den neuen Auftrag.');return;}window.workEntryOrderDraft=true;get('add-order').click();get('order-edit-customer').value=customer;});
    function updateDuration(){
      const start=get('start').value,end=get('end').value;
      hours.readOnly=!!(start&&end);
      if(!hours.readOnly)return;
      const minutes=value=>{const[h,m]=value.split(':').map(Number);return h*60+m;};
      const duration=minutes(end)-minutes(start),pause=Number(get('pause').value||0);
      if(duration<=0||pause<0||pause>=duration){
        hours.value='';
        get('hours-result').textContent=duration<=0?'Die Endzeit muss nach dem Beginn liegen.':'Bitte die Pausenlänge prüfen.';
        get('hours-result').setAttribute('role','alert');
      }else{hours.value=calculateHours();get('hours-result').setAttribute('role','status');}
    }
    ['start','end','pause'].forEach(id=>get(id).addEventListener('input',updateDuration));
    form.addEventListener('reset',()=>{hours.readOnly=false;get('work-session-id')?.remove();});
    form.querySelectorAll('input,select,textarea').forEach(field=>{if(!field.id)field.id='entry-'+field.name;const label=field.previousElementSibling;if(label?.tagName==='LABEL')label.htmlFor=field.id;});
    form.addEventListener('submit',event=>{const start=get('start').value,end=get('end').value,pause=Number(get('pause').value||0);let error='';if(!!start!==!!end)error='Bitte Beginn und Ende gemeinsam angeben – oder nur die Arbeitsdauer.';else if(start&&end){const minutes=value=>{const[h,m]=value.split(':').map(Number);return h*60+m;};const duration=minutes(end)-minutes(start);if(duration<=0)error='Die Endzeit muss nach dem Beginn liegen.';else if(pause>=duration)error='Die Pause muss kürzer als der gesamte Einsatz sein.';else hours.value=calculateHours();}else if(!(Number(hours.value)>0))error='Bitte eine Arbeitsdauer größer als 0 eintragen.';if(error){event.preventDefault();event.stopImmediatePropagation();window.showUndoToast(error);get('hours-result').textContent=error;get('hours-result').setAttribute('role','alert');}},true);
  }
  window.onWorkEntryOrderSaved=function(payload,id){if(!window.workEntryOrderDraft)return false;window.workEntryOrderDraft=false;finishPage('work-entry');get('customer').value=payload.customer_id;ordersForCustomer();get('order').value=id;window.showUndoToast('Neuer Auftrag ausgewählt. Deine Arbeitsangaben bleiben erhalten.');return true;};
  function recentWork(){let card=get('workday-recent');if(!card){card=document.createElement('section');card.id='workday-recent';card.className='card workday-recent';document.querySelector('#home .folder-grid').after(card);}const rows=worklogs().slice().sort((a,b)=>String(b.work_date||'').localeCompare(String(a.work_date||''))||String(b.start_time||'').localeCompare(String(a.start_time||''))).slice(0,3);card.innerHTML='<h3>Zuletzt erfasst</h3>'+(rows.length?rows.map(row=>`<button type="button" class="workday-recent-row" data-edit-worklog="${esc(row.id)}"><strong>${esc(customerName(row.customer_id))}</strong><span>${esc(worklogDateLabel(row.work_date))} · ${esc(String(row.activity||'').slice(0,75))}</span><b>${Number(row.hours||0).toLocaleString('de-AT')} h</b></button>`).join(''):'<p class="workday-empty">Noch keine Arbeit erfasst. Starte oben mit „Arbeit erfassen“.</p>');}
  function versionLabel(){let version='1.27.1';try{const info=JSON.parse(AndroidData.getAppInfo());if(info.versionName)version=info.versionName;}catch(_){/* Older bridge / read-only preview. */}document.querySelector('.brand p').textContent='Betriebsverwaltung · Version '+version;}
  function photoTools(){const card=get('order-photo-card');if(!card||card.closest('details'))return;const details=document.createElement('details');details.className='more workday-photo-tools';const summary=document.createElement('summary');summary.textContent='Fotos zu einem Auftrag hinzufügen';details.append(summary);card.before(details);details.append(card);}
  const sessionRequest=action=>`${action}-${Date.now()}-${crypto.randomUUID?.()||Math.random().toString(16).slice(2)}`;
  function readSession(){try{return JSON.parse(AndroidData.getWorkSession())}catch(_){return {state:'UNAVAILABLE'}}}
  function sessionDuration(value){const seconds=Math.max(0,Math.floor(Number(value||0)/1000));return `${String(Math.floor(seconds/3600)).padStart(2,'0')}:${String(Math.floor(seconds%3600/60)).padStart(2,'0')}:${String(seconds%60).padStart(2,'0')}`;}
  function sessionCall(name,...args){try{const result=AndroidData[name](...args);if(String(result).startsWith('ERROR:'))throw new Error(String(result).slice(6).trim());return JSON.parse(result)}catch(error){window.showUndoToast(error.message||'Die Zeiterfassung konnte nicht geändert werden.');return null;}}
  function workTimer(){
    if(!AndroidData?.getWorkSession)return;
    let panel=get('workday-timer');
    if(!panel){panel=document.createElement('section');panel.id='workday-timer';panel.className='card workday-timer';document.querySelector('#home .hero h2').after(panel);panel.addEventListener('click',event=>{
      const action=event.target.closest('[data-session-action]')?.dataset.sessionAction;if(!action)return;const session=readSession();let next=null;
      if(action==='photo'){
        const customerId=session.customer_id||get('customer')?.value;
        if(customerId&&typeof window.openCustomerDocumentationCamera==='function'){
          if(window.openCustomerDocumentationCamera(customerId,{session_id:session.id,order_id:session.order_id||''}))return;
        }
        openPage('work-entry');
        const customer=get('customer');
        if(session.customer_id&&customer&&[...customer.options].some(option=>option.value===session.customer_id)){
          customer.value=session.customer_id;
          customer.dispatchEvent(new Event('change',{bubbles:true}));
        }
        window.showUndoToast?.('Kunden auswählen und dann „Kamera öffnen“ antippen. Der Timer läuft weiter.');
        customer?.focus();
        return;
      }
      if(action==='start')next=sessionCall('startWorkSession',JSON.stringify({request_id:sessionRequest('start'),work_date:localDate()}));
      if(action==='pause')next=sessionCall('pauseWorkSession',session.id,sessionRequest('pause'));
      if(action==='resume')next=sessionCall('resumeWorkSession',session.id,sessionRequest('resume'));
      if(action==='stop')next=sessionCall('stopWorkSession',session.id,sessionRequest('stop'));
      if(action==='complete'){
        openPage('work-entry');let hidden=get('work-session-id');if(!hidden){hidden=document.createElement('input');hidden.type='hidden';hidden.name='_session_id';hidden.id='work-session-id';get('entry').append(hidden);}hidden.value=session.id;get('date').value=session.work_date||localDate();get('entry').elements.hours.value=Number(session.hours||0).toFixed(2);get('activity').value=session.activity||'';get('hours-result').textContent=`Erfasste Arbeitszeit: ${Number(session.hours||0).toLocaleString('de-AT')} Stunden`;const heading=get('work-entry').querySelector('.page-head h2');if(heading){heading.tabIndex=-1;heading.focus({preventScroll:true});}return;
      }
      if(next)renderTimer(next);
    });}
    renderTimer(readSession());
  }
  function renderTimer(session){
    const panel=get('workday-timer');if(!panel)return;const state=session.state||'IDLE',active=state==='RUNNING',paused=state==='PAUSED',stopped=state==='STOPPED_PENDING_REVIEW';
    panel.innerHTML=state==='UNAVAILABLE'?'<h3>Arbeitszeit</h3><p>Zeiterfassung ist in dieser Vorschau nicht verfügbar.</p>':state==='IDLE'||state==='FINALIZED'?'<div class="timer-title"><small>Schnelle Zeiterfassung</small><h3>Bereit für den Einsatz?</h3><p>Die Zeit läuft auch offline zuverlässig auf diesem Pixel.</p></div><button type="button" class="timer-main timer-start" data-session-action="start"><span>▶</span><b>Start</b><small>Arbeitszeit starten</small></button>':`<div class="timer-state"><small>${active?'Arbeit läuft':paused?'Pausiert':'Einsatz beendet'}</small><strong>${sessionDuration(session.worked_ms)}</strong><span>${active?'Die Zeit läuft auf diesem Pixel weiter.':paused?'Pausenzeit wird nicht mitgerechnet.':'Ergänze jetzt Kunde, Tätigkeit und weitere Angaben.'}</span></div><div class="timer-actions">${active?'<button type="button" class="timer-secondary" data-session-action="pause"><span>Ⅱ</span><b>Pause</b><small>Arbeitszeit pausieren</small></button>':''}${paused?'<button type="button" class="timer-main" data-session-action="resume"><span>▶</span><b>Fortsetzen</b><small>Arbeitszeit fortsetzen</small></button>':''}${active||paused?'<button type="button" class="timer-stop" data-session-action="stop"><span>■</span><b>Stop</b><small>Arbeitszeit beenden</small></button>':''}${active||paused?'<button type="button" class="timer-secondary" data-session-action="photo"><span>▣</span><b>Foto</b><small>Kamera öffnen</small></button>':''}${stopped?'<button type="button" class="timer-main" data-session-action="complete"><span>›</span><b>Angaben ergänzen</b><small>Prüfen und speichern</small></button>':''}</div>`;
  }
  function refresh(){navigationState();compactFilters();compactCustomers();recentWork();photoTools();versionLabel();workTimer();}
  function init(){const stylesheet=document.createElement('link');stylesheet.rel='stylesheet';stylesheet.href='workday-ui.css';document.head.append(stylesheet);configureNavigation();groupCustomerForm();entryForm();const prior=window.render;window.render=function(){prior();refresh();};const priorCustomer=window.renderCustomers;window.renderCustomers=function(rows){priorCustomer(rows);compactCustomers();};const priorPage=window.openPage;window.openPage=function(...args){priorPage(...args);navigationState();if(currentPage==='customer-edit')get('customer-edit').querySelector('h2').textContent=editingCustomer?'Kunde bearbeiten':'Neuer Kunde';};const priorBack=window.handleAndroidBack;window.handleAndroidBack=function(){const wasCustomer=currentPage==='customer-edit',result=priorBack();if(wasCustomer&&currentPage!=='customer-edit'&&!window.orderDraftForCustomer)window.worklogReturnFromCustomer=false;return result;};setInterval(()=>{if(currentPage==='home'&&readSession().state==='RUNNING')renderTimer(readSession());},1000);refresh();}
  // Existing inline code schedules final form tools. Initialise after those timers, once.
  document.addEventListener('DOMContentLoaded',()=>setTimeout(init,0),{once:true});
})();

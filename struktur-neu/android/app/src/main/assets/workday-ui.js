/* Focused daily UI on top of the existing app. Never rewrites stored records. */
(() => {
  'use strict';
  const get=id=>document.getElementById(id);
  window.hasWorkEntryDraft=function(){
    const form=get('entry');if(!form)return false;
    return ['customer_id','order_id','activity','material','notes','location','start_time','end_time','_session_id'].some(name=>String(form.elements[name]?.value||'').trim())||Number(form.elements.hours?.value)>0||Number(form.elements.kilometers?.value)>0||Number(form.elements.break_minutes?.value)>0||!!get('photo')?.files?.length;
  };
  const icons={home:'<path d="m3 10 9-7 9 7v11h-6v-7H9v7H3z"/>','planning-page':'<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4M17 3v4M3 11h18M8 15h3M8 18h7"/>',worklogs:'<rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 3h6v4H9zM9 11h6M9 15h6"/>',customers:'<circle cx="12" cy="7" r="4"/><path d="M4 21v-3a8 8 0 0 1 16 0v3"/>',orders:'<rect x="3" y="6" width="18" height="15" rx="2"/><path d="M8 6V3h8v3M3 12h18M10 12v3h4v-3"/>'};
  function configureNavigation(){
    const nav=document.querySelector('.bottom-inner');
    nav.innerHTML=[['home','Heute'],['customers','Kunden & Gärten'],['more-page','Mehr']].map(([id,label])=>`<button class="nav" type="button" data-open="${id}"${id==='planning-page'?' aria-label="Planung und Kalender"':''}><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">${icons[id]||'<path d="M4 6h16M4 12h16M4 18h16"/>'}</svg>${label}</button>`).join('');
    const more=document.createElement('section');more.id='more-page';more.className='page';more.innerHTML='<div class="page-head"><button type="button" class="back" aria-label="Zurück">‹</button><h2>Mehr</h2></div><div class="card more-destinations">'+[['planning-page','Planung','Termine und Einsatzgebiete'],['worklogs','Arbeiten','Gespeicherte Zeiten, Leistungen und Notizen'],['orders','Aufträge','Planung und zugehörige Arbeiten'],['weeks','Wochenstunden','Stunden und Notizen im Überblick'],['invoices','Rechnungen','Entwürfe und ausgestellte Rechnungen'],['sync-page','Historische Vorgänge','Erhaltene offene Prüfungen und lokale Sicherung']].map(([id,title,sub])=>`<button type="button" data-open="${id}"><span><strong>${title}</strong><small>${sub}</small></span><b aria-hidden="true">›</b></button>`).join('')+'</div>';document.querySelector('main.app').append(more);
    const sync=get('sync-status');sync.setAttribute('role','button');sync.tabIndex=0;sync.setAttribute('aria-label','Vollsicherung öffnen');sync.dataset.openFullBackup='';sync.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();window.openFullBackupSettings?.();}});
    more.querySelector('h2').textContent='Mehr & Einstellungen';
    const backbar=document.createElement('div');backbar.className='app-context-back';backbar.hidden=true;backbar.innerHTML='<button type="button" class="secondary" aria-label="Eine Ansicht zurück">‹ Zurück</button>';backbar.querySelector('button').onclick=()=>window.handleAndroidBack();document.body.append(backbar);new MutationObserver(changes=>{if(changes.some(c=>c.target.tagName==='DIALOG'))navigationState();}).observe(document.body,{subtree:true,attributes:true,attributeFilter:['open']});
    const bottom=document.querySelector('.bottom');new ResizeObserver(()=>{document.documentElement.style.setProperty('--app-nav-height',Math.ceil(bottom.getBoundingClientRect().height)+'px');}).observe(bottom);
  }
  function navigationState(){const section=['customers','customer-edit','customer-profile-page'].includes(currentPage)?'customers':['home','planning-page','work-entry'].includes(currentPage)?'home':'more-page';document.querySelectorAll('.nav').forEach(button=>{const active=button.dataset.open===section;button.classList.toggle('active',active);active?button.setAttribute('aria-current','page'):button.removeAttribute('aria-current');});
    const page=document.getElementById(currentPage),header=page?.querySelector(':scope>.page-head .back'),headerBack=!['home','customers','more-page'].includes(currentPage)&&!!header?.getClientRects().length;page?.classList.toggle('has-header-back',headerBack);
    const back=get('settings-page')&&document.querySelector('.app-context-back');if(back)back.hidden=headerBack||!['settings-page','sync-page','photo-library-page','weeks','invoices','offers-page','billing-page','trash-page','status-page'].includes(currentPage)||!!document.querySelector('dialog[open]');
    document.body.classList.toggle('app-planning-editing',currentPage==='planning-page'&&get('planning-page')?.dataset.planningMode==='edit');
  }
  window.HGNavigation=Object.freeze({update:navigationState});
  function compactFilters(){const panel=get('worklog-filter-panel');if(!panel||panel.querySelector('.workday-filters'))return;const details=document.createElement('details');details.className='more workday-filters';const summary=document.createElement('summary');summary.textContent='Filter · Zeitraum, Kunde, Abrechnung';details.append(summary);const grid=panel.querySelector('.form-grid'),reset=get('worklog-filter-reset');panel.append(details);if(grid)details.append(grid);if(reset)details.append(reset);}
  function groupCustomerForm(){
    const form=get('customer-form'),grid=form.querySelector('.form-grid');
    const groups=[['Name & Kontakt',['name','company','phone','email']],['Adresse',['street','postal_code','city']],['Weitere Kundendaten',['first_name','last_name','country','garden_area','sevdesk_customer_number']]];
    for(const [title,names] of groups){const isExtra=title==='Weitere Kundendaten',section=document.createElement(isExtra?'details':'fieldset');section.className=isExtra?'more workday-customer-extra':'customer-form-section';const heading=document.createElement(isExtra?'summary':'legend');heading.textContent=title;const body=document.createElement('div');body.className='form-grid';section.append(heading,body);grid.before(section);for(const name of names){const field=form.elements[name];if(field?.parentElement)body.append(field.parentElement);}}
    grid.remove();
    form.querySelectorAll('input,textarea').forEach(field=>{const label=field.previousElementSibling;if(label?.tagName==='LABEL'){if(!field.id)field.id='customer-field-'+field.name;label.htmlFor=field.id;}});
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
  window.onWorkEntryOrderSaved=function(payload,id){if(!window.workEntryOrderDraft)return false;window.workEntryOrderDraft=false;finishPage('work-entry');get('customer').value=payload.customer_id;ordersForCustomer();get('order').value=id;window.retainWorkEntryDraft?.();window.showUndoToast('Neuer Auftrag ausgewählt. Deine Arbeitsangaben bleiben erhalten.');return true;};
  function versionLabel(){let version='1.27.1';try{const info=JSON.parse(AndroidData.getAppInfo());if(info.versionName)version=info.versionName;}catch(_){/* Older bridge / read-only preview. */}document.querySelector('.brand p').textContent='Betriebsverwaltung · Version '+version;}
  function photoTools(){const card=get('order-photo-card');if(!card||card.closest('details'))return;const details=document.createElement('details');details.className='more workday-photo-tools';const summary=document.createElement('summary');summary.textContent='Fotos zu einem Auftrag hinzufügen';details.append(summary);card.before(details);details.append(card);}
  function refresh(){navigationState();compactFilters();photoTools();versionLabel();}
  function init(){const stylesheet=document.createElement('link');stylesheet.rel='stylesheet';stylesheet.href='workday-ui.css';document.head.append(stylesheet);configureNavigation();groupCustomerForm();entryForm();const prior=window.render;window.render=function(){prior();refresh();};const priorPage=window.openPage;window.openPage=function(...args){priorPage(...args);navigationState();if(currentPage==='customer-edit')get('customer-edit').querySelector('h2').textContent=editingCustomer?'Kunde bearbeiten':'Neuer Kunde';};const priorBack=window.handleAndroidBack;window.handleAndroidBack=function(){const wasCustomer=currentPage==='customer-edit',result=priorBack();if(wasCustomer&&currentPage!=='customer-edit'&&!window.orderDraftForCustomer)window.worklogReturnFromCustomer=false;return result;};refresh();}
  // Existing inline code schedules final form tools. Initialise after those timers, once.
  document.addEventListener('DOMContentLoaded',()=>setTimeout(init,0),{once:true});
})();

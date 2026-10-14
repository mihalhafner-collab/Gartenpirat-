/* A compact order overview with reachable actions. Existing storage stays unchanged. */
(() => {
  'use strict';
  const fieldIds = ['order-edit-select','order-edit-customer','order-edit-title','order-edit-description','order-edit-location','order-edit-date','order-edit-status','order-edit-visible'];
  let active = null;
  const baseOpen = window.openDirectEditWindow;
  const baseClose = window.closeDirectEditWindow;
  const snapshot = () => fieldIds.map(id => {const input=document.getElementById(id);return [id,input?.type==='checkbox'?input.checked:input?.value||''];});
  function restore(values) {for(const [id,value] of values){const input=document.getElementById(id);if(input)input.type==='checkbox'?input.checked=value:input.value=value;}}
  function changed(){return active && JSON.stringify(snapshot())!==JSON.stringify(active.initial);}
  function detail(label,value){const item=document.createElement('div'),term=document.createElement('dt'),text=document.createElement('dd');term.textContent=label;text.textContent=String(value??'').trim()||'Noch nicht angegeben';item.append(term,text);return item;}
  function showOverview(){
    if(!active)return;
    const {card,summary,dialog,footer}=active;
    summary.replaceChildren();
    const heading=document.createElement('h3');heading.textContent=document.getElementById('order-edit-title').value||'Projekt / Auftrag';
    const list=document.createElement('dl');
    const customer=document.getElementById('order-edit-customer');
    const values=[['Kunde',customer.selectedOptions[0]?.textContent],['Status',document.getElementById('order-edit-status').value],['Termin',document.getElementById('order-edit-date').value?new Date(document.getElementById('order-edit-date').value+'T12:00:00').toLocaleDateString('de-AT'):''],['Einsatzort',document.getElementById('order-edit-location').value],['Beschreibung',document.getElementById('order-edit-description').value]];
    values.forEach(([label,value])=>list.appendChild(detail(label,value)));
    summary.append(heading,list);
    active.mode='overview';card.hidden=true;summary.hidden=false;dialog.querySelector('#direct-edit-title').textContent='Auftrag im Überblick';
    footer.querySelector('[data-order-window-edit]').hidden=false;footer.querySelector('[data-order-window-save]').hidden=true;
    footer.querySelector('[data-order-window-back]').textContent='Schließen';
    active.status.textContent='Zum Ändern unten auf „Bearbeiten“ tippen.';
    dialog.querySelector('.direct-edit-content').scrollTop=0;
  }
  function showEditor(){
    if(!active)return;
    active.mode='edit';active.card.hidden=false;active.summary.hidden=true;active.dialog.querySelector('#direct-edit-title').textContent=active.isNew?'Auftrag anlegen':'Auftrag bearbeiten';
    active.footer.querySelector('[data-order-window-edit]').hidden=true;active.footer.querySelector('[data-order-window-save]').hidden=false;
    active.footer.querySelector('[data-order-window-back]').textContent='Abbrechen';
    active.status.textContent='Änderungen anschließend unten speichern.';
    active.dialog.querySelector('.direct-edit-content').scrollTop=0;
  }
  function cleanup(){
    if(!active)return;
    const {card,summary,footer,dialog,hiddenNodes}=active;
    card.hidden=false;hiddenNodes.forEach(([node,hidden])=>node.hidden=hidden);summary.remove();footer.remove();dialog.classList.remove('order-window-dialog');active=null;
  }
  function goBack(){
    if(!active)return false;
    if(active.mode==='edit'&&!active.isNew){if(changed()&&!confirm('Änderungen verwerfen und zur Auftragsübersicht zurückgehen?'))return true;restore(active.initial);showOverview();return true;}
    window.closeDirectEditWindow();return true;
  }
  window.handleOrderWindowBack=goBack;
  window.openDirectEditWindow=function(cardId,title,options={}){
    baseOpen(cardId,title);
    if(cardId!=='order-management-card')return;
    cleanup();
    const dialog=document.getElementById('direct-edit-dialog'),card=document.getElementById(cardId);
    if(!dialog?.open||!card)return;
    const summary=document.createElement('section');summary.className='order-window-summary';summary.setAttribute('aria-label','Auftragsdetails');
    const footer=document.createElement('footer');footer.className='order-window-actions';footer.innerHTML='<p role="status" aria-live="polite"></p><div><button type="button" class="secondary" data-order-window-back>Schließen</button><button type="button" class="primary" data-order-window-edit>Bearbeiten</button><button type="button" class="primary" data-order-window-save hidden>Änderungen speichern</button></div>';
    const selected=document.getElementById('order-edit-select');
    const hiddenNodes=[selected.closest('.select-search-wrap')||selected,card.querySelector('h3'),card.querySelector('p.sub'),document.getElementById('order-edit-save')].filter(Boolean).map(node=>[node,node.hidden]);
    hiddenNodes.forEach(([node])=>node.hidden=true);
    fieldIds.slice(1).forEach(id=>{const input=document.getElementById(id),label=input?.closest('label')||input?.previousElementSibling;if(label?.tagName==='LABEL')label.htmlFor=id;});
    dialog.classList.add('order-window-dialog');dialog.querySelector('.direct-edit-content').prepend(summary);dialog.append(footer);
    active={dialog,card,summary,footer,hiddenNodes,initial:snapshot(),isNew:!selected.value,status:footer.querySelector('p'),mode:'overview'};
    footer.querySelector('[data-order-window-edit]').addEventListener('click',showEditor);
    footer.querySelector('[data-order-window-back]').addEventListener('click',goBack);
    footer.querySelector('[data-order-window-save]').addEventListener('click',()=>document.getElementById('order-edit-save').click());
    dialog.addEventListener('close',cleanup,{once:true});
    if(options.edit||active.isNew)showEditor();else showOverview();
  };
  window.closeDirectEditWindow=function(force=false){
    if(active&&changed()&&!force&&!confirm('Ungespeicherte Auftragsänderungen verwerfen?'))return false;
    const result=baseClose(force);if(result!==false){if(!force&&!window.orderDraftForCustomer)window.workEntryOrderDraft=false;cleanup();}return result;
  };
  window.markOrderWindowSaved=function(){if(active)active.initial=snapshot();};
  const priorBack=window.handleAndroidBack;window.handleAndroidBack=function(){if(active?.dialog.open)return goBack();return priorBack();};
  // Creating a customer is a deliberate round trip; the existing handler preserves this draft.
  document.addEventListener('click',event=>{if(active&&event.target.closest('#order-add-customer'))active.initial=snapshot();},true);
  document.addEventListener('click',event=>{
    if(!event.target.closest('#add-order'))return;
    event.preventDefault();event.stopImmediatePropagation();
    ['order-edit-select','order-edit-customer','order-edit-title','order-edit-description','order-edit-location','order-edit-date'].forEach(id=>document.getElementById(id).value='');
    document.getElementById('order-edit-status').value='Geplant';document.getElementById('order-edit-visible').checked=true;
    window.openDirectEditWindow('order-management-card','Auftrag anlegen',{edit:true});
  },true);
})();

/* The worklog keeps its existing autosave page; only the reading/editing presentation changes. */
(() => {
  'use strict';
  function initialiseWorklogWindow(){
  const page=document.getElementById('worklog-detail'),card=document.getElementById('edit-worklog-card');
  if(!page||!card||!window.openWorklogDetail||page.querySelector('.worklog-window-actions'))return;
  // A selected entry has its own page: hide the old selector's orphan search field too.
  const recordPicker=card.querySelector('#edit-worklog-select')?.closest('.select-search-wrap');
  if(recordPicker)recordPicker.hidden=true;
  let mode='overview';
  const navigation=document.querySelector('.bottom');
  if(navigation){const updateInset=()=>page.style.setProperty('--work-navigation-height',navigation.getBoundingClientRect().height+'px');updateInset();new ResizeObserver(updateInset).observe(navigation);}
  const updateActive=()=>document.body.classList.toggle('worklog-window-open',page.classList.contains('active'));
  new MutationObserver(updateActive).observe(page,{attributes:true,attributeFilter:['class']});updateActive();
  const summary=document.createElement('section');summary.className='card worklog-window-summary';summary.setAttribute('aria-label','Arbeitsaufzeichnung im Überblick');
  const footer=document.createElement('footer');footer.className='worklog-window-actions';footer.innerHTML='<p>Details ansehen oder unten bearbeiten.</p><div><button type="button" class="secondary" data-work-window-back>Zurück</button><button type="button" class="primary" data-work-window-edit>Bearbeiten</button><button type="button" class="primary" data-work-window-save hidden>Speichern</button><button type="button" class="secondary" data-work-window-cancel hidden>Abbrechen</button><button type="button" class="secondary" data-work-window-more>Weitere Aktionen</button></div>';
  page.querySelector('#worklog-detail-form-slot').before(summary);page.append(footer);page.classList.add('worklog-window-page');
  const value=id=>document.getElementById(id)?.value||'';
  const selected=id=>document.getElementById(id)?.selectedOptions[0]?.textContent||'';
  function showOverview(){
    mode='overview';card.querySelectorAll('details[open]').forEach(section=>section.open=false);summary.replaceChildren();const title=document.createElement('h3');title.textContent='Arbeitsdetails';const dl=document.createElement('dl');
    const date=value('edit-worklog-date');
    const pairs=[['Kunde',selected('edit-worklog-customer')],['Datum',date?new Date(date+'T12:00:00').toLocaleDateString('de-AT'):''],['Arbeitszeit',value('edit-worklog-hours')+' h'],['Tätigkeit',value('edit-worklog-activity')],['Auftrag',selected('edit-worklog-order')],['Beginn / Ende',[value('edit-worklog-start'),value('edit-worklog-end')].filter(Boolean).join(' – ')],['Pause',(value('edit-worklog-pause')||'0')+' Minuten'],['Einsatzort',value('edit-worklog-location')],['Material',value('edit-worklog-material')],['Kilometer',(value('edit-worklog-kilometers')||'0')+' km'],['Notizen / Nachträge',value('edit-worklog-notes')],['Abrechnung',document.getElementById('edit-worklog-billed').checked?'In der App als verrechnet markiert':'App: offen · Rechnungszuordnung nicht belegt']];
    pairs.forEach(([label,text])=>{const row=document.createElement('div'),dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=label;dd.textContent=String(text).trim()||'Nicht angegeben';row.dataset.detailLabel=label;row.append(dt,dd);dl.append(row);});summary.append(title,dl);
    const id=value('edit-worklog-select'),attached=photos().filter(x=>x.worklog_id===id),gallery=document.createElement('section');gallery.className='detail-photo-gallery';gallery.innerHTML='<h4>Fotos · '+attached.length+'</h4>'+attached.map(x=>{const src=x.thumbnail_data||x.data;return src&&String(src).startsWith('data:image/')?'<img loading="lazy" alt="'+esc(x.caption||'Arbeitsfoto')+'" src="'+esc(src)+'">':'<span>Bild noch nicht lokal verfügbar</span>';}).join('');summary.append(gallery);const editGallery=card.querySelector('.detail-photo-gallery');if(editGallery)editGallery.innerHTML=gallery.innerHTML;
    page.querySelector('.page-head h2').textContent='Arbeitsaufzeichnung';footer.querySelector('[data-work-window-cancel]').hidden=true;footer.querySelector('[data-work-window-more]').hidden=false;
    page.dataset.workMode='overview';footer.querySelector('[data-work-window-edit]').hidden=false;footer.querySelector('[data-work-window-save]').hidden=true;footer.querySelector('[data-work-window-back]').textContent='Zurück';footer.querySelector('p').textContent='Zum Ändern unten auf „Bearbeiten“ tippen.';
  }
  function showEditor(){mode='edit';page.dataset.workMode='edit';page.querySelector('.page-head h2').textContent='Arbeit bearbeiten';footer.querySelector('[data-work-window-edit]').hidden=true;footer.querySelector('[data-work-window-save]').hidden=false;footer.querySelector('[data-work-window-back]').textContent='Übersicht';footer.querySelector('[data-work-window-cancel]').hidden=false;footer.querySelector('[data-work-window-more]').hidden=true;footer.querySelector('p').textContent='Speichern oder Zurück sichert gültige Änderungen. Abbrechen verwirft noch nicht gespeicherte Eingaben.';window.scrollTo({top:0,behavior:'auto'});}
  function finishEditing(){if(!window.saveWorklogDetail(true))return false;showOverview();window.scrollTo({top:0,behavior:'auto'});return true;}
  window.beginWorklogEdit=showEditor;
  const oldOpen=window.openWorklogDetail;window.openWorklogDetail=function(id){oldOpen(id);if(currentPage==='worklog-detail')showOverview();};
  const oldBack=window.handleAndroidBack;window.handleAndroidBack=function(){if(currentPage==='worklog-detail'&&mode==='edit'){finishEditing();return true;}return oldBack();};
  footer.querySelector('[data-work-window-edit]').addEventListener('click',showEditor);footer.querySelector('[data-work-window-save]').addEventListener('click',finishEditing);footer.querySelector('[data-work-window-back]').addEventListener('click',()=>window.handleAndroidBack());
  footer.querySelector('[data-work-window-cancel]').addEventListener('click',()=>{window.cancelWorklogDetail();showOverview();window.scrollTo({top:0,behavior:'auto'});});
  footer.querySelector('[data-work-window-more]').addEventListener('click',()=>{const id=value('edit-worklog-select'),row=document.querySelector('[data-edit-worklog="'+CSS.escape(id)+'"]');if(row)row.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));});
  }
  // The inline page initializer runs on window.DOMContentLoaded. A deferred
  // script runs earlier, even while readyState is already "interactive".
  if(document.getElementById('worklog-detail')&&window.openWorklogDetail)initialiseWorklogWindow();
  else window.addEventListener('DOMContentLoaded',initialiseWorklogWindow,{once:true});
})();

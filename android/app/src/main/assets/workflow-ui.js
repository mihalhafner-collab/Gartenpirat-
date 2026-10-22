/* Progressive disclosure on the operative screens; original controls/handlers stay intact. */
(()=>{
 'use strict';
 const get=id=>document.getElementById(id);let limit=30,visible=[];
 // Fixed presentation paths only; existing controls retain their original handlers.
 const menuIcons={
  search:'<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 5 5"/>',
  'planning-page':'<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M7 3v4m10-4v4M3 11h18m-13 4h2m4 0h2"/>',
  worklogs:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  orders:'<rect x="5" y="5" width="14" height="16" rx="2"/><path d="M9 5V3h6v2M9 10h6m-6 4h6m-6 4h4"/>',
  weeks:'<path d="M4 20V4m0 16h17M8 16v-4m5 4V7m5 9v-6"/>',
  invoices:'<path d="M6 3h9l4 4v14H6zM14 3v5h5M9 12h7m-7 4h5"/>',
  'billing-page':'<rect x="5" y="3" width="14" height="18" rx="2"/><path d="M8 7h8m-8 5h1m5 0h1m-7 5h1m5 0h1"/>',
  offers:'<path d="M5 3h10l4 4v7M5 3v18h7m2-3 5-5 2 2-5 5-3 1zM8 8h4m-4 4h6"/>',
  photos:'<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8" cy="9" r="1"/><path d="m4 18 5-5 4 3 3-4 4 6"/>',
  garden:'<path d="M20 4C7 2 2 8 6 15c5 5 13 2 14-11ZM4 21l12-12"/>',
  'settings-page':'<path d="M4 6h16M4 12h16M4 18h16"/><circle cx="9" cy="6" r="2"/><circle cx="15" cy="12" r="2"/><circle cx="9" cy="18" r="2"/>',
  'status-page':'<circle cx="12" cy="12" r="9"/><path d="M12 11v6m0-10v1"/>',
  trash:'<path d="M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7"/>'
 };
 function menuIcon(button,destination){
  if(button.querySelector(':scope>.more-icon'))return;
  const kind=button.hasAttribute('data-global-search')?'search':button.hasAttribute('data-open-garden-menu')?'garden':destination;
  button.insertAdjacentHTML('afterbegin','<svg class="more-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+(menuIcons[kind]||menuIcons['status-page'])+'</svg>');
  if(kind==='search'){button.classList.add('more-search');button.querySelector('small').textContent='Kunden, Arbeiten, Fotos, Dokumente';}
 }
 function organizeMenu(){
  const nav=document.querySelector('#more-page .more-destinations');if(!nav)return;
  const groups=[['data','Status & Einstellungen'],['work','Arbeit & Planung'],['billing','Angebote & Rechnungen'],['media','Fotos & Pflanzen']];
  nav.classList.remove('card');
  for(const [id,title]of groups)if(!nav.querySelector('[data-more-group="'+id+'"]')){const group=document.createElement('section');group.className='card more-group';group.dataset.moreGroup=id;const heading=document.createElement('h3');heading.textContent=title;group.append(heading);nav.append(group);}
  let tools=get('settings-data-tools');if(!tools&&get('settings-page')){tools=document.createElement('section');tools.id='settings-data-tools';tools.className='card settings-section more-destinations';tools.innerHTML='<h3>Daten verwalten</h3>';get('settings-page').querySelector('.settings-about').before(tools);}
  for(const button of [...nav.querySelectorAll('button')]){
   const destination=button.dataset.open||button.dataset.moreDestination;
   menuIcon(button,destination);
   if(button.hasAttribute('data-global-search')){if(button.parentElement!==nav)nav.prepend(button);continue;}
   if(destination==='sync-page'){button.remove();continue;}
   if(button.hasAttribute('data-open-trash')){if(tools)tools.append(button);continue;}
   const group=['planning-page','worklogs','orders','weeks'].includes(destination)?'work':['invoices','billing-page','offers'].includes(destination)?'billing':destination==='photos'||button.hasAttribute('data-open-garden-menu')?'media':'data';
   const target=nav.querySelector('[data-more-group="'+group+'"]');if(button.parentElement!==target)target.append(button);
   if(destination==='billing-page'){button.querySelector('strong').textContent='Abrechnung vorbereiten';button.querySelector('small').textContent='Arbeiten auswählen · Rechnung erstellen';}
  }
  // Components may finish at different times. Preserve the reviewed menu order
  // without replacing buttons/listeners or retriggering the observer forever.
  const order=['settings-page','status-page','trash','planning-page','worklogs','orders','weeks','offers','billing-page','invoices','photos','garden'];
  const rank=button=>{const key=button.dataset.open||button.dataset.moreDestination||(button.hasAttribute('data-open-garden-menu')?'garden':'');const i=order.indexOf(key);return i<0?order.length:i;};
  for(const group of nav.querySelectorAll('[data-more-group]')){
   const buttons=[...group.querySelectorAll(':scope>button')].sort((a,b)=>rank(a)-rank(b));let previous=group.querySelector('h3');
   for(const button of buttons){if(previous.nextElementSibling!==button)previous.after(button);previous=button;}
   group.hidden=!buttons.length;
  }

 }
 function refresh(){
  if(currentPage!=='photo-library-page')return;
  const filter=get('photo-library-scope').value,rows=photos().filter(p=>filter!=='unassigned'||!p.customer_id).sort((a,b)=>String(b.capture_time||b.created_at||'').localeCompare(String(a.capture_time||a.created_at||''))||String(a.id).localeCompare(String(b.id)));
  visible=rows;
  get('photo-library-count').textContent=rows.length+' Fotos'+(filter==='unassigned'?' ohne Kundenzuordnung':'');
  get('photo-library-results').innerHTML='<div class="design-photo-grid">'+rows.slice(0,limit).map(p=>{const src=String(p.thumbnail_data||''),safe=src.startsWith('data:image/')||/^content:\/\/at\.hausgarten\.service\.photothumbnails\/[0-9a-f]{64}\.jpg$/.test(src);return '<button type="button" data-design-action="photo" data-id="'+esc(p.id)+'" aria-label="'+esc(p.caption||'Foto öffnen')+'">'+(safe?'<img loading="lazy" src="'+esc(src)+'" alt="'+esc(p.caption||'Foto')+'">':'<span>'+esc(p.caption||'Foto öffnen')+'</span>')+'</button>';}).join('')+'</div>'+(limit<rows.length?'<button type="button" class="secondary" data-library-next>Weitere 30 Fotos</button>':'');
  get('photo-library-results').querySelector('[data-library-next]')?.addEventListener('click',()=>{limit+=30;refresh();});
 }
 function installSearch(){
  const button=document.createElement('button');button.type='button';button.className='secondary';button.dataset.globalSearch='';button.innerHTML='<span><strong>Überall suchen</strong><small>Kunden, Arbeiten, Fotos und Dokumente</small></span>';get('more-page').querySelector('.more-destinations').prepend(button);
  const homeButton=document.createElement('button');homeButton.type='button';homeButton.className='daily-settings';homeButton.id='global-search-open';homeButton.textContent='⌕';homeButton.setAttribute('aria-label','Überall suchen');
  const actions=document.createElement('div');actions.className='daily-header-actions';const settings=document.querySelector('#home .daily-settings');if(settings){settings.before(actions);actions.append(homeButton,settings);}else document.querySelector('#home .brand').append(homeButton);
  const dialog=document.createElement('dialog');dialog.id='global-search-dialog';dialog.className='business-action-dialog';dialog.innerHTML='<header class="design-section-head"><h2>Überall suchen</h2><button type="button" class="secondary" data-search-close>Schließen</button></header><label>Kunde, Tätigkeit oder Dokument<input type="search" id="global-search-input" autocomplete="off"></label><p role="status" data-search-count></p><div data-search-results></div>';document.body.append(dialog);
  const input=get('global-search-input'),results=dialog.querySelector('[data-search-results]'),status=dialog.querySelector('[data-search-count]');let indexed=[],sources=[],limit=50,timer=0;
  const normalize=HGDataOrder.fold;
  function index(){const kinds=[['customer','Kunde',customers()],['worklog','Arbeit',worklogs()],['photo','Foto',photos()],['invoice','Rechnung',merged('invoices','invoice')],['offer','Angebot',merged('offers','offer')],['appointment','Termin',merged('appointments','appointment')],['order','Auftrag',orders()],['service_template','Baustein',merged('service_catalog','service_template')]],next=kinds.map(x=>x[2]);if(next.every((x,i)=>x===sources[i]))return;sources=next;const names=new Map(kinds[0][2].map(c=>[c.id,c.name]));indexed=[];
   for(const [kind,label,rows]of kinds)for(const row of rows){const title=kind==='customer'?row.name:kind==='worklog'?row.activity:kind==='photo'?row.caption||row.original_name||'Foto':kind==='invoice'?row.status==='Entwurf'?'Rechnungsentwurf':row.invoice_number||'Rechnung':row.title||label,detail=[names.get(row.customer_id)||'',row.work_date||row.invoice_date||row.issue_date||(row.starts_at?HGBusinessFeedback.time(row.starts_at):''),kind==='customer'?[row.street,row.postal_code,row.city].filter(Boolean).join(' '):''].filter(Boolean).join(' · '),text=normalize([label,kind==='offer'?OfferContract.document_types.find(t=>t.id===row.document_type)?.label:'',title,detail,row.company,row.first_name,row.last_name,row.phone,row.email,row.notes,row.description,row.material,row.original_name,row.filename,row.invoice_number,row.offer_number,row.category,row.location].filter(Boolean).join(' '));indexed.push({kind,label:kind==='customer'&&!HGDataOrder.active(row.active)?'Archivierter Kunde':label,id:row.id,title,detail,text});}
  }
  function draw(){index();const terms=normalize(input.value).trim().split(/\s+/).filter(Boolean);if(!terms.length){status.textContent='Was suchst du? Deine Daten werden dabei nicht geändert.';results.replaceChildren();return;}const rows=indexed.filter(r=>HGDataOrder.matches(input.value,r.text)).sort((a,b)=>HGDataOrder.compare(a,b,'label','title','detail'));status.textContent=rows.length+' Treffer';results.innerHTML=rows.slice(0,limit).map((r,i)=>'<button type="button" class="design-profile-row" data-search-result="'+i+'" data-record-type="'+esc(r.kind)+'" data-record-id="'+esc(r.id)+'"><span><small>'+esc(r.label)+'</small><strong>'+esc(r.title)+'</strong><small>'+esc(r.detail)+'</small></span><b aria-hidden="true">›</b></button>').join('')+(rows.length>limit?'<button type="button" class="secondary" data-search-more>Weitere 50 Treffer</button>':'');
   results.querySelector('[data-search-more]')?.addEventListener('click',()=>{limit+=50;draw();});results.querySelectorAll('[data-search-result]').forEach(b=>b.onclick=()=>{const r=rows[Number(b.dataset.searchResult)];dialog.close();if(r.kind==='customer')openCustomer(r.id);else if(r.kind==='worklog')openWorklogDetail(r.id);else if(r.kind==='photo')window.openStoredPhoto(r.id,[r.id]);else if(r.kind==='invoice')openInvoiceRecord(r.id);else if(r.kind==='offer')OfferUI.open('',r.id);else if(r.kind==='appointment')PlanningUI.show(r.id);else if(r.kind==='service_template')OfferUI.editTemplate(r.id);else{openPage('orders');const select=get('order-edit-select');select.value=r.id;select.dispatchEvent(new Event('change',{bubbles:true}));window.openDirectEditWindow('order-management-card','Auftrag im Überblick');}});
  }
  const priorRender=window.render;window.render=function(...args){const value=priorRender.apply(this,args);if(dialog.open)draw();return value;};
  const open=()=>{limit=50;draw();dialog.showModal();input.focus();};button.onclick=open;homeButton.onclick=open;dialog.querySelector('[data-search-close]').onclick=()=>dialog.close();input.oninput=()=>{clearTimeout(timer);limit=50;timer=setTimeout(draw,160);};dialog.addEventListener('close',()=>clearTimeout(timer));
 }
 function init(){
  const more=get('more-page'),settings=get('settings-page');if(!more||!settings)return;
  const moveSettingsCards=()=>{for(const id of ['business-settings-card','photo-storage-settings']){const node=get(id);if(node&&node.parentElement!==settings)settings.insertBefore(node,settings.querySelector('.settings-about'));}};
  moveSettingsCards();
  // Modules may finish after this view on a busy device. Keep their existing
  // controls reachable under Settings regardless of timer execution order.
  new MutationObserver(moveSettingsCards).observe(more,{childList:true});
  const page=document.createElement('section');page.id='photo-library-page';page.className='page';page.innerHTML='<div class="page-head"><button type="button" class="back" aria-label="Zurück">‹</button><h2>Fotos</h2></div><section class="card"><label for="photo-library-scope">Anzeige</label><select id="photo-library-scope"><option value="all">Alle Fotos</option><option value="unassigned">Ohne Kundenzuordnung</option></select><p id="photo-library-count" role="status"></p><p class="sub">Fotos bleiben auch nach dem Entfernen einer Kundenakte hier erreichbar.</p><div id="photo-library-results"></div></section>';document.querySelector('main.app').append(page);
  const nav=document.createElement('button');nav.type='button';nav.dataset.moreDestination='photos';nav.innerHTML='<span><strong>Fotos</strong><small>Alle Kunden · ohne Zuordnung</small></span><b aria-hidden="true">›</b>';nav.onclick=()=>{openPage('photo-library-page');refresh();};more.querySelector('.more-destinations').append(nav);
  const garden=document.createElement('button');garden.type='button';garden.dataset.openGardenMenu='';garden.innerHTML='<span><strong>Gartenpirat & Pflanzen</strong><small>Pflanzenlexikon · Neophyten · Bilder</small></span><b aria-hidden="true">›</b>';garden.onclick=()=>window.Gartenpirat?.open(true);more.querySelector('.more-destinations').append(garden);
  get('photo-library-results').onclick=event=>{const button=event.target.closest('[data-design-action="photo"]');if(button){event.preventDefault();event.stopPropagation();window.openStoredPhoto?.(button.dataset.id,visible.map(p=>p.id));}};
  get('photo-library-scope').onchange=()=>{limit=30;refresh();};
  const prior=window.render;window.render=function(...args){prior(...args);refresh();};
  installSearch();organizeMenu();const menu=more.querySelector('.more-destinations');new MutationObserver(changes=>{if(changes.some(x=>[...x.addedNodes].some(n=>n.nodeType===1&&n.tagName==='BUTTON'&&menu.contains(n))))organizeMenu();}).observe(menu,{childList:true,subtree:true});
  // One expanded action group per screen. Native details keep keyboard and screen-reader support.
  document.addEventListener('toggle',event=>{const target=event.target;if(!target.matches?.('details.workflow-options,details.photo-more')||!target.open)return;const scope=target.closest('.page,dialog');scope?.querySelectorAll('details.workflow-options[open],details.photo-more[open]').forEach(d=>{if(d!==target)d.open=false;});},true);
 }
 window.PhotoLibraryUI=Object.freeze({refresh,open:id=>window.openStoredPhoto?.(id,visible.map(p=>p.id))});
 document.addEventListener('DOMContentLoaded',()=>setTimeout(init,120),{once:true});
})();

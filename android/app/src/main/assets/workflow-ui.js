/* Progressive disclosure on the operative screens; original controls/handlers stay intact. */
(()=>{
 'use strict';
 const get=id=>document.getElementById(id);let limit=30,visible=[];
 function organizeMenu(){
  const nav=document.querySelector('#more-page .more-destinations');if(!nav)return;
  const groups=[['work','Arbeit & Planung'],['billing','Angebote & Rechnungen'],['media','Fotos & Pflanzen'],['data','Daten & App']];
  nav.classList.remove('card');
  for(const [id,title]of groups)if(!nav.querySelector('[data-more-group="'+id+'"]')){const group=document.createElement('section');group.className='card more-group';group.dataset.moreGroup=id;const heading=document.createElement('h3');heading.textContent=title;group.append(heading);nav.append(group);}
  for(const button of [...nav.querySelectorAll('button')]){
   const destination=button.dataset.open||button.dataset.moreDestination;
   const group=['orders','weeks','planning-page'].includes(destination)?'work':['invoices','billing-page','offers'].includes(destination)?'billing':destination==='photos'||button.hasAttribute('data-open-garden-menu')?'media':'data';
   const target=nav.querySelector('[data-more-group="'+group+'"]');if(button.parentElement!==target)target.append(button);
   if(button.hasAttribute('data-open-trash'))button.innerHTML='<span><strong>Papierkorb</strong><small>Wiederherstellen · endgültig löschen</small></span><b aria-hidden="true">›</b>';
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
  const normalize=value=>String(value||'').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('de-AT');
  function index(){const kinds=[['customer','Kunde',customers()],['worklog','Arbeit',worklogs()],['photo','Foto',photos()],['invoice','Rechnung',merged('invoices','invoice')],['offer','Angebot',merged('offers','offer')],['appointment','Termin',merged('appointments','appointment')],['order','Auftrag',orders()]],next=kinds.map(x=>x[2]);if(next.every((x,i)=>x===sources[i]))return;sources=next;const names=new Map(kinds[0][2].map(c=>[c.id,c.name]));indexed=[];
   for(const [kind,label,rows]of kinds)for(const row of rows){const title=kind==='customer'?row.name:kind==='worklog'?row.activity:kind==='photo'?row.caption||row.original_name||'Foto':kind==='invoice'?row.status==='Entwurf'?'Rechnungsentwurf':row.invoice_number||'Rechnung':row.title||label,detail=[names.get(row.customer_id)||'',row.work_date||row.invoice_date||row.issue_date||(row.starts_at?HGBusinessFeedback.time(row.starts_at):''),kind==='customer'?[row.street,row.postal_code,row.city].filter(Boolean).join(' '):''].filter(Boolean).join(' · '),text=normalize([label,kind==='offer'?OfferContract.document_types.find(t=>t.id===row.document_type)?.label:'',title,detail,row.company,row.phone,row.email,row.notes,row.description,row.material].filter(Boolean).join(' '));indexed.push({kind,label,id:row.id,title,detail,text});}
  }
  function draw(){index();const terms=normalize(input.value).trim().split(/\s+/).filter(Boolean);if(!terms.length){status.textContent='Was suchst du? Deine Daten werden dabei nicht geändert.';results.replaceChildren();return;}const rows=indexed.filter(r=>terms.every(t=>r.text.includes(t)));status.textContent=rows.length+' Treffer';results.innerHTML=rows.slice(0,limit).map((r,i)=>'<button type="button" class="design-profile-row" data-search-result="'+i+'"><span><small>'+esc(r.label)+'</small><strong>'+esc(r.title)+'</strong><small>'+esc(r.detail)+'</small></span><b aria-hidden="true">›</b></button>').join('')+(rows.length>limit?'<button type="button" class="secondary" data-search-more>Weitere 50 Treffer</button>':'');
   results.querySelector('[data-search-more]')?.addEventListener('click',()=>{limit+=50;draw();});results.querySelectorAll('[data-search-result]').forEach(b=>b.onclick=()=>{const r=rows[Number(b.dataset.searchResult)];dialog.close();if(r.kind==='customer')openCustomer(r.id);else if(r.kind==='worklog')openWorklogDetail(r.id);else if(r.kind==='photo')window.openStoredPhoto(r.id,[r.id]);else if(r.kind==='invoice')openInvoiceRecord(r.id);else if(r.kind==='offer')OfferUI.open('',r.id);else if(r.kind==='appointment')PlanningUI.edit(r.id);else{openPage('orders');const select=get('order-edit-select');select.value=r.id;select.dispatchEvent(new Event('change',{bubbles:true}));window.openDirectEditWindow('order-management-card','Auftrag im Überblick');}});
  }
  const open=()=>{limit=50;draw();dialog.showModal();input.focus();};button.onclick=open;homeButton.onclick=open;dialog.querySelector('[data-search-close]').onclick=()=>dialog.close();input.oninput=()=>{clearTimeout(timer);limit=50;timer=setTimeout(draw,160);};dialog.addEventListener('close',()=>clearTimeout(timer));
 }
 function init(){
  const more=get('more-page'),settings=get('settings-page');if(!more||!settings)return;
  for(const id of ['business-settings-card','photo-storage-settings']){const node=get(id);if(node)settings.insertBefore(node,settings.querySelector('.settings-about'));}
  const page=document.createElement('section');page.id='photo-library-page';page.className='page';page.innerHTML='<div class="page-head"><button type="button" class="back" aria-label="Zurück">‹</button><h2>Fotos</h2></div><section class="card"><label for="photo-library-scope">Anzeige</label><select id="photo-library-scope"><option value="all">Alle Fotos</option><option value="unassigned">Ohne Kundenzuordnung</option></select><p id="photo-library-count" role="status"></p><p class="sub">Fotos bleiben auch nach dem Entfernen einer Kundenakte hier erreichbar.</p><div id="photo-library-results"></div></section>';document.querySelector('main.app').append(page);
  const nav=document.createElement('button');nav.type='button';nav.dataset.moreDestination='photos';nav.innerHTML='<span><strong>Fotos</strong><small>Alle Kunden · ohne Zuordnung</small></span><b aria-hidden="true">›</b>';nav.onclick=()=>{openPage('photo-library-page');refresh();};more.querySelector('.more-destinations').append(nav);
  const garden=document.createElement('button');garden.type='button';garden.dataset.openGardenMenu='';garden.innerHTML='<span><strong>Gartenpirat & Pflanzen</strong><small>Pflanzenlexikon · Neophyten · Bilder</small></span><b aria-hidden="true">›</b>';garden.onclick=()=>window.Gartenpirat?.open(true);more.querySelector('.more-destinations').append(garden);
  get('photo-library-results').onclick=event=>{const button=event.target.closest('[data-design-action="photo"]');if(button){event.preventDefault();event.stopPropagation();window.openStoredPhoto?.(button.dataset.id,visible.map(p=>p.id));}};
  get('photo-library-scope').onchange=()=>{limit=30;refresh();};
  const prior=window.render;window.render=function(...args){prior(...args);refresh();};
  installSearch();organizeMenu();const menu=more.querySelector('.more-destinations');new MutationObserver(changes=>{if(changes.some(x=>[...x.addedNodes].some(n=>n.nodeType===1&&n.tagName==='BUTTON'&&n.parentElement===menu)))organizeMenu();}).observe(menu,{childList:true});
  // One expanded action group per screen. Native details keep keyboard and screen-reader support.
  document.addEventListener('toggle',event=>{const target=event.target;if(!target.matches?.('details.workflow-options,details.photo-more')||!target.open)return;const scope=target.closest('.page,dialog');scope?.querySelectorAll('details.workflow-options[open],details.photo-more[open]').forEach(d=>{if(d!==target)d.open=false;});},true);
 }
 window.PhotoLibraryUI=Object.freeze({refresh,open:id=>window.openStoredPhoto?.(id,visible.map(p=>p.id))});
 document.addEventListener('DOMContentLoaded',()=>setTimeout(init,120),{once:true});
})();

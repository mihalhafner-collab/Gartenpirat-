/* Progressive disclosure on the operative screens; original controls/handlers stay intact. */
(()=>{
 'use strict';
 const get=id=>document.getElementById(id);let limit=30,visible=[];
 function refresh(){
  if(currentPage!=='photo-library-page')return;
  const filter=get('photo-library-scope').value,rows=photos().filter(p=>filter!=='unassigned'||!p.customer_id).sort((a,b)=>String(b.capture_time||b.created_at||'').localeCompare(String(a.capture_time||a.created_at||''))||String(a.id).localeCompare(String(b.id)));
  visible=rows;
  get('photo-library-count').textContent=rows.length+' Fotos'+(filter==='unassigned'?' ohne Kundenzuordnung':'');
  get('photo-library-results').innerHTML='<div class="design-photo-grid">'+rows.slice(0,limit).map(p=>{const src=String(p.thumbnail_data||''),safe=src.startsWith('data:image/')||/^content:\/\/at\.hausgarten\.service\.photothumbnails\/[0-9a-f]{64}\.jpg$/.test(src);return '<button type="button" data-design-action="photo" data-id="'+esc(p.id)+'" aria-label="'+esc(p.caption||'Foto öffnen')+'">'+(safe?'<img loading="lazy" src="'+esc(src)+'" alt="'+esc(p.caption||'Foto')+'">':'<span>'+esc(p.caption||'Foto öffnen')+'</span>')+'</button>';}).join('')+'</div>'+(limit<rows.length?'<button type="button" class="secondary" data-library-next>Weitere 30 Fotos</button>':'');
  get('photo-library-results').querySelector('[data-library-next]')?.addEventListener('click',()=>{limit+=30;refresh();});
 }
 function init(){
  const more=get('more-page'),settings=get('settings-page');if(!more||!settings)return;
  for(const id of ['business-settings-card','photo-storage-settings']){const node=get(id);if(node)settings.insertBefore(node,settings.querySelector('.settings-about'));}
  const page=document.createElement('section');page.id='photo-library-page';page.className='page';page.innerHTML='<div class="page-head"><button type="button" class="back" aria-label="Zurück">‹</button><h2>Fotos</h2></div><section class="card"><label for="photo-library-scope">Anzeige</label><select id="photo-library-scope"><option value="all">Alle Fotos</option><option value="unassigned">Ohne Kundenzuordnung</option></select><p id="photo-library-count" role="status"></p><p class="sub">Fotos bleiben auch nach dem Entfernen einer Kundenakte hier erreichbar.</p><div id="photo-library-results"></div></section>';document.querySelector('main.app').append(page);
  const nav=document.createElement('button');nav.type='button';nav.innerHTML='<span><strong>Fotos</strong><small>Alle Kunden · ohne Zuordnung</small></span><b aria-hidden="true">›</b>';nav.onclick=()=>{openPage('photo-library-page');refresh();};more.querySelector('.more-destinations').append(nav);
  const garden=document.createElement('button');garden.type='button';garden.dataset.openGardenMenu='';garden.innerHTML='<span><strong>Gartenpirat & Pflanzen</strong><small>Pflanzenlexikon · Neophyten · Bilder</small></span><b aria-hidden="true">›</b>';garden.onclick=()=>window.Gartenpirat?.open(true);more.querySelector('.more-destinations').append(garden);
  get('photo-library-results').onclick=event=>{const button=event.target.closest('[data-design-action="photo"]');if(button){event.preventDefault();event.stopPropagation();window.openStoredPhoto?.(button.dataset.id,visible.map(p=>p.id));}};
  get('photo-library-scope').onchange=()=>{limit=30;refresh();};
  const prior=window.render;window.render=function(...args){prior(...args);refresh();};
  // One expanded action group per screen. Native details keep keyboard and screen-reader support.
  document.addEventListener('toggle',event=>{const target=event.target;if(!target.matches?.('details.workflow-options,details.photo-more')||!target.open)return;const scope=target.closest('.page,dialog');scope?.querySelectorAll('details.workflow-options[open],details.photo-more[open]').forEach(d=>{if(d!==target)d.open=false;});},true);
 }
 window.PhotoLibraryUI=Object.freeze({refresh,open:id=>window.openStoredPhoto?.(id,visible.map(p=>p.id))});
 document.addEventListener('DOMContentLoaded',()=>setTimeout(init,120),{once:true});
})();

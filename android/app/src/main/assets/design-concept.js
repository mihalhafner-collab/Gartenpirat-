/* Presentation and navigation only. Business writes use the existing forms. */
(() => {
  'use strict';
  const get=id=>document.getElementById(id);
  let profileId='',photoAddDialog=null,photoViewer=null,viewerRows=[],viewerIndex=0,viewerScale=1,viewerX=0,viewerY=0,viewerLoadToken=0;
  const hours=value=>Number(value||0).toLocaleString('de-AT',{maximumFractionDigits:2})+' h';
  const openStatuses=new Set(['','Geplant','Aktiv','In Arbeit','Wartet auf Material','Offen']);
  function previewRows(rows,limit,label,renderRow){
    return rows.slice(0,limit).map(renderRow).join('')+(rows.length>limit?`<details class="design-more-rows"><summary>${esc(label)} · ${rows.length}</summary>${rows.slice(limit).map(renderRow).join('')}</details>`:'');
  }
  function photoRows(rows){
    const grid=items=>'<div class="design-photo-grid">'+items.map(p=>{const src=p.thumbnail_data||p.data||p.full_data,profile=p.role==='profile'||/^(profilbild|profil|profile)$/i.test(String(p.caption||'').trim());return '<button type="button" data-design-action="photo" data-id="'+esc(p.id)+'" aria-label="'+esc((p.caption||'Kundenfoto')+' öffnen')+'">'+(src&&String(src).startsWith('data:image/')?'<img src="'+esc(src)+'" alt="'+esc(p.caption||'Kundenfoto')+'" loading="lazy">':'<small>Original nach Drive-Abgleich laden</small>')+(profile?'<span class="design-photo-role">Profil</span>':'')+'</button>';}).join('')+'</div>';
    return rows.length?grid(rows.slice(0,6))+(rows.length>6?'<details class="design-more-rows"><summary>Weitere Fotos · '+(rows.length-6)+'</summary>'+grid(rows.slice(6))+'</details>':''):'<p class="sub">Noch keine Fotos zugeordnet.</p>';
  }
  function homeOverview(){
    let metrics=get('design-metrics');
    if(!metrics){metrics=document.createElement('section');metrics.id='design-metrics';metrics.className='design-metrics';metrics.setAttribute('aria-label','Dein Arbeitstag');const timer=get('workday-timer');if(timer)timer.after(metrics);else document.querySelector('#home .hero h2').after(metrics);}
    const logs=worklogs(),today=localDate(),todayHours=logs.filter(x=>x.work_date===today).reduce((sum,x)=>sum+Number(x.hours||0),0),open=orders().filter(x=>openStatuses.has(x.status||''));
    metrics.innerHTML=`<button type="button" class="design-metric" data-design-metric="today"><strong>${esc(hours(todayHours))}</strong><span>Heute erfasst</span></button><button type="button" class="design-metric" data-design-metric="week"><strong>${esc(get('current-week-hours')?.textContent||'0 h')}</strong><span>Diese Woche</span></button><button type="button" class="design-metric" data-design-metric="orders"><strong>${open.length}</strong><span>Offene Aufträge</span></button>`;
    document.querySelector('#home .hero h2').after(metrics);
    const timer=get('workday-timer');if(timer){metrics.after(timer);const start=timer.querySelector('[data-session-action="start"]');if(start)start.querySelector('b').textContent='Arbeit starten';}
    let upcoming=get('design-upcoming');if(!upcoming){upcoming=document.createElement('section');upcoming.id='design-upcoming';upcoming.className='card';get('design-metrics').after(upcoming);}
    const scheduled=open.filter(x=>x.scheduled_date===today);
    upcoming.hidden=!scheduled.length;upcoming.innerHTML='<h3>Heute geplant</h3>'+scheduled.map(x=>`<button type="button" class="design-upcoming-row" data-edit-order="${esc(x.id)}"><strong>${esc(x.title)}</strong><small>${esc(customerName(x.customer_id))}${x.location?' · '+esc(x.location):''}</small></button>`).join('');
    const week=document.querySelector('#home>.week-summary');if(week)week.hidden=true;
  }
  function compactLists(){
    const records=new Map(worklogs().map(row=>[row.id,row])),counts=new Map();
    photos().forEach(photo=>counts.set(photo.worklog_id,(counts.get(photo.worklog_id)||0)+1));
    document.querySelectorAll('.worklog-entry').forEach(row=>{const record=records.get(row.dataset.editWorklog);if(!record)return;const status=row.querySelector('.worklog-entry-status');if(status){const count=counts.get(record.id)||0;status.textContent=(Number(record.billed)?'Abgerechnet':'Nicht abgerechnet')+(count?' · '+count+' Fotos':'');}});
    const select=get('design-order-status'),rows=new Map(orders().map(row=>[row.id,row]));
    let visible=0;document.querySelectorAll('#order-list [data-edit-order]').forEach(button=>{const row=rows.get(button.dataset.editOrder);if(!row)return;const wrapper=button.closest('.swipe-shell')||button;wrapper.hidden=select?.value==='open'&&!openStatuses.has(row.status||'')||select?.value==='closed'&&openStatuses.has(row.status||'');if(!wrapper.hidden)visible++;let date=button.querySelector('.design-order-date');if(!date){date=document.createElement('span');date.className='design-order-date';button.append(date);}date.textContent=row.scheduled_date?worklogDateLabel(row.scheduled_date):'Ohne festen Termin';});
    let hint=get('design-order-empty');if(!hint){hint=document.createElement('p');hint.id='design-order-empty';hint.className='sub';hint.setAttribute('role','status');get('order-list').after(hint);}hint.textContent='Keine Aufträge für diese Auswahl. Suche oder Statusfilter zurücksetzen.';hint.hidden=visible>0||!get('order-list').querySelector('[data-edit-order]');
  }
  function resetWorkFilters(customer='all',period='all'){
    for(const [id,value]of Object.entries({'worklog-search':'','worklog-period':period,'worklog-customer-filter':customer,'worklog-billing-filter':'all'}))if(get(id))get(id).value=value;
    if(typeof worklogDisplayLimit!=='undefined')worklogDisplayLimit=50;
    render();
  }
  function metricActions(event){
    const button=event.target.closest('[data-design-metric]');if(!button)return;event.preventDefault();event.stopImmediatePropagation();
    if(button.dataset.designMetric==='today'){openPage('worklogs');resetWorkFilters('all','today');}
    if(button.dataset.designMetric==='week'){openPage('weeks');get('week-period').value='current';get('week-customer').value='all';renderWeeks(worklogs());}
    if(button.dataset.designMetric==='orders'){openPage('orders');get('order-search').value='';get('design-order-status').value='open';render();}
  }
  function avatar(customer){const picture=photos().filter(p=>p.customer_id===customer.id&&(p.role==='profile'||/^(profilbild|profil|profile)$/i.test(String(p.caption||'')))).sort((a,b)=>String(b.updated_at||b.created_at||'').localeCompare(String(a.updated_at||a.created_at||'')))[0],src=picture?.thumbnail_data||picture?.data||picture?.full_data;return src&&String(src).startsWith('data:image/')?`<img class="customer-avatar" src="${esc(src)}" alt="Profilbild">`:`<span class="customer-avatar">${esc(String(customer.name||'K').split(/\s+/).slice(0,2).map(x=>x[0]||'').join(''))}</span>`;}
  function profileContent(){
    const page=get('customer-profile-page'),customer=customers().find(x=>x.id===profileId);if(!page||!customer)return;
    page.dataset.customerId=customer.id;
    const address=[customer.street,[customer.postal_code,customer.city].filter(Boolean).join(' '),customer.country].filter(Boolean).join('\n'),logs=worklogs().filter(x=>x.customer_id===profileId).sort((a,b)=>String(b.work_date||'').localeCompare(String(a.work_date||''))),jobs=orders().filter(x=>x.customer_id===profileId),invoices=merged('invoices','invoice').filter(x=>x.customer_id===profileId),pictures=photos().filter(x=>x.customer_id===profileId);
    const jobRows=previewRows(jobs,4,'Weitere Aufträge anzeigen',x=>`<button type="button" class="design-profile-row" data-design-action="order" data-id="${esc(x.id)}"><span><strong>${esc(x.title)}</strong><small>${esc(x.status||'Geplant')}</small></span><b>›</b></button>`);
    // Read-only customer documentation timeline; uses existing worklog/photo IDs.
    const documentationRows=logs.map(log=>{
      const attached=pictures.filter(photo=>photo.worklog_id===log.id&&photo.role!=='profile');
      const labels=attached.map(photo=>String(photo.caption||'Dokumentationsfoto').trim()).filter(Boolean);
      const details=`<button type="button" class="design-profile-row" data-design-action="worklog" data-id="${esc(log.id)}"><span><strong>${esc(worklogDateLabel(log.work_date))} · ${esc(log.activity||'Arbeitseinsatz')}</strong><small>${esc(hours(log.hours))} · ${attached.length} Foto(s)${labels.length?' · '+esc(labels.slice(0,2).join(', ')):''}</small></span><b>›</b></button>`;
      return `<div class="design-documentation-entry">${details}${attached.length?photoRows(attached):''}</div>`;
    }).join('');
    const unassignedDocumentation=pictures.filter(photo=>photo.role!=='profile'&&!photo.worklog_id);
    const documentationSection=`<section class="card"><div class="design-section-head"><h3>Dokumentation · ${logs.length} Einsätze</h3></div><p class="sub">Chronologischer Verlauf mit Arbeitszeiten und zugeordneten Fotos.</p>${documentationRows||'<p class="sub">Noch keine Arbeitseinsätze dokumentiert.</p>'}${unassignedDocumentation.length?'<p class="sub">'+unassignedDocumentation.length+' Kundenfoto(s) ohne Arbeitszuordnung – siehe Fotos.</p>':''}</section>`;
    const workRows=logs.slice(0,5).map(x=>`<button type="button" class="design-profile-row" data-design-action="worklog" data-id="${esc(x.id)}"><span><strong>${esc(worklogDateLabel(x.work_date))}</strong><small>${esc(x.activity)}</small></span><b>${esc(hours(x.hours))}</b></button>`).join('');
    get('design-profile-content').innerHTML=`<section class="card"><div class="design-profile-header">${avatar(customer)}<div><h3>${esc(customer.name)}</h3><p>${esc(customer.company||customer.city||'Kunde')}${customer.active===0?' · Archiviert':''}</p></div></div><p class="design-address">${esc(address||'Adresse ergänzen')}</p><div class="design-contact-actions">${customer.phone?'<button type="button" class="secondary" data-design-action="call">Anrufen</button>':''}${address?'<button type="button" class="secondary" data-design-action="maps">Route</button>':''}${customer.email?'<button type="button" class="secondary" data-design-action="email">E-Mail</button>':''}</div></section><section class="card"><div class="design-section-head"><h3>Aufträge · ${jobs.length}</h3><button type="button" class="secondary" data-design-action="new-order">+ Auftrag</button></div>${jobRows||'<p class="sub">Noch kein Auftrag vorhanden.</p>'}</section><section class="card"><div class="design-section-head"><h3>Arbeiten · ${logs.length}</h3><button type="button" class="secondary" data-design-action="new-work">+ Arbeit</button></div>${workRows||'<p class="sub">Noch keine Arbeit erfasst.</p>'}${logs.length>5?'<button type="button" class="secondary" data-design-action="all-work">Alle Arbeiten anzeigen</button>':''}</section>${documentationSection}<section class="card"><div class="design-section-head"><h3>Rechnungen · ${invoices.length}</h3></div>${previewRows(invoices,3,'Weitere Rechnungen anzeigen',x=>`<button type="button" class="design-profile-row" data-design-action="invoice" data-id="${esc(x.id)}"><span><strong>${esc(x.invoice_number||'Entwurf')}</strong><small>${esc(x.status||'Entwurf')}</small></span><b>${Number(x.amount||0).toLocaleString('de-AT',{style:'currency',currency:'EUR'})}</b></button>`)||'<p class="sub">Keine Rechnung vorhanden.</p>'}</section><section class="card"><div class="design-section-head"><h3>Fotos · ${pictures.length}</h3><button type="button" class="secondary" data-design-action="add-photos">+ Fotos</button></div>${photoRows(pictures)}</section>${customer.notes?'<section class="card"><h3>Interne Notizen</h3><p class="design-note">'+esc(customer.notes)+'</p></section>':''}`;
  }
  function setPhotoFullscreen(enabled){try{window.AndroidGardenPhotos?.setFullscreen?.(enabled);}catch(_){}}
  function ensurePhotoAddDialog(){
    if(photoAddDialog)return photoAddDialog;
    photoAddDialog=document.createElement('dialog');photoAddDialog.id='design-customer-photo-add';photoAddDialog.className='design-photo-add';photoAddDialog.setAttribute('aria-labelledby','design-customer-photo-add-title');
    photoAddDialog.innerHTML='<form method="dialog"><header><div><small>KUNDENKONTO</small><h2 id="design-customer-photo-add-title">Fotos hinzufügen</h2></div><button type="button" class="secondary" data-photo-add-close aria-label="Schließen">×</button></header><label>Bildbeschreibung · optional<input id="design-customer-photo-caption" maxlength="160" placeholder="z. B. Garten vor dem Rückschnitt"></label><label class="check"><input id="design-customer-photo-profile" type="checkbox">Erstes Foto als Profilbild verwenden</label><label>Arbeitseinsatz · optional<select id="design-customer-photo-worklog"><option value="">Ohne Arbeitseinsatz</option></select></label><label>Aufnahmephase<select id="design-customer-photo-phase"><option value="">Allgemein</option><option value="Vorher">Vorher</option><option value="Während">Während</option><option value="Nachher">Nachher</option></select></label><p class="sub">Die Bilder werden verkleinert und zuerst offline auf diesem Gerät gespeichert. Die Zuordnung zum geöffneten Kunden bleibt erhalten.</p><div class="design-photo-add-actions"><button type="button" class="primary" data-photo-source="gallery">Aus Galerie auswählen</button><button type="button" class="secondary" data-photo-source="camera">Foto aufnehmen</button></div><input id="design-customer-photo-files" type="file" accept="image/*" multiple hidden><input id="design-customer-photo-camera" type="file" accept="image/*" capture="environment" hidden><p id="design-customer-photo-status" role="status" aria-live="polite"></p></form>';
    document.body.append(photoAddDialog);
    const gallery=get('design-customer-photo-files'),camera=get('design-customer-photo-camera'),status=get('design-customer-photo-status');
    photoAddDialog.querySelector('[data-photo-add-close]').onclick=()=>photoAddDialog.close();
    photoAddDialog.addEventListener('cancel',event=>{event.preventDefault();photoAddDialog.close();});
    photoAddDialog.querySelector('[data-photo-source="gallery"]').onclick=()=>{gallery.value='';gallery.click();};
    photoAddDialog.querySelector('[data-photo-source="camera"]').onclick=()=>{camera.value='';camera.click();};
    async function selected(input){
      const files=[...(input.files||[])];input.value='';if(!files.length)return;
      if(files.length>10){status.textContent='Bitte höchstens 10 Fotos gleichzeitig auswählen.';return;}
      const customer=customers().find(row=>row.id===profileId);if(!customer){status.textContent='Das Kundenkonto ist nicht mehr verfügbar.';return;}
      const caption=get('design-customer-photo-caption').value.trim(),asProfile=get('design-customer-photo-profile').checked,worklogId=get('design-customer-photo-worklog').value,phase=get('design-customer-photo-phase').value,worklog=worklogs().find(row=>row.id===worklogId&&row.customer_id===customer.id);
      photoAddDialog.querySelectorAll('button').forEach(button=>button.disabled=true);status.textContent='Fotos werden auf diesem Gerät vorbereitet …';
      let saved=0;const failures=[];
      for(let index=0;index<files.length;index++){
        const file=files[index];
        try{
          if(file.type&&!file.type.startsWith('image/'))throw new Error('keine unterstützte Bilddatei');
          const data=await compressedPhoto(file),name=(file.name||'kundenfoto').replace(/\.[^.]+$/,'')+'.jpg';
          const result=AndroidData.savePhoto(JSON.stringify({customer_id:customer.id,order_id:(asProfile&&index===0)?'':worklog?.order_id||'',worklog_id:(asProfile&&index===0)?'':worklog?.id||'',original_name:name,caption:(asProfile&&index===0)?'Profilbild':[phase,caption||name.replace(/\.jpg$/i,'')].filter(Boolean).join(' · '),role:(asProfile&&index===0)?'profile':'documentation',customer_visible:0,mime_type:'image/jpeg',data}));
          if(String(result).startsWith('ERROR:'))throw new Error(String(result).replace(/^ERROR:\s*/,''));saved++;
        }catch(error){failures.push((file.name||'Foto')+': '+(error.message||error));}
      }
      photoAddDialog.querySelectorAll('button').forEach(button=>button.disabled=false);
      if(saved){load();if(currentPage==='customer-profile-page')profileContent();}
      status.textContent=saved+' Foto(s) gespeichert.'+(failures.length?' '+failures.length+' fehlgeschlagen: '+failures.slice(0,2).join(' · '):'');
      if(saved&&!failures.length)setTimeout(()=>{if(photoAddDialog.open)photoAddDialog.close();},500);
    }
    gallery.addEventListener('change',()=>selected(gallery));camera.addEventListener('change',()=>selected(camera));
    return photoAddDialog;
  }
  function openPhotoAdd(){
    const dialog=ensurePhotoAddDialog();get('design-customer-photo-caption').value='';get('design-customer-photo-profile').checked=false;get('design-customer-photo-status').textContent='';const choices=get('design-customer-photo-worklog');choices.innerHTML='<option value="">Ohne Arbeitseinsatz</option>'+worklogs().filter(row=>row.customer_id===profileId).sort((a,b)=>String(b.work_date||'').localeCompare(String(a.work_date||''))).map(row=>'<option value="'+esc(row.id)+'">'+esc(worklogDateLabel(row.work_date)+' · '+(row.activity||'Arbeitseinsatz'))+'</option>').join('');get('design-customer-photo-phase').value='';dialog.showModal();dialog.querySelector('[data-photo-source="gallery"]').focus({preventScroll:true});
  }
  function applyViewerTransform(){if(!photoViewer)return;photoViewer.querySelector('img').style.transform=`translate3d(${viewerX}px,${viewerY}px,0) scale(${viewerScale})`;photoViewer.querySelector('[data-photo-zoom]').textContent=Math.round(viewerScale*100)+' %';}
  function ensurePhotoViewer(){
    if(photoViewer)return photoViewer;
    photoViewer=document.createElement('dialog');photoViewer.id='design-photo-view';photoViewer.className='design-photo-view';photoViewer.setAttribute('aria-labelledby','design-photo-caption');photoViewer.setAttribute('aria-describedby','design-photo-load-state');
    photoViewer.innerHTML='<header><button type="button" class="design-photo-close" aria-label="Foto schließen">×</button><p data-photo-counter></p><button type="button" class="design-photo-delete">Löschen</button></header><div class="design-photo-stage"><img alt="Kundenfoto" draggable="false"></div><footer><button type="button" class="secondary" data-photo-previous aria-label="Vorheriges Foto">‹</button><div><strong id="design-photo-caption" data-photo-caption></strong><small data-photo-zoom>100 %</small><small id="design-photo-load-state" data-photo-status role="status" aria-live="polite"></small></div><button type="button" class="secondary" data-photo-next aria-label="Nächstes Foto">›</button></footer>';
    document.body.append(photoViewer);const image=photoViewer.querySelector('img'),pointers=new Map();let startDistance=0,startScale=1,startX=0,startY=0;
    const reset=()=>{viewerScale=1;viewerX=0;viewerY=0;applyViewerTransform();};
    const show=()=>{
      const picture=viewerRows[viewerIndex];if(!picture)return;
      const token=++viewerLoadToken,status=photoViewer.querySelector('[data-photo-status]'),preview=picture.thumbnail_data,embedded=picture.full_data||picture.data,linked=!!(picture.worklog_id||picture.order_id),linkedNote=linked?' Dieses Belegfoto gehört zu '+(picture.worklog_id?'einer Arbeitsaufzeichnung':'einem Auftrag')+' und kann hier nicht gelöscht werden.':'';
      const source=embedded||preview||'';
      status.textContent=(embedded?'Original ist offline verfügbar.':preview?'Vorschau wird angezeigt. Original wird geladen …':'Original wird geladen …')+linkedNote;
      if(source&&String(source).startsWith('data:image/'))image.src=source;else image.removeAttribute('src');
      image.alt=picture.caption||'Kundenfoto';photoViewer.querySelector('[data-photo-caption]').textContent=picture.caption||picture.original_name||'Kundenfoto';photoViewer.querySelector('[data-photo-counter]').textContent=(viewerIndex+1)+' / '+viewerRows.length;photoViewer.querySelector('[data-photo-previous]').disabled=viewerRows.length<2;photoViewer.querySelector('[data-photo-next]').disabled=viewerRows.length<2;const remove=photoViewer.querySelector('.design-photo-delete');remove.disabled=linked;remove.title=linked?'Belegfotos bleiben mit ihrer Arbeitsaufzeichnung oder ihrem Auftrag erhalten.':'Dieses Kundenfoto entfernen';reset();
      if(embedded)return;
      if(typeof window.AndroidData?.getPhotoData!=='function'){status.textContent=(preview?'Vorschau wird angezeigt; Original ist auf diesem Gerät noch nicht verfügbar.':'Kein Bildinhalt auf diesem Gerät verfügbar.')+linkedNote;return;}
      requestAnimationFrame(()=>setTimeout(()=>{
        if(token!==viewerLoadToken||!photoViewer.open)return;
        try{
          const loaded=window.AndroidData.getPhotoData(picture.id);
          if(token!==viewerLoadToken||!photoViewer.open)return;
          if(String(loaded).startsWith('data:image/')){picture.full_data=loaded;image.src=loaded;status.textContent='Original ist offline verfügbar.'+linkedNote;}
          else status.textContent='Original derzeit nicht verfügbar: '+String(loaded||'kein lokaler oder verbundener Drive-Inhalt').replace(/^ERROR:\s*/,'')+linkedNote;
        }catch(error){if(token===viewerLoadToken&&photoViewer.open)status.textContent='Original derzeit nicht verfügbar: '+String(error.message||error)+linkedNote;}
      },0));
    };
    photoViewer._show=show;
    photoViewer.querySelector('.design-photo-close').onclick=()=>photoViewer.close();
    photoViewer.querySelector('[data-photo-previous]').onclick=()=>{viewerIndex=(viewerIndex-1+viewerRows.length)%viewerRows.length;show();};
    photoViewer.querySelector('[data-photo-next]').onclick=()=>{viewerIndex=(viewerIndex+1)%viewerRows.length;show();};
    photoViewer.querySelector('.design-photo-delete').onclick=()=>{
      const picture=viewerRows[viewerIndex];if(!picture)return;if(picture.worklog_id||picture.order_id){window.showUndoToast('Dieses Belegfoto bleibt mit der Arbeitsaufzeichnung oder dem Auftrag erhalten.');return;}if(!confirm('Dieses Kundenfoto dauerhaft entfernen? Die Löschung wird über den bestehenden Drive-Abgleich an andere Geräte weitergegeben.'))return;
      const result=AndroidData.removeEntity?.('photo',picture.id);if(!result||String(result).startsWith('ERROR:')){window.showUndoToast('Foto konnte nicht entfernt werden: '+String(result||'Funktion nicht verfügbar').replace(/^ERROR:\s*/,''));return;}
      photoViewer.close();load();if(currentPage==='customer-profile-page')profileContent();window.showUndoToast('Foto zum Entfernen vorgemerkt.',()=>{try{const restored=AndroidData.undoRemoveEntity?.('photo',picture.id);if(!restored||String(restored).startsWith('ERROR:'))throw new Error(String(restored||'Funktion nicht verfügbar').replace(/^ERROR:\s*/,''));load();if(currentPage==='customer-profile-page')profileContent();window.showUndoToast('Fotolöschung zurückgenommen.');}catch(error){window.showUndoToast('Rückgängig ist nicht mehr möglich: '+String(error.message||error));}});
    };
    image.addEventListener('dblclick',()=>{viewerScale=viewerScale>1?1:2.5;viewerX=viewerY=0;applyViewerTransform();});
    image.addEventListener('wheel',event=>{event.preventDefault();viewerScale=Math.min(5,Math.max(1,viewerScale*(event.deltaY<0?1.2:0.84)));if(viewerScale===1)viewerX=viewerY=0;applyViewerTransform();},{passive:false});
    image.addEventListener('pointerdown',event=>{image.setPointerCapture?.(event.pointerId);pointers.set(event.pointerId,{x:event.clientX,y:event.clientY});if(pointers.size===1){startX=event.clientX-viewerX;startY=event.clientY-viewerY;}if(pointers.size===2){const values=[...pointers.values()];startDistance=Math.hypot(values[0].x-values[1].x,values[0].y-values[1].y);startScale=viewerScale;}});
    image.addEventListener('pointermove',event=>{if(!pointers.has(event.pointerId))return;pointers.set(event.pointerId,{x:event.clientX,y:event.clientY});if(pointers.size===2){const values=[...pointers.values()],distance=Math.hypot(values[0].x-values[1].x,values[0].y-values[1].y);if(startDistance)viewerScale=Math.min(5,Math.max(1,startScale*distance/startDistance));}else if(viewerScale>1){viewerX=event.clientX-startX;viewerY=event.clientY-startY;}applyViewerTransform();});
    const release=event=>{pointers.delete(event.pointerId);if(pointers.size<2)startDistance=0;};image.addEventListener('pointerup',release);image.addEventListener('pointercancel',release);
    photoViewer.addEventListener('cancel',event=>{event.preventDefault();photoViewer.close();});photoViewer.addEventListener('close',()=>{viewerLoadToken++;pointers.clear();image.removeAttribute('src');setPhotoFullscreen(false);});
    photoViewer.addEventListener('keydown',event=>{if(event.key==='ArrowLeft'){event.preventDefault();photoViewer.querySelector('[data-photo-previous]').click();}if(event.key==='ArrowRight'){event.preventDefault();photoViewer.querySelector('[data-photo-next]').click();}if(event.key==='+'||event.key==='='){viewerScale=Math.min(5,viewerScale*1.25);applyViewerTransform();}if(event.key==='-'){viewerScale=Math.max(1,viewerScale/1.25);applyViewerTransform();}});
    return photoViewer;
  }
  function openPhotoViewer(id){
    viewerRows=photos().filter(row=>row.customer_id===profileId);viewerIndex=Math.max(0,viewerRows.findIndex(row=>row.id===id));const picture=viewerRows[viewerIndex],source=picture?.full_data||picture?.data||picture?.thumbnail_data;
    const canLoadOriginal=typeof window.AndroidData?.getPhotoData==='function'&&Boolean(picture?.id&&picture?.storage_name);
    if((!source||!String(source).startsWith('data:image/'))&&!canLoadOriginal){window.showUndoToast('Das Original ist noch nicht lokal verfügbar. Bitte Drive abgleichen.');return;}
    const dialog=ensurePhotoViewer();dialog.showModal();dialog._show();setPhotoFullscreen(true);dialog.querySelector('.design-photo-close').focus({preventScroll:true});
  }
  function profileActions(event){
    const button=event.target.closest('[data-design-action]');if(!button)return;event.preventDefault();event.stopImmediatePropagation();const action=button.dataset.designAction,id=button.dataset.id,customer=customers().find(x=>x.id===profileId);if(!customer)return;
    if(action==='edit'){window.editCustomer(profileId);return;}
    if(action==='call'){AndroidPhone.dial(customer.phone);return;}
    if(action==='maps'){AndroidPhone.openMaps([customer.street,customer.postal_code,customer.city,customer.country].filter(Boolean).join(', '));return;}
    if(action==='email'){location.href='mailto:'+encodeURIComponent(customer.email);return;}
    if(action==='worklog'){window.openWorklogDetail(id);return;}
    if(action==='new-work'){const form=get('entry');if((window.hasWorkEntryDraft())&&!confirm('Eine Arbeitsaufzeichnung wurde bereits begonnen. Diese Eingabe verwerfen und eine neue Arbeit für diesen Kunden beginnen?'))return;form.reset();get('date').value=localDate();openPage('work-entry');get('customer').value=profileId;ordersForCustomer();return;}
    if(action==='all-work'){openPage('worklogs');resetWorkFilters(profileId);return;}
    if(action==='new-order'){openPage('orders');get('add-order').click();get('order-edit-customer').value=profileId;return;}
    if(action==='order'){openPage('orders');get('order-edit-select').value=id;get('order-edit-select').dispatchEvent(new Event('change',{bubbles:true}));window.openDirectEditWindow('order-management-card','Auftrag im Überblick');return;}
    if(action==='invoice'){window.openInvoiceRecord(id);return;}
    if(action==='add-photos'){openPhotoAdd();return;}
    if(action==='photo'){openPhotoViewer(id);return;}
  }
  function init(){
    const css=document.createElement('link');css.rel='stylesheet';css.href='design-concept.css';document.head.append(css);
    const page=document.createElement('section');page.id='customer-profile-page';page.className='page';page.innerHTML='<div class="page-head"><button type="button" class="back" aria-label="Zurück zur Kundenliste">‹</button><h2>Kundenprofil</h2></div><div id="design-profile-content"></div><footer class="design-profile-footer"><button type="button" class="primary" data-design-action="edit">Kundendaten bearbeiten</button></footer>';document.querySelector('main.app').append(page);
    window.editCustomer=window.openCustomer;window.openCustomer=function(id){if(!customers().some(x=>x.id===id))return;profileId=id;profileContent();openPage('customer-profile-page');};
    window.openCustomerDocumentationCamera=function(id){if(!customers().some(x=>x.id===id))return false;window.openCustomer(id);openPhotoAdd();get('design-customer-photo-camera')?.click();return true;};
    const filter=document.createElement('select');filter.id='design-order-status';filter.className='design-order-filter';filter.setAttribute('aria-label','Auftragsstatus filtern');filter.innerHTML='<option value="all">Alle Aufträge</option><option value="open">Offen und geplant</option><option value="closed">Abgeschlossen</option>';get('order-list').before(filter);filter.addEventListener('change',compactLists);
    document.addEventListener('click',profileActions,true);
    document.addEventListener('click',metricActions,true);
    const todayOption=document.createElement('option');todayOption.value='today';todayOption.textContent='Heute';get('worklog-period').insertBefore(todayOption,get('worklog-period').options[1]);
    const order=get('order');order.required=false;const orderLabel=[...get('entry').querySelectorAll('label')].find(x=>x.htmlFor==='order'||x.textContent.includes('Projekt / Auftrag'));if(orderLabel)orderLabel.textContent='Auftrag · optional';order.options[0].textContent='Ohne Auftrag';
    const orderHint=order.closest('.entry-block')?.querySelector('small.sub');if(orderHint)orderHint.textContent='Du kannst Arbeiten direkt beim Kunden erfassen. Ein Auftrag ist optional.';
    simplifyWorkForms();
    const customerAdd=get('add-customer');customerAdd.textContent='+';customerAdd.setAttribute('aria-label','Neuen Kunden hinzufügen');get('customers').querySelector('.page-head').append(customerAdd);
    window.decorateWorklogActions=(actionDialog,ref)=>{if(ref.type!=='worklog'||actionDialog.querySelector('[data-design-duplicate]'))return;const button=document.createElement('button');button.type='button';button.className='secondary';button.dataset.designDuplicate='1';button.textContent='Duplizieren';button.onclick=()=>{const row=worklogs().find(x=>x.id===ref.id);if(!row)return;if(window.hasWorkEntryDraft()&&!confirm('Begonnene Eingabe verwerfen und diese Arbeit als Vorlage verwenden?'))return;actionDialog.close();const form=get('entry');form.reset();get('date').value=localDate();get('customer').value=row.customer_id;ordersForCustomer();get('order').value=row.order_id||'';for(const key of ['activity','hours','material','kilometers','location'])if(form.elements[key])form.elements[key].value=row[key]??'';openPage('work-entry');window.showUndoToast('Neue Eingabe aus Vorlage. Datum prüfen; Fotos, Abrechnung und interne Notizen wurden nicht kopiert.');};actionDialog.querySelector('.worklog-preview-actions')?.append(button);};
    const field=get('entry'),draftKey='hausgarten.entry.draft.v1';let draftTimer;
    const saveDraft=()=>{try{const values={};for(const input of field.elements)if(input.name&&input.type!=='file'&&input.type!=='submit'&&input.type!=='button')values[input.name]=input.value;const meaningful=window.hasWorkEntryDraft();if(meaningful){values.had_photo=!!get('photo')?.files?.length;localStorage.setItem(draftKey,JSON.stringify(values));}else localStorage.removeItem(draftKey);}catch(error){/* Explicit business save remains available if browser draft storage is full. */}};
    field.addEventListener('input',()=>{clearTimeout(draftTimer);draftTimer=setTimeout(saveDraft,300);});field.addEventListener('change',saveDraft);field.addEventListener('reset',()=>{clearTimeout(draftTimer);localStorage.removeItem(draftKey);});window.addEventListener('pagehide',saveDraft);
    try{const draft=JSON.parse(localStorage.getItem(draftKey)||'null');if(draft&&Object.values(draft).some(value=>String(value).trim())){for(const key of ['customer_id','work_date','activity','hours','start_time','end_time','break_minutes','kilometers','location','material','notes','photo_caption'])if(field.elements[key]&&draft[key]!=null)field.elements[key].value=draft[key];ordersForCustomer();if(draft.order_id&&[...order.options].some(x=>x.value===draft.order_id))order.value=draft.order_id;if(draft._session_id&&JSON.parse(AndroidData.getWorkSession()).id===draft._session_id){let hidden=get('work-session-id');if(!hidden){hidden=document.createElement('input');hidden.type='hidden';hidden.id='work-session-id';hidden.name='_session_id';field.append(hidden);}hidden.value=draft._session_id;}window.showUndoToast('Begonnene Arbeitsaufzeichnung wiederhergestellt.'+(draft.had_photo?' Fotos bitte erneut auswählen.':''));}}catch(error){/* Keep malformed draft untouched; never import it into business records. */}
    const renderBase=window.render;window.render=function(){renderBase();homeOverview();compactLists();if(currentPage==='customer-profile-page')profileContent();};
    const openBase=window.openPage;window.openPage=function(...args){openBase(...args);if(currentPage==='customer-profile-page')document.querySelectorAll('.nav').forEach(x=>x.classList.toggle('active',x.dataset.open==='customers'));};
    const backBase=window.handleAndroidBack;window.handleAndroidBack=function(){for(const dialog of [photoViewer,photoAddDialog])if(dialog?.open){dialog.close();return true;}return backBase();};
    render();
  }
  function simplifyWorkForms(){
    // Move existing controls; IDs, event handlers and save channels stay intact.
    function moveField(target,id){const input=get(id);if(!input)return;const wrap=input.closest('.select-search-wrap')||input;const label=[...input.closest('form, #edit-worklog-card').querySelectorAll('label')].find(x=>x.htmlFor===id)||wrap.previousElementSibling;if(label?.tagName==='LABEL')target.append(label);target.append(wrap);}
    function extra(parent,key,title){const section=document.createElement('details');section.className='more simple-extra';section.dataset.section=key;section.innerHTML='<summary>'+title+'</summary><div class="more-fields"></div>';parent.append(section);return section.querySelector('div');}
    const form=get('entry'),primary=document.createElement('section');primary.className='simple-primary';
    form.prepend(primary);moveField(primary,'customer');primary.append(get('work-entry-add-customer'));
    const grid=document.createElement('div');grid.className='form-grid simple-date-hours';const date=document.createElement('div'),duration=document.createElement('div');grid.append(date,duration);primary.append(grid);moveField(date,'date');moveField(duration,'entry-hours');moveField(primary,'activity');primary.append(get('hours-result'));
    const times=extra(form,'time','+ Zeit und Pause');const timeGrid=get('start').closest('.form-grid');times.append(timeGrid);moveField(times,'pause');
    const material=extra(form,'material','+ Material und Kilometer');moveField(material,'entry-material');moveField(material,'entry-kilometers');
    const notes=extra(form,'notes','+ Fotos und Notizen');moveField(notes,'entry-notes');moveField(notes,'photo');moveField(notes,'entry-photo_caption');
    const job=extra(form,'order','+ Auftrag und Arbeitsort · optional');moveField(job,'order');job.append(get('work-entry-add-order'));moveField(job,'entry-location');
    // Empty old shells contain no data and no actions; remove only those shells.
    form.querySelectorAll('.entry-block').forEach(x=>x.remove());const oldExtra=form.querySelector('.more:not(.simple-extra)');if(oldExtra)oldExtra.remove();form.append(form.querySelector('.actions'));
    const card=get('edit-worklog-card'),main=document.createElement('section');main.className='simple-primary';card.prepend(main);moveField(main,'edit-worklog-customer');
    const editGrid=document.createElement('div');editGrid.className='form-grid simple-date-hours';const editDate=document.createElement('div'),editHours=document.createElement('div');editGrid.append(editDate,editHours);main.append(editGrid);moveField(editDate,'edit-worklog-date');moveField(editHours,'edit-worklog-hours');moveField(main,'edit-worklog-activity');
    const editTimes=extra(card,'time','+ Zeit und Pause');editTimes.append(get('edit-worklog-start').closest('.form-grid'));moveField(editTimes,'edit-worklog-pause');
    const editMaterial=extra(card,'material','+ Material und Kilometer');moveField(editMaterial,'edit-worklog-material');moveField(editMaterial,'edit-worklog-kilometers');
    const editNotes=extra(card,'notes','+ Fotos, Notizen und Nachträge');moveField(editNotes,'edit-worklog-notes');const addendum=get('worklog-detail').querySelector('.worklog-addendum');if(addendum)editNotes.append(addendum);
    const pictures=document.createElement('div');pictures.className='detail-photo-gallery';editNotes.append(pictures);
    const editJob=extra(card,'order','+ Auftrag und Arbeitsort · optional');moveField(editJob,'edit-worklog-order');moveField(editJob,'edit-worklog-location');
    const status=extra(card,'status','+ Abrechnung und Kundenansicht');['edit-worklog-billed','edit-worklog-visible'].forEach(id=>status.append(get(id).closest('label')));
    const invoiceAction=card.querySelector('[data-invoice-worklog]');if(invoiceAction)status.append(invoiceAction);
    card.querySelectorAll('.worklog-edit-section-heading,.worklog-hours-hint,h3').forEach(x=>x.remove());card.querySelector('.worklog-edit-advanced')?.remove();
    const cancel=document.createElement('button');cancel.type='button';cancel.className='secondary';cancel.id='new-work-cancel';cancel.textContent='Abbrechen';cancel.onclick=()=>{if(window.hasWorkEntryDraft()&&!confirm('Begonnene Eingabe verwerfen?'))return;form.reset();get('date').value=localDate();window.handleAndroidBack();};form.querySelector('.actions').append(cancel);
  }
  document.addEventListener('DOMContentLoaded',()=>setTimeout(init,0),{once:true});
})();
/* v1.39: focused presentation; every write still uses the original native saver. */
(() => {
  'use strict';
  const get=id=>document.getElementById(id);
  const minutes=value=>{const total=Math.round(Number(value||0)*60);return Math.floor(total/60)+' h '+String(total%60).padStart(2,'0')+' min';};
  const pickers=new Map();
  let picker=null;
  function refreshPicker(select,button){
    button.textContent=(select.selectedOptions[0]?.textContent||'Bitte auswählen')+'  ⌄';
    button.disabled=select.disabled;
  }
  function closePicker(){if(picker?.open)picker.close();}
  function choose(select){
    if(!picker){picker=document.createElement('dialog');picker.id='design-select-dialog';picker.className='design-picker';picker.innerHTML='<header><h3 id="design-picker-title"></h3><button type="button" class="secondary" aria-label="Auswahl schließen">✕</button></header><input type="search" aria-label="Auswahl durchsuchen" placeholder="Suchen …"><div class="design-picker-results"></div>';picker.setAttribute('aria-labelledby','design-picker-title');picker.querySelector('header button').onclick=closePicker;document.body.append(picker);}
    const title=[...document.querySelectorAll('label')].find(x=>x.htmlFor===select.id)?.textContent||select.getAttribute('aria-label')||'Auswählen';
    picker.querySelector('h3').textContent=title;
    const search=picker.querySelector('input'),results=picker.querySelector('.design-picker-results');
    search.value='';
    function draw(){
      const term=search.value.trim().toLocaleLowerCase('de-AT');
      results.replaceChildren();
      for(const option of select.options){
        const customer=customers().find(x=>String(x.id)===option.value);
        const searchable=[option.textContent,customer?.company,customer?.city,customer?.street,customer?.postal_code].filter(Boolean).join(' ').toLocaleLowerCase('de-AT');
        if(term&&!searchable.includes(term))continue;
        const row=document.createElement('button');row.type='button';row.className='design-picker-option';row.dataset.value=option.value;
        const name=document.createElement('strong');name.textContent=option.textContent;row.append(name);
        if(customer){const detail=document.createElement('small');detail.textContent=[customer.city,customer.street,customer.active===0?'Archiviert':''].filter(Boolean).join(' · ');row.append(detail);}
        row.setAttribute('aria-pressed',String(option.selected));
        row.onclick=()=>{select.value=option.value;select.dispatchEvent(new Event('change',{bubbles:true}));refreshPicker(select,pickers.get(select.id));closePicker();};
        results.append(row);
      }
      if(!results.childElementCount){const empty=document.createElement('p');empty.className='sub';empty.textContent='Kein Treffer. Suche ändern oder einen neuen Kunden anlegen.';results.append(empty);}
      const add=select.id==='customer'?get('work-entry-add-customer'):select.id==='order'?get('work-entry-add-order'):select.id==='order-edit-customer'?get('order-add-customer'):null;
      if(add){const row=document.createElement('button');row.type='button';row.className='secondary';row.textContent=select.id==='order'?'+ Neuer Auftrag':'+ Neuer Kunde';row.onclick=()=>{closePicker();add.click();};results.append(row);}
    }
    search.oninput=draw;draw();picker.showModal();
    // Do not raise the keyboard until the user actually taps Search.
    picker.querySelector('header button').focus();
  }
  function bindPickers(){
    for(const id of ['customer','order','edit-worklog-customer','edit-worklog-order','order-edit-customer','week-customer','worklog-customer-filter','customer-billing-customer','entry-billing-customer','edit-billing-customer','order-billing-customer']){
      const select=get(id);if(!select)continue;
      if(pickers.has(id)){refreshPicker(select,pickers.get(id));continue;}
      let wrap=select.closest('.select-search-wrap');
      if(!wrap){wrap=document.createElement('div');wrap.className='select-search-wrap';select.before(wrap);wrap.append(select);}
      wrap.classList.add('design-unified-picker');select.dataset.unifiedPicker='1';select.tabIndex=-1;select.setAttribute('aria-hidden','true');
      const button=document.createElement('button');button.type='button';button.className='design-picker-button';button.dataset.pickerFor=id;button.setAttribute('aria-haspopup','dialog');button.onclick=()=>choose(select);
      const label=[...document.querySelectorAll('label')].find(x=>x.htmlFor===id);
      if(label){button.id=id+'-picker';label.htmlFor=button.id;button.setAttribute('aria-label',label.textContent.trim());}
      wrap.append(button);pickers.set(id,button);select.addEventListener('change',()=>refreshPicker(select,button));select.addEventListener('billing-options-updated',()=>refreshPicker(select,button));refreshPicker(select,button);
    }
  }
  function focusLayout(){
    const editing=currentPage==='work-entry'||currentPage==='customer-edit'||currentPage==='worklog-detail'&&get('worklog-detail')?.dataset.workMode==='edit';
    document.body.classList.toggle('design-focused',editing);
    document.body.dataset.focusPage=editing?currentPage:'';
    const status=get('worklog-detail-status');
    if(currentPage==='worklog-detail'&&get('worklog-detail')?.dataset.workMode==='edit'&&status?.textContent.startsWith('Eintrag geladen.'))status.textContent='Änderungen werden mit Speichern oder Zurück lokal gesichert.';
    for(const [id,button]of pickers){const select=get(id);if(select)refreshPicker(select,button);}
  }
  function feedback(){
    for(const root of [get('entry'),get('edit-worklog-card')]){
      if(!root)continue;
      for(const section of root.querySelectorAll('.simple-extra')){
        let preview=section.querySelector('.design-extra-preview');
        if(!preview){preview=document.createElement('small');preview.className='design-extra-preview';section.querySelector('summary').append(preview);}
        const values=[...section.querySelectorAll('input,textarea,select')].filter(x=>x.type!=='checkbox'&&x.type!=='file').map(x=>x.tagName==='SELECT'?(x.value?x.selectedOptions[0]?.textContent:''):x.value).filter(x=>x&&x!=='0');
        const text=values.join(' · ');preview.textContent=text.length>65?text.slice(0,65)+'…':text||'Bei Bedarf ergänzen';
      }
    }
  }
  function timeControls(root,ids){
    if(!root)return;
    const [hourId,startId,endId,pauseId]=ids,hour=get(hourId),start=get(startId),end=get(endId),pause=get(pauseId);
    const section=root.querySelector('[data-section="time"]'),grid=hour.closest('.simple-date-hours');
    const modes=document.createElement('div');modes.className='design-time-mode';modes.setAttribute('role','group');modes.setAttribute('aria-label','Arbeitszeit eingeben');
    modes.innerHTML='<button type="button" data-time-mode="duration">Dauer</button><button type="button" data-time-mode="clock">Uhrzeiten</button><p class="design-time-result" role="status"></p><button type="button" data-time-apply hidden>Berechnung übernehmen</button>';
    grid.after(modes);
    const hint=modes.querySelector('p');
    let explicitClock=false;
    hour.step='any';
    function update(applyCalculation=false){
      const timed=!!(start.value||end.value)||explicitClock;
      hour.readOnly=timed||!!get('work-session-id')?.value&&root.id==='entry';
      modes.querySelectorAll('[data-time-mode]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.timeMode===(timed?'clock':'duration'))));
      modes.querySelector('[data-time-apply]').hidden=true;
      if(timed&&start.value&&end.value){
        try{
          const amount=effectiveWorkHours(start.value,end.value,Number(pause.value||0));
          if(applyCalculation&&Math.abs(Number(hour.value)-amount)>.001){hour.value=String(amount);hour.dispatchEvent(new Event('input',{bubbles:true}));}
          const mismatch=Math.abs(Number(hour.value)-amount)>.001;
          hint.textContent=mismatch?'Uhrzeiten ergeben '+minutes(amount)+', eingetragen sind '+minutes(hour.value)+'. Berechnung bewusst übernehmen oder Dauer wählen.':'Nettozeit: '+minutes(amount)+' · Pause bereits abgezogen';
          modes.querySelector('[data-time-apply]').hidden=!mismatch;
        }catch(error){hint.textContent=error.message;}
      }else hint.textContent=timed?'Beginn, Ende und Pause ergänzen.':'Nettozeit: '+minutes(hour.value)+' · eine Pause nicht nochmals abziehen';
      root.dataset.timeMode=timed?'clock':'duration';
    }
    modes.querySelector('[data-time-mode="duration"]').onclick=()=>{
      if((start.value||end.value)&&!confirm('Auf Netto-Dauer umstellen? Beginn, Ende und Pause werden nur in dieser Eingabe geleert. Die gespeicherte Aufzeichnung bleibt bis zum Speichern unverändert.'))return;
      explicitClock=false;
      for(const input of [start,end,pause]){input.value=input===pause?'0':'';input.dispatchEvent(new Event('input',{bubbles:true}));}
      hour.readOnly=false;section.open=false;update();
    };
    modes.querySelector('[data-time-mode="clock"]').onclick=()=>{explicitClock=true;section.open=true;update();start.focus();};
    modes.querySelector('[data-time-apply]').onclick=()=>update(true);
    for(const input of [hour,start,end,pause])input.addEventListener('input',()=>{if(input===start||input===end)explicitClock=false;update(input!==hour);});
    root.addEventListener('reset',()=>{explicitClock=false;setTimeout(update,0);});
    root._refreshTimeMode=()=>{explicitClock=false;update();};
    root._applyCalculatedTime=()=>update(true);
    root._validateTimeMode=()=>{
      if(root.dataset.timeMode!=='clock')return true;
      try{const amount=effectiveWorkHours(start.value,end.value,Number(pause.value||0));if(Math.abs(Number(hour.value)-amount)>.001){update();hint.scrollIntoView({block:'center'});window.showUndoToast('Zeitangaben widersprechen sich. Berechnung übernehmen oder Netto-Dauer wählen.');return false;}return true;}
      catch(error){hint.textContent=error.message;section.open=true;start.focus();return false;}
    };
    const label=root.querySelector('label[for="'+hourId+'"]')||hour.parentElement.querySelector('label');if(label)label.textContent='Netto-Stunden';
    update();
  }
  function listPresentation(){
    const records=new Map(worklogs().map(row=>[String(row.id),row]));
    get('worklog-list')?.querySelectorAll('.design-date-group').forEach(x=>x.remove());
    let previous='';
    get('worklog-list')?.querySelectorAll('.worklog-entry').forEach(row=>{
      const record=records.get(row.dataset.editWorklog);if(!record)return;
      let parent=row.parentElement;
      if(parent===get('worklog-list')){const wrapper=document.createElement('div');row.before(wrapper);wrapper.append(row);parent=wrapper;}
      const outer=row.closest('.swipe-shell')||parent;
      if(record.work_date!==previous){const heading=document.createElement('h3');heading.className='design-date-group';heading.textContent=worklogDateLabel(record.work_date);outer.before(heading);previous=record.work_date;}
      parent.classList.add('design-record-row');
      if(!parent.querySelector('.design-record-menu')){const menu=document.createElement('button');menu.type='button';menu.className='design-record-menu';menu.textContent='⋮';menu.setAttribute('aria-label','Aktionen für '+customerName(record.customer_id));menu.onclick=()=>row.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));parent.append(menu);}
      const status=row.querySelector('.worklog-entry-status');if(status)status.textContent=(Number(record.billed)?'App: verrechnet':'App: offen')+(record.pending?' · wartet auf Drive':'');
      const duration=row.querySelector('.worklog-entry-hours');if(duration)duration.textContent=minutes(record.hours);
    });
    for(const card of get('customer-list')?.querySelectorAll('.customer-card')||[]){
      if(card.querySelector('.design-record-menu'))continue;
      const menu=document.createElement('button');menu.type='button';menu.className='design-record-menu';menu.textContent='⋮';menu.setAttribute('aria-label','Kundenaktionen');menu.onclick=()=>card.querySelector('[data-customer-open]').dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));card.append(menu);
    }
    for(const row of get('order-list')?.querySelectorAll('[data-edit-order]')||[]){
      let parent=row.parentElement;
      if(parent===get('order-list')){const wrapper=document.createElement('div');row.before(wrapper);wrapper.append(row);parent=wrapper;}
      parent.classList.add('design-record-row');
      if(parent.querySelector('.design-record-menu'))continue;
      const menu=document.createElement('button');menu.type='button';menu.className='design-record-menu';menu.textContent='⋮';menu.setAttribute('aria-label','Auftragsaktionen');menu.onclick=()=>row.dispatchEvent(new MouseEvent('contextmenu',{bubbles:true,cancelable:true}));parent.append(menu);
    }
  }
  function recentActivities(){
    const field=get('activity');let box=get('design-recent-activities');
    if(!box){box=document.createElement('div');box.id='design-recent-activities';box.className='design-suggestions';field.after(box);}
    box.replaceChildren();
    const seen=new Set();for(const row of worklogs().slice().sort((a,b)=>String(b.work_date).localeCompare(String(a.work_date)))){
      const text=String(row.activity||'').trim();if(!text||text.length>90||seen.has(text))continue;seen.add(text);
      const button=document.createElement('button');button.type='button';button.textContent=text;button.onclick=()=>{field.value=text;field.dispatchEvent(new Event('input',{bubbles:true}));};box.append(button);if(seen.size===3)break;
    }
  }
  function init(){
    timeControls(get('entry'),['entry-hours','start','end','pause']);
    timeControls(get('edit-worklog-card'),['edit-worklog-hours','edit-worklog-start','edit-worklog-end','edit-worklog-pause']);
    const speech=window.acceptNativeSpeech;window.acceptNativeSpeech=function(...args){speech(...args);get('entry')._refreshTimeMode();get('entry')._applyCalculatedTime();feedback();};
    get('entry').addEventListener('submit',event=>{if(!get('work-session-id')?.value&&!get('entry')._validateTimeMode()){event.preventDefault();event.stopImmediatePropagation();}},true);
    const save=window.saveWorklogDetail;window.saveWorklogDetail=function(...args){if(!get('edit-worklog-card')._validateTimeMode())return false;return save(...args);};
    const openDetail=window.openWorklogDetail;window.openWorklogDetail=function(...args){openDetail(...args);get('edit-worklog-card')._refreshTimeMode();focusLayout();};
    const edit=window.beginWorklogEdit;window.beginWorklogEdit=function(...args){edit(...args);get('edit-worklog-card')._refreshTimeMode();focusLayout();};
    const back=window.handleAndroidBack;window.handleAndroidBack=function(...args){if(picker?.open){closePicker();return true;}const result=back(...args);focusLayout();return result;};
    const renderBase=window.render;window.render=function(...args){renderBase(...args);bindPickers();listPresentation();recentActivities();feedback();focusLayout();};
    const openBase=window.openPage;window.openPage=function(...args){openBase(...args);if(currentPage==='work-entry')get('entry')._refreshTimeMode();focusLayout();feedback();};
    const sync=window.onDriveSync;window.onDriveSync=function(result){sync(result);syncLabel();};
    const filter=document.createElement('div');filter.className='design-quick-filters';
    for(const [value,title]of [['all','Alle'],['today','Heute'],['current-week','Diese Woche']]){const button=document.createElement('button');button.type='button';button.textContent=title;button.dataset.period=value;button.onclick=()=>{get('worklog-period').value=value;get('worklog-period').dispatchEvent(new Event('change',{bubbles:true}));};filter.append(button);}
    get('worklog-search').after(filter);
    const filters=get('worklog-filter-panel');const extras=document.createElement('details');extras.className='design-filter-options';extras.innerHTML='<summary>Weitere Filter</summary>';filters.querySelector('.form-grid').before(extras);extras.append(filters.querySelector('.form-grid'),get('worklog-filter-reset'));
    get('worklog-billing-filter').options[1].textContent='App: offen';get('worklog-billing-filter').options[2].textContent='App: verrechnet';
    const worksAdd=document.createElement('button');worksAdd.type='button';worksAdd.className='primary design-list-add';worksAdd.id='design-add-work';worksAdd.textContent='+ Arbeit';worksAdd.onclick=()=>openPage('work-entry');get('worklogs').append(worksAdd);
    const customerAdd=get('add-customer');customerAdd.textContent='+ Kunde';customerAdd.classList.add('design-list-add');get('customers').append(customerAdd);
    get('customer-search').addEventListener('input',listPresentation);
    document.addEventListener('input',feedback);
    document.addEventListener('click',event=>setTimeout(()=>{focusLayout();feedback();if(event.target.closest('[data-work-window-cancel]'))get('edit-worklog-card')._refreshTimeMode();},0));
    new MutationObserver(focusLayout).observe(document.querySelector('main.app'),{subtree:true,attributes:true,attributeFilter:['class','data-work-mode']});
    function keyboard(){const v=window.visualViewport;document.documentElement.style.setProperty('--design-keyboard',Math.max(0,v?window.innerHeight-v.height-v.offsetTop:0)+'px');}
    window.visualViewport?.addEventListener('resize',keyboard);keyboard();
    document.body.dataset.designVersion='1.39';
    render();
  }
  document.addEventListener('DOMContentLoaded',()=>setTimeout(init,50),{once:true});
})();

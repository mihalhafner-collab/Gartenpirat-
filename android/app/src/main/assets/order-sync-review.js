/* Inspect unsynchronized orders without changing them; save only by explicit user action. */
(() => {
 'use strict';if(window.OrderSyncReview)return;
 const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const dialog=document.createElement('dialog');dialog.id='order-sync-review';dialog.className='sr-dialog';dialog.setAttribute('aria-labelledby','sr-title');document.body.appendChild(dialog);
 let inspection=null,dirty=false,saving=false;
 function api(name,value){if(typeof window.AndroidData?.[name]!=='function')throw new Error('Diese Prüfung ist in der Android-App nach dem Update verfügbar.');const raw=AndroidData[name](value);if(String(raw).startsWith('ERROR:'))throw new Error(String(raw).replace(/^ERROR:\s*/,''));return typeof raw==='string'?JSON.parse(raw):raw;}
 function close(force=false){if(dirty&&!force&&!confirm('Prüfung ohne Speichern schließen? Deine bisherigen Auftragsdaten bleiben erhalten.'))return false;dialog.close();dirty=false;inspection=null;return true;}
 function refresh(){
  if(typeof state==='undefined')return;
  const pending=(state.pending||[]).filter(a=>a.kind==='order'&&a.payload?.id),ids=[...new Set(pending.map(a=>a.payload.id))];
  const queue=new Map((state.sync_queue?.items||[]).map(row=>[row.client_id,row]));
  for(const [target,id] of [['order-list','sr-orders'],['pending','sr-pending']]){
   const anchor=document.getElementById(target);if(!anchor)continue;
   let section=document.getElementById(id);if(!section){section=document.createElement('section');section.id=id;section.className='sr-queue';anchor.after(section);}
   section.hidden=!ids.length;
   section.innerHTML=ids.length?'<h3>Nicht synchronisierte Aufträge</h3><p>Prüfe die Angaben und entscheide anschließend, was gespeichert wird.</p>'+ids.map(orderId=>{
    const actions=pending.filter(a=>a.payload.id===orderId),last=actions.at(-1).payload,issues=actions.map(a=>queue.get(a.client_id)).filter(q=>q&&['sync_conflict','sync_failed'].includes(q.state));
    const message=issues.length?(issues[0].error||issues[0].message):'Auf diesem Gerät gespeichert · Abgleich ausstehend';
    return '<div class="sr-row"><strong>'+esc(last.title||'Auftrag ohne Bezeichnung')+'</strong><small>'+esc(message)+'</small><button type="button" data-review-order="'+esc(orderId)+'">Prüfen und bearbeiten</button></div>';
   }).join(''):'';
  }
 }
 function statusOptions(value){return [...new Set(['Geplant','In Arbeit','Wartet auf Material','Erledigt','Abgerechnet',value].filter(Boolean))].map(v=>'<option value="'+esc(v)+'"'+(v===value?' selected':'')+'>'+esc(v)+'</option>').join('');}
 function open(id){
  try{
   inspection=api('inspectOrder',id);const row=inspection.order,customers=inspection.customers||[],opts=(value,optional=false)=>(optional?'<option value="">Wie beim Kunden hinterlegt</option>':'<option value="">Bitte auswählen</option>')+(value&&!customers.some(c=>c.id===value)?'<option selected value="'+esc(value)+'">Bisherige Zuordnung fehlt</option>':'')+customers.map(c=>'<option value="'+esc(c.id)+'"'+(c.id===value?' selected':'')+'>'+esc(c.name)+(c.active===0?' (inaktiv)':'')+'</option>').join('');
   dialog.innerHTML='<header><h2 id="sr-title">Auftrag prüfen</h2><button type="button" data-sr-close aria-label="Prüfung schließen">×</button></header><div class="sr-body">'+
    '<p>Die Prüfung verändert keine Daten. Deine Entscheidung wird erst mit „Ergebnis speichern“ übernommen.</p>'+
    '<ul class="sr-checks">'+inspection.checks.map(c=>'<li class="'+(c.ok?'sr-ok':'sr-error')+'"><strong>'+esc(c.label)+': '+(c.ok?'In Ordnung':'Bitte korrigieren')+'</strong><span>'+esc(c.message)+'</span></li>').join('')+'</ul>'+
    (inspection.reasons?.length?'<aside class="sr-reasons"><strong>Warum der Abgleich offen ist</strong>'+inspection.reasons.map(r=>'<p>'+esc(r)+'</p>').join('')+'</aside>':'<p>Bislang kein fachlicher Fehler erkannt. Eine ausstehende Übertragung kann an Verbindung oder Drive-Zugriff liegen.</p>')+
    '<form id="sr-form"><fieldset><legend>Was soll gespeichert werden?</legend><label><input type="radio" name="decision" value="save" checked> Die unten angezeigten Angaben übernehmen</label>'+
    (inspection.current&&inspection.reasons?.length?'<label><input type="radio" name="decision" value="keep"> Den bereits übernommenen Stand behalten</label><details><summary>Bereits übernommener Stand</summary><p>'+esc(inspection.current.title)+' · '+esc(customers.find(c=>c.id===inspection.current.customer_id)?.name||inspection.current.customer_id)+' · '+esc(inspection.current.status)+'</p><p>'+esc(inspection.current.description||'Keine Beschreibung')+'</p><p>'+esc(inspection.current.location||'Kein Ort')+' · '+esc(inspection.current.scheduled_date||'Kein Termin')+'</p></details>':'')+
    '</fieldset><fieldset id="sr-fields"><legend>Auftragsangaben bearbeiten</legend><label>Bezeichnung<input name="title" required maxlength="500" value="'+esc(row.title)+'"></label><label>Kunde<select name="customer_id" required>'+opts(row.customer_id)+'</select></label><label>Rechnungsempfänger<select name="billing_customer_id">'+opts(row.billing_customer_id,true)+'</select></label><label>Beschreibung<textarea name="description">'+esc(row.description)+'</textarea></label><label>Ort<input name="location" value="'+esc(row.location)+'"></label><label>Termin · optional<input type="date" name="scheduled_date" value="'+esc(row.scheduled_date)+'"></label><label>Status<select name="status">'+statusOptions(row.status||'Geplant')+'</select></label><label><input name="customer_visible" type="checkbox" '+(Number(row.customer_visible??1)?'checked':'')+'> Für Kunden sichtbar</label></fieldset>'+
    '<p id="sr-message" role="status" aria-live="polite"></p><div class="sr-actions"><button type="button" data-sr-reload>Neu prüfen</button><button type="button" data-sr-close>Später entscheiden</button><button type="submit" class="primary">Ergebnis speichern</button></div></form></div>';
   dirty=false;if(!dialog.open)dialog.showModal();dialog.querySelector('[data-sr-close]').focus();
  }catch(error){if(dialog.open)dialog.querySelector('#sr-message').textContent=error.message;else alert(error.message);}
 }
 dialog.addEventListener('input',()=>{dirty=true;});
 dialog.addEventListener('change',event=>{dirty=true;if(event.target.name==='decision')dialog.querySelector('#sr-fields').disabled=event.target.value==='keep';});
 dialog.addEventListener('click',event=>{
  if(event.target.closest('[data-sr-close]'))close();
  if(event.target.closest('[data-sr-reload]')&&(!dirty||confirm('Neu prüfen und die noch nicht gespeicherten Eingaben ersetzen?')))open(inspection.id);
 });
 dialog.addEventListener('cancel',event=>{event.preventDefault();close();});
 dialog.addEventListener('submit',event=>{
  if(event.target.id!=='sr-form')return;event.preventDefault();if(saving)return;
  const form=event.target,values=Object.fromEntries(new FormData(form)),decision=values.decision;
  const request={id:inspection.id,token:inspection.token,decision,order:{...values,customer_visible:form.elements.customer_visible.checked?1:0}};
  saving=true;const button=form.querySelector('[type=submit]');button.disabled=true;
  try{
   api('saveReviewedOrder',JSON.stringify(request));close(true);
   if(typeof load==='function')load();refresh();window.showUndoToast?.('Prüfergebnis lokal gespeichert. Der Drive-Abgleich folgt automatisch.');
  }catch(error){dialog.querySelector('#sr-message').textContent=error.message;}
  finally{saving=false;button.disabled=false;}
 });
 document.addEventListener('click',event=>{const button=event.target.closest('[data-review-order]');if(button){event.preventDefault();event.stopPropagation();open(button.dataset.reviewOrder);}},true);
 const priorBack=window.handleAndroidBack;window.handleAndroidBack=function(){if(dialog.open){close();return true;}return typeof priorBack==='function'?priorBack.apply(this,arguments):false;};
 let scheduled=false;const observer=new MutationObserver(()=>{if(!scheduled){scheduled=true;queueMicrotask(()=>{scheduled=false;refresh();});}});
 for(const id of ['order-list','pending']){const node=document.getElementById(id);if(node)observer.observe(node,{childList:true});}
 window.OrderSyncReview=Object.freeze({refresh,open});refresh();
})();

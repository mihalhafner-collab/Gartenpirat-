/* One-shot location suggestion rules; never assigns a customer automatically. */
(() => {
  'use strict';
  const normalize=value=>String(value||'').normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/ß/g,'ss').replace(/strasse\b/g,'str').replace(/[.,]/g,' ').replace(/\s+/g,' ').trim();
  const street=value=>normalize(value).replace(/\s+(\d+[a-z]?(?:\s*[-/]\s*\d+[a-z]?)?)$/,'').trim();
  const house=value=>normalize(value).match(/\s+(\d+[a-z]?(?:\s*[-/]\s*\d+[a-z]?)?)$/)?.[1]?.replace(/\s/g,'')||'';
  const country=value=>{const n=normalize(value);return ['at','osterreich','austria'].includes(n)?'at':n;};
  function addressText(a){return [[a?.street,a?.house_number].filter(Boolean).join(' '),[a?.postal_code,a?.city].filter(Boolean).join(' ')].filter(Boolean).join(', ');}
  function candidates(rows,fix){
    if(fix?.status!=='OK'||fix.approximate||!Number.isFinite(Number(fix.accuracy_m))||Number(fix.accuracy_m)>100||Number(fix.accuracy_m)<0)return [];
    const a=fix.address;if(!a||!street(a.street)||(!a.city&&!a.postal_code))return [];
    return rows.filter(c=>Number(c.active??1)!==0).map(c=>{
      const sameStreet=street(c.street)===street(a.street),samePostal=!!a.postal_code&&normalize(c.postal_code)===normalize(a.postal_code),sameCity=!!a.city&&normalize(c.city)===normalize(a.city);
      if(!sameStreet||(!samePostal&&!sameCity)||(a.postal_code&&c.postal_code&&!samePostal)||(a.country&&c.country&&country(a.country)!==country(c.country)))return null;
      const exact=!!a.house_number&&house(c.street)===normalize(a.house_number).replace(/\s/g,'');
      return {customer:c,exact,score:exact?2:1};
    }).filter(Boolean).sort((a,b)=>b.score-a.score||String(a.customer.name).localeCompare(String(b.customer.name),'de'));
  }
  const rules={normalize,addressText,candidates};globalThis.WorkLocationRules=rules;
  if(typeof module!=='undefined')module.exports=rules;
  if(typeof document==='undefined')return;
  const get=id=>document.getElementById(id);
  let dialog,request='',fix=null,newCustomer=null,discarded='',starting=false;
  const requestId=()=> 'loc-'+Date.now()+'-'+(crypto.randomUUID?.()||Math.random().toString(16).slice(2));
  function native(name,...args){
    try{const raw=AndroidData[name](...args);if(String(raw).startsWith('ERROR:'))throw new Error(String(raw).slice(6).trim());return JSON.parse(raw);}
    catch(error){window.showUndoToast(error.message||'Die Zeiterfassung konnte nicht geändert werden.');return null;}
  }
  function start(customerId,location){
    if(starting)return false;
    const session=native('startWorkSession',JSON.stringify({request_id:requestId(),work_date:localDate(),customer_id:customerId||'',order_id:'',location:location||''}));
    if(!session)return false;
    starting=true;discarded='';render();finishPage('home');if(dialog?.open)setTimeout(()=>{closeDialog();starting=false;},350);else starting=false;window.showUndoToast('Zeiterfassung gestartet'+(customerId?' für '+customerName(customerId):' – Kunde kann später ergänzt werden')+'.');return true;
  }
  function closeDialog(){request='';AndroidData.cancelWorkLocation?.();if(dialog?.open)dialog.close();}
  function ensureDialog(){
    if(dialog)return;
    dialog=document.createElement('dialog');dialog.id='work-location-dialog';dialog.className='work-location-dialog';
    dialog.innerHTML='<h2>Einsatz auswählen</h2><p id="work-location-message" role="status"></p><div id="work-location-candidates"></div><label for="work-location-search">Kunden suchen</label><input id="work-location-search" type="search" placeholder="Name oder Ort"><label for="work-location-customer">Kunde</label><select id="work-location-customer"><option value="">Bitte auswählen</option></select><label for="work-location-place">Arbeitsort · bitte prüfen</label><input id="work-location-place"><p class="sub">Ein Vorschlag ist keine Zuordnung. Du entscheidest, bei wem du arbeitest.</p><button type="button" class="primary" id="work-location-start">Für diesen Kunden starten</button><button type="button" class="secondary" id="work-location-new">+ Neuen Kunden anlegen</button><button type="button" class="secondary" id="work-location-later">Ohne Kunde starten · später zuordnen</button><button type="button" class="secondary" id="work-location-cancel">Abbrechen</button>';
    document.body.append(dialog);
    get('work-location-cancel').onclick=closeDialog;
    dialog.addEventListener('cancel',event=>{event.preventDefault();closeDialog();});
    get('work-location-search').oninput=fillCustomers;
    get('work-location-customer').onchange=()=>{get('work-location-start').disabled=!get('work-location-customer').value;};
    get('work-location-start').onclick=()=>{
      const id=get('work-location-customer').value;if(!customers().some(c=>c.id===id)){window.showUndoToast('Bitte einen vorhandenen Kunden auswählen.');return;}
      start(id,get('work-location-place').value.trim());
    };
    get('work-location-later').onclick=()=>start('',get('work-location-place').value.trim());
    get('work-location-new').onclick=()=>{
      newCustomer={fix,location:get('work-location-place').value.trim()};closeDialog();get('add-customer').click();
      const form=get('customer-form'),a=fix?.address||{};
      for(const [name,value]of Object.entries({street:[a.street,a.house_number].filter(Boolean).join(' '),postal_code:a.postal_code||'',city:a.city||'',country:a.country||''}))form.elements[name].value=value;
      window.showUndoToast('Adresse bitte prüfen und Kundennamen ergänzen. Die Zeit startet erst nach dem Speichern.');
    };
  }
  function fillCustomers(){
    const select=get('work-location-customer'),previous=select.value,term=normalize(get('work-location-search').value);
    const all=customers().filter(c=>Number(c.active??1)!==0),recommended=candidates(all,fix),rank=new Map(recommended.map(c=>[c.customer.id,c]));
    const rows=all.filter(c=>normalize([c.name,c.company,c.city,c.street].join(' ')).includes(term)).sort((a,b)=>(rank.get(b.id)?.score||0)-(rank.get(a.id)?.score||0)||String(a.name).localeCompare(String(b.name),'de'));
    select.innerHTML='<option value="">Bitte auswählen</option>'+rows.map(c=>'<option value="'+esc(c.id)+'">'+esc(c.name+(c.city?' · '+c.city:'')+(rank.has(c.id)?' · Standortvorschlag':''))+'</option>').join('');
    if(rows.some(c=>c.id===previous))select.value=previous;
    get('work-location-start').disabled=!select.value;
    get('work-location-candidates').textContent=recommended.length?recommended.length+' mögliche Kunden an dieser Adresse/Straße. Bitte auswählen und bestätigen.':'Keine eindeutige Adressübereinstimmung. Kunden manuell auswählen oder neu anlegen.';
  }
  window.onWorkLocationResult=function(id,value){
    if(id!==request||!dialog?.open)return;
    request='';fix=value||{status:'LOCATION_UNAVAILABLE'};
    const a=addressText(fix.address);
    get('work-location-place').value=a||(fix.status==='OK'&&Number.isFinite(fix.latitude)&&Number.isFinite(fix.longitude)?fix.latitude.toFixed(5)+', '+fix.longitude.toFixed(5):'');
    get('work-location-message').textContent=(fix.message||(fix.status==='OK'?'Standort ermittelt. Adresse und Kunde bitte bestätigen.':'Standort konnte nicht geprüft werden.'))+(fix.status==='OK'?' Genauigkeit ca. '+Math.round(fix.accuracy_m)+' m.':'')+(fix.approximate?' Nur ungefährer Standort: keine automatische Kundensuche.':'')+(a?' '+a:'');
    fillCustomers();
  };
  function begin(){
    ensureDialog();fix=null;get('work-location-search').value='';get('work-location-place').value='';get('work-location-customer').value='';get('work-location-message').textContent='Aktuellen Pixel-Standort prüfen …';fillCustomers();dialog.showModal();
    request=requestId();
    if(AndroidData.requestWorkLocation)AndroidData.requestWorkLocation(request);
    else window.onWorkLocationResult(request,{status:'UNAVAILABLE',message:'Standortprüfung benötigt die aktualisierte Android-App. Bitte Kunden manuell auswählen.'});
  }
  function complete(session){
    const form=get('entry'),hidden=get('work-session-id');
    if(hidden?.value===session.id){openPage('work-entry');showCompletion(session);return;}
    const hasDraft=window.hasWorkEntryDraft();
    if(hasDraft&&!confirm('Eine andere Eingabe ist begonnen. Diese durch die gestoppte Zeiterfassung ersetzen?'))return;
    form.reset();openPage('work-entry');
    let link=get('work-session-id');if(!link){link=document.createElement('input');link.type='hidden';link.name='_session_id';link.id='work-session-id';form.append(link);}link.value=session.id;
    get('date').value=session.work_date||localDate();get('customer').value=session.customer_id||'';ordersForCustomer();
    if(session.order_id&&[...get('order').options].some(o=>o.value===session.order_id))get('order').value=session.order_id;
    get('entry-hours').value=Number(session.hours||0).toFixed(2);get('pause').value=Number(session.break_minutes||0);get('activity').value=session.activity||'';get('entry-location').value=session.location||'';showCompletion(session);
    get('activity').dispatchEvent(new Event('change',{bubbles:true}));window.scrollTo({top:0,behavior:'auto'});
  }
  function showCompletion(session){
    if(session.state!=='STOPPED_PENDING_REVIEW')return;
    const page=get('work-entry'),form=get('entry');page.classList.add('timer-completion-page');page.querySelector('.page-head h2').textContent='Arbeit abschließen';
    let summary=get('timer-completion-summary');if(!summary){summary=document.createElement('section');summary.id='timer-completion-summary';summary.className='card timer-completion-summary';page.querySelector('.page-head').after(summary);}
    const stamp=value=>{const date=new Date(value);return Number.isNaN(date.getTime())?'Nicht verfügbar':date.toLocaleString('de-AT',{timeZone:'Europe/Vienna',day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit'});};
    summary.hidden=false;summary.innerHTML='<strong>Zeiterfassung beendet · lokal gesichert</strong><dl>'+[['Kunde',get('customer').value?customerName(get('customer').value):'Bitte noch auswählen'],['Arbeitsort',get('entry-location').value||session.location||'Nicht angegeben'],['Beginn',session.started_at_ms?stamp(session.started_at_ms):'Nicht verfügbar'],['Ende',session.updated_at?stamp(session.updated_at):'Nicht verfügbar'],['Pause',Number(session.break_minutes||0).toLocaleString('de-AT')+' min'],['Nettozeit',Number(session.hours||0).toLocaleString('de-AT')+' h']].map(([label,value])=>'<div><dt>'+esc(label)+'</dt><dd>'+esc(value)+'</dd></div>').join('')+'</dl><p>Eine Arbeitsaufzeichnung entsteht erst mit Speichern. Pausen sind bereits abgezogen.</p>';
    get('entry-hours').value=Number(session.hours||0).toFixed(2);get('pause').value=Number(session.break_minutes||0);get('date').value=session.work_date||localDate();
    for(const id of ['entry-hours','pause','date'])get(id).readOnly=true;
    for(const id of ['start','end']){get(id).value='';get(id).disabled=true;}
    get('hours-result').textContent='Nettozeit aus dem Timer: '+Number(session.hours||0).toLocaleString('de-AT')+' h. Kein weiterer Pausenabzug.';
    const times=form.querySelector('[data-section="time"]');if(times)times.hidden=true;
    form.querySelector('button[type="submit"]').textContent='Arbeitsaufzeichnung speichern';get('new-work-cancel').hidden=true;
    let options=get('timer-completion-options');if(!options){options=document.createElement('div');options.id='timer-completion-options';options.className='timer-completion-options';options.innerHTML='<button type="button" class="secondary" data-session-action="later">Später ergänzen</button><details class="completion-more"><summary>Weitere Aktionen</summary><button type="button" class="secondary" data-session-action="continue">Zeitnahme fortsetzen</button><button type="button" class="timer-discard" data-session-action="discard">Zeiterfassung verwerfen</button></details>';form.querySelector('.actions').append(options);}options.hidden=false;
  }
  function clearCompletion(){
    const page=get('work-entry');page.classList.remove('timer-completion-page');page.querySelector('.page-head h2').textContent='Arbeit erfassen';
    if(get('timer-completion-summary'))get('timer-completion-summary').hidden=true;if(get('timer-completion-options')){get('timer-completion-options').hidden=true;get('timer-completion-options').querySelector('details').open=false;}
    for(const id of ['entry-hours','pause','date'])get(id).readOnly=false;
    for(const id of ['start','end'])get(id).disabled=false;
    const form=get('entry'),times=form.querySelector('[data-section="time"]');if(times)times.hidden=false;form.querySelector('button[type="submit"]').textContent='Speichern';if(get('new-work-cancel'))get('new-work-cancel').hidden=false;
  }
  function decorate(){
    const panel=get('workday-timer');if(!panel)return;
    let session;try{session=JSON.parse(AndroidData.getWorkSession());}catch(_){return;}
    const active=['RUNNING','PAUSED','STOPPED_PENDING_REVIEW'].includes(session.state);
    if(active&&!panel.querySelector('[data-session-action="discard"]')){
      const button=document.createElement('button');button.type='button';button.className='timer-discard';button.dataset.sessionAction='discard';button.textContent='Zeiterfassung löschen';panel.append(button);
      const context=document.createElement('p');context.className='timer-location-context';context.textContent=(session.customer_id?customerName(session.customer_id):'Kunde noch nicht zugeordnet')+(session.location?' · '+session.location:'');panel.prepend(context);
    }
  }
  function init(){
    const form=get('entry');
    document.addEventListener('click',event=>{
      const action=event.target.closest('[data-session-action]')?.dataset.sessionAction;
      if(!['start','discard','complete','stop','continue','later'].includes(action))return;
      event.preventDefault();event.stopImmediatePropagation();
      if(action==='start'){begin();return;}
      const session=JSON.parse(AndroidData.getWorkSession());
      if(action==='complete'){complete(session);return;}
      if(action==='stop'){const stopped=session.state==='STOPPED_PENDING_REVIEW'?session:native('stopWorkSession',session.id,requestId());if(stopped){render();complete(stopped);}return;}
      if(action==='later'){finishPage('home');window.showUndoToast('Zeiterfassung und begonnene Angaben bleiben erhalten. Später über „Angaben ergänzen“ fortfahren.');return;}
      if(action==='continue'){if(!confirm('Diese Zeiterfassung fortsetzen? Die Zeit seit Stop wird nicht als Arbeitszeit gezählt.'))return;const resumed=native('continueWorkSession',session.id,requestId());if(resumed){clearCompletion();render();finishPage('home');window.showUndoToast('Zeitnahme fortgesetzt. Deine Angaben bleiben für den nächsten Abschluss erhalten.');}return;}
      if(!confirm('Diese noch nicht gespeicherte Zeiterfassung löschen? Gespeicherte Cloudname_189a14e78c bleiben erhalten.'))return;
      const wasLinked=get('work-session-id')?.value===session.id;
      const removed=native('discardWorkSession',session.id,requestId());if(!removed)return;discarded=session.id;
      if(get('work-session-id')?.value===session.id){get('work-session-id').remove();form.elements.hours.value='';form.elements.hours.readOnly=false;get('hours-result').textContent='Zeiterfassung verworfen. Deine übrigen Angaben bleiben erhalten.';get('activity').dispatchEvent(new Event('change',{bubbles:true}));}
      clearCompletion();const draftSignature=()=>JSON.stringify([...form.elements].filter(x=>x.name).map(x=>[x.name,x.value])),discardedDraft=draftSignature(),undoId=session.id;render();if(currentPage==='work-entry')finishPage('home');window.showUndoToast('Zeiterfassung verworfen.',()=>{const restored=native('restoreWorkSession',undoId,requestId());if(restored){discarded='';if(wasLinked&&!get('work-session-id')&&discardedDraft===draftSignature()){const link=document.createElement('input');link.type='hidden';link.name='_session_id';link.id='work-session-id';link.value=restored.id;form.append(link);get('entry-hours').value=Number(restored.hours||0).toFixed(2);get('pause').value=Number(restored.break_minutes||0);get('activity').dispatchEvent(new Event('change',{bubbles:true}));}render();window.showUndoToast('Zeiterfassung wiederhergestellt und pausiert. Mit Fortsetzen weiterarbeiten.');}});
    },true);
    window.onTimerCustomerSaved=function(id){if(!newCustomer)return false;const draft=newCustomer;newCustomer=null;start(id,draft.location);return true;};
    const open=window.openPage;window.openPage=function(...args){const wasNew=currentPage==='customer-edit'&&newCustomer;open(...args);if(wasNew&&currentPage!=='customer-edit')newCustomer=null;};
    const back=window.handleAndroidBack;window.handleAndroidBack=function(){if(dialog?.open){closeDialog();return true;}const wasNew=currentPage==='customer-edit'&&newCustomer,result=back();if(wasNew&&currentPage!=='customer-edit')newCustomer=null;return result;};
    const panel=get('workday-timer');if(panel)new MutationObserver(decorate).observe(panel,{childList:true});decorate();
    form.addEventListener('reset',clearCompletion);
    form.addEventListener('change',event=>{if(!['customer','entry-location'].includes(event.target.id)||!get('work-session-id'))return;const session=JSON.parse(AndroidData.getWorkSession());if(session.state==='STOPPED_PENDING_REVIEW')showCompletion(session);});
    const submit=event=>{if(event.target!==form||!get('work-session-id'))return;const session=JSON.parse(AndroidData.getWorkSession());if(session.id!==get('work-session-id').value||session.state!=='STOPPED_PENDING_REVIEW'){event.preventDefault();event.stopImmediatePropagation();window.showUndoToast('Bitte die aktuelle Zeiterfassung zuerst stoppen. Es wurde nichts gebucht.');return;}if(session.pause_unverified){event.preventDefault();event.stopImmediatePropagation();window.showUndoToast('Pausenzeiten wegen geänderter Geräteuhr bitte prüfen. Es wurde nichts gebucht.');return;}get('entry-hours').value=Number(session.hours||0).toFixed(2);get('pause').value=Number(session.break_minutes||0);};document.addEventListener('submit',submit,true);
    const openCompletionBase=window.openPage;window.openPage=function(...args){openCompletionBase(...args);if(currentPage==='work-entry'&&get('work-session-id')){const session=JSON.parse(AndroidData.getWorkSession());if(session.id===get('work-session-id').value&&session.state==='STOPPED_PENDING_REVIEW')showCompletion(session);}};
  }
  document.addEventListener('DOMContentLoaded',()=>setTimeout(init,0),{once:true});
})();

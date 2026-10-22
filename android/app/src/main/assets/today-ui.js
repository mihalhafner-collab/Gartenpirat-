/* Single owner of Today rendering and original session commands. */
(()=>{
'use strict';const get=id=>document.getElementById(id);
const openStatuses=new Set(['','Geplant','Aktiv','In Arbeit','Wartet auf Material','Offen']);
const sessionRequest=action=>`${action}-${Date.now()}-${crypto.randomUUID?.()||Math.random().toString(16).slice(2)}`;
  function readSession(){try{return JSON.parse(AndroidData.getWorkSession())}catch(_){return {state:'UNAVAILABLE'}}}
  function sessionDuration(value){const seconds=Math.max(0,Math.floor(Number(value||0)/1000));return `${String(Math.floor(seconds/3600)).padStart(2,'0')}:${String(Math.floor(seconds%3600/60)).padStart(2,'0')}:${String(seconds%60).padStart(2,'0')}`;}
  function sessionCall(name,...args){try{const result=AndroidData[name](...args);if(String(result).startsWith('ERROR:'))throw new Error(String(result).slice(6).trim());return JSON.parse(result)}catch(error){window.showUndoToast(error.message||'Die Zeiterfassung konnte nicht geändert werden.');return null;}}
  function workTimer(){
    if(!AndroidData?.getWorkSession)return;
    let panel=get('workday-timer');
    if(!panel)return;
    if(!panel._bound){panel._bound=true;panel.addEventListener('click',event=>{
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
    const panel=get('workday-timer');if(!panel)return;
    const status=session.state||'IDLE',active=status==='RUNNING',paused=status==='PAUSED',stopped=status==='STOPPED_PENDING_REVIEW';
    const signature=JSON.stringify([session.id,status,session.customer_id,session.order_id,session.location]);
    if(panel._sessionView===signature){const clock=panel.querySelector('.timer-state strong');if(clock)clock.textContent=sessionDuration(session.worked_ms);return;}
    panel._sessionView=signature;panel.dataset.sessionState=status;panel.classList.toggle('has-active-session',active||paused||stopped);
    const manual='<button type="button" class="quick secondary" data-open="work-entry"><strong>Nachtragen</strong></button>';
    let body;
    if(['IDLE','FINALIZED','CANCELLED'].includes(status))body='<div id="field-today-actions" aria-label="Arbeit beginnen oder nachtragen"><button type="button" class="timer-main timer-start" data-session-action="start"><b>Arbeit starten</b></button>'+manual+'</div>';
    else if(['UNAVAILABLE','ERROR'].includes(status))body='<p>Zeiterfassung derzeit nicht verfügbar.</p><div id="field-today-actions">'+manual+'</div>';
    else body='<p class="timer-location-context">'+esc(session.customer_id?customerName(session.customer_id):'Kunde noch zuordnen')+'</p><div class="timer-state"><small>'+esc(active?'Arbeit läuft':paused?'Pausiert':'Einsatz beendet')+'</small><strong aria-label="Erfasste Arbeitszeit">'+sessionDuration(session.worked_ms)+'</strong></div><div class="timer-actions">'+(active?'<button type="button" class="timer-secondary" data-session-action="pause"><b>Pause</b></button>':'')+(paused?'<button type="button" class="timer-main" data-session-action="resume"><b>Fortsetzen</b></button>':'')+(active||paused?'<button type="button" class="timer-stop" data-session-action="stop"><b>Stoppen</b></button><button type="button" class="timer-secondary" data-session-action="photo"><b>Foto</b></button>':'')+(stopped?'<button type="button" class="timer-main" data-session-action="complete"><b>Angaben ergänzen</b></button>':'')+'</div><div id="field-today-actions"><span></span>'+manual+'</div>';
    panel.innerHTML='<h2>Jetzt</h2>'+body+((active||paused||stopped)?'<details class="today-timer-more"><summary>Weitere Aktionen</summary><button type="button" class="timer-discard" data-session-action="discard">Zeiterfassung löschen</button></details>':'');
  }
 function homeGestures(card){
  let touch=null,suppressUntil=0;
  card.addEventListener('touchstart',event=>{touch=null;suppressUntil=0;if(event.touches.length!==1||event.target.closest('[data-home-browse],.planning-row-menu,a,input,select,textarea,dialog'))return;const t=event.touches[0];if(t.clientX<(window.HGGestures?.safeLeft()||32)+44)return;touch={x:t.clientX,y:t.clientY,at:performance.now(),locked:false};},{passive:true});
  card.addEventListener('touchmove',event=>{if(!touch||event.touches.length!==1){touch=null;return;}const t=event.touches[0],dx=t.clientX-touch.x,dy=t.clientY-touch.y;if(!touch.locked&&Math.abs(dy)>10&&Math.abs(dy)>=Math.abs(dx)){touch=null;return;}if(Math.abs(dx)>12&&Math.abs(dx)>Math.abs(dy)*1.6){touch.locked=true;event.preventDefault();}},{passive:false});
  card.addEventListener('touchend',event=>{const start=touch;touch=null;if(!start||event.changedTouches.length!==1||performance.now()-start.at>1100)return;const t=event.changedTouches[0],dx=t.clientX-start.x,dy=t.clientY-start.y;if(Math.abs(dx)>=48&&Math.abs(dx)>Math.abs(dy)*1.6){event.preventDefault();event.stopPropagation();suppressUntil=performance.now()+450;browseHome(dx<0?1:-1);}},{passive:false});
  card.addEventListener('touchcancel',()=>touch=null,{passive:true});
  card.addEventListener('click',event=>{if(performance.now()<suppressUntil&&event.detail!==0&&event.sourceCapabilities?.firesTouchEvents!==false){event.preventDefault();event.stopImmediatePropagation();return;}const b=event.target.closest('[data-home-browse]');if(b){event.preventDefault();browseHome(Number(b.dataset.homeBrowse));}},{capture:true});
  card.addEventListener('keydown',event=>{if(event.target!==card||!['ArrowLeft','ArrowRight'].includes(event.key))return;event.preventDefault();browseHome(event.key==='ArrowRight'?1:-1);});
 }
  let showAll=false,futureSelection='',lastPlanningKey='';
  function syncChip(){
    const pill=get('sync-status');if(!pill)return;
    const labels={not_connected:'Drive verbinden',syncing:'Gleicht ab',action_required:'Hilfe nötig',review_required:'Prüfen',status_warning:'Status prüfen',pending:'Ausstehend',provider_readback:'Übergeben',synced:'Abgeglichen',retry_wait:'Versuch folgt',error:'Abgleich offen'};
    const shown=HGBusinessFeedback.sync(syncInfo),label=syncInfo.syncing?'Gleicht ab':Number(syncInfo.review)>0?'Prüfung nötig':syncInfo.configured===false?'Drive verbinden':labels[syncInfo.state]||(syncInfo.last_error?'Abgleich offen':shown.title);
    pill.textContent=label;pill.title=shown.title+'. '+shown.detail;pill.setAttribute('aria-label','Status öffnen: '+shown.title);pill.dataset.open='status-page';
  }
  function appointmentRow(entry){
    const a=entry.appointment,w=entry.weather||{},clashes=entry.collisions||[],warning=['critical','difficult','unknown'].includes(w.severity)||clashes.length||entry.availability_unknown||entry.calendar?.state==='review_required';
    const format=value=>new Date(value).toLocaleTimeString('de-AT',{timeZone:a.timezone||'Europe/Vienna',hour:'2-digit',minute:'2-digit'}),time=format(a.starts_at),end=a.ends_at?format(a.ends_at):'Ende offen',weather=w.label||'Wetter noch ungeprüft';
    const icon=['critical','difficult'].includes(w.severity)?'☂':w.severity==='good'||w.severity==='safe'?'☀':w.severity==='unknown'||!w.severity?'?':'☁';
    const notice=[weather,...(w.reasons||[]),clashes.length?'Terminkollision prüfen':'',entry.availability_unknown?'Google-Belegung ungeprüft':'',entry.calendar?.state==='review_required'?'Kalenderänderung prüfen':''].filter(Boolean).join(' · ');
    return '<button type="button" class="today-appointment" data-appointment-open="'+esc(a.id)+'" aria-label="'+esc([time,end,customerName(a.customer_id),a.title,a.location||'Einsatzort offen',notice].filter(Boolean).join(' · '))+'"><span class="today-time">'+esc(time)+'<small>'+esc(end)+'</small></span><strong class="today-customer">'+esc(customerName(a.customer_id))+'</strong><span class="today-place">'+esc(a.location||'Einsatzort offen')+'</span><span class="today-weather" title="'+esc(notice)+'"><span role="img" aria-label="'+esc(weather)+'">'+icon+'</span>'+(warning?'<span class="today-warning" role="img" aria-label="Hinweis prüfen">⚠</span>':'')+'</span></button>';
  }
  function browseHome(direction){const rows=window.PlanningUI?.upcoming()||[],index=Math.max(0,rows.findIndex(a=>a.id===futureSelection));if(rows[index+direction]){futureSelection=rows[index+direction].id;lastPlanningKey='';renderToday();}}
  function renderToday(){
    if(currentPage!=='home')return;
    const date=get('home-dayline');date.textContent=new Date().toLocaleDateString('de-AT',{timeZone:'Europe/Vienna',weekday:'short',day:'numeric',month:'short'});date.setAttribute('aria-label',new Date().toLocaleDateString('de-AT',{timeZone:'Europe/Vienna',dateStyle:'full'}));
    syncChip();workTimer();
    const data=window.PlanningUI?.homeRows(showAll?Infinity:3,futureSelection)||{entries:[],total:0,future:[]},card=get('planning-home');
    const key=JSON.stringify([data,showAll,orders().filter(o=>o.scheduled_date===localDate())]);
    if(key!==lastPlanningKey){lastPlanningKey=key;
      let content='<div class="today-planning-heading"><h2>Heute geplant'+(data.total?' · '+data.total:'')+'</h2><button type="button" class="field-planning" data-open="planning-page">Planung</button></div>';
      if(data.total){content+=data.entries.map(appointmentRow).join('');if(data.total>3)content+='<button type="button" class="secondary" data-today-all>'+(showAll?'Weniger anzeigen':'Alle anzeigen · '+data.total)+'</button>';}
      else{content+='<p class="sub">Heute keine Termine.</p>';if(data.next){futureSelection=data.next.appointment.id;const index=data.future.findIndex(a=>a.id===futureSelection);content+='<h3>Nächster Einsatz</h3><div class="planning-home-position"><button type="button" class="secondary" data-home-browse="-1" aria-label="Vorherigen Einsatz anzeigen"'+(index===0?' disabled':'')+'>‹</button><span role="status">'+(index+1)+' von '+data.future.length+' · '+esc(new Date(data.next.appointment.starts_at).toLocaleDateString('de-AT',{day:'numeric',month:'short'}))+'</span><button type="button" class="secondary" data-home-browse="1" aria-label="Nächsten Einsatz anzeigen"'+(index===data.future.length-1?' disabled':'')+'>›</button></div>'+appointmentRow(data.next);}}
      const planned=new Set(merged('appointments','appointment').filter(a=>!['cancelled','completed','draft'].includes(a.status)).map(a=>a.order_id));
      const legacy=orders().filter(o=>openStatuses.has(o.status||'')&&o.scheduled_date===localDate()&&!planned.has(o.id));
      content+='<section id="design-upcoming"'+(legacy.length?'':' hidden')+'><h3>Weitere Aufträge heute</h3>'+legacy.map(o=>'<button type="button" class="design-upcoming-row" data-edit-order="'+esc(o.id)+'"><strong>'+esc(o.title)+'</strong><small>'+esc(customerName(o.customer_id))+'</small></button>').join('')+'</section>';
      card.innerHTML=content;
    }
    const recent=get('workday-recent'),rows=worklogs().slice().sort((a,b)=>String(b.work_date||'').localeCompare(String(a.work_date||''))||String(b.start_time||'').localeCompare(String(a.start_time||''))).slice(0,3),recentKey=JSON.stringify(rows);
    get('today-recent').hidden=!rows.length;
    if(recent._rowsKey!==recentKey){recent._rowsKey=recentKey;recent.innerHTML=rows.map(row=>'<button type="button" class="workday-recent-row" data-edit-worklog="'+esc(row.id)+'"><strong>'+esc(customerName(row.customer_id))+'</strong><span>'+esc(worklogDateLabel(row.work_date))+' · '+esc(String(row.activity||'').slice(0,75))+'</span><b>'+Number(row.hours||0).toLocaleString('de-AT')+' h</b></button>').join('');}
    const review=Number(syncInfo.review??state.sync_queue?.review??0),help=syncInfo.user_action_required||['action_required','error','status_warning'].includes(syncInfo.state),open=get('today-open');open.hidden=!review&&!help;
    open.innerHTML='<h2>Offen</h2><div class="today-open-chips">'+(review?'<button type="button" data-open="sync-page">'+review+' Änderungen prüfen</button>':'')+(help?'<button type="button" data-open="status-page">'+(syncInfo.state==='status_warning'?'Status prüfen':'Abgleich braucht Hilfe')+'</button>':'')+'</div>';
    document.body.dataset.todayReady='true';
  }
  function init(){
    const card=get('planning-home');homeGestures(card);card.addEventListener('click',e=>{const all=e.target.closest('[data-today-all]');if(all){showAll=!showAll;lastPlanningKey='';renderToday();}});
    renderToday();setInterval(()=>{if(!document.hidden&&currentPage==='home')workTimer();},1000);
  }
  window.TodayUI=Object.freeze({render:renderToday,syncChip,invalidate(){lastPlanningKey='';renderToday();}});
  document.addEventListener('DOMContentLoaded',init,{once:true});
})();

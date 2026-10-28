/* Presentation readiness only. No data access, business gate or automatic retry. */
(() => {
 'use strict';
 // Diagnostic memory only: fixed technical labels and at most 128 samples each.
 // No storage, console logging, network, arguments or page/business identifiers.
 const measurements=new Map(),labels=new Set(['render','page-change','app-start']);
 function record(label,duration){if(!labels.has(label)||!Number.isFinite(duration)||duration<0)return;const rows=measurements.get(label)||[];if(rows.length===128)rows.shift();rows.push(duration);measurements.set(label,rows);}
 window.HGPerformance=Object.freeze({snapshot:()=>Object.fromEntries([...measurements].map(([key,rows])=>[key,rows.slice()]))});
 document.addEventListener('hg:startup-ready',()=>{
  record('app-start',performance.now());
  const render=window.render;window.render=function(...args){const started=performance.now();try{return render.apply(this,args);}finally{record('render',performance.now()-started);}};
  const open=window.openPage;window.openPage=function(...args){const before=currentPage,started=performance.now();try{return open.apply(this,args);}finally{if(currentPage!==before)requestAnimationFrame(()=>requestAnimationFrame(()=>record('page-change',performance.now()-started)));}};
 },{once:true});
 const html=document.documentElement,styles=['design-concept.css','planning-ui.css','field-ui.css','modern-ui.css'];
 const critical=new Set(['mobile.html','runtime-compat.js','data-order.js','owner-client.js','business-feedback.js','workday-ui.js','design-concept.js','daily-usability.js','billing-ui.js','workflow-ui.js','planning-ui.js','navigation-status.js','field-ui.js','revier-solo-ui.js','simple-forms.js','startup-ui.js','offers-ui.js','local-invoice-ui.js','record-actions.js','invoice-flow.js','invoice-editor.js','invoice-draft.js','order-window.js','gartenpirat-photo-store.js','equipment-photo-ui.js',...styles]);
 let failed=false,slow=false,queued=false,finished=false,ordered=false,recoveryBlocked=false;
 html.dataset.startup='loading';
 const get=id=>document.getElementById(id);
 function message(){
  const text=get('app-startup-message'),button=get('app-startup-retry');
  const value=recoveryBlocked&&document.querySelector('dialog[open]')?'Bitte zuerst den offenen Dialog abschließen.':failed?'Die App konnte nicht vollständig geöffnet werden. Bitte erneut öffnen.':slow?'Die App startet noch. Bereits gespeicherte Daten bleiben erhalten.':'Die App wird geöffnet …';if(text&&text.textContent!==value)text.textContent=value;
  if(button)button.hidden=!(failed||slow);
 }
 function check(){
  queued=false;if(finished)return;message();if(failed||!document.body)return;
  const d=document.body.dataset;
  if(d.fieldReady!=='true'||d.revierReady!=='true'||d.simpleReady!=='true'||d.todayReady!=='true'||!d.designVersion||!d.usabilityVersion)return;
  if(['offers-page','planning-page','status-page','photo-library-page','trash-page','entry-billing-customer'].some(id=>!get(id)))return;
  const links=styles.map(href=>document.querySelector('link[href="'+href+'"]'));
  // Moving an already loaded link can briefly detach its CSSOM without a new
  // load event. Keep checking this actual prerequisite until it is available.
  if(links.some(link=>!link?.sheet)){queue();return;}
  // Initializers also append inline styles. Preserve the old final cascade,
  // with the production sheets and current theme last, before the first UI frame.
  if(!ordered){ordered=true;for(const link of links)document.head.append(link);queue();return;}
  finished=true;clearTimeout(notice);observer.disconnect();html.dataset.startup='ready';get('app-startup-status').hidden=true;
  performance.mark?.('hg-startup-ready');document.dispatchEvent(new Event('hg:startup-ready'));
 }
 function queue(){if(!queued&&!finished){queued=true;requestAnimationFrame(check);}}
 const observer=new MutationObserver(queue);observer.observe(html,{childList:true,subtree:true,attributes:true,attributeFilter:['data-field-ready','data-revier-ready','data-simple-ready','data-today-ready','data-design-version','data-usability-version']});
 const notice=setTimeout(()=>{slow=true;message();},8000);
 window.addEventListener('error',event=>{
  if(finished)return;const target=event.target,source=target?.src||target?.href||event.filename||'';
  const name=source.split('/').pop()?.split(/[?#]/)[0];
  if(critical.has(name)){failed=true;html.dataset.startup='error';message();}
 },true);
 window.addEventListener('load',queue,true);document.addEventListener('DOMContentLoaded',()=>{message();queue();},{once:true});
 document.addEventListener('click',event=>{
  if(!event.target.closest('#app-startup-retry'))return;
  event.preventDefault();
  if(document.querySelector('dialog[open]')){recoveryBlocked=true;message();return;}
  recoveryBlocked=false;
  if(confirm('App erneut öffnen? Bereits gespeicherte Daten und gesicherte Entwürfe bleiben erhalten.'))location.reload();
 });
 window.HGStartup=Object.freeze({check:queue});queue();
})();

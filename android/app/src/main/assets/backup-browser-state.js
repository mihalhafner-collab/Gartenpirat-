/* App-private draft checkpoints. No automatic restore, network, passwords or file bytes. */
(()=>{'use strict';
 if(window.HGBackupSnapshot)return;
 const MIRROR='hausgarten.fullbackup.formdrafts.v1',SECRET=/(password|passwd|secret|credential|token|authorization|oauth|recovery.?code|api.?key)/i;
 let pageToken='',sequence=0,enabled=false,timer=null,storageFailure=false;
 const page=()=>String(typeof currentPage==='string'?currentPage:window.currentPage||location.hash||location.pathname||'app');
 const visible=e=>!!e&&!!e.getClientRects().length&&getComputedStyle(e).visibility!=='hidden'&&!e.closest('[hidden],[aria-hidden="true"]');
 const safe=e=>!e.disabled&&!e.readOnly&&!['password','file','submit','button','reset','image'].includes(String(e.type).toLowerCase())&&!SECRET.test([e.id,e.name,e.autocomplete].join('.'))&&!['current-password','new-password','one-time-code'].includes(e.autocomplete);
 const formKey=(form,index)=>form?.id||form?.getAttribute('name')||'form-'+index;
 function forms(){
  const found=[...document.forms].filter(f=>visible(f)&&!f.closest('#hg-backup-draft-dialog'));
  return found.map((form,index)=>{
   const fields=[...form.querySelectorAll('input,textarea,select')].filter(e=>safe(e)&&e.type!=='hidden'&&visible(e));
   const identity=[...form.querySelectorAll('input[type=hidden],select')].filter(e=>safe(e)&&/(^id$|_id$|Id$)/.test(e.name||e.id)).map(e=>[e.name||e.id,String(e.value)]).sort((a,b)=>a[0].localeCompare(b[0]));
   return {key:formKey(form,index),identity,fields:fields.map((e,ordinal)=>({id:e.id||'',name:e.name||'',ordinal,tag:e.tagName.toLowerCase(),type:e.type||'',label:String(e.labels?.[0]?.textContent||e.getAttribute('aria-label')||e.name||e.id||'Feld').trim().slice(0,200),value:String(e.value),...(['checkbox','radio'].includes(e.type)?{checked:e.checked}:{}),...(e.tagName==='SELECT'&&e.multiple?{selected:[...e.selectedOptions].map(o=>o.value)}:{})}))};
  }).filter(f=>f.fields.length);
 }
 function read(){try{const data=JSON.parse(localStorage.getItem(MIRROR)||'{"formatVersion":1,"pages":{}}');if(data.formatVersion!==1||!data.pages||typeof data.pages!=='object'||Array.isArray(data.pages))throw Error();return data;}catch(_){throw Error('DRAFT_CHECKPOINT_UNREADABLE');}}
 function mirror(){const current=forms();if(!current.length)return;const saved=read();saved.pages[page()]={page:page(),forms:current};try{localStorage.setItem(MIRROR,JSON.stringify(saved));storageFailure=false;}catch(_){storageFailure=true;notice('Formularentwurf konnte nicht zusätzlich gespeichert werden. Eingaben bleiben geöffnet; bitte den Vorgang speichern.');}}
 function capture(){
  const storage={};for(let i=0;i<localStorage.length;i++){const key=localStorage.key(i);if(key&&!SECRET.test(key))storage[key]=String(localStorage.getItem(key));}
  return {formatVersion:1,storage,drafts:{formatVersion:1,page:page(),forms:forms(),storageFailure},_checkpoint:{pageToken,sequence:++sequence}};
 }
 function flush(){if(timer){clearTimeout(timer);timer=null;}if(!enabled||!pageToken||typeof window.AndroidData?.checkpointFullBackup!=='function')return;try{AndroidData.checkpointFullBackup('');}catch(_){notice('Der zusätzliche Sicherungsentwurf ist noch nicht bestätigt. Bitte den Vorgang speichern oder die Sicherungsanzeige prüfen.');}}
 function schedule(){if(timer)clearTimeout(timer);timer=setTimeout(flush,300);}
 function notice(message){let banner=document.getElementById('hg-backup-draft-banner');if(!banner){banner=document.createElement('div');banner.id='hg-backup-draft-banner';banner.setAttribute('role','status');banner.style.cssText='position:relative;box-sizing:border-box;max-width:100%;overflow-wrap:anywhere;margin:8px;padding:12px;background:#F8EFE5;color:#33261A;font:inherit;';document.body.append(banner);}banner.textContent=message;}
 function review(){
  let saved;try{saved=read();}catch(_){notice('Gespeicherte Formularwerte konnten nicht gelesen werden. Vorhandene Daten wurden nicht verändert.');return;}
  let dialog=document.getElementById('hg-backup-draft-dialog');if(!dialog){dialog=document.createElement('dialog');dialog.id='hg-backup-draft-dialog';dialog.style.cssText='max-width:min(90vw,640px);box-sizing:border-box;background:#FCFAF7;color:#33261A;font:inherit;overflow-wrap:anywhere;';document.body.append(dialog);}dialog.replaceChildren();
  const heading=document.createElement('h2');heading.textContent='Gespeicherte Formularentwürfe';heading.tabIndex=-1;dialog.append(heading);
  const note=document.createElement('p');note.textContent='Diese Werte werden erst nach deinem Vergleich in einen geöffneten Editor übernommen. Gespeicherte Geschäftsdaten werden hier nicht geändert.';dialog.append(note);
  const entries=Object.entries(saved.pages);if(!entries.length){const empty=document.createElement('p');empty.textContent='Keine zusätzlich erfassten Formularwerte vorhanden.';dialog.append(empty);}
  for(const [key,draft]of entries){const section=document.createElement('section'),title=document.createElement('h3');title.textContent=draft.page;section.append(title);
   for(const form of draft.forms||[]){for(const field of form.fields||[]){const p=document.createElement('p');p.textContent=field.label+': '+(['checkbox','radio'].includes(field.type)?(field.checked?'ausgewählt':'nicht ausgewählt'):field.value);section.append(p);}}
   const apply=document.createElement('button');apply.type='button';apply.textContent='Mit geöffnetem Editor vergleichen';apply.style.cssText='min-height:48px;font:inherit;margin:4px;';section.append(apply);
   apply.onclick=()=>{const current=forms();if(page()!==draft.page||!(draft.forms||[]).every(f=>current.some(c=>c.key===f.key&&JSON.stringify(c.identity)===JSON.stringify(f.identity)))){notice('Öffne zuerst den passenden Eintrag mit „Bearbeiten“ und wähle denselben Kunden-/Auftragsbezug. Der Entwurf bleibt erhalten.');dialog.close();return;}
    const changes=[];for(const f of draft.forms)for(const field of f.fields){const c=current.find(c=>c.key===f.key)?.fields.find(c=>c.id===field.id&&c.name===field.name&&c.ordinal===field.ordinal);if(c&&(c.value!==field.value||c.checked!==field.checked))changes.push(field.label+': aktuell „'+c.value+'“, Entwurf „'+field.value+'“');}
    if(!changes.length){notice('Die geöffneten Formularwerte stimmen mit dem Entwurf überein.');dialog.close();return;}
    if(!confirm('Formularwerte vergleichen:\n'+changes.join('\n')+'\n\nDiese Werte nur in den geöffneten Editor übernehmen? Anschließend ausdrücklich speichern.'))return;
    let applied=0;for(const f of draft.forms){const currentForm=[...document.forms].filter(visible).find((v,index)=>formKey(v,index)===f.key);if(!currentForm)continue;for(const field of f.fields){const candidates=[...currentForm.querySelectorAll('input,textarea,select')].filter(e=>safe(e)&&e.type!=='hidden'&&visible(e)),e=candidates[field.ordinal];if(!e||e.id!==field.id||e.name!==field.name||e.tagName.toLowerCase()!==field.tag||e.type!==field.type)continue;if(e.tagName==='SELECT'&&field.selected)for(const option of e.options)option.selected=field.selected.includes(option.value);else e.value=field.value;if('checked'in field)e.checked=field.checked;e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));applied++;}}
    notice(applied+' Formularfelder in den Editor übernommen. Bitte prüfen und ausdrücklich speichern. Der Entwurf bleibt erhalten.');dialog.close();schedule();};
   const discard=document.createElement('button');discard.type='button';discard.textContent='Zusätzlichen Entwurf verwerfen';discard.style.cssText='min-height:48px;font:inherit;margin:4px;';section.append(discard);discard.onclick=()=>{if(!confirm('Nur diese zusätzlich erfassten Formularwerte verwerfen? Geschäftsdaten und Dateien bleiben erhalten.'))return;const latest=read();delete latest.pages[key];localStorage.setItem(MIRROR,JSON.stringify(latest));flush();review();};dialog.append(section);
  }
  const close=document.createElement('button');close.type='button';close.textContent='Schließen';close.style.cssText='min-height:48px;font:inherit;margin:4px;';close.onclick=()=>dialog.close();dialog.append(close);if(!dialog.open)dialog.showModal();heading.focus();
 }
 document.addEventListener('input',event=>{if(event.target.closest?.('#hg-backup-draft-dialog')||!event.target.closest?.('form'))return;try{mirror();}catch(_){notice('Zusätzlicher Entwurf nicht lesbar. Vorhandene Entwürfe bleiben erhalten; bitte den geöffneten Vorgang speichern.');}schedule();});
 document.addEventListener('change',event=>{if(event.target.closest?.('#hg-backup-draft-dialog')||!event.target.closest?.('form'))return;try{mirror();}catch(_){}schedule();});
 document.addEventListener('submit',schedule,true);document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='hidden')flush();});window.addEventListener('pagehide',flush);
 window.HGBackupSnapshot=Object.freeze({capture,flush,reviewDrafts:review,bind(token,active){if(pageToken!==token){pageToken=String(token||'');sequence=0;}enabled=!!active;}});
})();

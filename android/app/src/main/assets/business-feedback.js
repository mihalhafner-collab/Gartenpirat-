/* Presentation only. Raw evidence stays available in explicitly opened diagnostics. */
(()=>{'use strict';
 const recent=[];
 const time=value=>{const date=new Date(value);return value&&!Number.isNaN(date.getTime())?date.toLocaleString('de-AT',{dateStyle:'medium',timeStyle:'short'}):'';};
 function translate(value){
  const raw=String(value?.message||value||'');
  const missing=raw.match(/Pflichtangabe fehlt:\s*([a-z_]+)/);if(missing){const names={owner_name:'Ausstellername',business_name:'Geschäftsname',business_street:'Geschäftsanschrift',business_postal_code:'Postleitzahl',business_city:'Ort',business_vat_id:'UID',tax_exemption_text:'Steuerhinweis',customer_id:'Kunde',activity:'Tätigkeit',price_source:'Preisgrundlage',invoice_number_format:'Rechnungsnummernkreis'};return names[missing[1]]?'Bitte '+names[missing[1]]+' ergänzen.':'Bitte die fehlenden Pflichtangaben ergänzen. Weitere Angaben stehen unter Erweitert / Diagnose.';}
  if(/ENOSPC|SQLITE_FULL|speicher.*(voll|knapp)|disk.*full|QuotaExceeded/i.test(raw))return 'Nicht gespeichert. Der Gerätespeicher ist voll. Bitte Speicher freigeben und erneut speichern. Deine Eingabe bleibt erhalten.';
  if(/WRITE_CONFLICT|STALE_|record_hash|expected_.*hash|CAS|inzwischen geändert|Vergleichsstand/i.test(raw))return 'Inzwischen wurde dieser Eintrag geändert. Deine Eingabe bleibt erhalten. Bitte den aktuellen Stand prüfen, bevor du erneut speicherst.';
  if(/PERMISSION|UNAUTHORIZED|HTTP 401|HTTP 403|AUTH_REQUIRED|Freigabe fehlt|SecurityException/i.test(raw))return 'Die Freigabe fehlt oder ist abgelaufen. Bitte die Verbindung erneut freigeben. Bereits gespeicherte Daten bleiben erhalten.';
  if(/NETWORK|HTTP 429|HTTP 5\d\d|timeout|IOException|Socket|provider_readback|LEGACY_SOURCE_CHANGED|READ_ERROR|WRITE_ERROR|JSON|Snapshot|Journal|\bQueue\b/i.test(raw))return 'Der Vorgang konnte nicht abgeschlossen werden. Bereits gespeicherte Daten bleiben erhalten. Bitte später erneut versuchen; weitere Angaben stehen unter Erweitert / Diagnose.';
  if(/\b[A-Z][A-Z_]{4,}\b|\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\b|\b[a-f0-9]{32,64}\b|(?:record|preview|action|context)_hash|(?:content|file):\/\//.test(raw))return 'Der Vorgang konnte nicht abgeschlossen werden. Bereits gespeicherte Daten bleiben erhalten. Weitere Angaben stehen unter Erweitert / Diagnose.';
  return raw.replace(/^ERROR:\s*/, '')||'Der Vorgang konnte nicht abgeschlossen werden.';
 }
 const backupOnly=s=>s?.drive_role==='backup_only'||s?.mode==='backup_only'||Number(s?.storage_contract_version)>=3;
 function backupStatus(s){const b=s.full_backup;if(!b||typeof b!=='object')return {title:'Sicherungsstatus offen',detail:'Geschäftsdaten liegen auf diesem Gerät. Eine extern geprüfte Vollsicherung ist noch nicht bestätigt. Sicherung öffnen; Drive dient nur als Sicherungsziel.',tone:'busy'};
  const checked=time(b.last_external_verified_at),captured=time(b.last_external_captured_at);if(checked&&captured&&!b.clock_review_required)return {title:'Sicherung extern geprüft',detail:'Auf diesem Gerät gespeichert. Vollsicherung extern geprüft: '+checked+' · enthaltener Datenstand: '+captured+'. Neuere Arbeit ist damit nicht automatisch gesichert.',tone:'ok'};
  const jobs=(Array.isArray(b.jobs)?b.jobs:[]).filter(job=>job.context_matches===true&&job.target_matches===true),job=jobs[0]||{},phase=String(job.phase||'');
  if(b.clock_review_required)return {title:'Sicherungszeit prüfen',detail:'Geschäftsdaten bleiben lokal gespeichert. Zeitpunkt und Datenstand der Sicherung sind noch nicht zuverlässig bestätigt. Sicherung öffnen und Gerätezeit prüfen.',tone:'error'};
  if(b.requires_setup)return {title:'Sicherung einrichten',detail:'Geschäftsdaten bleiben lokal gespeichert. Sicherung öffnen, Wiederherstellungscode und gewähltes Drive- oder USB-Ziel prüfen. Kein Geschäftsabgleich nötig.',tone:'busy'};
  if(['FAILED','NEEDS_USER_ACTION','FORMAT_REVIEW_REQUIRED'].includes(phase)||b.last_scheduler_error_code)return {title:'Sicherung prüfen',detail:'Geschäftsdaten bleiben lokal gespeichert. Der Sicherungsauftrag benötigt eine Prüfung. Sicherung öffnen; keine externe Sicherung bestätigt.',tone:'error'};
  if(phase==='PROVIDER_VERIFIED')return {title:'Dateianbieter zurückgelesen',detail:'Das Sicherungsarchiv wurde vom Anbieter zurückgelesen. Ein unabhängiger externer Upload ist dadurch noch nicht bestätigt. Geschäftsdaten bleiben lokal gespeichert.',tone:'busy'};
  if(['UPLOAD_PENDING','UPLOADING','REMOTE_VERIFY_PENDING','LOCAL_VERIFIED'].includes(phase))return {title:'Sicherung ausstehend',detail:'Sicherung lokal vorbereitet. Externer Upload und vollständiges Rücklesen sind noch nicht bestätigt. Du kannst lokal weiterarbeiten.',tone:'busy'};
  return {title:'Sicherung ausstehend',detail:'Auf diesem Gerät gespeichert. Noch keine extern vollständig geprüfte Vollsicherung bestätigt. Automatische Uploads und zusätzliche USB-Sicherung unter Sicherung prüfen.',tone:'busy'};
 }
 function sync(s={}){
  if(backupOnly(s))return backupStatus(s);
  const pending=Number(s.pending||0),review=Number(s.review||0),code=String(s.last_error_code||''),state=String(s.state||'');
  if(s.syncing)return {title:'Synchronisierung läuft …',detail:'Du kannst weiterarbeiten.',tone:'busy'};
  const failures={FOLDER_NOT_SELECTED:['Drive-Ordner auswählen','Bitte zuerst den bestehenden Drive-Ordner auswählen. Lokale Daten bleiben erhalten.'],DEVICE_ID_MISSING:['Gerätekennung fehlt','App erneut öffnen und den Abgleich erneut versuchen. Keine neue Änderungsdatei angelegt.'],NETWORK_UNAVAILABLE:['Internetverbindung unterbrochen','Lokale Daten bleiben erhalten. Der nächste Versuch berücksichtigt die Wiederholungswartezeit.'],STATUS_NOT_SAVED:['Daten gesichert, Status offen','Daten gesichert, Status konnte nicht gespeichert werden. Der bestätigte Datenstand bleibt erhalten.']};
  if(failures[code])return {title:failures[code][0],detail:s.detail||failures[code][1],tone:code==='STATUS_NOT_SAVED'||code==='NETWORK_UNAVAILABLE'?'busy':'error'};
  if(/PERMISSION|AUTH|FORBIDDEN/.test(code))return {title:'Verbindung freigeben',detail:'Bitte die Google-Drive-Verbindung erneut freigeben. Bereits gespeicherte Daten bleiben auf diesem Gerät.',tone:'error'};
  if(review||state==='action_required'&&/CONFLICT|LEGACY|REVIEW/.test(code))return {title:'Prüfung erforderlich',detail:'Widersprüchliche Änderungen benötigen eine Entscheidung. Der aktuelle Datenbestand bleibt erhalten.',tone:'error'};
  if(s.last_error||s.drive_sync_failed||state==='error'||state==='action_required'){
   const permission=/PERMISSION|AUTH|FORBIDDEN/.test(code)||/Freigabe|Berechtigung/.test(s.last_error||'');
   return {title:permission?'Verbindung freigeben':'Synchronisierung derzeit nicht möglich',detail:permission?'Bitte die Google-Drive-Verbindung erneut freigeben. Bereits gespeicherte Daten bleiben auf diesem Gerät.':'Bereits gespeicherte Daten bleiben auf diesem Gerät. '+'Bitte die Verbindung prüfen und später erneut versuchen.',tone:'error'};
  }
  if(!s.configured)return {title:'Google Drive ist nicht verbunden',detail:'Du kannst auf diesem Gerät arbeiten. Für den Abgleich bitte Google Drive verbinden.',tone:'busy'};
  if(pending)return {title:'Auf diesem Gerät gespeichert',detail:'Die Synchronisierung steht noch aus.',tone:'busy'};
  if(s.cloud_confirmed)return {title:'Mit Google Drive abgeglichen',detail:'Letzter bestätigter Abgleich'+(time(s.last_full_sync||s.last_sync)?': '+time(s.last_full_sync||s.last_sync):'')+'.',tone:'ok'};
  if(s.last_sync&&s.confirmation_scope==='provider_readback')return {title:'Übertragung geprüft',detail:'Die Übertragung zu Google Drive ist geprüft. Eine vollständige Sicherung ist damit noch nicht bestätigt.',tone:'busy'};
  if(s.last_sync)return {title:'Abgleich geprüft',detail:'Letzter erfolgreicher Abgleich: '+time(s.last_sync)+'. Eine vollständige Sicherung wird separat geprüft.',tone:'ok'};
  return {title:'Google Drive ist verbunden',detail:'Der erste erfolgreiche Abgleich steht noch aus.',tone:'busy'};
 }
 function error(value){const raw=String(value?.message||value||''),shown=translate(value);if(raw&&raw!==shown){recent.push(raw);if(recent.length>20)recent.shift();}return shown;}
 function connection(info={}){if(info.state==='auth_error')return 'Bitte die ChatGPT-Verbindung erneut freigeben.';if(info.state==='start_blocked')return 'ChatGPT kann nicht starten. Bitte die Gerätefreigabe prüfen.';return info.running?(info.state==='ready'?'ChatGPT ist verbunden.':'ChatGPT ist eingeschaltet. Verbindung wird geprüft.'):'ChatGPT ist ausgeschaltet.';}
 window.HGBusinessFeedback=Object.freeze({error,sync,backupOnly,time,connection,diagnostics:()=>recent.slice()});
 document.addEventListener('DOMContentLoaded',()=>{const alert=window.alert.bind(window);window.alert=value=>alert(error(value));const toast=window.showUndoToast;if(toast){const wrapped=function(value,...args){return toast.call(this,error(value),...args);};window.showUndoToast=wrapped;}},{once:true});
})();

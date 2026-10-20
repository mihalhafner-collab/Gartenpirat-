/* Presentation only. Raw evidence stays available in explicitly opened diagnostics. */
(()=>{'use strict';
 const recent=[];
 const time=value=>{const date=new Date(value);return value&&!Number.isNaN(date.getTime())?date.toLocaleString('de-AT',{dateStyle:'medium',timeStyle:'short'}):'';};
 function translate(value){
  const raw=String(value?.message||value||'');
  if(/ENOSPC|SQLITE_FULL|speicher.*(voll|knapp)|disk.*full|QuotaExceeded/i.test(raw))return 'Nicht gespeichert. Der Gerätespeicher ist voll. Bitte Speicher freigeben und erneut speichern. Deine Eingabe bleibt erhalten.';
  if(/WRITE_CONFLICT|STALE_|record_hash|expected_.*hash|CAS|inzwischen geändert|Vergleichsstand/i.test(raw))return 'Inzwischen wurde dieser Eintrag geändert. Deine Eingabe bleibt erhalten. Bitte den aktuellen Stand prüfen, bevor du erneut speicherst.';
  if(/PERMISSION|UNAUTHORIZED|HTTP 401|HTTP 403|AUTH_REQUIRED|Freigabe fehlt|SecurityException/i.test(raw))return 'Die Freigabe fehlt oder ist abgelaufen. Bitte die Verbindung erneut freigeben. Bereits gespeicherte Daten bleiben erhalten.';
  if(/NETWORK|HTTP 429|HTTP 5\d\d|timeout|IOException|Socket|provider_readback|LEGACY_SOURCE_CHANGED|READ_ERROR|WRITE_ERROR|JSON|Snapshot|Journal|\bQueue\b/i.test(raw))return 'Der Vorgang konnte nicht abgeschlossen werden. Bereits gespeicherte Daten bleiben erhalten. Bitte später erneut versuchen; weitere Angaben stehen unter Erweitert / Diagnose.';
  return raw.replace(/^ERROR:\s*/, '')||'Der Vorgang konnte nicht abgeschlossen werden.';
 }
 function sync(s={}){
  const pending=Number(s.pending||0),review=Number(s.review||0),code=String(s.last_error_code||''),state=String(s.state||'');
  if(s.syncing)return {title:'Synchronisierung läuft …',detail:'Du kannst weiterarbeiten.',tone:'busy'};
  if(/PERMISSION|AUTH|FORBIDDEN/.test(code))return {title:'Verbindung freigeben',detail:'Bitte die Google-Drive-Verbindung erneut freigeben. Bereits gespeicherte Daten bleiben auf diesem Gerät.',tone:'error'};
  if(review||state==='action_required'&&/CONFLICT|LEGACY|REVIEW/.test(code))return {title:'Prüfung erforderlich',detail:'Widersprüchliche Änderungen benötigen eine Entscheidung. Der aktuelle Datenbestand bleibt erhalten.',tone:'error'};
  if(s.last_error||s.drive_sync_failed||state==='error'||state==='action_required'){
   const permission=/PERMISSION|AUTH|FORBIDDEN/.test(code)||/Freigabe|Berechtigung/.test(s.last_error||'');
   return {title:permission?'Verbindung freigeben':'Synchronisierung derzeit nicht möglich',detail:permission?'Bitte die Google-Drive-Verbindung erneut freigeben. Bereits gespeicherte Daten bleiben auf diesem Gerät.':'Bereits gespeicherte Daten bleiben auf diesem Gerät. '+'Bitte die Verbindung prüfen und später erneut versuchen.',tone:'error'};
  }
  if(!s.configured)return {title:'Google Drive ist nicht verbunden',detail:'Du kannst auf diesem Gerät arbeiten. Für den Abgleich bitte Google Drive verbinden.',tone:'busy'};
  if(pending)return {title:'Auf diesem Gerät gespeichert',detail:'Die Synchronisierung steht noch aus.',tone:'busy'};
  if(s.cloud_confirmed)return {title:'Mit Google Drive abgeglichen',detail:'Letzter bestätigter Abgleich'+(time(s.last_full_sync||s.last_sync)?': '+time(s.last_full_sync||s.last_sync):'')+'.',tone:'ok'};
  if(s.last_sync&&s.confirmation_scope==='provider_readback')return {title:'Übertragung geprüft',detail:'Google Drive hat die Daten zurückgegeben. Der Online-Abgleich ist noch nicht unabhängig bestätigt.',tone:'busy'};
  if(s.last_sync)return {title:'Abgleich geprüft',detail:'Letzter erfolgreicher Abgleich: '+time(s.last_sync)+'. Eine vollständige Sicherung wird separat geprüft.',tone:'ok'};
  return {title:'Google Drive ist verbunden',detail:'Der erste erfolgreiche Abgleich steht noch aus.',tone:'busy'};
 }
 function error(value){const raw=String(value?.message||value||''),shown=translate(value);if(raw&&raw!==shown){recent.push(raw);if(recent.length>20)recent.shift();}return shown;}
 function connection(info={}){if(info.state==='auth_error')return 'Bitte die ChatGPT-Verbindung erneut freigeben.';if(info.state==='start_blocked')return 'ChatGPT kann nicht starten. Bitte die Gerätefreigabe prüfen.';return info.running?(info.state==='ready'?'ChatGPT ist verbunden.':'ChatGPT ist eingeschaltet. Verbindung wird geprüft.'):'ChatGPT ist ausgeschaltet.';}
 window.HGBusinessFeedback=Object.freeze({error,sync,time,connection,diagnostics:()=>recent.slice()});
 document.addEventListener('DOMContentLoaded',()=>{const alert=window.alert.bind(window);window.alert=value=>alert(error(value));const toast=window.showUndoToast;if(toast){const wrapped=function(value,...args){return toast.call(this,error(value),...args);};window.showUndoToast=wrapped;}},{once:true});
})();

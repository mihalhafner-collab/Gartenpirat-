/* Settings affect new imports only; existing private files are never deleted. */
(()=>{'use strict';
 function init(){
  const page=document.getElementById('more-page');if(!page)return;
  const section=document.createElement('section');section.className='card';
  section.innerHTML='<h3>Fotospeicher</h3><label>Fotoqualität<select id="photo-quality"><option value="optimized">Speicheroptimierte Dokumentation</option><option value="high">Hoch</option><option value="original">Original behalten · mehr Speicher, Originalmetadaten bleiben erhalten</option></select></label><p>Gilt für neue Fotos. Bereits gespeicherte Bilder bleiben erhalten.</p><p data-photo-storage role="status"></p>';
  page.append(section);const choice=section.querySelector('select'),status=section.querySelector('[data-photo-storage]');
  const read=()=>{try{const settings=JSON.parse(AndroidData.getOwnerSettings()||'{}');choice.value=settings.photo_quality||'optimized';const s=JSON.parse(AndroidData.getPhotoStorageSummary()),sync=JSON.parse(AndroidData.getSyncStatus?.()||'{}');status.textContent=s.local_files+' lokale Bilddateien · '+(s.local_bytes/1048576).toFixed(1)+' MB · '+(s.available_bytes/1073741824).toFixed(1)+' GB frei · '+(sync.cloud_confirmed?'Drive bestätigt':sync.configured?'Drive-Abgleich noch nicht vollständig bestätigt':'Drive nicht verbunden')+(s.available_bytes<200*1048576?' · Speicher knapp. Vor weiteren Aufnahmen Speicher prüfen.':'');}catch(_){status.textContent='Speicherübersicht nach Aktualisierung der App verfügbar.';}};
  choice.onchange=()=>{try{const response=JSON.parse(AndroidData.ownerCall('get_settings','{}'));if(!response.ok)throw new Error('Einstellungen nicht verfügbar');const result=JSON.parse(AndroidData.ownerCall('update_settings',JSON.stringify({request_id:'photo-quality-'+crypto.randomUUID(),expected_record_hash:response.data.record_hash,updates:{photo_quality:choice.value}})));if(!result.ok)throw new Error(result.error?.message||'Speicherung nicht bestätigt');read();}catch(e){status.textContent=e.message;}};read();
 }
 document.addEventListener('DOMContentLoaded',()=>setTimeout(()=>setTimeout(init,0),0),{once:true});
})();

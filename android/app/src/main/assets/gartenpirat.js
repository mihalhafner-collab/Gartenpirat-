/* Gartenpirat: local botanical reference. Never reads or writes business data. */
(() => {
 'use strict';
 if (document.getElementById('gp-launcher')) return;
 const catalog = window.GartenpiratCatalog;
 const plants = Array.isArray(catalog?.plants) ? catalog.plants : [];
 const byId = new Map(plants.map(p => [p.id,p]));
 const POSITION_KEY = 'hausgarten.gartenpirat.position.v1';
 const escape = value => String(value ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
 const fold = text => String(text).toLocaleLowerCase('de').replace(/ä/g,'ae').replace(/ö/g,'oe').replace(/ü/g,'ue').replace(/ß/g,'ss').normalize('NFKD').replace(/[\u0300-\u036f]/g,'');
 const safeLink = value => {try {const u=new URL(value);return u.protocol==='https:'?escape(u.href):'';}catch{return '';}};
 const imagePath = value => /^plant-images\/[a-z0-9-]+\.jpg$/.test(value||'') ? value : '';
 const list = value => Array.isArray(value)&&value.length ? value.join(' · ') : 'In der Quelle nicht angegeben';
 const launcher=document.createElement('button');
 launcher.id='gp-launcher';launcher.type='button';launcher.className='gp-launcher';
 launcher.setAttribute('aria-label','Gartenpirat öffnen');launcher.setAttribute('aria-haspopup','dialog');
 launcher.setAttribute('aria-controls','gp-dialog');launcher.setAttribute('aria-expanded','false');
 launcher.setAttribute('aria-description','Zum Verschieben ziehen oder bei fokussiertem Knopf die Pfeiltasten verwenden. Pos1 setzt die Position zurück.');
 launcher.title='Gartenpirat öffnen · zum Verschieben ziehen';
 launcher.innerHTML='<span class="gp-mascot"><img src="gartenpirat.png" alt="" draggable="false"></span><span class="gp-launcher-label">Gartenpirat</span>';
 document.body.appendChild(launcher);
 const dialog=document.createElement('dialog');dialog.id='gp-dialog';dialog.className='gp-dialog';
 dialog.setAttribute('aria-labelledby','gp-title');
 dialog.innerHTML='<header class="gp-head"><button type="button" class="gp-back" aria-label="Gartenpirat: einen Schritt zurück">‹</button><div><p class="gp-eyebrow">DEIN GARTENWISSEN</p><h2 id="gp-title">Gartenpirat</h2></div><button type="button" class="gp-close" aria-label="Gartenpirat schließen">×</button></header><div class="gp-content" id="gp-content"></div>';
 document.body.appendChild(dialog);
 const content=dialog.querySelector('.gp-content');
 let state={view:'menu',returnTo:'menu',selected:null,query:'',category:'',light:'',neophyte:'',limit:40,scroll:0,imageScroll:0,imageIndex:0};
 let lastRandom=null,previousFocus=null,savedPhoto=null,photoFile=null,photoIsStored=false,photoSaving=false;
 let storageReady=window.GartenpiratPhotoStore?.get().then(record=>{savedPhoto=record;}).catch(()=>{})||Promise.resolve();
 const index=new Map(plants.map(p=>[p.id,fold([p.name,p.scientificName,p.family,p.category,...(p.alternativeNames||[]),...(p.light||[]),p.neophyte?.statusLabel,p.neophyte?.austriaStatus].join(' '))]));
 function menu() {
  releasePhoto();
  releasePlantViewer();
  state.view='menu';dialog.querySelector('#gp-title').textContent='Gartenpirat';
  content.innerHTML='<div class="gp-welcome"><div><span class="gp-badge">OFFLINE DABEI</span><h3>Was wächst denn da?</h3><p>Entdecke Pflanzen für deinen Gartenalltag.</p></div><img src="gartenpirat.png" alt="" draggable="false"></div>'+
   '<div class="gp-options"><button type="button" data-gp-action="random"><span class="gp-option-icon" aria-hidden="true">✦</span><span><strong>Zufällige Pflanze</strong><small>Ein neues Porträt mit Pflege und Bildern</small></span><span aria-hidden="true">›</span></button>'+
   '<button type="button" data-gp-action="lexicon"><span class="gp-option-icon" aria-hidden="true">☘</span><span><strong>Pflanzenlexikon</strong><small>Suchen, filtern und Pflanzen kennenlernen</small></span><span aria-hidden="true">›</span></button>'+
   '<button type="button" data-gp-action="neophytes"><span class="gp-option-icon" aria-hidden="true">⚠</span><span><strong>Neophyten in Österreich</strong><small>Fachlich getrennt: etabliert, invasiv und EU-reguliert</small></span><span aria-hidden="true">›</span></button>'+
   '<button type="button" data-gp-action="choose-photo"><span class="gp-option-icon" aria-hidden="true">▧</span><span><strong>Eigenes Foto öffnen</strong><small>Aus Google Fotos oder deiner Galerie · in Vollbild</small></span><span aria-hidden="true">›</span></button></div><p class="gp-photo-message" role="status" aria-live="polite" hidden></p>'+
   (savedPhoto?'<div class="gp-saved-photo"><button type="button" class="gp-primary" data-gp-action="stored-photo">Gespeichertes Bild öffnen</button><small>Auf diesem Gerät gespeichert · Beim Knopfdruck '+(savedPhoto.autoShow?'Bild anzeigen':'Menü anzeigen')+'</small></div>':'')+
   '<p class="gp-meta">'+plants.length+' Pflanzenprofile für Deutschland, Österreich und die Schweiz · Stand '+escape(catalog?.edition||'')+'</p>'+
   '<p class="gp-note">'+escape(catalog?.scope||'Der Pflanzenkatalog ist derzeit nicht verfügbar.')+'</p>'+
   '<button type="button" class="gp-text-button" data-gp-action="reset-position">Knopfposition zurücksetzen</button>';
  content.scrollTop=0;
 }

 const photoInput=document.createElement('input');
 photoInput.type='file';photoInput.id='gp-photo-input';photoInput.accept='image/*';
 photoInput.hidden=true;photoInput.setAttribute('aria-label','Eigenes Foto auswählen');
 dialog.appendChild(photoInput);
 let photoURL=null,pendingURL=null,pendingImage=null,photoGeneration=0,nativePhotoFullscreen=false;
 let viewerGesture={pointers:new Map(),distance:0,scale:1,x:0,y:0,swipe:null};
 function nativeFullscreen(enabled){
  try{
   if(typeof window.AndroidGardenPhotos?.setFullscreen==='function'){
    window.AndroidGardenPhotos.setFullscreen(enabled);nativePhotoFullscreen=enabled;return true;
   }
  }catch{}
  return false;
 }
 function cancelPendingPhoto(){
  photoGeneration++;
  if(pendingImage){pendingImage.onload=null;pendingImage.onerror=null;pendingImage.src='';pendingImage=null;}
  if(pendingURL){URL.revokeObjectURL(pendingURL);pendingURL=null;}
 }
 function releasePhoto(){
  cancelPendingPhoto();photoInput.value='';photoFile=null;photoIsStored=false;
  const image=content.querySelector('#gp-own-photo');if(image)image.removeAttribute('src');
  if(photoURL){URL.revokeObjectURL(photoURL);photoURL=null;}
  if(nativePhotoFullscreen)nativeFullscreen(false);
  dialog.classList.remove('gp-photo-mode');
 }
 function releasePlantViewer(){
  viewerGesture.pointers.clear();viewerGesture.distance=0;viewerGesture.scale=1;viewerGesture.x=0;viewerGesture.y=0;
  if(dialog.classList.contains('gp-image-mode')&&nativePhotoFullscreen)nativeFullscreen(false);
  dialog.classList.remove('gp-image-mode');
 }
 function photoMessage(message){
  let notice=content.querySelector('.gp-photo-message');
  if(!notice){notice=document.createElement('p');notice.className='gp-photo-message';notice.setAttribute('role','status');notice.setAttribute('aria-live','polite');content.appendChild(notice);}
  const options=content.querySelector('.gp-photo-options');
  if(options&&!options.hidden&&notice.parentNode!==options)options.appendChild(notice);
  else if(options?.hidden&&notice.parentNode!==content)content.appendChild(notice);
  notice.textContent=message;notice.hidden=!message;
 }
 function choosePhoto(){
  photoInput.value='';
  try{window.AndroidGardenPhotos?.preparePicker?.();}catch{}
  photoInput.click();
 }
 function selectedPhoto(file,stored=false){
  if(!file)return;
  cancelPendingPhoto();
  if(!file.size){photoMessage('Dieses Foto ist leer. Bitte wähle ein anderes Bild.');return;}
  if(file.size>40*1024*1024){photoMessage('Dieses Foto ist größer als 40 MB. Bitte wähle eine kleinere Bilddatei.');return;}
  if(/svg|xml|html/i.test(file.type)||/\.(svg|html?)$/i.test(file.name)||
   (!/^image\/(jpeg|jpg|png|webp|gif|avif|bmp|x-ms-bmp|heic|heif)$/i.test(file.type)&&!(/\.(jpe?g|png|webp|gif|avif|bmp|heic|heif)$/i.test(file.name)&&(!file.type||file.type==='application/octet-stream')))){
   photoMessage('Bitte wähle eine Bilddatei, zum Beispiel JPG, PNG oder WebP.');return;
  }
  const generation=photoGeneration;
  const candidate=URL.createObjectURL(file),image=new Image();
  pendingURL=candidate;pendingImage=image;
  photoMessage('Foto wird geöffnet …');
  function failed(message){
   if(generation!==photoGeneration)return;
   pendingImage=null;pendingURL=null;image.onload=null;image.onerror=null;image.src='';
   URL.revokeObjectURL(candidate);photoMessage(message);
  }
  image.onerror=()=>failed('Dieses Foto lässt sich hier nicht öffnen. Bitte wähle JPG, PNG oder WebP.');
  image.onload=()=>{
   if(generation!==photoGeneration||!dialog.open){URL.revokeObjectURL(candidate);return;}
   if(!image.naturalWidth||image.naturalWidth*image.naturalHeight>64000000){failed('Das Bild ist zu groß für diese Ansicht. Bitte wähle ein Foto mit höchstens 64 Megapixeln.');return;}
   pendingImage=null;pendingURL=null;image.onload=null;image.onerror=null;
   if(photoURL)URL.revokeObjectURL(photoURL);photoURL=candidate;photoFile=file;photoIsStored=stored;
   state.view='photo';dialog.classList.add('gp-photo-mode');dialog.querySelector('#gp-title').textContent='Dein Foto';
   content.innerHTML='<div class="gp-photo-stage"></div><div class="gp-photo-tools"><button type="button" data-gp-action="photo-options">Bildoptionen</button><button type="button" class="gp-photo-close" data-gp-action="back" aria-label="Foto schließen und zum Gartenpirat zurückkehren">×</button></div><section class="gp-photo-options" hidden><h3>Dein Bild</h3><p>Nur auf diesem Gerät. Unabhängig von Pflanzenlexikon und Aufträgen.</p><label><input type="checkbox" id="gp-photo-auto" '+(stored&&savedPhoto?.autoShow?'checked':'')+'> Beim Gartenpirat-Knopf sofort dieses Bild anzeigen</label><button type="button" class="gp-primary" data-gp-action="save-photo">Auf diesem Gerät speichern</button><button type="button" data-gp-action="choose-photo">Anderes Foto</button><button type="button" data-gp-action="back">Zum Gartenpirat-Menü</button><button type="button" data-gp-action="photo-options">Weiter zum Bild</button><p class="gp-photo-message" role="status" aria-live="polite" hidden></p></section>';
   image.id='gp-own-photo';image.alt='Dein ausgewähltes Foto';image.draggable=false;
   content.querySelector('.gp-photo-stage').appendChild(image);content.scrollTop=0;
   nativeFullscreen(true);
   content.querySelector('.gp-photo-close').focus({preventScroll:true});
  };
  image.src=candidate;
 }
 photoInput.addEventListener('change',()=>{const file=photoInput.files?.[0];photoInput.value='';selectedPhoto(file);});
 addEventListener('pagehide',releasePhoto);

 async function showStoredPhoto(){
  await storageReady;
  if(!dialog.open)return;
  if(!savedPhoto?.image){photoMessage('Noch kein Bild auf diesem Gerät gespeichert.');return;}
  selectedPhoto(new File([savedPhoto.image],savedPhoto.name||'Mein Foto',{type:savedPhoto.image.type}),true);
 }
 async function savePhoto(){
  if(!photoFile||photoSaving)return;
  const generation=photoGeneration,file=photoFile,auto=!!content.querySelector('#gp-photo-auto')?.checked;
  const button=content.querySelector('[data-gp-action="save-photo"]');photoSaving=true;if(button)button.disabled=true;
  photoMessage('Bild wird auf diesem Gerät gespeichert …');
  try{
   const record=await window.GartenpiratPhotoStore.put(file,auto);savedPhoto=record;
   if(generation===photoGeneration&&dialog.open){photoIsStored=true;photoMessage(auto?'Gespeichert. Beim Gartenpirat-Knopf erscheint dieses Bild.':'Gespeichert. Beim Gartenpirat-Knopf erscheint das Menü.');}
  }catch{if(generation===photoGeneration&&dialog.open)photoMessage('Speichern nicht möglich. Bitte freien Gerätespeicher prüfen und erneut versuchen. Dein bisher gespeichertes Bild bleibt erhalten.');}
  finally{photoSaving=false;if(button?.isConnected)button.disabled=false;}
 }
 function matchesNeophyteFilter(plant,filter){
  const neo=plant.neophyte;
  if(!filter)return true;
  if(filter==='all')return neo?.isNeophyte===true;
  if(filter==='established')return neo?.establishmentStatus==='established'||neo?.establishmentStatus==='locally-established';
  if(filter==='invasive')return neo?.invasive===true;
  if(filter==='union')return neo?.unionConcern===true;
  return false;
 }
 function neophyteBadge(plant){return plant.neophyte?.unionConcern?'EU-Unionsliste':plant.neophyte?.invasive?'Invasiver Neophyt':plant.neophyte?.isNeophyte?'Neophyt':'';}
 function results() {
  const tokens=fold(state.query.trim()).split(/\s+/).filter(Boolean);
  return plants.filter(p=>(!state.category||p.category===state.category)&&(!state.light||(p.light||[]).some(v=>v.startsWith(state.light)))&&matchesNeophyteFilter(p,state.neophyte)&&tokens.every(t=>index.get(p.id).includes(t)));
 }
 function renderResults() {
  const found=results(),shown=found.slice(0,state.limit),target=content.querySelector('#gp-results');
  content.querySelector('#gp-count').textContent=found.length+' von '+plants.length+' Pflanzen';
  target.innerHTML=shown.length?shown.map(p=>'<button type="button" class="gp-plant-card" data-gp-plant="'+escape(p.id)+'"><img src="'+escape(imagePath(p.images?.[0]?.path))+'" alt="'+escape(p.name)+'" loading="lazy" width="240" height="160"><span class="gp-plant-copy"><small>'+escape(p.category)+(neophyteBadge(p)?' · '+escape(neophyteBadge(p)):'')+'</small><strong>'+escape(p.name)+'</strong><em>'+escape(p.scientificName)+'</em></span></button>').join(''):'<p class="gp-empty">Keine Pflanze passt zu dieser Suche. Versuche einen anderen Namen oder entferne die Filter.</p>';
  let more=content.querySelector('#gp-more');if(more)more.remove();
  if(found.length>shown.length){more=document.createElement('button');more.id='gp-more';more.type='button';more.className='gp-primary';more.dataset.gpAction='more';more.textContent='Weitere Pflanzen anzeigen';target.after(more);}
 }
 function lexicon(restore=false) {
  state.view='lexicon';dialog.querySelector('#gp-title').textContent=state.neophyte?'Neophyten':'Pflanzenlexikon';
  const categories=[...new Set(plants.map(p=>p.category))].sort((a,b)=>a.localeCompare(b,'de'));
  const neophyteNotice=state.neophyte?'<aside class="gp-neophyte-note"><strong>Amtlicher Status, keine Bestimmungsfreigabe.</strong><span>'+escape(catalog.neophyteReference?.notice||'Vor Maßnahmen aktuelle amtliche und örtliche Vorgaben prüfen.')+'</span></aside>':'';
  content.innerHTML=neophyteNotice+'<div class="gp-search"><label for="gp-query">Pflanze suchen</label><input id="gp-query" type="search" maxlength="120" placeholder="Name, botanischer Name oder Familie" autocomplete="off" value="'+escape(state.query)+'"><div class="gp-filters"><label>Gruppe<select id="gp-category"><option value="">Alle Gruppen</option>'+categories.map(c=>'<option value="'+escape(c)+'"'+(state.category===c?' selected':'')+'>'+escape(c)+'</option>').join('')+'</select></label><label>Standort<select id="gp-light"><option value="">Alle Standorte</option>'+['Sonne','Halbschatten','Schatten'].map(l=>'<option'+(state.light===l?' selected':'')+'>'+l+'</option>').join('')+'</select></label><label>Status<select id="gp-neophyte"><option value="">Alle Pflanzen</option><option value="all"'+(state.neophyte==='all'?' selected':'')+'>Alle erfassten Neophyten</option><option value="established"'+(state.neophyte==='established'?' selected':'')+'>Etabliert / lokal etabliert</option><option value="invasive"'+(state.neophyte==='invasive'?' selected':'')+'>Invasiv eingestuft</option><option value="union"'+(state.neophyte==='union'?' selected':'')+'>EU-Unionsliste (reguliert)</option></select></label></div><p id="gp-count" class="gp-count" role="status" aria-live="polite"></p></div><div id="gp-results" class="gp-results"></div>';
  renderResults();content.scrollTop=restore?state.scroll:0;
 }
 function fact(title,value){return '<div class="gp-fact"><dt>'+escape(title)+'</dt><dd>'+escape(Array.isArray(value)?list(value):value||'In der Quelle nicht angegeben')+'</dd></div>';}
 function detail(plant,origin,restoreScroll=false) {
  if(!plant)return;
  releasePlantViewer();
  state.view='detail';state.selected=plant.id;state.returnTo=origin;dialog.querySelector('#gp-title').textContent='Pflanzenporträt';
  const gallery=(plant.images||[]).map((pic,i)=>'<figure><button type="button" class="gp-image-button" data-gp-image="'+i+'" aria-label="'+escape(plant.name)+' – Bild '+(i+1)+' im Vollbild öffnen"><img src="'+escape(imagePath(pic.path))+'" alt="'+escape(plant.name)+' – botanische Aufnahme '+(i+1)+'" width="800" height="600"></button><figcaption>'+escape(pic.author)+' · <a href="'+safeLink(pic.licenseUrl)+'" target="_blank" rel="noopener noreferrer">'+escape(pic.license)+'</a> · <a href="'+safeLink(pic.source)+'" target="_blank" rel="noopener noreferrer">Bildquelle</a><small>'+escape(pic.changes)+'</small></figcaption></figure>').join('');
  const neo=plant.neophyte;
  const neophytePanel=neo?'<section class="gp-neophyte-panel"><p class="gp-neophyte-badge">'+escape(neo.unionConcern?'EU-UNIONSLISTE · ÖSTERREICH':'NEOPHYT · ÖSTERREICH')+'</p><h4>Neophyten- und Rechtsinformation</h4><dl class="gp-facts">'+fact('Status in Österreich',neo.austriaStatus)+fact('Einordnung',neo.statusLabel)+fact('Erkennungsmerkmale',neo.recognition)+fact('Ausbreitung',neo.spread)+fact('Ökologische Auswirkungen',neo.impacts)+fact('Kontrolle',neo.control)+fact('Entsorgung',neo.disposal)+fact('Rechtlicher Hinweis',neo.legalNotes)+fact('Datenumfang',neo.dataNote)+'</dl><p class="gp-note">'+escape(catalog.neophyteReference?.notice||'Vor Maßnahmen aktuelle amtliche und örtliche Vorgaben prüfen.')+'</p></section>':'';
  content.innerHTML='<article class="gp-portrait"><p class="gp-category">'+escape(plant.category)+'</p><h3>'+escape(plant.name)+'</h3><p class="gp-latin">'+escape(plant.scientificName)+' · '+escape(plant.family)+'</p><div class="gp-gallery">'+gallery+'</div>'+
   '<h4>Standort & Wuchs</h4><dl class="gp-facts">'+fact('Licht',plant.light)+fact('Boden',plant.soil)+fact('Bodenfeuchte / Drainage',plant.water)+fact('Boden-pH',plant.ph)+fact('Größe · Richtwerte',plant.dimensions)+fact('Lebensdauer',plant.life)+fact('Laub',plant.foliage)+fact('Wachstum',plant.growth)+'</dl>'+
   '<h4>Blüte & Pflege</h4><dl class="gp-facts">'+fact('Blütezeit · Richtwert',plant.bloom)+fact('Blütenfarben',plant.colors)+fact('Pflanzen & Düngen',plant.planting)+fact('Pflege & Schnitt',plant.care)+fact('Vermehrung',plant.propagation)+fact('Winterhärte · Quellenklassifikation',plant.hardiness)+fact('Herkunft laut Quelle',plant.origin)+'</dl>'+
   neophytePanel+'<aside class="gp-safety"><h4>Giftigkeit & Umgang</h4><p>Giftigkeit laut Quelle: <strong>'+escape(list(plant.poison))+'</strong>.</p><p>Hautreaktionen laut Quelle: '+escape(list(plant.contact))+'.</p><p>'+escape(catalog.safetyNote)+'</p></aside>'+
   '<details class="gp-source"><summary>Quellen, Bildrechte & regionale Hinweise</summary><p>'+escape(catalog.climateNote)+'</p>'+plant.sources.map(s=>'<p><a href="'+safeLink(s.url)+'" target="_blank" rel="noopener noreferrer">'+escape(s.title)+'</a> · geprüft '+escape(s.checked)+'</p>').join('')+'<p>Pflegehinweise sind redaktionelle Orientierung. Bei Sorten, Obstgehölzen und örtlichen Schnittvorgaben ist die konkrete Situation maßgeblich.</p></details>'+
   (origin==='menu'?'<button type="button" class="gp-primary" data-gp-action="random">Nächste zufällige Pflanze</button>':'<button type="button" class="gp-primary" data-gp-action="back">Zurück zur Auswahl</button>')+'</article>';
  content.scrollTop=restoreScroll?state.imageScroll:0;
 }
 function resetViewerTransform(){
  viewerGesture.pointers.clear();viewerGesture.distance=0;viewerGesture.scale=1;viewerGesture.x=0;viewerGesture.y=0;
  applyViewerTransform();
 }
 function applyViewerTransform(){
  const image=content.querySelector('#gp-full-image');if(!image)return;
  image.style.transform='translate3d('+viewerGesture.x+'px,'+viewerGesture.y+'px,0) scale('+viewerGesture.scale+')';
  const output=content.querySelector('#gp-zoom-value');if(output)output.textContent=Math.round(viewerGesture.scale*100)+' %';
 }
 function setViewerScale(value){
  viewerGesture.scale=Math.max(1,Math.min(5,value));if(viewerGesture.scale===1){viewerGesture.x=0;viewerGesture.y=0;}applyViewerTransform();
 }
 function renderViewerImage(indexValue){
  const plant=byId.get(state.selected),pictures=plant?.images||[];if(!pictures.length)return;
  state.imageIndex=(Number(indexValue)+pictures.length)%pictures.length;const picture=pictures[state.imageIndex];
  const image=content.querySelector('#gp-full-image');image.src=imagePath(picture.path);image.alt=plant.name+' – botanische Aufnahme '+(state.imageIndex+1);
  content.querySelector('#gp-image-count').textContent='Bild '+(state.imageIndex+1)+' von '+pictures.length;
  content.querySelector('#gp-image-caption').innerHTML=escape(picture.caption||plant.name)+' · '+escape(picture.author)+' · <a href="'+safeLink(picture.licenseUrl)+'" target="_blank" rel="noopener noreferrer">'+escape(picture.license)+'</a> · <a href="'+safeLink(picture.source)+'" target="_blank" rel="noopener noreferrer">Bildquelle</a>';
  for(const button of content.querySelectorAll('[data-gp-action="image-prev"],[data-gp-action="image-next"]'))button.hidden=pictures.length<2;
  resetViewerTransform();
 }
 function showPlantImage(indexValue){
  const plant=byId.get(state.selected);if(!plant?.images?.length)return;
  state.imageScroll=content.scrollTop;state.view='image';dialog.classList.add('gp-image-mode');dialog.querySelector('#gp-title').textContent=plant.name;
  content.innerHTML='<section class="gp-image-viewer" aria-label="Pflanzenbild-Vollbildansicht"><div class="gp-image-stage"><img id="gp-full-image" draggable="false"></div><div class="gp-image-toolbar"><button type="button" data-gp-action="image-prev" aria-label="Vorheriges Bild">‹</button><span id="gp-image-count" role="status" aria-live="polite"></span><button type="button" data-gp-action="image-next" aria-label="Nächstes Bild">›</button><button type="button" data-gp-action="image-zoom-out" aria-label="Verkleinern">−</button><output id="gp-zoom-value">100 %</output><button type="button" data-gp-action="image-zoom-in" aria-label="Vergrößern">+</button><button type="button" class="gp-image-close" data-gp-action="back" aria-label="Vollbild schließen">×</button></div><p id="gp-image-caption" class="gp-image-caption"></p><p class="gp-image-help">Mit zwei Fingern zoomen · verschieben · Doppeltippen zum Zurücksetzen</p></section>';
  nativeFullscreen(true);renderViewerImage(indexValue);content.querySelector('.gp-image-close').focus({preventScroll:true});
 }
 function randomPlant() {
  if(!plants.length){content.innerHTML='<p class="gp-empty">Der Pflanzenkatalog konnte nicht geladen werden. Bitte die App erneut öffnen.</p>';return;}
  const pool=plants.length>1?plants.filter(p=>p.id!==lastRandom):plants;
  const sample=new Uint32Array(1);let index=0;
  if(window.crypto?.getRandomValues){window.crypto.getRandomValues(sample);index=Math.floor(sample[0]/4294967296*pool.length);}else index=Math.floor(Math.random()*pool.length);
  const plant=pool[index];lastRandom=plant.id;detail(plant,'menu');
 }
 function close() {releasePhoto();if(dialog.open)dialog.close();}
 function back() {
  if(!dialog.open)return false;
  if(state.view==='photo'){menu();dialog.querySelector('.gp-back').focus({preventScroll:true});}
  else if(state.view==='image'){const plant=byId.get(state.selected),origin=state.returnTo;releasePlantViewer();detail(plant,origin,true);}
  else if(state.view==='detail'){state.returnTo==='lexicon'?lexicon(true):menu();}
  else if(state.view==='lexicon')menu();else close();
  return true;
 }
 async function open(fromMenu=false) {
  syncLauncherVisibility();
  const menuRequest=fromMenu===true&&document.querySelector('#more-page.active')&&!document.querySelector('dialog[open]');
  if(dialog.open||(launcher.hidden&&!menuRequest))return false;
  previousFocus=document.activeElement;menu();dialog.showModal();launcher.setAttribute('aria-expanded','true');
  dialog.querySelector('.gp-back').focus({preventScroll:true});
  await storageReady;
  if(!dialog.open||state.view!=='menu')return;
  if(savedPhoto?.autoShow)showStoredPhoto();else menu();
  return true;
 }
 launcher.addEventListener('click',event=>{if(suppressClick){event.preventDefault();suppressClick=false;return;}dialog.open?back():open();});
 dialog.querySelector('.gp-close').addEventListener('click',close);
 dialog.querySelector('.gp-back').addEventListener('click',back);
 dialog.addEventListener('cancel',event=>{event.preventDefault();back();});
 dialog.addEventListener('close',()=>{releasePhoto();releasePlantViewer();launcher.setAttribute('aria-expanded','false');try{previousFocus?.focus({preventScroll:true});}catch{}});
 content.addEventListener('click',event=>{
  const galleryImage=event.target.closest('[data-gp-image]');if(galleryImage){showPlantImage(Number(galleryImage.dataset.gpImage));return;}
  const plant=event.target.closest('[data-gp-plant]');if(plant){state.scroll=content.scrollTop;detail(byId.get(plant.dataset.gpPlant),'lexicon');return;}
  const action=event.target.closest('[data-gp-action]')?.dataset.gpAction;
  if(action==='choose-photo')choosePhoto();
  if(action==='stored-photo')showStoredPhoto();
  if(action==='save-photo')savePhoto();
  if(action==='photo-options'){const options=content.querySelector('.gp-photo-options');if(options){options.hidden=!options.hidden;if(!options.hidden)options.querySelector('input')?.focus();}}
  if(action==='random')randomPlant();
  if(action==='lexicon'){state.neophyte='';lexicon();}
  if(action==='neophytes'){state.query='';state.category='';state.light='';state.neophyte='all';state.limit=40;lexicon();}
  if(action==='back')back();
  if(action==='image-prev')renderViewerImage(state.imageIndex-1);
  if(action==='image-next')renderViewerImage(state.imageIndex+1);
  if(action==='image-zoom-in')setViewerScale(viewerGesture.scale+.5);
  if(action==='image-zoom-out')setViewerScale(viewerGesture.scale-.5);
  if(action==='more'){state.limit+=40;renderResults();}
  if(action==='reset-position'){position=null;try{localStorage.removeItem(POSITION_KEY);}catch{}layout();}
 });
 content.addEventListener('input',event=>{if(event.target.id==='gp-query'){state.query=event.target.value;state.limit=40;renderResults();}});
 content.addEventListener('change',event=>{if(event.target.id==='gp-category'||event.target.id==='gp-light'||event.target.id==='gp-neophyte'){state.category=content.querySelector('#gp-category').value;state.light=content.querySelector('#gp-light').value;state.neophyte=content.querySelector('#gp-neophyte').value;state.limit=40;renderResults();}});
 content.addEventListener('pointerdown',event=>{
  if(state.view!=='image'||event.target.id!=='gp-full-image')return;event.preventDefault();
  try{event.target.setPointerCapture(event.pointerId);}catch{}
  viewerGesture.pointers.set(event.pointerId,{x:event.clientX,y:event.clientY});
  if(viewerGesture.pointers.size===1)viewerGesture.swipe=viewerGesture.scale===1?{id:event.pointerId,x:event.clientX,y:event.clientY,at:performance.now()}:null;
  if(viewerGesture.pointers.size===2){viewerGesture.swipe=null;const points=[...viewerGesture.pointers.values()];viewerGesture.distance=Math.hypot(points[0].x-points[1].x,points[0].y-points[1].y);}
 });
 content.addEventListener('pointermove',event=>{
  if(state.view!=='image'||!viewerGesture.pointers.has(event.pointerId))return;event.preventDefault();
  const previous=viewerGesture.pointers.get(event.pointerId);viewerGesture.pointers.set(event.pointerId,{x:event.clientX,y:event.clientY});
  if(viewerGesture.pointers.size===2){const points=[...viewerGesture.pointers.values()],distance=Math.hypot(points[0].x-points[1].x,points[0].y-points[1].y);if(viewerGesture.distance)setViewerScale(viewerGesture.scale*distance/viewerGesture.distance);viewerGesture.distance=distance;}
  else if(viewerGesture.scale>1){viewerGesture.x+=event.clientX-previous.x;viewerGesture.y+=event.clientY-previous.y;applyViewerTransform();}
 });
 const endViewerPointer=event=>{if(!viewerGesture.pointers.has(event.pointerId))return;const swipe=viewerGesture.swipe;viewerGesture.pointers.delete(event.pointerId);viewerGesture.distance=0;viewerGesture.swipe=null;if(event.type==='pointercancel'||!swipe||swipe.id!==event.pointerId||viewerGesture.scale!==1||viewerGesture.pointers.size)return;const dx=event.clientX-swipe.x,dy=event.clientY-swipe.y,threshold=Math.max(40,Math.min(90,content.clientWidth*.14));if(Math.abs(dx)>=threshold&&Math.abs(dx)>Math.abs(dy)*1.7&&performance.now()-swipe.at<1200&&(byId.get(state.selected)?.images?.length||0)>1)renderViewerImage(state.imageIndex+(dx<0?1:-1));};
 content.addEventListener('pointerup',endViewerPointer);content.addEventListener('pointercancel',endViewerPointer);
 content.addEventListener('dblclick',event=>{if(state.view==='image'&&event.target.id==='gp-full-image')setViewerScale(viewerGesture.scale>1?1:2.5);});
 content.addEventListener('wheel',event=>{if(state.view!=='image'||!event.target.closest('.gp-image-stage'))return;event.preventDefault();setViewerScale(viewerGesture.scale+(event.deltaY<0?.25:-.25));},{passive:false});
 const previousBack=window.handleAndroidBack;window.handleAndroidBack=function(){return back()||(typeof previousBack==='function'?previousBack.apply(this,arguments):false);};
 let position=null,drag=null,suppressClick=false;
 try{const saved=JSON.parse(localStorage.getItem(POSITION_KEY));if(saved&&Number.isFinite(saved.x)&&Number.isFinite(saved.y))position={x:Math.max(0,Math.min(1,saved.x)),y:Math.max(0,Math.min(1,saved.y))};}catch{}
 function bounds(ignoreNotices=false){
  const view=window.visualViewport,ox=view?.offsetLeft||0,oy=view?.offsetTop||0,width=view?.width||innerWidth,height=view?.height||innerHeight;
  let maxY=oy+height-launcher.offsetHeight-96;
  for(const notice of (ignoreNotices?[]:document.querySelectorAll('#mobile-toast,#undo-toast,.undo-toast'))){const r=notice.getBoundingClientRect();if(!notice.hidden&&r.width&&r.height&&getComputedStyle(notice).display!=='none'&&getComputedStyle(notice).visibility!=='hidden')maxY=Math.min(maxY,Math.min(r.top,notice.offsetTop||r.top)-launcher.offsetHeight-12);}
  return {minX:ox+8,minY:oy+8,maxX:Math.max(ox+8,ox+width-launcher.offsetWidth-8),maxY:Math.max(oy+8,maxY)};
 }
 function move(x,y){const b=bounds();launcher.style.left=Math.max(b.minX,Math.min(b.maxX,x))+'px';launcher.style.top=Math.max(b.minY,Math.min(b.maxY,y))+'px';}
 function remember(){const b=bounds(true);position={x:(parseFloat(launcher.style.left)-b.minX)/Math.max(1,b.maxX-b.minX),y:(parseFloat(launcher.style.top)-b.minY)/Math.max(1,b.maxY-b.minY)};try{localStorage.setItem(POSITION_KEY,JSON.stringify(position));}catch{}}
 function layout(){if(drag)return;const b=bounds(true);move(position?b.minX+position.x*(b.maxX-b.minX):b.maxX,position?b.minY+position.y*(b.maxY-b.minY):b.maxY);avoidControls();}
 function avoidControls(){const b=bounds();
  if(launcher.hidden||dialog.open)return;
  const controls=[...document.querySelectorAll('.page.active button,.page.active input,.page.active select,.page.active textarea,.page.active [role="button"],.view.active button,.bottom button')].filter(n=>n!==launcher&&!n.closest('dialog')&&n.getClientRects().length).map(n=>n.getBoundingClientRect()),w=launcher.offsetWidth,h=launcher.offsetHeight;
  const free=(x,y)=>controls.every(r=>x+w+4<=r.left||x-4>=r.right||y+h+4<=r.top||y-4>=r.bottom);
  const r=launcher.getBoundingClientRect();if(free(r.left,r.top))return;
  const candidates=[[b.minX,b.minY],[b.maxX,b.minY],[b.minX,b.maxY]];for(let y=b.minY;y<=b.maxY;y+=24)for(const x of [r.left,b.minX,b.maxX])candidates.push([x,y]);candidates.sort((a,b)=>(a[0]-r.left)**2+(a[1]-r.top)**2-(b[0]-r.left)**2-(b[1]-r.top)**2);const next=candidates.find(([x,y])=>free(x,y));if(next)move(...next);
 }
 launcher.addEventListener('pointerdown',event=>{if(event.button!==0)return;const r=launcher.getBoundingClientRect();drag={id:event.pointerId,x:event.clientX,y:event.clientY,left:r.left,top:r.top,moved:false};try{launcher.setPointerCapture(event.pointerId);}catch{}});
 launcher.addEventListener('pointermove',event=>{if(!drag||event.pointerId!==drag.id)return;const dx=event.clientX-drag.x,dy=event.clientY-drag.y;if(Math.hypot(dx,dy)>7)drag.moved=true;if(drag.moved){event.preventDefault();launcher.classList.add('gp-dragging');move(drag.left+dx,drag.top+dy);}});
 launcher.addEventListener('pointerup',event=>{if(!drag||event.pointerId!==drag.id)return;suppressClick=drag.moved;const moved=drag.moved;drag=null;launcher.classList.remove('gp-dragging');if(moved){avoidControls();remember();}});
 launcher.addEventListener('pointercancel',()=>{drag=null;suppressClick=false;launcher.classList.remove('gp-dragging');layout();});
 launcher.addEventListener('keydown',event=>{const deltas={ArrowLeft:[-12,0],ArrowRight:[12,0],ArrowUp:[0,-12],ArrowDown:[0,12]};if(event.key==='Home'){event.preventDefault();position=null;try{localStorage.removeItem(POSITION_KEY);}catch{}layout();return;}if(deltas[event.key]){event.preventDefault();const r=launcher.getBoundingClientRect(),d=deltas[event.key];move(r.left+d[0],r.top+d[1]);avoidControls();remember();}});
 document.addEventListener('transitionend',e=>{if(e.target.matches?.('#mobile-toast,#undo-toast,.undo-toast'))layout();},true);
 addEventListener('resize',layout);window.visualViewport?.addEventListener('resize',layout);let pendingLayout=false;addEventListener('scroll',()=>{if(pendingLayout)return;pendingLayout=true;requestAnimationFrame(()=>{pendingLayout=false;if(drag||launcher.hidden||dialog.open)return;const r=launcher.getBoundingClientRect();move(r.left,r.top);avoidControls();});},{passive:true,capture:true});
 // Android names the Today/dashboard page #home; the web/PWA shell names it
 // #view-dashboard. The launcher stays on either real dashboard surface and
 // never enters an unrelated modal/editor.
 function launcherAllowed(){
  const androidPage=document.querySelector('.page.active');
  const webView=document.querySelector('.view.active');
  const foreignModal=[...document.querySelectorAll('dialog[open]')].some(item=>item!==dialog);
  const onDashboard=androidPage?.id==='home'||webView?.id==='view-dashboard';
  return Boolean(onDashboard)&&!dialog.open&&!foreignModal&&!document.querySelector('#workday-timer.has-active-session');
 }
 function syncLauncherVisibility(){
  const allowed=launcherAllowed(),wasHidden=launcher.hidden;
  launcher.hidden=!allowed;launcher.tabIndex=allowed?0:-1;
  launcher.setAttribute('aria-hidden',String(!allowed));
  if(allowed&&wasHidden)requestAnimationFrame(layout);
  return allowed;
 }
 function host(){
  const target=dialog.open?dialog:document.body;
  if(launcher.parentNode!==target)target.appendChild(launcher);
  if(syncLauncherVisibility())layout();
 }
 const observer=new MutationObserver(records=>{let changed=false;for(const r of records){
   if(r.type==='childList'&&r.target.nodeType===1&&r.target.closest('#workday-recent'))changed=true;
   if(r.attributeName==='open'&&r.target.tagName==='DIALOG')changed=true;
   if(r.attributeName==='class'&&(r.target.classList?.contains('page')||r.target.classList?.contains('view')))changed=true;
   if(r.attributeName==='class'&&r.target.id==='workday-timer')changed=true;
   if(r.target.nodeType===1&&r.target.closest('#mobile-toast,#undo-toast,.undo-toast'))changed=true;
   if([...r.addedNodes,...r.removedNodes].some(n=>n.nodeType===1&&n.matches('#mobile-toast,#undo-toast,.undo-toast')))changed=true;
  }if(changed)host();});
 observer.observe(document.body,{subtree:true,childList:true,attributes:true,attributeFilter:['open','class','hidden','style']});host();
 window.Gartenpirat=Object.freeze({open,close,back,plantCount:plants.length,neophyteCount:plants.filter(plant=>plant.neophyte?.isNeophyte).length,unionNeophyteCount:plants.filter(plant=>plant.neophyte?.unionConcern).length});
})();

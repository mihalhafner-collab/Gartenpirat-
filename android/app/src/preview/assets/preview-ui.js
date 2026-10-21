/* Preview-only isolation. The daily surface is shared with production. */
(() => {
  'use strict';
  const get=id=>document.getElementById(id);
  function init(){
    if(document.body.dataset.fieldReady!=='true'){setTimeout(init,40);return;}
    document.body.classList.add('ui-preview');
    const isolation=document.createElement('section');isolation.className='card preview-isolation';
    isolation.innerHTML='<h3>Separate Test-App</h3><p>Diese App speichert nur ihre eigenen Testdaten. Drive, Google Kalender und ChatGPT-Verbindungen sind hier gesperrt.</p>';
    get('settings-page').querySelector('.page-head').after(isolation);
    for(const id of ['settings-drive','settings-planning','settings-connection']){const button=get(id);button.disabled=true;button.textContent='In der Test-App gesperrt';}
    const title=document.createElement('p');title.className='sub';title.textContent='Haus & Garten Test · Eigene Testdaten';document.querySelector('.field-heading').prepend(title);
    document.body.dataset.previewReady='true';
  }
  document.addEventListener('DOMContentLoaded',()=>setTimeout(init,320),{once:true});
})();

/* Daily overview and explicit capture. Original forms own validation and writes. */
(() => {
  'use strict';
  const get=id=>document.getElementById(id);
  function init(){
    if(!get('settings-page')||!window.StatusUI||!get('entry')?.querySelector('.simple-primary')||document.querySelector('dialog[open]')){setTimeout(init,40);return;}
    const style=document.createElement('link');style.rel='stylesheet';style.href='field-ui.css';style.onload=()=>{document.body.dataset.fieldReady='true';};document.head.append(style);
    document.body.classList.add('field-ui');
    const gardenKey='hausgarten.ui.garden_shortcut.v1';let gardenShortcut=false;
    try{gardenShortcut=localStorage.getItem(gardenKey)==='true';}catch(_){/* A quiet overview is the default. */}
    const gardenSetting=document.createElement('label');gardenSetting.className='check';
    gardenSetting.innerHTML='<input id="setting-garden-shortcut" type="checkbox">Gartenpirat-Schnellzugriff auf Heute anzeigen';
    get('settings-operation-title').parentElement.append(gardenSetting);
    const gardenCheckbox=get('setting-garden-shortcut');gardenCheckbox.checked=gardenShortcut;document.body.classList.toggle('field-garden-shortcut',gardenShortcut);
    gardenCheckbox.onchange=()=>{
      try{localStorage.setItem(gardenKey,String(gardenCheckbox.checked));gardenShortcut=gardenCheckbox.checked;document.body.classList.toggle('field-garden-shortcut',gardenShortcut);}
      catch(_){gardenCheckbox.checked=gardenShortcut;window.showUndoToast?.('Einstellung nicht gespeichert. Der bisherige Schnellzugriff bleibt erhalten.');}
    };
    get('customers').querySelector('h2').textContent='Kunden & Gärten';
    const menuButton=document.querySelector('#more-page .drawer-open');
    if(menuButton){menuButton.textContent='☰';menuButton.setAttribute('aria-label','Seitenmenü öffnen');get('more-page').querySelector('.page-head').append(menuButton);}
    // Original record form is moved, never copied. Drafts and listeners survive.
    const form=get('entry'),entry=get('work-entry'),placeholder=document.createComment('work-entry origin');entry.before(placeholder);
    const sheet=document.createElement('dialog');sheet.id='field-quick-entry';sheet.setAttribute('aria-labelledby','field-quick-title');
    sheet.innerHTML='<header class="field-sheet-heading"><h2 id="field-quick-title" tabindex="-1">Arbeit erfassen</h2><p>Zeit, Leistung & Notiz</p></header><div class="field-sheet-content"></div>';
    document.body.append(sheet);
    const notes=form.elements.notes,label=notes.previousElementSibling;
    if(label?.tagName==='LABEL')label.textContent='Notiz · optional';
    notes.rows=2;get('activity').rows=2;get('entry-hours').placeholder='z. B. 2,5';
    const extras=form.querySelector('details[data-section="order"]');
    if(extras){
      extras.querySelector('summary').textContent='Auftrag, Abrechnung & Ort';
      const billing=get('entry-billing-customer')?.closest('.billing-field');if(billing)extras.querySelector('.more-fields').append(billing);
      const add=get('work-entry-add-customer');if(add)extras.querySelector('.more-fields').append(add);
    }
    const times=form.querySelector('details[data-section="time"]');
    const timeGrid=get('start').closest('.form-grid');
    timeGrid.classList.add('simple-times');
    const dateGrid=form.querySelector('.simple-date-hours');
    dateGrid.after(timeGrid);
    const duration=get('entry-hours').parentElement;
    if(times){times.querySelector('summary').textContent='Dauer ohne Uhrzeiten und Pause';times.querySelector('.more-fields').prepend(duration);const modes=form.querySelector('.design-time-mode');if(modes)times.querySelector('.more-fields').append(modes);}
    const photo=form.querySelector('details[data-section="notes"]');
    if(photo){photo.querySelector('summary').textContent='Fotos, Notizen & Spracheingabe';photo.querySelector('.more-fields').prepend(get('speak'));}
    // A different invoice recipient is important context even with extras closed.
    const billingContext=document.createElement('p');billingContext.id='field-billing-context';billingContext.className='field-context';billingContext.hidden=true;
    get('customer').closest('.select-search-wrap').after(billingContext);
    const discard=get('new-work-cancel');if(discard){discard.textContent='Verwerfen';discard.classList.add('field-danger');form.querySelector('button[type="submit"]').closest('.actions').prepend(discard);}
    const later=document.createElement('button');later.type='button';later.className='secondary';later.id='field-entry-later';later.textContent='‹';later.setAttribute('aria-label','Zurück · Entwurf behalten');
    sheet.querySelector('.field-sheet-heading').prepend(later);
    const previousBack=window.handleAndroidBack;
    let moving=false;
    function closeSheet(){
      if(moving)return;moving=true;
      try{if(sheet.open)sheet.close();if(entry.parentNode!==placeholder.parentNode)placeholder.after(entry);}
      finally{moving=false;}
    }
    function showSheet(){
      if(currentPage!=='work-entry'||moving)return;moving=true;
      try{
        const content=sheet.querySelector('.field-sheet-content');if(entry.parentNode!==content)content.append(entry);
        if(!sheet.open){sheet.showModal();sheet.scrollTop=0;get('field-quick-title').focus({preventScroll:true});}
      }finally{moving=false;}
    }
    function back(){
      if(!sheet.open)return previousBack();
      const nested=[...document.querySelectorAll('dialog[open]')].filter(d=>d!==sheet);
      if(nested.length){
        const focused=document.activeElement?.closest('dialog[open]'),dialog=focused&&focused!==sheet?focused:nested.at(-1);
        const event=new Event('cancel',{cancelable:true});if(dialog.dispatchEvent(event))dialog.close();return true;
      }
      // Dismissing capture leaves the draft intact, including its optional fields.
      for(const details of entry.querySelectorAll('details[open]'))details.open=false;
      closeSheet();const result=previousBack();if(currentPage==='work-entry')showSheet();return result;
    }
    later.onclick=back;sheet.addEventListener('cancel',event=>{event.preventDefault();back();});window.handleAndroidBack=back;
    form.addEventListener('invalid',event=>{
      for(let node=event.target.parentElement;node&&node!==form;node=node.parentElement)if(node.matches('details'))node.open=true;
    },true);
    function projection(){
      if(moving)return;
      document.body.classList.toggle('field-home',currentPage==='home');
      const completing=entry.classList.contains('timer-completion-page');
      if(times&&get('entry-hours').value&&!get('start').value&&!get('end').value)times.open=true;
      get('field-quick-title').textContent=completing?'Arbeit abschließen':'Arbeit erfassen';
      later.hidden=completing;
      const customer=get('customer').value,billing=get('entry-billing-customer');
      billingContext.hidden=!customer||!billing?.value||billing.value===customer;
      billingContext.textContent=billingContext.hidden?'':'Rechnung an: '+customerName(billing.value);
      if(currentPage==='work-entry')showSheet();else closeSheet();
    }
    form.addEventListener('change',projection);form.addEventListener('reset',()=>setTimeout(projection,0));
    new MutationObserver(projection).observe(entry,{attributes:true,attributeFilter:['class']});
    const open=window.openPage;window.openPage=function(...args){const result=open(...args);projection();return result;};
    const render=window.render;window.render=function(...args){const result=render(...args);projection();return result;};
    const update=window.HGNavigation.update;window.HGNavigation=Object.freeze({update(){update();projection();}});
    projection();
  }
  document.addEventListener('DOMContentLoaded',()=>setTimeout(init,280),{once:true});
})();

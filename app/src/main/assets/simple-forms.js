/* Shared presentation only. Existing forms, draft owners and savers remain authoritative. */
(() => {
 'use strict';
 const roots='form,#order-management-card,#edit-worklog-card,.invoice-studio',done=new WeakSet(),headers=new Map();
 const byId=id=>document.getElementById(id);
 const mark=(node,name)=>{if(node&&!node.classList.contains(name))node.classList.add(name);};
 const localDay=(offset=0)=>{const today=new Intl.DateTimeFormat('sv-SE',{timeZone:'Europe/Vienna',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date()),d=new Date(today+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+offset);return d.toISOString().slice(0,10);};
 function text(node,value){if(node&&node.textContent!==value)node.textContent=value;}
 function extras(root,title='Weitere Angaben'){
  const details=document.createElement('details');details.className='more simple-options';
  const summary=document.createElement('summary');summary.textContent=title;details.append(summary);root.append(details);return details;
 }
 function moveField(root,details,selector){
  const field=root.querySelector(selector);if(!field||field.required||field.closest('details')||field.type==='hidden')return;
  const block=field.closest('.revier-field')||field.closest('label');
  if(block&&root.contains(block)){details.append(block);return;}
  const wrap=field.closest('.select-search-wrap')||field,previous=wrap.previousElementSibling;
  if(previous?.tagName==='LABEL')details.append(previous);details.append(wrap);
 }
 function group(root,selectors,title){
  const details=extras(root,title);for(const selector of selectors)moveField(root,details,selector);
  if(details.children.length===1){details.remove();return;}
  const bar=root.querySelector('.simple-actions,.revier-sticky-actions,.actions');if(bar?.parentElement===root)bar.before(details);
  const summary=details.querySelector('summary');
  const filled=()=>[...details.querySelectorAll('input,select,textarea')].filter(e=>e.type==='checkbox'?e.checked:e.type!=='hidden'&&e.name!=='area_unit'&&e.value.trim()!==''&&e.value!=='0').length;
  const update=()=>{const count=filled();text(summary,(title||'Weitere Angaben')+(count?' · '+count+' ausgefüllt':''));};
  root.addEventListener('input',update);root.addEventListener('change',update);update();details.open=filled()>0;
 }
 const optional={
  property:['district','area_decimal','area_unit','access_notes','notes'],
  inquiry:['wish_from','wish_until'],care:['season_start','season_end','notes'],
  visit:['notes','remaining_minutes'],material:['purchase_price','billing_price','notes'],
  equipment:['maintenance_due','notes'],equipment_use:['notes'],inspection:['notes'],
  maintenance_plan:[],maintenance_done:['notes'],maintenance_adjust:[]
 };
 function actionBar(root,selector,discardSelector,laterSelector){
  const bar=root.querySelector(selector);if(!bar)return;
  mark(bar,'simple-actions');
  const discard=discardSelector&&document.querySelector(discardSelector);
  if(discard&&(!discard.closest('form')||discard.closest('form')===root)){if(bar.firstElementChild!==discard)bar.prepend(discard);mark(discard,'simple-discard');text(discard,'Verwerfen');}
  const later=laterSelector&&document.querySelector(laterSelector);if(later)mark(later,'simple-later');
  for(const save of bar.querySelectorAll('[type=submit],[data-work-window-save],[data-order-window-save]'))mark(save,'simple-save');
 }
 function change(field,value){
  if(!field.isConnected||field.disabled||field.readOnly||field.closest('fieldset[disabled]'))return;
  field.value=value;field.dispatchEvent(new Event('input',{bubbles:true}));field.dispatchEvent(new Event('change',{bubbles:true}));
 }
 function choices(field,values){
  if(!field||field.dataset.simpleChoices)return;field.dataset.simpleChoices='true';
  const row=document.createElement('div');row.className='simple-choices';row.setAttribute('role','group');row.setAttribute('aria-label',(field.labels?.[0]?.textContent||'Wert').trim()+' auswählen');
  for(const [value,label]of values){const button=document.createElement('button');button.type='button';button.className='secondary';button.textContent=label;button.addEventListener('click',()=>change(field,typeof value==='function'?value():value));row.append(button);}
  field.after(row);field._simpleChoices=row;syncChoices(field);
 }
 function syncChoices(field){if(!field._simpleChoices)return;for(const button of field._simpleChoices.children){const disabled=field.disabled||field.readOnly||!!field.closest('fieldset[disabled]');if(button.disabled!==disabled)button.disabled=disabled;}}
 function restoreHeaders(){for(const [page,entry]of headers)if(!entry.form.isConnected){if(entry.button.isConnected)entry.button.replaceWith(entry.previous);headers.delete(page);}}
 function headerBack(form,selector){
  const button=form.querySelector(selector),page=form.closest('.page'),previous=page?.querySelector('.page-head button');if(!button||!previous)return;
  previous.replaceWith(button);button.classList.add('simple-back');button.setAttribute('aria-label','Zurück · Entwurf behalten');text(button,'‹');headers.set(page,{form,button,previous});
 }
 function enhanceForm(root){
  if(!done.has(root)){
   done.add(root);root.classList.add('simple-form');
   if(root.id==='revier-form'){
    const kind=root.dataset.editorKind;
    group(root,(optional[kind]||[]).map(name=>'[name="'+name+'"]'));
    for(const fieldset of [...root.querySelectorAll(':scope > fieldset')]){
     if(fieldset.querySelector('[required]'))continue;
     const legend=fieldset.querySelector('legend');if(!legend)continue;
     const details=extras(root,legend.textContent);fieldset.before(details);details.append(fieldset);legend.classList.add('sr-only');
     details.open=!!fieldset.querySelector('[data-equipment-condition=defective],input:checked')||[...fieldset.querySelectorAll('input:not([type=checkbox]),textarea')].some(f=>f.value.trim()!=='');
    }
    for(const name of ['planned_minutes','duration_minutes'])choices(root.elements[name],[['30','30 Min.'],['60','1 Std.'],['120','2 Std.']]);
    const activity=['visit','care'].includes(kind)&&root.elements.title;
    if(activity)choices(activity,[['Hecke schneiden','Hecke'],['Rasen mähen','Rasen'],['Beete pflegen','Beete']]);
   }
   if(root.id==='customer-form'){
    root.querySelector('.actions [data-open=customers]')?.remove();
    for(const section of [...root.querySelectorAll(':scope > fieldset')]){
     if(section.querySelector('[required]')||section.querySelector('[name=phone]'))continue;
     const legend=section.querySelector('legend');if(!legend)continue;
     const details=extras(root,legend.textContent);section.before(details);details.append(section);legend.classList.add('sr-only');
     details.open=section.querySelector('[name=billing_mode]')?.value==='via_customer';
    }
   }
   if(root.id==='business-settings-form')group(root,['business_bank','business_iban','business_bic','business_vat_id','default_payment_days','default_payment_terms','offer_binding_text','offer_nonbinding_text','offer_time_material_text','business_legal_info'].map(n=>'[name="'+n+'"]'),'Zahlung und weitere Vorgaben');
   if(root.id==='order-management-card')group(root,['#order-edit-description','#order-edit-location','#order-edit-visible'],'Weitere Auftragsangaben');
   if(root.id==='offer-form'){
    const tools=root.querySelector('details');if(tools)for(const id of ['offer-chat','offer-speech']){const button=root.querySelector('#'+id);if(button)tools.append(button);}
   }
   if(['entry','revier-form','appointment-form','order-management-card','edit-worklog-card'].includes(root.id)){
    for(const input of root.querySelectorAll('input[type=date]'))choices(input,[[()=>localDay(),'Heute'],[()=>localDay(1),'Morgen']]);
   }
  }
  if(root.id==='entry')actionBar(root,':scope > .actions','#new-work-cancel','#field-entry-later');
  if(root.id==='customer-form')actionBar(root,'.actions','#customer-discard');
  if(root.id==='revier-form')actionBar(root,'.revier-sticky-actions','[data-revier-editor-discard]','[data-revier-editor-later]');
  if(root.id==='appointment-form')actionBar(root,'.planning-save-actions','#planning-discard','#planning-back');
  if(root.id==='offer-form')actionBar(root,':scope > .customer-hub-actions','#offer-discard');
  if(root.id==='invoice-draft-form')actionBar(root,'.dialog-actions','#invoice-draft-form [data-cancel]');
  if(['local-payment-form','local-invoice-issue-form'].includes(root.id))actionBar(root,':scope > .local-payment-actions');
  if(root.id==='catalog-form')actionBar(root,':scope > .customer-hub-actions');
  if(['business-settings-form','customer-garden-form'].includes(root.id)){
   let bar=root.querySelector(':scope > .simple-actions,:scope > .customer-hub-actions');
   if(!bar){bar=document.createElement('div');bar.className='simple-actions';for(const button of [...root.querySelectorAll(':scope > button')])bar.append(button);root.append(bar);}
   mark(bar,'simple-actions');
  }
  if(root.id==='revier-form')headerBack(root,'[data-revier-editor-later]');
  // Planning's original header Back already retains the editor and returns to
  // its list. Remove only its duplicate footer control after initialization;
  // never move an ID out of a subtree which that editor later replaces.
  if(root.id==='appointment-form')root.querySelector('#planning-back')?.remove();
  for(const input of root.querySelectorAll('input,select,textarea'))syncChoices(input);
 }
 function enhance(scope=document){
  restoreHeaders();
  if(scope.matches?.(roots))enhanceForm(scope);
  scope.querySelectorAll?.(roots).forEach(enhanceForm);
  for(const button of scope.querySelectorAll?.('.page-head .back,[data-revier-back]')||[]){if(button.getAttribute('aria-label')!=='Zurück')button.setAttribute('aria-label','Zurück');mark(button,'simple-back');}
  for(const bar of scope.querySelectorAll?.('.worklog-window-actions,.order-window-actions,.invoice-draft-dialog>form>footer,.invoice-draft-footer')||[])mark(bar,'simple-actions');
 }
 function reveal(field){for(let n=field?.parentElement;n;n=n.parentElement)if(n.tagName==='DETAILS')n.open=true;}
 function install(){
  if(document.body.dataset.revierReady!=='true'){document.addEventListener('hg:revier-ready',install,{once:true});return;}
  document.body.classList.add('simple-ui');let scheduled=false;const pending=new Set();
  const observer=new MutationObserver(records=>{
   for(const record of records){
    if(record.type==='attributes'){
     if(record.attributeName==='aria-invalid'&&record.target.getAttribute('aria-invalid')==='true')reveal(record.target);
     if(record.target.matches('input,select,textarea'))syncChoices(record.target);
     if(record.target.matches('fieldset'))record.target.querySelectorAll('input,select,textarea').forEach(syncChoices);
     continue;
    }
    const form=record.target.closest?.(roots);if(form)pending.add(form);if(record.removedNodes.length)pending.add(record.target);
    for(const node of record.addedNodes)if(node.nodeType===1)pending.add(node);
   }
   if(!pending.size||scheduled)return;scheduled=true;queueMicrotask(()=>{scheduled=false;observer.disconnect();try{for(const node of pending)if(node.isConnected)enhance(node);pending.clear();}finally{observe();}});
  });
  function observe(){observer.observe(document.body,{childList:true,subtree:true,attributes:true,attributeFilter:['disabled','readonly','aria-invalid']});}
  enhance();observe();
  document.addEventListener('invalid',e=>reveal(e.target),true);
  document.addEventListener('click',event=>{
   const field=event.target.closest('input[type=date],input[type=time],input[type=datetime-local]');
   if(field&&!field.disabled&&!field.readOnly&&event.isTrusted)try{field.showPicker?.();}catch(_){/* Native input remains usable without showPicker support. */}
  });
  document.body.dataset.simpleReady='true';
 }
 window.HGSimpleForms=Object.freeze({enhance,reveal});
 document.addEventListener('DOMContentLoaded',install,{once:true});
})();

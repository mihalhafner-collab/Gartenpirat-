/* Personal, device-local overview. Never modifies worklogs or invoices. */
(() => {
 'use strict';
 function install(){
 const key='hausgarten.overview.personal-paid.v1';
 function read(){
  const value=JSON.parse(localStorage.getItem(key)||'[]');
  if(!Array.isArray(value)||!value.every(id=>typeof id==='string'))throw Error('Invalid payment overview');
  return new Set(value);
 }
 function refresh(){
  let paid;try{paid=read();}catch(_){return;}
  const records=new Map(worklogs().filter(row=>row.id).map(row=>[row.id,row]));
  for(const row of document.querySelectorAll('#worklog-list .worklog-entry, #workday-recent [data-edit-worklog]')){
   const id=row.dataset.editWorklog;if(!records.has(id))continue;
   let wrapper=row.parentElement;
   if(!wrapper.classList.contains('personal-payment-record')){
    wrapper=document.createElement('div');wrapper.className='personal-payment-record';row.before(wrapper);wrapper.append(row);
    const button=document.createElement('button');button.type='button';button.className='personal-payment-toggle';button.dataset.personalPaid=id;
    wrapper.append(button);
   }
   const marked=paid.has(id),button=wrapper.querySelector('[data-personal-paid]');
   button.textContent=marked?'b.b. · bezahlt':'b.b.';button.setAttribute('aria-pressed',String(marked));
   button.setAttribute('aria-label',(marked?'Bezahlmarkierung entfernen: ':'Persönlich als bezahlt markieren: ')+records.get(id).activity);
   button.title='Persönliche Übersicht auf diesem Gerät';
   let label=row.querySelector('.personal-payment-label');
   if(marked&&!label){label=document.createElement('small');label.className='personal-payment-label';label.textContent='bezahlt';row.append(label);}
   if(!marked)label?.remove();
  }
 }
 document.addEventListener('click',event=>{
  const button=event.target.closest('[data-personal-paid]');if(!button)return;
  event.preventDefault();event.stopImmediatePropagation();
  const id=button.dataset.personalPaid;if(!worklogs().some(row=>row.id===id))return;
  try{const paid=read();if(paid.has(id))paid.delete(id);else paid.add(id);localStorage.setItem(key,JSON.stringify([...paid]));refresh();}
  catch(_){window.showUndoToast?.('Bezahlmarkierung nicht gespeichert. Die bisherige Übersicht bleibt erhalten.');}
 },true);
 const render=window.render;window.render=function(...args){const result=render.apply(this,args);refresh();return result;};
 addEventListener('storage',event=>{if(event.key===key)refresh();});
 refresh();
 }
 document.addEventListener('DOMContentLoaded',()=>setTimeout(install,0),{once:true});
})();

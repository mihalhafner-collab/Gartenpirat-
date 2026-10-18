/* Existing device UI consumes the same owner preferences and share handoff. */
(()=>{
 'use strict';
 const get=id=>document.getElementById(id);
 function defaults(){
  if(typeof AndroidData?.getOwnerSettings!=='function')return;
  try{const s=JSON.parse(AndroidData.getOwnerSettings());if(!get('activity').value&&s.default_activity)get('activity').value=s.default_activity;if(!get('entry-location')?.value&&s.default_location&&get('entry-location'))get('entry-location').value=s.default_location;}catch(_){}
 }
 function handoff(){
  if(typeof AndroidData?.getInvoiceShareHandoff!=='function')return;
  let value;try{value=JSON.parse(AndroidData.getInvoiceShareHandoff());}catch(_){return;}
  let card=get('invoice-share-handoff');if(!value.invoice_id){card?.remove();return;}
  if(!card){card=document.createElement('section');card.id='invoice-share-handoff';card.className='card';card.innerHTML='<h3>Rechnung zum Teilen bereit</h3><p>Empfänger und Versand bestätigst du in der gewählten Versand-App.</p><button type="button" class="secondary">Versand-App öffnen</button>';get('invoice-list').before(card);card.querySelector('button').onclick=()=>{const result=AndroidData.sharePreparedInvoice();if(String(result).startsWith('ERROR:'))window.showUndoToast?.(String(result).slice(6));};}
  const invoice=merged('invoices','invoice').find(row=>row.id===value.invoice_id);card.querySelector('p').textContent=[invoice?.invoice_number,invoice?customerName(invoice.customer_id):'Vorbereiteten Beleg prüfen','Empfänger und Versand bestätigst du in der Versand-App.'].filter(Boolean).join(' · ');
 }
 document.addEventListener('DOMContentLoaded',()=>{
  get('entry')?.addEventListener('reset',()=>setTimeout(defaults,0));
  const render=window.render;window.render=function(...args){const r=render.apply(this,args);if(currentPage==='invoices')handoff();return r;};
  if(!localStorage.getItem('hausgarten.entry.draft.v1'))defaults();
 },{once:true});
})();

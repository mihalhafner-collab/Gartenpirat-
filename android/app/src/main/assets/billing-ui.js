/* Recipient relationship UI. No parallel store; all writes use existing savers. */
(() => {
  'use strict';
  const get=id=>document.getElementById(id), recipient=w=>w.billing_customer_id||w.customer_id;
  const named=id=>customers().find(c=>c.id===id)?.name||'Zuordnung prüfen';
  function standard(id,orderId=''){
    const c=customers().find(c=>c.id===id),o=orders().find(o=>o.id===orderId&&o.customer_id===id);
    return o?.billing_customer_id||(c?.billing_mode==='via_customer'?c.default_billing_customer_id:id)||'';
  }
  function fill(select,value,exclude='',optional=false){
    select.innerHTML=(optional?'<option value="">Kundenstandard verwenden</option>':'<option value="">Rechnungsempfänger auswählen</option>')+
      customers().filter(c=>c.id!==exclude).sort((a,b)=>String(a.name).localeCompare(String(b.name),'de')).map(c=>`<option value="${esc(c.id)}">${esc(c.name)}${c.active===0?' · Archiviert':''}</option>`).join('');
    select.value=value||'';if(value&&!select.value){const o=document.createElement('option');o.value=value;o.textContent='Nicht vorhanden · Zuordnung prüfen';select.append(o);select.value=value;}
    select.dispatchEvent(new Event('billing-options-updated'));
  }
  function field(id,label,name,anchor,optional=false){
    const box=document.createElement('div');box.className='billing-field';box.innerHTML=`<label for="${id}">${label}</label><select id="${id}" ${name?'name="'+name+'"':''}></select><small class="sub"></small>`;
    (anchor.closest('.select-search-wrap')||anchor).after(box);const select=get(id);select.required=!optional;return select;
  }
  function entryDefault(force=false){
    const select=get('entry-billing-customer'),id=get('customer').value;
    let target=standard(id,get('order').value);
    const sid=get('work-session-id')?.value;
    if(sid)try{const session=JSON.parse(AndroidData.getWorkSession());if(session.id===sid&&session.customer_id===id&&session.billing_customer_id)target=session.billing_customer_id;}catch(_){}
    if(!force&&select.dataset.workCustomer===id&&select.dataset.userOverride==='1')target=select.value;
    if(force)select.dataset.userOverride='0';
    select.dataset.workCustomer=id;
    fill(select,target);select.closest('.billing-field').querySelector('small').textContent=target===id?'Direktabrechnung an den Einsatzkunden.':'Vorschlag aus Kunde/Auftrag · für diesen Eintrag änderbar.';
  }
  function profile(){
    const page=get('customer-profile-page');if(!page?.classList.contains('active'))return;
    const c=customers().find(c=>c.id===page.dataset.customerId);
    if(!c)return;
    let label=get('customer-billing-summary');if(!label){label=document.createElement('p');label.id='customer-billing-summary';label.className='billing-summary';page.querySelector('.design-profile-header').after(label);}
    label.textContent=c.billing_mode==='via_customer'?'Abrechnung über: '+named(c.default_billing_customer_id):'Abrechnung: Direkt';
  }
  let billingLimit=50;
  function groups(){
    if(currentPage!=='billing-page'){get('billing-group-list')?.replaceChildren();return;}
    const map=new Map(),invoices=merged('invoices','invoice');
    const invoiced=new Set(invoices.filter(i=>!['Storniert','Cancelled','Entfernt'].includes(i.status)&&(i.issued_at||i.sevdesk_id&&i.status!=='Entwurf')).flatMap(i=>[...(i.worklog_ids||[]),...(i.positions||[]).map(p=>p.worklog_id)]));
    let remaining=billingLimit;
    for(const w of worklogs().slice().sort((a,b)=>String(b.work_date).localeCompare(String(a.work_date)))){
      if(w.billed||invoiced.has(w.id))continue;
      const id=recipient(w);if(!map.has(id))map.set(id,{id,hours:0,rows:[]});map.get(id).hours+=Number(w.hours||0);map.get(id).rows.push(w);
    }
    get('billing-group-list').innerHTML=[...map.values()].sort((a,b)=>named(a.id).localeCompare(named(b.id),'de')).map(g=>`<section class="card"><h3>${esc(named(g.id))}</h3><strong class="billing-total">${g.hours.toLocaleString('de-AT',{maximumFractionDigits:2})} h</strong>${g.rows.slice(0,Math.max(0,remaining)).map(w=>{remaining--;return `<button type="button" class="billing-work-row" data-edit-worklog="${esc(w.id)}"><span><strong>${esc(named(w.customer_id))}</strong><small>${esc(worklogDateLabel(w.work_date))} · ${esc(w.activity)}</small></span><b>${Number(w.hours||0).toLocaleString('de-AT')} h</b></button>`}).join('')}</section>`).join('')||'<div class="card">Keine in der App offen markierten Leistungen.</div>';if([...map.values()].reduce((sum,g)=>sum+g.rows.length,0)>billingLimit)get('billing-group-list').insertAdjacentHTML('beforeend','<button type="button" class="secondary" id="billing-load-more">Weitere 50 anzeigen</button>');
  }
  function init(){
    const form=get('customer-form'),section=document.createElement('fieldset');section.className='customer-form-section billing-config';section.innerHTML='<legend>Abrechnung</legend><label for="customer-billing-mode">Standard-Abrechnung</label><select id="customer-billing-mode" name="billing_mode"><option value="direct">Direkt an diesen Kunden</option><option value="via_customer">Über einen anderen Auftraggeber</option></select><div id="customer-billing-target"><label for="customer-billing-customer">Rechnungsempfänger</label><select id="customer-billing-customer" name="default_billing_customer_id"></select><small>Gilt als Vorschlag für neue Arbeiten. Alte Einträge bleiben unverändert.</small></div>';form.querySelector('[name="notes"]').previousElementSibling.before(section);
    const csel=get('customer-billing-customer'),mode=get('customer-billing-mode');
    function customerMode(){get('customer-billing-target').hidden=mode.value!=='via_customer';csel.required=mode.value==='via_customer';if(mode.value==='direct')csel.value='';}
    mode.addEventListener('change',customerMode);
    function customerEdit(id){const c=customers().find(c=>c.id===id);mode.value=c?.billing_mode||'direct';fill(csel,c?.default_billing_customer_id,id);customerMode();}
    const editBase=window.editCustomer;window.editCustomer=function(id){customerEdit(id);editBase(id);};
    const e=field('entry-billing-customer','Abrechnung an','billing_customer_id',get('customer'));
    const w=field('edit-billing-customer','Abrechnung an','',get('edit-worklog-order'));
    const o=field('order-billing-customer','Abrechnung an · optional','',get('order-edit-customer'),true);
    get('customer').addEventListener('change',()=>entryDefault(true));get('order').addEventListener('change',()=>entryDefault(true));
    const ordersBase=window.ordersForCustomer;window.ordersForCustomer=function(...args){ordersBase(...args);entryDefault();};
    const orderSaved=window.onWorkEntryOrderSaved;window.onWorkEntryOrderSaved=function(...args){const result=orderSaved?.(...args);if(result)entryDefault(true);return result;};
    e.addEventListener('change',()=>e.dataset.userOverride='1');
    get('entry').addEventListener('reset',()=>setTimeout(()=>entryDefault(true),0));
    document.addEventListener('click',event=>{if(event.target.closest('[data-session-action="complete"]'))setTimeout(()=>{
      try{const session=JSON.parse(AndroidData.getWorkSession());if(session.id===get('work-session-id')?.value&&session.customer_id){get('customer').value=session.customer_id;ordersForCustomer();get('order').value=session.order_id||'';}}catch(_){}
      entryDefault(true);
    },0);});
    document.addEventListener('change',event=>{
      if(event.target.id==='edit-worklog-select'){const row=worklogs().find(w=>w.id===event.target.value);fill(w,row?recipient(row):'');}
      if(event.target.id==='edit-worklog-customer'){fill(w,standard(event.target.value,get('edit-worklog-order').value));}
      if(event.target.id==='order-edit-select'){fill(o,orders().find(o=>o.id===event.target.value)?.billing_customer_id||'', '',true);}
    });
    form.addEventListener('reset',()=>setTimeout(()=>customerEdit(''),0));
    const page=document.createElement('section');page.id='billing-page';page.className='page';page.innerHTML='<div class="page-head"><button type="button" class="back" aria-label="Zurück">‹</button><h2>Abrechnung</h2></div><p class="billing-notice">In der App offen markierte Leistungen nach Rechnungsempfänger. Dies ist kein Nachweis, dass noch keine Rechnung gestellt wurde.</p><div id="billing-group-list"></div>';document.querySelector('main.app').append(page);
    const link=document.createElement('button');link.type='button';link.dataset.open='billing-page';link.innerHTML='<span><strong>Offene Leistungen</strong><small>Nach Rechnungsempfänger · mit Einsatzorten</small></span><b>›</b>';get('more-page').querySelector('[data-open="invoices"]').before(link);
    document.addEventListener('click',event=>{if(event.target.closest('#billing-load-more')){billingLimit+=50;groups();}});
    const renderBase=window.render;window.render=function(){renderBase();groups();profile();};
    const pageBase=window.openPage;window.openPage=function(...args){pageBase(...args);if(currentPage==='work-entry')entryDefault();if(currentPage==='customer-edit'&&!form.elements.id.value)customerEdit('');if(currentPage==='customer-profile-page')profile();if(currentPage==='billing-page')groups();};
    const detail=window.openWorklogDetail;window.openWorklogDetail=function(id){detail(id);const row=worklogs().find(w=>w.id===id);if(row){fill(w,recipient(row));window.captureWorklogBaseline?.();const info=get('worklog-detail-subtitle');if(info&&recipient(row)!==row.customer_id)info.textContent+=' · Abrechnung über: '+named(recipient(row));}};
    // Add a compact recipient line without replacing existing record rows/handlers.
    function decorate(){document.querySelectorAll('.worklog-entry[data-edit-worklog]').forEach(node=>{const row=worklogs().find(w=>w.id===node.dataset.editWorklog);if(row&&recipient(row)!==row.customer_id&&!node.querySelector('.billing-line')){const span=document.createElement('small');span.className='billing-line';span.textContent='Abrechnung über: '+named(recipient(row));node.append(span);}});}
    const after=window.render;window.render=function(){after();decorate();};
    customerEdit('');entryDefault();fill(w,'');fill(o,'','',true);
    try{const draft=JSON.parse(localStorage.getItem('hausgarten.entry.draft.v1')||'null');if(draft?.customer_id===get('customer').value&&customers().some(c=>c.id===draft.billing_customer_id)){fill(e,draft.billing_customer_id);e.dataset.userOverride='1';}}catch(_){/* An unreadable browser draft is never imported as business data. */}
    render();
  }
  function ready(){if(!get('more-page')||!get('entry-location')){setTimeout(ready,40);return;}init();}
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',()=>setTimeout(ready,160),{once:true});else ready();
  window.BillingUI={recipient,standard};
})();

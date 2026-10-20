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
    get('billing-group-list').innerHTML=[...map.values()].sort((a,b)=>named(a.id).localeCompare(named(b.id),'de')).map(g=>`<section class="card"><h3>${esc(named(g.id))}</h3><strong class="billing-total">${g.hours.toLocaleString('de-AT',{maximumFractionDigits:2})} h</strong><button type="button" class="primary" data-prepare-billing="${esc(g.id)}">Rechnung vorbereiten</button>${g.rows.slice(0,Math.max(0,remaining)).map(w=>{remaining--;return `<button type="button" class="billing-work-row" data-edit-worklog="${esc(w.id)}"><span><strong>${esc(named(w.customer_id))}</strong><small>${esc(worklogDateLabel(w.work_date))} · ${esc(w.activity)}</small></span><b>${Number(w.hours||0).toLocaleString('de-AT')} h</b></button>`}).join('')}</section>`).join('')||'<div class="card">Keine in der App offen markierten Leistungen.</div>';if([...map.values()].reduce((sum,g)=>sum+g.rows.length,0)>billingLimit)get('billing-group-list').insertAdjacentHTML('beforeend','<button type="button" class="secondary" id="billing-load-more">Weitere 50 anzeigen</button>');
  }
  function reserved(){return new Set(merged('invoices','invoice').filter(i=>!['Storniert','Cancelled','Entfernt'].includes(i.status)).flatMap(i=>[...(i.worklog_ids||[]),...(i.positions||[]).map(p=>p.worklog_id)]).filter(Boolean));}
  function prepareInvoice(context){
    const excluded=reserved(),allRows=worklogs().filter(w=>!w.billed&&(!context.site||w.customer_id===context.site)&&(!context.recipient||recipient(w)===context.recipient)),rows=allRows.filter(w=>!excluded.has(w.id));
    let dialog=get('billing-prepare-dialog');if(!dialog){dialog=document.createElement('dialog');dialog.id='billing-prepare-dialog';dialog.className='business-action-dialog';document.body.append(dialog);}
    const ids=[...new Set(allRows.map(recipient))].filter(id=>customers().some(c=>c.id===id));let selected=ids.length===1?ids[0]:context.recipient||'',limit=50;
    dialog.innerHTML='<h2>Rechnung vorbereiten</h2><p>Offene Arbeiten prüfen. Noch keine Rechnung und keine Abrechnungsmarkierung. Unbekannte Preise und Materialmengen bleiben leer.</p><label>Rechnungsempfänger<select data-prepare-recipient><option value="">Bitte eindeutig auswählen</option>'+ids.map(id=>'<option value="'+esc(id)+'">'+esc(named(id))+'</option>').join('')+'</select></label><div data-prepare-rows></div><p role="status" data-prepare-status></p><div class="dialog-actions"><button type="button" class="secondary" data-prepare-cancel>Abbrechen</button><button type="button" class="primary" data-prepare-invoice>Auswahl als Entwurf vorbereiten</button></div>';
    const selector=dialog.querySelector('[data-prepare-recipient]'),list=dialog.querySelector('[data-prepare-rows]'),status=dialog.querySelector('[data-prepare-status]'),chosen=new Set();
    function draw(){const matches=rows.filter(w=>recipient(w)===selected);list.innerHTML=matches.slice(0,limit).map(w=>'<label class="check"><input type="checkbox" value="'+esc(w.id)+'" '+(chosen.has(w.id)?'checked':'')+'><span>'+esc(worklogDateLabel(w.work_date)+' · '+named(w.customer_id)+' · '+w.activity+' · '+Number(w.hours).toLocaleString('de-AT')+' h')+'</span></label>').join('')+(matches.length>limit?'<button type="button" class="secondary" data-prepare-more>Weitere 50 Arbeiten</button>':'');status.textContent=matches.length?'Bitte Auswahl, Stunden und anschließend Preise prüfen.':'Keine verfügbaren offenen Arbeiten. Bestehende Rechnungen und Entwürfe bitte unter Rechnungen prüfen.';dialog.querySelector('[data-prepare-invoice]').disabled=!matches.length;
     if(!matches.length){const related=new Set(allRows.filter(w=>recipient(w)===selected).map(w=>w.id));for(const invoice of merged('invoices','invoice').filter(i=>i.status==='Entwurf'&&[...(i.worklog_ids||[]),...(i.positions||[]).map(p=>p.worklog_id)].some(id=>related.has(id)))){const b=document.createElement('button');b.type='button';b.className='secondary';b.dataset.billingExistingDraft=invoice.id;b.textContent='Vorhandenen Rechnungsentwurf öffnen';b.onclick=()=>{dialog.close();window.openInvoiceRecord(invoice.id);};list.append(b);}}
     list.querySelectorAll('input').forEach(input=>input.onchange=()=>input.checked?chosen.add(input.value):chosen.delete(input.value));list.querySelector('[data-prepare-more]')?.addEventListener('click',()=>{limit+=50;draw();});}
    selector.value=selected;rows.filter(w=>recipient(w)===selected).slice(0,50).forEach(w=>chosen.add(w.id));selector.onchange=()=>{selected=selector.value;limit=50;chosen.clear();rows.filter(w=>recipient(w)===selected).slice(0,50).forEach(w=>chosen.add(w.id));draw();};draw();
    dialog.querySelector('[data-prepare-cancel]').onclick=()=>dialog.close();dialog.querySelector('[data-prepare-invoice]').onclick=()=>{try{const blocked=reserved(),latest=worklogs(),selection=rows.filter(w=>chosen.has(w.id));if(!selection.length)throw Error('Bitte mindestens eine Arbeit auswählen.');if(!customers().some(c=>c.id===selected)||selection.some(w=>recipient(w)!==selected))throw Error('Rechnungsempfänger eindeutig prüfen.');if(selection.some(w=>blocked.has(w.id)||w.billed||JSON.stringify(latest.find(x=>x.id===w.id))!==JSON.stringify(w)))throw Error('Die ausgewählten Arbeiten wurden inzwischen geändert. Bitte neu prüfen; kein Entwurf wurde erzeugt.');
      const draft=InvoiceFlow.fromWorklogs(selection.map(w=>({...w,work_customer_name:named(w.customer_id)})));if(draft.positions.length>200)throw Error('Bitte weniger Arbeiten auswählen. Höchstens 200 Rechnungspositionen pro Entwurf.');dialog.close();window.openInvoiceDraft(null,draft);
     }catch(e){status.textContent=HGBusinessFeedback.error(e);}};dialog.showModal();
  }
  window.prepareCustomerInvoice=id=>prepareInvoice({site:id});
  function invoiceReadiness(){
    if(currentPage!=='invoices')return;let card=get('invoice-readiness');if(!card){card=document.createElement('section');card.id='invoice-readiness';card.className='card';get('invoices').querySelector('.page-head').after(card);}
    let settings={};try{settings=HGOwnerClient.create('Unternehmensdaten nicht lesbar')('get_business_settings').settings||{};}catch(_){}
    const yes=v=>v===true||v==='1'||v==='true',missing=[];
    if(!['owner_name','business_street','business_postal_code','business_city'].every(k=>String(settings[k]||'').trim())||!yes(settings.invoice_issuer_confirmed))missing.push('Unternehmensdaten bestätigen');
    if(!['small_business','standard'].includes(settings.tax_mode)||!String(settings[settings.tax_mode==='standard'?'business_vat_id':'tax_exemption_text']||'').trim())missing.push('Steuerdarstellung bestätigen');
    if(!yes(settings.invoice_numbering_confirmed)||settings.invoice_numbering_authority!=='pixel_drive'||!settings.invoice_numbering_device_id)missing.push('Bestehenden Rechnungsnummernkreis bestätigen');
    card.innerHTML='<h3>Rechnungen vorbereiten</h3><p>'+(missing.length?'Vor dem ersten Abschluss einmalig prüfen:':'Einrichtungsangaben vorhanden. Beim Abschluss werden Pflichtangaben, Nummernkreis und Verbindung erneut geprüft.')+'</p>'+(missing.length?'<ul>'+missing.map(x=>'<li>'+esc(x)+'</li>').join('')+'</ul>':'')+'<button type="button" class="secondary" data-invoice-setup>Unternehmen und Rechnungen einrichten</button>';
    card.querySelector('button').onclick=()=>window.openBusinessSettings?.();
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
    document.addEventListener('click',event=>{const prepare=event.target.closest('[data-prepare-billing]');if(prepare)prepareInvoice({recipient:prepare.dataset.prepareBilling});if(event.target.closest('#billing-load-more')){billingLimit+=50;groups();}});
    const renderBase=window.render;window.render=function(){renderBase();groups();profile();invoiceReadiness();};
    const pageBase=window.openPage;window.openPage=function(...args){pageBase(...args);if(currentPage==='work-entry')entryDefault();if(currentPage==='customer-edit'&&!form.elements.id.value)customerEdit('');if(currentPage==='customer-profile-page')profile();if(currentPage==='billing-page')groups();if(currentPage==='invoices')invoiceReadiness();};
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

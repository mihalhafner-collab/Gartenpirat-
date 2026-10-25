/* Android adapter for the shared invoice workspace. Native storage remains unchanged. */
(() => {
  const dialog=document.createElement('dialog');dialog.className='invoice-editor-dialog';document.body.append(dialog);
  const layout=document.createElement('dialog');layout.className='invoice-preview-dialog';document.body.append(layout);
  let studio=null,original={},customerReturn=null,saving=false,returnToRead=null,editSession=null;
  const invoices=()=>merged('invoices','invoice');
  const money=n=>Number(n).toLocaleString('de-AT',{style:'currency',currency:'EUR'});
  const issuer=()=>({...state.master?.invoice_issuer,...state.master?.business_settings});
  const toast=message=>{let box=document.getElementById('invoice-toast');if(!box){box=document.createElement('p');box.id='invoice-toast';box.style.cssText='position:fixed;bottom:95px;left:14px;right:14px;z-index:100;background:#183c2c;color:white;padding:15px;border-radius:12px;pointer-events:none';box.setAttribute('role','status');document.body.append(box);}box.textContent=message;box.hidden=false;clearTimeout(toast.timer);toast.timer=setTimeout(()=>box.hidden=true,6500);};
  function retain(){if(!studio||!editSession)return true;try{editSession.values=studio.read();editSession.context.pane=dialog.querySelector('.invoice-studio')?.dataset.pane;HGFormDrafts.put(editSession);return true;}catch(_){toast('Rechnungsbearbeitung konnte nicht gesichert werden. Eingaben bleiben geöffnet.');return false;}}
  function close(discard=false) {
    if(saving)return false;if(!retain())return false;
    if(discard){if(editSession?.pending){toast('Zuerst dieselbe Speicherung erneut prüfen.');return false;}if(editSession?.dirty&&!confirm('Nur ungespeicherte Rechnungsänderungen verwerfen? Der gespeicherte Rechnungsentwurf bleibt erhalten.'))return false;if(editSession)HGFormDrafts.remove(editSession,{discard:true,formId:'invoice-draft-form'});}
    dialog.close();editSession=null;if(returnToRead){const id=returnToRead;returnToRead=null;window.openInvoiceRecord(id);}return true;
  }
  dialog.addEventListener('cancel',event=>{event.preventDefault();if(studio?.back())return;close();});
  function showPreview(invoice) {
    layout.innerHTML='<div class="invoice-preview-close"><strong>Rechnungsvorschau · Entwurf</strong><button type="button" class="secondary">Schließen</button></div>'+
      InvoiceStudio.preview(invoice,issuer(),customers().find(x=>x.id===invoice.customer_id)||{},'hafner-logo.png');
    layout.querySelector('button').onclick=()=>layout.close();layout.showModal();
  }
  function open(id,preset={},options={}) {
    if(dialog.open&&!close())return;const resumed=options.session||null;original=JSON.parse(JSON.stringify(resumed?.context.base||invoices().find(x=>x.id===id)||preset));
    if(original.id&&(original.status!=='Entwurf'||original.sevdesk_id||original.issued_at)){alert('Abgeschlossene und importierte Rechnungen bleiben unverändert.');return;}
    if(original.id&&state.pending.some(a=>['invoice_issue','owner_invoice_issue','pixel_invoice_issue_v1'].includes(a.kind)&&a.payload.id===original.id)){alert('Abschluss bereits vorgemerkt. Bitte zuerst den Pixel-/Drive-Abgleich abwarten.');return;}
    returnToRead=options.returnToRead?id:null;
    let source;try{source=window.HGBillingUI?.sourceContext?.()||null;}catch(error){toast(error.message);return;}
    let priceTemplates=[];try{priceTemplates=HGOwnerClient.create('Preisbausteine nicht verfügbar')('search_offer_templates',{limit:100}).entries.map(x=>x.entry).filter(t=>t.active!==false&&t.price_confirmed===true&&t.default_price!==null&&t.default_price!==undefined);}catch(_){}
    dialog.innerHTML='<form id="invoice-draft-form" novalidate><div class="dialog-heading"><div><small>RECHNUNG · ENTWURF</small><h2>'+(original.id?'Entwurf bearbeiten':'Rechnung erstellen')+'</h2></div><button type="button" class="secondary" data-later aria-label="Bearbeitung später fortsetzen">×</button></div><div data-studio></div><div class="dialog-actions"><button type="button" class="secondary" data-cancel>Abbrechen</button><button type="submit" class="primary">Entwurf speichern</button></div></form>';
    studio=InvoiceStudio.mount(dialog.querySelector('[data-studio]'),{
      preset:resumed?.values||original,priceTemplates,customers:customers(),orders:orders(),worklogs:source?source.rows:worklogs(),decimalQuantities:!!source,reservedWorklogIds:new Set(invoices().filter(i=>i.id!==original.id&&!['Storniert','Cancelled','Entfernt'].includes(i.status)&&(!source||i.status==='Entwurf'&&!i.issued_at)).flatMap(i=>[...(i.worklog_ids||[]),...(i.positions||[]).map(p=>p.worklog_id)]).filter(Boolean)),issuer:issuer(),logo:'hafner-logo.png',notify:toast,
      onAddCustomer:draft=>{
        if(!retain())return;customerReturn=editSession;dialog.close();
        const form=document.getElementById('customer-form');form.reset();editingCustomer=null;
        form.elements.id.value='';form.elements.active.checked=true;
        document.getElementById('custom-fields').innerHTML='';
        openPage('customer-edit');form.elements.name.focus();
      },
    });
    editSession=resumed||HGFormDrafts.start('invoice',studio.read(),{base:original,returnToRead});dialog.querySelector('form').dataset.editSession=editSession.session_id;
    dialog.querySelector('form').addEventListener('input',retain);dialog.querySelector('form').addEventListener('change',retain);
    dialog.querySelectorAll('[data-cancel]').forEach(button=>button.onclick=()=>close(true));dialog.querySelector('[data-later]').onclick=()=>close();
    const later=document.createElement('button');later.type='button';later.className='secondary';later.textContent='Später fortsetzen';later.onclick=()=>close();dialog.querySelector('.dialog-actions').prepend(later);
    if(resumed?.context.pane)dialog.querySelector('[data-pane="'+CSS.escape(resumed.context.pane)+'"]')?.click();
    if(editSession.pending){studio.setBusy(true);dialog.querySelectorAll('input,select,textarea,[data-cancel]').forEach(e=>e.disabled=true);dialog.querySelector('[type=submit]').textContent='Dieselbe Speicherung erneut prüfen';}

    dialog.querySelector('form').onsubmit=event=>{
      event.preventDefault();if(saving)return;
      try {
        const payload=editSession.pending?HGOwnerClient.clone(editSession.pending.args):studio.validate();
        if(!editSession.pending&&!event.currentTarget.checkValidity()){studio.revealInvalid();return;}
        if(original.id){payload._edit_base=original;payload._edit_changes=Object.fromEntries(Object.entries(payload).filter(([key,value])=>['customer_id','order_id','invoice_date','service_start','service_end','payment_days','notes','positions','domestic_service_confirmed','header_text','footer_text','tax_mode','recipient_type','recipient_vat_id'].includes(key)&&JSON.stringify(value)!==JSON.stringify(original[key])));}
        if(!payload.invoice_number)payload.invoice_number='Entwurf-Pixel-'+Date.now();
        saving=true;studio.setBusy(true);dialog.querySelector('[type="submit"]').disabled=true;
        if(!payload.id)payload.id='inv_mobile_'+crypto.randomUUID().replaceAll('-','');if(!retain())return;const result=HGFormDrafts.nativeSave(editSession,'saveInvoice',payload);original.id=result;editSession.pending=null;HGFormDrafts.remove(editSession,{formId:'invoice-draft-form'});editSession=null;
        if(String(result).startsWith('ERROR:'))throw Error(String(result).replace(/^ERROR:\s*/,''));
        const pane=dialog.querySelector('.invoice-studio')?.dataset.pane,returning=!!returnToRead;dialog.close();try{load();}catch(_){toast('Rechnungsentwurf lokal gespeichert. Übersicht bitte erneut laden.');}saving=false;open(String(result),{}, {returnToRead:returning});if(pane)dialog.querySelector('[data-pane="'+pane+'"]')?.click();toast(source?'Entwurf lokal gespeichert. Belegte Restmengen werden erst bei der Ausgabe zugeordnet.':'Entwurf auf diesem Gerät gespeichert. Drive-Abgleich folgt; Arbeitsstunden bleiben offen.');
      }catch(error){studio.revealInvalid();if(editSession?.pending){dialog.querySelectorAll('input,select,textarea,[data-cancel]').forEach(e=>e.disabled=true);dialog.querySelector('[type=submit]').textContent='Dieselbe Speicherung erneut prüfen';}alert(error.message);}
      finally{saving=false;studio.setBusy(!!editSession?.pending);dialog.querySelector('[type="submit"]').disabled=false;}
    };
    dialog.showModal();
  }
  window.onInvoiceCustomerSaved=id=>{
    if(!customerReturn)return false;
    const edit=customerReturn;customerReturn=null;edit.values={...edit.values,customer_id:id,order_id:null,positions:(edit.values.positions||[]).filter(p=>!p.worklog_id),worklog_ids:[]};HGFormDrafts.put(edit);finishPage('invoices');open(null,{}, {session:edit});return true;
  };
  window.openInvoiceDraft=open;window.resumeInvoiceEdit=edit=>open(edit.context.base?.id||null,{}, {session:edit,returnToRead:edit.context.returnToRead});
  function draftRead(invoice){
    const recipient=customers().find(row=>row.id===invoice.customer_id)||{},facts=[['Rechnungsempfänger',recipient.name||'Zuordnung prüfen'],['Adresse',[recipient.street,[recipient.postal_code,recipient.city].filter(Boolean).join(' ')].filter(Boolean).join(', ')||'Noch ergänzen'],['Status','Entwurf · noch keine ausgestellte Rechnung'],['Datum',worklogDateLabel(invoice.invoice_date)],['Leistungszeitraum',[invoice.service_start,invoice.service_end].filter(Boolean).join(' bis ')||'Noch ergänzen'],['Betrag',invoice.amount==null?'Noch unvollständig':money(invoice.amount)]];
    return '<section class="card invoice-draft-read"><h2 tabindex="-1">Gespeicherter Rechnungsentwurf</h2><dl>'+facts.map(([name,value])=>'<dt>'+esc(name)+'</dt><dd>'+esc(value)+'</dd>').join('')+'</dl><h3>Gespeicherte Positionen</h3><ul>'+((invoice.positions||[]).length?(invoice.positions||[]).map(position=>'<li><strong>'+esc(position.title||'Bezeichnung noch ergänzen')+'</strong>'+(position.description?'<p>'+esc(position.description)+'</p>':'')+'<p>'+esc(String(position.quantity??'Menge fehlt')+' '+(position.unit||'')+' × '+(position.price==null?'Preis fehlt':String(position.price)+' EUR')+' · USt '+String(position.tax_rate??'offen')+' %')+'</p></li>').join(''):'<li>Noch keine Position erfasst.</li>')+'</ul>'+(invoice.notes?'<h3>Interne Notiz</h3><p>'+esc(invoice.notes)+'</p>':'')+'<div class="local-payment-actions"><button type="button" class="primary" data-edit-mobile-invoice="'+esc(invoice.id)+'" data-return-to-read>Bearbeiten</button></div><details><summary>Layoutvorschau des Entwurfs</summary>'+InvoiceStudio.preview(invoice,issuer(),recipient,'hafner-logo.png')+'</details></section>';
  }
  window.openInvoiceRecord=function(id){
    const invoice=invoices().find(x=>x.id===id);if(!invoice){toast('Diese Rechnung ist im lokalen Datenstand nicht vorhanden. Bitte abgleichen.');return;}
    if(invoice.status==='Entwurf'&&!invoice.sevdesk_id&&!invoice.issued_at){layout.innerHTML='<div class="invoice-preview-close"><strong>Rechnungsentwurf · Details</strong><button type="button" class="secondary" data-close-draft-read>Schließen</button></div>'+draftRead(invoice);layout.querySelector('[data-close-draft-read]').onclick=()=>layout.close();if(!layout.open)layout.showModal();layout.querySelector('h2')?.focus({preventScroll:true});return;}
    layout.innerHTML='<div class="invoice-preview-close"><strong>Rechnungsdetails</strong><button type="button" class="secondary">Schließen</button></div><section class="card"><h2>'+esc(invoice.invoice_number||'Rechnung')+'</h2><p>'+esc(customerName(invoice.customer_id))+'</p><dl><dt>Status</dt><dd>'+esc(invoice.status||'Nicht angegeben')+'</dd><dt>Rechnungsdatum</dt><dd>'+esc(worklogDateLabel(invoice.invoice_date))+'</dd><dt>Betrag</dt><dd>'+money(invoice.amount)+'</dd></dl>'+(invoice.notes?'<p>'+esc(invoice.notes)+'</p>':'')+((invoice.pdf_base64||invoice.pdf_storage_name)?'<button type="button" class="primary" data-share-mobile-invoice="'+esc(invoice.id)+'" data-channel="share">PDF teilen</button>':'<p class="sub">Kein Rechnungs-PDF im lokalen Datenstand. Eine neu erzeugte Vorschau wäre nicht das Original und wird deshalb nicht angezeigt.</p>')+'</section>';
    if(invoice.issued_at&&invoice.status!=='Storniert')window.HGBusinessActions?.payment(layout.querySelector('section'),invoice.id);
    layout.querySelector('.invoice-preview-close button').onclick=()=>layout.close();if(!layout.open)layout.showModal();
  };
  const button=document.createElement('button');button.className='primary';button.textContent='+ Rechnung erstellen';button.onclick=()=>open();
  document.getElementById('invoice-list').before(button);
  const filters=document.createElement('div');filters.className='invoice-filters';
  filters.innerHTML='<label>Rechnung oder Kunde suchen<input type="search" id="mobile-invoice-search" placeholder="Name oder Rechnungsnummer"></label><label>Status<select id="mobile-invoice-filter"><option value="all">Alle</option><option>Entwurf</option><option>Gestellt</option><option>Bezahlt</option><option>Storniert</option></select></label><button type="button" class="secondary">Zurücksetzen</button>';
  document.getElementById('invoice-list').before(filters);
  const noResults=document.createElement('p');noResults.className='invoice-hint';noResults.hidden=true;
  noResults.textContent='Keine Rechnungen gefunden. Suche oder Statusfilter zurücksetzen.';
  document.getElementById('invoice-list').after(noResults);
  function filter() {
    const term=filters.querySelector('input').value.toLocaleLowerCase('de-AT'),status=filters.querySelector('select').value;
    let visible=0;
    document.querySelectorAll('#invoice-list [data-invoice]').forEach(row=>{
      const invoice=invoices().find(x=>x.id===row.dataset.invoice);if(!invoice)return;
      const hidden=![customerName(invoice.customer_id),invoice.invoice_number].join(' ').toLocaleLowerCase('de-AT').includes(term)||(status!=='all'&&invoice.status!==status);
      const shell=row.closest('.swipe-shell')||row;shell.hidden=hidden;
      if(!hidden)visible++;
    });
    noResults.hidden=visible>0;
  }
  filters.querySelector('input').oninput=filter;filters.querySelector('select').onchange=filter;
  filters.querySelector('button').onclick=()=>{filters.querySelector('input').value='';filters.querySelector('select').value='all';filter();};
  document.querySelector('.brand p').textContent='Mobile Betriebsverwaltung · Version 1.26.0';
  document.addEventListener('change',event=>{if(event.target.id==='edit-worklog-select'){const button=document.querySelector('#edit-worklog-card [data-invoice-worklog]'),row=worklogs().find(w=>w.id===event.target.value);if(button){button.dataset.invoiceWorklog=event.target.value;button.hidden=!row||!!row.billed;}}});
  document.addEventListener('click',event=>{
    const edit=event.target.closest('[data-record-edit]'),sheet=document.getElementById('record-action-dialog');
    if(edit&&sheet?.dataset.actionType==='invoice'){event.preventDefault();event.stopImmediatePropagation();sheet.close();open(sheet.dataset.actionId);return;}
    const editInvoice=event.target.closest('[data-edit-mobile-invoice]');if(editInvoice){event.preventDefault();event.stopImmediatePropagation();const returning=editInvoice.hasAttribute('data-return-to-read');layout.close();open(editInvoice.dataset.editMobileInvoice,{}, {returnToRead:returning});return;}
    const fromWorklog=event.target.closest('[data-invoice-worklog]');
    if(fromWorklog){event.preventDefault();event.stopImmediatePropagation();if(currentPage==='worklog-detail'&&window.saveWorklogDetail?.(false)===false)return;try{const source=window.HGBillingUI?.sourceContext?.()||null,row=(source?source.rows:worklogs()).find(w=>w.id===fromWorklog.dataset.invoiceWorklog);if(!row)throw new Error('Für diese Arbeitsaufzeichnung ist keine eindeutig offene Restmenge belegt. Bitte den aktuellen Abrechnungsstand prüfen.');open(null,InvoiceFlow.fromWorklogs([row]));}catch(error){alert(error.message);}return;}
    const preview=event.target.closest('[data-preview-mobile-invoice]');
    if(preview){event.preventDefault();event.stopImmediatePropagation();showPreview(invoices().find(x=>x.id===preview.dataset.previewMobileInvoice));return;}
    const action=event.target.closest('[data-issue-mobile-invoice],[data-share-mobile-invoice]');
    if(action){event.preventDefault();event.stopImmediatePropagation();const id=action.dataset.issueMobileInvoice||action.dataset.shareMobileInvoice;const invoice=invoices().find(x=>x.id===id);if(!invoice)return;
      if(action.dataset.shareMobileInvoice){if(!invoice.pdf_base64&&!invoice.pdf_storage_name){alert('PDF fehlt noch. Bitte Drive abgleichen, nachdem der Nummernservice die Rechnung abgeschlossen hat.');return;}if(!confirm('Die Zustimmung des Kunden zur elektronischen Rechnung muss vorliegen. PDF jetzt übergeben? Empfänger und tatsächlichen Versand bestätigst du in der Versand-App.'))return;const result=AndroidData.shareInvoiceById?AndroidData.shareInvoiceById(invoice.id,action.dataset.channel||'share'):AndroidData.shareInvoiceVia(JSON.stringify(invoice),action.dataset.channel||'share');if(String(result).startsWith('ERROR:'))alert(result);return;}
      if(window.LocalInvoiceUI?.ready?.()&&window.LocalInvoiceUI?.issueInvoice){LocalInvoiceUI.issueInvoice(id);return;}
      if(!invoice.domestic_service_confirmed||!invoice.service_start||!invoice.service_end){alert('Bitte Leistungszeitraum und Inlandsbestätigung im Entwurf ergänzen.');return;}
      if(confirm('Rechnung mit '+money(invoice.amount)+' verbindlich abschließen? Pflichtangaben, bestehender Nummernkreis und Verbindung werden geprüft. Nummer und Original-PDF werden verbindlich festgeschrieben. Danach ist nur eine Korrektur möglich. Dies versendet keine Rechnung und erfasst keine Zahlung.')){const result=AndroidData.requestInvoiceIssue(id);if(String(result).startsWith('ERROR:'))alert(result);else{action.disabled=true;action.textContent='Abschluss vorgemerkt';toast('Rechnungsabschluss angefordert. Noch nicht ausgestellt; Ergebnis nach dem Abgleich prüfen.');}}
      return;
    }
    const row=event.target.closest('[data-invoice]');if(!row||!row.closest('#invoice-list'))return;
    const invoice=invoices().find(x=>x.id===row.dataset.invoice);
    if(invoice){event.preventDefault();event.stopImmediatePropagation();window.openInvoiceRecord(invoice.id);}
  },true);
  const previousRender=render;
  document.getElementById('invoice-list').classList.add('invoice-overview');
  render=function(){
    previousRender();
    document.querySelectorAll('#invoice-list [data-invoice]').forEach(row=>{
      const invoice=invoices().find(x=>x.id===row.dataset.invoice);if(!invoice)return;
      row.classList.add('invoice-list-row');
      row.innerHTML='<div><strong class="invoice-list-title">'+esc(customerName(invoice.customer_id))+'</strong><p>'+esc(invoice.status==='Entwurf'&&!invoice.sevdesk_id?'Rechnungsentwurf':invoice.invoice_number)+' · '+esc(worklogDateLabel(invoice.invoice_date))+'</p><span class="invoice-list-status '+(invoice.status==='Entwurf'?'is-draft':'')+'">'+esc(invoice.status)+'</span></div><div class="invoice-list-amount">'+money(invoice.amount)+'</div>';
      const actions=document.createElement('div');actions.className='invoice-list-actions';row.append(actions);
      const issueError=(state.master?.sync_errors||[]).find(e=>e.invoice_id===invoice.id&&['invoice_issue','owner_invoice_issue','pixel_invoice_issue_v1'].includes(e.kind));
      if(issueError&&!invoice.issued_at&&!state.pending.some(a=>['invoice_issue','owner_invoice_issue','pixel_invoice_issue_v1'].includes(a.kind)&&a.payload.id===invoice.id)){const message=document.createElement('p');message.className='invoice-form-error';message.textContent='Nicht abgeschlossen: '+issueError.error+' Entwurf bearbeiten und erneut prüfen.';row.append(message);}
      if(invoice.issued_at){for(const[channel,label]of [['email','E-Mail'],['whatsapp','WhatsApp'],['share','PDF teilen']]){const send=document.createElement('button');send.type='button';send.className='secondary';send.dataset.shareMobileInvoice=invoice.id;send.dataset.channel=channel;send.textContent=label;actions.append(send);}}
      else if(invoice.positions&&invoice.status==='Entwurf'&&!invoice.sevdesk_id){
        const edit=document.createElement('button');edit.type='button';edit.className='primary';edit.textContent='Bearbeiten';edit.dataset.editMobileInvoice=invoice.id;actions.append(edit);
        const view=document.createElement('button');view.type='button';view.className='secondary';view.textContent='Vorschau';view.dataset.previewMobileInvoice=invoice.id;actions.append(view);
        const action=document.createElement('button');action.type='button';action.className='secondary';action.dataset.issueMobileInvoice=invoice.id;const pending=state.pending.some(a=>['invoice_issue','owner_invoice_issue','pixel_invoice_issue_v1'].includes(a.kind)&&a.payload.id===invoice.id);action.textContent=pending?'Abschluss vorgemerkt':'Abschließen';action.disabled=pending;actions.append(action);
      }
    });
    const invoiceData=new Map(invoices().map(x=>[x.id,x]));
    [...document.querySelectorAll('#invoice-list [data-invoice]')].sort((a,b)=>{
      const left=invoiceData.get(a.dataset.invoice),right=invoiceData.get(b.dataset.invoice);
      return String(right?.invoice_date||'').localeCompare(String(left?.invoice_date||''))||String(right?.updated_at||'').localeCompare(String(left?.updated_at||''));
    }).forEach(row=>document.getElementById('invoice-list').append(row.closest('.swipe-shell')||row));
    filter();
    const selected=document.getElementById('edit-worklog-select')?.value,row=worklogs().find(w=>w.id===selected),card=document.getElementById('edit-worklog-card');
    if(card){let button=card.querySelector('[data-invoice-worklog]');if(!button){button=document.createElement('button');button.type='button';button.className='secondary';button.textContent='Rechnung aus dieser Arbeit erstellen';card.append(button);}button.dataset.invoiceWorklog=selected||'';button.hidden=!row||!!row.billed;}
  };render();
  const previousBack=window.handleAndroidBack;
  window.handleAndroidBack=function(){
    if(layout.open){layout.close();return true;}
    if(dialog.open){if(studio?.back())return true;close();return true;}
    if(customerReturn&&currentPage==='customer-edit'){
      if(!confirm('Kundenanlage abbrechen und zum Rechnungsentwurf zurückkehren?'))return true;
      const draft=customerReturn;customerReturn=null;finishPage('invoices');open(null,{}, {session:draft});return true;
    }
    return previousBack?previousBack():false;
  };
})();

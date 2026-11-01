/* Shared invoice workspace. Preview only: never issues, numbers, saves or sends. */
(function(root) {
  'use strict';
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const euro = n => Number(n).toLocaleString('de-AT', {style:'currency',currency:'EUR'});
  const today = () => new Date().toLocaleDateString('sv-SE');
  const date = value => /^\d{4}-\d{2}-\d{2}$/.test(value || '') ? value.split('-').reverse().join('.') : 'Noch ergänzen';
  // Integer decimal arithmetic: same per-line ROUND_HALF_UP as the server.
  function decimal(value) {
    const text = String(value ?? '').trim().replace(',', '.');
    if (!/^\d+(?:\.\d{0,8})?$/.test(text)) throw new Error('Bitte eine gültige, nicht negative Zahl eingeben (maximal 8 Nachkommastellen).');
    const [whole, fraction = ''] = text.split('.');
    return [BigInt(whole + fraction), 10n ** BigInt(fraction.length)];
  }
  function lineCents(p, taxMode="unconfigured") {
    if(p.price===null||p.price===undefined||p.price==='')throw new Error('Preis fehlt. Bitte ausdrücklich ergänzen.');
    if(taxMode==='standard'&&(p.tax_rate===null||p.tax_rate===undefined||p.tax_rate===''))throw new Error('Steuersatz fehlt. Bitte ausdrücklich prüfen.');
    const [q, qs] = decimal(p.quantity), [price, ps] = decimal(p.price), [d, ds] = decimal(p.discount || 0);
    if (q <= 0n || d > 100n * ds || (Number(p.tax_rate||0)<0||Number(p.tax_rate||0)>100||(!["standard"].includes(taxMode)&&Number(p.tax_rate||0)!==0))) throw new Error('Menge muss positiv sein; Rabatt 0 bis 100 %. Steuermodus und Steuersatz prüfen.');
    const numerator = q * price * (100n * ds - d), denominator = qs * ps * ds;
    const cents = (2n * numerator + denominator) / (2n * denominator);
    if (cents > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('Der Betrag ist zu groß.');
    return Number(cents);
  }
  function taxCents(p,taxMode){const [rate,scale]=decimal(p.tax_rate||0),base=BigInt(lineCents(p,taxMode));return Number((2n*base*rate+100n*scale)/(200n*scale));}
  function total(positions,taxMode="unconfigured") {
    return positions.reduce((sum,p)=>sum+lineCents(p,taxMode)+taxCents(p,taxMode),0)/100;
  }
  function dueDate(value, days) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '') || !Number.isInteger(Number(days)) || Number(days) < 0 || Number(days) > 365) return '';
    const parsed = new Date(value + 'T12:00:00Z');
    if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0,10) !== value) return '';
    parsed.setUTCDate(parsed.getUTCDate() + Number(days));
    return parsed.toISOString().slice(0,10);
  }
  function preview(invoice, issuer = {}, recipient = {}, logo = '', draft = true) {
    const mode=invoice.tax_mode&&invoice.tax_mode!=="unconfigured"?invoice.tax_mode:issuer.tax_mode||"unconfigured";
    let amount = null;
    try { amount = total(invoice.positions || [],mode); } catch (_) {}
    const address = [recipient.company !== recipient.name ? recipient.company : '', recipient.name,
      recipient.street, [recipient.postal_code,recipient.city].filter(Boolean).join(' '), recipient.country].filter(Boolean);
    const sender = [issuer.owner_name, issuer.business_street, [issuer.business_postal_code,issuer.business_city].filter(Boolean).join(' ')].filter(Boolean);
    const missing = [];
    if (!recipient.name || !recipient.street || !recipient.postal_code || !recipient.city) missing.push('Kunde / Rechnungsadresse');
    if (!issuer.owner_name || !issuer.business_street || !issuer.business_postal_code || !issuer.business_city) missing.push('Ausstellerdaten unter Unternehmen und Rechnungen');
    if (!invoice.invoice_date) missing.push('Rechnungsdatum');
    if (!invoice.service_start || !invoice.service_end) missing.push('Leistungszeitraum');
    if (invoice.service_start && invoice.service_end && invoice.service_end < invoice.service_start) missing.push('Leistungsende liegt vor Beginn');
    if (!(invoice.positions || []).length || (invoice.positions || []).some(p=>!String(p.title || '').trim())) missing.push('Leistungsbeschreibung');
    if (amount === null) missing.push('Menge / Preis / Rabatt');
    if (draft && !invoice.domestic_service_confirmed) missing.push('Bestätigung der Inlandsleistung');
    if (draft && amount === 0) missing.push('Preise prüfen: Gesamtbetrag ist 0 €');
    const lines = (invoice.positions || []).map((p,i) => {
      let subtotal = 'Bitte prüfen'; try { subtotal = euro(lineCents(p,mode)/100); } catch (_) {}
      return `<tr><td>${i+1}.</td><td><strong>${escape(p.title || 'Leistung ergänzen')}</strong>${p.description ? `<small>${escape(p.description)}</small>` : ''}${Number(p.discount)>0?`<small>Rabatt ${escape(p.discount)} %</small>`:''}</td><td>${escape(p.quantity)} ${escape(p.unit)}</td><td>${p.price===null||p.price===undefined||p.price===''?'Preis fehlt':escape(euro(Number(String(p.price).replace(',','.'))))}</td><td>${escape(subtotal)}</td></tr>`;
    }).join('');
    const invoiceNumber = draft ? 'ENTWURF' : invoice.invoice_number;
    return `<div class="invoice-preview-note">${draft?'Layoutvorschau · noch keine ausgestellte Rechnung':'Rechnungsansicht'}${missing.length ? `<details class="invoice-checks"><summary>Noch prüfen: ${missing.length} Angaben</summary><ul>${missing.map(x=>`<li>${escape(x)}</li>`).join('')}</ul></details>` : '<span>Angaben erfasst. Verbindliche Prüfung erfolgt beim Abschluss.</span>'}</div><article class="invoice-paper" aria-label="Optische Rechnungsvorschau"><header class="invoice-paper-head"><div>${sender.map(x=>`<span>${escape(x)}</span>`).join('') || '<span>Ausstellerdaten ergänzen</span>'}</div>${logo?`<img src="${escape(logo)}" alt="Firmenlogo">`:''}</header><div class="invoice-paper-address"><div>${address.map(x=>`<span>${escape(x)}</span>`).join('') || '<span>Kunde auswählen</span>'}</div><dl><dt>Rechnungs-Nr.</dt><dd>${escape(invoiceNumber)}</dd><dt>Rechnungsdatum</dt><dd>${date(invoice.invoice_date)}</dd><dt>Leistung</dt><dd>${date(invoice.service_start)} – ${date(invoice.service_end)}</dd><dt>Kundennummer</dt><dd>${escape(recipient.sevdesk_customer_number || '–')}</dd></dl></div><h2>${draft?'Rechnungsentwurf':'Rechnung '+escape(invoiceNumber)}</h2>${draft?'<p class="invoice-paper-draft">ENTWURF · Nummer erst nach Abschluss · nicht zum Versand</p>':''}<p class="invoice-paper-text">${escape(invoice.header_text || 'Sehr geehrte Damen und Herren,\n\nvielen Dank für Ihren Auftrag. Hiermit stelle ich Ihnen folgende Leistungen in Rechnung:')}</p><table><thead><tr><th>Pos.</th><th>Beschreibung</th><th>Menge</th><th>Einzelpreis</th><th>Betrag</th></tr></thead><tbody>${lines || '<tr><td colspan="5">Noch keine Leistungen erfasst.</td></tr>'}</tbody></table><div class="invoice-paper-total"><span>Gesamtbetrag</span><strong>${amount===null?'Bitte prüfen':escape(euro(amount))}</strong></div><p class="invoice-paper-exemption">${escape(mode==='small_business'?(issuer.tax_exemption_text||invoice.tax_exemption||'Steuerbefreiungshinweis in Einstellungen ergänzen'):mode==='standard'?('Umsatzsteuer enthalten · UID '+(issuer.business_vat_id||'ergänzen')):'Steuermodus in zentralen Einstellungen bestätigen')}</p><p class="invoice-paper-text">Zahlung innerhalb von ${escape(invoice.payment_days ?? 14)} Tagen.${dueDate(invoice.invoice_date,invoice.payment_days??14)?` Fällig am ${date(dueDate(invoice.invoice_date,invoice.payment_days??14))}.`:''}</p><p class="invoice-paper-text">${escape(invoice.footer_text || 'Bitte überweisen Sie den Rechnungsbetrag unter Angabe der Rechnungsnummer.\n\nMit freundlichen Grüßen\n'+(issuer.owner_name || ''))}</p><footer>${[sender.join('\n'),[issuer.business_phone,issuer.business_email].filter(Boolean).join('\n'),issuer.business_legal_info,[issuer.business_bank,issuer.business_iban?'IBAN '+issuer.business_iban:'',issuer.business_bic?'BIC '+issuer.business_bic:''].filter(Boolean).join('\n')].filter(Boolean).map(x=>`<span>${escape(x)}</span>`).join('')}</footer></article><p class="invoice-preview-disclaimer">Layoutvorschau, kein finales PDF. Das endgültige PDF wird separat geprüft und beim Abschluss unveränderlich gespeichert.</p>`;
  }

  function mount(container, options = {}) {
    const original = options.preset || {}, customers = options.customers || [], orders = options.orders || [], worklogs = options.worklogs || [];
    const notify = options.notify || ((message)=>alert(message));
    let mode=original.tax_mode&&original.tax_mode!=="unconfigured"?original.tax_mode:options.issuer?.tax_mode||"unconfigured";
    const defaultDays=String(options.issuer?.default_payment_days??'').trim()!==''?Number(options.issuer.default_payment_days):14;
    let active = 'details', removed = null, busy = false;
    const input = (label,name,value,type='text',extra='') => `<label>${label}<input name="${name}" type="${type}" value="${escape(value)}" ${extra}></label>`;
    container.classList.add('invoice-studio');
    container.innerHTML = `<nav class="invoice-tabs" aria-label="Rechnung erstellen"><button type="button" data-pane="details" aria-pressed="true"><span>1</span>Kunde & Details</button><button type="button" data-pane="lines" aria-pressed="false"><span>2</span>Leistungen</button><button type="button" data-pane="preview" aria-pressed="false"><span>3</span>Vorschau</button></nav><div class="invoice-workspace"><div class="invoice-edit-panes"><section data-section="details"><h3>Kunde & Rechnungsdetails</h3><label>Kunde suchen<input type="search" data-customer-search placeholder="Name oder Ort" autocomplete="off"></label><label>Kunde<select name="customer_id" required><option value="">Kunde auswählen</option>${customers.filter(c=>c.active!==0||c.id===original.customer_id).map(c=>`<option value="${escape(c.id)}" ${c.id===original.customer_id?'selected':''}>${escape(c.name)}${c.city?' · '+escape(c.city):''}</option>`).join('')}</select></label><button type="button" class="secondary" data-add-customer>+ Neuer Kunde</button><div class="invoice-recipient" data-recipient></div><div class="invoice-field-grid">${input('Rechnungsdatum','invoice_date',original.invoice_date||today(),'date','required')}${input('Zahlungsziel (Tage)','payment_days',original.payment_days??defaultDays,'number','min="0" max="365" step="1" required')}${input('Leistung von','service_start',original.service_start||'','date')}${input('Leistung bis','service_end',original.service_end||'','date')}</div><label>Steuermodus dieses Entwurfs<select name="tax_mode"><option value="unconfigured" ${mode==='unconfigured'?'selected':''}>Noch nicht bestätigt</option><option value="small_business" ${mode==='small_business'?'selected':''}>Kleinunternehmer</option><option value="standard" ${mode==='standard'?'selected':''}>Umsatzsteuerpflichtig</option></select></label><label>Rechnungsempfänger-Typ<select name="recipient_type"><option value="">Noch nicht festgelegt</option><option value="b2b" ${original.recipient_type==='b2b'?'selected':''}>Unternehmen</option><option value="consumer" ${original.recipient_type==='consumer'?'selected':''}>Verbraucher</option></select></label>${input('UID des Empfängers (falls erforderlich)','recipient_vat_id',original.recipient_vat_id||'')}<label>Projekt / Auftrag (optional)<select name="order_id"></select></label><details class="invoice-options"><summary>Texte & interne Notiz</summary><label>Einleitung<textarea name="header_text" rows="3">${escape(original.header_text||'')}</textarea></label><label>Schlusstext<textarea name="footer_text" rows="3">${escape(original.footer_text||'')}</textarea></label><label>Interne Notiz · nicht auf der Rechnung<textarea name="notes" rows="2">${escape(original.notes||'')}</textarea></label></details><label class="invoice-confirm"><input type="checkbox" name="domestic_service_confirmed" ${original.domestic_service_confirmed?'checked':''}><span>Gewöhnliche Leistung in Österreich<small>Keine Auslandsleistung, Reverse Charge, Anzahlung, Schlussrechnung oder strukturierte Rechnung an den Bund.</small></span></label><p class="invoice-hint">${mode==='small_business'?'Kleinunternehmermodus aus zentralen Einstellungen.':mode==='standard'?'Umsatzsteuerpflichtiger Modus aus zentralen Einstellungen.':'Steuermodus vor Abschluss zentral bestätigen.'} Die endgültige Nummer wird erst beim Abschluss vergeben.</p></section><section data-section="lines" hidden><div class="invoice-section-title"><h3>Leistungen & Preise</h3><button type="button" class="secondary" data-add-line>+ Position</button></div><details class="invoice-options" data-worklog-section><summary>+ Arbeitsaufzeichnungen übernehmen</summary><p class="invoice-hint">Nur offene Arbeiten des gewählten Kunden. Preise und Materialmengen bitte selbst prüfen.</p><label>Arbeit suchen<input type="search" data-worklog-search placeholder="Datum oder Tätigkeit"></label><div data-worklogs class="invoice-worklog-list"></div><button type="button" class="secondary" data-import>Auswahl übernehmen</button></details><div data-lines></div><button type="button" class="secondary" data-add-line>+ Weitere Position</button><button type="button" class="secondary" data-undo hidden>Entfernen rückgängig</button><p class="invoice-hint">Steuerdarstellung folgt den zentralen Einstellungen. Arbeitsstunden werden erst beim Abschluss als abgerechnet markiert.</p></section><section data-section="preview" hidden><h3>Rechnung prüfen</h3><p class="invoice-hint">So sieht dein Entwurf aus. Noch keine ausgestellte Rechnung.</p>${options.onPdf&&original.id?'<button type="button" class="secondary" data-pdf>Gespeicherten Entwurf als PDF prüfen</button><p class="invoice-hint">PDF zeigt den zuletzt gespeicherten Stand, nicht ungespeicherte Änderungen. Fehlende Pflichtangaben können die PDF-Prüfung verhindern.</p>':''}</section><p class="invoice-form-error" role="alert" hidden></p></div><aside class="invoice-preview" data-preview></aside></div><div class="invoice-studio-footer"><div><small>Rechnungsbetrag</small><strong data-total></strong><span data-draft-status>Nur Entwurf · noch nicht abgeschlossen</span></div><button type="button" class="secondary" data-next>Weiter zu Leistungen</button></div>`;
    const q = selector => container.querySelector(selector), qa = selector => [...container.querySelectorAll(selector)];
    function positions() {
      return qa('.invoice-line').map(row=>({...Object.fromEntries([...row.querySelectorAll('[data-key]')].map(x=>[x.dataset.key,x.value])),worklog_id:row.dataset.worklogId||'',...Object.fromEntries([...row.querySelectorAll('[data-key]')].filter(x=>['quantity','price','discount','tax_rate'].includes(x.dataset.key)).map(x=>[x.dataset.key,x.value===''&&x.dataset.key!=='discount'?null:options.decimalQuantities&&x.dataset.key==='quantity'?x.value:Number(x.value)]))}));
    }
    function read() {
      const values = Object.fromEntries(qa('[name]').filter(x=>x.type!=='checkbox').map(x=>[x.name,x.value]));
      const lines = positions();
      return {...original,...values,positions:lines,tax_mode:mode,status:'Entwurf',domestic_service_confirmed:q('[name="domestic_service_confirmed"]').checked,
        worklog_ids:[...new Set(lines.map(p=>p.worklog_id).filter(Boolean))]};
    }
    const initial = () => JSON.stringify(read());
    let baseline;
    function update() {
      const draft = read(), customer = customers.find(c=>c.id===draft.customer_id) || {};
      try { q('[data-total]').textContent=euro(total(draft.positions,mode)); q('.invoice-form-error').hidden=true; }
      catch(error) { q('[data-total]').textContent='Bitte prüfen'; q('.invoice-form-error').textContent=error.message; q('.invoice-form-error').hidden=false; }
      q('[data-preview]').innerHTML=preview(draft,options.issuer||{},customer,options.logo||'');
      qa('.invoice-line').forEach((row,i)=>{row.querySelector('legend').textContent='Position '+(i+1);try{row.querySelector('[data-line-total]').textContent=euro(lineCents(draft.positions[i],mode)/100);}catch(_){row.querySelector('[data-line-total]').textContent='Bitte prüfen';}});
      if(baseline)q('[data-draft-status]').textContent=initial()===baseline?'Nur Entwurf · noch nicht abgeschlossen':'Änderungen noch nicht gespeichert';
    }
    function show(pane, focus = false) {
      active = pane;
      container.dataset.pane=pane;
      qa('[data-section]').forEach(x=>x.hidden=x.dataset.section!==pane);
      qa('.invoice-tabs [data-pane]').forEach(x=>x.setAttribute('aria-pressed',String(x.dataset.pane===pane)));
      q('[data-next]').textContent=pane==='details'?'Weiter zu Leistungen':pane==='lines'?'Vorschau prüfen':'Zurück zur Eingabe';
      if(focus)q(`[data-section="${pane}"] h3`)?.scrollIntoView({block:'nearest'});
    }
    function add(p = {}, index = -1) {
      const row=document.createElement('fieldset');row.className='invoice-line';row.dataset.worklogId=p.worklog_id||'';
      row.innerHTML=`<legend>Position</legend><label class="invoice-line-title">Leistung / Material<textarea data-key="title" rows="2" required>${escape(p.title||'')}</textarea></label><div class="invoice-field-grid"><label>Menge<input data-key="quantity" type="number" min="0.00000001" step="any" value="${escape(p.quantity===null?'':p.quantity??1)}" required></label><label>Einheit<input data-key="unit" value="${escape(p.unit||'Stk.')}" list="invoice-units" required></label><label>Einzelpreis (€)<input data-key="price" type="number" min="0" step="any" value="${escape(p.price??'')}" required></label><div class="invoice-line-sum"><small>Positionsbetrag</small><strong data-line-total></strong></div></div><details class="invoice-options" ${(p.description||Number(p.discount))?'open':''}><summary>Beschreibung & Rabatt</summary><label>Beschreibung<textarea data-key="description" rows="2">${escape(p.description||'')}</textarea></label><label>Rabatt (%)<input data-key="discount" type="number" min="0" max="100" step="any" value="${escape(p.discount??0)}"></label></details><label data-tax-field ${mode==='standard'?'':'hidden'}>USt (%)<input data-key="tax_rate" type="number" min="0" max="100" step="any" value="${escape(p.tax_rate??(mode==='standard'?'':0))}"></label><div class="invoice-line-footer"><small>${p.worklog_id?'Mit Arbeitsaufzeichnung verknüpft':'Manuell erfasste Position'}</small><button type="button" class="invoice-remove" data-remove-line aria-label="Diese Position entfernen">Entfernen</button></div>`;
      const rows=qa('.invoice-line');if(index>=0&&rows[index])rows[index].before(row);else q('[data-lines]').append(row);
      const unit=value=>['h','std','std.','stunde','stunden'].includes(String(value||'').toLowerCase())?'h':String(value||'').toLowerCase(),templates=(options.priceTemplates||[]).filter(t=>unit(t.unit)===unit(p.unit||'Stk.'));
      if(templates.length){const label=document.createElement('label');label.textContent='Freigegebenen Preisbaustein verwenden';const select=document.createElement('select');select.dataset.invoicePriceTemplate='true';select.add(new Option('Preis selbst ergänzen oder Baustein wählen',''));for(const t of templates)select.add(new Option(t.title+' · '+euro(t.default_price)+' / '+t.unit,t.id));select.onchange=()=>{const t=templates.find(t=>t.id===select.value);if(!t)return;row.querySelector('[data-key="price"]').value=t.default_price;if(t.tax_rate!==undefined&&t.tax_rate!==null)row.querySelector('[data-key="tax_rate"]').value=t.tax_rate;update();};label.append(select);row.querySelector('.invoice-field-grid').after(label);}
      row.querySelector('[data-remove-line]').onclick=()=>{removed={position:positions()[qa('.invoice-line').indexOf(row)],index:qa('.invoice-line').indexOf(row)};row.remove();q('[data-undo]').hidden=false;update();showWorklogs();};
      update(); return row;
    }
    function renderOrders(selected='') {
      const customer=q('[name="customer_id"]').value;
      q('[name="order_id"]').innerHTML='<option value="">Ohne Auftrag</option>'+orders.filter(o=>o.customer_id===customer).map(o=>`<option value="${escape(o.id)}" ${o.id===selected?'selected':''}>${escape(o.title)}</option>`).join('');
      const c=customers.find(x=>x.id===customer);
      q('[data-recipient]').textContent=c?[c.name,c.street,[c.postal_code,c.city].filter(Boolean).join(' '),c.email].filter(Boolean).join('\n'):'Wähle einen Kunden, um seine Rechnungsadresse zu übernehmen.';
    }
    function showWorklogs() {
      const taken=new Set(positions().map(p=>p.worklog_id).filter(Boolean)), term=q('[data-worklog-search]').value.toLocaleLowerCase('de-AT');
      const rows=worklogs.filter(w=>(w.billing_customer_id||w.customer_id)===q('[name="customer_id"]').value&&!w.billed&&!taken.has(w.id)&&!options.reservedWorklogIds?.has(w.id)).sort((a,b)=>String(b.work_date).localeCompare(String(a.work_date)));
      q('[data-worklogs]').innerHTML=rows.map(w=>`<label data-worklog-text="${escape([w.work_date,date(w.work_date),w.activity].join(' ').toLocaleLowerCase('de-AT'))}"><input type="checkbox" value="${escape(w.id)}"><span><strong>${date(w.work_date)} · ${escape(root.InvoiceFlow.quantityLabel(w))}</strong><small>${escape(w.activity)}</small></span></label>`).join('')||'<p class="invoice-hint">Keine weiteren offenen Arbeiten für diesen Kunden.</p>';
      q('[data-worklogs]').querySelectorAll('label').forEach(x=>x.hidden=!x.dataset.worklogText.includes(term));
    }
    (original.positions || [{}]).forEach(p=>add(p));
    q('[data-lines]').insertAdjacentHTML('beforeend','<datalist id="invoice-units"><option value="Std."><option value="Stk."><option value="km"><option value="m²"><option value="m³"><option value="pauschal"></datalist>');
    renderOrders(original.order_id);showWorklogs();show('details');baseline=initial();update();
    let previousCustomer=q('[name="customer_id"]').value;
    q('[name="customer_id"]').onchange=()=>{
      const next=q('[name="customer_id"]').value;
      if(next===previousCustomer)return;
      if(positions().some(p=>p.worklog_id)&&!confirm('Kunde wechseln? Verknüpfte Arbeitspositionen werden aus diesem Entwurf entfernt. Die Arbeitsaufzeichnungen bleiben erhalten.')){q('[name="customer_id"]').value=previousCustomer;return;}
      qa('.invoice-line').filter(x=>x.dataset.worklogId).forEach(x=>x.remove());removed=null;q('[data-undo]').hidden=true;
      previousCustomer=next;renderOrders();showWorklogs();update();
    };
    q('[name="tax_mode"]').onchange=()=>{const next=q('[name="tax_mode"]').value;if(next!=="standard"&&positions().some(p=>p.tax_rate!==0)&&!confirm('Steuermodus ändern und die Steuersätze dieses Entwurfs auf 0 setzen? Festgeschriebene Belege bleiben erhalten.')){q('[name="tax_mode"]').value=mode;return;}mode=next;qa('[data-tax-field]').forEach(label=>{label.hidden=mode!=='standard';if(mode!=='standard')label.querySelector('input').value='0';});update();};
    q('[data-customer-search]').oninput=event=>{const term=event.target.value.toLocaleLowerCase('de-AT');[...q('[name="customer_id"]').options].forEach((o,i)=>o.hidden=i>0&&!o.textContent.toLocaleLowerCase('de-AT').includes(term));};
    q('[data-worklog-search]').oninput=event=>q('[data-worklogs]').querySelectorAll('label').forEach(x=>x.hidden=!x.dataset.worklogText.includes(event.target.value.toLocaleLowerCase('de-AT')));
    q('[data-add-customer]').onclick=()=>{
      if(positions().some(p=>p.worklog_id)&&!confirm('Neuen Kunden für diese Rechnung anlegen? Verknüpfte Arbeiten gehören zum bisherigen Kunden und werden nach der Kundenanlage aus diesem Entwurf entfernt.'))return;
      options.onAddCustomer?.(read());
    };
    q('[data-add-customer]').hidden=!options.onAddCustomer;
    qa('[data-add-line]').forEach(button=>button.onclick=()=>{const row=add();row.querySelector('textarea').focus();});
    q('[data-undo]').onclick=()=>{if(removed){add(removed.position,removed.index);removed=null;q('[data-undo]').hidden=true;showWorklogs();}};
    q('[data-import]').onclick=()=>{
      try {
        const rows=[...q('[data-worklogs]').querySelectorAll('input:checked')].map(x=>worklogs.find(w=>w.id===x.value));
        const prepared=root.InvoiceFlow.fromWorklogs(rows.map(w=>({...w,work_customer_name:customers.find(c=>c.id===w.customer_id)?.name||w.customer_name})),positions());
        qa('.invoice-line').filter(x=>!x.querySelector('[data-key="title"]').value.trim()).forEach(x=>x.remove());
        prepared.positions.forEach(p=>add(p));
        const dates=worklogs.filter(w=>positions().some(p=>p.worklog_id===w.id)).map(w=>w.work_date).sort();
        q('[name="service_start"]').value=dates[0]||'';q('[name="service_end"]').value=dates.at(-1)||'';
        update();showWorklogs();notify('Arbeiten übernommen. Bitte Preise und Materialmengen prüfen.');
      }catch(error){notify(error.message,true);}
    };
    qa('[data-pane]').forEach(button=>button.onclick=()=>show(button.dataset.pane,true));
    q('[data-next]').onclick=()=>show(active==='details'?'lines':active==='lines'?'preview':'details',true);
    q('[data-pdf]')?.addEventListener('click',()=>options.onPdf?.(original.id));
    container.addEventListener('input',update);container.addEventListener('change',update);
    return {
      read, positions, total:()=>total(positions(),mode), isDirty:()=>initial()!==baseline,
      back:()=>{if(active==='details')return false;show(active==='preview'?'lines':'details');return true;},
      validate:()=>{
        const draft=read();
        if(!draft.customer_id)throw new Error('Bitte einen Kunden auswählen.');
        if(!draft.positions.length||draft.positions.length>200||draft.positions.some(p=>!p.title.trim()||!p.unit.trim()))throw new Error('Bitte 1 bis 200 Positionen mit Bezeichnung und Einheit erfassen.');
        total(draft.positions,mode);
        if(!Number.isInteger(Number(draft.payment_days))||Number(draft.payment_days)<0||Number(draft.payment_days)>365)throw new Error('Zahlungsziel: ganze Tage zwischen 0 und 365.');
        if(draft.service_start&&draft.service_end&&draft.service_end<draft.service_start)throw new Error('Leistungsende darf nicht vor Leistungsbeginn liegen.');
        return {...draft,amount:total(draft.positions,mode)};
      },
      revealInvalid:()=>{const invalid=container.querySelector('input:invalid,select:invalid,textarea:invalid');if(invalid){show(invalid.closest('[data-section]')?.dataset.section||'lines');invalid.focus();invalid.reportValidity();}},
      setBusy:value=>{busy=value;qa('button,input,select,textarea').forEach(x=>x.disabled=busy);},
    };
  }
  root.InvoiceStudio={mount,preview,lineCents,total,dueDate};
})(typeof globalThis!=='undefined'?globalThis:window);

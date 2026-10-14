const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

const state = {
  view: 'dashboard', dashboard: {}, customers: [], orders: [], worklogs: [],
  invoices: [], photos: [], settings: {}, editorType: null, preset: {},
  filters: {term: '', worklogTerm: '', customerId: 'all', period: 'all', billed: 'all'}, worklogLimit: 50, saving: false,
  returnToEditor: null,
};

const titles = {
  dashboard: 'Übersicht', customers: 'Kunden', orders: 'Projekte & Aufträge',
  worklogs: 'Cloudname_189a14e78c', photos: 'Fotos', invoices: 'Rechnungen',
  customer: 'Kundenansicht',
};

const entityConfig = {
  customer: { url: '/api/customers', title: 'Kunde anlegen' },
  order: { url: '/api/orders', title: 'Auftrag anlegen' },
  worklog: { url: '/api/worklogs', title: 'Arbeitszeit erfassen' },
  invoice: { url: '/api/invoices', title: 'Rechnung erfassen' },
  photo: { url: '/api/photos', title: 'Foto zuordnen' },
};

function escapeHtml(value = '') {
  return String(value).replace(/[&<>'"]/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]));
}

function customerMapsAction(item) {
  const address = [item.street, item.postal_code, item.city, item.country].filter(Boolean).join(', ');
  return address ? `<a class="secondary small" href="https://www.google.com/maps/search/?api=1&amp;query=${encodeURIComponent(address)}" target="_blank" rel="noopener noreferrer">⌖ Google Maps</a>` : '';
}

function id(prefix) {
  return `${prefix}_${crypto.randomUUID().replaceAll('-', '')}`;
}

function localDate(value = new Date()) {
  return `${value.getFullYear()}-${String(value.getMonth()+1).padStart(2,'0')}-${String(value.getDate()).padStart(2,'0')}`;
}

function overlayQueuedRecords() {
  const collections = {customer:'customers',order:'orders',worklog:'worklogs',invoice:'invoices',photo:'photos'};
  const queue=readQueue(false);
  for (const action of queue) {
    const collection=collections[action.kind], payload=action.payload;
    if (!collection || !payload?.id) continue;
    const index=state[collection].findIndex(item=>item.id===payload.id);
    const record={...(state[collection][index]||{}),...payload,pending:true};
    record.customer_name=state.customers.find(c=>c.id===record.customer_id)?.name||record.customer_name;
    record.order_title=state.orders.find(o=>o.id===record.order_id)?.title||record.order_title;
    if(index<0)state[collection].push(record);else state[collection][index]=record;
  }
  if(queue.length){
    const sum=rows=>Math.round(rows.reduce((total,row)=>total+Number(row.hours||0),0)*100)/100;
    Object.assign(state.dashboard,{
      customers:state.customers.filter(c=>c.active).length,
      open_orders:state.orders.filter(o=>!['Erledigt','Abgerechnet'].includes(o.status)).length,
      unbilled_hours:sum(state.worklogs.filter(w=>!w.billed)),
      open_invoices:Math.round(state.invoices.filter(i=>['Gestellt','Offen','Fällig'].includes(i.status)).reduce((total,i)=>total+Number(i.amount||0),0)*100)/100,
      today_logs:state.worklogs.filter(w=>w.work_date===localDate()).length,
      recent:[...state.worklogs].sort((a,b)=>String(b.work_date||'').localeCompare(String(a.work_date||''))||String(b.created_at||'').localeCompare(String(a.created_at||''))).slice(0,8)
    });
  }
}

function formatDate(value) {
  if (!value) return '–';
  const [year, month, day] = value.slice(0, 10).split('-');
  return `${day}.${month}.${year}`;
}

function money(value) {
  return new Intl.NumberFormat('de-AT', {style:'currency', currency:'EUR'}).format(Number(value || 0));
}

function status(value = '') {
  return `<span class="status ${escapeHtml(value.toLowerCase())}">${escapeHtml(value || '–')}</span>`;
}

function notice(message, isError = false) {
  const box = $('#notice');
  box.textContent = message;
  box.className = `notice${isError ? ' error' : ''}`;
  box.hidden = false;
  clearTimeout(notice.timer);
  notice.timer = setTimeout(() => box.hidden = true, 5000);
}

async function api(url, options = {}) {
  let response = await fetch(url, {
    headers: {'Content-Type':'application/json', ...(options.headers || {})}, ...options,
  });
  if (response.status === 401 && !options._retried) {
    const pin = window.prompt('Bitte den sechsstelligen Zugangscode aus dem Startfenster am Laptop eingeben:');
    if (!pin) throw new Error('Zugangscode erforderlich.');
    const auth = await fetch('/api/auth', {
      method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({pin}),
    });
    if (!auth.ok) throw new Error('Der Zugangscode ist nicht korrekt.');
    const retry = {...options, _retried:true};
    delete retry._retried;
    response = await fetch(url, {
      headers: {'Content-Type':'application/json', ...(retry.headers || {})}, ...retry,
    });
  }
  const result = await response.json().catch(() => ({}));
  if (!response.ok) { const error = new Error(result.error || ('Fehler ' + response.status)); error.code = result.code; error.matches = result.matches || []; throw error; }
  return result;
}

function cachedState() {
  try { return JSON.parse(localStorage.getItem('haus-garten-cache') || '{}'); }
  catch { return {}; }
}

function saveCache() {
  const snapshot = {};
  for (const key of ['dashboard','customers','orders','worklogs','invoices','photos','settings']) snapshot[key] = state[key];
  localStorage.setItem('haus-garten-cache', JSON.stringify(snapshot));
}

function queueAction(kind, payload) {
  const queue = readQueue();
  queue.push({client_id: crypto.randomUUID(), kind, payload});
  localStorage.setItem('haus-garten-queue', JSON.stringify(queue));
  updateOnlineState();
}

async function saveEntity(kind, payload) {
  const prefixes = {customer:'cus', order:'ord', worklog:'wrk', invoice:'inv', photo:'pho'};
  payload.id ||= id(prefixes[kind]);
  try {
    return await api(entityConfig[kind].url, {method:'POST', body:JSON.stringify(payload)});
  } catch (error) {
    if (!navigator.onLine || error instanceof TypeError) {
      queueAction(kind, payload);
      notice('Offline gespeichert. Der Eintrag wird später synchronisiert.');
      return payload;
    }
    throw error;
  }
}

async function synchronize() {
  if (state.syncing) return;
  let queue;
  try { queue = readQueue(); } catch(error) { notice(error.message,true); return; }
  if (!queue.length) {
    notice('Alle Einträge sind synchronisiert.');
    return;
  }
  try {
    state.syncing = true;
    const response = await api('/api/sync', {method:'POST', body:JSON.stringify({actions:queue})});
    const failed = response.results.filter(item => !item.ok);
    const successfulIds = new Set(response.results.filter(item=>item.ok).map(item=>item.client_id));
    // Keep edits added while this request was in flight and unacknowledged actions.
    const current = readQueue();
    localStorage.setItem('haus-garten-queue', JSON.stringify(current.filter(item => !successfulIds.has(item.client_id))));
    if (failed.length) notice(`${failed.length} Einträge benötigen Kontrolle.`, true);
    else notice(`${queue.length} mobile Einträge wurden synchronisiert.`);
    await loadAll();
  } catch (error) {
    notice('Laptop nicht erreichbar. Die Daten bleiben sicher vorgemerkt.', true);
  }
  finally { state.syncing = false; }
  updateOnlineState();
}

function updateOnlineState() {
  const pending = readQueue(false).length;
  $('#online-dot').classList.toggle('offline', !navigator.onLine || pending > 0);
  $('#online-text').textContent = state.queueError ? 'Abgleich prüfen' : !navigator.onLine ? 'Offline' : pending ? `${pending} nicht synchronisiert` : 'Verbunden';
}

function readQueue(strict = true) {
  try {
    const queue=JSON.parse(localStorage.getItem('haus-garten-queue') || '[]');
    if(!Array.isArray(queue)||queue.some(action=>!action||typeof action.client_id!=='string'||typeof action.kind!=='string'||!action.payload||typeof action.payload!=='object'||Array.isArray(action.payload)))throw new Error();
    state.queueError=false;return queue;
  } catch(error) {
    state.queueError=true;
    const message='Die lokalen Abgleichdaten benötigen Prüfung. Sie bleiben unverändert; bitte nicht den Browserspeicher löschen.';
    if(strict)throw new Error(message);
    notice(message,true);return [];
  }
}

async function loadAll() {
  try {
    const [dashboard, customers, orders, worklogs, invoices, photos, settings] = await Promise.all([
      api('/api/dashboard'), api('/api/customers'), api('/api/orders'), api('/api/worklogs'),
      api('/api/invoices'), api('/api/photos'), api('/api/settings'),
    ]);
    Object.assign(state, {dashboard, customers, orders, worklogs, invoices, photos, settings});
    saveCache();
  } catch (error) {
    Object.assign(state, cachedState());
    notice('Offline: zuletzt gespeicherter Stand wird angezeigt.', true);
  }
  overlayQueuedRecords();
  renderAll();
  updateOnlineState();
}

function table(headers, body) {
  if (!body.length) return $('#empty-template').innerHTML;
  return `<table class="data-table"><thead><tr>${headers.map(item => `<th>${item}</th>`).join('')}</tr></thead><tbody>${body.join('')}</tbody></table>`;
}

function renderDashboard() {
  const data = state.dashboard || {};
  const metrics = [
    ['Aktive Kunden', data.customers || 0, 'Stammdaten'],
    ['Offene Aufträge', data.open_orders || 0, 'zu bearbeiten'],
    ['Nicht abgerechnet', `${Number(data.unbilled_hours || 0).toLocaleString('de-AT')} h`, 'Arbeitszeit'],
    ['Offene Rechnungen', money(data.open_invoices), 'Zahlung ausständig'],
  ];
  $('#metrics').innerHTML = metrics.map(([label,value,small]) => `<div class="metric"><span class="label">${label}</span><strong>${value}</strong><small>${small}</small></div>`).join('');
  const rows = (data.recent || []).map(item => `<tr><td>${formatDate(item.work_date)}</td><td><strong>${escapeHtml(item.customer_name)}</strong></td><td>${escapeHtml(item.activity)}</td><td>${Number(item.hours || 0).toLocaleString('de-AT')} h</td><td>${item.billed ? status('Abgerechnet') : status('Offen')}</td></tr>`);
  $('#recent-worklogs').innerHTML = table(['Datum','Kunde','Tätigkeit','Stunden','Status'], rows);
}

function renderCustomers(items = state.customers || []) {
  const container = $('#customers-table');
  $('#customer-count').textContent = `${items.length} ${items.length === 1 ? 'Kunde' : 'Kunden'}`;
  if (!items.length) { container.innerHTML = $('#empty-template').innerHTML; return; }
  const workByCustomer = new Map();
  const photosByCustomer = new Map();
  for (const photo of state.photos) { const list = photosByCustomer.get(photo.customer_id) || []; list.push(photo); photosByCustomer.set(photo.customer_id, list); }
  for (const log of state.worklogs) {
    const list = workByCustomer.get(log.customer_id) || [];
    list.push(log); workByCustomer.set(log.customer_id, list);
  }
  container.innerHTML = items.map(item => {
    const address = [item.street,item.postal_code,item.city].filter(Boolean).join(', ');
    const phone = String(item.phone || '').trim().replace(/[^\d+]/g, '');
    const logs = (workByCustomer.get(item.id) || []).sort((a,b) => String(b.work_date).localeCompare(String(a.work_date)) || String(b.start_time || '').localeCompare(String(a.start_time || '')));
    const avatarPhoto = (photosByCustomer.get(item.id) || []).find(photo => /^(profilbild|profil|profile)$/i.test(String(photo.caption || '').trim()));
    const initials = String(item.name || '?').trim().split(/\s+/).slice(0,2).map(part => part[0] || '').join('').toLocaleUpperCase('de-AT');
    const avatar = avatarPhoto ? `<img class="customer-avatar" src="${escapeHtml(avatarPhoto.url)}" alt="Profilbild ${escapeHtml(item.name)}" loading="lazy">` : `<span class="customer-avatar customer-avatar-placeholder" aria-hidden="true">${escapeHtml(initials || 'K')}</span>`;
    const recent = logs.slice(0,2).map(log => `<div class="customer-worklog"><div><strong>${formatDate(log.work_date)}</strong><span>${Number(log.hours || 0).toLocaleString('de-AT')} h</span></div><p>${escapeHtml(log.activity || 'Ohne Tätigkeitsangabe')}</p>${log.order_title ? `<small>${escapeHtml(log.order_title)}</small>` : ''}</div>`).join('');
    return `<article class="customer-card${item.active ? '' : ' is-archived'}"><button type="button" class="customer-profile" data-customer-detail="${escapeHtml(item.id)}" aria-label="Profil und Historie von ${escapeHtml(item.name)} öffnen">${avatar}<span class="customer-profile-copy"><strong>${escapeHtml(item.name)}</strong>${item.company && item.company !== item.name ? `<small>${escapeHtml(item.company)}</small>` : `<small>${escapeHtml(item.city || 'Kein Ort hinterlegt')}</small>`}</span><span class="customer-card-status">${status(item.active ? 'Aktiv' : 'Archiv')}</span></button><div class="customer-contact">${address ? `<span>⌖ ${escapeHtml(address)}</span>` : '<span class="muted">Keine Adresse hinterlegt</span>'}${item.phone ? `<a href="tel:${escapeHtml(phone)}">☎ ${escapeHtml(item.phone)}</a>` : ''}${item.email ? `<a href="mailto:${escapeHtml(item.email)}">✉ ${escapeHtml(item.email)}</a>` : ''}</div><section class="customer-history"><div class="customer-history-heading"><strong>Arbeitsaufzeichnungen</strong><span>${logs.length}</span></div>${recent || '<p class="muted customer-no-history">Noch keine Cloudname_189a14e78c</p>'}${logs.length > 2 ? `<button type="button" class="link-button customer-more" data-customer-detail="${escapeHtml(item.id)}">Alle ${logs.length} ansehen →</button>` : ''}</section><footer class="customer-card-actions"><button type="button" class="secondary small" data-edit-customer="${escapeHtml(item.id)}">Bearbeiten</button>${phone ? `<a class="secondary small" href="tel:${escapeHtml(phone)}">Anrufen</a>` : ''}${customerMapsAction(item)}<button type="button" class="quiet-action" data-remove="customer" data-id="${escapeHtml(item.id)}" aria-label="${item.active ? 'Kunde archivieren' : 'Kunden löschen'}">${item.active ? 'Archivieren' : 'Löschen'}</button></footer></article>`;
  }).join('');
}

function renderOrders() {
  const orders = filteredOrders();
  const photosByOrder = new Map();
  for (const photo of state.photos) { if (!photo.order_id) continue; const list = photosByOrder.get(photo.order_id) || []; list.push(photo); photosByOrder.set(photo.order_id, list); }
  $('#orders-grid').innerHTML = orders.length ? orders.map(item => {
    const photos = photosByOrder.get(item.id) || [];
    const previews = photos.length ? `<div class="order-photo-strip" aria-label="${photos.length} Auftragsfotos">${photos.slice(0,5).map(photo => `<img src="${escapeHtml(photo.url)}" alt="${escapeHtml(photo.caption || photo.original_name || 'Auftragsfoto')}" loading="lazy">`).join('')}${photos.length > 5 ? `<span class="photo-more">+${photos.length - 5}</span>` : ''}</div>` : '';
    return `<article class="order-card"><header><div><p class="customer-name">${escapeHtml(item.customer_name)}</p><h3>${escapeHtml(item.title)}</h3></div>${status(item.status)}</header><p>${escapeHtml(item.description || 'Keine Beschreibung')}</p>${previews}<div class="order-meta"><span>${formatDate(item.scheduled_date)}</span><strong>${Number(item.total_hours || 0).toLocaleString('de-AT')} h</strong></div><footer class="card-actions"><button class="secondary small" data-edit-order="${escapeHtml(item.id)}">Bearbeiten</button><button class="secondary small danger" data-remove="order" data-id="${escapeHtml(item.id)}">Löschen</button></footer></article>`;
  }).join('') : $('#empty-template').innerHTML;
}

function renderWorklogs() {
  const customerFilter = $('#worklog-customer-filter');
  if (customerFilter) {
    const selected = state.filters.customerId || 'all';
    customerFilter.innerHTML = `<option value="all">Alle Kunden</option>${state.customers.filter(item => item.active).map(item => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.name)}</option>`).join('')}`;
    customerFilter.value = [...customerFilter.options].some(option => option.value === selected) ? selected : 'all';
  }
  const filtered = filteredWorklogs().slice().sort((a,b) => String(b.work_date || '').localeCompare(String(a.work_date || '')) || String(b.start_time || '').localeCompare(String(a.start_time || '')));
  const totalHours = filtered.reduce((sum,item) => sum + Number(item.hours || 0), 0);
  $('#worklog-summary').textContent = `${filtered.length} ${filtered.length === 1 ? 'Eintrag' : 'Einträge'} · ${totalHours.toLocaleString('de-AT',{maximumFractionDigits:2})} Stunden`;
  const limit = Math.max(50, Number(state.worklogLimit) || 50), shown = filtered.slice(0, limit);
  const rows = shown.map(item => `<tr><td>${formatDate(item.work_date)}<br><span class="muted">${escapeHtml([item.start_time,item.end_time].filter(Boolean).join(' – '))}</span></td><td><strong>${escapeHtml(item.customer_name)}</strong><br><span class="muted">${escapeHtml(item.order_title || '')}</span></td><td>${escapeHtml(item.activity)}</td><td>${Number(item.hours || 0).toLocaleString('de-AT')} h</td><td>${Number(item.kilometers || 0).toLocaleString('de-AT')} km</td><td>${escapeHtml(item.material) || '–'}</td><td>${item.billed ? status('Abgerechnet') : status('Offen')}</td><td class="card-actions"><button class="secondary small" data-edit-worklog="${escapeHtml(item.id)}">Bearbeiten</button><button class="secondary small danger" data-remove="worklog" data-id="${escapeHtml(item.id)}">Löschen</button></td></tr>`);
  const more = filtered.length > shown.length ? `<div class="worklog-load-more"><span>${shown.length} von ${filtered.length} angezeigt</span><button class="secondary" id="worklog-load-more" type="button">Weitere 50 anzeigen</button></div>` : '';
  $('#worklogs-table').innerHTML = `${table(['Datum','Kunde / Auftrag','Tätigkeit','Stunden','Kilometer','Material','Abrechnung',''], rows)}${more}`;
  $$('#worklogs-table [data-edit-worklog]').forEach(button=>{
    const worklog=shown.find(w=>w.id===button.dataset.editWorklog);if(worklog?.billed)return;
    const invoiceButton=document.createElement('button');invoiceButton.className='secondary small';invoiceButton.dataset.invoiceWorklog=worklog.id;invoiceButton.textContent='Rechnung erstellen';button.after(invoiceButton);
  });
}

function searchText(item) { return Object.values(item || {}).filter(value => typeof value === 'string').join(' ').toLowerCase(); }
function periodMatch(date, period) {
  if (period === 'all' || !date) return true;
  const now = new Date(); const target = new Date(`${date}T12:00:00`);
  if (period === 'today') return localDate(target) === localDate(now);
  const start = new Date(now); start.setHours(0, 0, 0, 0);
  if (period === 'week') { const day = (start.getDay() + 6) % 7; start.setDate(start.getDate() - day); const end=new Date(start);end.setDate(end.getDate()+7);return target >= start && target < end; }
  if (period === 'month') return target.getFullYear() === now.getFullYear() && target.getMonth() === now.getMonth();
  return true;
}
function filteredOrders() {
  const term = state.filters.term; return state.orders.filter(item => !term || searchText(item).includes(term));
}
function filteredWorklogs() {
  const {term, worklogTerm, customerId, period, billed} = state.filters;
  return state.worklogs.filter(item => (!term || searchText(item).includes(term)) && (!worklogTerm || searchText(item).includes(worklogTerm)) && (!customerId || customerId === 'all' || item.customer_id === customerId) && periodMatch(item.work_date, period) && (billed === 'all' || Boolean(item.billed) === (billed === 'yes')));
}

function showInvoiceLayout(identifier) {
  const invoice=state.invoices.find(x=>x.id===identifier);
  if(!invoice?.positions)return;
  let dialog=$('#invoice-layout-dialog');
  if(!dialog){dialog=document.createElement('dialog');dialog.id='invoice-layout-dialog';dialog.className='invoice-preview-dialog';document.body.append(dialog);}
  const customer=state.customers.find(x=>x.id===invoice.customer_id)||{};
  dialog.innerHTML='<div class="invoice-preview-close"><strong>Rechnungsvorschau · Entwurf</strong><button type="button" class="secondary" data-close>Schließen</button></div>'+
    InvoiceStudio.preview(invoice,state.settings,customer,'/hafner-logo.png');
  dialog.showModal();
}
function renderInvoices() {
  const drafts=state.invoices.filter(x=>x.status==='Entwurf'&&!x.sevdesk_id&&!x.issued_at);
  const open=state.invoices.filter(x=>['Gestellt','Offen','Fällig'].includes(x.status));
  const prefs=state.invoiceFilters||{term:'',status:'all'};
  const sorted=state.invoices.slice().sort((a,b)=>String(b.invoice_date||'').localeCompare(String(a.invoice_date||''))||String(b.updated_at||'').localeCompare(String(a.updated_at||'')));
  $('#invoices-table').innerHTML = '<div class="invoice-list-summary"><span>'+drafts.length+' Entwürfe</span><span>'+open.length+' offene Rechnungen</span><span>Erfassen → Vorschau → Abschließen → Teilen</span></div>'+
    '<div class="invoice-filters"><label>Rechnung oder Kunde suchen<input id="invoice-search" type="search" placeholder="Name oder Rechnungsnummer" value="'+escapeHtml(prefs.term)+'"></label><label>Status<select id="invoice-filter"><option value="all">Alle</option>'+[...new Set(state.invoices.map(x=>x.status).filter(Boolean))].map(x=>'<option '+(x===prefs.status?'selected':'')+'>'+escapeHtml(x)+'</option>').join('')+'</select></label><button class="secondary" id="invoice-reset">Zurücksetzen</button></div>'+
    '<div class="invoice-overview">'+sorted.map(item=>{
      const draft=item.status==='Entwurf'&&!item.sevdesk_id&&!item.issued_at;
      return '<article class="invoice-list-row" data-invoice-id="'+escapeHtml(item.id)+'"><div><strong class="invoice-list-title">'+escapeHtml(item.customer_name||'Kunde')+'</strong><p>'+escapeHtml(draft?'Rechnungsentwurf':item.invoice_number)+' · '+formatDate(item.invoice_date)+'</p><span class="invoice-list-status '+(draft?'is-draft':item.status==='Gestellt'?'is-open':'')+'">'+escapeHtml(item.status)+'</span></div><div class="invoice-list-amount">'+money(item.amount)+'</div><div class="invoice-list-actions">'+
        (item.issued_at?'<a class="primary small" href="/api/invoice-pdf/'+encodeURIComponent(item.id)+'" target="_blank" rel="noopener">PDF öffnen</a><button class="secondary small" data-share-invoice="'+escapeHtml(item.id)+'" data-channel="email">E-Mail</button><button class="secondary small" data-share-invoice="'+escapeHtml(item.id)+'" data-channel="whatsapp">WhatsApp</button>':
        '<button class="'+(draft?'primary':'secondary')+' small" data-edit-invoice="'+escapeHtml(item.id)+'">'+(draft?'Bearbeiten':'Bestandsdaten')+'</button>'+
        (draft&&item.positions?'<button class="secondary small" data-invoice-layout="'+escapeHtml(item.id)+'">Vorschau</button><button class="secondary small" data-issue-invoice="'+escapeHtml(item.id)+'">Abschließen</button>':'')+
        (draft?'<button class="invoice-remove" data-remove="invoice" data-id="'+escapeHtml(item.id)+'">Entwurf löschen</button>':''))+'</div></article>';
    }).join('')+'</div><p id="invoice-empty" class="hint" hidden>Keine Rechnungen für diese Suche. Filter zurücksetzen oder eine neue Rechnung erstellen.</p>';
  const filter=()=>{
    const term=$('#invoice-search').value.trim().toLocaleLowerCase('de-AT'),status=$('#invoice-filter').value;
    state.invoiceFilters={term:$('#invoice-search').value,status};
    let visible=0;
    $$('.invoice-list-row').forEach(card=>{
      const invoice=state.invoices.find(x=>x.id===card.dataset.invoiceId);
      card.hidden=![invoice.customer_name,invoice.invoice_number].join(' ').toLocaleLowerCase('de-AT').includes(term)||(status!=='all'&&invoice.status!==status);
      if(!card.hidden)visible++;
    });
    $('#invoice-empty').hidden=visible>0;
  };
  $('#invoice-search').oninput=filter;$('#invoice-filter').onchange=filter;
  $('#invoice-reset').onclick=()=>{$('#invoice-search').value='';$('#invoice-filter').value='all';filter();};
  filter();
}

function renderPhotos() {
  $('#photos-grid').innerHTML = state.photos.length ? state.photos.map(item => `<article class="photo-card"><img src="${escapeHtml(item.url)}" alt="${escapeHtml(item.caption || item.original_name)}"><div><strong>${escapeHtml(item.customer_name)}</strong><p>${escapeHtml(item.order_title || item.caption || 'Nicht einem Auftrag zugeordnet')}</p>${item.customer_visible ? status('Kundensichtbar') : status('Intern')}</div></article>`).join('') : $('#empty-template').innerHTML;
}

function renderCustomerOptions() {
  const options = state.customers.filter(item => item.active).map(item => `<option value="${item.id}">${escapeHtml(item.name)}</option>`).join('');
  const select = $('#customer-view-select');
  const current = select.value;
  select.innerHTML = `<option value="">Kunde auswählen</option>${options}`;
  select.value = current;
  makeSelectSearchable(select, 'Kunden suchen');
}

function renderAll() {
  const term = state.filters.term;
  renderDashboard(); renderCustomers(state.customers.filter(item => !term || searchText(item).includes(term))); renderOrders(); renderWorklogs(); renderInvoices(); renderPhotos(); renderCustomerOptions();
}

function showView(name) {
  state.view = name;
  $$('.view').forEach(item => item.classList.toggle('active', item.id === `view-${name}`));
  $$('#navigation button').forEach(item => item.classList.toggle('active', item.dataset.view === name));
  $('#page-title').textContent = titles[name];
  $('#quick-button').hidden = name !== 'dashboard';
  $('#sidebar').classList.remove('open');
}

function customerOptions(selected = '') {
  return `<option value="">Bitte auswählen</option>${state.customers.filter(item => item.active || item.id === selected).map(item => `<option value="${item.id}" ${item.id === selected ? 'selected' : ''}>${escapeHtml(item.name)}${item.active?'':' · archiviert'}</option>`).join('')}`;
}

function orderOptions(customerId = '', selected = '') {
  return `<option value="">Ohne Auftrag</option>${state.orders.filter(item => !customerId || item.customer_id === customerId).map(item => `<option value="${item.id}" ${item.id === selected ? 'selected' : ''}>${escapeHtml(item.title)}</option>`).join('')}`;
}

function makeSelectSearchable(select, placeholder) {
  if (!select || select.dataset.searchable === 'true') return;
  const wrapper = document.createElement('div'); wrapper.className = 'searchable-select';
  select.parentNode.insertBefore(wrapper, select); wrapper.append(select);
  const search = document.createElement('input'); search.type = 'search'; search.className = 'select-search';
  search.placeholder = placeholder; search.setAttribute('aria-label', placeholder);
  wrapper.insertBefore(search, select); select.dataset.searchable = 'true';
  search.addEventListener('input', () => {
    const term = search.value.trim().toLocaleLowerCase('de-AT');
    [...select.options].forEach((option, index) => { option.hidden = index > 0 && !option.textContent.toLocaleLowerCase('de-AT').includes(term); });
  });
  select.addEventListener('change', () => { search.value = ''; [...select.options].forEach(option => option.hidden = false); });
}

function resetSelectSearch(select) {
  const search = select.parentElement?.querySelector('.select-search');
  if (search) { search.value = ''; [...select.options].forEach(option => option.hidden = false); }
}

function field(label, name, value = '', type = 'text', extra = '', full = false) {
  return `<label class="${full ? 'full' : ''}">${label}<input name="${name}" type="${type}" value="${escapeHtml(value ?? '')}" ${extra}></label>`;
}

function checkbox(label, name, checked = false) {
  return `<label class="checkbox full"><input name="${name}" type="checkbox" ${checked ? 'checked' : ''}> ${label}</label>`;
}

function openEditor(type, preset = {}) {
  state.invoiceEditor = null;
  $('#form-fields').classList.remove('invoice-studio');
  delete $('#form-fields').dataset.pane;
  $('#editor-dialog').classList.remove('invoice-editor-dialog');
  $('#editor-form').noValidate = false;
  $('#editor-form button[type="submit"]').textContent = 'Speichern';
  state.editorType = type; state.preset = preset;
  const editing = Boolean(preset?.id);
  const editTitles = {customer:'Kunde bearbeiten', order:'Auftrag bearbeiten', worklog:'Arbeitsaufzeichnung bearbeiten', invoice:'Rechnung bearbeiten', photo:'Foto bearbeiten'};
  $('#dialog-eyebrow').textContent = editing ? 'BEARBEITEN' : 'NEUER EINTRAG';
  $('#dialog-title').textContent = editing ? editTitles[type] : entityConfig[type].title;
  const today = localDate();
  let html = '';
  if (type === 'customer') html = [
    `<div class="full form-section-heading"><h3>Stammdaten</h3><p>Name ist das einzige Pflichtfeld.</p></div>`,
    field('Name / Anzeigename*','name',preset.name,'text','required',true),
    field('Firma','company',preset.company), field('Vorname','first_name',preset.first_name), field('Nachname','last_name',preset.last_name),
    `<div class="full form-section-heading"><h3>Kontakt</h3><p>Telefon und E-Mail sind optional.</p></div>`,
    field('Telefon','phone',preset.phone,'tel'), field('E-Mail','email',preset.email,'email'),
    `<div class="full form-section-heading"><h3>Adresse</h3><p>Wird für Google Maps verwendet.</p></div>`,
    field('Straße','street',preset.street), field('PLZ','postal_code',preset.postal_code), field('Ort','city',preset.city),
    `<label class="full">Interne Hinweise<textarea name="notes" rows="3">${escapeHtml(preset.notes || '')}</textarea></label>`,
    checkbox('Kunde ist aktiv','active',preset.active !== false && preset.active !== 0),
  ].join('');
  if (type === 'order') html = [
    `<div class="full form-section-heading"><h3>1 · Kunde und Auftrag</h3><p>Der Auftrag bleibt immer einem Kunden zugeordnet.</p></div>`,
    `<div class="full order-customer-field"><label>Kunde*<select name="customer_id" required>${customerOptions(preset.customer_id)}</select></label><button type="button" class="secondary small" data-new-customer-for-order>+ Kunde hinzufügen</button></div>`,
    field('Auftragsname*','title',preset.title,'text','required'),
    `<div class="full form-section-heading"><h3>2 · Planung und Details</h3><p>Ergänze Termin, Ort oder eine kurze Beschreibung, wenn nötig.</p></div>`,
    field('Termin','scheduled_date',preset.scheduled_date,'date'),
    `<label>Status<select name="status">${[...new Set(['Geplant','In Arbeit','Wartet auf Material','Erledigt','Abgerechnet', preset.status].filter(Boolean))].map(value => `<option ${value === preset.status ? 'selected' : ''}>${value}</option>`).join('')}</select></label>`,
    field('Einsatzort','location',preset.location,'text','',true),
    `<label class="full">Beschreibung<textarea name="description" rows="3">${escapeHtml(preset.description || '')}</textarea></label>`,
    checkbox('In Kundenansicht zeigen','customer_visible',preset.customer_visible !== false && preset.customer_visible !== 0),
  ].join('');
  if (type === 'worklog') html = [
    `<div class="full form-section-heading"><h3>Einsatz</h3><p>Kunde und Auftrag gehören bei neuen Einträgen zusammen.</p></div>`,
    `<div class="worklog-customer-field"><label>Kunde*<select name="customer_id" required id="worklog-customer">${customerOptions(preset.customer_id)}</select></label><button type="button" class="secondary small" data-new-customer-for-worklog>+ Kunde</button></div>`,
    `<label>Projekt / Auftrag<select name="order_id" id="worklog-order">${orderOptions(preset.customer_id,preset.order_id)}</select></label>`,
    field('Datum*','work_date',preset.work_date || today,'date','required',true),
    `<div class="full form-section-heading"><h3>Arbeitszeit</h3><p>Effektivstunden werden aus Beginn, Ende und Pause berechnet.</p></div>`,
    field('Beginn','start_time',preset.start_time,'time'), field('Ende','end_time',preset.end_time,'time'),
    field('Pause (Minuten)','break_minutes',preset.break_minutes || 0,'number','min="0"'),
    `<div class="full duration-preview" id="worklog-duration-preview" aria-live="polite">Zeiten eingeben – die Arbeitsdauer wird hier angezeigt.</div>`,
    `<div class="full form-section-heading"><h3>Tätigkeit</h3><p>Kurz notieren, was vor Ort erledigt wurde.</p></div>`,
    field('Tätigkeit*','activity',preset.activity,'text','required',true),
    `<details class="full optional-fields"><summary>Weitere Angaben · optional</summary><div class="optional-fields-grid">`,
    field('Stunden manuell','hours',preset.start_time && preset.end_time ? '' : (preset.hours || ''),'number','min="0" step="0.01"'),
    field('Kilometer','kilometers',preset.kilometers || 0,'number','min="0" step="0.1"'),
    field('Ort','location',preset.location,'text','',true),
    `<label class="full">Material / Entsorgung<textarea name="material" rows="2">${escapeHtml(preset.material || '')}</textarea></label>`,
    `<label class="full">Interne Notiz<textarea name="notes" rows="2">${escapeHtml(preset.notes || '')}</textarea></label>`,
    checkbox('In Kundenansicht zeigen','customer_visible',preset.customer_visible !== false && preset.customer_visible !== 0), checkbox('Bereits abgerechnet','billed',preset.billed),
    `</div></details>`,
  ].join('');
  if (type === 'invoice') html = [
    `<label>Kunde*<select name="customer_id" required>${customerOptions(preset.customer_id)}</select></label>`,
    `<label>Auftrag<select name="order_id">${orderOptions(preset.customer_id,preset.order_id)}</select></label>`,
    field('Entwurfskennung – Nummer erst beim Abschluss','invoice_number',preset.invoice_number,'text','readonly'), field('Rechnungsdatum','invoice_date',preset.invoice_date || today,'date'),
    field('Leistungsbeginn','service_start',preset.service_start,'date'), field('Leistungsende','service_end',preset.service_end,'date'),
    field('Betrag in Euro','amount',preset.amount || 0,'number','min="0" step="0.01"'),
    `<label>Status<select name="status">${['Entwurf','Gestellt','Bezahlt','Storniert'].map(value => `<option ${value === preset.status ? 'selected' : ''}>${value}</option>`).join('')}</select></label>`,
    `<label class="full">Notiz<textarea name="notes" rows="3">${escapeHtml(preset.notes || '')}</textarea></label>`,
  ].join('');
  if (type === 'photo') html = [
    `<label>Kunde*<select name="customer_id" required id="photo-customer">${customerOptions(preset.customer_id)}</select></label>`,
    `<label>Auftrag<select name="order_id" id="photo-order">${orderOptions(preset.customer_id,preset.order_id)}</select></label>`,
    `<label class="full">Fotos*<input name="files" type="file" accept="image/*" multiple required><small>Bis zu 10 Fotos auswählen; alle werden dem Kunden/Auftrag zugeordnet.</small></label>`,
    field('Bildbeschreibung','caption',preset.caption,'text','',true), checkbox('In Kundenansicht zeigen','customer_visible',preset.customer_visible),
  ].join('');
  $('#form-fields').innerHTML = html;
  if (type === 'invoice' && (!preset.id || (preset.status === 'Entwurf' && !preset.sevdesk_id))) setupInvoiceDraft(preset);
  if (type === 'photo' && preset.customer_id) $('#photo-customer').value = preset.customer_id;
  if (!state.invoiceEditor) $$('select', $('#form-fields')).forEach(select => makeSelectSearchable(select, `Suchen: ${select.closest('label')?.childNodes[0]?.textContent.trim() || 'Auswahl'}`));
  $('#editor-dialog').showModal();
  if(type==='invoice'&&preset.id&&(preset.sevdesk_id||preset.issued_at)){
    $$('#form-fields input,#form-fields select,#form-fields textarea').forEach(input=>input.disabled=true);
    $('#editor-form button[type="submit"]').hidden=true;
  }else $('#editor-form button[type="submit"]').hidden=false;
  if (type === 'worklog') {
    const updateDuration = () => {
      const start = $('#form-fields [name="start_time"]')?.value, end = $('#form-fields [name="end_time"]')?.value;
      const pause = Number($('#form-fields [name="break_minutes"]')?.value || 0), output = $('#worklog-duration-preview');
      if (!start || !end) { output.textContent = 'Zeiten eingeben – die Arbeitsdauer wird hier angezeigt.'; return; }
      const [sh, sm] = start.split(':').map(Number), [eh, em] = end.split(':').map(Number);
      const minutes = eh * 60 + em - (sh * 60 + sm);
      if(minutes<=0||pause<0||pause>=minutes){output.textContent=minutes<=0?'Die Endzeit muss nach dem Beginn liegen.':'Bitte eine kürzere, gültige Pause eingeben.';return;}
      const net = minutes - pause;
      output.textContent = `Berechnete Arbeitszeit: ${(net / 60).toLocaleString('de-AT',{maximumFractionDigits:2})} Stunden`;
    };
    ['start_time','end_time','break_minutes'].forEach(name => $('#form-fields [name="' + name + '"]')?.addEventListener('input', updateDuration));
    updateDuration();
  }
  state.editorInitial = JSON.stringify(formObject($('#editor-form')));
  $('#worklog-customer')?.addEventListener('change', event => {
    const order = $('#worklog-order'); order.innerHTML = orderOptions(event.target.value); resetSelectSearch(order);
    const candidates = state.orders.filter(item => item.customer_id === event.target.value && !['Erledigt','Abgerechnet','Storniert'].includes(item.status));
    if (candidates.length === 1) { order.value = candidates[0].id; const location = $('#form-fields [name="location"]'); if (location && !location.value) location.value = candidates[0].location || ''; notice('Ein offener Auftrag wurde vorgeschlagen. Bitte Zuordnung prüfen.'); }
  });
  $('#worklog-order')?.addEventListener('change', event => {
    const selected = state.orders.find(item => item.id === event.target.value); if (!selected) return;
    const customer = $('#worklog-customer'); customer.value = selected.customer_id; resetSelectSearch(customer);
    const location = $('#form-fields [name="location"]'); if (location && !location.value) location.value = selected.location || '';
  });
  $('#photo-customer')?.addEventListener('change', event => { $('#photo-order').innerHTML = orderOptions(event.target.value); resetSelectSearch($('#photo-order')); });
}

function formObject(form) {
  const data = Object.fromEntries(new FormData(form));
  $$('input[type="checkbox"]', form).forEach(input => data[input.name] = input.checked);
  return data;
}

function editorIsDirty() {
  if(!$('#editor-dialog').open)return false;
  return state.invoiceEditor ? state.invoiceEditor.isDirty() :
    state.editorInitial !== JSON.stringify(formObject($('#editor-form')));
}

async function compressedPhotoData(file) {
  const source = URL.createObjectURL(file);
  try {
    const image = await new Promise((resolve, reject) => { const value = new Image(); value.onload = () => resolve(value); value.onerror = reject; value.src = source; });
    const scale = Math.min(1, 1600 / Math.max(image.width, image.height));
    const canvas = document.createElement('canvas'); canvas.width = Math.round(image.width * scale); canvas.height = Math.round(image.height * scale);
    canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL('image/jpeg', .78);
  } finally { URL.revokeObjectURL(source); }
}

function readInvoicePositions() {
  if (state.invoiceEditor) return state.invoiceEditor.positions();
  return [...document.querySelectorAll('#invoice-positions .invoice-position')].map(row =>
    ({...Object.fromEntries([...row.querySelectorAll('[data-position-field]')].map(input => [input.dataset.positionField, input.value])), worklog_id:row.dataset.worklogId || ''}));
}

function setupInvoiceDraft(preset) {
  $('#editor-dialog').classList.add('invoice-editor-dialog');
  $('#editor-form').noValidate = true;
  $('#editor-form button[type="submit"]').textContent = 'Entwurf speichern';
  state.invoiceEditor = InvoiceStudio.mount($('#form-fields'), {
    preset, customers:state.customers, orders:state.orders, worklogs:state.worklogs,
    issuer:state.settings, logo:'/hafner-logo.png', notify:notice,
    onAddCustomer:draft => {
      state.returnToEditor={type:'invoice',payload:draft};
      $('#editor-dialog').close(); openEditor('customer');
    },
    onPdf:identifier => window.open('/api/invoice-preview/'+encodeURIComponent(identifier),'_blank','noopener'),
  });
}

function returnFromInvoiceCustomer() {
  if(state.editorType!=='customer'||state.returnToEditor?.type!=='invoice')return false;
  if(!confirm('Kundenanlage abbrechen? Ungespeicherte Kundendaten werden verworfen; dein Rechnungsentwurf bleibt erhalten.'))return true;
  const draft=state.returnToEditor;state.returnToEditor=null;
  $('#editor-dialog').close();openEditor(draft.type,draft.payload);return true;
}

async function submitEditor(event) {
  event.preventDefault();
  if (state.saving) return;
  const form = event.currentTarget;
  const payload = {...state.preset, ...formObject(form)};
  if (!payload.id && (state.editorType === 'customer' || state.editorType === 'order')) payload._new_record = true;
  if (state.editorType === 'worklog') {
    payload._require_order = !payload.id;
    if (!payload.order_id && !payload.id) { notice('Bitte zuerst einen Auftrag auswählen oder über + anlegen.', true); return; }
    if (Number(payload.break_minutes || 0) < 0 || Number(payload.kilometers || 0) < 0 || Number(payload.hours || 0) < 0) { notice('Pause, Stunden und Kilometer dürfen nicht negativ sein.', true); return; }
    if (payload.start_time && payload.end_time && payload.end_time < payload.start_time) { notice('Die Endzeit liegt vor der Beginnzeit. Bitte prüfen.', true); return; }
  }
  if (state.editorType === 'invoice' && state.invoiceEditor) {
    try {
      Object.assign(payload, state.invoiceEditor.validate());
      if (!form.checkValidity()) { state.invoiceEditor.revealInvalid(); return; }
    } catch(error) { state.invoiceEditor.revealInvalid(); notice(error.message, true); return; }
    if (!payload.invoice_number) payload.invoice_number = `Entwurf-${payload.id || Date.now()}`;
  }
  let photoCount = 0;
  state.saving = true;
  state.invoiceEditor?.setBusy(true);
  const submit = form.querySelector('button[type="submit"]'); if (submit) submit.disabled = true;
  try {
    if (state.editorType === 'photo') {
      const files = [...form.elements.files.files];
      if (!files.length) throw new Error('Bitte mindestens ein Foto auswählen.');
      if (files.length > 10) throw new Error('Bitte höchstens 10 Fotos gleichzeitig auswählen.');
      delete payload.files;
      const failures = [];
      for (const file of files) {
        try {
          const originalName = `${file.name.replace(/\.[^.]+$/, '') || 'foto'}.jpg`;
          await saveEntity('photo', {...payload, data: await compressedPhotoData(file), original_name: originalName});
          photoCount++;
        } catch (error) { failures.push(`${file.name}: ${error.message}`); }
      }
      if (!photoCount) throw new Error(failures[0] || 'Die Fotos konnten nicht gespeichert werden.');
      $('#editor-dialog').close();
      notice(failures.length ? `${photoCount} Fotos gespeichert; ${failures.length} fehlgeschlagen.` : `${photoCount} Fotos dem Auftrag zugeordnet.`, failures.length > 0);
      if (failures.length) console.warn('Nicht gespeicherte Auftragsfotos:', failures);
      await loadAll();
      return;
    }
    if (state.preset.source) payload.source = state.preset.source;
    const saved = await saveEntity(state.editorType, payload);
    $('#editor-dialog').close();
    notice('Eintrag wurde gespeichert.');
    await loadAll();
    if (state.editorType === 'customer' && state.returnToEditor) {
      const draft = state.returnToEditor; state.returnToEditor = null;
      const nextCustomer=saved.id || payload.id;
      const nextPayload={...draft.payload, customer_id:nextCustomer};
      if(draft.type==='invoice'&&draft.payload.customer_id!==nextCustomer){
        nextPayload.positions=(draft.payload.positions||[]).filter(p=>!p.worklog_id);
        nextPayload.worklog_ids=[];nextPayload.order_id=null;
      }
      openEditor(draft.type, nextPayload);
    }
  } catch (error) {
    if (error.code === 'DUPLICATE') {
      const first = error.matches && error.matches[0];
      const summary = first ? (first.title || first.name || 'Eintrag') + (first.scheduled_date ? ' · ' + formatDate(first.scheduled_date) : '') : '';
      const openExisting = window.confirm(error.message + '\n\n' + summary + '\n\nOK = bestehenden Eintrag öffnen\nAbbrechen = weitere Entscheidung');
      if (openExisting && first && first.id) {
        $('#editor-dialog').close();
        openEditor(state.editorType, state.editorType === 'order' ? state.orders.find(item => item.id === first.id) : state.customers.find(item => item.id === first.id));
      } else if (window.confirm('Trotzdem einen neuen Eintrag mit neuer ID erstellen?')) {
        payload.allow_duplicate = true;
        try { await saveEntity(state.editorType, payload); $('#editor-dialog').close(); notice('Eintrag trotz Ähnlichkeit gespeichert.'); await loadAll(); } catch (retry) { notice(retry.message, true); }
      }
    } else notice(error.message, true);
  }
  finally { state.saving = false; state.invoiceEditor?.setBusy(false); if (submit) submit.disabled = false; }
}

async function openCustomerDetail(customerId) {
  const customer = state.customers.find(item => item.id === customerId); if (!customer) return;
  const orders = state.orders.filter(item => item.customer_id === customerId);
  const worklogs = state.worklogs.filter(item => item.customer_id === customerId).sort((a,b) => String(b.work_date).localeCompare(String(a.work_date)) || String(b.start_time || '').localeCompare(String(a.start_time || '')));
  const dialog = $('#customer-detail-dialog');
  const dial = String(customer.phone || '').trim().replace(/[^\d+]/g, '');
  const address = [customer.street, customer.postal_code, customer.city, customer.country].filter(Boolean).join(', ');
  const customerPhotos = state.photos.filter(photo => photo.customer_id === customerId);
  const avatarPhoto = customerPhotos.find(photo => /^(profilbild|profil|profile)$/i.test(String(photo.caption || '').trim()));
  const initials = String(customer.name || '?').trim().split(/\s+/).slice(0,2).map(part => part[0] || '').join('').toLocaleUpperCase('de-AT');
  const avatar = avatarPhoto ? `<img class="detail-avatar" src="${escapeHtml(avatarPhoto.url)}" alt="Profilbild ${escapeHtml(customer.name)}">` : `<span class="detail-avatar customer-avatar-placeholder">${escapeHtml(initials || 'K')}</span>`;
  $('#customer-detail-content').innerHTML = `<div class="customer-detail-hero"><div class="customer-detail-identity">${avatar}<div><p class="eyebrow">KUNDENPROFIL</p><h2>${escapeHtml(customer.name)}</h2><p class="muted">${escapeHtml(customer.company && customer.company !== customer.name ? customer.company : address || 'Keine Adresse hinterlegt')}</p></div></div><div class="customer-detail-actions">${dial ? `<a class="primary" href="tel:${escapeHtml(dial)}">☎ Anrufen</a>` : ''}${customerMapsAction(customer)}<button class="secondary" data-new="photo" data-customer-id="${escapeHtml(customer.id)}">${avatarPhoto ? 'Bild ändern' : '+ Profilbild'}</button><button class="secondary" data-edit-customer="${escapeHtml(customer.id)}">Bearbeiten</button><button class="quiet-action" data-remove="customer" data-id="${escapeHtml(customer.id)}">${customer.active ? 'Archivieren' : 'Löschen'}</button></div></div><div class="customer-detail-contact">${address ? `<span>⌖ ${escapeHtml(address)}</span>` : ''}${customer.phone ? `<span>☎ ${escapeHtml(customer.phone)}</span>` : ''}${customer.email ? `<span>✉ ${escapeHtml(customer.email)}</span>` : ''}${!address && !customer.phone && !customer.email ? '<span class="muted">Noch keine Kontaktdaten hinterlegt</span>' : ''}</div><div class="detail-grid customer-detail-grid"><section><div class="section-title-row"><h3>Projekte & Aufträge</h3><button class="secondary small" data-new="order" data-customer-id="${escapeHtml(customer.id)}">+ Hinzufügen</button></div>${orders.map(item => `<article class="customer-order-row"><div><strong>${escapeHtml(item.title)}</strong><small>${status(item.status)}${item.scheduled_date ? ` · ${formatDate(item.scheduled_date)}` : ''}</small></div><button class="secondary small" data-edit-order="${escapeHtml(item.id)}">Bearbeiten</button></article>`).join('') || '<p class="muted">Noch keine Aufträge.</p>'}</section><section class="full"><div class="section-title-row"><div><h3>Arbeitsaufzeichnungen</h3><small class="muted">${worklogs.length} Einträge · ${worklogs.reduce((sum,item)=>sum+Number(item.hours||0),0).toLocaleString('de-AT')} Stunden gesamt</small></div><button class="primary small" data-new="worklog" data-customer-id="${escapeHtml(customer.id)}">+ Arbeit erfassen</button></div><div class="customer-worklog-list">${worklogs.map(item => `<article class="customer-worklog customer-worklog-detail"><div><strong>${formatDate(item.work_date)}${item.start_time ? ` · ${escapeHtml(item.start_time)}${item.end_time ? `–${escapeHtml(item.end_time)}` : ''}` : ''}</strong><span>${Number(item.hours || 0).toLocaleString('de-AT')} h</span></div><p>${escapeHtml(item.activity || 'Ohne Tätigkeitsangabe')}</p>${item.order_title ? `<small>Auftrag: ${escapeHtml(item.order_title)}</small>` : ''}${item.material ? `<small>Material: ${escapeHtml(item.material)}</small>` : ''}<footer><small>${item.billed ? 'Abgerechnet' : 'Nicht abgerechnet'}</small><button class="secondary small" data-edit-worklog="${escapeHtml(item.id)}">Bearbeiten</button></footer></article>`).join('') || '<p class="muted">Noch keine Cloudname_189a14e78c zugeordnet.</p>'}</div></section></div>`;
  dialog.showModal();
}

async function removeRecord(kind, recordId) {
  const labels = {customer:'Kunde', order:'Auftrag', worklog:'Arbeitsaufzeichnung', invoice:'Rechnung', photo:'Foto'};
  const prompt = kind === 'customer'
    ? 'Kunde archivieren? Verknüpfte Aufträge und Cloudname_189a14e78c bleiben erhalten.'
    : `Diesen Eintrag (${labels[kind] || 'Eintrag'}) wirklich löschen?`;
  if (!window.confirm(prompt)) return;
  try {
    const result = await api(`/api/${kind === 'customer' ? 'customers' : kind === 'order' ? 'orders' : kind === 'worklog' ? 'worklogs' : `${kind}s`}/${encodeURIComponent(recordId)}`, {method:'DELETE'});
    $('#customer-detail-dialog').close();
    notice(result.message || 'Eintrag entfernt.');
    await loadAll();
  } catch (error) { notice(error.message, true); }
}

async function showCustomerView(customerId) {
  const target = $('#customer-view-content');
  if (!customerId) { target.className = 'customer-preview empty-state'; target.textContent = 'Wähle einen Kunden aus. Interne Notizen und vertrauliche Daten werden hier nicht angezeigt.'; return; }
  try {
    const data = await api(`/api/customer-view/${encodeURIComponent(customerId)}`);
    target.className = 'customer-preview';
    target.innerHTML = `<div class="customer-cover"><p class="eyebrow">LEISTUNGSÜBERSICHT</p><h2>${escapeHtml(data.customer.name)}</h2><span class="muted">${escapeHtml(data.customer.city || '')}</span></div><div class="customer-sections"><section><h3>Ausgeführte Arbeiten</h3><div class="customer-list">${data.worklogs.length ? data.worklogs.map(item => `<div class="customer-item"><strong>${formatDate(item.work_date)} · ${Number(item.hours || 0).toLocaleString('de-AT')} h</strong><p>${escapeHtml(item.activity)}</p>${item.material ? `<small>Material: ${escapeHtml(item.material)}</small>` : ''}</div>`).join('') : '<p class="muted">Keine freigegebenen Arbeiten.</p>'}</div></section><section><h3>Fotos</h3><div class="customer-photos">${data.photos.length ? data.photos.map(item => `<figure><img src="${escapeHtml(item.url)}" alt=""><figcaption>${escapeHtml(item.caption || '')}</figcaption></figure>`).join('') : '<p class="muted">Keine freigegebenen Fotos.</p>'}</div></section></div>`;
  } catch (error) { notice(error.message, true); }
}

function openVoice() { $('#voice-dialog').showModal(); $('#voice-text').focus(); }

async function processVoice(event) {
  event.preventDefault();
  const text = $('#voice-text').value.trim();
  if (!text) return notice('Bitte zuerst einen Arbeitsbericht sprechen oder eingeben.', true);
  let parsed = {activity:text, work_date:localDate(), source:'Spracheingabe'};
  try { parsed = await api('/api/voice/parse', {method:'POST', body:JSON.stringify({text})}); } catch {}
  $('#voice-dialog').close(); openEditor('worklog', parsed);
}

function startRecognition() {
  if (window.AndroidSpeech?.start) {
    $('#voice-status').textContent = 'Android-Spracheingabe wird geöffnet …';
    window.AndroidSpeech.start();
    return;
  }
  const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!Recognition) return notice('Dieser Browser unterstützt keine direkte Spracheingabe. Bitte nutze die Mikrofontaste der Android-Tastatur.', true);
  const recognition = new Recognition(); recognition.lang = 'de-AT'; recognition.interimResults = true;
  $('#voice-status').textContent = 'Ich höre zu …';
  recognition.onresult = event => { $('#voice-text').value = [...event.results].map(result => result[0].transcript).join(' '); };
  recognition.onerror = () => $('#voice-status').textContent = 'Aufnahme fehlgeschlagen';
  recognition.onend = () => $('#voice-status').textContent = 'Aufnahme beendet';
  recognition.start();
}

window.acceptNativeSpeech = text => {
  $('#voice-text').value = text || '';
  $('#voice-status').textContent = 'Aufnahme beendet';
};

async function openSettings() {
  try { state.settings = await api('/api/settings'); } catch {}
  const form = $('#settings-form'); form.elements.backup_folder.value = state.settings.backup_folder || ''; form.elements.business_name.value = state.settings.business_name || ''; form.elements.home_address.value = state.settings.home_address || '';
  for (const key of ['owner_name','business_street','business_postal_code','business_city','business_legal_info','business_iban','business_bank','business_bic','business_phone','business_email','invoice_prefix']) form.elements[key].value = state.settings[key] || '';
  for (const key of ['invoice_issuer_confirmed','invoice_numbering_confirmed']) form.elements[key].checked = state.settings[key] === '1';
  $('#settings-dialog').showModal();
}

async function saveSettings(event) {
  event.preventDefault();
  try { const values=formObject(event.currentTarget); for(const key of ['invoice_issuer_confirmed','invoice_numbering_confirmed'])values[key]=values[key]?'1':'0'; state.settings = await api('/api/settings', {method:'POST', body:JSON.stringify(values)}); $('#settings-dialog').close(); notice('Einstellungen gespeichert.'); }
  catch (error) { notice(error.message, true); }
}

async function backup() {
  const folder = $('#settings-form').elements.backup_folder.value.trim();
  try { const result = await api('/api/backup', {method:'POST', body:JSON.stringify({folder})}); notice(`Sicherung erstellt: ${result.path}`); }
  catch (error) { notice(error.message, true); }
}

async function driveSync() {
  const folder = $('#settings-form').elements.backup_folder.value.trim();
  try {
    const result = await api('/api/drive-sync', {method:'POST', body:JSON.stringify({folder})});
    notice(result.ok ? `${result.imported} mobile Einträge übernommen. Drive-Stand aktualisiert.` : `${result.imported} Einträge übernommen; ${result.failed?.length||0} benötigen Prüfung. Nicht übernommene Daten bleiben erhalten.`, !result.ok);
    await loadAll();
  } catch (error) { notice(error.message, true); }
}

function bindEvents() {
  document.addEventListener('click', async event => {
    const layout=event.target.closest('[data-invoice-layout]');
    if(layout){showInvoiceLayout(layout.dataset.invoiceLayout);return;}
    const fromWorklog=event.target.closest('[data-invoice-worklog]');
    if(fromWorklog){try{const row=state.worklogs.find(w=>w.id===fromWorklog.dataset.invoiceWorklog);openEditor('invoice',InvoiceFlow.fromWorklogs([row]));}catch(error){notice(error.message,true);}return;}
    const issue = event.target.closest('[data-issue-invoice]');
    if(issue){
      if(!confirm('Geprüfte Rechnung verbindlich abschließen? Nummer und PDF werden festgeschrieben. Verknüpfte Arbeitsstunden werden abgerechnet. Danach ist kein direktes Bearbeiten oder Löschen möglich.'))return;
      issue.disabled=true;
      try{await api('/api/invoice-issue',{method:'POST',body:JSON.stringify({id:issue.dataset.issueInvoice,confirmed:true})});await loadAll();notice('Rechnung abgeschlossen. PDF öffnen oder teilen.');}catch(error){notice(error.message,true);}finally{issue.disabled=false;}
    }
    const share = event.target.closest('[data-share-invoice]');
    if(share){
      if(!confirm('Die Zustimmung des Kunden zur elektronischen Rechnung muss vorliegen. PDF an '+(share.dataset.channel==='whatsapp'?'WhatsApp':'dein E-Mail-Programm')+' übergeben? Versand und Empfänger bestätigst du selbst.'))return;
      try {
        const invoice=state.invoices.find(i=>i.id===share.dataset.shareInvoice), subject='Rechnung '+invoice.invoice_number;
        const response=await fetch('/api/invoice-pdf/'+encodeURIComponent(invoice.id));if(!response.ok)throw new Error('PDF konnte nicht geladen werden.');
        const file=new File([await response.blob()],invoice.invoice_number.replace(/[^A-Za-z0-9_-]/g,'-')+'.pdf',{type:'application/pdf'});
        if(navigator.canShare?.({files:[file]})){await navigator.share({files:[file],title:subject,text:'Guten Tag, anbei Ihre Rechnung. Mit freundlichen Grüßen, Hafner Cloudname_3455071506.'});notice('PDF übergeben. Die App bestätigt keine Zustellung.');}
        else {
          const url=URL.createObjectURL(file), download=document.createElement('a');download.href=url;download.download=file.name;download.click();setTimeout(()=>URL.revokeObjectURL(url),60000);
          const link=share.dataset.channel==='whatsapp'?'https://wa.me/?text='+encodeURIComponent('Guten Tag, anbei Ihre '+subject+'.'):'mailto:'+encodeURIComponent(invoice.recipient?.email||'')+'?subject='+encodeURIComponent(subject)+'&body='+encodeURIComponent('Guten Tag,\n\nanbei Ihre Rechnung. Bitte das heruntergeladene PDF anhängen.\n\nMit freundlichen Grüßen\nHafner Cloudname_3455071506');
          const action=document.createElement('a');action.href=link;action.target='_blank';action.rel='noopener';action.textContent=share.dataset.channel==='whatsapp'?'WhatsApp öffnen':'E-Mail öffnen';
          notice('PDF heruntergeladen. Öffne den Versand und füge dieses PDF als Anhang hinzu.');clearTimeout(notice.timer);$('#notice').append(' ',action);
        }
      } catch(error){if(error.name!=='AbortError')notice(error.message,true);}
    }
  });
  $('#navigation').addEventListener('click', event => { const button = event.target.closest('[data-view]'); if (button) showView(button.dataset.view); });
  document.addEventListener('click', event => {
    const addCustomerToOrder = event.target.closest('[data-new-customer-for-order]');
    const addCustomerToWorklog = event.target.closest('[data-new-customer-for-worklog]');
    const addCustomerToInvoice = event.target.closest('[data-new-customer-for-invoice]');
    if (addCustomerToOrder || addCustomerToWorklog || addCustomerToInvoice) {
      const draftPayload = {...state.preset,...formObject($('#editor-form'))};
      if(addCustomerToInvoice){draftPayload.positions=readInvoicePositions();draftPayload.worklog_ids=[...document.querySelectorAll('#invoice-worklogs input:checked')].map(input=>input.value);}
      state.returnToEditor = {type:addCustomerToWorklog ? 'worklog' : addCustomerToInvoice ? 'invoice' : 'order', payload:draftPayload};
      $('#editor-dialog').close(); openEditor('customer'); return;
    }
    const create = event.target.closest('[data-new]'); if (create) openEditor(create.dataset.new, create.dataset.customerId ? {customer_id:create.dataset.customerId} : {});
    const addType = event.target.closest('[data-add-type]');
    if (addType) { $('#add-dialog').close(); openEditor(addType.dataset.addType); }
    const go = event.target.closest('[data-go]'); if (go) showView(go.dataset.go);
    if (event.target.closest('[data-voice]')) openVoice();
    if (event.target.closest('[data-close]')) {
      const dialog=event.target.closest('dialog');
      if(dialog.id==='editor-dialog'&&state.saving)return;
      if(dialog.id==='editor-dialog'&&returnFromInvoiceCustomer())return;
      if(dialog.id==='editor-dialog'&&editorIsDirty()&&!confirm('Ungespeicherte Änderungen verwerfen?'))return;
      if (state.editorType === 'customer') state.returnToEditor = null;
      dialog.close();
    }
    const customerDetail = event.target.closest('[data-customer-detail]'); if (customerDetail) openCustomerDetail(customerDetail.dataset.customerDetail);
    const editCustomer = event.target.closest('[data-edit-customer]'); if (editCustomer) { $('#customer-detail-dialog').close(); openEditor('customer', state.customers.find(item => item.id === editCustomer.dataset.editCustomer)); }
    const editOrder = event.target.closest('[data-edit-order]'); if (editOrder) openEditor('order', state.orders.find(item => item.id === editOrder.dataset.editOrder));
    const editWorklog = event.target.closest('[data-edit-worklog]'); if (editWorklog) openEditor('worklog', state.worklogs.find(item => item.id === editWorklog.dataset.editWorklog));
    const editInvoice = event.target.closest('[data-edit-invoice]'); if (editInvoice) openEditor('invoice', state.invoices.find(item => item.id === editInvoice.dataset.editInvoice));
    const remove = event.target.closest('[data-remove]'); if (remove) removeRecord(remove.dataset.remove, remove.dataset.id);
  });
  $('#menu-button').addEventListener('click', () => $('#sidebar').classList.toggle('open'));
  $('#quick-button').addEventListener('click', () => $('#add-dialog').showModal());
  $('#hero-worklog-button').addEventListener('click', () => openEditor('worklog'));
  $('#voice-button').addEventListener('click', openVoice);
  $('#record-button').addEventListener('click', startRecognition);
  $('#voice-form').addEventListener('submit', processVoice);
  $('#editor-form').addEventListener('submit', submitEditor);
  $('#editor-dialog').addEventListener('cancel', event => {
    if(state.saving){event.preventDefault();return;}
    if(state.editorType==='customer'&&state.returnToEditor?.type==='invoice'){event.preventDefault();returnFromInvoiceCustomer();return;}
    if(state.invoiceEditor?.back()){event.preventDefault();return;}
    if(editorIsDirty()&&!confirm('Ungespeicherte Änderungen verwerfen?'))event.preventDefault();
  });
  $('#sync-button').addEventListener('click', synchronize);
  $('#settings-button').addEventListener('click', openSettings);
  $('#settings-form').addEventListener('submit', saveSettings);
  $('#backup-button').addEventListener('click', backup);
  $('#drive-sync-button').addEventListener('click', driveSync);
  $('#customer-view-select').addEventListener('change', event => showCustomerView(event.target.value));
  $('#worklog-period').addEventListener('change', event => { state.filters.period = event.target.value; state.worklogLimit = 50; renderWorklogs(); });
  $('#worklog-billed').addEventListener('change', event => { state.filters.billed = event.target.value; state.worklogLimit = 50; renderWorklogs(); });
  $('#worklog-customer-filter').addEventListener('change', event => { state.filters.customerId = event.target.value; state.worklogLimit = 50; renderWorklogs(); });
  $('#worklog-search').addEventListener('input', event => { state.filters.worklogTerm = event.target.value.trim().toLocaleLowerCase('de-AT'); state.worklogLimit = 50; renderWorklogs(); });
  $('#worklogs-table').addEventListener('click', event => { if (!event.target.closest('#worklog-load-more')) return; state.worklogLimit += 50; renderWorklogs(); });
  $('#clear-filters').addEventListener('click', () => { state.filters = {term:'', worklogTerm:'', customerId:'all', period:'all', billed:'all'}; state.worklogLimit = 50; $('#global-search').value = ''; $('#worklog-search').value = ''; $('#worklog-period').value = 'all'; $('#worklog-customer-filter').value = 'all'; $('#worklog-billed').value = 'all'; renderAll(); });
  $('#global-search').addEventListener('input', event => {
    const value = event.target.value;
    clearTimeout(state.searchTimer);
    state.searchTimer = setTimeout(() => {
      const term = value.trim().toLowerCase();
      state.filters.term = term;
      state.worklogLimit = 50;
      if (state.view === 'customers') { $('#customer-search').value = value; renderCustomers(state.customers.filter(item => !term || searchText(item).includes(term))); }
      if (state.view === 'orders') renderOrders();
      if (state.view === 'worklogs') renderWorklogs();
    }, 120);
  });
  $('#customer-search').addEventListener('input', event => {
    const term = event.target.value.trim().toLocaleLowerCase('de-AT');
    clearTimeout(state.customerSearchTimer);
    state.customerSearchTimer = setTimeout(() => renderCustomers(state.customers.filter(item => !term || searchText(item).includes(term))), 100);
  });
  addEventListener('online', synchronize); addEventListener('offline', updateOnlineState);
  addEventListener('beforeunload',event=>{if(editorIsDirty()){event.preventDefault();event.returnValue='';}});
}

async function start() {
  bindEvents();
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(() => {});
  await loadAll();
}

start();

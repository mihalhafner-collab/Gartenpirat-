/* Shared offline-safe draft preparation. Does not save, issue, price or send an invoice. */
(function(root) {
  function fromWorklogs(rows, existing = []) {
    if (!rows.length) throw new Error('Bitte mindestens eine Arbeitsaufzeichnung auswählen.');
    const recipient = w => w.billing_customer_id || w.customer_id;
    const customer = recipient(rows[0]);
    if (!customer || rows.some(w => recipient(w) !== customer)) throw new Error('Eine Rechnung kann nur Arbeiten desselben Rechnungsempfängers enthalten.');
    if (rows.some(w => w.billed)) throw new Error('Bereits abgerechnete Arbeiten dürfen nicht erneut übernommen werden.');
    const taken = new Set(existing.map(p => p.worklog_id).filter(Boolean)), seen = new Set();
    const positions = [];
    rows.slice().sort((a,b) => String(a.work_date).localeCompare(String(b.work_date))).forEach(w => {
      if (taken.has(w.id) || seen.has(w.id)) return;
      if (!/^\d{4}-\d{2}-\d{2}$/.test(w.work_date || '') || !w.activity?.trim() || !Number.isFinite(Number(w.hours)) || Number(w.hours) <= 0) throw new Error('Bitte Datum, Tätigkeit und positive Arbeitsstunden im Eintrag ergänzen.');
      seen.add(w.id);
      const date = w.work_date.split('-').reverse().join('.');
      const site = w.customer_id !== customer ? (w.work_customer_name || w.customer_name || w.location || w.customer_id) : '';
      positions.push({title:w.activity.trim(),description:[date,site,w.order_title,w.location].filter(Boolean).join(' · '),quantity:Number(w.hours),unit:'Std.',price:null,tax_rate:0,discount:0,worklog_id:w.id});
      if (w.material?.trim()) positions.push({title:w.material.trim(),description:'Materialmenge und Preis vor Abschluss prüfen.',quantity:null,unit:'pauschal',price:null,tax_rate:0,discount:0,worklog_id:''});
      if (Number(w.kilometers) > 0) positions.push({title:'Fahrtkosten',description:date,quantity:Number(w.kilometers),unit:'km',price:null,tax_rate:0,discount:0,worklog_id:''});
    });
    const dates = rows.map(w=>w.work_date).sort(), orders = [...new Set(rows.map(w=>w.order_id || ''))];
    return {customer_id:customer,order_id:orders.length===1&&rows.every(w=>w.customer_id===customer)?orders[0]:null,service_start:dates[0],service_end:dates[dates.length-1],positions,worklog_ids:[...new Set(positions.map(p=>p.worklog_id).filter(Boolean))]};
  }
  function total(positions) {
    if(positions.some(p=>p.price===null||p.price===undefined||p.price===''||p.quantity===null||p.quantity===undefined||p.quantity===''))throw new Error('Menge und Preis bitte ausdrücklich ergänzen.');
    return positions.reduce((sum,p) => sum + Math.round((Number(p.quantity)*Number(p.price)*(1-Number(p.discount||0)/100)+Number.EPSILON)*100),0)/100;
  }
  root.InvoiceFlow = {fromWorklogs,total};
})(typeof globalThis !== 'undefined' ? globalThis : window);

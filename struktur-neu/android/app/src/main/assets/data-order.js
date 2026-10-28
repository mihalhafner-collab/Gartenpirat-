/* Shared presentation/search rules. Never changes IDs, records or saved versions. */
(()=>{
 'use strict';
 const collator=new Intl.Collator('de-AT',{numeric:true,sensitivity:'base'});
 const text=value=>String(value??'');
 const active=value=>![false,0,'0','false'].includes(value);
 const fold=value=>text(value).normalize('NFKD').replace(/[\u0300-\u036f]/g,'').toLocaleLowerCase('de-AT').replace(/ß/g,'ss').replace(/ae/g,'a').replace(/oe/g,'o').replace(/ue/g,'u');
 function matches(query,values){
  const words=fold(query).trim().split(/\s+/).filter(Boolean),items=Array.isArray(values)?values:[values],haystack=fold(items.map(text).join(' '));
  if(words.every(word=>haystack.includes(word)))return true;
  const raw=text(query).trim(),digits=raw.replace(/\D/g,'');
  return digits.length>=4&&/^[+\d\s()./\-]+$/.test(raw)&&items.some(value=>text(value).replace(/\D/g,'').includes(digits));
 }
 function compare(a,b,...fields){for(const field of fields){const order=collator.compare(text(a[field]),text(b[field]));if(order)return order;}return text(a.id).localeCompare(text(b.id));}
 function sorted(collection,rows){
  const result=rows.slice();
  if(collection==='customers')result.sort((a,b)=>Number(active(b.active))-Number(active(a.active))||compare(a,b,'name','company','last_name','first_name'));
  else if(collection==='orders')result.sort((a,b)=>compare(a,b,'title','customer_name'));
  else if(collection==='service_catalog')result.sort((a,b)=>compare(a,b,'category','title'));
  return result;
 }
 window.HGDataOrder=Object.freeze({active,fold,matches,compare,sorted});
})();

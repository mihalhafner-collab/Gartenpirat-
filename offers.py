"""Offers in the existing business database and journal, never an invoice number service."""
from copy import deepcopy
from datetime import date
from decimal import Decimal, ROUND_HALF_UP
import json
from pathlib import Path
import db
import runtime_paths
import sync_review

CONTRACT=json.loads((Path(__file__).parent/'android/app/src/main/assets/offer-contract.json').read_text())
TYPES=tuple(t['id'] for t in CONTRACT['document_types'])
TEXT_FIELDS = ('location','title','execution_window','payment_terms','notes','assumptions','exclusions','header_text','footer_text','customer_type','contract_channel','consumer_information')

def decimal(value):
    try:
        result = Decimal(str(value))
        if not result.is_finite() or len(result.as_tuple().digits)>24 or abs(result.as_tuple().exponent)>10: raise ValueError()
        return result
    except Exception: raise ValueError('Endliche Mengen und Preise erforderlich.')

def money(value): return value.quantize(Decimal('.01'), rounding=ROUND_HALF_UP)

def list_records(kind='offer', database=None):
    if database is None:
        with db.connect() as connection: return list_records(kind,connection)
    table = 'offers' if kind == 'offer' else 'service_catalog'
    return [json.loads(r[0]) for r in database.execute('SELECT payload FROM '+table+' ORDER BY rowid')]

def get(identifier, kind='offer', database=None):
    return next((row for row in list_records(kind,database) if row['id']==identifier),None)

def snapshot(database):
    import owner_journal
    return {'customers':db.dicts(database.execute('SELECT * FROM customers')),
            'orders':db.dicts(database.execute('SELECT * FROM orders')),
            'worklogs':db.dicts(database.execute('SELECT * FROM worklogs')),
            'offers':list_records(database=database), 'service_catalog':list_records('service_template',database),
            'business_settings':owner_journal.business_settings(database)}

def normalize(row, old, state):
    if old and old.get('status','draft') not in ('draft','validated'):raise ValueError('Finalisierte Angebote bleiben unverändert. Für Änderungen duplizieren.')
    if not any(c['id']==row.get('customer_id') for c in state['customers']):raise ValueError('Eindeutiger Kunde fehlt.')
    if row.get('billing_customer_id') and not any(c['id']==row['billing_customer_id'] for c in state['customers']):raise ValueError('Rechnungsempfänger fehlt.')
    if row.get('order_id') and not any(o['id']==row['order_id'] and o['customer_id']==row['customer_id'] for o in state['orders']):raise ValueError('Kunde und Auftrag widersprechen sich.')
    kind=row.get('document_type','')
    if kind and kind not in TYPES:raise ValueError('Unbekannte Angebotsart.')
    definition=next((t for t in CONTRACT['document_types'] if t['id']==kind),{})
    row['pricing_mode']=definition.get('pricing_mode','unselected');row['binding_mode']=definition.get('binding_mode','unselected')
    if old and kind!=old.get('document_type') and row.get('binding_notice')==old.get('binding_notice'):row.pop('binding_notice',None)
    notice_key='offer_binding_text' if kind=='estimate_binding' else 'offer_nonbinding_text' if kind=='estimate_nonbinding' else 'offer_time_material_text'
    row.setdefault('binding_notice','' if kind=='fixed_price' else (state.get('business_settings',{}).get(notice_key) or definition.get('notice','')))
    binding_hash=sync_review.digest(dict(document_type=kind,binding_notice=row.get('binding_notice','')))
    if old and (kind!=old.get('document_type') or row.get('binding_notice')!=old.get('binding_notice')) and row.get('binding_confirmation_hash')!=binding_hash:row['binding_confirmed']=False
    if row.get('binding_confirmed'):row['binding_confirmation_hash']=binding_hash
    else:row.pop('binding_confirmation_hash',None)
    row['binding_status']=('confirmed' if row.get('binding_confirmed') else 'pending_review') if kind.startswith('estimate_') else 'not_applicable'
    row.setdefault('delivery_status','not_prepared');row.pop('validated_at',None);row.pop('validation_context_hash',None)
    for key in TEXT_FIELDS:
        row.setdefault(key,'')
        if not isinstance(row[key],str) or len(row[key])>10000:raise ValueError('Ungültiger Angebotstext: '+key)
    for key in ('issue_date','valid_until'):
        if row.get(key):date.fromisoformat(row[key])
    if not row.get('tax_mode'):row['tax_mode']=state.get('business_settings',{}).get('tax_mode','')
    for key,values in [('tax_mode','tax_modes'),('customer_type','customer_types'),('contract_channel','contract_channels')]:
        if row.get(key) and row[key] not in CONTRACT[values]:raise ValueError('Unbekannter Domänenwert: '+key)
    lines=row.setdefault('positions',[])
    if not isinstance(lines,list) or len(lines)>200:raise ValueError('Höchstens 200 Angebotspositionen.')
    net=tax=Decimal(0);complete=True
    for line in lines:
        if not isinstance(line,dict):raise ValueError('Position ist kein Datensatz.')
        line.setdefault('price_basis','catalog' if line.get('catalog_entry_id') else 'confirmed')
        if 'price_source' in line and line['price_source'] not in CONTRACT['price_sources']:raise ValueError('Preisquelle exakt user_confirmed verwenden.')
        if 'price_basis' in line and line['price_basis'] not in CONTRACT['price_bases']:raise ValueError('Unbekannte Preisgrundlage.')
        if len(line.get('title',''))>500 or len(line.get('description',''))>10000 or len(line.get('price_note',''))>2000:raise ValueError('Positionstext ist zu lang.')
        if line.get('quantity') is None or line.get('price') is None:
            complete=False;line['net']=line['tax']=None;continue
        quantity,price,rate=decimal(line['quantity']),decimal(line['price']),decimal(line.get('tax_rate') if line.get('tax_rate') is not None else 0)
        if not 0<quantity<=1000000000 or not 0<=price<=1000000000 or not 0<=rate<=100:raise ValueError('Mengen, Preise oder Steuer sind ungültig.')
        if row['tax_mode']=='small_business' and rate:raise ValueError('Kleinunternehmermodus erlaubt keinen Steuerausweis.')
        if row['tax_mode']=='standard' and line.get('tax_rate') is None:
            complete=False;line['net']=line['tax']=None;continue
        subtotal=money(quantity*price);line_tax=money(subtotal*rate/100)
        line['net'],line['tax']=float(subtotal),float(line_tax);net+=subtotal;tax+=line_tax
    if net+tax>1000000000:raise ValueError('Angebot überschreitet die sichere Betragsgrenze von einer Milliarde EUR.')
    row.update(net_total=float(net) if complete else None,tax_total=float(tax) if complete else None,gross_total=float(net+tax) if complete else None,status='draft')
    row.setdefault('created_at',db.now())
    return row

def validate(row,state):
    if row.get('status','draft') not in ('draft','validated') and row.get('issuer') and row.get('recipient'):
        state=deepcopy(state);state['business_settings']=row['issuer'];recipient_id=row.get('billing_customer_id') or row['customer_id'];state['customers']=[c for c in state['customers'] if c['id']!=recipient_id]+[{**row['recipient'],'id':recipient_id}]
    normalize(deepcopy(row),None,state)
    errors=[];settings=state.get('business_settings',{})
    for key in CONTRACT['issuer_required_fields']:
        if not str(settings.get(key,'')).strip():errors.append('Aussteller: '+key)
    if str(settings.get('invoice_issuer_confirmed','')) not in ('1','true'):errors.append('Ausstellerdaten bestätigen')
    for key in CONTRACT['required_fields']:
        if not str(row.get(key,'')).strip():errors.append(key+' fehlt')
    if row.get('document_type') not in TYPES:errors.append('Dokumentart ausdrücklich auswählen')
    try:
        if date.fromisoformat(row['valid_until'])<date.fromisoformat(row['issue_date']):errors.append('Gültigkeit liegt vor Angebotsdatum')
    except (KeyError,TypeError,ValueError):errors.append('Gültige Angebotsdaten erforderlich')
    recipient=next((c for c in state['customers'] if c['id']==(row.get('billing_customer_id') or row.get('customer_id'))),None)
    if not recipient:errors.append('Eindeutiger Kunde fehlt')
    else:
        for key in ('name','street','postal_code','city'):
            if not str(recipient.get(key) or '').strip():errors.append('Empfänger: '+key)
        if str(recipient.get('country','')).lower() not in ('at','austria','österreich'):errors.append('Auslandsfall benötigt gesonderte Prüfung')
    customer=next((c for c in state['customers'] if c['id']==row.get('customer_id')),None)
    if customer and customer.get('billing_mode')=='via_customer' and not row.get('billing_customer_id'):errors.append('Abweichenden Rechnungsempfänger ausdrücklich auswählen')
    if settings.get('tax_mode') not in ('small_business','standard') or row.get('tax_mode')!=settings.get('tax_mode'):errors.append('Steuermodus zentral bestätigen')
    if row.get('tax_mode')=='small_business' and not settings.get('tax_exemption_text'):errors.append('Steuerbefreiungshinweis fehlt')
    if row.get('tax_mode')=='standard' and not settings.get('business_vat_id'):errors.append('UID des Ausstellers fehlt')
    if not row.get('positions'):errors.append('Mindestens eine Leistung erforderlich')
    for i,line in enumerate(row.get('positions',[]),1):
        at=f'Position {i}: '
        if not line.get('title','').strip() or not line.get('unit','').strip():errors.append(at+'Leistung und Einheit fehlen')
        if line.get('price') is None:errors.append(at+'Preis fehlt – nachfragen')
        if line.get('quantity') is None or decimal(line['quantity'])<=0:errors.append(at+'Menge fehlt')
        if line.get('catalog_entry_id'):
            template=next((t for t in state.get('service_catalog',[]) if t['id']==line['catalog_entry_id']),None)
            if not template or not template.get('price_confirmed') or template.get('default_price') is None or sync_review.digest(template)!=line.get('catalog_hash') or line.get('price') is None or decimal(template['default_price'])!=decimal(line['price']):errors.append(at+'freigegebenen Katalogpreis erneut prüfen')
        elif line.get('price_source') not in CONTRACT['price_sources']:errors.append(at+'Preisquelle fehlt – keinen Preis erfinden')
        basis=line.get('price_basis','catalog' if line.get('catalog_entry_id') else 'confirmed')
        if basis not in CONTRACT['price_bases'] or (basis=='catalog')!=bool(line.get('catalog_entry_id')):errors.append(at+'Preisgrundlage widerspricht Preisquelle')
        if basis in ('calculated','estimated') and not line.get('price_note','').strip():errors.append(at+'Preisgrundlage erläutern und bestätigen')
        if basis=='calculated':
            try:
                calculation=line['calculation'];derived=money(decimal(calculation['base_price'])*decimal(calculation['factor'])+decimal(calculation.get('surcharge',0)))
                if derived!=decimal(line['price']):errors.append(at+'Berechnung stimmt nicht mit dem Einzelpreis überein')
            except (KeyError,ValueError,TypeError):errors.append(at+'Vollständige Preisberechnung erforderlich')
        if row.get('tax_mode')=='standard' and line.get('tax_rate') is None:errors.append(at+'Steuersatz fehlt')
    if row.get('document_type','').startswith('estimate_') and (row.get('binding_confirmed') is not True or not row.get('binding_notice','').strip()):errors.append('Bindungsart und Hinweis ausdrücklich bestätigen')
    if row.get('customer_type') not in CONTRACT['customer_types']:errors.append('B2B oder Verbraucher bewusst auswählen')
    if row.get('scope_confirmed') is not True:errors.append('Leistungsumfang, Annahmen und Ausschlüsse prüfen')
    if row.get('customer_type')=='consumer':
        if row.get('contract_channel') not in ('on_premise','distance','off_premise'):errors.append('Vertragsweg für Verbraucher auswählen')
        if row.get('contract_channel') in ('distance','off_premise') and (row.get('consumer_information_confirmed') is not True or not row.get('consumer_information','').strip()):errors.append('FAGG-Informationen und Rücktrittsbelehrung gesondert prüfen und beifügen')
    if row.get('customer_type')=='consumer' and row.get('contract_channel') in ('distance','off_premise'):
        for key in ('business_phone','business_email'):
            if not str(settings.get(key,'')).strip():errors.append('FAGG-Kontaktangaben: '+key)
    return {'valid':not errors,'errors':errors,'legal_rules_version':CONTRACT['legal_rules_version']}

def context(row,state):
    import owner_journal
    customer=next(c for c in state['customers'] if c['id']==row['customer_id'])
    recipient=next(c for c in state['customers'] if c['id']==(row.get('billing_customer_id') or row['customer_id']))
    order=next((o for o in state['orders'] if o['id']==row.get('order_id')),None)
    order_guard={k:order.get(k) for k in ('id','updated_at','customer_id','title','description','location','scheduled_date','status','notes','customer_visible','billing_customer_id','offer_id')} if order else None
    return {'customer':owner_journal.customer_guard(customer),'recipient':owner_journal.customer_guard(recipient),'order':order_guard,'business_settings':state['business_settings'],'service_catalog':state['service_catalog']}

def save(payload, kind='offer'):
    import owner_journal
    with db.atomic() as database:
        identifier=payload.get('id')
        if not identifier:raise ValueError('Stabile ID erforderlich.')
        old=get(identifier,kind,database)
        if payload.get('_mcp_create') and old:raise ValueError('WRITE_CONFLICT: Neue ID ist bereits belegt.')
        base=payload.get('_sync_base') or payload.get('_mcp_base')
        if old:
            if not base:raise ValueError('WRITE_CONFLICT: Vergleichsstand fehlt.')
            owner_journal.compare_base(base,old)
        elif base:raise ValueError('WRITE_CONFLICT: Original fehlt.')
        row=deepcopy({**(old or {}),**owner_journal.clean(payload)})
        if kind=='offer':normalize(row,old,snapshot(database))
        else:
            for key in ('title','category','unit'):
                if not str(row.get(key,'')).strip():raise ValueError('Leistungsbaustein benötigt Titel, Kategorie und Einheit.')
            for key in ('default_price','default_quantity'):
                if row.get(key) is not None and decimal(row[key])<0:raise ValueError('Negative Bausteinwerte sind ungültig.')
            row.setdefault('default_price',None);row.setdefault('active',True);row.setdefault('price_confirmed',False)
        row.setdefault('updated_at',db.now());persist(database,row,kind);return row

def persist(database,row,kind='offer'):
    if kind=='offer':database.execute('INSERT INTO offers(id,customer_id,billing_customer_id,order_id,payload) VALUES(?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET customer_id=excluded.customer_id,billing_customer_id=excluded.billing_customer_id,order_id=excluded.order_id,payload=excluded.payload',(row['id'],row['customer_id'],row.get('billing_customer_id') or None,row.get('order_id') or None,json.dumps(row,ensure_ascii=False)))
    else:database.execute('INSERT INTO service_catalog(id,payload) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload',(row['id'],json.dumps(row,ensure_ascii=False)))

def position_text(positions):
    return '\n'.join(p['title']+' — '+sync_review.canonical(decimal(p['quantity']))+' '+p.get('unit','')+' × '+sync_review.canonical(decimal(p['price']))+' EUR' for p in positions)

def apply(action,attachment_root=None):
    import owner_journal
    p=action['payload'];kind=action['kind']
    if kind in ('offer','service_template'):return save(p,kind)
    with db.atomic() as database:
        state=snapshot(database);old=get(p['id'],database=database)
        if not old or sync_review.digest(owner_journal.clean(old))!=p.get('expected_record_hash'):raise ValueError('WRITE_CONFLICT: Angebot wurde inzwischen verändert.')
        if p.get('confirmed') is not True:raise ValueError('Ausdrückliche Bestätigung erforderlich.')
        row=deepcopy(old)
        if kind=='offer_finalize':
            if sync_review.digest(context(old,state))!=p.get('expected_context_hash'):raise ValueError('WRITE_CONFLICT: Kunde, Aussteller oder Preise wurden geändert.')
            normalize(row,old,state);result=validate(row,state)
            if not result['valid']:raise ValueError('; '.join(result['errors']))
            number=('KV-' if row.get('document_type','').startswith('estimate_') else 'ANG-')+row['issue_date'][:4]+'-'+sync_review.digest(row['id'])[:16].upper()
            if any(o.get('offer_number')==number and o['id']!=row['id'] for o in state['offers']):raise ValueError('Angebotsnummer bereits vergeben.')
            frozen=context(old,state);row.update(offer_number=number,status='finalized',finalized_at=action['created_at'],issuer=frozen['business_settings'],recipient=frozen['recipient'])
            # Preserve the exact Pixel PDF, not a differently rendered replacement.
            for key in ('document_id','pdf_storage_name','pdf_sha256','pdf_byte_size'):
                if key in p:row[key]=p[key]
            if row.get('pdf_storage_name'):
                if attachment_root is None:raise ValueError('Angebots-PDF fehlt noch im Medienabgleich.')
                if not __import__('re').fullmatch(r'[0-9a-f]{64}\.pdf',str(row['pdf_storage_name'])) or not isinstance(row.get('pdf_byte_size'),int) or not 5<=row['pdf_byte_size']<=20*1024*1024:raise ValueError('Ungültige PDF-Referenz.')
                root=runtime_paths.business_path(Path(attachment_root).parent/'HausGarten-Dokumente');file=root/row['pdf_storage_name']
                if file.is_symlink() or file.stat().st_size>20*1024*1024:raise ValueError('Ungültige PDF-Datei.')
                raw=file.read_bytes()
                if not row['pdf_storage_name']==row.get('pdf_sha256','')+'.pdf' or len(raw)!=row['pdf_byte_size'] or __import__('hashlib').sha256(raw).hexdigest()!=row['pdf_sha256'] or not raw.startswith(b'%PDF-'):raise ValueError('Angebots-PDF-Prüfsumme stimmt nicht überein.')
                local=db.DATA_DIR/'documents';local.mkdir(exist_ok=True);target=local/row['pdf_storage_name']
                if target.exists() and target.read_bytes()!=raw:raise ValueError('Original-PDF weicht ab.')
                if not target.exists():
                    with target.open('xb') as out:out.write(raw)
        elif kind=='offer_convert':
            if old.get('status')!='finalized':raise ValueError('Nur ein finalisiertes, ausdrücklich angenommenes Angebot übernehmen.')
            order_id=p.get('existing_order_id') or 'ord_offer_'+sync_review.digest(action['client_id'])[:32]
            order=next((o for o in state['orders'] if o['id']==order_id),None)
            if p.get('existing_order_id'):
                if not order or order['customer_id']!=row['customer_id'] or order.get('offer_id'):raise ValueError('Auftrag und Angebot passen nicht zusammen.')
                # Pixel guard uses a stable subset, supplied and checked separately.
                if p.get('_expected_order') and sync_review.digest(p['_expected_order'])==p.get('expected_order_hash'):owner_journal.compare_base(p['_expected_order'],order)
                else:raise ValueError('WRITE_CONFLICT: Auftragsvergleich fehlt.')
            elif order:raise ValueError('WRITE_CONFLICT: Auftrags-ID bereits belegt.')
            customer=next(c for c in state['customers'] if c['id']==row['customer_id'])
            target={**(order or {}),'id':order_id,'customer_id':row['customer_id'],'title':(order or {}).get('title',row['title']),'location':(order or {}).get('location') or row.get('location') or f"{customer.get('street','')}, {customer.get('postal_code','')} {customer.get('city','')}",'status':(order or {}).get('status','Geplant'),'description':((order or {}).get('description','')+'\n'+row['offer_number']+'\n'+position_text(row['positions'])).strip(),'billing_customer_id':(order or {}).get('billing_customer_id') or row.get('billing_customer_id') or row['customer_id'],'offer_id':row['id'],'_sync_updated_at':action['created_at']}
            db.save_order(target);row.update(status='accepted',converted_order_id=order_id)
        else:raise ValueError('Unbekannte Angebotsaktion.')
        row['updated_at']=action['created_at'];persist(database,row);return row

"""Owner protocol on the existing database; business change and receipt commit together."""
import json
import db
import sync_review

TABLES={'customer':'customers','order':'orders','worklog':'worklogs','invoice':'invoices','photo':'photos','offer':'offers','service_template':'service_catalog'}
BUSINESS_KEYS=('business_name','owner_name','business_street','business_postal_code','business_city','business_legal_info','business_iban','business_bank','business_bic','business_phone','business_email','invoice_issuer_confirmed','invoice_numbering_confirmed','invoice_number_format','invoice_prefix','invoice_sevdesk_last_number','invoice_sevdesk_checked_at','tax_mode','tax_exemption_text','business_vat_id','default_payment_days','default_payment_terms')

def business_settings(database=None):
    if database is None:
        with db.connect() as connection:return business_settings(connection)
    values={r['key']:r['value'] for r in database.execute('SELECT key,value FROM settings')}
    return {key:values.get(key,'') for key in BUSINESS_KEYS}

NUMBERS={'hours','break_minutes','kilometers','billed','customer_visible','active','garden_area','payment_days','domestic_service_confirmed','byte_size'}

def customer_guard(row):
    fields=('id','updated_at','name','first_name','last_name','company','street','postal_code','city','country','email','phone','notes','active','garden_area','sevdesk_customer_number','custom_fields','billing_mode','default_billing_customer_id')
    result={key:row.get(key) for key in fields}
    custom=result['custom_fields'];result['custom_fields']=json.loads(custom or '{}') if isinstance(custom,str) else custom or {}
    return result


def clean(row):
    return {k:v for k,v in row.items() if not k.startswith('_') and k not in ('pending','allow_duplicate','require_order')}

def compare_base(base,current):
    if not isinstance(base,dict) or current is None or not base.get('id') or base['id']!=current.get('id'):
        raise ValueError('WRITE_CONFLICT: Der geprüfte Datensatz fehlt oder hat eine andere ID.')
    def comparable(key,value):
        if key=='garden_area' and value in (None,''):return None
        if key in NUMBERS:return int(value) if isinstance(value,bool) else db.number(value,0)
        if key=='custom_fields':return json.loads(value or '{}') if isinstance(value,str) else value or {}
        return '' if value is None else value
    for key,value in base.items():
        if sync_review.canonical(comparable(key,value)) != sync_review.canonical(comparable(key,current.get(key))):
            raise ValueError('WRITE_CONFLICT: Der Datensatz wurde inzwischen geändert; beide Varianten bleiben erhalten.')

def record(database,entity,identifier):
    if entity not in TABLES:raise ValueError('Unbekannter Fachbereich.')
    if entity in ('offer','service_template'):
        import offers
        return offers.get(identifier,entity,database)
    row=db.one(database.execute('SELECT * FROM '+TABLES[entity]+' WHERE id=?',(identifier,)))
    if row and entity=='invoice':
        details=database.execute('SELECT payload FROM invoice_details WHERE invoice_id=?',(identifier,)).fetchone()
        if details:row.update(json.loads(details[0]))
    return row

def metadata(database):
    saved=database.execute("SELECT payload FROM owner_journal_metadata WHERE key='owner_protocol'").fetchone()
    return json.loads(saved[0]) if saved else {'owner_archive':{},'owner_conflict_history':{},'drive_resolved_actions':{}}

def snapshot_metadata():
    with db.connect() as database:return metadata(database)

def apply(action,attachment_root=None):
    kind=action['kind'];p=action['payload'];identifier=str(action.get('client_id') or '')
    if not identifier:raise ValueError('Aktionskennung fehlt.')
    fingerprint=sync_review.digest(action)
    with db.atomic() as database:
        old=database.execute('SELECT sha256,result FROM owner_journal_receipts WHERE action_id=?',(identifier,)).fetchone()
        if old:
            if old['sha256']!=fingerprint:raise ValueError('ACTION_ID_CONFLICT: Bestätigte Aktion wurde verändert.')
            return json.loads(old['result'])
        state=metadata(database)
        state.setdefault('owner_archive_bases',{})
        state.setdefault('owner_archive_storage',{})
        if kind=='business_settings':
            changes=p.get('updates')
            if not isinstance(changes,dict) or not changes or set(changes)-set(BUSINESS_KEYS):raise ValueError('Ungültige fachliche Einstellung; Zugangsdaten sind nicht erlaubt.')
            if sync_review.digest(business_settings(database))!=p.get('expected_record_hash'):raise ValueError('WRITE_CONFLICT: Fachliche Einstellungen wurden geändert.')
            for key,value in changes.items():
                if key.endswith('_confirmed') and not isinstance(value,bool):raise ValueError('Bestätigung muss Ja/Nein sein.')
                if key=='invoice_number_format' and value not in ('own','sevdesk'):raise ValueError('Ungültiger Nummernkreis.')
                if key=='tax_mode' and value not in ('small_business','standard'):raise ValueError('Steuermodus ist ungültig.')
                if key=='default_payment_days' and (isinstance(value,bool) or not isinstance(value,int) or not 0<=value<=365):raise ValueError('Zahlungsziel ist ungültig.')
                if key=='invoice_sevdesk_last_number' and (isinstance(value,bool) or not isinstance(value,int) or not 0<=value<=1000000000):raise ValueError('Ungültiger Nummernstand.')
                if key=='invoice_prefix' and not __import__('re').fullmatch('[A-Za-z][A-Za-z0-9-]{0,19}',str(value)):raise ValueError('Ungültiges Präfix.')
                if key not in ('invoice_issuer_confirmed','invoice_numbering_confirmed','invoice_sevdesk_last_number','default_payment_days') and not isinstance(value,str):raise ValueError('Einstellung muss Text sein.')
                if len(str(value))>2000:raise ValueError('Einstellung zu lang.')
                if key=='invoice_sevdesk_checked_at' and value:
                    stamp=__import__('datetime').datetime.fromisoformat(str(value).replace('Z','+00:00'))
                    if stamp.tzinfo is None:raise ValueError('Zeitpunkt benötigt Zeitzone.')
                text=('1' if value else '0') if isinstance(value,bool) else str(value)
                database.execute('INSERT OR REPLACE INTO settings(key,value) VALUES(?,?)',(key,text))
        elif kind=='conflict_resolution':
            prior=p.get('_original_action');target=p.get('client_id')
            if not isinstance(prior,dict) or prior.get('client_id')!=target or sync_review.digest(prior)!=p.get('expected_action_hash') or target in state['drive_resolved_actions']:
                raise ValueError('ACTION_ID_CONFLICT: Die ursprüngliche Konfliktaktion fehlt oder wurde verändert.')
            if 'expected_current_hash' in p and sync_review.digest(p.get('_current_base'))!=p['expected_current_hash']:raise ValueError('WRITE_CONFLICT: Hash und Vergleichsstand widersprechen sich.')
            legacy_delete=prior['kind']=='delete';proposed=prior['payload'];entity=proposed['entity'] if legacy_delete else prior['kind'];current=record(database,entity,proposed['id'])
            if p.get('_current_base') is None:
                if current is not None:raise ValueError('WRITE_CONFLICT: Die neue ID ist inzwischen belegt.')
            else:compare_base(p['_current_base'],current)
            if p['resolution']=='apply_proposed':
                payload={**(current or {}),**clean(proposed)}
                if current:payload['_sync_base']=p['_current_base']
                else:payload['_mcp_create']=True
                if legacy_delete:
                    if current:
                        apply({'kind':'archive','client_id':identifier+'-archive','created_at':action.get('created_at',''),'payload':{'entity':entity,'id':current['id'],'_record_base':p['_current_base'],'_archive_record':current,'updated_at':action.get('created_at') or db.now()}},attachment_root)
                        state=metadata(database)
                else:
                    import server
                    server.apply_sync_action({'kind':entity,'client_id':identifier+'-resolved','payload':payload},attachment_root)
            elif p['resolution']!='keep_current':raise ValueError('Ungültige Konfliktentscheidung.')
            state['owner_conflict_history'][target]={'original_action':prior,'previous':current,'resolution':p['resolution'],'resolution_action':identifier,'action_hash':p['expected_action_hash'],'reason':p.get('reason','explicit_owner_decision'),'resolved_at':action.get('created_at','')}
            state['drive_resolved_actions'][target]=sync_review.digest(prior)
        else:
            entity='invoice' if kind=='invoice_payment' else p['entity'];item=p['id'];current=record(database,entity,item);key=entity+':'+item
            if kind=='restore':
                saved=state['owner_archive'].get(key)
                if not saved or sync_review.digest(saved)!=p['expected_record_hash']:raise ValueError('WRITE_CONFLICT: Archivstand wurde verändert.')
                original=state['owner_archive_storage'].get(key,saved['record'])
                if entity=='customer':
                    base={**original,'active':0,'updated_at':state['owner_archive_bases'][key]};compare_base({k:v for k,v in base.items() if k in current},current)
                elif current is not None:raise ValueError('WRITE_CONFLICT: Die Original-ID ist bereits belegt.')
                table=TABLES[entity]
                columns={r[1] for r in database.execute('PRAGMA table_info('+table+')')}
                payload={k:v for k,v in original.items() if k in columns and k!='id'}
                for k,v in list(payload.items()):
                    if isinstance(v,(dict,list)):payload[k]=json.dumps(v,ensure_ascii=False)
                    if k.endswith('_id') and not v:payload[k]=None
                db._upsert_in_connection(table,item,payload,original.get('updated_at') or db.now(),database)
                if entity=='invoice':database.execute('INSERT INTO invoice_details(invoice_id,payload) VALUES(?,?)',(item,json.dumps({k:v for k,v in original.items() if k not in columns},ensure_ascii=False)))
                del state['owner_archive'][key]
                state['owner_archive_storage'].pop(key,None)
                state['owner_archive_bases'].pop(key,None)
            else:
                compare_base(p['_record_base'],current)
                if kind=='invoice_payment':
                    if not current.get('issued_at') or current['status']=='Storniert':raise ValueError('Nur gültige ausgestellte Rechnungen können bezahlt/offen markiert werden.')
                    database.execute('UPDATE invoices SET status=?,updated_at=? WHERE id=?',('Bezahlt' if p['paid'] else 'Gestellt',p['updated_at'],item))
                else:
                    if key in state['owner_archive']:raise ValueError('Archivstand existiert bereits.')
                    # Preserve the exact version used by Pixel and every original file.
                    original=p.get('_archive_record')
                    if not isinstance(original,dict):raise ValueError('Originalversion zum Archivieren fehlt.')
                    compare_base({k:v for k,v in original.items() if k in current},current)
                    state['owner_archive_storage'][key]=current
                    saved={'record':original,'archived_at':action.get('created_at',''),'archive_action':identifier}
                    if entity=='customer':
                        archived={**original,'active':0,'updated_at':p['updated_at']};saved['archived_record_hash']=sync_review.digest(customer_guard(archived));state['owner_archive_bases'][key]=p['updated_at']
                        database.execute('UPDATE customers SET active=0,updated_at=? WHERE id=?',(p['updated_at'],item))
                    else:
                        validate_archive(database,entity,current)
                        database.execute('DELETE FROM '+TABLES[entity]+' WHERE id=?',(item,))
                    state['owner_archive'][key]=saved
        result={'client_id':identifier,'applied':True,'cloud_confirmed':False,'effects':effects(database,action)}
        database.execute("INSERT OR REPLACE INTO owner_journal_metadata(key,payload) VALUES('owner_protocol',?)",(json.dumps(state,ensure_ascii=False),))
        database.execute('INSERT INTO owner_journal_receipts(action_id,sha256,result) VALUES(?,?,?)',(identifier,fingerprint,json.dumps(result)))
        return result

def validate_archive(database,entity,row):
    item=row['id']
    if entity=='invoice' and (row.get('issued_at') or row.get('sevdesk_id') or row['status'] not in ('Entwurf','Storniert')):raise ValueError('Ausgestellte Belege bleiben geschützt.')
    if entity=='order':
        if any(database.execute('SELECT 1 FROM '+table+' WHERE order_id=? LIMIT 1',(item,)).fetchone() for table in ('worklogs','invoices','photos','offers')):raise ValueError('Verknüpfte Auftragsdaten zuerst prüfen.')
    if entity=='worklog':
        if database.execute('SELECT 1 FROM photos WHERE worklog_id=?',(item,)).fetchone():raise ValueError('Verknüpfte Fotos zuerst prüfen.')
        for detail in database.execute('SELECT payload FROM invoice_details'):
            if item in json.loads(detail[0]).get('worklog_ids',[]):raise ValueError('Verknüpfte Rechnung zuerst prüfen.')


def effects(database,action):
    """Evidence of the exact committed versions, never a blanket export exception."""
    kind=action['kind'];p=action['payload'];targets=[]
    if kind in ('offer_finalize','offer_convert'):
        targets.append(('offer',p.get('id')))
        item=record(database,'offer',p.get('id'))
        if item and item.get('converted_order_id'):targets.append(('order',item['converted_order_id']))
    elif kind in TABLES:targets.append((kind,p.get('id')))
    elif kind in ('archive','restore'):targets.append((p.get('entity'),p.get('id')))
    elif kind=='conflict_resolution':
        prior=p.get('_original_action',{});original=prior.get('payload',{});targets.append((original.get('entity') if prior.get('kind')=='delete' else prior.get('kind'),original.get('id')))
    elif kind=='invoice_payment':targets.append(('invoice',p.get('id')))
    elif kind in ('invoice_issue','owner_invoice_issue'):
        invoice=record(database,'invoice',p.get('id'))
        if invoice:
            targets.append(('invoice',invoice['id']))
            targets.extend(('worklog',identifier) for identifier in invoice.get('worklog_ids',[]))
            if invoice.get('document_type')=='cancellation':targets.append(('invoice',invoice.get('correction_of')))
    result=[]
    for entity,identifier in targets:
        if entity not in TABLES or not identifier:continue
        result.append({'collection':TABLES[entity],'entity':entity,'id':identifier,'sha256':sync_review.digest(record(database,entity,identifier))})
    return result


def verified_effects(action):
    with db.connect() as database:
        receipt=database.execute('SELECT sha256,result FROM owner_journal_receipts WHERE action_id=?',(action['client_id'],)).fetchone()
        if not receipt:return set()
        if receipt['sha256']!=sync_review.digest(action):raise ValueError('ACTION_ID_CONFLICT: Eine dauerhaft bestätigte Aktion wurde verändert.')
        result=set()
        for effect in json.loads(receipt['result']).get('effects',[]):
            if effect['sha256']==sync_review.digest(record(database,effect['entity'],effect['id'])):result.add((effect['collection'],effect['id']))
        return result


def run_action(action,attachment_root=None):
    """Business mutation plus durable replay receipt in a single SQLite transaction."""
    import server
    fingerprint=sync_review.digest(action)
    with db.atomic() as database:
        receipt=database.execute('SELECT sha256,result FROM owner_journal_receipts WHERE action_id=?',(action['client_id'],)).fetchone()
        if receipt:
            if receipt['sha256']!=fingerprint:raise ValueError('ACTION_ID_CONFLICT: Anfragekennung enthält andere Angaben.')
            return json.loads(receipt['result'])['business_result'] if 'business_result' in json.loads(receipt['result']) else json.loads(receipt['result'])
        effective=action
        p=action['payload']
        if action['kind'] in ('customer','order','worklog','photo') and p.get('_sync_field_merge') is True and p.get('_sync_base') and not p.get('_mcp_base'):
            import sync_protocol
            current=record(database,action['kind'],p.get('id'))
            merged=sync_protocol.merge_fields(p['_sync_base'],p,current)
            merged['_sync_base']={key:current.get(key) for key in p['_sync_base']}
            effective={**action,'payload':merged}
        result=server.apply_sync_action(effective,attachment_root)
        if action['kind'] not in ('archive','restore','conflict_resolution','invoice_payment','business_settings'):
            saved={'business_result':result,'effects':effects(database,action)}
            database.execute('INSERT INTO owner_journal_receipts(action_id,sha256,result) VALUES(?,?,?)',(action['client_id'],fingerprint,json.dumps(saved,ensure_ascii=False)))
        return result

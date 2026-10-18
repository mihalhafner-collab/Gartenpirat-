"""Pure dependency ordering/field merge shared by the existing Drive importer.

No I/O, credentials, second database, or modification of original journal actions.
"""
import json
import sync_review

NUMERIC={'hours','break_minutes','kilometers','billed','customer_visible','active','garden_area'}

def normalize_garden(data):
    result=dict(data)
    def number(value):
        import math
        if value is None or str(value).strip()=='':return None
        try:
            n=float(str(value).replace(',','.'))
            if not math.isfinite(n) or not 0<=n<=1_000_000_000:raise ValueError()
            return n
        except (ValueError,TypeError):raise ValueError('Bitte eine gültige, nicht negative Gartenfläche oder Heckenlänge eingeben.')
    if 'garden_area' in result:result['garden_area']=number(result['garden_area'])
    if 'custom_fields' in result:
        raw=result['custom_fields'];fields=json.loads(raw or '{}') if isinstance(raw,str) else dict(raw or {})
        for key in ('Rasenfläche (m²)','Heckenlänge (m)'):
            if key in fields:fields[key]=number(fields[key])
        for key in ('Bewässerung','Pflegehinweise','Zugangshinweise'):
            if fields.get(key) is not None and (not isinstance(fields[key],str) or len(fields[key])>2000):raise ValueError('Gartenhinweise dürfen höchstens 2000 Zeichen enthalten.')
        if result.get('garden_area') is not None and fields.get('Rasenfläche (m²)') is not None and fields['Rasenfläche (m²)']>result['garden_area']:raise ValueError('Die Rasenfläche darf nicht größer als die gesamte Gartenfläche sein.')
        result['custom_fields']=fields
    return result

def comparable(key,value):
    if key=='garden_area' and value in (None,''):return None
    if key=='custom_fields':return json.loads(value or '{}') if isinstance(value,str) else value or {}
    if key in NUMERIC:return float(value or 0)
    return '' if value is None else value

def merge_fields(base,patch,current):
    if not current or base.get('id')!=current.get('id'):raise ValueError('WRITE_CONFLICT: Der ursprüngliche Datensatz fehlt.')
    result=dict(current)
    for key,value in patch.items():
        if key.startswith('_') or key in {'id','updated_at','created_at','pending'} or key not in base:continue
        equal=lambda a,b:sync_review.canonical(comparable(key,a))==sync_review.canonical(comparable(key,b))
        if not equal(base[key],value):
            if not equal(base[key],current.get(key)) and not equal(value,current.get(key)):
                raise ValueError('WRITE_CONFLICT: Dasselbe Feld wurde unterschiedlich geändert. Beide Varianten bleiben erhalten.')
            result[key]=value
    if 'updated_at' in patch:result['updated_at']=patch['updated_at']
    return result

def ordered(actions):
    values=list(actions);by_id={str(a['client_id']):a for a in values};creators={};versions={};dependencies={}
    for a in values:
        p=a['payload'];entity=(a['kind'],p.get('id',''))
        if p.get('_mcp_create') and p.get('id'):creators.setdefault(entity,a['client_id'])
        if p.get('updated_at'):versions[(*entity,p['updated_at'])]=a['client_id']
    for a in values:
        p=a['payload'];identifier=a['client_id'];deps=set(x for x in p.get('_sync_predecessors',[]) if x in by_id)
        for field,kind in [('customer_id','customer'),('billing_customer_id','customer'),('order_id','order'),('worklog_id','worklog')]:
            parent=creators.get((kind,p.get(field,'')))
            if parent is not None and parent!=identifier:deps.add(parent)
        base=p.get('_sync_base') or p.get('_mcp_base') or {}
        prior=versions.get((a['kind'],p.get('id',''),base.get('updated_at','')))
        if prior is not None and prior!=identifier:deps.add(prior)
        dependencies[identifier]=deps
    result=[];done=set()
    while len(done)<len(by_id):
        moved=False
        for a in values:
            identifier=a['client_id']
            if identifier not in done and dependencies[identifier]<=done:
                result.append(a);done.add(identifier);moved=True
        if not moved:raise ValueError('DEPENDENCY_CYCLE: Zyklische Vorgängerkette. Alle Originalaktionen bleiben erhalten.')
    return result

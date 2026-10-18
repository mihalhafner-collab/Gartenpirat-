"""Order-only explicit review receipts. No business data or I/O in this module."""
import hashlib
import json
from decimal import Decimal

def quote(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'))

def canonical(value):
    if isinstance(value, dict):
        return '{' + ','.join(quote(k) + ':' + canonical(value[k]) for k in sorted(value, key=lambda k:k.encode('utf-16-be'))) + '}'
    if isinstance(value, list):
        return '[' + ','.join(canonical(v) for v in value) + ']'
    if value is None:return 'null'
    if isinstance(value, bool):return 'true' if value else 'false'
    if isinstance(value, (int, float, Decimal)):
        number=Decimal(str(value))
        if not number.is_finite():raise ValueError('Ungültige Zahl im Abgleichpaket.')
        if number == 0:return '0'
        text=format(number,'f')
        return text.rstrip('0').rstrip('.') if '.' in text else text
    return quote(value)

def digest(action):
    return hashlib.sha256(canonical(action).encode('utf-8')).hexdigest()

def validate_resolution(action, actions, failures, accepted, resolved):
    refs=action.get('payload',{}).get('_sync_resolves')
    if refs is None:return []
    if action.get('kind')!='order' or not isinstance(refs,list) or not 1<=len(refs)<=1000:
        raise ValueError('Ungültige Auftragsprüfung. Keine bisherige Änderung zurückgestellt.')
    identifiers=[]
    for ref in refs:
        if not isinstance(ref,dict):raise ValueError('Ungültige Auftragsprüfung.')
        identifier=ref.get('client_id');prior=actions.get(identifier)
        if (not prior or identifier in identifiers or identifier not in failures or identifier in accepted or identifier in resolved
            or prior.get('kind')!='order' or not action['payload'].get('id')
            or prior.get('payload',{}).get('id')!=action['payload']['id']
            or digest(prior)!=ref.get('sha256')):
            raise ValueError('Die geprüfte Änderung wurde inzwischen verändert oder übernommen. Bitte erneut prüfen.')
        identifiers.append(identifier)
    return identifiers

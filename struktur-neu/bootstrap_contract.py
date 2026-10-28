"""The sole allowed bundled bootstrap. Contains no operative records."""
import json

COLLECTIONS = ('customers', 'orders', 'worklogs', 'invoices', 'photos', 'offers',
               'appointments', 'service_catalog')
BOOTSTRAP = dict(format=1, schema_version=2, accepted_ids=[], sync_errors=[],
                 capabilities=dict(worklog_compare_and_save=True, entity_compare_and_save=True,
                                   customer_custom_fields_compare_and_save=True, billing_relations=True),
                 **{name: [] for name in COLLECTIONS})


def verify_bootstrap(raw):
    value = json.loads(raw)
    if value != BOOTSTRAP:
        raise ValueError('Bootstrap muss exakt dem leeren öffentlichen Vertrag entsprechen.')
    return True


def verify_legacy_empty_bootstrap(raw):
    """Previously shipped empty builds remain public; never allow old rows."""
    value = json.loads(raw)
    if not isinstance(value,dict) or set(value) - set(BOOTSTRAP):
        raise ValueError('Unbekannte Bootstrap-Felder.')
    if type(value.get('format')) is not int or value['format'] != 1:
        raise ValueError('Ungültiges Bootstrap-Format.')
    if 'schema_version' in value and (type(value['schema_version']) is not int or value['schema_version'] not in (1,2)):
        raise ValueError('Ungültige Bootstrap-Schemaversion.')
    if any(value.get(k,[]) != [] for k in COLLECTIONS + ('accepted_ids','sync_errors')):
        raise ValueError('Operative Bootstrap-Daten sind gesperrt.')
    capabilities=value.get('capabilities',{})
    if not isinstance(capabilities,dict) or set(capabilities)-set(BOOTSTRAP['capabilities']) or any(not isinstance(v,bool) for v in capabilities.values()):
        raise ValueError('Private Bootstrap-Konfiguration.')
    return True

"""P1 frozen public contracts and synthetic observations; no operating store access."""
from pathlib import Path
import argparse
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / 'tests/fixtures/refactoring-baseline.json'
JAVA = ROOT / 'android/app/src/main/java/at/hausgarten/service'
BRIDGE_SOURCES = {'MainActivity.java', 'BusinessDataBridge.java', 'BrowserMediaBridge.java'}

def javascript_bridges(directory=JAVA, reviewed_sources=None):
    """Inventory every annotated source; a new bridge file cannot evade P1."""
    reviewed = BRIDGE_SOURCES if reviewed_sources is None else set(reviewed_sources)
    signatures = []
    for path in sorted(directory.rglob('*.java')):
        source = path.read_text()
        if not re.search(r'@(?:android\.webkit\.)?JavascriptInterface\b', source):
            continue
        name = path.relative_to(directory).as_posix()
        if name not in reviewed:
            raise ValueError('Unreviewed Javascript bridge source: '+name)
        signatures.extend(re.findall(r'@(?:android\.webkit\.)?JavascriptInterface\s+public\s+([^\{]+)\{', source))
    return sorted(signatures)

def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
                                    separators=(',', ':')).encode()).hexdigest()

def differences(expected, current):
    return sorted(key for key in expected.keys() | current.keys()
                  if key not in expected or key not in current or digest(expected[key]) != digest(current[key]))

def approved_evolution(expected, current, delta, schema_repair=None):
    # Narrow explicit functional evolution. The original P1 baseline stays immutable.
    import copy
    if delta.get('version') != 1 or delta.get('baseline_sha256') != hashlib.sha256(BASELINE.read_bytes()).hexdigest():
        raise ValueError('Invalid versioned functional-contract proof')
    reviewed = copy.deepcopy(expected)
    for change in delta['changes']:
        group, key = change['group'], change['key']
        if (group, key) not in {('contracts', 'DriveJournalRules'), ('observations', 'qa_case_14')}:
            raise ValueError('Unapproved functional-contract scope')
        if reviewed[group][key] != change['before'] or not change.get('reason'):
            raise ValueError('Functional-contract predecessor changed')
        reviewed[group][key] = change['after']
    # One reproduced advertised-value contradiction. Preserve the original baseline
    # and every other schema byte; do not permit a general catalog replacement.
    if schema_repair is not None:
        repair = schema_repair
        if (repair.get('version') != 1 or repair.get('tool') != 'update_business_settings'
                or repair.get('property') != 'invoice_numbering_authority'
                or repair.get('before_min_length') != 1 or repair.get('after_min_length') != 0
                or repair.get('enum') != ['', 'pixel_drive'] or not repair.get('reason')):
            raise ValueError('Unapproved MCP schema repair scope')
        tool = next(t for t in reviewed['tools'] if t['name'] == repair['tool'])
        field = tool['inputSchema']['properties']['updates']['properties'][repair['property']]
        if field.get('minLength') != 1 or field.get('enum') != repair['enum']:
            raise ValueError('MCP schema predecessor changed')
        field['minLength'] = 0
    return differences(reviewed, current)

def observe():
    tools = json.loads((ROOT/'outputs/integration-java/source-tool-catalog.json').read_text())
    ui = json.loads((ROOT/'android/app/src/main/assets/ui-mcp-actions.json').read_text())
    observations = json.loads((ROOT/'outputs/integration-java/refactoring-observations.json').read_text())
    contracts = {}
    for name in ('DriveJournalRules', 'OwnerJournalRules', 'OfferContract', 'OfferRules',
                 'InvoiceDraftRules', 'InvoiceIssueRules', 'PhotoJournalRules', 'WorklogRules',
                 'ManualChangeRules', 'TrashRules', 'LegacyBackupPolicy', 'PixelDriveState'):
        contracts[name] = hashlib.sha256((JAVA/(name+'.java')).read_bytes()).hexdigest()
    store = JAVA/('PixelLocalStore.java' if (JAVA/'PixelLocalStore.java').exists() else 'MainActivity.java')
    bridge = javascript_bridges()
    return dict(tools=tools, ui_actions=ui['actions'], observations=observations,
                contracts=contracts,
                sqlite_schema=re.findall(r'"(CREATE (?:TABLE|INDEX)[^"]+)"', store.read_text()),
                javascript_bridge=bridge,
                bootstrap=json.loads((ROOT/'android/app/src/main/assets/initial-data.json').read_text()),
                offer_contract=json.loads((ROOT/'android/app/src/main/assets/offer-contract.json').read_text()),
                package='at.hausgarten.service',
                protocol=int(re.search(r'PROTOCOL_VERSION\s*=\s*(\d+)', (JAVA/'PixelMcpReadTools.java').read_text())[1]))


def approved_revier_timer(expected, current, delta, repair, evolution):
    """Remove only the explicitly reviewed additions, then run the original gate."""
    import copy
    if (evolution.get('version') != 1 or not evolution.get('authorization')
            or evolution.get('baseline_sha256') != hashlib.sha256(BASELINE.read_bytes()).hexdigest()
            or evolution.get('added_tool', {}).get('name') != 'review_work_session'
            or evolution.get('added_ui_action', {}).get('id') != 'zeiterfassung.pruefen'
            or evolution.get('added_bridge') != 'String reviewWorkSession(String sessionId,String input) '):
        raise ValueError('Unapproved Revier timer contract scope')
    previous = copy.deepcopy(current)
    for group, key in [('tools', 'added_tool'), ('ui_actions', 'added_ui_action'),
                       ('javascript_bridge', 'added_bridge')]:
        addition = evolution[key]
        if previous[group].count(addition) != 1:
            return [group]
        previous[group].remove(addition)
    return approved_evolution(expected, previous, delta, schema_repair=repair)


def approved_revier_local_workflows(expected, current, delta, repair, timer, evolution):
    """Admit only named new workflows and two optional stable-context properties.

    Exact additions are removed before the original P1/timer gates. Existing
    schemas, action order, package, SQL, bootstrap and observations remain frozen.
    """
    import copy
    names = {
        'get_full_backup_status', 'get_restore_status', 'request_full_backup', 'retry_full_backup',
        'get_revier_overview', 'list_properties', 'get_property', 'create_property',
        'update_property', 'list_inquiries', 'get_inquiry', 'create_inquiry',
        'update_inquiry', 'plan_inquiry_inspection', 'list_care_plans',
        'create_care_plan', 'update_care_plan', 'generate_care_visits', 'list_visits',
        'get_visit', 'plan_visit', 'update_visit', 'get_day_tour', 'set_day_tour',
        'move_tour_stop', 'list_materials', 'save_material',
        'record_material_movement', 'list_equipment', 'save_equipment',
        'record_equipment_usage', 'search_local',
        'get_payment_ledger', 'record_payment', 'correct_payment',
        'get_invoice_document_job', 'retry_invoice_document_job',
        'preview_local_authority', 'activate_local_authority',
        'reconcile_local_invoice_authority',
        'get_offer_decision_history', 'record_offer_acceptance', 'decline_offer',
    }
    bridges = {'void openFullBackup() ', 'void checkpointFullBackup(String snapshot) ',
               'String restoredDraftReview(String raw) ',
               'String mediaStatus(String raw)', 'String beginMediaUpload(String raw)',
               'String writeMediaChunk(String raw)', 'String finishMediaUpload(String raw)',
               'String readMediaChunk(String raw)', 'String readMediaRecord(String raw)',
               'String writeMediaRecord(String raw)', 'String beginMediaMirror(String raw)',
               'String completeMediaMirror(String raw)'}
    additions = evolution.get('added_tools', [])
    if (evolution.get('version') != 1 or not evolution.get('authorization')
            or evolution.get('baseline_sha256') != hashlib.sha256(BASELINE.read_bytes()).hexdigest()
            or len(additions) != 43 or {item.get('name') for item in additions} != names
            or len(evolution.get('added_bridges', [])) != 12
            or set(evolution['added_bridges']) != bridges):
        raise ValueError('Unapproved Revier local-workflow contract scope')
    actions = evolution.get('added_ui_actions', [])
    if len(actions) != 47 or len({a.get('id') for a in actions}) != 47:
        raise ValueError('Unapproved Revier local UI scope')
    mapped = [a for a in actions if a.get('kind') == 'business']
    platform = [a for a in actions if a.get('kind') == 'platform_or_presentation']
    platform_ids = {'platform.recovery_vollbackup', 'platform.vollbackup_saf',
                    'platform.vollrestore_bestaetigen', 'platform.portabilitaet_ziel'}
    if (len(mapped) != 43 or {tuple(a.get('mcp', [])) for a in mapped} != {(n,) for n in names}
            or len(platform) != 4 or {a.get('id') for a in platform} != platform_ids
            or any(a.get('mcp') != [] or not a.get('barrier') or not a.get('platform_only') for a in platform)):
        raise ValueError('Unapproved Revier local UI/MCP mapping')
    previous = copy.deepcopy(current)
    for group, items in [('tools', additions), ('ui_actions', actions),
                         ('javascript_bridge', evolution['added_bridges'])]:
        for item in items:
            if previous[group].count(item) != 1:
                return [group]
            previous[group].remove(item)
    changes = evolution.get('modified_tools', [])
    if len(changes) != 8 or {c.get('name') for c in changes} != {'start_work', 'create_photo', 'update_photo', 'create_invoice_draft', 'update_invoice_draft', 'request_invoice_issue', 'create_backup', 'restore_backup'}:
        raise ValueError('Unapproved stable-context schema scope')
    originals = {tool['name']: tool for tool in expected['tools']}
    for change in changes:
        name = change['name']
        if not change.get('reason') or change.get('before') != originals[name]:
            raise ValueError('Stable-context predecessor changed')
        approved = copy.deepcopy(originals[name])
        if name in {'request_invoice_issue','create_backup','restore_backup'}:
            descriptors = {
                'request_invoice_issue': ('Beauftragt den Pixel-Rechnungsabschluss nach ausdrücklicher Nutzeranweisung. Online-Nummernreservierung mit Versionsschutz und Original-PDF erforderlich; kein automatischer Versand oder Zahlungsstatus.', 'Beauftragt den Pixel-Rechnungsabschluss nach ausdrücklicher Nutzeranweisung. get_capabilities.invoice_finalization legt den versionierten Pfad fest: im Altbetrieb geschützter Onlinevergleich, nach kontrollierter Umstellung atomare lokale Nummernvergabe mit Belegsnapshot und dauerhaftem PDF-Job. Im lokalen Pfad die vollständige get_invoice.issue_review prüfen und deren record_hash sowie context_hash als expected_record_hash und expected_context_hash senden. Nach Restore bis externem Original-/Nummernabgleich gesperrt. Keine erfolgreiche PDF-Freigabe vor verifiziertem Original; kein automatischer Versand oder Zahlungsstatus.'),
                'create_backup': ('Verwendet die bestehenden App-Speicher und fachlichen Prüfungen.', 'Erstellt die bisherige begrenzte Geschäftssicherung im vorhandenen Altformat für Geschäftssnapshot, Journal und lokale Originale. Keine verschlüsselte Vollsicherung und kein vollständiger Restore aller neuen Revier-/Jobdaten. Vollbackup über request_full_backup beziehungsweise die geschützte Geräteoberfläche verwenden.'),
                'restore_backup': ('Prüft Sicherung und übernimmt ausschließlich fehlende Datensätze. Abweichende vorhandene Versionen und aktive Warteschlangen stoppen die Übernahme; keine blinde Ersetzung.', 'Versionierter Altpfad: im Legacybetrieb Sicherung prüfen und ausschließlich fehlende Datensätze übernehmen; Konflikte und aktive Warteschlangen stoppen. Nach lokaler Autoritätsumstellung mit RESTORE_FULL_REPLACEMENT_REQUIRED und Geräteschritt gesperrt: vollständigen verschlüsselten Bestand prüfen und ausdrücklich ersetzen, keine automatische Vermischung.'),
            }
            before, after = descriptors[name]
            if not approved['description'].startswith(before):
                raise ValueError('Invoice authority descriptor predecessor changed')
            approved['description'] = approved['description'].replace(before, after, 1)
            if name == 'request_invoice_issue':
                approved['inputSchema']['properties']['expected_context_hash'] = {'type':'string','maxLength':64,'pattern':'[0-9a-f]{64}'}
        else:
            properties = approved['inputSchema']['properties']
            if name in {'update_photo', 'update_invoice_draft'}:
                properties = properties['updates']['properties']
            if name in {'create_invoice_draft', 'update_invoice_draft'}:
                properties = properties['positions']['items']['properties']
                fields = {
                    'source_type': {'type':'string', 'maxLength':500, 'minLength':1, 'enum':['worklog','material_movement','manual']},
                    'source_id': {'type':'string', 'maxLength':500, 'minLength':1},
                    'source_quantity_decimal': {'type':'string', 'maxLength':64, 'pattern':r'(?:0|[1-9][0-9]*)(?:\.[0-9]+)?'},
                }
            else:
                fields = {key:{'type':'string', 'maxLength':500, 'minLength':1} for key in ('property_id','visit_id')}
            for key, spec in fields.items():
                if key in properties:
                    raise ValueError('Stable context already existed')
                properties[key] = spec
        if approved != change.get('after'):
            raise ValueError('Unapproved change beyond optional property/visit context')
        matches = [i for i,t in enumerate(previous['tools']) if t['name'] == name]
        if len(matches) != 1 or previous['tools'][matches[0]] != approved:
            return ['tools']
        previous['tools'][matches[0]] = copy.deepcopy(originals[name])
    # Two reviewed financial corrections; the frozen source hashes remain in P1.
    contract_changes = evolution.get('modified_contracts', [])
    reviewed_contracts = {
        'OfferRules': 'f7fa9a091582e86276294adc3526edf6d5fd16cd4a18f38fdc778fb46e0bb9d0',
        'InvoiceDraftRules': '6e152cb6d6e5451f7e5c568995fe42cd73a4c383169cfb12bda865ee2b640917',
    }
    if len(contract_changes) != 2 or {c.get('name') for c in contract_changes} != set(reviewed_contracts):
        raise ValueError('Unapproved local financial contract scope')
    for change in contract_changes:
        name = change['name']
        if (change.get('before') != expected['contracts'][name]
                or change.get('after') != reviewed_contracts[name] or not change.get('reason')):
            raise ValueError('Local financial contract predecessor or reviewed hash changed')
        if previous['contracts'].get(name) != change['after']:
            return ['contracts']
        previous['contracts'][name] = change['before']
    return approved_revier_timer(expected, previous, delta, repair, timer)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--record-baseline', action='store_true')
    args = parser.parse_args()
    current = observe()
    if args.record_baseline:
        # Explicit, exclusive capture before structural edits; never silently refresh expected behavior.
        with BASELINE.open('x') as stream:
            json.dump(dict(commit='1abf049c1e27167bc16259d8717513aeb5ffe670', contract=current), stream,
                      ensure_ascii=False, indent=2)
            stream.write('\n')
    expected = json.loads(BASELINE.read_text())['contract']
    changed = differences(expected, current)
    if changed:
        delta = json.loads((ROOT/'tests/fixtures/sync-optimization-contract-v1.json').read_text())
        repair_path = ROOT / 'tests/fixtures/mcp-authority-schema-repair-v1.json'
        repair = json.loads(repair_path.read_text()) if repair_path.exists() else None
        unapproved = approved_evolution(expected, current, delta, schema_repair=repair)
        if unapproved:
            evolution = json.loads((ROOT/'tests/fixtures/revier-timer-contract-v1.json').read_text())
            unapproved = approved_revier_timer(expected, current, delta, repair, evolution)
        if unapproved:
            local = json.loads((ROOT/'tests/fixtures/revier-local-workflows-contract-v1.json').read_text())
            unapproved = approved_revier_local_workflows(expected, current, delta, repair, evolution, local)
        if unapproved:
            raise SystemExit('FAIL unreviewed functional contract changed: '+', '.join(unapproved))
        print('PASS explicitly reviewed contract evolution; original P1 differences: '+', '.join(changed))
    read = sum(t['annotations']['readOnlyHint'] for t in current['tools'])
    print(json.dumps(dict(result='PASS', tools=len(current['tools']), read=read,
                          write=len(current['tools'])-read, ui_actions=len(current['ui_actions']),
                          contract_sha256=digest(current)), sort_keys=True))

if __name__ == '__main__':
    main()

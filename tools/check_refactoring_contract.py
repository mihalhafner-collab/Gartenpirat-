"""P1 frozen public contracts and synthetic observations; no operating store access."""
from pathlib import Path
import argparse
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[1]
BASELINE = ROOT / 'tests/fixtures/refactoring-baseline.json'
JAVA = ROOT / 'android/app/src/main/java/at/hausgarten/service'

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
    bridge_sources = (JAVA/'MainActivity.java').read_text()
    if (JAVA/'BusinessDataBridge.java').exists():
        bridge_sources += (JAVA/'BusinessDataBridge.java').read_text()
    bridge = sorted(re.findall(r'@JavascriptInterface\s+public\s+([^\{]+)\{', bridge_sources))
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
            raise SystemExit('FAIL unreviewed functional contract changed: '+', '.join(unapproved))
        print('PASS explicitly reviewed contract evolution; original P1 differences: '+', '.join(changed))
    read = sum(t['annotations']['readOnlyHint'] for t in current['tools'])
    print(json.dumps(dict(result='PASS', tools=len(current['tools']), read=read,
                          write=len(current['tools'])-read, ui_actions=len(current['ui_actions']),
                          contract_sha256=digest(current)), sort_keys=True))

if __name__ == '__main__':
    main()

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
        raise SystemExit('FAIL P1 contract changed: '+', '.join(changed))
    read = sum(t['annotations']['readOnlyHint'] for t in current['tools'])
    print(json.dumps(dict(result='PASS', tools=len(current['tools']), read=read,
                          write=len(current['tools'])-read, ui_actions=len(current['ui_actions']),
                          contract_sha256=digest(current)), sort_keys=True))

if __name__ == '__main__':
    main()

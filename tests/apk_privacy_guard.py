"""Mandatory three-stage privacy gate; never opens a productive database."""
import argparse,json,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
from privacy_guard import source_gate,workspace_gate,artifact_gate,scan_files

# Compatibility for the existing source-package helper: no live marker lookup.
def private_markers():return set()
def scan(files,markers=()):
    scan_files(files)
    if markers:raise ValueError('Live markers are forbidden; use the data-independent privacy contract.')
def inspect():
    source_gate()
    return {str(p.relative_to(ROOT)):p.read_bytes() for p in (ROOT/'app/src/main/assets').rglob('*') if p.is_file()}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--apk');parser.add_argument('--artifact');parser.add_argument('--prebuild',action='store_true');args=parser.parse_args()
    if not (args.artifact or args.apk or args.prebuild):parser.error('Release-Abnahme benötigt ein tatsächliches Artefakt; --prebuild prüft nur die Vorstufe.')
    result={'stage':'PREBUILD_ONLY' if args.prebuild else 'RELEASE_ARTIFACT','source_privacy':source_gate(),'workspace_privacy':workspace_gate(),'artifact_privacy':artifact_gate(args.artifact or args.apk) if args.artifact or args.apk else 'NOT_TESTABLE','secret_values_printed':False}
    print(json.dumps(result))

if __name__=='__main__':
    try:main()
    except Exception as error:print('PRIVACY_GATE_FAILED '+str(error));sys.exit(1)

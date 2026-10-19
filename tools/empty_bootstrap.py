"""Only permitted source bootstrap writer: a constant empty public contract."""
import argparse
import json
import sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from bootstrap_contract import BOOTSTRAP, verify_bootstrap
import runtime_paths


def generate(target=None):
    canonical = ROOT / 'android/app/src/main/assets/initial-data.json'
    target = Path(target) if target is not None else canonical
    if target.resolve() != canonical.resolve() and runtime_paths.synthetic_root(target) is None:
        raise ValueError('Only the canonical empty bootstrap or an isolated synthetic test target is allowed.')
    target.write_text(json.dumps(BOOTSTRAP, ensure_ascii=False, separators=(',', ':')) + '\n')

if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--write', action='store_true')
    args = parser.parse_args()
    target = ROOT / 'android/app/src/main/assets/initial-data.json'
    if args.write:
        generate()
    verify_bootstrap(target.read_bytes())
    print('PASS empty bootstrap; operational_seed_rows=0')

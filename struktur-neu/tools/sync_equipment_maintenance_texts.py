"""Validate the single public maintenance phrase catalogue consumed by Compose."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'android/app/src/main/assets/equipment-maintenance-texts.json'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    parser.parse_args()
    phrases = json.loads(SOURCE.read_text())['actions']
    if len({row['id'] for row in phrases}) != len(phrases):
        raise SystemExit('Duplicate maintenance phrase')
    if any(not all(isinstance(row.get(key), str) and row[key].strip() for key in ('id','label','text')) for row in phrases):
        raise SystemExit('Invalid maintenance phrase')
    print(f'PASS {len(phrases)} public offline maintenance phrases')

if __name__ == '__main__':
    main()

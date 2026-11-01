"""Deterministic source metrics, not a claim of dynamic call coverage."""
from pathlib import Path
import json
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT/'app/src/main/java/at/hausgarten/service'
NAMES = ('PixelOwnerAccess', 'PixelMcpReadTools', 'PixelMcpOwnerTools', 'MainActivity')

BASELINE = '1abf049c1e27167bc16259d8717513aeb5ffe670'

def measure(commit=None):
    classes = {p.stem for p in JAVA.glob('*.java')}
    results = {}
    for name in NAMES:
        text = ((JAVA/(name+'.java')).read_text() if commit is None else
                subprocess.run(['git','show',f'{commit}:android/app/src/main/java/at/hausgarten/service/{name}.java'],
                               cwd=ROOT, check=True, capture_output=True, text=True).stdout)
        dependencies = sorted((set(re.findall(r'\b[A-Z]\w+\b', text)) & classes) - {name})
        results[name] = dict(lines=len(text.splitlines()), bytes=len(text.encode()),
                             switches=len(re.findall(r'\bswitch\s*\(', text)),
                             cases=len(re.findall(r'\bcase\s+"', text)),
                             dependencies=dependencies,
                             business_dependencies=[n for n in dependencies if n.endswith(('Rules','Tools','ApplicationService'))],
                             direct_store_calls=len(re.findall(r'\bstore\.', text)),
                             direct_drive_calls=len(re.findall(r'\b(?:DriveSyncCoordinator|DriveDirectFiles|DriveRestClient)\.', text)),
                             # Same stable subset before/after: query/insert/delete;
                             # exclude camera File.delete, DDL and generic preference writes.
                             direct_sql=len(re.findall(r'\.(?:rawQuery|insertOrThrow|insertWithOnConflict)\(', text))+
                                len(re.findall(r'\b(?:db|database|tx)\.delete\(|\b(?:store\.)?getWritableDatabase\(\)\.delete\(', text)))
    return results

if __name__ == '__main__':
    print(json.dumps(measure(BASELINE if '--before' in sys.argv else None), indent=2, sort_keys=True))

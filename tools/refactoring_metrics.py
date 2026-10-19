"""Deterministic source metrics, not a claim of dynamic call coverage."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT/'android/app/src/main/java/at/hausgarten/service'
NAMES = ('PixelOwnerAccess', 'PixelMcpReadTools', 'PixelMcpOwnerTools', 'MainActivity')

def measure():
    classes = {p.stem for p in JAVA.glob('*.java')}
    results = {}
    for name in NAMES:
        text = (JAVA/(name+'.java')).read_text()
        dependencies = sorted((set(re.findall(r'\b[A-Z]\w+\b', text)) & classes) - {name})
        results[name] = dict(lines=len(text.splitlines()), bytes=len(text.encode()),
                             switches=len(re.findall(r'\bswitch\s*\(', text)),
                             cases=len(re.findall(r'\bcase\s+"', text)),
                             dependencies=dependencies,
                             direct_sql=len(re.findall(r'\.(?:rawQuery|insertOrThrow|insertWithOnConflict|delete)\(', text)))
    return results

if __name__ == '__main__':
    print(json.dumps(measure(), indent=2, sort_keys=True))

"""Check that every app action maps to its ChatGPT (MCP) tool."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'android/app/src/main/java/at/hausgarten/service'
TARGET = ROOT / 'android/app/src/main/assets/ui-mcp-actions.json'
manifest = json.loads(TARGET.read_text())


def native_source(name):
    path = SOURCE / (name + '.kt')
    if not path.is_file():
        raise SystemExit('Expected Kotlin source: ' + name)
    return path


for action in manifest['actions']:
    for key in ('source', 'native_dispatch'):
        path = SOURCE / action[key]
        assert path.is_file() and path.suffix == '.kt', 'Missing native action source: ' + action['id']

# A reintroduced WebView bridge needs an explicit architecture review.
for path in SOURCE.glob('*'):
    if path.suffix in ('.java', '.kt') and re.search(r'@(?:android\.webkit\.)?JavascriptInterface\b|\bWebView\s*\(', path.read_text()):
        raise SystemExit('Unexpected WebView UI source: ' + str(path.relative_to(ROOT)))

# Check every executed menu entry against the current semantic inventory.
pattern = re.compile(r'NativeAction\s*\(\s*"([^"\\]*(?:\\.[^"\\]*)*)"\s*,\s*"([^"\\]*(?:\\.[^"\\]*)*)"\s*,\s*"([^"\\]*(?:\\.[^"\\]*)*)"\s*,\s*listOf\(([^)]*)\)\s*,?([^)]*)\)', re.S)
entries = pattern.findall(native_source('NativeActionCatalog').read_text())
registry = {entry[0]: entry for entry in entries}
assert len(entries) == len(registry) == len(manifest['actions'])
assert entries, "Empty executable action registry"
assert set(registry) == {action['id'] for action in manifest['actions']}
known = set()
for path in SOURCE.glob('*Tools.kt'):
    known.update(re.findall(r'"([a-z][a-z0-9_]+)"', path.read_text()))
for action in manifest['actions']:
    _, area, label, tools, flags = registry[action['id']]
    assert area == action['area'] and json.loads('"' + label + '"') == action['action'], 'Registry action mismatch: ' + action['id']
    assert re.findall(r'"([a-z][a-z0-9_]+)"', tools) == action['mcp'], 'Registry tool mismatch: ' + action['id']
    assert ('retired = true' in flags) == action.get('retired', False), 'Registry retirement mismatch: ' + action['id']
    assert ('platformOnly = true' in flags) == action['platform_only'], 'Registry platform mismatch: ' + action['id']
    if action['kind'] == 'business':
        assert action['mcp'] and all(name in known for name in action['mcp']), 'Missing MCP mapping: ' + action['id']
    else:
        assert action['platform_only'] and action.get('barrier'), 'Missing platform exception: ' + action['id']

print(f'PASS {len(entries)} semantic actions and exact executable registry mapping')

"""Review native UI sources and the complete semantic UI/MCP action mapping."""
from pathlib import Path
import hashlib
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'android/app/src/main/java/at/hausgarten/service'
TARGET = ROOT / 'android/app/src/main/assets/ui-mcp-actions.json'
manifest = json.loads(TARGET.read_text())


def native_source(name):
    path = SOURCE / (name + '.kt')
    if not path.is_file():
        raise SystemExit('Expected reviewed Kotlin source: ' + name)
    return path


# Every Compose source joins the review automatically, including newly added screens.
paths = {p for p in SOURCE.glob('*.kt') if re.search(r'@(?:androidx\.compose\.runtime\.)?Composable\b', p.read_text())}
paths.update(native_source(name) for name in (
    'MainActivity', 'NativePrimaryScreens', 'NativeForms', 'NativeCompose',
    'NativeAppController', 'NativeActionCatalog', 'NativeDataRepository',
    'LocalJournalRules', 'GardenRules', 'BillingRules', 'TestDataRules', 'WorklogRules',
    'EntityProtectionRules', 'OwnerJournalRules', 'LocalActionOrder',

    'PixelOwnerAccess', 'LocalOwnerRepository', 'PixelLocalStore',
    'WorkSessionStore', 'BrowserDraftRepository', 'BackupSettingsRepository',
    'LocalBackupDataset', 'NativeOwnerQueries', 'PixelMcpOwnerTools',
    'ArchiveApplicationService',
    'LocalBusinessState', 'OfferMoney', 'OfferFileProvider', 'RestoreStartupGuard', 'RevierApplication',
    'PlanningConnectionActivity', 'PixelTunnelActivity', 'PixelTunnelService',
    'PixelTunnelBootReceiver',  'PendingWorkPhotoStore',
    'PixelBackupService', 'BackupDocumentTransfer', 'BackupInventory',
    'EncryptedBackupArchive', 'BackupRestoreService', 'RestoreStartupProtection',
    'BackupScheduleRules', 'BackupStatusRules', 'LocalStorageMaintenance',
    'BrowserMediaRepository', 'BrowserMediaRepositoryRules', 'RestoredBrowserCheckpoint',
    'RestoredBrowserReviewRules', 'RevierApplicationService', 'RevierRules',
    'EquipmentMaintenanceRules', 'EquipmentProfilePhotoRules', 'RevierRepository',
    'RevierLocalRepository', 'RevierSnapshotRepository', 'LocalWorkflowTools',
    'PlanningApplicationService', 'OfferApplicationService',
    'OwnerRequestTransactions', 'McpErrors', 'CalendarSetupRules', 'FullBackupActivity',
    'RestoreRecoveryActivity',
))
paths.add(ROOT / 'android/app/src/main/assets/offer-contract.json')
for action in manifest['actions']:
    for key in ('source', 'native_dispatch'):
        path = SOURCE / action[key]
        assert path.is_file() and path.suffix == '.kt', 'Missing native action source: ' + action['id']
        paths.add(path)

# A reintroduced WebView bridge needs an explicit architecture review.
for path in SOURCE.glob('*'):
    if path.suffix in ('.java', '.kt') and re.search(r'@(?:android\.webkit\.)?JavascriptInterface\b|\bWebView\s*\(', path.read_text()):
        raise SystemExit('Unexpected WebView UI source: ' + str(path.relative_to(ROOT)))
observed = {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(paths)}

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

if '--record-reviewed' in sys.argv:
    manifest['reviewed_sources'] = observed
    TARGET.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
elif manifest['reviewed_sources'] != observed:
    raise SystemExit('UI source review required; fingerprint or source set changed')
print(f'PASS native UI review fingerprints; {len(entries)} semantic actions and exact executable registry mapping')

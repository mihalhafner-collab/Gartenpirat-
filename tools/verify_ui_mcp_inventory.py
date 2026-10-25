"""Versioned semantic UI audit. Record fingerprints only after reviewing UI changes."""
from pathlib import Path
import hashlib,json,sys,re
ROOT=Path(__file__).resolve().parents[1];target=ROOT/'android/app/src/main/assets/ui-mcp-actions.json';m=json.loads(target.read_text())
paths=sorted(p for p in (ROOT/'android/app/src/main/assets').glob('*') if p.suffix in ('.js','.css') or p.name in ('mobile.html','backup-restore-drafts.html','offer-contract.json'))
paths += [ROOT/f'android/app/src/main/java/at/hausgarten/service/{n}.java' for n in ('MainActivity','DriveJournalRules','DriveRolePolicy','DriveSyncCoordinator','DriveSyncJob','DriveConnectionActivity','PixelOwnerAccess','LocalOwnerRepository','PixelLocalStore','WorkSessionStore','BusinessDataBridge','BrowserDraftRepository','BackupSettingsRepository','LocalBackupDataset','BrowserMediaBridge','NativeOwnerQueries','PixelMcpOwnerTools','SyncArchiveApplicationService','LocalMigrationInventory','LocalAuthorityMigration','LocalAuthorityRules','PlanningConnectionActivity','DriveConnectionActivity','PixelTunnelActivity','NativeUi','BackgroundWorkDispatch','PendingWorkPhotoStore','BackupAutomation','BackupScheduleRules','TrashMediaCleanup','BrowserMediaRepository','BrowserMediaRepositoryRules','RestoredBrowserCheckpoint','RestoredBrowserReviewRules','RevierApplicationService','LocalWorkflowTools','PlanningApplicationService','OfferApplicationService','LocalInvoiceService','OwnerRequestTransactions','McpErrors','CalendarSetupRules','FullBackupActivity','RestoreRecoveryActivity','LocalAuthorityActivity')]
inventoried_java={p for p in paths if p.suffix=='.java'}
for path in (ROOT/'android/app/src/main/java/at/hausgarten/service').rglob('*.java'):
 if re.search(r'@(?:android\.webkit\.)?JavascriptInterface\b',path.read_text()) and path not in inventoried_java:raise SystemExit('UI bridge source review required: '+str(path.relative_to(ROOT)))
observed={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
if '--record-reviewed' in sys.argv:
 m['reviewed_sources']=observed;m['protocol_version']=9
 for a in m['actions']:
  a['platform_only']=a['kind']!='business'
  if 'validate_offer' in a['mcp'] and 'validate_offer_draft' not in a['mcp']:a['mcp'].append('validate_offer_draft')
  if 'create_offer_draft' in a['mcp'] and 'create_and_validate_offer' not in a['mcp']:a['mcp'].append('create_and_validate_offer')
  if 'prepare_offer_share' in a['mcp']:
   for n in ('prepare_offer_delivery','confirm_offer_delivery'):
    if n not in a['mcp']:a['mcp'].append(n)
  if 'get_ui_action_catalog' in a['mcp']:
   for n in ('get_offer_contract','run_self_test'):
    if n not in a['mcp']:a['mcp'].append(n)
  a.setdefault('test_scope','Mapping/schema/catalog contract; executed workflow tests are listed in CODEX-STATUS.md')
 target.write_text(json.dumps(m,ensure_ascii=False,indent=2)+'\n')
else:
 if m['reviewed_sources']!=observed:raise SystemExit('UI source review required; fingerprint or source set changed')
known=set()
for p in (ROOT/'android/app/src/main/java/at/hausgarten/service').glob('*Tools.java'):
 known.update(re.findall(r'\"([a-z][a-z0-9_]+)\"',p.read_text()))
for a in m['actions']:
 if a['kind']=='business':
  assert a['mcp'] and all(n in known for n in a['mcp']), 'Missing/unknown MCP mapping: '+a['id']
 else:assert a.get('platform_only') and a.get('barrier'), 'Missing explicit platform exception'
assert len(m['actions'])==209 and len({a['id'] for a in m['actions']})==209
print('PASS UI review fingerprints;209 semantic actions')

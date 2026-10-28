"""Compile and execute original Java contract tests against synthetic JSON data.

Requires a JDK, HG_JSON_JAR, resolved pinned Gradle dependencies and ANDROID_HOME
(or ANDROID_SDK_ROOT). No ADB,
installation, credentials or productive records are accessed.
"""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
jar = Path(os.environ.get('HG_JSON_JAR', ROOT/'.dependencies/pixel-tunnel-tests/json-20240303.jar'))
if hashlib.sha256(jar.read_bytes()).hexdigest() != '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed':
    raise RuntimeError('JSON test dependency mismatch')
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
android = sdk/'platforms/android-37.0/android.jar'
classes = ROOT/'outputs/integration-java/classes'
classes.mkdir(parents=True, exist_ok=True)
src = ROOT/'android/app/src/main/java/at/hausgarten/service'
names = ['DeletionBackupStore', 'SnapshotProjection', 'OwnerCommand', 'EntityContract', 'EntityApplicationService', 'OwnerApplicationService','PlanningApplicationService','OfferApplicationService','WorkApplicationService','PhotoApplicationService','SyncArchiveApplicationService','SettingsApplicationService','InvoiceApplicationService','OwnerRepository', 'McpFields', 'McpErrors', 'SnapshotReadSupport', 'BusinessQueries', 'OwnerToolSchemas', 'OfferContract', 'OfferDeliveryClient', 'TestDataRules', 'AppointmentRules', 'AppointmentTools', 'WeatherRiskRules', 'FreeWeatherSource', 'WeatherAlertRules', 'WeatherLocationRules', 'CalendarRules', 'CalendarAccessRules', 'AppointmentAvailability', 'CalendarClient', 'CalendarCredential', 'LegacyBackupPolicy', 'TrashPurgeBatch', 'WorklogArchiveBatch', 'TrashRules', 'DriveTransferPool', 'DriveMediaBatch', 'SyncWakeup', 'SyncMediaPlan', 'SyncPolicy', 'PhotoJournalRules', 'DeletionRules', 'InvoiceIssueRules', 'DriveNumbering', 'DrivePrimaryState', 'PixelDriveState', 'OfferRules', 'OfferTools', 'OfferJournalRules', 'LegacyConflictRules', 'PhotoPolicy', 'BillingRules', 'WorklogRules', 'PixelMcpWriteTools', 'EntityProtectionRules',
         'PixelMcpEntityWriteTools', 'PixelMcpReadTools', 'PixelMcpSessionTools', 'PixelMcpBusinessTools',
         'WorklogRelationRules', 'ManualChangeRules', 'GardenRules', 'SyncActionOrder', 'DriveRestClient', 'DrivePublication', 'McpSchema', 'McpFileTransfer', 'InvoiceDraftRules', 'PixelMcpOwnerTools', 'OwnerJournalRules', 'DriveJournalRules', 'OrderReviewRules', 'PixelTunnelConfig',
         'PixelTunnelWire', 'WorkSessionPauseRules']
names += ['DriveRolePolicy','BackgroundWorkDispatch','LocalMigrationInventory','WorkSessionTimingRules','SyncCommitRules','OwnerRequestTransactions','ReceiptRetentionRules','SyncFailureRules','LocalTestTombstone','ActiveWorkProtection','ImmutableStateReference','CatalogSemanticHash','SyncQueueCounters','VerifiedSyncCache','SyncIoFailure','CalendarRecoveryRules','OpenMeteoWeatherSource','CalendarSetupRules','PlanningWire']
names += ['DriveAccount', 'BackupCrypto', 'BackupRecoveryExport', 'EncryptedBackupArchive', 'ServiceCatalogBootstrap', 'BackupFilePins', 'DriveBackupUpload', 'BackupScheduleRules', 'BackupStorageBudget']
names += ['LocalAuthorityRules','LocalWorkflowTools','LocalWorkflowApplicationService','RevierRepository','RevierRules','RevierApplicationService','OfferDecisionRules','OfferDecisionService','BackupDocumentTransfer','BackupDatasetExchange','BackupPortableFormat','LocalBillingRules','OfferDecisionValidation','LocalFinancialValidation','LocalFinancialOriginals','IssuedInvoiceRules']
names += ['LocalInvoiceCreationRules', 'InvoiceIssueReviewRules', 'RestoredBrowserReviewRules', 'BrowserMediaFiles', 'BrowserMediaRepositoryRules', 'EquipmentMaintenanceRules', 'EquipmentProfilePhotoRules']
tests = sorted((ROOT/'tests/java/at/hausgarten/service').glob('*Tests.java'))
dependency_cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle'))/'caches/modules-2/files-2.1'
crypto_jars = []
for group, artifact, version in [('com.google.crypto.tink', 'tink-android', '1.24.0'),
                                 ('com.google.code.gson', 'gson', '2.13.2')]:
    resolved = list((dependency_cache/group/artifact/version).glob('*/*.jar'))
    if len(resolved) != 1:
        raise RuntimeError('Resolve the pinned Android dependency with Gradle first: '+artifact)
    crypto_jars += resolved
cp = os.pathsep.join(map(str, [classes, jar, android, *crypto_jars]))

def run(command):
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding='utf-8', timeout=180)
    if result.returncode:
        print(result.stdout, result.stderr)
        raise RuntimeError('Native contract suite failed')
    return result.stdout

run([shutil.which('javac'), '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes),
     *[str((src if (src/(name+'.java')).exists() else ROOT/'android/app/src/androidTest/java/at/hausgarten/service')/(name+'.java')) for name in names], str(ROOT/'android/app/src/androidTest/java/at/hausgarten/service/LegacyDriveRestFixture.java'), str(ROOT/'tests/java/at/hausgarten/service/SyntheticBusinessData.java'), *map(str, tests)])
results = []
for test in tests:
    # Execute real Tink on the host JCE without Android stub classes. The heap is
    # smaller than the complete encrypted/archive/restored dataset on disk.
    crypto = test.stem in ('EncryptedBackupArchiveTests','DriveBackupUploadTests','BackupDocumentTransferTests','BackupPortableFormatTests','LocalFinancialOriginalsTests','BrowserMediaFilesTests')
    runtime_cp = os.pathsep.join(map(str, [classes, jar, *crypto_jars])) if crypto else cp
    output = run([shutil.which('java'), *(['-Xmx64m'] if crypto else []), '-Dfile.encoding=UTF-8', '-cp', runtime_cp, 'at.hausgarten.service.'+test.stem])
    (classes.parent/(test.stem+'.log')).write_text(output)
    if test.stem == 'OrderReviewTests':
        sys.path.insert(0, str(ROOT))
        import sync_review
        cross = {'client_id': 'hash', 'kind': 'order', 'payload': {'id': 'qa_o', 'title': 'ÄÖÜ </script> \u2028 ☘',
                 'hours': 1.00, 'customer_visible': True}, 'created_at': '2026-10-15T08:00:00Z'}
        if 'CROSS_HASH '+sync_review.digest(cross) not in output:
            raise RuntimeError('Android/Python review hash differs')
    if test.stem == 'OffersAndPhotosTests':
        sys.path.insert(0, str(ROOT))
        import offers, sync_review
        def record(prefix):return next(line[len(prefix):] for line in output.splitlines() if line.startswith(prefix))
        fixture=json.loads(record('CROSS_OFFER_STATE '));row=json.loads(record('CROSS_OFFER_ROW '))
        if record('CROSS_OFFER_CONTEXT_HASH ')!=sync_review.digest(offers.context(row,fixture)):
            raise RuntimeError('Android/Python offer customer/context hash differs')
        original=sync_review.digest(row)
        if original!=sync_review.digest(offers.normalize(row,None,fixture)):
            raise RuntimeError('Android/Python offer normalization differs')
        if json.loads(record('CROSS_OFFER_POSITIONS '))!=offers.position_text(row['positions']):
            raise RuntimeError('Android/Python order description differs')
    if test.stem == 'OwnerToolsTests':
        sys.path.insert(0, str(ROOT))
        import owner_journal, sync_review
        fixture={'id':'qa_c','name':'QA','active':0,'custom_fields':'{"A":true}','updated_at':'t2'}
        if 'CROSS_OWNER_HASH '+sync_review.digest(owner_journal.customer_guard(fixture)) not in output:
            raise RuntimeError('Android/Python archive guard differs')
    results.append({'suite': test.stem, 'result': 'PASS', 'summary': output.strip().splitlines()[-1]})
    print(results[-1], flush=True)
# Compile the actual Android repository adapter against its production dependency classes.
# The executed method uses a named, pure transaction port; native acceptance separately proves SQLite.
production = ROOT/'android/app/build/intermediates/javac/release/compileReleaseJavaWithJavac/classes'
if not (production/'at/hausgarten/service/PhotoAttachmentStore.class').is_file():
    raise RuntimeError('Compile release Java with Gradle before the repository JVM adapter suite.')
import tempfile
with tempfile.TemporaryDirectory(prefix='qa_owner_repository_') as temporary:
    owner_classes=Path(temporary)/'classes';owner_classes.mkdir()
    owner_cp=os.pathsep.join(map(str,[owner_classes,classes,production,jar,android]))
    test=ROOT/'tests/java-owner/at/hausgarten/service/LocalOwnerRepositoryTests.java'
    run([shutil.which('javac'),'-encoding','UTF-8','-implicit:none','-sourcepath',temporary,'-cp',owner_cp,'-d',str(owner_classes),str(src/'LocalOwnerRepository.java'),str(test)])
    output=run([shutil.which('java'),'-cp',owner_cp,'at.hausgarten.service.LocalOwnerRepositoryTests'])
    (classes.parent/'LocalOwnerRepositoryTests.log').write_text(output)
    results.append({'suite':'LocalOwnerRepositoryTests','result':'PASS','summary':output.strip().splitlines()[-1]})
    print(results[-1],flush=True)
(classes.parent/'results.json').write_text(json.dumps({'suites': results, 'cross_language_hash': 'PASS'}, indent=2))
if (ROOT/'tests/fixtures/refactoring-baseline.json').exists():
    print(run([sys.executable, str(ROOT/'tools/check_refactoring_contract.py')]).strip())

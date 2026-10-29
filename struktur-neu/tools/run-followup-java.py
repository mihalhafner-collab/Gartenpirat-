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
if classes.exists():shutil.rmtree(classes)  # Only generated synthetic JVM class output.
classes.mkdir(parents=True, exist_ok=True)
src = ROOT/'android/app/src/main/java/at/hausgarten/service'
names = ['DeletionBackupStore', 'SnapshotProjection', 'OwnerCommand', 'EntityContract', 'EntityApplicationService', 'OwnerApplicationService','PlanningApplicationService','OfferApplicationService','WorkApplicationService','PhotoApplicationService','SyncArchiveApplicationService','SettingsApplicationService','InvoiceApplicationService','OwnerRepository', 'McpFields', 'McpErrors', 'SnapshotReadSupport', 'BusinessQueries', 'OwnerToolSchemas', 'OfferContract', 'OfferDeliveryClient', 'TestDataRules', 'AppointmentRules', 'AppointmentTools', 'WeatherRiskRules', 'FreeWeatherSource', 'WeatherAlertRules', 'WeatherLocationRules', 'CalendarRules', 'CalendarAccessRules', 'AppointmentAvailability', 'CalendarClient', 'CalendarCredential', 'TrashPurgeBatch', 'WorklogArchiveBatch', 'TrashRules', 'SyncPolicy', 'PhotoJournalRules', 'DeletionRules', 'InvoiceIssueRules', 'PixelDriveState', 'OfferRules', 'OfferTools', 'OfferJournalRules', 'LegacyConflictRules', 'PhotoPolicy', 'BillingRules', 'WorklogRules', 'PixelMcpWriteTools', 'EntityProtectionRules',
         'PixelMcpEntityWriteTools', 'PixelMcpReadTools', 'PixelMcpSessionTools', 'PixelMcpBusinessTools',
         'WorklogRelationRules', 'ManualChangeRules', 'GardenRules', 'SyncActionOrder', 'HttpTransport', 'DrivePublication', 'McpSchema', 'McpFileTransfer', 'InvoiceDraftRules', 'PixelMcpOwnerTools', 'OwnerJournalRules', 'DriveJournalRules', 'OrderReviewRules', 'PixelTunnelConfig',
         'PixelTunnelWire', 'WorkSessionPauseRules']
names += ['DriveRolePolicy','BackgroundWorkDispatch','LocalMigrationInventory','WorkSessionTimingRules','OwnerRequestTransactions','SyncFailureRules','LocalTestTombstone','ActiveWorkProtection','CatalogSemanticHash','SyncQueueCounters','SyncIoFailure','CalendarRecoveryRules','OpenMeteoWeatherSource','CalendarSetupRules','PlanningWire']
names += ['BackupCrypto', 'EncryptedBackupArchive', 'ServiceCatalogBootstrap', 'BackupFilePins', 'BackupScheduleRules', 'BackupStatusRules', 'BackupStorageBudget']
names += ['LocalAuthorityRules','LocalWorkflowTools','LocalWorkflowApplicationService','RevierRepository','RevierRules','RevierApplicationService','OfferDecisionRules','OfferDecisionService','BackupDocumentTransfer','BackupDatasetExchange','LocalBillingRules','OfferDecisionValidation','LocalFinancialValidation','LocalFinancialOriginals','IssuedInvoiceRules']
names += ['LocalInvoiceCreationRules', 'InvoiceIssueReviewRules', 'RestoredBrowserReviewRules', 'BrowserMediaFiles', 'BrowserMediaRepositoryRules', 'EquipmentMaintenanceRules', 'EquipmentProfilePhotoRules']
table_tests = '--tables' in sys.argv
tests = sorted(p for p in (ROOT/'tests/java/at/hausgarten/service').glob('*Tests.java')
               if table_tests or p.stem != 'BusinessTableStoreTests')
if table_tests:
    names += ['BusinessTableStore']
support = sorted((ROOT/'tests/java-support').rglob('*.java'))
dependency_cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle'))/'caches/modules-2/files-2.1'
crypto_jars = []
for group, artifact, version in [('com.google.crypto.tink', 'tink-android', '1.24.0'),
                                 ('com.google.code.gson', 'gson', '2.13.2')]:
    resolved = list((dependency_cache/group/artifact/version).glob('*/*.jar'))
    if len(resolved) != 1:
        raise RuntimeError('Resolve the pinned Android dependency with Gradle first: '+artifact)
    crypto_jars += resolved
table_jars = []
if table_tests:
    for variable, digest in [
        ('HG_SQLITE_JDBC_JAR', '6dc7464e3803648d3ff18a7359bab6adf079fcd8495b18991f6f5edcb8ac6e3b'),
        ('HG_SLF4J_API_JAR', 'd3ef575e3e4979678dc01bf1dcce51021493b4d11fb7f1be8ad982877c16a1c0')]:
        dependency = Path(os.environ[variable])
        if hashlib.sha256(dependency.read_bytes()).hexdigest() != digest:
            raise RuntimeError('Table-test dependency mismatch: '+variable)
        table_jars.append(dependency)
cp = os.pathsep.join(map(str, [classes, jar, android, *crypto_jars, *table_jars]))

def run(command):
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding='utf-8', timeout=180)
    if result.returncode:
        print(result.stdout, result.stderr)
        raise RuntimeError('Java contract suite failed')
    return result.stdout

run([shutil.which('javac'), '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes),
     *[str((src if (src/(name+'.java')).exists() else ROOT/'android/app/src/androidTest/java/at/hausgarten/service')/(name+'.java')) for name in names], str(ROOT/'tests/java/at/hausgarten/service/SyntheticBusinessData.java'), *map(str, support), *map(str, tests)])
results = []
for test in tests:
    runtime_cp = os.pathsep.join(map(str, [classes, jar, *crypto_jars, *table_jars]))
    heap = '-Xmx512m' if test.stem == 'BusinessTableStoreTests' else '-Xmx64m'
    output = run([shutil.which('java'), heap, '-Dfile.encoding=UTF-8', '-cp', runtime_cp, 'at.hausgarten.service.'+test.stem])
    print(output.strip())
    results.append(test.stem)
print('PASS current Java suites:', len(results))

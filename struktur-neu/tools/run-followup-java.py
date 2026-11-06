"""Compile Kotlin/Java production code and contract tests against synthetic data.

Requires a JDK, Kotlin 2.2.10 compiler, HG_JSON_JAR, pinned dependencies and ANDROID_HOME
(or ANDROID_SDK_ROOT). No ADB,
installation, credentials or productive records are accessed.
"""
from pathlib import Path
import hashlib
import json
import os
import shlex
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
names = ['SnapshotProjection', 'OwnerCommand', 'EntityContract', 'EntityApplicationService', 'OwnerApplicationService','PlanningApplicationService','OfferApplicationService','WorkApplicationService','PhotoApplicationService','ArchiveApplicationService','SettingsApplicationService','OwnerRepository', 'McpFields', 'McpErrors', 'SnapshotReadSupport', 'BusinessQueries', 'OwnerToolSchemas', 'OfferContract', 'OfferDeliveryClient', 'TestDataRules', 'AppointmentRules', 'AppointmentTools', 'WeatherRiskRules', 'FreeWeatherSource', 'WeatherAlertRules', 'WeatherLocationRules', 'CalendarRules', 'CalendarAccessRules', 'AppointmentAvailability', 'CalendarClient', 'CalendarCredential', 'TrashPurgeBatch', 'WorklogArchiveBatch', 'TrashRules', 'PhotoJournalRules', 'DeletionRules', 'OfferRules', 'OfferTools', 'OfferJournalRules', 'PhotoPolicy', 'BillingRules', 'WorklogRules', 'PixelMcpWriteTools', 'EntityProtectionRules',
         'PixelMcpEntityWriteTools', 'PixelMcpReadTools', 'PixelMcpSessionTools', 'PixelMcpBusinessTools',
         'WorklogRelationRules', 'ManualChangeRules', 'GardenRules', 'LocalActionOrder', 'HttpTransport', 'McpSchema', 'McpFileTransfer', 'PixelMcpOwnerTools', 'OwnerJournalRules', 'LocalJournalRules',  'PixelTunnelConfig',
         'PixelTunnelWire', 'WorkSessionPauseRules']
names += ['WorkSessionTimingRules','OwnerRequestTransactions','LocalTestTombstone','ActiveWorkProtection','CatalogSemanticHash','CalendarRecoveryRules','OpenMeteoWeatherSource','CalendarSetupRules','PlanningWire']
names += ['BackupArchive', 'ServiceCatalogBootstrap', 'BackupFilePins', 'BackupScheduleRules', 'BackupStatusRules', 'BackupInventory', 'BackupStorageBudget']
names += ['OfferMoney','LocalBusinessState','LocalWorkflowTools','LocalWorkflowApplicationService','RevierRepository','RevierRules','RevierApplicationService','OfferDecisionRules','OfferDecisionService','BackupDocumentTransfer','BackupDatasetExchange','OfferDecisionValidation']
names += ['SettlementRules','SettlementTools','SettlementApplicationService','SettlementSourceProtection']
names += ['RestoredBrowserReviewRules', 'BrowserMediaFiles', 'BrowserMediaRepositoryRules', 'EquipmentMaintenanceRules', 'EquipmentProfilePhotoRules', 'AccountingRules']
table_tests = '--tables' in sys.argv
tests = sorted(p for p in (ROOT/'tests/java/at/hausgarten/service').glob('*Tests.java')
               if table_tests or p.stem != 'BusinessTableStoreTests')
if table_tests:
    names += ['BusinessTableStore']
support = sorted((ROOT/'tests/java-support').rglob('*.java'))
dependency_cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle'))/'caches/modules-2/files-2.1'
table_jars = []
if table_tests:
    for variable, digest in [
        ('HG_SQLITE_JDBC_JAR', '6dc7464e3803648d3ff18a7359bab6adf079fcd8495b18991f6f5edcb8ac6e3b'),
        ('HG_SLF4J_API_JAR', 'd3ef575e3e4979678dc01bf1dcce51021493b4d11fb7f1be8ad982877c16a1c0')]:
        dependency = Path(os.environ[variable])
        if hashlib.sha256(dependency.read_bytes()).hexdigest() != digest:
            raise RuntimeError('Table-test dependency mismatch: '+variable)
        table_jars.append(dependency)
stdlib = list((dependency_cache/'org.jetbrains.kotlin/kotlin-stdlib/2.2.10').glob('*/*.jar'))
if len(stdlib) != 1:
    raise RuntimeError('Resolve the pinned Kotlin standard library with Gradle first')
cp = os.pathsep.join(map(str, [classes, jar, android, *table_jars, *stdlib]))

def run(command):
    result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, encoding='utf-8', timeout=180)
    if result.returncode:
        print(result.stdout, result.stderr)
        raise RuntimeError('Java contract suite failed')
    return result.stdout

sources = []
for name in names:
    candidates = [folder/(name+suffix) for folder in (src, ROOT/'android/app/src/androidTest/java/at/hausgarten/service') for suffix in ('.java','.kt')]
    found = [path for path in candidates if path.is_file()]
    if len(found) != 1:
        raise RuntimeError('Expected one production or test-support source: '+name)
    sources += found
java_sources = [p for p in sources if p.suffix == '.java']
java_sources += [ROOT/'tests/java/at/hausgarten/service/SyntheticBusinessData.java', *support, *tests]
kotlin_sources = [p for p in sources if p.suffix == '.kt']
kotlin_tests = sorted((ROOT/'tests/kotlin').rglob('*.kt'))
if kotlin_sources or kotlin_tests:
    if os.environ.get('HG_KOTLINC'):
        compiler = shlex.split(os.environ['HG_KOTLINC'])
    else:
        compiler_jars = []
        for group, artifact, version in [
            ('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.2.10'),
            ('org.jetbrains.kotlin', 'kotlin-stdlib', '2.2.10'),
            ('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.2.10'),
            ('org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10'),
            ('org.jetbrains.kotlin', 'kotlin-daemon-embeddable', '2.2.10'),
            ('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0'),
            ('org.jetbrains', 'annotations', '23.0.0'),
        ]:
            resolved = list((dependency_cache/group/artifact/version).glob('*/*.jar'))
            if len(resolved) != 1:
                raise RuntimeError('Compile the Kotlin Android app with Gradle first or set HG_KOTLINC: '+artifact)
            compiler_jars += resolved
        compiler = [shutil.which('java'), '-cp', os.pathsep.join(map(str, compiler_jars)),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler']
    run([*compiler, '-no-stdlib', '-no-reflect', '-jvm-target', '17', '-classpath', cp, '-d', str(classes),
         *map(str, kotlin_sources), *map(str, kotlin_tests), *map(str, java_sources)])
run([shutil.which('javac'), '-encoding', 'UTF-8', '-cp', cp, '-d', str(classes), *map(str, java_sources)])
results = []
for test in tests:
    runtime_cp = os.pathsep.join(map(str, [classes, jar, *table_jars, *stdlib]))
    heap = '-Xmx512m' if test.stem == 'BusinessTableStoreTests' else '-Xmx64m'
    output = run([shutil.which('java'), heap, '-Dfile.encoding=UTF-8', '-cp', runtime_cp, 'at.hausgarten.service.'+test.stem])
    print(output.strip())
    results.append(test.stem)
print('PASS current JVM suites:', len(results))

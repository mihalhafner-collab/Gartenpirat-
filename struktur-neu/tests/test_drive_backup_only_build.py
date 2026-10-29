"""Keep retired transports out of the app and current synthetic test sources."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'android/app/src/main/java/at/hausgarten/service'
FIXTURES = ROOT / 'android/app/src/androidTest/java/at/hausgarten/service'


class DriveBackupOnlyBuildTests(unittest.TestCase):
    def test_business_transports_are_not_production_sources(self):
        for name in ('DriveDirectFiles', 'DriveNumbering', 'DriveMediaBatch'):
            self.assertFalse((MAIN / (name + '.java')).exists(), name)
        for name in ('LegacyDriveSyncFixture', 'LegacyDriveDirectFixture',
                     'LegacyDriveRestFixture', 'LegacyDriveAccountFixture',
                     'LegacyConflictBackupFixture', 'DriveNumbering', 'DriveMediaBatch'):
            self.assertFalse((FIXTURES / (name + '.java')).exists(), name)
        for path in MAIN.glob('*.java'):
            self.assertNotRegex(path.read_text(), r'\bLegacy\w*Fixture\b', path.name)

    def test_all_cloud_backup_transports_are_absent_from_production(self):
        for name in ('DriveAccount','DriveRestClient','DriveBackupUpload','BackupFolderPicker','BackupAutomation','FullBackupJob'):
            self.assertFalse((MAIN/(name+'.java')).exists(),name)
        for path in MAIN.glob('*.java'):
            self.assertNotRegex(path.read_text(),r'googleapis.com/(?:auth/drive|drive/v3|upload/drive)')
            self.assertNotRegex(path.read_text(),r'\bRetired(?:Backup|Drive|FullBackup|LocalBackup)\w*\b')
        self.assertNotIn('openConnection()', (MAIN/'HttpTransport.java').read_text())

    def test_unused_legacy_classes_and_writers_are_removed(self):
        for name in ('BackupPortableFormat', 'BackupRecoveryExport', 'BackupRecoveryRepairService',
                     'DriveConnectionActivity', 'DrivePrimaryState', 'DriveTransferPool',
                     'ImmutableStateReference', 'LegacyBackupPolicy', 'OwnerReceiptRetention',
                     'ReceiptRetentionRules', 'SyncCommitRules', 'SyncMediaPlan',
                     'SyncWakeup', 'VerifiedSyncCache'):
            self.assertFalse((MAIN / (name + '.java')).exists(), name)
        for name in ('RetiredDriveAccount', 'RetiredDriveBackupUpload',
                     'RetiredBackupDriveClient', 'RetiredBackupScheduleRules'):
            self.assertFalse((FIXTURES / (name + '.java')).exists(), name)
        self.assertNotIn('static OutputStream encrypt(', (MAIN/'BackupCrypto.java').read_text())
        self.assertNotIn('repairAfterVerifiedArchive(', (MAIN/'BackupRecoveryKeyStore.java').read_text())
        self.assertNotIn('DriveConnectionActivity', (ROOT/'android/app/src/main/AndroidManifest.xml').read_text())

    def test_old_jobs_have_no_registered_service_or_scheduling(self):
        manifest = ET.parse(ROOT / 'android/app/src/main/AndroidManifest.xml')
        key = '{http://schemas.android.com/apk/res/android}name'
        services = {element.get(key) for element in manifest.findall('.//service')}
        self.assertNotIn('.DriveSyncJob', services)
        self.assertNotIn('.FullBackupJob', services)
        self.assertIn('cancelRetiredJobs',(MAIN/'PixelBackupService.java').read_text())
        source = (MAIN / 'DriveSyncJob.java').read_text()
        self.assertNotIn('JobService', source)
        self.assertNotIn('new JobInfo', source)
        self.assertIn('jobs.cancel(0x484701)', source)
        self.assertIn('jobs.cancel(0x484702)', source)
        self.assertIn('DriveSyncJob.install(this)', (MAIN / 'RevierApplication.java').read_text())

    def test_retirement_facade_cannot_open_remote_or_local_business_state(self):
        source = (MAIN / 'DriveSyncCoordinator.java').read_text()
        self.assertNotRegex(source, r'\b(?:Files|Doc|DriveRestClient|ContentResolver|DocumentsContract|ExecutorService)\b')
        self.assertNotRegex(source, r'\b(?:commitSnapshot|acknowledge|pendingActionsForDrive|getWritableDatabase)\(')
        for name in ('MainActivity', 'PixelTunnelService', 'PixelOwnerAccess',
                     'PixelLocalStore', 'LocalOwnerRepository', 'PhotoThumbnailProvider'):
            self.assertNotRegex((MAIN / (name + '.java')).read_text(),
                                r'DriveSyncCoordinator\.(?:fetch\w*|poll|request|noteRequest)\(')

    def test_old_partial_restore_rejects_before_accessing_backup_or_business_data(self):
        source = (MAIN / 'LocalOwnerRepository.java').read_text()
        body = source.split('public JSONObject restoreBackup(', 1)[1].split('@Override public JSONArray pendingActions()', 1)[0]
        self.assertIn('RESTORE_FULL_REPLACEMENT_REQUIRED', body)
        self.assertNotRegex(body, r'\b(?:state|effective|fileMeta|fileBytes|enqueue|local)\(')
        self.assertNotIn('LocalAuthorityGuard', body)

    def test_plaintext_backup_rejects_cloud_and_usb_targets(self):
        source=(MAIN/'PixelBackupService.java').read_text()
        self.assertIn('"com.android.externalstorage.documents".equals(uri.getAuthority())',source)
        self.assertIn('startsWith("primary:")',source)
        self.assertLess(source.index('requirePixel(target)'),source.index('LocalBackupCoordinator service='))
        self.assertNotIn('BackupRecoveryKeyStore',source)

    def test_backup_does_not_require_financial_business_validity(self):
        # The shared capture/staging boundary is used by save, restore and rollback.
        # Explicit business-table transfer remains a separate validated operation.
        source = (MAIN/'LocalBackupDataset.java').read_text()
        backup, table_transfer = source.split('static BusinessTableStore.Evidence tableEvidence(', 1)
        self.assertNotRegex(backup, r'LocalFinancial(?:Snapshot)?Validation\s*\.\s*inspect\(')
        self.assertNotIn('.put("financial_inventory"', backup)
        self.assertIn('LocalFinancialSnapshotValidation.inspect(', table_transfer)
        for name in ('LocalBackupCoordinator', 'BackupRestoreService', 'RestoreStartupProtection'):
            self.assertIn('LocalBackupDataset.inspectStaged(', (MAIN/(name+'.java')).read_text())
        # New invoice issuance still rejects an already used number before recording it.
        invoice = (MAIN/'LocalInvoiceService.java').read_text()
        self.assertIn('"INVOICE_NUMBER_COLLISION"', invoice)
        self.assertLess(invoice.index('"INVOICE_NUMBER_COLLISION"'),
                        invoice.index('store.putOwnerValue("local_number_reservation:'))

if __name__=='__main__':unittest.main()

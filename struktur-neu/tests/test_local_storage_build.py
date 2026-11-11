"""Keep retired transports out of the app and current synthetic test sources."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / 'android/app/src/main/java/at/hausgarten/service'
FIXTURES = ROOT / 'android/app/src/androidTest/java/at/hausgarten/service'


def source(name):
    paths = [MAIN / (name + extension) for extension in ('.java', '.kt')]
    found = [path for path in paths if path.is_file()]
    if len(found) != 1:
        raise AssertionError('Expected one native implementation: ' + name)
    return found[0].read_text()


def native_sources():
    return (path for path in MAIN.rglob('*') if path.suffix in ('.java', '.kt'))


class LocalStorageBuildTests(unittest.TestCase):
    def test_business_transports_are_not_production_sources(self):
        for name in ('DriveDirectFiles', 'DriveNumbering', 'DriveMediaBatch',
                     'SyncPolicy', 'SyncIoFailure', 'SyncFailureRules',
                     'DriveJournalRules', 'PixelDriveState', 'SyncActionOrder', 'LocalAuthorityRules',
                     'IssuedInvoiceRules', 'LegacyPdfAttachment', 'InvoiceFileProvider', 'OrderReviewRules',
                     'BackgroundWorkDispatch', 'McpSyncHost'):
            self.assertFalse(any((MAIN / (name + ext)).exists() for ext in ('.java', '.kt')), name)
        for name in ('LegacyDriveSyncFixture', 'LegacyDriveDirectFixture',
                     'LegacyDriveRestFixture', 'LegacyDriveAccountFixture',
                     'LegacyConflictBackupFixture', 'DriveNumbering', 'DriveMediaBatch'):
            self.assertFalse(any((FIXTURES / (name + ext)).exists() for ext in ('.java', '.kt')), name)
        for path in native_sources():
            self.assertNotRegex(path.read_text(), r'\bLegacy\w*Fixture\b', path.name)

    def test_all_cloud_backup_transports_are_absent_from_production(self):
        for name in ('DriveAccount','DriveRestClient','DriveBackupUpload','BackupFolderPicker','BackupAutomation','FullBackupJob'):
            self.assertFalse(any((MAIN / (name + ext)).exists() for ext in ('.java', '.kt')), name)
        for path in native_sources():
            self.assertNotRegex(path.read_text(),r'googleapis.com/(?:auth/drive|drive/v3|upload/drive)')
            self.assertNotRegex(path.read_text(),r'\bRetired(?:Backup|Drive|FullBackup|LocalBackup)\w*\b')
        self.assertNotIn('openConnection()', source('HttpTransport'))

    def test_unused_legacy_classes_and_writers_are_removed(self):
        for name in ('BackupCrypto', 'BackupRecoveryKeyStore', 'EncryptedBackupArchive', 'FullBackupController', 'LocalBackupCoordinator', 'BackupJobRepository', 'BackupPortableFormat', 'BackupRecoveryExport', 'BackupRecoveryRepairService',
                     'DriveConnectionActivity', 'DrivePrimaryState', 'DriveTransferPool',
                     'ImmutableStateReference', 'LegacyBackupPolicy', 'OwnerReceiptRetention',
                     'ReceiptRetentionRules', 'SyncCommitRules', 'SyncMediaPlan',
                     'SyncWakeup', 'VerifiedSyncCache'):
            self.assertFalse(any((MAIN / (name + ext)).exists() for ext in ('.java', '.kt')), name)
        for name in ('RetiredDriveAccount', 'RetiredDriveBackupUpload',
                     'RetiredBackupDriveClient', 'RetiredBackupScheduleRules'):
            self.assertFalse(any((FIXTURES / (name + ext)).exists() for ext in ('.java', '.kt')), name)
        self.assertNotIn('com.google.crypto.tink', (ROOT/'android/app/build.gradle').read_text())
        self.assertNotIn('recoveryCode', source('FullBackupActivity'))
        self.assertNotIn('recoveryCode', source('BackupRestoreService'))
        self.assertNotIn('DriveConnectionActivity', (ROOT/'android/app/src/main/AndroidManifest.xml').read_text())

    def test_old_jobs_have_no_registered_service_or_scheduling(self):
        manifest = ET.parse(ROOT / 'android/app/src/main/AndroidManifest.xml')
        key = '{http://schemas.android.com/apk/res/android}name'
        services = {element.get(key) for element in manifest.findall('.//service')}
        self.assertNotIn('.DriveSyncJob', services)
        self.assertNotIn('.FullBackupJob', services)
        self.assertIn('cancelRetiredJobs',source('PixelBackupService'))
        self.assertFalse((MAIN / "DriveSyncJob.kt").exists())
        self.assertNotIn("DriveSyncJob", source("RevierApplication"))
        self.assertNotIn("DriveSyncJob", source("MainActivity"))

    def test_old_partial_backup_and_restore_are_removed(self):
        for name in ('LocalOwnerRepository', 'DeletionApplicationService', 'OwnerRepository'):
            self.assertNotRegex(source(name), r'\b(?:createBackup|protectDeletion|restoreBackup)\s*\(')
        self.assertFalse((MAIN / 'DeletionBackupStore.kt').exists())

    def test_plaintext_backup_uses_selected_document_provider(self):
        code = source('PixelBackupService')
        self.assertIn('BACKUP_DOCUMENT_REQUIRED', code)
        selected = code.split('private fun saveSelected(', 1)[1].split('private fun requireSelectedFileAccess(', 1)[0]
        self.assertLess(selected.index('requireDocument(target)'), selected.index('requireSelectedFileAccess(selected)'))
        self.assertLess(selected.index('requireSelectedFileAccess(selected)'), selected.index('return saveToApproved('))
        self.assertNotIn('requirePixel', code)
        self.assertNotIn('BackupRecoveryKeyStore', code)
        picker = source('FullBackupActivity')
        self.assertIn('ACTION_CREATE_DOCUMENT', picker)
        self.assertIn('ACTION_OPEN_DOCUMENT', picker)
        self.assertIn('EXTRA_LOCAL_ONLY', picker)
        provider = (FIXTURES / 'SyntheticBackupDocumentsProvider.kt').read_text()
        self.assertIn('DocumentsContract.Root.FLAG_LOCAL_ONLY', provider)
        self.assertRegex(code, r'\.put\("cloud_confirmed",\s*false\)')

    def test_backup_has_no_migration_or_invoice_authority_gate(self):
        code = source('LocalBackupDataset')
        self.assertNotIn('tableEvidence(', code)
        self.assertNotIn('LocalMigrationInventory', code)
        self.assertNotIn('LocalFinancialValidation', code)
        self.assertNotIn('LocalOriginalVerifier', code)
        self.assertIn('BackupInventory.originalProblem(', code)
        # Provider replacement/readback and source preservation are exercised by the
        # JVM/native backup suites; direct-to-provider streaming is no longer required.
        for name in ('BackupRestoreService',):
            self.assertIn('LocalBackupDataset.inspectStaged(', source(name))


if __name__=='__main__':unittest.main()

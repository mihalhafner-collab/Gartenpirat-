"""Keep retired transports out of the app and current synthetic test sources."""
from pathlib import Path
import re
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


def method_body(code, name):
    """Extract one Java/Kotlin body without counting braces inside text or comments."""
    declarations = list(re.finditer(r'\b(?:fun\s+' + re.escape(name) +
                                    r'|JSONObject\s+' + re.escape(name) + r')\s*\(', code))
    if len(declarations) != 1:
        raise AssertionError('Expected one method declaration: ' + name)
    tokens = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|'
                        r'//[^\n]*|/\*[\s\S]*?\*/|[{}]')
    start = None
    depth = 0
    for token in tokens.finditer(code, declarations[0].end()):
        if token.group() == '{':
            if start is None:
                start = token.end()
            depth += 1
        elif token.group() == '}':
            depth -= 1
            if depth == 0 and start is not None:
                return code[start:token.start()]
    raise AssertionError('Unclosed method body: ' + name)


class DriveBackupOnlyBuildTests(unittest.TestCase):
    def test_business_transports_are_not_production_sources(self):
        for name in ('DriveDirectFiles', 'DriveNumbering', 'DriveMediaBatch'):
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
        for name in ('FullBackupController', 'LocalBackupCoordinator', 'BackupJobRepository', 'BackupPortableFormat', 'BackupRecoveryExport', 'BackupRecoveryRepairService',
                     'DriveConnectionActivity', 'DrivePrimaryState', 'DriveTransferPool',
                     'ImmutableStateReference', 'LegacyBackupPolicy', 'OwnerReceiptRetention',
                     'ReceiptRetentionRules', 'SyncCommitRules', 'SyncMediaPlan',
                     'SyncWakeup', 'VerifiedSyncCache'):
            self.assertFalse(any((MAIN / (name + ext)).exists() for ext in ('.java', '.kt')), name)
        for name in ('RetiredDriveAccount', 'RetiredDriveBackupUpload',
                     'RetiredBackupDriveClient', 'RetiredBackupScheduleRules'):
            self.assertFalse(any((FIXTURES / (name + ext)).exists() for ext in ('.java', '.kt')), name)
        self.assertNotRegex(source('BackupCrypto'), r'\bencrypt\s*\(')
        self.assertNotIn('repairAfterVerifiedArchive(', source('BackupRecoveryKeyStore'))
        self.assertNotIn('DriveConnectionActivity', (ROOT/'android/app/src/main/AndroidManifest.xml').read_text())

    def test_old_jobs_have_no_registered_service_or_scheduling(self):
        manifest = ET.parse(ROOT / 'android/app/src/main/AndroidManifest.xml')
        key = '{http://schemas.android.com/apk/res/android}name'
        services = {element.get(key) for element in manifest.findall('.//service')}
        self.assertNotIn('.DriveSyncJob', services)
        self.assertNotIn('.FullBackupJob', services)
        self.assertIn('cancelRetiredJobs',source('PixelBackupService'))
        code = source('DriveSyncJob')
        self.assertNotIn('JobService', code)
        self.assertNotIn('new JobInfo', code)
        self.assertIn('jobs.cancel(0x484701)', code)
        self.assertIn('jobs.cancel(0x484702)', code)
        self.assertIn('DriveSyncJob.install(this)', source('RevierApplication'))

    def test_retirement_facade_cannot_open_remote_or_local_business_state(self):
        code = source('DriveSyncCoordinator')
        self.assertNotRegex(code, r'\b(?:Files|Doc|DriveRestClient|ContentResolver|DocumentsContract|ExecutorService)\b')
        self.assertNotRegex(code, r'\b(?:commitSnapshot|acknowledge|pendingActionsForDrive|getWritableDatabase)\(')
        for name in ('MainActivity', 'PixelTunnelService', 'PixelOwnerAccess',
                     'PixelLocalStore', 'LocalOwnerRepository', 'PhotoThumbnailProvider'):
            self.assertNotRegex(source(name),
                                r'DriveSyncCoordinator\.(?:fetch\w*|poll|request|noteRequest)\(')

    def test_old_partial_restore_rejects_before_accessing_backup_or_business_data(self):
        code = source('LocalOwnerRepository')
        body = method_body(code, 'restoreBackup')
        self.assertIn('RESTORE_FULL_REPLACEMENT_REQUIRED', body)
        self.assertRegex(body, r'^\s*throw\s+(?:new\s+)?PixelMcpWriteTools\.Rejected\s*\(\s*'
                              r'"RESTORE_FULL_REPLACEMENT_REQUIRED"\s*,\s*"(?:\\.|[^"\\])*"\s*\)\s*;?\s*$')
        self.assertNotRegex(body, r'\b(?:state|effective|fileMeta|fileBytes|enqueue|local)\(')
        self.assertNotIn('LocalAuthorityGuard', body)

    def test_plaintext_backup_uses_selected_document_provider(self):
        code = source('PixelBackupService')
        self.assertIn('BACKUP_DOCUMENT_REQUIRED', code)
        self.assertLess(code.index('requireDocument(target)'), code.index('return saveTo('))
        self.assertNotIn('requirePixel', code)
        self.assertNotIn('BackupRecoveryKeyStore', code)
        picker = source('FullBackupActivity')
        self.assertIn('ACTION_CREATE_DOCUMENT', picker)
        self.assertIn('ACTION_OPEN_DOCUMENT', picker)
        self.assertIn('EXTRA_LOCAL_ONLY', picker)
        self.assertRegex(code, r'\.put\("cloud_confirmed",\s*false\)')

    def test_backup_does_not_require_financial_business_validity(self):
        # The shared capture/staging boundary is used by save, restore and rollback.
        # Explicit business-table transfer remains a separate validated operation.
        code = source('LocalBackupDataset')
        boundary = re.search(r'(?:static\s+BusinessTableStore\.Evidence|fun)\s+tableEvidence\s*\(', code)
        self.assertIsNotNone(boundary, 'Explicit table-transfer boundary must remain separate from backup capture')
        backup, table_transfer = code[:boundary.start()], code[boundary.start():]
        self.assertNotRegex(backup, r'LocalFinancial(?:Snapshot)?Validation\s*\.\s*inspect\(')
        self.assertNotIn('.put("financial_inventory"', backup)
        self.assertNotIn('LocalMigrationInventory', backup)
        self.assertNotIn('BACKUP_LOCAL_ORIGINALS_INCOMPLETE', backup)
        self.assertNotIn('LocalFinancialOriginals.inspect', backup)
        self.assertNotIn('validateRelationships()', backup)
        self.assertNotIn('recordSnapshotPhotoAttachments', backup)
        self.assertIn('LocalFinancialSnapshotValidation.inspect(', table_transfer)
        service = source('PixelBackupService')
        self.assertIn('LocalBackupDataset.forStreaming(', service)
        self.assertIn('BackupDocumentTransfer.exportStream(', service)
        self.assertNotIn('EncryptedBackupArchive.verifyPlain(', service)
        for name in ('BackupRestoreService', 'RestoreStartupProtection'):
            self.assertIn('LocalBackupDataset.inspectStaged(', source(name))
        # New invoice issuance still rejects an already used number before recording it.
        invoice = source('LocalInvoiceService')
        self.assertIn('"INVOICE_NUMBER_COLLISION"', invoice)
        reservation_write = re.search(r'\bstore(?:!!)?\s*\.\s*putOwnerValue\s*\(\s*\(?\s*'
                                      r'"local_number_reservation:', invoice)
        self.assertIsNotNone(reservation_write, 'The reservation must still be persisted after collision checks')
        self.assertLess(invoice.index('"INVOICE_NUMBER_COLLISION"'),
                        reservation_write.start())

    def test_local_authority_does_not_require_external_backup(self):
        migration = source('LocalAuthorityMigration')
        finance = source('LocalAuthorityRules')
        ui = source('LocalAuthorityActivity')
        workflow = source('LocalWorkflowTools')
        self.assertNotIn('externalBackupProof', finance)
        self.assertNotIn('requireBackup(', migration)
        self.assertNotIn('LOCAL_MIGRATION_EXTERNAL_BACKUP_REQUIRED', migration)
        self.assertNotIn('requires_current_external_backup', migration)
        self.assertNotIn('selectedBackup', ui)
        self.assertNotIn('Arrays.asList("expected_preview_hash", "backup_id", "writer_evidence")', workflow)
        self.assertIn('LOCAL_MIGRATION_OPEN_NUMBER_ACTIONS', migration)
        self.assertIn('LOCAL_MIGRATION_DATA_REVIEW_REQUIRED', migration)
        self.assertIn('requireCurrentEvidence(writerProof,', migration)


if __name__=='__main__':unittest.main()

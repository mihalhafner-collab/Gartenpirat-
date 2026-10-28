"""Production local-backup UI boundaries; runtime ZIP/fault tests are JVM/native."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'android/app/src/main/java/at/hausgarten/service'
class BackupSetupUiTests(unittest.TestCase):
    def test_simple_local_save_without_account_or_key_setup(self):
        s=(JAVA/'FullBackupActivity.java').read_text()
        self.assertIn('Intent.ACTION_CREATE_DOCUMENT',s)
        self.assertIn('new PixelBackupService(this,store).save(',s)
        for forbidden in ('DriveAccount','BackupFolderPicker','FullBackupJob','new BackupRecoveryKeyStore','confirmRecovery','automatic','unmetered'):
            self.assertNotIn(forbidden,s)
    def test_only_pixel_storage_is_accepted(self):
        s=(JAVA/'PixelBackupService.java').read_text()
        self.assertIn('com.android.externalstorage.documents',s)
        self.assertIn('startsWith("primary:")',s)
        self.assertIn('requirePixel(target)',s)
        self.assertIn('BACKUP_LOCAL_TARGET_CHANGED',s)
    def test_plain_archive_preserves_readback_and_completed_file(self):
        s=(JAVA/'LocalBackupCoordinator.java').read_text()
        self.assertIn('EncryptedBackupArchive.writePlain(',s)
        self.assertNotIn('uploadToDrive',s)
        self.assertIn('archiveFormat',''.join(s))
        t=(JAVA/'BackupDocumentTransfer.java').read_text()
        self.assertIn('if(!alreadyVerified)',t)
        self.assertIn('BACKUP_LOCAL_READBACK_FAILED',t)
    def test_restore_remains_explicit_and_legacy_codes_not_serialized(self):
        s=(JAVA/'FullBackupActivity.java').read_text()
        self.assertIn('requestActivation(',s)
        self.assertIn('setPositiveButton("Wiederherstellen"',s)
        saved=s.split('protected void onSaveInstanceState(',1)[1]
        self.assertNotIn('getText()',saved)
        restore=(JAVA/'BackupRestoreService.java').read_text()
        self.assertIn('EncryptedBackupArchive.verifyArchive(',restore)
        self.assertIn('BackupDatasetExchange.rollback(',restore)
    def test_new_plain_restore_needs_no_keystore(self):
        s=(JAVA/'BackupRestoreService.java').read_text()
        self.assertIn('if(!record.optBoolean("unencrypted"))BackupRecoveryKeyStore',s)
        self.assertIn('record.optBoolean("unencrypted")?""',s)
        self.assertIn('EncryptedBackupArchive.writePlain(', (JAVA/'RestoreStartupProtection.java').read_text())
    def test_historical_authority_proof_stays_readable_without_relaxing_gate(self):
        control=(JAVA/'FullBackupController.java').read_text()
        self.assertIn('args.optBoolean("legacy_evidence")',control)
        self.assertIn('put("legacy_evidence",true)',(JAVA/'LocalAuthorityActivity.java').read_text())
        self.assertIn('LOCAL_MIGRATION_EXTERNAL_BACKUP_REQUIRED',(JAVA/'LocalAuthorityRules.java').read_text())
        self.assertIn('REMOVABLE_STORAGE_VOLUME',(JAVA/'LocalAuthorityRules.java').read_text())
if __name__=='__main__':unittest.main()


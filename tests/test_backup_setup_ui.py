"""Guard setup cancellation and proof boundaries alongside executable JVM tests."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'android/app/src/main/java/at/hausgarten/service'


class BackupSetupUiTests(unittest.TestCase):
    def test_folder_choice_is_cancelled_on_destroy_and_bound_to_the_chosen_account(self):
        source=(JAVA/'FullBackupActivity.java').read_text()
        picker=(JAVA/'BackupFolderPicker.java').read_text()
        self.assertIn('choice!=setupChoiceSerial',source)
        self.assertIn('!chosen.equals(account.getText().toString().trim())',source)
        self.assertIn('if(folderPicker!=null)folderPicker.close()',source)
        self.assertIn('!closed&&!activity.isFinishing()&&!activity.isDestroyed()&&request==revision',picker)
        self.assertNotIn('BackupSettingsRepository',picker)
        self.assertNotIn('DriveSyncCoordinator',picker)

    def test_copy_proof_and_candidate_secrets_are_never_serialized(self):
        source=(JAVA/'FullBackupActivity.java').read_text()
        save=source.split('protected void onSaveInstanceState(',1)[1].split('private void leave()',1)[0]
        for value in ('candidateCode','verifiedRecoveryCopy','pendingCodeExport','codeSeparate.isChecked()'):
            self.assertNotIn(value,save)
        self.assertIn('BackupRecoveryExport.writeAndVerify(',source)
        self.assertIn('verifiedRecoveryCopy=copied',source)
        self.assertIn('keys.confirmAndStore(keyRequest,candidate,confirmed)',source)
        self.assertIn('boolean separate=codeSeparate.isChecked()',source)

    def test_setup_never_hardcodes_the_private_backup_target(self):
        for path in (JAVA/'FullBackupActivity.java',JAVA/'BackupFolderPicker.java'):
            self.assertNotIn('https://drive.google.com/drive/folders/',path.read_text())
        self.assertNotIn('checkDriveAccess()', (JAVA/'FullBackupActivity.java').read_text())

    def test_new_setup_allows_mobile_data_without_opt_in_to_automatic_runs(self):
        source=(JAVA/'FullBackupActivity.java').read_text()
        settings=(JAVA/'BackupSettingsRepository.java').read_text()
        self.assertIn('automatic.setChecked(false)',source)
        self.assertIn('unmetered.setChecked(false)',source)
        self.assertIn('put("unmeteredOnly",false)',settings)
        self.assertNotIn('unmeteredOnly",true',source)
        self.assertNotIn('unmeteredOnly",true',settings)
        self.assertIn('config.optBoolean("unmeteredOnly",false)',source)

    def test_explicit_user_request_executes_in_foreground_and_retains_identity(self):
        source=(JAVA/'FullBackupActivity.java').read_text()
        manual=source.split('private void requestBackup(){',1)[1].split('private void prepareRecoveryExport()',1)[0]
        self.assertIn('BackupScheduleRules.dispatchManual(',manual)
        self.assertIn('new BackupAutomation(this,store).runManual(id,',manual)
        self.assertNotIn('FullBackupJob.schedule',manual)
        self.assertIn('put("request_id",request).put("expected_settings_hash",settings)',manual)
        self.assertNotIn('if(!backupRequest.isEmpty()){refresh(false);return;}',source)


if __name__=='__main__':
    unittest.main()

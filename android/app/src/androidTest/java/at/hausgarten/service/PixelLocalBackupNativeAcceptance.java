package at.hausgarten.service;
import org.json.*;
import java.io.*;
import java.util.zip.ZipFile;
final class PixelLocalBackupNativeAcceptance {
    static void run(PixelMcpInstrumentation test)throws Exception {
        test.probe("local plaintext backup captures full synthetic DB without key or account and previews without activation",()->{try(OwnerNativeAcceptance.Fixture f=new OwnerNativeAcceptance.Fixture(test.getTargetContext())){
            BackupSettingsRepository checkpoint=new BackupSettingsRepository(f.context,f.store);checkpoint.saveBrowser("qa_plain_checkpoint",checkpoint.browserRevision(),new JSONObject().put("formatVersion",1).put("storage",new JSONObject()));
            String before=PixelMcpWriteTools.hash(BusinessStateFingerprint.read(f.store.getWritableDatabase()));test.yes(!new BackupRecoveryKeyStore(f.context).configured());
            LocalBackupCoordinator service=new LocalBackupCoordinator(f.context,f.store);JSONObject browser=checkpoint.browser().getJSONObject("browser");service.createWithIntent("qa_plain_request","qa_plain_generation",new JSONObject().put("kind","saf").put("treeUri","content://com.android.externalstorage.documents/document/primary%3Aqa_backup.zip").put("purpose","pixel_plain_backup"),browser,"manual");
            JSONObject job=service.prepareLocal("qa_plain_generation");File file=service.verifiedArchive("qa_plain_generation");test.yes(job.getJSONObject("archive").getString("archiveFormat").equals("PLAIN_ZIP"));
            try(ZipFile zip=new ZipFile(file)){test.yes(zip.getEntry("data/database.sqlite")!=null&&zip.getEntry("manifest.json")!=null);}
            JSONObject preview=new BackupRestoreService(f.context).previewFrom("qa_plain_preview","qa_local_zip","",()->new FileInputStream(file));test.yes(preview.getString("phase").equals("VALIDATED"));
            test.yes(BackupRestoreService.pendingStatus(f.context).getString("phase").equals("NONE")&&!new BackupRecoveryKeyStore(f.context).configured());test.yes(before.equals(PixelMcpWriteTools.hash(BusinessStateFingerprint.read(f.store.getWritableDatabase()))));
            test.yes(service.prepareLocal("qa_plain_generation").getJSONObject("archive").getString("sha256").equals(job.getJSONObject("archive").getString("sha256")));
        }});
    }
}

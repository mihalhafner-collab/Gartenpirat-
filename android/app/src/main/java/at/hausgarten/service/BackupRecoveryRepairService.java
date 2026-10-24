package at.hausgarten.service;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Explicit device-only repair after independent recovery-code verification; never rotates a healthy key. */
final class BackupRecoveryRepairService {
    private final Context context;private final PixelLocalStore store;
    BackupRecoveryRepairService(Context context,PixelLocalStore store){this.context=context;this.store=store;}
    JSONObject repair(String requestId,String backupId,String candidate,String confirmation)throws Exception {
        if(requestId==null||!requestId.matches("[A-Za-z0-9_-]{8,200}"))throw new IOException("RECOVERY_REPAIR_REQUEST_ID_REQUIRED");if(store.getWritableDatabase().inTransaction())throw new IOException("RECOVERY_REPAIR_REQUIRES_OUTSIDE_TRANSACTION");
        byte[] expected=BackupCrypto.recoveryKey(candidate),checked=null;
        try{checked=BackupCrypto.recoveryKey(confirmation);if(!MessageDigest.isEqual(expected,checked))throw new java.security.GeneralSecurityException("RECOVERY_CONFIRMATION_MISMATCH");}finally{Arrays.fill(expected,(byte)0);if(checked!=null)Arrays.fill(checked,(byte)0);}
        LocalBackupCoordinator service=new LocalBackupCoordinator(context,store);File archive=service.verifiedArchive(backupId);JSONObject metadata=service.metadata(backupId),job=service.status(backupId);
        File root=new File(context.getNoBackupFilesDir(),"backup-recovery-repair");if(java.nio.file.Files.isSymbolicLink(root.toPath())||!root.getCanonicalFile().getParentFile().equals(context.getNoBackupFilesDir().getCanonicalFile()))throw new IOException("RECOVERY_REPAIR_STAGING_UNAVAILABLE");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("RECOVERY_REPAIR_STAGING_UNAVAILABLE");JSONObject proof;
        try(EncryptedBackupArchive.Verified verified=EncryptedBackupArchive.verifyAndStage(new FileInputStream(archive),candidate,new File(root,"verify-"+UUID.randomUUID()),Math.max(0,root.getUsableSpace()-BackupStorageBudget.BUSINESS_RESERVE_BYTES),PixelLocalStore.SCHEMA_VERSION)){
            if(!verified.manifest.getString("backupId").equals(backupId)||!verified.manifest.getString("businessId").equals(metadata.getString("businessId"))||!verified.manifest.getJSONObject("database").getString("sha256").equals(metadata.getJSONObject("database").getString("sha256")))throw new IOException("RECOVERY_REPAIR_BUSINESS_BINDING_MISMATCH");LocalBackupDataset.inspectStaged(new File(verified.directory,"data"),verified.manifest);
            proof=new JSONObject().put("formatVersion",1).put("archive_verified",true).put("backup_id",backupId).put("archive_sha256",job.getJSONObject("archive").getString("sha256")).put("business_id",metadata.getString("businessId")).put("database_sha256",verified.manifest.getJSONObject("database").getString("sha256"));
        }
        JSONObject result=new BackupRecoveryKeyStore(context).repairAfterVerifiedArchive(requestId,candidate,confirmation,proof);
        return new JSONObject().put("repaired",true).put("backup_id",backupId).put("archive_sha256",proof.getString("archive_sha256")).put("replayed",result.optBoolean("replayed")).put("old_capsule_preserved",true).put("old_alias_deleted",false);
    }
}

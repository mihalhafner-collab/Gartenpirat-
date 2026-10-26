package at.hausgarten.service;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Device-only writer/number fence. Never restored from a business archive. */
final class LocalAuthorityGuard {
    private static final Object LOCK=new Object();
    private static final ThreadLocal<Boolean> EXCLUSIVE=ThreadLocal.withInitial(()->false);
    private final Context context;private final AtomicFile file;
    LocalAuthorityGuard(Context context){this.context=context;file=new AtomicFile(new File(context.getNoBackupFilesDir(),"local-authority/authority.v1"));}
    JSONObject current()throws Exception {synchronized(LOCK){
        if(!file.getBaseFile().exists()&&!new File(file.getBaseFile()+".bak").exists())return new JSONObject().put("formatVersion",1).put("mode","LEGACY_DRIVE").put("invoice_authority","LEGACY_ONLINE").put("revision",PixelMcpWriteTools.hash("legacy-authority-v1"));
        JSONObject value;try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1){if(out.size()+n>256*1024)throw new IOException("LOCAL_AUTHORITY_RECORD_INVALID");out.write(block,0,n);}value=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}
        if(!"1".equals(String.valueOf(value.opt("formatVersion")))||!Arrays.asList("LEGACY_DRIVE","MIGRATING","LOCAL_ACTIVE","RESTORE_REVIEW").contains(value.getString("mode")))throw new IOException("LOCAL_AUTHORITY_CONTRACT_UNSUPPORTED");
        return value.put("revision",PixelMcpWriteTools.hash(value));
    }}
    static boolean legacySyncAllowed(Context context){try{return "LEGACY_DRIVE".equals(new LocalAuthorityGuard(context).current().getString("mode"))&&!restorePending(context);}catch(Exception invalid){return false;}}
    static boolean local(Context context){try{return !"LEGACY_DRIVE".equals(new LocalAuthorityGuard(context).current().getString("mode"));}catch(Exception invalid){return true;}}
    static void assertStoreMayOpen(Context context){
        if(EXCLUSIVE.get())return;
        if(restorePending(context))throw new IllegalStateException("RESTORE_RESTART_REQUIRED");
        try{if("MIGRATING".equals(new LocalAuthorityGuard(context).current().getString("mode")))throw new IllegalStateException("LOCAL_MIGRATION_REVIEW_REQUIRED");}catch(IllegalStateException e){throw e;}catch(Exception invalid){throw new IllegalStateException("LOCAL_AUTHORITY_RECORD_INVALID",invalid);}
    }
    static boolean restorePending(Context context){return new File(context.getNoBackupFilesDir(),"local-authority/restore-pending.v1").isFile()||BackupRestoreService.requiresExclusiveStartup(context)||RestoreStartupProtection.requiresColdRestart(context);}
    static AutoCloseable exclusiveStartup(){if(EXCLUSIVE.get())throw new IllegalStateException("RESTORE_STARTUP_REENTRY");EXCLUSIVE.set(true);return ()->EXCLUSIVE.remove();}
    static void markRestorePending(Context context,String request)throws Exception {
        if(request==null||!request.matches("[A-Za-z0-9_-]{8,200}"))throw new IOException("RESTORE_REQUEST_ID_REQUIRED");
        write(new AtomicFile(new File(context.getNoBackupFilesDir(),"local-authority/restore-pending.v1")),new JSONObject().put("request_id",request));
    }
    static void clearRestorePending(Context context){new AtomicFile(new File(context.getNoBackupFilesDir(),"local-authority/restore-pending.v1")).delete();}
    JSONObject beginMigration(String request,String expected,JSONObject evidence)throws Exception {synchronized(LOCK){
        JSONObject current=current();if(request.equals(current.optString("migration_request"))){if(!PixelMcpWriteTools.hash(evidence).equals(current.optString("migration_evidence_sha256")))throw new IOException("LOCAL_MIGRATION_REPLAY_MISMATCH");return current;}
        if(!current.getString("revision").equals(expected)||!Arrays.asList("LEGACY_DRIVE","RESTORE_REVIEW").contains(current.getString("mode")))throw new IOException("LOCAL_MIGRATION_WRITE_CONFLICT");
        LocalAuthorityRules.requireEvidence(evidence);JSONObject identity=new BackupDeviceGuard(context).current();JSONObject next=new JSONObject().put("formatVersion",1).put("mode","MIGRATING").put("invoice_authority","LOCKED").put("migration_request",request).put("migration_evidence_sha256",PixelMcpWriteTools.hash(evidence)).put("migration_epoch",UUID.randomUUID().toString()).put("installation_id",identity.getString("installationId")).put("restore_epoch",identity.getString("restoreEpoch")).put("created_at",java.time.Instant.now().toString()).put("invoice_high_water",current.optInt("invoice_high_water",0)).put("writer_checked_at",evidence.getString("checked_at")).put("evidence_not_before",current.optString("evidence_not_before",current.optString("blocked_at","")));save(next);return current();
    }}
    JSONObject finishMigration(String request,JSONObject contract)throws Exception {synchronized(LOCK){
        JSONObject value=current(),identity=new BackupDeviceGuard(context).current();if(!request.equals(value.optString("migration_request"))||!value.getString("migration_epoch").equals(contract.getString("migration_epoch"))||!identity.getString("installationId").equals(value.optString("installation_id"))||!identity.getString("restoreEpoch").equals(value.optString("restore_epoch")))throw new IOException("LOCAL_MIGRATION_RECEIPT_MISMATCH");
        if(value.optString("mode").equals("LOCAL_ACTIVE"))return value;
        if(!value.optString("mode").equals("MIGRATING")&&!(value.optString("mode").equals("RESTORE_REVIEW")&&value.optString("review_reason").equals("MIGRATION_INTERRUPTED")))throw new IOException("LOCAL_MIGRATION_REVIEW_REQUIRED");
        value.remove("revision");value.put("mode","LOCAL_ACTIVE").put("invoice_authority","LOCKED").put("contract_sha256",PixelMcpWriteTools.hash(contract));save(value);return current();
    }}
    void blockAfterRestore(String request,JSONObject manifest)throws Exception {synchronized(LOCK){
        JSONObject value=current();value.remove("revision");value.put("formatVersion",1).put("mode","RESTORE_REVIEW").put("invoice_authority","LOCKED").put("restore_request",request).put("restored_backup_id",manifest.getString("backupId")).put("blocked_at",java.time.Instant.now().toString());value.put("evidence_not_before",value.getString("blocked_at"));
        JSONObject identity=new BackupDeviceGuard(context).current();if(!request.equals(identity.optString("lastRestoreRequest")))identity=new BackupDeviceGuard(context).advanceForRestore(request,identity.getString("restoreEpoch"));
        value.put("installation_id",identity.getString("installationId")).put("restore_epoch",identity.getString("restoreEpoch"));save(value);
    }}
    void requireInvoiceAuthority()throws Exception {
        JSONObject value=current(),identity=new BackupDeviceGuard(context).current();
        if(!value.optString("mode").equals("LOCAL_ACTIVE")||!value.optString("invoice_authority").equals("RECONCILED")||!identity.getString("installationId").equals(value.optString("installation_id"))||!identity.getString("restoreEpoch").equals(value.optString("restore_epoch"))||restorePending(context))throw new IOException("INVOICE_AUTHORITY_RECONCILIATION_REQUIRED");
    }
    static void verifyDatabaseContract(Context context,android.database.sqlite.SQLiteDatabase database)throws Exception {
        String contract="";try(android.database.Cursor row=database.rawQuery("SELECT value FROM owner_state WHERE key='local_authority_contract'",null)){if(row.moveToFirst())contract=row.getString(0);}
        LocalAuthorityGuard guard=new LocalAuthorityGuard(context);JSONObject state=guard.current();
        if(!contract.isEmpty()){
            JSONObject value=new JSONObject(contract);
            if(!"1".equals(String.valueOf(value.opt("formatVersion"))))throw new IOException("LOCAL_DATABASE_CONTRACT_UNSUPPORTED");
            if(!value.optString("migration_epoch").equals(state.optString("migration_epoch"))||state.optString("mode").equals("LEGACY_DRIVE"))guard.blockUnverifiedDatabase("database-contract-"+PixelMcpWriteTools.hash(contract).substring(0,32));
        }else if(state.optString("mode").equals("LOCAL_ACTIVE"))guard.blockUnverifiedDatabase("database-contract-missing");
    }
    void blockUnverifiedDatabase(String request)throws Exception {synchronized(LOCK){
        JSONObject value=current();if("RESTORE_REVIEW".equals(value.optString("mode"))&&request.equals(value.optString("database_review_request")))return;value.remove("revision");if(!"RESTORE_REVIEW".equals(value.optString("mode"))){JSONObject identity=new BackupDeviceGuard(context).current();identity=new BackupDeviceGuard(context).advanceForRestore(request,identity.getString("restoreEpoch"));value.put("restore_epoch",identity.getString("restoreEpoch")).put("installation_id",identity.getString("installationId"));}
        value.put("mode","RESTORE_REVIEW").put("invoice_authority","LOCKED").put("review_reason","DATABASE_CONTRACT_MISMATCH").put("database_review_request",request).put("blocked_at",java.time.Instant.now().toString());value.put("evidence_not_before",value.getString("blocked_at"));save(value);
    }}
    void recoverInterruptedMigration()throws Exception {synchronized(LOCK){JSONObject value=current();if(value.optString("mode").equals("MIGRATING")){value.remove("revision");value.put("mode","RESTORE_REVIEW").put("invoice_authority","LOCKED").put("review_reason","MIGRATION_INTERRUPTED");save(value);}}}
    void reconcileInvoices(String request,JSONObject proof,int lastNumber)throws Exception {synchronized(LOCK){
        LocalAuthorityRules.requireEvidence(proof);if(lastNumber<0)throw new IOException("INVOICE_NUMBER_STATE_INVALID");JSONObject value=current();if(!Arrays.asList("LOCAL_ACTIVE","RESTORE_REVIEW").contains(value.optString("mode")))throw new IOException("LOCAL_AUTHORITY_REQUIRED");value.remove("revision");JSONObject identity=new BackupDeviceGuard(context).current();
        value.put("mode","LOCAL_ACTIVE").put("invoice_authority","RECONCILED").put("invoice_evidence_sha256",PixelMcpWriteTools.hash(proof)).put("invoice_reconciliation_request",request).put("invoice_high_water",Math.max(lastNumber,value.optInt("invoice_high_water"))).put("installation_id",identity.getString("installationId")).put("restore_epoch",identity.getString("restoreEpoch"));save(value);
    }}
    void advanceInvoiceHighWater(int number)throws Exception {synchronized(LOCK){requireInvoiceAuthority();JSONObject value=current();value.remove("revision");if(number<value.optInt("invoice_high_water"))throw new IOException("INVOICE_NUMBER_ROLLBACK_DETECTED");value.put("invoice_high_water",number);save(value);}}
    private void save(JSONObject value)throws Exception {value.remove("revision");write(file,value);}
    private static void write(AtomicFile file,JSONObject value)throws Exception {File directory=file.getBaseFile().getParentFile();if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("LOCAL_AUTHORITY_STORAGE_UNAVAILABLE");FileOutputStream out=null;try{out=file.startWrite();out.write(value.toString().getBytes(StandardCharsets.UTF_8));file.finishWrite(out);out=null;}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}}
}

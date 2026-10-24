package at.hausgarten.service;

import org.json.*;
import java.time.Instant;

/** Independent proof tampering vectors, including same-device provider caches and stale evidence. */
public final class LocalAuthorityRulesTests {
    private static int checks;private static void yes(boolean value){checks++;if(!value)throw new AssertionError("authority proof "+checks);}
    private interface Task {void run()throws Exception;}
    private static void reject(Task task)throws Exception {try{task.run();throw new AssertionError("unsafe proof accepted");}catch(IllegalArgumentException|JSONException expected){checks++;}}
    public static void main(String[] ignored)throws Exception {
        Instant now=Instant.parse("2026-10-24T08:00:00Z");String sha=PixelMcpWriteTools.hash("qa_archive"),business=PixelMcpWriteTools.hash("qa_business");JSONObject manifest=new JSONObject().put("backupId","qa_backup_proof").put("capturedAt","2026-10-24T06:00:00Z").put("business_snapshot_sha256",business);
        JSONObject provider=new JSONObject().put("formatVersion",1).put("external_storage_verified",true).put("destination_location","REMOVABLE_STORAGE_VOLUME").put("verification_scope","DOCUMENT_PROVIDER_READBACK").put("backup_id","qa_backup_proof").put("archive_sha256",sha).put("byte_size",50000000).put("verified_at","2026-10-24T07:00:00Z");JSONObject job=new JSONObject().put("backupId","qa_backup_proof").put("phase","PROVIDER_VERIFIED").put("destination",new JSONObject().put("kind","saf")).put("archive",new JSONObject().put("sha256",sha).put("size",50000000).put("localVerifiedAt","2026-10-24T06:30:00Z")).put("provider",provider);
        yes(LocalAuthorityRules.externalBackupProof(job,manifest,now).getString("verification_scope").equals("removable_storage_full_readback"));
        for(String field:new String[]{"external_storage_verified","destination_location","verification_scope","backup_id","archive_sha256","byte_size","verified_at","formatVersion"}){
            JSONObject changed=new JSONObject(job.toString());Object wrong=field.equals("external_storage_verified")?false:field.equals("byte_size")?50000001:field.equals("formatVersion")?1.5:field.equals("verified_at")?"2026-10-23T00:00:00Z":"qa_unproved";changed.getJSONObject("provider").put(field,wrong);reject(()->LocalAuthorityRules.externalBackupProof(changed,manifest,now));
        }
        JSONObject future=new JSONObject(job.toString());future.getJSONObject("provider").put("verified_at","2026-10-25T00:00:00Z");reject(()->LocalAuthorityRules.externalBackupProof(future,manifest,now));JSONObject deviceCache=new JSONObject(job.toString());deviceCache.getJSONObject("provider").put("destination_location","DOCUMENT_PROVIDER_EXTERNALITY_UNCONFIRMED");reject(()->LocalAuthorityRules.externalBackupProof(deviceCache,manifest,now));
        JSONObject remote=new JSONObject(job.toString());remote.remove("provider");remote.put("phase","REMOTE_VERIFIED").put("destination",new JSONObject().put("kind","drive").put("folderId","qa_drive_folder"));String binding=PixelMcpWriteTools.hash(new JSONObject().put("backupId","qa_backup_proof").put("folder","qa_drive_folder").put("size",50000000).put("sha256",sha));remote.put("upload",new JSONObject().put("state","REMOTE_VERIFIED").put("binding",binding).put("remoteId","qa_remote_original").put("remoteSha256",sha).put("uploadedBytes",50000000).put("remoteVerifiedAt","2026-10-24T07:00:00Z"));yes(LocalAuthorityRules.externalBackupProof(remote,manifest,now).getString("verification_scope").equals("drive_server_full_hash"));
        for(String field:new String[]{"state","binding","remoteId","remoteSha256","uploadedBytes","remoteVerifiedAt"}){JSONObject changed=new JSONObject(remote.toString());changed.getJSONObject("upload").put(field,field.equals("uploadedBytes")?1:field.equals("remoteVerifiedAt")?"2026-10-24T06:01:00Z":field.equals("remoteId")?"https://qa.invalid":"qa_changed");reject(()->LocalAuthorityRules.externalBackupProof(changed,manifest,now));}
        JSONObject proof=new JSONObject().put("formatVersion",1.5).put("external_writers_disabled",true).put("evidence_sha256",sha).put("evidence_reference","qa_proof").put("checked_at",now.toString());reject(()->LocalAuthorityRules.requireEvidence(proof));
        yes(LocalAuthorityRules.minor("148.00")==14800);JSONObject balance=LocalAuthorityRules.paymentBalance(14800,new JSONArray().put(new JSONObject().put("payment_id","qa_first").put("allocated_minor",6000)));yes(balance.getLong("remaining_minor")==8800);yes(LocalAuthorityRules.paymentBalance(14800,new JSONArray().put(new JSONObject().put("payment_id","qa_total").put("allocated_minor",15000))).getLong("credit_minor")==200);
        System.out.println("PASS "+checks+" local authority external-proof and exact-money checks");
    }
}

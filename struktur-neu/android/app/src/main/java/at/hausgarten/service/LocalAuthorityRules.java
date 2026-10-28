package at.hausgarten.service;

import org.json.*;
import java.math.*;
import java.util.*;

/** Versioned local-writer and exact-decimal rules; no network, Android or inferred evidence. */
final class LocalAuthorityRules {
    static final String ENGINE="pixel_local_v2";
    static final int CONTRACT=1;
    static long minor(Object value)throws Exception {
        if(value==null||value==JSONObject.NULL)throw new IllegalArgumentException("MONEY_REQUIRED");
        return new BigDecimal(value.toString()).setScale(2,RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }
    static String decimal(long value){return BigDecimal.valueOf(value,2).toPlainString();}
    static JSONObject exactTotals(JSONObject invoice)throws Exception {
        long net=0,tax=0;JSONArray positions=new JSONArray(invoice.getJSONArray("positions").toString());
        for(int i=0;i<positions.length();i++){
            JSONObject line=positions.getJSONObject(i);BigDecimal quantity=new BigDecimal(line.get("quantity").toString()),price=new BigDecimal(line.get("price").toString()),discount=new BigDecimal(line.optString("discount","0")),rate=new BigDecimal(line.optString("tax_rate","0"));
            if(quantity.signum()<0||price.signum()<0||discount.signum()<0||discount.compareTo(new BigDecimal("100"))>0||rate.signum()<0)throw new IllegalArgumentException("INVALID_DECIMAL_POSITION");
            BigDecimal calculated=quantity.multiply(price).multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2))).setScale(2,RoundingMode.HALF_UP);
            long n=minor(calculated),t=minor(calculated.multiply(rate).movePointLeft(2).setScale(2,RoundingMode.HALF_UP));
            if("cancellation".equals(invoice.optString("document_type"))){n=Math.negateExact(n);t=Math.negateExact(t);}
            line.put("quantity_decimal",quantity.toPlainString()).put("unit_price_minor_decimal",price.movePointRight(2).toPlainString()).put("net_minor",n).put("tax_minor",t);net=Math.addExact(net,n);tax=Math.addExact(tax,t);
        }
        if(invoice.has("net_total")&&minor(invoice.get("net_total"))!=net||invoice.has("tax_total")&&minor(invoice.get("tax_total"))!=tax||invoice.has("gross_total")&&minor(invoice.get("gross_total"))!=Math.addExact(net,tax))throw new IllegalArgumentException("INVOICE_TOTAL_MISMATCH");
        return new JSONObject().put("formatVersion",1).put("currency","EUR").put("positions",positions).put("net_minor",net).put("tax_minor",tax).put("gross_minor",Math.addExact(net,tax));
    }
    static JSONObject paymentBalance(long invoiceMinor,JSONArray payments)throws Exception {
        long allocated=0;Set<String> ids=new HashSet<>();
        for(int i=0;i<payments.length();i++){JSONObject p=payments.getJSONObject(i);if(!ids.add(p.getString("payment_id")))throw new IllegalArgumentException("PAYMENT_DUPLICATE_ID");allocated=Math.addExact(allocated,p.getLong("allocated_minor"));}
        long difference=Math.subtractExact(invoiceMinor,allocated);return new JSONObject().put("invoice_minor",invoiceMinor).put("allocated_minor",allocated).put("remaining_minor",Math.max(0,difference)).put("credit_minor",Math.max(0,Math.negateExact(difference))).put("paid",difference<=0);
    }
    static void requireEvidence(JSONObject proof)throws Exception {
        if(!"1".equals(String.valueOf(proof.opt("formatVersion")))||!proof.optBoolean("external_writers_disabled")||!proof.optString("evidence_sha256").matches("[0-9a-f]{64}")||proof.optString("evidence_reference").trim().isEmpty())throw new IllegalArgumentException("LOCAL_AUTHORITY_EVIDENCE_REQUIRED");
        java.time.Instant.parse(proof.getString("checked_at"));
    }
    static JSONObject externalBackupProof(JSONObject job,JSONObject manifest,java.time.Instant now)throws Exception {
        JSONObject archive=job.getJSONObject("archive"),destination=job.getJSONObject("destination");String id=job.getString("backupId"),sha=archive.getString("sha256"),verified="",scope="";long size=new BigDecimal(archive.get("size").toString()).longValueExact();
        if(!sha.matches("[0-9a-f]{64}")||size<=0||!id.equals(manifest.getString("backupId")))throw new IllegalArgumentException("LOCAL_MIGRATION_EXTERNAL_BACKUP_REQUIRED");
        JSONObject media=manifest.optJSONObject("browser_media_inventory"),capture=job.optJSONObject("capture");
        if(!"1".equals(String.valueOf(manifest.opt("browser_media_format_version")))||media==null||!"1".equals(String.valueOf(media.opt("formatVersion")))||!Boolean.TRUE.equals(media.opt("mirror_complete"))||!media.optString("record_hash").matches("[0-9a-f]{64}")||!"VERIFIED_NATIVE_MIRROR".equals(archive.optString("mediaCoverage"))||!"1".equals(String.valueOf(archive.opt("mediaFormatVersion")))||!PixelMcpWriteTools.hash(media).equals(archive.optString("mediaInventorySha256"))||capture==null||!capture.optString("metadataSha256").matches("[0-9a-f]{64}")||!capture.getString("metadataSha256").equals(archive.optString("captureMetadataSha256")))throw new IllegalArgumentException("LOCAL_MIGRATION_EXTERNAL_BACKUP_REQUIRED");
        JSONObject upload=job.optJSONObject("upload"),provider=job.optJSONObject("provider");
        if(upload!=null&&job.optString("phase").equals("REMOTE_VERIFIED")&&upload.optString("state").equals("REMOTE_VERIFIED")&&destination.optString("kind").equals("drive")){
            String binding=PixelMcpWriteTools.hash(new JSONObject().put("backupId",id).put("folder",destination.getString("folderId")).put("size",size).put("sha256",sha));
            if(binding.equals(upload.optString("binding"))&&HttpTransport.validId(upload.optString("remoteId"))&&sha.equals(upload.optString("remoteSha256"))&&size==new BigDecimal(upload.get("uploadedBytes").toString()).longValueExact()){verified=upload.getString("remoteVerifiedAt");scope="drive_server_full_hash";}
        }
        if(verified.isEmpty()&&provider!=null&&"1".equals(String.valueOf(provider.opt("formatVersion")))&&provider.optBoolean("external_storage_verified")&&provider.optString("destination_location").equals("REMOVABLE_STORAGE_VOLUME")&&provider.optString("verification_scope").equals("DOCUMENT_PROVIDER_READBACK")&&id.equals(provider.optString("backup_id"))&&sha.equals(provider.optString("archive_sha256"))&&size==new BigDecimal(provider.get("byte_size").toString()).longValueExact()){
            verified=provider.getString("verified_at");scope="removable_storage_full_readback";
        }
        if(verified.isEmpty())throw new IllegalArgumentException("LOCAL_MIGRATION_EXTERNAL_BACKUP_REQUIRED");java.time.Instant checked=java.time.Instant.parse(verified),captured=java.time.Instant.parse(manifest.getString("capturedAt")),local=java.time.Instant.parse(archive.getString("localVerifiedAt"));
        if(checked.isAfter(now)||checked.isBefore(captured)||checked.isBefore(local))throw new IllegalArgumentException("LOCAL_MIGRATION_EXTERNAL_BACKUP_REQUIRED");
        return new JSONObject().put("formatVersion",1).put("verified",true).put("external_verified",true).put("backup_id",id).put("archive_sha256",sha).put("business_snapshot_sha256",manifest.getString("business_snapshot_sha256")).put("captured_at",captured.toString()).put("verified_at",checked.toString()).put("verification_scope",scope).put("media_coverage","VERIFIED_NATIVE_MIRROR").put("browser_media_format_version",1).put("browser_media_inventory_sha256",archive.getString("mediaInventorySha256"));
    }
    private LocalAuthorityRules(){}
}

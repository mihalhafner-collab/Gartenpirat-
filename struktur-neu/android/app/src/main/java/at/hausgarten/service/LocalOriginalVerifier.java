package at.hausgarten.service;

import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Shared read-only original checks for live diagnostics and isolated backup snapshots. */
final class LocalOriginalVerifier implements LocalMigrationInventory.Originals {
    interface PhotoMetadata {JSONObject get(String id)throws Exception;}
    interface DocumentJobs {JSONObject get(String invoiceId)throws Exception;}
    private final File files;
    private final PhotoMetadata photos;
    private final DocumentJobs documents;
    LocalOriginalVerifier(File files,PhotoMetadata photos){this(files,photos,null);}
    LocalOriginalVerifier(File files,PhotoMetadata photos,DocumentJobs documents){this.files=files;this.photos=photos;this.documents=documents;}
    String problem(String kind,JSONObject reference)throws Exception {
        if(verified(kind,reference)||kind.equals("document")&&pendingDocument(reference))return "";
        JSONObject metadata=kind.equals("photo")?photos.get(reference.optString("id")):reference;if(metadata==null)metadata=reference;
        String name=metadata.optString(kind.equals("photo")?"storage_name":"pdf_storage_name");
        String sha=metadata.optString(kind.equals("photo")?"content_sha256":"pdf_sha256");
        if(name.isEmpty()||!sha.matches("[0-9a-f]{64}"))return "ORIGINAL_METADATA_INCOMPLETE";
        File file=new File(new File(files,kind.equals("photo")?"customer-photo-originals":"business-documents"),name);
        if(!file.isFile())return "ORIGINAL_FILE_MISSING";
        return "ORIGINAL_NOT_VERIFIABLE";
    }
    @Override public boolean verified(String kind,JSONObject reference)throws Exception {
        JSONObject metadata=reference;File file;
        if(kind.equals("photo")){
            JSONObject local=photos.get(reference.optString("id"));if(local!=null)metadata=local;
            if(!PhotoAttachmentStore.canonicalName(metadata.optString("storage_name")))return false;
            file=new File(new File(files,"customer-photo-originals"),metadata.getString("storage_name"));
        }else{
            if(reference.optString("pdf_state").equals("PDF_PENDING"))return false;
            if(!metadata.optString("pdf_storage_name").matches("[0-9a-f]{64}\\.pdf"))return false;
            file=new File(new File(files,"business-documents"),metadata.getString("pdf_storage_name"));
        }
        String expected=metadata.optString(kind.equals("photo")?"content_sha256":"pdf_sha256");long size=metadata.optLong(kind.equals("photo")?"byte_size":"pdf_byte_size",-1);
        if(!expected.matches("[0-9a-f]{64}")||!file.isFile()||java.nio.file.Files.isSymbolicLink(file.toPath())||file.length()!=size)return false;
        if(!kind.equals("photo"))try(InputStream header=new FileInputStream(file)){byte[] prefix=new byte[5];if(header.read(prefix)!=5||!new String(prefix,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))return false;}
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long read=0;
        try(InputStream input=new BufferedInputStream(new FileInputStream(file))){byte[] buffer=new byte[32768];int n;while((n=input.read(buffer))!=-1){digest.update(buffer,0,n);read+=n;}}
        StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",value&255));if(read!=size||!expected.equals(hash.toString()))return false;
        if(!kind.equals("photo")&&documents!=null){JSONObject job=documents.get(reference.optString("id"));if(job!=null&&job.has("original_adoption")&&!adoptionVerified(reference,job))return false;}return true;
    }
    private boolean adoptionVerified(JSONObject invoice,JSONObject job)throws Exception {
        String id=invoice.optString("id"),sha=job.optString("snapshot_sha256");JSONObject frozen=job.optJSONObject("snapshot"),adoption=job.optJSONObject("original_adoption"),document=job.optJSONObject("document");
        if(job.optInt("formatVersion")!=1||!job.optString("phase").equals("READY")||!id.equals(job.optString("invoice_id"))||!sha.matches("[0-9a-f]{64}")||frozen==null||!sha.equals(PixelMcpWriteTools.hash(frozen))||!LocalFinancialValidation.sameFrozenInvoice(invoice,frozen)||adoption==null||adoption.optInt("formatVersion")!=1||!sha.equals(adoption.optString("snapshot_sha256"))||!adoption.optString("ledger_proof_sha256").matches("[0-9a-f]{64}")||adoption.optString("request_id").isEmpty()||document==null||!sameDocument(document,invoice))return false;
        java.time.Instant.parse(adoption.getString("adopted_at"));File directory=new File(files,"invoice-document-receipts"),base=new File(directory,sha+".json"),backup=new File(base+".bak");
        if(java.nio.file.Files.isSymbolicLink(directory.toPath()))return false;for(File candidate:new File[]{base,backup,new File(base+".new")})if(java.nio.file.Files.isSymbolicLink(candidate.toPath()))return false;File source=backup.exists()?backup:base;if(!source.isFile()||source.length()>65536)return false;JSONObject receipt;try(InputStream input=new FileInputStream(source);ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] block=new byte[4096];int n;while((n=input.read(block))!=-1){if(n>65536-output.size())return false;output.write(block,0,n);}receipt=new JSONObject(new String(output.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));}
        if(receipt.optInt("formatVersion")!=1||!id.equals(receipt.optString("invoice_id"))||!invoice.optString("invoice_number").equals(receipt.optString("invoice_number"))||!sha.equals(receipt.optString("snapshot_sha256"))||receipt.optJSONObject("document")==null||!sameDocument(document,receipt.getJSONObject("document")))return false;
        return LocalFinancialOriginals.receiptEvidenceMatches(adoption,receipt);
    }
    private static boolean sameDocument(JSONObject first,JSONObject second)throws Exception {for(String key:new String[]{"document_id","pdf_sha256","pdf_storage_name","pdf_byte_size"})if(!first.has(key)||!second.has(key)||!first.get(key).toString().equals(second.get(key).toString()))return false;return true;}
    @Override public boolean pendingDocument(JSONObject invoice)throws Exception {
        String id=invoice.optString("id");if(documents==null||id.isEmpty()||!invoice.optString("document_job_id").equals("invoice:"+id)||!invoice.optString("pdf_state").equals("PDF_PENDING")||invoice.optString("invoice_number").trim().isEmpty()||invoice.optString("issued_at").isEmpty())return false;
        // A missing or damaged previously produced original is never reclassified as a pending job.
        for(String name:new String[]{"pdf_sha256","pdf_storage_name","pdf_base64"})if(!invoice.optString(name).isEmpty())return false;
        JSONObject job=documents.get(id);if(job==null||job.optInt("formatVersion")!=1||!job.optString("invoice_id").equals(id)||!job.optString("phase").equals("PDF_PENDING"))return false;
        if(job.has("original_adoption"))return false;
        if(job.has("creation_authorization")){JSONObject proof=job.optJSONObject("creation_authorization");if(proof==null||proof.optInt("formatVersion")!=1||!job.optString("snapshot_sha256").equals(proof.optString("snapshot_sha256"))||!proof.optString("ledger_proof_sha256").matches("[0-9a-f]{64}")||proof.optString("installation_id").isEmpty()||proof.optString("restore_epoch").isEmpty()||proof.optString("request_id").isEmpty())return false;java.time.Instant.parse(proof.getString("authorized_at"));}
        JSONObject snapshot=job.optJSONObject("snapshot");String hash=job.optString("snapshot_sha256");if(snapshot==null||!hash.matches("[0-9a-f]{64}")||!PixelMcpWriteTools.hash(snapshot).equals(hash))return false;
        if(!snapshot.optString("id").equals(id)||!snapshot.optString("invoice_number").equals(invoice.optString("invoice_number"))||!snapshot.optString("issued_at").equals(invoice.optString("issued_at"))||!snapshot.optString("document_job_id").equals("invoice:"+id)||!snapshot.optString("pdf_state").equals("PDF_PENDING"))return false;
        JSONObject issuer=snapshot.optJSONObject("issuer"),recipient=snapshot.optJSONObject("recipient"),money=snapshot.optJSONObject("money_minor");JSONArray positions=snapshot.optJSONArray("positions");if(issuer==null||recipient==null||money==null||positions==null||positions.length()==0)return false;
        for(String field:new String[]{"owner_name","business_street","business_postal_code","business_city"})if(issuer.optString(field).trim().isEmpty())return false;
        for(String field:new String[]{"name","street","postal_code","city"})if(recipient.optString(field).trim().isEmpty())return false;
        String tax=snapshot.optString("tax_mode");if(!java.util.Arrays.asList("small_business","standard").contains(tax)||tax.equals("small_business")&&issuer.optString("tax_exemption_text").trim().isEmpty()||tax.equals("standard")&&issuer.optString("business_vat_id").trim().isEmpty())return false;
        java.time.Instant.parse(snapshot.getString("issued_at"));for(String field:new String[]{"invoice_date","service_start","service_end"})java.time.LocalDate.parse(snapshot.getString(field));
        if(!PixelMcpWriteTools.hash(LocalAuthorityRules.exactTotals(snapshot)).equals(PixelMcpWriteTools.hash(money)))return false;
        JSONObject original=new JSONObject(snapshot.toString()),current=new JSONObject(invoice.toString());
        // Payment/cancellation status and document completion are separate from the frozen invoice.
        for(String mutable:new String[]{"status","updated_at","payment_balance","pdf_state","pdf_sha256","pdf_storage_name","pdf_byte_size","pdf_base64","document_id"}){original.remove(mutable);current.remove(mutable);}
        return PixelMcpWriteTools.hash(original).equals(PixelMcpWriteTools.hash(current));
    }
}

package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class BackupPortableFormatTests {
    static int checks;static void yes(boolean value){checks++;if(!value)throw new AssertionError("Portable format check "+checks);}
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("qa_portable_format_");try{
            for(String key:new String[]{"revier:v1:entity:property:qa_p","revier:v1:audit:qa_history","local_payment:qa_part1","local_invoice_sources:qa_invoice","local_invoice_material_sources:qa_invoice","invoice_opening_balance:qa_invoice","local_number_reservation:qa_request","local_invoice_job:qa_invoice","local_invoice_counter","local_invoice_external_evidence","local_invoice_reconciliation:qa_request","local_invoice_document_recovery:qa_request"})yes(BackupPortableFormat.allowedBusinessKey(key));
            for(String key:new String[]{"backup_job:qa_backup","full_backup_saf:qa_export","full_backup_browser","portable_export:qa_export","weather_secret","mcp_upload:qa_upload","drive_upload_session","settings","api_key","local_invoice_unknown_private_key"})yes(!BackupPortableFormat.allowedBusinessKey(key));
            JSONObject customer=new JSONObject().put("id","qa_customer").put("name","QA Ä\"☘\nText").put("notes"," =HYPERLINK(\"https://example.invalid\")");JSONArray rows=new JSONArray().put(customer);String csv=BackupPortableFormat.csv(rows);yes(csv.startsWith("\"id\",")&&csv.contains("QA Ä\"\"☘\nText")&&csv.contains("' =HYPERLINK"));yes(BackupPortableFormat.csv(new JSONArray().put(new JSONObject().put("amount_minor",-6000))).contains("\"-6000\""));
            File photo=root.resolve("qa_original.jpg").toFile();byte[] block=new byte[65536];new Random(27).nextBytes(block);try(OutputStream output=new FileOutputStream(photo)){for(int i=0;i<768;i++)output.write(block);}String original=EncryptedBackupArchive.sha256(photo);
            JSONObject business=new JSONObject().put("customers",rows).put("invoices",new JSONArray().put(new JSONObject().put("id","qa_invoice").put("customer_id","qa_customer").put("money_minor",new JSONObject().put("gross_minor",14800))));
            JSONObject meta=new JSONObject().put("capturedAt","2027-05-18T11:00:00Z").put("schemaVersion",4).put("businessId","qa_business").put("backupId","qa_export").put("database",new JSONObject().put("sha256","a".repeat(64))).put("counts",new JSONObject().put("customers",1));Path archive=root.resolve("qa_portable.zip");
            Map<String,JSONArray> localCsv=new LinkedHashMap<>();localCsv.put("payments",new JSONArray().put(new JSONObject().put("id","qa_payment").put("invoice_id","qa_invoice").put("amount_minor",6000)));
            try(OutputStream output=Files.newOutputStream(archive)){BackupPortableFormat.write(output,business,meta,Collections.singletonList(new EncryptedBackupArchive.Source("files/customer-photo-originals/qa_original.jpg",photo)),writer->writer.write("{\"formatVersion\":1,\"entries\":[{\"key\":\"local_payment:qa_payment\",\"value\":\"{\\\"amount_minor\\\":6000}\"}]}"),localCsv);}
            yes(Files.size(archive)>40L*1024*1024);yes(BackupPortableFormat.verify(archive.toFile(),meta).getString("completion").equals("COMPLETE"));
            try(ZipFile zip=new ZipFile(archive.toFile(),StandardCharsets.UTF_8)){
                JSONObject restored=new JSONObject(new String(zip.getInputStream(zip.getEntry("business.json")).readAllBytes(),StandardCharsets.UTF_8));yes(restored.getJSONObject("business").getJSONArray("customers").getJSONObject(0).getString("notes").equals(customer.getString("notes")));yes(restored.getJSONObject("business").getJSONArray("invoices").getJSONObject(0).getJSONObject("money_minor").getLong("gross_minor")==14800);
                JSONObject local=new JSONObject(new String(zip.getInputStream(zip.getEntry("revier.json")).readAllBytes(),StandardCharsets.UTF_8));yes(local.getJSONArray("entries").getJSONObject(0).getString("value").contains("6000"));
                String payments=new String(zip.getInputStream(zip.getEntry("csv/local_payments.csv")).readAllBytes(),StandardCharsets.UTF_8);yes(payments.contains("qa_payment")&&payments.contains("qa_invoice")&&payments.contains("6000"));
                JSONObject manifest=new JSONObject(new String(zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(),StandardCharsets.UTF_8));yes(manifest.getBoolean("plaintext")&&!manifest.getBoolean("restorable_database_backup")&&manifest.getString("completion").equals("COMPLETE"));JSONArray entries=manifest.getJSONArray("file_manifest");for(int i=0;i<entries.length();i++){JSONObject item=entries.getJSONObject(i);ZipEntry entry=zip.getEntry(item.getString("path"));yes(entry!=null&&entry.getSize()==item.getLong("size"));}
                Path originalCopy=root.resolve("qa_roundtrip.jpg");try(InputStream input=zip.getInputStream(zip.getEntry("originals/files/customer-photo-originals/qa_original.jpg"));OutputStream output=Files.newOutputStream(originalCopy)){input.transferTo(output);}yes(EncryptedBackupArchive.sha256(originalCopy.toFile()).equals(original));yes(zip.getEntry("database.sqlite")==null&&zip.getEntry("preferences.json")==null);
            }
            System.out.println("PASS "+checks+" plaintext export/48MiB stream/exactIDs/minorAmounts/originalhash/UTF8CSV/formula-protection/business-key-allowlist checks;synthetic dataset,no provider or product data");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}}
    }
}

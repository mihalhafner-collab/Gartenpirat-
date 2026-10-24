package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Real isolated host files, including large streamed originals and rejected symlink escapes. */
public final class LocalFinancialOriginalsTests {
    static int checks;static void yes(boolean value){checks++;if(!value)throw new AssertionError("Financial originals "+checks);}
    static String hash(byte[] bytes)throws Exception{StringBuilder result=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))result.append(String.format(Locale.ROOT,"%02x",value&255));return result.toString();}
    static JSONObject evidence(String sha,Long size)throws Exception{JSONObject entry=new JSONObject().put("invoice_id","qa_external_invoice").put("invoice_number","QA-EXTERNAL-1").put("pdf_sha256",sha);if(size!=null)entry.put("pdf_byte_size",size);return new JSONObject().put("issued",new JSONArray().put(entry));}
    static void denied(File files,JSONObject evidence,String code)throws Exception{try{LocalFinancialOriginals.inspect(files,evidence);throw new AssertionError("Expected original rejection "+code);}catch(IOException expected){yes(expected.getMessage().equals("FINANCIAL_ORIGINAL_"+code));}}
    public static void main(String[] ignored)throws Exception{
        SyntheticBusinessData.verify();Path root=Files.createTempDirectory("qa_financial_originals_");try{
            Path files=Files.createDirectory(root.resolve("qa_captured_files")),documents=Files.createDirectory(files.resolve("business-documents"));byte[] pdf="%PDF-1.4\nQA synthetic original only\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);String sha=hash(pdf);Path original=documents.resolve(sha+".pdf");Files.write(original,pdf);JSONObject proof=evidence(sha,(long)pdf.length);String before=proof.toString();
            JSONObject result=LocalFinancialOriginals.inspect(files.toFile(),proof);yes(result.getString("status").equals("PASS")&&result.getInt("reference_count")==1&&result.getInt("original_count")==1&&result.getLong("byte_size")==pdf.length&&result.getBoolean("original_files_verified")&&!result.getBoolean("data_changed"));yes(before.equals(proof.toString())&&Arrays.equals(Files.readAllBytes(original),pdf));yes(!result.toString().contains("QA-EXTERNAL")&&!result.toString().contains("qa_external_invoice")&&!result.toString().contains(files.toString()));yes(LocalFinancialOriginals.inspect(files.toFile(),proof).getString("manifest_sha256").equals(result.getString("manifest_sha256")));
            JSONObject missingSize=LocalFinancialOriginals.inspect(files.toFile(),evidence(sha,null));yes(missingSize.getInt("size_observed_only_count")==1&&missingSize.getString("manifest_sha256").equals(result.getString("manifest_sha256")));
            JSONObject duplicates=evidence(sha,null);duplicates.getJSONArray("issued").put(proof.getJSONArray("issued").getJSONObject(0));JSONObject duplicateResult=LocalFinancialOriginals.inspect(files.toFile(),duplicates);yes(duplicateResult.getInt("original_count")==1&&duplicateResult.getInt("reference_count")==2&&duplicateResult.getLong("byte_size")==pdf.length&&duplicateResult.getString("manifest_sha256").equals(result.getString("manifest_sha256")));
            denied(files.toFile(),evidence(sha,(long)pdf.length+1),"SIZE_MISMATCH");denied(files.toFile(),evidence("a".repeat(64),(long)pdf.length),"MISSING");denied(files.toFile(),evidence("../qa_escape",(long)pdf.length),"EVIDENCE_INVALID");
            JSONObject fractional=evidence(sha,(long)pdf.length);fractional.getJSONArray("issued").getJSONObject(0).put("pdf_byte_size",pdf.length+0.5);denied(files.toFile(),fractional,"EVIDENCE_INVALID");
            byte[] changed=pdf.clone();changed[changed.length-2]^=1;Files.write(original,changed);denied(files.toFile(),proof,"HASH_MISMATCH");yes(Arrays.equals(Files.readAllBytes(original),changed));Files.write(original,pdf);
            byte[] invalid="QA this is no PDF\n".getBytes(StandardCharsets.US_ASCII);String invalidSha=hash(invalid);Files.write(documents.resolve(invalidSha+".pdf"),invalid);denied(files.toFile(),evidence(invalidSha,(long)invalid.length),"PDF_INVALID");
            Path outside=root.resolve("qa_outside.pdf");Files.move(original,outside);Files.createSymbolicLink(original,outside);denied(files.toFile(),proof,"PATH_REJECTED");Files.delete(original);Files.move(outside,original);
            Path moved=root.resolve("qa_original_documents");Files.move(documents,moved);Files.createSymbolicLink(documents,moved);denied(files.toFile(),proof,"PATH_REJECTED");Files.delete(documents);Files.move(moved,documents);
            Path rootLink=root.resolve("qa_root_link");Files.createSymbolicLink(rootLink,files);denied(rootLink.toFile(),proof,"PATH_REJECTED");Files.delete(rootLink);
            yes(LocalFinancialOriginals.inspect(root.resolve("qa_missing_unused_root").toFile(),new JSONObject()).getInt("original_count")==0);
            Path largeTemporary=documents.resolve("qa_streaming_original.tmp");MessageDigest digest=MessageDigest.getInstance("SHA-256");long size;try(OutputStream output=new BufferedOutputStream(Files.newOutputStream(largeTemporary))){byte[] header="%PDF-1.4\nQA large streaming fixture\n".getBytes(StandardCharsets.US_ASCII);output.write(header);digest.update(header);byte[] block=new byte[32768];Arrays.fill(block,(byte)'Q');for(int i=0;i<1536;i++){output.write(block);digest.update(block);}size=header.length+1536L*block.length;}StringBuilder largeSha=new StringBuilder();for(byte value:digest.digest())largeSha.append(String.format(Locale.ROOT,"%02x",value&255));Path large=documents.resolve(largeSha+".pdf");Files.move(largeTemporary,large);JSONObject largeResult=LocalFinancialOriginals.inspect(files.toFile(),evidence(largeSha.toString(),size));yes(size>40L*1024*1024&&largeResult.getLong("byte_size")==size&&largeResult.getInt("original_count")==1);
            System.out.println("PASS "+checks+" external-original real-file/hash/size/header/dedup/path/symlink/48MiB streaming checks; synthetic isolated files only");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(root)){for(Path path:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}}
    }
}

package at.hausgarten.service;

import org.json.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.channels.Channels;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Streaming original proof for external ledger entries, including absent canonical invoices. */
final class LocalFinancialOriginals {
    /** The immutable receipt keeps proof A even when a later reviewed SQL adoption uses proof B. */
    static boolean receiptEvidenceMatches(JSONObject adoption,JSONObject receipt)throws Exception {
        Object ledger=adoption.opt("ledger_proof_sha256"),bound=adoption.has("receipt_proof_sha256")?adoption.opt("receipt_proof_sha256"):ledger;
        if(!(ledger instanceof String)||!((String)ledger).matches("[0-9a-f]{64}")||!(bound instanceof String)||!((String)bound).matches("[0-9a-f]{64}"))return false;
        if(!receipt.has("recovered_from_full_evidence_sha256"))return !adoption.has("receipt_proof_sha256");
        Object recorded=receipt.opt("recovered_from_full_evidence_sha256");return recorded instanceof String&&((String)recorded).matches("[0-9a-f]{64}")&&bound.equals(recorded);
    }
    private static IOException error(String code){return new IOException("FINANCIAL_ORIGINAL_"+code);}
    private static long size(Object raw)throws IOException {try{long value=new BigDecimal(raw.toString()).longValueExact();if(value<5)throw error("EVIDENCE_INVALID");return value;}catch(IOException fixed){throw fixed;}catch(Exception invalid){throw error("EVIDENCE_INVALID");}}
    private static String hex(byte[] bytes){StringBuilder result=new StringBuilder();for(byte value:bytes)result.append(String.format(Locale.ROOT,"%02x",value&255));return result.toString();}
    private static void safeDirectory(Path path)throws IOException {if(Files.isSymbolicLink(path)||!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw error("PATH_REJECTED");}
    private static void sameFile(BasicFileAttributes before,BasicFileAttributes after)throws IOException {if(!after.isRegularFile()||before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())||!Objects.equals(before.fileKey(),after.fileKey()))throw error("CHANGED");}
    private static long verify(Path root,Path directory,String sha,Long expected)throws Exception {
        safeDirectory(root);safeDirectory(directory);Path path=directory.resolve(sha+".pdf");
        if(Files.isSymbolicLink(path))throw error("PATH_REJECTED");if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw error("MISSING");
        Path actual=path.toRealPath(LinkOption.NOFOLLOW_LINKS);if(!actual.getParent().equals(directory.toRealPath(LinkOption.NOFOLLOW_LINKS))||!actual.startsWith(root.toRealPath(LinkOption.NOFOLLOW_LINKS)))throw error("PATH_REJECTED");
        BasicFileAttributes before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(before.size()<5)throw error("PDF_INVALID");if(expected!=null&&before.size()!=expected)throw error("SIZE_MISMATCH");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;byte[] header=new byte[5];Set<OpenOption> options=new HashSet<>();options.add(StandardOpenOption.READ);options.add(LinkOption.NOFOLLOW_LINKS);
        try(InputStream input=new BufferedInputStream(Channels.newInputStream(Files.newByteChannel(path,options)))){byte[] buffer=new byte[32768];int n;while((n=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("FINANCIAL_ORIGINAL_INTERRUPTED");if(count<5)System.arraycopy(buffer,0,header,(int)count,Math.min(n,5-(int)count));digest.update(buffer,0,n);count=Math.addExact(count,n);}}
        safeDirectory(root);safeDirectory(directory);if(Files.isSymbolicLink(path))throw error("PATH_REJECTED");BasicFileAttributes after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);sameFile(before,after);
        if(count!=before.size()||expected!=null&&count!=expected)throw error("SIZE_MISMATCH");if(!new String(header,StandardCharsets.US_ASCII).equals("%PDF-"))throw error("PDF_INVALID");if(!sha.equals(hex(digest.digest())))throw error("HASH_MISMATCH");return count;
    }
    static JSONObject inspect(File capturedFiles,JSONObject externalEvidence)throws Exception {
        try{
            JSONArray entries=externalEvidence==null||externalEvidence.length()==0?new JSONArray():externalEvidence.getJSONArray("issued");TreeMap<String,Long> originals=new TreeMap<>();int withSize=0,pendingNotCreated=0,originalReferences=0;
            for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);String resolution=entry.optString("pdf_resolution","original");if(!Arrays.asList("original","not_created").contains(resolution))throw error("EVIDENCE_INVALID");if(resolution.equals("not_created")){
                    if(!(entry.opt("invoice_id") instanceof String)||entry.getString("invoice_id").trim().isEmpty()||!(entry.opt("invoice_number") instanceof String)||entry.getString("invoice_number").trim().isEmpty()||!Boolean.TRUE.equals(entry.opt("not_created_confirmed"))||!entry.optString("snapshot_sha256").matches("[0-9a-f]{64}")||!(entry.opt("not_created_reason") instanceof String)||entry.getString("not_created_reason").trim().isEmpty()||entry.getString("not_created_reason").length()>4000)throw error("EVIDENCE_INVALID");
                    for(String field:new String[]{"pdf_sha256","pdf_byte_size","pdf_storage_name","pdf_base64","document_id","document"})if(entry.has(field))throw error("EVIDENCE_INVALID");pendingNotCreated++;continue;
                }
                if(entry.has("not_created_confirmed")||entry.has("not_created_reason")||entry.has("snapshot_sha256")&&!entry.optString("snapshot_sha256").matches("[0-9a-f]{64}"))throw error("EVIDENCE_INVALID");originalReferences++;String sha=entry.getString("pdf_sha256");if(!sha.matches("[0-9a-f]{64}"))throw error("EVIDENCE_INVALID");Long expected=entry.has("pdf_byte_size")?size(entry.get("pdf_byte_size")):null;if(expected!=null)withSize++;if(originals.containsKey(sha)){Long previous=originals.get(sha);if(previous!=null&&expected!=null&&!previous.equals(expected))throw error("EVIDENCE_INVALID");if(previous==null&&expected!=null)originals.put(sha,expected);}else originals.put(sha,expected);}
            MessageDigest manifest=MessageDigest.getInstance("SHA-256");manifest.update("hausgarten.financial_originals.v1\n".getBytes(StandardCharsets.US_ASCII));long bytes=0;
            if(!originals.isEmpty()){if(capturedFiles==null)throw error("PATH_REJECTED");Path root=capturedFiles.toPath().toAbsolutePath().normalize(),directory=root.resolve("business-documents");for(Map.Entry<String,Long> entry:originals.entrySet()){long actual=verify(root,directory,entry.getKey(),entry.getValue());bytes=Math.addExact(bytes,actual);manifest.update((entry.getKey()+" "+actual+"\n").getBytes(StandardCharsets.US_ASCII));}}
            return new JSONObject().put("formatVersion",1).put("status","PASS").put("reference_count",entries.length()).put("original_count",originals.size()).put("byte_size",bytes).put("size_evidence_count",withSize).put("size_observed_only_count",originalReferences-withSize).put("original_reference_count",originalReferences).put("pending_not_created_count",pendingNotCreated).put("manifest_format","sha256_size_ascii_lines_v1").put("manifest_sha256",hex(manifest.digest())).put("original_files_verified",pendingNotCreated==0).put("data_changed",false);
        }catch(IOException fixed){if(fixed.getMessage()!=null&&fixed.getMessage().startsWith("FINANCIAL_ORIGINAL_"))throw fixed;throw error("READ_FAILED");}catch(Exception invalid){throw error("EVIDENCE_INVALID");}
    }
    private LocalFinancialOriginals(){}
}

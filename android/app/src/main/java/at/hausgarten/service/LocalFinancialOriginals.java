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
            JSONArray entries=externalEvidence==null||externalEvidence.length()==0?new JSONArray():externalEvidence.getJSONArray("issued");TreeMap<String,Long> originals=new TreeMap<>();int withSize=0;
            for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);String sha=entry.getString("pdf_sha256");if(!sha.matches("[0-9a-f]{64}"))throw error("EVIDENCE_INVALID");Long expected=entry.has("pdf_byte_size")?size(entry.get("pdf_byte_size")):null;if(expected!=null)withSize++;if(originals.containsKey(sha)){Long previous=originals.get(sha);if(previous!=null&&expected!=null&&!previous.equals(expected))throw error("EVIDENCE_INVALID");if(previous==null&&expected!=null)originals.put(sha,expected);}else originals.put(sha,expected);}
            MessageDigest manifest=MessageDigest.getInstance("SHA-256");manifest.update("hausgarten.financial_originals.v1\n".getBytes(StandardCharsets.US_ASCII));long bytes=0;
            if(!originals.isEmpty()){if(capturedFiles==null)throw error("PATH_REJECTED");Path root=capturedFiles.toPath().toAbsolutePath().normalize(),directory=root.resolve("business-documents");for(Map.Entry<String,Long> entry:originals.entrySet()){long actual=verify(root,directory,entry.getKey(),entry.getValue());bytes=Math.addExact(bytes,actual);manifest.update((entry.getKey()+" "+actual+"\n").getBytes(StandardCharsets.US_ASCII));}}
            return new JSONObject().put("formatVersion",1).put("status","PASS").put("reference_count",entries.length()).put("original_count",originals.size()).put("byte_size",bytes).put("size_evidence_count",withSize).put("size_observed_only_count",entries.length()-withSize).put("manifest_format","sha256_size_ascii_lines_v1").put("manifest_sha256",hex(manifest.digest())).put("original_files_verified",true).put("data_changed",false);
        }catch(IOException fixed){if(fixed.getMessage()!=null&&fixed.getMessage().startsWith("FINANCIAL_ORIGINAL_"))throw fixed;throw error("READ_FAILED");}catch(Exception invalid){throw error("EVIDENCE_INVALID");}
    }
    private LocalFinancialOriginals(){}
}

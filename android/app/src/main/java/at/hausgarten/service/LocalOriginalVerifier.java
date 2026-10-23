package at.hausgarten.service;

import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Shared read-only original checks for live diagnostics and isolated backup snapshots. */
final class LocalOriginalVerifier implements LocalMigrationInventory.Originals {
    interface PhotoMetadata {JSONObject get(String id)throws Exception;}
    private final File files;
    private final PhotoMetadata photos;
    LocalOriginalVerifier(File files,PhotoMetadata photos){this.files=files;this.photos=photos;}
    @Override public boolean verified(String kind,JSONObject reference)throws Exception {
        JSONObject metadata=reference;File file;
        if(kind.equals("photo")){
            JSONObject local=photos.get(reference.optString("id"));if(local!=null)metadata=local;
            if(!PhotoAttachmentStore.canonicalName(metadata.optString("storage_name")))return false;
            file=new File(new File(files,"customer-photo-originals"),metadata.getString("storage_name"));
        }else{
            if(!metadata.optString("pdf_storage_name").matches("[0-9a-f]{64}\\.pdf"))return false;
            file=new File(new File(files,"business-documents"),metadata.getString("pdf_storage_name"));
        }
        String expected=metadata.optString(kind.equals("photo")?"content_sha256":"pdf_sha256");long size=metadata.optLong(kind.equals("photo")?"byte_size":"pdf_byte_size",-1);
        if(!expected.matches("[0-9a-f]{64}")||!file.isFile()||java.nio.file.Files.isSymbolicLink(file.toPath())||file.length()!=size)return false;
        if(!kind.equals("photo"))try(InputStream header=new FileInputStream(file)){byte[] prefix=new byte[5];if(header.read(prefix)!=5||!new String(prefix,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))return false;}
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long read=0;
        try(InputStream input=new BufferedInputStream(new FileInputStream(file))){byte[] buffer=new byte[32768];int n;while((n=input.read(buffer))!=-1){digest.update(buffer,0,n);read+=n;}}
        StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",value&255));return read==size&&expected.equals(hash.toString());
    }
}

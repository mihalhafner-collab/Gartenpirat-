package at.hausgarten.service;
import org.json.JSONObject;
import java.io.IOException;

/** One legacy inline-PDF decoding/reference rule; caller decides whether to cache bytes. */
final class LegacyPdfAttachment {
    static final class Prepared {final byte[] bytes;Prepared(byte[] bytes){this.bytes=bytes;}}
    static Prepared describe(JSONObject invoice)throws Exception {
        String inline=invoice.optString("pdf_base64");if(inline.isEmpty())return null;
        if(inline.length()>28*1024*1024)throw new IOException("Original-PDF ist zu groß.");
        byte[] raw=android.util.Base64.decode(inline,android.util.Base64.DEFAULT);String hash=PhotoAttachmentStore.sha256(raw);
        if(!invoice.optString("pdf_sha256").isEmpty()&&!hash.equals(invoice.getString("pdf_sha256")))throw new IOException("Original-PDF hat eine abweichende Prüfsumme.");
        invoice.put("pdf_sha256",hash).put("pdf_storage_name",hash+".pdf").put("pdf_byte_size",raw.length).put("document_id","invoice:"+invoice.getString("id"));return new Prepared(raw);
    }
    private LegacyPdfAttachment(){}
}

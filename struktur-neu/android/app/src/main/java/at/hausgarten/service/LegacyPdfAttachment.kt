package at.hausgarten.service

import java.io.IOException
import org.json.JSONObject

/** One legacy inline-PDF decoding/reference rule; caller decides whether to cache bytes. */
internal object LegacyPdfAttachment {
    class Prepared {
        @JvmField val bytes: ByteArray

        constructor(bytes: ByteArray) {
            this.bytes = bytes
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun describe(invoice: JSONObject): Prepared? {
        var inline = invoice!!.optString("pdf_base64")
        if (inline!!.isEmpty()) {
            return null
        }
        if ((inline!!.length > ((28 * 1024) * 1024))) {
            throw IOException("Original-PDF ist zu groß.")
        }
        var raw: ByteArray = android.util.Base64.decode(inline, android.util.Base64.DEFAULT)
        var hash = PhotoAttachmentStore.sha256(raw)
        if (
            (!(invoice!!.optString("pdf_sha256"))!!.isEmpty() &&
                !hash!!.equals(invoice!!.getString("pdf_sha256")))
        ) {
            throw IOException("Original-PDF hat eine abweichende Prüfsumme.")
        }
        (((invoice!!.put("pdf_sha256", hash))!!.put(
                    "pdf_storage_name",
                    (hash.toString() + ".pdf"),
                ))!!
                .put("pdf_byte_size", raw!!.size))!!
            .put("document_id", ("invoice:" + invoice!!.getString("id")))
        return Prepared(raw)
    }
}

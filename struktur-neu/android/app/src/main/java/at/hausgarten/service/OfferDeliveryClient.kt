package at.hausgarten.service

import java.time.Instant
import java.util.*
import org.json.*

/** Read-only Gmail evidence adapter. It NEVER sends mail or trusts a client-supplied receipt. */
internal class OfferDeliveryClient {
    private val token: CalendarClient.Token
    private val wire: HttpTransport.Wire

    constructor(token: CalendarClient.Token, wire: HttpTransport.Wire) {
        this.token = token
        this.wire = wire
    }

    @Throws(Exception::class)
    fun read(path: String): JSONObject {
        var r =
            wire!!.send(
                "GET",
                ("https://www.googleapis.com/gmail/v1/users/me/" + path),
                Collections.singletonMap("Authorization", ("Bearer " + token!!.get())),
                null,
                ((28 * 1024) * 1024),
            )
        if (((r!!.status == 401) || (r!!.status == 403))) {
            throw PixelMcpWriteTools.Rejected(
                "MAIL_AUTH_REQUIRED",
                "Gmail-Lesefreigabe für Versandbelege am Gerät bestätigen.",
            )
        }
        if (((r!!.status == 429) || (r!!.status >= 500))) {
            throw HttpTransport.Failure(
                "MAIL_TEMPORARY_UNAVAILABLE",
                "Mailanbieter vorübergehend nicht verfügbar. Später erneut prüfen; kein Versand wiederholen.",
                HttpTransport.retryAfter(r!!.header("Retry-After")),
                false,
            )
        }
        if ((r!!.status != 200)) {
            throw PixelMcpWriteTools.Rejected(
                "MAIL_READBACK_REQUIRED",
                "Versand nicht vom Mailanbieter bestätigt; kein sent-Status.",
            )
        }
        return r!!.json()
    }

    @Throws(Exception::class)
    fun verify(id: String, offer: JSONObject): JSONObject {
        if (!id!!.matches(Regex("[A-Za-z0-9_-]{1,200}"))) {
            throw IllegalArgumentException("Ungültige Nachrichtenkennung.")
        }
        var message = read(((("messages/" + id)).toString() + "?format=full"))
        if (
            (!id!!.equals(message!!.optString("id")) ||
                !((message!!.getJSONArray("labelIds"))!!.toString())!!.contains("\"SENT\""))
        ) {
            throw IllegalArgumentException("Nachricht ist nicht als versendet bestätigt.")
        }
        var payload = message!!.getJSONObject("payload")
        var headers: MutableMap<String, String> = HashMap()
        var h = payload!!.getJSONArray("headers")
        run {
            var i: Int = 0
            while ((i < h!!.length())) {
                var key = ((h!!.getJSONObject(i))!!.getString("name"))!!.lowercase(Locale.ROOT)
                if (((key!!.equals("to") || key!!.equals("from")) && headers!!.containsKey(key))) {
                    throw IllegalArgumentException("Mehrdeutige Versandköpfe.")
                }
                headers!!.put(key, (h!!.getJSONObject(i))!!.getString("value"))

                i++
            }
        }
        var recipient = offer!!.optString("delivery_recipient")
        var sender = (offer!!.getJSONObject("issuer"))!!.optString("business_email")
        if (
            (((recipient!!.isEmpty() ||
                !(oneAddress(headers!!.get("to")))!!.equals(recipient, ignoreCase = true)) ||
                sender!!.isEmpty()) ||
                !(oneAddress(headers!!.get("from")))!!.equals(sender, ignoreCase = true))
        ) {
            throw IllegalArgumentException(
                "Versandempfänger oder Aussteller stimmt nicht mit dem bestätigten Dokument überein."
            )
        }
        var found: Boolean = part(payload, id, offer!!.getString("pdf_sha256"), 0, intArrayOf(0))
        if (!found) {
            throw IllegalArgumentException("Original-PDF im Anbieterbeleg nicht nachgewiesen.")
        }
        return (((((((JSONObject())!!.put("provider", "gmail"))!!.put("message_id", id))!!.put(
                            "recipient_email",
                            recipient,
                        ))!!
                        .put("pdf_sha256", offer!!.getString("pdf_sha256")))!!
                    .put(
                        "sent_at",
                        (Instant.ofEpochMilli(
                                java.lang.Long.parseLong(message!!.getString("internalDate"))
                            ))!!
                            .toString(),
                    ))!!
                .put("verified_at", (Instant.now())!!.toString()))!!
            .put("verified_by", "pixel_gmail_readback")
    }

    private @Throws(Exception::class) fun part(
        p: JSONObject,
        message: String,
        hash: String,
        depth: Int,
        count: IntArray,
    ): Boolean {
        if (((depth > 8) || (++count!![0] > 100))) {
            throw IllegalArgumentException("Mailstruktur überschreitet Prüfgrenze.")
        }
        if ((p!!.optString("mimeType"))!!.equals("application/pdf")) {
            var body = p!!.getJSONObject("body")
            if ((body!!.optLong("size") > ((20 * 1024) * 1024))) {
                throw IllegalArgumentException("PDF zu groß.")
            }
            var data = body!!.optString("data")
            if ((data!!.isEmpty() && !(body!!.optString("attachmentId"))!!.isEmpty())) {
                var id = body!!.getString("attachmentId")
                if (!id!!.matches(Regex("[A-Za-z0-9_-]{1,1000}"))) {
                    throw IllegalArgumentException("Ungültige Anhangkennung.")
                }
                data =
                    (read((((("messages/" + message)).toString() + "/attachments/") + id)))!!
                        .getString("data")
            }
            if ((data!!.length > ((28 * 1024) * 1024))) {
                throw IllegalArgumentException("Anhang überschreitet Prüfgrenze.")
            }
            var raw: ByteArray = (Base64.getUrlDecoder())!!.decode(data)
            if (
                (((raw!!.size > 0) && (raw!!.size <= ((20 * 1024) * 1024))) &&
                    (HttpTransport.sha256(raw))!!.equals(hash))
            ) {
                return true
            }
        }
        var parts = p!!.optJSONArray("parts")
        run {
            var i: Int = 0
            while (((parts !== null) && (i < parts!!.length()))) {
                if (part(parts!!.getJSONObject(i), message, hash, (depth + 1), count)) {
                    return true
                }

                i++
            }
        }
        return false
    }

    companion object {
        @JvmStatic
        fun oneAddress(value: String?): String {
            if (
                ((((value === null) || value!!.contains(",")) || value!!.contains("\n")) ||
                    value!!.contains("\r"))
            ) {
                return ""
            }
            var l: Int = value!!.indexOf('<')
            var r: Int = value!!.indexOf('>')
            var address =
                (if (((l >= 0) && (r > l))) value!!.substring((l + 1), r) else value!!.trim())
            return (if (address!!.matches(Regex("[^\\s<>@,;]+@[^\\s<>@,;]+\\.[^\\s<>@,;]+")))
                address
            else "")
        }
    }
}

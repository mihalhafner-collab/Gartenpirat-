package at.hausgarten.service

import java.net.*
import java.io.*

/** Fixed HTTPS destinations only; never includes provider bodies/URLs/keys in errors. */
internal class PlanningWire : HttpTransport.Wire {
    @Throws(Exception::class) override fun send(method: String, url: String, headers: Map<String, String>, body: ByteArray?, maximum: Int): HttpTransport.Reply {
        val target = URL(url)
        if (!allowed(method, target, headers)) throw SecurityException("Nicht freigegebenes Planungsziel.")
        val connection = target.openConnection() as HttpURLConnection
        connection.connectTimeout = 8000; connection.readTimeout = 12000; connection.instanceFollowRedirects = false; connection.requestMethod = method
        try {
            for ((key, value) in headers) connection.setRequestProperty(key, value)
            if (body != null) {
                connection.doOutput = true; connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode; val resultHeaders = HashMap<String, String>()
            for ((key, values) in connection.headerFields) if (key != null && values.isNotEmpty()) resultHeaders[key] = values[0]
            val result = ByteArrayOutputStream()
            (if (status >= 400) connection.errorStream else connection.inputStream)?.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer); if (count == -1) break
                    if (result.size() + count > maximum) throw IOException("Planungsantwort zu groß.")
                    result.write(buffer, 0, count)
                }
            }
            return HttpTransport.Reply(status, result.toByteArray(), resultHeaders)
        } catch (error: IOException) { throw IOException("Planungsdienst nicht erreichbar. Gespeicherte Daten bleiben erhalten.") }
        finally { connection.disconnect() }
    }
    companion object {
        @JvmStatic fun allowed(method: String, target: URL, headers: Map<String, String>): Boolean {
            if (target.protocol != "https" || target.port != -1 || target.userInfo != null || target.host !in listOf("www.googleapis.com", "warnungen.zamg.at", "dataset.api.hub.geosphere.at", "charts.ecmwf.int", "api.open-meteo.com")) return false
            if (target.host != "www.googleapis.com") {
                if (method != "GET") return false
                if (headers.keys.any { it.equals("Authorization", true) || it.equals("Cookie", true) }) return false
            }
            return true
        }
    }
}

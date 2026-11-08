package at.hausgarten.service

import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

class PixelTunnelWire {
    fun interface Transport {
        @Throws(Exception::class)
        fun request(
            method: String,
            path: String,
            body: JSONObject?,
            shard: String?,
            timeoutMs: Int,
        ): Reply
    }

    class Reply {
        @JvmField val code: Int
        @JvmField val json: JSONObject
        @JvmField val receivedNanos: Long

        constructor(code: Int, json: JSONObject) : this(code, json, System.nanoTime()) {}

        constructor(code: Int, json: JSONObject, time: Long) {
            this.code = code
            this.json = json
            this.receivedNanos = time
        }
    }

    class AccessError : IOException {
        @JvmField val httpCode: Int
        @JvmField val reason: String

        constructor(code: Int) : this(code, "auth_denied") {}

        constructor(
            code: Int,
            reason: String,
        ) : super("Tunnel-ID oder Runtime-Berechtigung prüfen.") {
            httpCode = code
            this.reason = reason
        }

        fun userMessage(): String {

            when (reason) {
                "organization_required" -> {
                    return "Die OpenAI-Organisationsangabe fehlt. Verbindungseinstellungen öffnen und die Organisation des Pixel-Tunnels bestätigen."
                }
                "organization_mismatch" -> {
                    return "Der gespeicherte Schlüssel gehört zu einer anderen OpenAI-Organisation als der Pixel-Tunnel. Bitte einen Schlüssel aus der Organisation des Tunnels eingeben."
                }
                "tunnel_access_denied" -> {
                    return "Der Schlüssel darf diesen Tunnel nicht verwenden. Im richtigen OpenAI-Konto Tunnels Read + Use prüfen."
                }
                "invalid_api_key" -> {
                    return "OpenAI erkennt den gespeicherten Schlüssel nicht. Bitte den gültigen Runtime-Schlüssel erneut im privaten Einrichtungsfenster eingeben."
                }
                else -> {
                    return (("OpenAI verweigert die Tunnel-Anmeldung (HTTP " + httpCode) +
                        "). Organisation, Schlüssel und Tunnels Read + Use prüfen.")
                }
            }
        }
    }

    class RateLimit : IOException {
        @JvmField val retryAfterMs: Long

        constructor(wait: Long) : super("Tunnel-Anfragen sind vorübergehend begrenzt.") {
            retryAfterMs = Math.max(1000, Math.min(900000, wait))
        }
    }

    private val id: String
    private val transport: Transport
    private val tools: PixelMcpReadTools

    private class Cached {
        @JvmField val fingerprint: String
        @JvmField val response: JSONObject

        constructor(fingerprint: String, response: JSONObject) {
            this.fingerprint = fingerprint
            this.response = response
        }
    }

    private val results: MutableMap<String, Cached> =
        object : LinkedHashMap<String, Cached>() {
            override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Cached>?): Boolean {

                return (size > 128)
            }
        }

    constructor(id: String, transport: Transport, tools: PixelMcpReadTools) {
        PixelTunnelConfig.validateId(id)
        this.id = id
        this.transport = transport
        this.tools = tools
    }

    private fun path(suffix: String): String {

        return (("/v1/tunnels/" + id) + suffix)
    }

    @Throws(Exception::class)
    fun verify(): Unit {

        check(transport.request("GET", path(""), null, null, 15000))
    }

    @Throws(Exception::class)
    fun cycle(): Int {

        var poll =
            transport.request("GET", path("/poll?limit=1&timeout_ms=15000"), null, null, 35000)
        check(poll)
        if ((poll.code == 204)) {
            return 0
        }
        var commands = poll.json.getJSONArray("commands")
        var delivered: Int = 0
        run {
            var i: Int = 0
            var firstIteration112 = true
            while (true) {
                if (firstIteration112) firstIteration112 = false
                else {
                    i++
                }
                if (!((i < commands.length()))) break

                if (Thread.currentThread().isInterrupted()) {
                    throw InterruptedException()
                }
                var command = commands.getJSONObject(i)
                var expiry: Long = deadline(command.opt("response_timeout"), poll.receivedNanos)
                if (((expiry != Long.MAX_VALUE) && (System.nanoTime() >= expiry))) {
                    continue
                }
                var requestId = command.getString("request_id")
                var shard = command.getString("shard_token")
                if (
                    (((((requestId.isEmpty() || (requestId.length > 4096)) || shard.isEmpty()) ||
                        (shard.length > 16384)) || (shard.indexOf('\r') >= 0)) ||
                        (shard.indexOf('\n') >= 0))
                ) {
                    throw IOException("Ungültige Tunnel-Antwort.")
                }
                var type = command.optString("command_type")
                var channel = command.optString("channel", "main")
                if ((!(("jsonrpc" == type)) && !(("session_termination" == type)))) {
                    continue
                }
                var fingerprint =
                    ((((type + "\n") + channel) + "\n") +
                        canonical(command.optJSONObject("jsonrpc")))
                var cached = results.get(requestId)
                if (((cached != null) && !((cached.fingerprint == fingerprint)))) {
                    throw IOException(
                        "Widersprüchliche Anfrage-ID. Verbindung wird erneut geprüft."
                    )
                }
                var response = (if ((cached == null)) null else cached.response)
                if ((response == null)) {
                    response = JSONObject().put("request_id", requestId).put("channel", channel)
                    if ((type == "session_termination")) {
                        response
                            .put("resp_code", 204)
                            .put("resp_type", "session_termination_response")
                    } else {
                        var rpc = command.getJSONObject("jsonrpc")
                        var result: JSONObject?
                        if (!(("main" == channel))) {
                            result =
                                JSONObject()
                                    .put("jsonrpc", "2.0")
                                    .put(
                                        "id",
                                        (if ((rpc.opt("id") == null)) JSONObject.NULL
                                        else rpc.opt("id")),
                                    )
                                    .put(
                                        "error",
                                        JSONObject()
                                            .put("code", -(32601))
                                            .put("message", "Channel nicht unterstützt."),
                                    )
                        } else {
                            result = tools.rpc(rpc)
                        }
                        response
                            .put("resp_code", (if ((result == null)) 202 else 200))
                            .put(
                                "resp_type",
                                (if ((result == null)) "notify_ack" else "jsonrpc_response"),
                            )
                        if ((result != null)) {
                            response
                                .put("resp_json", result)
                                .put(
                                    "resp_headers",
                                    JSONObject()
                                        .put("Content-Type", JSONArray().put("application/json")),
                                )
                        }
                    }
                    results.put(requestId, Cached(fingerprint, response))
                }
                if (((expiry != Long.MAX_VALUE) && (System.nanoTime() >= expiry))) {
                    continue
                }
                var timeout: Int =
                    (if ((expiry == Long.MAX_VALUE)) 15000
                    else
                        (Math.max(1, Math.min(15000, (((expiry - System.nanoTime())) / 1000000))))
                            .toInt())
                check(transport.request("POST", path("/response"), response, shard, timeout))
                delivered++
            }
        }
        return delivered
    }

    class HttpTransport : Transport {
        private val root: String
        private val secret: String
        private val organization: String
        private val instance: String = UUID.randomUUID().toString()
        @Volatile private var active: HttpURLConnection? = null

        constructor(secret: String) : this("https://api.openai.com", secret, false) {}

        constructor(
            secret: String,
            organization: String,
        ) : this("https://api.openai.com", secret, false, organization) {}

        constructor(root: String, secret: String, test: Boolean) : this(root, secret, test, "") {}

        constructor(root: String, secret: String, test: Boolean, organization: String) {
            if (
                (!((root == "https://api.openai.com")) &&
                    !(((test && root.matches(Regex("http://127\\.0\\.0\\.1:[0-9]{1,5}"))))))
            ) {
                throw IllegalArgumentException("Nicht erlaubtes Tunnel-Ziel.")
            }
            PixelTunnelConfig.validateOrganization(organization)
            this.root = root
            this.secret = secret
            this.organization = organization
        }

        fun close(): Unit {

            var current = active
            if ((current != null)) {
                current.disconnect()
            }
        }

        override @Throws(Exception::class) fun request(
            method: String,
            path: String,
            body: JSONObject?,
            shard: String?,
            timeoutMs: Int,
        ): Reply {

            if ((!(path.startsWith("/v1/tunnels/")) || path.contains(".."))) {
                throw IOException("Ungültiger Tunnel-Pfad.")
            }
            var connection = (URL((root + path)).openConnection() as HttpURLConnection)
            active = connection
            try {

                connection.setInstanceFollowRedirects(false)
                connection.setConnectTimeout(Math.min(timeoutMs, 15000))
                connection.setReadTimeout(timeoutMs)
                connection.setRequestMethod(method)
                connection.setRequestProperty("Authorization", ("Bearer " + secret))
                connection.setRequestProperty("Accept", "application/json")
                if (!(organization.isEmpty())) {
                    connection.setRequestProperty("OpenAI-Organization", organization)
                }
                connection.setRequestProperty("X-Tunnel-Client-Name", "hausgarten-android")
                connection.setRequestProperty("X-Tunnel-Client-Version", "0.1.0")
                connection.setRequestProperty("X-Tunnel-Client-Instance-Id", instance)
                connection.setRequestProperty(
                    "X-Tunnel-MCP-Server-Info",
                    "{\"version\":1,\"channels\":[{\"name\":\"main\",\"proc_affinity\":true}]}",
                )
                if ((shard != null)) {
                    connection.setRequestProperty("X-Tunnel-Shard-Token", shard)
                }
                if ((body != null)) {
                    connection.setDoOutput(true)
                    connection.setRequestProperty("Content-Type", "application/json")
                    var bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.getOutputStream().use { out -> out.write(bytes) }
                }
                var code: Int = connection.getResponseCode()
                var receipt: Long = System.nanoTime()
                if ((code == 204)) {
                    return Reply(code, JSONObject(), receipt)
                }
                if (((code < 200) || (code >= 300))) {
                    var safe = JSONObject()
                    if ((code == 429)) {
                        safe.put(
                            "retry_after_ms",
                            retryAfter(
                                connection.getHeaderField("Retry-After"),
                                System.currentTimeMillis(),
                            ),
                        )
                    }
                    if (((code == 401) || (code == 403))) {
                        safe.put("access_reason", readAccessReason(connection))
                    }
                    return Reply(code, safe, receipt)
                }
                connection.getInputStream().use { input ->
                    ByteArrayOutputStream().use { out ->
                        var buffer = ByteArray(8192)
                        var count: Int
                        var total: Int = 0
                        while (true) {
                            count = input.read(buffer)
                            if (count == -1) break
                            total += count
                            if ((total > ((2 * 1024) * 1024))) {
                                throw IOException("Tunnel-Antwort zu groß.")
                            }
                            out.write(buffer, 0, count)
                        }
                        var value = String(out.toByteArray(), StandardCharsets.UTF_8)
                        return Reply(
                            code,
                            (if (value.trim().isEmpty()) JSONObject() else JSONObject(value)),
                            receipt,
                        )
                    }
                }
            } finally {
                connection.disconnect()
                if ((active == connection)) {
                    active = null
                }
            }
        }

        companion object {
            private @JvmStatic fun readAccessReason(connection: HttpURLConnection): String {

                try {
                    connection.getErrorStream().use { input ->
                        ByteArrayOutputStream().use { out ->
                            if ((input == null)) {
                                return "auth_denied"
                            }
                            var buffer = ByteArray(4096)
                            var count: Int
                            var total: Int = 0
                            while (true) {
                                count = input.read(buffer)
                                if (count == -1) break
                                total += count
                                if ((total > 65536)) {
                                    return "auth_denied"
                                }
                                out.write(buffer, 0, count)
                            }
                            return accessReason(
                                JSONObject(String(out.toByteArray(), StandardCharsets.UTF_8))
                            )
                        }
                    }
                } catch (ignored: Exception) {
                    return "auth_denied"
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun retryAfter(value: String?, now: Long): Long {

            if ((value == null)) {
                return 15000
            }
            try {

                return Math.max(
                    1000,
                    Math.min(
                        900000,
                        Math.multiplyExact(java.lang.Long.parseLong(value.trim()), 1000L),
                    ),
                )
            } catch (ignored: Exception) {
                try {

                    return Math.max(
                        1000,
                        Math.min(
                            900000,
                            (java.time.ZonedDateTime.parse(
                                    value,
                                    java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME,
                                )
                                .toInstant()
                                .toEpochMilli() - now),
                        ),
                    )
                } catch (invalid: Exception) {
                    return 15000
                }
            }
        }

        private @JvmStatic @Throws(Exception::class) fun check(r: Reply): Unit {

            if ((r.code == 429)) {
                throw RateLimit(r.json.optLong("retry_after_ms", 15000))
            }
            if (((r.code == 401) || (r.code == 403))) {
                throw AccessError(r.code, r.json.optString("access_reason", "auth_denied"))
            }
            if (((r.code < 200) || (r.code >= 300))) {
                throw IOException(
                    (("Tunnel vorübergehend nicht erreichbar (HTTP " + r.code) + ").")
                )
            }
        }

        @JvmStatic
        fun deadline(timeout: Any?, receipt: Long): Long {

            if (
                (!((timeout is String)) ||
                    !(((timeout as String)).matches(Regex("[0-9]+(?:ns|us|ms|s|m|h)"))))
            ) {
                return Long.MAX_VALUE
            }
            var m = java.util.regex.Pattern.compile("([0-9]+)([a-z]+)").matcher((timeout as String))
            m.matches()
            try {

                var amount: Long = java.lang.Long.parseLong(m.group(1)!!)
                var factor: Long
                when (m.group(2)) {
                    "ns" -> {
                        factor = 1
                    }
                    "us" -> {
                        factor = 1000
                    }
                    "ms" -> {
                        factor = 1000000
                    }
                    "s" -> {
                        factor = 1000000000
                    }
                    "m" -> {
                        factor = 60000000000L
                    }
                    else -> {
                        factor = 3600000000000L
                    }
                }
                return Math.addExact(receipt, Math.multiplyExact(amount, factor))
            } catch (invalid: Exception) {
                return Long.MAX_VALUE
            }
        }

        private @JvmStatic @Throws(Exception::class) fun canonical(value: Any?): String {

            if (value is JSONObject) {
                var obj = (value as JSONObject)
                var keys = ArrayList<String>()
                var iterator = obj.keys()
                while (iterator.hasNext()) {
                    keys.add(iterator.next())
                }
                Collections.sort(keys)
                var out = StringBuilder("{")
                for (key in keys) {
                    out.append(JSONObject.quote(key))
                        .append(':')
                        .append(canonical(obj.get(key)))
                        .append(',')
                }
                return out.append('}').toString()
            }
            if (value is JSONArray) {
                var array = (value as JSONArray)
                var out = StringBuilder("[")
                run {
                    var i: Int = 0
                    var firstIteration113 = true
                    while (true) {
                        if (firstIteration113) firstIteration113 = false
                        else {
                            i++
                        }
                        if (!((i < array.length()))) break

                        out.append(canonical(array.get(i))).append(',')
                    }
                }
                return out.append(']').toString()
            }
            return (if (((value == null) || (value == JSONObject.NULL))) "null"
            else (if (value is String) JSONObject.quote((value as String)) else value.toString()))
        }

        @JvmStatic
        fun accessReason(json: JSONObject): String {

            var error = json.optJSONObject("error")
            var message =
                ((((java.lang.String.valueOf(
                    ((if ((error == null)) "" else error.optString("message")))
                ) + " ") + json.optString("detail")) + " ") + json.optString("message"))
            if (json.opt("error") is String) {
                message += (" " + json.optString("error"))
            }
            message = message.lowercase(Locale.ROOT)
            if (
                message.contains("openai-organization header should match organization for api key")
            ) {
                return "organization_mismatch"
            }
            if (message.contains("requires an active organization context")) {
                return "organization_required"
            }
            if (message.contains("does not have permission to use this tunnel")) {
                return "tunnel_access_denied"
            }
            if (((error != null) && ("invalid_api_key" == error.optString("code")))) {
                return "invalid_api_key"
            }
            return "auth_denied"
        }
    }
}

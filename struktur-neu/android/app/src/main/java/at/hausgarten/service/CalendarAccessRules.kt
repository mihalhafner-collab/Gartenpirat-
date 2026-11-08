package at.hausgarten.service

import java.util.*
import org.json.*

/** Fixed diagnostic vocabulary only. Never exposes Google bodies, accounts or tokens. */
internal object CalendarAccessRules {
    const val BASE_SCOPE =
        "https://www.googleapis.com/auth/calendar.app.created https://www.googleapis.com/auth/calendar.calendarlist.readonly"
    const val BUSY_SCOPE = "https://www.googleapis.com/auth/calendar.freebusy"

    @JvmStatic
    fun scope(busy: Boolean): String = "oauth2:" + BASE_SCOPE + (if (busy) " " + BUSY_SCOPE else "")

    @JvmStatic
    fun requestedScopes(busy: Boolean): List<String> =
        Collections.unmodifiableList(
            BASE_SCOPE.split(" ").toMutableList().apply { if (busy) add(BUSY_SCOPE) }
        )

    @JvmStatic
    @Throws(Exception::class)
    fun bindingHash(account: String, calendar: String, busy: Boolean): String =
        PixelMcpWriteTools.hash(
            JSONObject().put("account", account).put("calendar", calendar).put("busy", busy)
        )

    @JvmStatic
    fun grantCode(busy: Boolean, token: String?, granted: Collection<String>?): String {
        if (
            token.isNullOrEmpty() || granted == null || !granted.containsAll(requestedScopes(false))
        )
            return "CALENDAR_AUTH_REQUIRED"
        return if (busy && !granted.contains(BUSY_SCOPE)) "CALENDAR_BUSY_PERMISSION_REQUIRED"
        else ""
    }

    @JvmStatic
    fun googleStatus(status: Int): String =
        when (status) {
            4,
            5 -> "CALENDAR_ACCOUNT_ACCESS_REQUIRED"
            7,
            15,
            20,
            21,
            22 -> "CALENDAR_TOKEN_NETWORK"
            10 -> "CALENDAR_APP_REGISTRATION_REQUIRED"
            16,
            12501 -> "CALENDAR_CONSENT_CANCELLED"
            17,
            18 -> "CALENDAR_GOOGLE_SERVICES_REQUIRED"
            else -> "CALENDAR_GOOGLE_AUTH_ERROR"
        }

    private val CODES =
        setOf(
            "CALENDAR_AUTH_REQUIRED",
            "CALENDAR_API_DISABLED",
            "CALENDAR_RATE_LIMIT",
            "CALENDAR_RETRY",
            "CALENDAR_APP_REGISTRATION_REQUIRED",
            "CALENDAR_GOOGLE_AUTH_ERROR",
            "CALENDAR_ACCOUNT_ACCESS_REQUIRED",
            "CALENDAR_CONSENT_CANCELLED",
            "CALENDAR_INTERRUPTED",
            "CALENDAR_AUTH_RESULT_MISSING",
            "CALENDAR_GOOGLE_SERVICES_REQUIRED",
            "CALENDAR_SCOPE_UNSUPPORTED",
            "CALENDAR_TOKEN_NETWORK",
            "CALENDAR_NETWORK",
            "CALENDAR_REQUEST_REJECTED",
            "CALENDAR_CREATE_PENDING",
            "CALENDAR_READBACK_PENDING",
            "CALENDAR_DUPLICATE",
            "CALENDAR_OWNER_REQUIRED",
            "CALENDAR_SETTINGS_CHANGED",
            "CALENDAR_REPLY_INVALID",
            "CALENDAR_BUSY_PERMISSION_REQUIRED",
            "CALENDAR_UNKNOWN",
        )

    @JvmStatic
    fun safeCode(value: String?): String =
        if (value == "") "" else if (value in CODES) value!! else "CALENDAR_UNKNOWN"

    @JvmStatic
    fun phase(value: String?): String =
        if (
            value in
                listOf(
                    "grant",
                    "calendar_lookup",
                    "calendar_create",
                    "calendar_readback",
                    "settings_save",
                )
        )
            value!!
        else "grant"

    @JvmStatic
    fun phaseLabel(value: String?): String =
        when (phase(value)) {
            "calendar_lookup" -> "App-Kalender suchen"
            "calendar_create" -> "App-Kalender anlegen"
            "calendar_readback" -> "App-Kalender bestätigen"
            "settings_save" -> "Verbindung speichern"
            else -> "Google-Freigabe"
        }

    @JvmStatic
    fun message(value: String?): String =
        when (safeCode(value)) {
            "CALENDAR_AUTH_REQUIRED" ->
                "Google hat die Kalenderfreigabe nicht bestätigt. Erneut verbinden und die Freigabe am Pixel bestätigen."
            "CALENDAR_API_DISABLED" ->
                "Die Google Calendar API ist für die App nicht freigeschaltet. Die App-Einrichtung im Google-Projekt muss korrigiert werden; wiederholtes Antippen hilft hier nicht."
            "CALENDAR_APP_REGISTRATION_REQUIRED" ->
                "Google hat die Anmeldung dieser App abgewiesen. Das kann bereits vor dem Berechtigungsdialog passieren. Die App-Freigabe muss im zugehörigen Google-Projekt korrigiert werden; erneutes Bestätigen allein hilft hier nicht."
            "CALENDAR_GOOGLE_AUTH_ERROR" ->
                "Google konnte die Kalenderanmeldung nicht abschließen. Google-Konto erneut wählen. Erscheint keine Freigabe, muss die App-Einrichtung bei Google geprüft werden."
            "CALENDAR_GOOGLE_SERVICES_REQUIRED" ->
                "Die Google-Play-Dienste sind nicht bereit. Am Pixel aktualisieren und die Kalenderfreigabe anschließend erneut starten."
            "CALENDAR_SCOPE_UNSUPPORTED" ->
                "Google hat die angefragten Kalenderrechte abgewiesen. Die Kalenderfreigabe der App muss bei Google geprüft werden."
            "CALENDAR_ACCOUNT_ACCESS_REQUIRED" ->
                "Das gewählte Google-Konto ist für die App nicht zugänglich. Unter Konto und Freigaben erneut auswählen."
            "CALENDAR_CONSENT_CANCELLED" ->
                "Die Google-Freigabe wurde nicht abgeschlossen. Erneut verbinden. Falls du nicht abgebrochen hast, Konto und Google-App-Einrichtung prüfen."
            "CALENDAR_AUTH_RESULT_MISSING" ->
                "Google hat kein auswertbares Freigabeergebnis zurückgegeben. Erneut verbinden; bei Wiederholung Google-Konto und App-Einrichtung prüfen."
            "CALENDAR_INTERRUPTED" ->
                "Die Kalenderprüfung wurde technisch unterbrochen. Bei bestehender Verbindung folgt ein erneuter Abgleich; die Einrichtung kann erneut gestartet werden."
            "CALENDAR_RATE_LIMIT" ->
                "Google begrenzt gerade die Kalenderzugriffe. Später erneut versuchen; keine neue Kontofreigabe nötig."
            "CALENDAR_RETRY" ->
                "Google Kalender ist vorübergehend nicht verfügbar. Später erneut versuchen."
            "CALENDAR_TOKEN_NETWORK",
            "CALENDAR_NETWORK" ->
                "Google ist derzeit nicht erreichbar. Internetverbindung prüfen und erneut versuchen."
            "CALENDAR_REQUEST_REJECTED" ->
                "Google hat die Kalenderanlage abgewiesen. Es wurde keine erfolgreiche Anlage bestätigt. Die App-Einrichtung muss geprüft werden."
            "CALENDAR_CREATE_PENDING" ->
                "Eine frühere Kalenderanlage ist noch unbestätigt. Google Kalender aktualisieren und erneut verbinden. Die App legt bis zur Klärung keinen zweiten Kalender an."
            "CALENDAR_READBACK_PENDING" ->
                "Der App-Kalender ist noch nicht vollständig bestätigt. Später erneut verbinden; die bekannte Kalenderzuordnung bleibt erhalten."
            "CALENDAR_DUPLICATE" ->
                "Mehrere eigene App-Kalender gefunden. Zuordnung prüfen; es wird kein weiterer Kalender angelegt."
            "CALENDAR_OWNER_REQUIRED" ->
                "Der gefundene App-Kalender gehört nicht dem gewählten Konto. Das ursprüngliche Google-Konto wählen."
            "CALENDAR_SETTINGS_CHANGED" ->
                "Die Planungseinstellungen wurden inzwischen geändert. Diese Ansicht erneut öffnen; die bisherige Verbindung bleibt erhalten."
            "CALENDAR_BUSY_PERMISSION_REQUIRED" ->
                "Die zusätzliche Freigabe für Belegtzeiten fehlt. Alle angefragten Kalenderrechte bestätigen oder bewusst nur den App-Kalender verbinden."
            "CALENDAR_REPLY_INVALID" ->
                "Google hat keine vollständig lesbare Kalenderbestätigung geliefert. Später erneut prüfen; keine doppelte Kalenderanlage."
            else ->
                "Die Kalenderverbindung konnte nicht abgeschlossen werden. Erneut prüfen; der genaue Grund ist noch unbekannt."
        }

    @JvmStatic
    fun code(error: Throwable): String =
        when {
            CalendarRecoveryRules.interrupted(error) -> "CALENDAR_INTERRUPTED"
            error is HttpTransport.Failure -> safeCode(error.code)
            error is PixelMcpWriteTools.Rejected -> "CALENDAR_SETTINGS_CHANGED"
            error is JSONException -> "CALENDAR_REPLY_INVALID"
            error is java.io.IOException -> "CALENDAR_NETWORK"
            else -> "CALENDAR_UNKNOWN"
        }

    @JvmStatic
    fun authenticationCode(error: Exception): String {
        if (CalendarRecoveryRules.interrupted(error)) return "CALENDAR_INTERRUPTED"
        if (error.javaClass.simpleName == "OperationCanceledException")
            return "CALENDAR_CONSENT_CANCELLED"
        if (error is SecurityException) return "CALENDAR_ACCOUNT_ACCESS_REQUIRED"
        // Compare known machine identifiers in memory; never retain or display exception text.
        val detail = error.message.toString().uppercase(Locale.ROOT)
        if (
            listOf("UNREGISTERED_ON_API_CONSOLE", "DEVELOPER_ERROR", "INVALID_CLIENT").any {
                detail.contains(it)
            }
        )
            return "CALENDAR_APP_REGISTRATION_REQUIRED"
        if (detail.contains("INVALID_SCOPE") || detail.contains("INVALIDSCOPES"))
            return "CALENDAR_SCOPE_UNSUPPORTED"
        if (error is java.io.IOException) return "CALENDAR_TOKEN_NETWORK"
        return "CALENDAR_GOOGLE_AUTH_ERROR"
    }

    @JvmStatic
    fun missingResultCode(result: Int): String =
        if (result == 0) "CALENDAR_CONSENT_CANCELLED" else "CALENDAR_AUTH_RESULT_MISSING"

    @JvmStatic
    fun rejectedCreation(error: HttpTransport.Failure): Boolean =
        error.code in
            listOf(
                "CALENDAR_AUTH_REQUIRED",
                "CALENDAR_API_DISABLED",
                "CALENDAR_RATE_LIMIT",
                "CALENDAR_REQUEST_REJECTED",
                "CALENDAR_APP_REGISTRATION_REQUIRED",
                "CALENDAR_GOOGLE_AUTH_ERROR",
                "CALENDAR_ACCOUNT_ACCESS_REQUIRED",
                "CALENDAR_CONSENT_CANCELLED",
                "CALENDAR_GOOGLE_SERVICES_REQUIRED",
                "CALENDAR_SCOPE_UNSUPPORTED",
                "CALENDAR_BUSY_PERMISSION_REQUIRED",
                "CALENDAR_TOKEN_NETWORK",
            )

    @JvmStatic
    fun reasons(reply: HttpTransport.Reply): Set<String> {
        val reasons = HashSet<String>()
        try {
            val error = reply.json().optJSONObject("error") ?: return reasons
            for (key in arrayOf("errors", "details")) {
                val values = error.optJSONArray(key) ?: continue
                for (i in 0 until values.length()) if (values.optJSONObject(i) != null)
                    reasons.add(values.getJSONObject(i).optString("reason"))
            }
        } catch (ignored: Exception) {}
        return reasons
    }

    @JvmStatic
    fun failure(reply: HttpTransport.Reply): HttpTransport.Failure? {
        val reasons = reasons(reply)
        var user = false
        val code =
            when {
                reply.status == 401 -> {
                    user = true
                    "CALENDAR_AUTH_REQUIRED"
                }
                reply.status == 403 ->
                    when {
                        reasons.any {
                            it in listOf("accessNotConfigured", "SERVICE_DISABLED", "API_DISABLED")
                        } -> {
                            user = true
                            "CALENDAR_API_DISABLED"
                        }
                        reasons.any {
                            it in
                                listOf(
                                    "rateLimitExceeded",
                                    "userRateLimitExceeded",
                                    "quotaExceeded",
                                    "dailyLimitExceeded",
                                    "RATE_LIMIT_EXCEEDED",
                                    "QUOTA_EXCEEDED",
                                )
                        } -> "CALENDAR_RATE_LIMIT"
                        else -> {
                            user = true
                            "CALENDAR_AUTH_REQUIRED"
                        }
                    }
                reply.status == 429 -> "CALENDAR_RATE_LIMIT"
                reply.status >= 500 -> "CALENDAR_RETRY"
                else -> return null
            }
        var retry = 0L
        if (!user) {
            retry = 30000
            try {
                retry = (reply.header("Retry-After").toLong() * 1000).coerceIn(retry, 3600000)
            } catch (absent: Exception) {
                try {
                    retry =
                        java.time.Duration.between(
                                java.time.Instant.now(),
                                java.time.ZonedDateTime.parse(
                                        reply.header("Retry-After"),
                                        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME,
                                    )
                                    .toInstant(),
                            )
                            .toMillis()
                            .coerceIn(retry, 3600000)
                } catch (ignored: Exception) {}
            }
        }
        return HttpTransport.Failure(code, message(code), retry, user)
    }
}

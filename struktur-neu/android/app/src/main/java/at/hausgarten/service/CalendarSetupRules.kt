package at.hausgarten.service

/** Presentation of observed setup steps; account selection never grants Calendar access. */
internal object CalendarSetupRules {
    @JvmStatic
    fun grantVerified(existingGrant: Boolean, stage: String?, code: String?): Boolean {
        if (
            code in
                listOf(
                    "CALENDAR_APP_REGISTRATION_REQUIRED",
                    "CALENDAR_AUTH_REQUIRED",
                    "CALENDAR_BUSY_PERMISSION_REQUIRED",
                    "CALENDAR_SCOPE_UNSUPPORTED",
                    "CALENDAR_ACCOUNT_ACCESS_REQUIRED",
                    "CALENDAR_CONSENT_CANCELLED",
                    "CALENDAR_AUTH_RESULT_MISSING",
                    "CALENDAR_GOOGLE_AUTH_ERROR",
                )
        )
            return false
        return existingGrant ||
            stage in
                listOf("calendar_lookup", "calendar_create", "calendar_readback", "settings_save")
    }

    @JvmStatic
    fun progress(
        accountSelected: Boolean,
        existingGrant: Boolean,
        calendarConfirmed: Boolean,
        stage: String?,
        code: String?,
        waitingForGoogle: Boolean,
    ): String =
        "1 Konto " +
            (if (accountSelected) "gewählt" else "offen") +
            " · 2 Freigabe " +
            (if (waitingForGoogle) "bei Google bestätigen"
            else if (grantVerified(existingGrant, stage, code)) "erteilt" else "ausstehend") +
            " · 3 Kalender " +
            (if (calendarConfirmed) "bestätigt" else "offen")

    @JvmStatic
    fun registrationChecklist(packageName: String, sha1: String): String =
        "Google-Projekt prüfen: Calendar API aktiviert; Android-OAuth-Client für " +
            packageName +
            "; Originalsignatur SHA-1: " +
            sha1 +
            ". Veröffentlichungsstatus und nötige Verifizierung prüfen: Ein externer Testbetrieb ist kein dauerhafter Zugang. Danach die Google-Kalenderfreigabe auf dem Pixel bestätigen."
}

package at.hausgarten.service

import android.accounts.AccountManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.*
import org.json.*

/** Existing device consent, explicit account setup and immediately saved switches. */
class PlanningConnectionActivity : ComponentActivity() {
    private val accountChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            activityResult(ACCOUNT_REQUEST, it.resultCode, it.data)
        }
    private val googleConsent =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            activityResult(GOOGLE_AUTH_REQUEST, it.resultCode, it.data)
        }
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    PlanningJob.schedule(this@PlanningConnectionActivity, 1000)
                }
            }
            status =
                if (WeatherNotifications.ready(this))
                    "Pixel-Meldungen sind freigegeben. Testmeldung möglich."
                else
                    "Pixel-Meldungen noch nicht freigegeben. Warnungen bleiben in der App sichtbar."
            refreshPresentation()
        }
    private var status by mutableStateOf("")
    private var loaded by mutableStateOf(false)
    private var working by mutableStateOf(false)
    private var account by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var calendar by mutableStateOf(false)
    private var weather by mutableStateOf(false)
    private var notifications by mutableStateOf(false)
    private var from by mutableStateOf("08:00")
    private var to by mutableStateOf("17:00")
    private var travel by mutableStateOf("0")
    private val days = mutableStateListOf<Boolean>()
    private val thresholds =
        mutableStateMapOf(
            "rain_mm" to "1",
            "rain_probability" to "60",
            "gust_kmh" to "40",
            "frost_c" to "0",
            "heat_c" to "30",
        )
    private var presentation by mutableStateOf(Presentation())
    private var disconnectConfirmation by mutableStateOf(false)
    private var waitingForGoogle by mutableStateOf(false)
    @Volatile private var setupStage = "grant"
    @Volatile private var connectionGeneration = 0L
    private var pendingGoogleBusy = false
    private var pendingGoogleAccount = ""
    private var openedCalendarBinding = ""
    private var openedSettingsHash = ""
    private var connectAfterAccount = false
    private var presentationGeneration = 0L
    private var accountDisclosure by mutableStateOf(false)
    private var technicalDisclosure by mutableStateOf(false)
    private var notificationDisclosure by mutableStateOf(false)
    private var advancedDisclosure by mutableStateOf(false)
    private var weatherSourceDisclosure by mutableStateOf(false)

    private data class Presentation(
        val connected: Boolean = false,
        val enabled: Boolean = false,
        val state: String = "pending",
        val setupCode: String = "",
        val existingGrant: Boolean = false,
        val accountLabel: String = "",
        val steps: String = "1 Konto · 2 Freigabe · 3 Kalender",
        val details: String = "",
        val weather: String = "",
        val action: String = "Verbinden",
        val label: String = "Prüfung nötig",
        val initialStatus: String = "",
        val weatherEnabled: Boolean = false,
        val notificationsEnabled: Boolean = false,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Kalender & Wetter"
        window.nativeLightSystemBars()
        setContent {
            HausGartenTheme {
                Scaffold(
                    modifier = Modifier.safeDrawingPadding().imePadding(),
                    bottomBar = {
                        NativeActionBar(
                            presentation.action,
                            { calendarPrimary() },
                            "Zurück",
                            { onBackPressedDispatcher.onBackPressed() },
                            enabled = loaded && !working,
                        )
                    },
                ) { insets ->
                    Column(
                        Modifier.fillMaxSize()
                            .padding(insets)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Kalender & Wetter", style = MaterialTheme.typography.headlineSmall)
                        NativeStatus(
                            presentation.label,
                            if (presentation.setupCode.isEmpty()) NativeStatusTone.Neutral
                            else NativeStatusTone.Attention,
                        )

                        if (status.isNotEmpty()) Text(status)
                        if (loaded)
                            Action("Status prüfen") {
                                status = ""
                                refreshPresentation()
                            }
                        if (!loaded) {
                            if (status.isEmpty()) CircularProgressIndicator()
                        } else {
                            Section("Google Kalender") {
                                if (presentation.setupCode.isNotEmpty())
                                    Text(
                                        CalendarAccessRules.message(presentation.setupCode),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                if (presentation.setupCode == "CALENDAR_BUSY_PERMISSION_REQUIRED")
                                    Action("Kalender ohne Belegtzeiten verbinden") {
                                        busy = false
                                        beginCalendarSetup()
                                    }

                                Toggle("Google Kalender verwenden", calendar) { enabled ->
                                    if (enabled && !presentation.connected) beginCalendarSetup()
                                    else saveCalendarSwitch(enabled)
                                }
                                Action("Kalender-App öffnen") { openCalendarApp() }
                                Disclosure(
                                    "Konto und Freigaben",
                                    accountDisclosure,
                                    { accountDisclosure = !accountDisclosure },
                                ) {
                                    Text(presentation.accountLabel)
                                    Action("Google-Konto wählen oder wechseln") {
                                        connectAfterAccount = true
                                        chooseAccount()
                                    }
                                    Action("Kalenderverbindung trennen") {
                                        disconnectConfirmation = true
                                    }
                                }
                                Toggle("Belegtzeiten prüfen", busy) { busy = it }

                                Action("Kalenderfreigaben übernehmen / erneuern") {
                                    beginCalendarSetup()
                                }
                                Disclosure(
                                    "Technische Details",
                                    technicalDisclosure,
                                    { technicalDisclosure = !technicalDisclosure },
                                ) {
                                    Text(
                                        presentation.details,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Action("Einrichtungshinweise bei Google öffnen") {
                                        try {
                                            startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(
                                                        "https://developer.android.com/identity/authorization"
                                                    ),
                                                )
                                            )
                                        } catch (unavailable: Exception) {
                                            status =
                                                "Google-Einrichtungshinweise derzeit nicht erreichbar."
                                        }
                                    }
                                }
                            }
                            Section("Wetter & Pixel") {
                                Text(presentation.weather)
                                Toggle("Wetterwarnungen", weather) {
                                    saveWeatherSwitch("weather_enabled", it)
                                }
                                Toggle("Pixel-Benachrichtigungen", notifications) {
                                    saveWeatherSwitch("notifications", it)
                                }

                                Action("Kalender abgleichen und Wetter aktualisieren") {
                                    checkPlanning()
                                }
                                Disclosure(
                                    "Wetterquellen und Nutzungsbedingungen",
                                    weatherSourceDisclosure,
                                    { weatherSourceDisclosure = !weatherSourceDisclosure },
                                ) {
                                    Text(
                                        "Hauptanbieter: Open-Meteo, öffentlicher Gratis-Endpunkt für nichtkommerzielle API-Nutzung. CC BY 4.0 der Wetterdaten bestätigt keine gewerbliche Nutzung dieses API-Dienstes. Gewerbliche API-Nutzung ungeklärt; Bedingungen: https://open-meteo.com/en/terms. GeoSphere Austria liefert kurzfristige Ersatzvorhersagen und amtliche Warnungen. GPS nur auf Wunsch.",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                Disclosure(
                                    "Pixel-Meldungen testen oder freigeben",
                                    notificationDisclosure,
                                    { notificationDisclosure = !notificationDisclosure },
                                ) {
                                    Action("Pixel-Testmeldung anzeigen") {
                                        try {
                                            if (
                                                !WeatherNotifications.test(
                                                    this@PlanningConnectionActivity
                                                )
                                            ) {
                                                requestNotificationPermission()
                                                status =
                                                    "Testmeldung nicht gesendet. Pixel-Benachrichtigungen freigeben."
                                            } else
                                                status =
                                                    "Testmeldung an Pixel übergeben. In der Benachrichtigungsleiste prüfen."
                                        } catch (blocked: Exception) {
                                            status =
                                                "Testmeldung nicht gesendet. Pixel-Einstellungen prüfen."
                                        }
                                        refreshPresentation()
                                    }
                                    Action("Pixel-Benachrichtigungen einstellen") {
                                        notificationSettings()
                                    }
                                }
                            }
                            Disclosure(
                                "Arbeitszeiten und Wettergrenzen",
                                advancedDisclosure,
                                { advancedDisclosure = !advancedDisclosure },
                            ) {
                                Text("Arbeitszeiten für Terminvorschläge")
                                val labels =
                                    arrayOf(
                                        "Montag",
                                        "Dienstag",
                                        "Mittwoch",
                                        "Donnerstag",
                                        "Freitag",
                                        "Samstag",
                                        "Sonntag",
                                    )
                                for (i in labels.indices) Toggle(
                                    labels[i],
                                    days.getOrElse(i) { false },
                                ) {
                                    days[i] = it
                                }
                                NativeField(
                                    "Arbeitsbeginn (HH:mm)",
                                    from,
                                    { from = it },
                                    enabled = !working,
                                )
                                NativeField(
                                    "Arbeitsende (HH:mm)",
                                    to,
                                    { to = it },
                                    enabled = !working,
                                )
                                NativeField(
                                    "Anfahrtpuffer in Minuten",
                                    travel,
                                    { travel = it },
                                    enabled = !working,
                                )
                                Text(
                                    "Betriebliche Wetterschwellen für Hinweise; keine Sicherheitsfreigabe. Fehlende Wetterdaten bleiben unbekannt."
                                )
                                for ((key, label) in
                                    arrayOf(
                                        "rain_mm" to "Regen mm/h",
                                        "rain_probability" to "Regenwahrscheinlichkeit %",
                                        "gust_kmh" to "Böen km/h",
                                        "frost_c" to "Frost °C",
                                        "heat_c" to "Hitze °C",
                                    )) NativeField(
                                    label,
                                    thresholds[key] ?: "",
                                    { thresholds[key] = it },
                                    enabled = !working,
                                )
                                Action("Arbeitszeiten und Wettergrenzen speichern") {
                                    saveAdvanced()
                                }
                            }
                        }
                    }
                }
                if (disconnectConfirmation)
                    AlertDialog(
                        onDismissRequest = { disconnectConfirmation = false },
                        text = {
                            Text(
                                "Nur Verbindung trennen? App-Termine und Google-Einträge bleiben erhalten."
                            )
                        },
                        dismissButton = {
                            NativeTextButton(onClick = { disconnectConfirmation = false }) {
                                Text("Abbrechen")
                            }
                        },
                        confirmButton = {
                            NativeTextButton(
                                onClick = {
                                    disconnectConfirmation = false
                                    disconnect()
                                }
                            ) {
                                Text("Trennen")
                            }
                        },
                    )
            }
        }
        lifecycleScope.launch {
            try {
                val initial =
                    withContext(Dispatchers.IO) {
                        RevierApplication.awaitStartup(this@PlanningConnectionActivity)
                        PixelLocalStore(this@PlanningConnectionActivity).use { store ->
                            val settings =
                                AppointmentRuntime.publicSettings(
                                    this@PlanningConnectionActivity,
                                    store,
                                )
                            Triple(
                                settings,
                                PixelMcpWriteTools.hash(settings),
                                PlanningAccount.bindingHash(this@PlanningConnectionActivity),
                            )
                        }
                    }
                openedSettingsHash = initial.second
                openedCalendarBinding = initial.third
                val preferences = PlanningAccount.prefs(this@PlanningConnectionActivity)
                account =
                    savedInstanceState?.getString("selected_calendar_account")
                        ?: preferences.getString("account", "")
                        ?: ""
                busy =
                    savedInstanceState?.getBoolean(
                        "edited_busy",
                        preferences.getBoolean("read_busy", false),
                    ) ?: preferences.getBoolean("read_busy", false)
                val settings = initial.first
                weather = settings.optBoolean("weather_enabled")
                notifications = settings.optBoolean("notifications")
                from =
                    savedInstanceState?.getString("edited_from")
                        ?: settings.optString("day_start", "08:00")
                to =
                    savedInstanceState?.getString("edited_to")
                        ?: settings.optString("day_end", "17:00")
                travel =
                    savedInstanceState?.getString("edited_travel")
                        ?: settings.optInt("travel_minutes").toString()
                val configured = settings.optJSONArray("workdays")
                val savedDays = savedInstanceState?.getBooleanArray("edited_days")
                days.clear()
                for (day in 1..7) days.add(
                    savedDays?.getOrNull(day - 1)
                        ?: (0 until (configured?.length() ?: 0)).any {
                            configured!!.optInt(it) == day
                        }
                )
                for (key in thresholds.keys.toList()) thresholds[key] =
                    savedInstanceState?.getString("edited_$key")
                        ?: if (settings.has(key)) settings.optString(key) else thresholds[key]!!
                accountDisclosure = savedInstanceState?.getBoolean("account_disclosure") ?: false
                technicalDisclosure =
                    savedInstanceState?.getBoolean("technical_disclosure") ?: false
                notificationDisclosure =
                    savedInstanceState?.getBoolean("notification_disclosure") ?: false
                advancedDisclosure = savedInstanceState?.getBoolean("advanced_disclosure") ?: false
                if (savedInstanceState?.getBoolean("waiting_for_google", false) == true) {
                    pendingGoogleAccount =
                        savedInstanceState.getString("pending_calendar_account", "") ?: ""
                    pendingGoogleBusy =
                        savedInstanceState.getBoolean("pending_calendar_busy", false)
                    if (pendingGoogleAccount.isNotEmpty()) {
                        waitingForGoogle = true
                        account = pendingGoogleAccount
                        busy = pendingGoogleBusy
                        openedSettingsHash =
                            savedInstanceState.getString(
                                "calendar_settings_hash",
                                openedSettingsHash,
                            ) ?: openedSettingsHash
                        openedCalendarBinding =
                            savedInstanceState.getString(
                                "calendar_binding_hash",
                                openedCalendarBinding,
                            ) ?: openedCalendarBinding
                        working = true
                        status = "Google-Freigabe bestätigen. Danach wird die Verbindung geprüft."
                    }
                }
                loaded = true
                refreshPresentation()
                queuedActivityResult?.let {
                    queuedActivityResult = null
                    handleActivityResult(it.first, it.second, it.third)
                }
                if (
                    !waitingForGoogle &&
                        !(preferences.getString("calendar_setup_code", "") ?: "").isEmpty()
                )
                    status = "Verbindung nicht abgeschlossen. Bitte erneut verbinden."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unavailable: Exception) {
                status = "Einstellungen derzeit nicht lesbar. Keine Änderung gespeichert."
            }
        }
    }

    @Composable
    private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HorizontalDivider()
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }

    @Composable
    private fun Toggle(label: String, checked: Boolean, action: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f))
            Switch(checked, action, enabled = !working)
        }
    }

    @Composable
    private fun Action(label: String, action: () -> Unit) {
        NativeOutlinedButton(
            onClick = action,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label)
        }
    }

    @Composable
    private fun Disclosure(
        title: String,
        expanded: Boolean,
        change: () -> Unit,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        NativeTextButton(onClick = change, modifier = Modifier.fillMaxWidth()) { Text(title) }
        if (expanded) Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }

    private fun refreshPresentation() {
        if (!loaded) return
        val generation = ++presentationGeneration
        val selected = account
        val waiting = waitingForGoogle
        val stage = setupStage
        lifecycleScope.launch {
            val next =
                try {
                    withContext(Dispatchers.IO) {
                        PixelLocalStore(this@PlanningConnectionActivity).use { store ->
                            val context = this@PlanningConnectionActivity
                            val preferences = PlanningAccount.prefs(context)
                            val connected = PlanningAccount.connected(context)
                            val enabled = AppointmentRuntime.calendarEnabled(context, store)
                            val state = store.ownerValue("calendar:status", "pending")
                            var label =
                                when {
                                    !connected -> "Nicht verbunden"
                                    !enabled -> "Pausiert"
                                    state == "authorization_required" -> "Google-Freigabe nötig"
                                    state == "review_required" -> "Kalenderänderung prüfen"
                                    state == "checked" -> "Verbunden · letzter Abgleich geprüft"
                                    state == "retry_pending" ->
                                        "Verbunden · erneuter Abgleich folgt"
                                    else -> "Verbunden · Abgleich ausstehend"
                                }
                            val last = store.ownerValue("calendar:last_success_at", "")
                            if (
                                enabled &&
                                    state == "checked" &&
                                    !CalendarRecoveryRules.fresh(last, Instant.now())
                            )
                                label =
                                    if (last.isEmpty())
                                        "Verbunden · letzter Prüfzeitpunkt unbekannt"
                                    else "Verbunden · erneute Prüfung ausstehend"
                            val setupCode =
                                CalendarAccessRules.safeCode(
                                    preferences.getString("calendar_setup_code", "")
                                )
                            val needsReview =
                                setupCode.isNotEmpty() ||
                                    state in
                                        listOf(
                                            "authorization_required",
                                            "review_required",
                                            "retry_limit_reached",
                                        )
                            val existingGrant =
                                connected &&
                                    !preferences.getBoolean("calendar_blocked", false) &&
                                    state != "authorization_required"
                            val connectedAccount = preferences.getString("account", "") ?: ""
                            val sameAccount = selected == connectedAccount
                            val steps =
                                CalendarSetupRules.progress(
                                    selected.isNotEmpty(),
                                    existingGrant && sameAccount,
                                    connected && sameAccount,
                                    if (setupCode.isEmpty()) stage
                                    else preferences.getString("calendar_setup_stage", "grant"),
                                    setupCode,
                                    waiting,
                                )
                            val accountLabel =
                                if (connected)
                                    "Verbundenes Konto: " +
                                        connectedAccount +
                                        (if (sameAccount) "" else "\nAusgewählt: " + selected)
                                else if (selected.isEmpty()) "Konto wird beim Verbinden ausgewählt."
                                else "Ausgewähltes Konto: " + selected + "\nNoch nicht verbunden."
                            val rawCode = preferences.getString("calendar_setup_code", "") ?: ""
                            val details =
                                StringBuilder(
                                    if (rawCode.isEmpty())
                                        "Keine fehlgeschlagene Einrichtung gespeichert."
                                    else
                                        "Prüfschritt: " +
                                            CalendarAccessRules.phaseLabel(
                                                preferences.getString(
                                                    "calendar_setup_stage",
                                                    "grant",
                                                )
                                            ) +
                                            "\nHinweiscode: " +
                                            CalendarAccessRules.safeCode(rawCode)
                                )
                            if (last.isNotEmpty())
                                details
                                    .append("\nLetzte erfolgreiche Prüfung: ")
                                    .append(calendarTime(last))
                            val signingSha1 = CalendarAuthorization.signingSha1(context)
                            details
                                .append("\nAndroid-OAuth-Paket: ")
                                .append(packageName)
                                .append("\nSignatur-SHA-1: ")
                                .append(signingSha1)
                            if (preferences.contains("calendar_setup_result_code"))
                                details
                                    .append("\nDialog-Rückgabecode: ")
                                    .append(preferences.getInt("calendar_setup_result_code", 0))
                            val attempted =
                                preferences.getString("calendar_setup_attempt_at", "") ?: ""
                            if (attempted.isNotEmpty())
                                details
                                    .append("\nEinrichtungsversuch: ")
                                    .append(calendarTime(attempted))
                            val retry = preferences.getString("calendar_retry_at", "") ?: ""
                            if (retry.isNotEmpty())
                                details
                                    .append("\nErneuter Versuch frühestens: ")
                                    .append(calendarTime(retry))
                            if (setupCode == "CALENDAR_APP_REGISTRATION_REQUIRED")
                                details
                                    .append("\n")
                                    .append(
                                        CalendarSetupRules.registrationChecklist(
                                            packageName,
                                            signingSha1,
                                        )
                                    )
                            val action =
                                if (setupCode.isNotEmpty() || state == "authorization_required") {
                                    if (existingGrant) "Freigabe erneuern" else "Erneut verbinden"
                                } else if (!connected) "Verbinden"
                                else if (!enabled) "Kalender einschalten"
                                else "Kalender abgleichen und Wetter aktualisieren"
                            val weatherEnabled =
                                AppointmentRuntime.flag(context, store, "weather_enabled")
                            val notificationEnabled =
                                AppointmentRuntime.flag(context, store, "notifications")
                            val delivery = WeatherNotifications.state(context)
                            val pixel =
                                if (!notificationEnabled) "AUS"
                                else
                                    when (delivery) {
                                        "ready" -> "bereit"
                                        "channel_blocked" -> "Wetterkanal blockiert"
                                        "app_blocked" -> "App-Meldungen blockiert"
                                        else -> "Freigabe nötig"
                                    }
                            var summary =
                                "Wetter: " +
                                    (if (weatherEnabled) "EIN" else "AUS") +
                                    " · Pixel: " +
                                    pixel
                            if (weatherEnabled)
                                try {
                                    val snapshot = PixelMcpReadTools.effective(store.getState())
                                    val all = snapshot.getJSONArray("appointments")
                                    var known = 0
                                    var missing = 0
                                    val now = Instant.now()
                                    for (i in 0 until all.length()) {
                                        val appointment = all.getJSONObject(i)
                                        if (
                                            !AppointmentRules.active(appointment) ||
                                                appointment.optString("status") == "draft" ||
                                                !AppointmentRules.pending(appointment, now) ||
                                                AppointmentRules.instant(
                                                        appointment.getString("starts_at")
                                                    )
                                                    .isAfter(
                                                        now.plusSeconds(
                                                            FreeWeatherSource.MONITOR_WINDOW
                                                        )
                                                    )
                                        )
                                            continue
                                        if (
                                            WeatherLocationRules.coordinates(
                                                AppointmentRuntime.weatherLocation(
                                                    store,
                                                    appointment,
                                                )
                                            )
                                        )
                                            known++
                                        else missing++
                                    }
                                    summary +=
                                        "\nEinsatzgebiete: " +
                                            known +
                                            " erkannt" +
                                            (if (missing > 0) " · " + missing + " noch offen."
                                            else if (known == 0)
                                                ". Keine anstehenden Einsätze in den nächsten 16 Tagen."
                                            else ".")
                                } catch (unavailable: Exception) {
                                    summary += "\nEinsatzgebiete derzeit nicht lesbar."
                                }
                            Presentation(
                                connected,
                                enabled,
                                state,
                                setupCode,
                                existingGrant,
                                accountLabel,
                                steps,
                                details.toString(),
                                summary,
                                action,
                                if (needsReview) "Prüfung nötig"
                                else if (connected) {
                                    if (enabled) "Kalender eingeschaltet" else "Kalender pausiert"
                                } else "Kalender nicht eingerichtet",
                                if (needsReview)
                                    "Bitte Verbindung prüfen und bei Bedarf erneut verbinden."
                                else if (!connected)
                                    "Konto auswählen und Google Kalender verbinden."
                                else label.replace("Verbunden · ", ""),
                                weatherEnabled = weatherEnabled,
                                notificationsEnabled = notificationEnabled,
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (unavailable: Exception) {
                    status = "Kalenderstatus derzeit nicht lesbar. Bitte erneut öffnen."
                    return@launch
                }
            if (generation == presentationGeneration) {
                presentation = next
                calendar = next.enabled
                weather = next.weatherEnabled
                notifications = next.notificationsEnabled
                if (status.isEmpty()) status = next.initialStatus
            }
        }
    }

    private fun beginCalendarSetup() {
        if (account.isEmpty()) {
            connectAfterAccount = true
            chooseAccount()
        } else connect()
    }

    private fun calendarPrimary() {
        if (
            !presentation.connected ||
                presentation.setupCode.isNotEmpty() ||
                presentation.state == "authorization_required"
        )
            beginCalendarSetup()
        else if (!presentation.enabled) saveCalendarSwitch(true) else checkPlanning()
    }

    private fun saveCalendarSwitch(enabled: Boolean) {
        if (working) return
        working = true
        lifecycleScope.launch {
            try {
                openedSettingsHash =
                    withContext(Dispatchers.IO) {
                        PixelLocalStore(this@PlanningConnectionActivity).use { store ->
                            AppointmentRuntime.setCalendarEnabled(
                                this@PlanningConnectionActivity,
                                store,
                                enabled,
                                openedSettingsHash,
                            )
                            PixelMcpWriteTools.hash(
                                AppointmentRuntime.publicSettings(
                                    this@PlanningConnectionActivity,
                                    store,
                                )
                            )
                        }
                    }
                calendar = enabled
                status =
                    if (enabled) "Google Kalender eingeschaltet. Abgleich folgt."
                    else
                        "Google Kalender pausiert. App-Termine und Google-Einträge bleiben erhalten."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (changed: Exception) {
                status =
                    if (changed is IllegalArgumentException) changed.message ?: "Nicht geändert."
                    else "Nicht geändert. Einstellungen wurden inzwischen geändert; erneut öffnen."
            } finally {
                working = false
                refreshPresentation()
            }
        }
    }

    private fun saveWeatherSwitch(key: String, enabled: Boolean) {
        if (working) return
        working = true
        lifecycleScope.launch {
            try {
                openedSettingsHash =
                    withContext(Dispatchers.IO) {
                        PixelLocalStore(this@PlanningConnectionActivity).use { store ->
                            val settings = AppointmentRuntime.settings(store)
                            settings.put(key, enabled)
                            AppointmentRuntime.saveSettings(
                                this@PlanningConnectionActivity,
                                store,
                                settings,
                                openedSettingsHash,
                            )
                            PlanningAccount.prefs(this@PlanningConnectionActivity)
                                .edit()
                                .putBoolean(key, enabled)
                                .putBoolean("weather_blocked", false)
                                .putInt("weather_failures", 0)
                                .remove("weather_retry_at")
                                .apply()
                            if (!enabled) {
                                WeatherNotifications.cancelAll(this@PlanningConnectionActivity)
                                store.putOwnerValue("weather:delivery-paused", "true")
                            }
                            PlanningJob.schedule(this@PlanningConnectionActivity, 1000)
                            PixelMcpWriteTools.hash(
                                AppointmentRuntime.publicSettings(
                                    this@PlanningConnectionActivity,
                                    store,
                                )
                            )
                        }
                    }
                if (key == "weather_enabled") weather = enabled
                else if (key == "notifications") notifications = enabled
                status =
                    if (enabled) "Gespeichert. Wetterprüfung folgt automatisch."
                    else "Ausgeschaltet und gespeichert."
                if (notifications && (key == "notifications" || weather))
                    requestNotificationPermission()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (changed: Exception) {
                status = "Nicht geändert. Einstellungen wurden inzwischen geändert; erneut öffnen."
            } finally {
                working = false
                refreshPresentation()
            }
        }
    }

    private fun saveAdvanced() {
        if (working) return
        val selected = JSONArray()
        for (i in days.indices) if (days[i]) selected.put(i + 1)
        val start = from
        val end = to
        val buffer = travel
        val limits = thresholds.toMap()
        working = true
        lifecycleScope.launch {
            try {
                openedSettingsHash =
                    withContext(Dispatchers.IO) {
                        PixelLocalStore(this@PlanningConnectionActivity).use { store ->
                            val settings =
                                AppointmentRuntime.settings(store)
                                    .put("workdays", selected)
                                    .put("day_start", start)
                                    .put("day_end", end)
                                    .put("travel_minutes", buffer.toInt())
                            for ((key, value) in limits) settings.put(key, value.toDouble())
                            AppointmentRuntime.saveSettings(
                                this@PlanningConnectionActivity,
                                store,
                                settings,
                                openedSettingsHash,
                            )
                            PlanningJob.schedule(this@PlanningConnectionActivity, 1000)
                            PixelMcpWriteTools.hash(
                                AppointmentRuntime.publicSettings(
                                    this@PlanningConnectionActivity,
                                    store,
                                )
                            )
                        }
                    }
                status = "Arbeitszeiten und Wettergrenzen gespeichert."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (changed: Exception) {
                status =
                    if (changed is IllegalArgumentException) changed.message ?: "Nicht gespeichert."
                    else "Nicht gespeichert. Einstellungen erneut öffnen; Angaben bleiben erhalten."
            } finally {
                working = false
                refreshPresentation()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission("android.permission.POST_NOTIFICATIONS") !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        )
            notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
    }

    private fun notificationSettings() {
        WeatherNotifications.channel(this)
        val state = WeatherNotifications.state(this)
        val intent =
            Intent(
                    if (state == "permission_required" || state == "app_blocked")
                        android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    else android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                )
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, WeatherNotifications.CHANNEL)
        try {
            startActivity(intent)
        } catch (unavailable: Exception) {
            status = "Benachrichtigungen in den Pixel-App-Einstellungen freigeben."
        }
    }

    private fun openCalendarApp() {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(
                        "content://com.android.calendar/time/" + System.currentTimeMillis()
                    ),
                )
            )
        } catch (unavailable: Exception) {
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://calendar.google.com/"),
                    )
                )
            } catch (missing: Exception) {
                status = "Keine Kalender-App verfügbar."
            }
        }
    }

    private fun checkPlanning() {
        if (working) return
        working = true
        status = "Eingeschalteten Kalender abgleichen und eingeschaltetes Wetter aktualisieren …"
        lifecycleScope.launch {
            try {
                runInterruptible(Dispatchers.IO) {
                    PixelLocalStore(this@PlanningConnectionActivity).use { store ->
                        AppointmentRuntime.request(this@PlanningConnectionActivity, store)
                        AppointmentRuntime.refresh(this@PlanningConnectionActivity, store)
                    }
                }
                status =
                    "Aktualisierung beendet. Den gespeicherten Kalenderstand und die Wetterhinweise getrennt beachten."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unavailable: Exception) {
                status = "Prüfung derzeit nicht möglich. Gespeicherte Termine bleiben erhalten."
            } finally {
                working = false
                refreshPresentation()
            }
        }
    }

    private fun phase(value: String) {
        setupStage = CalendarAccessRules.phase(value)
        runOnUiThread { status = CalendarAccessRules.phaseLabel(value) + " …" }
    }

    private suspend fun connectionFailed(error: Exception) {
        val code = CalendarAccessRules.code(error)
        val stage = CalendarAccessRules.phase(setupStage)
        withContext(Dispatchers.IO) {
            PlanningAccount.prefs(this@PlanningConnectionActivity)
                .edit()
                .putString("calendar_setup_code", code)
                .putString("calendar_setup_stage", stage)
                .putString("calendar_setup_failed_at", Instant.now().toString())
                .apply()
        }
        if (!isFinishing && !isDestroyed) {
            status = "Verbindung nicht abgeschlossen. Bitte erneut verbinden."
            refreshPresentation()
        }
    }

    private fun currentConnection(generation: Long): Boolean =
        generation == connectionGeneration && !isFinishing && !isDestroyed

    private fun connect() {
        if (working) return
        if (account.isEmpty()) {
            status = "Bitte Google-Konto auswählen."
            refreshPresentation()
            return
        }
        connect(account, busy, null)
    }

    private fun connect(selected: String, readBusy: Boolean, granted: String?) {
        if (working) return
        working = true
        val generation = ++connectionGeneration
        status = "Google-Freigabe und bestehenden App-Kalender prüfen …"
        lifecycleScope.launch {
            var awaitingConsent = false
            try {
                val result =
                    runInterruptible(Dispatchers.IO) {
                        PixelLocalStore(this@PlanningConnectionActivity).use { store ->
                            val context = this@PlanningConnectionActivity
                            if (granted == null)
                                PlanningAccount.prefs(context)
                                    .edit()
                                    .putString(
                                        "calendar_setup_attempt_at",
                                        Instant.now().toString(),
                                    )
                                    .remove("calendar_setup_result_code")
                                    .apply()
                            phase("grant")
                            val credential =
                                granted
                                    ?: PlanningAccount.token(context, selected, readBusy, context)
                            if (!currentConnection(generation)) throw CancellationException()
                            if (openedCalendarBinding != PlanningAccount.bindingHash(context))
                                throw CalendarAuthorization.failure("CALENDAR_SETTINGS_CHANGED")
                            val client =
                                PlanningAccount.client(context, selected, readBusy, credential)
                            phase("calendar_lookup")
                            val preferences = PlanningAccount.prefs(context)
                            var marker = preferences.getString("calendar_marker", "") ?: ""
                            if (
                                marker.isEmpty() &&
                                    selected == preferences.getString("account", "") &&
                                    !preferences.getString("calendar_id", "").isNullOrEmpty()
                            ) {
                                val known = client.calendars()
                                for (i in 0 until known.length()) {
                                    val entry = known.getJSONObject(i)
                                    if (
                                        preferences.getString("calendar_id", "") ==
                                            entry.optString("id") &&
                                            entry.optString("accessRole") == "owner" &&
                                            entry
                                                .optString("description")
                                                .startsWith("hausgarten.app.calendar.v1:")
                                    )
                                        marker = entry.getString("description")
                                }
                            }
                            if (marker.isEmpty()) {
                                val device = PixelTunnelConfig(context).id()
                                marker =
                                    "hausgarten.app.calendar.v1:" +
                                        (if (device.isEmpty()) UUID.randomUUID().toString()
                                        else device)
                                if (
                                    !preferences
                                        .edit()
                                        .putString("calendar_marker", marker)
                                        .commit()
                                )
                                    throw java.io.IOException(
                                        "Kalenderzuordnung konnte nicht gespeichert werden."
                                    )
                            }
                            if (!preferences.edit().putString("calendar_marker", marker).commit())
                                throw java.io.IOException(
                                    "Kalenderzuordnung konnte nicht gespeichert werden."
                                )
                            val pending =
                                "calendar-create:" + PixelMcpWriteTools.hash(selected + marker)
                            val found =
                                client.ensureOwnCalendar(
                                    marker,
                                    object : CalendarClient.CalendarSetup {
                                        override fun phase(value: String) {
                                            this@PlanningConnectionActivity.phase(value)
                                        }

                                        override fun pending(): Boolean =
                                            preferences.getBoolean(pending, false)

                                        override fun confirmedId(): String =
                                            preferences.getString(pending + ":confirmed_id", "")
                                                ?: ""

                                        override fun confirmedId(id: String) {
                                            if (
                                                !preferences
                                                    .edit()
                                                    .putString(pending + ":confirmed_id", id)
                                                    .commit()
                                            )
                                                throw java.io.IOException(
                                                    "Kalenderbestätigung nicht gesichert."
                                                )
                                        }

                                        override fun pending(value: Boolean) {
                                            if (
                                                !preferences
                                                    .edit()
                                                    .putBoolean(pending, value)
                                                    .commit()
                                            )
                                                throw java.io.IOException(
                                                    "Kalenderabsicht konnte nicht gesichert werden."
                                                )
                                        }
                                    },
                                )
                            if (!currentConnection(generation)) throw CancellationException()
                            phase("settings_save")
                            val calendarId = found.getString("id")
                            require(found.optString("accessRole", "owner") == "owner") {
                                "App-Kalender benötigt Eigentümerzugriff."
                            }
                            synchronized(PlanningAccount::class.java) {
                                val database = store.writableDatabase
                                database.beginTransaction()
                                try {
                                    if (
                                        openedCalendarBinding !=
                                            PlanningAccount.bindingHash(context)
                                    )
                                        throw CalendarAuthorization.failure(
                                            "CALENDAR_SETTINGS_CHANGED"
                                        )
                                    if (
                                        PixelMcpWriteTools.hash(
                                            AppointmentRuntime.publicSettings(context, store)
                                        ) != openedSettingsHash
                                    )
                                        throw PixelMcpWriteTools.Rejected(
                                            "WRITE_CONFLICT",
                                            "Planung wurde inzwischen geändert. Einstellungen erneut öffnen; Termine bleiben erhalten.",
                                        )
                                    // Reset only transport mappings for a deliberately different
                                    // calendar.
                                    val different =
                                        calendarId != preferences.getString("calendar_id", "")
                                    if (different) {
                                        store.putOwnerValue("calendar:token", "")
                                        store.putOwnerValue("calendar:index", "[]")
                                        store.putOwnerValue("calendar:busy", "{}")
                                        store.putOwnerValue("calendar:dirty", "{}")
                                        store.putOwnerValue(
                                            "calendar:binding_namespace",
                                            calendarId,
                                        )
                                        store.putOwnerValue("calendar:last_success_at", "")
                                        store.putOwnerValue("calendar:last_attempt_at", "")
                                        store.putOwnerValue("calendar:last_error_code", "")
                                        store.putOwnerValue("calendar:status", "pending")
                                    }
                                    val config =
                                        AppointmentRuntime.settings(store)
                                            .put("calendar_enabled", true)
                                    AppointmentRuntime.saveSettings(
                                        context,
                                        store,
                                        config,
                                        openedSettingsHash,
                                    )
                                    if (
                                        !preferences
                                            .edit()
                                            .putString("account", selected)
                                            .putString("calendar_id", calendarId)
                                            .putBoolean("read_busy", readBusy)
                                            .remove("calendar_setup_code")
                                            .remove("calendar_setup_stage")
                                            .remove("calendar_setup_result_code")
                                            .remove("calendar_setup_failed_at")
                                            .putBoolean("calendar_blocked", false)
                                            .putBoolean("weather_blocked", false)
                                            .putInt("calendar_failures", 0)
                                            .putInt("weather_failures", 0)
                                            .remove("calendar_retry_at")
                                            .remove("weather_retry_at")
                                            .commit()
                                    )
                                        throw java.io.IOException(
                                            "Kalenderfreigabe konnte nicht gespeichert werden."
                                        )
                                    database.setTransactionSuccessful()
                                    openedSettingsHash =
                                        PixelMcpWriteTools.hash(
                                            AppointmentRuntime.publicSettings(context, store)
                                        )
                                } finally {
                                    database.endTransaction()
                                }
                                openedCalendarBinding = PlanningAccount.bindingHash(context)
                            }
                            PlanningJob.schedule(context, 1000)
                            true
                        }
                    }
                if (result && currentConnection(generation)) {
                    calendar = true
                    status =
                        "Google Kalender: EIN. Gespeicherte Termine werden automatisch übertragen."
                }
            } catch (required: CalendarAuthorization.ConsentRequired) {
                if (currentConnection(generation)) {
                    awaitingConsent = true
                    waitingForGoogle = true
                    pendingGoogleAccount = selected
                    pendingGoogleBusy = readBusy
                    try {
                        googleConsent.launch(
                            IntentSenderRequest.Builder(required.resolution.intentSender).build()
                        )
                    } catch (unavailable: Exception) {
                        awaitingConsent = false
                        waitingForGoogle = false
                        pendingGoogleAccount = ""
                        connectionFailed(CalendarAuthorization.failure(unavailable))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentConnection(generation)) connectionFailed(error)
            } finally {
                if (!awaitingConsent && currentConnection(generation)) {
                    working = false
                    refreshPresentation()
                }
            }
        }
    }

    private fun disconnect() {
        if (working) return
        working = true
        lifecycleScope.launch {
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        PixelLocalStore(this@PlanningConnectionActivity).use { store ->
                            val context = this@PlanningConnectionActivity
                            val binding =
                                synchronized(PlanningAccount::class.java) {
                                    if (
                                        openedCalendarBinding !=
                                            PlanningAccount.bindingHash(context)
                                    )
                                        throw CalendarAuthorization.failure(
                                            "CALENDAR_SETTINGS_CHANGED"
                                        )
                                    val database = store.writableDatabase
                                    database.beginTransaction()
                                    try {
                                        val config =
                                            AppointmentRuntime.settings(store)
                                                .put("calendar_enabled", false)
                                        AppointmentRuntime.saveSettings(
                                            context,
                                            store,
                                            config,
                                            openedSettingsHash,
                                        )
                                        if (
                                            !PlanningAccount.prefs(context)
                                                .edit()
                                                .remove("calendar_id")
                                                .commit()
                                        )
                                            throw java.io.IOException("Trennung nicht gesichert.")
                                        database.setTransactionSuccessful()
                                    } finally {
                                        database.endTransaction()
                                    }
                                    PlanningAccount.bindingHash(context)
                                }
                            PlanningJob.schedule(context, 1000)
                            Pair(
                                PixelMcpWriteTools.hash(
                                    AppointmentRuntime.publicSettings(context, store)
                                ),
                                binding,
                            )
                        }
                    }
                openedSettingsHash = result.first
                openedCalendarBinding = result.second
                status =
                    "Google Kalender getrennt. App-Termine und Google-Einträge bleiben erhalten."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (unavailable: Exception) {
                status = "Kalenderverbindung nicht geändert. Einstellungen erneut öffnen."
            } finally {
                working = false
                refreshPresentation()
            }
        }
    }

    private fun chooseAccount() {
        try {
            accountChooser.launch(
                AccountManager.newChooseAccountIntent(
                    null,
                    null,
                    arrayOf("com.google"),
                    null,
                    null,
                    null,
                    null,
                )
            )
        } catch (unavailable: Exception) {
            status = "Google-Kontoauswahl auf diesem Gerät nicht verfügbar."
            refreshPresentation()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPresentation()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selected_calendar_account", account)
        outState.putBoolean("waiting_for_google", waitingForGoogle)
        if (waitingForGoogle) {
            outState.putString("pending_calendar_account", pendingGoogleAccount)
            outState.putBoolean("pending_calendar_busy", pendingGoogleBusy)
            outState.putString("calendar_binding_hash", openedCalendarBinding)
            outState.putString("calendar_settings_hash", openedSettingsHash)
        }
        if (loaded) {
            outState.putBoolean("edited_busy", busy)
            outState.putString("edited_from", from)
            outState.putString("edited_to", to)
            outState.putString("edited_travel", travel)
            outState.putBooleanArray("edited_days", days.toBooleanArray())
            for ((key, value) in thresholds) outState.putString("edited_$key", value)
            outState.putBoolean("account_disclosure", accountDisclosure)
            outState.putBoolean("technical_disclosure", technicalDisclosure)
            outState.putBoolean("notification_disclosure", notificationDisclosure)
            outState.putBoolean("advanced_disclosure", advancedDisclosure)
        }
    }

    override fun onDestroy() {
        connectionGeneration++
        super.onDestroy()
    }

    private fun activityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!loaded) {
            queuedActivityResult = Triple(requestCode, resultCode, data)
            return
        }
        handleActivityResult(requestCode, resultCode, data)
    }

    private var queuedActivityResult: Triple<Int, Int, Intent?>? = null

    private fun handleActivityResult(request: Int, result: Int, data: Intent?) {
        if (request == GOOGLE_AUTH_REQUEST) {
            if (!waitingForGoogle || pendingGoogleAccount.isEmpty()) return
            val selected = pendingGoogleAccount
            val readBusy = pendingGoogleBusy
            waitingForGoogle = false
            pendingGoogleAccount = ""
            working = false
            setupStage = "grant"
            PlanningAccount.prefs(this).edit().putInt("calendar_setup_result_code", result).apply()
            if (selected != account || readBusy != busy) {
                lifecycleScope.launch {
                    connectionFailed(CalendarAuthorization.failure("CALENDAR_SETTINGS_CHANGED"))
                }
                return
            }
            try {
                connect(
                    selected,
                    readBusy,
                    CalendarAuthorization.fromActivityResult(this, result, data, readBusy),
                )
            } catch (failure: Exception) {
                lifecycleScope.launch { connectionFailed(CalendarAuthorization.failure(failure)) }
            }
            return
        }
        if (request == ACCOUNT_REQUEST && result == RESULT_OK && data != null && !working) {
            account = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) ?: ""
            refreshPresentation()
            if (connectAfterAccount) {
                connectAfterAccount = false
                connect()
            }
        } else if (request == ACCOUNT_REQUEST && connectAfterAccount) {
            connectAfterAccount = false
            status = "Kontoauswahl abgebrochen. Bisherige Verbindung und Termine bleiben erhalten."
            refreshPresentation()
        }
    }

    private fun calendarTime(value: String): String =
        try {
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN)
                .withZone(ZoneId.of("Europe/Vienna"))
                .format(Instant.parse(value))
        } catch (invalid: Exception) {
            "Zeitpunkt nicht lesbar"
        }

    companion object {
        private const val ACCOUNT_REQUEST = 1
        private const val GOOGLE_AUTH_REQUEST = 84
    }
}

/** This overview reads only local evidence; foreign ChatGPT accounts are outside the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeConnections(controller: NativeAppController) {
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    var snapshot by remember(controller) { mutableStateOf<JSONObject?>(null) }
    var checking by remember(controller) { mutableStateOf(false) }
    var error by remember(controller) { mutableStateOf("") }
    var explanationOpen by remember(controller) { mutableStateOf(false) }
    fun refreshPresentation() {
        if (checking) return
        checking = true
        scope.launch {
            try {
                snapshot =
                    withContext(Dispatchers.IO) {
                        val context = controller.context
                        val store = controller.store
                        val settings = AppointmentRuntime.publicSettings(context, store)
                        JSONObject()
                            .put("calendar_connected", PlanningAccount.connected(context))
                            .put(
                                "calendar_enabled",
                                AppointmentRuntime.calendarEnabled(context, store),
                            )
                            .put("calendar_state", store.ownerValue("calendar:status", "pending"))
                            .put(
                                "calendar_last_success_at",
                                store.ownerValue("calendar:last_success_at", ""),
                            )
                            .put("weather_enabled", settings.optBoolean("weather_enabled"))
                            .put("weather_state", store.ownerValue("weather:status", "pending"))
                            .put("notifications_enabled", settings.optBoolean("notifications"))
                            .put("notification_state", WeatherNotifications.state(context))
                            .put("pixel", PixelTunnelService.status(context))
                    }
                error = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Lokaler Verbindungsstatus derzeit nicht lesbar."
            } finally {
                checking = false
            }
        }
    }
    LaunchedEffect(controller) { refreshPresentation() }
    DisposableEffect(owner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPresentation()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (explanationOpen)
        ModalBottomSheet(
            onDismissRequest = { explanationOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
                Text(
                    "UI und MCP: Funktionsprüfung",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Dieses Blatt erklärt die vorhandenen Wege. Es führt keine Prüfung aus.")
                    NativeGroup("Fachdaten lesen und ändern") {
                        Text(
                            "Arbeiten, L.C.R., Planung, Kunden und Abrechnung haben native Bedienwege und MCP-Werkzeuge. Lesen öffnet Originale; Änderungen brauchen die jeweilige bewusste Aktion und dieselben fachlichen Prüfungen.",
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Text(
                            "MCP-Schreibzugriff gibt der Eigentümer in der App frei. Eine gespeicherte Freigabe ist noch kein Nachweis einer erfolgreichen Anfrage.",
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    NativeGroup("Android-Bedienung und Freigaben") {
                        Text(
                            "Kamera, Spracheingabe, Kontoauswahl und Dateiauswahl benötigen die passenden Android-Apps und Freigaben. MCP ersetzt diese Bedienung nicht.",
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    NativeGroup("Sicherung, Wiederherstellung und Speicher") {
                        Text(
                            "Eine ausgewählte ZIP-Datei wird fortgeschrieben. Wiederherstellen ist ein eigener bewusst bestätigter Weg. Dateizugriff, Integrität und erhaltene Originale müssen tatsächlich geprüft werden.",
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    NativeGroup("Verbindungen und Prüfstand") {
                        Text(
                            "Status prüfen liest den lokalen Zustand. Kalenderabgleich und Wetteraktualisierung sind eigene Aktionen. Konten, Tunnel und Freigaben bleiben erhalten; lokale Statusangaben bestätigen keine neue Verbindung oder Zustellung.",
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    NativeGroup("Prüfung der Abläufe") {
                        Text(
                            "Eine Funktionsabnahme muss Lesen, Schreiben, Fehler, Wiederholung und Konflikte mit synthetischen Daten ausführen und das Ergebnis erneut am Original lesen. Dieses Erklärblatt meldet keine bestandene Abnahme.",
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                NativeActionBar("Erklärung schließen", { explanationOpen = false })
            }
        }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Optionale Verbindungen", style = MaterialTheme.typography.titleLarge)
            NativeTextButton({ explanationOpen = true }) { Text("UI und MCP erklären") }

            NativeStatus(
                if (checking) "Lokalen Status lesen …"
                else if (error.isNotEmpty()) "Status unbestätigt"
                else if (snapshot != null) "Lokalen Status gelesen" else "Noch nicht geprüft",
                if (error.isNotEmpty()) NativeStatusTone.Attention else NativeStatusTone.Neutral,
            )
            if (error.isNotEmpty()) Text(error)
            snapshot?.let { info ->
                val last = info.optString("calendar_last_success_at")
                val calendar =
                    when {
                        !info.optBoolean("calendar_connected") -> "Nicht eingerichtet"
                        !info.optBoolean("calendar_enabled") -> "Eingerichtet · pausiert"
                        info.optString("calendar_state") == "authorization_required" ->
                            "Eingeschaltet · Google-Freigabe prüfen"
                        info.optString("calendar_state") == "review_required" ->
                            "Eingeschaltet · Kalenderänderung prüfen"
                        else -> "Eingerichtet · eingeschaltet"
                    }
                val calendarTime =
                    if (last.isBlank()) "Noch kein erfolgreicher Abgleich gespeichert."
                    else {
                        val time = runCatching {
                            Instant.parse(last).toEpochMilli()
                        }.getOrDefault(0)
                        "Letzter erfolgreicher Abgleich: " +
                            pixelConnectionTime(time, "Zeitpunkt nicht lesbar")
                    }
                NativeInfoRow(
                    "Google Kalender",
                    "$calendar\n$calendarTime",
                    { controller.open(PlanningConnectionActivity::class.java) },
                )
                val weather =
                    if (!info.optBoolean("weather_enabled")) "Aus · optionale Funktion"
                    else
                        when (info.optString("weather_state")) {
                            "checked" -> "Eingeschaltet · gespeicherte Hinweise je Einsatz ansehen"
                            "authorization_required",
                            "review_required",
                            "retry_limit_reached" ->
                                "Eingeschaltet · Wetterprüfung benötigt Aufmerksamkeit"
                            else -> "Eingeschaltet · neue Wetterdaten nicht bestätigt"
                        }
                NativeInfoRow(
                    "Wetter",
                    weather,
                    { controller.open(PlanningConnectionActivity::class.java) },
                )
                val notifications =
                    if (!info.optBoolean("notifications_enabled")) "Aus"
                    else
                        when (info.optString("notification_state")) {
                            "ready" -> "Lokal freigegeben · keine Zustellung getestet"
                            else -> "Eingeschaltet · Android-Freigabe prüfen"
                        }
                NativeInfoRow(
                    "Pixel-Wetterbenachrichtigungen",
                    notifications,
                    { controller.open(PlanningConnectionActivity::class.java) },
                )
                val pixel = info.getJSONObject("pixel")
                NativeInfoRow(
                    "ChatGPT-Verbindung",
                    pixelConnectionLabel(pixel) +
                        "\nLetzte beantwortete Anfrage: " +
                        pixelConnectionTime(
                            pixel.optLong("last_response_at"),
                            "noch keine bestätigt",
                        ),
                    { controller.open(PixelTunnelActivity::class.java) },
                )
            }
        }
        NativeActionBar(
            "Status prüfen",
            { refreshPresentation() },
            "Zurück",
            { controller.back() },
            enabled = !checking,
        )
    }
}

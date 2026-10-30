package at.hausgarten.service

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Real MainActivity/Compose semantics, against the disposable provider suite's known fixture. */
class NativeComposeAppTests private constructor(private val test: Instrumentation) {
    companion object {
        /** Run only after document_provider_reopen, on the same disposable API 35 emulator. */
        @JvmStatic fun run(test: Instrumentation): JSONObject = NativeComposeAppTests(test).execute()
    }

    private val context = test.targetContext
    private val automation = test.uiAutomation
    private var checks = 0
    private var activity: Activity? = null
    @Volatile private var latest: Activity? = null
    private val fixturePrefix = "[TEST] QA Native Page "
    private val createdName = "[TEST] QA Native Form 100"
    private val editedName = "[TEST] QA Native Form 101"
    private val flowName = "[TEST] QA Native Flow 102"
    private lateinit var store: PixelLocalStore
    private lateinit var tools: PixelMcpReadTools

    private fun yes(value: Boolean, code: String) {
        if (!value) throw AssertionError("QA_NATIVE_COMPOSE_$code")
        checks++
    }

    private fun shell(command: String): String = automation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes().toString(Charsets.UTF_8).trim() }
    }

    private fun waitUntil(code: String, timeout: Long = 20000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(75)
        }
        throw AssertionError("QA_NATIVE_COMPOSE_TIMEOUT_$code; visible=" + visibleTexts().joinToString(" | ").take(3000))
    }

    private fun all(node: AccessibilityNodeInfo?, result: MutableList<AccessibilityNodeInfo> = ArrayList()): List<AccessibilityNodeInfo> {
        if (node != null) {
            result.add(node)
            for (i in 0 until node.childCount) all(node.getChild(i), result)
        }
        return result
    }

    private fun nodes() = all(automation.rootInActiveWindow)
    private fun texts(node: AccessibilityNodeInfo) = listOfNotNull(node.text?.toString(), node.contentDescription?.toString(), node.hintText?.toString())
    private fun visibleTexts() = nodes().filter { it.isVisibleToUser }.flatMap(::texts)
    private fun matches(node: AccessibilityNodeInfo, label: String) = texts(node).any { it == label || it.split('\n').any { line -> line == label } }
    private fun node(label: String): AccessibilityNodeInfo? = nodes().firstOrNull { it.isVisibleToUser && matches(it, label) }
    private fun shown(label: String) { waitUntil("VISIBLE_$label") { node(label) != null }; checks++ }
    private fun reveal(label: String) {
        if (node(label) == null) scrollTop()
        var lastScroll = 0L
        waitUntil("REVEAL_$label") {
            if (node(label) != null) return@waitUntil true
            if (SystemClock.uptimeMillis() - lastScroll > 250) { scroll(); lastScroll = SystemClock.uptimeMillis() }
            false
        }
        checks++
    }

    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var target: AccessibilityNodeInfo? = null
        while (current != null) {
            // Text-field values can equal a card label. Their click action only
            // focuses the editor; a child must not bypass an editable ancestor.
            if (current.isEditable) return null
            if (target == null && current.isVisibleToUser && current.isEnabled && current.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }) target = current
            current = current.parent
        }
        return target
    }

    private fun scrollTargets(action: Int): List<AccessibilityNodeInfo> {
        val vertical = if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        return nodes().filter { node ->
            node.isVisibleToUser && !node.isEditable &&
                node.actionList.any { it.id == action } && node.actionList.any { it.id == vertical }
        }
    }

    private fun scroll(): Boolean = scrollTargets(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        .any { it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }

    private fun click(label: String, scrollTo: Boolean = true): AccessibilityNodeInfo {
        if (scrollTo && nodes().none { matches(it, label) && clickable(it) != null }) scrollTop()
        var lastScroll = 0L
        var clicked: AccessibilityNodeInfo? = null
        waitUntil("CLICK_$label") {
            val candidate = nodes().firstOrNull { matches(it, label) && clickable(it) != null }
            val target = candidate?.let(::clickable)
            if (target != null && target.refresh() && target.isVisibleToUser && target.isEnabled) {
                val accepted = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (accepted) clicked = target
                return@waitUntil accepted
            }
            if (scrollTo && SystemClock.uptimeMillis() - lastScroll > 250) {
                nodes().firstOrNull { matches(it, label) }?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                scroll(); lastScroll = SystemClock.uptimeMillis()
            }
            false
        }
        checks++
        return requireNotNull(clicked)
    }

    private fun field(label: String): AccessibilityNodeInfo? = nodes().firstOrNull { candidate ->
        candidate.isVisibleToUser && candidate.isEditable && (
            matches(candidate, label) || all(candidate).any { matches(it, label) } ||
                candidate.parent?.let { parent -> parent.childCount <= 5 && all(parent).any { matches(it, label) } } == true)
    }

    private fun enter(label: String, value: String) {
        if (field(label) == null) scrollTop()
        var lastScroll = 0L
        waitUntil("FIELD_$label") {
            if (field(label) != null) return@waitUntil true
            if (SystemClock.uptimeMillis() - lastScroll > 250) { scroll(); lastScroll = SystemClock.uptimeMillis() }
            false
        }
        val selected = requireNotNull(field(label))
        yes(selected.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }), "SET_TEXT_$label")
        waitUntil("VALUE_$label") { field(label)?.text?.toString()?.contains(value) == true || value.isEmpty() && field(label) != null }
    }

    private fun back() {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val now = SystemClock.uptimeMillis()
            yes(automation.injectInputEvent(KeyEvent(now, now, action, KeyEvent.KEYCODE_BACK, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD), true), "SYSTEM_BACK")
        }
    }

    private fun read(name: String, args: JSONObject = JSONObject()): JSONObject {
        val response = tools.call(name, args)
        yes(!response.has("ok") || response.getBoolean("ok"), "MCP_$name")
        return response.optJSONObject("data") ?: response
    }

    private fun customers(name: String): List<JSONObject> {
        val master = PixelMcpReadTools.effective(store.getState())
        val rows = master.getJSONArray("customers")
        return (0 until rows.length()).map { rows.getJSONObject(it) }.filter { it.optString("name") == name }
    }

    private fun guardFixture() {
        // Check the device and test APK before reading any application data or fixture marker.
        yes(test.context.packageName == "at.hausgarten.service.test", "TEST_APK_REQUIRED")
        yes(context.packageName == "at.hausgarten.service", "ORIGINAL_PACKAGE_REQUIRED")
        yes(Build.VERSION.SDK_INT == 35, "API_35_REQUIRED")
        yes(Build.HARDWARE in listOf("ranchu", "goldfish") && shell("getprop ro.kernel.qemu") == "1", "DISPOSABLE_EMULATOR_REQUIRED")
        val marker = File(context.noBackupFilesDir, "qa-provider-fixture.json")
        yes(marker.isFile, "PROVIDER_FIXTURE_REQUIRED")
        val saved = JSONObject(marker.readText())
        yes(saved.getString("fixture") == "synthetic-provider-app-v1", "RECOGNIZED_SYNTHETIC_FIXTURE")
        val original = context.getSharedPreferences("haus_garten", Context.MODE_PRIVATE).getString("drive_snapshot", "") ?: ""
        yes(PixelMcpWriteTools.hash(original) == saved.getString("snapshot_sha256"), "PROVIDER_FIXTURE_HASH")
        yes(PixelMcpWriteTools.hash(JSONObject(original)) == PixelMcpWriteTools.hash(SyntheticBusinessData.profile("minimal")), "EXACT_PUBLIC_SYNTHETIC_PROFILE")
        yes(!File(context.noBackupFilesDir, "qa-native-compose-fixture.json").exists(), "FRESH_COMPOSE_SUITE_REQUIRED")
        yes(context.getSharedPreferences("planning_private", Context.MODE_PRIVATE).all.isEmpty(), "NO_CALENDAR_ACCOUNT_OR_WEATHER_CONFIGURATION")
        store = PixelLocalStore(context)
        yes(store.ownerValue("qa_provider_marker", "") == "QA synthetic provider fixture", "PROVIDER_DATABASE_IDENTITY")
        val master = PixelMcpReadTools.effective(store.getState())
        yes(PixelMcpWriteTools.hash(master.getJSONArray("customers")) == PixelMcpWriteTools.hash(SyntheticBusinessData.profile("minimal").getJSONArray("customers")), "UNMODIFIED_SYNTHETIC_CUSTOMERS")
        File(context.noBackupFilesDir, "qa-native-compose-fixture.json").writeText(JSONObject().put("fixture", "synthetic-native-compose-v1").put("source_fixture", saved.getString("fixture")).toString())
        tools = PixelMcpReadTools(store.readSource(), store, PixelOwnerAccess(context, store))
        yes(read("get_active_work_session").optString("state") == "IDLE", "NO_EXISTING_TIMER")
        // Explicit AUS prevents the real UI lifecycle from scheduling any weather/account work.
        yes(context.getSharedPreferences("planning_private", Context.MODE_PRIVATE).edit().putBoolean("weather_enabled", false).putBoolean("notifications", false).commit(), "OFFLINE_SYNTHETIC_PLANNING")
    }

    private fun seedPaging() {
        for (i in 0 until 55) {
            val name = fixturePrefix + String.format(Locale.ROOT, "%03d", i)
            store.saveEditorRecord("customer", JSONObject().put("name", name).put("active", true).put("test_record", true)
                .put("_request_id", "qa_native_page_$i"))
        }
        val rows = PixelMcpReadTools.effective(store.getState()).getJSONArray("customers")
        yes((0 until rows.length()).count { rows.getJSONObject(it).optString("name").startsWith(fixturePrefix) } == 55, "REAL_WRITER_CREATED_PAGING_ROWS")
    }

    private fun launch(planning: Boolean = false, appointment: String = "", action: String = "") {
        shell("wm dismiss-keyguard")
        activity = test.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("open_planning", planning)
            .putExtra("planning_appointment_id", appointment).putExtra("planning_action", action))
        latest = activity
        waitUntil("MAIN_COMPOSE_CONTENT") {
            var found = false
            test.runOnMainSync { found = hasCompose(requireNotNull(activity).window.decorView) }
            found && automation.rootInActiveWindow?.packageName?.toString() == context.packageName
        }
        checks++
    }

    private fun hasCompose(view: View): Boolean = view.javaClass.name.startsWith("androidx.compose.ui.platform.") ||
        view is ViewGroup && (0 until view.childCount).any { hasCompose(view.getChildAt(it)) }

    private fun recreate() {
        val old = requireNotNull(activity)
        latest = null
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(value: Activity, state: Bundle?) { if (value is MainActivity && value !== old) latest = value }
            override fun onActivityStarted(value: Activity) = Unit
            override fun onActivityResumed(value: Activity) = Unit
            override fun onActivityPaused(value: Activity) = Unit
            override fun onActivityStopped(value: Activity) = Unit
            override fun onActivitySaveInstanceState(value: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(value: Activity) = Unit
        }
        old.application.registerActivityLifecycleCallbacks(callbacks)
        try {
            test.runOnMainSync { old.recreate() }
            waitUntil("REAL_ACTIVITY_RECREATION") { latest != null && latest !== old }
            activity = latest
            waitUntil("RECREATED_COMPOSE") {
                var found = false
                test.runOnMainSync { found = hasCompose(requireNotNull(activity).window.decorView) }
                found
            }
            checks++
        } finally { old.application.unregisterActivityLifecycleCallbacks(callbacks) }
    }

    private fun execute(): JSONObject {
        guardFixture()
        val previousInfo = automation.serviceInfo
        val previousFlags = previousInfo.flags
        val oldFont = shell("settings get system font_scale")
        try {
            automation.serviceInfo = previousInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
            seedPaging()
            launch()
            val failures = ArrayList<Throwable>()
            fun journey(name: String, run: () -> Unit) {
                val failure = try { run(); null }
                    catch (failure: AssertionError) { failure }
                    catch (failure: Exception) { failure }
                if (failure == null) android.util.Log.i("NativeComposeQA", "$name PASS")
                else failures.add(AssertionError("QA_NATIVE_COMPOSE_JOURNEY_$name", failure))
            }
            journey("customer", ::customerJourney)
            journey("timer", ::timerJourney)
            journey("notification", ::notificationJourney)
            journey("large_text", ::largeTextJourney)
            if (failures.isNotEmpty()) {
                val failure = AssertionError("QA_NATIVE_COMPOSE_JOURNEYS_FAILED_${failures.size}")
                failures.forEach(failure::addSuppressed)
                throw failure
            }
            return JSONObject().put("checks", checks).put("synthetic_only", true).put("productive_data_read", false)
                .put("real_main_activity", true).put("native_compose_ui", true).put("customer_crud", true)
                .put("paging_and_search", true).put("mcp_write_visible_without_reload", true)
                .put("draft_recreation", true).put("timer_state_transitions", true).put("font_scale_200_percent", true)
                .put("planning_notification_alternatives", true).put("customer_conflict_review", true)
                .put("draft_write_failure_recovery", true)
                .put("drive_account_tested", false).put("physical_pixel_tested", false)
        } finally {
            shell(if (oldFont == "null") "settings delete system font_scale" else "settings put system font_scale $oldFont")
            activity?.let { current -> test.runOnMainSync { if (!current.isFinishing) current.finish() } }
            automation.serviceInfo = previousInfo.apply { flags = previousFlags }
            store.close()
        }
    }

    private fun scrollTop() {
        // A dispatched scroll can temporarily return false while Compose is
        // animating. Only a settled absence of backward actions proves the top.
        var lastScroll = 0L
        var boundarySince = 0L
        waitUntil("SCROLL_TOP") {
            val now = SystemClock.uptimeMillis()
            val targets = scrollTargets(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            if (targets.isEmpty()) {
                if (boundarySince == 0L) boundarySince = now
                now - boundarySince >= 500
            } else {
                boundarySince = 0L
                if (now - lastScroll >= 350) {
                    targets.any { it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
                    lastScroll = now
                }
                false
            }
        }
    }

    private fun draftFailure(key: String): Throwable? {
        // Read the real process-wide failure record; no production injection switch is added.
        val type = Class.forName("at.hausgarten.service.NativeDraftPersistence", true, MainActivity::class.java.classLoader)
        val field = type.getDeclaredField("failures").apply { isAccessible = true }
        return (field.get(null) as Map<*, *>)[key] as? Throwable
    }

    private fun customerDraftWriteFailure(draftKey: String) {
        val logicalKey = "editor:customer:new"
        val transientName = createdName + " transient"
        val durableBeforeFailure = store.ownerValue(draftKey, "")
        yes(draftFailure(logicalKey) == null, "DRAFT_FAILURE_INITIAL_CLEAR")
        yes(JSONObject(durableBeforeFailure).getJSONObject("values").getString("name") == createdName, "DRAFT_FAILURE_BASE_IS_DURABLE")
        val database = store.writableDatabase
        val trigger = "qa_native_customer_draft_write_failure"
        var installed = false
        try {
            // A persistent SQLite trigger applies to the actual Activity writer's connection.
            database.execSQL("CREATE TRIGGER $trigger BEFORE INSERT ON owner_state WHEN NEW.key='$draftKey' " +
                "BEGIN SELECT RAISE(ABORT,'QA_DRAFT_WRITE_FAILURE'); END")
            installed = true
            enter("Name *", transientName)
            waitUntil("REAL_DRAFT_WRITE_FAILURE_RECORDED") { draftFailure(logicalKey) != null }
            // The store preserves its public LOCAL_STATE_WRITE_FAILED wrapper;
            // require the actual injected SQLite trigger failure in its cause chain.
            val failures = generateSequence(draftFailure(logicalKey)) { it.cause }.take(16).toList()
            yes(failures.any { it is android.database.sqlite.SQLiteConstraintException &&
                it.message?.contains("QA_DRAFT_WRITE_FAILURE") == true }, "ACTUAL_SQLITE_DRAFT_FAILURE")
            yes(store.ownerValue(draftKey, "") == durableBeforeFailure, "FAILED_DRAFT_WRITE_PRESERVES_DISK_VALUE")
        } finally {
            if (installed) database.execSQL("DROP TRIGGER $trigger")
        }
        recreate()
        waitUntil("LATEST_FAILED_DRAFT_SURVIVES_RECREATION") { field("Name *")?.text?.toString()?.contains(transientName) == true }
        yes(store.ownerValue(draftKey, "") == durableBeforeFailure, "RECREATION_DOES_NOT_HIDE_FAILED_DISK_WRITE")
        yes(customers(transientName).isEmpty(), "FAILED_DRAFT_IS_NOT_CUSTOMER")
        enter("Name *", createdName)
        waitUntil("DRAFT_WRITE_RECOVERED") {
            val saved = store.ownerValue(draftKey, "")
            saved.isNotEmpty() && JSONObject(saved).getJSONObject("values").optString("name") == createdName && draftFailure(logicalKey) == null
        }
        yes(draftFailure(logicalKey) == null, "SUCCESSFUL_EDIT_CLEARS_REAL_DRAFT_FAILURE")
    }

    private fun customerJourney() {
        click("Kunden")
        enter("Suchen", fixturePrefix)
        shown("55 Treffer · 1–50")
        click("Weitere 50")
        scrollTop()
        shown("55 Treffer · 51–55")
        reveal(fixturePrefix + "054")
        click("Vorherige 50")
        scrollTop()
        shown("55 Treffer · 1–50")
        enter("Suchen", fixturePrefix + "054")
        shown("1 Treffer · 1–1")
        shown(fixturePrefix + "054")
        click("Kunde anlegen")
        waitUntil("BLANK_CUSTOMER_FORM") { field("Name *") != null }
        val draftKey = "native_form_draft:" + PixelMcpWriteTools.hash("editor:customer:new")
        yes(store.ownerValue(draftKey, "").isEmpty(), "UNTOUCHED_FORM_HAS_NO_DRAFT")
        enter("Name *", createdName)
        waitUntil("CUSTOMER_DRAFT_DURABLE") { store.ownerValue(draftKey, "").contains(createdName) }
        yes(customers(createdName).isEmpty(), "CUSTOMER_DRAFT_IS_NOT_RECORD")
        customerDraftWriteFailure(draftKey)
        recreate()
        waitUntil("CUSTOMER_DRAFT_AFTER_RECREATION") { field("Name *")?.text?.toString()?.contains(createdName) == true }
        yes(customers(createdName).isEmpty(), "RECREATION_DOES_NOT_SAVE_CUSTOMER")
        click("Speichern")
        waitUntil("CUSTOMER_CREATED") { customers(createdName).size == 1 }
        val id = customers(createdName).single().getString("id")
        shown(createdName)
        yes(store.ownerValue(draftKey, "").isEmpty(), "SAVE_REMOVES_CUSTOMER_DRAFT")
        click("Bearbeiten")
        enter("Name *", editedName)
        val remoteNote = "[TEST] remote note"
        val externalBefore = tools.call("get_customer", JSONObject().put("customer_id", id))
        yes(!externalBefore.has("ok") || externalBefore.getBoolean("ok"), "MCP_CUSTOMER_CAS_BASE")
        read("update_customer", JSONObject().put("request_id", "qa_native_customer_cas_note_001").put("customer_id", id)
            .put("expected_record_hash", externalBefore.getString("record_hash")).put("updates", JSONObject().put("notes", remoteNote)))
        // The open form still owns its original baseline. A remote write must cause
        // an explicit conflict, rather than silently refreshing the hash at save time.
        click("Speichern")
        scrollTop()
        shown("Der Eintrag wurde zwischenzeitlich verändert. Vergleiche den aktuellen Stand mit deinen Eingaben.")
        yes(customers(editedName).isEmpty(), "STALE_UI_SAVE_DOES_NOT_APPLY_NAME")
        yes(customers(createdName).single().let { it.getString("id") == id && it.optString("notes") == remoteNote }, "CONFLICT_RETAINS_REMOTE_CUSTOMER")
        click("Aktuellen Stand zum Vergleichen lesen")
        reveal("Notizen: " + remoteNote)
        yes(customers(editedName).isEmpty(), "COMPARISON_READ_DOES_NOT_SAVE_CUSTOMER")
        click("Geprüfte Eingaben als neuen Entwurf fortsetzen")
        scrollTop()
        waitUntil("CUSTOMER_REBASED_EDITOR") { field("Name *")?.text?.toString()?.contains(editedName) == true }
        yes(customers(editedName).isEmpty(), "EXPLICIT_REBASE_REMAINS_UNSAVED_DRAFT")
        click("Speichern")
        waitUntil("CUSTOMER_EDITED") { customers(editedName).singleOrNull()?.optString("id") == id }
        yes(customers(editedName).single().optString("notes") == remoteNote, "REVIEWED_SAVE_PRESERVES_REMOTE_NOTE")
        yes(customers(createdName).isEmpty(), "CUSTOMER_UPDATE_PRESERVES_ID_WITHOUT_DUPLICATE")
        reveal("Bearbeiten")
        val customerNavigation = nodes().firstNotNullOfOrNull { candidate -> if (matches(candidate, "Kunden")) clickable(candidate) else null }
        yes(customerNavigation != null && customerNavigation.isClickable && !customerNavigation.isSelected,
            "CUSTOMER_DETAIL_TAB_EXPOSES_ACCESSIBLE_LIST_NAVIGATION")
        click("Kunden")
        enter("Suchen", "QA Native F")
        shown("1 Treffer · 1–1")
        shown(editedName)
        // This uses the real MCP writer on a separate adapter. No refresh, navigation or
        // direct notification is allowed between this write and the visible UI assertion.
        val envelope = tools.call("get_customer", JSONObject().put("customer_id", id))
        yes(!envelope.has("ok") || envelope.getBoolean("ok"), "MCP_CUSTOMER_READ")
        val hash = envelope.getString("record_hash")
        read("update_customer", JSONObject().put("request_id", "qa_native_visible_flow_001").put("customer_id", id)
            .put("expected_record_hash", hash).put("updates", JSONObject().put("name", flowName)))
        shown(flowName)
        yes(node(editedName) == null, "COMMITTED_MCP_EDIT_REPLACES_VISIBLE_ROW")
        click(flowName)
        click("Kunde archivieren")
        click("Bewusst speichern")
        waitUntil("CUSTOMER_ARCHIVED") { customers(flowName).isEmpty() || customers(flowName).all { it.opt("active") == false || it.optInt("active", 1) == 0 || it.optBoolean("archived") } }
        click("Kunden")
        enter("Suchen", "QA Native F")
        shown("0 Treffer · 0–0")
        yes(node(flowName) == null, "ARCHIVED_CUSTOMER_NOT_IN_ACTIVE_LIST")
    }

    private fun timerJourney() {
        activity?.let { current -> test.runOnMainSync { current.finish() } }
        launch()
        click("Kunden")
        val before = PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length()
        enter("Suchen", fixturePrefix + "000")
        shown("1 Treffer · 1–1")
        val searchField = requireNotNull(field("Suchen"))
        yes(searchField.isEditable && clickable(searchField) == null, "SEARCH_VALUE_IS_NOT_A_CUSTOMER_CARD_ACTION")
        val customerCard = click(fixturePrefix + "000")
        yes(!customerCard.isEditable && customerCard.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }, "TIMER_CUSTOMER_CARD_USES_NON_EDITABLE_ACTION")
        reveal("Arbeit beginnen")
        click("Arbeit beginnen")
        click("Bewusst speichern")
        waitUntil("TIMER_STARTED") { read("get_active_work_session").optString("state") == "RUNNING" }
        val session = read("get_active_work_session")
        val id = session.getString("id")
        yes(session.optString("customer_id") == customers(fixturePrefix + "000").single().getString("id"), "TIMER_SYNTHETIC_CUSTOMER")
        click("Heute")
        click("Laufende Arbeit öffnen")
        reveal("Arbeit läuft")
        click("Pause starten")
        reveal("Pause läuft")
        yes(read("get_active_work_session").getString("state") == "PAUSED", "UI_PAUSES_REAL_TIMER")
        recreate()
        reveal("Pause läuft")
        yes(read("get_active_work_session").getString("id") == id, "RECREATION_PRESERVES_TIMER_ID")
        click("Pause beenden")
        reveal("Arbeit läuft")
        yes(read("get_active_work_session").getString("state") == "RUNNING", "UI_RESUMES_REAL_TIMER")
        click("Arbeit stoppen")
        reveal("Arbeit gestoppt")
        yes(read("get_active_work_session").getString("state") == "STOPPED_PENDING_REVIEW", "UI_STOPS_WITHOUT_FINALIZING")
        yes(PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() == before, "STOP_DOES_NOT_CREATE_WORKLOG")
        click("Sitzung verwerfen")
        waitUntil("TIMER_DISCARDED") { read("get_active_work_session").optString("state") == "IDLE" }
        yes(store.workSessions().current(id).getString("state") == "CANCELLED", "UI_DISCARD_PERSISTS_EXACT_SESSION")
        yes(PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() == before, "DISCARD_DOES_NOT_CREATE_WORKLOG")
    }

    private fun notificationJourney() {
        val zone = ZoneId.of("Europe/Vienna")
        val start = LocalDate.now(zone).plusDays(1).atTime(10, 0).atZone(zone).toInstant()
        val result = read("create_appointment", JSONObject().put("request_id", "qa_native_notification_001")
            .put("customer_id", customers(fixturePrefix + "000").single().getString("id"))
            .put("title", "[TEST] QA Native Notification").put("starts_at", start.toString()).put("ends_at", start.plusSeconds(3600).toString())
            .put("timezone", zone.id).put("weather_profile", "indoor").put("status", "tentative"))
        val id = result.getString("appointment_id")
        val original = PixelMcpWriteTools.hash(read("get_appointment", JSONObject().put("appointment_id", id)).getJSONObject("appointment"))
        activity?.let { current -> test.runOnMainSync { current.finish() } }
        launch(planning = true, appointment = id, action = "alternatives")
        shown("Vorgeschlagene Zeitfenster")
        click("Schließen")
        waitUntil("ALTERNATIVES_DISMISSED") { node("Vorgeschlagene Zeitfenster") == null }
        recreate()
        shown("[TEST] QA Native Notification")
        yes(node("Vorgeschlagene Zeitfenster") == null, "DISMISSED_NOTIFICATION_ACTION_IS_CONSUMED")
        // Deliver a fresh intent to the same actual Activity, as a second notification does.
        test.runOnMainSync {
            requireNotNull(activity).startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_planning", true).putExtra("planning_appointment_id", id).putExtra("planning_action", "alternatives"))
        }
        shown("Vorgeschlagene Zeitfenster")
        click("Schließen")
        yes(PixelMcpWriteTools.hash(read("get_appointment", JSONObject().put("appointment_id", id)).getJSONObject("appointment")) == original,
            "OPENING_ALTERNATIVES_DOES_NOT_CHANGE_APPOINTMENT")
    }

    private fun actionBounds(label: String): Rect {
        val selected = requireNotNull(node(label)) { "QA_NATIVE_COMPOSE_MISSING_$label" }
        val target = requireNotNull(clickable(selected)) { "QA_NATIVE_COMPOSE_NOT_ACTIONABLE_$label" }
        return Rect().also { target.getBoundsInScreen(it) }
    }

    private fun largeTextJourney() {
        activity?.let { current -> test.runOnMainSync { current.finish() } }
        shell("settings put system font_scale 2.0")
        launch(planning = true)
        waitUntil("200_PERCENT_RESOURCES") {
            var font = 0f
            test.runOnMainSync { font = requireNotNull(activity).resources.configuration.fontScale }
            font >= 1.99f
        }
        checks++
        shown("Planung")
        shown("Einsatz planen")
        shown("Jetzt prüfen")
        val plan = actionBounds("Einsatz planen")
        val check = actionBounds("Jetzt prüfen")
        yes(!Rect.intersects(plan, check), "LARGE_TEXT_ACTIONS_DO_NOT_OVERLAP")
        val frame = Rect()
        val safe = Rect()
        var minimumTouch = 0
        test.runOnMainSync {
            val decor = requireNotNull(activity).window.decorView
            decor.getWindowVisibleDisplayFrame(frame)
            val insets = requireNotNull(decor.rootWindowInsets).getInsets(WindowInsets.Type.systemBars())
            val location = IntArray(2); decor.getLocationOnScreen(location)
            safe.set(location[0] + insets.left, location[1] + insets.top, location[0] + decor.width - insets.right, location[1] + decor.height - insets.bottom)
            minimumTouch = (48 * decor.resources.displayMetrics.density).toInt()
        }
        yes(plan.height() >= minimumTouch && check.height() >= minimumTouch, "LARGE_TEXT_TOUCH_TARGETS")
        yes(plan.bottom <= frame.bottom && check.bottom <= frame.bottom, "ACTIONS_WITHIN_VISIBLE_FRAME")
        yes(safe.contains(plan) && safe.contains(check), "ACTIONS_CLEAR_SYSTEM_BARS")
        val before = PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments").length()
        click("Einsatz planen")
        shown("Termin speichern")
        enter("Datum und Startzeit (z. B. 2027-05-19T09:00)", "2027-05-19T09:00")
        // Wait for the draft's actual disk commit, then recreate the actual activity.
        val planningDraft = "native_form_draft:" + PixelMcpWriteTools.hash("planning:appointment:new")
        waitUntil("PLANNING_DRAFT_DURABLE") { store.ownerValue(planningDraft, "").contains("2027-05-19T09:00") }
        recreate()
        waitUntil("PLANNING_DRAFT_AFTER_RECREATION") { field("Datum und Startzeit (z. B. 2027-05-19T09:00)")?.text?.toString()?.contains("2027-05-19T09:00") == true }
        yes(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments").length() == before, "DRAFT_IS_NOT_APPOINTMENT")
        back()
        shown("Entwurf fortsetzen")
        click("Entwurf fortsetzen")
        click("Ungespeicherten Entwurf verwerfen")
        click("Verwerfen", scrollTo = false)
        shown("Einsatz planen")
        yes(store.ownerValue("native_form_draft:" + PixelMcpWriteTools.hash("planning:appointment:new"), "").isEmpty(), "PLANNING_DISCARD_REMOVES_DRAFT")
        yes(PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments").length() == before, "PLANNING_DISCARD_DOES_NOT_CREATE_RECORD")
        back()
        waitUntil("PLANNING_BACK_TO_MAIN") { node("Planung") == null && automation.rootInActiveWindow?.packageName?.toString() == context.packageName }
        checks++
    }
}

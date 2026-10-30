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
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.runBlocking
import android.net.Uri
import android.graphics.Bitmap
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
    private var officeMeasurements = JSONObject()

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

    private fun nodes(): List<AccessibilityNodeInfo> {
        // API35 is required by guardFixture. A fresh tree avoids acting on a
        // previous route or pre-scroll bounds retained by AccessibilityCache.
        automation.clearCache()
        return all(automation.rootInActiveWindow)
    }
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

    private fun settleUi() {
        test.waitForIdleSync()
        automation.waitForIdle(500, 5000)
    }

    private fun scroll(): Boolean {
        val moved = scrollTargets(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            .any { it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }
        if (moved) settleUi()
        return moved
    }

    private fun click(label: String, scrollTo: Boolean = true, onAction: ((Long) -> Unit)? = null): AccessibilityNodeInfo {
        if (scrollTo && nodes().none { matches(it, label) && clickable(it) != null }) scrollTop()
        var lastScroll = 0L
        var clicked: AccessibilityNodeInfo? = null
        waitUntil("CLICK_$label") {
            val candidate = nodes().firstOrNull { matches(it, label) && clickable(it) != null }
            val target = candidate?.let(::clickable)
            if (target != null && target.refresh() && target.isVisibleToUser && target.isEnabled) {
                val start = System.nanoTime()
                val accepted = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (accepted) { clicked = target; onAction?.invoke(start) }
                return@waitUntil accepted
            }
            if (scrollTo && SystemClock.uptimeMillis() - lastScroll > 250) {
                val exposed = nodes().firstOrNull { matches(it, label) }
                    ?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id) == true
                if (exposed) settleUi() else if (!scroll()) scrollTop()
                lastScroll = SystemClock.uptimeMillis()
            }
            false
        }
        checks++
        if (onAction == null) settleUi()
        return requireNotNull(clicked)
    }

    private fun field(label: String): AccessibilityNodeInfo? {
        val current = nodes()
        // Native fields retain their identity even when a floating label is
        // clipped by scrolling. Do not infer identity from neighboring fields.
        current.firstOrNull { it.isVisibleToUser && it.isEditable && it.viewIdResourceName == "native-field:$label" }?.let { return it }
        return current.firstOrNull { candidate ->
            candidate.isVisibleToUser && candidate.isEditable && candidate.viewIdResourceName?.startsWith("native-field:") != true && (
                matches(candidate, label) || all(candidate).any { matches(it, label) } ||
                    candidate.parent?.let { parent ->
                        val siblings = all(parent)
                        siblings.count { it.isEditable } == 1 && siblings.any { matches(it, label) }
                    } == true)
        }
    }

    private fun enter(label: String, value: String, displayed: String = value) {
        if (field(label) == null) scrollTop()
        var lastScroll = 0L
        waitUntil("FIELD_$label") {
            if (field(label) != null) return@waitUntil true
            if (SystemClock.uptimeMillis() - lastScroll > 250) { scroll(); lastScroll = SystemClock.uptimeMillis() }
            false
        }
        waitUntil("SET_TEXT_$label") {
            val selected = field(label)
            selected != null && selected.refresh() && selected.isEnabled && selected.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            })
        }
        checks++
        waitUntil("VALUE_$label") { field(label)?.text?.toString()?.contains(displayed) == true || value.isEmpty() && field(label) != null }
        settleUi()
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
            automation.serviceInfo = previousInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
            seedPaging()
            launch()
            val failures = ArrayList<Throwable>()
            fun journey(name: String, run: () -> Unit) {
                val failure = try { run(); null }
                    catch (failure: AssertionError) { failure }
                    catch (failure: Exception) { failure }
                if (failure == null) android.util.Log.i("NativeComposeQA", "$name PASS")
                else {
                    runCatching { captureSyntheticScreen("failure-$name"); logVisibleActions("failure_$name") }
                    failures.add(AssertionError("QA_NATIVE_COMPOSE_JOURNEY_$name", failure))
                }
            }
            journey("customer", ::customerJourney)
            journey("usability", ::usabilityJourney)
            journey("accessories", ::accessoryJourney)
            journey("timer", ::timerJourney)
            journey("notification", ::notificationJourney)
            journey("customer_context", ::customerContextJourney)
            journey("office_opening", ::officeOpeningMeasurements)
            journey("large_text", ::largeTextJourney)
            if (failures.isNotEmpty()) {
                val failure = AssertionError("QA_NATIVE_COMPOSE_JOURNEYS_FAILED_${failures.size}")
                failures.forEach(failure::addSuppressed)
                throw failure
            }
            return JSONObject().put("checks", checks).put("synthetic_only", true).put("productive_data_read", false)
                .put("real_main_activity", true).put("native_compose_ui", true).put("customer_crud", true).put("customer_context_navigation", true)
                .put("paging_and_search", true).put("mcp_write_visible_without_reload", true)
                .put("draft_recreation", true).put("timer_state_transitions", true).put("font_scale_200_percent", true)
                .put("planning_notification_alternatives", true).put("customer_conflict_review", true)
                .put("draft_write_failure_recovery", true)
                .put("work_entry_usability", true).put("work_photo_retention", true)
                .put("machine_accessory_roundtrip", true).put("decimal_comma_inputs", true)
                .put("work_ime_actions", true).put("office_opening", officeMeasurements)
                .put("drive_account_tested", false).put("physical_pixel_tested", false)
        } finally {
            shell(if (oldFont == "null") "settings delete system font_scale" else "settings put system font_scale $oldFont")
            activity?.let { current -> test.runOnMainSync { if (!current.isFinishing) current.finish() } }
            automation.serviceInfo = previousInfo.apply { flags = previousFlags }
            store.close()
        }
    }

    private fun scrollTop() {
        settleUi()
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
                    if (targets.any { it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }) settleUi()
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
        yes(customerNavigation != null && customerNavigation.isClickable && customerNavigation.isSelected,
            "CUSTOMER_DETAIL_SELECTED_AREA_EXPOSES_ACCESSIBLE_LIST_NAVIGATION")
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

    private fun usabilityJourney() {
        click("Heute")
        reveal("Erfasste Arbeiten")
        reveal("Hinweise")
        yes(node("Mehr") == null, "NO_TODAY_MORE_MENU")
        click("Kunden")
        enter("Suchen", fixturePrefix)
        click("Weitere 50")
        click("Heute")
        click("Kunden")
        shown("55 Treffer · 1–50")
        yes(field("Suchen")?.text?.toString()?.contains(fixturePrefix) == true, "CUSTOMER_FILTER_PRESERVED_ON_ENTRY")
        click("Heute")
        click("Arbeit erfassen")
        click("Kunde: Nicht zugeordnet")
        enter("Suchen", fixturePrefix + "000")
        click(fixturePrefix + "000")
        enter("Beginn", "08:00")
        enter("Ende", "12:00")
        waitUntil("WORK_TIMES_DURABLE") {
            val raw = store.ownerValue("native_form_draft:" + PixelMcpWriteTools.hash("editor:worklog:new"), "{}")
            val values = JSONObject(raw.ifBlank { "{}" }).optJSONObject("values")
            values?.optString("start_time") == "08:00" && values.optString("end_time") == "12:00"
        }
        // Preset and free input use the same visible field, with no pause submenu.
        reveal("Pause (Minuten)")
        logVisibleActions("pause_before_preset")
        click("30")
        enter("Freie Minuten", "23")
        enter("Was wurde erledigt? *", "[TEST] QA usability work")
        click("Textbausteine ▾")
        click("Rasen gemäht")
        enter("Freier Betrag (€)", "73,40")
        enter("Notizen", "[TEST] QA sofort sichtbare Notiz")
        checkWorkKeyboardActions()
        recreate()
        reveal("Freier Betrag (€)")
        yes(field("Freier Betrag (€)")?.text?.toString()?.contains("73,40") == true, "FREE_DISPOSAL_SURVIVES_RECREATION")
        click("Speichern")
        fun saved(): JSONObject? {
            val rows = PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            return (0 until rows.length()).map { rows.getJSONObject(it) }.firstOrNull { it.optString("activity").startsWith("[TEST] QA usability work") }
        }
        waitUntil("SAVED_WORK") { saved() != null }
        val row = requireNotNull(saved())
        yes(row.getInt("break_minutes") == 23 && row.getDouble("hours") == 3.62, "PAUSE_DEDUCTED_ONCE")
        yes(row.getDouble("disposal_cost") == 73.4 && row.getString("activity").contains("Rasen gemäht"), "DISPOSAL_AND_PHRASE_SAVED")
        reveal("Notizen: [TEST] QA sofort sichtbare Notiz")
        click("Bearbeiten")
        enter("Was wurde erledigt? *", "[TEST] QA ungespeichert")
        click("Verwerfen")
        shown("Ungespeicherte Eingaben und Foto verwerfen?")
        click("Abbrechen", scrollTo = false)
        reveal("Was wurde erledigt? *")
        yes(field("Was wurde erledigt? *")?.text?.toString()?.contains("ungespeichert") == true, "CANCEL_DISCARD_RETAINS_INPUT")
        click("Verwerfen")
        click("OK", scrollTo = false)
        yes(saved()?.getString("id") == row.getString("id"), "DISCARD_PRESERVES_SAVED_WORK")
        yes(saved()?.getString("activity") == row.getString("activity"), "DISCARD_DOES_NOT_SAVE_CHANGES")
        workPhotoRetention(row)
        // Discard has already returned to Today; its selected tab has no click action.
        reveal("Erfasste Arbeiten")
        click("Arbeit erfassen")
        click("Kunde: Nicht zugeordnet")
        enter("Suchen", fixturePrefix + "000")
        click(fixturePrefix + "000")
        enter("Was wurde erledigt? *", "[TEST] QA freie Dezimalwerte")
        enter("Arbeitsdauer in Stunden (nur ohne Beginn/Ende; netto)", "3,5", "3.5")
        enter("Kilometer", "7,25", "7.25")
        enter("Materialkosten", "12,50", "12.5")
        click("Speichern")
        waitUntil("DECIMAL_WORK_SAVED") {
            val rows = PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            (0 until rows.length()).map { rows.getJSONObject(it) }.any {
                it.optString("activity") == "[TEST] QA freie Dezimalwerte" && it.optDouble("hours") == 3.5 &&
                    it.optDouble("kilometers") == 7.25 && it.optString("material").contains("12.50")
            }
        }
        checks++
    }

    private fun checkWorkKeyboardActions() {
        val input = requireNotNull(field("Notizen"))
        yes(input.performAction(AccessibilityNodeInfo.ACTION_CLICK), "WORK_FIELD_CLICK")
        waitUntil("WORK_KEYBOARD_VISIBLE") {
            var visible = false
            test.runOnMainSync { visible = requireNotNull(activity).window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
            visible
        }
        // IME visibility is reported before its first draw and inset animation.
        // Observe the real keyboard window and stable geometry, not a fixed delay.
        var lastGeometry = ""
        var clearSince = 0L
        waitUntil("WORK_ACTIONS_CLEAR_KEYBOARD") {
            val frame = Rect()
            var keyboardTop = 0
            test.runOnMainSync {
                val decor = requireNotNull(activity).window.decorView
                decor.getWindowVisibleDisplayFrame(frame)
                val location = IntArray(2); decor.getLocationOnScreen(location)
                keyboardTop = location[1] + decor.height - requireNotNull(decor.rootWindowInsets).getInsets(WindowInsets.Type.ime()).bottom
            }
            val keyboardShown = automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && it.root?.isVisibleToUser == true }
            val save = runCatching { actionBounds("Speichern") }.getOrNull()
            val discard = runCatching { actionBounds("Verwerfen") }.getOrNull()
            val clear = keyboardShown && save != null && discard != null && frame.contains(save) && frame.contains(discard) &&
                save.bottom <= keyboardTop && discard.bottom <= keyboardTop && !Rect.intersects(save, discard)
            val geometry = "$frame keyboardTop=$keyboardTop keyboardShown=$keyboardShown save=$save discard=$discard"
            if (geometry != lastGeometry) {
                android.util.Log.i("NativeComposeQA", "work_keyboard_bounds $geometry")
                lastGeometry = geometry; clearSince = 0L
            }
            if (!clear) clearSince = 0L
            else if (clearSince == 0L) clearSince = SystemClock.uptimeMillis()
            clear && SystemClock.uptimeMillis() - clearSince >= 500
        }
        checks++
        captureSyntheticScreen("work-keyboard")
        back()
        waitUntil("WORK_KEYBOARD_HIDDEN") {
            var hidden = false
            test.runOnMainSync { hidden = requireNotNull(activity).window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == false }
            hidden
        }
    }

    private fun accessoryJourney() {
        click("Büro")
        click("Maschinenpark und Gerätewartung")
        click("Gerät anlegen")
        click("Gerätebezeichnung auswählen ▾")
        shown("Gerätebezeichnung auswählen")
        logVisibleActions("equipment_suggestions_open")
        enter("Vorschläge durchsuchen", "kett")
        click("Kettensäge")
        waitUntil("EQUIPMENT_SEARCHABLE_PRESET") { field("Gerätebezeichnung *")?.text?.toString() == "Kettensäge" }
        checks++
        val name = "[TEST] QA Zubehör Säge"
        enter("Gerätebezeichnung *", name)
        click("Speichern")
        reveal("Gerätewartung")
        click("Zubehör")
        click("Zubehör bearbeiten")
        click("Zubehör hinzufügen")
        click("Anderes Zubehör")
        click("Kette", scrollTo = false)
        enter("Bezeichnung", "QA Kette A")
        enter("Hersteller", "QA Hersteller")
        enter("Modell", "QA Modell")
        enter("Kettenteilung", "3/8 LP")
        enter("Treibgliedstärke", "1.1 mm")
        enter("Anzahl Treibglieder", "45")
        enter("Feilengröße", "4 mm")
        click("Aktuell verwendet")
        click("Speichern")
        reveal("Aktuell verwendete Kette")
        fun machine(): JSONObject {
            val rows = read("list_equipment", JSONObject().put("limit", 100)).getJSONArray("items")
            return (0 until rows.length()).map { rows.getJSONObject(it) }.first { it.optString("name") == name }
        }
        val first = machine()
        val a = first.getJSONArray("accessories").getJSONObject(0)
        yes(first.getString("current_chain_id") == a.getString("id") && a.getString("equipment_id") == first.getString("id"), "UI_ACCESSORY_CURRENT_MACHINE")
        yes(a.getString("pitch") == "3/8 LP" && a.getString("gauge") == "1.1 mm" && a.getString("drive_links") == "45" && a.getString("file_size") == "4 mm", "UI_ACCESSORY_SPECIFICATIONS")
        // Synthetic photo traverses the real native upload/store path. Picker UI remains a separate test.
        val source = File(context.cacheDir, "qa-accessory.png")
        val image = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        image.eraseColor(0xff1c7a38.toInt())
        source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        val controller = NativeAppController(requireNotNull(activity) as MainActivity, store)
        try {
            val upload = runBlocking { controller.stageEquipmentPhoto(Uri.fromFile(source), "qa_accessory_photo") }
            val b = JSONObject().put("id", "qa_ui_chain_b").put("type", "chain").put("name", "QA Kette B").put("photo_upload_id", upload)
            val args = JSONObject().put("request_id", "qa_accessory_second").put("equipment_id", first.getString("id"))
                .put("expected_record_hash", first.getString("record_hash")).put("accessories", JSONArray().put(JSONObject(a.toString()).apply { remove("equipment_id"); remove("photo") }).put(b))
                .put("current_chain_id", "qa_ui_chain_b")
            read("save_equipment", args)
            val second = machine()
            yes(second.getString("current_chain_id") == "qa_ui_chain_b" && second.getJSONArray("accessories").length() == 2, "ONLY_ONE_CURRENT_CHAIN")
            val storedB = second.getJSONArray("accessories").getJSONObject(1)
            yes(!storedB.has("pitch") || storedB.optString("pitch").isEmpty(), "UNKNOWN_SPECIFICATION_NOT_INVENTED")
            val photo = storedB.getJSONObject("photo")
            val native = RevierApplicationService(RevierLocalRepository(store, null)).equipmentPhoto(photo.getString("id"))
            yes(native?.getString("content_sha256") == photo.getString("content_sha256"), "ACCESSORY_PHOTO_REOPEN")
            // Machine navigation intentionally remembers its current detail.
            // Leave through the visible list action before testing a fresh open.
            click("Zur Liste")
            click("Heute"); click("Büro"); click("Maschinenpark und Gerätewartung")
            click(name); click("Zubehör")
            reveal("QA Kette B")
            reveal("Aktuell verwendete Kette")
            recreate()
            reveal("QA Kette B")
            reveal("Gerätefoto")
            captureSyntheticScreen("accessory")
            click("Gerät & Wartung")
            reveal("Wartung einrichten")
        } finally { source.delete() }
    }

    private fun workPhotoRetention(work: JSONObject) = runBlocking {
        val controller = NativeAppController(requireNotNull(activity) as MainActivity, store)
        val source = File(context.cacheDir, "qa-work-entry-photo.png")
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff1c7a38.toInt())
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val draft = nativeRetainPhotoDraft(controller, Uri.fromFile(source))
        val other = nativeRetainPhotoDraft(controller, Uri.fromFile(source))
        val identity = BackupDeviceGuard(context).current()
        val args = JSONObject().put("_photo_request_id", "qa_work_entry_photo_001").put("customer_id", work.getString("customer_id"))
            .put("worklog_id", work.getString("id")).put("role", "documentation").put("caption", "QA synthetic 3x3")
            .put("_capture_context", JSONObject().put("formatVersion", 1).put("installation_id", identity.getString("installationId")).put("restore_epoch", identity.getString("restoreEpoch")))
        val photo = controller.importPhoto(draft, args)
        val photoId = photo.getString("id")
        yes(controller.importPhoto(draft, args).getString("id") == photoId, "PHOTO_REPLAY_NO_DUPLICATE")
        nativeDeletePhotoDraft(controller, draft.toString())
        yes(!File(requireNotNull(draft.path)).exists() && File(requireNotNull(other.path)).isFile, "DISCARD_ONLY_OWN_TEMP_PHOTO")
        val saved = read("get_photo", JSONObject().put("photo_id", photoId))
        yes(saved.toString().contains(work.getString("id")), "SAVED_PHOTO_RETAINS_WORK_RELATION")
        val attachment = requireNotNull(store.photoAttachment(photoId))
        val original = store.photoAttachmentStore().verifiedFile(attachment)
        val originalBytes = original.readBytes()
        controller.saveDraft("editor:worklog:new", JSONObject().put("values", JSONObject()
            .put("_request_id", "qa_discard_work_photo").put("activity", "QA unsaved photo draft"))
            .put("selected_photo_uri", other.toString()))
        controller.flushDrafts()
        click("Heute"); click("Arbeit erfassen")
        reveal("Ungespeichertes Foto")
        val workCount = PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length()
        click("Speichern")
        reveal("Für das Foto zuerst einen Kunden wählen.")
        yes(PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() == workCount, "PHOTO_CUSTOMER_REQUIREMENT_CHECKED_BEFORE_WORK_SAVE")
        click("Verwerfen"); click("Abbrechen", scrollTo = false)
        yes(File(requireNotNull(other.path)).isFile, "CANCEL_DISCARD_KEEPS_TEMP_PHOTO")
        reveal("Was wurde erledigt? *")
        yes(field("Was wurde erledigt? *")?.text?.toString() == "QA unsaved photo draft", "CANCEL_DISCARD_KEEPS_PHOTO_FORM")
        click("Verwerfen"); click("OK", scrollTo = false)
        waitUntil("DISCARD_REMOVES_TEMP_PHOTO_AND_DRAFT") {
            !File(requireNotNull(other.path)).exists() &&
                store.ownerValue("native_form_draft:" + PixelMcpWriteTools.hash("editor:worklog:new"), "").isEmpty()
        }
        yes(controller.draft("editor:worklog:new") == null, "DISCARD_REMOVES_ONLY_OWN_DRAFT")
        yes(original.isFile && original.readBytes().contentEquals(originalBytes), "DISCARD_PRESERVES_SAVED_PHOTO_BYTES")
        source.delete()
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

    private fun logVisibleActions(stage: String) {
        // Only the previously verified disposable synthetic fixture is allowed here.
        nodes().filter { it.isVisibleToUser && texts(it).isNotEmpty() }.forEach { item ->
            val bounds = Rect(); item.getBoundsInScreen(bounds)
            android.util.Log.i("NativeComposeQA", "$stage ${texts(item)} $bounds id=${item.viewIdResourceName} editable=${item.isEditable} clickable=${clickable(item) != null}")
        }
    }

    private fun captureSyntheticScreen(name: String) {
        val bitmap = requireNotNull(automation.takeScreenshot()) { "Synthetic emulator screenshot unavailable" }
        try {
            File(requireNotNull(context.getExternalFilesDir(null)), "qa-native-$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
    }

    private fun customerContextJourney() {
        val controller = NativeAppController(requireNotNull(activity) as MainActivity, store)
        controller.navigate("worklogs", "qa-route", "edit")
        yes(controller.route.area == "worklogs", "WORK_EDITOR_AREA")
        controller.action("Prüfen", "get_work_record")
        yes(controller.route.area == "worklogs", "ACTION_RETAINS_AREA")
        val restored = NativeAppController(requireNotNull(activity) as MainActivity, store)
        restored.restoreRoute(controller.savedRoute())
        yes(restored.route.area == "worklogs", "RECREATED_ACTION_AREA")
        controller.message("QA previous save")
        controller.navigate("photos", args = JSONObject().put("customer_id", "qa-customer"))
        yes(controller.messages.value.isEmpty() && controller.route.area == "office", "NAVIGATION_CLEARS_OLD_MESSAGE")
        controller.message("QA previous photo")
        controller.back()
        yes(controller.messages.value.isEmpty(), "BACK_CLEARS_OLD_MESSAGE")

        val fixture = JSONObject(store.getState().getJSONObject("master").toString())
        val offers = fixture.optJSONArray("offers") ?: JSONArray()
        val source = File(context.cacheDir, "qa-context-photo.png")
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff346a45.toInt())
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val identity = BackupDeviceGuard(context).current()
        for (index in 0..1) {
            val name = fixturePrefix + String.format(Locale.ROOT, "%03d", index)
            val id = customers(name).single().getString("id")
            offers.put(JSONObject().put("id", "qa_context_offer_$index").put("customer_id", id)
                .put("title", "QA Kontextangebot $index").put("status", "draft").put("test_record", true))
            runBlocking { controller.importPhoto(Uri.fromFile(source), JSONObject().put("_photo_request_id", "qa_context_photo_$index")
                .put("customer_id", id).put("role", "documentation").put("caption", "QA Kontextfoto $index")
                .put("_capture_context", JSONObject().put("formatVersion", 1).put("installation_id", identity.getString("installationId"))
                    .put("restore_epoch", identity.getString("restoreEpoch")))) }
        }
        fixture.put("offers", offers); store.putOwnerValue("snapshot", fixture.toString()); store.notifyCommittedChange()
        for (index in 0..1) {
            val name = fixturePrefix + String.format(Locale.ROOT, "%03d", index)
            click("Kunden"); enter("Suchen", name); click(name)
            click("Kundenfotos")
            shown(name); shown("QA Kontextfoto $index")
            yes(node("QA Kontextfoto ${1-index}") == null, "PHOTOS_KEEP_CUSTOMER_$index")
            if (index == 0) captureSyntheticScreen("customer-photos")
            click("‹ Zurück"); click("Angebote")
            shown("QA Kontextangebot $index")
            yes(node("QA Kontextangebot ${1-index}") == null, "OFFERS_KEEP_CUSTOMER_$index")
            if (index == 0) captureSyntheticScreen("customer-offers")
            yes(nodes().any { it.isSelected && matches(it, "Büro") }, "OFFERS_OFFICE_AREA_$index")
            click("QA Kontextangebot $index"); click("Zur Liste")
            shown("QA Kontextangebot $index")
            yes(node("QA Kontextangebot ${1-index}") == null, "OFFER_DETAIL_RETURN_FILTER_$index")
        }
        source.delete()
        click("Heute")
    }

    private fun officeOpeningMeasurements() {
        // The disposable fixture was checked before any application data was read.
        // Preserve its existing rows/journal; add only synthetic office records.
        click("Heute")
        val fixture = JSONObject(store.getState().getJSONObject("master").toString())
        val invoices = JSONArray()
        for (index in 0 until 120) invoices.put(JSONObject().put("id", "qa_ui_office_$index")
            .put("invoice_number", "QA-UI-$index").put("customer_id", fixture.getJSONArray("customers").getJSONObject(0).getString("id"))
            .put("status", "Gestellt").put("issued_at", "2026-10-30T08:00:00Z").put("invoice_date", "2026-10-30")
            .put("gross_total", if (index == 0) 25 else 100)
            .put("money_minor", JSONObject().put("gross_minor", if (index == 0) 2500 else 10000)).put("test_record", true))
        fixture.put("invoices", invoices)
        store.putOwnerValue("snapshot", fixture.toString())
        for (index in 0 until 240) store.putOwnerValue("local_payment:qa_ui_office_$index", JSONObject()
            .put("payment_id", "qa_ui_office_$index").put("amount_minor", 2500).put("paid_on", "2026-10-30")
            .put("method", "bank").put("recorded_at", "2026-10-30T08:00:00Z")
            .put("allocations", JSONArray().put(JSONObject().put("invoice_id", "qa_ui_office_${index % 120}").put("amount_minor", 2500))).toString())
        store.notifyCommittedChange()
        officeMeasurements = JSONObject().put("scope", "EMULATOR_UI_ACTION_TO_VISIBLE_READY_CONTENT;WARM_PROCESS")
            .put("invoice_count", 120).put("payment_count", 240).put("samples_per_path", 40).put("warmups_per_path", 2)
            .put("payments_and_credits_share_view", true).put("performance_claim", false)
            .put("display", shell("wm size") + ";" + shell("wm density"))
            .put("font_scale", shell("settings get system font_scale"))
            .put("api", Build.VERSION.SDK_INT).put("host_load", "NOT_CONTROLLED")
        for ((name, entry, ready) in listOf(
            Triple("billing", "Arbeiten abrechnen", "Rechnungsempfänger: Nicht zugeordnet"),
            Triple("invoices", "Rechnungen", "120 Treffer · 50 auf dieser Seite"),
            Triple("payments_and_credits", "Zahlungen und Guthaben", "Rest 0,00 € · Guthaben 25,00 €")
        )) {
            val values = ArrayList<Double>()
            repeat(42) { sample ->
                click("Büro")
                // Verify the actual destination before locating its action;
                // a late query result from the previous route is not a ready menu.
                reveal("Abrechnung")
                var start = 0L
                click(entry, onAction = { start = it })
                waitUntil("OFFICE_READY_$name") { node(ready) != null }
                val elapsed = (System.nanoTime() - start) / 1_000_000.0
                if (sample >= 2) values.add(elapsed)
            }
            val sorted = values.sorted()
            officeMeasurements.put(name, JSONObject().put("mean_ms", values.average()).put("p95_ms", sorted[37])
                .put("slowest_5_percent_mean_ms", sorted.takeLast(2).average()).put("samples_ms", JSONArray(values)))
        }
        test.sendStatus(0, Bundle().apply { putString("office_opening", officeMeasurements.toString()) })
        click("Heute")
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
        shown("Heute")
        click("Arbeit erfassen")
        shown("Speichern")
        reveal("Arbeitsdatum *")
        enter("Beginn", "08:00")
        enter("Ende", "12:00")
        reveal("Pause (Minuten)")
        click("60")
        reveal("Freie Minuten")
        waitUntil("LARGE_TEXT_PAUSE_PRESET") { field("Freie Minuten")?.text?.toString() == "60" }
        checks++
        click("150 €")
        reveal("Freier Betrag (€)")
        waitUntil("LARGE_TEXT_DISPOSAL_PRESET") { field("Freier Betrag (€)")?.text?.toString() == "150" }
        checks++
        val saveWork = actionBounds("Speichern")
        val discardWork = actionBounds("Verwerfen")
        yes(!Rect.intersects(saveWork, discardWork) && safe.contains(saveWork) && safe.contains(discardWork), "LARGE_TEXT_WORK_ACTIONS_CLEAR_SYSTEM_BARS")
        yes(saveWork.height() >= minimumTouch && discardWork.height() >= minimumTouch, "LARGE_TEXT_WORK_TOUCH_TARGETS")
        captureSyntheticScreen("work-large-font")
        click("Verwerfen"); click("OK", scrollTo = false)
    }
}

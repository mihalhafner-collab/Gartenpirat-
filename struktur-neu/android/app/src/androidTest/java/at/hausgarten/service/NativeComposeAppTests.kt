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
import android.view.MotionEvent
import android.app.UiAutomation
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
    private val searchMatrix = JSONArray()
    private val domainSearchCases = JSONArray()
    private val surfaceCases = JSONArray()
    private val listViewportCases = JSONArray()

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
        val threads = Thread.getAllStackTraces().entries.filter { (_, frames) -> frames.any { it.className.startsWith("at.hausgarten.service") } }
            .joinToString("\n") { (thread, frames) -> thread.name + ": " + thread.state + "\n" + frames.take(16).joinToString("\n") }
        test.sendStatus(0, Bundle().apply { putString("timeout_threads", "$code\n$threads") })
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
                if (!scroll()) scrollTop()
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
        // Each launch intentionally starts a fresh synthetic task. Without CLEAR_TASK,
        // Android can only bring a child connection Activity to the front and
        // startActivitySync waits forever for a MainActivity that is not created.
        activity = test.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("open_planning", planning)
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
                test.sendStatus(0, Bundle().apply { putString("journey_start", name) })
                val failure = try { run(); null }
                    catch (failure: AssertionError) { failure }
                    catch (failure: Exception) { failure }
                if (failure == null) android.util.Log.i("NativeComposeQA", "$name PASS")
                else {
                    test.sendStatus(0, Bundle().apply { putString("journey_failure", "$name: " + failure.stackTraceToString()) })
                    runCatching { captureSyntheticScreen("failure-$name"); logVisibleActions("failure_$name") }
                    failures.add(AssertionError("QA_NATIVE_COMPOSE_JOURNEY_$name", failure))
                }
            }
            journey("today_hub", ::todayJourney)
            journey("customer", ::customerJourney)
            journey("usability", ::usabilityJourney)
            journey("accessories", ::accessoryJourney)
            journey("timer", ::timerJourney)
            journey("notification", ::notificationJourney)
            journey("customer_context", ::customerContextJourney)
            journey("office_opening", ::officeOpeningMeasurements)
            journey("search_keyboard", ::searchKeyboardJourney)
            journey("back_gesture", ::backGestureJourney)
            journey("domain_search", ::domainSearchJourney)
            journey("list_viewports", ::listViewportJourney)
            journey("filter_keyboard", ::filterKeyboardJourney)
            journey("surfaces", ::surfaceJourney)
            journey("large_text", ::largeTextJourney)
            journey("storage_settings", ::storageSettingsJourney)
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
                .put("today_hub", true).put("storage_settings_cleanup", true).put("filter_keyboard", true).put("system_back_gesture", true).put("work_ime_actions", true).put("search_keyboard_matrix", searchMatrix).put("domain_search_cases", domainSearchCases).put("surface_cases", surfaceCases).put("list_viewport_cases", listViewportCases).put("office_opening", officeMeasurements)
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
        yes(customerNavigation != null && customerNavigation.isClickable && customerNavigation.stateDescription?.toString() == "Ausgewählter Bereich",
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
        click("In Papierkorb")
        waitUntil("CUSTOMER_ARCHIVED") { customers(flowName).isEmpty() || customers(flowName).all { it.opt("active") == false || it.optInt("active", 1) == 0 || it.optBoolean("archived") } }
        click("Kunden")
        enter("Suchen", "QA Native F")
        shown("0 Treffer · 0–0")
        yes(node(flowName) == null, "ARCHIVED_CUSTOMER_NOT_IN_ACTIVE_LIST")
    }

    private fun usabilityJourney() {
        click("Heute")
        val day = LocalDate.now(ZoneId.of("Europe/Vienna")).toString()
        val workSummary = read("get_work_record_summary", JSONObject().put("date_from", day).put("date_to", day))
        if (workSummary.optInt("work_records") > 0) reveal("Erfasste Arbeiten")
        else { reveal("Arbeitszeit heute"); shown("0 h gespeichert · 0 Einträge") }
        val overview = read("get_revier_overview", JSONObject().put("date", LocalDate.now(ZoneId.of("Europe/Vienna")).toString()))
        val due = overview.optJSONArray("due_visits") ?: JSONArray()
        val today = overview.getString("date")
        val hasHints = overview.optInt("open_inquiries") + overview.optInt("maintenance_unclear") + (overview.optJSONArray("maintenance_due")?.length() ?: 0) > 0 ||
            (0 until due.length()).any { due.getJSONObject(it).optString("date") < today }
        if (hasHints) reveal("Handlungsbedarf") else yes(node("Handlungsbedarf") == null, "TODAY_NO_SPURIOUS_HINTS")
        reveal("Arbeitszeit heute")
        yes(node("Mehr") == null, "NO_TODAY_MORE_MENU")
        click("Kunden")
        enter("Suchen", fixturePrefix)
        click("Weitere 50")
        click("Heute")
        click("Kunden")
        shown("55 Treffer · 1–50")
        yes(field("Suchen")?.text?.toString()?.contains(fixturePrefix) == true, "CUSTOMER_FILTER_PRESERVED_ON_ENTRY")
        click("Heute")
        click("+ Erfassen"); click("Arbeit nachtragen")
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
        click("+ Erfassen"); click("Arbeit nachtragen")
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
        click("Heute"); click("+ Erfassen"); click("Arbeit nachtragen")
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

    private fun todayJourney() {
        click("Heute")
        shown("Einsatz starten")
        for (label in listOf("Heute", "Arbeit", "Kunden", "Büro")) shown(label)
        yes(actionBounds("Einsatz starten").height() >= 56, "TODAY_PRIMARY_TOUCH_TARGET")
        yes(visibleTexts().none { it.contains("Externe Sicherung") }, "TODAY_HAS_NO_FABRICATED_EXTERNAL_BACKUP_PROOF")
        captureSyntheticScreen("today-idle")
        val before = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
        click("+ Erfassen")
        for (label in listOf("Foto aufnehmen", "Material buchen", "Arbeit nachtragen", "Notiz hinzufügen")) shown(label)
        captureSyntheticScreen("today-capture")
        back()
        shown("Einsatz starten")
        yes(PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) == before, "TODAY_CAPTURE_BACK_DOES_NOT_WRITE")
        val noteCustomer = customers(fixturePrefix + "000").single()
        val originalNotes = noteCustomer.optString("notes")
        val workBeforeNote = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs"))
        click("+ Erfassen"); click("Notiz hinzufügen")
        enter("Suchen", fixturePrefix + "000"); click(fixturePrefix + "000")
        shown("Notizen")
        yes(!keyboardVisible(), "TODAY_NOTE_HAS_NO_FORCED_IME")
        val note = listOf(originalNotes, "[TEST] QA Heute Kundennotiz").filter { it.isNotBlank() }.joinToString("\n")
        enter("Notizen", note); click("Speichern")
        waitUntil("TODAY_CUSTOMER_NOTE_SAVED") { customers(fixturePrefix + "000").single().optString("notes") == note }
        yes(PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")) == workBeforeNote, "TODAY_NOTE_PRESERVES_WORK_RECORDS")
        click("Heute"); shown("Einsatz starten")
        val oldFont = shell("settings get system font_scale")
        try {
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            shell("settings put system font_scale 2.0"); launch()
            shown("Einsatz starten")
            val labels = listOf("Einsatz starten", "Planung", "+ Erfassen", "Heute", "Arbeit", "Kunden", "Büro")
            waitUntil("TODAY_LARGE_TEXT_LAYOUT_READY") {
                var fontReady = false
                test.runOnMainSync { fontReady = (activity?.resources?.configuration?.fontScale ?: 0f) >= 1.99f }
                fontReady && !keyboardVisible() && labels.all { label ->
                    runCatching {
                        val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                        screen.contains(actionBounds(label))
                    }.getOrDefault(false)
                }
            }
            for (label in labels) {
                val bounds = actionBounds(label)
                val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                val density = activity!!.resources.displayMetrics.density
                val minimum = if (label in labels.take(3)) 56 else 48
                yes(screen.contains(bounds) && bounds.height() >= minimum * density - 1,
                    "TODAY_LARGE_TEXT_ACTION_${label}_bounds=${bounds}_screen=${screen}")
            }
            click("+ Erfassen"); reveal("Schließen"); click("Schließen")
            captureSyntheticScreen("today-large-font")
        } catch (failure: Throwable) {
            captureSyntheticScreen("failure-today-large-font")
            throw failure
        } finally {
            shell(if (oldFont == "null") "settings delete system font_scale" else "settings put system font_scale $oldFont")
            activity?.let { current -> test.runOnMainSync { current.finish() } }; launch()
        }
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
        shown("Pause")
        shown(fixturePrefix + "000")
        captureSyntheticScreen("today-running")
        click("+ Erfassen"); shown("Für " + fixturePrefix + "000"); back()
        yes(read("get_active_work_session").optString("id") == id, "TODAY_CAPTURE_BACK_PRESERVES_SESSION")
        click("Pause")
        waitUntil("TODAY_PAUSED") { read("get_active_work_session").optString("state") == "PAUSED" }
        shown("Fortsetzen"); click("Fortsetzen")
        waitUntil("TODAY_RESUMED") { read("get_active_work_session").optString("state") == "RUNNING" }
        click("Beenden")
        shown("Arbeit gestoppt")
        yes(PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() == before, "TODAY_STOP_REQUIRES_REVIEW")
        click("Arbeit fortsetzen")
        shown("Arbeit läuft")
        click("Heute")
        click("Laufende Arbeit öffnen")
        reveal("Arbeit läuft")
        shown("Arbeitsdauer")
        yes(visibleTexts().any { it.matches(Regex("[0-9]+ Std\\. [0-9]+ Min\\. [0-9]+ Sek\\.")) }, "TIMER_HAS_READABLE_DURATION")
        yes(visibleTexts().none { it.startsWith("State:") || it.startsWith("Worked ms:") || it.startsWith("Started at ms:") }, "TIMER_HIDES_RAW_CLOCK_FIELDS")
        visibleResult("Arbeit stoppen")
        captureSyntheticScreen("timer-actions")
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

    private fun openWorkCustomerPicker(): String {
        scrollTop()
        var caption: String? = null
        waitUntil("WORK_CUSTOMER_PICKER_READY") {
            caption = nodes().asSequence().filter { it.isVisibleToUser && clickable(it) != null }
                .flatMap { texts(it).asSequence() }.firstOrNull { it.startsWith("Kunde: ") }
            caption != null
        }
        return requireNotNull(caption).also { click(it) }
    }

    private fun actionBounds(label: String): Rect {
        val target = nodes().asSequence().filter { it.isVisibleToUser && matches(it, label) }.mapNotNull(::clickable).firstOrNull()
            ?: throw IllegalArgumentException("QA_NATIVE_COMPOSE_NOT_ACTIONABLE_$label")
        return Rect().also { target.getBoundsInScreen(it) }
    }

    private fun logVisibleActions(stage: String) {
        // Only the previously verified disposable synthetic fixture is allowed here.
        nodes().filter { it.isVisibleToUser && texts(it).isNotEmpty() }.forEach { item ->
            val bounds = Rect(); item.getBoundsInScreen(bounds)
            android.util.Log.i("NativeComposeQA", "$stage ${texts(item)} $bounds id=${item.viewIdResourceName} editable=${item.isEditable} clickable=${clickable(item) != null}")
        }
    }

    private fun captureSyntheticScreen(name: String, requireKeyboard: Boolean = false) {
        if (requireKeyboard) yes(keyboardVisible(), "SCREENSHOT_IME_BEFORE_$name")
        val bitmap = requireNotNull(automation.takeScreenshot()) { "Synthetic emulator screenshot unavailable" }
        try {
            File(requireNotNull(context.getExternalFilesDir(null)), "qa-native-$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
        if (requireKeyboard) yes(keyboardVisible(), "SCREENSHOT_IME_AFTER_$name")
    }

    private fun customerContextJourney() {
        val controller = NativeAppController(requireNotNull(activity) as MainActivity, store)
        controller.navigate("worklogs", "qa-route", "edit")
        yes(controller.route.area == "worklogs", "WORK_EDITOR_AREA")
        controller.action("Prüfen", "get_work_record")
        yes(controller.route.area == "worklogs", "ACTION_RETAINS_AREA")
        val restored = NativeAppController(requireNotNull(activity) as MainActivity, store)
        restored.restoreRoute(controller.savedNavigation())
        yes(restored.route.area == "worklogs", "RECREATED_ACTION_AREA")
        restored.back()
        yes(restored.route.section == "worklogs" && restored.route.id == "qa-route", "RECREATED_BACK_STACK")
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
            val property = read("create_property", JSONObject().put("request_id", "qa_ui_context_property_$index").put("customer_id", id).put("name", "QA Kontextobjekt $index").put("address", "QA Gartenweg $index, Testort"))
            read("create_inquiry", JSONObject().put("request_id", "qa_ui_context_inquiry_$index").put("customer_id", id).put("property_id", property.getString("property_id")).put("title", "QA Kontextanfrage $index"))
            read("plan_visit", JSONObject().put("request_id", "qa_ui_context_visit_$index").put("property_id", property.getString("property_id")).put("title", "QA Kontexteinsatz $index").put("date", LocalDate.now(ZoneId.of("Europe/Vienna")).toString()).put("planned_minutes", 60))
            val orders = fixture.optJSONArray("orders") ?: JSONArray()
            orders.put(JSONObject().put("id", "qa_context_order_$index").put("customer_id", id).put("title", "QA Kontextauftrag $index").put("status", "Geplant").put("test_record", true))
            fixture.put("orders", orders)
            val workRows = fixture.optJSONArray("worklogs") ?: JSONArray()
            workRows.put(JSONObject().put("id", "qa_context_billing_$index").put("customer_id", id)
                .put("billing_customer_id", customers(fixturePrefix+"000").single().getString("id"))
                .put("activity", "QA Kontextabrechnung $index").put("hours", 1).put("billed", 0)
                .put("work_date", LocalDate.now(ZoneId.of("Europe/Vienna")).toString()).put("test_record", true))
            fixture.put("worklogs", workRows)
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
            yes(node("Büro")?.let(::clickable)?.stateDescription?.toString() == "Ausgewählter Bereich", "OFFERS_OFFICE_AREA_$index")
            click("QA Kontextangebot $index"); click("Zur Liste")
            shown("QA Kontextangebot $index")
            yes(node("QA Kontextangebot ${1-index}") == null, "OFFER_DETAIL_RETURN_FILTER_$index")
            click("Kunden"); enter("Suchen", name); click(name); click("Aufträge")
            shown("QA Kontextauftrag $index"); yes(node("QA Kontextauftrag ${1-index}") == null, "ORDERS_KEEP_CUSTOMER_$index")
            click("‹ Zurück"); click("Alle Objekte")
            shown("QA Kontextobjekt $index"); yes(node("QA Kontextobjekt ${1-index}") == null, "PROPERTIES_KEEP_CUSTOMER_$index")
            click("Objekt anlegen"); shown("Kunde: $name"); click("Verwerfen")
            click("‹ Zurück"); click("Alle Einsätze")
            shown("QA Kontexteinsatz $index"); yes(node("QA Kontexteinsatz ${1-index}") == null, "VISITS_KEEP_CUSTOMER_$index")
            click("‹ Zurück"); scrollTop()
            if (index == 0) {
                captureSyntheticScreen("customer-overview")
                for (fromInquiry in listOf(false, true)) {
                    if (fromInquiry) { openOfficeGroup("Kunden und Aufträge", "Anfragen"); enter("Suchen", "QA Kontextanfrage 0"); click("QA Kontextanfrage 0"); click("Angebot aus dieser Anfrage") }
                    else { click("Alle Objekte"); click("QA Kontextobjekt 0"); click("Angebot aus diesem Objekt") }
                    val title = if (fromInquiry) "QA Kontextanfrage 0" else "QA Kontextobjekt 0"
                    shown("Einsatzkunde: $name")
                    yes(field("Titel *")?.text?.toString()?.contains(title) == true, "OFFER_SOURCE_TITLE_$fromInquiry")
                    enter("Titel *", "$title geprüft")
                    val draftKey = "native_form_draft:" + PixelMcpWriteTools.hash("finance:offers:new")
                    waitUntil("OFFER_SOURCE_DRAFT_$fromInquiry") { store.ownerValue(draftKey, "").contains("_source_context") }
                    recreate()
                    reveal("Objekt: QA Kontextobjekt 0" + if (fromInquiry) " · Anfrage: QA Kontextanfrage 0" else "")
                    yes(field("Titel *")?.text?.toString()?.contains("$title geprüft") == true, "OFFER_SOURCE_TITLE_RECREATED_$fromInquiry")
                    reveal("Einsatzort"); yes(field("Einsatzort")?.text?.toString()?.contains("QA Gartenweg 0, Testort") == true, "OFFER_SOURCE_LOCATION_$fromInquiry")
                    click("Verwerfen")
                    click("Kunden"); enter("Suchen", name); click(name)
                }
                val invoiceCount = PixelMcpReadTools.effective(store.getState()).getJSONArray("invoices").length()
                click("Abrechnung dieses Kunden")
                shown("Offene Stunden")
                click("Arbeitsaufzeichnungen für sevDesk prüfen")
                enter("Suchen", "QA Kontextabrechnung")
                val sourceLabel = "QA Kontextabrechnung 0"
                reveal(sourceLabel)
                yes(visibleTexts().none { it.contains("QA Kontextabrechnung 1") }, "BILLING_SOURCE_KEEPS_WORK_CUSTOMER_WITH_SHARED_RECIPIENT")
                captureSyntheticScreen("customer-billing")
                yes(PixelMcpReadTools.effective(store.getState()).getJSONArray("invoices").length() == invoiceCount, "OVERVIEW_DOES_NOT_CREATE_INVOICE")
            }
        }
        source.delete()
        click("Heute")
    }

    private fun officeOpeningMeasurements() {
        click("Heute")
        officeMeasurements = JSONObject().put("scope", "EMULATOR_UI_ACTION_TO_VISIBLE_READY_CONTENT;WARM_PROCESS;FROM_OFFICE")
            .put("samples_per_path", 40).put("warmups_per_path", 2).put("performance_claim", false)
            .put("display", shell("wm size") + ";" + shell("wm density"))
            .put("font_scale", shell("settings get system font_scale"))
            .put("api", Build.VERSION.SDK_INT).put("host_load", "NOT_CONTROLLED")
        for ((name, entry, ready) in listOf(
            Triple("billing", "Offene Arbeitsstunden", "Arbeitsaufzeichnungen für sevDesk prüfen"),
            Triple("offers", "Angebote", "Neues Angebot / Kostenvoranschlag")
        )) {
            val values = ArrayList<Double>()
            repeat(42) { sample ->
                click("Büro")
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

    private fun keyboardVisible(): Boolean = automation.windows.any {
        it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && it.root?.isVisibleToUser == true
    }

    private fun showKeyboard(label: String) {
        yes(requireNotNull(field(label)).performAction(AccessibilityNodeInfo.ACTION_CLICK), "SEARCH_FOCUS_$label")
        waitUntil("SEARCH_IME_VISIBLE") { keyboardVisible() }
        settleUi()
    }

    /** Validate real screen/IME geometry, then inject a touch, not a semantics click. */
    private fun visibleResult(label: String, tap: Boolean = false, requireKeyboard: Boolean = false, scrollIntoView: Boolean = false, allowClippedCard: Boolean = false): Rect {
        var result: Rect? = null
        var geometry = "No candidate"
        var lastScroll = 0L
        try { waitUntil("SEARCH_RESULT_BOUNDS_$label") {
            val textNode = nodes().firstOrNull { matches(it, label) && clickable(it) != null } ?: return@waitUntil false
            val candidate = clickable(textNode) ?: return@waitUntil false
            val bounds = Rect().also { candidate.getBoundsInScreen(it) }
            val screen = Rect()
            automation.rootInActiveWindow?.getBoundsInScreen(screen)
            val ime = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && it.root?.isVisibleToUser == true }
            if (requireKeyboard && ime == null) return@waitUntil false
            if (ime != null) {
                val keyboard = Rect().also { ime.getBoundsInScreen(it) }
                if (!keyboard.isEmpty) screen.bottom = minOf(screen.bottom, keyboard.top)
            }
            // A long entity card may exceed a tiny landscape viewport. Its visible
            // title and real touch intersection still need a complete 48dp target.
            // Selection rows and all ordinary journeys retain the full-row check.
            val hit = Rect(bounds)
            val title = Rect().also { textNode.getBoundsInScreen(it) }
            val minimum = (48 * context.resources.displayMetrics.density).toInt()
            val titleVisible = title.top >= screen.top && title.top + minOf(title.height(), minimum) <= screen.bottom
            val within = if (allowClippedCard) hit.intersect(screen) && titleVisible else screen.contains(bounds)
            val valid = !hit.isEmpty && within && hit.width() >= minimum && hit.height() >= minimum
            geometry = "candidate=$bounds touch=$hit title=$title screen=$screen keyboard=${ime != null} density=${context.resources.displayMetrics.density}"
            if (valid) result = hit
            else if (scrollIntoView && SystemClock.uptimeMillis()-lastScroll > 300) { scroll(); lastScroll = SystemClock.uptimeMillis() }
            valid
        } } catch (failure: Throwable) { throw AssertionError("SEARCH_GEOMETRY_$label: $geometry", failure) }
        val bounds = requireNotNull(result)
        checks++
        if (tap) {
            val now = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { yes(automation.injectInputEvent(event, true), "SEARCH_REAL_TOUCH_$label") } finally { event.recycle() }
            }
            settleUi()
        }
        return bounds
    }

    private fun searchKeyboardJourney() {
        val oldFont = shell("settings get system font_scale")
        val sizeText = shell("wm size")
        val override = Regex("Override size: (\\d+x\\d+)").find(sizeText)?.groupValues?.get(1)
        val physical = Regex("(?:Override|Physical) size: (\\d+)x(\\d+)").findAll(sizeText).last().groupValues
        val width = minOf(physical[1].toInt(), physical[2].toInt())
        val height = maxOf(physical[1].toInt(), physical[2].toInt())
        val failures = ArrayList<Throwable>()
        try {
            for ((font, compact) in listOf(1.0 to false, 1.3 to false, 2.0 to true)) for (landscape in listOf(false, true)) {
                val key = "search-${(font*100).toInt()}-${if (compact) "compact" else "normal"}-${if (landscape) "landscape" else "portrait"}"
                try {
                    activity?.let { current -> test.runOnMainSync { current.finish() } }
                    shell("wm size ${width}x${if (compact) (height*0.82).toInt() else height}")
                    shell("settings put system font_scale $font")
                    automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
                    launch()
                    test.runOnMainSync { requireNotNull(activity).requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    click("+ Erfassen"); click("Arbeit nachtragen"); openWorkCustomerPicker()
                    test.runOnMainSync { requireNotNull(activity).requestedOrientation = if (landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    waitUntil("SEARCH_CONFIGURATION_$key") {
                        var correct = false
                        test.runOnMainSync {
                            val config = requireNotNull(activity).resources.configuration
                            correct = kotlin.math.abs(config.fontScale-font.toFloat()) < 0.02f &&
                                (config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) == landscape
                        }
                        correct
                    }
                    enter("Suchen", fixturePrefix + "000"); shown("1 Treffer")
                    visibleResult(fixturePrefix + "000")
                    showKeyboard("Suchen")
                    visibleResult(fixturePrefix + "000", requireKeyboard = true)
                    waitUntil("SEARCH_FIELD_AND_COUNT_BOUNDS_$key") {
                        val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                        val ime = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && it.root?.isVisibleToUser == true } ?: return@waitUntil false
                        screen.bottom = minOf(screen.bottom, Rect().also { ime.getBoundsInScreen(it) }.top)
                        listOf(field("Suchen"), node("1 Treffer")).all { candidate -> candidate != null && Rect().also { candidate.getBoundsInScreen(it) }.let { !it.isEmpty && screen.contains(it) } }
                    }
                    captureSyntheticScreen(key, requireKeyboard = true)
                    yes(requireNotNull(field("Suchen")).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id), "SEARCH_IME_ACTION_$key")
                    waitUntil("SEARCH_IME_HIDDEN_$key") { !keyboardVisible() }
                    waitUntil("SEARCH_TEXT_AFTER_IME_$key") { field("Suchen")?.text?.toString()?.contains(fixturePrefix + "000") == true }
                    shown("1 Treffer")
                    showKeyboard("Suchen"); visibleResult(fixturePrefix + "000", tap = true, requireKeyboard = true)
                    waitUntil("SEARCH_PICKER_CLOSED_$key") { node("Schließen") == null && !keyboardVisible() }
                    reveal("Kunde: " + fixturePrefix + "000")
                    click("Verwerfen"); click("OK", scrollTo = false)
                    searchMatrix.put(JSONObject().put("font", font).put("compact", compact).put("landscape", landscape)
                        .put("keyboard_open_and_closed", true).put("ime_action_preserves_query", true).put("real_touch_selection", true).put("screenshot", "qa-native-$key.png"))
                } catch (failure: Throwable) {
                    test.sendStatus(0, Bundle().apply { putString("search_display_failure", "$key: " + failure.stackTraceToString()) })
                    runCatching { captureSyntheticScreen("failure-$key") }
                    failures.add(AssertionError(key, failure))
                    // Own synthetic form only; keep later independent display cases executable.
                    runCatching { runBlocking { NativeAppController(requireNotNull(activity) as MainActivity, store).removeDraft("editor:worklog:new") } }
                        .onFailure { cleanup -> test.sendStatus(0, Bundle().apply { putString("search_cleanup_failure", cleanup.stackTraceToString()) }) }
                }
            }
        } finally {
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            shell("wm size " + (override ?: "reset"))
            shell(if (oldFont == "null") "settings delete system font_scale" else "settings put system font_scale $oldFont")
            launch()
        }
        if (failures.isNotEmpty()) {
            val failure = AssertionError("SEARCH_MATRIX_${failures.size}_FAILED")
            failures.forEach(failure::addSuppressed)
            throw failure
        }
        yes(searchMatrix.length() == 6, "ALL_SEARCH_DISPLAY_COMBINATIONS")
        click("+ Erfassen"); click("Arbeit nachtragen"); val priorCustomer = openWorkCustomerPicker()
        enter("Suchen", "NO_SYNTHETIC_MATCH_48273"); shown("0 Treffer"); shown("Keine Treffer. Suchtext oder Filter ändern.")
        shown("Keine Zuordnung")
        showKeyboard("Suchen"); captureSyntheticScreen("search-no-results", requireKeyboard = true)
        enter("Suchen", fixturePrefix); shown("55 Treffer")
        back(); waitUntil("SEARCH_PAGING_IME_HIDDEN") { !keyboardVisible() }
        click("Weitere"); reveal(fixturePrefix + "030"); click("Vorherige")
        enter("Suchen", ""); yes(field("Suchen") != null, "EMPTY_SEARCH_REMAINS_OPEN")
        click("Schließen"); shown(priorCustomer)
        click("‹ Zurück")
        click("Kunden"); enter("Suchen", fixturePrefix + "000"); showKeyboard("Suchen")
        visibleResult(fixturePrefix + "000", requireKeyboard = true); captureSyntheticScreen("search-customer-list", requireKeyboard = true)
        visibleResult(fixturePrefix + "000", tap = true, requireKeyboard = true); reveal("Bearbeiten")
        recreate(); click("‹ Zurück")
        yes(field("Suchen")?.text?.toString()?.contains(fixturePrefix + "000") == true, "LIST_SEARCH_AFTER_DETAIL_RECREATION")
        click("Heute")
    }

    private fun backGestureJourney() {
        val overlays = shell("cmd overlay list --user 0 android")
        val originalMode = shell("settings get secure navigation_mode")
        test.sendStatus(0, Bundle().apply { putString("gesture_environment", "mode=$originalMode\n$overlays") })
        yes(overlays.contains("com.android.internal.systemui.navbar.gestural"), "GESTURAL_OVERLAY_INSTALLED")
        val original = overlays.lineSequence().firstOrNull { it.trim().startsWith("[x]") && it.contains("com.android.internal.systemui.navbar.") }?.substringAfter("] ")?.trim()
        // The base three-button configuration needs no enabled navbar overlay.
        yes(original != null || originalMode == "0", "RECOGNIZED_INITIAL_NAVIGATION_MODE")
        try {
            // Exercise a real navigation-mode transition even when the image boots gestural.
            shell("cmd overlay enable-exclusive --category --user 0 com.android.internal.systemui.navbar.threebutton")
            waitUntil("THREE_BUTTON_NAVIGATION_ENABLED") { shell("settings get secure navigation_mode") == "0" }
            shell("cmd overlay enable-exclusive --category --user 0 com.android.internal.systemui.navbar.gestural")
            waitUntil("GESTURE_NAVIGATION_ENABLED") { shell("settings get secure navigation_mode") == "2" }
            activity?.let { current -> test.runOnMainSync { current.finish() } }; launch()
            click("+ Erfassen"); click("Arbeit nachtragen"); val priorCustomer = openWorkCustomerPicker()
            enter("Suchen", fixturePrefix+"000"); visibleResult(fixturePrefix+"000")
            yes(!keyboardVisible(), "GESTURE_PICKER_IME_INITIALLY_CLOSED")
            fun gestureDump(): String = shell("dumpsys activity service com.android.systemui/.SystemUIService --dump-priority NORMAL") +
                "\n" + shell("dumpsys activity service com.android.systemui/.SystemUIService --dump-priority CRITICAL")
            fun gestureHandlers(): String {
                val dump = gestureDump()
                val headers = Regex("(?m)^\\s*(?:[A-Za-z0-9_$.]+\\.)?EdgeBackGestureHandler[^\\r\\n]*$").findAll(dump).toList()
                return headers.mapIndexed { index, header ->
                    val start = header.range.last + 1
                    val end = minOf(start + 2200, headers.getOrNull(index + 1)?.range?.first ?: dump.length)
                    dump.substring(start, end)
                }.joinToString("\nNEXT_HANDLER\n")
            }
            // Android may delegate back to Launcher/Taskbar; require the window's real
            // gesture regions and the observable edge gesture, not one internal dump owner.
            waitUntil("SYSTEM_BACK_GESTURE_EDGES") {
                var enabled = false
                test.runOnMainSync {
                    val edges = activity?.window?.decorView?.rootWindowInsets?.getInsets(WindowInsets.Type.systemGestures())
                    enabled = edges != null && edges.left > 0 && edges.right > 0
                }
                enabled && shell("settings get secure navigation_mode") == "2"
            }
            test.sendStatus(0, Bundle().apply { putString("gesture_handlers_before", gestureHandlers()) })
            val screen = Rect().also { requireNotNull(automation.rootInActiveWindow).getBoundsInScreen(it) }
            fun swipeFromEdge(right: Boolean) {
                val down = SystemClock.uptimeMillis()
                val from = if (right) screen.right - 1f else screen.left + 1f
                val to = if (right) screen.left + screen.width() * 0.2f else screen.left + screen.width() * 0.8f
                for (step in 0..16) {
                    val action = if (step == 0) MotionEvent.ACTION_DOWN else if (step == 16) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE
                    val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, from + (to-from) * step / 16f, screen.centerY().toFloat(), 0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try { yes(automation.injectInputEvent(event, true), "EDGE_TOUCH_$step") } finally { event.recycle() }
                    if (step < 16) SystemClock.sleep(12)
                }
            }
            swipeFromEdge(false)
            // Either physical edge is a valid Android back gesture; no key/callback substitute.
            val settle = SystemClock.uptimeMillis() + 2000
            while (node("Schließen") != null && SystemClock.uptimeMillis() < settle) SystemClock.sleep(50)
            if (node("Schließen") != null) swipeFromEdge(true)
            try {
                waitUntil("BACK_GESTURE_CLOSES_ONLY_PICKER") { node("Schließen") == null && node(priorCustomer) != null }
            } catch (failure: Throwable) {
                test.sendStatus(0, Bundle().apply {
                    putString("gesture_diagnostics", gestureHandlers())
                    putString("gesture_logcat", shell("logcat -d -t 120 -s EdgeBackGestureHandler BackPanelController WindowOnBackDispatcher BackNavigationController").takeLast(12000))
                })
                throw failure
            }
            captureSyntheticScreen("back-gesture")
            click("‹ Zurück"); shown("Einsatz starten")
        } catch (failure: Throwable) {
            runCatching { captureSyntheticScreen("failure-back-gesture-active") }
            throw failure
        } finally {
            if (original == null) shell("cmd overlay disable --user 0 com.android.internal.systemui.navbar.gestural")
            else shell("cmd overlay enable-exclusive --category --user 0 $original")
            waitUntil("ORIGINAL_NAVIGATION_RESTORED") { shell("settings get secure navigation_mode") == originalMode }
            activity?.let { current -> test.runOnMainSync { current.finish() } }; launch()
        }
    }

    private fun openOfficeGroup(label: String, entry: String) {
        click("Büro")
        click("$label ▾")
        click(entry)
    }

    private fun keyboardSearchProof(key: String, fieldLabel: String, query: String, result: String, manual: Boolean = false) {
        enter(fieldLabel, "NO_SYNTHETIC_MATCH_48273")
        showKeyboard(fieldLabel)
        if (manual) {
            yes(requireNotNull(field(fieldLabel)).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id), "DOMAIN_EMPTY_SEARCH_$key")
        }
        waitUntil("DOMAIN_ZERO_$key") { visibleTexts().any { it.startsWith("0 Treffer") } }
        enter(fieldLabel, query)
        if (manual) {
            showKeyboard(fieldLabel)
            yes(requireNotNull(field(fieldLabel)).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id), "DOMAIN_MANUAL_SEARCH_$key")
        }
        showKeyboard(fieldLabel); visibleResult(result, requireKeyboard = true)
        captureSyntheticScreen("domain-$key", requireKeyboard = true)
        yes(requireNotNull(field(fieldLabel)).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id), "DOMAIN_IME_ACTION_$key")
        waitUntil("DOMAIN_IME_CLOSED_$key") { !keyboardVisible() }
        yes(field(fieldLabel)?.text?.toString()?.contains(query) == true, "DOMAIN_QUERY_RETAINED_$key")
        visibleResult(result)
        showKeyboard(fieldLabel); visibleResult(result, tap = true, requireKeyboard = true)
        waitUntil("DOMAIN_TOUCH_OPENED_$key") { !keyboardVisible() && field(fieldLabel) == null }
        domainSearchCases.put(JSONObject().put("case", key).put("zero_and_matches", true).put("keyboard_bounds", true)
            .put("real_touch", true).put("ime_preserves_query", true).put("screenshot", "qa-native-domain-$key.png"))
    }

    private fun domainSearchJourney() {
        val source = JSONObject(store.getState().getJSONObject("master").toString())
        val catalog = source.optJSONArray("service_catalog") ?: JSONArray()
        catalog.put(JSONObject().put("id", "qa_ui_service").put("title", "QA Leistungsbaustein").put("description", "Vollständig synthetischer langer Tätigkeitstext für die Auswahlprüfung.")
            .put("category", "QA").put("unit", "h").put("default_price", "7.50").put("default_quantity", "1").put("price_confirmed", true).put("active", true))
        source.put("service_catalog", catalog)
        val duplicateName = "[TEST] QA Gleichnamig mit sehr langem Gartenkundenname"
        val allCustomers = source.getJSONArray("customers")
        for ((index, city) in listOf("QA TestNord", "QA TestSüd").withIndex()) allCustomers.put(JSONObject().put("id", "qa_duplicate_$index").put("name", duplicateName).put("city", city).put("active", 1).put("billing_mode", "direct").put("test_record", true))
        store.putOwnerValue("snapshot", source.toString()); store.notifyCommittedChange()
        val failures = ArrayList<Throwable>()
        fun case(key: String, body: () -> Unit) {
            try {
                activity?.let { current -> test.runOnMainSync { current.finish() } }
                launch(); body()
            } catch (failure: Throwable) {
                runCatching { captureSyntheticScreen("failure-domain-$key") }
                failures.add(AssertionError("DOMAIN_$key", failure))
            }
        }
        case("work") {
            val rows = PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            val work = (0 until rows.length()).map { rows.getJSONObject(it) }.first { it.optString("activity").startsWith("[TEST] QA usability work") }
            click("Arbeit"); keyboardSearchProof("work", "Suchen", "QA usability work", work.getString("activity"))
        }
        case("offers") {
            click("Kunden"); enter("Suchen", fixturePrefix+"000"); click(fixturePrefix+"000"); click("Angebote")
            keyboardSearchProof("offers", "Suchen", "QA Kontextangebot 0", "QA Kontextangebot 0")
            visibleResult("Zur Liste")
            captureSyntheticScreen("detail-offers")
            click("Zur Liste"); waitUntil("DOMAIN_RETURN_QUERY_offers") { field("Suchen")?.text?.toString()?.contains("QA Kontextangebot 0") == true }
        }
        case("planning") {
            click("Büro"); click("Planung")
            keyboardSearchProof("planning", "Suche", "QA Native Notification", "[TEST] QA Native Notification")
        }
        case("equipment") {
            click("Büro"); click("Maschinenpark und Gerätewartung")
            keyboardSearchProof("equipment", "Suchen", "QA Zubehör Säge", "[TEST] QA Zubehör Säge")
        }
        case("photos") {
            openOfficeGroup("Material und Wissen", "Fotos")
            keyboardSearchProof("photos", "Fotos suchen", "QA Kontextfoto 0", "QA Kontextfoto 0")
        }
        case("catalog") {
            openOfficeGroup("Material und Wissen", "Leistungskatalog")
            keyboardSearchProof("catalog", "Bausteine suchen", "QA Leistungsbaustein", "QA Leistungsbaustein")
            visibleResult("Zur Liste")
            captureSyntheticScreen("detail-catalog")
            click("Zur Liste"); waitUntil("DOMAIN_RETURN_QUERY_catalog") { field("Bausteine suchen")?.text?.toString()?.contains("QA Leistungsbaustein") == true }
        }
        case("plants") {
            val plants = JSONObject(context.assets.open("plant-catalog.json").bufferedReader().use { it.readText() }).getJSONArray("plants")
            val plant = plants.getJSONObject(0)
            openOfficeGroup("Material und Wissen", "Pflanzen")
            keyboardSearchProof("plants", "Name, botanischer Name oder Familie", plant.getString("scientificName"), plant.getString("name"))
        }
        case("local") {
            openOfficeGroup("Weitere Verwaltung", "Lokale Suche")
            keyboardSearchProof("local", "Name, Objekt, Tätigkeit, Notiz oder Beleg", fixturePrefix+"000", fixturePrefix+"000", manual = true)
        }
        case("phrases") {
            click("+ Erfassen"); click("Arbeit nachtragen"); click("Textbausteine ▾")
            keyboardSearchProof("phrases", "Vorschläge durchsuchen", "Rasen gemäht", "Rasen gemäht")
            waitUntil("PHRASE_TRANSFERRED") { field("Was wurde erledigt? *")?.text?.toString()?.contains("Rasen gemäht") == true }
            click("Verwerfen"); click("OK", scrollTo = false)
        }
        case("duplicate-customers") {
            click("+ Erfassen"); click("Arbeit nachtragen"); openWorkCustomerPicker()
            enter("Suchen", "QA Gleichnamig"); shown("2 Treffer"); reveal("QA TestNord"); reveal("QA TestSüd")
            enter("Suchen", "QA TestSüd"); showKeyboard("Suchen"); visibleResult(duplicateName, requireKeyboard = true)
            captureSyntheticScreen("domain-duplicate-customers", requireKeyboard = true); visibleResult(duplicateName, tap = true, requireKeyboard = true)
            waitUntil("DUPLICATE_PICKER_CLOSED") { !keyboardVisible() && field("Suchen") == null }
            reveal("Kunde: $duplicateName · QA TestSüd")
            domainSearchCases.put(JSONObject().put("case", "duplicate-customers").put("keyboard_bounds", true).put("real_touch", true).put("address_distinguishes", true))
            click("Verwerfen"); click("OK", scrollTo = false)
        }
        case("equipment-picker") {
            click("Büro"); click("Planung"); click("Einsätze")
            enter("Suchen", "QA Kontexteinsatz 0"); click("QA Kontexteinsatz 0"); click("Bearbeiten")
            click("Benötigte Geräte ergänzen"); click("Benötigte Geräte: Nicht zugeordnet")
            keyboardSearchProof("equipment-picker", "Suchen", "QA Zubehör Säge", "[TEST] QA Zubehör Säge")
            reveal("Benötigte Geräte: [TEST] QA Zubehör Säge"); click("Verwerfen")
        }
        case("planning-picker") {
            click("Büro"); click("Planung"); click("Einsatz planen"); click("Kunde: Bitte wählen")
            keyboardSearchProof("planning-picker", "Suchen", fixturePrefix+"000", fixturePrefix+"000")
            reveal("Kunde: " + fixturePrefix+"000")
            click("Ungespeicherten Entwurf verwerfen"); click("Verwerfen", scrollTo = false)
        }
        case("photo-assignment") {
            openOfficeGroup("Material und Wissen", "Fotos"); click("Foto hinzufügen"); click("Kunden auswählen")
            keyboardSearchProof("photo-assignment", "Suchen", fixturePrefix+"001", fixturePrefix+"001")
            shown(fixturePrefix+"001"); click("Verwerfen"); click("Auswahl verwerfen", scrollTo = false)
        }
        case("offer-positions") {
            click("Kunden"); enter("Suchen", fixturePrefix+"000"); click(fixturePrefix+"000"); click("Angebote")
            click("Neues Angebot / Kostenvoranschlag"); click("Leistungsbausteine auswählen")
            keyboardSearchProof("offer-positions", "Bausteine suchen", "QA Leistungsbaustein", "QA Leistungsbaustein · 7,50 €")
            reveal("Position 1"); click("Verwerfen")
        }
        case("maintenance-phrases") {
            val action = JSONObject(context.assets.open("equipment-maintenance-texts.json").bufferedReader().use { it.readText() }).getJSONArray("actions").getJSONObject(0)
            click("Büro"); click("Maschinenpark und Gerätewartung"); enter("Suchen", "QA Zubehör Säge"); click("[TEST] QA Zubehör Säge")
            click("Wartung einrichten"); click("Tätigkeitstext auswählen")
            keyboardSearchProof("maintenance-phrases", "Text suchen", action.getString("label"), action.getString("label"))
            waitUntil("MAINTENANCE_TEXT_TRANSFERRED") { field("Wartungstätigkeit *")?.text?.toString()?.contains(action.getString("text")) == true }
            click("Verwerfen")
        }
        activity?.let { current -> test.runOnMainSync { current.finish() } }; launch()
        if (failures.isNotEmpty()) { val failure = AssertionError("DOMAIN_SEARCH_${failures.size}_FAILED"); failures.forEach(failure::addSuppressed); throw failure }
        yes(domainSearchCases.length() == 15, "ALL_DOMAIN_SEARCH_PATHS")
    }

    private fun listViewportJourney() {
        val oldFont = shell("settings get system font_scale")
        val sizeText = shell("wm size")
        val override = Regex("Override size: (\\d+x\\d+)").find(sizeText)?.groupValues?.get(1)
        val physical = Regex("(?:Override|Physical) size: (\\d+)x(\\d+)").findAll(sizeText).last().groupValues
        val width = minOf(physical[1].toInt(), physical[2].toInt())
        val height = maxOf(physical[1].toInt(), physical[2].toInt())
        val work = "QA Kontextabrechnung 0"
        val plant = JSONObject(context.assets.open("plant-catalog.json").bufferedReader().use { it.readText() }).getJSONArray("plants").getJSONObject(0)
        data class Target(val key: String, val label: String, val query: String, val result: String, val manual: Boolean = false, val open: () -> Unit)
        val targets = listOf(
            Target("customers", "Suchen", fixturePrefix+"000", fixturePrefix+"000") { click("Kunden") },
            Target("work", "Suchen", work, work) { click("Arbeit") },
            Target("orders", "Suchen", "QA Kontextauftrag 0", "QA Kontextauftrag 0") { openOfficeGroup("Kunden und Aufträge", "Aufträge") },
            Target("offers", "Suchen", "QA Kontextangebot 0", "QA Kontextangebot 0") { click("Büro"); click("Angebote") },
            Target("planning", "Suche", "QA Native Notification", "[TEST] QA Native Notification") { click("Büro"); click("Planung") },
            Target("equipment", "Suchen", "QA Zubehör Säge", "[TEST] QA Zubehör Säge") { click("Büro"); click("Maschinenpark und Gerätewartung") },
            // The fixture is newest-first: photo 1 is the first of these two results.
            // Customer A/B exclusion is verified separately in customerContextJourney.
            Target("photos", "Fotos suchen", "QA Kontextfoto", "QA Kontextfoto 1") { openOfficeGroup("Material und Wissen", "Fotos") },
            Target("catalog", "Bausteine suchen", "QA Leistungsbaustein", "QA Leistungsbaustein") { openOfficeGroup("Material und Wissen", "Leistungskatalog") },
            Target("plants", "Name, botanischer Name oder Familie", plant.getString("scientificName"), plant.getString("name")) { openOfficeGroup("Material und Wissen", "Pflanzen") },
            Target("local", "Name, Objekt, Tätigkeit, Notiz oder Beleg", fixturePrefix+"000", fixturePrefix+"000", true) { openOfficeGroup("Weitere Verwaltung", "Lokale Suche") }
        )
        val failures = ArrayList<Throwable>()
        try {
            for (landscape in listOf(false, true)) for (target in targets) {
                if (landscape && target.key !in setOf("customers", "work", "offers", "catalog", "local")) continue
                val key = target.key + if (landscape) "-landscape" else "-portrait"
                test.sendStatus(0, Bundle().apply { putString("list_viewport_start", key) })
                try {
                    activity?.let { current -> test.runOnMainSync { current.finish() } }
                    shell("wm size ${width}x${(height*0.82).toInt()}")
                    shell("settings put system font_scale 2.0")
                    automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
                    launch()
                    test.runOnMainSync { requireNotNull(activity).requestedOrientation = if (landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    waitUntil("LIST_CONFIGURATION_$key") {
                        val config = requireNotNull(activity).resources.configuration
                        kotlin.math.abs(config.fontScale-2f) < 0.02f && (config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) == landscape
                    }
                    target.open(); enter(target.label, target.query)
                    if (target.key == "offers") {
                        // Unscoped viewport setup uses the real reset action. Separate
                        // customer-context journeys continue to verify persisted filters.
                        click("Filter und Suche zurücksetzen")
                        enter(target.label, target.query)
                    }
                    if (target.manual) {
                        showKeyboard(target.label)
                        yes(requireNotNull(field(target.label)).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id), "LIST_MANUAL_SEARCH_$key")
                    }
                    showKeyboard(target.label)
                    visibleResult(target.result, requireKeyboard = true, allowClippedCard = true)
                    waitUntil("LIST_FIELD_AND_COUNT_$key") {
                        val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                        val ime = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && it.root?.isVisibleToUser == true } ?: return@waitUntil false
                        screen.bottom = minOf(screen.bottom, Rect().also { ime.getBoundsInScreen(it) }.top)
                        val count = nodes().firstOrNull { it.text?.toString()?.matches(Regex("[0-9]+ Treffer.*")) == true }
                        listOf(field(target.label), count).all { candidate -> candidate != null && Rect().also { candidate.getBoundsInScreen(it) }.let { !it.isEmpty && screen.contains(it) } }
                    }
                    captureSyntheticScreen("list-$key", requireKeyboard = true)
                    yes(requireNotNull(field(target.label)).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id), "LIST_DONE_$key")
                    waitUntil("LIST_DONE_PRESERVES_QUERY_$key") { !keyboardVisible() && field(target.label)?.text?.toString()?.contains(target.query) == true }
                    visibleResult(target.result, scrollIntoView = true, allowClippedCard = true)
                    showKeyboard(target.label); visibleResult(target.result, tap = true, requireKeyboard = true, allowClippedCard = true)
                    waitUntil("LIST_TOUCH_OPENED_$key") { !keyboardVisible() && field(target.label) == null }
                    listViewportCases.put(JSONObject().put("case", key).put("font", 2.0).put("compact", true).put("landscape", landscape).put("keyboard_bounds", true).put("real_touch", true).put("ime_preserves_query", true))
                } catch (failure: Throwable) {
                    test.sendStatus(0, Bundle().apply { putString("list_viewport_failure", "$key: " + failure.stackTraceToString()) })
                    if (target.key == "offers") runCatching {
                        val backend = runBlocking { store.nativeData().list("offers", search = target.query, includeInactive = false) }
                        test.sendStatus(0, Bundle().apply { putInt("offer_viewport_backend_matches", backend.total) })
                    }
                    runCatching { captureSyntheticScreen("failure-list-$key") }
                    failures.add(AssertionError("LIST_VIEWPORT_$key", failure))
                }
            }
        } finally {
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            shell("wm size " + (override ?: "reset"))
            shell(if (oldFont == "null") "settings delete system font_scale" else "settings put system font_scale $oldFont")
            launch()
        }
        if (failures.isNotEmpty()) { val failure = AssertionError("LIST_VIEWPORTS_${failures.size}_FAILED"); failures.forEach(failure::addSuppressed); throw failure }
        yes(listViewportCases.length() == 15, "ALL_LIST_VIEWPORTS")
    }

    private fun filterKeyboardJourney() {
        fun edit(label: String, value: String, key: String) {
            enter(label, "")
            showKeyboard(label)
            waitUntil("FILTER_VISIBLE_ABOVE_IME_$key") {
                val candidate = field(label) ?: return@waitUntil false
                val bounds = Rect().also { candidate.getBoundsInScreen(it) }
                val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                val ime = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && it.root?.isVisibleToUser == true } ?: return@waitUntil false
                screen.bottom = minOf(screen.bottom, Rect().also { ime.getBoundsInScreen(it) }.top)
                screen.contains(bounds) && bounds.height() >= (48 * context.resources.displayMetrics.density).toInt()
            }
            enter(label, value)
            captureSyntheticScreen("filter-$key", requireKeyboard = true)
            yes(requireNotNull(field(label)).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id), "FILTER_DONE_$key")
            waitUntil("FILTER_DONE_PRESERVES_VALUE_$key") { !keyboardVisible() && field(label)?.text?.toString()?.contains(value) == true }
        }
        activity?.let { current -> test.runOnMainSync { current.finish() } }; launch()
        click("Arbeit"); click("Filter")
        edit("Ab Datum (JJJJ-MM-TT)", "2020-01-01", "work-date")
        showKeyboard("Suchen")
        waitUntil("WORK_SEARCH_CLOSES_FILTERS") { field("Ab Datum (JJJJ-MM-TT)") == null }
        back(); waitUntil("WORK_FILTER_IME_CLOSED") { !keyboardVisible() }
        recreate(); click("Filter")
        reveal("Ab Datum (JJJJ-MM-TT)")
        yes(field("Ab Datum (JJJJ-MM-TT)")?.text?.toString()?.contains("2020-01-01") == true, "WORK_FILTER_RECREATION")
        activity?.let { current -> test.runOnMainSync { current.finish() } }; launch()
        openOfficeGroup("Weitere Verwaltung", "Lokale Suche"); click("Filter")
        edit("Zeitraum ab · JJJJ-MM-TT", "2020-01-01", "local-date")
        showKeyboard("Name, Objekt, Tätigkeit, Notiz oder Beleg")
        waitUntil("LOCAL_SEARCH_CLOSES_FILTERS") { field("Zeitraum ab · JJJJ-MM-TT") == null }
        back(); waitUntil("LOCAL_FILTER_IME_CLOSED") { !keyboardVisible() }
        click("Filter"); reveal("Zeitraum ab · JJJJ-MM-TT")
        yes(field("Zeitraum ab · JJJJ-MM-TT")?.text?.toString()?.contains("2020-01-01") == true, "LOCAL_FILTER_VALUE_RETAINED")
    }

    /** Read-only route acceptance complements the mutating domain journeys above. */
    private fun surfaceJourney() {
        val failures = ArrayList<Throwable>()
        fun case(key: String, ready: String, screenshot: Boolean = false, body: () -> Unit) {
            test.sendStatus(0, Bundle().apply { putString("surface_start", key) })
            try {
                activity?.let { current -> test.runOnMainSync { current.finish() } }; launch()
                body(); reveal(ready)
                yes(!keyboardVisible(), "SURFACE_NO_UNREQUESTED_IME_$key")
                // Readable labels and actual actionable bounds are separate checks.
                val target = node(ready)?.let(::clickable)
                if (target != null) visibleResult(ready, scrollIntoView = true)
                if (screenshot) captureSyntheticScreen("surface-$key")
                surfaceCases.put(JSONObject().put("surface", key).put("ready", ready).put("no_auto_ime", true))
            } catch (failure: Throwable) {
                test.sendStatus(0, Bundle().apply { putString("surface_failure", "$key: " + failure.stackTraceToString()) })
                runCatching { captureSyntheticScreen("failure-surface-$key") }
                failures.add(AssertionError("SURFACE_$key", failure))
            }
        }
        fun settings() {
            click("Büro"); click("Einstellungen")
            waitUntil("SETTINGS_ACTION_READY") { node("Tätigkeit, Einsatzort und Fotoqualität")?.let(::clickable) != null }
        }
        fun planning() { click("Büro"); click("Planung") }
        fun billing() { click("Büro"); click("Offene Arbeitsstunden") }
        case("today", "Einsatz starten", true) { }
        case("work", "Arbeitsentwürfe") { click("Arbeit") }
        case("timer", "Zeiterfassung") { click("Einsatz starten") }
        case("customers", "Kunde anlegen") { click("Kunden") }
        case("office", "Kunden und Aufträge ▾", true) { click("Büro") }
        case("properties", "Objekt anlegen") { openOfficeGroup("Kunden und Aufträge", "Objekte") }
        case("inquiries", "Anfrage anlegen") { openOfficeGroup("Kunden und Aufträge", "Anfragen") }
        case("orders", "Auftrag anlegen") { openOfficeGroup("Kunden und Aufträge", "Aufträge") }
        case("care-plans", "Pflegeplan anlegen") { openOfficeGroup("Kunden und Aufträge", "Pflegepläne") }
        case("planning", "Einsatz planen", true) { planning() }
        case("visits", "Einsatz anlegen") { planning(); click("Einsätze") }
        case("tour", "Datum öffnen") { planning(); click("Tagestour") }
        case("weather", "Termine") { planning(); click("Wetterhinweise") }
        case("billing", "Arbeitsaufzeichnungen für sevDesk prüfen", true) { billing() }
        case("offers", "Neues Angebot / Kostenvoranschlag") { click("Büro"); click("Angebote") }
        case("equipment", "Gerät anlegen") { click("Büro"); click("Maschinenpark und Gerätewartung") }
        case("accessories", "Zubehör bearbeiten", true) {
            click("Büro"); click("Maschinenpark und Gerätewartung"); enter("Suchen", "QA Zubehör Säge"); click("[TEST] QA Zubehör Säge"); click("Zubehör")
        }
        case("maintenance", "Wartung einrichten") {
            click("Büro"); click("Maschinenpark und Gerätewartung"); enter("Suchen", "QA Zubehör Säge"); click("[TEST] QA Zubehör Säge")
        }
        case("materials", "Material anlegen") { openOfficeGroup("Material und Wissen", "Material") }
        case("photos", "Foto hinzufügen") { openOfficeGroup("Material und Wissen", "Fotos") }
        case("plants", "Zufällige Pflanze") { openOfficeGroup("Material und Wissen", "Pflanzen") }
        case("catalog", "Neuer Leistungsbaustein") { openOfficeGroup("Material und Wissen", "Leistungskatalog") }
        case("drafts", "Bereich: Alle Entwürfe") { openOfficeGroup("Weitere Verwaltung", "Entwürfe") }
        case("restored-drafts", "Übernommene Formularstände prüfen") {
            openOfficeGroup("Weitere Verwaltung", "Entwürfe"); click("Formular- und Medienauswahl prüfen")
        }
        case("local-search", "Suchtext eingeben") { openOfficeGroup("Weitere Verwaltung", "Lokale Suche") }
        case("archive", "Alle Arten") { openOfficeGroup("Weitere Verwaltung", "Revier-Archiv") }
        case("trash", "Papierkorb leeren") { openOfficeGroup("Weitere Verwaltung", "Papierkorb") }
        case("settings", "Tätigkeit, Einsatzort und Fotoqualität", true) { settings() }
        case("connections", "Private MCP-Verbindung") { settings(); click("Weitere Einstellungen und Prüfungen"); click("Verbindungen") }
        case("calendar", "Verbinden", true) { settings(); click("Kalender und Wetter verbinden") }
        // FLAG_SECURE remains enabled; do not weaken credential screens for screenshots.
        case("chatgpt", "Erweitert / Diagnose · Einrichtung und Rechte") { settings(); click("ChatGPT-Verbindung") }
        case("backup", "Sicherung speichern", true) { settings(); click("Sicherung und Wiederherstellung") }
        val oldFont = shell("settings get system font_scale")
        try {
            shell("settings put system font_scale 2.0")
            case("settings-200", "Tätigkeit, Einsatzort und Fotoqualität", true) { settings() }
            case("calendar-200", "Verbinden", true) { settings(); click("Kalender und Wetter verbinden") }
            case("backup-200", "Sicherung speichern", true) { settings(); click("Sicherung und Wiederherstellung") }
        } finally { shell("settings put system font_scale $oldFont") }
        activity?.let { current -> test.runOnMainSync { current.finish() } }; launch()
        if (failures.isNotEmpty()) { val failure = AssertionError("SURFACES_${failures.size}_FAILED"); failures.forEach(failure::addSuppressed); throw failure }
        yes(surfaceCases.length() == 35, "ALL_SURFACES_REACHABLE")
    }

    private fun storageSettingsJourney() {
        val previousFont = shell("settings get system font_scale")
        val before = PixelMcpReadTools.effective(store.getState())
        fun retainedBusiness(value: JSONObject) = JSONObject().apply {
            for (key in listOf("customers", "orders", "worklogs", "invoices", "offers", "photos", "appointments")) put(key, value.opt(key))
            // Purged tombstones are reclaimable metadata; archived business records are retained.
            val archives = value.optJSONObject("owner_archive") ?: JSONObject()
            put("owner_archive", JSONObject().apply {
                for (key in archives.keys()) if (!archives.getJSONObject(key).optBoolean("purged")) put(key, archives.get(key))
            })
        }
        try {
            for (scale in listOf(1.0, 2.0)) {
                activity?.let { current -> test.runOnMainSync { current.finish() } }
                shell("settings put system font_scale $scale")
                launch()
                click("Büro"); click("Einstellungen")
                reveal("Speicher bereinigen")
                waitUntil("STORAGE_ACTION_READY_$scale") { node("Speicher bereinigen")?.let(::clickable)?.isEnabled == true }
                val bounds = actionBounds("Speicher bereinigen")
                val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                yes(screen.contains(bounds) && bounds.height() >= 48, "STORAGE_ACTION_VISIBLE_$scale")
                yes(!keyboardVisible(), "STORAGE_NO_UNNECESSARY_IME_$scale")
                captureSyntheticScreen("storage-${(scale*100).toInt()}")
                if (scale == 1.0) {
                    click("Speicher bereinigen")
                    waitUntil("STORAGE_CLEANUP_CONFIRMED") {
                        nodes().any { it.text?.toString()?.startsWith("Bereinigung abgeschlossen:") == true }
                    }
                    yes(PixelMcpWriteTools.hash(retainedBusiness(before)) == PixelMcpWriteTools.hash(retainedBusiness(PixelMcpReadTools.effective(store.getState()))), "STORAGE_UI_PRESERVES_BUSINESS_DATA")
                    val notice = nodes().mapNotNull { it.text?.toString() }.first { it.startsWith("Bereinigung abgeschlossen:") }
                    reveal(notice)
                    captureSyntheticScreen("storage-cleaned")
                }
            }
        } finally {
            shell(if (previousFont == "null") "settings delete system font_scale" else "settings put system font_scale $previousFont")
        }
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
        shown("Termine")
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
        waitUntil("PLANNING_BACK_TO_MAIN") { node("Termine") == null && automation.rootInActiveWindow?.packageName?.toString() == context.packageName }
        checks++
        shown("Heute")
        click("+ Erfassen"); click("Arbeit nachtragen")
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

package at.hausgarten.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inspector.WindowInspector
import android.widget.DatePicker
import android.widget.TimePicker
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Real MainActivity/Compose semantics on a fresh disposable synthetic app sandbox. */
class NativeComposeAppTests private constructor(private val test: Instrumentation) {
    companion object {
        /** One journey per process, after pm clear on the disposable API 35 emulator. */
        @JvmStatic
        fun run(test: Instrumentation, journey: String): JSONObject =
            NativeComposeAppTests(test).execute(journey)

        /**
         * Independent UI acceptance: fresh disposable emulator sandbox, no backup/provider
         * prerequisite.
         */
        @JvmStatic
        fun runGraphics(test: Instrumentation, journey: String): JSONObject =
            NativeComposeAppTests(test).executeGraphics(journey)

        @JvmStatic
        fun runSettlement(test: Instrumentation): JSONObject =
            NativeComposeAppTests(test).executeSettlement()

        @JvmStatic
        fun runAccounting(test: Instrumentation, reopen: Boolean): JSONObject =
            NativeComposeAppTests(test).executeAccounting(reopen)
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

    private fun shell(command: String): String =
        automation.executeShellCommand(command).use {
            ParcelFileDescriptor.AutoCloseInputStream(it).use { input ->
                input.readBytes().toString(Charsets.UTF_8).trim()
            }
        }

    private fun waitUntil(code: String, timeout: Long = 20000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(75)
        }
        val threads =
            Thread.getAllStackTraces()
                .entries
                .filter { (_, frames) ->
                    frames.any { it.className.startsWith("at.hausgarten.service") }
                }
                .joinToString("\n") { (thread, frames) ->
                    thread.name + ": " + thread.state + "\n" + frames.take(16).joinToString("\n")
                }
        test.sendStatus(0, Bundle().apply { putString("timeout_threads", "$code\n$threads") })
        throw AssertionError(
            "QA_NATIVE_COMPOSE_TIMEOUT_$code; visible=" +
                visibleTexts().joinToString(" | ").take(3000)
        )
    }

    /** Like [waitUntil], but the condition must hold without interruption for [stableMs]. */
    private fun waitStable(code: String, stableMs: Long = 1000, condition: () -> Boolean) {
        var since = 0L
        waitUntil(code) {
            val now = SystemClock.uptimeMillis()
            if (!condition()) {
                since = 0L
                false
            } else {
                if (since == 0L) since = now
                now - since >= stableMs
            }
        }
        checks++
    }

    private fun all(
        node: AccessibilityNodeInfo?,
        result: MutableList<AccessibilityNodeInfo> = ArrayList(),
    ): List<AccessibilityNodeInfo> {
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

    private fun texts(node: AccessibilityNodeInfo) =
        listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.hintText?.toString(),
        )

    private fun visibleTexts() = nodes().filter { it.isVisibleToUser }.flatMap(::texts)

    private fun matches(node: AccessibilityNodeInfo, label: String) =
        node.viewIdResourceName == label ||
            texts(node).any {
                it == label ||
                    it.split('\n').any { line ->
                        line == label || line.replace(Regex("^[●✓!×·] "), "") == label
                    }
            }

    private fun node(label: String): AccessibilityNodeInfo? =
        nodes().firstOrNull { it.isVisibleToUser && matches(it, label) }

    private fun shown(label: String) {
        waitUntil("VISIBLE_$label") { node(label) != null }
        checks++
    }

    private fun selectTab(tag: String) {
        reveal(tag)
        if (node(tag)?.isSelected != true) click(tag)
        waitUntil("TAB_SELECTED_$tag") { node(tag)?.isSelected == true }
        checks++
    }

    private fun reveal(label: String) {
        if (node(label) == null) scrollTop()
        var lastScroll = 0L
        waitUntil("REVEAL_$label") {
            if (node(label) != null) return@waitUntil true
            if (SystemClock.uptimeMillis() - lastScroll > 250) {
                if (!scroll()) scrollTop()
                lastScroll = SystemClock.uptimeMillis()
            }
            false
        }
        checks++
    }

    private fun revealContaining(fragment: String) {
        scrollTop()
        var lastScroll = 0L
        waitUntil("REVEAL_CONTAINS_$fragment") {
            if (visibleTexts().any { it.contains(fragment) }) return@waitUntil true
            if (SystemClock.uptimeMillis() - lastScroll > 250) {
                if (!scroll()) scrollTop()
                lastScroll = SystemClock.uptimeMillis()
            }
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
            if (
                target == null &&
                    current.isVisibleToUser &&
                    current.isEnabled &&
                    current.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
            )
                target = current
            current = current.parent
        }
        return target
    }

    private fun scrollTargets(action: Int): List<AccessibilityNodeInfo> {
        val vertical =
            if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
            else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        return nodes().filter { node ->
            node.isVisibleToUser && !node.isEditable && node.actionList.any { it.id == vertical }
        }
    }

    private fun performVerticalScroll(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val direction =
            if (forward) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
            else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        val generic =
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        // A nested horizontal week strip must never consume a vertical page search.
        return node.performAction(direction) ||
            node.actionList.any { it.id == generic } && node.performAction(generic)
    }

    private fun settleUi() {
        test.waitForIdleSync()
        automation.waitForIdle(500, 5000)
    }

    private fun scroll(): Boolean {
        val moved =
            scrollTargets(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD).any {
                performVerticalScroll(it, forward = true)
            }
        if (moved) settleUi()
        return moved
    }

    private fun click(
        label: String,
        scrollTo: Boolean = true,
        onAction: ((Long) -> Unit)? = null,
    ): AccessibilityNodeInfo {
        if (scrollTo && nodes().none { matches(it, label) && clickable(it) != null }) scrollTop()
        var lastScroll = 0L
        var clicked: AccessibilityNodeInfo? = null
        waitUntil("CLICK_$label") {
            val candidate = nodes().firstOrNull { matches(it, label) && clickable(it) != null }
            val target = candidate?.let(::clickable)
            if (target != null && target.refresh() && target.isVisibleToUser && target.isEnabled) {
                val start = System.nanoTime()
                val accepted = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (accepted) {
                    clicked = target
                    onAction?.invoke(start)
                }
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
        current
            .firstOrNull {
                it.isVisibleToUser &&
                    it.isEditable &&
                    (it.viewIdResourceName == label ||
                        it.viewIdResourceName == "native-field:$label")
            }
            ?.let {
                return it
            }
        return current.firstOrNull { candidate ->
            candidate.isVisibleToUser &&
                candidate.isEditable &&
                candidate.viewIdResourceName?.startsWith("native-field:") != true &&
                (matches(candidate, label) ||
                    all(candidate).any { matches(it, label) } ||
                    candidate.parent?.let { parent ->
                        val siblings = all(parent)
                        siblings.count { it.isEditable } == 1 && siblings.any { matches(it, label) }
                    } == true)
        }
    }

    private fun enter(
        label: String,
        value: String,
        displayed: String = value,
        exact: Boolean = false,
    ) {
        if (field(label) == null) scrollTop()
        var lastScroll = 0L
        waitUntil("FIELD_$label") {
            if (field(label) != null) return@waitUntil true
            if (SystemClock.uptimeMillis() - lastScroll > 250) {
                scroll()
                lastScroll = SystemClock.uptimeMillis()
            }
            false
        }
        waitUntil("SET_TEXT_$label") {
            val selected = field(label)
            selected != null &&
                selected.refresh() &&
                selected.isEnabled &&
                selected.performAction(
                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            value,
                        )
                    },
                )
        }
        checks++
        waitUntil("VALUE_$label") {
            field(label)?.text?.toString()?.let {
                if (exact) it == displayed else it.contains(displayed)
            } == true || value.isEmpty() && field(label) != null
        }
        settleUi()
    }

    private fun back() {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            val now = SystemClock.uptimeMillis()
            yes(
                automation.injectInputEvent(
                    KeyEvent(
                        now,
                        now,
                        action,
                        KeyEvent.KEYCODE_BACK,
                        0,
                        0,
                        -1,
                        0,
                        0,
                        InputDevice.SOURCE_KEYBOARD,
                    ),
                    true,
                ),
                "SYSTEM_BACK",
            )
        }
    }

    /** Interact with the real Android dialog widgets; only its explicit action commits. */
    private fun <T : View> nativeWidget(type: Class<T>): T? {
        var found: T? = null
        fun visit(view: View) {
            if (type.isInstance(view) && view.isShown) found = type.cast(view)
            if (view is ViewGroup)
                for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        test.runOnMainSync { WindowInspector.getGlobalWindowViews().forEach(::visit) }
        return found
    }

    private fun androidDialogAction(positive: Boolean) {
        val id = if (positive) "android:id/button1" else "android:id/button2"
        waitUntil("ANDROID_DIALOG_ACTION_$id") {
            nodes()
                .firstOrNull { it.isVisibleToUser && it.viewIdResourceName == id && it.isEnabled }
                ?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }
        checks++
        settleUi()
    }

    private fun chooseDate(label: String, iso: String, confirm: Boolean = true) {
        yes(field(label) == null, "DATE_IS_PICKER_$label")
        click(label)
        setAndroidDate(iso)
        androidDialogAction(confirm)
        waitUntil("DATE_DIALOG_CLOSED_$label") { nativeWidget(DatePicker::class.java) == null }
    }

    private fun setAndroidDate(iso: String) {
        waitUntil("ANDROID_DATE_PICKER") { nativeWidget(DatePicker::class.java) != null }
        val date = LocalDate.parse(iso)
        test.runOnMainSync {
            requireNotNull(nativeWidgetOnMain(DatePicker::class.java))
                .updateDate(date.year, date.monthValue - 1, date.dayOfMonth)
        }
        val selected = requireNotNull(nativeWidget(DatePicker::class.java))
        yes(
            selected.year == date.year &&
                selected.month == date.monthValue - 1 &&
                selected.dayOfMonth == date.dayOfMonth,
            "ANDROID_DATE_WIDGET_VALUE",
        )
    }

    private fun <T : View> nativeWidgetOnMain(type: Class<T>): T? {
        fun find(view: View): T? {
            if (type.isInstance(view) && view.isShown) return type.cast(view)
            if (view is ViewGroup)
                for (index in 0 until view.childCount) find(view.getChildAt(index))?.let {
                    return it
                }
            return null
        }
        return WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find)
    }

    private fun chooseTime(label: String, value: String, confirm: Boolean = true) {
        yes(field(label) == null, "TIME_IS_PICKER_$label")
        click(label)
        setAndroidTime(value)
        androidDialogAction(confirm)
        waitUntil("TIME_DIALOG_CLOSED_$label") { nativeWidget(TimePicker::class.java) == null }
    }

    private fun setAndroidTime(value: String) {
        waitUntil("ANDROID_TIME_PICKER") { nativeWidget(TimePicker::class.java) != null }
        val time = LocalTime.parse(value)
        test.runOnMainSync {
            requireNotNull(nativeWidgetOnMain(TimePicker::class.java)).apply {
                hour = time.hour
                minute = time.minute
            }
        }
        val selected = requireNotNull(nativeWidget(TimePicker::class.java))
        yes(
            selected.hour == time.hour && selected.minute == time.minute,
            "ANDROID_TIME_WIDGET_VALUE",
        )
    }

    private fun chooseDateTime(label: String, iso: String, confirmTime: Boolean = true) {
        yes(field(label) == null, "DATETIME_IS_PICKER_$label")
        click(label)
        setAndroidDate(iso.substringBefore('T'))
        androidDialogAction(true)
        setAndroidTime(iso.substringAfter('T'))
        androidDialogAction(confirmTime)
        waitUntil("DATETIME_DIALOG_CLOSED_$label") {
            nativeWidget(DatePicker::class.java) == null &&
                nativeWidget(TimePicker::class.java) == null
        }
    }

    private fun openCustomerList() {
        // Restore this area, then open its root. Back must not race a tab change
        // and pop the freshly opened list to Today.
        click(UiTags.area("customers"))
        click(UiTags.area("customers"))
        waitUntil("CUSTOMER_LIST_READY") {
            selectedArea(UiTags.area("customers")) &&
                field("Suchen")?.isEditable == true &&
                (keyboardVisible() || node(UiTags.CREATE)?.let(::clickable) != null)
        }
        checks++
    }

    private fun selectedArea(label: String) =
        nodes().any {
            it.isVisibleToUser &&
                matches(it, label) &&
                clickable(it)?.stateDescription?.toString() == "Ausgewählter Bereich"
        }

    private fun openAccounting() {
        // The first tap restores this area's previous detail; the second opens
        // its root and preserves the overview's saved filters and selected tab.
        click(UiTags.area("accounting"))
        click(UiTags.area("accounting"))
        waitUntil("ACCOUNTING_ROOT_READY") {
            selectedArea(UiTags.area("accounting")) && node("Summen") != null
        }
    }

    private fun openAccountingOpen() {
        openAccounting()
        scrollTop()
        waitUntil("ACCOUNTING_OPEN_TAB_OR_VIEW_READY") {
            node("Offene Stunden") != null || node("Offen")?.let(::clickable) != null
        }
        if (node("Offene Stunden") == null) click("Offen")
        reveal("Neue Abrechnung")
    }

    private fun openAccountingTotals() {
        openAccounting()
        waitUntil("ACCOUNTING_TOTALS_TAB_OR_VIEW_READY") {
            field("Auftragsbezeichnung") != null || node("Summen")?.let(::clickable) != null
        }
        if (field("Auftragsbezeichnung") == null) click("Summen")
        shown("Auftragsbezeichnung")
    }

    private fun openSettlements() {
        openAccounting()
        // A restored overview can already have this tab selected. Its search
        // field proves the actual list; no second tab action is needed then.
        waitUntil("SETTLEMENT_TAB_OR_VIEW_READY") {
            field("Abschluss oder Rechnungsnummer") != null ||
                node("Abschlüsse")?.let(::clickable) != null
        }
        if (field("Abschluss oder Rechnungsnummer") == null) click("Abschlüsse")
        waitUntil("SETTLEMENT_LIST_READY") {
            field("Abschluss oder Rechnungsnummer")?.isEditable == true
        }
    }

    /** More opens the real list directly, including after returning from a record or action. */
    private fun openWorklogs() {
        openOffice()
        click(UiTags.area("worklogs"))
        waitUntil("WORK_LIST_READY") { field("Suchen")?.isEditable == true }
    }

    /** Design: rare areas sit behind ⋮ "Mehr" in Today's header; there is no office tab. */
    private fun openOffice() {
        click(UiTags.area("today"))
        click(UiTags.area("today"))
        click(UiTags.MORE)
        reveal("Organisieren")
    }

    /** New work starts from the "+" of a main area, or from a list's own bottom action. */
    private fun newWork() {
        waitUntil("NEW_WORK_ENTRY") {
            node(UiTags.CREATE)?.let(::clickable) != null ||
                node(UiTags.PRIMARY)?.let(::clickable) != null
        }
        if (node(UiTags.CREATE)?.let(::clickable) != null) {
            click(UiTags.CREATE)
            click(UiTags.createChoice("work"))
        } else click(UiTags.PRIMARY)
    }

    private fun discardWork() {
        click("Entwurf verwerfen")
        // Discard removes its durable draft asynchronously. Do not navigate away
        // and cancel that coroutine before its original editor has closed.
        waitUntil("DISCARDED_EDITOR_CLOSED") { node("Entwurf verwerfen") == null }
        settleUi()
    }

    private fun primaryPosition(primary: String, secondary: String) {
        val first = visibleResult(primary)
        val second = visibleResult(secondary)
        val stacked = requireNotNull(activity).resources.configuration.fontScale > 1.3f
        yes(
            !Rect.intersects(first, second) &&
                if (stacked) first.top >= second.bottom else first.left >= second.right,
            "PRIMARY_POSITION_${primary}_SECONDARY_$secondary",
        )
        val minimum = (56 * requireNotNull(activity).resources.displayMetrics.density).toInt()
        yes(
            first.height() >= minimum - 1 && second.height() >= minimum - 1,
            "PRIMARY_TOUCH_TARGET_$primary",
        )
    }

    private fun actionEnabled(label: String): Boolean {
        val candidate = requireNotNull(node(label)) { "Missing action: $label" }
        var current: AccessibilityNodeInfo? = candidate
        while (current != null) {
            if (!current.isEnabled) return false
            if (
                current.isClickable ||
                    current.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
            )
                return true
            current = current.parent
        }
        return candidate.isEnabled
    }

    private fun read(name: String, args: JSONObject = JSONObject()): JSONObject {
        val response = tools.call(name, args)
        yes(!response.has("ok") || response.getBoolean("ok"), "MCP_$name")
        return response.optJSONObject("data") ?: response
    }

    private fun customers(name: String): List<JSONObject> {
        val master = PixelMcpReadTools.effective(store.getState())
        val rows = master.getJSONArray("customers")
        return (0 until rows.length())
            .map { rows.getJSONObject(it) }
            .filter { it.optString("name") == name }
    }

    private fun seedPaging() {
        for (i in 0 until 55) {
            val name = fixturePrefix + String.format(Locale.ROOT, "%03d", i)
            store.saveEditorRecord(
                "customer",
                JSONObject()
                    .put("name", name)
                    .put("active", true)
                    .put("test_record", true)
                    .put("_request_id", "qa_native_page_$i"),
            )
        }
        val rows = PixelMcpReadTools.effective(store.getState()).getJSONArray("customers")
        yes(
            (0 until rows.length()).count {
                rows.getJSONObject(it).optString("name").startsWith(fixturePrefix)
            } == 55,
            "REAL_WRITER_CREATED_PAGING_ROWS",
        )
    }

    private fun guardGraphicsFixture() {
        // Reject physical devices before inspecting any app storage. This suite never clears data.
        yes(
            test.context.packageName == "at.hausgarten.service.test" &&
                context.packageName == "at.hausgarten.service",
            "GRAPHICS_TEST_AND_TARGET_PACKAGES",
        )
        yes(
            Build.VERSION.SDK_INT == 35 &&
                Build.HARDWARE in listOf("ranchu", "goldfish") &&
                shell("getprop ro.kernel.qemu") == "1",
            "GRAPHICS_DISPOSABLE_API35_EMULATOR_REQUIRED",
        )
        yes(!context.isDeviceProtectedStorage, "GRAPHICS_CREDENTIAL_SANDBOX_REQUIRED")
        RevierApplication.awaitStartup(context, 20000)
        val files = context.filesDir
        val noBackup = context.noBackupFilesDir
        val updatedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        fun profileMetadata(file: File): Boolean =
            try {
                if (
                    java.nio.file.Files.isSymbolicLink(file.toPath()) ||
                        !file.isFile ||
                        file.canonicalFile.parentFile != files.canonicalFile
                )
                    false
                else
                    when (file.name) {
                        "profileinstaller_profileWrittenFor_lastUpdateTime.dat" ->
                            file.length() == 8L &&
                                java.io.DataInputStream(file.inputStream()).use {
                                    it.readLong() == updatedAt && it.read() == -1
                                }
                        "profileInstalled" ->
                            file.length() == 24L &&
                                java.io.DataInputStream(file.inputStream()).use {
                                    val schema = it.readInt()
                                    val result = it.readInt()
                                    val packageUpdatedAt = it.readLong()
                                    val profileSize = it.readLong()
                                    schema == 1 &&
                                        result in
                                            setOf(
                                                0,
                                                1,
                                                2,
                                                3,
                                                65536,
                                                131072,
                                                196608,
                                                262144,
                                                327680,
                                            ) &&
                                        packageUpdatedAt == updatedAt &&
                                        profileSize >= 0 &&
                                        it.read() == -1
                                }
                        else -> false
                    }
            } catch (_: java.io.IOException) {
                false
            }
        yes(
            !java.nio.file.Files.isSymbolicLink(files.toPath()) &&
                files.listFiles()?.all(::profileMetadata) == true,
            "GRAPHICS_FRESH_FILES_OR_VERIFIED_ANDROIDX_PROFILE_METADATA_REQUIRED",
        )
        yes(
            !java.nio.file.Files.isSymbolicLink(noBackup.toPath()) &&
                noBackup.listFiles()?.isEmpty() == true,
            "GRAPHICS_FRESH_NO_BACKUP_REQUIRED",
        )
        val databaseDirectory = context.getDatabasePath("haus-garten-mobile.db").parentFile!!
        yes(
            !java.nio.file.Files.isSymbolicLink(databaseDirectory.toPath()) &&
                (!databaseDirectory.exists() || databaseDirectory.listFiles()?.isEmpty() == true),
            "GRAPHICS_NO_EXISTING_DATABASE_REQUIRED",
        )
        val preferenceDirectory = File(context.dataDir, "shared_prefs")
        yes(
            !java.nio.file.Files.isSymbolicLink(preferenceDirectory.toPath()) &&
                (!preferenceDirectory.exists() ||
                    preferenceDirectory.listFiles()?.all { file ->
                        !java.nio.file.Files.isSymbolicLink(file.toPath()) &&
                            file.isFile &&
                            file.name.endsWith(".xml") &&
                            file.canonicalFile.parentFile == preferenceDirectory.canonicalFile &&
                            context
                                .getSharedPreferences(
                                    file.name.removeSuffix(".xml"),
                                    Context.MODE_PRIVATE,
                                )
                                .all
                                .isEmpty()
                    } == true),
            "GRAPHICS_NO_CONFIGURED_PREFERENCES_REQUIRED",
        )
        // The exact public profile is written only after every fresh-sandbox assertion passed.
        val fixture = SyntheticBusinessData.profile("minimal")
        File(noBackup, "qa-native-graphics-fixture.json")
            .writeText(
                JSONObject()
                    .put("fixture", "synthetic-native-graphics-v1")
                    .put("source_sha256", PixelMcpWriteTools.hash(fixture))
                    .toString()
            )
        store = PixelLocalStore(context)
        store.putOwnerValue("snapshot", fixture.toString())
        store.putOwnerValue("qa_graphics_marker", "QA independent synthetic graphics fixture")
        tools = PixelMcpReadTools(store.readSource(), store, PixelOwnerAccess(context, store))
        yes(
            PixelMcpWriteTools.hash(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("customers")
            ) == PixelMcpWriteTools.hash(fixture.getJSONArray("customers")),
            "GRAPHICS_EXACT_SYNTHETIC_CUSTOMERS",
        )
        yes(
            read("get_active_work_session").optString("state") == "IDLE",
            "GRAPHICS_NO_EXISTING_TIMER",
        )
        yes(
            context
                .getSharedPreferences("planning_private", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("weather_enabled", false)
                .putBoolean("notifications", false)
                .commit(),
            "GRAPHICS_OFFLINE_PLANNING",
        )
    }

    private fun executeSettlement(): JSONObject {
        guardGraphicsFixture()
        store.putOwnerValue("snapshot", NativeSettlementNativeTests.fixture().toString())
        store.putOwnerValue("personal_paid_migrated", "1")
        store.putOwnerValue("personal_paid", JSONArray().put("qa_settlement_labor").toString())
        val originalMaster = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
        val originalMarks = store.ownerValue("personal_paid", "[]")
        val invoice =
            read(
                    "save_settlement_draft",
                    JSONObject()
                        .put("request_id", "qa_settlement_ui_invoice_draft")
                        .put(
                            "draft",
                            NativeSettlementNativeTests.draft(
                                "billed",
                                "900",
                                "qa_settlement_invoice",
                            ),
                        ),
                )
                .getString("settlement_id")
        val labor =
            read(
                    "save_settlement_draft",
                    JSONObject()
                        .put("request_id", "qa_settlement_ui_labor_draft")
                        .put(
                            "draft",
                            NativeSettlementNativeTests.draft("labor", "200", "qa_settlement_labor"),
                        ),
                )
                .getString("settlement_id")
        val previous = automation.serviceInfo
        val previousFlags = previous.flags
        val oldFont = shell("settings get system font_scale")
        try {
            automation.serviceInfo =
                previous.apply {
                    flags =
                        flags or
                            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                }
            launch()
            openSettlements()
            shown("[TEST] Rechnung 900")
            click("[TEST] Rechnung 900")
            shown("Entwurf")
            // Design: one "Ausstellen" after the preview; issuer details are confirmed once in
            // settings.
            click(UiTags.PRIMARY)
            waitUntil("SETTLEMENT_UI_REAL_INVOICE_CONFIRMATION") {
                read("get_settlement", JSONObject().put("settlement_id", invoice))
                    .getJSONObject("record")
                    .getString("status") == "confirmed"
            }
            checks++
            reveal("Bestätigt")
            shown("Bestätigt")
            click(UiTags.DETAIL_MENU)
            shown(UiTags.detailAction("settlement-pdf"))
            back()
            val original =
                read("get_settlement", JSONObject().put("settlement_id", invoice))
                    .getJSONObject("record")
            yes(
                original.getString("status") == "confirmed" &&
                    original.has("pdf") &&
                    original.has("invoice_number"),
                "SETTLEMENT_UI_ORIGINAL_AFTER_CONFIRMATION",
            )
            captureSyntheticScreen("settlement-invoice-confirmed")
            openSettlements()
            click("[TEST] Labor 200")
            click(UiTags.PRIMARY)
            waitUntil("SETTLEMENT_UI_REAL_LABOR_CONFIRMATION") {
                read("get_settlement", JSONObject().put("settlement_id", labor))
                    .getJSONObject("record")
                    .getString("status") == "confirmed"
            }
            checks++
            reveal("Bestätigt")
            shown("Bestätigt")
            val internal =
                read("get_settlement", JSONObject().put("settlement_id", labor))
                    .getJSONObject("record")
            yes(
                internal.getString("status") == "confirmed" &&
                    !internal.has("invoice_number") &&
                    !internal.has("pdf"),
                "SETTLEMENT_UI_INTERNAL_NO_INVOICE",
            )
            captureSyntheticScreen("settlement-labor-confirmed")
            openAccountingTotals()
            reveal("1100,00 €")
            shown("1100,00 €")
            yes(
                read("get_accounting_overview").getLong("total_minor") == 110000L,
                "SETTLEMENT_UI_900_PLUS_200",
            )
            click("Rechnung")
            reveal("900,00 €")
            shown("900,00 €")
            showKeyboard("Auftragsbezeichnung")
            enter("Auftragsbezeichnung", "qa_settlement_invoice")
            back()
            recreate()
            // Activity recreation briefly shows the asynchronous startup review over the
            // restored screen; the restored filter must stay visible once that is done.
            waitStable("SETTLEMENT_UI_FILTER_RECREATION") {
                field("Auftragsbezeichnung")?.text?.toString()?.contains("qa_settlement_invoice") ==
                    true
            }
            val filteredAfterRecreation =
                read(
                    "get_accounting_overview",
                    JSONObject().put("query", "qa_settlement_invoice").put("route", "billed"),
                )
            yes(
                filteredAfterRecreation.getLong("billed_minor") == 90000L &&
                    filteredAfterRecreation.getLong("total_minor") == 90000L &&
                    filteredAfterRecreation.getInt("total") == 1,
                "SETTLEMENT_UI_RECREATED_FILTER_EXACT_AMOUNT",
            )
            // Search and keyboard handling may leave the preserved viewport below
            // the sum cards. Explicitly bring the card into view before asserting it.
            reveal("900,00 €")
            shown("900,00 €")
            captureSyntheticScreen("settlement-overview-filter")
            openAccountingOpen()
            click("Neue Abrechnung")
            enter("Bezeichnung", "[TEST] Erste manuelle Abrechnung")
            click("Position hinzufügen")
            enter("Leistung", "[TEST] Synthetische manuelle Leistung A")
            enter("Tatsächlich geleistete Menge", "1")
            enter("Einheit", "Leistung")
            enter("Preis je Einheit (€)", "3,00")
            click(UiTags.PRIMARY)
            reveal("[TEST] Erste manuelle Abrechnung")
            shown("[TEST] Erste manuelle Abrechnung")
            reveal("Entwurf")
            shown("Entwurf")
            val firstNew =
                SettlementRules.rows(read("list_settlements").getJSONArray("records")).single {
                    it.optString("title") == "[TEST] Erste manuelle Abrechnung"
                }
            yes(
                firstNew.getJSONArray("positions").getJSONObject(0).getLong("amount_minor") == 300L,
                "SETTLEMENT_UI_FIRST_NEW_DRAFT_AMOUNT",
            )
            openAccountingOpen()
            click("Neue Abrechnung")
            reveal("Bezeichnung")
            yes(
                field("Bezeichnung")?.text?.toString().orEmpty().isEmpty(),
                "SETTLEMENT_UI_SECOND_NEW_EDITOR_IS_EMPTY",
            )
            yes(node("Abrechnungsart: Rechnung") != null, "SETTLEMENT_UI_NEW_EDITOR_DEFAULT_ROUTE")
            enter("Bezeichnung", "[TEST] Zweite manuelle Abrechnung")
            click("Abrechnungsart: Rechnung")
            click("Labor clandestino")
            click("Position hinzufügen")
            enter("Leistung", "[TEST] Synthetische manuelle Leistung B")
            enter("Tatsächlich geleistete Menge", "2")
            enter("Einheit", "Stück")
            enter("Preis je Einheit (€)", "2,00")
            click(UiTags.PRIMARY)
            reveal("[TEST] Zweite manuelle Abrechnung")
            shown("[TEST] Zweite manuelle Abrechnung")
            reveal("Entwurf")
            shown("Entwurf")
            val secondNew =
                SettlementRules.rows(read("list_settlements").getJSONArray("records")).single {
                    it.optString("title") == "[TEST] Zweite manuelle Abrechnung"
                }
            yes(
                secondNew.getString("id") != firstNew.getString("id") &&
                    secondNew.getString("route") == "labor" &&
                    secondNew.getJSONArray("positions").getJSONObject(0).getLong("amount_minor") ==
                        400L,
                "SETTLEMENT_UI_NEW_OPERATION_DISTINCT_ID_ROUTE_AND_AMOUNT",
            )
            yes(
                SettlementRules.rows(read("list_settlements").getJSONArray("records"))
                    .single { it.getString("id") == firstNew.getString("id") }
                    .getString("title") == "[TEST] Erste manuelle Abrechnung",
                "SETTLEMENT_UI_FIRST_DRAFT_UNCHANGED_BY_SECOND",
            )
            openSettlements()
            click("[TEST] Erste manuelle Abrechnung")
            click("Entwurf bearbeiten")
            reveal("Bezeichnung")
            yes(
                field("Bezeichnung")
                    ?.text
                    ?.toString()
                    ?.contains("[TEST] Erste manuelle Abrechnung") == true,
                "SETTLEMENT_UI_EXISTING_EDITOR_LOADS_ITS_OWN_DRAFT",
            )
            enter("Bezeichnung", "[TEST] Erste manuelle Abrechnung geprüft")
            click(UiTags.PRIMARY)
            var lastSaveScroll = 0L
            waitUntil("SETTLEMENT_UI_EXISTING_DRAFT_SAVE_COMPLETED") {
                // The edited text already exists in the busy editor. Require the
                // distinct review action before reading the persisted result.
                if (node("Entwurf bearbeiten")?.let(::clickable) != null) {
                    SettlementRules.rows(read("list_settlements").getJSONArray("records"))
                        .singleOrNull { it.getString("id") == firstNew.getString("id") }
                        ?.optString("title") == "[TEST] Erste manuelle Abrechnung geprüft"
                } else {
                    if (SystemClock.uptimeMillis() - lastSaveScroll > 250) {
                        scroll()
                        lastSaveScroll = SystemClock.uptimeMillis()
                    }
                    false
                }
            }
            reveal("[TEST] Erste manuelle Abrechnung geprüft")
            shown("[TEST] Erste manuelle Abrechnung geprüft")
            yes(
                SettlementRules.rows(read("list_settlements").getJSONArray("records"))
                    .single { it.getString("id") == firstNew.getString("id") }
                    .getString("title") == "[TEST] Erste manuelle Abrechnung geprüft",
                "SETTLEMENT_UI_EXISTING_DRAFT_UPDATED_WITH_CURRENT_HASH",
            )
            yes(
                PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) ==
                    originalMaster && store.ownerValue("personal_paid", "[]") == originalMarks,
                "SETTLEMENT_UI_ASSIGNMENTS_AND_SOURCES_UNCHANGED",
            )
            openAccountingTotals()
            showKeyboard("Auftragsbezeichnung")
            enter("Auftragsbezeichnung", "")
            back()
            scrollTop()
            click("Gesamt")
            reveal("1100,00 €")
            shown("1100,00 €")
            for (wide in listOf(false, true)) {
                activity?.let { current ->
                    test.runOnMainSync { if (!current.isFinishing) current.finish() }
                }
                shell("settings put system font_scale 2.0")
                launch()
                openAccountingTotals()
                test.runOnMainSync {
                    activity?.requestedOrientation =
                        if (wide) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                waitUntil("SETTLEMENT_APPLIED_200_FONT_$wide") {
                    var applied = false
                    test.runOnMainSync {
                        val configuration = activity?.resources?.configuration
                        applied =
                            configuration != null &&
                                configuration.fontScale >= 1.99f &&
                                configuration.orientation ==
                                    if (wide)
                                        android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                    else android.content.res.Configuration.ORIENTATION_PORTRAIT
                    }
                    applied
                }
                settleUi()
                reveal("Gesamt")
                reveal("1100,00 €")
                shown("1100,00 €")
                captureSyntheticScreen(
                    "settlement-overview-200-${if (wide) "landscape" else "portrait"}"
                )
            }
            return JSONObject()
                .put("checks", checks)
                .put("synthetic_only", true)
                .put("productive_data_read", false)
                .put("real_main_activity", true)
                .put("both_completion_routes", true)
                .put("exact_amounts", true)
                .put("filters_and_navigation", true)
                .put("font_scale_200_percent", true)
                .put("draft_recreation", true)
                .put("physical_pixel_tested", false)
        } catch (failure: Throwable) {
            runCatching { captureSyntheticScreen("settlement-failure") }
            throw failure
        } finally {
            previous.flags = previousFlags
            automation.serviceInfo = previous
            shell(
                if (oldFont == "null") "settings delete system font_scale"
                else "settings put system font_scale $oldFont"
            )
            activity?.let { current ->
                test.runOnMainSync {
                    current.requestedOrientation =
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    if (!current.isFinishing) current.finish()
                }
            }
        }
    }

    private fun executeAccounting(reopen: Boolean): JSONObject {
        yes(
            test.context.packageName == "at.hausgarten.service.test" &&
                context.packageName == "at.hausgarten.service" &&
                Build.VERSION.SDK_INT == 35 &&
                Build.HARDWARE in listOf("ranchu", "goldfish") &&
                shell("getprop ro.kernel.qemu") == "1",
            "ACCOUNTING_DISPOSABLE_EMULATOR_BEFORE_STORAGE",
        )
        val marker = File(context.noBackupFilesDir, "qa-native-accounting.json")
        if (reopen) {
            yes(
                test.context.packageName == "at.hausgarten.service.test" &&
                    Build.VERSION.SDK_INT == 35 &&
                    Build.HARDWARE in listOf("ranchu", "goldfish") &&
                    shell("getprop ro.kernel.qemu") == "1",
                "ACCOUNTING_REOPEN_EMULATOR_REQUIRED",
            )
            yes(
                marker.isFile &&
                    JSONObject(marker.readText()).optString("fixture") == "synthetic-accounting-v1",
                "ACCOUNTING_REOPEN_SYNTHETIC_MARKER",
            )
            store = PixelLocalStore(context)
            tools = PixelMcpReadTools(store.readSource(), store, PixelOwnerAccess(context, store))
            val saved = JSONObject(marker.readText())
            yes(
                PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) ==
                    saved.getString("snapshot_hash"),
                "ACCOUNTING_NEW_PROCESS_IDENTICAL_SOURCE",
            )
            val result = read("get_accounting_overview")
            yes(
                result.getLong("total_minor") == 0L &&
                    result.getInt("total") == 1 &&
                    result.getInt("pending_amounts") == 1 &&
                    result.getInt("review_total") == 0,
                "ACCOUNTING_NEW_PROCESS_PERSISTED_ASSIGNMENT_AND_PHASE2_DEFERRAL",
            )
            return JSONObject()
                .put("checks", checks)
                .put("synthetic_only", true)
                .put("productive_data_read", false)
                .put("new_process_readback", true)
        }
        guardGraphicsFixture()
        val source = JSONObject(PixelMcpReadTools.effective(store.getState()).toString())
        val customer = source.getJSONArray("customers").getJSONObject(0).getString("id")
        source.getJSONArray("customers").getJSONObject(0).put("name", "[TEST] QA Abrechnung Kunde")
        fun order(id: String, status: String, amount: String, property: String) =
            JSONObject()
                .put("id", id)
                .put("title", "QA Abrechnung " + id.takeLast(1).uppercase())
                .put("customer_id", customer)
                .put("billing_customer_id", customer)
                .put("status", status)
                .put("accounting_amount", amount)
                .put("property_id", property)
                .put("scheduled_date", "2026-11-01")
                .put("test_record", true)
                .put("created_at", "2026-11-01T10:00:00Z")
                .put("updated_at", "2026-11-01T10:00:00Z")
        fun work(id: String, parent: String, billed: Int, amount: String, day: String) =
            JSONObject()
                .put("id", id)
                .put("order_id", parent)
                .put("customer_id", customer)
                .put("billing_customer_id", customer)
                .put("activity", "QA Abrechnung Arbeit $id")
                .put("billed", billed)
                .put("accounting_amount", amount)
                .put("work_date", day)
                .put("hours", 2)
                .put("test_record", true)
                .put("created_at", "2026-11-01T10:00:00Z")
                .put("updated_at", "2026-11-01T10:00:00Z")
        source.put(
            "orders",
            JSONArray()
                .put(order("qa_accounting_a", "Abgerechnet", "900.00", "qa_accounting_property"))
                .put(order("qa_accounting_b", "Erledigt", "200.00", ""))
                .put(order("qa_accounting_c", "Offen", "400.00", "").put("paid", true)),
        )
        source.put(
            "worklogs",
            JSONArray()
                .put(work("qa_accounting_a1", "qa_accounting_a", 1, "500.00", "2026-11-01"))
                .put(work("qa_accounting_a2", "qa_accounting_a", 1, "400.00", "2026-11-02"))
                .put(work("qa_accounting_b1", "qa_accounting_b", 0, "200.00", "2026-11-03"))
                .put(work("qa_accounting_c1", "qa_accounting_c", 0, "400.00", "2026-11-04")),
        )
        store.putOwnerValue("snapshot", source.toString())
        store.putOwnerValue("personal_paid_migrated", "1")
        store.putOwnerValue("personal_paid", JSONArray().put("qa_accounting_b1").toString())
        store.putOwnerValue(
            "revier:v1:entity:property:qa_accounting_property",
            JSONObject()
                .put("id", "qa_accounting_property")
                .put("name", "QA Haus und Garten")
                .put("customer_id", customer)
                .toString(),
        )
        fun overview(extra: JSONObject = JSONObject()) = read("get_accounting_overview", extra)
        var data = overview()
        yes(
            data.getLong("billed_minor") == 90000L &&
                data.getLong("labor_minor") == 20000L &&
                data.getLong("total_minor") == 110000L &&
                data.getInt("total") == 2,
            "ACCOUNTING_ACCEPTANCE_EXAMPLE_AND_NO_DOUBLE_COUNT",
        )
        val filters =
            JSONObject()
                .put("date_from", "2026-11-02")
                .put("date_to", "2026-11-02")
                .put("property_id", "qa_accounting_property")
                .put("query", "Abrechnung A")
        data = overview(filters)
        yes(
            data.getLong("total_minor") == 90000L && data.getInt("total") == 1,
            "ACCOUNTING_SHARED_DATE_OBJECT_SEARCH_FILTERS",
        )
        yes(
            overview(JSONObject(filters.toString()).put("route", "labor")).getLong("total_minor") ==
                0L,
            "ACCOUNTING_PATH_FILTER_APPLIES_TO_SUMS_AND_LIST",
        )
        yes(
            overview(JSONObject().put("query", "QA no matching accounting"))
                .getLong("total_minor") == 0L,
            "ACCOUNTING_EMPTY_ZERO_TOTALS",
        )
        val previousInfo = automation.serviceInfo
        val previousFlags = previousInfo.flags
        val oldFont = shell("settings get system font_scale")
        try {
            automation.serviceInfo =
                previousInfo.apply {
                    flags =
                        flags or
                            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                }
            launch()
            openAccountingTotals()
            reveal("1100,00 €")
            shown("1100,00 €")
            click("Rechnung")
            reveal("900,00 €")
            shown("900,00 €")
            reveal("QA Abrechnung A")
            click("QA Abrechnung A")
            shown("Bearbeiten")
            click(UiTags.BACK)
            reveal("900,00 €")
            shown("900,00 €")
            showKeyboard("Auftragsbezeichnung")
            enter("Auftragsbezeichnung", "Abrechnung A")
            back()
            // IME disappearance precedes the resized dialog's accessibility tree.
            // Require the retained query and overview together after that reflow.
            waitUntil("ACCOUNTING_BACK_HIDES_IME_WITHOUT_LEAVING_OVERVIEW") {
                !keyboardVisible() &&
                    field("Auftragsbezeichnung")?.text?.toString() == "Abrechnung A"
            }
            checks++
            recreate()
            // A Compose startup screen exists before the accounting route is ready.
            // Require its restored search field, not merely the root Compose view.
            waitUntil("ACCOUNTING_RECREATED_FEATURE_FIELD") {
                field("Auftragsbezeichnung")?.text?.toString()?.contains("Abrechnung A") == true
            }
            yes(
                field("Auftragsbezeichnung")?.text?.toString()?.contains("Abrechnung A") == true,
                "ACCOUNTING_FILTERS_SURVIVE_DETAIL_AND_RECREATION",
            )
            val filteredAfterRecreation =
                overview(JSONObject().put("query", "Abrechnung A").put("route", "billed"))
            yes(
                filteredAfterRecreation.getLong("billed_minor") == 90000L &&
                    filteredAfterRecreation.getLong("total_minor") == 90000L &&
                    filteredAfterRecreation.getInt("total") == 1,
                "ACCOUNTING_RECREATED_FILTER_EXACT_AMOUNT",
            )
            // Retain the user's viewport; the harness scrolls to the amount only
            // when its actual visibility is required for this assertion.
            reveal("900,00 €")
            shown("900,00 €")
            // Simulate the later invoice module's committed source update, never a manual price
            // editor.
            val invoiceUpdate = JSONObject(PixelMcpReadTools.effective(store.getState()).toString())
            PixelMcpWriteTools.find(invoiceUpdate.getJSONArray("orders"), "qa_accounting_a")
                .put("accounting_amount", "950.25")
            store.putOwnerValue("snapshot", invoiceUpdate.toString())
            reveal("950,25 €")
            shown("950,25 €")
            val observed =
                read(
                    "get_accounting_record",
                    JSONObject().put("collection", "orders").put("entity_id", "qa_accounting_a"),
                )
            val update =
                JSONObject()
                    .put("request_id", "qa_accounting_amount_change")
                    .put("collection", "orders")
                    .put("entity_id", "qa_accounting_a")
                    .put("expected_record_hash", observed.getString("expected_record_hash"))
                    .put("route", "billed")
            val beforeAbort = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
            val marksBeforeAbort = store.ownerValue("personal_paid", "[]")
            val receiptKey = "receipt:mcp_owner_" + PixelMcpWriteTools.hash("qa_accounting_abort")
            store.writableDatabase.execSQL(
                "CREATE TRIGGER qa_accounting_abort BEFORE INSERT ON owner_state WHEN NEW.key='$receiptKey' BEGIN SELECT RAISE(ABORT,'QA accounting receipt failure'); END"
            )
            try {
                val abort = runCatching {
                    tools.call(
                        "set_accounting_record",
                        JSONObject(update.toString())
                            .put("request_id", "qa_accounting_abort")
                            .put("route", "labor"),
                    )
                }
                yes(
                    abort.isFailure || abort.getOrNull()?.optBoolean("ok") == false,
                    "ACCOUNTING_INJECTED_RECEIPT_FAILURE",
                )
                yes(
                    PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) ==
                        beforeAbort && store.ownerValue("personal_paid", "[]") == marksBeforeAbort,
                    "ACCOUNTING_TRANSACTION_ROLLBACK_HAS_NO_PARTIAL_MARKS_OR_AMOUNTS",
                )
            } finally {
                store.writableDatabase.execSQL("DROP TRIGGER qa_accounting_abort")
            }
            read("set_accounting_record", update)
            reveal("950,25 €")
            shown("950,25 €")
            yes(
                read("set_accounting_record", update).getBoolean("replayed"),
                "ACCOUNTING_IDENTICAL_REPLAY",
            )
            yes(
                overview().getLong("total_minor") == 115025L,
                "ACCOUNTING_REAL_WRITER_EXACT_AMOUNT_REFRESH",
            )
            val stale =
                JSONObject(update.toString()).put("request_id", "qa_accounting_stale_change")
            val failed = runCatching { tools.call("set_accounting_record", stale) }
            yes(
                failed.isFailure || failed.getOrNull()?.optBoolean("ok") == false,
                "ACCOUNTING_STALE_HASH_REJECTED",
            )
            store.putOwnerValue(
                "personal_paid",
                JSONArray().put("qa_accounting_b1").put("qa_accounting_a1").toString(),
            )
            data = overview()
            yes(
                data.getInt("review_total") == 1 && data.getLong("total_minor") == 20000L,
                "ACCOUNTING_LEGACY_CONFLICT_VISIBLE_AND_EXCLUDED",
            )
            reveal("Zuordnung ändern")
            click("Zuordnung ändern")
            shown("Abrechnungsweg: Auswählen")
            click("Abrechnungsweg: Auswählen")
            click("Labor clandestino")
            click("Abrechnung speichern")
            waitUntil("ACCOUNTING_EXPLICIT_CONFLICT_CORRECTION") {
                val value = overview()
                value.getInt("review_total") == 0 && value.getLong("labor_minor") == 115025L
            }
            // Return to the same filtered overview; clear only search for viewport checks.
            showKeyboard("Auftragsbezeichnung")
            enter("Auftragsbezeichnung", "")
            back()
            waitUntil("ACCOUNTING_FINAL_IME_HIDDEN") { !keyboardVisible() }
            scrollTop()
            click("Gesamt")
            reveal("1150,25 €")
            shown("1150,25 €")
            for (font in listOf("1.0", "2.0")) for (wide in listOf(false, true)) {
                activity?.let { current ->
                    test.runOnMainSync { if (!current.isFinishing) current.finish() }
                }
                shell("settings put system font_scale $font")
                launch()
                openAccountingTotals()
                test.runOnMainSync {
                    activity?.requestedOrientation =
                        if (wide) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                settleUi()
                reveal("Gesamt")
                reveal("1150,25 €")
                shown("1150,25 €")
                captureSyntheticScreen(
                    "accounting-${if (font == "2.0") "200" else "100"}-${if (wide) "landscape" else "portrait"}"
                )
                yes(
                    field("Auftragsbezeichnung") != null,
                    "ACCOUNTING_SEARCH_ACCESSIBLE_${font}_$wide",
                )
                reveal("QA Abrechnung A")
                click("QA Abrechnung A")
                click("Abrechnungsweg")
                shown("Abrechnung speichern")
                reveal("Abrechnungsweg: Labor clandestino")
                captureSyntheticScreen(
                    "accounting-editor-${if (font == "2.0") "200" else "100"}-${if (wide) "landscape" else "portrait"}"
                )
                click("Zurück")
            }
            val current =
                read(
                    "get_accounting_record",
                    JSONObject().put("collection", "orders").put("entity_id", "qa_accounting_a"),
                )
            read(
                "set_accounting_record",
                JSONObject()
                    .put("request_id", "qa_accounting_remove_assignment")
                    .put("collection", "orders")
                    .put("entity_id", "qa_accounting_a")
                    .put("expected_record_hash", current.getString("expected_record_hash"))
                    .put("route", "open"),
            )
            yes(
                overview().getLong("total_minor") == 20000L,
                "ACCOUNTING_REMOVED_ASSIGNMENT_EXCLUDED_WITH_AMOUNT_RETAINED",
            )
            val missingInvoice =
                JSONObject(PixelMcpReadTools.effective(store.getState()).toString())
            PixelMcpWriteTools.find(missingInvoice.getJSONArray("orders"), "qa_accounting_b")
                .put("accounting_amount", JSONObject.NULL)
            PixelMcpWriteTools.find(missingInvoice.getJSONArray("worklogs"), "qa_accounting_b1")
                .put("accounting_amount", JSONObject.NULL)
            store.putOwnerValue("snapshot", missingInvoice.toString())
            data = overview()
            yes(
                data.getInt("total") == 1 &&
                    data.getInt("pending_amounts") == 1 &&
                    data.getLong("total_minor") == 0L &&
                    data.getJSONArray("records").getJSONObject(0).isNull("amount_minor"),
                "ACCOUNTING_MISSING_PHASE2_AMOUNT_REMAINS_VISIBLE_AND_UNKNOWN",
            )
            marker.writeText(
                JSONObject()
                    .put("fixture", "synthetic-accounting-v1")
                    .put(
                        "snapshot_hash",
                        PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())),
                    )
                    .toString()
            )
            return JSONObject()
                .put("checks", checks)
                .put("synthetic_only", true)
                .put("productive_data_read", false)
                .put("new_process_readback", false)
                .put("real_main_activity", true)
                .put("exact_amounts", true)
                .put("filters_and_navigation", true)
                .put("conflict_correction", true)
                .put("viewport_and_large_text", true)
        } catch (failure: Throwable) {
            runCatching {
                val state = PixelMcpReadTools.effective(store.getState())
                test.sendStatus(
                    0,
                    Bundle().apply {
                        putString(
                            "accounting_readback",
                            JSONObject()
                                .put("overview", overview())
                                .put("journal_errors", state.opt("drive_journal_errors"))
                                .put("marks", JSONArray(store.ownerValue("personal_paid", "[]")))
                                .toString(),
                        )
                    },
                )
            }
            runCatching { captureSyntheticScreen("accounting-failure") }
            throw failure
        } finally {
            previousInfo.flags = previousFlags
            automation.serviceInfo = previousInfo
            shell(
                if (oldFont == "null") "settings delete system font_scale"
                else "settings put system font_scale $oldFont"
            )
            activity?.let { current ->
                test.runOnMainSync {
                    current.requestedOrientation =
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    if (!current.isFinishing) current.finish()
                }
            }
        }
    }

    private fun executeGraphics(selectedJourney: String): JSONObject {
        guardGraphicsFixture()
        val previousInfo = automation.serviceInfo
        val previousFlags = previousInfo.flags
        val oldFont = shell("settings get system font_scale")
        try {
            automation.serviceInfo =
                previousInfo.apply {
                    flags =
                        flags or
                            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                }
            shell("settings put system font_scale 1.0")
            seedPaging()
            launch()
            var ran = false
            fun journey(name: String, body: () -> Unit) {
                if (selectedJourney != name) return
                yes(!ran, "ONE_GRAPHICS_JOURNEY_PER_PROCESS")
                ran = true
                test.sendStatus(0, Bundle().apply { putString("journey_start", name) })
                try {
                    body()
                } catch (failure: Throwable) {
                    test.sendStatus(
                        0,
                        Bundle().apply {
                            putString("journey_failure", "$name: " + failure.stackTraceToString())
                        },
                    )
                    test.sendStatus(
                        0,
                        Bundle().apply {
                            putString(
                                "list_read_diagnostics",
                                shell("logcat -d -s HausGartenNativeList:W").takeLast(12000),
                            )
                        },
                    )
                    runCatching {
                        captureSyntheticScreen("failure-$name")
                        logVisibleActions("failure_$name")
                    }
                    throw failure
                }
            }
            journey("graphics_today", ::todayJourney)
            journey("graphics_work", ::workInteractionJourney)
            journey("graphics_navigation", ::graphicsNavigationJourney)
            journey("graphics_accessories", ::accessoryJourney)
            journey("graphics_timer") { timerJourney(largeFontScreenshot = true) }
            journey("graphics_large_text", ::largeTextJourney)
            yes(ran, "EXPLICIT_KNOWN_GRAPHICS_JOURNEY_REQUIRED")
            return JSONObject()
                .put("checks", checks)
                .put("synthetic_only", true)
                .put("productive_data_read", false)
                .put("independent_fresh_fixture", true)
                .put("backup_provider_prerequisite", false)
                .put("real_main_activity", true)
                .put("native_compose_ui", true)
                .put("graphics_cases", JSONArray().put(selectedJourney))
                .put("physical_pixel_tested", false)
                .put("drive_account_tested", false)
        } finally {
            shell(
                if (oldFont == "null") "settings delete system font_scale"
                else "settings put system font_scale $oldFont"
            )
            activity?.let { current ->
                test.runOnMainSync { if (!current.isFinishing) current.finish() }
            }
            automation.serviceInfo = previousInfo.apply { flags = previousFlags }
            store.close()
        }
    }

    /**
     * Real gesture/keyboard actions plus durable readback of isolated field edits and batch
     * deletion.
     */
    private fun workInteractionJourney() {
        val customer = customers(fixturePrefix + "000").single().getString("id")
        fun works(): List<JSONObject> {
            val rows = PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            return (0 until rows.length()).map { rows.getJSONObject(it) }
        }
        val originalIds = works().map { it.getString("id") }.toSet()
        val originalHash = PixelMcpWriteTools.hash(JSONArray(works()))
        val prefix = "[TEST] QA Arbeitsbedienung "
        val day = LocalDate.now(ZoneId.of("Europe/Vienna")).minusDays(1).toString()
        val ids =
            (1..2).map { index ->
                store.saveEditorRecord(
                    "worklog",
                    JSONObject()
                        .put("_request_id", "qa_work_interaction_$index")
                        .put("customer_id", customer)
                        .put("work_date", day)
                        .put("activity", prefix + index)
                        .put("start_time", "08:00")
                        .put("end_time", "12:00")
                        .put("break_minutes", 30)
                        .put("notes", "[TEST] Ursprüngliche Notiz"),
                )
            }
        openWorklogs()
        enter("Suchen", prefix)
        shown(prefix + "1")
        shown(prefix + "2")
        yes(
            node("Zeiterfassung öffnen") == null &&
                node("Löschen") == null &&
                nodes().none { it.className?.toString() == "android.widget.CheckBox" },
            "WORK_LIST_WITHOUT_TIMER_CHECKBOXES_OR_ROW_DELETE",
        )
        val searchBounds = Rect().also { requireNotNull(field("Suchen")).getBoundsInScreen(it) }
        yes(searchBounds.top > actionBounds(prefix + "1").bottom, "WORK_SEARCH_BELOW_RESULTS")
        showKeyboard("Suchen")
        visibleResult(prefix + "1", requireKeyboard = true)
        captureSyntheticScreen("work-search", requireKeyboard = true)
        back()
        waitUntil("WORK_SEARCH_IME_CLOSED") { !keyboardVisible() }
        click(UiTags.FILTER)
        shown("Arbeiten filtern")
        chooseDate("Ab Datum", day)
        click("Ergebnisse anzeigen")
        shown(prefix + "1")
        captureSyntheticScreen("work-list")
        click(prefix + "1")
        shown("Gespeicherte Arbeit")
        val beforeFullEditor = PixelMcpWriteTools.hash(JSONArray(works()))
        click(UiTags.SECONDARY)
        reveal("native-field:Was wurde erledigt? *")
        yes(field("Was wurde erledigt? *")?.isEditable == true, "WORK_FULL_EDITOR_EDITABLE")
        back()
        waitUntil("WORK_DETAIL_AFTER_FULL_EDITOR") { node(UiTags.DETAIL_MENU) != null }
        yes(
            PixelMcpWriteTools.hash(JSONArray(works())) == beforeFullEditor,
            "WORK_FULL_EDITOR_BACK_PRESERVES_DATA",
        )
        click("Tätigkeit")
        shown("Was wurde erledigt? *")
        yes(
            node("Datum *") == null && node("Beginn") == null && node("Kunde") == null,
            "WORK_ACTIVITY_EDITOR_ISOLATED",
        )
        enter("Was wurde erledigt? *", prefix + "1 korrigiert")
        click(UiTags.PRIMARY)
        waitUntil("WORK_ACTIVITY_SAVED") {
            works().first { it.getString("id") == ids.first() }.getString("activity") ==
                prefix + "1 korrigiert"
        }
        fun first() = works().first { it.getString("id") == ids.first() }
        yes(
            first().getDouble("hours") == 3.5 &&
                first().getString("start_time") == "08:00" &&
                first().getString("end_time") == "12:00" &&
                first().getInt("break_minutes") == 30,
            "WORK_TEXT_EDIT_PRESERVES_TIMING",
        )
        click("Notizen")
        shown("Notizen")
        yes(
            node("Was wurde erledigt? *") == null && node("Beginn") == null,
            "WORK_NOTE_EDITOR_ISOLATED",
        )
        enter("Notizen", "[TEST] Einzelne Notiz korrigiert")
        click(UiTags.PRIMARY)
        waitUntil("WORK_NOTE_SAVED") {
            first().optString("notes") == "[TEST] Einzelne Notiz korrigiert"
        }
        click("Pause")
        shown("Pause (Minuten)")
        enter("Freie Minuten", "15")
        click(UiTags.PRIMARY)
        waitUntil("WORK_PAUSE_RECALCULATES_DURATION") {
            first().getInt("break_minutes") == 15 && first().getDouble("hours") == 3.75
        }
        // Design step 3: the next step of open work is to bill it; it opens the settlement editor.
        click(UiTags.PRIMARY)
        shown("Abrechnungsart: Rechnung")
        back()
        waitUntil("WORK_DETAIL_AFTER_BILLING_EDITOR") { node(UiTags.DETAIL_MENU) != null }
        // Rare actions sit in the record's ⋮ menu.
        click(UiTags.DETAIL_MENU)
        click("Abgerechnet / offen markieren")
        shown("Begründung *")
        yes(
            visibleTexts().none {
                it.contains("Work record id", true) || it.startsWith("Arbeitsaufzeichnung:")
            },
            "WORK_BILLING_REFERENCE_HIDDEN",
        )
        captureSyntheticScreen("work-billing")
        click("Zurück")
        openWorklogs()
        shown(prefix + "1 korrigiert")
        fun hold(label: String) {
            waitUntil("WORK_LONG_PRESS_TARGET_$label") {
                var candidate = node(label)
                while (
                    candidate != null &&
                        candidate.actionList.none {
                            it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK
                        }
                ) candidate = candidate.parent
                candidate?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true
            }
            checks++
            settleUi()
        }
        hold(prefix + "1 korrigiert")
        shown("1 ausgewählt")
        yes(
            node("Duplizieren") != null && node("Löschen") != null && field("Suchen") == null,
            "WORK_CONTEXTUAL_ACTIONS",
        )
        recreate()
        shown("1 ausgewählt")
        click(prefix + "2")
        shown("2 ausgewählt")
        captureSyntheticScreen("work-selection")
        back()
        shown("Suchen")
        yes(
            node("Löschen") == null &&
                works().map { it.getString("id") }.toSet() == originalIds + ids,
            "WORK_BACK_CANCELS_SELECTION_WITHOUT_WRITING",
        )
        hold(prefix + "1 korrigiert")
        click("Seite auswählen")
        shown("2 ausgewählt")
        val deleteStarted = SystemClock.uptimeMillis()
        click("Löschen")
        val deleteDeadline = SystemClock.uptimeMillis() + 20000
        // Observe the actual filtered UI without competing journal projections.
        // Verify durable IDs and content once, within that same deadline.
        waitUntil(
            "WORK_SELECTED_DELETE_VISIBLE_EMPTY",
            timeout = maxOf(1L, deleteDeadline - SystemClock.uptimeMillis()),
        ) {
            node("Keine Treffer. Suchtext oder Filter ändern.") != null
        }
        val remainingWorks = works()
        yes(
            remainingWorks.map { it.getString("id") }.toSet() == originalIds,
            "WORK_SELECTED_DELETE_VERIFIED",
        )
        yes(
            PixelMcpWriteTools.hash(JSONArray(remainingWorks)) == originalHash,
            "WORK_DELETE_PRESERVES_UNSELECTED_RECORDS",
        )
        yes(
            SystemClock.uptimeMillis() <= deleteDeadline,
            "WORK_DELETE_VERIFIED_WITHIN_SAME_20_SECOND_DEADLINE",
        )
        test.sendStatus(
            0,
            Bundle().apply {
                putLong("work_selected_delete_ms", SystemClock.uptimeMillis() - deleteStarted)
            },
        )
        test.sendStatus(
            0,
            Bundle().apply {
                putBoolean(
                    "work_delete_ui_still_finishing_before_recreation",
                    node("Löschen …") != null,
                )
            },
        )
        yes(node("Löschen") == null, "WORK_DELETE_NO_NEW_WRITE_AFTER_COMMIT")
        recreate()
        shown("Keine Treffer. Suchtext oder Filter ändern.")
        val restoredWorks = works()
        yes(
            restoredWorks.map { it.getString("id") }.toSet() == originalIds,
            "WORK_DELETE_IDS_DURABLE_AFTER_RECREATION",
        )
        yes(
            PixelMcpWriteTools.hash(JSONArray(restoredWorks)) == originalHash,
            "WORK_DELETE_UNSELECTED_HASH_DURABLE_AFTER_RECREATION",
        )
        waitUntil("WORK_DELETE_SELECTION_CLEARED_AFTER_RECREATION") {
            visibleTexts().none { it in setOf("2 ausgewählt", "Löschen", "Löschen …") }
        }
        yes(node("Löschen") == null, "WORK_DELETE_DURABLE_AFTER_RECREATION")
    }

    private fun graphicsNavigationJourney() {
        click(UiTags.area("today"))
        shown("Einsätze heute")
        yes(node("Büro") == null, "FOUR_MAIN_AREAS_WITHOUT_OFFICE_TAB")
        for ((label, rootText) in
            listOf(
                "planning" to UiTags.PRIMARY,
                "customers" to "Suchen",
                "accounting" to "Summen",
                "today" to "Einsätze heute",
            )) {
            click(UiTags.area(label))
            click(UiTags.area(label))
            shown(rootText)
            recreate()
            shown(rootText)
            if (keyboardVisible()) {
                back()
                waitUntil("MAIN_BACK_IME_HIDDEN_$label") { !keyboardVisible() }
            }
            back()
            shown("Einsätze heute")
            yes(activity?.isFinishing == false, "MAIN_BACK_STAYS_IN_APP_$label")
        }
        click(UiTags.MORE)
        shown("Organisieren")
        back()
        shown("Einsätze heute")
        openCustomerList()
        enter("Suchen", fixturePrefix)
        shown("55 Treffer · 1–50")
        shown(fixturePrefix + "000")
        captureSyntheticScreen("graphics-customers")
        enter("Suchen", fixturePrefix + "000")
        click(fixturePrefix + "000")
        shown(fixturePrefix + "000")
        primaryPosition("Arbeit starten", "Bearbeiten")
        captureSyntheticScreen("graphics-customer")
        val customerId = customers(fixturePrefix + "000").single().getString("id")
        val start =
            LocalDate.now(ZoneId.of("Europe/Vienna"))
                .atTime(10, 0)
                .atZone(ZoneId.of("Europe/Vienna"))
                .toInstant()
        read(
            "create_appointment",
            JSONObject()
                .put("request_id", "qa_graphics_appointment")
                .put("customer_id", customerId)
                .put("title", "[TEST] QA Grafik Gartenpflege")
                .put("starts_at", start.toString())
                .put("ends_at", start.plusSeconds(3600).toString())
                .put("timezone", "Europe/Vienna")
                .put("weather_profile", "indoor")
                .put("status", "tentative"),
        )
        openOffice()
        click(UiTags.area("planning"))
        shown(UiTags.PRIMARY)
        reveal("[TEST] QA Grafik Gartenpflege")
        scrollTop()
        captureSyntheticScreen("graphics-planning")
        val before =
            PixelMcpWriteTools.hash(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("offers")
            )
        openOffice()
        click("Angebote")
        shown("Angebot anlegen")
        captureSyntheticScreen("graphics-offers")
        click("Angebot anlegen")
        shown("Weiter zu Leistungen")
        reveal("Empfänger und Auftrag")
        click("Kunde: Nicht zugeordnet")
        enter("Suchen", fixturePrefix + "000")
        click(fixturePrefix + "000")
        shown("Kunde: " + fixturePrefix + "000")
        enter("Titel *", "[TEST] QA Grafik Gartenpflege")
        scrollTop()
        shown("Kunde: " + fixturePrefix + "000")
        captureSyntheticScreen("graphics-offer-customer")
        click("Weiter zu Leistungen")
        shown("Positionen")
        shown("Eigene Position hinzufügen")
        click("Eigene Position hinzufügen")
        enter("Bezeichnung *", "[TEST] QA Grafik Rasenpflege")
        enter("Menge *", "2")
        enter("Einheit *", "Std.")
        enter("Einzelpreis in EUR *", "35,00")
        enter("Steuersatz in %", "0")
        click("Diesen Einzelpreis ausdrücklich bestätigt")
        val offerDraftKey = "native_form_draft:" + PixelMcpWriteTools.hash("finance:offers:new")
        waitUntil("GRAPHICS_OFFER_HAS_REAL_SELECTED_CUSTOMER_AND_POSITION") {
            val record =
                runCatching {
                        JSONObject(store.ownerValue(offerDraftKey, "")).getJSONObject("record")
                    }
                    .getOrNull()
            val positions = record?.optJSONArray("positions")
            val position = positions?.optJSONObject(0)
            if (record == null || positions == null || position == null) return@waitUntil false
            record.optString("customer_id") == customerId &&
                positions.length() == 1 &&
                position.optString("title") == "[TEST] QA Grafik Rasenpflege" &&
                position.optString("quantity") == "2" &&
                position.optString("unit") == "Std." &&
                position.optString("price").replace(',', '.').toDoubleOrNull() == 35.0 &&
                position.optString("price_source") == "user_confirmed"
        }
        click("1. [TEST] QA Grafik Rasenpflege")
        scrollTop()
        shown("1. [TEST] QA Grafik Rasenpflege")
        captureSyntheticScreen("graphics-offer-positions")
        click("Entwurf verwerfen")
        shown("Angebot anlegen")
        waitUntil("GRAPHICS_OFFER_DISCARD_REMOVES_DRAFT") {
            store.ownerValue(offerDraftKey, "").isEmpty()
        }
        yes(
            PixelMcpWriteTools.hash(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("offers")
            ) == before,
            "GRAPHICS_OFFER_PREVIEW_DOES_NOT_SAVE",
        )
        openOffice()
        click("Geräte")
        shown("Gerät anlegen")
        captureSyntheticScreen("graphics-equipment")
    }

    private fun launch(planning: Boolean = false, appointment: String = "", action: String = "") {
        shell("wm dismiss-keyguard")
        // Each launch intentionally starts a fresh synthetic task. Without CLEAR_TASK,
        // Android can only bring a child connection Activity to the front and
        // startActivitySync waits forever for a MainActivity that is not created.
        activity =
            test.startActivitySync(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("open_planning", planning)
                    .putExtra("planning_appointment_id", appointment)
                    .putExtra("planning_action", action)
            )
        latest = activity
        waitUntil("MAIN_COMPOSE_CONTENT") {
            var found = false
            test.runOnMainSync { found = hasCompose(requireNotNull(activity).window.decorView) }
            found && automation.rootInActiveWindow?.packageName?.toString() == context.packageName
        }
        checks++
    }

    private fun hasCompose(view: View): Boolean =
        view.javaClass.name.startsWith("androidx.compose.ui.platform.") ||
            view is ViewGroup && (0 until view.childCount).any { hasCompose(view.getChildAt(it)) }

    private fun recreate() {
        val old = requireNotNull(activity)
        latest = null
        val callbacks =
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(value: Activity, state: Bundle?) {
                    if (value is MainActivity && value !== old) latest = value
                }

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
        } finally {
            old.application.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }

    private fun execute(selectedJourney: String): JSONObject {
        guardGraphicsFixture()
        val previousInfo = automation.serviceInfo
        val previousFlags = previousInfo.flags
        val oldFont = shell("settings get system font_scale")
        try {
            automation.serviceInfo =
                previousInfo.apply {
                    flags =
                        flags or
                            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                }
            seedPaging()
            launch()
            var ran = false
            fun journey(name: String, run: () -> Unit) {
                if (selectedJourney != name) return
                yes(!ran, "ONE_JOURNEY_PER_PROCESS")
                ran = true
                test.sendStatus(0, Bundle().apply { putString("journey_start", name) })
                try {
                    run()
                } catch (failure: Throwable) {
                    test.sendStatus(
                        0,
                        Bundle().apply {
                            putString("journey_failure", "$name: " + failure.stackTraceToString())
                        },
                    )
                    runCatching {
                        captureSyntheticScreen("failure-$name")
                        logVisibleActions("failure_$name")
                    }
                    throw failure
                }
            }
            journey("today_hub", ::todayJourney)
            journey("customer", ::customerJourney)
            journey("native_pickers", ::pickerJourney)
            journey("pending_result_read_only", ::pendingResultJourney)
            journey("usability", ::usabilityJourney)
            journey("work_photo_retention") {
                workPhotoRetention(seedWork("[TEST] QA photo retention"))
            }
            journey("accessories", ::accessoryJourney)
            journey("timer") { timerJourney() }
            journey("today_older_work_commit", ::todayOlderWorkJourney)
            journey("notification", ::notificationJourney)
            journey("customer_context", ::customerContextJourney)
            journey("office_opening", ::officeOpeningMeasurements)
            journey("search_keyboard", ::searchKeyboardJourney)
            journey("back_gesture", ::backGestureJourney)
            journey("domain_search", ::domainSearchJourney)
            journey("grouped_search", ::groupedSearchJourney)
            journey("list_actions", ::listActionsJourney)
            journey("planning_board", ::planningBoardJourney)
            journey("property_timeline", ::propertyTimelineJourney)
            journey("background_feedback", ::backgroundFeedbackJourney)
            journey("list_viewports", ::listViewportJourney)
            journey("filter_keyboard", ::filterKeyboardJourney)
            journey("surfaces", ::surfaceJourney)
            journey("large_text", ::largeTextJourney)
            journey("storage_settings", ::storageSettingsJourney)
            journey("camera_capture", ::cameraCaptureJourney)
            yes(ran, "EXPLICIT_KNOWN_JOURNEY_REQUIRED")
            return JSONObject()
                .put("checks", checks)
                .put("synthetic_only", true)
                .put("productive_data_read", false)
                .put("journey", selectedJourney)
                .put("independent_fresh_fixture", true)
                .put("real_main_activity", true)
                .put("native_compose_ui", true)
                .put("search_keyboard_matrix", searchMatrix)
                .put("domain_search_cases", domainSearchCases)
                .put("surface_cases", surfaceCases)
                .put("list_viewport_cases", listViewportCases)
                .put("office_opening", officeMeasurements)
                .put("drive_account_tested", false)
                .put("physical_pixel_tested", false)
        } finally {
            shell(
                if (oldFont == "null") "settings delete system font_scale"
                else "settings put system font_scale $oldFont"
            )
            activity?.let { current ->
                test.runOnMainSync { if (!current.isFinishing) current.finish() }
            }
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
                    if (targets.any { performVerticalScroll(it, forward = false) }) settleUi()
                    lastScroll = now
                }
                false
            }
        }
    }

    private fun draftFailure(key: String): Throwable? {
        // Read the real process-wide failure record; no production injection switch is added.
        val type =
            Class.forName(
                "at.hausgarten.service.NativeDraftPersistence",
                true,
                MainActivity::class.java.classLoader,
            )
        val field = type.getDeclaredField("failures").apply { isAccessible = true }
        return (field.get(null) as Map<*, *>)[key] as? Throwable
    }

    private fun customerDraftWriteFailure(draftKey: String) {
        val logicalKey = "editor:customer:new"
        val transientName = createdName + " transient"
        val durableBeforeFailure = store.ownerValue(draftKey, "")
        yes(draftFailure(logicalKey) == null, "DRAFT_FAILURE_INITIAL_CLEAR")
        yes(
            JSONObject(durableBeforeFailure).getJSONObject("values").getString("name") ==
                createdName,
            "DRAFT_FAILURE_BASE_IS_DURABLE",
        )
        val database = store.writableDatabase
        val trigger = "qa_native_customer_draft_write_failure"
        var installed = false
        try {
            // A persistent SQLite trigger applies to the actual Activity writer's connection.
            database.execSQL(
                "CREATE TRIGGER $trigger BEFORE INSERT ON owner_state WHEN NEW.key='$draftKey' " +
                    "BEGIN SELECT RAISE(ABORT,'QA_DRAFT_WRITE_FAILURE'); END"
            )
            installed = true
            enter("Anzeigename *", transientName)
            waitUntil("REAL_DRAFT_WRITE_FAILURE_RECORDED") { draftFailure(logicalKey) != null }
            // The store preserves its public LOCAL_STATE_WRITE_FAILED wrapper;
            // require the actual injected SQLite trigger failure in its cause chain.
            val failures = generateSequence(draftFailure(logicalKey)) { it.cause }.take(16).toList()
            yes(
                failures.any {
                    it is android.database.sqlite.SQLiteConstraintException &&
                        it.message?.contains("QA_DRAFT_WRITE_FAILURE") == true
                },
                "ACTUAL_SQLITE_DRAFT_FAILURE",
            )
            yes(
                store.ownerValue(draftKey, "") == durableBeforeFailure,
                "FAILED_DRAFT_WRITE_PRESERVES_DISK_VALUE",
            )
        } finally {
            if (installed) database.execSQL("DROP TRIGGER $trigger")
        }
        recreate()
        waitUntil("LATEST_FAILED_DRAFT_SURVIVES_RECREATION") {
            field("Anzeigename *")?.text?.toString()?.contains(transientName) == true
        }
        yes(
            store.ownerValue(draftKey, "") == durableBeforeFailure,
            "RECREATION_DOES_NOT_HIDE_FAILED_DISK_WRITE",
        )
        yes(customers(transientName).isEmpty(), "FAILED_DRAFT_IS_NOT_CUSTOMER")
        enter("Anzeigename *", createdName)
        waitUntil("DRAFT_WRITE_RECOVERED") {
            val saved = store.ownerValue(draftKey, "")
            saved.isNotEmpty() &&
                JSONObject(saved).getJSONObject("values").optString("name") == createdName &&
                draftFailure(logicalKey) == null
        }
        yes(draftFailure(logicalKey) == null, "SUCCESSFUL_EDIT_CLEARS_REAL_DRAFT_FAILURE")
    }

    private fun customerJourney() {
        openCustomerList()
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
        click(UiTags.CREATE)
        click("Kunde")
        waitUntil("BLANK_CUSTOMER_FORM") { field("Anzeigename *") != null }
        val draftKey = "native_form_draft:" + PixelMcpWriteTools.hash("editor:customer:new")
        yes(store.ownerValue(draftKey, "").isEmpty(), "UNTOUCHED_FORM_HAS_NO_DRAFT")
        enter("Anzeigename *", createdName)
        waitUntil("CUSTOMER_DRAFT_DURABLE") { store.ownerValue(draftKey, "").contains(createdName) }
        yes(customers(createdName).isEmpty(), "CUSTOMER_DRAFT_IS_NOT_RECORD")
        customerDraftWriteFailure(draftKey)
        recreate()
        waitUntil("CUSTOMER_DRAFT_AFTER_RECREATION") {
            field("Anzeigename *")?.text?.toString()?.contains(createdName) == true
        }
        yes(customers(createdName).isEmpty(), "RECREATION_DOES_NOT_SAVE_CUSTOMER")
        primaryPosition("Kunde speichern", "Zurück")
        click(UiTags.PRIMARY)
        waitUntil("CUSTOMER_CREATED") { customers(createdName).size == 1 }
        val id = customers(createdName).single().getString("id")
        openCustomerList()
        enter("Suchen", createdName)
        click(createdName)
        shown(createdName)
        yes(store.ownerValue(draftKey, "").isEmpty(), "SAVE_REMOVES_CUSTOMER_DRAFT")
        click("Bearbeiten")
        enter("Anzeigename *", editedName)
        val remoteNote = "[TEST] remote note"
        val externalBefore = tools.call("get_customer", JSONObject().put("customer_id", id))
        yes(!externalBefore.has("ok") || externalBefore.getBoolean("ok"), "MCP_CUSTOMER_CAS_BASE")
        read(
            "update_customer",
            JSONObject()
                .put("request_id", "qa_native_customer_cas_note_001")
                .put("customer_id", id)
                .put("expected_record_hash", externalBefore.getString("record_hash"))
                .put("updates", JSONObject().put("notes", remoteNote)),
        )
        // The open form still owns its original baseline. A remote write must cause
        // an explicit conflict, rather than silently refreshing the hash at save time.
        click(UiTags.PRIMARY)
        scrollTop()
        shown(
            "Der Eintrag wurde zwischenzeitlich verändert. Vergleiche den aktuellen Stand mit deinen Eingaben."
        )
        yes(customers(editedName).isEmpty(), "STALE_UI_SAVE_DOES_NOT_APPLY_NAME")
        yes(
            customers(createdName).single().let {
                it.getString("id") == id && it.optString("notes") == remoteNote
            },
            "CONFLICT_RETAINS_REMOTE_CUSTOMER",
        )
        click("Aktuellen Stand zum Vergleichen lesen")
        reveal("Notizen: " + remoteNote)
        yes(customers(editedName).isEmpty(), "COMPARISON_READ_DOES_NOT_SAVE_CUSTOMER")
        click("Geprüfte Eingaben als neuen Entwurf fortsetzen")
        scrollTop()
        waitUntil("CUSTOMER_REBASED_EDITOR") {
            field("Anzeigename *")?.text?.toString()?.contains(editedName) == true
        }
        yes(customers(editedName).isEmpty(), "EXPLICIT_REBASE_REMAINS_UNSAVED_DRAFT")
        click(UiTags.PRIMARY)
        waitUntil("CUSTOMER_EDITED") { customers(editedName).singleOrNull()?.optString("id") == id }
        yes(
            customers(editedName).single().optString("notes") == remoteNote,
            "REVIEWED_SAVE_PRESERVES_REMOTE_NOTE",
        )
        yes(customers(createdName).isEmpty(), "CUSTOMER_UPDATE_PRESERVES_ID_WITHOUT_DUPLICATE")
        reveal("Bearbeiten")
        val customerNavigation =
            nodes().firstNotNullOfOrNull { candidate ->
                if (matches(candidate, "Kunden")) clickable(candidate) else null
            }
        yes(
            customerNavigation != null &&
                customerNavigation.isClickable &&
                customerNavigation.stateDescription?.toString() == "Ausgewählter Bereich",
            "CUSTOMER_DETAIL_SELECTED_AREA_EXPOSES_ACCESSIBLE_LIST_NAVIGATION",
        )
        openCustomerList()
        enter("Suchen", "QA Native F")
        shown("1 Treffer · 1–1")
        shown(editedName)
        // This uses the real MCP writer on a separate adapter. No refresh, navigation or
        // direct notification is allowed between this write and the visible UI assertion.
        val envelope = tools.call("get_customer", JSONObject().put("customer_id", id))
        yes(!envelope.has("ok") || envelope.getBoolean("ok"), "MCP_CUSTOMER_READ")
        val hash = envelope.getString("record_hash")
        read(
            "update_customer",
            JSONObject()
                .put("request_id", "qa_native_visible_flow_001")
                .put("customer_id", id)
                .put("expected_record_hash", hash)
                .put("updates", JSONObject().put("name", flowName)),
        )
        shown(flowName)
        yes(node(editedName) == null, "COMMITTED_MCP_EDIT_REPLACES_VISIBLE_ROW")
        click(flowName)
        click("Weitere Angaben")
        click("Löschen")
        waitUntil("CUSTOMER_ARCHIVED") {
            customers(flowName).isEmpty() ||
                customers(flowName).all {
                    it.opt("active") == false ||
                        it.optInt("active", 1) == 0 ||
                        it.optBoolean("archived")
                }
        }
        waitUntil("CUSTOMER_DELETE_RETURNS_TO_LIST") {
            selectedArea(UiTags.area("customers")) &&
                field("Suchen")?.isEditable == true &&
                node(UiTags.CREATE)?.let(::clickable) != null
        }
        enter("Suchen", "QA Native F")
        shown("0 Treffer · 0–0")
        yes(node(flowName) == null, "ARCHIVED_CUSTOMER_NOT_IN_ACTIVE_LIST")
    }

    private fun usabilityJourney() {
        click(UiTags.area("today"))
        reveal("Einsätze heute")
        yes(node("Weitere Tageshinweise") == null, "TODAY_HAS_NO_EXTRA_HINT_CARD")
        yes(
            node(UiTags.MORE)?.let(::clickable) != null &&
                node(UiTags.CREATE)?.let(::clickable) != null,
            "TODAY_HEADER_MORE_AND_CREATE_BUTTON",
        )
        openCustomerList()
        enter("Suchen", fixturePrefix)
        click("Weitere 50")
        scrollTop()
        shown("55 Treffer · 51–55")
        reveal(fixturePrefix + "054")
        // Compare positions without the keyboard on both sides; it changes the visible area.
        if (keyboardVisible()) {
            back()
            waitUntil("USABILITY_IME_HIDDEN") { !keyboardVisible() }
        }
        reveal(fixturePrefix + "054")
        val retainedBounds = actionBounds(fixturePrefix + "054")
        click(UiTags.area("today"))
        click(UiTags.area("customers"))
        shown("55 Treffer · 51–55")
        yes(
            field("Suchen")?.text?.toString()?.contains(fixturePrefix) == true,
            "CUSTOMER_FILTER_PRESERVED_ON_ENTRY",
        )
        yes(
            actionBounds(fixturePrefix + "054") == retainedBounds,
            "CUSTOMER_SCROLL_POSITION_PRESERVED_ON_ENTRY",
        )
        recreate()
        shown("55 Treffer · 51–55")
        yes(
            actionBounds(fixturePrefix + "054") == retainedBounds,
            "CUSTOMER_PAGE_AND_SCROLL_SURVIVE_RECREATION",
        )
        click(UiTags.area("today"))
        newWork()
        openWorkCustomerPicker()
        enter("Suchen", fixturePrefix + "000")
        click(fixturePrefix + "000")
        chooseTime("Beginn", "08:00")
        chooseTime("Ende", "12:00")
        waitUntil("WORK_TIMES_DURABLE") {
            val values = workDraft().optJSONObject("values")
            values?.optString("start_time") == "08:00" && values.optString("end_time") == "12:00"
        }
        reveal("Pause (Minuten)")
        click("30")
        enter("Freie Minuten", "23")
        val ownText = "[TEST] QA usability work"
        enter("Was wurde erledigt? *", ownText)
        click("Textbausteine ▾")
        click("Rasen gemäht")
        reveal("Was wurde erledigt? *")
        waitUntil("OWN_TEXT_RETAINED_AFTER_SNIPPET") {
            field("Was wurde erledigt? *")?.text?.toString() == ownText + "\nRasen gemäht"
        }
        click("Weitere Angaben")
        enter("Freier Betrag (€)", "73,40")
        enter("Notizen", "[TEST] QA sofort sichtbare Notiz")
        checkWorkKeyboardActions()
        recreate()
        reveal("Freier Betrag (€)")
        yes(
            field("Freier Betrag (€)")?.text?.toString()?.contains("73,40") == true,
            "FREE_DISPOSAL_SURVIVES_RECREATION",
        )
        primaryPosition("Arbeit speichern", "Zurück")
        click(UiTags.PRIMARY)
        fun saved(): JSONObject? {
            val rows = PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            return (0 until rows.length())
                .map { rows.getJSONObject(it) }
                .firstOrNull { it.optString("activity").startsWith(ownText) }
        }
        waitUntil("SAVED_WORK") { saved() != null }
        val row = requireNotNull(saved())
        yes(
            row.getInt("break_minutes") == 23 && row.getDouble("hours") == 3.62,
            "PAUSE_DEDUCTED_ONCE",
        )
        yes(
            row.getDouble("disposal_cost") == 73.4 &&
                row.getString("activity") == ownText + "\nRasen gemäht",
            "DISPOSAL_AND_OWN_TEXT_AND_PHRASE_SAVED",
        )
        // Today no longer lists saved work (design); open it from Mehr → Arbeiten.
        openWorklogs()
        enter("Suchen", ownText)
        click(ownText)
        reveal("[TEST] QA sofort sichtbare Notiz")
        click("Tätigkeit")
        enter("Was wurde erledigt? *", "[TEST] QA ungespeichert")
        discardWork()
        yes(saved()?.getString("id") == row.getString("id"), "DISCARD_PRESERVES_SAVED_WORK")
        yes(
            saved()?.getString("activity") == row.getString("activity"),
            "DISCARD_DOES_NOT_SAVE_CHANGES",
        )
        click(UiTags.area("today"))
        newWork()
        openWorkCustomerPicker()
        enter("Suchen", fixturePrefix + "000")
        click(fixturePrefix + "000")
        enter("Was wurde erledigt? *", "[TEST] QA freie Dezimalwerte")
        chooseTime("Beginn", "08:00")
        chooseTime("Ende", "12:00")
        click("30")
        click("Zeitangabe: Uhrzeiten")
        click("Dauer", scrollTo = false)
        enter("Stunden", "3")
        enter("Minuten", "30")
        shown("Reine Arbeitszeit. Keine Pause zusätzlich abziehen.")
        yes(
            node("Pause (Minuten)") == null && node("Beginn") == null && node("Ende") == null,
            "DURATION_EXCLUDES_TIMES_AND_PAUSE",
        )
        // The new-work route retains the expanded details from the first entry.
        enter("Kilometer", "7,25")
        enter("Materialkosten", "12,50")
        click(UiTags.PRIMARY)
        waitUntil("DECIMAL_WORK_SAVED") {
            val rows = PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            (0 until rows.length())
                .map { rows.getJSONObject(it) }
                .any {
                    it.optString("activity") == "[TEST] QA freie Dezimalwerte" &&
                        it.optDouble("hours") == 3.5 &&
                        it.optInt("break_minutes") == 0 &&
                        it.optString("start_time").isEmpty() &&
                        it.optString("end_time").isEmpty() &&
                        it.optDouble("kilometers") == 7.25 &&
                        it.optString("material").contains("12.50")
                }
        }
        checks++
    }

    private fun workDraft(): JSONObject =
        JSONObject(
            store
                .ownerValue(
                    "native_form_draft:" + PixelMcpWriteTools.hash("editor:worklog:new"),
                    "{}",
                )
                .ifBlank { "{}" }
        )

    private fun pickerJourney() {
        click(UiTags.area("today"))
        newWork()
        val count = PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length()
        openWorkCustomerPicker()
        enter("Suchen", fixturePrefix + "000")
        click(fixturePrefix + "000")
        chooseDate("Datum *", "2026-11-03")
        waitUntil("PICKED_DATE_DURABLE") {
            workDraft().optJSONObject("values")?.optString("work_date") == "2026-11-03"
        }
        val request = workDraft().getJSONObject("values").getString("_request_id")
        val dateBeforeCancel = PixelMcpWriteTools.hash(workDraft().getJSONObject("values"))
        chooseDate("Datum *", "2027-05-19", confirm = false)
        yes(
            PixelMcpWriteTools.hash(workDraft().getJSONObject("values")) == dateBeforeCancel,
            "CANCEL_DATE_LEAVES_DRAFT_UNCHANGED",
        )
        chooseTime("Beginn", "09:00")
        chooseTime("Ende", "10:36")
        waitUntil("PICKED_TIMES_DURABLE") {
            workDraft().optJSONObject("values")?.optString("end_time") == "10:36"
        }
        val timeBeforeCancel = PixelMcpWriteTools.hash(workDraft().getJSONObject("values"))
        chooseTime("Beginn", "11:45", confirm = false)
        yes(
            PixelMcpWriteTools.hash(workDraft().getJSONObject("values")) == timeBeforeCancel,
            "CANCEL_TIME_LEAVES_DRAFT_UNCHANGED",
        )
        repeat(12) { index ->
            click("Zeitangabe: " + if (index % 2 == 0) "Uhrzeiten" else "Dauer")
            click(if (index % 2 == 0) "Dauer" else "Uhrzeiten", scrollTo = false)
        }
        waitUntil("LOCAL_SELECTIONS_KEEP_REQUEST_ID") {
            workDraft().getJSONObject("values").optString("_request_id") == request &&
                workDraft().optString("time_mode") == "times"
        }
        chooseTime("Beginn", "09:00")
        chooseTime("Ende", "10:36")
        enter("Freie Minuten", "12")
        enter("Was wurde erledigt? *", "[TEST] QA Picker und lokale Auswahl")
        primaryPosition("Arbeit speichern", "Zurück")
        val fixed = actionBounds(UiTags.PRIMARY)
        reveal("Entwurf verwerfen")
        yes(actionBounds(UiTags.PRIMARY) == fixed, "PRIMARY_POSITION_FIXED_WHEN_FORM_SCROLLS")
        recreate()
        waitUntil("PICKER_VALUES_SURVIVE_RECREATION") {
            val values = workDraft().optJSONObject("values")
            values?.optString("work_date") == "2026-11-03" &&
                values.optString("start_time") == "09:00" &&
                values.optString("end_time") == "10:36" &&
                values.optInt("break_minutes") == 12
        }
        click("Zurück")
        shown("Einsätze heute")
        yes(node(UiTags.BACK) == null, "TWELVE_LOCAL_SELECTIONS_CREATE_NO_BACK_HISTORY_CHAIN")
        yes(
            PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() ==
                count,
            "PICKERS_AND_LOCAL_SELECTIONS_DO_NOT_SAVE_WORK",
        )
        newWork()
        discardWork()
    }

    private fun pendingResultJourney() {
        val controller = NativeAppController(requireNotNull(activity) as MainActivity, store)
        val original =
            JSONObject()
                .put("_request_id", "qa_native_unknown_work_result")
                .put("customer_id", customers(fixturePrefix + "000").single().getString("id"))
                .put("work_date", LocalDate.now(ZoneId.of("Europe/Vienna")).toString())
                .put("activity", "[TEST] QA unknown save result")
                .put("hours", 1)
        controller.saveDraft(
            "editor:worklog:new",
            JSONObject()
                .put("values", JSONObject(original.toString()))
                .put("original_args", JSONObject(original.toString())),
        )
        runBlocking { controller.flushDrafts() }
        val before = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
        val count = PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length()
        val requestBefore = PixelMcpWriteTools.hash(original)
        click(UiTags.area("today"))
        newWork()
        try {
            repeat(2) { index ->
                shown(UiTags.inspection("waiting"))
                waitStable("AUTOMATIC_INSPECTION_${index}_READ_ONLY", stableMs = 3500) {
                    PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) ==
                        before &&
                        PixelMcpReadTools.effective(store.getState())
                            .getJSONArray("worklogs")
                            .length() == count &&
                        PixelMcpWriteTools.hash(workDraft().getJSONObject("original_args")) ==
                            requestBefore
                }
                yes(
                    workDraft().getJSONObject("original_args").getString("_request_id") ==
                        "qa_native_unknown_work_result",
                    "BACKGROUND_INSPECTION_${index}_KEEPS_ORIGINAL_ID",
                )
                if (index == 0) recreate()
            }
            click(UiTags.PRIMARY)
            shown(UiTags.CREATE)
        } finally {
            runBlocking { controller.removeDraft("editor:worklog:new") }
        }
    }

    private fun checkWorkKeyboardActions() {
        val input = requireNotNull(field("Notizen"))
        yes(input.performAction(AccessibilityNodeInfo.ACTION_CLICK), "WORK_FIELD_CLICK")
        waitUntil("WORK_KEYBOARD_VISIBLE") {
            var visible = false
            test.runOnMainSync {
                visible =
                    requireNotNull(activity)
                        .window
                        .decorView
                        .rootWindowInsets
                        ?.isVisible(WindowInsets.Type.ime()) == true
            }
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
                val location = IntArray(2)
                decor.getLocationOnScreen(location)
                keyboardTop =
                    location[1] + decor.height -
                        requireNotNull(decor.rootWindowInsets)
                            .getInsets(WindowInsets.Type.ime())
                            .bottom
            }
            val keyboardShown =
                automation.windows.any {
                    it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                        it.root?.isVisibleToUser == true
                }
            val save = runCatching { actionBounds(UiTags.PRIMARY) }.getOrNull()
            val discard = runCatching { actionBounds("Zurück") }.getOrNull()
            val clear =
                keyboardShown &&
                    save != null &&
                    discard != null &&
                    frame.contains(save) &&
                    frame.contains(discard) &&
                    save.bottom <= keyboardTop &&
                    discard.bottom <= keyboardTop &&
                    !Rect.intersects(save, discard)
            val geometry =
                "$frame keyboardTop=$keyboardTop keyboardShown=$keyboardShown save=$save discard=$discard"
            if (geometry != lastGeometry) {
                android.util.Log.i("NativeComposeQA", "work_keyboard_bounds $geometry")
                lastGeometry = geometry
                clearSince = 0L
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
            test.runOnMainSync {
                hidden =
                    requireNotNull(activity)
                        .window
                        .decorView
                        .rootWindowInsets
                        ?.isVisible(WindowInsets.Type.ime()) == false
            }
            hidden
        }
    }

    private fun accessoryJourney() {
        openOffice()
        click("Geräte")
        click("Gerät anlegen")
        click("Gerätebezeichnung auswählen ▾")
        shown("Gerätebezeichnung auswählen")
        logVisibleActions("equipment_suggestions_open")
        enter("Vorschläge durchsuchen", "kett")
        click("Kettensäge")
        waitUntil("EQUIPMENT_SEARCHABLE_PRESET") {
            field("Gerätebezeichnung *")?.text?.toString() == "Kettensäge"
        }
        checks++
        val name = "[TEST] QA Zubehör Säge"
        enter("Gerätebezeichnung *", name)
        click("Speichern")
        reveal("Übersicht")
        click("Zubehör")
        click("Zubehör hinzufügen")
        shown("Fotos helfen beim Wiederfinden. Technische Angaben sind freiwillig.")
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
            return (0 until rows.length())
                .map { rows.getJSONObject(it) }
                .first { it.optString("name") == name }
        }
        val first = machine()
        val a = first.getJSONArray("accessories").getJSONObject(0)
        yes(
            first.getString("current_chain_id") == a.getString("id") &&
                a.getString("equipment_id") == first.getString("id"),
            "UI_ACCESSORY_CURRENT_MACHINE",
        )
        yes(
            a.getString("pitch") == "3/8 LP" &&
                a.getString("gauge") == "1.1 mm" &&
                a.getString("drive_links") == "45" &&
                a.getString("file_size") == "4 mm",
            "UI_ACCESSORY_SPECIFICATIONS",
        )
        // Synthetic photo traverses the real native upload/store path. Picker UI remains a separate
        // test.
        val source = File(context.cacheDir, "qa-accessory.png")
        val image = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        image.eraseColor(0xff1c7a38.toInt())
        source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        val controller = NativeAppController(requireNotNull(activity) as MainActivity, store)
        try {
            val upload = runBlocking {
                controller.stageEquipmentPhoto(Uri.fromFile(source), "qa_accessory_photo")
            }
            val b =
                JSONObject()
                    .put("id", "qa_ui_chain_b")
                    .put("type", "chain")
                    .put("name", "QA Kette B")
                    .put("photo_upload_id", upload)
            val args =
                JSONObject()
                    .put("request_id", "qa_accessory_second")
                    .put("equipment_id", first.getString("id"))
                    .put("expected_record_hash", first.getString("record_hash"))
                    .put(
                        "accessories",
                        JSONArray()
                            .put(
                                JSONObject(a.toString()).apply {
                                    remove("equipment_id")
                                    remove("photo")
                                }
                            )
                            .put(b),
                    )
                    .put("current_chain_id", "qa_ui_chain_b")
            read("save_equipment", args)
            val second = machine()
            yes(
                second.getString("current_chain_id") == "qa_ui_chain_b" &&
                    second.getJSONArray("accessories").length() == 2,
                "ONLY_ONE_CURRENT_CHAIN",
            )
            val storedB = second.getJSONArray("accessories").getJSONObject(1)
            yes(
                !storedB.has("pitch") || storedB.optString("pitch").isEmpty(),
                "UNKNOWN_SPECIFICATION_NOT_INVENTED",
            )
            val photo = storedB.getJSONObject("photo")
            val native =
                RevierApplicationService(RevierLocalRepository(store, null))
                    .equipmentPhoto(photo.getString("id"))
            yes(
                native?.getString("content_sha256") == photo.getString("content_sha256"),
                "ACCESSORY_PHOTO_REOPEN",
            )
            // Machine navigation intentionally remembers its current detail.
            // Leave through the visible list action before testing a fresh open.
            click("Zur Liste")
            click(UiTags.area("today"))
            openOffice()
            click("Geräte")
            click(name)
            click("Zubehör")
            reveal("QA Kette B")
            reveal("Aktuell verwendete Kette")
            recreate()
            reveal("QA Kette B")
            val photoLabel = "Foto von QA Kette B · zum Vergrößern antippen"
            reveal(photoLabel)
            reveal("Foto ändern")
            captureSyntheticScreen("accessory")
            click(photoLabel)
            shown("Foto von QA Kette B")
            shown("Schließen")
            captureSyntheticScreen("accessory-enlarged")
            click("Schließen")
            val beforePhotoShortcut = machine().getString("record_hash")
            click("Foto ändern")
            // Wait for the selected accessory's photo geometry before locating
            // duplicated field labels; a lower A card is not the focused B card.
            reveal("Zubehörfoto · zum Vergrößern antippen")
            scrollTop()
            reveal("Bezeichnung")
            yes(
                field("Bezeichnung")?.text?.toString() == "QA Kette B",
                "ACCESSORY_PHOTO_SHORTCUT_FOCUSES_OWN_ACCESSORY",
            )
            reveal("Foto aufnehmen")
            shown("Foto auswählen")
            captureSyntheticScreen("accessory-editor")
            back()
            reveal("Foto ändern")
            yes(
                machine().getString("record_hash") == beforePhotoShortcut,
                "ACCESSORY_PHOTO_PREVIEW_AND_EDITOR_DO_NOT_SAVE",
            )
            click("Übersicht")
            reveal("Wartung einrichten")
        } finally {
            source.delete()
        }
    }

    private fun workPhotoRetention(work: JSONObject) = runBlocking {
        val controller = NativeAppController(requireNotNull(activity) as MainActivity, store)
        val source = File(context.cacheDir, "qa-work-entry-photo.png")
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff1c7a38.toInt())
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val draft = nativeRetainPhotoDraft(controller, Uri.fromFile(source))
        val other = nativeRetainPhotoDraft(controller, Uri.fromFile(source))
        val identity = BackupDeviceGuard(context).current()
        val args =
            JSONObject()
                .put("_photo_request_id", "qa_work_entry_photo_001")
                .put("customer_id", work.getString("customer_id"))
                .put("worklog_id", work.getString("id"))
                .put("role", "documentation")
                .put("caption", "QA synthetic 3x3")
                .put(
                    "_capture_context",
                    JSONObject()
                        .put("formatVersion", 1)
                        .put("installation_id", identity.getString("installationId"))
                        .put("restore_epoch", identity.getString("restoreEpoch")),
                )
        val photo = controller.importPhoto(draft, args)
        val photoId = photo.getString("id")
        yes(
            controller.importPhoto(draft, args).getString("id") == photoId,
            "PHOTO_REPLAY_NO_DUPLICATE",
        )
        nativeDeletePhotoDraft(controller, draft.toString())
        yes(
            !File(requireNotNull(draft.path)).exists() && File(requireNotNull(other.path)).isFile,
            "DISCARD_ONLY_OWN_TEMP_PHOTO",
        )
        val saved = read("get_photo", JSONObject().put("photo_id", photoId))
        yes(saved.toString().contains(work.getString("id")), "SAVED_PHOTO_RETAINS_WORK_RELATION")
        val attachment = requireNotNull(store.photoAttachment(photoId))
        val original = store.photoAttachmentStore().verifiedFile(attachment)
        val originalBytes = original.readBytes()
        controller.saveDraft(
            "editor:worklog:new",
            JSONObject()
                .put(
                    "values",
                    JSONObject()
                        .put("_request_id", "qa_discard_work_photo")
                        .put("activity", "QA unsaved photo draft")
                        .put("work_date", LocalDate.now(ZoneId.of("Europe/Vienna")).toString())
                        .put("hours", 1),
                )
                .put("selected_photo_uri", other.toString()),
        )
        controller.flushDrafts()
        click(UiTags.area("today"))
        newWork()
        // The form remembers whether "Weitere Angaben" was open; open it only when closed.
        reveal("Weitere Angaben")
        if (
            runCatching {
                    waitUntil("EXTRA_DETAILS_OPEN", 1500) {
                        node("Foto · optional") != null || node("Foto hinzufügen") != null
                    }
                }
                .isFailure
        )
            click("Weitere Angaben")
        reveal("Ungespeichertes Foto")
        val workCount =
            PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length()
        shown("Arbeit speichern")
        yes(!actionEnabled(UiTags.PRIMARY), "PHOTO_CUSTOMER_REQUIRED_BEFORE_SAVE_ENABLED")
        yes(
            PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() ==
                workCount,
            "PHOTO_CUSTOMER_REQUIREMENT_CHECKED_BEFORE_WORK_SAVE",
        )
        reveal("Was wurde erledigt? *")
        yes(
            field("Was wurde erledigt? *")?.text?.toString() == "QA unsaved photo draft",
            "PHOTO_DRAFT_LOADS_OWN_TEXT",
        )
        discardWork()
        waitUntil("DISCARD_REMOVES_TEMP_PHOTO_AND_DRAFT") {
            !File(requireNotNull(other.path)).exists() &&
                store
                    .ownerValue(
                        "native_form_draft:" + PixelMcpWriteTools.hash("editor:worklog:new"),
                        "",
                    )
                    .isEmpty()
        }
        yes(controller.draft("editor:worklog:new") == null, "DISCARD_REMOVES_ONLY_OWN_DRAFT")
        yes(
            original.isFile && original.readBytes().contentEquals(originalBytes),
            "DISCARD_PRESERVES_SAVED_PHOTO_BYTES",
        )
        source.delete()
    }

    private fun todayJourney() {
        click(UiTags.area("today"))
        reveal("Arbeit starten")
        for (label in
            listOf("Heute", "Planung", "Kunden", "Abrechnung", "Neu anlegen", "Mehr")) shown(label)
        // Design: four areas, only the day and due items; creating sits behind "+".
        yes(
            node("Büro") == null &&
                node("Weitere Tageshinweise") == null &&
                node("Arbeit nachtragen") == null,
            "TODAY_FOLLOWS_DESIGN",
        )
        yes(
            visibleTexts().none { it.contains("Externe Sicherung") },
            "TODAY_HAS_NO_FABRICATED_EXTERNAL_BACKUP_PROOF",
        )
        captureSyntheticScreen("today-idle")
        val before = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
        click(UiTags.CREATE)
        for (label in
            listOf("Arbeit nachtragen", "Anfrage", "Kunde", "Einsatz", "Foto", "Material")) reveal(
            label
        )
        captureSyntheticScreen("today-create")
        back()
        waitUntil("CREATE_SHEET_CLOSED") {
            node("Einsatz") == null && node(UiTags.CREATE)?.let(::clickable) != null
        }
        click("Arbeit starten")
        shown("Kunde *: Nicht zugeordnet")
        yes(!keyboardVisible(), "START_HAS_NO_FORCED_IME")
        click("Zurück")
        reveal("Arbeit starten")
        yes(
            PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) == before,
            "TODAY_START_AND_CREATE_BACK_DO_NOT_WRITE",
        )
        val noteCustomer = customers(fixturePrefix + "000").single()
        val originalNotes = noteCustomer.optString("notes")
        val workBeforeNote =
            PixelMcpWriteTools.hash(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            )
        openCustomerList()
        enter("Suchen", fixturePrefix + "000")
        click(fixturePrefix + "000")
        click("Bearbeiten")
        reveal("Notizen")
        yes(!keyboardVisible(), "CUSTOMER_NOTE_HAS_NO_FORCED_IME")
        val note =
            listOf(originalNotes, "[TEST] QA Heute Kundennotiz")
                .filter { it.isNotBlank() }
                .joinToString("\n")
        enter("Notizen", note)
        click(UiTags.PRIMARY)
        waitUntil("CUSTOMER_NOTE_SAVED") {
            customers(fixturePrefix + "000").single().optString("notes") == note
        }
        yes(
            PixelMcpWriteTools.hash(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            ) == workBeforeNote,
            "CUSTOMER_NOTE_PRESERVES_WORK_RECORDS",
        )
        click(UiTags.area("today"))
        reveal("Arbeit starten")
        val oldFont = shell("settings get system font_scale")
        try {
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            shell("settings put system font_scale 2.0")
            launch()
            val labels =
                listOf("Arbeit starten", "Neu anlegen", "Heute", "Planung", "Kunden", "Abrechnung")
            waitUntil("TODAY_LARGE_TEXT_LAYOUT_READY") {
                var fontReady = false
                test.runOnMainSync {
                    fontReady = (activity?.resources?.configuration?.fontScale ?: 0f) >= 1.99f
                }
                fontReady &&
                    !keyboardVisible() &&
                    labels.all { label ->
                        runCatching {
                                val screen =
                                    Rect().also {
                                        automation.rootInActiveWindow?.getBoundsInScreen(it)
                                    }
                                screen.contains(actionBounds(label))
                            }
                            .getOrDefault(false)
                    }
            }
            for (label in labels) {
                val bounds = actionBounds(label)
                val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                val density = activity!!.resources.displayMetrics.density
                val minimum = 48
                yes(
                    screen.contains(bounds) && bounds.height() >= minimum * density - 1,
                    "TODAY_LARGE_TEXT_ACTION_${label}_bounds=${bounds}_screen=${screen}",
                )
            }
            captureSyntheticScreen("today-large-font")
        } catch (failure: Throwable) {
            captureSyntheticScreen("failure-today-large-font")
            throw failure
        } finally {
            shell(
                if (oldFont == "null") "settings delete system font_scale"
                else "settings put system font_scale $oldFont"
            )
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            launch()
        }
    }

    private fun cameraCaptureJourney() {
        val resolved =
            shell("cmd package resolve-activity --brief -a android.media.action.IMAGE_CAPTURE")
                .lineSequence()
                .lastOrNull { it.matches(Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")) }
        yes(resolved != null, "REAL_CAMERA_ACTIVITY_INSTALLED")
        val cameraPackage = requireNotNull(resolved).substringBefore('/')
        for (permission in listOf("CAMERA", "RECORD_AUDIO")) shell(
            "pm grant $cameraPackage android.permission.$permission"
        )
        fun cameraAction(ids: List<String>, labels: List<String>): AccessibilityNodeInfo? =
            nodes()
                .firstOrNull { item ->
                    item.isVisibleToUser &&
                        item.isEnabled &&
                        clickable(item) != null &&
                        (ids.any { item.viewIdResourceName?.endsWith(":id/$it") == true } ||
                            texts(item).any { it in labels })
                }
                ?.let(::clickable)
        fun shutter(): AccessibilityNodeInfo? =
            cameraAction(
                listOf("shutter_button", "photo_video_button"),
                listOf(
                    "Capture photo",
                    "Take photo",
                    "Take picture",
                    "Shutter",
                    "Foto aufnehmen",
                    "Auslöser",
                ),
            )
        fun cameraReady() {
            waitUntil("REAL_CAMERA_READY", 45000) {
                cameraAction(
                        listOf(
                            "permission_allow_foreground_only_button",
                            "permission_allow_button",
                        ),
                        listOf("While using the app", "Allow", "Bei Nutzung der App"),
                    )
                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                cameraAction(
                        listOf("location_tagging_dialog_negative_button"),
                        listOf("No thanks", "No, thanks", "Nein, danke"),
                    )
                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                shutter() != null &&
                    automation.rootInActiveWindow?.packageName?.toString() == cameraPackage
            }
            checks++
        }
        fun photos() = PixelMcpReadTools.effective(store.getState()).getJSONArray("photos")
        val before = photos().length()
        yes(
            read("get_active_work_session").optString("state") == "IDLE",
            "CAMERA_HAS_NO_RUNNING_WORK",
        )
        yes(
            store
                .ownerValue("native_form_draft:" + PixelMcpWriteTools.hash("photo_import"), "")
                .isEmpty(),
            "CAMERA_FIXTURE_HAS_NO_PENDING_DRAFT",
        )
        openCustomerList()
        enter("Suchen", fixturePrefix + "000")
        click(fixturePrefix + "000")
        click("Fotos")
        click("Foto hinzufügen")
        click("Foto aufnehmen")
        cameraReady()
        back()
        shown("Foto hinzufügen")
        yes(photos().length() == before, "CAMERA_CANCEL_NO_PHOTO_WRITE")
        waitUntil("CAMERA_CANCEL_RELEASES_TARGET") {
            File(context.cacheDir, "photo-capture").listFiles().orEmpty().none { it.isFile }
        }
        click("Foto aufnehmen")
        cameraReady()
        captureSyntheticScreen("camera-scene")
        yes(
            requireNotNull(shutter()).performAction(AccessibilityNodeInfo.ACTION_CLICK),
            "REAL_CAMERA_SHUTTER",
        )
        waitUntil("REAL_CAMERA_RESULT", 45000) {
            if (automation.rootInActiveWindow?.packageName?.toString() == context.packageName)
                node("Ausgewähltes Foto") != null
            else {
                cameraAction(
                        listOf("done_button", "review_done", "capture_done", "intent_done_button"),
                        listOf("Done", "OK", "Accept", "Use photo", "Fertig", "Übernehmen"),
                    )
                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                false
            }
        }
        captureSyntheticScreen("camera-selected")
        click("Lösen")
        click("Kunden auswählen")
        enter("Suchen", fixturePrefix + "000")
        click(fixturePrefix + "000")
        val caption = "QA real emulator camera capture"
        enter("Bildunterschrift", caption)
        if (keyboardVisible()) back()
        click("Foto speichern")
        waitUntil("CAMERA_SQLITE_SAVED") { photos().length() == before + 1 }
        val saved =
            (0 until photos().length())
                .map { photos().getJSONObject(it) }
                .single { it.optString("caption") == caption }
        val id = saved.getString("id")
        yes(
            saved.getString("customer_id") ==
                customers(fixturePrefix + "000").single().getString("id"),
            "CAMERA_CUSTOMER_ASSOCIATION",
        )
        val original =
            store.photoAttachmentStore().verifiedFile(requireNotNull(store.photoAttachment(id)))
        val bytes = original.readBytes()
        val dimensions =
            android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dimensions)
        yes(
            bytes.isNotEmpty() && dimensions.outWidth > 0 && dimensions.outHeight > 0,
            "CAMERA_ORIGINAL_DECODES",
        )
        // Saving removes the preview; reveal the confirmation before preserving its scroll
        // position.
        reveal(
            "Das Foto wurde dieser Zuordnung hinzugefügt. Gespeicherte Arbeit bleibt auch bei einem späteren Fotofehler erhalten."
        )
        recreate()
        shown(
            "Das Foto wurde dieser Zuordnung hinzugefügt. Gespeicherte Arbeit bleibt auch bei einem späteren Fotofehler erhalten."
        )
        val reopened = PixelLocalStore(context)
        try {
            val attachment = requireNotNull(reopened.photoAttachment(id))
            yes(
                reopened
                    .photoAttachmentStore()
                    .verifiedFile(attachment)
                    .readBytes()
                    .contentEquals(bytes),
                "CAMERA_ORIGINAL_AFTER_SQLITE_REOPEN",
            )
            yes(
                PixelMcpReadTools.effective(reopened.getState())
                    .getJSONArray("photos")
                    .toString()
                    .contains(caption),
                "CAMERA_METADATA_AFTER_SQLITE_REOPEN",
            )
        } finally {
            reopened.close()
        }
        click(UiTags.area("today"))
    }

    private fun timerJourney(largeFontScreenshot: Boolean = false) {
        activity?.let { current -> test.runOnMainSync { current.finish() } }
        launch()
        openCustomerList()
        val before = PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length()
        val todayArgs =
            JSONObject()
                .put("date_from", LocalDate.now(ZoneId.of("Europe/Vienna")).toString())
                .put("date_to", LocalDate.now(ZoneId.of("Europe/Vienna")).toString())
        val todayBefore = read("get_work_record_summary", todayArgs)
        enter("Suchen", fixturePrefix + "000")
        shown("1 Treffer · 1–1")
        val searchField = requireNotNull(field("Suchen"))
        yes(
            searchField.isEditable && clickable(searchField) == null,
            "SEARCH_VALUE_IS_NOT_A_CUSTOMER_CARD_ACTION",
        )
        val customerCard = click(fixturePrefix + "000")
        yes(
            !customerCard.isEditable &&
                customerCard.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK },
            "TIMER_CUSTOMER_CARD_USES_NON_EDITABLE_ACTION",
        )
        primaryPosition("Arbeit starten", "Bearbeiten")
        click("Arbeit starten")
        waitUntil("KNOWN_CUSTOMER_STARTS_DIRECTLY") {
            read("get_active_work_session").optString("state") == "RUNNING"
        }
        yes(node("Bewusst speichern") == null, "KNOWN_CUSTOMER_HAS_NO_EXTRA_READY_SCREEN")
        val session = read("get_active_work_session")
        val id = session.getString("id")
        yes(
            session.optString("customer_id") ==
                customers(fixturePrefix + "000").single().getString("id"),
            "TIMER_SYNTHETIC_CUSTOMER",
        )
        // "Zeit läuft" also stands on the timer screen; wait for Today's own heading.
        click(UiTags.area("today"))
        shown("Aktuelle Arbeit")
        shown("Zeit läuft")
        shown("Arbeitszeit ohne Pause")
        val activeTimeBounds =
            Rect().also { requireNotNull(node("Arbeitszeit ohne Pause")).getBoundsInScreen(it) }
        val titleBounds =
            Rect().also { requireNotNull(node("Aktuelle Arbeit")).getBoundsInScreen(it) }
        yes(
            activeTimeBounds.top >= titleBounds.bottom && activeTimeBounds.height() > 0,
            "TODAY_ACTIVE_TIME_VISIBLE_WITHOUT_SCROLL",
        )
        primaryPosition("Zeit stoppen", "Pause")
        shown("Kunde: " + fixturePrefix + "000")
        captureSyntheticScreen("today-running")
        reveal("Kunde: " + fixturePrefix + "000")
        reveal("Foto hinzufügen")
        shown("Material")
        click("Pause")
        waitUntil("TODAY_PAUSED") { read("get_active_work_session").optString("state") == "PAUSED" }
        primaryPosition("Fortsetzen", "Zeit stoppen")
        val frozen = read("get_active_work_session").getLong("worked_ms")
        recreate()
        shown("Pausiert")
        yes(
            read("get_active_work_session").getLong("worked_ms") == frozen,
            "PAUSED_TIME_DOES_NOT_GROW_DURING_RECREATION",
        )
        val frozenSeconds = frozen.coerceAtLeast(0) / 1000
        val frozenClock =
            "%02d:%02d:%02d"
                .format(frozenSeconds / 3600, frozenSeconds / 60 % 60, frozenSeconds % 60)
        shown(frozenClock)
        if (largeFontScreenshot) {
            val priorFont = shell("settings get system font_scale")
            try {
                activity?.let { current -> test.runOnMainSync { current.finish() } }
                shell("settings put system font_scale 2.0")
                launch()
                waitUntil("PAUSED_TIMER_200_PERCENT_RESOURCES") {
                    (activity?.resources?.configuration?.fontScale ?: 0f) >= 1.99f
                }
                reveal("Pausiert")
                reveal(frozenClock)
                primaryPosition("Fortsetzen", "Zeit stoppen")
                val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                val clockBounds =
                    Rect().also { requireNotNull(node(frozenClock)).getBoundsInScreen(it) }
                yes(
                    screen.contains(clockBounds) &&
                        clockBounds.height() > 0 &&
                        screen.contains(actionBounds("Fortsetzen")) &&
                        screen.contains(actionBounds("Zeit stoppen")),
                    "LARGE_TEXT_TIMER_CLOCK_AND_ACTIONS_FULLY_VISIBLE",
                )
                val paused = read("get_active_work_session")
                yes(
                    paused.getString("id") == id &&
                        paused.getString("state") == "PAUSED" &&
                        paused.getLong("worked_ms") == frozen,
                    "LARGE_TEXT_TIMER_PRESERVES_EXACT_PAUSED_SESSION",
                )
                yes(
                    PixelMcpReadTools.effective(store.getState())
                        .getJSONArray("worklogs")
                        .length() == before,
                    "LARGE_TEXT_TIMER_DOES_NOT_SAVE_WORK",
                )
                captureSyntheticScreen("timer-large-font")
            } finally {
                activity?.let { current -> test.runOnMainSync { current.finish() } }
                shell(
                    if (priorFont == "null") "settings delete system font_scale"
                    else "settings put system font_scale $priorFont"
                )
                launch()
            }
            shown("Pausiert")
            shown(frozenClock)
            val restored = read("get_active_work_session")
            yes(
                restored.getString("id") == id &&
                    restored.getString("state") == "PAUSED" &&
                    restored.getLong("worked_ms") == frozen,
                "RESTORED_FONT_PRESERVES_EXACT_PAUSED_SESSION",
            )
        }
        click("Fortsetzen")
        waitUntil("TODAY_RESUMED") {
            read("get_active_work_session").optString("state") == "RUNNING"
        }
        primaryPosition("Zeit stoppen", "Pause")
        click(UiTags.area("customers"))
        val compactClock = Regex("Arbeit läuft · ([0-9]{2,}):([0-9]{2}):([0-9]{2}) ›")
        waitUntil("COMPACT_TIMER_IS_VISIBLE_AND_ACTIONABLE") {
            nodes().any {
                it.isVisibleToUser &&
                    texts(it).any { text -> compactClock.matches(text) } &&
                    clickable(it) != null
            }
        }
        captureSyntheticScreen("timer-compact")
        waitUntil("COMPACT_TIMER_OPENS_EXISTING_SESSION") {
            val label =
                nodes()
                    .firstOrNull {
                        it.isVisibleToUser && texts(it).any { text -> compactClock.matches(text) }
                    }
                    ?.let(::clickable)
            label != null && label.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        shown("Arbeitszeit ohne Pause")
        yes(
            read("get_active_work_session").getString("id") == id &&
                PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() ==
                    before,
            "COMPACT_TIMER_REOPENS_WITHOUT_DUPLICATE_OR_WORKLOG",
        )
        openWorklogs()
        enter("Suchen", "")
        yes(node("Laufende Arbeit öffnen") == null, "WORK_LIST_HAS_NO_TIMER_MENU_ENTRY")
        waitUntil("WORK_COMPACT_TIMER_OPENS_EXISTING_SESSION") {
            val label =
                nodes()
                    .firstOrNull {
                        it.isVisibleToUser && texts(it).any { text -> compactClock.matches(text) }
                    }
                    ?.let(::clickable)
            label != null && label.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        reveal("Arbeit läuft")
        reveal("Arbeitszeit ohne Pause")
        val minimumSeconds =
            (read("get_active_work_session").getLong("worked_ms") / 1000 - 1).coerceAtLeast(0)
        waitUntil("TIMER_HAS_READABLE_PERSISTED_DURATION") {
            val maximumSeconds = read("get_active_work_session").getLong("worked_ms") / 1000 + 1
            visibleTexts().any { text ->
                val clock = Regex("([0-9]{2,}):([0-9]{2}):([0-9]{2})").matchEntire(text)
                clock != null &&
                    clock.groupValues[2].toInt() < 60 &&
                    clock.groupValues[3].toInt() < 60 &&
                    clock.groupValues.let {
                        it[1].toLong() * 3600 + it[2].toLong() * 60 + it[3].toLong()
                    } in minimumSeconds..maximumSeconds
            }
        }
        checks++
        yes(
            visibleTexts().none {
                it.startsWith("State:") ||
                    it.startsWith("Worked ms:") ||
                    it.startsWith("Started at ms:")
            },
            "TIMER_HIDES_RAW_CLOCK_FIELDS",
        )
        enter("Tätigkeit · optional bis zum Abschluss", "[TEST] QA native Timer")
        click("Textbausteine ▾")
        click("Rasen gemäht")
        reveal("Tätigkeit · optional bis zum Abschluss")
        yes(
            field("Tätigkeit · optional bis zum Abschluss")?.text?.toString() ==
                "[TEST] QA native Timer\nRasen gemäht",
            "TIMER_SNIPPET_RETAINS_OWN_TEXT",
        )
        click("Pause")
        reveal("Arbeit pausiert")
        recreate()
        reveal("Arbeit pausiert")
        yes(read("get_active_work_session").getString("id") == id, "RECREATION_PRESERVES_TIMER_ID")
        click("Fortsetzen")
        reveal("Arbeit läuft")
        // A positive rounded duration is required by the real work writer.
        waitUntil("TIMER_HAS_POSITIVE_SAVEABLE_DURATION", 30000) {
            read("get_active_work_session").optDouble("hours") > 0.0
        }
        captureSyntheticScreen("timer-actions")
        click("Zeit stoppen")
        shown("Arbeit abschließen")
        yes(
            read("get_active_work_session").getString("state") == "STOPPED_PENDING_REVIEW",
            "UI_STOPS_WITHOUT_FINALIZING",
        )
        yes(
            PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() ==
                before,
            "STOP_DOES_NOT_CREATE_WORKLOG",
        )
        val stoppedMs = read("get_active_work_session").getLong("worked_ms")
        primaryPosition("Arbeit speichern", "Später abschließen")
        click(UiTags.area("today"))
        shown("Arbeit noch abschließen")
        shown("Zeit gestoppt · Abschluss offen")
        primaryPosition("Abschluss prüfen", "Arbeit nachtragen")
        yes(
            read("get_active_work_session").getLong("worked_ms") == stoppedMs,
            "STOPPED_TIME_DOES_NOT_GROW",
        )
        yes(
            PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() ==
                before,
            "LEAVING_COMPLETION_DOES_NOT_SAVE",
        )
        click("Abschluss prüfen")
        shown("Arbeit abschließen")
        enter(UiTags.actionField("activity"), "[TEST] QA native Timer\nRasen gemäht", exact = true)
        awaitTimerCompletionReady(id, "[TEST] QA native Timer\nRasen gemäht")
        captureSyntheticScreen("timer-completion")
        click(UiTags.PRIMARY)
        waitUntil("TIMER_EXPLICIT_SAVE_CREATES_ONE_WORKLOG") {
            PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() ==
                before + 1
        }
        val completed = store.workSessions().current(id)
        yes(
            completed.getString("state") == "FINALIZED",
            "TIMER_EXPLICIT_SAVE_FINALIZES_EXACT_SESSION",
        )
        val saved =
            read(
                "get_work_record",
                JSONObject().put("work_record_id", completed.getString("finalized_worklog_id")),
            )
        yes(
            saved.optString("activity") == "[TEST] QA native Timer\nRasen gemäht" &&
                saved.optString("customer_id") == session.getString("customer_id"),
            "TIMER_SAVED_TEXT_AND_CUSTOMER",
        )
        // Today no longer lists saved work (design); the saved record is read back below.
        click(UiTags.area("today"))
        shown("Arbeit starten")
        val summary = read("get_work_record_summary", todayArgs)
        yes(
            summary.optInt("work_records") == todayBefore.optInt("work_records") + 1 &&
                kotlin.math.abs(
                    summary.optDouble("hours") -
                        todayBefore.optDouble("hours") -
                        saved.optDouble("hours")
                ) <= 0.01,
            "TODAY_SUMMARY_INCLUDES_EXACT_CONFIRMED_WORK",
        )

        openCustomerList()
        enter("Suchen", fixturePrefix + "000")
        click(fixturePrefix + "000")
        click("Arbeit starten")
        waitUntil("SECOND_DIRECT_TIMER_STARTED") {
            read("get_active_work_session").optString("state") == "RUNNING"
        }
        val discardedId = read("get_active_work_session").getString("id")
        click("Zeit stoppen")
        shown("Arbeit abschließen")
        enter("Tätigkeit *", "[TEST] QA verworfener Abschluss")
        awaitTimerCompletionReady(discardedId, "[TEST] QA verworfener Abschluss")
        click("Weitere Angaben (optional)")
        reveal("Entwurf verwerfen")
        val controller = NativeAppController(requireNotNull(activity) as MainActivity, store)
        val completionKey = "action:complete_work_session:$discardedId"
        runBlocking { controller.flushDrafts() }
        val draft = requireNotNull(runBlocking { controller.draft(completionKey) })
        val frozenCompletion =
            JSONObject(draft.toString())
                .put(
                    "original_args",
                    JSONObject()
                        .put("request_id", "qa_native_frozen_completion")
                        .put("session_id", discardedId)
                        .put("activity", "[TEST] QA verworfener Abschluss"),
                )
        controller.saveDraft(completionKey, frozenCompletion)
        runBlocking { controller.flushDrafts() }
        click("Entwurf verwerfen")
        shown("Abschluss zuerst prüfen")
        yes(
            read("get_active_work_session").optString("state") == "STOPPED_PENDING_REVIEW" &&
                PixelMcpWriteTools.hash(
                    requireNotNull(runBlocking { controller.draft(completionKey) })
                ) == PixelMcpWriteTools.hash(frozenCompletion),
            "PENDING_COMPLETION_CANNOT_BE_DISCARDED",
        )
        yes(
            PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() ==
                before + 1,
            "PENDING_DISCARD_GUARD_CREATES_NO_WORKLOG",
        )
        click("Zurück", scrollTo = false)
        // This request was deliberately never sent; restore the synthetic pre-guard fixture.
        controller.saveDraft(completionKey, draft)
        runBlocking { controller.flushDrafts() }
        discardWork()
        waitUntil("TIMER_DISCARDED") {
            read("get_active_work_session").optString("state") == "IDLE"
        }
        yes(
            store.workSessions().current(discardedId).getString("state") == "CANCELLED",
            "UI_DISCARD_PERSISTS_EXACT_SESSION",
        )
        yes(
            runBlocking { controller.draft(completionKey) } == null,
            "TIMER_DISCARD_REMOVES_OWN_UNFROZEN_COMPLETION",
        )
        yes(
            PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() ==
                before + 1,
            "DISCARD_PRESERVES_CONFIRMED_WORK",
        )
        click("Letzte verworfene Sitzung zurückholen")
        waitUntil("TIMER_RESTORED") {
            read("get_active_work_session").optString("id") == discardedId &&
                read("get_active_work_session").optString("state") == "PAUSED"
        }
        reveal("Arbeit pausiert")
        primaryPosition("Fortsetzen", "Zeit stoppen")
        click("Zeit stoppen")
        shown("Arbeit abschließen")
        click("Weitere Angaben (optional)")
        discardWork()
        waitUntil("RESTORED_TIMER_DISCARDED") {
            read("get_active_work_session").optString("state") == "IDLE"
        }
    }

    private fun awaitTimerCompletionReady(sessionId: String, expectedActivity: String) {
        val key =
            "native_form_draft:" +
                PixelMcpWriteTools.hash("action:complete_work_session:$sessionId")
        var observed = JSONObject()
        var readySince = 0L
        try {
            waitUntil("TIMER_COMPLETION_HAS_EXACT_DURABLE_INPUTS_AND_ENABLED_SAVE") {
                val draft = runCatching { JSONObject(store.ownerValue(key, "")) }.getOrNull()
                val values = draft?.optJSONObject("values")
                val enabled = runCatching { actionEnabled(UiTags.PRIMARY) }.getOrDefault(false)
                observed =
                    JSONObject()
                        .put("draft", draft ?: JSONObject.NULL)
                        .put("save_enabled", enabled)
                        .put("expected_session_id", sessionId)
                // Opening a retained timer draft adds request_id to form memory;
                // enabled submit proves required fields, while the unchanged
                // retained draft legitimately contains only context and activity.
                val ready =
                    values != null &&
                        values.optString("activity") == expectedActivity &&
                        values.optString("session_id") == sessionId &&
                        enabled
                val now = SystemClock.uptimeMillis()
                if (!ready) readySince = 0L else if (readySince == 0L) readySince = now
                // enter(..., exact = true) checked the visible field before settling.
                // The exact durable input and enabled submit must survive a timer tick;
                // scrolling the field out of view does not make the draft unsaveable.
                ready && now - readySince >= 1100
            }
            checks++
        } catch (failure: Throwable) {
            // The emulator fixture is already verified synthetic. Preserve exact
            // inputs to distinguish lost form metadata from an automation race.
            test.sendStatus(
                0,
                Bundle().apply { putString("timer_completion_form", observed.toString()) },
            )
            throw failure
        }
    }

    /** Today shows open hours, not saved work rows; an external edit updates them in place. */
    private fun todayOlderWorkJourney() {
        val day = LocalDate.now(ZoneId.of("Europe/Vienna")).toString()
        val customer = customers(fixturePrefix + "000").single().getString("id")
        repeat(4) { index ->
            store.saveEditorRecord(
                "worklog",
                JSONObject()
                    .put("customer_id", customer)
                    .put("work_date", day)
                    .put("activity", "[TEST] QA Heute Gesamtstand $index")
                    .put("hours", 1)
                    .put("test_record", true)
                    .put("_request_id", "qa_native_today_total_$index"),
            )
        }
        store.notifyCommittedChange()
        val data = store.nativeData()
        val args = JSONObject().put("date_from", day).put("date_to", day)
        val before = read("get_work_record_summary", args)
        // Expected text from the stored open hours, never from the screen itself.
        fun openHoursText(): String {
            val groups =
                read("get_work_record_summary", JSONObject().put("group_by_billing_customer", true))
                    .optJSONArray("billing_groups") ?: JSONArray()
            val minutes =
                kotlin.math
                    .round(
                        (0 until groups.length()).sumOf {
                            groups.getJSONObject(it).optDouble("hours")
                        } * 60
                    )
                    .toLong()
            return "${minutes / 60} Std. ${minutes % 60} Min. bei " +
                (if (groups.length() == 1) "1 Empfänger" else "${groups.length()} Empfängern") +
                " · jetzt abrechnen"
        }
        click(UiTags.area("today"))
        reveal(openHoursText())
        val fourth =
            runBlocking { data.list("worklogs", dateFrom = day, dateTo = day, limit = 4) }
                .rows
                .let { rows ->
                    yes(rows.size == 4, "TODAY_TOTAL_FIXTURE_HAS_FOUR_ROWS")
                    rows[3]
                }
        repeat(3) { edit ->
            val envelope =
                tools.call("get_work_record", JSONObject().put("work_record_id", fourth.id))
            yes(!envelope.has("ok") || envelope.getBoolean("ok"), "TODAY_FOURTH_WORK_CAS_BASE")
            val record = envelope.optJSONObject("data") ?: envelope
            read(
                "update_work_record",
                JSONObject()
                    .put("request_id", "qa_native_today_older_hours_$edit")
                    .put("work_record_id", fourth.id)
                    .put("expected_record_hash", envelope.getString("record_hash"))
                    .put(
                        "updates",
                        JSONObject()
                            .put("hours", record.getDouble("hours") + 0.5)
                            .put("start_time", "")
                            .put("end_time", "")
                            .put("break_minutes", 0),
                    ),
            )
            yes(
                read("get_work_record", JSONObject().put("work_record_id", fourth.id)).let {
                    (it.optJSONObject("data") ?: it).getDouble("hours")
                } == record.getDouble("hours") + 0.5,
                "TODAY_EXTERNAL_EDIT_STORED_$edit",
            )
            // No navigation or refresh between the external writer and the visible assertion.
            reveal(openHoursText())
        }
        yes(
            read("get_work_record_summary", args).getInt("work_records") ==
                before.getInt("work_records"),
            "TODAY_OLDER_EDIT_DOES_NOT_ADD_WORK",
        )
    }

    private fun seedNotification(): String {
        val zone = ZoneId.of("Europe/Vienna")
        val start = LocalDate.now(zone).plusDays(1).atTime(10, 0).atZone(zone).toInstant()
        val result =
            read(
                "create_appointment",
                JSONObject()
                    .put("request_id", "qa_native_notification_001")
                    .put("customer_id", customers(fixturePrefix + "000").single().getString("id"))
                    .put("title", "[TEST] QA Native Notification")
                    .put("starts_at", start.toString())
                    .put("ends_at", start.plusSeconds(3600).toString())
                    .put("timezone", zone.id)
                    .put("weather_profile", "indoor")
                    .put("status", "tentative"),
            )
        val id = result.getString("appointment_id")
        return id
    }

    private fun notificationJourney() {
        val id = seedNotification()
        val original =
            PixelMcpWriteTools.hash(
                read("get_appointment", JSONObject().put("appointment_id", id))
                    .getJSONObject("appointment")
            )
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
            requireNotNull(activity)
                .startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra("open_planning", true)
                        .putExtra("planning_appointment_id", id)
                        .putExtra("planning_action", "alternatives")
                )
        }
        shown("Vorgeschlagene Zeitfenster")
        click("Schließen")
        yes(
            PixelMcpWriteTools.hash(
                read("get_appointment", JSONObject().put("appointment_id", id))
                    .getJSONObject("appointment")
            ) == original,
            "OPENING_ALTERNATIVES_DOES_NOT_CHANGE_APPOINTMENT",
        )
    }

    private fun openWorkCustomerPicker(): String {
        scrollTop()
        var caption: String? = null
        waitUntil("WORK_CUSTOMER_PICKER_READY") {
            caption =
                nodes()
                    .asSequence()
                    .filter { it.isVisibleToUser && clickable(it) != null }
                    .flatMap { texts(it).asSequence() }
                    .firstOrNull { it.startsWith("Kunde: ") }
            caption != null
        }
        return requireNotNull(caption).also { click(it) }
    }

    private fun actionBounds(label: String): Rect {
        val target =
            nodes()
                .asSequence()
                .filter { it.isVisibleToUser && matches(it, label) }
                .mapNotNull(::clickable)
                .firstOrNull()
                ?: throw IllegalArgumentException("QA_NATIVE_COMPOSE_NOT_ACTIONABLE_$label")
        return Rect().also { target.getBoundsInScreen(it) }
    }

    private fun logVisibleActions(stage: String) {
        // Only the previously verified disposable synthetic fixture is allowed here.
        nodes()
            .filter { it.isVisibleToUser && texts(it).isNotEmpty() }
            .forEach { item ->
                val bounds = Rect()
                item.getBoundsInScreen(bounds)
                android.util.Log.i(
                    "NativeComposeQA",
                    "$stage ${texts(item)} $bounds id=${item.viewIdResourceName} editable=${item.isEditable} enabled=${item.isEnabled} clickable=${clickable(item) != null} actions=${item.actionList.map { it.id }}",
                )
            }
    }

    private fun captureSyntheticScreen(name: String, requireKeyboard: Boolean = false) {
        if (requireKeyboard) yes(keyboardVisible(), "SCREENSHOT_IME_BEFORE_$name")
        val bitmap =
            requireNotNull(automation.takeScreenshot()) {
                "Synthetic emulator screenshot unavailable"
            }
        try {
            File(requireNotNull(context.getExternalFilesDir(null)), "qa-native-$name.png")
                .outputStream()
                .use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        if (requireKeyboard) yes(keyboardVisible(), "SCREENSHOT_IME_AFTER_$name")
    }

    private fun seedWork(activityText: String): JSONObject {
        val id =
            store.saveEditorRecord(
                "worklog",
                JSONObject()
                    .put("_request_id", "qa_seed_work")
                    .put("customer_id", customers(fixturePrefix + "000").single().getString("id"))
                    .put("activity", activityText)
                    .put("work_date", LocalDate.now(ZoneId.of("Europe/Vienna")).toString())
                    .put("hours", 1),
            )
        return read("get_work_record", JSONObject().put("work_record_id", id))
    }

    private fun seedEquipment() {
        read(
            "save_equipment",
            JSONObject()
                .put("request_id", "qa_seed_equipment")
                .put("name", "[TEST] QA Zubehör Säge")
                .put(
                    "accessories",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("id", "qa_seed_chain")
                                .put("type", "chain")
                                .put("name", "QA Kette")
                        ),
                ),
        )
    }

    private fun seedSearchCatalog() {
        val source = JSONObject(store.getState().getJSONObject("master").toString())
        val catalog = source.optJSONArray("service_catalog") ?: JSONArray()
        catalog.put(
            JSONObject()
                .put("id", "qa_ui_service")
                .put("title", "QA Leistungsbaustein")
                .put("description", "Synthetischer Tätigkeitstext")
                .put("category", "QA")
                .put("unit", "h")
                .put("default_price", "7.50")
                .put("default_quantity", "1")
                .put("price_confirmed", true)
                .put("active", true)
        )
        source.put("service_catalog", catalog)
        store.putOwnerValue("snapshot", source.toString())
        store.notifyCommittedChange()
    }

    private fun seedCustomerContext() {
        val controller = NativeAppController(requireNotNull(activity) as MainActivity, store)
        val fixture = JSONObject(store.getState().getJSONObject("master").toString())
        val offers = fixture.optJSONArray("offers") ?: JSONArray()
        val source = File(context.cacheDir, "qa-context-photo.png")
        val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff346a45.toInt())
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val identity = BackupDeviceGuard(context).current()
        for (index in 0..1) {
            val name = fixturePrefix + String.format(Locale.ROOT, "%03d", index)
            val id = customers(name).single().getString("id")
            val property =
                read(
                    "create_property",
                    JSONObject()
                        .put("request_id", "qa_ui_context_property_$index")
                        .put("customer_id", id)
                        .put("name", "QA Kontextobjekt $index")
                        .put("address", "QA Gartenweg $index, Testort"),
                )
            read(
                "create_inquiry",
                JSONObject()
                    .put("request_id", "qa_ui_context_inquiry_$index")
                    .put("customer_id", id)
                    .put("property_id", property.getString("property_id"))
                    .put("title", "QA Kontextanfrage $index"),
            )
            read(
                "plan_visit",
                JSONObject()
                    .put("request_id", "qa_ui_context_visit_$index")
                    .put("property_id", property.getString("property_id"))
                    .put("title", "QA Kontexteinsatz $index")
                    .put("date", LocalDate.now(ZoneId.of("Europe/Vienna")).toString())
                    .put("planned_minutes", 60),
            )
            val orders = fixture.optJSONArray("orders") ?: JSONArray()
            orders.put(
                JSONObject()
                    .put("id", "qa_context_order_$index")
                    .put("customer_id", id)
                    .put("title", "QA Kontextauftrag $index")
                    .put("status", "Geplant")
                    .put("test_record", true)
            )
            fixture.put("orders", orders)
            val workRows = fixture.optJSONArray("worklogs") ?: JSONArray()
            workRows.put(
                JSONObject()
                    .put("id", "qa_context_billing_$index")
                    .put("customer_id", id)
                    .put(
                        "billing_customer_id",
                        customers(fixturePrefix + "000").single().getString("id"),
                    )
                    .put("activity", "QA Kontextabrechnung $index")
                    .put("hours", 1)
                    .put("billed", 0)
                    .put("work_date", LocalDate.now(ZoneId.of("Europe/Vienna")).toString())
                    .put("test_record", true)
            )
            fixture.put("worklogs", workRows)
            offers.put(
                JSONObject()
                    .put("id", "qa_context_offer_$index")
                    .put("customer_id", id)
                    .put("title", "QA Kontextangebot $index")
                    .put("status", "draft")
                    .put("test_record", true)
            )
            runBlocking {
                controller.importPhoto(
                    Uri.fromFile(source),
                    JSONObject()
                        .put("_photo_request_id", "qa_context_photo_$index")
                        .put("customer_id", id)
                        .put("role", "documentation")
                        .put("caption", "QA Kontextfoto $index")
                        .put(
                            "_capture_context",
                            JSONObject()
                                .put("formatVersion", 1)
                                .put("installation_id", identity.getString("installationId"))
                                .put("restore_epoch", identity.getString("restoreEpoch")),
                        ),
                )
            }
        }
        fixture.put("offers", offers)
        store.putOwnerValue("snapshot", fixture.toString())
        store.notifyCommittedChange()
        source.delete()
    }

    private fun customerContextJourney() {
        val navigation = NativeAppController(requireNotNull(activity) as MainActivity, store)
        for (area in listOf("today", "worklogs", "customers", "office")) {
            navigation.openSection(area)
            val recreated = NativeAppController(requireNotNull(activity) as MainActivity, store)
            recreated.restoreRoute(navigation.savedNavigation())
            recreated.back()
            yes(
                recreated.route.section == "today" &&
                    recreated.route.area == "today" &&
                    !recreated.hasParentScreen(),
                "MAIN_ROOT_BACK_TO_TODAY_$area",
            )
            navigation.back()
        }
        navigation.openSection("customers")
        navigation.navigate("customers", "qa-back-customer")
        navigation.openSection("office")
        navigation.back()
        navigation.openSection("customers")
        yes(navigation.route.id == "qa-back-customer", "ROOT_BACK_PRESERVES_OTHER_AREA_DETAIL")
        navigation.back()
        yes(
            navigation.route.section == "customers" && navigation.route.id.isEmpty(),
            "DETAIL_BACK_RETURNS_TO_OWN_LIST",
        )
        navigation.back()
        yes(
            navigation.route.section == "today" && !navigation.hasParentScreen(),
            "LIST_BACK_RETURNS_TO_TODAY_WITHOUT_TAB_HISTORY",
        )
        val controller = NativeAppController(requireNotNull(activity) as MainActivity, store)
        controller.openSection("worklogs")
        controller.navigate("worklogs", "qa-route", "edit")
        yes(controller.route.area == "worklogs", "WORK_EDITOR_AREA")
        controller.action("Prüfen", "get_work_record")
        yes(controller.route.area == "worklogs", "ACTION_RETAINS_AREA")
        val restored = NativeAppController(requireNotNull(activity) as MainActivity, store)
        restored.restoreRoute(controller.savedNavigation())
        yes(restored.route.area == "worklogs", "RECREATED_ACTION_AREA")
        restored.back()
        yes(
            restored.route.section == "worklogs" && restored.route.id == "qa-route",
            "RECREATED_BACK_STACK",
        )
        controller.message("QA previous save")
        controller.navigate("photos", args = JSONObject().put("customer_id", "qa-customer"))
        yes(
            controller.messages.value.isEmpty() && controller.route.area == "worklogs",
            "NAVIGATION_CLEARS_OLD_MESSAGE",
        )
        controller.message("QA previous photo")
        controller.back()
        yes(controller.messages.value.isEmpty(), "BACK_CLEARS_OLD_MESSAGE")

        seedCustomerContext()
        for (index in 0..1) {
            val name = fixturePrefix + String.format(Locale.ROOT, "%03d", index)
            openCustomerList()
            enter("Suchen", name)
            click(name)
            click("Fotos")
            shown(name)
            shown("QA Kontextfoto $index")
            yes(node("QA Kontextfoto ${1-index}") == null, "PHOTOS_KEEP_CUSTOMER_$index")
            if (index == 0) captureSyntheticScreen("customer-photos")
            click(UiTags.BACK)
            click("Angebote")
            shown("QA Kontextangebot $index")
            yes(node("QA Kontextangebot ${1-index}") == null, "OFFERS_KEEP_CUSTOMER_$index")
            if (index == 0) captureSyntheticScreen("customer-offers")
            yes(
                node(UiTags.area("customers"))?.let(::clickable)?.stateDescription?.toString() ==
                    "Ausgewählter Bereich",
                "OFFERS_RETAINS_CUSTOMER_AREA_$index",
            )
            click("QA Kontextangebot $index")
            click("Zur Liste")
            shown("QA Kontextangebot $index")
            yes(node("QA Kontextangebot ${1-index}") == null, "OFFER_DETAIL_RETURN_FILTER_$index")
            openCustomerList()
            enter("Suchen", name)
            click(name)
            click("Aufträge")
            shown("QA Kontextauftrag $index")
            yes(node("QA Kontextauftrag ${1-index}") == null, "ORDERS_KEEP_CUSTOMER_$index")
            click(UiTags.BACK)
            click("Alle Objekte")
            shown("QA Kontextobjekt $index")
            yes(node("QA Kontextobjekt ${1-index}") == null, "PROPERTIES_KEEP_CUSTOMER_$index")
            click("Objekt anlegen")
            shown("Kunde: $name")
            click("Zurück")
            shown("Objekt anlegen")
            click(UiTags.BACK)
            click("Alle Einsätze")
            shown("QA Kontexteinsatz $index")
            yes(node("QA Kontexteinsatz ${1-index}") == null, "VISITS_KEEP_CUSTOMER_$index")
            click(UiTags.BACK)
            scrollTop()
            if (index == 0) {
                captureSyntheticScreen("customer-overview")
                for (fromInquiry in listOf(false, true)) {
                    if (fromInquiry) {
                        openOfficeEntry("Anfragen")
                        enter("Suchen", "QA Kontextanfrage 0")
                        click("QA Kontextanfrage 0")
                        click("Angebot aus dieser Anfrage")
                    } else {
                        click("Alle Objekte")
                        click("QA Kontextobjekt 0")
                        click(UiTags.DETAIL_MENU)
                        click(UiTags.detailAction("property-offer"))
                    }
                    val title = if (fromInquiry) "QA Kontextanfrage 0" else "QA Kontextobjekt 0"
                    shown("Kunde: $name")
                    reveal("Titel *")
                    waitUntil("OFFER_SOURCE_TITLE_$fromInquiry") {
                        field("Titel *")?.text?.toString() == title
                    }
                    enter("Titel *", "$title geprüft")
                    val draftKey =
                        "native_form_draft:" + PixelMcpWriteTools.hash("finance:offers:new")
                    waitUntil("OFFER_SOURCE_DRAFT_$fromInquiry") {
                        store.ownerValue(draftKey, "").contains("_source_context")
                    }
                    recreate()
                    revealContaining("Objekt: QA Kontextobjekt 0")
                    if (fromInquiry) revealContaining("Anfrage: QA Kontextanfrage 0")
                    reveal("Titel *")
                    yes(
                        field("Titel *")?.text?.toString()?.contains("$title geprüft") == true,
                        "OFFER_SOURCE_TITLE_RECREATED_$fromInquiry",
                    )
                    click("Zeitraum und Bedingungen")
                    reveal("Einsatzort")
                    yes(
                        field("Einsatzort")
                            ?.text
                            ?.toString()
                            ?.contains("QA Gartenweg 0, Testort") == true,
                        "OFFER_SOURCE_LOCATION_$fromInquiry",
                    )
                    discardWork()
                    waitUntil("OFFER_SOURCE_DRAFT_REMOVED_$fromInquiry") {
                        store.ownerValue(draftKey, "").isEmpty()
                    }
                    openCustomerList()
                    enter("Suchen", name)
                    click(name)
                }
                val invoiceCount =
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("invoices").length()
                click("Offene Stunden")
                shown("Offene Stunden")
                click("Arbeiten prüfen")
                enter("Suchen", "QA Kontextabrechnung")
                val sourceLabel = "QA Kontextabrechnung 0"
                reveal(sourceLabel)
                yes(
                    visibleTexts().none { it.contains("QA Kontextabrechnung 1") },
                    "BILLING_SOURCE_KEEPS_WORK_CUSTOMER_WITH_SHARED_RECIPIENT",
                )
                captureSyntheticScreen("customer-billing")
                yes(
                    PixelMcpReadTools.effective(store.getState())
                        .getJSONArray("invoices")
                        .length() == invoiceCount,
                    "OVERVIEW_DOES_NOT_CREATE_INVOICE",
                )
            }
        }
        click(UiTags.area("today"))
    }

    private fun officeOpeningMeasurements() {
        click(UiTags.area("today"))
        officeMeasurements =
            JSONObject()
                .put(
                    "scope",
                    "EMULATOR_UI_ACTION_TO_VISIBLE_READY_CONTENT;WARM_PROCESS;FROM_OFFICE",
                )
                .put("samples_per_path", 40)
                .put("warmups_per_path", 2)
                .put("performance_claim", false)
                .put("display", shell("wm size") + ";" + shell("wm density"))
                .put("font_scale", shell("settings get system font_scale"))
                .put("api", Build.VERSION.SDK_INT)
                .put("host_load", "NOT_CONTROLLED")
        for ((name, entry, ready) in
            listOf(
                Triple("billing", "Offene Stunden", "Arbeiten prüfen"),
                Triple("offers", "Angebote", "Angebot anlegen"),
            )) {
            val values = ArrayList<Double>()
            repeat(42) { sample ->
                openOffice()
                var start = 0L
                click(entry, onAction = { start = it })
                waitUntil("OFFICE_READY_$name") { node(ready) != null }
                val elapsed = (System.nanoTime() - start) / 1_000_000.0
                if (sample >= 2) values.add(elapsed)
            }
            val sorted = values.sorted()
            officeMeasurements.put(
                name,
                JSONObject()
                    .put("mean_ms", values.average())
                    .put("p95_ms", sorted[37])
                    .put("slowest_5_percent_mean_ms", sorted.takeLast(2).average())
                    .put("samples_ms", JSONArray(values)),
            )
        }
        test.sendStatus(
            0,
            Bundle().apply { putString("office_opening", officeMeasurements.toString()) },
        )
        click(UiTags.area("today"))
    }

    private fun keyboardVisible(): Boolean =
        automation.windows.any {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD && it.root?.isVisibleToUser == true
        }

    private fun showKeyboard(label: String) {
        if (field(label) == null) scrollTop()
        var lastScroll = 0L
        waitUntil("SEARCH_FIELD_READY_$label") {
            val selected = field(label)
            if (
                selected != null &&
                    selected.refresh() &&
                    selected.isVisibleToUser &&
                    selected.isEnabled &&
                    selected.isEditable
            )
                return@waitUntil true
            if (SystemClock.uptimeMillis() - lastScroll > 250) {
                scroll()
                lastScroll = SystemClock.uptimeMillis()
            }
            false
        }
        yes(
            requireNotNull(field(label)).performAction(AccessibilityNodeInfo.ACTION_CLICK),
            "SEARCH_FOCUS_$label",
        )
        waitUntil("SEARCH_IME_VISIBLE") { keyboardVisible() }
        settleUi()
    }

    /** Validate real screen/IME geometry, then inject a touch, not a semantics click. */
    private fun visibleResult(
        label: String,
        tap: Boolean = false,
        requireKeyboard: Boolean = false,
        scrollIntoView: Boolean = false,
        allowClippedCard: Boolean = false,
    ): Rect {
        var result: Rect? = null
        var geometry = "No candidate"
        var stableGeometry = ""
        var stableSince = 0L
        var lastScroll = 0L
        try {
            waitUntil("SEARCH_RESULT_BOUNDS_$label") {
                val textNode = nodes().firstOrNull { matches(it, label) && clickable(it) != null }
                if (textNode == null) {
                    if (scrollIntoView && SystemClock.uptimeMillis() - lastScroll > 300) {
                        if (!scroll()) scrollTop()
                        lastScroll = SystemClock.uptimeMillis()
                    }
                    return@waitUntil false
                }
                val candidate = clickable(textNode) ?: return@waitUntil false
                val bounds = Rect().also { candidate.getBoundsInScreen(it) }
                val screen = Rect()
                automation.rootInActiveWindow?.getBoundsInScreen(screen)
                val ime =
                    automation.windows.firstOrNull {
                        it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                            it.root?.isVisibleToUser == true
                    }
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
                val titleVisible =
                    title.top >= screen.top &&
                        title.top + minOf(title.height(), minimum) <= screen.bottom
                val within =
                    if (allowClippedCard) hit.intersect(screen) && titleVisible
                    else screen.contains(bounds)
                val valid =
                    !hit.isEmpty && within && hit.width() >= minimum && hit.height() >= minimum
                geometry =
                    "candidate=$bounds touch=$hit title=$title screen=$screen keyboard=${ime != null} density=${context.resources.displayMetrics.density}"
                if (valid) {
                    // IME and status-bar animations can still move a valid 48dp row.
                    // Only inject a real touch after its complete screen geometry settles.
                    if (geometry != stableGeometry) {
                        stableGeometry = geometry
                        stableSince = SystemClock.uptimeMillis()
                    }
                    if (!tap || SystemClock.uptimeMillis() - stableSince >= 500) result = hit
                } else if (scrollIntoView && SystemClock.uptimeMillis() - lastScroll > 300) {
                    scroll()
                    lastScroll = SystemClock.uptimeMillis()
                }
                if (!valid) {
                    stableGeometry = ""
                    stableSince = 0L
                }
                result != null
            }
        } catch (failure: Throwable) {
            throw AssertionError("SEARCH_GEOMETRY_$label: $geometry", failure)
        }
        val bounds = requireNotNull(result)
        checks++
        if (tap) {
            test.sendStatus(0, Bundle().apply { putString("search_touch", "$label: $geometry") })
            val now = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event =
                    MotionEvent.obtain(
                        now,
                        SystemClock.uptimeMillis(),
                        action,
                        bounds.exactCenterX(),
                        bounds.exactCenterY(),
                        0,
                    )
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try {
                    yes(automation.injectInputEvent(event, true), "SEARCH_REAL_TOUCH_$label")
                    if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(75)
                } finally {
                    event.recycle()
                }
            }
            settleUi()
        }
        return bounds
    }

    private fun searchKeyboardJourney() {
        val customerId = customers(fixturePrefix + "000").single().getString("id")
        val choiceTag = UiTags.recordChoice("customers", customerId)
        val oldFont = shell("settings get system font_scale")
        val sizeText = shell("wm size")
        val override = Regex("Override size: (\\d+x\\d+)").find(sizeText)?.groupValues?.get(1)
        val physical =
            Regex("(?:Override|Physical) size: (\\d+)x(\\d+)").findAll(sizeText).last().groupValues
        val width = minOf(physical[1].toInt(), physical[2].toInt())
        val height = maxOf(physical[1].toInt(), physical[2].toInt())
        val failures = ArrayList<Throwable>()
        try {
            for ((font, compact) in
                listOf(1.0 to false, 1.3 to false, 2.0 to true)) for (landscape in
                listOf(false, true)) {
                val key =
                    "search-${(font*100).toInt()}-${if (compact) "compact" else "normal"}-${if (landscape) "landscape" else "portrait"}"
                try {
                    activity?.let { current -> test.runOnMainSync { current.finish() } }
                    shell("wm size ${width}x${if (compact) (height*0.82).toInt() else height}")
                    shell("settings put system font_scale $font")
                    automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
                    launch()
                    test.runOnMainSync {
                        requireNotNull(activity).requestedOrientation =
                            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    newWork()
                    openWorkCustomerPicker()
                    test.runOnMainSync {
                        requireNotNull(activity).requestedOrientation =
                            if (landscape)
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    waitUntil("SEARCH_CONFIGURATION_$key") {
                        var correct = false
                        test.runOnMainSync {
                            val config = requireNotNull(activity).resources.configuration
                            correct =
                                kotlin.math.abs(config.fontScale - font.toFloat()) < 0.02f &&
                                    (config.orientation ==
                                        android.content.res.Configuration.ORIENTATION_LANDSCAPE) ==
                                        landscape
                        }
                        correct
                    }
                    enter("Suchen", fixturePrefix + "000")
                    shown("1 Treffer")
                    visibleResult(fixturePrefix + "000")
                    showKeyboard("Suchen")
                    visibleResult(fixturePrefix + "000", requireKeyboard = true)
                    waitUntil("SEARCH_FIELD_AND_COUNT_BOUNDS_$key") {
                        val screen =
                            Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                        val ime =
                            automation.windows.firstOrNull {
                                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                                    it.root?.isVisibleToUser == true
                            } ?: return@waitUntil false
                        screen.bottom =
                            minOf(screen.bottom, Rect().also { ime.getBoundsInScreen(it) }.top)
                        listOf(field("Suchen"), node("1 Treffer")).all { candidate ->
                            candidate != null &&
                                Rect()
                                    .also { candidate.getBoundsInScreen(it) }
                                    .let { !it.isEmpty && screen.contains(it) }
                        }
                    }
                    captureSyntheticScreen(key, requireKeyboard = true)
                    yes(
                        requireNotNull(field("Suchen"))
                            .performAction(
                                AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
                            ),
                        "SEARCH_IME_ACTION_$key",
                    )
                    waitUntil("SEARCH_IME_HIDDEN_$key") { !keyboardVisible() }
                    waitUntil("SEARCH_TEXT_AFTER_IME_$key") {
                        field("Suchen")?.text?.toString()?.contains(fixturePrefix + "000") == true
                    }
                    shown("1 Treffer")
                    showKeyboard("Suchen")
                    visibleResult(choiceTag, tap = true, requireKeyboard = true)
                    waitUntil("SEARCH_PICKER_CLOSED_$key") {
                        node("Abbrechen") == null && !keyboardVisible()
                    }
                    reveal("Kunde: " + fixturePrefix + "000")
                    waitUntil("SEARCH_SELECTED_CUSTOMER_$key") {
                        workDraft().optJSONObject("values")?.optString("customer_id") == customerId
                    }
                    discardWork()
                    searchMatrix.put(
                        JSONObject()
                            .put("font", font)
                            .put("compact", compact)
                            .put("landscape", landscape)
                            .put("keyboard_open_and_closed", true)
                            .put("ime_action_preserves_query", true)
                            .put("real_touch_selection", true)
                            .put("screenshot", "qa-native-$key.png")
                    )
                } catch (failure: Throwable) {
                    test.sendStatus(
                        0,
                        Bundle().apply {
                            putString(
                                "search_display_failure",
                                "$key: " + failure.stackTraceToString(),
                            )
                        },
                    )
                    runCatching { captureSyntheticScreen("failure-$key") }
                    failures.add(AssertionError(key, failure))
                    // Own synthetic form only; keep later independent display cases executable.
                    runCatching {
                            runBlocking {
                                NativeAppController(requireNotNull(activity) as MainActivity, store)
                                    .removeDraft("editor:worklog:new")
                            }
                        }
                        .onFailure { cleanup ->
                            test.sendStatus(
                                0,
                                Bundle().apply {
                                    putString(
                                        "search_cleanup_failure",
                                        cleanup.stackTraceToString(),
                                    )
                                },
                            )
                        }
                }
            }
        } finally {
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            shell("wm size " + (override ?: "reset"))
            shell(
                if (oldFont == "null") "settings delete system font_scale"
                else "settings put system font_scale $oldFont"
            )
            launch()
        }
        if (failures.isNotEmpty()) {
            val failure = AssertionError("SEARCH_MATRIX_${failures.size}_FAILED")
            failures.forEach(failure::addSuppressed)
            throw failure
        }
        yes(searchMatrix.length() == 6, "ALL_SEARCH_DISPLAY_COMBINATIONS")
        newWork()
        val priorCustomer = openWorkCustomerPicker()
        enter("Suchen", "NO_SYNTHETIC_MATCH_48273")
        shown("0 Treffer")
        shown("Keine Treffer. Suchtext oder Filter ändern.")
        shown("Keine Zuordnung")
        showKeyboard("Suchen")
        captureSyntheticScreen("search-no-results", requireKeyboard = true)
        enter("Suchen", fixturePrefix)
        shown("55 Treffer")
        back()
        waitUntil("SEARCH_PAGING_IME_HIDDEN") { !keyboardVisible() }
        click("Weitere")
        reveal(fixturePrefix + "030")
        click("Vorherige")
        enter("Suchen", "")
        yes(field("Suchen") != null, "EMPTY_SEARCH_REMAINS_OPEN")
        click("Abbrechen")
        shown(priorCustomer)
        click(UiTags.BACK)
        openCustomerList()
        enter("Suchen", fixturePrefix + "000")
        showKeyboard("Suchen")
        visibleResult(fixturePrefix + "000", requireKeyboard = true)
        captureSyntheticScreen("search-customer-list", requireKeyboard = true)
        visibleResult(fixturePrefix + "000", tap = true, requireKeyboard = true)
        reveal("Bearbeiten")
        recreate()
        click(UiTags.BACK)
        yes(
            field("Suchen")?.text?.toString()?.contains(fixturePrefix + "000") == true,
            "LIST_SEARCH_AFTER_DETAIL_RECREATION",
        )
        click(UiTags.area("today"))
    }

    private fun backGestureJourney() {
        val overlays = shell("cmd overlay list --user 0 android")
        val originalMode = shell("settings get secure navigation_mode")
        test.sendStatus(
            0,
            Bundle().apply { putString("gesture_environment", "mode=$originalMode\n$overlays") },
        )
        yes(
            overlays.contains("com.android.internal.systemui.navbar.gestural"),
            "GESTURAL_OVERLAY_INSTALLED",
        )
        val original =
            overlays
                .lineSequence()
                .firstOrNull {
                    it.trim().startsWith("[x]") &&
                        it.contains("com.android.internal.systemui.navbar.")
                }
                ?.substringAfter("] ")
                ?.trim()
        // The base three-button configuration needs no enabled navbar overlay.
        yes(original != null || originalMode == "0", "RECOGNIZED_INITIAL_NAVIGATION_MODE")
        try {
            // Exercise a real navigation-mode transition even when the image boots gestural.
            shell(
                "cmd overlay enable-exclusive --category --user 0 com.android.internal.systemui.navbar.threebutton"
            )
            waitUntil("THREE_BUTTON_NAVIGATION_ENABLED") {
                shell("settings get secure navigation_mode") == "0"
            }
            shell(
                "cmd overlay enable-exclusive --category --user 0 com.android.internal.systemui.navbar.gestural"
            )
            waitUntil("GESTURE_NAVIGATION_ENABLED") {
                shell("settings get secure navigation_mode") == "2"
            }
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            launch()
            newWork()
            val priorCustomer = openWorkCustomerPicker()
            enter("Suchen", fixturePrefix + "000")
            visibleResult(fixturePrefix + "000")
            yes(!keyboardVisible(), "GESTURE_PICKER_IME_INITIALLY_CLOSED")
            fun gestureDump(): String =
                shell(
                    "dumpsys activity service com.android.systemui/.SystemUIService --dump-priority NORMAL"
                ) +
                    "\n" +
                    shell(
                        "dumpsys activity service com.android.systemui/.SystemUIService --dump-priority CRITICAL"
                    )
            fun gestureHandlers(): String {
                val dump = gestureDump()
                val headers =
                    Regex("(?m)^\\s*(?:[A-Za-z0-9_$.]+\\.)?EdgeBackGestureHandler[^\\r\\n]*$")
                        .findAll(dump)
                        .toList()
                return headers
                    .mapIndexed { index, header ->
                        val start = header.range.last + 1
                        val end =
                            minOf(
                                start + 2200,
                                headers.getOrNull(index + 1)?.range?.first ?: dump.length,
                            )
                        dump.substring(start, end)
                    }
                    .joinToString("\nNEXT_HANDLER\n")
            }
            // Android may delegate back to Launcher/Taskbar; require the window's real
            // gesture regions and the observable edge gesture, not one internal dump owner.
            waitUntil("SYSTEM_BACK_GESTURE_EDGES") {
                var enabled = false
                test.runOnMainSync {
                    val edges =
                        activity
                            ?.window
                            ?.decorView
                            ?.rootWindowInsets
                            ?.getInsets(WindowInsets.Type.systemGestures())
                    enabled = edges != null && edges.left > 0 && edges.right > 0
                }
                enabled && shell("settings get secure navigation_mode") == "2"
            }
            test.sendStatus(
                0,
                Bundle().apply { putString("gesture_handlers_before", gestureHandlers()) },
            )
            // Injected edge swipes do not reach the emulator's system gesture reliably; the
            // back key uses the same app back path. The real edge gesture is a Pixel check.
            back()
            try {
                waitUntil("BACK_GESTURE_CLOSES_ONLY_PICKER") {
                    node("Abbrechen") == null && node(priorCustomer) != null
                }
            } catch (failure: Throwable) {
                test.sendStatus(
                    0,
                    Bundle().apply {
                        putString("gesture_diagnostics", gestureHandlers())
                        putString(
                            "gesture_logcat",
                            shell(
                                    "logcat -d -t 120 -s EdgeBackGestureHandler BackPanelController WindowOnBackDispatcher BackNavigationController"
                                )
                                .takeLast(12000),
                        )
                    },
                )
                throw failure
            }
            captureSyntheticScreen("back-gesture")
            click(UiTags.BACK)
            shown("Arbeit starten")
        } catch (failure: Throwable) {
            runCatching { captureSyntheticScreen("failure-back-gesture-active") }
            throw failure
        } finally {
            if (original == null)
                shell("cmd overlay disable --user 0 com.android.internal.systemui.navbar.gestural")
            else shell("cmd overlay enable-exclusive --category --user 0 $original")
            waitUntil("ORIGINAL_NAVIGATION_RESTORED") {
                shell("settings get secure navigation_mode") == originalMode
            }
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            launch()
        }
    }

    /** The office lists every entry directly; the group label is only a heading. */
    private fun openOfficeEntry(entry: String) {
        openOffice()
        click(entry)
    }

    private fun keyboardSearchProof(
        key: String,
        fieldLabel: String,
        query: String,
        result: String,
        manual: Boolean = false,
    ) {
        enter(fieldLabel, "NO_SYNTHETIC_MATCH_48273")
        showKeyboard(fieldLabel)
        if (manual) {
            yes(
                requireNotNull(field(fieldLabel))
                    .performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id),
                "DOMAIN_EMPTY_SEARCH_$key",
            )
        }
        waitUntil("DOMAIN_ZERO_$key") { visibleTexts().any { it.startsWith("0 Treffer") } }
        enter(fieldLabel, query)
        if (manual) {
            showKeyboard(fieldLabel)
            yes(
                requireNotNull(field(fieldLabel))
                    .performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id),
                "DOMAIN_MANUAL_SEARCH_$key",
            )
        }
        showKeyboard(fieldLabel)
        visibleResult(result, requireKeyboard = true)
        captureSyntheticScreen("domain-$key", requireKeyboard = true)
        yes(
            requireNotNull(field(fieldLabel))
                .performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id),
            "DOMAIN_IME_ACTION_$key",
        )
        waitUntil("DOMAIN_IME_CLOSED_WITH_QUERY_$key") {
            !keyboardVisible() && field(fieldLabel)?.text?.toString()?.contains(query) == true
        }
        visibleResult(result)
        showKeyboard(fieldLabel)
        visibleResult(result, tap = true, requireKeyboard = true)
        waitUntil("DOMAIN_TOUCH_OPENED_$key") { !keyboardVisible() && field(fieldLabel) == null }
        domainSearchCases.put(
            JSONObject()
                .put("case", key)
                .put("zero_and_matches", true)
                .put("keyboard_bounds", true)
                .put("real_touch", true)
                .put("ime_preserves_query", true)
                .put("screenshot", "qa-native-domain-$key.png")
        )
    }

    /**
     * Search from Today: results grouped by kind, call and route on the hit, last opened without a
     * query.
     */
    /**
     * Real gestures, undo, durable deletion after recreation, positive actions and invoice guard.
     */
    private fun listActionsJourney() {
        val beforeMaster = JSONObject(store.getState().getJSONObject("master").toString())
        val beforeLedger = store.ownerValue(SettlementRules.KEY, "{}")
        val beforeQueue = store.ownerValue(ListActionTools.KEY, "{}")
        val customerId = "qa_swipe_customer"
        val orderId = "qa_swipe_order"
        val invoiceId = "qa_swipe_invoice"
        val claimTrigger = "qa_list_claim_abort"
        val customer = customers(fixturePrefix + "000").single().getString("id")
        val source = JSONObject(beforeMaster.toString())
        source
            .getJSONArray("customers")
            .put(
                JSONObject()
                    .put("id", customerId)
                    .put("name", "[TEST] AAA Wischkunde")
                    .put("active", 1)
                    .put("billing_mode", "direct")
                    .put("test_record", true)
            )
        source
            .getJSONArray("orders")
            .put(
                JSONObject()
                    .put("id", orderId)
                    .put("customer_id", customer)
                    .put("title", "[TEST] AAA Wischauftrag")
                    .put("status", "Geplant")
                    .put("created_at", "2026-11-08T10:00:00Z")
                    .put("updated_at", "2026-11-08T10:00:00Z")
                    .put("test_record", true)
            )
        val invoice =
            JSONObject()
                .put("id", invoiceId)
                .put("title", "[TEST] AAA geschützte Rechnung")
                .put("kind", "invoice")
                .put("route", "billed")
                .put("status", "confirmed")
                .put("issue_date", LocalDate.now(ZoneId.of("Europe/Vienna")).toString())
                .put("invoice_number", "QA-WISCH-1")
                .put("total_minor", 1000)
        store.putOwnerValue("snapshot", source.toString())
        store.putOwnerValue(
            SettlementRules.KEY,
            JSONObject()
                .put("records", JSONArray().put(invoice))
                .put("payments", JSONArray())
                .toString(),
        )
        store.notifyCommittedChange()
        activity?.let { current -> test.runOnMainSync { current.finish() } }
        launch()
        fun rows(entity: String, id: String): List<JSONObject> {
            val response = tools.call("get_list_actions", JSONObject())
            val entries = (response.optJSONObject("data") ?: response).getJSONArray("actions")
            return (0 until entries.length())
                .map { entries.getJSONObject(it) }
                .filter { it.optString("entity") == entity && it.optString("entity_id") == id }
        }
        fun record(collection: String, id: String) =
            OwnerJournalRules.findOrNull(
                PixelMcpReadTools.effective(store.getState()).optJSONArray(collection),
                id,
            )
        fun swipe(tag: String, right: Boolean) {
            reveal(tag)
            val target = requireNotNull(node(tag))
            val bounds = Rect().also(target::getBoundsInScreen)
            val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
            yes(
                bounds.intersect(screen) && bounds.width() >= 48 && bounds.height() >= 48,
                "SWIPE_REAL_TARGET_$tag",
            )
            val start = bounds.left + bounds.width() * if (right) 0.15f else 0.85f
            val end = bounds.left + bounds.width() * if (right) 0.85f else 0.15f
            val down = SystemClock.uptimeMillis()
            fun event(action: Int, x: Float) {
                val motion =
                    MotionEvent.obtain(
                        down,
                        SystemClock.uptimeMillis(),
                        action,
                        x,
                        bounds.exactCenterY(),
                        0,
                    )
                motion.source = InputDevice.SOURCE_TOUCHSCREEN
                try {
                    yes(automation.injectInputEvent(motion, true), "SWIPE_INPUT_$tag")
                } finally {
                    motion.recycle()
                }
            }
            event(MotionEvent.ACTION_DOWN, start)
            for (step in 1..12) {
                SystemClock.sleep(16)
                event(MotionEvent.ACTION_MOVE, start + (end - start) * step / 12f)
            }
            event(MotionEvent.ACTION_UP, end)
        }
        try {
            click(UiTags.area("customers"))
            click(UiTags.area("customers"))
            swipe(UiTags.swipe("customer", customerId), right = false)
            // The snackbar is fixed; scrolling while it appears spends its five-second window.
            click(UiTags.UNDO, scrollTo = false)
            waitUntil("SWIPE_CUSTOMER_UNDONE") {
                rows("customer", customerId).lastOrNull()?.optString("state") == "undone" &&
                    record("customers", customerId) != null
            }
            reveal(UiTags.swipe("customer", customerId))
            store.writableDatabase.execSQL(
                """
                CREATE TRIGGER $claimTrigger BEFORE INSERT ON owner_state
                WHEN NEW.key='${ListActionTools.KEY}' AND instr(NEW.value,'"state":"applying"')>0
                BEGIN SELECT RAISE(ABORT,'QA list claim failure'); END
                """
                    .trimIndent()
            )
            swipe(UiTags.swipe("customer", customerId), right = false)
            waitUntil("SWIPE_CUSTOMER_QUEUED") {
                rows("customer", customerId).lastOrNull()?.optString("state") == "queued"
            }
            val due = rows("customer", customerId).last().getLong("undo_until")
            waitUntil("SWIPE_CLAIM_FAILURE_WINDOW") { System.currentTimeMillis() >= due + 1500 }
            yes(
                rows("customer", customerId).last().optString("state") == "queued" &&
                    record("customers", customerId) != null &&
                    node(UiTags.area("customers")) != null,
                "SWIPE_FAILED_CLAIM_PRESERVES_DATA_AND_APP",
            )
            store.writableDatabase.execSQL("DROP TRIGGER $claimTrigger")
            recreate()
            waitUntil("SWIPE_CUSTOMER_PERMANENT_AFTER_RECREATION") {
                rows("customer", customerId).lastOrNull()?.optString("state") == "done" &&
                    record("customers", customerId) == null
            }
            yes(
                !OwnerJournalRules.archives(PixelMcpReadTools.effective(store.getState()))
                    .has("customer:$customerId"),
                "SWIPE_NO_TRASH_AFTER_WINDOW",
            )
            click(UiTags.area("today"))
            click(UiTags.area("today"))
            click(UiTags.MORE)
            click(UiTags.area("orders"))
            val workBefore =
                PixelMcpWriteTools.hash(
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
                )
            swipe(UiTags.swipe("order", orderId), right = true)
            click(UiTags.UNDO, scrollTo = false)
            waitUntil("SWIPE_ORDER_UNDONE") {
                rows("order", orderId).lastOrNull()?.optString("state") == "undone" &&
                    record("orders", orderId)?.optString("status") == "Geplant"
            }
            swipe(UiTags.swipe("order", orderId), right = true)
            waitUntil("SWIPE_ORDER_COMPLETED") {
                rows("order", orderId).lastOrNull()?.optString("state") == "done" &&
                    record("orders", orderId)?.optString("status") == "Erledigt"
            }
            val completedCount = rows("order", orderId).size
            swipe(UiTags.swipe("order", orderId), right = true)
            waitStable("SWIPE_COMPLETED_ORDER_REPEAT", stableMs = 500) {
                rows("order", orderId).size == completedCount &&
                    record("orders", orderId)?.optString("status") == "Erledigt"
            }
            yes(
                PixelMcpWriteTools.hash(
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
                ) == workBefore,
                "SWIPE_ORDER_DOES_NOT_INVENT_WORK",
            )
            click(UiTags.area("accounting"))
            click(UiTags.area("accounting"))
            click(UiTags.billingTab(1))
            val invoiceBefore = store.ownerValue(SettlementRules.KEY, "{}")
            swipe(UiTags.swipe("settlement", invoiceId), right = false)
            swipe(UiTags.swipe("settlement", invoiceId), right = true)
            waitStable("SWIPE_ISSUED_INVOICE_PROTECTED", stableMs = 500) {
                rows("settlement", invoiceId).isEmpty() &&
                    store.ownerValue(SettlementRules.KEY, "{}") == invoiceBefore
            }
            yes(node(UiTags.UNDO) == null, "SWIPE_INVOICE_HAS_NO_UNDO_ACTION")
            captureSyntheticScreen("list-swipe-protection")
        } finally {
            store.writableDatabase.execSQL("DROP TRIGGER IF EXISTS $claimTrigger")
            waitUntil("SWIPE_TEST_ACTIONS_FINISHED") {
                listOf("customer" to customerId, "order" to orderId, "settlement" to invoiceId)
                    .none { (entity, id) ->
                        rows(entity, id).any {
                            it.optString("state") in setOf("queued", "applying", "uncertain")
                        }
                    }
            }
            store.putOwnerValue("snapshot", beforeMaster.toString())
            store.putOwnerValue(SettlementRules.KEY, beforeLedger)
            store.putOwnerValue(ListActionTools.KEY, beforeQueue)
            store.notifyCommittedChange()
        }
    }

    /** Real long-press drags, shared planning reads, weather and a blocked external map handoff. */
    private fun planningBoardJourney() {
        val beforeMaster = JSONObject(store.getState().getJSONObject("master").toString())
        val beforeOwners = mutableListOf<Pair<String, String>>()
        val beforePending = mutableListOf<Array<String>>()
        store.readableDatabase.rawQuery("SELECT key,value FROM owner_state", null).use { cursor ->
            while (cursor.moveToNext()) beforeOwners += cursor.getString(0) to cursor.getString(1)
        }
        store.readableDatabase
            .rawQuery("SELECT id,kind,payload,created_at FROM pending", null)
            .use { cursor ->
                while (cursor.moveToNext()) beforePending += Array(4) { cursor.getString(it) }
            }
        val day = LocalDate.now(ZoneId.of("Europe/Vienna"))
        val zone = ZoneId.of("Europe/Vienna")
        val customer = customers(fixturePrefix + "000").single().getString("id")
        val appointmentId = "qa_board_ui_appointment"
        val monitorResult = java.util.concurrent.atomic.AtomicReference<String>()
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action != Intent.ACTION_VIEW) return null
                    monitorResult.set(intent.dataString)
                    return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
            }
        var monitorInstalled = false
        try {
            fun property(suffix: String, latitude: Double, longitude: Double) =
                read(
                        "create_property",
                        JSONObject()
                            .put("request_id", "qa_board_property_$suffix")
                            .put("customer_id", customer)
                            .put("name", "[TEST] QA Planungsobjekt $suffix")
                            .put("address", "[TEST] QA Gartenweg $suffix")
                            .put("latitude", latitude)
                            .put("longitude", longitude),
                    )
                    .getString("property_id")
            val firstProperty = property("a", 48.2, 16.3)
            val secondProperty = property("b", 48.21, 16.32)
            fun visit(suffix: String, propertyId: String, begin: String, end: String) =
                read(
                        "plan_visit",
                        JSONObject()
                            .put("request_id", "qa_board_visit_$suffix")
                            .put("property_id", propertyId)
                            .put("date", day.toString())
                            .put("title", "[TEST] QA Planungseinsatz $suffix")
                            .put("planned_minutes", 60)
                            .put("window_start", begin)
                            .put("window_end", end),
                    )
                    .getString("visit_id")
            val firstVisit = visit("a", firstProperty, "09:00", "10:00")
            val secondVisit = visit("b", secondProperty, "15:00", "16:00")
            val appointment =
                JSONObject()
                    .put("id", appointmentId)
                    .put("customer_id", customer)
                    .put("title", "[TEST] QA Planungstermin")
                    .put("purpose", "work")
                    .put("starts_at", day.atTime(12, 0).atZone(zone).toInstant().toString())
                    .put("ends_at", day.atTime(13, 0).atZone(zone).toInstant().toString())
                    .put("timezone", zone.id)
                    .put("location", "[TEST] QA Terminort")
                    .put("latitude", 48.25)
                    .put("longitude", 16.35)
                    .put("weather_profile", "general")
                    .put("status", "confirmed")
                    .put("travel_minutes", 0)
                    .put("break_minutes", 0)
                    .put("notes", "")
                    .put("scope_refs", JSONArray())
            val master = JSONObject(beforeMaster.toString())
            master.getJSONArray("appointments").put(appointment)
            store.putOwnerValue("snapshot", master.toString())
            val tour = read("get_day_tour", JSONObject().put("date", day.toString()))
            read(
                "set_day_tour",
                JSONObject()
                    .put("request_id", "qa_board_initial_tour")
                    .put("date", day.toString())
                    .put("expected_record_hash", tour.getString("record_hash"))
                    .put("visit_ids", JSONArray().put(firstVisit).put(secondVisit)),
            )

            // Synthetic hourly data; no weather request or network dependency.
            val times = JSONArray()
            val temperature = JSONArray()
            val rain = JSONArray()
            val gust = JSONArray()
            val probability = JSONArray()
            val base = day.atStartOfDay(zone).toEpochSecond()
            for (hour in 0..25) {
                times.put(base + hour * 3600L)
                temperature.put(15)
                rain.put(8)
                gust.put(90)
                probability.put(90)
            }
            val cache =
                JSONObject()
                    .put("fetched_at", java.time.Instant.now().toString())
                    .put(
                        "forecast",
                        JSONObject()
                            .put(
                                "hourly",
                                JSONObject()
                                    .put("time", times)
                                    .put("temperature_2m", temperature)
                                    .put("precipitation", rain)
                                    .put("wind_gusts_10m", gust)
                                    .put("weather_code", JSONArray((0..25).map { 95 }))
                                    .put("precipitation_probability", probability),
                            ),
                    )
            store.putOwnerValue(
                "weather:" +
                    WeatherRiskRules.locationKey(
                        JSONObject().put("latitude", 48.2).put("longitude", 16.3)
                    ),
                cache.toString(),
            )
            val priorSettings = store.ownerValue("planning:settings", "{}")
            store.putOwnerValue(
                "planning:settings",
                JSONObject(priorSettings).put("weather_enabled", true).toString(),
            )
            store.notifyCommittedChange()
            val boardArgs =
                JSONObject()
                    .put("date_from", day.toString())
                    .put("date_to", day.toString())
                    .put("limit", 50)
            val board = read("get_planning_board", boardArgs)
            val items = SettlementRules.rows(board.getJSONArray("items"))
            yes(
                items.map { it.getJSONObject("record").getString("id") }.toSet() ==
                    setOf(firstVisit, secondVisit, appointmentId),
                "BOARD_READ_HAS_BOTH_PLANNING_KINDS",
            )
            yes(
                items
                    .single { it.getJSONObject("record").optString("id") == firstVisit }
                    .getJSONObject("weather")
                    .optString("severity") == "critical",
                "BOARD_VISIT_WEATHER_FROM_KNOWN_OBJECT",
            )
            fun stateHash() =
                PixelMcpWriteTools.hash(
                    JSONObject()
                        .put("master", PixelMcpReadTools.effective(store.getState()))
                        .put(
                            "visit_a",
                            read("get_visit", JSONObject().put("visit_id", firstVisit))
                                .getString("record_hash"),
                        )
                        .put(
                            "visit_b",
                            read("get_visit", JSONObject().put("visit_id", secondVisit))
                                .getString("record_hash"),
                        )
                        .put(
                            "property_a",
                            read("get_property", JSONObject().put("property_id", firstProperty))
                                .getString("record_hash"),
                        )
                        .put(
                            "property_b",
                            read("get_property", JSONObject().put("property_id", secondProperty))
                                .getString("record_hash"),
                        )
                        .put(
                            "tour",
                            read("get_day_tour", JSONObject().put("date", day.toString()))
                                .getString("record_hash"),
                        )
                )
            val beforeViews = stateHash()
            click(UiTags.area("planning"))
            click(UiTags.area("planning"))
            reveal(UiTags.planningWeather("visit", firstVisit))
            click(UiTags.planningView("week"))
            shown(UiTags.PLANNING_GRID)
            click(UiTags.planningView("map"))
            shown(UiTags.PLANNING_MAP)
            click(UiTags.planningView("day"))
            shown(UiTags.PLANNING_GRID)
            yes(stateHash() == beforeViews, "BOARD_VIEWS_AND_WEATHER_ARE_READ_ONLY")
            store.putOwnerValue("planning:settings", priorSettings)
            store.notifyCommittedChange()

            fun dragHalfHour(kind: String, id: String) {
                val tag = UiTags.planningEntry(kind, id)
                reveal(tag)
                var hit = Rect()
                waitUntil("BOARD_REAL_DRAG_TARGET_$tag") {
                    val target = node(tag) ?: return@waitUntil false
                    hit = Rect().also(target::getBoundsInScreen)
                    var parent = target.parent
                    while (parent != null) {
                        val bounds = Rect().also(parent::getBoundsInScreen)
                        if (!bounds.isEmpty && !hit.intersect(bounds)) return@waitUntil false
                        parent = parent.parent
                    }
                    val minimum = (48 * context.resources.displayMetrics.density).toInt()
                    hit.width() >= minimum && hit.height() >= minimum
                }
                val down = SystemClock.uptimeMillis()
                val delta = 30 * context.resources.displayMetrics.density
                fun event(action: Int, y: Float) {
                    val motion =
                        MotionEvent.obtain(
                            down,
                            SystemClock.uptimeMillis(),
                            action,
                            hit.exactCenterX(),
                            y,
                            0,
                        )
                    motion.source = InputDevice.SOURCE_TOUCHSCREEN
                    try {
                        yes(automation.injectInputEvent(motion, true), "BOARD_DRAG_TOUCH_$tag")
                    } finally {
                        motion.recycle()
                    }
                }
                event(MotionEvent.ACTION_DOWN, hit.exactCenterY())
                SystemClock.sleep(
                    android.view.ViewConfiguration.getLongPressTimeout().toLong() + 150
                )
                for (step in 1..12) {
                    SystemClock.sleep(16)
                    event(MotionEvent.ACTION_MOVE, hit.exactCenterY() + delta * step / 12)
                }
                event(MotionEvent.ACTION_UP, hit.exactCenterY() + delta)
            }
            dragHalfHour("visit", firstVisit)
            waitUntil("BOARD_VISIT_DRAG_COMMITTED") {
                read("get_visit", JSONObject().put("visit_id", firstVisit))
                    .getJSONObject("record")
                    .optString("window_start") == "09:30"
            }
            val movedVisit =
                read("get_visit", JSONObject().put("visit_id", firstVisit)).getJSONObject("record")
            yes(
                movedVisit.optString("window_end") == "10:30" &&
                    movedVisit.optInt("planned_minutes") == 60 &&
                    movedVisit.optString("property_id") == firstProperty,
                "BOARD_VISIT_DURATION_AND_RELATIONS_RETAINED",
            )
            dragHalfHour("appointment", appointmentId)
            waitUntil("BOARD_APPOINTMENT_DRAG_COMMITTED") {
                read("get_appointment", JSONObject().put("appointment_id", appointmentId))
                    .getJSONObject("appointment")
                    .optString("starts_at") ==
                    day.atTime(12, 30).atZone(zone).toInstant().toString()
            }
            val movedAppointment =
                read("get_appointment", JSONObject().put("appointment_id", appointmentId))
                    .getJSONObject("appointment")
            yes(
                movedAppointment.getString("ends_at") ==
                    day.atTime(13, 30).atZone(zone).toInstant().toString(),
                "BOARD_APPOINTMENT_DURATION_RETAINED",
            )
            recreate()
            yes(
                read("get_visit", JSONObject().put("visit_id", firstVisit))
                    .getJSONObject("record")
                    .optString("window_start") == "09:30",
                "BOARD_DRAG_DURABLE_AFTER_RECREATION",
            )
            click(UiTags.PLANNING_TOUR)
            if (node(UiTags.TOUR_ORDER)?.isSelected != true) click(UiTags.TOUR_ORDER)
            click(UiTags.tourMove(secondVisit, "up"))
            click(UiTags.TOUR_SAVE)
            waitUntil("BOARD_TOUR_ORDER_COMMITTED") {
                val current =
                    read("get_day_tour", JSONObject().put("date", day.toString()))
                        .getJSONObject("tour")
                        .getJSONArray("visit_ids")
                current.length() == 2 &&
                    current.optString(0) == secondVisit &&
                    current.optString(1) == firstVisit
            }
            val saved = read("get_day_tour", JSONObject().put("date", day.toString()))
            val navigation =
                read(
                    "get_tour_navigation",
                    JSONObject()
                        .put("date", day.toString())
                        .put("expected_record_hash", saved.getString("record_hash")),
                )
            yes(
                navigation.getString("target_visit_id") == secondVisit,
                "BOARD_NAVIGATION_USES_FIRST_SAVED_STOP",
            )
            val beforeNavigation = stateHash()
            test.addMonitor(monitor)
            monitorInstalled = true
            click(UiTags.TOUR_NAVIGATE)
            waitUntil("BOARD_EXTERNAL_NAVIGATION_INTENT") {
                monitorResult.get()?.startsWith("google.navigation:") == true
            }
            yes(
                monitorResult.get().contains(Uri.encode("48.21,16.32")),
                "BOARD_MAP_HANDOFF_HAS_CORRECT_DESTINATION",
            )
            yes(stateHash() == beforeNavigation, "BOARD_NAVIGATION_DOES_NOT_WRITE_BUSINESS_DATA")
            captureSyntheticScreen("planning-board-tour")
        } finally {
            if (monitorInstalled) test.removeMonitor(monitor)
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            test.waitForIdleSync()
            val db = store.writableDatabase
            store.beginBusinessTransaction(db)
            try {
                db.delete("pending", null, null)
                for (row in beforePending) db.execSQL(
                    "INSERT INTO pending(id,kind,payload,created_at) VALUES(?,?,?,?)",
                    row,
                )
                db.delete("owner_state", null, null)
                for ((key, value) in beforeOwners) db.execSQL(
                    "INSERT INTO owner_state(key,value) VALUES(?,?)",
                    arrayOf(key, value),
                )
                store.putOwnerValue("snapshot", beforeMaster.toString())
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            store.notifyCommittedChange()
            launch()
        }
    }

    private fun propertyTimelineJourney() {
        val oldFont = shell("settings get system font_scale")
        val beforeMaster = JSONObject(store.getState().getJSONObject("master").toString())
        val beforeOwners = mutableListOf<Pair<String, String>>()
        val beforePending = mutableListOf<Array<String>>()
        store.readableDatabase.rawQuery("SELECT key,value FROM owner_state", null).use { cursor ->
            while (cursor.moveToNext()) beforeOwners += cursor.getString(0) to cursor.getString(1)
        }
        store.readableDatabase
            .rawQuery("SELECT id,kind,payload,created_at FROM pending", null)
            .use { cursor ->
                while (cursor.moveToNext()) beforePending += Array(4) { cursor.getString(it) }
            }
        val day = LocalDate.now(ZoneId.of("Europe/Vienna"))
        val photoFile = File(context.cacheDir, "qa-property-timeline.png")
        var photoId = ""
        var photoOriginal: File? = null
        val navigation = java.util.concurrent.atomic.AtomicReference<Intent>()
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action !in setOf(Intent.ACTION_VIEW, Intent.ACTION_DIAL)) return null
                    navigation.set(intent)
                    return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
            }
        test.addMonitor(monitor)
        try {
            val customer =
                store.saveEditorRecord(
                    "customer",
                    JSONObject()
                        .put("_request_id", "qa_timeline_customer")
                        .put("name", "[TEST] QA Zeitleistenkunde")
                        .put("street", "[TEST] QA Empfängerweg 7")
                        .put("postal_code", "1010")
                        .put("city", "[TEST] QA Wien")
                        .put("country", "AT")
                        .put("phone", "+430000000007")
                        .put("active", true)
                        .put("test_record", true),
                )
            val property =
                read(
                        "create_property",
                        JSONObject()
                            .put("request_id", "qa_timeline_property")
                            .put("customer_id", customer)
                            .put("name", "[TEST] QA Zeitleistenobjekt")
                            .put("address", "[TEST] QA Zeitleistenweg 7")
                            .put("latitude", 48.2)
                            .put("longitude", 16.3),
                    )
                    .getString("property_id")
            val order =
                store.saveEditorRecord(
                    "order",
                    JSONObject()
                        .put("_request_id", "qa_timeline_order")
                        .put("customer_id", customer)
                        .put("title", "[TEST] QA Zeitleistenauftrag")
                        .put("status", "Geplant")
                        .put("test_record", true),
                )
            val visit =
                read(
                        "plan_visit",
                        JSONObject()
                            .put("request_id", "qa_timeline_visit")
                            .put("property_id", property)
                            .put("title", "[TEST] QA Zeitleisteneinsatz")
                            .put("date", day.toString())
                            .put("planned_minutes", 60),
                    )
                    .getString("visit_id")
            val inquiry =
                read(
                        "create_inquiry",
                        JSONObject()
                            .put("request_id", "qa_timeline_inquiry")
                            .put("customer_id", customer)
                            .put("property_id", property)
                            .put("title", "[TEST] QA Zeitleistenanfrage"),
                    )
                    .getString("inquiry_id")
            val inquiryHash =
                read("get_inquiry", JSONObject().put("inquiry_id", inquiry))
                    .getString("record_hash")
            read(
                "update_inquiry",
                JSONObject()
                    .put("request_id", "qa_timeline_inquiry_order")
                    .put("inquiry_id", inquiry)
                    .put("expected_record_hash", inquiryHash)
                    .put("updates", JSONObject().put("order_id", order)),
            )
            val care =
                read(
                        "create_care_plan",
                        JSONObject()
                            .put("request_id", "qa_timeline_care")
                            .put("property_id", property)
                            .put("title", "[TEST] QA Zeitleistenpflege")
                            .put("anchor_date", day.toString())
                            .put("interval_days", 7)
                            .put("planned_minutes", 60),
                    )
                    .getString("care_plan_id")
            val work = "qa_timeline_work"
            val appointment =
                read(
                        "create_appointment",
                        JSONObject()
                            .put("request_id", "qa_timeline_appointment")
                            .put("customer_id", customer)
                            .put("order_id", order)
                            .put("title", "[TEST] QA Zeitleistentermin")
                            .put("purpose", "work")
                            .put(
                                "starts_at",
                                day.atTime(14, 0)
                                    .atZone(ZoneId.of("Europe/Vienna"))
                                    .toInstant()
                                    .toString(),
                            )
                            .put(
                                "ends_at",
                                day.atTime(15, 0)
                                    .atZone(ZoneId.of("Europe/Vienna"))
                                    .toInstant()
                                    .toString(),
                            )
                            .put("timezone", "Europe/Vienna")
                            .put("status", "confirmed")
                            .put("location", "[TEST] QA Zeitleistenweg 7")
                            .put("weather_profile", "general")
                            .put("travel_minutes", 0)
                            .put("break_minutes", 0),
                    )
                    .getString("appointment_id")
            val source = JSONObject(PixelMcpReadTools.effective(store.getState()).toString())
            source.put(
                "business_settings",
                NativeSettlementNativeTests.fixture().getJSONObject("business_settings"),
            )
            source
                .getJSONArray("worklogs")
                .put(
                    NativeSettlementNativeTests.work(work, 0)
                        .put("customer_id", customer)
                        .put("billing_customer_id", customer)
                        .put("property_id", property)
                        .put("visit_id", visit)
                )
            store.putOwnerValue("snapshot", source.toString())
            val bitmap = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xff8a3421.toInt())
            photoFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            val identity = BackupDeviceGuard(context).current()
            val controller = NativeAppController(requireNotNull(activity) as MainActivity, store)
            photoId =
                runBlocking {
                        controller.importPhoto(
                            Uri.fromFile(photoFile),
                            JSONObject()
                                .put("_photo_request_id", "qa_timeline_photo")
                                .put("customer_id", customer)
                                .put("property_id", property)
                                .put("visit_id", visit)
                                .put("role", "documentation")
                                .put("caption", "[TEST] QA Objektfoto")
                                .put(
                                    "_capture_context",
                                    JSONObject()
                                        .put("formatVersion", 1)
                                        .put(
                                            "installation_id",
                                            identity.getString("installationId"),
                                        )
                                        .put("restore_epoch", identity.getString("restoreEpoch")),
                                ),
                        )
                    }
                    .getString("photo_id")
            photoOriginal =
                store.photoAttachment(photoId)?.let {
                    store.photoAttachmentStore().verifiedFile(it)
                }
            yes(
                LocalOwnerRepository(context, store).migratePersonalPaid("[]") == "OK",
                "TIMELINE_PERSONAL_ASSIGNMENTS_INITIALIZED_THROUGH_EXISTING_WRITER",
            )
            val draft =
                NativeSettlementNativeTests.draft("labor", "20", work)
                    .put("billing_customer_id", customer)
            val settlement =
                read(
                        "save_settlement_draft",
                        JSONObject().put("request_id", "qa_timeline_settlement").put("draft", draft),
                    )
                    .getString("settlement_id")
            fun timeline(view: String = "history") =
                read(
                    "get_property_timeline",
                    JSONObject().put("property_id", property).put("view", view),
                )
            val history = SettlementRules.rows(timeline().getJSONArray("items"))
            yes(
                history.any { it.getString("key") == "inquiry:$inquiry" } &&
                    history.any { it.getString("key") == "order:$order" } &&
                    history.any { it.getString("key") == "visit:$visit" } &&
                    history.any { it.getString("key") == "appointment:$appointment" },
                "TIMELINE_EXPLICIT_CONTEXT_PROJECTED",
            )
            yes(
                history.none { it.getString("kind") == "payment" },
                "TIMELINE_NO_PAYMENT_BEFORE_REAL_PAYMENT",
            )
            store.notifyCommittedChange()
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            launch()
            fun openProperty() {
                openOffice()
                click(UiTags.area("properties"))
                scrollTop()
                var lastScroll = 0L
                waitUntil("TIMELINE_PROPERTY_LIST_OR_DETAIL") {
                    val available =
                        node(UiTags.recordChoice("properties", property)) != null ||
                            node(UiTags.OBJECT_ROUTE) != null
                    if (!available && SystemClock.uptimeMillis() - lastScroll > 250) {
                        if (!scroll()) scrollTop()
                        lastScroll = SystemClock.uptimeMillis()
                    }
                    available
                }
                if (node(UiTags.recordChoice("properties", property)) != null)
                    click(UiTags.recordChoice("properties", property))
                selectTab(UiTags.objectTab("history"))
            }
            openProperty()
            val beforeNavigation =
                PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
            click(UiTags.OBJECT_ROUTE)
            waitUntil("TIMELINE_EXTERNAL_ROUTE") {
                navigation.get()?.dataString?.startsWith("geo:") == true
            }
            yes(
                navigation.get().dataString!!.contains("48.2"),
                "TIMELINE_ROUTE_USES_SAVED_OBJECT_LOCATION",
            )
            click(UiTags.OBJECT_CALL)
            waitUntil("TIMELINE_PHONE_DIALER") { navigation.get()?.action == Intent.ACTION_DIAL }
            yes(
                navigation.get().data?.schemeSpecificPart == "+430000000007",
                "TIMELINE_CALL_USES_CUSTOMER_NUMBER",
            )
            yes(
                PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) ==
                    beforeNavigation,
                "TIMELINE_EXTERNAL_ACTIONS_READ_ONLY",
            )
            selectTab(UiTags.objectTab("care"))
            reveal(UiTags.objectEntry("care_plan:$care"))
            selectTab(UiTags.objectTab("photos"))
            click(UiTags.objectEntry("photo:$photoId"))
            shown(UiTags.context("properties", property))
            click(UiTags.PRIMARY)
            enter(UiTags.PHOTO_CAPTION, "[TEST] QA Objektfoto ergänzt")
            if (keyboardVisible()) back()
            click(UiTags.PRIMARY)
            waitUntil("TIMELINE_PHOTO_REAL_SAVE") {
                read("get_photo", JSONObject().put("photo_id", photoId))
                    .getJSONObject("photo")
                    .optString("caption") == "[TEST] QA Objektfoto ergänzt"
            }
            recreate()
            click(UiTags.context("properties", property))
            selectTab(UiTags.objectTab("money"))
            click(UiTags.objectEntry("settlement:$settlement"))
            reveal(UiTags.settlementState("draft"))
            waitUntil("TIMELINE_CLOSURE_DRAFT_READY") {
                node(UiTags.settlementState("draft")) != null &&
                    node(UiTags.PRIMARY) != null &&
                    actionEnabled(UiTags.PRIMARY)
            }
            click(UiTags.PRIMARY)
            waitUntil("TIMELINE_INTERNAL_CLOSURE_CONFIRMED") {
                read("get_settlement", JSONObject().put("settlement_id", settlement))
                    .getJSONObject("record")
                    .optString("status") == "confirmed"
            }
            // The new footer records payment through the existing durable fiscal operation.
            waitUntil("TIMELINE_PAYMENT_FOOTER_READY") {
                node(UiTags.settlementState("confirmed")) != null &&
                    node(UiTags.PRIMARY) != null &&
                    actionEnabled(UiTags.PRIMARY)
            }
            click(UiTags.PRIMARY)
            waitUntil("TIMELINE_PAYMENT_RECORDED") {
                read("get_settlement", JSONObject().put("settlement_id", settlement))
                    .optLong("balance_minor", -1) == 0L
            }
            recreate()
            val paid = read("get_settlement", JSONObject().put("settlement_id", settlement))
            yes(
                SettlementRules.rows(paid.getJSONArray("payments")).size == 1,
                "TIMELINE_RECREATE_DOES_NOT_REPEAT_PAYMENT",
            )
            reveal(UiTags.DETAIL_DELETE)
            yes(
                !actionEnabled(UiTags.DETAIL_DELETE),
                "TIMELINE_CONFIRMED_DOCUMENT_DELETE_PROTECTED",
            )
            click(UiTags.context("properties", property))
            selectTab(UiTags.objectTab("history"))
            click(UiTags.objectEntry("visit:$visit"))
            shown(UiTags.context("properties", property))
            click(UiTags.DETAIL_MENU)
            shown(UiTags.detailAction("visit-tour"))
            back()
            click(UiTags.context("properties", property))
            selectTab(UiTags.objectTab("history"))
            click(UiTags.objectEntry("appointment:$appointment"))
            shown(UiTags.context("properties", property))
            shown(UiTags.context("orders", order))
            click(UiTags.DETAIL_MENU)
            shown(UiTags.detailAction("appointment-cancel"))
            back()
            click(UiTags.context("properties", property))
            shell("settings put system font_scale 1.7")
            recreate()
            waitUntil("TIMELINE_LARGE_FONT_APPLIED") {
                requireNotNull(activity).resources.configuration.fontScale >= 1.69f
            }
            openProperty()
            waitStable("TIMELINE_LARGE_FONT_FAB_CLEAR_OF_FOOTER", stableMs = 500) {
                val plus = runCatching { actionBounds(UiTags.CREATE) }.getOrNull()
                val primary = runCatching { actionBounds(UiTags.PRIMARY) }.getOrNull()
                val secondary = runCatching { actionBounds(UiTags.SECONDARY) }.getOrNull()
                plus != null &&
                    primary != null &&
                    secondary != null &&
                    !Rect.intersects(plus, primary) &&
                    !Rect.intersects(plus, secondary) &&
                    plus.bottom <= minOf(primary.top, secondary.top)
            }
            click(UiTags.CREATE)
            click(UiTags.createChoice("visit"))
            enter(UiTags.detailField("title"), "[TEST] QA vom Objekt vorgeplant")
            if (keyboardVisible()) back()
            click(UiTags.PRIMARY)
            waitUntil("TIMELINE_NEW_VISIT_WITH_CONTEXT") {
                SettlementRules.rows(
                        read("list_visits", JSONObject().put("property_id", property))
                            .getJSONArray("items")
                    )
                    .any {
                        it.optString("title") == "[TEST] QA vom Objekt vorgeplant" &&
                            it.optString("property_id") == property &&
                            it.optString("customer_id") == customer
                    }
            }
            val paymentEvents =
                SettlementRules.rows(timeline("money").getJSONArray("items")).filter {
                    it.optString("kind") == "payment"
                }
            yes(
                paymentEvents.size == 1 && paymentEvents.single().getLong("amount_minor") == 2000L,
                "TIMELINE_SHOWS_REAL_PAYMENT_ONCE",
            )
        } finally {
            shell(
                if (oldFont == "null") "settings delete system font_scale"
                else "settings put system font_scale $oldFont"
            )
            test.removeMonitor(monitor)
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            val db = store.writableDatabase
            db.beginTransaction()
            try {
                if (photoId.isNotBlank())
                    db.delete("photo_attachments", "photo_id=?", arrayOf(photoId))
                db.delete("pending", null, null)
                for (row in beforePending) db.execSQL(
                    "INSERT INTO pending(id,kind,payload,created_at) VALUES(?,?,?,?)",
                    row,
                )
                db.delete("owner_state", null, null)
                for ((key, value) in beforeOwners) db.execSQL(
                    "INSERT INTO owner_state(key,value) VALUES(?,?)",
                    arrayOf(key, value),
                )
                store.putOwnerValue("snapshot", beforeMaster.toString())
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            photoFile.delete()
            photoOriginal?.delete()
            store.notifyCommittedChange()
            launch()
        }
    }

    private fun backgroundFeedbackJourney() {
        val beforeMaster = JSONObject(store.getState().getJSONObject("master").toString())
        val beforeOwners = mutableListOf<Pair<String, String>>()
        val beforePending = mutableListOf<Array<String>>()
        store.readableDatabase.rawQuery("SELECT key,value FROM owner_state", null).use { cursor ->
            while (cursor.moveToNext()) beforeOwners += cursor.getString(0) to cursor.getString(1)
        }
        store.readableDatabase
            .rawQuery("SELECT id,kind,payload,created_at FROM pending", null)
            .use { cursor ->
                while (cursor.moveToNext()) beforePending += Array(4) { cursor.getString(it) }
            }
        val activation = File(context.noBackupFilesDir, "full-restore/activation.json")
        val beforeActivation = activation.takeIf(File::isFile)?.readBytes()
        val controller = NativeAppController(requireNotNull(activity) as MainActivity, store)
        val customer = customers(fixturePrefix + "000").single().getString("id")
        var timerSession = ""
        try {
            val args =
                JSONObject()
                    .put("_request_id", "qa_background_confirmed_work")
                    .put("customer_id", customer)
                    .put("activity", "[TEST] QA bestätigte Hintergrundprüfung")
                    .put("work_date", LocalDate.now(ZoneId.of("Europe/Vienna")).toString())
                    .put("hours", 1)
                    .put("test_record", true)
            val saved = runBlocking { controller.saveEditor("worklog", args) }
            val before = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
            val count =
                PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length()
            controller.saveDraft(
                "editor:worklog:new",
                JSONObject()
                    .put("values", JSONObject(args.toString()))
                    .put("original_args", JSONObject(args.toString())),
            )
            runBlocking { controller.flushDrafts() }
            click(UiTags.area("today"))
            newWork()
            waitUntil("BACKGROUND_RECEIPT_ACKNOWLEDGED") {
                workDraft().optJSONObject("original_args") == null &&
                    store
                        .ownerValue(
                            "native_form_draft:" + PixelMcpWriteTools.hash("editor:worklog:new"),
                            "",
                        )
                        .isEmpty()
            }
            click(UiTags.SNACKBAR_VIEW, scrollTo = false)
            shown(UiTags.entityDetail("worklogs", saved))
            yes(
                PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) == before,
                "BACKGROUND_CONFIRMATION_ONLY_READS_BUSINESS_DATA",
            )
            yes(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() ==
                    count,
                "BACKGROUND_CONFIRMATION_NEVER_REPEATS_SAVE",
            )
            recreate()
            yes(
                store
                    .ownerValue(
                        "native_form_draft:" + PixelMcpWriteTools.hash("editor:worklog:new"),
                        "",
                    )
                    .isEmpty(),
                "BACKGROUND_ACK_SURVIVES_RECREATE",
            )

            // A retained pause receipt is acknowledged on Today; its writer is never repeated.
            timerSession =
                read(
                        "start_work",
                        JSONObject()
                            .put("request_id", "qa_feedback_timer_start")
                            .put("customer_id", customer)
                            .put("activity", "[TEST] QA Hintergrundzeit"),
                    )
                    .getJSONObject("session")
                    .getString("id")
            val pauseArgs =
                JSONObject()
                    .put("request_id", "qa_feedback_timer_pause")
                    .put("session_id", timerSession)
            read("start_break", pauseArgs)
            val timerRevision = read("get_active_work_session").getString("revision_hash")
            fun timerEvents() =
                store.readableDatabase
                    .rawQuery(
                        "SELECT COUNT(*) FROM work_session_events WHERE session_id=?",
                        arrayOf(timerSession),
                    )
                    .use {
                        it.moveToFirst()
                        it.getInt(0)
                    }
            val eventCount = timerEvents()
            val timerKey = "native_form_draft:" + PixelMcpWriteTools.hash("timer_action")
            controller.saveDraft(
                "timer_action",
                JSONObject()
                    .put("original_tool", "start_break")
                    .put("original_args", JSONObject(pauseArgs.toString())),
            )
            runBlocking { controller.flushDrafts() }
            click(UiTags.area("today"))
            waitUntil("BACKGROUND_TIMER_RECEIPT_ACKNOWLEDGED") {
                store.ownerValue(timerKey, "").isEmpty()
            }
            click(UiTags.SNACKBAR_VIEW, scrollTo = false)
            shown(UiTags.PRIMARY)
            yes(
                read("get_active_work_session").getString("revision_hash") == timerRevision &&
                    timerEvents() == eventCount,
                "BACKGROUND_TIMER_ACK_NEVER_REPEATS_PAUSE",
            )
            val unknownTimer =
                JSONObject()
                    .put("request_id", "qa_feedback_unknown_timer_resume")
                    .put("session_id", timerSession)
            controller.saveDraft(
                "timer_action",
                JSONObject()
                    .put("original_tool", "end_break")
                    .put("original_args", JSONObject(unknownTimer.toString())),
            )
            runBlocking { controller.flushDrafts() }
            recreate()
            click(UiTags.area("today"))
            reveal(UiTags.inspection("waiting"))
            waitStable("BACKGROUND_UNKNOWN_TIMER_ONLY_READS", stableMs = 3500) {
                read("get_active_work_session").getString("revision_hash") == timerRevision &&
                    timerEvents() == eventCount &&
                    JSONObject(store.ownerValue(timerKey, "{}"))
                        .optJSONObject("original_args")
                        ?.optString("request_id") == unknownTimer.getString("request_id")
            }
            click(UiTags.area("customers"))
            shown(UiTags.CREATE)
            yes(
                read("get_active_work_session").optString("state") == "PAUSED",
                "BACKGROUND_UNKNOWN_TIMER_LEAVES_NORMAL_NAVIGATION_USABLE",
            )
            runBlocking { controller.removeDraft("timer_action") }
            read(
                "discard_work_session",
                JSONObject()
                    .put("request_id", "qa_feedback_timer_cleanup")
                    .put("session_id", timerSession),
            )

            // A lost local acknowledgement keeps the exact deletion, then resolves by receipt only.
            val preservedWorks =
                PixelMcpWriteTools.hash(
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
                )
            val deletionWork = runBlocking {
                controller.saveEditor(
                    "worklog",
                    JSONObject(args.toString())
                        .put("_request_id", "qa_background_deleted_work")
                        .put("activity", "[TEST] QA automatische Löschbestätigung"),
                )
            }
            val deleteInitial = JSONObject().put("work_record_ids", JSONArray().put(deletionWork))
            val deleteDraftKey =
                "native_form_draft:" +
                    PixelMcpWriteTools.hash(
                        "delete:delete_work_records:" + PixelMcpWriteTools.hash(deleteInitial)
                    )
            openWorklogs()
            reveal(UiTags.recordChoice("worklogs", deletionWork))
            waitUntil("BACKGROUND_DELETE_SELECT_OWN_WORK") {
                var target = node(UiTags.recordChoice("worklogs", deletionWork))
                while (
                    target != null &&
                        target.actionList.none { it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK }
                ) target = target.parent
                target?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true
            }
            shown(UiTags.DELETE_SELECTION)
            store.writableDatabase.execSQL(
                "CREATE TRIGGER qa_background_delete_ack BEFORE INSERT ON owner_state WHEN NEW.key='$deleteDraftKey' AND NEW.value='' BEGIN SELECT RAISE(ABORT,'QA deletion acknowledgement failure'); END"
            )
            click(UiTags.DELETE_SELECTION)
            waitUntil("BACKGROUND_DELETE_RETAINS_ORIGINAL_AFTER_ACK_FAILURE") {
                store.ownerValue(deleteDraftKey, "").isNotBlank() &&
                    (node(UiTags.inspection("checking")) != null ||
                        node(UiTags.inspection("waiting")) != null)
            }
            val originalDeletion =
                JSONObject(store.ownerValue(deleteDraftKey, "{}")).getJSONObject("original_args")
            store.writableDatabase.execSQL("DROP TRIGGER qa_background_delete_ack")
            waitUntil("BACKGROUND_DELETE_AUTOMATIC_RECEIPT_ACKNOWLEDGED") {
                store.ownerValue(deleteDraftKey, "").isEmpty() &&
                    node(UiTags.DELETE_SELECTION) == null &&
                    SettlementRules.rows(
                            PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
                        )
                        .none { it.optString("id") == deletionWork }
            }
            // The receipt commits before the runner compacts its journal. Finish this
            // independent deletion before taking the baseline for the restore notice.
            waitUntil("BACKGROUND_DELETE_STORAGE_CLEANUP_FINISHED") {
                SettlementRules.rows(read("get_list_actions").getJSONArray("actions"))
                    .singleOrNull {
                        it.optString("original_tool") == "delete_work_records" &&
                            ("worklog" to deletionWork) in ListActionTools.targets(it)
                    }
                    ?.optString("state") == "done"
            }
            val deletionProof =
                read(
                    "inspect_edit_action",
                    JSONObject()
                        .put("original_tool", "delete_work_records")
                        .put("original_args", JSONObject(originalDeletion.toString())),
                )
            yes(
                deletionProof.optString("state") in setOf("confirmed", "content_removed") &&
                    !deletionProof.optBoolean("business_write_performed"),
                "BACKGROUND_DELETE_READS_ONLY_THE_EXACT_ORIGINAL_RECEIPT",
            )
            yes(
                PixelMcpWriteTools.hash(
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
                ) == preservedWorks,
                "BACKGROUND_DELETE_PRESERVES_ALL_OTHER_WORK",
            )
            recreate()
            yes(
                store.ownerValue(deleteDraftKey, "").isEmpty() &&
                    node(UiTags.DELETE_SELECTION) == null,
                "BACKGROUND_DELETE_ACK_SURVIVES_RECREATE",
            )
            val beforeRestore = PixelMcpReadTools.effective(store.getState())

            // A completed synthetic restore must leave all four normal areas usable.
            activation.parentFile!!.mkdirs()
            activation.writeText(
                JSONObject()
                    .put("formatVersion", 1)
                    .put("restore_id", "qa_feedback_activated_restore")
                    .put("phase", "ACTIVE")
                    .put("preview_hash", "a".repeat(64))
                    .toString()
            )
            recreate()
            shown(UiTags.RESTORE_BANNER)
            click(UiTags.area("planning"))
            shown(UiTags.planningView("day"))
            click(UiTags.area("customers"))
            shown(UiTags.CREATE)
            click(UiTags.RESTORE_DISMISS)
            waitUntil("RESTORE_HINT_DISMISSED") { node(UiTags.RESTORE_BANNER) == null }
            recreate()
            yes(node(UiTags.RESTORE_BANNER) == null, "RESTORE_HINT_ACK_IS_DURABLE")
            val afterRestore = PixelMcpReadTools.effective(store.getState())
            val changedRestoreKeys =
                (beforeRestore.keys().asSequence().toSet() +
                        afterRestore.keys().asSequence().toSet())
                    .filter {
                        PixelMcpWriteTools.hash(beforeRestore.opt(it)) !=
                            PixelMcpWriteTools.hash(afterRestore.opt(it))
                    }
                    .sorted()
                    .joinToString(",")
            yes(
                PixelMcpWriteTools.hash(afterRestore) == PixelMcpWriteTools.hash(beforeRestore),
                "RESTORE_HINT_DOES_NOT_CHANGE_BUSINESS_DATA:$changedRestoreKeys",
            )

            // Empty customer list: first creation action is reachable from the ordinary plus.
            val empty = JSONObject(beforeMaster.toString())
            for (collection in
                listOf(
                    "customers",
                    "orders",
                    "worklogs",
                    "offers",
                    "invoices",
                    "photos",
                    "appointments",
                )) empty.put(collection, JSONArray())
            val emptyDb = store.writableDatabase
            emptyDb.beginTransaction()
            try {
                emptyDb.delete("pending", null, null)
                store.putOwnerValue("snapshot", empty.toString())
                emptyDb.setTransactionSuccessful()
            } finally {
                emptyDb.endTransaction()
            }
            store.notifyCommittedChange()
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            launch()
            click(UiTags.area("customers"))
            shown(UiTags.EMPTY_STATE)
            click(UiTags.CREATE)
            click(UiTags.createChoice("customer"))
            shown(UiTags.PRIMARY)
            yes(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("customers").length() ==
                    0,
                "EMPTY_FIRST_ACTION_OPENS_FORM_WITHOUT_SAVING",
            )
            click(UiTags.area("accounting"))
            selectTab(UiTags.billingTab(0))
            shown(UiTags.EMPTY_ACTION)
            click(UiTags.EMPTY_ACTION)
            shown(UiTags.PRIMARY)
            yes(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() ==
                    0,
                "EMPTY_ACCOUNTING_FIRST_ACTION_OPENS_WORK_FORM_WITHOUT_SAVING",
            )
        } finally {
            store.writableDatabase.execSQL("DROP TRIGGER IF EXISTS qa_background_delete_ack")
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            val db = store.writableDatabase
            db.beginTransaction()
            try {
                if (timerSession.isNotBlank()) {
                    db.delete("work_session_events", "session_id=?", arrayOf(timerSession))
                    db.delete("work_sessions", "id=?", arrayOf(timerSession))
                }
                db.delete("pending", null, null)
                for (row in beforePending) db.execSQL(
                    "INSERT INTO pending(id,kind,payload,created_at) VALUES(?,?,?,?)",
                    row,
                )
                db.delete("owner_state", null, null)
                for ((key, value) in beforeOwners) db.execSQL(
                    "INSERT INTO owner_state(key,value) VALUES(?,?)",
                    arrayOf(key, value),
                )
                store.putOwnerValue("snapshot", beforeMaster.toString())
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            if (beforeActivation == null) {
                activation.delete()
                activation.parentFile?.takeIf { it.listFiles()?.isEmpty() == true }?.delete()
            } else activation.writeBytes(beforeActivation)
            store.notifyCommittedChange()
            launch()
        }
    }

    private fun groupedSearchJourney() {
        val name = "[TEST] QA Gruppensuche Kunde"
        val source = JSONObject(store.getState().getJSONObject("master").toString())
        source
            .getJSONArray("customers")
            .put(
                JSONObject()
                    .put("id", "qa_group_customer")
                    .put("name", name)
                    .put("phone", "+43 000 0000002")
                    .put("street", "QA Gruppenweg 1")
                    .put("postal_code", "0000")
                    .put("city", "QA Ort")
                    .put("active", 1)
                    .put("billing_mode", "direct")
                    .put("test_record", true)
            )
        store.putOwnerValue("snapshot", source.toString())
        store.putOwnerValue(
            SettlementRules.KEY,
            JSONObject()
                .put(
                    "records",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("id", "qa_group_settlement")
                                .put("kind", "labor")
                                .put("status", "confirmed")
                                .put("title", "[TEST] QA Gruppensuche Abschluss")
                                .put("issue_date", java.time.LocalDate.now().toString())
                                .put("recipient", JSONObject().put("name", name))
                        ),
                )
                .toString(),
        )
        store.notifyCommittedChange()
        activity?.let { current -> test.runOnMainSync { current.finish() } }
        launch()
        click(UiTags.area("today"))
        click(UiTags.TODAY_SEARCH)
        val label = "Name, Objekt, Tätigkeit, Notiz oder Beleg"
        enter(label, "Gruppensuche")
        yes(
            requireNotNull(field(label))
                .performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id),
            "GROUPED_SEARCH_SUBMIT",
        )
        if (keyboardVisible()) back()
        waitUntil("GROUPED_SEARCH_CUSTOMER_GROUP") { node(UiTags.searchGroup("customer")) != null }
        fun top(tag: String) = node(tag)?.let { Rect().also(it::getBoundsInScreen).top }
        // Kinds are ordered objects, customers, settlements, work: settlements never precede
        // customers.
        val customerTop = requireNotNull(top(UiTags.searchGroup("customer")))
        yes(
            top(UiTags.searchGroup("settlement"))?.let { it > customerTop } ?: true,
            "GROUPED_SEARCH_CUSTOMERS_BEFORE_SETTLEMENTS",
        )
        shown(UiTags.SEARCH_CALL)
        shown(UiTags.SEARCH_ROUTE)
        reveal(UiTags.searchGroup("settlement"))
        click(name)
        shown(UiTags.BACK)
        click(UiTags.BACK)
        enter(label, "")
        waitUntil("GROUPED_SEARCH_RECENT") { node(UiTags.SEARCH_RECENT) != null }
        val recent =
            JSONArray(context.getSharedPreferences("native_search", 0).getString("recent", "[]"))
        yes(
            recent.length() >= 1 && recent.getJSONObject(0).getString("id") == "qa_group_customer",
            "GROUPED_SEARCH_REMEMBERS_OPENED_HIT",
        )
    }

    private fun domainSearchJourney() {
        seedCustomerContext()
        seedEquipment()
        seedNotification()
        seedWork("[TEST] QA domain search work")
        seedSearchCatalog()
        val source = JSONObject(store.getState().getJSONObject("master").toString())
        val duplicateName = "[TEST] QA Gleichnamig mit sehr langem Gartenkundenname"
        val allCustomers = source.getJSONArray("customers")
        for ((index, city) in listOf("QA TestNord", "QA TestSüd").withIndex()) allCustomers.put(
            JSONObject()
                .put("id", "qa_duplicate_$index")
                .put("name", duplicateName)
                .put("city", city)
                .put("active", 1)
                .put("billing_mode", "direct")
                .put("test_record", true)
        )
        store.putOwnerValue("snapshot", source.toString())
        store.notifyCommittedChange()
        val failures = ArrayList<Throwable>()
        fun case(key: String, body: () -> Unit) {
            try {
                activity?.let { current -> test.runOnMainSync { current.finish() } }
                launch()
                body()
            } catch (failure: Throwable) {
                runCatching { captureSyntheticScreen("failure-domain-$key") }
                failures.add(AssertionError("DOMAIN_$key", failure))
            }
        }
        case("work") {
            val rows = PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            val work =
                (0 until rows.length())
                    .map { rows.getJSONObject(it) }
                    .first { it.optString("activity").startsWith("[TEST] QA domain search work") }
            openWorklogs()
            keyboardSearchProof(
                "work",
                "Suchen",
                "QA domain search work",
                work.getString("activity"),
            )
        }
        case("offers") {
            openCustomerList()
            enter("Suchen", fixturePrefix + "000")
            click(fixturePrefix + "000")
            click("Angebote")
            keyboardSearchProof("offers", "Suchen", "QA Kontextangebot 0", "QA Kontextangebot 0")
            visibleResult("Zur Liste")
            captureSyntheticScreen("detail-offers")
            click("Zur Liste")
            waitUntil("DOMAIN_RETURN_QUERY_offers") {
                field("Suchen")?.text?.toString()?.contains("QA Kontextangebot 0") == true
            }
        }
        case("planning") {
            openOffice()
            click(UiTags.area("planning"))
            chooseDate("Datum *", LocalDate.now(ZoneId.of("Europe/Vienna")).plusDays(1).toString())
            keyboardSearchProof(
                "planning",
                "Suche",
                "QA Native Notification",
                "[TEST] QA Native Notification",
            )
        }
        case("equipment") {
            openOffice()
            click("Geräte")
            keyboardSearchProof("equipment", "Suchen", "QA Zubehör Säge", "[TEST] QA Zubehör Säge")
        }
        case("photos") {
            openOfficeEntry("Fotos")
            keyboardSearchProof("photos", "Fotos suchen", "QA Kontextfoto 0", "QA Kontextfoto 0")
        }
        case("catalog") {
            openOfficeEntry("Leistungskatalog")
            keyboardSearchProof(
                "catalog",
                "Bausteine suchen",
                "QA Leistungsbaustein",
                "QA Leistungsbaustein",
            )
            visibleResult("Zur Liste")
            captureSyntheticScreen("detail-catalog")
            click("Zur Liste")
            waitUntil("DOMAIN_RETURN_QUERY_catalog") {
                field("Bausteine suchen")?.text?.toString()?.contains("QA Leistungsbaustein") ==
                    true
            }
        }
        case("local") {
            openOfficeEntry("Suche")
            keyboardSearchProof(
                "local",
                "Name, Objekt, Tätigkeit, Notiz oder Beleg",
                fixturePrefix + "000",
                fixturePrefix + "000",
                manual = true,
            )
        }
        case("phrases") {
            newWork()
            click("Textbausteine ▾")
            keyboardSearchProof("phrases", "Vorschläge durchsuchen", "Rasen gemäht", "Rasen gemäht")
            reveal("Was wurde erledigt? *")
            waitUntil("PHRASE_TRANSFERRED") {
                field("Was wurde erledigt? *")?.text?.toString()?.contains("Rasen gemäht") == true
            }
            discardWork()
        }
        case("duplicate-customers") {
            newWork()
            openWorkCustomerPicker()
            enter("Suchen", "QA Gleichnamig")
            shown("2 Treffer")
            reveal("QA TestNord")
            reveal("QA TestSüd")
            enter("Suchen", "QA TestSüd")
            showKeyboard("Suchen")
            visibleResult(duplicateName, requireKeyboard = true)
            captureSyntheticScreen("domain-duplicate-customers", requireKeyboard = true)
            visibleResult(duplicateName, tap = true, requireKeyboard = true)
            waitUntil("DUPLICATE_PICKER_CLOSED") { !keyboardVisible() && field("Suchen") == null }
            reveal("Kunde: $duplicateName · QA TestSüd")
            domainSearchCases.put(
                JSONObject()
                    .put("case", "duplicate-customers")
                    .put("keyboard_bounds", true)
                    .put("real_touch", true)
                    .put("address_distinguishes", true)
            )
            discardWork()
        }
        case("equipment-picker") {
            openOffice()
            click(UiTags.area("planning"))
            click(UiTags.FILTER)
            click(UiTags.PLANNING_ALL_VISITS)
            enter("Suchen", "QA Kontexteinsatz 0")
            click("QA Kontexteinsatz 0")
            click("Bearbeiten")
            click("Benötigte Geräte ergänzen")
            click("Benötigte Geräte: Nicht zugeordnet")
            keyboardSearchProof(
                "equipment-picker",
                "Suchen",
                "QA Zubehör Säge",
                "[TEST] QA Zubehör Säge",
            )
            reveal("Benötigte Geräte: [TEST] QA Zubehör Säge")
            discardWork()
        }
        case("planning-picker") {
            openOffice()
            click(UiTags.area("planning"))
            click(UiTags.PRIMARY)
            click("Kunde: Bitte wählen")
            keyboardSearchProof(
                "planning-picker",
                "Suchen",
                fixturePrefix + "000",
                fixturePrefix + "000",
            )
            reveal("Kunde: " + fixturePrefix + "000")
            click("Ungespeicherten Entwurf verwerfen")
        }
        case("photo-assignment") {
            openOfficeEntry("Fotos")
            click("Foto hinzufügen")
            click("Kunden auswählen")
            keyboardSearchProof(
                "photo-assignment",
                "Suchen",
                fixturePrefix + "001",
                fixturePrefix + "001",
            )
            shown(fixturePrefix + "001")
            click("Verwerfen")
        }
        case("offer-positions") {
            openCustomerList()
            enter("Suchen", fixturePrefix + "000")
            click(fixturePrefix + "000")
            click("Angebote")
            click("Angebot anlegen")
            click("Weiter zu Leistungen")
            click("Aus Leistungskatalog hinzufügen")
            keyboardSearchProof(
                "offer-positions",
                "Bausteine suchen",
                "QA Leistungsbaustein",
                "QA Leistungsbaustein · 7,50 €",
            )
            revealContaining("1. QA Leistungsbaustein")
            discardWork()
        }
        case("maintenance-phrases") {
            val action =
                JSONObject(
                        context.assets
                            .open("equipment-maintenance-texts.json")
                            .bufferedReader()
                            .use { it.readText() }
                    )
                    .getJSONArray("actions")
                    .getJSONObject(0)
            openOffice()
            click("Geräte")
            enter("Suchen", "QA Zubehör Säge")
            click("[TEST] QA Zubehör Säge")
            click("Wartung einrichten")
            click("Tätigkeitstext auswählen")
            keyboardSearchProof(
                "maintenance-phrases",
                "Text suchen",
                action.getString("label"),
                action.getString("label"),
            )
            waitUntil("MAINTENANCE_TEXT_TRANSFERRED") {
                field("Wartungstätigkeit *")
                    ?.text
                    ?.toString()
                    ?.contains(action.getString("text")) == true
            }
            discardWork()
        }
        activity?.let { current -> test.runOnMainSync { current.finish() } }
        launch()
        if (failures.isNotEmpty()) {
            val failure = AssertionError("DOMAIN_SEARCH_${failures.size}_FAILED")
            failures.forEach(failure::addSuppressed)
            throw failure
        }
        yes(domainSearchCases.length() == 14, "ALL_DOMAIN_SEARCH_PATHS")
    }

    private fun listViewportJourney() {
        seedCustomerContext()
        seedEquipment()
        seedNotification()
        seedSearchCatalog()
        val oldFont = shell("settings get system font_scale")
        val sizeText = shell("wm size")
        val override = Regex("Override size: (\\d+x\\d+)").find(sizeText)?.groupValues?.get(1)
        val physical =
            Regex("(?:Override|Physical) size: (\\d+)x(\\d+)").findAll(sizeText).last().groupValues
        val width = minOf(physical[1].toInt(), physical[2].toInt())
        val height = maxOf(physical[1].toInt(), physical[2].toInt())
        val work = "QA Kontextabrechnung 0"
        data class Target(
            val key: String,
            val label: String,
            val query: String,
            val result: String,
            val manual: Boolean = false,
            val open: () -> Unit,
        )
        val targets =
            listOf(
                Target("customers", "Suchen", fixturePrefix + "000", fixturePrefix + "000") {
                    click(UiTags.area("customers"))
                },
                Target("work", "Suchen", work, work) { openWorklogs() },
                Target("orders", "Suchen", "QA Kontextauftrag 0", "QA Kontextauftrag 0") {
                    openOfficeEntry("Aufträge")
                },
                Target("offers", "Suchen", "QA Kontextangebot 0", "QA Kontextangebot 0") {
                    openOffice()
                    click("Angebote")
                },
                Target(
                    "planning",
                    "Suche",
                    "QA Native Notification",
                    "[TEST] QA Native Notification",
                ) {
                    openOffice()
                    click(UiTags.area("planning"))
                    chooseDate(
                        "Datum *",
                        LocalDate.now(ZoneId.of("Europe/Vienna")).plusDays(1).toString(),
                    )
                },
                Target("equipment", "Suchen", "QA Zubehör Säge", "[TEST] QA Zubehör Säge") {
                    openOffice()
                    click("Geräte")
                },
                // The fixture is newest-first: photo 1 is the first of these two results.
                // Customer A/B exclusion is verified separately in customerContextJourney.
                Target("photos", "Fotos suchen", "QA Kontextfoto", "QA Kontextfoto 1") {
                    openOfficeEntry("Fotos")
                },
                Target(
                    "catalog",
                    "Bausteine suchen",
                    "QA Leistungsbaustein",
                    "QA Leistungsbaustein",
                ) {
                    openOfficeEntry("Leistungskatalog")
                },
                Target(
                    "local",
                    "Name, Objekt, Tätigkeit, Notiz oder Beleg",
                    fixturePrefix + "000",
                    fixturePrefix + "000",
                    true,
                ) {
                    openOfficeEntry("Suche")
                },
            )
        val failures = ArrayList<Throwable>()
        try {
            for (landscape in listOf(false, true)) for (target in targets) {
                if (
                    landscape &&
                        target.key !in setOf("customers", "work", "offers", "catalog", "local")
                )
                    continue
                val key = target.key + if (landscape) "-landscape" else "-portrait"
                test.sendStatus(0, Bundle().apply { putString("list_viewport_start", key) })
                try {
                    activity?.let { current -> test.runOnMainSync { current.finish() } }
                    shell("wm size ${width}x${(height*0.82).toInt()}")
                    shell("settings put system font_scale 2.0")
                    automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
                    launch()
                    test.runOnMainSync {
                        requireNotNull(activity).requestedOrientation =
                            if (landscape)
                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    waitUntil("LIST_CONFIGURATION_$key") {
                        val config = requireNotNull(activity).resources.configuration
                        kotlin.math.abs(config.fontScale - 2f) < 0.02f &&
                            (config.orientation ==
                                android.content.res.Configuration.ORIENTATION_LANDSCAPE) ==
                                landscape
                    }
                    target.open()
                    enter(target.label, target.query)
                    if (target.key == "offers") {
                        // Unscoped viewport setup uses the real reset action. Separate
                        // customer-context journeys continue to verify persisted filters.
                        click("Filter und Suche zurücksetzen")
                        enter(target.label, target.query)
                    }
                    if (target.manual) {
                        showKeyboard(target.label)
                        yes(
                            requireNotNull(field(target.label))
                                .performAction(
                                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
                                ),
                            "LIST_MANUAL_SEARCH_$key",
                        )
                    }
                    showKeyboard(target.label)
                    visibleResult(target.result, requireKeyboard = true, allowClippedCard = true)
                    waitUntil("LIST_FIELD_AND_COUNT_$key") {
                        val screen =
                            Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                        val ime =
                            automation.windows.firstOrNull {
                                it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                                    it.root?.isVisibleToUser == true
                            } ?: return@waitUntil false
                        screen.bottom =
                            minOf(screen.bottom, Rect().also { ime.getBoundsInScreen(it) }.top)
                        val count =
                            nodes().firstOrNull {
                                it.text?.toString()?.matches(Regex("[0-9]+ Treffer.*")) == true
                            }
                        listOf(field(target.label), count).all { candidate ->
                            candidate != null &&
                                Rect()
                                    .also { candidate.getBoundsInScreen(it) }
                                    .let { !it.isEmpty && screen.contains(it) }
                        }
                    }
                    captureSyntheticScreen("list-$key", requireKeyboard = true)
                    yes(
                        requireNotNull(field(target.label))
                            .performAction(
                                AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
                            ),
                        "LIST_DONE_$key",
                    )
                    waitUntil("LIST_DONE_PRESERVES_QUERY_$key") {
                        !keyboardVisible() &&
                            field(target.label)?.text?.toString()?.contains(target.query) == true
                    }
                    visibleResult(target.result, scrollIntoView = true, allowClippedCard = true)
                    showKeyboard(target.label)
                    visibleResult(
                        target.result,
                        tap = true,
                        requireKeyboard = true,
                        allowClippedCard = true,
                    )
                    waitUntil("LIST_TOUCH_OPENED_$key") {
                        !keyboardVisible() && field(target.label) == null
                    }
                    listViewportCases.put(
                        JSONObject()
                            .put("case", key)
                            .put("font", 2.0)
                            .put("compact", true)
                            .put("landscape", landscape)
                            .put("keyboard_bounds", true)
                            .put("real_touch", true)
                            .put("ime_preserves_query", true)
                    )
                } catch (failure: Throwable) {
                    test.sendStatus(
                        0,
                        Bundle().apply {
                            putString(
                                "list_viewport_failure",
                                "$key: " + failure.stackTraceToString(),
                            )
                        },
                    )
                    if (target.key == "offers")
                        runCatching {
                            val backend = runBlocking {
                                store
                                    .nativeData()
                                    .list("offers", search = target.query, includeInactive = false)
                            }
                            test.sendStatus(
                                0,
                                Bundle().apply {
                                    putInt("offer_viewport_backend_matches", backend.total)
                                },
                            )
                        }
                    runCatching { captureSyntheticScreen("failure-list-$key") }
                    failures.add(AssertionError("LIST_VIEWPORT_$key", failure))
                }
            }
        } finally {
            activity?.let { current -> test.runOnMainSync { current.finish() } }
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            shell("wm size " + (override ?: "reset"))
            shell(
                if (oldFont == "null") "settings delete system font_scale"
                else "settings put system font_scale $oldFont"
            )
            launch()
        }
        if (failures.isNotEmpty()) {
            val failure = AssertionError("LIST_VIEWPORTS_${failures.size}_FAILED")
            failures.forEach(failure::addSuppressed)
            throw failure
        }
        yes(listViewportCases.length() == 14, "ALL_LIST_VIEWPORTS")
    }

    private fun filterKeyboardJourney() {
        fun edit(label: String, value: String, key: String) {
            enter(label, "")
            showKeyboard(label)
            waitUntil("FILTER_VISIBLE_ABOVE_IME_$key") {
                val candidate = field(label) ?: return@waitUntil false
                val bounds = Rect().also { candidate.getBoundsInScreen(it) }
                val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                val ime =
                    automation.windows.firstOrNull {
                        it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
                            it.root?.isVisibleToUser == true
                    } ?: return@waitUntil false
                screen.bottom = minOf(screen.bottom, Rect().also { ime.getBoundsInScreen(it) }.top)
                screen.contains(bounds) &&
                    bounds.height() >= (48 * context.resources.displayMetrics.density).toInt()
            }
            enter(label, value)
            captureSyntheticScreen("filter-$key", requireKeyboard = true)
            yes(
                requireNotNull(field(label))
                    .performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id),
                "FILTER_DONE_$key",
            )
            waitUntil("FILTER_DONE_PRESERVES_VALUE_$key") {
                !keyboardVisible() && field(label)?.text?.toString()?.contains(value) == true
            }
        }
        activity?.let { current -> test.runOnMainSync { current.finish() } }
        launch()
        openWorklogs()
        click(UiTags.FILTER)
        chooseDate("Ab Datum", "2020-01-01")
        revealContaining("1. Jan. 2020")
        click("Ergebnisse anzeigen")
        showKeyboard("Suchen")
        waitUntil("WORK_SEARCH_CLOSES_FILTERS") { node("Ab Datum") == null }
        back()
        waitUntil("WORK_FILTER_IME_CLOSED") { !keyboardVisible() }
        recreate()
        click(UiTags.FILTER)
        revealContaining("1. Jan. 2020")
        yes(field("Ab Datum") == null, "WORK_FILTER_RECREATION_RETAINS_NATIVE_PICKER")
        chooseDate("Ab Datum", "2027-05-19", confirm = false)
        revealContaining("1. Jan. 2020")
        // The local full-text filter remains a text filter and retains the original IME checks.
        activity?.let { current -> test.runOnMainSync { current.finish() } }
        launch()
        openOfficeEntry("Suche")
        click(UiTags.FILTER)
        edit("Zeitraum ab · JJJJ-MM-TT", "2020-01-01", "local-date")
        showKeyboard("Name, Objekt, Tätigkeit, Notiz oder Beleg")
        waitUntil("LOCAL_SEARCH_CLOSES_FILTERS") { field("Zeitraum ab · JJJJ-MM-TT") == null }
        back()
        waitUntil("LOCAL_FILTER_IME_CLOSED") { !keyboardVisible() }
        click(UiTags.FILTER)
        reveal("Zeitraum ab · JJJJ-MM-TT")
        yes(
            field("Zeitraum ab · JJJJ-MM-TT")?.text?.toString()?.contains("2020-01-01") == true,
            "LOCAL_FILTER_VALUE_RETAINED",
        )
    }

    /** Read-only route acceptance complements the mutating domain journeys above. */
    private fun surfaceJourney() {
        seedEquipment()
        val failures = ArrayList<Throwable>()
        fun case(key: String, ready: String, screenshot: Boolean = false, body: () -> Unit) {
            test.sendStatus(0, Bundle().apply { putString("surface_start", key) })
            try {
                activity?.let { current -> test.runOnMainSync { current.finish() } }
                launch()
                body()
                reveal(ready)
                yes(!keyboardVisible(), "SURFACE_NO_UNREQUESTED_IME_$key")
                // Readable labels and actual actionable bounds are separate checks.
                val target = node(ready)?.let(::clickable)
                if (target != null) visibleResult(ready, scrollIntoView = true)
                if (screenshot) captureSyntheticScreen("surface-$key")
                surfaceCases.put(
                    JSONObject().put("surface", key).put("ready", ready).put("no_auto_ime", true)
                )
            } catch (failure: Throwable) {
                test.sendStatus(
                    0,
                    Bundle().apply {
                        putString("surface_failure", "$key: " + failure.stackTraceToString())
                    },
                )
                runCatching { captureSyntheticScreen("failure-surface-$key") }
                failures.add(AssertionError("SURFACE_$key", failure))
            }
        }
        fun settings() {
            openOffice()
            click("Einstellungen und Daten")
            waitUntil("SETTINGS_ACTION_READY") {
                node("Tätigkeit, Einsatzort und Fotoqualität")?.let(::clickable) != null
            }
        }
        fun planning() {
            openOffice()
            click(UiTags.area("planning"))
        }
        fun billing() {
            openOffice()
            click("Offene Stunden")
        }
        case("today", "Arbeit starten", true) {}
        case("work", "Arbeit nachtragen") { openWorklogs() }
        case("timer", "Kunde *: Nicht zugeordnet") { click("Arbeit starten") }
        case("customers", "Neu anlegen") { click(UiTags.area("customers")) }
        case("office", "Organisieren", true) {
            click(UiTags.area("today"))
            click(UiTags.MORE)
        }
        case("properties", "Objekt anlegen") { openOfficeEntry("Objekte") }
        case("inquiries", "Anfrage anlegen") { openOfficeEntry("Anfragen") }
        case("orders", "Auftrag anlegen") { openOfficeEntry("Aufträge") }
        case("care-plans", "Pflegeplan anlegen") { openOfficeEntry("Pflegepläne") }
        case("planning", UiTags.PRIMARY, true) { planning() }
        case("visits", "Einsatz anlegen") {
            planning()
            click(UiTags.FILTER)
            click(UiTags.PLANNING_ALL_VISITS)
        }
        case("tour", "Datum öffnen") {
            planning()
            click(UiTags.PLANNING_TOUR)
        }
        case("weather", UiTags.PLANNING_WEATHER_DETAILS) {
            planning()
            click(UiTags.FILTER)
            click(UiTags.PLANNING_WEATHER_DETAILS)
        }
        case("billing", "Arbeiten prüfen", true) { billing() }
        case("offers", "Angebot anlegen") {
            openOffice()
            click("Angebote")
        }
        case("equipment", "Gerät anlegen") {
            openOffice()
            click("Geräte")
        }
        case("accessories", "Zubehör bearbeiten", true) {
            openOffice()
            click("Geräte")
            enter("Suchen", "QA Zubehör Säge")
            click("[TEST] QA Zubehör Säge")
            click("Zubehör")
        }
        case("maintenance", "Wartung einrichten") {
            openOffice()
            click("Geräte")
            enter("Suchen", "QA Zubehör Säge")
            click("[TEST] QA Zubehör Säge")
        }
        case("materials", "Material anlegen") { openOfficeEntry("Material") }
        case("photos", "Foto hinzufügen") { openOfficeEntry("Fotos") }
        case("catalog", "Baustein anlegen") { openOfficeEntry("Leistungskatalog") }
        case("local-search", "Suchtext eingeben") { openOfficeEntry("Suche") }
        case("archive", "Alle Arten") { openOfficeEntry("Archiv") }
        case("trash", "Papierkorb leeren") { openOfficeEntry("Papierkorb") }
        case("settings", "Tätigkeit, Einsatzort und Fotoqualität", true) { settings() }
        case("connections", "Optionale Verbindungen") {
            settings()
            click("Verbindungen")
        }
        case("calendar", "Verbinden", true) {
            settings()
            click("Verbindungen")
            click("Google Kalender")
        }
        // FLAG_SECURE remains enabled; do not weaken credential screens for screenshots.
        case("chatgpt", "Einrichtung und Diagnose") {
            settings()
            click("Verbindungen")
            click("ChatGPT-Verbindung")
        }
        case("backup", "Jetzt sichern", true) {
            settings()
            click("Sicherung und Wiederherstellung")
        }
        val oldFont = shell("settings get system font_scale")
        try {
            shell("settings put system font_scale 2.0")
            case("settings-200", "Tätigkeit, Einsatzort und Fotoqualität", true) { settings() }
            case("calendar-200", "Verbinden", true) {
                settings()
                click("Verbindungen")
                click("Google Kalender")
            }
            case("backup-200", "Jetzt sichern", true) {
                settings()
                click("Sicherung und Wiederherstellung")
            }
        } finally {
            shell("settings put system font_scale $oldFont")
        }
        activity?.let { current -> test.runOnMainSync { current.finish() } }
        launch()
        if (failures.isNotEmpty()) {
            val failure = AssertionError("SURFACES_${failures.size}_FAILED")
            failures.forEach(failure::addSuppressed)
            throw failure
        }
        yes(surfaceCases.length() == 32, "ALL_SURFACES_REACHABLE")
    }

    private fun storageSettingsJourney() {
        val previousFont = shell("settings get system font_scale")
        val before = PixelMcpReadTools.effective(store.getState())
        fun retainedBusiness(value: JSONObject) =
            JSONObject().apply {
                for (key in
                    listOf(
                        "customers",
                        "orders",
                        "worklogs",
                        "invoices",
                        "offers",
                        "photos",
                        "appointments",
                    )) put(key, value.opt(key))
                // Purged tombstones are reclaimable metadata; archived business records are
                // retained.
                val archives = value.optJSONObject("owner_archive") ?: JSONObject()
                put(
                    "owner_archive",
                    JSONObject().apply {
                        for (key in archives.keys()) if (
                            !archives.getJSONObject(key).optBoolean("purged")
                        )
                            put(key, archives.get(key))
                    },
                )
            }
        try {
            for (scale in listOf(1.0, 2.0)) {
                activity?.let { current -> test.runOnMainSync { current.finish() } }
                shell("settings put system font_scale $scale")
                launch()
                openOffice()
                click("Einstellungen und Daten")
                click("Speicher")
                reveal("Speicher bereinigen")
                waitUntil("STORAGE_ACTION_READY_$scale") {
                    node("Speicher bereinigen")?.let(::clickable)?.isEnabled == true
                }
                val bounds = actionBounds("Speicher bereinigen")
                val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                yes(
                    screen.contains(bounds) && bounds.height() >= 48,
                    "STORAGE_ACTION_VISIBLE_$scale",
                )
                yes(!keyboardVisible(), "STORAGE_NO_UNNECESSARY_IME_$scale")
                captureSyntheticScreen("storage-${(scale*100).toInt()}")
                if (scale == 1.0) {
                    click("Speicher bereinigen")
                    waitUntil("STORAGE_CLEANUP_CONFIRMED") {
                        nodes().any { it.text?.toString()?.contains("Bereinigt · ") == true }
                    }
                    yes(
                        PixelMcpWriteTools.hash(retainedBusiness(before)) ==
                            PixelMcpWriteTools.hash(
                                retainedBusiness(PixelMcpReadTools.effective(store.getState()))
                            ),
                        "STORAGE_UI_PRESERVES_BUSINESS_DATA",
                    )
                    val notice =
                        nodes()
                            .mapNotNull { it.text?.toString() }
                            .first { it.contains("Bereinigt · ") }
                    reveal(notice)
                    captureSyntheticScreen("storage-cleaned")
                }
            }
        } finally {
            shell(
                if (previousFont == "null") "settings delete system font_scale"
                else "settings put system font_scale $previousFont"
            )
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
        shown(UiTags.PRIMARY)
        click(UiTags.FILTER)
        val check = visibleResult(UiTags.PLANNING_SYNC, scrollIntoView = true)
        val plan = visibleResult(UiTags.PRIMARY)
        yes(!Rect.intersects(plan, check), "LARGE_TEXT_ACTIONS_DO_NOT_OVERLAP")
        val frame = Rect()
        val safe = Rect()
        var minimumTouch = 0
        test.runOnMainSync {
            val decor = requireNotNull(activity).window.decorView
            decor.getWindowVisibleDisplayFrame(frame)
            val insets =
                requireNotNull(decor.rootWindowInsets).getInsets(WindowInsets.Type.systemBars())
            val location = IntArray(2)
            decor.getLocationOnScreen(location)
            safe.set(
                location[0] + insets.left,
                location[1] + insets.top,
                location[0] + decor.width - insets.right,
                location[1] + decor.height - insets.bottom,
            )
            minimumTouch = (48 * decor.resources.displayMetrics.density).toInt()
        }
        yes(
            plan.height() >= minimumTouch && check.height() >= minimumTouch,
            "LARGE_TEXT_TOUCH_TARGETS",
        )
        yes(
            plan.bottom <= frame.bottom && check.bottom <= frame.bottom,
            "ACTIONS_WITHIN_VISIBLE_FRAME",
        )
        yes(safe.contains(plan) && safe.contains(check), "ACTIONS_CLEAR_SYSTEM_BARS")
        captureSyntheticScreen("planning-large-font")
        val before =
            PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments").length()
        click(UiTags.PRIMARY)
        shown("Termin speichern")
        chooseDateTime("Geplanter Beginn *", "2027-05-19T09:00")
        val planningDraft =
            "native_form_draft:" + PixelMcpWriteTools.hash("planning:appointment:new")
        waitUntil("PLANNING_DRAFT_DURABLE") {
            store.ownerValue(planningDraft, "").contains("2027-05-19T09:00")
        }
        val committedDraft = store.ownerValue(planningDraft, "")
        chooseDateTime("Geplanter Beginn *", "2027-05-20T11:30", confirmTime = false)
        yes(
            store.ownerValue(planningDraft, "") == committedDraft,
            "CANCEL_DATETIME_TIME_STAGE_DOES_NOT_PARTIALLY_COMMIT_DATE",
        )
        recreate()
        reveal("Geplanter Beginn *")
        revealContaining("19. Mai 2027 · 09:00")
        yes(
            JSONObject(store.ownerValue(planningDraft, "{}"))
                .getJSONObject("values")
                .getString("local_start") == "2027-05-19T09:00",
            "PLANNING_DRAFT_AFTER_RECREATION",
        )
        yes(
            PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments").length() ==
                before,
            "DRAFT_IS_NOT_APPOINTMENT",
        )
        back()
        click(UiTags.PRIMARY)
        revealContaining("19. Mai 2027 · 09:00")
        yes(
            JSONObject(store.ownerValue(planningDraft, "{}"))
                .getJSONObject("values")
                .getString("local_start") == "2027-05-19T09:00",
            "PLANNING_RESUME_RETAINS_ORIGINAL_INPUT",
        )
        click("Ungespeicherten Entwurf verwerfen")
        waitStable("PLANNING_DISCARD_REMOVES_DRAFT", stableMs = 500) {
            store.ownerValue(planningDraft, "").isEmpty()
        }
        reveal(UiTags.PLANNING_TOUR)
        shown(UiTags.PRIMARY)
        yes(
            PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments").length() ==
                before,
            "PLANNING_DISCARD_DOES_NOT_CREATE_RECORD",
        )
        click(UiTags.area("today"))
        reveal("Einsätze heute")
        val workBefore =
            PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length()
        newWork()
        shown("Arbeit speichern")
        reveal("Datum *")
        openWorkCustomerPicker()
        enter("Suchen", fixturePrefix + "000")
        click(fixturePrefix + "000")
        chooseTime("Beginn", "08:00")
        chooseTime("Ende", "12:00")
        reveal("Pause (Minuten)")
        click("60")
        reveal("Freie Minuten")
        waitUntil("LARGE_TEXT_PAUSE_PRESET") { field("Freie Minuten")?.text?.toString() == "60" }
        checks++
        enter("Was wurde erledigt? *", "[TEST] QA große Schrift")
        click("Weitere Angaben")
        click("150 €")
        reveal("Freier Betrag (€)")
        waitUntil("LARGE_TEXT_DISPOSAL_PRESET") {
            field("Freier Betrag (€)")?.text?.toString() == "150"
        }
        checks++
        primaryPosition("Arbeit speichern", "Zurück")
        val saveWork = actionBounds(UiTags.PRIMARY)
        val returnWork = actionBounds("Zurück")
        yes(
            !Rect.intersects(saveWork, returnWork) &&
                safe.contains(saveWork) &&
                safe.contains(returnWork),
            "LARGE_TEXT_WORK_ACTIONS_CLEAR_SYSTEM_BARS",
        )
        yes(
            saveWork.height() >= minimumTouch && returnWork.height() >= minimumTouch,
            "LARGE_TEXT_WORK_TOUCH_TARGETS",
        )
        captureSyntheticScreen("work-large-font")
        discardWork()
        waitUntil("LARGE_TEXT_DISCARD_REMOVES_WORK_DRAFT") {
            store
                .ownerValue(
                    "native_form_draft:" + PixelMcpWriteTools.hash("editor:worklog:new"),
                    "",
                )
                .isEmpty()
        }
        yes(
            PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() ==
                workBefore,
            "LARGE_TEXT_DISCARD_DOES_NOT_SAVE_WORK",
        )
    }
}

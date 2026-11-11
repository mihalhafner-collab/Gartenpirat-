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
            NativeComposeAppTests(test).withActivities { execute(journey) }

        /**
         * Independent UI acceptance: fresh disposable emulator sandbox, no backup/provider
         * prerequisite.
         */
        @JvmStatic
        fun runGraphics(test: Instrumentation, journey: String): JSONObject =
            NativeComposeAppTests(test).withActivities { executeGraphics(journey) }

        @JvmStatic
        fun runSettlement(test: Instrumentation): JSONObject =
            NativeComposeAppTests(test).withActivities { executeSettlement() }

        @JvmStatic
        fun runAccounting(test: Instrumentation, reopen: Boolean): JSONObject =
            NativeComposeAppTests(test).withActivities { executeAccounting(reopen) }
    }

    private val context = test.targetContext
    private val automation = test.uiAutomation
    private var checks = 0
    private val executedMcpTools = linkedSetOf<String>()
    @Volatile private var activity: Activity? = null
    @Volatile private var latest: Activity? = null
    private val sharedSearchLabel = "Suchen"
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

    private fun withActivities(block: NativeComposeAppTests.() -> JSONObject): JSONObject {
        val application = context.applicationContext as Application
        val callbacks =
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(value: Activity, state: Bundle?) {
                    value.window.addFlags(
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                    if (value is MainActivity) {
                        latest = value
                        activity = value
                    }
                }

                override fun onActivityResumed(value: Activity) {
                    if (value is MainActivity) {
                        latest = value
                        activity = value
                    }
                }

                override fun onActivityDestroyed(value: Activity) {
                    if (activity === value) activity = null
                    if (latest === value) latest = null
                }

                override fun onActivityStarted(value: Activity) = Unit

                override fun onActivityPaused(value: Activity) = Unit

                override fun onActivityStopped(value: Activity) = Unit

                override fun onActivitySaveInstanceState(value: Activity, state: Bundle) = Unit
            }
        application.registerActivityLifecycleCallbacks(callbacks)
        return try {
            block()
        } finally {
            application.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }

    private fun yes(value: Boolean, code: String) {
        if (!value) throw AssertionError("QA_NATIVE_COMPOSE_$code")
        checks++
    }

    private fun physicalPixelTested(): Boolean =
        Build.MANUFACTURER.equals("Google", ignoreCase = true) &&
            Build.MODEL.startsWith("Pixel", ignoreCase = true) &&
            Build.HARDWARE !in listOf("ranchu", "goldfish") &&
            shell("getprop ro.kernel.qemu") != "1"

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
        logUiTimeoutState(code)
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
        // A fresh tree avoids acting on a previous route or pre-scroll bounds
        // retained by AccessibilityCache.
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
        fun selected() =
            nodes().any { candidate ->
                candidate.isVisibleToUser &&
                    matches(candidate, tag) &&
                    generateSequence(candidate) { it.parent }.any { it.isSelected }
            }
        reveal(tag)
        if (!selected()) click(tag)
        waitUntil("TAB_SELECTED_$tag") { selected() }
        checks++
    }

    private fun selectCustomerTab(label: String) {
        fun selected() =
            nodes().any { candidate ->
                candidate.isVisibleToUser &&
                    matches(candidate, label) &&
                    generateSequence(candidate) { it.parent }.any(::navigationSelected)
            }
        reveal(label)
        if (!selected()) click(label)
        waitUntil("CUSTOMER_TAB_SELECTED_$label") { selected() }
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

    private fun openDetails(label: String) {
        reveal(label)
        fun state() =
            node(label)?.let { node ->
                generateSequence(node) { it.parent }
                    .mapNotNull { it.stateDescription?.toString() }
                    .firstOrNull { it in setOf("Aufgeklappt", "Zugeklappt") }
            }
        waitUntil("DETAIL_GROUP_STATE_$label") { state() != null }
        if (state() == "Zugeklappt") click(label)
        waitUntil("DETAIL_GROUP_OPEN_$label") { state() == "Aufgeklappt" }
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
                node("Suchen")?.let(::clickable) != null &&
                node(UiTags.CREATE)?.let(::clickable) != null
        }
        checks++
    }

    @Suppress("DEPRECATION") // API 35 exposes the selected navigation button through isChecked.
    private fun navigationSelected(node: AccessibilityNodeInfo) = node.isChecked

    private fun selectedArea(label: String) =
        nodes().any {
            it.isVisibleToUser &&
                matches(it, label) &&
                (navigationSelected(it) || clickable(it)?.let(::navigationSelected) == true)
        }

    /** The customer card opens the same explicit search as Today. */
    private fun searchCustomers(query: String) {
        if (node("Suche") == null) {
            if (keyboardVisible()) back()
            click("action:global-search")
            waitUntil("SHARED_CUSTOMER_SEARCH_READY") {
                field(sharedSearchLabel)?.isEditable == true
            }
        }
        enter(sharedSearchLabel, query)
        val expected =
            read(
                    "search_local",
                    JSONObject()
                        .put("query", query)
                        .put("types", JSONArray(listOf("customer", "property")))
                        .put("limit", 50),
                )
                .getInt("total")
        showKeyboard(sharedSearchLabel)
        yes(
            requireNotNull(field(sharedSearchLabel))
                .performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id),
            "SHARED_CUSTOMER_SEARCH_SUBMITTED",
        )
        waitUntil("SHARED_CUSTOMER_SEARCH_RESULT") {
            !keyboardVisible() && node("$expected Treffer") != null
        }
    }

    private fun openAccounting() {
        // The first tap restores this area's previous detail; the second opens
        // its root and preserves the overview's saved filters.
        click(UiTags.area("accounting"))
        click(UiTags.area("accounting"))
        scrollTop()
        waitUntil("ACCOUNTING_ROOT_READY") {
            selectedArea(UiTags.area("accounting")) && node("ausgestellt") != null
        }
    }

    private fun openAccountingOpen() {
        openAccounting()
        reveal("Offen abzurechnen")
    }

    private fun openAccountingTotals() {
        openAccounting()
        shown("ausgestellt")
    }

    private fun openSettlements() {
        openAccounting()
        reveal("Alle Abschlüsse")
        click("Alle Abschlüsse")
        waitUntil("SETTLEMENT_LIST_READY") {
            field("Abschluss oder Rechnungsnummer")?.isEditable == true
        }
    }

    /** Only establishes a detail context; business actions still use real controls. */
    private fun openContext(
        section: String,
        id: String = "",
        mode: String = "list",
        args: JSONObject = JSONObject(),
    ) {
        // Compose can already be attached while the asynchronously loaded controller is null.
        // Poll from the instrumentation worker; the main thread only reads and navigates.
        waitUntil("CONTEXT_CONTROLLER_READY_$section") {
            var opened = false
            test.runOnMainSync {
                val current = activity as? MainActivity ?: return@runOnMainSync
                if (current.isDestroyed || current.isFinishing) return@runOnMainSync
                val delegate =
                    MainActivity::class
                        .java
                        .declaredFields
                        .first { it.name.startsWith("controller") }
                        .apply { isAccessible = true }
                        .get(current) as androidx.compose.runtime.State<*>
                val controller = delegate.value as? NativeAppController ?: return@runOnMainSync
                controller.navigate(section, id, mode, args)
                opened = true
            }
            opened
        }
        settleUi()
    }

    /** More opens the real list directly, including after returning from a record or action. */
    private fun openWorklogs() {
        openOffice()
        click(UiTags.area("worklogs"))
        waitUntil("WORK_LIST_READY") { field("Suchen")?.isEditable == true }
    }

    /** The same overflow menu is available on each main area. */
    private fun openOffice() {
        click(UiTags.area("today"))
        click(UiTags.area("today"))
        click(UiTags.MORE)
        reveal("Vorgänge")
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
        var first = Rect()
        var second = Rect()
        var minimum = 0
        var priorGeometry = ""
        var geometry = "No actions"
        var stableSince = 0L
        try {
            waitUntil("PRIMARY_ACTIONS_READY_${primary}_$secondary") {
                val snapshot = nodes()
                fun actions(label: String) =
                    snapshot
                        .filter { it.isVisibleToUser && matches(it, label) }
                        .mapNotNull(::clickable)
                val primaryActions = actions(primary)
                val primaryAction =
                    primaryActions.firstOrNull { it.viewIdResourceName == UiTags.PRIMARY }
                        ?: primaryActions.firstOrNull()
                        ?: return@waitUntil false
                first = Rect().also(primaryAction::getBoundsInScreen)
                val secondaryActions = actions(secondary)
                val secondaryAction =
                    secondaryActions.firstOrNull { it.viewIdResourceName == UiTags.SECONDARY }
                        ?: secondaryActions.minByOrNull {
                            val bounds = Rect().also(it::getBoundsInScreen)
                            kotlin.math.abs(bounds.centerY() - first.centerY())
                        }
                        ?: return@waitUntil false
                second = Rect().also(secondaryAction::getBoundsInScreen)
                val viewport = Rect()
                test.runOnMainSync {
                    activity?.window?.decorView?.let {
                        it.getWindowVisibleDisplayFrame(viewport)
                        minimum = (48 * it.resources.displayMetrics.density).toInt()
                    }
                }
                geometry = "primary=$first secondary=$second viewport=$viewport minimum=$minimum"
                if (geometry != priorGeometry) {
                    test.sendStatus(
                        0,
                        Bundle().apply { putString("primary_action_geometry", geometry) },
                    )
                    priorGeometry = geometry
                    stableSince = SystemClock.uptimeMillis()
                }
                minimum > 0 &&
                    viewport.contains(first) &&
                    viewport.contains(second) &&
                    !Rect.intersects(first, second) &&
                    (first.top >= second.bottom ||
                        first.left >= second.right &&
                            first.centerY() in second.top..second.bottom) &&
                    first.height() >= minimum - 1 &&
                    second.height() >= minimum - 1 &&
                    SystemClock.uptimeMillis() - stableSince >= 500
            }
        } catch (failure: Throwable) {
            throw AssertionError(
                "PRIMARY_ACTION_GEOMETRY_${primary}_$secondary: $geometry",
                failure,
            )
        }
        yes(
            !Rect.intersects(first, second) &&
                (first.top >= second.bottom ||
                    first.left >= second.right && first.centerY() in second.top..second.bottom),
            "PRIMARY_POSITION_${primary}_SECONDARY_$secondary",
        )
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
        executedMcpTools.add(name)
        return response.optJSONObject("data") ?: response
    }

    private fun planningWorkAndDeliveryState(): String {
        val current = PixelMcpReadTools.effective(store.getState())
        return PixelMcpWriteTools.hash(
            JSONObject()
                .put("worklogs", current.getJSONArray("worklogs"))
                .put("invoices", current.getJSONArray("invoices"))
                .put("offers", current.getJSONArray("offers"))
                .put("settlement", store.ownerValue(SettlementRules.KEY, "{}"))
        )
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
        // Check package identity and fresh synthetic storage before writing the fixture.
        // This suite never clears app data.
        yes(
            test.context.packageName == "at.hausgarten.service.test" &&
                context.packageName == "at.hausgarten.service",
            "GRAPHICS_TEST_AND_TARGET_PACKAGES",
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

    /** Pre-existing synthetic original, seeded without calling the retired Labor writer. */
    private fun seedHistoricalLabor(draft: JSONObject, fixtureId: String): String {
        val master = PixelMcpReadTools.effective(store.getState())
        val record =
            SettlementRules.review(SettlementRules.normalized(draft, fixtureId), master)
                .put("status", "confirmed")
                .put("issue_date", "2026-11-05")
                .put("confirmed_at", "2026-11-05T10:00:00Z")
        record.put("total_minor", SettlementRules.total(record))
        val ledger = JSONObject(store.ownerValue(SettlementRules.KEY, "{}"))
        val records =
            ledger.optJSONArray("records") ?: JSONArray().also { ledger.put("records", it) }
        records.put(record)
        store.putOwnerValue(SettlementRules.KEY, ledger.toString())
        return fixtureId
    }

    private fun executeSettlement(): JSONObject {
        guardGraphicsFixture()
        store.putOwnerValue("snapshot", NativeSettlementNativeTests.fixture().toString())
        store.putOwnerValue("personal_paid_migrated", "1")
        store.putOwnerValue("personal_paid", JSONArray().put("qa_settlement_labor").toString())
        var originalMaster = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
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
            seedHistoricalLabor(
                NativeSettlementNativeTests.draft("labor", "200", "qa_settlement_labor"),
                "qa_settlement_ui_historical_labor",
            )
        val pdfViewIntent = java.util.concurrent.atomic.AtomicReference<Intent>()
        val pdfShareIntent = java.util.concurrent.atomic.AtomicReference<Intent>()
        val shareLaunches = java.util.concurrent.atomic.AtomicInteger()
        val pdfMonitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    val target =
                        if (intent.action == Intent.ACTION_CHOOSER)
                            androidx.core.content.IntentCompat.getParcelableExtra(
                                intent,
                                Intent.EXTRA_INTENT,
                                Intent::class.java,
                            )
                        else intent
                    if (target?.type == "application/pdf")
                        when (target.action) {
                            Intent.ACTION_VIEW -> pdfViewIntent.set(Intent(target))
                            Intent.ACTION_SEND -> {
                                pdfShareIntent.set(Intent(target))
                                shareLaunches.incrementAndGet()
                            }
                        }
                    return null
                }
            }
        test.addMonitor(pdfMonitor)
        fun cancelPdfChooser(
            reference: java.util.concurrent.atomic.AtomicReference<Intent>,
            code: String,
        ): Intent {
            waitUntil("${code}_INTENT") { reference.get() != null }
            waitUntil("${code}_ANDROID_CHOOSER_VISIBLE") {
                automation.rootInActiveWindow?.packageName?.toString()?.let {
                    it != context.packageName
                } == true
            }
            val target = requireNotNull(reference.get())
            back()
            waitUntil("${code}_RETURNED_TO_APP") {
                automation.rootInActiveWindow?.packageName?.toString() == context.packageName
            }
            checks++
            return target
        }
        val previous = automation.serviceInfo
        val previousFlags = previous.flags
        val oldFont = shell("settings get system font_scale")
        try {
            automation.serviceInfo = previous.apply {
                flags =
                    flags or
                        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            launch()
            openSettlements()
            shown("[TEST] Rechnung 900")
            click("[TEST] Rechnung 900")
            shown("Ausstellen")
            fun editValue(row: String, label: String, value: String) {
                reveal(row)
                click(row)
                enter(label, value)
                if (keyboardVisible()) back()
                click("Fertig")
                waitUntil("SETTLEMENT_VALUE_EDITOR_CLOSED_$label") {
                    field(label) == null && node("Fertig") == null
                }
            }
            val beforeMissingPrice = store.ownerValue(SettlementRules.KEY, "{}")
            editValue("Preis je Einheit", "Preis je Einheit (€)", "")
            reveal("Preis je Einheit (€)")
            waitUntil("SETTLEMENT_MISSING_PRICE_STAYS_EMPTY") {
                val values = runCatching {
                    JSONObject(
                            store.ownerValue(
                                "native_form_draft:" +
                                    PixelMcpWriteTools.hash("settlement-editor:$invoice"),
                                "{}",
                            )
                        )
                        .optJSONObject("values")
                }
                    .getOrNull()
                values?.optJSONArray("positions")?.optJSONObject(0)?.let {
                    it.has("price") && it.optString("price").isEmpty()
                } == true
            }
            yes(
                !actionEnabled(UiTags.PRIMARY) &&
                    store.ownerValue(SettlementRules.KEY, "{}") == beforeMissingPrice,
                "SETTLEMENT_MISSING_PRICE_BLOCKS_ISSUE_WITHOUT_INVENTING_ZERO_OR_CHANGING_ORIGINAL",
            )
            // Color is an explicit usability signal; only the missing-price control is sampled.
            val missingBounds = actionBounds("Preis je Einheit (€)")
            val priceCapture = requireNotNull(automation.takeScreenshot())
            val priceScreen =
                try {
                    requireNotNull(priceCapture.copy(Bitmap.Config.ARGB_8888, false))
                } finally {
                    priceCapture.recycle()
                }
            try {
                var red = false
                for (y in
                    missingBounds.top.coerceAtLeast(0) until
                        missingBounds.bottom.coerceAtMost(priceScreen.height) step
                        2) for (x in
                    missingBounds.left.coerceAtLeast(0) until
                        missingBounds.right.coerceAtMost(priceScreen.width) step
                        2) {
                    val color = priceScreen.getPixel(x, y)
                    if (
                        android.graphics.Color.red(color) >
                            android.graphics.Color.green(color) + 40 &&
                            android.graphics.Color.red(color) >
                                android.graphics.Color.blue(color) + 40
                    )
                        red = true
                }
                yes(red, "SETTLEMENT_MISSING_PRICE_HAS_VISIBLE_ERROR_COLOR")
            } finally {
                priceScreen.recycle()
            }
            editValue("Preis je Einheit (€)", "Preis je Einheit (€)", "900")
            waitUntil("SETTLEMENT_PRICE_RESTORED_ISSUE_ENABLED") {
                val entered = runCatching {
                    JSONObject(
                            store.ownerValue(
                                "native_form_draft:" +
                                    PixelMcpWriteTools.hash("settlement-editor:$invoice"),
                                "{}",
                            )
                        )
                        .getJSONObject("values")
                        .getJSONArray("positions")
                        .getJSONObject(0)
                        .getString("price")
                }
                    .getOrNull()
                entered == "900" &&
                    runCatching { actionEnabled(UiTags.PRIMARY) }.getOrDefault(false) &&
                    store.ownerValue(SettlementRules.KEY, "{}") == beforeMissingPrice
            }
            val beforePreview = store.ownerValue(SettlementRules.KEY, "{}")
            reveal("PDF ansehen")
            click("PDF ansehen")
            val previewIntent = cancelPdfChooser(pdfViewIntent, "SETTLEMENT_DRAFT_PREVIEW")
            yes(
                previewIntent.action == Intent.ACTION_VIEW,
                "SETTLEMENT_PREVIEW_OPENS_NATIVE_PDF_VIEW",
            )
            context.contentResolver
                .openFileDescriptor(requireNotNull(previewIntent.data), "r")
                .use { descriptor ->
                    android.graphics.pdf.PdfRenderer(requireNotNull(descriptor)).use { renderer ->
                        yes(renderer.pageCount > 0, "SETTLEMENT_PREVIEW_URI_READS_REAL_PDF")
                        renderer.openPage(0).use { page ->
                            yes(
                                page.width > 0 && page.height > 0,
                                "SETTLEMENT_PREVIEW_FIRST_PAGE_READABLE",
                            )
                        }
                    }
                }
            val afterPreview =
                read("get_settlement", JSONObject().put("settlement_id", invoice))
                    .getJSONObject("record")
            yes(
                store.ownerValue(SettlementRules.KEY, "{}") == beforePreview &&
                    afterPreview.optString("status") == "draft" &&
                    !afterPreview.has("invoice_number") &&
                    !afterPreview.has("pdf") &&
                    !SettlementRules.active(afterPreview),
                "SETTLEMENT_UI_PREVIEW_RESERVES_NO_NUMBER_OR_BINDING_AND_ISSUES_NO_ORIGINAL",
            )
            // Design: one "Ausstellen" after the preview; issuer details are confirmed once in
            // settings.
            click(UiTags.PRIMARY)
            waitUntil("SETTLEMENT_UI_REAL_INVOICE_CONFIRMATION") {
                read("get_settlement", JSONObject().put("settlement_id", invoice))
                    .getJSONObject("record")
                    .getString("status") == "confirmed"
            }
            shown("E-Rechnung")
            val beforeShare = read("get_settlement", JSONObject().put("settlement_id", invoice))
            val shareRecordHash = beforeShare.getString("record_hash")
            val sharePayments = beforeShare.getJSONArray("payments").toString()
            val shareDelivery = beforeShare.getJSONArray("delivery_evidence").toString()
            click("Zustimmung speichern")
            val sharedIntent = cancelPdfChooser(pdfShareIntent, "SETTLEMENT_CONSENT_SHARE")
            yes(
                sharedIntent.action == Intent.ACTION_SEND && sharedIntent.type == "application/pdf",
                "SETTLEMENT_CONSENT_OPENS_ANDROID_PDF_SHARE",
            )
            val consent =
                read("get_customer", JSONObject().put("customer_id", "qa_settlement_recipient"))
            yes(
                consent.getBoolean("electronic_invoice_consent") &&
                    consent.getString("electronic_invoice_consent_date") == SettlementRules.today(),
                "SETTLEMENT_NATIVE_CONSENT_AND_ACTUAL_DATE_PERSISTED",
            )
            val afterShare = read("get_settlement", JSONObject().put("settlement_id", invoice))
            yes(
                afterShare.getString("record_hash") == shareRecordHash &&
                    afterShare.getJSONArray("payments").toString() == sharePayments &&
                    afterShare.getJSONArray("delivery_evidence").toString() == shareDelivery &&
                    afterShare.getLong("paid_minor") == 0L,
                "SETTLEMENT_CANCELLED_ANDROID_SHARE_NEITHER_CHANGES_ORIGINAL_NOR_BOOKS_PAYMENT_OR_ACCESS",
            )
            openContext("customers", "qa_settlement_recipient")
            selectCustomerTab("Geld")
            reveal("E-Rechnung")
            click("E-Rechnung")
            waitUntil("SETTLEMENT_NATIVE_CONSENT_REVOKED") {
                val current =
                    read("get_customer", JSONObject().put("customer_id", "qa_settlement_recipient"))
                !current.optBoolean("electronic_invoice_consent") &&
                    current.optString("electronic_invoice_consent_date").isBlank()
            }
            openContext("settlement_review", invoice)
            click(UiTags.DETAIL_MENU)
            click(UiTags.detailAction("settlement-share"))
            shown("E-Rechnung")
            yes(shareLaunches.get() == 1, "SETTLEMENT_REVOKED_CONSENT_BLOCKS_SECOND_ANDROID_SHARE")
            click("Später")
            originalMaster = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
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
            shown("900,00 €")
            yes(
                read("get_accounting_overview").getLong("total_minor") == 110000L,
                "SETTLEMENT_UI_900_PLUS_200",
            )
            val filtered =
                read(
                    "get_accounting_overview",
                    JSONObject().put("query", "qa_settlement_invoice").put("route", "billed"),
                )
            yes(
                filtered.getLong("billed_minor") == 90000L &&
                    filtered.getLong("total_minor") == 90000L &&
                    filtered.getInt("total") == 1,
                "SETTLEMENT_SHARED_FILTER_EXACT_AMOUNT",
            )
            captureSyntheticScreen("settlement-overview")
            val sources =
                JSONObject()
                    .put("billing_customer_id", "qa_settlement_recipient")
                    .put("work_record_ids", JSONArray().put("qa_settlement_race"))
            openContext("settlement_edit", args = sources)
            editValue("Bezeichnung", "Bezeichnung", "[TEST] Erste Abrechnung")
            recreate()
            shown("[TEST] Erste Abrechnung")
            // The real writer pauses inside SQLite; no alternate save implementation is used.
            if (Build.VERSION.SDK_INT >= 30) {
                var liveController: NativeAppController? = null
                waitUntil("SETTLEMENT_DELAY_CONTROLLER_READY") {
                    test.runOnMainSync {
                        val current = activity as? MainActivity ?: return@runOnMainSync
                        val delegate =
                            MainActivity::class
                                .java
                                .declaredFields
                                .first { it.name.startsWith("controller") }
                                .apply { isAccessible = true }
                                .get(current) as androidx.compose.runtime.State<*>
                        liveController = delegate.value as? NativeAppController
                    }
                    liveController != null
                }
                val database = requireNotNull(liveController).store.writableDatabase
                val entered = java.util.concurrent.CountDownLatch(1)
                val release = java.util.concurrent.CountDownLatch(1)
                val paused = java.util.concurrent.atomic.AtomicBoolean()
                val trigger = "qa_settlement_delayed_save"
                database.setCustomScalarFunction(
                    trigger,
                    java.util.function.UnaryOperator { value ->
                        if (paused.compareAndSet(false, true)) {
                            entered.countDown()
                            check(release.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                                "Synthetic settlement delay was not released"
                            }
                        }
                        value
                    },
                )
                database.execSQL(
                    "CREATE TRIGGER $trigger BEFORE INSERT ON owner_state WHEN NEW.key='settlement:v1' BEGIN SELECT $trigger('hold'); END"
                )
                try {
                    reveal("Entwurf sichern")
                    val saveBounds = actionBounds("Entwurf sichern")
                    click("Entwurf sichern", onAction = {})
                    yes(
                        entered.await(5, java.util.concurrent.TimeUnit.SECONDS),
                        "SETTLEMENT_ACTUAL_SQLITE_SAVE_IS_DELAYED",
                    )
                    waitUntil("SETTLEMENT_SAVE_DISABLED_WHILE_WRITER_BLOCKED") {
                        !actionEnabled("Entwurf sichern")
                    }
                    yes(
                        actionEnabled(UiTags.area("customers")),
                        "SETTLEMENT_DELAY_DISABLES_SAVE_WITHOUT_BLOCKING_MAIN_NAVIGATION",
                    )
                    repeat(2) {
                        val time = SystemClock.uptimeMillis()
                        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                            val event =
                                MotionEvent.obtain(
                                    time,
                                    SystemClock.uptimeMillis(),
                                    action,
                                    saveBounds.exactCenterX(),
                                    saveBounds.exactCenterY(),
                                    0,
                                )
                            event.source = InputDevice.SOURCE_TOUCHSCREEN
                            try {
                                yes(
                                    automation.injectInputEvent(event, true),
                                    "SETTLEMENT_SECOND_TAP_WHILE_SAVE_DISABLED",
                                )
                            } finally {
                                event.recycle()
                            }
                        }
                    }
                    release.countDown()
                    waitUntil("SETTLEMENT_DELAYED_DRAFT_COMMITTED_ONCE") {
                        SettlementRules.rows(read("list_settlements").getJSONArray("records"))
                            .count {
                                it.optString("title") == "[TEST] Erste Abrechnung"
                            } == 1
                    }
                } finally {
                    release.countDown()
                    database.execSQL("DROP TRIGGER IF EXISTS $trigger")
                }
            } else
                throw AssertionError("Synthetic native settlement delay requires API 30 or newer")
            shown("Abschlüsse")
            recreate()
            waitUntil("SETTLEMENT_UI_FIRST_NEW_DRAFT_SAVED") {
                SettlementRules.rows(read("list_settlements").getJSONArray("records")).any {
                    it.optString("title") == "[TEST] Erste Abrechnung"
                }
            }
            shown("Abschlüsse")
            val firstNew =
                SettlementRules.rows(read("list_settlements").getJSONArray("records")).single {
                    it.optString("title") == "[TEST] Erste Abrechnung"
                }
            yes(
                firstNew.getJSONArray("positions").getJSONObject(0).getLong("amount_minor") ==
                    4000L,
                "SETTLEMENT_UI_FIRST_NEW_DRAFT_AMOUNT",
            )
            yes(
                firstNew.optString("status") == "draft" &&
                    !firstNew.has("invoice_number") &&
                    !firstNew.has("pdf") &&
                    SettlementRules.rows(read("list_settlements").getJSONArray("records")).count {
                        it.optString("title") == "[TEST] Erste Abrechnung"
                    } == 1,
                "SETTLEMENT_DELAY_DOUBLE_TAP_AND_RECREATE_KEEP_EXACTLY_ONE_DRAFT_WITHOUT_ACCIDENTAL_ISSUE",
            )
            val beforePlus = store.ownerValue(SettlementRules.KEY, "{}")
            openAccountingTotals()
            click(UiTags.CREATE)
            for (label in
                listOf(
                    "Arbeiten",
                    "L.C.R.",
                    "Geräte",
                    "Abrechnung vorbereiten",
                    "Arbeit nachtragen",
                    "Entwurf fortsetzen",
                    "Zahlung erfassen",
                )) reveal(label)
            yes(
                node("Labor clandestino") == null &&
                    node(UiTags.createChoice("care_plan")) == null &&
                    node("Pflegeplan anlegen") == null,
                "SETTLEMENT_ACCOUNTING_PLUS_HAS_ONLY_AUTHORIZED_SHORTCUTS_AND_NO_RETIRED_COMPLETION",
            )
            click(UiTags.createChoice("work"))
            shown("Was wurde erledigt? *")
            back()
            openAccountingTotals()
            click(UiTags.CREATE)
            click(UiTags.createChoice("Abrechnung vorbereiten"))
            shown("Bezeichnung")
            back()
            yes(
                store.ownerValue(SettlementRules.KEY, "{}") == beforePlus,
                "SETTLEMENT_PLUS_WORK_AND_INVOICE_SHORTCUTS_OPEN_FORMS_WITHOUT_BUSINESS_SIDE_EFFECTS",
            )
            openContext("settlement_edit", args = sources)
            shown("Bezeichnung")
            yes(node("[TEST] Erste Abrechnung") == null, "SETTLEMENT_UI_SECOND_NEW_EDITOR_IS_FRESH")
            editValue("Bezeichnung", "Bezeichnung", "[TEST] Zweite Abrechnung")
            editValue("Menge · Stunden", "Menge", "2")
            click("Entwurf sichern")
            waitUntil("SETTLEMENT_UI_SECOND_NEW_DRAFT_SAVED") {
                SettlementRules.rows(read("list_settlements").getJSONArray("records")).any {
                    it.optString("title") == "[TEST] Zweite Abrechnung"
                }
            }
            shown("Abschlüsse")
            val secondNew =
                SettlementRules.rows(read("list_settlements").getJSONArray("records")).single {
                    it.optString("title") == "[TEST] Zweite Abrechnung"
                }
            yes(
                secondNew.getString("id") != firstNew.getString("id") &&
                    secondNew.getString("route") == "billed" &&
                    secondNew.getJSONArray("positions").getJSONObject(0).getLong("amount_minor") ==
                        8000L,
                "SETTLEMENT_UI_NEW_OPERATION_DISTINCT_ID_ROUTE_AND_AMOUNT",
            )
            yes(
                SettlementRules.rows(read("list_settlements").getJSONArray("records"))
                    .single { it.getString("id") == firstNew.getString("id") }
                    .getString("title") == "[TEST] Erste Abrechnung",
                "SETTLEMENT_UI_FIRST_DRAFT_UNCHANGED_BY_SECOND",
            )
            val beforePaymentShortcut = store.ownerValue(SettlementRules.KEY, "{}")
            openAccountingTotals()
            click(UiTags.CREATE)
            click(UiTags.createChoice("Zahlung erfassen"))
            waitUntil("SETTLEMENT_PAYMENT_SHORTCUT_FILTER_READY") {
                node("[TEST] Rechnung 900") != null
            }
            yes(
                node("[TEST] Erste Abrechnung") == null &&
                    node("[TEST] Zweite Abrechnung") == null &&
                    store.ownerValue(SettlementRules.KEY, "{}") == beforePaymentShortcut,
                "SETTLEMENT_PAYMENT_SHORTCUT_SHOWS_OPEN_ORIGINALS_WITHOUT_DRAFTS_OR_PAYMENT_BOOKING",
            )
            val countBeforeResume = read("list_settlements").getJSONArray("records").length()
            openAccountingTotals()
            click(UiTags.CREATE)
            shown("Entwurf fortsetzen")
            click(UiTags.createChoice("Entwurf fortsetzen"))
            waitUntil("SETTLEMENT_DRAFT_SHORTCUT_FILTER_READY") {
                node("[TEST] Erste Abrechnung") != null
            }
            yes(
                node("[TEST] Rechnung 900") == null && node("[TEST] Labor 200") == null,
                "SETTLEMENT_DRAFT_SHORTCUT_EXCLUDES_FINAL_ORIGINALS",
            )
            click("[TEST] Erste Abrechnung")
            shown("[TEST] Erste Abrechnung")
            val resumed =
                read("get_settlement", JSONObject().put("settlement_id", firstNew.getString("id")))
                    .getJSONObject("record")
            yes(
                read("list_settlements").getJSONArray("records").length() == countBeforeResume &&
                    resumed.getString("id") == firstNew.getString("id") &&
                    resumed.getJSONArray("positions").toString() ==
                        firstNew.getJSONArray("positions").toString(),
                "SETTLEMENT_PLUS_RESUMES_SAME_DRAFT_WITH_SELECTION_AND_WITHOUT_SECOND_OPERATION",
            )
            editValue("Bezeichnung", "Bezeichnung", "[TEST] Erste Abrechnung geprüft")
            click("Entwurf sichern")
            waitUntil("SETTLEMENT_UI_EXISTING_DRAFT_SAVE_COMPLETED") {
                SettlementRules.rows(read("list_settlements").getJSONArray("records"))
                    .singleOrNull { it.getString("id") == firstNew.getString("id") }
                    ?.optString("title") == "[TEST] Erste Abrechnung geprüft"
            }
            shown("Abschlüsse")
            yes(
                PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) ==
                    originalMaster && store.ownerValue("personal_paid", "[]") == originalMarks,
                "SETTLEMENT_UI_ASSIGNMENTS_AND_SOURCES_UNCHANGED",
            )
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
                reveal("900,00 €")
                shown("900,00 €")
                reveal("bezahlt · diese Belege")
                shown("bezahlt · diese Belege")
                captureSyntheticScreen(
                    "settlement-overview-200-${if (wide) "landscape" else "portrait"}"
                )
            }
            activity?.let { current ->
                test.runOnMainSync { if (!current.isFinishing) current.finish() }
            }
            shell("settings put system font_scale 1.0")
            launch()
            test.runOnMainSync {
                activity?.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            waitUntil("SETTLEMENT_ACTION_JOURNEYS_NORMAL_LAYOUT") {
                var applied = false
                test.runOnMainSync {
                    val configuration = activity?.resources?.configuration
                    applied =
                        configuration != null &&
                            configuration.fontScale < 1.01f &&
                            configuration.orientation ==
                                android.content.res.Configuration.ORIENTATION_PORTRAIT
                }
                applied
            }
            settlementCancellationUi(invoice)
            offerAcceptanceUi()
            settlementPaymentLifecycleUi()
            return JSONObject()
                .put("checks", checks)
                .put("executed_mcp_tools", JSONArray(executedMcpTools.toList()))
                .put("synthetic_only", true)
                .put("productive_data_read", false)
                .put("real_main_activity", true)
                .put("invoice_completion_and_historical_labor_readback", true)
                .put("exact_amounts", true)
                .put("filters_and_navigation", true)
                .put("font_scale_200_percent", true)
                .put("draft_recreation", true)
                .put("native_storno_and_offer_acceptance_readback", true)
                .put("native_payment_lifecycle_readback", true)
                .put("native_offer_share_chooser", true)
                .put("physical_pixel_tested", physicalPixelTested())
        } catch (failure: Throwable) {
            runCatching { captureSyntheticScreen("settlement-failure") }
            throw failure
        } finally {
            test.removeMonitor(pdfMonitor)
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

    private fun settlementCancellationUi(invoice: String) {
        val withoutDelivery = read("get_settlement", JSONObject().put("settlement_id", invoice))
        val deliveryArgs =
            JSONObject()
                .put("request_id", "qa_native_invoice_delivery_evidence")
                .put("settlement_id", invoice)
                .put("expected_record_hash", withoutDelivery.getString("record_hash"))
                .put("date", SettlementRules.today())
                .put(
                    "evidence",
                    "[TEST] Synthetische persönliche Übergabe; keine Versandnachricht erzeugt",
                )
        read("record_settlement_delivery", deliveryArgs)
        yes(
            read("record_settlement_delivery", deliveryArgs).getBoolean("replayed"),
            "SETTLEMENT_MCP_DELIVERY_EXACT_REQUEST_REPLAY",
        )
        val before = read("get_settlement", JSONObject().put("settlement_id", invoice))
        yes(
            before.getJSONArray("delivery_evidence").length() == 1 &&
                before.getJSONArray("delivery_evidence").getJSONObject(0).getString("evidence") ==
                    deliveryArgs.getString("evidence") &&
                before.getJSONArray("delivery_evidence").getJSONObject(0).getString("date") ==
                    deliveryArgs.getString("date") &&
                before.getString("record_hash") == withoutDelivery.getString("record_hash") &&
                before.getJSONArray("payments").toString() ==
                    withoutDelivery.getJSONArray("payments").toString(),
            "SETTLEMENT_MCP_DELIVERY_READBACK_HAS_ONE_EVIDENCE_AND_UNALTERED_ORIGINAL_WITHOUT_PAYMENT",
        )
        val original = before.getJSONObject("record")
        val masterHash = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
        val beforeLedger = JSONObject(store.ownerValue(SettlementRules.KEY, "{}"))
        val reason = "[TEST] Vollständige Aufhebung nach ausdrücklicher Prüfung"
        val evidence = "[TEST] Kein Zugang behauptet; Berichtigung persönlich vereinbart"
        openAccountingTotals()
        click("Umsätze und Zahlungen")
        click(original.getString("invoice_number"))
        click(UiTags.DETAIL_MENU)
        click(UiTags.detailAction("settlement-correction"))
        enter("Grund der Korrektur", reason)
        enter("Vorhandene Übermittlung / Zugang / Berichtigung", evidence)
        if (keyboardVisible()) back()
        click("Storno bewusst vorbereiten")
        waitUntil("SETTLEMENT_NATIVE_STORNO_PREPARED") {
            SettlementRules.records(JSONObject(store.ownerValue(SettlementRules.KEY, "{}"))).any {
                it.optString("corrects") == invoice && it.optString("status") == "prepared"
            }
        }
        val prepared =
            SettlementRules.records(JSONObject(store.ownerValue(SettlementRules.KEY, "{}")))
                .single { it.optString("corrects") == invoice }
        val correction = prepared.getString("id")
        yes(
            read("get_settlement", JSONObject().put("settlement_id", invoice))
                .getString("record_hash") == before.getString("record_hash") &&
                !read(
                        "get_lcr_work_records",
                        JSONObject().put("work_record_id", "qa_settlement_invoice"),
                    )
                    .getJSONArray("records")
                    .getJSONObject(0)
                    .getBoolean("lcr_mutable"),
            "M20_NATIVE_PREPARED_STORNO_KEEPS_ORIGINAL_AND_SOURCE_BINDING",
        )
        recreate()
        shown("Ausstellen fortsetzen")
        click(UiTags.PRIMARY)
        waitUntil("SETTLEMENT_NATIVE_STORNO_ISSUED") {
            read("get_settlement", JSONObject().put("settlement_id", correction))
                .getJSONObject("record")
                .optString("status") == "confirmed"
        }
        shown("E-Rechnung")
        click("Später")
        val completed =
            read("get_settlement", JSONObject().put("settlement_id", correction))
                .getJSONObject("record")
        val cancelled = read("get_settlement", JSONObject().put("settlement_id", invoice))
        val retained = JSONObject(cancelled.getJSONObject("record").toString())
        retained.put("status", original.getString("status"))
        retained.remove("corrected_by")
        yes(
            cancelled.getJSONObject("record").getString("status") == "cancelled" &&
                cancelled.getJSONObject("record").getString("corrected_by") == correction &&
                completed.getString("kind") == "cancellation" &&
                completed.getString("corrects") == invoice &&
                completed.getString("original_number") == original.getString("invoice_number") &&
                completed.getString("invoice_number") != original.getString("invoice_number") &&
                completed.getLong("total_minor") == -original.getLong("total_minor") &&
                completed.getString("reason") == reason &&
                completed.getString("access_evidence") == evidence &&
                PixelMcpWriteTools.hash(retained) == PixelMcpWriteTools.hash(original),
            "M20_NATIVE_STORNO_HAS_OWN_ORIGINAL_PDF_NUMBER_AND_UNALTERED_INVOICE_REFERENCE",
        )
        val correctionPdf =
            read("get_settlement_pdf", JSONObject().put("settlement_id", correction))
        yes(
            correctionPdf.getJSONObject("metadata").getString("pdf_sha256") ==
                completed.getJSONObject("pdf").getString("pdf_sha256") &&
                read("get_settlement_pdf", JSONObject().put("settlement_id", invoice))
                    .getJSONObject("metadata")
                    .getString("pdf_sha256") ==
                    original.getJSONObject("pdf").getString("pdf_sha256") &&
                read(
                        "get_lcr_work_records",
                        JSONObject().put("work_record_id", "qa_settlement_invoice"),
                    )
                    .getJSONArray("records")
                    .getJSONObject(0)
                    .getBoolean("lcr_mutable") &&
                cancelled.getJSONArray("payments").toString() ==
                    before.getJSONArray("payments").toString() &&
                cancelled.getJSONArray("delivery_evidence").toString() ==
                    before.getJSONArray("delivery_evidence").toString() &&
                PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) ==
                    masterHash,
            "M20_NATIVE_COMPLETED_STORNO_RELEASES_SOURCE_WITHOUT_REWRITING_WORK_PAYMENT_OR_ACCESS",
        )
        reveal("Bestätigt")
        captureSyntheticScreen("settlement-storno-original")
        val issuedLedger = JSONObject(store.ownerValue(SettlementRules.KEY, "{}"))
        val replayArgs =
            JSONObject()
                .put("request_id", "qa_native_storno_complete_replay")
                .put("settlement_id", correction)
        read("complete_settlement", replayArgs)
        yes(
            read("complete_settlement", replayArgs).getBoolean("replayed"),
            "M20_NATIVE_STORNO_COMPLETION_REPLAY_CONFIRMED",
        )
        recreate()
        val afterReplay = JSONObject(store.ownerValue(SettlementRules.KEY, "{}"))
        yes(
            afterReplay.getJSONArray("records").toString() ==
                issuedLedger.getJSONArray("records").toString() &&
                afterReplay.getJSONObject("series_counters").toString() ==
                    issuedLedger.getJSONObject("series_counters").toString() &&
                afterReplay.optJSONArray("payments")?.toString().orEmpty() ==
                    beforeLedger.optJSONArray("payments")?.toString().orEmpty() &&
                SettlementRules.records(afterReplay).count {
                    it.optString("corrects") == invoice
                } == 1,
            "M20_NATIVE_STORNO_REPLAY_AND_RECREATE_CREATE_NO_SECOND_DOCUMENT_NUMBER_OR_PAYMENT",
        )
        val beforeStale = store.ownerValue(SettlementRules.KEY, "{}")
        val staleDelivery = runCatching {
            tools.call(
                "record_settlement_delivery",
                JSONObject(deliveryArgs.toString())
                    .put("request_id", "qa_native_invoice_delivery_stale"),
            )
        }
            .exceptionOrNull()
        yes(
            staleDelivery is IllegalArgumentException &&
                staleDelivery.message == "Vorgang geändert. Erneut öffnen." &&
                store.ownerValue(SettlementRules.KEY, "{}") == beforeStale &&
                read("get_settlement", JSONObject().put("settlement_id", invoice))
                    .getJSONArray("delivery_evidence")
                    .length() == 1,
            "SETTLEMENT_MCP_DELIVERY_STALE_ORIGINAL_HASH_REJECTS_WITHOUT_DUPLICATE_ACCESS_EVIDENCE",
        )
    }

    private fun offerAcceptanceUi() {
        val day = LocalDate.now(ZoneId.of("Europe/Vienna"))
        // This entire MainActivity process was created by guardGraphicsFixture from an empty
        // disposable QA app. Marked test offers intentionally cannot become orders, so this
        // positive acceptance uses its own unmarked synthetic customer inside that same sandbox.
        val customer =
            read(
                    "create_customer",
                    JSONObject()
                        .put("request_id", "qa_native_acceptance_customer")
                        .put("name", "QA ausschließlich lokaler Angebotskunde")
                        .put("street", "Fiktiver Annahmeweg 9")
                        .put("postal_code", "3000")
                        .put("city", "Fiktivstadt")
                        .put("country", "AT"),
                )
                .getString("entity_id")
        val offer =
            read(
                    "create_and_validate_offer",
                    JSONObject()
                        .put("request_id", "qa_native_acceptance_offer")
                        .put("customer_id", customer)
                        .put("title", "QA Angebot zur nativen Annahme")
                        .put("document_type", "fixed_price")
                        .put("issue_date", day.toString())
                        .put("valid_until", day.plusDays(14).toString())
                        .put("execution_window", "Nach ausdrücklicher Vereinbarung")
                        .put("payment_terms", "Binnen 14 Tagen")
                        .put("tax_mode", "small_business")
                        .put("customer_type", "b2b")
                        .put("scope_confirmed", true)
                        .put(
                            "positions",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("title", "QA Vereinbarte Gartenpflege")
                                        .put(
                                            "description",
                                            "Synthetischer bestätigter Leistungsumfang",
                                        )
                                        .put("quantity", 2)
                                        .put("price", 35)
                                        .put("tax_rate", 0)
                                        .put("unit", "h")
                                        .put("price_source", "user_confirmed")
                                ),
                        ),
                )
                .getString("offer_id")
        val validated = read("get_offer", JSONObject().put("offer_id", offer))
        read(
            "finalize_offer",
            JSONObject()
                .put("request_id", "qa_native_acceptance_final")
                .put("offer_id", offer)
                .put("expected_record_hash", validated.getString("record_hash"))
                .put("expected_context_hash", validated.getString("context_hash"))
                .put("confirmed", true),
        )
        val original = read("get_offer", JSONObject().put("offer_id", offer))
        val originalPdf = original.getJSONObject("offer").getString("pdf_sha256")
        val ledgerBefore = store.ownerValue(SettlementRules.KEY, "{}")
        val ordersBefore =
            PixelMcpReadTools.effective(store.getState()).getJSONArray("orders").length()
        openContext("offers", offer)
        val shareIntent = java.util.concurrent.atomic.AtomicReference<Intent>()
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    val actual =
                        if (intent.action == Intent.ACTION_CHOOSER)
                            androidx.core.content.IntentCompat.getParcelableExtra(
                                intent,
                                Intent.EXTRA_INTENT,
                                Intent::class.java,
                            )
                        else intent
                    if (actual?.action == Intent.ACTION_SEND && actual.type == "application/pdf")
                        shareIntent.set(Intent(actual))
                    return null
                }
            }
        val decisionsBeforeShare =
            read("get_offer_decision_history", JSONObject().put("offer_id", offer)).getInt("total")
        test.addMonitor(monitor)
        try {
            shown("Teilen")
            click(UiTags.PRIMARY)
            shown("Android-Teilen öffnen")
            click("Android-Teilen öffnen")
            waitUntil("OFFER_NATIVE_REAL_ANDROID_SHARE_CHOOSER") {
                shareIntent.get() != null &&
                    automation.rootInActiveWindow?.packageName?.toString()?.let {
                        it != context.packageName
                    } == true
            }
            val shared = requireNotNull(shareIntent.get())
            val uri =
                requireNotNull(
                    androidx.core.content.IntentCompat.getParcelableExtra(
                        shared,
                        Intent.EXTRA_STREAM,
                        Uri::class.java,
                    )
                )
            val sharedBytes =
                context.contentResolver.openInputStream(uri).use { requireNotNull(it).readBytes() }
            yes(
                PhotoAttachmentStore.sha256(sharedBytes) == originalPdf,
                "M06_NATIVE_OFFER_SHARE_OPENS_EXACT_ORIGINAL_PDF_BYTES",
            )
            back()
            waitUntil("OFFER_NATIVE_ANDROID_SHARE_CANCELLED_RETURNS_TO_APP") {
                automation.rootInActiveWindow?.packageName?.toString() == context.packageName
            }
            val afterShare = read("get_offer", JSONObject().put("offer_id", offer))
            yes(
                OfferDecisionRules.contentVersion(afterShare.getJSONObject("offer")) ==
                    OfferDecisionRules.contentVersion(original.getJSONObject("offer")) &&
                    afterShare.getJSONObject("offer").optString("delivery_status") ==
                        "share_prepared" &&
                    read("get_offer_decision_history", JSONObject().put("offer_id", offer))
                        .getInt("total") == decisionsBeforeShare &&
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("orders").length() ==
                        ordersBefore &&
                    store.ownerValue(SettlementRules.KEY, "{}") == ledgerBefore,
                "M06_NATIVE_OFFER_SHARE_CANCEL_NEITHER_ACCEPTS_NOR_SENDS_NOR_CREATES_ORDER_INVOICE_PAYMENT",
            )
            captureSyntheticScreen("offer-share-cancelled")
        } finally {
            test.removeMonitor(monitor)
        }
        reveal("Annehmen")
        click("Annehmen")
        val acceptedAt = java.time.Instant.now().minusSeconds(60).toString()
        val evidence = "[TEST] Persönlich ausdrücklich zugesagte unveränderte Angebotsversion"
        enter(nativeLabel("accepted_at"), acceptedAt)
        enter(nativeLabel("evidence"), evidence)
        if (keyboardVisible()) back()
        val draftKey =
            "native_form_draft:" + PixelMcpWriteTools.hash("action:record_offer_acceptance:$offer")
        waitUntil("OFFER_NATIVE_ACCEPTANCE_ARGS_PERSISTED") {
            JSONObject(store.ownerValue(draftKey, "{}")).optJSONObject("values")?.let {
                it.optString("accepted_at") == acceptedAt &&
                    it.optString("evidence") == evidence &&
                    it.optString("request_id").isNotBlank()
            } == true
        }
        val values = JSONObject(store.ownerValue(draftKey, "{}")).getJSONObject("values")
        val acceptanceArgs = JSONObject()
        for (key in
            LocalWorkflowTools.schema("record_offer_acceptance")
                .getJSONObject("properties")
                .keys()
                .asSequence()) if (values.has(key)) acceptanceArgs.put(key, values.get(key))
        click(UiTags.PRIMARY)
        waitUntil("OFFER_NATIVE_ACCEPTANCE_WRITES_ORDER") {
            read("get_offer", JSONObject().put("offer_id", offer))
                .getJSONObject("offer")
                .optString("converted_order_id")
                .isNotBlank()
        }
        val accepted = read("get_offer", JSONObject().put("offer_id", offer))
        val orderId = accepted.getJSONObject("offer").getString("converted_order_id")
        shown(UiTags.entityDetail("orders", orderId))
        val order = read("get_order", JSONObject().put("order_id", orderId))
        val history = read("get_offer_decision_history", JSONObject().put("offer_id", offer))
        val decision = history.getJSONArray("history").getJSONObject(0)
        yes(
            accepted.getJSONObject("offer").getString("status") == "accepted" &&
                accepted.getJSONObject("offer").getString("pdf_sha256") == originalPdf &&
                order.getString("id") == orderId &&
                order.getString("offer_id") == offer &&
                order.getString("customer_id") == customer &&
                order.getJSONArray("offer_refs").getJSONObject(0).getString("offer_id") == offer &&
                history.getInt("total") == 1 &&
                decision.getString("decision") == "accepted" &&
                decision.getString("accepted_at") == acceptedAt &&
                decision.getString("evidence") == evidence &&
                decision.getString("pdf_sha256") == originalPdf &&
                PixelMcpReadTools.effective(store.getState()).getJSONArray("orders").length() ==
                    ordersBefore + 1 &&
                store.ownerValue(SettlementRules.KEY, "{}") == ledgerBefore,
            "M06_NATIVE_ACCEPTANCE_CREATES_AND_OPENS_EXACTLY_ONE_ORDER_WITH_ORIGINAL_OFFER_EVIDENCE_WITHOUT_INVOICE",
        )
        val replay = read("record_offer_acceptance", acceptanceArgs)
        yes(
            replay.getBoolean("replayed") && replay.getString("order_id") == orderId,
            "M06_NATIVE_ACCEPTANCE_EXACT_REQUEST_REPLAY_RETURNS_SAME_ORDER",
        )
        val duplicate = runCatching {
            tools.call(
                "record_offer_acceptance",
                JSONObject(acceptanceArgs.toString())
                    .put("request_id", "qa_native_acceptance_second_action")
                    .put("expected_record_hash", accepted.getString("record_hash")),
            )
        }
            .exceptionOrNull()
        yes(
            duplicate is PixelMcpWriteTools.Rejected &&
                duplicate.code == "OFFER_DECISION_ALREADY_RECORDED",
            "M06_NATIVE_ACCEPTANCE_SECOND_ACTION_EXPLICITLY_REJECTED",
        )
        recreate()
        yes(
            read("get_offer", JSONObject().put("offer_id", offer))
                .getJSONObject("offer")
                .getString("converted_order_id") == orderId &&
                read("get_order", JSONObject().put("order_id", orderId)).getString("id") ==
                    orderId &&
                read("get_offer_decision_history", JSONObject().put("offer_id", offer))
                    .getInt("total") == 1 &&
                PixelMcpReadTools.effective(store.getState()).getJSONArray("orders").length() ==
                    ordersBefore + 1 &&
                store.ownerValue(SettlementRules.KEY, "{}") == ledgerBefore,
            "M06_NATIVE_ACCEPTANCE_RECREATE_REPLAY_AND_SECOND_ACTION_KEEP_ONE_ORDER_AND_UNALTERED_FINANCE_LEDGER",
        )
    }

    private fun settlementPaymentLifecycleUi() {
        val day = SettlementRules.today()
        val customerName = "[TEST] QA Zahlungsablauf 240"
        val customer =
            read(
                    "create_customer",
                    JSONObject()
                        .put("request_id", "qa_payment_ui_customer")
                        .put("name", customerName)
                        .put("test_record", true)
                        .put("street", "Erfundener Zahlungsweg 4")
                        .put("postal_code", "1000")
                        .put("city", "Synthetischer Ort")
                        .put("country", "AT"),
                )
                .getString("entity_id")
        val workId =
            read(
                    "create_customer_work_record",
                    JSONObject()
                        .put("request_id", "qa_payment_ui_work")
                        .put("customer_id", customer)
                        .put("work_date", day)
                        .put("hours", 1)
                        .put("activity", "[TEST] QA tatsächliche Arbeit im Zahlungsablauf"),
                )
                .getString("work_record_id")
        val workHash =
            PixelMcpWriteTools.hash(
                read("get_work_record", JSONObject().put("work_record_id", workId))
            )
        val title = "[TEST] QA Rechnung Zahlungsablauf 240"
        val initialIds =
            SettlementRules.rows(read("list_settlements").getJSONArray("records"))
                .map { it.getString("id") }
                .toSet()
        openContext("customers", customer)
        selectCustomerTab("Geld")
        reveal("Abrechnen")
        click("Abrechnen")
        shown("Bezeichnung")
        fun edit(row: String, label: String, value: String) {
            reveal(row)
            click(row)
            enter(label, value)
            if (keyboardVisible()) back()
            click("Fertig")
        }
        edit("Bezeichnung", "Bezeichnung", title)
        edit("Preis je Einheit", "Preis je Einheit (€)", "240")
        click("Entwurf sichern")
        waitUntil("M04_NATIVE_RECIPIENT_GROUP_SAVES_ONE_REAL_DRAFT") {
            SettlementRules.rows(read("list_settlements").getJSONArray("records")).count {
                it.optString("title") == title
            } == 1
        }
        val invoice =
            SettlementRules.rows(read("list_settlements").getJSONArray("records"))
                .single { it.optString("title") == title }
                .getString("id")
        fun current() = read("get_settlement", JSONObject().put("settlement_id", invoice))
        val draft = current()
        yes(
            draft.getJSONObject("record").getString("billing_customer_id") == customer &&
                draft.getJSONObject("record").getJSONArray("sources").length() == 1 &&
                draft
                    .getJSONObject("record")
                    .getJSONArray("sources")
                    .getJSONObject(0)
                    .getString("id") == workId &&
                draft.getJSONObject("record").getLong("total_minor") == 24000L &&
                draft.getJSONObject("record").getString("status") == "draft",
            "M04_NATIVE_RECIPIENT_GROUP_ABRECHNEN_PRESERVES_ONE_ORIGINAL_SOURCE_IN_EXACT_240_EUR_DRAFT",
        )
        read(
            "prepare_settlement",
            JSONObject()
                .put("request_id", "qa_payment_ui_prepare")
                .put("settlement_id", invoice)
                .put("expected_review_hash", draft.getString("review_hash"))
                .put("confirmed", true)
                .put("domestic_eur_confirmed", true)
                .put("issuer_details_confirmed", true)
                .put("electronic_delivery_accepted", true),
        )
        read(
            "complete_settlement",
            JSONObject().put("request_id", "qa_payment_ui_complete").put("settlement_id", invoice),
        )
        val original = current().getJSONObject("record")
        val originalPdf = original.getJSONObject("pdf").getString("pdf_sha256")
        openContext("settlement_review", invoice)
        shown(UiTags.DETAIL_MENU)
        var controller: NativeAppController? = null
        test.runOnMainSync {
            val activity = requireNotNull(this.activity) as MainActivity
            val delegated =
                MainActivity::class
                    .java
                    .declaredFields
                    .first { it.name.startsWith("controller") }
                    .apply { isAccessible = true }
                    .get(activity) as androidx.compose.runtime.State<*>
            controller = delegated.value as NativeAppController
        }
        val database = requireNotNull(controller).store.writableDatabase
        val captured = java.util.concurrent.atomic.AtomicReference<String>()
        val trigger = "qa_capture_payment_ui_request"
        // Observe the real durable UI request before its normal writer runs; no alternate sink,
        // money mutation, delay or request injection is used by this diagnostic trigger.
        if (Build.VERSION.SDK_INT >= 30)
            database.setCustomScalarFunction(
                trigger,
                java.util.function.UnaryOperator { raw ->
                    runCatching {
                        val saved = JSONObject(raw)
                        val args = saved.optJSONObject("original_args")
                        if (
                            saved.optString("original_tool") == "add_settlement_payment" &&
                                args?.optString("settlement_id") == invoice
                        )
                            captured.set(args.toString())
                    }
                    raw
                },
            )
        else
            throw AssertionError(
                "Synthetic native payment request capture requires API 30 or newer"
            )
        database.execSQL(
            "CREATE TRIGGER $trigger AFTER INSERT ON owner_state WHEN NEW.key LIKE 'native_form_draft:%' AND instr(NEW.value,'$invoice')>0 AND instr(NEW.value,'add_settlement_payment')>0 BEGIN SELECT $trigger(NEW.value); END"
        )
        fun uiPayment(
            action: String,
            amount: String?,
            kind: String,
            minor: Long,
            reversal: String = "",
        ): JSONObject {
            openContext("settlement_review", invoice)
            val before = current()
            val prior =
                SettlementRules.rows(before.getJSONArray("payments"))
                    .map { it.getString("id") }
                    .toSet()
            click(UiTags.DETAIL_MENU)
            click(UiTags.detailAction(action))
            if (amount != null) enter("Betrag (€)", amount)
            chooseDate("Zahlungsdatum *", day)
            enter("Zahlungsnotiz", "[TEST] QA tatsächlich bestätigter $kind Betrag $minor")
            if (keyboardVisible()) back()
            captured.set(null)
            click(UiTags.PRIMARY)
            waitUntil("PAYMENT_UI_REAL_${kind}_${prior.size}_COMMITTED") {
                current().getJSONArray("payments").length() == prior.size + 1 &&
                    captured.get() != null
            }
            val args = JSONObject(requireNotNull(captured.get()))
            val after = current()
            val entry =
                SettlementRules.rows(after.getJSONArray("payments")).single {
                    it.getString("id") !in prior
                }
            yes(
                args.getString("expected_payment_hash") ==
                    before.getString("payment_record_hash") &&
                    args.getString("expected_record_hash") == before.getString("record_hash") &&
                    SettlementRules.minor(args.get("amount")) == minor &&
                    args.getString("payment_kind") == kind &&
                    args.optString("reversal_of") == reversal &&
                    entry.getLong("amount_minor") == minor &&
                    entry.getString("payment_kind") == kind &&
                    entry.getString("date") == day &&
                    entry.optString("reversal_of") == reversal &&
                    after.getJSONObject("record").getJSONObject("pdf").getString("pdf_sha256") ==
                        originalPdf,
                "PAYMENT_UI_${kind}_${prior.size}_HAS_EXACT_DATED_AMOUNT_ORIGINAL_PAYMENT_REFERENCE_AND_UNCHANGED_PDF",
            )
            val stable = after.getJSONArray("payments").toString()
            yes(
                read("add_settlement_payment", args).getBoolean("replayed") &&
                    current().getJSONArray("payments").toString() == stable,
                "PAYMENT_UI_${kind}_${prior.size}_EXACT_REAL_REQUEST_REPLAY_CREATES_NO_DUPLICATE",
            )
            return entry
        }
        fun showCurrentMoney() {
            // Reload the actual review after the committed write before capturing its state.
            openContext("settlements")
            openContext("settlement_review", invoice)
            revealContaining("Bezahlt:")
        }
        try {
            val partial = uiPayment("settlement-payment", "100", "receipt", 10000L)
            var state = current()
            yes(
                state.getLong("paid_minor") == 10000L &&
                    state.getLong("open_minor") == 14000L &&
                    state.getLong("credit_minor") == 0L &&
                    state.getLong("refund_due_minor") == 0L,
                "FIN16_NATIVE_UI_240_INVOICE_100_ACTUAL_RECEIPT_READS_EXACT_140_REMAINING",
            )
            showCurrentMoney()
            captureSyntheticScreen("payment-partial-100-open-140")
            val correction100 =
                uiPayment(
                    "settlement-reverse:${partial.getString("id")}",
                    null,
                    "correction",
                    -10000L,
                    partial.getString("id"),
                )
            yes(
                current().getLong("paid_minor") == 0L &&
                    current().getLong("open_minor") == 24000L &&
                    correction100.getString("reversal_of") == partial.getString("id"),
                "PAYMENT_UI_FULL_100_CORRECTION_RETAINS_ORIGINAL_AND_REOPENS_EXACT_240",
            )
            val receipt120 = uiPayment("settlement-payment", "120", "receipt", 12000L)
            uiPayment(
                "settlement-reverse:${receipt120.getString("id")}",
                null,
                "correction",
                -12000L,
                receipt120.getString("id"),
            )
            state = current()
            yes(
                state.getLong("paid_minor") == 0L &&
                    state.getLong("open_minor") == 24000L &&
                    SettlementRules.rows(state.getJSONArray("payments")).count {
                        it.optString("reversal_of") == receipt120.getString("id")
                    } == 1,
                "FIN17_NATIVE_UI_FULL_PLUS120_MINUS120_CORRECTION_READS_PAID_ZERO_OPEN_240_AND_RETAINED_ORIGINALS",
            )
            showCurrentMoney()
            captureSyntheticScreen("payment-full-correction-120-open-240")
            val refundable = uiPayment("settlement-payment", "120", "receipt", 12000L)
            val beforeStorno = current()
            val correction =
                read(
                        "correct_settlement",
                        JSONObject()
                            .put("request_id", "qa_payment_ui_storno_prepare")
                            .put("settlement_id", invoice)
                            .put("expected_record_hash", beforeStorno.getString("record_hash"))
                            .put(
                                "reason",
                                "[TEST] Ausdrücklich bestätigtes vollständiges Storno im Rückzahlungsablauf",
                            )
                            .put("confirmed", true),
                    )
                    .getString("settlement_id")
            read(
                "complete_settlement",
                JSONObject()
                    .put("request_id", "qa_payment_ui_storno_complete")
                    .put("settlement_id", correction),
            )
            state = current()
            yes(
                state.getJSONObject("record").getString("status") == "cancelled" &&
                    state.getLong("paid_minor") == 12000L &&
                    state.getLong("refund_due_minor") == 12000L &&
                    state.getJSONArray("payments").toString() ==
                        beforeStorno.getJSONArray("payments").toString(),
                "PAYMENT_UI_ACTUAL_STORNO_RETAINS_120_RECEIPT_AND_OPENS_120_REFUND_WITHOUT_AUTOMATIC_CASH_MOVEMENT",
            )
            openContext("settlement_review", invoice)
            revealContaining("Rückzahlung offen:")
            captureSyntheticScreen("payment-storno-refund-due-120")
            uiPayment(
                "settlement-refund:${refundable.getString("id")}",
                null,
                "refund",
                -12000L,
                refundable.getString("id"),
            )
            state = current()
            val storno =
                read("get_settlement", JSONObject().put("settlement_id", correction))
                    .getJSONObject("record")
            val retained =
                JSONObject(state.getJSONObject("record").toString())
                    .put("status", original.getString("status"))
            retained.remove("corrected_by")
            yes(
                state.getLong("paid_minor") == 0L &&
                    state.getLong("open_minor") == 0L &&
                    state.getLong("refund_due_minor") == 0L &&
                    state.getJSONArray("payments").length() == 6 &&
                    storno.getString("corrects") == invoice &&
                    storno.getLong("total_minor") == -24000L &&
                    PixelMcpWriteTools.hash(retained) == PixelMcpWriteTools.hash(original) &&
                    PixelMcpWriteTools.hash(
                        read("get_work_record", JSONObject().put("work_record_id", workId))
                    ) == workHash &&
                    SettlementRules.rows(read("list_settlements").getJSONArray("records"))
                        .map { it.getString("id") }
                        .toSet() == initialIds + invoice + correction,
                "F10_NATIVE_UI_REFUND_RETAINS_ALL_SIX_LINKED_ORIGINAL_MOVEMENTS_PDFS_AND_SOURCE_WITH_ZERO_NET_AND_ZERO_REFUND_DUE",
            )
            read("get_settlement_pdf", JSONObject().put("settlement_id", invoice))
            read("get_settlement_pdf", JSONObject().put("settlement_id", correction))
            database.execSQL("DROP TRIGGER IF EXISTS $trigger")
            recreate()
            revealContaining("Bezahlt:")
            captureSyntheticScreen("payment-real-refund-balanced")
            yes(
                current().getJSONArray("payments").length() == 6 &&
                    current().getLong("paid_minor") == 0L,
                "PAYMENT_UI_RECREATE_PRESERVES_SIX_MOVEMENTS_WITHOUT_REPEATING_REFUND",
            )
        } finally {
            if (database.isOpen) database.execSQL("DROP TRIGGER IF EXISTS $trigger")
        }
    }

    private fun orderAndInquiryCreationUi() {
        // The caller is an existing journey in guardGraphicsFixture's empty disposable QA app.
        yes(
            read("get_active_work_session").optString("state") == "IDLE",
            "ORDER_UI_STARTS_WITHOUT_ACTIVE_SESSION",
        )
        val customerName = "[TEST] QA Anfrage und Auftrag UI"
        val customer =
            read(
                    "create_customer",
                    JSONObject()
                        .put("request_id", "qa_native_order_inquiry_customer")
                        .put("name", customerName)
                        .put("test_record", true),
                )
                .getString("entity_id")
        val inquiryTitle = "[TEST] QA bewusst gespeicherte Anfrage"
        val orderTitle = "[TEST] QA bewusst gespeicherter Auftrag"
        val description = "[TEST] Tatsächlich bestätigter synthetischer Auftragsumfang"
        val ledgerBefore = store.ownerValue(SettlementRules.KEY, "{}")
        val workBefore =
            PixelMcpWriteTools.hash(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            )
        fun chooseCustomer() {
            click("Kunde: Nicht zugeordnet")
            enter("Suchen", "Anfrage und Auftrag UI")
            click(customerName)
        }
        fun inquiries() =
            SettlementRules.rows(
                read("list_inquiries", JSONObject().put("customer_id", customer))
                    .getJSONArray("items")
            )
        fun orders() =
            SettlementRules.rows(
                read(
                        "search_orders",
                        JSONObject().put("customer_id", customer).put("query", orderTitle),
                    )
                    .getJSONArray("orders")
            )
        openOffice()
        click("Anfragen")
        val inquiryFilter = "QA"
        enter("Suchen", inquiryFilter)
        click("Anfrage anlegen")
        chooseCustomer()
        enter("Bezeichnung *", inquiryTitle)
        enter("Anliegen", "[TEST] Ausdrücklich erfasster synthetischer Kundenwunsch")
        if (keyboardVisible()) back()
        click(UiTags.PRIMARY)
        waitUntil("M05_NATIVE_INQUIRY_ACTUALLY_SAVED") {
            inquiries().count { it.optString("title") == inquiryTitle } == 1
        }
        val inquiryId = inquiries().single { it.getString("title") == inquiryTitle }.getString("id")
        val inquiry =
            read("get_inquiry", JSONObject().put("inquiry_id", inquiryId)).getJSONObject("record")
        yes(
            inquiry.getString("customer_id") == customer &&
                inquiry.getString("title") == inquiryTitle &&
                inquiry.optString("order_id").isBlank() &&
                inquiry.optString("offer_id").isBlank(),
            "M05_NATIVE_CREATE_SAVES_ONE_LINKED_INQUIRY_WITHOUT_AUTOMATIC_OFFER_OR_ORDER",
        )
        openOffice()
        click("Anfragen")
        waitUntil("M05_NATIVE_MENU_RETURNS_TO_OVERVIEW_WITH_RETAINED_LIST_FILTER") {
            field("Suchen")?.text?.toString() == inquiryFilter
        }
        enter("Suchen", inquiryTitle)
        shown(UiTags.recordChoice("inquiries", inquiryId))
        captureSyntheticScreen("inquiry-created-list-readback")
        val beforeInquiryOpen =
            PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
        click(UiTags.recordChoice("inquiries", inquiryId))
        waitUntil("M05_NATIVE_NEW_ORIGINAL_INQUIRY_DETAIL_LOADED") {
            field("Suchen") == null &&
                node("Zur Liste") != null &&
                node(inquiry.getString("title")) != null
        }
        reveal("Kunde: $customerName")
        reveal(inquiry.getString("concern"))
        val inquiryAfterOpen =
            read("get_inquiry", JSONObject().put("inquiry_id", inquiryId)).getJSONObject("record")
        yes(
            inquiryAfterOpen.getString("id") == inquiryId &&
                inquiryAfterOpen.getString("record_hash") == inquiry.getString("record_hash") &&
                PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) ==
                    beforeInquiryOpen,
            "M05_NATIVE_OPENED_ORIGINAL_INQUIRY_DETAIL_RETAINS_ID_VALUES_AND_BUSINESS_STATE",
        )
        scrollTop()
        shown(inquiry.getString("title"))
        captureSyntheticScreen("inquiry-created-detail-readback")
        openOffice()
        click("Aufträge")
        click("Auftrag anlegen")
        chooseCustomer()
        enter("Bezeichnung *", orderTitle)
        enter("Beschreibung", description)
        if (keyboardVisible()) back()
        click(UiTags.PRIMARY)
        waitUntil("M07_NATIVE_ORDER_ACTUALLY_SAVED") {
            orders().count { it.optString("title") == orderTitle } == 1
        }
        val orderId = orders().single { it.getString("title") == orderTitle }.getString("id")
        val order = read("get_order", JSONObject().put("order_id", orderId))
        yes(
            order.getString("customer_id") == customer &&
                order.getString("description") == description,
            "M07_NATIVE_CREATE_SAVES_ONE_ORDER_WITH_ACTUAL_CUSTOMER_AND_DESCRIPTION",
        )
        fun openOrder() {
            openOffice()
            click("Aufträge")
            enter("Suchen", orderTitle)
            click(orderTitle)
            shown(UiTags.entityDetail("orders", orderId))
        }
        openOrder()
        openDetails("Auftrag")
        reveal(description)
        captureSyntheticScreen("order-created-readback")
        val planningFilter =
            JSONObject()
                .put("date_from", "2020-01-01")
                .put("date_to", "2040-12-31")
                .put("customer_id", customer)
                .put("order_id", orderId)
        val planningBefore =
            read("get_planning_board", planningFilter).getJSONArray("items").toString()
        val orderBeforePlanning =
            PixelMcpWriteTools.hash(read("get_order", JSONObject().put("order_id", orderId)))
        click(UiTags.CREATE)
        click("Termin anlegen")
        shown("Termin planen")
        revealContaining(customerName)
        revealContaining(orderTitle)
        // Observe the route reached by the real Plus action; never navigate or inject context.
        var planningArgs = JSONObject()
        test.runOnMainSync {
            val current = requireNotNull(activity) as MainActivity
            val delegate =
                MainActivity::class
                    .java
                    .declaredFields
                    .first { it.name.startsWith("controller") }
                    .apply { isAccessible = true }
                    .get(current) as androidx.compose.runtime.State<*>
            planningArgs = JSONObject((delegate.value as NativeAppController).route.args.toString())
        }
        yes(
            planningArgs.getString("customer_id") == customer &&
                planningArgs.getString("order_id") == orderId &&
                (order.optString("billing_customer_id").isBlank() ||
                    planningArgs.optString("billing_customer_id") ==
                        order.getString("billing_customer_id")),
            "M07_NATIVE_ORDER_PLUS_PLANNING_CARRIES_EXACT_ORIGINAL_CUSTOMER_ORDER_AND_BILLING_CONTEXT",
        )
        captureSyntheticScreen("order-planning-context")
        click("Ungespeicherten Entwurf verwerfen")
        waitUntil("M07_NATIVE_PLANNING_CANCEL_CLEARS_UNSAVED_DRAFT") {
            store
                .ownerValue(
                    "native_form_draft:" + PixelMcpWriteTools.hash("planning:appointment:new"),
                    "",
                )
                .isBlank()
        }
        yes(
            read("get_planning_board", planningFilter).getJSONArray("items").toString() ==
                planningBefore &&
                PixelMcpWriteTools.hash(read("get_order", JSONObject().put("order_id", orderId))) ==
                    orderBeforePlanning,
            "M07_NATIVE_PLANNING_OPEN_AND_DISCARD_CREATE_NO_PHANTOM_OR_ORDER_CHANGE",
        )
        openOrder()
        var sessionId = ""
        try {
            click("Arbeit starten")
            waitUntil("M07_NATIVE_ORDER_FOOTER_STARTS_REAL_SESSION") {
                val active = read("get_active_work_session")
                if (active.optString("state") == "RUNNING") sessionId = active.getString("id")
                sessionId.isNotBlank()
            }
            val active = read("get_active_work_session")
            yes(
                active.getString("customer_id") == customer &&
                    active.getString("order_id") == orderId &&
                    PixelMcpWriteTools.hash(
                        PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
                    ) == workBefore,
                "M07_NATIVE_ORDER_WORK_START_READS_EXACT_SESSION_CONTEXT_WITHOUT_SAVING_WORK",
            )
            shown("Arbeit läuft")
            captureSyntheticScreen("order-started-session")
        } finally {
            if (sessionId.isBlank()) {
                val active = read("get_active_work_session")
                if (
                    active.optString("customer_id") == customer &&
                        active.optString("order_id") == orderId &&
                        active.optString("state") in
                            setOf("RUNNING", "PAUSED", "STOPPED_PENDING_REVIEW")
                )
                    sessionId = active.getString("id")
            }
            if (sessionId.isNotBlank())
                read(
                    "discard_work_session",
                    JSONObject()
                        .put("request_id", "qa_native_order_session_cleanup")
                        .put("session_id", sessionId),
                )
        }
        yes(
            read("get_active_work_session").optString("state") == "IDLE" &&
                PixelMcpWriteTools.hash(
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
                ) == workBefore &&
                store.ownerValue(SettlementRules.KEY, "{}") == ledgerBefore &&
                inquiries().size == 1 &&
                orders().size == 1,
            "M05_M07_NATIVE_CREATION_RETAINS_ONE_INQUIRY_AND_ORDER_WITH_EXPLICIT_SESSION_CLEANUP_NO_FINANCIAL_SIDE_EFFECT",
        )
    }

    private fun executeAccounting(reopen: Boolean): JSONObject {
        yes(
            test.context.packageName == "at.hausgarten.service.test" &&
                context.packageName == "at.hausgarten.service",
            "ACCOUNTING_TEST_AND_TARGET_PACKAGES",
        )
        val marker = File(context.noBackupFilesDir, "qa-native-accounting.json")
        if (reopen) {
            yes(
                marker.isFile &&
                    JSONObject(marker.readText()).optString("fixture") ==
                        "synthetic-eisbaer-accounting-v1",
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
            val marked =
                read(
                        "get_lcr_work_records",
                        JSONObject().put("work_record_id", "qa_settlement_old_unbound"),
                    )
                    .getJSONArray("records")
                    .getJSONObject(0)
            yes(
                marked.getBoolean("lcr") &&
                    read("get_finance_overview").getLong("issued_minor") == 90000L &&
                    read("get_finance_overview").getLong("paid_minor") == 90000L,
                "ACCOUNTING_NEW_PROCESS_LCR_AND_DOCUMENT_PRESERVED",
            )
            return JSONObject()
                .put("checks", checks)
                .put("executed_mcp_tools", JSONArray(executedMcpTools.toList()))
                .put("synthetic_only", true)
                .put("productive_data_read", false)
                .put("new_process_readback", true)
        }
        guardGraphicsFixture()
        store.putOwnerValue("snapshot", NativeSettlementNativeTests.fixture().toString())
        store.putOwnerValue("personal_paid_migrated", "1")
        store.putOwnerValue("personal_paid", JSONArray().put("qa_settlement_labor").toString())
        val historical =
            seedHistoricalLabor(
                NativeSettlementNativeTests.draft("labor", "200", "qa_settlement_labor"),
                "qa_accounting_historical_labor",
            )
        val invoice =
            read(
                    "save_settlement_draft",
                    JSONObject()
                        .put("request_id", "qa_eisbaer_accounting_invoice")
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
        val review = read("get_settlement", JSONObject().put("settlement_id", invoice))
        read(
            "prepare_settlement",
            JSONObject()
                .put("request_id", "qa_eisbaer_accounting_prepare")
                .put("settlement_id", invoice)
                .put("expected_review_hash", review.getString("review_hash"))
                .put("confirmed", true)
                .put("domestic_eur_confirmed", true)
                .put("issuer_details_confirmed", true)
                .put("electronic_delivery_accepted", false),
        )
        read(
            "complete_settlement",
            JSONObject()
                .put("request_id", "qa_eisbaer_accounting_complete")
                .put("settlement_id", invoice),
        )
        val sourceHash = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
        val originalLedger = store.ownerValue(SettlementRules.KEY, "{}")
        val originalMarks = store.ownerValue("personal_paid", "[]")
        val historicalHash =
            PixelMcpWriteTools.hash(
                read("get_settlement", JSONObject().put("settlement_id", historical))
                    .getJSONObject("record")
            )
        val previous = automation.serviceInfo
        val previousFlags = previous.flags
        val oldFont = shell("settings get system font_scale")
        try {
            automation.serviceInfo = previous.apply {
                flags =
                    flags or
                        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            launch()
            openContext("worklogs", "qa_settlement_old_unbound")
            reveal("work-lcr")
            click("work-lcr")
            waitUntil("ACCOUNTING_UI_LCR_MARKED") {
                AccountingRules.ids(JSONArray(store.ownerValue(SettlementRules.LCR_KEY, "[]")))
                    .contains("qa_settlement_old_unbound")
            }
            yes(
                PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) ==
                    sourceHash &&
                    store.ownerValue(SettlementRules.KEY, "{}") == originalLedger &&
                    store.ownerValue("personal_paid", "[]") == originalMarks,
                "ACCOUNTING_LCR_UI_CHANGES_NO_ORIGINAL_LEDGER_PAYMENT_OR_HISTORICAL_MARK",
            )
            val summary =
                read("get_work_record_summary", JSONObject().put("group_by_billing_customer", true))
            yes(
                SettlementRules.rows(summary.getJSONArray("billing_groups"))
                    .flatMap { SettlementRules.rows(it.getJSONArray("work_records")) }
                    .none { it.optString("id") == "qa_settlement_old_unbound" },
                "ACCOUNTING_MARKED_ORIGINAL_EXCLUDED_ONLY_FROM_OPEN_BILLING",
            )
            openContext("lcr")
            reveal("[TEST] Tatsächliche synthetische Gartenarbeit qa_settlement_old_unbound")
            captureSyntheticScreen("lcr-original-reference-list")
            click("[TEST] Tatsächliche synthetische Gartenarbeit qa_settlement_old_unbound")
            shown(UiTags.entityDetail("worklogs", "qa_settlement_old_unbound"))
            reveal("work-lcr")
            @Suppress("DEPRECATION") // API 35 exposes the actual switch state through isChecked.
            fun lcrControlChecked(checked: Boolean) =
                node("work-lcr")?.let { it.isCheckable && it.isChecked == checked } == true
            waitStable("ACCOUNTING_REFERENCE_OPENS_SAME_ORIGINAL_WITH_VISIBLE_ACTIVE_LCR") {
                lcrControlChecked(true) && actionEnabled("work-lcr")
            }
            captureSyntheticScreen("lcr-original-marked-detail")
            click("work-lcr")
            waitUntil("ACCOUNTING_UI_LCR_UNMARKED") {
                !AccountingRules.ids(JSONArray(store.ownerValue(SettlementRules.LCR_KEY, "[]")))
                    .contains("qa_settlement_old_unbound")
            }
            val eligible =
                read("get_work_record_summary", JSONObject().put("group_by_billing_customer", true))
            yes(
                SettlementRules.rows(eligible.getJSONArray("billing_groups"))
                    .flatMap { SettlementRules.rows(it.getJSONArray("work_records")) }
                    .any { it.optString("id") == "qa_settlement_old_unbound" },
                "ACCOUNTING_UNMARKED_ORIGINAL_RETURNS_TO_OPEN_BILLING",
            )
            click("work-lcr")
            waitUntil("ACCOUNTING_UI_LCR_REMARKED") {
                AccountingRules.ids(JSONArray(store.ownerValue(SettlementRules.LCR_KEY, "[]")))
                    .contains("qa_settlement_old_unbound")
            }
            openContext("worklogs", "qa_settlement_invoice")
            shown(UiTags.entityDetail("worklogs", "qa_settlement_invoice"))
            val boundOriginal =
                read(
                        "get_lcr_work_records",
                        JSONObject().put("work_record_id", "qa_settlement_invoice"),
                    )
                    .getJSONArray("records")
                    .getJSONObject(0)
            yes(
                boundOriginal.getString("id") == "qa_settlement_invoice" &&
                    !boundOriginal.getBoolean("lcr") &&
                    !boundOriginal.getBoolean("lcr_mutable") &&
                    boundOriginal.getString("lcr_block_reason").isNotBlank(),
                "ACCOUNTING_ORIGINAL_HAS_ACTUAL_RETAINED_DOCUMENT_BINDING",
            )
            revealContaining(boundOriginal.getString("lcr_block_reason"))
            fun boundControlReady(): Boolean {
                val control = node("work-lcr") ?: return false
                val bounds = Rect().also { control.getBoundsInScreen(it) }
                val frame = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                val top = runCatching { actionBounds(UiTags.BACK).bottom }.getOrDefault(frame.top)
                val bottom = runCatching {
                    actionBounds(UiTags.PRIMARY).top
                }
                    .getOrDefault(frame.bottom)
                return control.isVisibleToUser &&
                    !bounds.isEmpty &&
                    frame.contains(bounds) &&
                    bounds.top >= top &&
                    bounds.bottom <= bottom &&
                    lcrControlChecked(false) &&
                    runCatching { !actionEnabled("work-lcr") }.getOrDefault(false)
            }
            var lastBoundScroll = 0L
            waitUntil("ACCOUNTING_BOUND_CONTROL_VISIBLE_AFTER_DETAIL_AND_BINDING_LOAD") {
                if (boundControlReady()) true
                else {
                    if (SystemClock.uptimeMillis() - lastBoundScroll > 250) {
                        val bounds =
                            node("work-lcr")?.let { control ->
                                Rect().also { control.getBoundsInScreen(it) }
                            }
                        val top = runCatching { actionBounds(UiTags.BACK).bottom }.getOrDefault(0)
                        if (bounds != null && bounds.top < top)
                            scrollTargets(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD).any {
                                performVerticalScroll(it, false)
                            }
                        else scroll()
                        lastBoundScroll = SystemClock.uptimeMillis()
                    }
                    false
                }
            }
            waitStable("ACCOUNTING_DOCUMENT_BOUND_LCR_CONTROL_LOCKED") { boundControlReady() }
            shown(boundOriginal.getString("lcr_block_reason"))
            captureSyntheticScreen("lcr-original-bound-detail")
            val rejectedMark = runCatching {
                tools.call(
                    "set_work_record_lcr",
                    JSONObject()
                        .put("request_id", "qa_accounting_bound_lcr_rejected")
                        .put("work_record_id", "qa_settlement_invoice")
                        .put("lcr", true)
                        .put("expected_record_hash", boundOriginal.getString("lcr_record_hash")),
                )
            }
            yes(
                rejectedMark.isFailure || rejectedMark.getOrNull()?.optBoolean("ok") == false,
                "ACCOUNTING_BOUND_ORIGINAL_REJECTS_MARKER_WRITE_THROUGH_SHARED_MCP_PATH",
            )
            yes(
                !read(
                        "get_lcr_work_records",
                        JSONObject().put("work_record_id", "qa_settlement_invoice"),
                    )
                    .getJSONArray("records")
                    .getJSONObject(0)
                    .getBoolean("lcr") &&
                    store.ownerValue(SettlementRules.KEY, "{}") == originalLedger,
                "ACCOUNTING_REJECTED_BOUND_MARKER_PRESERVES_ORIGINAL_AND_LEDGER",
            )
            yes(
                PixelMcpWriteTools.hash(
                    read("get_settlement", JSONObject().put("settlement_id", historical))
                        .getJSONObject("record")
                ) == historicalHash,
                "ACCOUNTING_HISTORICAL_ORIGINAL_NOT_REINTERPRETED",
            )
            val report = read("get_finance_overview")
            yes(
                report.getLong("issued_minor") == 90000L &&
                    report.getLong("paid_minor") == 0L &&
                    report.getLong("open_minor") == 90000L,
                "ACCOUNTING_DOCUMENT_PAYMENT_AND_OPEN_AMOUNTS_SEPARATE",
            )
            for (font in listOf("1.0", "2.0")) for (wide in listOf(false, true)) {
                activity?.let { current ->
                    test.runOnMainSync { if (!current.isFinishing) current.finish() }
                }
                shell("settings put system font_scale $font")
                launch()
                openAccountingTotals()
                val expectedOrientation =
                    if (wide) android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    else android.content.res.Configuration.ORIENTATION_PORTRAIT
                test.runOnMainSync {
                    activity?.requestedOrientation =
                        if (wide) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                waitUntil("ACCOUNTING_FONT_${font}_AND_ORIENTATION_${wide}_APPLIED") {
                    var applied = false
                    test.runOnMainSync {
                        val configuration = activity?.resources?.configuration
                        applied =
                            configuration != null &&
                                kotlin.math.abs(configuration.fontScale - font.toFloat()) < 0.01f &&
                                configuration.orientation == expectedOrientation
                    }
                    applied
                }
                settleUi()
                val totals =
                    listOf(
                        Triple("issued_minor", "ausgestellt", "900,00 €"),
                        Triple("paid_minor", "bezahlt · diese Belege", "0,00 €"),
                        Triple("open_minor", "offen · diese Belege", "900,00 €"),
                    )
                for ((key, label, amount) in totals) {
                    val tag = "accounting-total:$key"
                    reveal(tag)
                    node(tag)
                        ?.performAction(
                            AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id
                        )
                    settleUi()
                    textWithinOwnScrollViewport(label, tag)
                    textWithinOwnScrollViewport(amount, tag)
                    waitStable("ACCOUNTING_TOTAL_${font}_${wide}_${key}_COMPLETE_WITHOUT_OVERLAP") {
                        val current = nodes()
                        fun bounds(tag: String): Rect? =
                            current
                                .firstOrNull { it.isVisibleToUser && matches(it, tag) }
                                ?.let {
                                    Rect().also(it::getBoundsInScreen).takeUnless(Rect::isEmpty)
                                }
                        val cell = bounds(tag) ?: return@waitStable false
                        val caption =
                            bounds("accounting-total-label:$key") ?: return@waitStable false
                        val value = bounds("accounting-total-value:$key") ?: return@waitStable false
                        val cells = totals.mapNotNull { bounds("accounting-total:${it.first}") }
                        cell.contains(caption) &&
                            cell.contains(value) &&
                            caption.bottom <= value.top &&
                            cells.indices.all { left ->
                                (left + 1 until cells.size).all { right ->
                                    !Rect.intersects(cells[left], cells[right])
                                }
                            }
                    }
                }
                reveal("900,00 €")
                reveal("ausgestellt")
                shown("ausgestellt")
                reveal("bezahlt · diese Belege")
                shown("bezahlt · diese Belege")
                reveal("offen · diese Belege")
                shown("offen · diese Belege")
                if (font == "2.0" && wide) {
                    val railLabel =
                        textWithinOwnScrollViewport("Abrechnung", UiTags.area("accounting"))
                    yes(
                        railLabel.second.contains(railLabel.first) && railLabel.first.height() > 0,
                        "ACCOUNTING_200_LANDSCAPE_LAST_NAV_LABEL_REACHABLE_IN_OWN_RAIL",
                    )
                    captureSyntheticScreen("eisbaer-accounting-200-landscape-rail-visible")
                }
                captureSyntheticScreen(
                    "eisbaer-accounting-${if (font == "2.0") "200" else "100"}-${if (wide) "landscape" else "portrait"}"
                )
            }
            // The asynchronously loaded invoices do not change a disclosure's saved state.
            // Open the actual group before acting on its payment control.
            openDetails("Zahlung offen")
            reveal("Bezahlt")
            click("Bezahlt")
            click("Rückgängig")
            waitUntil("ACCOUNTING_PAID_ACTION_UNDONE_BEFORE_BOOKING") {
                SettlementRules.rows(read("get_list_actions").getJSONArray("actions")).any {
                    it.optString("original_tool") == "add_settlement_payment" &&
                        it.optString("state") == "undone"
                }
            }
            yes(
                read("get_settlement", JSONObject().put("settlement_id", invoice))
                    .getLong("paid_minor") == 0L,
                "ACCOUNTING_PAID_UNDO_CREATES_NO_PAYMENT",
            )
            reveal("Bezahlt")
            waitUntil("ACCOUNTING_PAID_BUTTON_READY_AFTER_UNDO") { actionEnabled("Bezahlt") }
            click("Bezahlt")
            waitUntil("ACCOUNTING_PAID_BUTTON_COMMITS_ONCE_AFTER_FIVE_SECONDS") {
                read("get_settlement", JSONObject().put("settlement_id", invoice))
                    .getLong("paid_minor") == 90000L
            }
            recreate()
            val paid = read("get_settlement", JSONObject().put("settlement_id", invoice))
            yes(
                paid.getJSONArray("payments").length() == 1 && paid.getLong("open_minor") == 0L,
                "ACCOUNTING_REAL_PAID_BUTTON_AND_RECREATE_NEVER_DUPLICATE_PAYMENT",
            )
            marker.writeText(
                JSONObject()
                    .put("fixture", "synthetic-eisbaer-accounting-v1")
                    .put("snapshot_hash", sourceHash)
                    .toString()
            )
            return JSONObject()
                .put("checks", checks)
                .put("executed_mcp_tools", JSONArray(executedMcpTools.toList()))
                .put("synthetic_only", true)
                .put("productive_data_read", false)
                .put("new_process_readback", false)
                .put("real_main_activity", true)
                .put("lcr_original_reference", true)
                .put("historical_original_preservation", true)
                .put("viewport_and_large_text", true)
        } catch (failure: Throwable) {
            runCatching { captureSyntheticScreen("eisbaer-accounting-failure") }
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

    private fun executeGraphics(selectedJourney: String): JSONObject {
        guardGraphicsFixture()
        val previousInfo = automation.serviceInfo
        val previousFlags = previousInfo.flags
        val oldFont = shell("settings get system font_scale")
        try {
            automation.serviceInfo = previousInfo.apply {
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
                .put("executed_mcp_tools", JSONArray(executedMcpTools.toList()))
                .put("synthetic_only", true)
                .put("productive_data_read", false)
                .put("independent_fresh_fixture", true)
                .put("backup_provider_prerequisite", false)
                .put("real_main_activity", true)
                .put("native_compose_ui", true)
                .put("graphics_cases", JSONArray().put(selectedJourney))
                .put("physical_pixel_tested", physicalPixelTested())
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
        shown("Gespeichert")
        click(prefix + "1")
        shown("Tätigkeit")
        yes(
            node("Arbeitsdatum") == null && field("Beginn") == null,
            "WORK_ACTIVITY_EDITOR_ISOLATED",
        )
        enter("Tätigkeit", prefix + "1 korrigiert")
        if (keyboardVisible()) back()
        click("Fertig")
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
        openDetails("Arbeit")
        reveal("Notizen")
        click("Notizen")
        shown("Notizen")
        yes(field("Tätigkeit") == null && field("Beginn") == null, "WORK_NOTE_EDITOR_ISOLATED")
        enter("Notizen", "[TEST] Einzelne Notiz korrigiert")
        if (keyboardVisible()) back()
        click("Fertig")
        waitUntil("WORK_NOTE_SAVED") {
            first().optString("notes") == "[TEST] Einzelne Notiz korrigiert"
        }
        reveal("Pause in Minuten")
        click("Pause in Minuten")
        enter("Pause in Minuten", "15")
        if (keyboardVisible()) back()
        click("Fertig")
        waitUntil("WORK_PAUSE_RECALCULATES_DURATION") {
            first().getInt("break_minutes") == 15 && first().getDouble("hours") == 3.75
        }
        // Design step 3: the next step of open work is to bill it; it opens the settlement editor.
        click(UiTags.PRIMARY)
        shown("Rechnung")
        back()
        waitUntil("WORK_DETAIL_AFTER_BILLING_EDITOR") { node(UiTags.DETAIL_MENU) != null }
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
        shown("Termine heute")
        yes(node("Büro") == null, "FOUR_MAIN_AREAS_WITHOUT_OFFICE_TAB")
        for ((label, rootText) in
            listOf(
                "planning" to UiTags.PRIMARY,
                "customers" to "Suchen",
                "accounting" to "Offen abzurechnen",
                "today" to "Termine heute",
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
            shown("Termine heute")
            yes(activity?.isFinishing == false, "MAIN_BACK_STAYS_IN_APP_$label")
        }
        click(UiTags.MORE)
        shown("Vorgänge")
        back()
        shown("Termine heute")
        openCustomerList()
        searchCustomers(fixturePrefix)
        shown("55 Treffer")
        shown(fixturePrefix + "000")
        captureSyntheticScreen("graphics-customers")
        searchCustomers(fixturePrefix + "000")
        click(fixturePrefix + "000")
        shown(fixturePrefix + "000")
        visibleResult("Arbeit starten")
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
            val record = runCatching {
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

    private var foregroundDiagnostic = "No foreground check yet"

    /** A Compose object or cached A11y package does not prove a usable focused window. */
    private fun uiForegroundReady(): Boolean {
        val interactive =
            context.getSystemService(android.os.PowerManager::class.java)?.isInteractive == true
        val unlocked =
            context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked ==
                false
        var resumed = false
        var attachedVisible = false
        var baseFocused = false
        test.runOnMainSync {
            activity
                ?.takeUnless { it.isFinishing || it.isDestroyed }
                ?.let { current ->
                    resumed =
                        (current as? androidx.lifecycle.LifecycleOwner)
                            ?.lifecycle
                            ?.currentState
                            ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true
                    val decor = current.window.decorView
                    attachedVisible =
                        decor.isAttachedToWindow &&
                            decor.isShown &&
                            decor.windowVisibility == View.VISIBLE &&
                            hasCompose(decor)
                    baseFocused = current.hasWindowFocus() && decor.hasWindowFocus()
                }
        }
        val windows = automation.windows
        // NativeValueSheet/DatePicker may legitimately own focus over their MainActivity.
        val ownedDialogOrMainFocused = windows.any { window ->
            window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                window.isFocused &&
                window.root?.packageName?.toString() == context.packageName
        }
        val rootPackage = automation.rootInActiveWindow?.packageName?.toString()
        foregroundDiagnostic =
            "interactive=$interactive unlocked=$unlocked resumed=$resumed attachedVisible=$attachedVisible baseFocused=$baseFocused ownedFocused=$ownedDialogOrMainFocused root=$rootPackage windows=" +
                windows.joinToString {
                    "${it.id}:${it.type}:active=${it.isActive}:focused=${it.isFocused}:${it.root?.packageName}"
                }
        return interactive &&
            unlocked &&
            resumed &&
            attachedVisible &&
            (baseFocused || ownedDialogOrMainFocused) &&
            rootPackage == context.packageName
    }

    private fun logUiTimeoutState(code: String) {
        val fields =
            listOf(
                "mWakefulness=",
                "mInteractive=",
                "mIsPowered=",
                "mScreenOffTimeoutSetting=",
                "mCurrentFocus=",
                "mFocusedApp=",
                "topResumedActivity=",
                "mResumedActivity=",
                "mCurFocusedWindow=",
                "mCurFocusedWindowClient=",
                "mInputShown=",
                "mShowRequested=",
                "mServedView=",
                "mCurMethodId=",
            )
        val state = runCatching {
            listOf("power", "window windows", "activity activities", "input_method").joinToString(
                "\n"
            ) { service ->
                val snapshot = shell("dumpsys -t 3 $service")
                service +
                    ":\n" +
                    snapshot
                        .lineSequence()
                        .filter { line -> fields.any(line::contains) }
                        .take(24)
                        .joinToString("\n")
            }
        }
            .getOrElse { "System diagnostics failed: ${it.javaClass.simpleName}" }
        test.sendStatus(
            0,
            Bundle().apply {
                putString(
                    "ui_timeout_window_state",
                    "$code\nlastForegroundCheck: $foregroundDiagnostic\n$state",
                )
            },
        )
    }

    /** A real field tap asks the focused Android window to show IME, without editing its value. */
    private fun tapFieldForKeyboard(label: String): Boolean {
        val selected =
            field(label)?.takeIf { it.refresh() && it.isEnabled && it.isVisibleToUser }
                ?: return false
        val hit = Rect().also { selected.getBoundsInScreen(it) }
        val frame = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
        if (!hit.intersect(frame) || hit.isEmpty) return false
        val began = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event =
                MotionEvent.obtain(
                    began,
                    SystemClock.uptimeMillis(),
                    action,
                    hit.exactCenterX(),
                    hit.exactCenterY(),
                    0,
                )
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                yes(automation.injectInputEvent(event, true), "SEARCH_REAL_FIELD_FOCUS_$label")
                if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(75)
            } finally {
                event.recycle()
            }
        }
        settleUi()
        return true
    }

    private fun launch(planning: Boolean = false, appointment: String = "", action: String = "") {
        shell("input keyevent KEYCODE_WAKEUP")
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
        waitUntil("MAIN_COMPOSE_CONTENT") { uiForegroundReady() }
        checks++
    }

    private fun hasCompose(view: View): Boolean =
        view.javaClass.name.startsWith("androidx.compose.ui.platform.") ||
            view is ViewGroup && (0 until view.childCount).any { hasCompose(view.getChildAt(it)) }

    private fun recreate() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        var old: Activity? = null
        waitUntil("REAL_ACTIVITY_RECREATION_REQUESTED") {
            var requested = false
            test.runOnMainSync {
                val current = activity ?: return@runOnMainSync
                if (current.isDestroyed || current.isFinishing) return@runOnMainSync
                old = current
                latest = null
                current.recreate()
                requested = true
            }
            requested
        }
        waitUntil("REAL_ACTIVITY_RECREATION") { latest != null && latest !== old }
        waitUntil("RECREATED_COMPOSE") { uiForegroundReady() }
        checks++
    }

    private fun execute(selectedJourney: String): JSONObject {
        guardGraphicsFixture()
        val previousInfo = automation.serviceInfo
        val previousFlags = previousInfo.flags
        val oldFont = shell("settings get system font_scale")
        try {
            automation.serviceInfo = previousInfo.apply {
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
                .put("executed_mcp_tools", JSONArray(executedMcpTools.toList()))
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
                .put("physical_pixel_tested", physicalPixelTested())
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
        val total = runBlocking {
            store.nativeData().list("customers", limit = 50, includeInactive = false).total
        }
        shown("$total Treffer · 1–50")
        click("Weitere 50")
        scrollTop()
        shown("$total Treffer · 51–$total")
        reveal(fixturePrefix + "054")
        val lastPage = runBlocking {
            store.nativeData().list("customers", offset = 50, limit = 50, includeInactive = false)
        }
        yes(
            lastPage.rows.any { it.title == fixturePrefix + "054" } && lastPage.total == total,
            "CUSTOMER_LAST_PAGE_MATCHES_STORED_CUSTOMERS",
        )
        click("Vorherige 50")
        scrollTop()
        shown("$total Treffer · 1–50")
        searchCustomers(fixturePrefix + "054")
        shown(fixturePrefix + "054")
        yes(
            customers(fixturePrefix + "054").single().getString("id") ==
                lastPage.rows.single { it.title == fixturePrefix + "054" }.id,
            "CUSTOMER_SEARCH_MATCHES_STORED_LAST_PAGE_RECORD",
        )
        openCustomerList()
        click(UiTags.CREATE)
        click(UiTags.createChoice("customer"))
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
        searchCustomers(createdName)
        click(createdName)
        shown(createdName)
        yes(store.ownerValue(draftKey, "").isEmpty(), "SAVE_REMOVES_CUSTOMER_DRAFT")
        click(createdName)
        enter("Name", editedName)
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
        if (keyboardVisible()) back()
        click("Fertig")
        shown("Angabe geändert")
        yes(customers(editedName).isEmpty(), "STALE_UI_SAVE_DOES_NOT_APPLY_NAME")
        yes(
            customers(createdName).single().let {
                it.getString("id") == id && it.optString("notes") == remoteNote
            },
            "CONFLICT_RETAINS_REMOTE_CUSTOMER",
        )
        click("Diesen behalten · meine Angabe")
        waitUntil("CUSTOMER_EDITED") { customers(editedName).singleOrNull()?.optString("id") == id }
        yes(
            customers(editedName).single().optString("notes") == remoteNote,
            "REVIEWED_SAVE_PRESERVES_REMOTE_NOTE",
        )
        yes(customers(createdName).isEmpty(), "CUSTOMER_UPDATE_PRESERVES_ID_WITHOUT_DUPLICATE")
        reveal("Arbeit starten")
        val customerNavigation =
            nodes().firstNotNullOfOrNull { candidate ->
                if (matches(candidate, "Kunden")) clickable(candidate) else null
            }
        yes(
            customerNavigation != null &&
                customerNavigation.isClickable &&
                navigationSelected(customerNavigation),
            "CUSTOMER_DETAIL_SELECTED_AREA_EXPOSES_ACCESSIBLE_LIST_NAVIGATION",
        )
        openCustomerList()
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
        reveal("Löschen")
        click("Löschen")
        waitUntil("CUSTOMER_DELETED") { customers(flowName).isEmpty() }
        waitUntil("CUSTOMER_DELETE_RETURNS_TO_LIST") {
            selectedArea(UiTags.area("customers")) &&
                node("Suchen")?.let(::clickable) != null &&
                node(UiTags.CREATE)?.let(::clickable) != null
        }
        searchCustomers(flowName)
        shown("0 Treffer")
        yes(
            nodes().none {
                it.isVisibleToUser &&
                    !it.isEditable &&
                    matches(it, flowName) &&
                    clickable(it) != null
            } && customers(flowName).none { it.optString("id") == id },
            "DELETED_CUSTOMER_NOT_IN_ACTIVE_LIST",
        )
    }

    private fun usabilityJourney() {
        click(UiTags.area("today"))
        reveal("Termine heute")
        yes(node("Weitere Tageshinweise") == null, "TODAY_HAS_NO_EXTRA_HINT_CARD")
        yes(
            node(UiTags.MORE)?.let(::clickable) != null &&
                node(UiTags.CREATE)?.let(::clickable) != null,
            "TODAY_HEADER_MORE_AND_CREATE_BUTTON",
        )
        openCustomerList()
        val total = runBlocking {
            store.nativeData().list("customers", limit = 50, includeInactive = false).total
        }
        click("Weitere 50")
        scrollTop()
        shown("$total Treffer · 51–$total")
        reveal(fixturePrefix + "054")
        // Compare positions without the keyboard on both sides; it changes the visible area.
        if (keyboardVisible()) {
            back()
            waitUntil("USABILITY_IME_HIDDEN") { !keyboardVisible() }
        }
        reveal(fixturePrefix + "054")
        var retainedBounds = Rect()
        var previousBounds = Rect()
        waitStable("CUSTOMER_SCROLL_POSITION_BEFORE_LEAVING", stableMs = 500) {
            val measured = runCatching { actionBounds(fixturePrefix + "054") }.getOrDefault(Rect())
            val stable = !measured.isEmpty && measured == previousBounds
            previousBounds = Rect(measured)
            retainedBounds = measured
            stable
        }
        val retainedViewport = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
        test.sendStatus(
            0,
            Bundle().apply {
                putString(
                    "customer_retained_bounds",
                    "$retainedBounds viewport=$retainedViewport ime=${keyboardVisible()}",
                )
            },
        )
        click(UiTags.area("today"))
        click(UiTags.area("customers"))
        shown("$total Treffer · 51–$total")
        yes(
            runBlocking {
                store
                    .nativeData()
                    .list("customers", offset = 50, limit = 50, includeInactive = false)
                    .rows
            }
                .any { it.title == fixturePrefix + "054" },
            "CUSTOMER_RESTORED_PAGE_MATCHES_DATA",
        )
        var restoredBounds = Rect()
        var priorRestoredBounds: Rect? = null
        waitStable("CUSTOMER_SCROLL_POSITION_RESTORED retained=$retainedBounds", stableMs = 500) {
            restoredBounds =
                runCatching { actionBounds(fixturePrefix + "054") }.getOrDefault(Rect())
            if (restoredBounds != priorRestoredBounds) {
                test.sendStatus(
                    0,
                    Bundle().apply {
                        putString("customer_restored_bounds", restoredBounds.toString())
                    },
                )
                priorRestoredBounds = Rect(restoredBounds)
            }
            restoredBounds == retainedBounds
        }
        val restoredViewport = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
        yes(
            restoredBounds == retainedBounds,
            "CUSTOMER_SCROLL_POSITION_PRESERVED_ON_ENTRY retained=$retainedBounds restored=$restoredBounds viewport=$restoredViewport ime=${keyboardVisible()}",
        )
        recreate()
        shown("$total Treffer · 51–$total")
        waitStable(
            "CUSTOMER_SCROLL_POSITION_AFTER_RECREATION retained=$retainedBounds",
            stableMs = 500,
        ) {
            val measured = runCatching { actionBounds(fixturePrefix + "054") }.getOrDefault(Rect())
            if (measured != priorRestoredBounds) {
                test.sendStatus(
                    0,
                    Bundle().apply { putString("customer_recreated_bounds", measured.toString()) },
                )
                priorRestoredBounds = Rect(measured)
            }
            measured == retainedBounds
        }
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
        click("Textbausteine")
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
        // Saved work is opened from the shared overflow menu.
        openWorklogs()
        enter("Suchen", ownText)
        click(ownText)
        openDetails("Arbeit")
        reveal("[TEST] QA sofort sichtbare Notiz")
        click(ownText)
        enter("Tätigkeit", ownText + " ergänzt")
        if (keyboardVisible()) back()
        click("Fertig")
        waitUntil("WORK_FIELD_CLOSE_SAVES") {
            saved()?.getString("activity") == ownText + " ergänzt"
        }
        click(UiTags.UNDO)
        waitUntil("WORK_FIELD_UNDO_RESTORES") {
            saved()?.getString("activity") == row.getString("activity")
        }
        yes(saved()?.getString("id") == row.getString("id"), "FIELD_UNDO_PRESERVES_SAVED_WORK")
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
        yes(
            node("Pause (Minuten)") == null && node("Beginn") == null && node("Ende") == null,
            "DURATION_EXCLUDES_TIMES_AND_PAUSE",
        )
        // The new-work route retains the expanded details from the first entry.
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
                        it.optString("material").contains("12.50")
                }
        }
        // The operational material flow saves its product and consumption through real controls.
        val productName = "[TEST] QA UI Kompostprodukt"
        val consumptionNote = "[TEST] QA UI Verbrauch zur Originalarbeit"
        val workId = row.getString("id")
        fun product(): JSONObject? =
            SettlementRules.rows(
                    read("list_materials", JSONObject().put("query", productName))
                        .optJSONArray("items")
                )
                .singleOrNull { it.optString("name") == productName }
        fun uiOperation(kind: String, matches: (JSONObject) -> Boolean): JSONObject {
            var snapshot: JSONObject? = null
            waitUntil("MATERIAL_UI_RETAINED_$kind") {
                val drafts = mutableListOf<JSONObject>()
                store.readableDatabase
                    .rawQuery(
                        "SELECT value FROM owner_state WHERE key GLOB ?",
                        arrayOf("native_form_draft:*"),
                    )
                    .use { cursor ->
                        while (cursor.moveToNext()) {
                            val draft =
                                runCatching { JSONObject(cursor.getString(0)) }.getOrNull()
                                    ?: continue
                            if (
                                draft.optString("kind") == kind &&
                                    matches(draft.optJSONObject("values") ?: JSONObject())
                            )
                                drafts.add(draft)
                        }
                    }
                snapshot = drafts.singleOrNull()
                snapshot != null
            }
            // Reuse the production adapter to replay the exact retained UI request, without
            // reconstructing its generated ID, price conversions or source hash in the test.
            val edit = RevierEdit.restore("qa_material_ui_readback", requireNotNull(snapshot))
            return revierArguments(edit, edit.row())
        }
        openOffice()
        click("Material")
        click("Material anlegen")
        enter("Materialbezeichnung *", productName)
        enter("Einheit *", "Sack")
        enter("Einstandspreis in EUR (optional)", "7,50")
        enter("Abrechnungspreis in EUR (optional)", "12,50")
        if (keyboardVisible()) back()
        val productOperation = uiOperation("material") { it.optString("name") == productName }
        click("Speichern")
        waitUntil("MATERIAL_UI_PRODUCT_SAVED") { product() != null }
        val originalProduct = requireNotNull(product())
        val materialId = originalProduct.getString("id")
        yes(
            originalProduct.getString("unit") == "Sack" &&
                originalProduct.getLong("purchase_price_minor") == 750L &&
                originalProduct.getLong("billing_price_minor") == 1250L,
            "MATERIAL_UI_PRODUCT_ORIGINAL_AND_EXACT_PRICES",
        )
        val replayedProduct =
            read(productOperation.getString("tool"), productOperation.getJSONObject("args"))
        yes(
            replayedProduct.optBoolean("replayed") && product()?.getString("id") == materialId,
            "MATERIAL_UI_PRODUCT_REQUEST_REPLAY_HAS_ONE_ORIGINAL",
        )
        reveal("Verbrauch erfassen")
        click("Verbrauch erfassen")
        click("Arbeit: Nicht zugeordnet")
        enter("Suchen", ownText)
        click(ownText)
        enter("Verbrauch in Sack *", "0")
        if (keyboardVisible()) back()
        click("Verbrauch speichern")
        revealContaining("Eine positive Verbrauchsmenge eingeben.")
        yes(
            SettlementRules.rows(requireNotNull(product()).optJSONArray("movements")).isEmpty(),
            "MATERIAL_UI_INVALID_CONSUMPTION_LEAVES_NO_MOVEMENT",
        )
        enter("Verbrauch in Sack *", "2,5")
        enter("Notiz", consumptionNote)
        if (keyboardVisible()) back()
        val consumptionOperation =
            uiOperation("movement") {
                it.optString("material_id") == materialId &&
                    it.optString("worklog_id") == workId &&
                    it.optString("quantity_decimal") == "2,5"
            }
        val workHashBeforeConsumption =
            PixelMcpWriteTools.hash(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            )
        click("Verbrauch speichern")
        shown("Verbrauch gebucht")
        waitUntil("MATERIAL_UI_CONSUMPTION_SAVED") {
            SettlementRules.rows(product()?.optJSONArray("movements")).size == 1
        }
        val movement =
            SettlementRules.rows(requireNotNull(product()).optJSONArray("movements")).single()
        val movementId = movement.getString("id")
        yes(
            movement.getString("material_id") == materialId &&
                movement.getString("worklog_id") == workId &&
                movement.getString("quantity_decimal") == "2.5" &&
                movement.getString("unit") == "Sack" &&
                movement.getString("reason") == consumptionNote,
            "MATERIAL_UI_CONSUMPTION_REFERENCES_PRODUCT_AND_ORIGINAL_WORK",
        )
        yes(
            PixelMcpWriteTools.hash(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            ) == workHashBeforeConsumption,
            "MATERIAL_UI_CONSUMPTION_NEVER_DUPLICATES_OR_REWRITES_WORK",
        )
        val replayedConsumption =
            read(consumptionOperation.getString("tool"), consumptionOperation.getJSONObject("args"))
        yes(
            replayedConsumption.optBoolean("replayed") &&
                SettlementRules.rows(requireNotNull(product()).optJSONArray("movements"))
                    .single()
                    .getString("id") == movementId,
            "MATERIAL_UI_CONSUMPTION_REQUEST_REPLAY_HAS_ONE_MOVEMENT",
        )
        click("Zum Material")
        recreate()
        revealContaining(consumptionNote)
        captureSyntheticScreen("material-consumption-saved")
        yes(
            product()?.getString("id") == materialId &&
                SettlementRules.rows(requireNotNull(product()).optJSONArray("movements"))
                    .single()
                    .getString("id") == movementId,
            "MATERIAL_UI_PRODUCT_AND_SINGLE_CONSUMPTION_SURVIVE_RECREATION",
        )
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
        shown("Termine heute")
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
        click("Gerätebezeichnung auswählen")
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
        selectTab("Zubehör")
        click("Zubehör hinzufügen")
        shown("Maschine: " + name)
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
            selectTab("Zubehör")
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
            selectTab("Übersicht")
            reveal("Wartung einrichten")
            val equipmentId = machine().getString("id")
            val maintenanceArgs = JSONObject().put("equipment_id", equipmentId)
            fun maintenance() = read("get_equipment_maintenance", maintenanceArgs)
            val beforeEvents = maintenance().getInt("total")
            val maintenanceTitle = "[TEST] QA UI Kettenwartung"
            val maintenanceDay = LocalDate.now(ZoneId.of("Europe/Vienna"))
            click("Wartung einrichten")
            enter("Wartungstätigkeit *", maintenanceTitle)
            enter("Alle *", "7")
            chooseDate("Intervall beginnt am *", maintenanceDay.toString())
            click(UiTags.PRIMARY)
            waitUntil("UI_EQUIPMENT_MAINTENANCE_PLAN_PERSISTED") {
                SettlementRules.rows(maintenance().getJSONArray("plans")).any {
                    it.optString("title") == maintenanceTitle &&
                        it.optInt("interval_value") == 7 &&
                        it.optString("interval_unit") == "days"
                }
            }
            val plan =
                SettlementRules.rows(maintenance().getJSONArray("plans")).single {
                    it.optString("title") == maintenanceTitle
                }
            val planId = plan.getString("id")
            yes(
                maintenance().getInt("total") == beforeEvents,
                "UI_PLAN_CONFIGURATION_IS_NOT_A_COMPLETED_MAINTENANCE",
            )
            reveal(maintenanceTitle)
            click(maintenanceTitle)
            val maintenanceReason = "[TEST] Bewusst durchgeführte Kettenwartung"
            val maintenanceNote = "[TEST] Kette gereinigt und geprüft"
            chooseDate("Erledigt am *", maintenanceDay.toString())
            enter("Grund / Anlass", maintenanceReason)
            enter("Notiz", maintenanceNote)
            if (keyboardVisible()) back()
            // Replay exactly the operation prepared by the real form, with its original request
            // and version. The assertions below read persisted events independently of the form.
            val editKey =
                "revier:maintenance_done:$equipmentId:" +
                    listOf(equipmentId, planId, "", "", "", "").joinToString(":")
            var maintenanceOperation: JSONObject? = null
            waitUntil("UI_MAINTENANCE_OPERATION_DURABLE") {
                val saved = runBlocking { controller.draft(editKey) }
                if (saved?.optJSONObject("values")?.optString("notes") != maintenanceNote) false
                else {
                    val edit = RevierEdit.restore(editKey, requireNotNull(saved))
                    maintenanceOperation = revierArguments(edit, edit.row())
                    true
                }
            }
            val save = requireNotNull(node(UiTags.PRIMARY)?.let(::clickable))
            yes(
                save.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                "UI_MAINTENANCE_SAVE_BUTTON_ACTIVATED",
            )
            // A second tap may be rejected by the disabled button or replay the same operation.
            // Either way there must be exactly one new persisted maintenance event.
            save.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            waitUntil("UI_MAINTENANCE_EVENT_PERSISTED_ONCE") {
                maintenance().getInt("total") == beforeEvents + 1
            }
            shown("Wartung gespeichert")
            val event =
                SettlementRules.rows(maintenance().getJSONArray("items")).single {
                    it.optString("title") == maintenanceTitle
                }
            val eventId = event.getString("id")
            yes(
                event.getString("equipment_id") == equipmentId &&
                    event.getString("plan_id") == planId &&
                    event.getString("performed_on") == maintenanceDay.toString() &&
                    event.getString("reason") == maintenanceReason &&
                    event.getString("notes") == maintenanceNote,
                "UI_MAINTENANCE_PRESERVES_ORIGINAL_DEVICE_PLAN_DATE_AND_CONTENT",
            )
            val savedPlan =
                SettlementRules.rows(maintenance().getJSONArray("plans")).single {
                    it.getString("id") == planId
                }
            yes(
                savedPlan.getString("last_event_id") == eventId &&
                    savedPlan.getString("last_completed_on") == maintenanceDay.toString() &&
                    savedPlan.getString("next_due_on") == maintenanceDay.plusDays(7).toString(),
                "UI_MAINTENANCE_ADVANCES_EXISTING_PLAN_FROM_PERFORMED_DATE",
            )
            val originalOperation = requireNotNull(maintenanceOperation)
            yes(
                read(originalOperation.getString("tool"), originalOperation.getJSONObject("args"))
                    .getBoolean("replayed"),
                "UI_MAINTENANCE_EXACT_REQUEST_REPLAY",
            )
            yes(
                maintenance().getInt("total") == beforeEvents + 1,
                "UI_MAINTENANCE_REPLAY_ADDS_NO_SECOND_EVENT",
            )
            click("Zum Gerät")
            recreate()
            reveal(maintenanceTitle)
            captureSyntheticScreen("equipment-maintenance-saved")
            yes(
                maintenance().getInt("total") == beforeEvents + 1 &&
                    SettlementRules.rows(maintenance().getJSONArray("items"))
                        .single { it.optString("title") == maintenanceTitle }
                        .getString("id") == eventId &&
                    machine().getString("id") == equipmentId,
                "UI_MAINTENANCE_EVENT_AND_DEVICE_ID_SURVIVE_RECREATION",
            )
            PixelLocalStore(context).use { fresh ->
                val reader =
                    PixelMcpReadTools(fresh.readSource(), fresh, PixelOwnerAccess(context, fresh))
                val response = reader.call("get_equipment_maintenance", maintenanceArgs)
                yes(
                    !response.has("ok") || response.getBoolean("ok"),
                    "UI_MAINTENANCE_REOPENED_MCP_READ",
                )
                val persisted = response.optJSONObject("data") ?: response
                yes(
                    persisted.getInt("total") == beforeEvents + 1 &&
                        SettlementRules.rows(persisted.getJSONArray("items"))
                            .single { it.optString("title") == maintenanceTitle }
                            .getString("id") == eventId,
                    "UI_MAINTENANCE_EVENT_READ_FROM_NEW_STORE_ADAPTER",
                )
            }
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
        // A selected image is already a private immutable draft. Remove only that readable
        // source while the actual Save commits work first, then opens the image stream.
        // This injects a real filesystem read failure; the writer and UI stay unchanged.
        val importDraft = nativeRetainPhotoDraft(controller, Uri.fromFile(source))
        val importSource = File(requireNotNull(importDraft.path))
        val unavailable = File(importSource.parentFile, importSource.name + ".qa-unavailable")
        val workTitle = "[TEST] QA Arbeit bleibt nach Fotoimportfehler"
        val workRequest = "qa_work_photo_import_failure"
        val photoOrigin =
            JSONObject()
                .put("formatVersion", 1)
                .put("installation_id", identity.getString("installationId"))
                .put("restore_epoch", identity.getString("restoreEpoch"))
        fun workIds(): Set<String> =
            SettlementRules.rows(
                    read(
                            "list_work_records",
                            JSONObject().put("limit", 100),
                        )
                        .getJSONArray("work_records")
                )
                .map { it.getString("id") }
                .toSet()
        fun workPhotos(id: String): List<JSONObject> =
            SettlementRules.rows(
                read(
                        "list_photos",
                        JSONObject().put("worklog_id", id).put("limit", 100),
                    )
                    .getJSONArray("photos")
            )
        val beforeWorkIds = workIds()
        val beforePhotos =
            SettlementRules.rows(
                    read("list_photos", JSONObject().put("limit", 100)).getJSONArray("photos")
                )
                .map { it.getString("id") }
                .toSet()
        val sourceBytes = importSource.readBytes()
        try {
            controller.saveDraft(
                "editor:worklog:new",
                JSONObject()
                    .put(
                        "values",
                        JSONObject()
                            .put("_request_id", workRequest)
                            .put("customer_id", work.getString("customer_id"))
                            .put("activity", workTitle)
                            .put("work_date", "2026-09-15")
                            .put("hours", 1)
                            .put("customer_visible", 1),
                    )
                    .put("selected_photo_uri", importDraft.toString())
                    .put("photo_origin", photoOrigin)
                    .put("time_mode", "duration")
                    .put("duration_hours", "1")
                    .put("duration_minutes", "0")
                    .put("duration_seeded", true),
            )
            controller.flushDrafts()
            click(UiTags.area("today"))
            newWork()
            reveal("Was wurde erledigt? *")
            waitUntil("TEST21_REAL_WORK_AND_PHOTO_DRAFT_LOADED") {
                field("Was wurde erledigt? *")?.text?.toString() == workTitle &&
                    workDraft().optString("selected_photo_uri") == importDraft.toString() &&
                    actionEnabled(UiTags.PRIMARY)
            }
            shown("Arbeit speichern")
            yes(
                importSource.isFile && !unavailable.exists() && importSource.renameTo(unavailable),
                "TEST21_ONLY_SELECTED_SYNTHETIC_STREAM_SOURCE_UNAVAILABLE",
            )
            click(UiTags.PRIMARY)
            var savedId = ""
            waitUntil("TEST21_WORK_COMMITTED_BEFORE_PHOTO_ERROR") {
                val retained = workDraft()
                savedId = retained.optString("saved_work_id")
                savedId.isNotBlank() &&
                    workIds() == beforeWorkIds + savedId &&
                    node("Arbeit gespeichert · Foto noch offen") != null &&
                    node("Foto speichern") != null
            }
            revealContaining("Arbeit ist gespeichert. Foto oder Entwurf:")
            revealContaining(importSource.name)
            yes(
                visibleTexts().any { it.contains(importSource.name) && it.contains("ENOENT") },
                "TEST21_VISIBLE_ERROR_IS_ACTUAL_MISSING_IMAGE_STREAM",
            )
            shown("Arbeit gespeichert · Foto noch offen")
            val failedDraft = workDraft()
            val photoArgs = failedDraft.getJSONObject("photo_args")
            yes(
                failedDraft.getString("saved_work_id") == savedId &&
                    failedDraft.getJSONObject("values").getString("_request_id") == workRequest &&
                    failedDraft.getString("selected_photo_uri") == importDraft.toString() &&
                    photoArgs.getString("worklog_id") == savedId &&
                    photoArgs.getString("_photo_request_id") == workRequest + "_photo" &&
                    workPhotos(savedId).isEmpty(),
                "TEST21_FAILED_IMPORT_KEEPS_EXACT_WORK_AND_PHOTO_REQUEST",
            )
            val storedWork = read("get_work_record", JSONObject().put("work_record_id", savedId))
            yes(
                storedWork.getString("activity") == workTitle &&
                    storedWork.getDouble("hours") == 1.0 &&
                    storedWork.getString("customer_id") == work.getString("customer_id") &&
                    !importSource.exists() &&
                    unavailable.readBytes().contentEquals(sourceBytes),
                "TEST21_REAL_SAVED_WORK_READBACK_AND_UNREADABLE_PHOTO_SOURCE",
            )
            val workHash = PixelMcpWriteTools.hash(storedWork)
            captureSyntheticScreen("work-photo-import-failure")
            recreate()
            reveal("Arbeit gespeichert · Foto noch offen")
            shown("Foto speichern")
            yes(
                workDraft().getString("saved_work_id") == savedId &&
                    PixelMcpWriteTools.hash(workDraft().getJSONObject("photo_args")) ==
                        PixelMcpWriteTools.hash(photoArgs) &&
                    workIds() == beforeWorkIds + savedId &&
                    workPhotos(savedId).isEmpty(),
                "TEST21_RECREATE_RETAINS_ONE_WORK_AND_SAME_PENDING_PHOTO",
            )
            yes(
                !importSource.exists() && unavailable.renameTo(importSource),
                "TEST21_RESTORE_ONLY_SELECTED_SYNTHETIC_SOURCE_FOR_RETRY",
            )
            click(UiTags.PRIMARY)
            waitUntil("TEST21_VISIBLE_PHOTO_RETRY_SAVES_ON_EXISTING_WORK") {
                workPhotos(savedId).size == 1 &&
                    store
                        .ownerValue(
                            "native_form_draft:" + PixelMcpWriteTools.hash("editor:worklog:new"),
                            "",
                        )
                        .isEmpty() &&
                    node("Foto speichern") == null
            }
            val imported = workPhotos(savedId).single()
            val importedId = imported.getString("id")
            yes(
                workIds() == beforeWorkIds + savedId &&
                    PixelMcpWriteTools.hash(
                        read("get_work_record", JSONObject().put("work_record_id", savedId))
                    ) == workHash &&
                    imported.getString("worklog_id") == savedId &&
                    imported.getString("customer_id") == work.getString("customer_id"),
                "TEST21_PHOTO_RETRY_NEVER_DUPLICATES_OR_REWRITES_WORK",
            )
            val retainedPhoto =
                read("get_photo", JSONObject().put("photo_id", importedId)).getJSONObject("photo")
            val storedAttachment = requireNotNull(store.photoAttachment(importedId))
            yes(
                store.photoAttachmentStore().verifiedFile(storedAttachment).isFile &&
                    retainedPhoto.getString("worklog_id") == savedId &&
                    !importSource.exists(),
                "TEST21_RETRY_PHOTO_PRIVATE_ORIGINAL_READBACK_AND_TEMP_CLEANUP",
            )
            val afterPhotos =
                SettlementRules.rows(
                        read("list_photos", JSONObject().put("limit", 100)).getJSONArray("photos")
                    )
                    .map { it.getString("id") }
                    .toSet()
            yes(
                afterPhotos == beforePhotos + importedId,
                "TEST21_RETRY_EXACTLY_ONE_ADDITIONAL_PHOTO",
            )
            recreate()
            yes(
                workIds() == beforeWorkIds + savedId &&
                    workPhotos(savedId).single().getString("id") == importedId &&
                    PixelMcpWriteTools.hash(
                        read("get_work_record", JSONObject().put("work_record_id", savedId))
                    ) == workHash,
                "TEST21_COMPLETED_RETRY_AND_ORIGINAL_IDS_SURVIVE_RECREATION",
            )
            captureSyntheticScreen("work-photo-import-retry-saved")
        } finally {
            if (unavailable.exists() && !importSource.exists()) unavailable.renameTo(importSource)
            nativeDeletePhotoDraft(controller, importDraft.toString())
            unavailable.delete()
        }
        source.delete()
    }

    private fun todayJourney() {
        click(UiTags.area("today"))
        reveal("Arbeit starten")
        for (label in listOf("Heute", "Planung", "Kunden", "Abrechnung", "Neu anlegen")) shown(
            label
        )
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
        val searchBounds = actionBounds("action:global-search")
        val menuBounds = actionBounds(UiTags.MORE)
        val createBounds = actionBounds(UiTags.CREATE)
        yes(
            searchBounds.top > menuBounds.bottom && createBounds.top > menuBounds.bottom,
            "EISBAER_SEARCH_AND_PLUS_BELOW_CONTENT_HEADER",
        )
        captureSyntheticScreen("today-idle")
        val before = PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
        click(UiTags.CREATE)
        for (label in
            listOf(
                "Arbeit nachtragen",
                "Anfrage anlegen",
                "Kunde anlegen",
                "Termin anlegen",
                "Foto hinzufügen",
                "Material anlegen",
            )) reveal(label)
        captureSyntheticScreen("today-create")
        back()
        waitUntil("CREATE_SHEET_CLOSED") {
            node("Termin anlegen") == null && node(UiTags.CREATE)?.let(::clickable) != null
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
        searchCustomers(fixturePrefix + "000")
        click(fixturePrefix + "000")
        reveal("Kontakt")
        click("Kontakt")
        reveal("Notizen")
        click("Notizen")
        yes(!keyboardVisible(), "CUSTOMER_NOTE_HAS_NO_FORCED_IME")
        val note =
            listOf(originalNotes, "[TEST] QA Heute Kundennotiz")
                .filter { it.isNotBlank() }
                .joinToString("\n")
        enter("Notizen", note)
        if (keyboardVisible()) back()
        click("Fertig")
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
                listOf(
                    "Arbeit starten",
                    "Neu anlegen",
                    "action:global-search",
                    "Heute",
                    "Planung",
                    "Kunden",
                    "Abrechnung",
                )
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
        searchCustomers(fixturePrefix + "000")
        click(fixturePrefix + "000")
        selectCustomerTab("Vorgänge")
        reveal("Fotos")
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
        reveal("Foto gespeichert")
        recreate()
        shown("Foto gespeichert")
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
        searchCustomers(fixturePrefix + "000")
        val searchField = requireNotNull(field(sharedSearchLabel))
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
        visibleResult("Arbeit starten")
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
        shown("Pausen gesamt")
        val characterLocations = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        var activeTimeBounds = Rect()
        var activeViewport = Rect()
        var clockDiagnostic = "Main clock has no complete character-location proof"
        waitUntil("TODAY_ACTIVE_CLOCK_VISIBLE") {
            val currentNodes = nodes()
            val heading =
                currentNodes.firstOrNull { it.isVisibleToUser && matches(it, "Aktuelle Arbeit") }
                    ?: return@waitUntil false
            val clock =
                currentNodes.firstOrNull {
                    it.isVisibleToUser && matches(it, UiTags.TIMER_CLOCK) && !it.isEditable
                } ?: return@waitUntil false
            val text = clock.text?.toString().orEmpty()
            if (
                !Regex("[0-9]{2,}:[0-9]{2}:[0-9]{2}").matches(text) ||
                    characterLocations !in clock.availableExtraData
            )
                return@waitUntil false
            val scroller =
                generateSequence(clock.parent) { it.parent }
                    .firstOrNull { parent ->
                        parent.className?.toString() == "android.widget.ScrollView" ||
                            parent.isScrollable &&
                                parent.actionList.any {
                                    it.id ==
                                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP
                                            .id ||
                                        it.id ==
                                            AccessibilityNodeInfo.AccessibilityAction
                                                .ACTION_SCROLL_DOWN
                                                .id
                                }
                    } ?: return@waitUntil false
            val args =
                Bundle().apply {
                    putInt(
                        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX,
                        0,
                    )
                    putInt(
                        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
                        text.length,
                    )
                }
            // Read the running clock in place: no show-on-screen action or scroll is allowed.
            if (!clock.refreshWithExtraData(characterLocations, args) || !scroller.refresh())
                return@waitUntil false
            @Suppress("DEPRECATION")
            val glyphs =
                clock.extras.getParcelableArray(characterLocations)?.map {
                    it as? android.graphics.RectF
                } ?: return@waitUntil false
            val viewport = Rect().also { scroller.getBoundsInScreen(it) }
            val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
            if (!viewport.intersect(screen) || !heading.refresh()) return@waitUntil false
            val headingBounds = Rect().also { heading.getBoundsInScreen(it) }
            val bounds = Rect().also { clock.getBoundsInScreen(it) }
            val complete =
                glyphs.size == text.length &&
                    glyphs.all { box ->
                        box != null &&
                            !box.isEmpty &&
                            box.left >= viewport.left + 1f &&
                            box.right <= viewport.right - 1f &&
                            box.top >= viewport.top + 1f &&
                            box.bottom <= viewport.bottom - 1f
                    }
            clockDiagnostic =
                "text=$text bounds=$bounds viewport=$viewport heading=$headingBounds glyphs=$glyphs"
            if (!complete || bounds.top < headingBounds.bottom || !viewport.contains(bounds))
                return@waitUntil false
            activeTimeBounds = bounds
            activeViewport = viewport
            true
        }
        test.sendStatus(0, Bundle().apply { putString("today_clock_visibility", clockDiagnostic) })
        yes(
            activeTimeBounds.height() > 0 && activeViewport.contains(activeTimeBounds),
            "TODAY_ACTIVE_TIME_VISIBLE_WITHOUT_SCROLL",
        )
        primaryPosition("Zeit stoppen", "Pause")
        shown("Kunde: " + fixturePrefix + "000")
        captureSyntheticScreen("today-running")
        reveal("Kunde: " + fixturePrefix + "000")
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
                val mainClock = textWithinOwnScrollViewport(frozenClock, UiTags.TIMER_CLOCK)
                primaryPosition("Fortsetzen", "Zeit stoppen")
                val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
                val clockBounds = mainClock.first
                yes(
                    mainClock.second.contains(clockBounds) &&
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
        shown("Laufende Arbeit öffnen")
        captureSyntheticScreen("timer-compact")
        click("Laufende Arbeit öffnen")
        shown("Arbeit läuft")
        reveal("Foto")
        shown("Material")
        yes(
            read("get_active_work_session").getString("id") == id &&
                PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() ==
                    before,
            "COMPACT_TIMER_REOPENS_WITHOUT_DUPLICATE_OR_WORKLOG",
        )
        openWorklogs()
        enter("Suchen", "")
        yes(
            node("Laufende Arbeit öffnen")?.let(::clickable) != null,
            "WORK_LIST_EXPOSES_GLOBAL_RUNNING_TIMER",
        )
        click("Laufende Arbeit öffnen")
        reveal("Arbeit läuft")
        reveal("Zeit läuft")
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
        click("Textbausteine")
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
        shown("Arbeit speichern")
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
        primaryPosition("Speichern", "Später abschließen")
        click("Später abschließen")
        click(UiTags.area("today"))
        shown("Arbeit noch abschließen")
        shown("Zeit gestoppt · Abschluss offen")
        primaryPosition("Arbeit speichern", "Arbeit nachtragen")
        yes(
            read("get_active_work_session").getLong("worked_ms") == stoppedMs,
            "STOPPED_TIME_DOES_NOT_GROW",
        )
        yes(
            PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs").length() ==
                before,
            "LEAVING_COMPLETION_DOES_NOT_SAVE",
        )
        click("Arbeit speichern")
        shown("Arbeit speichern")
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
        searchCustomers(fixturePrefix + "000")
        click(fixturePrefix + "000")
        click("Arbeit starten")
        waitUntil("SECOND_DIRECT_TIMER_STARTED") {
            read("get_active_work_session").optString("state") == "RUNNING"
        }
        val discardedId = read("get_active_work_session").getString("id")
        click("Zeit stoppen")
        shown("Arbeit speichern")
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
        shown("Arbeit speichern")
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
        openDetails("Zu erledigen")
        reveal(openHoursText())
        val fourth = runBlocking {
            data.list("worklogs", dateFrom = day, dateTo = day, limit = 4)
        }
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

    /** Every actual character must be visible; Compose clips ordinary A11y node rectangles. */
    private fun textWithinOwnScrollViewport(
        label: String,
        requiredAncestorTag: String = "",
    ): Pair<Rect, Rect> {
        val extraKey = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        val up = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
        val down = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
        var proof: Pair<Rect, Rect>? = null
        var nudges = 0
        var diagnostic = "No exact text with character locations and its own vertical scroller"
        waitUntil("OWN_SCROLL_CHARACTERS_$label") {
            val target =
                nodes().firstNotNullOfOrNull { candidate ->
                    if (
                        candidate.text?.toString() != label ||
                            candidate.isEditable ||
                            extraKey !in candidate.availableExtraData
                    )
                        return@firstNotNullOfOrNull null
                    val parents = generateSequence(candidate.parent) { it.parent }.toList()
                    if (
                        requiredAncestorTag.isNotBlank() &&
                            !matches(candidate, requiredAncestorTag) &&
                            parents.none { matches(it, requiredAncestorTag) }
                    )
                        return@firstNotNullOfOrNull null
                    val scroller =
                        parents.firstOrNull { parent ->
                            parent.className?.toString() == "android.widget.ScrollView" ||
                                (parent.isScrollable &&
                                    parent.actionList.any { it.id == up || it.id == down })
                        } ?: return@firstNotNullOfOrNull null
                    candidate to scroller
                } ?: return@waitUntil false
            // Compose handles this action from its UNCLIPPED positionInRoot+size and scrolls
            // precisely the nearest ScrollBy ancestor, unlike its clipped node/character bounds.
            val requested =
                target.first.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id
                )
            settleUi()
            val args =
                Bundle().apply {
                    putInt(
                        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX,
                        0,
                    )
                    putInt(
                        AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
                        label.length,
                    )
                }
            if (!target.first.refreshWithExtraData(extraKey, args) || !target.second.refresh())
                return@waitUntil false
            @Suppress("DEPRECATION")
            val characters =
                target.first.extras.getParcelableArray(extraKey)?.map {
                    it as? android.graphics.RectF
                } ?: return@waitUntil false
            val viewport = Rect().also { target.second.getBoundsInScreen(it) }
            val screen = Rect().also { automation.rootInActiveWindow?.getBoundsInScreen(it) }
            if (!viewport.intersect(screen)) return@waitUntil false
            diagnostic =
                "showOnScreen=$requested chars=${characters.size}/${label.length} glyphs=$characters ownViewport=$viewport nudges=$nudges"
            // Character locations are clipped too. A clipped glyph meets a clipping edge;
            // require one physical pixel inside, also covering node floor/ceil rounding.
            val complete =
                characters.size == label.length &&
                    characters.all { box ->
                        box != null &&
                            !box.isEmpty &&
                            box.left >= viewport.left + 1f &&
                            box.right <= viewport.right - 1f &&
                            box.top >= viewport.top + 1f &&
                            box.bottom <= viewport.bottom - 1f
                    }
            if (!complete) {
                val boxes = characters.filterNotNull()
                if (
                    nudges < 4 &&
                        boxes.size == label.length &&
                        boxes.all {
                            !it.isEmpty &&
                                it.left >= viewport.left + 1f &&
                                it.right <= viewport.right - 1f
                        }
                ) {
                    val topEdge = boxes.any { it.top < viewport.top + 1f }
                    val bottomEdge = boxes.any { it.bottom > viewport.bottom - 1f }
                    val distance = android.view.ViewConfiguration.get(context).scaledTouchSlop + 4
                    if (topEdge != bottomEdge && distance < viewport.height() / 2) {
                        val x = viewport.centerX()
                        val start = viewport.centerY()
                        val end = start + if (bottomEdge) -distance else distance
                        // Slow real touch exceeds touch slop by only four pixels, without a fling.
                        shell("input touchscreen swipe $x $start $x $end 600")
                        nudges++
                        settleUi()
                    }
                }
                return@waitUntil false
            }
            val glyphs = android.graphics.RectF(requireNotNull(characters.first()))
            characters.drop(1).forEach { glyphs.union(requireNotNull(it)) }
            val bounds = Rect().also { glyphs.roundOut(it) }
            proof = bounds to Rect(viewport)
            true
        }
        val result = requireNotNull(proof) { "QA_NATIVE_COMPOSE_OWN_SCROLL_$label:$diagnostic" }
        yes(
            result.second.contains(result.first),
            "OWN_SCROLL_ALL_CHARACTERS_INSIDE_VIEWPORT_$label",
        )
        test.sendStatus(
            0,
            Bundle().apply { putString("own_scroll_viewport", "$label: $diagnostic") },
        )
        return result
    }

    private fun captureSyntheticScreen(name: String, requireKeyboard: Boolean = false) {
        settleUi()
        val display = android.graphics.Point()
        var ownWindow: View? = null
        waitUntil("SCREENSHOT_WINDOW_DRAW_READY_$name") {
            val root = automation.rootInActiveWindow ?: return@waitUntil false
            val rootBounds = Rect().also { root.getBoundsInScreen(it) }
            if (!root.isVisibleToUser || rootBounds.isEmpty) return@waitUntil false
            var ready = false
            test.runOnMainSync {
                @Suppress("DEPRECATION")
                context
                    .getSystemService(android.view.WindowManager::class.java)
                    .defaultDisplay
                    .getRealSize(display)
                ownWindow =
                    if (root.packageName?.toString() == context.packageName)
                        WindowInspector.getGlobalWindowViews().firstOrNull {
                            it.isAttachedToWindow && it.isShown && it.hasWindowFocus()
                        }
                    else null
                val focused = ownWindow
                ready =
                    if (root.packageName?.toString() != context.packageName) {
                        // The real Android camera may legitimately own the active window.
                        display.x > 0 && display.y > 0
                    } else if (focused == null) false
                    else {
                        val orientation = focused.resources.configuration.orientation
                        val displayMatches =
                            when (orientation) {
                                android.content.res.Configuration.ORIENTATION_LANDSCAPE ->
                                    display.x > display.y
                                android.content.res.Configuration.ORIENTATION_PORTRAIT ->
                                    display.y > display.x
                                else -> false
                            }
                        focused.width > 0 &&
                            focused.height > 0 &&
                            !focused.isLayoutRequested &&
                            displayMatches
                    }
            }
            ready
        }
        ownWindow?.let { view ->
            val drawn = java.util.concurrent.CountDownLatch(1)
            test.runOnMainSync {
                if (view.isHardwareAccelerated) {
                    view.viewTreeObserver.registerFrameCommitCallback { drawn.countDown() }
                } else {
                    val listener =
                        object : android.view.ViewTreeObserver.OnDrawListener {
                            override fun onDraw() {
                                view.post {
                                    if (view.viewTreeObserver.isAlive)
                                        view.viewTreeObserver.removeOnDrawListener(this)
                                    drawn.countDown()
                                }
                            }
                        }
                    view.viewTreeObserver.addOnDrawListener(listener)
                }
                view.invalidate()
            }
            yes(
                drawn.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "SCREENSHOT_FRESH_FRAME_COMMITTED_$name",
            )
        }
        // The frame-commit callback fences the actual draw; finish pending public UI work.
        test.waitForIdleSync()
        if (requireKeyboard) yes(keyboardVisible(), "SCREENSHOT_IME_BEFORE_$name")
        val bitmap =
            requireNotNull(automation.takeScreenshot()) {
                "Synthetic emulator screenshot unavailable"
            }
        try {
            yes(
                bitmap.width == display.x && bitmap.height == display.y,
                "SCREENSHOT_MATCHES_CURRENT_DISPLAY_$name",
            )
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
        val planningContext = NativeAppController(requireNotNull(activity) as MainActivity, store)
        planningContext.navigate("planning", args = JSONObject().put("date", "2027-05-18"))
        val planningStateKey = planningContext.savedRoute()
        planningContext.route.args.put("date", "2027-05-19").put("customer_id", "qa-context")
        yes(
            planningContext.savedRoute() == planningStateKey,
            "PLANNING_CURRENT_CONTEXT_DOES_NOT_RESET_SEARCH_OR_FOCUS_STATE_KEY",
        )
        val planningRecreated = NativeAppController(requireNotNull(activity) as MainActivity, store)
        planningRecreated.restoreRoute(planningContext.savedNavigation())
        yes(
            planningRecreated.savedRoute() == planningStateKey &&
                planningRecreated.route.args.optString("date") == "2027-05-19" &&
                planningRecreated.route.args.optString("customer_id") == "qa-context",
            "RECREATED_PLANNING_RETAINS_STABLE_STATE_KEY_AND_CURRENT_CONTEXT",
        )
        planningRecreated.navigate("planning", args = JSONObject().put("date", "2027-05-20"))
        yes(
            planningRecreated.savedRoute() != planningStateKey &&
                planningRecreated.route.args.optString("date") == "2027-05-20",
            "EXPLICIT_NEW_CONTEXT_CHANGES_PLANNING_STATE_KEY",
        )
        val filtered = NativeAppController(requireNotNull(activity) as MainActivity, store)
        filtered.navigate("settlements", args = JSONObject().put("status", "draft"))
        filtered.navigate("settlements", args = JSONObject().put("payment_open", true))
        yes(
            filtered.route.args.optBoolean("payment_open") && !filtered.route.args.has("status"),
            "SAME_OVERVIEW_EXPLICIT_SHORTCUT_REPLACES_FILTER",
        )
        filtered.navigate("settlement_review", "qa-filter-history")
        filtered.navigate("settlements", args = JSONObject().put("status", "draft"))
        yes(
            filtered.route.args.optString("status") == "draft" &&
                !filtered.route.args.has("payment_open"),
            "HISTORY_OVERVIEW_EXPLICIT_SHORTCUT_REPLACES_FILTER",
        )
        filtered.navigate("settlement_review", "qa-filter-history")
        filtered.navigate("settlements")
        yes(filtered.route.args.optString("status") == "draft", "OVERVIEW_RETAINS_SAVED_FILTER")
        val filteredRecreated = NativeAppController(requireNotNull(activity) as MainActivity, store)
        filteredRecreated.restoreRoute(filtered.savedNavigation())
        yes(
            filteredRecreated.route.args.optString("status") == "draft",
            "EXPLICIT_SHORTCUT_FILTER_SURVIVES_RECREATE",
        )
        filteredRecreated.back()
        yes(filteredRecreated.route.section == "today", "SHORTCUT_REUSES_OVERVIEW_STACK_POSITION")
        val navigation = NativeAppController(requireNotNull(activity) as MainActivity, store)
        for (area in listOf("today", "planning", "customers", "accounting")) {
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
        navigation.openSection("accounting")
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
            searchCustomers(name)
            click(name)
            if (index == 0) {
                val customerId = customers(name).single().getString("id")
                val customerArgs = JSONObject().put("customer_id", customerId)
                val contactSeedProof = tools.call("get_customer", customerArgs)
                yes(contactSeedProof.optBoolean("ok"), "CUSTOMER_CONTACT_FIXTURE_ORIGINAL_READ")
                read(
                    "update_customer",
                    JSONObject()
                        .put("request_id", "qa_native_customer_contact_context")
                        .put("customer_id", customerId)
                        .put("expected_record_hash", contactSeedProof.getString("record_hash"))
                        .put(
                            "updates",
                            JSONObject()
                                .put("phone", "+430000000011")
                                .put("street", "[TEST] QA Kundenkontaktweg 11")
                                .put("postal_code", "1010")
                                .put("city", "[TEST] QA Wien"),
                        ),
                )
                val contactOriginal = read("get_customer", customerArgs)
                val contactProof = tools.call("get_customer", customerArgs)
                yes(contactProof.optBoolean("ok"), "CUSTOMER_CONTACT_ORIGINAL_PROOF")
                val contactBusiness =
                    PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
                val contactLedger = store.ownerValue(SettlementRules.KEY, "{}")
                val contactIntent = java.util.concurrent.atomic.AtomicReference<Intent>()
                val contactMonitor =
                    object : Instrumentation.ActivityMonitor() {
                        override fun onStartActivity(
                            intent: Intent
                        ): Instrumentation.ActivityResult? {
                            if (intent.action !in setOf(Intent.ACTION_DIAL, Intent.ACTION_VIEW))
                                return null
                            contactIntent.set(intent)
                            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                        }
                    }
                test.addMonitor(contactMonitor)
                try {
                    for (tab in listOf("Objekte", "Vorgänge", "Geld")) {
                        selectCustomerTab(tab)
                        shown(UiTags.entityDetail("customers", customerId))
                        contactIntent.set(null)
                        click("Anrufen")
                        waitUntil("CUSTOMER_CONTACT_DIALS_EXACT_ORIGINAL_$tab") {
                            contactIntent.get()?.let {
                                it.action == Intent.ACTION_DIAL &&
                                    it.data?.scheme == "tel" &&
                                    it.data?.schemeSpecificPart ==
                                        contactOriginal.getString("phone")
                            } == true
                        }
                        contactIntent.set(null)
                        click("Route")
                        val address =
                            listOf("street", "postal_code", "city")
                                .map { contactOriginal.optString(it) }
                                .filter { it.isNotBlank() }
                                .joinToString(", ")
                        waitUntil("CUSTOMER_CONTACT_ROUTES_EXACT_ORIGINAL_$tab") {
                            contactIntent.get()?.let {
                                it.action == Intent.ACTION_VIEW &&
                                    it.dataString == "geo:0,0?q=" + Uri.encode(address)
                            } == true
                        }
                        shown(UiTags.entityDetail("customers", customerId))
                        val afterContacts = tools.call("get_customer", customerArgs)
                        yes(
                            afterContacts.optBoolean("ok") &&
                                afterContacts.getJSONObject("data").getString("id") == customerId &&
                                afterContacts.getString("record_hash") ==
                                    contactProof.getString("record_hash") &&
                                selectedArea(UiTags.area("customers")) &&
                                PixelMcpWriteTools.hash(
                                    PixelMcpReadTools.effective(store.getState())
                                ) == contactBusiness &&
                                store.ownerValue(SettlementRules.KEY, "{}") == contactLedger,
                            "CUSTOMER_CONTACT_CANCELED_INTENTS_KEEP_ORIGINAL_AND_BUSINESS_$tab",
                        )
                    }
                    captureSyntheticScreen("customer-contact-actions-readback")
                } finally {
                    test.removeMonitor(contactMonitor)
                }
                selectCustomerTab("Objekte")
                val businessBeforePlus =
                    PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
                click(UiTags.CREATE)
                shown("Schnellzugriff")
                for (label in listOf("Arbeiten", "L.C.R.", "Geräte")) reveal(label)
                captureSyntheticScreen("customer-context-plus")
                click(UiTags.createChoice("work"))
                shown("Kunde: $name")
                enter("Was wurde erledigt? *", "[TEST] QA Kunden-Plus-Kontext")
                waitUntil("CUSTOMER_PLUS_ORIGINAL_CONTEXT_DURABLE_IN_WORK_DRAFT") {
                    workDraft().optJSONObject("values")?.optString("customer_id") == customerId
                }
                val workRequest = workDraft().getJSONObject("values").getString("_request_id")
                recreate()
                shown("Kunde: $name")
                yes(
                    workDraft().getJSONObject("values").getString("customer_id") == customerId &&
                        workDraft().getJSONObject("values").getString("_request_id") == workRequest,
                    "CUSTOMER_PLUS_RECREATE_RETAINS_EXACT_CUSTOMER_AND_REQUEST",
                )
                val originalCustomerLabel =
                    "Kunde: " +
                        read("get_customer", JSONObject().put("customer_id", customerId))
                            .getString("name")
                waitStable("CUSTOMER_PLUS_RECREATED_ORIGINAL_NAME_READY", stableMs = 500) {
                    node(originalCustomerLabel) != null &&
                        workDraft().getJSONObject("values").getString("customer_id") == customerId
                }
                captureSyntheticScreen("customer-plus-work-context")
                discardWork()
                yes(
                    PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) ==
                        businessBeforePlus,
                    "CUSTOMER_PLUS_OPEN_CONTEXT_AND_DISCARD_CREATE_NO_BUSINESS_RECORD",
                )
            }
            selectCustomerTab("Vorgänge")
            reveal("Fotos")
            click("Fotos")
            shown(name)
            shown("QA Kontextfoto $index")
            yes(node("QA Kontextfoto ${1-index}") == null, "PHOTOS_KEEP_CUSTOMER_$index")
            if (index == 0) captureSyntheticScreen("customer-photos")
            click(UiTags.BACK)
            openDetails("Angebote")
            reveal("Alle Angebote")
            click("Alle Angebote")
            shown("QA Kontextangebot $index")
            yes(node("QA Kontextangebot ${1-index}") == null, "OFFERS_KEEP_CUSTOMER_$index")
            if (index == 0) captureSyntheticScreen("customer-offers")
            yes(selectedArea(UiTags.area("customers")), "OFFERS_RETAINS_CUSTOMER_AREA_$index")
            click("QA Kontextangebot $index")
            click("Zur Liste")
            shown("QA Kontextangebot $index")
            yes(node("QA Kontextangebot ${1-index}") == null, "OFFER_DETAIL_RETURN_FILTER_$index")
            openCustomerList()
            searchCustomers(name)
            click(name)
            selectCustomerTab("Vorgänge")
            openDetails("Aufträge")
            reveal("Alle Aufträge")
            click("Alle Aufträge")
            shown("QA Kontextauftrag $index")
            yes(node("QA Kontextauftrag ${1-index}") == null, "ORDERS_KEEP_CUSTOMER_$index")
            click(UiTags.BACK)
            selectCustomerTab("Objekte")
            click("Alle Objekte")
            shown("QA Kontextobjekt $index")
            yes(node("QA Kontextobjekt ${1-index}") == null, "PROPERTIES_KEEP_CUSTOMER_$index")
            click("Objekt anlegen")
            shown("Kunde: $name")
            click("Zurück")
            shown("Objekt anlegen")
            click(UiTags.BACK)
            selectCustomerTab("Vorgänge")
            openDetails("Termine")
            reveal("Alle Termine")
            click("Alle Termine")
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
                        selectCustomerTab("Objekte")
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
                    searchCustomers(name)
                    click(name)
                }
                val invoiceCount =
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("invoices").length()
                selectCustomerTab("Geld")
                shown("Offene Stunden")
                selectCustomerTab("Vorgänge")
                openDetails("Arbeiten")
                reveal("Alle Arbeiten")
                click("Alle Arbeiten")
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
        orderAndInquiryCreationUi()
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
                Triple("billing", "Abrechnung", "Offen abzurechnen"),
                Triple("offers", "Angebote", "Angebot anlegen"),
            )) {
            val values = ArrayList<Double>()
            repeat(42) { sample ->
                var start = 0L
                if (name == "billing") {
                    click(UiTags.area("today"))
                    click(UiTags.area("accounting"), onAction = { start = it })
                } else {
                    openOffice()
                    click(entry, onAction = { start = it })
                }
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
        waitUntil("SEARCH_FOCUSED_WINDOW_$label") { uiForegroundReady() }
        yes(tapFieldForKeyboard(label), "SEARCH_FOCUS_$label")
        var focusAttempts = 1
        var lastFocus = SystemClock.uptimeMillis()
        waitUntil("SEARCH_IME_VISIBLE") {
            if (keyboardVisible()) return@waitUntil true
            if (
                focusAttempts < 3 &&
                    SystemClock.uptimeMillis() - lastFocus >= 1000 &&
                    uiForegroundReady()
            ) {
                if (tapFieldForKeyboard(label)) focusAttempts++
                lastFocus = SystemClock.uptimeMillis()
            }
            false
        }
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
        searchCustomers(fixturePrefix + "000")
        showKeyboard(sharedSearchLabel)
        visibleResult(fixturePrefix + "000", requireKeyboard = true)
        captureSyntheticScreen("search-customer-list", requireKeyboard = true)
        visibleResult(fixturePrefix + "000", tap = true, requireKeyboard = true)
        reveal("Arbeit starten")
        recreate()
        click(UiTags.BACK)
        yes(
            field(sharedSearchLabel)?.text?.toString()?.contains(fixturePrefix + "000") == true,
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
        val context =
            mapOf(
                "Objekte" to "properties",
                "Fotos" to "photos",
                "Leistungskatalog" to "catalog",
                "Suche" to "revier_search",
                "Offene Stunden" to "billing",
            )[entry]
        if (context != null) openContext(context)
        else {
            openOffice()
            click(entry)
        }
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
            var bounds = Rect()
            var previousGeometry = ""
            waitUntil("SWIPE_REAL_TARGET_$tag") {
                val target = node(tag) ?: return@waitUntil false
                val taggedBounds = Rect().also(target::getBoundsInScreen)
                // SwipeToDismissBox also exposes its background semantics. Use the
                // actual clickable content row inside this exact entity's gesture box.
                val content =
                    all(target).drop(1).firstOrNull { it.isVisibleToUser && it.isClickable }
                        ?: target
                bounds = Rect().also(content::getBoundsInScreen)
                val screen = Rect()
                var density = 0f
                test.runOnMainSync {
                    activity?.window?.decorView?.let {
                        it.getWindowVisibleDisplayFrame(screen)
                        density = it.resources.displayMetrics.density
                    }
                }
                // Swipe the real 42 dp list row; the 48 dp minimum applies to buttons.
                val visible =
                    density > 0f &&
                        screen.contains(bounds) &&
                        bounds.width() >= (48 * density).toInt() &&
                        bounds.height() >= (42 * density).toInt()
                val geometry = "tag=$taggedBounds content=$bounds viewport=$screen density=$density"
                if (geometry != previousGeometry) {
                    test.sendStatus(0, Bundle().apply { putString("swipe_geometry", geometry) })
                    previousGeometry = geometry
                }
                if (!visible) {
                    content.performAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id
                    )
                    settleUi()
                }
                visible
            }
            checks++
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
            reveal("Alle Abschlüsse")
            click("Alle Abschlüsse")
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
                    .put(
                        "visit_ids",
                        JSONArray()
                            .put(firstVisit)
                            .put(secondVisit)
                            .put("appointments:" + appointmentId),
                    ),
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
            click(UiTags.planningView("day"))
            shown(UiTags.PLANNING_GRID)
            yes(stateHash() == beforeViews, "BOARD_VIEWS_AND_WEATHER_ARE_READ_ONLY")
            store.putOwnerValue("planning:settings", priorSettings)
            store.notifyCommittedChange()

            fun dragHalfHour(kind: String, id: String) {
                val tag = UiTags.planningEntry(kind, id)
                reveal(UiTags.PLANNING_GRID)
                val scrollUp = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
                val scrollDown = AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
                fun boardScroller(): AccessibilityNodeInfo? {
                    val grid = node(UiTags.PLANNING_GRID) ?: return null
                    return all(grid).firstOrNull { candidate ->
                        candidate.isScrollable &&
                            candidate.actionList.any { it.id == scrollUp || it.id == scrollDown }
                    }
                }
                fun visibleBounds(target: AccessibilityNodeInfo): Rect {
                    val bounds = Rect().also(target::getBoundsInScreen)
                    var parent = target.parent
                    while (parent != null) {
                        val parentBounds = Rect().also(parent::getBoundsInScreen)
                        if (!parentBounds.isEmpty && !bounds.intersect(parentBounds))
                            bounds.setEmpty()
                        parent = parent.parent
                    }
                    return bounds
                }
                fun boardPageScroller(): AccessibilityNodeInfo? {
                    var parent = node(UiTags.PLANNING_GRID)?.parent
                    while (parent != null) {
                        if (
                            parent.isScrollable &&
                                parent.actionList.any { it.id == scrollUp || it.id == scrollDown }
                        )
                            return parent
                        parent = parent.parent
                    }
                    return null
                }
                // Date and view controls belong to the outer page. In this three-entry
                // fixture the grid is its final item; expose that item before time scrolling.
                fun exposeBoardPage(): Boolean {
                    val page = boardPageScroller() ?: return false
                    if (page.actionList.any { it.id == scrollDown }) {
                        performVerticalScroll(page, forward = true)
                        settleUi()
                        return false
                    } else {
                        val viewport = boardScroller()?.let(::visibleBounds) ?: Rect()
                        val minimum = (48 * context.resources.displayMetrics.density).toInt()
                        test.sendStatus(
                            0,
                            Bundle().apply {
                                putString("board_drag_outer_page", "$tag viewport=$viewport")
                            },
                        )
                        return viewport.width() >= minimum && viewport.height() >= minimum * 2
                    }
                }
                waitUntil("BOARD_OUTER_PAGE_EXPOSES_GRID_$tag") { exposeBoardPage() }
                // This is the board's inner time viewport, not the surrounding page list.
                waitUntil("BOARD_TIME_VIEWPORT_START_$tag") {
                    val viewport = boardScroller() ?: return@waitUntil false
                    if (viewport.actionList.none { it.id == scrollUp }) true
                    else {
                        performVerticalScroll(viewport, forward = false)
                        settleUi()
                        false
                    }
                }
                // An upward scroll at the time boundary can carry remaining distance into
                // the outer page. Restore its viewport after that reset, not just before it.
                waitUntil("BOARD_OUTER_PAGE_AFTER_TIME_RESET_$tag") { exposeBoardPage() }
                var hit = Rect()
                waitUntil("BOARD_REAL_DRAG_TARGET_$tag") {
                    val viewportNode = boardScroller() ?: return@waitUntil false
                    val viewport = visibleBounds(viewportNode)
                    val target = node(tag)
                    val raw = target?.let { Rect().also(it::getBoundsInScreen) }
                    hit = target?.let(::visibleBounds) ?: Rect()
                    val density = context.resources.displayMetrics.density
                    val minimum = (48 * density).toInt()
                    val delta = 30 * density
                    val grid = node(UiTags.PLANNING_GRID)
                    val canPanRight =
                        grid?.actionList?.any {
                            it.id ==
                                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
                        } == true
                    val canPanLeft =
                        grid?.actionList?.any {
                            it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
                        } == true
                    val clippedRight = raw != null && raw.right >= viewport.right && canPanRight
                    val clippedLeft = raw != null && raw.left <= viewport.left && canPanLeft
                    val visible =
                        raw != null &&
                            hit == raw &&
                            hit.width() >= minimum &&
                            hit.height() >= minimum &&
                            !clippedRight &&
                            !clippedLeft &&
                            hit.exactCenterY() + delta < viewport.bottom
                    if (visible) return@waitUntil true
                    test.sendStatus(
                        0,
                        Bundle().apply {
                            putString(
                                "board_drag_viewport",
                                "$tag viewport=$viewport raw=$raw hit=$hit",
                            )
                        },
                    )
                    if (viewport.width() < minimum || viewport.height() < minimum * 2) {
                        exposeBoardPage()
                        return@waitUntil false
                    }
                    if (
                        raw != null &&
                            raw.top >= viewport.top &&
                            raw.bottom <= viewport.bottom &&
                            (clippedRight ||
                                clippedLeft ||
                                raw.left < viewport.left ||
                                raw.right > viewport.right)
                    ) {
                        // The first move can put the later appointment into another display
                        // lane. Pan that real grid before pressing its fully visible card.
                        val scrollRight = clippedRight || raw.right > viewport.right
                        val y = viewport.top + (20 * density).coerceAtMost(viewport.height() / 4f)
                        val startX =
                            if (scrollRight) viewport.right - 20 * density
                            else viewport.left + 20 * density
                        val distance = (96 * density).coerceAtMost(viewport.width() / 3f)
                        val endX = startX + if (scrollRight) -distance else distance
                        val down = SystemClock.uptimeMillis()
                        fun panEvent(action: Int, x: Float) {
                            val motion =
                                MotionEvent.obtain(
                                    down,
                                    SystemClock.uptimeMillis(),
                                    action,
                                    x,
                                    y,
                                    0,
                                )
                            motion.source = InputDevice.SOURCE_TOUCHSCREEN
                            try {
                                yes(
                                    automation.injectInputEvent(motion, true),
                                    "BOARD_HORIZONTAL_VIEWPORT_TOUCH_$tag",
                                )
                            } finally {
                                motion.recycle()
                            }
                        }
                        panEvent(MotionEvent.ACTION_DOWN, startX)
                        for (step in 1..12) {
                            SystemClock.sleep(40)
                            panEvent(MotionEvent.ACTION_MOVE, startX + (endX - startX) * step / 12)
                        }
                        SystemClock.sleep(100)
                        panEvent(MotionEvent.ACTION_UP, endX)
                        settleUi()
                        return@waitUntil false
                    }
                    // Short real swipes in the hour gutter expose the card without starting
                    // its long-press drag. Page-sized accessibility scrolls can skip a card.
                    val x = viewport.left + (24 * density).coerceAtMost(viewport.width() / 4f)
                    val startY = viewport.bottom - 20 * density
                    val endY = startY - (96 * density).coerceAtMost(viewport.height() / 3f)
                    val down = SystemClock.uptimeMillis()
                    fun scrollEvent(action: Int, y: Float) {
                        val motion =
                            MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
                        motion.source = InputDevice.SOURCE_TOUCHSCREEN
                        try {
                            yes(
                                automation.injectInputEvent(motion, true),
                                "BOARD_VIEWPORT_TOUCH_$tag",
                            )
                        } finally {
                            motion.recycle()
                        }
                    }
                    scrollEvent(MotionEvent.ACTION_DOWN, startY)
                    for (step in 1..12) {
                        SystemClock.sleep(40)
                        scrollEvent(MotionEvent.ACTION_MOVE, startY + (endY - startY) * step / 12)
                    }
                    SystemClock.sleep(100)
                    scrollEvent(MotionEvent.ACTION_UP, endY)
                    settleUi()
                    false
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
            val masterBeforeOrder =
                PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
            val expectedTourIds = listOf(secondVisit, firstVisit, "appointments:" + appointmentId)
            fun exactTourOrder(result: JSONObject): Boolean {
                val persisted = result.getJSONObject("tour").getJSONArray("visit_ids")
                val stops = result.getJSONArray("stops")
                return (0 until persisted.length()).map(persisted::getString) == expectedTourIds &&
                    stops.length() == 3 &&
                    SettlementRules.rows(stops).map { it.getString("id") } == expectedTourIds &&
                    SettlementRules.rows(stops).map { it.getString("source_id") }.toSet() ==
                        setOf(firstVisit, secondVisit, appointmentId) &&
                    stops.getJSONObject(2).getString("source_collection") == "appointments"
            }
            click(UiTags.PLANNING_TOUR)
            reveal(UiTags.TOUR_ORDER)
            fun tourOrderingSelected() =
                nodes().any { candidate ->
                    candidate.isVisibleToUser &&
                        matches(candidate, UiTags.TOUR_ORDER) &&
                        generateSequence(candidate) { it.parent }.any(::navigationSelected)
                }
            if (!tourOrderingSelected()) click(UiTags.TOUR_ORDER)
            waitUntil("BOARD_TOUR_ORDERING_SELECTED") { tourOrderingSelected() }
            click(UiTags.tourMove(secondVisit, "up"))
            click(UiTags.TOUR_SAVE)
            waitUntil("BOARD_TOUR_ORDER_COMMITTED") {
                exactTourOrder(read("get_day_tour", JSONObject().put("date", day.toString())))
            }
            yes(
                PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())) ==
                    masterBeforeOrder,
                "BOARD_TOUR_ORDER_KEEPS_MASTER_ORIGINALS",
            )
            yes(
                read("get_visit", JSONObject().put("visit_id", firstVisit))
                    .getString("record_hash") == movedVisit.getString("record_hash") &&
                    read("get_visit", JSONObject().put("visit_id", secondVisit))
                        .getJSONObject("record")
                        .getString("window_start") == "15:00",
                "BOARD_TOUR_ORDER_KEEPS_VISIT_TIMES_AND_IDENTITIES",
            )
            PixelLocalStore(context).use { reopened ->
                val reopenedTools =
                    PixelMcpReadTools(
                        reopened.readSource(),
                        reopened,
                        PixelOwnerAccess(context, reopened),
                    )
                val persisted =
                    reopenedTools
                        .call("get_day_tour", JSONObject().put("date", day.toString()))
                        .getJSONObject("data")
                yes(exactTourOrder(persisted), "BOARD_TOUR_ORDER_DURABLE_IN_REOPENED_STORE")
            }
            recreate()
            reveal(UiTags.TOUR_NAVIGATE)
            val saved = read("get_day_tour", JSONObject().put("date", day.toString()))
            yes(exactTourOrder(saved), "BOARD_TOUR_ORDER_DURABLE_AFTER_RECREATION")
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
                monitorResult.get()?.startsWith("https://www.google.com/maps/dir/") == true
            }
            yes(
                monitorResult.get().contains(Uri.encode("[TEST] QA Gartenweg b")) &&
                    monitorResult.get().contains(Uri.encode("[TEST] QA Gartenweg a")) &&
                    monitorResult.get().contains(Uri.encode("[TEST] QA Terminort")),
                "BOARD_NAVIGATION_HANDOFF_HAS_ALL_SAVED_ADDRESSES",
            )
            yes(stateHash() == beforeNavigation, "BOARD_NAVIGATION_DOES_NOT_WRITE_BUSINESS_DATA")
            captureSyntheticScreen("planning-board-tour")

            // Opening and automatic date prefill are read-only; only the explicit save may
            // create this empty original. Keep this after the three-source tour proof above.
            val blankDay = day.toString()
            fun appointmentRows(): List<JSONObject> =
                SettlementRules.rows(
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments")
                )
            val idsBeforeEmpty = appointmentRows().map { it.getString("id") }.toSet()
            val workAndDeliveryBeforeEmpty = planningWorkAndDeliveryState()
            val workBeforeEmpty =
                PixelMcpWriteTools.hash(
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
                )
            openContext("planning", args = JSONObject().put("date", blankDay))
            reveal(UiTags.PRIMARY)
            click(UiTags.PRIMARY)
            reveal("Titel / Tätigkeit")
            waitUntil("BOARD_UNTOUCHED_PLAN_EDITOR_READY") {
                field("Titel / Tätigkeit")?.text?.toString()?.isBlank() == true &&
                    node("Termin speichern") != null
            }
            back()
            reveal(UiTags.PLANNING_TOUR)
            waitStable("BOARD_UNTOUCHED_PREFILL_CANCEL_CREATES_NOTHING", stableMs = 1200) {
                appointmentRows().map { it.getString("id") }.toSet() == idsBeforeEmpty
            }
            reveal(UiTags.PRIMARY)
            click(UiTags.PRIMARY)
            reveal("Titel / Tätigkeit")
            waitUntil("BOARD_EMPTY_PLAN_EDITOR_READY") {
                field("Titel / Tätigkeit")?.text?.toString()?.isBlank() == true &&
                    node("Termin speichern") != null
            }
            click(UiTags.PRIMARY)
            waitUntil("BOARD_EXPLICIT_EMPTY_SAVE_ONE_ORIGINAL") {
                appointmentRows().count { it.getString("id") !in idsBeforeEmpty } == 1
            }
            waitUntil("BOARD_EMPTY_SAVE_ACKNOWLEDGED_IN_UI") {
                node("Termin speichern") == null && node(UiTags.PLANNING_TOUR) != null
            }
            val emptyId =
                appointmentRows().single { it.getString("id") !in idsBeforeEmpty }.getString("id")
            fun sourceRows(rows: JSONArray): List<JSONObject> =
                SettlementRules.rows(rows).filter {
                    it.optString("source_collection") == "appointments" &&
                        it.optString("source_id", it.optString("id")) == emptyId
                }
            fun sharedDay(date: String, activeCount: Int, status: String? = null) {
                val board =
                    read(
                        "get_planning_board",
                        JSONObject().put("date_from", date).put("date_to", date),
                    )
                val boardOriginals = sourceRows(board.getJSONArray("items"))
                yes(
                    boardOriginals.size == if (status != null) 1 else activeCount,
                    "BOARD_UNTIMED_PUBLIC_PLANNING_SOURCE_$date",
                )
                if (status != null)
                    yes(
                        boardOriginals.single().getJSONObject("record").getString("status") ==
                            status,
                        "BOARD_UNTIMED_PUBLIC_PLANNING_STATUS_$status",
                    )
                val today = read("get_revier_overview", JSONObject().put("date", date))
                val tour = read("get_day_tour", JSONObject().put("date", date))
                yes(
                    sourceRows(today.getJSONArray("planned_visits")).size == activeCount &&
                        sourceRows(tour.getJSONArray("stops")).size == activeCount,
                    "BOARD_UNTIMED_PUBLIC_TODAY_TOUR_SAME_SOURCE_$date",
                )
                for (row in
                    sourceRows(today.getJSONArray("planned_visits")) +
                        sourceRows(tour.getJSONArray("stops"))) yes(
                    row.optString("window_start").isBlank() &&
                        row.optString("window_end").isBlank() &&
                        row.optString("customer_id").isBlank() &&
                        row.optString("location").isBlank(),
                    "BOARD_UNTIMED_PROJECTIONS_NEVER_INVENT_TIME_CUSTOMER_OR_ADDRESS",
                )
            }
            val emptyProof = read("get_appointment", JSONObject().put("appointment_id", emptyId))
            val emptyOriginal = emptyProof.getJSONObject("appointment")
            yes(
                emptyOriginal.getString("title").isBlank() &&
                    emptyOriginal.getString("customer_id").isBlank() &&
                    emptyOriginal.optString("order_id").isBlank(),
                "BOARD_EMPTY_SAVE_DOES_NOT_INVENT_CONTEXT",
            )
            yes(
                emptyOriginal.getString("date") == blankDay &&
                    emptyOriginal.getString("starts_at").isBlank() &&
                    emptyOriginal.getString("ends_at").isBlank(),
                "BOARD_EMPTY_SAVE_PREFILLED_DAY_WITHOUT_INVENTED_TIME",
            )
            yes(
                appointmentRows().size == idsBeforeEmpty.size + 1,
                "BOARD_EMPTY_SAVE_EXACTLY_ONE_NEW_ID",
            )
            sharedDay(blankDay, 1)
            reveal(UiTags.planningEntry("appointment", emptyId))
            captureSyntheticScreen("planning-untimed-original")
            click(UiTags.area("today"))
            reveal("Weitere Termine")
            click("Weitere Termine")
            reveal("Termin")
            shown("Ohne Uhrzeit")
            captureSyntheticScreen("planning-untimed-today")
            click("Termin")
            waitUntil("BOARD_UNTIMED_TODAY_OPENS_EXACT_ORIGINAL") {
                var sameOriginal = false
                test.runOnMainSync {
                    val current = activity as? MainActivity ?: return@runOnMainSync
                    val delegate =
                        MainActivity::class
                            .java
                            .declaredFields
                            .first { it.name.startsWith("controller") }
                            .apply { isAccessible = true }
                            .get(current) as androidx.compose.runtime.State<*>
                    val route = (delegate.value as? NativeAppController)?.route
                    sameOriginal = route?.section == "appointments" && route.id == emptyId
                }
                sameOriginal && node("Termin") != null
            }
            openContext("planning", args = JSONObject().put("date", blankDay))
            click(UiTags.PLANNING_TOUR)
            val withUntimed = read("get_day_tour", JSONObject().put("date", blankDay))
            val untimedPosition =
                SettlementRules.rows(withUntimed.getJSONArray("stops")).indexOfFirst {
                    it.optString("source_id") == emptyId
                } + 1
            yes(untimedPosition > 0, "BOARD_UNTIMED_TOUR_RETAINS_ADDRESSLESS_ORIGINAL")
            reveal("$untimedPosition. Termin")
            revealContaining("Ohne Uhrzeit")
            captureSyntheticScreen("planning-untimed-tour")
            if (!tourOrderingSelected()) click(UiTags.TOUR_ORDER)
            click(UiTags.TOUR_SAVE)
            waitUntil("BOARD_UNTIMED_TOUR_ORDER_ACKNOWLEDGED") {
                !read("get_day_tour", JSONObject().put("date", blankDay))
                    .getJSONObject("tour")
                    .optBoolean("needs_review") && node(UiTags.TOUR_NAVIGATE) != null
            }
            val addresslessTour = read("get_day_tour", JSONObject().put("date", blankDay))
            val routeWithoutUntimed =
                read(
                    "get_tour_navigation",
                    JSONObject()
                        .put("date", blankDay)
                        .put("expected_record_hash", addresslessTour.getString("record_hash")),
                )
            yes(
                sourceRows(addresslessTour.getJSONArray("stops")).size == 1 &&
                    routeWithoutUntimed.getInt("without_address") == 1 &&
                    SettlementRules.rows(addresslessTour.getJSONArray("stops")).size == 4 &&
                    (0 until routeWithoutUntimed.getJSONArray("navigation_stop_ids").length())
                        .none {
                            routeWithoutUntimed.getJSONArray("navigation_stop_ids").getString(it) ==
                                "appointments:$emptyId"
                        } &&
                    routeWithoutUntimed.getJSONArray("addresses").length() == 3,
                "BOARD_UNTIMED_VISIBLE_IN_TOUR_WITHOUT_NAVIGATION_STOP_OR_INVENTED_ADDRESS",
            )
            recreate()
            yes(
                read("get_appointment", JSONObject().put("appointment_id", emptyId))
                    .getString("record_hash") == emptyProof.getString("record_hash"),
                "BOARD_EMPTY_ORIGINAL_DURABLE_AFTER_RECREATION",
            )
            openContext("appointments", emptyId, "detail")
            shown("Termin")
            yes(
                read("get_appointment", JSONObject().put("appointment_id", emptyId))
                    .getJSONObject("appointment")
                    .getString("title")
                    .isBlank(),
                "BOARD_EMPTY_DETAIL_DISPLAY_FALLBACK_PRESERVES_EMPTY_ORIGINAL",
            )
            // Establish only the existing original's edit context; the field edit and its save
            // still go through the actual native controls, writer and reread.
            openContext("appointments", emptyId, "edit")
            reveal("Titel / Tätigkeit")
            waitUntil("BOARD_EMPTY_ORIGINAL_EDITOR_READY") {
                field("Titel / Tätigkeit")?.text?.toString()?.isBlank() == true
            }
            recreate()
            reveal("Titel / Tätigkeit")
            waitUntil("BOARD_EMPTY_ORIGINAL_EDITOR_RECREATED") {
                field("Titel / Tätigkeit")?.text?.toString()?.isBlank() == true
            }
            val editedTitle = "[TEST] QA bewusst leer gespeicherter Termin ergänzt"
            enter("Titel / Tätigkeit", editedTitle)
            waitUntil("BOARD_EMPTY_ORIGINAL_REAL_EDIT_UPDATES_SAME_ID") {
                read("get_appointment", JSONObject().put("appointment_id", emptyId))
                    .getJSONObject("appointment")
                    .optString("title") == editedTitle &&
                    appointmentRows().size == idsBeforeEmpty.size + 1 &&
                    appointmentRows().count { it.getString("id") !in idsBeforeEmpty } == 1
            }
            reveal("Gespeichert")
            waitUntil("BOARD_EMPTY_ORIGINAL_EDIT_ACKNOWLEDGED_IN_UI") {
                actionEnabled(UiTags.PRIMARY) && node("Gespeichert") != null
            }
            val edited =
                read("get_appointment", JSONObject().put("appointment_id", emptyId))
                    .getJSONObject("appointment")
            yes(
                edited.getString("id") == emptyId &&
                    edited.getString("date") == blankDay &&
                    edited.getString("customer_id").isBlank() &&
                    edited.getString("starts_at").isBlank() &&
                    edited.getString("ends_at").isBlank(),
                "BOARD_EMPTY_ORIGINAL_EDIT_PRESERVES_ID_DAY_AND_OPTIONAL_FIELDS",
            )
            yes(
                PixelMcpWriteTools.hash(
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
                ) == workBeforeEmpty,
                "BOARD_EMPTY_PLAN_FLOW_CREATES_NO_ACTUAL_WORK",
            )
            yes(
                planningWorkAndDeliveryState() == workAndDeliveryBeforeEmpty,
                "BOARD_EMPTY_SAVE_AND_EDIT_PRESERVE_WORK_DOCUMENTS_PAYMENTS_AND_DELIVERY",
            )
            captureSyntheticScreen("planning-explicit-empty-save-and-edit")
            val movedBlankDay = day.plusDays(2).toString()
            chooseDate("Datum", movedBlankDay)
            waitUntil("BOARD_UNTIMED_DATE_ONLY_UI_MOVE_UPDATES_SAME_ORIGINAL") {
                read("get_appointment", JSONObject().put("appointment_id", emptyId))
                    .getJSONObject("appointment")
                    .optString("date") == movedBlankDay && node("Gespeichert") != null
            }
            val dateMoved =
                read("get_appointment", JSONObject().put("appointment_id", emptyId))
                    .getJSONObject("appointment")
            yes(
                dateMoved.getString("id") == emptyId &&
                    dateMoved.getString("title") == editedTitle &&
                    dateMoved.optString("customer_id").isBlank() &&
                    dateMoved.optString("order_id").isBlank() &&
                    dateMoved.optString("location").isBlank() &&
                    dateMoved.optString("starts_at").isBlank() &&
                    dateMoved.optString("ends_at").isBlank() &&
                    appointmentRows().size == idsBeforeEmpty.size + 1,
                "BOARD_UNTIMED_DATE_MOVE_PRESERVES_ORIGINAL_WITHOUT_INVENTED_FIELDS",
            )
            sharedDay(blankDay, 0)
            sharedDay(movedBlankDay, 1)
            click(UiTags.PRIMARY)
            openContext("planning", args = JSONObject().put("date", movedBlankDay))
            reveal(UiTags.planningEntry("appointment", emptyId))
            click(UiTags.planningEntry("appointment", emptyId))
            shown(editedTitle)
            click(UiTags.DETAIL_MENU)
            click(UiTags.detailAction("appointment-cancel"))
            waitUntil("BOARD_UNTIMED_UI_CANCEL_CONFIRMED_ON_ORIGINAL") {
                read("get_appointment", JSONObject().put("appointment_id", emptyId))
                    .getJSONObject("appointment")
                    .optString("status") == "cancelled"
            }
            val cancelled =
                read("get_appointment", JSONObject().put("appointment_id", emptyId))
                    .getJSONObject("appointment")
            val preservedFields =
                (dateMoved.keys().asSequence().toSet() + cancelled.keys().asSequence().toSet()) -
                    setOf("status", "updated_at", "record_hash")
            yes(
                preservedFields.all {
                    PixelMcpWriteTools.canonical(cancelled.opt(it)) ==
                        PixelMcpWriteTools.canonical(dateMoved.opt(it))
                } && appointmentRows().size == idsBeforeEmpty.size + 1,
                "BOARD_UNTIMED_CANCEL_PRESERVES_ORIGINAL_HISTORY_WITHOUT_DUPLICATE",
            )
            sharedDay(blankDay, 0)
            sharedDay(movedBlankDay, 0, "cancelled")
            recreate()
            yes(
                read("get_appointment", JSONObject().put("appointment_id", emptyId))
                    .getJSONObject("appointment")
                    .optString("status") == "cancelled" &&
                    PixelMcpWriteTools.hash(
                        PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
                    ) == workBeforeEmpty,
                "BOARD_UNTIMED_MOVE_CANCEL_RECREATION_CREATES_NO_WORK_OR_NEW_ID",
            )
            val deleteTarget = JSONObject().put("entity", "appointment").put("entity_id", emptyId)
            val deletePreview = read("preview_delete", deleteTarget)
            val originalArchives =
                JSONObject(
                    OwnerJournalRules.archives(PixelMcpReadTools.effective(store.getState()))
                        .toString()
                )
            val otherOriginalsBeforeDelete =
                PixelMcpWriteTools.hash(
                    JSONArray(appointmentRows().filter { it.getString("id") != emptyId })
                )
            val financeBeforeDelete = store.ownerValue(SettlementRules.KEY, "{}")
            yes(
                deletePreview.getBoolean("allowed") &&
                    deletePreview.getString("entity_id") == emptyId &&
                    read("get_appointment", JSONObject().put("appointment_id", emptyId))
                        .getJSONObject("appointment")
                        .optString("status") == "cancelled",
                "BOARD_UNTIMED_DELETE_PREVIEW_IS_READ_ONLY_AND_TARGETS_OWN_ORIGINAL",
            )
            fun ownDeleteActions(): List<JSONObject> =
                SettlementRules.rows(
                        JSONObject(store.ownerValue(ListActionTools.KEY, "{}"))
                            .optJSONArray("actions")
                    )
                    .filter {
                        it.optString("entity") == "appointment" &&
                            it.optString("entity_id") == emptyId &&
                            it.optString("original_tool") == "delete_entity"
                    }
            reveal(UiTags.DETAIL_DELETE)
            click(UiTags.DETAIL_DELETE)
            waitUntil("BOARD_UNTIMED_NATIVE_DELETE_RETAINS_CURRENT_PREVIEW_PROOF") {
                ownDeleteActions()
                    .singleOrNull()
                    ?.optJSONObject("original_args")
                    ?.optString("expected_record_hash") ==
                    deletePreview.getString("expected_record_hash")
            }
            waitUntil("BOARD_UNTIMED_NATIVE_DELETE_CONFIRMED_AFTER_UNDO_WINDOW") {
                ownDeleteActions().singleOrNull()?.optString("state") == "done" &&
                    appointmentRows().none { it.getString("id") == emptyId }
            }
            sharedDay(blankDay, 0)
            sharedDay(movedBlankDay, 0)
            try {
                read("get_appointment", JSONObject().put("appointment_id", emptyId))
                error("Deleted planning original unexpectedly remains actively readable")
            } catch (missing: PixelMcpWriteTools.Rejected) {
                yes(
                    missing.code == "REFERENCE_NOT_FOUND",
                    "BOARD_UNTIMED_DELETED_ORIGINAL_NO_LONGER_ACTIVELY_READABLE",
                )
            }
            val afterDelete = PixelMcpReadTools.effective(store.getState())
            val afterArchives = OwnerJournalRules.archives(afterDelete)
            val retainedOwnArchive = afterArchives.optJSONObject("appointment:$emptyId")
            yes(
                originalArchives.keys().asSequence().all {
                    PixelMcpWriteTools.canonical(originalArchives.get(it)) ==
                        PixelMcpWriteTools.canonical(afterArchives.opt(it))
                } &&
                    (retainedOwnArchive == null ||
                        retainedOwnArchive.optJSONObject("record")?.optString("id") == emptyId) &&
                    PixelMcpWriteTools.hash(JSONArray(appointmentRows())) ==
                        otherOriginalsBeforeDelete &&
                    appointmentRows().map { it.getString("id") }.toSet() == idsBeforeEmpty &&
                    PixelMcpWriteTools.hash(afterDelete.getJSONArray("worklogs")) ==
                        workBeforeEmpty &&
                    store.ownerValue(SettlementRules.KEY, "{}") == financeBeforeDelete,
                "BOARD_UNTIMED_NATIVE_DELETE_PRESERVES_OTHER_ORIGINALS_ARCHIVES_WORK_AND_FINANCE",
            )
            recreate()
            sharedDay(blankDay, 0)
            sharedDay(movedBlankDay, 0)
            yes(
                appointmentRows().none { it.getString("id") == emptyId } &&
                    ownDeleteActions().size == 1 &&
                    ownDeleteActions().single().optString("state") == "done",
                "BOARD_UNTIMED_NATIVE_DELETE_DURABLE_WITHOUT_REPEATED_ACTION_OR_NEW_ORIGINAL",
            )
            // Repeat the same explicit global shortcut after closing its first editor. A fresh
            // entry must not restore routeEditorOpened=true with an already closed editor.
            openContext("planning", args = JSONObject().put("date", movedBlankDay))
            val beforeReentry = PixelMcpWriteTools.hash(JSONArray(appointmentRows()))
            val newPlanningDraft =
                "native_form_draft:" + PixelMcpWriteTools.hash("planning:appointment:new")
            fun currentEditorContext(): JSONObject {
                var result = JSONObject()
                test.runOnMainSync {
                    val current = activity as? MainActivity ?: return@runOnMainSync
                    val delegate =
                        MainActivity::class
                            .java
                            .declaredFields
                            .first { it.name.startsWith("controller") }
                            .apply { isAccessible = true }
                            .get(current) as androidx.compose.runtime.State<*>
                    val route =
                        (delegate.value as? NativeAppController)?.route ?: return@runOnMainSync
                    result =
                        JSONObject()
                            .put("section", route.section)
                            .put("mode", route.mode)
                            .put("args", JSONObject(route.args.toString()))
                }
                return result
            }
            fun assertUnchangedNewEditor() {
                reveal("Kunde (optional): Keine Auswahl")
                reveal("Uhrzeit (optional)")
                reveal("Titel / Tätigkeit")
                yes(
                    field("Titel / Tätigkeit")?.text?.toString()?.isBlank() == true &&
                        node("Termin speichern") != null &&
                        actionEnabled(UiTags.PRIMARY),
                    "BOARD_GLOBAL_PLUS_REENTRY_HAS_REAL_OPTIONAL_EDITOR_CONTROLS",
                )
            }
            fun discardUnchangedNewEditor() {
                click("Ungespeicherten Entwurf verwerfen")
                waitUntil("BOARD_GLOBAL_PLUS_REENTRY_DISCARD_CLOSES_EDITOR") {
                    node("Termin speichern") == null &&
                        node(UiTags.PLANNING_TOUR) != null &&
                        store.ownerValue(newPlanningDraft, "").isEmpty()
                }
            }
            reveal(UiTags.CREATE)
            click(UiTags.CREATE)
            click(UiTags.createChoice("visit"))
            assertUnchangedNewEditor()
            val firstEntryContext = currentEditorContext()
            val firstEntryArgs = firstEntryContext.getJSONObject("args")
            val firstEntryId = firstEntryArgs.getString("_editor_entry")
            yes(
                firstEntryContext.getString("section") == "appointments" &&
                    firstEntryContext.getString("mode") == "new" &&
                    firstEntryId.isNotBlank() &&
                    firstEntryArgs.getString("date") == movedBlankDay,
                "BOARD_GLOBAL_PLUS_OPENS_OPTIONAL_EDITOR_IN_CURRENT_DAY_CONTEXT",
            )
            discardUnchangedNewEditor()
            reveal(UiTags.CREATE)
            click(UiTags.CREATE)
            click(UiTags.createChoice("visit"))
            assertUnchangedNewEditor()
            val secondEntryContext = currentEditorContext()
            val secondEntryArgs = secondEntryContext.getJSONObject("args")
            val secondEntryId = secondEntryArgs.getString("_editor_entry")
            val firstBusinessContext = JSONObject(firstEntryArgs.toString())
            val secondBusinessContext = JSONObject(secondEntryArgs.toString())
            firstBusinessContext.remove("_editor_entry")
            secondBusinessContext.remove("_editor_entry")
            yes(
                secondEntryContext.getString("section") == "appointments" &&
                    secondEntryContext.getString("mode") == "new" &&
                    secondEntryId.isNotBlank() &&
                    secondEntryId != firstEntryId &&
                    PixelMcpWriteTools.canonical(firstBusinessContext) ==
                        PixelMcpWriteTools.canonical(secondBusinessContext),
                "BOARD_GLOBAL_PLUS_SAME_CONTEXT_REOPENS_A_FRESH_EDITOR_ENTRY",
            )
            recreate()
            assertUnchangedNewEditor()
            yes(
                currentEditorContext().getJSONObject("args").getString("_editor_entry") ==
                    secondEntryId,
                "BOARD_GLOBAL_PLUS_RECREATION_RETAINS_SAME_OPEN_EDITOR_ENTRY",
            )
            discardUnchangedNewEditor()
            waitStable(
                "BOARD_GLOBAL_PLUS_REENTRY_AND_DISCARD_NEVER_CREATE_OR_RETAIN_A_DRAFT",
                stableMs = 1200,
            ) {
                PixelMcpWriteTools.hash(JSONArray(appointmentRows())) == beforeReentry &&
                    appointmentRows().map { it.getString("id") }.toSet() == idsBeforeEmpty &&
                    store.ownerValue(newPlanningDraft, "").isEmpty() &&
                    PixelMcpWriteTools.hash(
                        PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
                    ) == workBeforeEmpty
            }
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
        var addedPhotoId = ""
        var addedPhotoOriginal: File? = null
        var selectedPhotoFile: File? = null
        val photoSelection = java.util.concurrent.atomic.AtomicReference<Uri>()
        val cameraRequests = java.util.concurrent.atomic.AtomicInteger()
        val galleryRequests = java.util.concurrent.atomic.AtomicInteger()
        val navigation = java.util.concurrent.atomic.AtomicReference<Intent>()
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action == android.provider.MediaStore.ACTION_IMAGE_CAPTURE) {
                        cameraRequests.incrementAndGet()
                        return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                    }
                    if (intent.action == Intent.ACTION_OPEN_DOCUMENT && intent.type == "image/*") {
                        galleryRequests.incrementAndGet()
                        val selected = photoSelection.get()
                        return if (selected == null)
                            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                        else
                            Instrumentation.ActivityResult(
                                Activity.RESULT_OK,
                                Intent()
                                    .setData(selected)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                    }
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
            val beforeRemovedCare =
                PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState()))
            try {
                tools.call(
                    "create_care_plan",
                    JSONObject()
                        .put("request_id", "qa_timeline_removed_care")
                        .put("property_id", property)
                        .put("title", "[TEST] QA entfernte Pflege")
                        .put("anchor_date", day.toString())
                        .put("interval_days", 7)
                        .put("planned_minutes", 60),
                )
                error("Removed care action unexpectedly succeeded")
            } catch (removed: PixelMcpWriteTools.Rejected) {
                yes(removed.code == "FUNCTION_REMOVED", "TIMELINE_CARE_EXPLICITLY_REMOVED")
            }
            yes(
                beforeRemovedCare ==
                    PixelMcpWriteTools.hash(PixelMcpReadTools.effective(store.getState())),
                "TIMELINE_REMOVED_CARE_HAS_NO_BUSINESS_SIDE_EFFECT",
            )
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
                NativeSettlementNativeTests.draft("billed", "20", work)
                    .put("billing_customer_id", customer)
            val settlement =
                read(
                        "save_settlement_draft",
                        JSONObject()
                            .put("request_id", "qa_timeline_settlement")
                            .put("draft", draft),
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
                openOfficeEntry("Objekte")
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
                navigation.get().dataString!!.contains(Uri.encode("[TEST] QA Zeitleistenweg 7")),
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
            yes(node(UiTags.objectTab("care")) == null, "TIMELINE_HAS_NO_CARE_TAB")
            yes(node(UiTags.OBJECT_PLAN) == null, "TIMELINE_HAS_NO_OBJECT_PLANNING_ACTION")
            fun photos() =
                SettlementRules.rows(
                    PixelMcpReadTools.effective(store.getState()).getJSONArray("photos")
                )
            fun nonPhotoOriginals(): String {
                val state = PixelMcpReadTools.effective(store.getState())
                val unchanged = JSONObject()
                for (collection in
                    listOf(
                        "customers",
                        "orders",
                        "offers",
                        "worklogs",
                        "invoices",
                        "appointments",
                    )) unchanged.put(collection, state.getJSONArray(collection))
                return PixelMcpWriteTools.hash(unchanged)
            }
            val photoIdsBeforeAdd = photos().map { it.getString("id") }.toSet()
            val originalBusinessBeforeAdd = nonPhotoOriginals()
            val originalPropertyHash =
                read("get_property", JSONObject().put("property_id", property))
                    .getString("record_hash")
            val originalSettlement = store.ownerValue("settlement:v1", "{}")
            val existingPhotoBytes = requireNotNull(photoOriginal).readBytes()
            selectTab(UiTags.objectTab("photos"))
            shown("Foto hinzufügen")
            click(UiTags.PRIMARY)
            waitUntil("TIMELINE_PHOTO_ACTION_OPENS_NATIVE_IMPORT") {
                cameraRequests.get() == 1 && node("Foto auswählen") != null
            }
            shown(UiTags.context("properties", property))
            // The native selection contract is exercised with a cancelled synthetic result;
            // physical camera/provider interaction is a separate device acceptance.
            click("Foto auswählen")
            waitUntil("TIMELINE_PHOTO_GALLERY_CANCEL_RETURNS_TO_IMPORT") {
                galleryRequests.get() == 1 && node("Foto auswählen") != null
            }
            yes(
                photos().map { it.getString("id") }.toSet() == photoIdsBeforeAdd &&
                    nonPhotoOriginals() == originalBusinessBeforeAdd &&
                    node("Ausgewähltes Foto") == null &&
                    !actionEnabled(UiTags.PRIMARY),
                "TIMELINE_PHOTO_CANCEL_CREATES_NO_ORIGINAL",
            )
            selectedPhotoFile = PhotoCaptureProvider.createTarget(context)
            val selectedBitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            selectedBitmap.eraseColor(0xff257946.toInt())
            val encodedPhoto = java.io.ByteArrayOutputStream()
            yes(
                selectedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, encodedPhoto),
                "TIMELINE_SYNTHETIC_GALLERY_IMAGE_ENCODED",
            )
            selectedBitmap.recycle()
            // A small metadata-free JPEG has an exact, known byte identity after import.
            val selectedBytes =
                PhotoPolicy.withoutMetadata(encodedPhoto.toByteArray(), "image/jpeg")
            requireNotNull(selectedPhotoFile).writeBytes(selectedBytes)
            photoSelection.set(PhotoCaptureProvider.uri(context, selectedPhotoFile))
            shown(UiTags.context("properties", property))
            click("Foto auswählen")
            waitUntil("TIMELINE_PHOTO_GALLERY_SELECTED_DRAFT_ONLY") {
                galleryRequests.get() == 2 && node("Ausgewähltes Foto") != null
            }
            yes(
                photos().map { it.getString("id") }.toSet() == photoIdsBeforeAdd &&
                    nonPhotoOriginals() == originalBusinessBeforeAdd,
                "TIMELINE_PHOTO_SELECTION_DOES_NOT_SAVE",
            )
            val addedCaption = "[TEST] QA Foto bewusst im Objekt gespeichert"
            enter("Bildunterschrift", addedCaption)
            if (keyboardVisible()) back()
            click("Foto speichern")
            waitUntil("TIMELINE_PHOTO_EXPLICIT_SAVE_ONE_ORIGINAL") {
                photos().count { it.getString("id") !in photoIdsBeforeAdd } == 1
            }
            addedPhotoId =
                photos().single { it.getString("id") !in photoIdsBeforeAdd }.getString("id")
            val addedProof = read("get_photo", JSONObject().put("photo_id", addedPhotoId))
            val addedPhoto = addedProof.getJSONObject("photo")
            yes(
                addedPhoto.getString("id") == addedPhotoId &&
                    addedPhoto.getString("property_id") == property &&
                    addedPhoto.getString("customer_id") == customer &&
                    addedPhoto.getString("caption") == addedCaption &&
                    photos().size == photoIdsBeforeAdd.size + 1,
                "TIMELINE_PHOTO_SAVED_EXACT_OBJECT_CONTEXT",
            )
            addedPhotoOriginal =
                store
                    .photoAttachmentStore()
                    .verifiedFile(requireNotNull(store.photoAttachment(addedPhotoId)))
            yes(
                requireNotNull(addedPhotoOriginal).readBytes().contentEquals(selectedBytes),
                "TIMELINE_PHOTO_SAVED_ORIGINAL_BYTES",
            )
            yes(
                nonPhotoOriginals() == originalBusinessBeforeAdd &&
                    read("get_property", JSONObject().put("property_id", property))
                        .getString("record_hash") == originalPropertyHash &&
                    store.ownerValue("settlement:v1", "{}") == originalSettlement &&
                    requireNotNull(photoOriginal).readBytes().contentEquals(existingPhotoBytes),
                "TIMELINE_PHOTO_ADD_ONLY_WRITES_NEW_PHOTO_AND_LINK",
            )
            reveal("Foto gespeichert")
            recreate()
            shown("Foto gespeichert")
            val reopenedPhotos = PixelLocalStore(context)
            try {
                val reopenedPhoto =
                    PixelMcpReadTools.effective(reopenedPhotos.getState())
                        .getJSONArray("photos")
                        .let(SettlementRules::rows)
                        .single { it.getString("id") == addedPhotoId }
                yes(
                    reopenedPhoto.getString("property_id") == property &&
                        reopenedPhoto.getString("customer_id") == customer &&
                        reopenedPhotos
                            .photoAttachmentStore()
                            .verifiedFile(
                                requireNotNull(reopenedPhotos.photoAttachment(addedPhotoId))
                            )
                            .readBytes()
                            .contentEquals(selectedBytes),
                    "TIMELINE_PHOTO_ORIGINAL_AND_CONTEXT_AFTER_REOPEN",
                )
            } finally {
                reopenedPhotos.close()
            }
            click("Fertig")
            selectTab(UiTags.objectTab("photos"))
            val savedPhotoIds = photoIdsBeforeAdd + addedPhotoId
            val savedPhotoHash =
                read("get_photo", JSONObject().put("photo_id", addedPhotoId))
                    .getString("record_hash")
            click(UiTags.PRIMARY)
            waitUntil("TIMELINE_PHOTO_SECOND_ENTRY_OPENS_FRESH_IMPORT") {
                cameraRequests.get() == 2 &&
                    node("Noch nicht gespeichert") != null &&
                    node("Foto auswählen") != null
            }
            shown(UiTags.context("properties", property))
            val secondEntryHasNoPreviousImage =
                node("Ausgewähltes Foto") == null && node("Gespeichertes Foto öffnen") == null
            // Header and caption occupy separate scroll positions on the small CI viewport.
            // Inspect both through the real visible controls instead of requiring them together.
            reveal("Bildunterschrift")
            yes(
                secondEntryHasNoPreviousImage &&
                    field("Bildunterschrift")?.text?.toString()?.isBlank() == true &&
                    !actionEnabled(UiTags.PRIMARY) &&
                    photos().map { it.getString("id") }.toSet() == savedPhotoIds,
                "TIMELINE_PHOTO_SECOND_ENTRY_HAS_NO_OLD_SELECTION_CAPTION_OR_SAVED_ID",
            )
            recreate()
            reveal("Noch nicht gespeichert")
            yes(
                cameraRequests.get() == 2 &&
                    node("Ausgewähltes Foto") == null &&
                    node("Gespeichertes Foto öffnen") == null,
                "TIMELINE_PHOTO_RECREATED_ENTRY_RETAINS_FRESH_HEADER_WITHOUT_RETAKING_PHOTO",
            )
            reveal("Bildunterschrift")
            waitUntil("TIMELINE_PHOTO_FRESH_ENTRY_SURVIVES_RECREATION") {
                cameraRequests.get() == 2 &&
                    field("Bildunterschrift")?.text?.toString()?.isBlank() == true &&
                    !actionEnabled(UiTags.PRIMARY)
            }
            click("Verwerfen")
            selectTab(UiTags.objectTab("photos"))
            yes(
                photos().map { it.getString("id") }.toSet() == savedPhotoIds &&
                    read("get_photo", JSONObject().put("photo_id", addedPhotoId))
                        .getString("record_hash") == savedPhotoHash &&
                    nonPhotoOriginals() == originalBusinessBeforeAdd &&
                    requireNotNull(addedPhotoOriginal).readBytes().contentEquals(selectedBytes),
                "TIMELINE_PHOTO_SECOND_ENTRY_DISCARD_PRESERVES_ONE_SAVED_ORIGINAL",
            )
            click(UiTags.objectEntry("photo:$photoId"))
            shown(UiTags.context("properties", property))
            click("Bildunterschrift")
            enter(UiTags.PHOTO_CAPTION, "[TEST] QA Objektfoto ergänzt")
            if (keyboardVisible()) back()
            click("Schließen")
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
            shown("E-Rechnung")
            click("Später")
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
            reveal(UiTags.objectEntry("visit:$visit"))
            reveal(UiTags.objectEntry("appointment:$appointment"))
            yes(
                history
                    .filter { it.optString("kind") in setOf("visit", "appointment") }
                    .all { it.optString("route").isBlank() },
                "TIMELINE_RETAINS_TERMIN_HISTORY_WITHOUT_OBJECT_PLANNING_LINKS",
            )
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
                    !Rect.intersects(plus, primary) &&
                    (secondary == null || !Rect.intersects(plus, secondary))
            }
            click(UiTags.CREATE)
            yes(node(UiTags.createChoice("care_plan")) == null, "TIMELINE_PLUS_HAS_NO_CARE_ACTION")
            back()
            val paymentEvents =
                SettlementRules.rows(timeline("money").getJSONArray("items")).filter {
                    it.optString("kind") == "payment"
                }
            yes(
                paymentEvents.size == 1 && paymentEvents.single().getLong("amount_minor") == 2000L,
                "TIMELINE_SHOWS_REAL_PAYMENT_ONCE",
            )
            shell(
                if (oldFont == "null") "settings delete system font_scale"
                else "settings put system font_scale $oldFont"
            )
            recreate()
            openProperty()
            reveal(UiTags.objectEntry("appointment:$appointment"))
            captureSyntheticScreen("object-history")
            selectTab(UiTags.objectTab("photos"))
            reveal(UiTags.objectEntry("photo:$addedPhotoId"))
            captureSyntheticScreen("object-photos")
            selectTab(UiTags.objectTab("money"))
            reveal(UiTags.objectEntry("settlement:$settlement"))
            captureSyntheticScreen("object-money")
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
                if (addedPhotoId.isNotBlank())
                    db.delete("photo_attachments", "photo_id=?", arrayOf(addedPhotoId))
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
            selectedPhotoFile?.delete()
            addedPhotoOriginal?.delete()
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
            shown(UiTags.EMPTY_STATE)
            click(UiTags.CREATE)
            click(UiTags.createChoice("work"))
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
        click("action:global-search")
        val label = "Suchen"
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
            searchCustomers(fixturePrefix + "000")
            click(fixturePrefix + "000")
            selectCustomerTab("Vorgänge")
            openDetails("Angebote")
            reveal("Alle Angebote")
            click("Alle Angebote")
            keyboardSearchProof("offers", "Suchen", "QA Kontextangebot 0", "QA Kontextangebot 0")
            visibleResult("Zur Liste")
            captureSyntheticScreen("detail-offers")
            click("Zur Liste")
            waitUntil("DOMAIN_RETURN_QUERY_offers") {
                field("Suchen")?.text?.toString()?.contains("QA Kontextangebot 0") == true
            }
        }
        case("planning") {
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
                "Suchen",
                fixturePrefix + "000",
                fixturePrefix + "000",
                manual = true,
            )
        }
        case("phrases") {
            newWork()
            click("Textbausteine")
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
            click(UiTags.area("planning"))
            click(UiTags.FILTER)
            click(UiTags.PLANNING_ALL_VISITS)
            enter("Suchen", "QA Kontexteinsatz 0")
            click("QA Kontexteinsatz 0")
            reveal("Benötigte Geräte")
            click("Benötigte Geräte")
            click("Benötigte Geräte ergänzen")
            click("Benötigte Geräte: Nicht zugeordnet")
            keyboardSearchProof(
                "equipment-picker",
                "Suchen",
                "QA Zubehör Säge",
                "[TEST] QA Zubehör Säge",
            )
            reveal("Benötigte Geräte: [TEST] QA Zubehör Säge")
            click("Fertig")
        }
        case("planning-picker") {
            click(UiTags.area("planning"))
            click(UiTags.PRIMARY)
            click("Kunde (optional): Keine Auswahl")
            keyboardSearchProof(
                "planning-picker",
                "Suchen",
                fixturePrefix + "000",
                fixturePrefix + "000",
            )
            reveal("Kunde (optional): " + fixturePrefix + "000")
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
            searchCustomers(fixturePrefix + "000")
            click(fixturePrefix + "000")
            selectCustomerTab("Vorgänge")
            openDetails("Angebote")
            reveal("Alle Angebote")
            click("Alle Angebote")
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
                Target(
                    "customers",
                    "Suchen",
                    fixturePrefix + "000",
                    fixturePrefix + "000",
                    manual = true,
                ) {
                    openCustomerList()
                    click("Suchen")
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
                Target("local", "Suchen", fixturePrefix + "000", fixturePrefix + "000", true) {
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
                    val expectedCustomer =
                        if (target.key in setOf("customers", "local"))
                            JSONObject(customers(target.result).single().toString())
                        else null
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
                    // Objects precede customers in the shared search. Reach the same
                    // customer by scrolling the actual result list, retaining query and groups.
                    visibleResult(
                        target.result,
                        requireKeyboard = true,
                        scrollIntoView = true,
                        allowClippedCard = true,
                    )
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
                        scrollIntoView = true,
                        allowClippedCard = true,
                    )
                    waitUntil("LIST_TOUCH_OPENED_$key") {
                        !keyboardVisible() && field(target.label) == null
                    }
                    expectedCustomer?.let { original ->
                        val id = original.getString("id")
                        shown(UiTags.entityDetail("customers", id))
                        val publicCustomer =
                            read("get_customer", JSONObject().put("customer_id", id))
                        yes(
                            publicCustomer.getString("id") == id &&
                                publicCustomer.getString("name") == target.result &&
                                PixelMcpWriteTools.hash(customers(target.result).single()) ==
                                    PixelMcpWriteTools.hash(original),
                            "LIST_TOUCH_OPENS_UNCHANGED_ORIGINAL_CUSTOMER_$key",
                        )
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
                            .apply {
                                expectedCustomer?.let {
                                    put("selected_original_id", it.getString("id"))
                                    put("original_customer_readback", true)
                                }
                            }
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
            waitUntil("FILTER_IME_CLOSED_$key") { !keyboardVisible() }
            settleUi()
            // Restored app bars can move the edited filter below the visible viewport.
            reveal(label)
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
        showKeyboard("Suchen")
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
        val connectionProofs = HashMap<String, JSONObject>()
        fun connectionState(): String {
            val result = JSONObject()
            val local = JSONObject()
            store.readableDatabase
                .rawQuery(
                    "SELECT key,value FROM owner_state WHERE key LIKE 'planning:%' OR key LIKE 'calendar:%' OR key LIKE 'weather:%' ORDER BY key",
                    null,
                )
                .use { rows ->
                    while (rows.moveToNext()) local.put(rows.getString(0), rows.getString(1))
                }
            result.put("local_planning_calendar_weather", local)
            for (name in
                listOf("planning_private", "pixel_tunnel_private", "pixel_tunnel_status")) {
                val values = JSONObject()
                for ((key, value) in context.getSharedPreferences(name, 0).all.toSortedMap()) values
                    .put(
                        key,
                        if (value is Set<*>) JSONArray(value.map { it.toString() }.sorted())
                        else value,
                    )
                result.put(name, values)
            }
            val scheduler = context.getSystemService(android.app.job.JobScheduler::class.java)
            result.put(
                "planning_jobs",
                JSONArray().apply {
                    for (job in
                        scheduler
                            ?.allPendingJobs
                            .orEmpty()
                            .filter {
                                it.service.className == PlanningJob::class.java.name
                            }
                            .sortedBy { it.id }) put(
                        JSONObject()
                            .put("id", job.id)
                            .put("minimum_latency", job.minLatencyMillis)
                            .put("periodic", job.isPeriodic)
                            .put("interval", job.intervalMillis)
                    )
                },
            )
            return PixelMcpWriteTools.hash(result)
        }
        fun connectionReadOnlyProof(key: String, scale: Int) {
            waitUntil("CONNECTIONS_ACTUAL_FONT_SCALE_$scale") {
                kotlin.math.abs(
                    (activity?.resources?.configuration?.fontScale ?: 0f) - scale / 100f
                ) < 0.01f
            }
            waitUntil("CONNECTIONS_LOCAL_STATUS_READY_$scale") {
                node("Lokalen Status gelesen") != null &&
                    node("Status prüfen")?.let(::clickable)?.isEnabled == true
            }
            val businessBefore = PixelMcpWriteTools.hash(store.getState())
            val connectionBefore = connectionState()
            click("Status prüfen")
            waitUntil("CONNECTIONS_EXPLICIT_LOCAL_STATUS_READY_$scale") {
                node("Lokalen Status gelesen") != null &&
                    node("Status prüfen")?.let(::clickable)?.isEnabled == true
            }
            yes(
                PixelMcpWriteTools.hash(store.getState()) == businessBefore &&
                    connectionState() == connectionBefore,
                "CONNECTIONS_STATUS_READ_ONLY_WITHOUT_SYNC_OR_REFRESH_$scale",
            )
            click("UI und MCP erklären")
            shown("UI und MCP: Funktionsprüfung")
            shown("Dieses Blatt erklärt die vorhandenen Wege. Es führt keine Prüfung aus.")
            visibleResult("Erklärung schließen")
            captureSyntheticScreen("connection-explanation-$scale")
            reveal("Prüfung der Abläufe")
            val section = requireNotNull(node("Prüfung der Abläufe"))
            yes(
                generateSequence(section.parent) { it.parent }.any { it.isScrollable },
                "CONNECTIONS_EXPLANATION_HAS_OWN_SCROLL_$scale",
            )
            captureSyntheticScreen("connection-explanation-end-$scale")
            visibleResult("Erklärung schließen")
            click("Erklärung schließen")
            waitUntil("CONNECTIONS_EXPLANATION_CLOSED_$scale") {
                node("UI und MCP: Funktionsprüfung") == null &&
                    node("Optionale Verbindungen") != null
            }
            yes(
                PixelMcpWriteTools.hash(store.getState()) == businessBefore &&
                    connectionState() == connectionBefore,
                "CONNECTIONS_EXPLANATION_OPEN_CLOSE_PRESERVES_ALL_DATA_$scale",
            )
            connectionProofs[key] =
                JSONObject()
                    .put("explicit_status_click", true)
                    .put("business_state_unchanged", true)
                    .put("connection_configuration_unchanged", true)
                    .put("no_sync_or_refresh_state_or_job_change", true)
                    .put("explanation_opened_and_closed", true)
                    .put("explanation_own_scroll", true)
                    .put("font_scale_percent", scale)
        }
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
                    JSONObject()
                        .put("surface", key)
                        .put("ready", ready)
                        .put("no_auto_ime", true)
                        .apply { connectionProofs[key]?.let { put("connection_read_only", it) } }
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
            click("Einstellungen")
            waitUntil("SETTINGS_ACTION_READY") {
                node("Tätigkeit, Einsatzort und Fotoqualität")?.let(::clickable) != null
            }
        }
        fun planning() {
            click(UiTags.area("planning"))
        }
        fun billing() {
            openOfficeEntry("Offene Stunden")
        }
        case("today", "Arbeit starten", true) {}
        case("work", "Arbeit nachtragen") { openWorklogs() }
        case("lcr", "L.C.R. suchen") {
            openOffice()
            click("L.C.R.")
        }
        case("timer", "Kunde *: Nicht zugeordnet") { click("Arbeit starten") }
        case("customers", "Neu anlegen") { click(UiTags.area("customers")) }
        case("menu", "Vorgänge", true) {
            click(UiTags.area("today"))
            click(UiTags.MORE)
        }
        case("properties", "Objekt anlegen") { openOfficeEntry("Objekte") }
        case("inquiries", "Anfrage anlegen") { openOfficeEntry("Anfragen") }
        case("orders", "Auftrag anlegen") { openOfficeEntry("Aufträge") }
        case("planning", UiTags.PRIMARY, true) { planning() }
        case("visits", "Termin anlegen") {
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
        case("billing", "Offen abzurechnen", true) { billing() }
        case("offers", "Angebot anlegen") {
            openOffice()
            click("Angebote")
        }
        case("equipment", "Gerät anlegen") {
            openOffice()
            click("Geräte")
        }
        case("accessories", "Zubehör hinzufügen", true) {
            openOffice()
            click("Geräte")
            enter("Suchen", "QA Zubehör Säge")
            click("[TEST] QA Zubehör Säge")
            selectTab("Zubehör")
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
        case("settings", "Tätigkeit, Einsatzort und Fotoqualität", true) { settings() }
        case("connections", "Optionale Verbindungen", true) {
            settings()
            click("Verbindungen")
            connectionReadOnlyProof("connections", 100)
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
            case("connections-200", "Optionale Verbindungen", true) {
                settings()
                click("Verbindungen")
                connectionReadOnlyProof("connections-200", 200)
            }
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
        yes(surfaceCases.length() == 31, "ALL_SURFACES_REACHABLE")
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
            // Save through the actual field editors, then read the same values through MCP.
            val businessBefore = read("get_business_settings").getJSONObject("settings")
            val defaultsBefore = read("get_settings").getJSONObject("settings")
            val paymentTerms =
                "[TEST] Zahlung binnen 21 Tagen ab Erhalt; dieser ausschließlich synthetische lange Hinweis prüft die vollständige Darstellung der vereinbarten Zahlungsbedingungen bei großer Schrift."
            val defaultActivity = "[TEST] QA dauerhaft gespeicherte Arbeitsvorgabe"
            fun except(value: JSONObject, key: String) =
                JSONObject(value.toString()).apply { remove(key) }
            openOffice()
            click("Einstellungen")
            openDetails("Rechnungsdaten")
            click("Zahlungsziel")
            enter("Zahlungsziel", paymentTerms)
            if (keyboardVisible()) back()
            click("Fertig")
            waitUntil("SETTINGS_UI_INVOICE_VALUE_SAVED") {
                read("get_business_settings")
                    .getJSONObject("settings")
                    .optString("default_payment_terms") == paymentTerms
            }
            val businessSaved = read("get_business_settings").getJSONObject("settings")
            yes(
                PixelMcpWriteTools.hash(except(businessSaved, "default_payment_terms")) ==
                    PixelMcpWriteTools.hash(except(businessBefore, "default_payment_terms")) &&
                    businessSaved.optString("tax_mode") == businessBefore.optString("tax_mode"),
                "SETTINGS_UI_INVOICE_VALUE_PRESERVES_TAX_AND_OTHER_SETTINGS",
            )
            recreate()
            openDetails("Rechnungsdaten")
            revealContaining(paymentTerms)
            captureSyntheticScreen("settings-invoice-data-saved")
            reveal("Tätigkeit, Einsatzort und Fotoqualität")
            click("Tätigkeit, Einsatzort und Fotoqualität")
            val activityLabel = nativeLabel("default_activity")
            waitUntil("SETTINGS_UI_WORK_DEFAULT_EDITOR_READY") { field(activityLabel) != null }
            enter(activityLabel, defaultActivity)
            if (keyboardVisible()) back()
            click(UiTags.PRIMARY)
            waitUntil("SETTINGS_UI_WORK_DEFAULT_SAVED") {
                read("get_settings").getJSONObject("settings").optString("default_activity") ==
                    defaultActivity
            }
            waitUntil("SETTINGS_UI_WORK_DEFAULT_EDITOR_CLOSED") {
                field(activityLabel) == null &&
                    node("Tätigkeit, Einsatzort und Fotoqualität") != null
            }
            val defaultsSaved = read("get_settings").getJSONObject("settings")
            yes(
                PixelMcpWriteTools.hash(except(defaultsSaved, "default_activity")) ==
                    PixelMcpWriteTools.hash(except(defaultsBefore, "default_activity")),
                "SETTINGS_UI_WORK_DEFAULT_PRESERVES_OTHER_DEFAULTS",
            )
            recreate()
            PixelLocalStore(context).use { fresh ->
                val publicTools =
                    PixelMcpReadTools(fresh.readSource(), fresh, PixelOwnerAccess(context, fresh))
                fun reopened(name: String): JSONObject {
                    val response = publicTools.call(name, JSONObject())
                    yes(response.optBoolean("ok"), "SETTINGS_UI_REOPENED_PUBLIC_$name")
                    return response.getJSONObject("data").getJSONObject("settings")
                }
                val durableBusiness = reopened("get_business_settings")
                val durableDefaults = reopened("get_settings")
                yes(
                    durableBusiness.getString("default_payment_terms") == paymentTerms &&
                        durableDefaults.getString("default_activity") == defaultActivity &&
                        durableBusiness.optString("tax_mode") ==
                            businessBefore.optString("tax_mode") &&
                        PixelMcpWriteTools.hash(except(durableBusiness, "default_payment_terms")) ==
                            PixelMcpWriteTools.hash(
                                except(businessBefore, "default_payment_terms")
                            ),
                    "SETTINGS_UI_BOTH_VALUES_DURABLE_AFTER_RECREATE_AND_NEW_STORE_WITH_UNCHANGED_TAX",
                )
            }
            click("Tätigkeit, Einsatzort und Fotoqualität")
            waitUntil("SETTINGS_UI_WORK_DEFAULT_VISIBLE_AFTER_RECREATE") {
                field(activityLabel)?.text?.toString() == defaultActivity
            }
            captureSyntheticScreen("settings-work-default-saved")
            click("Zurück")
            for (scale in listOf(1.0, 2.0)) {
                activity?.let { current -> test.runOnMainSync { current.finish() } }
                shell("settings put system font_scale $scale")
                launch()
                openOffice()
                click("Einstellungen")
                if (scale == 2.0) {
                    openDetails("Rechnungsdaten")
                    revealContaining(paymentTerms)
                    captureSyntheticScreen("settings-invoice-long-payment-200")
                }
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

    private fun planningTimezoneInputUi() {
        activity?.let { current ->
            test.runOnMainSync { if (!current.isFinishing) current.finish() }
        }
        shell("settings put system font_scale 1.0")
        launch(planning = true)
        fun originals(): List<JSONObject> =
            SettlementRules.rows(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments")
            )
        val beforeIds = originals().map { it.getString("id") }.toSet()
        val workBefore =
            PixelMcpWriteTools.hash(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            )
        fun added() = originals().filter { it.getString("id") !in beforeIds }
        click(UiTags.PRIMARY)
        shown("Termin planen")
        chooseDate("Datum", "2026-03-29")
        // Choosing a real date may already commit one valid date-only original.
        waitUntil("DST_UI_DATE_ONLY_ORIGINAL_AUTOSAVED") {
            added().singleOrNull()?.let {
                it.optString("date") == "2026-03-29" &&
                    it.optString("starts_at").isBlank() &&
                    it.optString("ends_at").isBlank()
            } == true
        }
        val id = added().single().getString("id")
        fun readOriginal() = read("get_appointment", JSONObject().put("appointment_id", id))
        reveal("Gespeichert")
        waitUntil("DST_UI_DATE_ONLY_SAVE_ACKNOWLEDGED") {
            node("Gespeichert") != null && actionEnabled(UiTags.PRIMARY)
        }
        val dateOnly = readOriginal()
        val validHash = dateOnly.getString("record_hash")
        yes(
            dateOnly.getJSONObject("appointment").getString("timezone") == "Europe/Vienna" &&
                added().size == 1,
            "DST_UI_ONE_VALID_VIENNA_DATE_ONLY_ORIGINAL",
        )
        chooseTime("Uhrzeit (optional)", "02:30")
        revealContaining("Diese Uhrzeit existiert wegen der Zeitumstellung nicht.")
        waitStable("DST_UI_SPRING_GAP_DOES_NOT_PUBLISH_INVALID_START", stableMs = 1600) {
            val current = readOriginal()
            current.getString("record_hash") == validHash &&
                current.getJSONObject("appointment").optString("starts_at").isBlank() &&
                current.getJSONObject("appointment").optString("ends_at").isBlank() &&
                current.getJSONObject("appointment").getString("date") == "2026-03-29" &&
                added().singleOrNull()?.getString("id") == id
        }
        captureSyntheticScreen("planning-dst-spring-gap")
        chooseDate("Datum", "2026-10-25")
        revealContaining("Diese Uhrzeit gibt es zweimal. Bitte den Zeitzonenoffset auswählen.")
        waitStable("DST_UI_AUTUMN_OVERLAP_REQUIRES_EXPLICIT_OFFSET", stableMs = 1600) {
            // The unresolved autumn input stays in the editor; the previous valid original is
            // intact.
            readOriginal().getString("record_hash") == validHash &&
                added().singleOrNull()?.getString("id") == id
        }
        captureSyntheticScreen("planning-dst-autumn-offset-required")
        click("Kalender und Wetter")
        val offsetLabel = "Bei doppelter Uhrzeit: UTC-Offset"
        enter(offsetLabel, "+01:00")
        if (keyboardVisible()) back()
        waitUntil("DST_UI_AUTUMN_EXPLICIT_STANDARD_OFFSET_AUTOSAVED") {
            readOriginal().getJSONObject("appointment").let {
                it.getString("id") == id &&
                    it.getString("date") == "2026-10-25" &&
                    it.getString("timezone") == "Europe/Vienna" &&
                    it.getString("starts_at") == "2026-10-25T01:30:00Z" &&
                    it.optString("ends_at").isBlank()
            }
        }
        reveal("Gespeichert")
        waitUntil("DST_UI_STANDARD_OFFSET_SAVE_ACKNOWLEDGED") {
            node("Gespeichert") != null && actionEnabled(UiTags.PRIMARY)
        }
        val winterHash = readOriginal().getString("record_hash")
        captureSyntheticScreen("planning-dst-autumn-offset-0100")
        recreate()
        reveal("Datum")
        revealContaining("02:30")
        yes(
            readOriginal().getString("record_hash") == winterHash &&
                added().singleOrNull()?.getString("id") == id,
            "DST_UI_RECREATE_KEEPS_SAME_UNAMBIGUOUS_ORIGINAL",
        )
        reveal(offsetLabel)
        enter(offsetLabel, "+02:00")
        if (keyboardVisible()) back()
        waitUntil("DST_UI_AUTUMN_EXPLICIT_SUMMER_OFFSET_CHANGES_INSTANT_SAME_ID") {
            readOriginal().getJSONObject("appointment").let {
                it.getString("id") == id &&
                    it.getString("date") == "2026-10-25" &&
                    it.getString("starts_at") == "2026-10-25T00:30:00Z" &&
                    it.optString("ends_at").isBlank()
            } && added().size == 1
        }
        reveal("Gespeichert")
        waitUntil("DST_UI_SUMMER_OFFSET_SAVE_ACKNOWLEDGED") {
            node("Gespeichert") != null && actionEnabled(UiTags.PRIMARY)
        }
        captureSyntheticScreen("planning-dst-autumn-offset-0200")
        click(UiTags.PRIMARY)
        waitUntil("DST_UI_SAVE_RETURNS_TO_BOARD_WITHOUT_PENDING_EDITOR") {
            node("Termin speichern") == null && node(UiTags.PLANNING_TOUR) != null
        }
        yes(
            store
                .ownerValue(
                    "native_form_draft:" + PixelMcpWriteTools.hash("planning:appointment:new"),
                    "",
                )
                .isBlank(),
            "DST_UI_SAVE_LEAVES_NO_PENDING_EDITOR_DRAFT",
        )
        PixelLocalStore(context).use { fresh ->
            val publicTools =
                PixelMcpReadTools(fresh.readSource(), fresh, PixelOwnerAccess(context, fresh))
            val response =
                publicTools.call("get_appointment", JSONObject().put("appointment_id", id))
            yes(response.optBoolean("ok"), "DST_UI_REOPENED_PUBLIC_ORIGINAL_READ")
            val original = response.getJSONObject("data").getJSONObject("appointment")
            yes(
                original.getString("id") == id &&
                    original.getString("date") == "2026-10-25" &&
                    original.getString("timezone") == "Europe/Vienna" &&
                    original.getString("starts_at") == "2026-10-25T00:30:00Z" &&
                    original.optString("ends_at").isBlank() &&
                    added().singleOrNull()?.getString("id") == id,
                "DST_UI_BOTH_EXPLICIT_OFFSETS_PRESERVE_DATE_OPEN_END_AND_SINGLE_DURABLE_ID",
            )
        }
        yes(
            PixelMcpWriteTools.hash(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("worklogs")
            ) == workBefore,
            "DST_UI_PICKERS_AND_OFFSET_RESOLUTION_CREATE_NO_ACTUAL_WORK",
        )
    }

    private fun largeTextJourney() {
        planningTimezoneInputUi()
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
        fun appointments(): List<JSONObject> =
            SettlementRules.rows(
                PixelMcpReadTools.effective(store.getState()).getJSONArray("appointments")
            )
        val beforeIds = appointments().map { it.getString("id") }.toSet()
        val workAndDeliveryBeforeAutosave = planningWorkAndDeliveryState()
        fun created(): JSONObject? =
            appointments().filter { it.getString("id") !in beforeIds }.singleOrNull()
        fun localStart(record: JSONObject): String = runCatching {
            AppointmentRules.instant(record.getString("starts_at"))
                .atZone(ZoneId.of("Europe/Vienna"))
                .toLocalDateTime()
                .toString()
        }
            .getOrDefault("")
        click(UiTags.PRIMARY)
        shown("Termin planen")
        chooseDate("Datum", "2027-05-19")
        chooseTime("Uhrzeit (optional)", "09:00")
        waitUntil("PLANNING_REAL_INPUT_AUTOSAVES_ONE_ORIGINAL") {
            created()?.let { localStart(it) == "2027-05-19T09:00" } == true
        }
        val savedId = requireNotNull(created()).getString("id")
        val storedHash =
            read("get_appointment", JSONObject().put("appointment_id", savedId))
                .getString("record_hash")
        chooseDate("Datum", "2027-05-20", confirm = false)
        chooseTime("Uhrzeit (optional)", "11:30", confirm = false)
        yes(
            read("get_appointment", JSONObject().put("appointment_id", savedId))
                .getString("record_hash") == storedHash,
            "CANCEL_OPTIONAL_DATE_AND_TIME_PRESERVES_ORIGINAL",
        )
        recreate()
        reveal("Datum")
        revealContaining("19. Mai 2027")
        revealContaining("09:00")
        yes(created()?.getString("id") == savedId, "PLANNING_AUTOSAVED_ID_AFTER_RECREATION")
        chooseTime("Uhrzeit (optional)", "09:15")
        waitUntil("PLANNING_RECREATED_EDIT_UPDATES_SAME_ORIGINAL") {
            created()?.let {
                it.getString("id") == savedId && localStart(it) == "2027-05-19T09:15"
            } == true
        }
        yes(appointments().size == beforeIds.size + 1, "PLANNING_RECREATED_EDIT_HAS_NO_DUPLICATE")
        // Data commit can precede draft flush/readback. Leave only after the real editor has
        // finished the save, so Back closes this editor and the next action belongs to the board.
        reveal("Gespeichert")
        waitUntil("PLANNING_RECREATED_SAVE_FINISHED_IN_UI") {
            actionEnabled(UiTags.PRIMARY) && node("Termin speichern") != null
        }
        yes(
            planningWorkAndDeliveryState() == workAndDeliveryBeforeAutosave,
            "PLANNING_AUTOSAVE_AND_RECREATED_EDIT_PRESERVE_WORK_DOCUMENTS_PAYMENTS_AND_DELIVERY",
        )
        back()
        reveal(UiTags.PLANNING_TOUR)
        shown(UiTags.planningView("day"))
        click(UiTags.PRIMARY)
        shown("Termin planen")
        click("Ungespeicherten Entwurf verwerfen")
        val planningDraft =
            "native_form_draft:" + PixelMcpWriteTools.hash("planning:appointment:new")
        waitStable("PLANNING_DISCARD_REMOVES_ONLY_UNTOUCHED_DRAFT", stableMs = 500) {
            store.ownerValue(planningDraft, "").isEmpty()
        }
        reveal(UiTags.PLANNING_TOUR)
        shown(UiTags.PRIMARY)
        yes(
            appointments().size == beforeIds.size + 1 && created()?.getString("id") == savedId,
            "PLANNING_DISCARD_PRESERVES_AUTOSAVED_ORIGINAL_WITHOUT_NEW_RECORD",
        )
        click(UiTags.area("today"))
        reveal("Termine heute")
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

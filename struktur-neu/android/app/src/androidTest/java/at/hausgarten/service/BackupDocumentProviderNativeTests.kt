package at.hausgarten.service

import android.app.Activity
import android.app.Instrumentation
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.util.Base64
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Random

/** Real resolver, separate-UID local document provider and system DocumentsUI tests. */
class BackupDocumentProviderNativeTests private constructor(private val test: Instrumentation) {
    companion object {
        /** Run provider first, force-stop the target, then run provider_reopen in a new process. */
        @JvmStatic
        fun run(test: Instrumentation, reopen: Boolean): JSONObject = BackupDocumentProviderNativeTests(test).execute(reopen)

        // AndroidX ProfileInstaller 1.4.1 writes these two package-bound cache
        // records after startup. They contain no application or fixture data.
        private fun verifiedProfileMetadata(root: File, file: File, updatedAt: Long): Boolean = try {
            if (java.nio.file.Files.isSymbolicLink(file.toPath()) || !file.isFile || file.canonicalFile.parentFile != root.canonicalFile) false
            else when (file.name) {
                "profileinstaller_profileWrittenFor_lastUpdateTime.dat" -> file.length() == 8L &&
                    java.io.DataInputStream(file.inputStream()).use { it.readLong() == updatedAt && it.read() == -1 }
                "profileInstalled" -> file.length() == 24L && java.io.DataInputStream(file.inputStream()).use { input ->
                    val schema = input.readInt()
                    val result = input.readInt()
                    val packageUpdatedAt = input.readLong()
                    val profileSize = input.readLong()
                    schema == 1 && result in setOf(0, 1, 2, 3, 65536, 131072, 196608, 262144, 327680) &&
                        packageUpdatedAt == updatedAt && profileSize >= 0 && input.read() == -1
                }
                else -> false
            }
        } catch (_: IOException) { false }
    }

    private val context: Context = test.targetContext
    private val automation = test.uiAutomation
    private val marker: File get() = File(context.noBackupFilesDir, "qa-provider-fixture.json")
    private var checks = 0

    private fun yes(value: Boolean, code: String) {
        if (!value) throw AssertionError("QA_PROVIDER_$code")
        checks++
    }

    private fun rejects(code: String, action: () -> Unit) {
        try { action() } catch (_: Exception) { checks++; return }
        throw AssertionError("QA_PROVIDER_EXPECTED_REJECTION_$code")
    }

    private fun shell(command: String): String = automation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes().toString(Charsets.UTF_8).trim() }
    }

    private fun control(method: String, id: String? = null, args: Bundle? = null): JSONObject {
        val request = JSONObject().put("method", method)
        if (id != null) request.put("id", id)
        args?.keySet()?.forEach { request.put(it, args.get(it)) }
        val encoded = Base64.encodeToString(request.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        // The actual shell owns DUMP and ACCESS_CONTENT_PROVIDERS_EXTERNALLY. AMS accepts an
        // accessible path permission for acquisition; document transport still enforces SAF grants.
        val output = shell("content call --uri ${SyntheticBackupDocumentsProvider.CONTROL_URI} " +
            "--method ${SyntheticBackupDocumentsProvider.CONTROL}shell --arg $encoded")
        val payload = Regex("qa_result=([A-Za-z0-9_=-]+)").find(output)?.groupValues?.get(1)
            ?: throw IOException("QA_SHELL_CONTROL_FAILED: $output")
        val response = JSONObject(String(Base64.decode(payload, Base64.URL_SAFE), Charsets.UTF_8))
        if (response.getInt("caller_uid") != Process.SHELL_UID) throw SecurityException("QA_ACTUAL_SHELL_RESPONSE_REQUIRED")
        return response.getJSONObject("result")
    }

    private fun stats(): JSONObject = JSONObject(requireNotNull(control("stats").getString("state")))
    private fun documentStats(id: String): JSONObject = stats().getJSONObject("documents").getJSONObject(id)
    private fun create(grant: Boolean = true): String = requireNotNull(control("create").getString("id")).also {
        if (grant) control("grant", it)
    }
    private fun fault(id: String, value: String) { control("fault", id, Bundle().apply { putString("fault", value) }) }
    private fun uri(id: String): Uri = SyntheticBackupDocumentsProvider.document(id)

    private fun execute(reopen: Boolean): JSONObject {
        // Guard before inspecting any application fixture contents. Never run on a physical Pixel.
        yes(test.context.packageName == "at.hausgarten.service.test", "TEST_APK_REQUIRED")
        yes(context.packageName == "at.hausgarten.service", "ORIGINAL_TARGET_PACKAGE_REQUIRED")
        yes(Build.HARDWARE in listOf("ranchu", "goldfish") && shell("getprop ro.kernel.qemu") == "1", "DISPOSABLE_EMULATOR_REQUIRED")
        yes(Build.VERSION.SDK_INT == 35, "API_35_REQUIRED")
        yes(!context.isDeviceProtectedStorage, "SEPARATE_CREDENTIAL_FIXTURE")
        if (reopen) return reopen()
        val files = context.filesDir
        val noBackup = context.noBackupFilesDir
        val entries = files.listFiles()
        val noBackupEntries = noBackup.listFiles()
        val updatedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        val onlyProfileMetadata = !java.nio.file.Files.isSymbolicLink(files.toPath()) &&
            entries != null && entries.all { verifiedProfileMetadata(files, it, updatedAt) }
        val noBackupEmpty = !java.nio.file.Files.isSymbolicLink(noBackup.toPath()) && noBackupEntries?.isEmpty() == true
        val nonemptyPreferences = BackupPreferences.GROUPS.filter { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.isNotEmpty() }
        val databaseExists = context.getDatabasePath("haus-garten-mobile.db").exists()
        if (marker.exists() || databaseExists || !onlyProfileMetadata || !noBackupEmpty || nonemptyPreferences.isNotEmpty()) {
            val details = JSONObject().put("marker_exists", marker.exists()).put("database_exists", databaseExists)
                .put("files", org.json.JSONArray(entries?.map { it.name } ?: emptyList<String>()))
                .put("files_readable", entries != null).put("only_verified_profile_metadata", onlyProfileMetadata)
                .put("no_backup", org.json.JSONArray(noBackupEntries?.map { it.name } ?: emptyList<String>()))
                .put("no_backup_empty", noBackupEmpty).put("nonempty_preferences", org.json.JSONArray(nonemptyPreferences))
            throw IOException("FRESH_SYNTHETIC_PROVIDER_APP_SANDBOX_REQUIRED: $details")
        }
        control("prepare") // Refuses a nonempty provider fixture; never clears existing data.
        val remote = control("stats")
        yes(remote.getInt("pid") != android.os.Process.myPid() && remote.getInt("uid") != android.os.Process.myUid(), "REAL_REMOTE_PROVIDER_UID_AND_PROCESS")
        marker.writeText(JSONObject().put("fixture", "synthetic-provider-app-v1").put("pid", android.os.Process.myPid()).toString())
        val snapshot = SyntheticBusinessData.profile("minimal").toString()
        yes(context.getSharedPreferences("haus_garten", Context.MODE_PRIVATE).edit().putString("drive_snapshot", snapshot).commit(), "SYNTHETIC_SNAPSHOT")
        File(context.filesDir, "qa-provider-payload.bin").writeBytes(ByteArray(196608).also { Random(87).nextBytes(it) })

        val success = create()
        var targetControlRejected = false
        try {
            context.contentResolver.call(SyntheticBackupDocumentsProvider.CONTROL_URI,
                SyntheticBackupDocumentsProvider.CONTROL + "shell", null, null)
        } catch (failure: SecurityException) {
            targetControlRejected = failure.message?.contains("QA_CONTROL_REQUIRES_ACTUAL_SHELL_UID") == true
        }
        yes(targetControlRejected, "TARGET_DOCUMENT_GRANT_CANNOT_CONTROL_FIXTURE")
        val request = "qa_provider_save_001"
        PixelLocalStore(context).use { store ->
            store.putOwnerValue("qa_provider_marker", "QA synthetic provider fixture")
            val service = PixelBackupService(context, store)
            val result = service.save(request, uri(success)) {}
            yes(result.getBoolean("verified") && !result.getBoolean("cloud_confirmed"), "REAL_PROVIDER_SAVE_READBACK")
            yes(documentStats(success).getInt("writes") == 1 && documentStats(success).getInt("reads") == 1, "REMOTE_PROVIDER_OPENS")
            val good = store.ownerValue("pixel_backup_success", "")
            val denied = create(false)
            rejects("MISSING_WRITE_GRANT") { service.save("qa_provider_denied_001", uri(denied)) {} }
            yes(documentStats(denied).getInt("writes") == 0, "DENIED_BEFORE_PROVIDER_WRITE")
            control("grant", denied, Bundle().apply { putBoolean("read_only", true) })
            rejects("READ_GRANT_CANNOT_WRITE") { service.save("qa_provider_readonly_001", uri(denied)) {} }
            yes(documentStats(denied).getInt("writes") == 0, "READ_ONLY_GRANT_ENFORCED")
            for (mode in listOf("WRITE_OPEN", "WRITE_STREAM", "READ_OPEN", "READ_TRUNCATED")) {
                val id = create()
                fault(id, mode)
                rejects(mode) { service.save("qa_provider_failure_$mode", uri(id)) {} }
                yes(service.status(JSONObject()).getString("state") == "FAILED", "FAILURE_STATUS_$mode")
                yes(store.ownerValue("pixel_backup_success", "") == good, "PRIOR_VERIFIED_PROOF_$mode")
                yes(documentStats(id).getInt("writes") == 1, "WRITE_ATTEMPT_$mode")
                yes(documentStats(id).getInt("reads") == if (mode.startsWith("READ_")) 1 else 0, "READ_ATTEMPT_$mode")
                yes(!File(context.noBackupFilesDir, "pixel-backup").exists(), "WORK_CLEANED_$mode")
            }
            service.save(request, uri(success)) {}
            yes(documentStats(success).getInt("writes") == 1, "RETRY_READS_WITHOUT_REWRITE")
        }
        val restore = BackupRestoreService(context)
        val deniedSource = create(false)
        rejects("MISSING_READ_GRANT") { restore.preview("qa_provider_preview_denied", uri(deniedSource)) }
        yes(documentStats(deniedSource).getInt("reads") == 0, "DENIED_BEFORE_PROVIDER_READ")
        val revokedSource = create(false)
        control("grant", revokedSource, Bundle().apply { putBoolean("read_only", true) })
        context.contentResolver.openInputStream(uri(revokedSource)).use { input ->
            yes(input != null && input.read() == -1, "READ_GRANT_WORKS_BEFORE_REVOCATION")
        }
        val readsBeforeRevocation = documentStats(revokedSource).getInt("reads")
        yes(readsBeforeRevocation == 1, "GRANTED_PROVIDER_READ_OCCURRED")
        control("revoke", revokedSource)
        var revokedRejected = false
        try {
            restore.preview("qa_provider_preview_revoked", uri(revokedSource))
        } catch (_: SecurityException) {
            revokedRejected = true
        }
        yes(revokedRejected, "REVOKED_READ_GRANT_REJECTED")
        yes(documentStats(revokedSource).getInt("reads") == readsBeforeRevocation, "REVOKED_BEFORE_PROVIDER_OPEN")
        fault(success, "READ_OPEN")
        rejects("PREVIEW_READ_OPEN") { restore.preview("qa_provider_preview_read_error", uri(success)) }
        fault(success, "READ_TRUNCATED")
        rejects("PREVIEW_SHORT_READ") { restore.preview("qa_provider_preview_short", uri(success)) }
        fault(success, "NONE")
        yes(restore.preview("qa_provider_preview_ok", uri(success)).getString("phase") == "VALIDATED", "PROVIDER_RESTORE_PREVIEW")

        // These actions navigate the actual Android picker. No result Intent is fabricated.
        val picked = pickerChecks()
        val document = documentStats(picked)
        val saved = PixelLocalStore(context).use { JSONObject(it.ownerValue("pixel_backup_success", "")) }
        yes(saved.getJSONObject("destination").getString("treeUri") == uri(picked).toString(), "PICKER_RESULT_REACHED_PRODUCTION_SAVE")
        yes(context.contentResolver.persistedUriPermissions.any { it.uri == uri(picked) && it.isReadPermission && it.isWritePermission }, "REAL_PICKER_PERSISTED_GRANT")
        marker.writeText(JSONObject().put("fixture", "synthetic-provider-app-v1").put("pid", android.os.Process.myPid())
            .put("provider_pid", control("stats").getInt("pid")).put("document_id", picked).put("writes", document.getInt("writes"))
            .put("request_id", saved.getString("requestId")).put("archive_sha256", saved.getJSONObject("archive").getString("sha256"))
            .put("snapshot_sha256", PixelMcpWriteTools.hash(snapshot)).toString())
        return report(false)
    }

    private fun reopen(): JSONObject {
        yes(marker.isFile, "REOPEN_MARKER")
        val saved = JSONObject(marker.readText())
        yes(saved.getString("fixture") == "synthetic-provider-app-v1", "REOPEN_SYNTHETIC_IDENTITY")
        yes(saved.getInt("pid") != android.os.Process.myPid(), "NEW_TARGET_PROCESS")
        yes(saved.getInt("provider_pid") != control("stats").getInt("pid"), "NEW_PROVIDER_PROCESS")
        yes(PixelMcpWriteTools.hash(context.getSharedPreferences("haus_garten", Context.MODE_PRIVATE).getString("drive_snapshot", "")) == saved.getString("snapshot_sha256"), "REOPEN_SYNTHETIC_SNAPSHOT")
        val id = saved.getString("document_id")
        yes(context.contentResolver.persistedUriPermissions.any { it.uri == uri(id) && it.isReadPermission && it.isWritePermission }, "PERSISTED_PICKER_PERMISSION_AFTER_RESTART")
        PixelLocalStore(context).use { store ->
            yes(store.ownerValue("qa_provider_marker", "") == "QA synthetic provider fixture", "REOPEN_DATABASE_MARKER")
            val before = documentStats(id).getInt("reads")
            yes(PixelBackupService(context, store).save(saved.getString("request_id"), uri(id)) {}.getBoolean("verified"), "REOPEN_REAL_PROVIDER_READBACK")
            yes(documentStats(id).getInt("writes") == saved.getInt("writes"), "REOPEN_NO_REWRITE")
            yes(documentStats(id).getInt("reads") == before + 1, "REOPEN_PROVIDER_READ_OCCURRED")
            yes(JSONObject(store.ownerValue("pixel_backup_success", "")).getJSONObject("archive").getString("sha256") == saved.getString("archive_sha256"), "REOPEN_ARCHIVE_PROOF")
        }
        yes(BackupRestoreService(context).preview("qa_provider_preview_reopened", uri(id)).getString("phase") == "VALIDATED", "REOPEN_PROVIDER_PREVIEW")
        return report(true)
    }

    private fun report(reopened: Boolean): JSONObject = JSONObject().put("checks", checks).put("synthetic_only", true)
        .put("productive_data_read", false).put("real_document_provider_tested", true)
        .put("separate_uid_local_provider_tested", true)
        .put("android_file_picker_tested", !reopened).put("picker_cancellation_tested", !reopened)
        .put("picker_activity_recreation_tested", !reopened).put("new_process_readback", reopened)
        .put("provider_process_restart_tested", reopened).put("drive_account_tested", false)

    private fun waitUntil(code: String, timeout: Long = 20000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("QA_PICKER_TIMEOUT_$code VISIBLE=" + screenTexts())
    }

    /** Synthetic screen labels only, so a timeout names what was actually shown. */
    private fun screenTexts(): String {
        val out = ArrayList<String>()
        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null || out.size >= 40) return
            val label = (node.text ?: node.contentDescription)?.toString()?.take(60)
            if (!label.isNullOrBlank()) out.add(label + (if (node.isEnabled) "" else "[disabled]") + (if (node.isVisibleToUser) "" else "[hidden]"))
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(automation.rootInActiveWindow)
        return out.joinToString(" | ")
    }

    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), predicate)?.let { return it }
        return null
    }

    private fun node(label: String): AccessibilityNodeInfo? = find(automation.rootInActiveWindow) {
        it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label)
    }

    private fun actionableAncestor(node: AccessibilityNodeInfo, boundary: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null && current != boundary) {
            if (current.isVisibleToUser && current.isEnabled &&
                (current.isClickable || current.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })) return current
            current = current.parent
        }
        return null
    }

    private fun click(label: String, scope: () -> AccessibilityNodeInfo? = { automation.rootInActiveWindow }) {
        waitUntil("CLICK_$label") {
            val root = scope() ?: return@waitUntil false
            val matches: (AccessibilityNodeInfo) -> Boolean = {
                it.text?.toString()?.trim() == label || it.contentDescription?.toString()?.trim() == label
            }
            val match = find(root) { matches(it) && it.isVisibleToUser }
            if (match == null) {
                // Compose does not expose nodes outside the viewport; scroll until the label appears.
                val hidden = find(root, matches)
                if (hidden != null) hidden.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                else find(root) { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                return@waitUntil false
            }
            val target = actionableAncestor(match, root)
            if (target != null && target.refresh() && target.isVisibleToUser && target.isEnabled &&
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return@waitUntil true
            }
            // DocumentsUI occasionally exposes a visible root row without ACTION_CLICK
            // on its accessible descendants. Use a real injected touch, not a fabricated URI.
            if (!pickerVisible() || !match.isEnabled) return@waitUntil false
            val bounds = android.graphics.Rect()
            match.getBoundsInScreen(bounds)
            if (bounds.isEmpty) return@waitUntil false
            val downAt = SystemClock.uptimeMillis()
            val down = android.view.MotionEvent.obtain(downAt, downAt,
                android.view.MotionEvent.ACTION_DOWN, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            val up = android.view.MotionEvent.obtain(downAt, downAt + 80,
                android.view.MotionEvent.ACTION_UP, bounds.exactCenterX(), bounds.exactCenterY(), 0)
            down.source = InputDevice.SOURCE_TOUCHSCREEN
            up.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                val pressed = automation.injectInputEvent(down, true)
                val released = automation.injectInputEvent(up, true)
                pressed && released
            } finally {
                down.recycle()
                up.recycle()
            }
        }
        checks++
    }

    private fun back() {
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            yes(automation.injectInputEvent(KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action,
                KeyEvent.KEYCODE_BACK, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD), true), "BACK_EVENT")
        }
    }

    private fun pickerVisible(): Boolean = automation.rootInActiveWindow?.packageName?.toString()?.let {
        it == "com.android.documentsui" || it == "com.google.android.documentsui"
    } == true

    private fun chooseSyntheticRoot() {
        waitUntil("DOCUMENTS_UI") { pickerVisible() }
        // A remembered root also appears in the breadcrumb. Only the actual roots-list row
        // opens a provider; the breadcrumb must never decide whether the drawer is open.
        fun rootsList(): AccessibilityNodeInfo? = find(automation.rootInActiveWindow) {
            it.isVisibleToUser && it.viewIdResourceName?.endsWith(":id/roots_list") == true
        }
        if (rootsList() == null) click("Show roots")
        waitUntil("ROOTS_DRAWER_VISIBLE") { rootsList() != null }
        click(SyntheticBackupDocumentsProvider.ROOT_LABEL, ::rootsList)
        waitUntil("ROOT_SELECTED") {
            rootsList() == null && pickerVisible() && node(SyntheticBackupDocumentsProvider.ROOT_LABEL) != null
        }
    }

    private fun pickerChecks(): String {
        var activity: Activity? = null
        try {
            automation.serviceInfo = automation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
            shell("wm dismiss-keyguard")
            activity = test.startActivitySync(Intent(context, FullBackupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitUntil("BACKUP_ACTIVITY") { node("Jetzt sichern") != null }
            val before = stats().toString()
            click("Jetzt sichern")
            waitUntil("CREATE_PICKER_CANCEL") { pickerVisible() }
            back()
            waitUntil("CREATE_CANCEL_RETURNED") { node("Jetzt sichern") != null }
            yes(stats().toString() == before, "CREATE_CANCEL_NO_PROVIDER_MUTATION")
            val state = Bundle()
            test.runOnMainSync { test.callActivityOnSaveInstanceState(requireNotNull(activity), state) }
            yes(state.getString("target", "").isEmpty(), "CREATE_CANCEL_NO_TARGET")
            click("Sicherung wiederherstellen")
            waitUntil("OPEN_PICKER_CANCEL") { pickerVisible() }
            back()
            waitUntil("OPEN_CANCEL_RETURNED") { !pickerVisible() && node("Jetzt sichern") != null }
            val canceled = Bundle()
            test.runOnMainSync { test.callActivityOnSaveInstanceState(requireNotNull(activity), canceled) }
            yes(canceled.getString("restore_uri", "").isEmpty(), "OPEN_CANCEL_NO_SOURCE")
            yes(stats().toString() == before, "OPEN_CANCEL_NO_PROVIDER_MUTATION")

            click("Jetzt sichern")
            chooseSyntheticRoot()
            waitUntil("SYSTEM_CREATE_SAVE_BUTTON") {
                find(automation.rootInActiveWindow) {
                    it.isVisibleToUser && it.isEnabled && (it.viewIdResourceName?.endsWith(":id/button1") == true ||
                        it.text?.toString()?.equals("Save", ignoreCase = true) == true)
                } != null
            }
            val save = requireNotNull(find(automation.rootInActiveWindow) {
                it.isVisibleToUser && it.isEnabled && (it.viewIdResourceName?.endsWith(":id/button1") == true ||
                    it.text?.toString()?.equals("Save", ignoreCase = true) == true)
            })
            yes(save.performAction(AccessibilityNodeInfo.ACTION_CLICK), "SYSTEM_CREATE_CONFIRM")
            waitUntil("PRODUCTION_SAVE_VERIFIED", 45000) {
                find(automation.rootInActiveWindow) { it.text?.toString()?.startsWith("ZIP vollständig zurückgelesen") == true } != null
            }
            val id = stats().getString("last_created")
            yes(documentStats(id).getInt("writes") == 1 && documentStats(id).getInt("reads") == 1, "UI_PROVIDER_SAVE_READBACK")
            click("Sicherung wiederherstellen")
            chooseSyntheticRoot()
            click(documentStats(id).getString("title"))
            waitUntil("OPEN_RETURNED") { node("Datei prüfen") != null }
            val opened = Bundle()
            test.runOnMainSync { test.callActivityOnSaveInstanceState(requireNotNull(activity), opened) }
            yes(opened.getString("restore_uri") == uri(id).toString(), "SYSTEM_OPEN_RESULT_URI")
            val monitor = test.addMonitor(FullBackupActivity::class.java.name, null, false)
            try {
                val previous = requireNotNull(activity)
                test.runOnMainSync { previous.recreate() }
                activity = monitor.waitForActivityWithTimeout(20000) ?: throw AssertionError("QA_PICKER_RECREATE_FAILED")
                yes(activity !== previous, "REAL_ACTIVITY_RECREATED")
                waitUntil("RECREATED_SOURCE") { node("Datei prüfen") != null }
                val recreated = Bundle()
                test.runOnMainSync { test.callActivityOnSaveInstanceState(requireNotNull(activity), recreated) }
                yes(recreated.getString("restore_uri") == uri(id).toString(), "RECREATE_PRESERVES_PICKED_URI")
                yes(recreated.keySet().none { it.contains("recovery_code") }, "RECREATE_NO_RECOVERY_CODE")
            } finally { test.removeMonitor(monitor) }
            click("Datei prüfen")
            waitUntil("PRODUCTION_PREVIEW_VALIDATED", 45000) {
                find(automation.rootInActiveWindow) { it.text?.toString()?.startsWith("Vorschau bereit.") == true } != null
            }
            yes(documentStats(id).getInt("reads") == 2, "UI_PROVIDER_RESTORE_READ")
            return id
        } finally {
            activity?.let { value -> test.runOnMainSync { value.finish() } }
        }
    }
}

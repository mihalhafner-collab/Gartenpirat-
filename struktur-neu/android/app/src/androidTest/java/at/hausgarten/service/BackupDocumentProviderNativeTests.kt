package at.hausgarten.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import android.view.InputDevice
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.io.IOException
import java.util.Random
import org.json.JSONObject

/** Real resolver, separate-UID local document provider and system DocumentsUI tests. */
class BackupDocumentProviderNativeTests private constructor(private val test: Instrumentation) {
    companion object {
        /** Run provider first, force-stop the target, then run provider_reopen in a new process. */
        @JvmStatic
        fun run(test: Instrumentation, reopen: Boolean): JSONObject =
            BackupDocumentProviderNativeTests(test).execute(reopen)

        @JvmStatic
        fun runMedia(test: Instrumentation, reopen: Boolean): JSONObject =
            BackupDocumentProviderNativeTests(test).mediaProviderProof(reopen)

        // AndroidX ProfileInstaller 1.4.1 writes these two package-bound cache
        // records after startup. They contain no application or fixture data.
        private fun verifiedProfileMetadata(root: File, file: File, updatedAt: Long): Boolean =
            try {
                if (
                    java.nio.file.Files.isSymbolicLink(file.toPath()) ||
                        !file.isFile ||
                        file.canonicalFile.parentFile != root.canonicalFile
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
                                java.io.DataInputStream(file.inputStream()).use { input ->
                                    val schema = input.readInt()
                                    val result = input.readInt()
                                    val packageUpdatedAt = input.readLong()
                                    val profileSize = input.readLong()
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
                                        input.read() == -1
                                }
                        else -> false
                    }
            } catch (_: IOException) {
                false
            }
    }

    private val context: Context = test.targetContext
    private val automation = test.uiAutomation
    private val marker: File
        get() = File(context.noBackupFilesDir, "qa-provider-fixture.json")

    private var checks = 0

    private fun yes(value: Boolean, code: String) {
        if (!value) throw AssertionError("QA_PROVIDER_$code")
        checks++
    }

    private fun rejects(code: String, action: () -> Unit) {
        try {
            action()
        } catch (_: Exception) {
            checks++
            return
        }
        throw AssertionError("QA_PROVIDER_EXPECTED_REJECTION_$code")
    }

    private fun rejectsCode(code: String, action: () -> Unit) {
        try {
            action()
        } catch (failure: Exception) {
            yes(failure.message == code, "EXACT_REJECTION_$code")
            return
        }
        throw AssertionError("QA_PROVIDER_EXPECTED_REJECTION_$code")
    }

    private fun shell(command: String): String =
        automation.executeShellCommand(command).use {
            ParcelFileDescriptor.AutoCloseInputStream(it).use { input ->
                input.readBytes().toString(Charsets.UTF_8).trim()
            }
        }

    private fun control(method: String, id: String? = null, args: Bundle? = null): JSONObject {
        val request = JSONObject().put("method", method)
        if (id != null) request.put("id", id)
        @Suppress("DEPRECATION") // Test echo of arbitrary provider extras; values are untyped.
        args?.keySet()?.forEach { request.put(it, args.get(it)) }
        val encoded =
            Base64.encodeToString(
                request.toString().toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP,
            )
        // The actual shell owns DUMP and ACCESS_CONTENT_PROVIDERS_EXTERNALLY. AMS accepts an
        // accessible path permission for acquisition; document transport still enforces SAF grants.
        val output =
            shell(
                "content call --uri ${SyntheticBackupDocumentsProvider.CONTROL_URI} " +
                    "--method ${SyntheticBackupDocumentsProvider.CONTROL}shell --arg $encoded"
            )
        val payload =
            Regex("qa_result=([A-Za-z0-9_=-]+)").find(output)?.groupValues?.get(1)
                ?: throw IOException("QA_SHELL_CONTROL_FAILED: $output")
        val response = JSONObject(String(Base64.decode(payload, Base64.URL_SAFE), Charsets.UTF_8))
        if (response.getInt("caller_uid") != Process.SHELL_UID)
            throw SecurityException("QA_ACTUAL_SHELL_RESPONSE_REQUIRED")
        return response.getJSONObject("result")
    }

    private fun stats(): JSONObject =
        JSONObject(requireNotNull(control("stats").getString("state")))

    private fun documentStats(id: String): JSONObject =
        stats().getJSONObject("documents").getJSONObject(id)

    private fun create(grant: Boolean = true): String =
        requireNotNull(control("create").getString("id")).also { if (grant) control("grant", it) }

    private fun fault(id: String, value: String) {
        control("fault", id, Bundle().apply { putString("fault", value) })
    }

    private fun uri(id: String): Uri = SyntheticBackupDocumentsProvider.document(id)

    /** Real user-selected ZIP in public Documents, with actual special-access Settings UI. */
    @Suppress("DEPRECATION") // The deliberate public Documents fixture is outside app directories.
    private fun mediaProviderProof(reopen: Boolean): JSONObject {
        yes(test.context.packageName == "at.hausgarten.service.test", "MEDIA_TEST_PACKAGE")
        yes(context.packageName == "at.hausgarten.service", "MEDIA_TARGET_PACKAGE")
        yes(
            shell("getprop ro.kernel.qemu") == "1" && android.os.Build.VERSION.SDK_INT == 35,
            "MEDIA_DISPOSABLE_EMULATOR_ONLY",
        )
        val documents =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val savedState = File(context.noBackupFilesDir, "qa-real-media-backup.json")
        val saved = if (reopen) JSONObject(savedState.readText()) else JSONObject()
        val originalAllFilesAccess =
            if (reopen) saved.getBoolean("original_all_files_access")
            else Environment.isExternalStorageManager()
        val token =
            if (reopen) saved.getString("token")
            else java.util.UUID.randomUUID().toString().replace("-", "")
        yes(token.matches(Regex("[a-f0-9]{32}")), "MEDIA_OWN_TOKEN")
        if (reopen)
            yes(
                saved.getString("fixture") == "synthetic-public-documents-backup-v1",
                "MEDIA_REOPEN_MARKER",
            )
        else {
            yes(!savedState.exists(), "MEDIA_NEW_TEST_MARKER")
            yes(
                !context.getDatabasePath("haus-garten-mobile.db").exists(),
                "MEDIA_FRESH_CREDENTIAL_FIXTURE",
            )
        }
        val directory = File(documents, "qa-backup-$token")
        val filename = "QA-Eisbaer-$token.zip"
        val archive = File(directory, filename)
        var completed = false
        var ownsDirectory = false
        var target: Uri? = if (reopen) Uri.parse(saved.getString("uri")) else null
        try {
            if (reopen) setAllFilesAccess(true) else backupPermissionSetup()
            yes(Environment.isExternalStorageManager(), "MEDIA_EFFECTIVE_ALL_FILES_ACCESS")
            yes(
                documents.isDirectory && documents.canonicalPath.endsWith("/Documents"),
                "MEDIA_PUBLIC_DOCUMENTS_DIRECTORY",
            )
            if (!reopen) yes(!directory.exists() && directory.mkdir(), "MEDIA_NEW_OWNED_FOLDER")
            ownsDirectory = true
            yes(
                directory.isDirectory &&
                    directory.canonicalFile.parentFile == documents.canonicalFile &&
                    !directory.canonicalPath.contains("/Android/"),
                "MEDIA_EXACT_PUBLIC_OWNED_FOLDER",
            )
            PixelLocalStore(context).use { store ->
                val tools =
                    PixelMcpReadTools(store.readSource(), store, PixelOwnerAccess(context, store))
                fun call(name: String, args: JSONObject = JSONObject()): JSONObject {
                    val result = tools.call(name, args)
                    yes(result.optBoolean("ok"), "MEDIA_MCP_$name")
                    return result
                }
                fun data(name: String, args: JSONObject = JSONObject()) =
                    call(name, args).getJSONObject("data")
                fun request(id: String) = JSONObject().put("request_id", "qa_media_${token}_$id")
                fun originalHash(): String {
                    val current = PixelMcpReadTools.effective(store.getState())
                    val originals = JSONObject()
                    for (name in
                        listOf(
                            "customers",
                            "orders",
                            "worklogs",
                            "photos",
                            "invoices",
                            "offers",
                        )) originals.put(name, current.optJSONArray(name) ?: org.json.JSONArray())
                    return PixelMcpWriteTools.hash(originals)
                }
                fun addPhoto(customer: String, work: String, name: String, color: Int): String {
                    val bitmap =
                        android.graphics.Bitmap.createBitmap(
                            3,
                            2,
                            android.graphics.Bitmap.Config.ARGB_8888,
                        )
                    bitmap.eraseColor(color)
                    val image =
                        java.io.ByteArrayOutputStream().use { output ->
                            yes(
                                bitmap.compress(
                                    android.graphics.Bitmap.CompressFormat.PNG,
                                    100,
                                    output,
                                ),
                                "MEDIA_SYNTHETIC_IMAGE",
                            )
                            bitmap.recycle()
                            output.toByteArray()
                        }
                    val upload =
                        data(
                                "begin_file_upload",
                                request("${name}_begin")
                                    .put("byte_size", image.size)
                                    .put("sha256", McpFileTransfer.sha(image))
                                    .put("mime_type", "image/png"),
                            )
                            .getString("upload_id")
                    data(
                        "write_file_chunk",
                        request("${name}_chunk")
                            .put("upload_id", upload)
                            .put("offset", 0)
                            .put(
                                "chunk_base64",
                                java.util.Base64.getEncoder().encodeToString(image),
                            )
                            .put("chunk_sha256", McpFileTransfer.sha(image)),
                    )
                    return data(
                            "create_photo",
                            request("${name}_attach")
                                .put("upload_id", upload)
                                .put("customer_id", customer)
                                .put("worklog_id", work)
                                .put("role", "documentation"),
                        )
                        .getString("photo_id")
                }
                val draftKey = "native_form_draft:" + PixelMcpWriteTools.hash("editor:worklog:new")
                fun protectedState(includeBackupRecords: Boolean = true): String {
                    val state = JSONObject()
                    for (table in listOf("work_sessions", "work_session_events")) {
                        val rows = org.json.JSONArray()
                        store.readableDatabase
                            .rawQuery("SELECT * FROM $table ORDER BY id", null)
                            .use { cursor ->
                                while (cursor.moveToNext()) {
                                    val row = JSONObject()
                                    for (column in 0 until cursor.columnCount) row.put(
                                        cursor.getColumnName(column),
                                        if (cursor.isNull(column)) JSONObject.NULL
                                        else cursor.getString(column),
                                    )
                                    rows.put(row)
                                }
                            }
                        state.put(table, rows)
                    }
                    val keys =
                        if (includeBackupRecords)
                            listOf(draftKey, "pixel_backup_last", "pixel_backup_success")
                        else listOf(draftKey)
                    for (key in keys) state.put(key, store.ownerValue(key, ""))
                    return PixelMcpWriteTools.hash(state)
                }
                fun photoMetadata(id: String) =
                    data(
                        "get_file_metadata",
                        JSONObject().put("resource_type", "photo").put("resource_id", id),
                    )
                if (!reopen) {
                    val initial = PixelMcpReadTools.effective(store.getState())
                    for (name in
                        listOf("customers", "worklogs", "photos", "invoices", "offers")) yes(
                        (initial.optJSONArray(name)?.length() ?: 0) == 0,
                        "MEDIA_EMPTY_$name",
                    )
                    val customer =
                        data(
                                "create_customer",
                                request("customer")
                                    .put("name", "[TEST] QA Media $token")
                                    .put("test_record", true),
                            )
                            .getString("entity_id")
                    val session =
                        call(
                                "start_work",
                                request("timer")
                                    .put("customer_id", customer)
                                    .put("activity", "[TEST] QA laufende Sicherungsarbeit"),
                            )
                            .getJSONObject("session")
                    yes(
                        session.getString("state") == "RUNNING" &&
                            session.getString("id").isNotEmpty(),
                        "MEDIA_SYNTHETIC_SESSION_RUNNING",
                    )
                    store.putOwnerValue(
                        draftKey,
                        JSONObject()
                            .put(
                                "values",
                                JSONObject()
                                    .put("customer_id", customer)
                                    .put("activity", "[TEST] QA offener Arbeitsentwurf")
                                    .put("hours", "0.75"),
                            )
                            .toString(),
                    )
                    fun work(name: String) =
                        data(
                                "create_customer_work_record",
                                request(name)
                                    .put("customer_id", customer)
                                    .put("work_date", "2026-11-01")
                                    .put("activity", "[TEST] QA $name")
                                    .put("hours", 1.25),
                            )
                            .getString("work_record_id")
                    val retainedWork = work("retained")
                    val removedWork = work("removed")
                    val retainedPhoto =
                        addPhoto(customer, retainedWork, "original", android.graphics.Color.RED)
                    val retainedPhotoSha = photoMetadata(retainedPhoto).getString("sha256")
                    val folderUri =
                        android.provider.DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents",
                            "primary:Documents/${directory.name}",
                        )
                    mediaPickerSave(folderUri, filename)
                    val first = JSONObject(store.ownerValue("pixel_backup_success", ""))
                    val selected =
                        Uri.parse(first.getJSONObject("destination").getString("treeUri"))
                    target = selected
                    yes(
                        selected.authority == "com.android.externalstorage.documents" &&
                            android.provider.DocumentsContract.getDocumentId(selected) ==
                                "primary:Documents/${directory.name}/$filename",
                        "MEDIA_REAL_PICKER_SELECTED_EXACT_FILE",
                    )
                    yes(
                        context.contentResolver.persistedUriPermissions.any {
                            it.uri == target && it.isReadPermission && it.isWritePermission
                        },
                        "MEDIA_REAL_PERSISTED_READ_WRITE_GRANT",
                    )
                    yes(archive.isFile, "MEDIA_ACTUAL_SCOPED_STORAGE_FILE")
                    var oldHash = BackupArchive.sha256(archive)
                    yes(
                        oldHash == first.getJSONObject("archive").getString("sha256"),
                        "MEDIA_INITIAL_PROVIDER_READBACK_MATCH",
                    )
                    val protectedBeforeDenial = protectedState()
                    val unselected = File(directory, "QA-unselected-$token.zip")
                    val unselectedBytes =
                        "[TEST] QA unselected ZIP must remain untouched".toByteArray()
                    unselected.writeBytes(unselectedBytes)
                    val unselectedUri =
                        android.provider.DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents",
                            "primary:Documents/${directory.name}/${unselected.name}",
                        )
                    yes(
                        context.contentResolver.persistedUriPermissions.none {
                            it.uri == unselectedUri
                        },
                        "MEDIA_UNSELECTED_TARGET_HAS_NO_GRANT",
                    )
                    rejectsCode("BACKUP_DOCUMENT_PERMISSION_REQUIRED") {
                        PixelBackupService(context, store).save(
                            "qa_media_${token}_unselected",
                            unselectedUri,
                        ) {}
                    }
                    yes(
                        Environment.isExternalStorageManager() &&
                            unselected.readBytes().contentEquals(unselectedBytes),
                        "MEDIA_ALL_FILES_DOES_NOT_AUTHORIZE_UNSELECTED_TARGET",
                    )
                    yes(unselected.delete(), "MEDIA_OWN_UNSELECTED_FILE_REMOVED")
                    yes(
                        protectedState() == protectedBeforeDenial,
                        "MEDIA_UNSELECTED_DENIAL_PRESERVES_TARGET_SESSION_AND_DRAFT",
                    )
                    val oldSize = archive.length()
                    setAllFilesAccess(false)
                    yes(!Environment.isExternalStorageManager(), "MEDIA_ALL_FILES_ACCESS_REVOKED")
                    rejectsCode("BACKUP_ALL_FILES_ACCESS_REQUIRED") {
                        PixelBackupService(context, store).save(
                            "qa_media_${token}_revoked",
                            selected,
                        ) {}
                    }
                    val allFilesDenied = PixelBackupService(context, store).status(JSONObject())
                    yes(
                        allFilesDenied.getString("state") == "FAILED" &&
                            allFilesDenied.getString("current_access_error") ==
                                "BACKUP_ALL_FILES_ACCESS_REQUIRED" &&
                            allFilesDenied.getBoolean("all_files_access_required") &&
                            !allFilesDenied.getBoolean("document_permission_required"),
                        "MEDIA_LIVE_STATUS_REPORTS_ALL_FILES_REVOCATION",
                    )
                    context.contentResolver.openInputStream(selected).use {
                        BackupDocumentTransfer.verifyReadback(it, oldHash, oldSize)
                    }
                    yes(
                        context.contentResolver.persistedUriPermissions.any {
                            it.uri == selected && it.isReadPermission && it.isWritePermission
                        },
                        "MEDIA_REVOKED_ALL_FILES_PRESERVES_ZIP_AND_SAF_GRANT",
                    )
                    yes(
                        protectedState() == protectedBeforeDenial,
                        "MEDIA_PERMISSION_DENIAL_PRESERVES_TARGET_SESSION_AND_DRAFT",
                    )
                    setAllFilesAccess(true)
                    mediaAtomicPathDiagnostics(selected)
                    context.contentResolver.releasePersistableUriPermission(
                        selected,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    yes(
                        context.contentResolver.persistedUriPermissions.none {
                            it.uri == selected && it.isReadPermission && it.isWritePermission
                        },
                        "MEDIA_SELECTED_PERSISTED_GRANT_RELEASED",
                    )
                    rejectsCode("BACKUP_DOCUMENT_PERMISSION_REQUIRED") {
                        PixelBackupService(context, store).save(
                            "qa_media_${token}_document_grant_revoked",
                            selected,
                        ) {}
                    }
                    val documentDenied = PixelBackupService(context, store).status(JSONObject())
                    yes(
                        documentDenied.getString("state") == "FAILED" &&
                            documentDenied.getString("current_access_error") ==
                                "BACKUP_DOCUMENT_PERMISSION_REQUIRED" &&
                            documentDenied.getBoolean("document_permission_required") &&
                            !documentDenied.getBoolean("all_files_access_required"),
                        "MEDIA_LIVE_STATUS_REPORTS_DOCUMENT_GRANT_REVOCATION",
                    )
                    yes(
                        BackupArchive.sha256(archive) == oldHash &&
                            archive.length() == oldSize &&
                            protectedState() == protectedBeforeDenial,
                        "MEDIA_DOCUMENT_DENIAL_PRESERVES_ZIP_TARGET_SESSION_AND_DRAFT",
                    )
                    mediaRegrantPicker(selected, filename)
                    val reauthorized = JSONObject(store.ownerValue("pixel_backup_success", ""))
                    oldHash = BackupArchive.sha256(archive)
                    yes(
                        reauthorized.getJSONObject("destination").getString("treeUri") ==
                            selected.toString() &&
                            reauthorized.getJSONObject("archive").getString("sha256") == oldHash &&
                            directory.listFiles().orEmpty().map { it.name } == listOf(filename),
                        "MEDIA_REGRANT_SAVES_SAME_EXISTING_ZIP_WITHOUT_DUPLICATE",
                    )
                    val original =
                        call("get_work_record", JSONObject().put("work_record_id", retainedWork))
                    data(
                        "update_work_record",
                        request("edit")
                            .put("work_record_id", retainedWork)
                            .put("expected_record_hash", original.getString("record_hash"))
                            .put("updates", JSONObject().put("notes", "[TEST] QA changed")),
                    )
                    val addedPhoto =
                        addPhoto(customer, retainedWork, "additional", android.graphics.Color.BLUE)
                    val removal =
                        call("get_work_record", JSONObject().put("work_record_id", removedWork))
                    data(
                        "delete_entity",
                        request("delete")
                            .put("entity", "worklog")
                            .put("entity_id", removedWork)
                            .put("expected_record_hash", removal.getString("entity_hash")),
                    )
                    val expected = originalHash()
                    val secondRequest = "qa_media_${token}_second"
                    val updated =
                        PixelBackupService(context, store).save(secondRequest, selected) {}
                    yes(
                        updated.getBoolean("verified") && BackupArchive.sha256(archive) != oldHash,
                        "MEDIA_SAME_TARGET_ATOMIC_UPDATE",
                    )
                    val current = JSONObject(store.ownerValue("pixel_backup_success", ""))
                    yes(
                        current.getJSONObject("destination").getString("treeUri") ==
                            target.toString() &&
                            directory.listFiles().orEmpty().map { it.name } == listOf(filename),
                        "MEDIA_ONE_VISIBLE_BACKUP_SAME_URI",
                    )
                    yes(
                        photoMetadata(retainedPhoto).getString("sha256") == retainedPhotoSha,
                        "MEDIA_UNCHANGED_PHOTO_IDENTITY",
                    )
                    val service = PixelBackupService(context, store)
                    val withoutComment = archive.readBytes()
                    val end = withoutComment.size - 22
                    yes(
                        end >= 0 &&
                            withoutComment[end] == 0x50.toByte() &&
                            withoutComment[end + 1] == 0x4b.toByte() &&
                            withoutComment[end + 2] == 0x05.toByte() &&
                            withoutComment[end + 3] == 0x06.toByte() &&
                            withoutComment[end + 20] == 0.toByte() &&
                            withoutComment[end + 21] == 0.toByte(),
                        "MEDIA_GENERATED_ZIP_HAS_EMPTY_EOCD_COMMENT",
                    )
                    val comment = "[TEST] QA complete ZIP comment ".repeat(2048).take(48 * 1024)
                    val commentBytes = comment.toByteArray(Charsets.US_ASCII)
                    val withComment = withoutComment.copyOf(withoutComment.size + commentBytes.size)
                    withComment[end + 20] = (commentBytes.size and 255).toByte()
                    withComment[end + 21] = (commentBytes.size ushr 8).toByte()
                    commentBytes.copyInto(withComment, withoutComment.size)
                    archive.writeBytes(withComment)
                    java.util.zip.ZipFile(archive).use {
                        yes(it.comment == comment, "MEDIA_LONG_ZIP_COMMENT_IS_STANDARD_VALID_ZIP")
                    }
                    val approval = service.inspectExistingTarget(selected)
                    yes(
                        approval.getString("destination") == selected.toString() &&
                            approval.getString("archive_sha256") == BackupArchive.sha256(archive) &&
                            approval.getLong("archive_size") == archive.length() &&
                            approval.getJSONObject("counts").getInt("customers") == 1 &&
                            approval.getJSONObject("counts").getInt("worklogs") == 1 &&
                            approval.getJSONObject("counts").getInt("photos") == 2 &&
                            approval.getJSONObject("current_counts").getInt("worklogs") == 1 &&
                            approval.getJSONObject("current_counts").getInt("photos") == 2,
                        "MEDIA_EXISTING_TARGET_INSPECTION_COVERS_LONG_ZIP_COMMENT",
                    )
                    val validArchive = archive.readBytes()
                    try {
                        val changed = validArchive.clone()
                        changed[changed.size / 2] =
                            (changed[changed.size / 2].toInt() xor 1).toByte()
                        archive.writeBytes(changed)
                        rejectsCode("BACKUP_TARGET_CHANGED") {
                            service.saveWithApproval(
                                "qa_media_${token}_stale_approval",
                                selected,
                                approval,
                            ) {}
                        }
                        yes(
                            archive.readBytes().contentEquals(changed),
                            "MEDIA_STALE_SAME_SIZE_APPROVAL_DOES_NOT_OVERWRITE",
                        )
                    } finally {
                        archive.writeBytes(validArchive)
                    }
                    val guard = BackupDeviceGuard(context)
                    val oldIdentity = guard.current()
                    val newIdentity =
                        guard.advanceForRestore(
                            "qa_media_${token}_binding_epoch",
                            oldIdentity.getString("restoreEpoch"),
                        )
                    yes(
                        newIdentity.getString("installationId") ==
                            oldIdentity.getString("installationId") &&
                            newIdentity.getString("restoreEpoch") !=
                                oldIdentity.getString("restoreEpoch"),
                        "MEDIA_SYNTHETIC_EPOCH_CHANGED_WITHOUT_RESTORE",
                    )
                    val unboundStatus = service.status(JSONObject())
                    yes(
                        service.targetSelection().optString("uri").isEmpty() &&
                            unboundStatus.getInt("total") == 0 &&
                            unboundStatus.getString("last_local_verified_at").isEmpty(),
                        "MEDIA_NEW_EPOCH_DOES_NOT_INHERIT_OLD_TARGET_OR_PROOF",
                    )
                    mediaAtomicPathDiagnostics(selected)
                    mediaExistingTargetRebind(selected, filename, archive) { protectedState() }
                    val firstRebound = JSONObject(store.ownerValue("pixel_backup_success", ""))
                    yes(
                        firstRebound.getString("installationId") ==
                            newIdentity.getString("installationId") &&
                            firstRebound.getString("restoreEpoch") ==
                                newIdentity.getString("restoreEpoch") &&
                            firstRebound.getJSONObject("destination").getString("treeUri") ==
                                selected.toString() &&
                            directory.listFiles().orEmpty().map { it.name } == listOf(filename),
                        "MEDIA_CONFIRMED_EXISTING_ZIP_BINDS_CURRENT_IDENTITY",
                    )
                    val pendingIdentity =
                        guard.advanceForRestore(
                            "qa_media_${token}_pending_epoch",
                            newIdentity.getString("restoreEpoch"),
                        )
                    val pendingRequest = "qa_media_${token}_pending_approved"
                    val pendingApproval = service.inspectExistingTarget(selected)
                    val beforePendingHash = BackupArchive.sha256(archive)
                    val beforePendingSize = archive.length()
                    val beforePendingBusiness = protectedState(false)
                    var pendingGrantReleased = false
                    rejectsCode("BACKUP_DOCUMENT_PERMISSION_REQUIRED") {
                        service.saveWithApproval(pendingRequest, selected, pendingApproval) {
                            message ->
                            if (
                                !pendingGrantReleased &&
                                    message.startsWith("Gewählte Sicherungsdatei aktualisieren")
                            ) {
                                context.contentResolver.releasePersistableUriPermission(
                                    selected,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                )
                                pendingGrantReleased = true
                            }
                        }
                    }
                    val prepared = JSONObject(store.ownerValue("pixel_backup_last", ""))
                    val pendingSelection = service.targetSelection()
                    yes(
                        pendingGrantReleased &&
                            prepared.getString("requestId") == pendingRequest &&
                            prepared.getString("restoreEpoch") ==
                                pendingIdentity.getString("restoreEpoch") &&
                            prepared
                                .getJSONObject("approved_existing_target")
                                .getString("archive_sha256") == beforePendingHash &&
                            prepared.getJSONObject("archive").getLong("size") > 0 &&
                            pendingSelection.getString("uri") == selected.toString() &&
                            pendingSelection.getString("request_id") == pendingRequest &&
                            JSONObject(store.ownerValue("pixel_backup_success", ""))
                                .getString("restoreEpoch") == newIdentity.getString("restoreEpoch"),
                        "MEDIA_PENDING_APPROVAL_AND_PREPARED_ARCHIVE_KEEP_REQUEST_WITHOUT_SUCCESS",
                    )
                    yes(
                        BackupArchive.sha256(archive) == beforePendingHash &&
                            archive.length() == beforePendingSize &&
                            protectedState(false) == beforePendingBusiness,
                        "MEDIA_PENDING_GRANT_LOSS_PRESERVES_ZIP_SESSION_AND_DRAFT",
                    )
                    mediaRegrantPicker(selected, filename, pendingRequest)
                    val rebound = JSONObject(store.ownerValue("pixel_backup_success", ""))
                    yes(
                        rebound.getString("requestId") == pendingRequest &&
                            rebound.getString("restoreEpoch") ==
                                pendingIdentity.getString("restoreEpoch") &&
                            rebound.has("approved_existing_target"),
                        "MEDIA_PENDING_APPROVED_REGRANT_CONTINUES_SAME_REQUEST",
                    )
                    val reboundHash = BackupArchive.sha256(archive)
                    val reboundStat = android.system.Os.stat(archive.path)
                    val reboundModified = archive.lastModified()
                    yes(
                        service
                            .save(rebound.getString("requestId"), selected) {}
                            .getBoolean("verified") &&
                            BackupArchive.sha256(archive) == reboundHash &&
                            android.system.Os.stat(archive.path).st_ino == reboundStat.st_ino &&
                            archive.lastModified() == reboundModified,
                        "MEDIA_APPROVED_SAME_REQUEST_REPLAYS_READBACK_WITHOUT_REWRITE",
                    )
                    val changedApproval =
                        JSONObject(rebound.getJSONObject("approved_existing_target").toString())
                            .put(
                                "archive_size",
                                rebound
                                    .getJSONObject("approved_existing_target")
                                    .getLong("archive_size") + 1,
                            )
                    rejectsCode("BACKUP_REQUEST_ID_CONFLICT") {
                        service.saveWithApproval(
                            rebound.getString("requestId"),
                            selected,
                            changedApproval,
                        ) {}
                    }
                    yes(
                        BackupArchive.sha256(archive) == reboundHash,
                        "MEDIA_CHANGED_APPROVAL_REPLAY_PRESERVES_ARCHIVE",
                    )
                    savedState.writeText(
                        JSONObject()
                            .put("fixture", "synthetic-public-documents-backup-v1")
                            .put("original_all_files_access", originalAllFilesAccess)
                            .put("token", token)
                            .put("uri", target.toString())
                            .put("request_id", rebound.getString("requestId"))
                            .put("pid", android.os.Process.myPid())
                            .put("archive_sha256", BackupArchive.sha256(archive))
                            .put("source_hash", expected)
                            .put("retained_work", retainedWork)
                            .put("removed_work", removedWork)
                            .put("retained_photo", retainedPhoto)
                            .put("retained_photo_sha256", retainedPhotoSha)
                            .put("added_photo", addedPhoto)
                            .put(
                                "added_photo_sha256",
                                photoMetadata(addedPhoto).getString("sha256"),
                            )
                            .toString()
                    )
                } else {
                    val selected = requireNotNull(target)
                    yes(
                        android.os.Process.myPid() != saved.getInt("pid"),
                        "MEDIA_NEW_TARGET_PROCESS",
                    )
                    yes(
                        context.contentResolver.persistedUriPermissions.any {
                            it.uri == target && it.isReadPermission && it.isWritePermission
                        },
                        "MEDIA_GRANT_AFTER_PROCESS_RESTART",
                    )
                    yes(
                        archive.isFile &&
                            BackupArchive.sha256(archive) == saved.getString("archive_sha256"),
                        "MEDIA_REOPEN_ARCHIVE_UNCHANGED",
                    )
                    val result =
                        PixelBackupService(context, store).save(
                            saved.getString("request_id"),
                            selected,
                        ) {}
                    yes(
                        result.getBoolean("verified") &&
                            BackupArchive.sha256(archive) == saved.getString("archive_sha256"),
                        "MEDIA_PROVIDER_REOPEN_READBACK_NO_REWRITE",
                    )
                    yes(
                        originalHash() == saved.getString("source_hash"),
                        "MEDIA_REOPEN_SOURCE_UNCHANGED",
                    )
                    isolatedMediaRestore(selected, saved, token)
                }
            }
            if (reopen)
                mediaRestoreUi(
                    requireNotNull(target),
                    filename,
                    saved.getString("source_hash"),
                    token,
                )
            completed = true
            return JSONObject()
                .put("checks", checks)
                .put("synthetic_only", true)
                .put("productive_data_read", false)
                .put("real_external_storage_provider", true)
                .put("actual_saf_grant", true)
                .put("same_target_atomic_replace", true)
                .put("public_documents_target", true)
                .put("all_files_access", true)
                .put("permission_ui_tested", true)
                .put("unselected_target_denied", !reopen)
                .put("permission_revocation_preserves_zip", !reopen)
                .put("permission_denial_preserves_target_session_and_draft", !reopen)
                .put("persisted_grant_reselection", !reopen)
                .put("existing_target_rebinding", !reopen)
                .put("existing_target_cancel_preserves_state", !reopen)
                .put("stale_existing_target_approval_rejected", !reopen)
                .put("long_zip_comment_fully_hashed", !reopen)
                .put("approved_request_replay_readback", !reopen)
                .put("pending_approved_request_regrant_same_id", !reopen)
                .put(
                    "existing_target_ui_screenshot",
                    if (reopen) "" else "qa-native-backup-restore-existing-target.png",
                )
                .put("permission_state_restored", reopen)
                .put("uninstall_tested", false)
                .put("new_process_readback", reopen)
                .put("isolated_restore", reopen)
                .put("restore_cold_start_guard_retained", reopen)
                .put("media_restore_ui", reopen)
                .put("restore_confirmation_cancel_no_activation", reopen)
                .put("restore_confirmation_pending_restart", reopen)
                .put(
                    "media_restore_ui_screenshots",
                    org.json.JSONArray(
                        if (reopen)
                            listOf(
                                "qa-native-backup-restore-confirm.png",
                                "qa-native-backup-restore-cancelled.png",
                                "qa-native-backup-restore-pending.png",
                            )
                        else emptyList<String>()
                    ),
                )
                .put("physical_pixel_tested", false)
        } finally {
            // Phase one keeps only its own fixture until the following new-process test.
            if (reopen || !completed) {
                try {
                    setAllFilesAccess(true)
                    target?.let { selected ->
                        if (
                            selected.authority == "com.android.externalstorage.documents" &&
                                android.provider.DocumentsContract.getDocumentId(selected)
                                    .substringAfterLast('/') == filename &&
                                context.contentResolver.persistedUriPermissions.any {
                                    it.uri == selected &&
                                        it.isReadPermission &&
                                        it.isWritePermission
                                }
                        )
                            yes(
                                android.provider.DocumentsContract.deleteDocument(
                                    context.contentResolver,
                                    selected,
                                ),
                                "MEDIA_OWN_DOCUMENT_REMOVED",
                            )
                        try {
                            context.contentResolver.releasePersistableUriPermission(
                                selected,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                            )
                        } catch (_: SecurityException) {
                            /* A failed picker may not have persisted a grant. */
                        }
                    }
                    if (
                        ownsDirectory &&
                            directory.canonicalFile.parentFile == documents.canonicalFile &&
                            directory.name == "qa-backup-$token"
                    )
                        yes(directory.deleteRecursively(), "MEDIA_OWN_TEST_FOLDER_REMOVED")
                    if (savedState.isFile) yes(savedState.delete(), "MEDIA_OWN_TEST_MARKER_REMOVED")
                } finally {
                    setAllFilesAccess(originalAllFilesAccess)
                }
            }
        }
    }

    /** Exercise the product handoff, including refusal and no automatic save on return. */
    private fun backupPermissionSetup() {
        setAllFilesAccess(false)
        var activity: Activity? = null
        try {
            activity =
                test.startActivitySync(
                    Intent(context, FullBackupActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            waitUntil("ACCESS_BACKUP_ACTIVITY") { node("Jetzt sichern") != null }
            val before =
                PixelLocalStore(context).use { store ->
                    listOf("pixel_backup_last", "pixel_backup_success").map {
                        store.ownerValue(it, "")
                    }
                }
            click("Jetzt sichern")
            waitUntil("ACCESS_REQUIRED_BEFORE_PICKER") {
                node("Dateizugriff erforderlich") != null && !pickerVisible()
            }
            setAllFilesAccess(false) { click("Dateizugriff einrichten") }
            waitUntil("ACCESS_SETTINGS_REFUSED") {
                node("Dateizugriff nicht freigegeben") != null && !pickerVisible()
            }
            yes(!Environment.isExternalStorageManager(), "MEDIA_SETTINGS_REFUSAL_ENFORCED")
            setAllFilesAccess(true) { click("Dateizugriff einrichten") }
            waitUntil("ACCESS_SETTINGS_GRANTED_WITHOUT_SAVE") {
                node("Dateizugriff eingerichtet") != null &&
                    node("Jetzt sichern") != null &&
                    !pickerVisible()
            }
            val state = Bundle()
            test.runOnMainSync {
                test.callActivityOnSaveInstanceState(requireNotNull(activity), state)
            }
            yes(
                state.getString("target", "").isEmpty() && state.getString("request", "").isEmpty(),
                "MEDIA_PERMISSION_RETURN_DOES_NOT_CHOOSE_OR_SAVE",
            )
            PixelLocalStore(context).use { store ->
                yes(
                    listOf("pixel_backup_last", "pixel_backup_success").map {
                        store.ownerValue(it, "")
                    } == before,
                    "MEDIA_PERMISSION_HANDOFF_PRESERVES_BACKUP_STATE",
                )
            }
        } finally {
            activity?.let { value -> test.runOnMainSync { value.finish() } }
        }
    }

    /** Change only this disposable app's real Android special-access switch, never an app-op. */
    private fun setAllFilesAccess(enabled: Boolean, launch: (() -> Unit)? = null) {
        yes(
            test.context.packageName == "at.hausgarten.service.test" &&
                context.packageName == "at.hausgarten.service" &&
                android.os.Build.VERSION.SDK_INT == 35 &&
                shell("getprop ro.kernel.qemu") == "1",
            "ALL_FILES_SETTINGS_DISPOSABLE_EMULATOR_ONLY",
        )
        automation.serviceInfo =
            automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
        shell("wm dismiss-keyguard")
        if (launch != null) launch()
        else
            test.runOnMainSync {
                context.startActivity(
                    Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        try {
            waitUntil("ALL_FILES_SETTINGS_PAGE") {
                automation.rootInActiveWindow?.packageName?.toString() == "com.android.settings"
            }
            val root = requireNotNull(automation.rootInActiveWindow)
            var toggle: AccessibilityNodeInfo? = null
            waitUntil("ALL_FILES_SETTINGS_SWITCH") {
                toggle =
                    find(automation.rootInActiveWindow) {
                        it.isVisibleToUser && it.isEnabled && it.isCheckable
                    }
                toggle != null
            }
            if (Environment.isExternalStorageManager() != enabled) {
                val switch = requireNotNull(toggle)
                val action = if (switch.isClickable) switch else actionableAncestor(switch, root)
                yes(
                    action?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true,
                    "ALL_FILES_REAL_SETTINGS_TOGGLE",
                )
                waitUntil("ALL_FILES_EFFECTIVE_PERMISSION") {
                    Environment.isExternalStorageManager() == enabled
                }
            }
        } finally {
            if (automation.rootInActiveWindow?.packageName?.toString() == "com.android.settings")
                back()
        }
        yes(
            Environment.isExternalStorageManager() == enabled,
            "ALL_FILES_SETTINGS_RESULT_$enabled",
        )
        waitUntil("ALL_FILES_SETTINGS_RETURNED") {
            automation.rootInActiveWindow?.packageName?.toString() != "com.android.settings"
        }
    }

    /**
     * Synthetic diagnostics survive a later assertion or adapter failure without exposing a URI.
     */
    private fun mediaAtomicPathDiagnostics(target: Uri) {
        val report = JSONObject().put("all_files_access", Environment.isExternalStorageManager())
        try {
            context.assets.openXmlResourceParser("AndroidManifest.xml").use { parser ->
                var found = false
                while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (
                        parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG &&
                            parser.name == "application"
                    ) {
                        report.put(
                            "raw_optin",
                            parser.getAttributeBooleanValue(
                                "http://schemas.android.com/apk/res/android",
                                "requestRawExternalStorageAccess",
                                false,
                            ),
                        )
                        found = true
                        break
                    }
                    parser.next()
                }
                if (!found) report.put("raw_optin", JSONObject.NULL)
            }
        } catch (failure: Exception) {
            report.put("raw_optin", JSONObject.NULL)
            report.put("raw_optin_read_error", failure.javaClass.simpleName)
        }
        try {
            context.contentResolver.openFileDescriptor(target, "r").use { descriptor ->
                val fd = requireNotNull(descriptor)
                val provider = android.system.Os.fstat(fd.fileDescriptor)
                report.put(
                    "provider_fstat",
                    JSONObject().put("st_dev", provider.st_dev).put("st_ino", provider.st_ino),
                )
                val path = android.system.Os.readlink("/proc/self/fd/${fd.fd}")
                val file = File(path)
                val local = android.system.Os.stat(path)
                report.put(
                    "rawpath_stat",
                    JSONObject().put("st_dev", local.st_dev).put("st_ino", local.st_ino),
                )
                report.put(
                    "path_class",
                    if (
                        Regex("/Documents/qa-backup-[a-f0-9]{32}/QA-Eisbaer-[a-f0-9]{32}\\.zip$")
                            .containsMatchIn(path)
                    )
                        "public_documents_qa"
                    else "unexpected",
                )
                report.put("canonical_equals_absolute", file.canonicalFile == file.absoluteFile)
                report.put(
                    "same_device_and_inode",
                    provider.st_dev == local.st_dev && provider.st_ino == local.st_ino,
                )
            }
        } catch (failure: Exception) {
            report.put("stat_read_error", failure.javaClass.simpleName)
        } finally {
            test.sendStatus(
                0,
                Bundle().apply { putString("qa_backup_atomic_path", report.toString()) },
            )
        }
    }

    /** A new epoch has no old binding: selecting an existing ZIP needs explicit replacement. */
    private fun mediaExistingTargetRebind(
        target: Uri,
        filename: String,
        archive: File,
        protectedState: () -> String,
    ) {
        var activity: Activity? = null
        val originalHash = BackupArchive.sha256(archive)
        val originalProtected = protectedState()
        try {
            activity =
                test.startActivitySync(
                    Intent(context, FullBackupActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            waitUntil("MEDIA_BINDING_BACKUP_ACTIVITY") { node("Jetzt sichern") != null }
            click("Vorhandene Sicherungsdatei wählen")
            waitUntil("MEDIA_BINDING_REAL_OPEN_PICKER") { pickerVisible() }
            if (node(filename) == null) click(requireNotNull(archive.parentFile).name)
            click(filename)
            waitUntil("MEDIA_BINDING_CONFIRMATION", 45000) {
                node("Vorhandene Sicherung ersetzen?") != null
            }
            yes(
                find(automation.rootInActiveWindow) {
                    it.isVisibleToUser &&
                        it.text?.toString()?.contains("Bestand in der Datei:") == true &&
                        it.text?.toString()?.contains("Aktueller Bestand:") == true
                } != null,
                "MEDIA_BINDING_DIALOG_SHOWS_FILE_AND_CURRENT_COUNTS",
            )
            mediaScreenshot("existing-target")
            click("Abbrechen")
            waitUntil("MEDIA_BINDING_CANCELLED") {
                node("Vorhandene Sicherung ersetzen?") == null &&
                    node("Sicherung fortsetzen") != null
            }
            yes(
                protectedState() == originalProtected &&
                    BackupArchive.sha256(archive) == originalHash,
                "MEDIA_EXISTING_TARGET_CANCEL_PRESERVES_ZIP_SESSION_DRAFT_AND_OWNER",
            )
            PixelLocalStore(context).use { store ->
                yes(
                    PixelBackupService(context, store).targetSelection().optString("uri").isEmpty(),
                    "MEDIA_CANCEL_DOES_NOT_BIND_EXISTING_TARGET",
                )
            }
            click("Sicherung fortsetzen")
            waitUntil("MEDIA_BINDING_SECOND_CONFIRMATION") {
                node("Vorhandene Sicherung ersetzen?") != null
            }
            click("Diese Datei aktualisieren")
            waitUntil("MEDIA_BINDING_VERIFIED", 45000) {
                node("Gespeichert und geprüft") != null && node("ZIP geprüft.") != null
            }
            yes(
                context.contentResolver.persistedUriPermissions.any {
                    it.uri == target && it.isReadPermission && it.isWritePermission
                },
                "MEDIA_CONFIRMED_BINDING_RETAINS_EXACT_RW_GRANT",
            )
        } finally {
            activity?.let { value -> test.runOnMainSync { value.finish() } }
        }
    }

    /** Reauthorize the same existing document through the product's actual open-file action. */
    private fun mediaRegrantPicker(
        target: Uri,
        filename: String,
        expectedRequestId: String? = null,
    ) {
        var activity: Activity? = null
        try {
            activity =
                test.startActivitySync(
                    Intent(context, FullBackupActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            waitUntil("MEDIA_REGRANT_BACKUP_ACTIVITY") {
                node(if (expectedRequestId == null) "Jetzt sichern" else "Sicherung fortsetzen") !=
                    null
            }
            if (expectedRequestId != null) {
                val pending = Bundle()
                test.runOnMainSync {
                    test.callActivityOnSaveInstanceState(requireNotNull(activity), pending)
                }
                yes(
                    pending.getString("request") == expectedRequestId &&
                        pending.getString("target") == target.toString(),
                    "MEDIA_PENDING_REQUEST_REACHES_REAL_REGRANT_ACTIVITY",
                )
            }
            click("Sicherungsdatei erneut wählen")
            waitUntil("MEDIA_REGRANT_REAL_OPEN_PICKER") { pickerVisible() }
            click(filename)
            waitUntil("MEDIA_REGRANT_VERIFIED", 45000) {
                if (expectedRequestId != null && node("Vorhandene Sicherung ersetzen?") != null)
                    throw AssertionError("QA_PENDING_APPROVAL_MUST_NOT_PROMPT_AGAIN")
                node("Gespeichert und geprüft") != null && node("ZIP geprüft.") != null
            }
            yes(
                context.contentResolver.persistedUriPermissions.any {
                    it.uri == target && it.isReadPermission && it.isWritePermission
                },
                "MEDIA_EXISTING_DOCUMENT_RW_PERMISSION_REGRANTED_BY_PICKER",
            )
            PixelLocalStore(context).use { store ->
                val status = PixelBackupService(context, store).status(JSONObject())
                yes(
                    status.getString("state") == "VERIFIED" &&
                        status.getString("current_access_error").isEmpty() &&
                        !status.getBoolean("all_files_access_required") &&
                        !status.getBoolean("document_permission_required"),
                    "MEDIA_REGRANT_CLEARS_LIVE_ACCESS_ERROR_AFTER_VERIFIED_SAVE",
                )
                if (expectedRequestId != null)
                    yes(
                        JSONObject(store.ownerValue("pixel_backup_success", ""))
                            .getString("requestId") == expectedRequestId,
                        "MEDIA_REAL_REGRANT_PRESERVES_PENDING_REQUEST_ID",
                    )
            }
        } finally {
            activity?.let { value -> test.runOnMainSync { value.finish() } }
        }
    }

    /** Real picker and confirmation; a request is observed and then cancelled without a swap. */
    private fun mediaRestoreUi(target: Uri, filename: String, expectedHash: String, token: String) {
        yes(
            BackupRestoreService.pendingStatus(context).getString("phase") == "NONE",
            "MEDIA_UI_NO_PRIOR_ACTIVATION",
        )
        yes(mediaOriginalHash() == expectedHash, "MEDIA_UI_INITIAL_ORIGINALS")
        val folder =
            android.provider.DocumentsContract.buildDocumentUri(
                requireNotNull(target.authority),
                android.provider.DocumentsContract.getDocumentId(target).substringBeforeLast('/'),
            )
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action == Intent.ACTION_OPEN_DOCUMENT)
                        intent.putExtra(
                            android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                            folder,
                        )
                    return null
                }
            }
        test.addMonitor(monitor)
        automation.serviceInfo =
            automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
        var activity: Activity? = null
        var restoreId = ""
        var previewHash = ""
        try {
            shell("wm dismiss-keyguard")
            activity =
                test.startActivitySync(
                    Intent(context, FullBackupActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            waitUntil("MEDIA_UI_BACKUP_ACTIVITY") { node("Jetzt sichern") != null }
            click("Sicherung wiederherstellen")
            waitUntil("MEDIA_UI_REAL_OPEN_PICKER") { pickerVisible() }
            click(filename)
            waitUntil("MEDIA_UI_PICKED_SOURCE") { node("Datei prüfen") != null }
            val picked = Bundle()
            test.runOnMainSync {
                test.callActivityOnSaveInstanceState(requireNotNull(activity), picked)
            }
            yes(
                picked.getString("restore_uri") == target.toString(),
                "MEDIA_UI_REAL_OPEN_EXACT_URI",
            )
            click("Datei prüfen")
            waitUntil("MEDIA_UI_PREVIEW_VALIDATED", 45000) {
                find(automation.rootInActiveWindow) {
                    it.isVisibleToUser &&
                        it.text?.toString()?.startsWith("Vorschau bereit.") == true
                } != null && node("Wiederherstellen") != null
            }
            val preview = Bundle()
            test.runOnMainSync {
                test.callActivityOnSaveInstanceState(requireNotNull(activity), preview)
            }
            restoreId = requireNotNull(preview.getString("restore_id"))
            val verifiedPreview = BackupRestoreService(context).status(restoreId)
            previewHash = verifiedPreview.getString("preview_hash")
            yes(
                verifiedPreview.getString("phase") == "VALIDATED",
                "MEDIA_UI_REAL_PREVIEW_VALIDATED",
            )
            click("Wiederherstellen")
            waitUntil("MEDIA_UI_FIRST_CONFIRMATION") { node("Bestand ersetzen?") != null }
            mediaScreenshot("confirm")
            click("Abbrechen")
            waitUntil("MEDIA_UI_CONFIRMATION_CANCELLED") {
                node("Bestand ersetzen?") == null && node("Wiederherstellen") != null
            }
            yes(
                BackupRestoreService.pendingStatus(context).getString("phase") == "NONE" &&
                    BackupRestoreService(context).status(restoreId).getString("phase") ==
                        "VALIDATED",
                "MEDIA_UI_CANCEL_NO_ACTIVATION",
            )
            yes(mediaOriginalHash() == expectedHash, "MEDIA_UI_CANCEL_NO_BUSINESS_MUTATION")
            mediaScreenshot("cancelled")
            click("Wiederherstellen")
            waitUntil("MEDIA_UI_SECOND_CONFIRMATION") { node("Bestand ersetzen?") != null }
            click("Bestand ersetzen")
            waitUntil("MEDIA_UI_RESTORE_REQUESTED", 45000) {
                node("Wiederherstellung vorgemerkt") != null &&
                    BackupRestoreService.pendingStatus(context).optBoolean("restart_required")
            }
            val pending = BackupRestoreService.pendingStatus(context)
            yes(
                pending.getString("phase") == "RESTART_REQUIRED" &&
                    pending.getString("restore_id") == restoreId &&
                    pending.getString("preview_hash") == previewHash &&
                    pending.getBoolean("restart_required") &&
                    pending.getBoolean("current_state_unchanged"),
                "MEDIA_UI_REAL_CONFIRMATION_PENDING_RESTART",
            )
            yes(
                BackupRestoreService(context).status(restoreId).getBoolean("restart_required"),
                "MEDIA_UI_REAL_REQUEST_ACTIVATION_READBACK",
            )
            // No ordinary store is opened and no activatePending/startup guard is touched here.
            yes(mediaOriginalHash() == expectedHash, "MEDIA_UI_PENDING_NO_BUSINESS_MUTATION")
            mediaScreenshot("pending")
        } finally {
            test.removeMonitor(monitor)
            if (restoreId.isNotEmpty()) {
                val pending = BackupRestoreService.pendingStatus(context)
                if (
                    pending.optString("restore_id") == restoreId &&
                        pending.optBoolean("restart_required")
                ) {
                    // Ordinary cancellation cleans up only this test's request, before any swap.
                    val cancelled =
                        BackupRestoreService(context)
                            .cancelActivation("qa_media_ui_cleanup_$token", restoreId, previewHash)
                    yes(
                        cancelled.getString("phase") == "CANCELLED" &&
                            !cancelled.getBoolean("restart_required") &&
                            cancelled.getBoolean("current_state_unchanged"),
                        "MEDIA_UI_OWN_PENDING_REQUEST_CANCELLED",
                    )
                }
                BackupRestoreService.discardPreview(context, restoreId)
            }
            activity?.let { value -> test.runOnMainSync { value.finish() } }
        }
    }

    /** Inspect originals without opening a normal store after the activation request. */
    private fun mediaOriginalHash(): String =
        android.database.sqlite.SQLiteDatabase.openDatabase(
                context.getDatabasePath("haus-garten-mobile.db").path,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            )
            .use { database ->
                val initial =
                    context.assets.open("initial-data.json").bufferedReader().use { it.readText() }
                val catalog =
                    context.assets.open("service-catalog.json").bufferedReader().use {
                        it.readText()
                    }
                val source =
                    SnapshotProjection.effective(
                        BackupSnapshotState.read(database, JSONObject(), initial, catalog)
                    )
                val originals = JSONObject()
                for (name in
                    listOf(
                        "customers",
                        "orders",
                        "worklogs",
                        "photos",
                        "invoices",
                        "offers",
                    )) originals.put(name, source.optJSONArray(name) ?: org.json.JSONArray())
                PixelMcpWriteTools.hash(originals)
            }

    private fun mediaScreenshot(stage: String) {
        val bitmap = requireNotNull(automation.takeScreenshot())
        try {
            File(
                    requireNotNull(context.getExternalFilesDir(null)),
                    "qa-native-backup-restore-$stage.png",
                )
                .outputStream()
                .use {
                    yes(
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it),
                        "MEDIA_UI_SCREENSHOT_$stage",
                    )
                }
        } finally {
            bitmap.recycle()
        }
    }

    /** Changes only the starting folder/title, never supplies a result URI or a grant. */
    private fun mediaPickerSave(folder: Uri, filename: String) {
        val monitor =
            object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action == Intent.ACTION_CREATE_DOCUMENT) {
                        intent.putExtra(
                            android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                            folder,
                        )
                        intent.putExtra(Intent.EXTRA_TITLE, filename)
                    }
                    return null
                }
            }
        test.addMonitor(monitor)
        automation.serviceInfo =
            automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
        var activity: Activity? = null
        try {
            shell("wm dismiss-keyguard")
            activity =
                test.startActivitySync(
                    Intent(context, FullBackupActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            waitUntil("MEDIA_BACKUP_ACTIVITY") { node("Jetzt sichern") != null }
            click("Jetzt sichern")
            waitUntil("MEDIA_REAL_CREATE_PICKER") { pickerVisible() }
            waitUntil("MEDIA_CREATE_SAVE_BUTTON") {
                find(automation.rootInActiveWindow) {
                    it.isVisibleToUser &&
                        it.isEnabled &&
                        (it.viewIdResourceName?.endsWith(":id/button1") == true ||
                            it.text?.toString()?.equals("Save", true) == true)
                } != null
            }
            val save =
                requireNotNull(
                    find(automation.rootInActiveWindow) {
                        it.isVisibleToUser &&
                            it.isEnabled &&
                            (it.viewIdResourceName?.endsWith(":id/button1") == true ||
                                it.text?.toString()?.equals("Save", true) == true)
                    }
                )
            yes(
                save.performAction(AccessibilityNodeInfo.ACTION_CLICK),
                "MEDIA_SYSTEM_PICKER_CONFIRM",
            )
            waitUntil("MEDIA_FIRST_BACKUP_VERIFIED", 45000) {
                node("Gespeichert und geprüft") != null && node("ZIP geprüft.") != null
            }
        } finally {
            test.removeMonitor(monitor)
            activity?.let { value -> test.runOnMainSync { value.finish() } }
        }
    }

    private fun isolatedMediaRestore(uri: Uri, expected: JSONObject, token: String) {
        val root = File(context.cacheDir, "qa-media-restore-$token")
        yes(!root.exists() && root.mkdir(), "MEDIA_NEW_ISOLATED_RESTORE")
        val isolated = MediaRestoreContext(context, root, "qa_media_$token")
        try {
            val restore = BackupRestoreService(isolated)
            val preview = restore.preview("qa_media_preview_$token", uri)
            yes(preview.getString("phase") == "VALIDATED", "MEDIA_REAL_ZIP_PREVIEW")
            restore.requestActivation(
                "qa_media_activate_$token",
                preview.getString("restore_id"),
                preview.getString("preview_hash"),
                true,
            )
            yes(
                BackupRestoreService.activatePending(isolated, RestoreStartupProtection(isolated))
                    .getString("phase") == "ACTIVE",
                "MEDIA_ISOLATED_RESTORE_ACTIVE",
            )
            yes(
                RestoreStartupProtection.requiresColdRestart(isolated),
                "MEDIA_RESTORE_COLD_START_GUARD_RETAINED",
            )
            // Inspect only the actually activated synthetic files. Ordinary app access remains
            // blocked until cold restart; do not delete or bypass its restore guard.
            android.database.sqlite.SQLiteDatabase.openDatabase(
                    isolated.getDatabasePath("haus-garten-mobile.db").path,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
                )
                .use { database ->
                    val initial =
                        isolated.assets.open("initial-data.json").bufferedReader().use {
                            it.readText()
                        }
                    val catalog =
                        isolated.assets.open("service-catalog.json").bufferedReader().use {
                            it.readText()
                        }
                    val source =
                        SnapshotProjection.effective(
                            BackupSnapshotState.read(database, JSONObject(), initial, catalog)
                        )
                    val originals = JSONObject()
                    for (name in
                        listOf(
                            "customers",
                            "orders",
                            "worklogs",
                            "photos",
                            "invoices",
                            "offers",
                        )) originals.put(name, source.optJSONArray(name) ?: org.json.JSONArray())
                    yes(
                        PixelMcpWriteTools.hash(originals) == expected.getString("source_hash"),
                        "MEDIA_RESTORED_ALL_ORIGINAL_COLLECTIONS",
                    )
                    val work =
                        PixelMcpWriteTools.find(
                            source.getJSONArray("worklogs"),
                            expected.getString("retained_work"),
                        )
                    yes(
                        work.getString("notes") == "[TEST] QA changed" &&
                            SettlementRules.rows(source.getJSONArray("worklogs")).none {
                                it.optString("id") == expected.getString("removed_work")
                            },
                        "MEDIA_RESTORED_CHANGE_AND_DELETION",
                    )
                    for ((id, hash) in
                        listOf(
                            "retained_photo" to "retained_photo_sha256",
                            "added_photo" to "added_photo_sha256",
                        )) {
                        val metadata =
                            BackupSnapshotState.photo(database, expected.getString(id))
                                ?: throw IOException("QA_MEDIA_RESTORED_PHOTO_MISSING")
                        yes(
                            PhotoAttachmentStore.sha256(
                                PhotoAttachmentStore(isolated).read(metadata)
                            ) == expected.getString(hash),
                            "MEDIA_RESTORED_PHOTO_$id",
                        )
                    }
                    yes(database.version == 7, "MEDIA_RESTORED_SCHEMA_7")
                }
        } finally {
            isolated.clearPreferences()
            yes(
                root.canonicalFile.parentFile == context.cacheDir.canonicalFile &&
                    root.deleteRecursively(),
                "MEDIA_OWN_RESTORE_FIXTURE_REMOVED",
            )
        }
    }

    private class MediaRestoreContext(
        private val owner: Context,
        private val root: File,
        private val prefix: String,
    ) : android.content.ContextWrapper(owner) {
        private val preferences = mutableSetOf<String>()

        override fun getApplicationContext(): Context = this

        override fun getDataDir(): File = directory("")

        override fun getFilesDir(): File = directory("files")

        override fun getNoBackupFilesDir(): File = directory("no_backup")

        override fun getCacheDir(): File = directory("cache")

        override fun getDatabasePath(name: String): File {
            require(name == "haus-garten-mobile.db")
            return File(directory("databases"), name)
        }

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: android.database.sqlite.SQLiteDatabase.CursorFactory?,
        ) = openOrCreateDatabase(name, mode, factory, null)

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: android.database.sqlite.SQLiteDatabase.CursorFactory?,
            handler: android.database.DatabaseErrorHandler?,
        ): android.database.sqlite.SQLiteDatabase =
            android.database.sqlite.SQLiteDatabase.openDatabase(
                getDatabasePath(name).path,
                factory,
                android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY,
                handler,
            )

        override fun getSharedPreferences(
            name: String,
            mode: Int,
        ): android.content.SharedPreferences {
            require(name.matches(Regex("[A-Za-z0-9_]+")))
            val scoped = "${prefix}_$name"
            preferences.add(scoped)
            return owner.getSharedPreferences(scoped, mode)
        }

        fun clearPreferences() {
            for (name in preferences) owner.deleteSharedPreferences(name)
        }

        private fun directory(name: String): File =
            (if (name.isEmpty()) root else File(root, name)).also {
                if (!it.isDirectory && !it.mkdirs())
                    throw IOException("QA_MEDIA_RESTORE_FIXTURE_DIRECTORY")
            }
    }

    private fun execute(reopen: Boolean): JSONObject {
        // Verify the test APK and target package before inspecting any fixture contents.
        yes(test.context.packageName == "at.hausgarten.service.test", "TEST_APK_REQUIRED")
        yes(context.packageName == "at.hausgarten.service", "ORIGINAL_TARGET_PACKAGE_REQUIRED")
        yes(!context.isDeviceProtectedStorage, "SEPARATE_CREDENTIAL_FIXTURE")
        if (reopen) return reopen()
        val files = context.filesDir
        val noBackup = context.noBackupFilesDir
        val entries = files.listFiles()
        val noBackupEntries = noBackup.listFiles()
        val updatedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        val onlyProfileMetadata =
            !java.nio.file.Files.isSymbolicLink(files.toPath()) &&
                entries != null &&
                entries.all { verifiedProfileMetadata(files, it, updatedAt) }
        val noBackupEmpty =
            !java.nio.file.Files.isSymbolicLink(noBackup.toPath()) &&
                noBackupEntries?.isEmpty() == true
        val nonemptyPreferences =
            BackupPreferences.GROUPS.filter {
                context.getSharedPreferences(it, Context.MODE_PRIVATE).all.isNotEmpty()
            }
        val databaseExists = context.getDatabasePath("haus-garten-mobile.db").exists()
        if (
            marker.exists() ||
                databaseExists ||
                !onlyProfileMetadata ||
                !noBackupEmpty ||
                nonemptyPreferences.isNotEmpty()
        ) {
            val details =
                JSONObject()
                    .put("marker_exists", marker.exists())
                    .put("database_exists", databaseExists)
                    .put(
                        "files",
                        org.json.JSONArray(entries?.map { it.name } ?: emptyList<String>()),
                    )
                    .put("files_readable", entries != null)
                    .put("only_verified_profile_metadata", onlyProfileMetadata)
                    .put(
                        "no_backup",
                        org.json.JSONArray(noBackupEntries?.map { it.name } ?: emptyList<String>()),
                    )
                    .put("no_backup_empty", noBackupEmpty)
                    .put("nonempty_preferences", org.json.JSONArray(nonemptyPreferences))
            throw IOException("FRESH_SYNTHETIC_PROVIDER_APP_SANDBOX_REQUIRED: $details")
        }
        control("prepare") // Refuses a nonempty provider fixture; never clears existing data.
        val remote = control("stats")
        yes(
            remote.getInt("pid") != android.os.Process.myPid() &&
                remote.getInt("uid") != android.os.Process.myUid(),
            "REAL_REMOTE_PROVIDER_UID_AND_PROCESS",
        )
        marker.writeText(
            JSONObject()
                .put("fixture", "synthetic-provider-app-v1")
                .put("pid", android.os.Process.myPid())
                .toString()
        )
        val snapshot = SyntheticBusinessData.profile("minimal").toString()
        yes(
            context
                .getSharedPreferences("haus_garten", Context.MODE_PRIVATE)
                .edit()
                .putString("drive_snapshot", snapshot)
                .commit(),
            "SYNTHETIC_SNAPSHOT",
        )
        File(context.filesDir, "qa-provider-payload.bin")
            .writeBytes(ByteArray(196608).also { Random(87).nextBytes(it) })

        val success = create()
        var targetControlRejected = false
        try {
            context.contentResolver.call(
                SyntheticBackupDocumentsProvider.CONTROL_URI,
                SyntheticBackupDocumentsProvider.CONTROL + "shell",
                null,
                null,
            )
        } catch (failure: SecurityException) {
            targetControlRejected =
                failure.message?.contains("QA_CONTROL_REQUIRES_ACTUAL_SHELL_UID") == true
        }
        yes(targetControlRejected, "TARGET_DOCUMENT_GRANT_CANNOT_CONTROL_FIXTURE")
        val request = "qa_provider_save_001"
        PixelLocalStore(context).use { store ->
            store.putOwnerValue("qa_provider_marker", "QA synthetic provider fixture")
            val service = PixelBackupService(context, store)
            val result = service.save(request, uri(success)) {}
            yes(
                result.getBoolean("verified") && !result.getBoolean("cloud_confirmed"),
                "REAL_PROVIDER_SAVE_READBACK",
            )
            yes(
                documentStats(success).getInt("writes") == 1 &&
                    documentStats(success).getInt("reads") == 2,
                "REMOTE_PROVIDER_OPENS",
            )
            val good = store.ownerValue("pixel_backup_success", "")
            val denied = create(false)
            rejects("MISSING_WRITE_GRANT") {
                service.save("qa_provider_denied_001", uri(denied)) {}
            }
            yes(documentStats(denied).getInt("writes") == 0, "DENIED_BEFORE_PROVIDER_WRITE")
            control("grant", denied, Bundle().apply { putBoolean("read_only", true) })
            rejects("READ_GRANT_CANNOT_WRITE") {
                service.save("qa_provider_readonly_001", uri(denied)) {}
            }
            yes(documentStats(denied).getInt("writes") == 0, "READ_ONLY_GRANT_ENFORCED")
            for (mode in listOf("WRITE_OPEN", "WRITE_STREAM", "READ_OPEN", "READ_TRUNCATED")) {
                val id = create()
                fault(id, mode)
                rejects(mode) { service.save("qa_provider_failure_$mode", uri(id)) {} }
                yes(
                    service.status(JSONObject()).getString("state") == "FAILED",
                    "FAILURE_STATUS_$mode",
                )
                yes(
                    store.ownerValue("pixel_backup_success", "") == good,
                    "PRIOR_VERIFIED_PROOF_$mode",
                )
                yes(
                    documentStats(id).getInt("writes") == if (mode == "READ_OPEN") 0 else 1,
                    "WRITE_ATTEMPT_$mode",
                )
                yes(
                    documentStats(id).getInt("reads") == if (mode == "READ_TRUNCATED") 2 else 1,
                    "READ_ATTEMPT_$mode",
                )
                yes(!File(context.noBackupFilesDir, "pixel-backup").exists(), "WORK_CLEANED_$mode")
            }
            service.save(request, uri(success)) {}
            yes(documentStats(success).getInt("writes") == 1, "RETRY_READS_WITHOUT_REWRITE")
            rejects("NO_ATOMIC_PROVIDER_STRATEGY") {
                service.save("qa_provider_update_blocked", uri(success)) {}
            }
            yes(documentStats(success).getInt("writes") == 1, "UNSAFE_UPDATE_DOES_NOT_TRUNCATE")
            yes(
                store.ownerValue("pixel_backup_success", "") == good,
                "UNSAFE_UPDATE_PRESERVES_VERIFIED_PROOF",
            )
            service.save(request, uri(success)) {}
        }
        val restore = BackupRestoreService(context)
        val deniedSource = create(false)
        rejects("MISSING_READ_GRANT") {
            restore.preview("qa_provider_preview_denied", uri(deniedSource))
        }
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
        yes(
            documentStats(revokedSource).getInt("reads") == readsBeforeRevocation,
            "REVOKED_BEFORE_PROVIDER_OPEN",
        )
        fault(success, "READ_OPEN")
        rejects("PREVIEW_READ_OPEN") {
            restore.preview("qa_provider_preview_read_error", uri(success))
        }
        fault(success, "READ_TRUNCATED")
        rejects("PREVIEW_SHORT_READ") { restore.preview("qa_provider_preview_short", uri(success)) }
        fault(success, "NONE")
        yes(
            restore.preview("qa_provider_preview_ok", uri(success)).getString("phase") ==
                "VALIDATED",
            "PROVIDER_RESTORE_PREVIEW",
        )

        // These actions navigate the actual Android picker. No result Intent is fabricated.
        val picked = pickerChecks()
        val document = documentStats(picked)
        val saved =
            PixelLocalStore(context).use { JSONObject(it.ownerValue("pixel_backup_success", "")) }
        yes(
            saved.getJSONObject("destination").getString("treeUri") == uri(picked).toString(),
            "PICKER_RESULT_REACHED_PRODUCTION_SAVE",
        )
        yes(
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri(picked) && it.isReadPermission && it.isWritePermission
            },
            "REAL_PICKER_PERSISTED_GRANT",
        )
        marker.writeText(
            JSONObject()
                .put("fixture", "synthetic-provider-app-v1")
                .put("pid", android.os.Process.myPid())
                .put("provider_pid", control("stats").getInt("pid"))
                .put("document_id", picked)
                .put("writes", document.getInt("writes"))
                .put("request_id", saved.getString("requestId"))
                .put("archive_sha256", saved.getJSONObject("archive").getString("sha256"))
                .put("snapshot_sha256", PixelMcpWriteTools.hash(snapshot))
                .toString()
        )
        return report(false)
    }

    private fun reopen(): JSONObject {
        yes(marker.isFile, "REOPEN_MARKER")
        val saved = JSONObject(marker.readText())
        yes(saved.getString("fixture") == "synthetic-provider-app-v1", "REOPEN_SYNTHETIC_IDENTITY")
        yes(saved.getInt("pid") != android.os.Process.myPid(), "NEW_TARGET_PROCESS")
        yes(saved.getInt("provider_pid") != control("stats").getInt("pid"), "NEW_PROVIDER_PROCESS")
        yes(
            PixelMcpWriteTools.hash(
                context
                    .getSharedPreferences("haus_garten", Context.MODE_PRIVATE)
                    .getString("drive_snapshot", "")
            ) == saved.getString("snapshot_sha256"),
            "REOPEN_SYNTHETIC_SNAPSHOT",
        )
        val id = saved.getString("document_id")
        yes(
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri(id) && it.isReadPermission && it.isWritePermission
            },
            "PERSISTED_PICKER_PERMISSION_AFTER_RESTART",
        )
        PixelLocalStore(context).use { store ->
            yes(
                store.ownerValue("qa_provider_marker", "") == "QA synthetic provider fixture",
                "REOPEN_DATABASE_MARKER",
            )
            val before = documentStats(id).getInt("reads")
            yes(
                PixelBackupService(context, store)
                    .save(saved.getString("request_id"), uri(id)) {}
                    .getBoolean("verified"),
                "REOPEN_REAL_PROVIDER_READBACK",
            )
            yes(documentStats(id).getInt("writes") == saved.getInt("writes"), "REOPEN_NO_REWRITE")
            yes(documentStats(id).getInt("reads") == before + 1, "REOPEN_PROVIDER_READ_OCCURRED")
            yes(
                JSONObject(store.ownerValue("pixel_backup_success", ""))
                    .getJSONObject("archive")
                    .getString("sha256") == saved.getString("archive_sha256"),
                "REOPEN_ARCHIVE_PROOF",
            )
        }
        yes(
            BackupRestoreService(context)
                .preview("qa_provider_preview_reopened", uri(id))
                .getString("phase") == "VALIDATED",
            "REOPEN_PROVIDER_PREVIEW",
        )
        return report(true)
    }

    private fun report(reopened: Boolean): JSONObject =
        JSONObject()
            .put("checks", checks)
            .put("synthetic_only", true)
            .put("productive_data_read", false)
            .put("real_document_provider_tested", true)
            .put("separate_uid_local_provider_tested", true)
            .put("android_file_picker_tested", !reopened)
            .put("picker_cancellation_tested", !reopened)
            .put("picker_activity_recreation_tested", !reopened)
            .put("new_process_readback", reopened)
            .put("provider_process_restart_tested", reopened)
            .put("drive_account_tested", false)

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
            if (!label.isNullOrBlank())
                out.add(
                    label +
                        (if (node.isEnabled) "" else "[disabled]") +
                        (if (node.isVisibleToUser) "" else "[hidden]")
                )
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(automation.rootInActiveWindow)
        return out.joinToString(" | ")
    }

    private fun find(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), predicate)?.let {
            return it
        }
        return null
    }

    private fun node(label: String): AccessibilityNodeInfo? =
        find(automation.rootInActiveWindow) {
            it.isVisibleToUser &&
                (it.text?.toString() == label || it.contentDescription?.toString() == label)
        }

    private fun actionableAncestor(
        node: AccessibilityNodeInfo,
        boundary: AccessibilityNodeInfo,
    ): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null && current != boundary) {
            if (
                current.isVisibleToUser &&
                    current.isEnabled &&
                    (current.isClickable ||
                        current.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK })
            )
                return current
            current = current.parent
        }
        return null
    }

    private fun click(
        label: String,
        scope: () -> AccessibilityNodeInfo? = { automation.rootInActiveWindow },
    ) {
        waitUntil("CLICK_$label") {
            val root = scope() ?: return@waitUntil false
            val matches: (AccessibilityNodeInfo) -> Boolean = {
                it.text?.toString()?.trim() == label ||
                    it.contentDescription?.toString()?.trim() == label
            }
            val match = find(root) { matches(it) && it.isVisibleToUser }
            if (match == null) {
                // Compose does not expose nodes outside the viewport; scroll until the label
                // appears.
                val hidden = find(root, matches)
                if (hidden != null)
                    hidden.performAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id
                    )
                else
                    find(root) { it.isScrollable }
                        ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                return@waitUntil false
            }
            val target = actionableAncestor(match, root)
            if (
                target != null &&
                    target.refresh() &&
                    target.isVisibleToUser &&
                    target.isEnabled &&
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            ) {
                return@waitUntil true
            }
            // DocumentsUI occasionally exposes a visible root row without ACTION_CLICK
            // on its accessible descendants. Use a real injected touch, not a fabricated URI.
            if (!pickerVisible() || !match.isEnabled) return@waitUntil false
            val bounds = android.graphics.Rect()
            match.getBoundsInScreen(bounds)
            if (bounds.isEmpty) return@waitUntil false
            val downAt = SystemClock.uptimeMillis()
            val down =
                android.view.MotionEvent.obtain(
                    downAt,
                    downAt,
                    android.view.MotionEvent.ACTION_DOWN,
                    bounds.exactCenterX(),
                    bounds.exactCenterY(),
                    0,
                )
            val up =
                android.view.MotionEvent.obtain(
                    downAt,
                    downAt + 80,
                    android.view.MotionEvent.ACTION_UP,
                    bounds.exactCenterX(),
                    bounds.exactCenterY(),
                    0,
                )
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
            yes(
                automation.injectInputEvent(
                    KeyEvent(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
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
                "BACK_EVENT",
            )
        }
    }

    private fun pickerVisible(): Boolean =
        automation.rootInActiveWindow?.packageName?.toString()?.let {
            it == "com.android.documentsui" || it == "com.google.android.documentsui"
        } == true

    private fun chooseSyntheticRoot() {
        waitUntil("DOCUMENTS_UI") { pickerVisible() }
        // A remembered root also appears in the breadcrumb. Only the actual roots-list row
        // opens a provider; the breadcrumb must never decide whether the drawer is open.
        fun rootsList(): AccessibilityNodeInfo? =
            find(automation.rootInActiveWindow) {
                it.isVisibleToUser && it.viewIdResourceName?.endsWith(":id/roots_list") == true
            }
        if (rootsList() == null) click("Show roots")
        waitUntil("ROOTS_DRAWER_VISIBLE") { rootsList() != null }
        click(SyntheticBackupDocumentsProvider.ROOT_LABEL, ::rootsList)
        waitUntil("ROOT_SELECTED") {
            rootsList() == null &&
                pickerVisible() &&
                node(SyntheticBackupDocumentsProvider.ROOT_LABEL) != null
        }
    }

    private fun pickerChecks(): String {
        var activity: Activity? = null
        val originalAllFilesAccess = Environment.isExternalStorageManager()
        try {
            setAllFilesAccess(true)
            automation.serviceInfo =
                automation.serviceInfo.apply {
                    flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                }
            shell("wm dismiss-keyguard")
            activity =
                test.startActivitySync(
                    Intent(context, FullBackupActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            waitUntil("BACKUP_ACTIVITY") { node("Jetzt sichern") != null }
            val before = stats().toString()
            val beforeRegrant = Bundle()
            test.runOnMainSync {
                test.callActivityOnSaveInstanceState(requireNotNull(activity), beforeRegrant)
            }
            click("Sicherungsdatei erneut wählen")
            waitUntil("EXISTING_BACKUP_PICKER_CANCEL") { pickerVisible() }
            back()
            waitUntil("EXISTING_BACKUP_CANCEL_RETURNED") {
                !pickerVisible() && node("Jetzt sichern") != null
            }
            val afterRegrant = Bundle()
            test.runOnMainSync {
                test.callActivityOnSaveInstanceState(requireNotNull(activity), afterRegrant)
            }
            yes(
                afterRegrant.getString("target") == beforeRegrant.getString("target") &&
                    afterRegrant.getString("request") == beforeRegrant.getString("request") &&
                    stats().toString() == before,
                "EXISTING_BACKUP_PICKER_CANCEL_PRESERVES_TARGET_AND_REQUEST",
            )
            click("Anderen Speicherort wählen")
            waitUntil("CREATE_PICKER_CANCEL") { pickerVisible() }
            back()
            waitUntil("CREATE_CANCEL_RETURNED") { node("Jetzt sichern") != null }
            yes(stats().toString() == before, "CREATE_CANCEL_NO_PROVIDER_MUTATION")
            val state = Bundle()
            test.runOnMainSync {
                test.callActivityOnSaveInstanceState(requireNotNull(activity), state)
            }
            yes(
                state.getString("target", "").isNotEmpty(),
                "CREATE_CANCEL_PRESERVES_PREVIOUS_TARGET",
            )
            click("Sicherung wiederherstellen")
            waitUntil("OPEN_PICKER_CANCEL") { pickerVisible() }
            back()
            waitUntil("OPEN_CANCEL_RETURNED") { !pickerVisible() && node("Jetzt sichern") != null }
            val canceled = Bundle()
            test.runOnMainSync {
                test.callActivityOnSaveInstanceState(requireNotNull(activity), canceled)
            }
            yes(canceled.getString("restore_uri", "").isEmpty(), "OPEN_CANCEL_NO_SOURCE")
            yes(stats().toString() == before, "OPEN_CANCEL_NO_PROVIDER_MUTATION")

            click("Anderen Speicherort wählen")
            chooseSyntheticRoot()
            waitUntil("SYSTEM_CREATE_SAVE_BUTTON") {
                find(automation.rootInActiveWindow) {
                    it.isVisibleToUser &&
                        it.isEnabled &&
                        (it.viewIdResourceName?.endsWith(":id/button1") == true ||
                            it.text?.toString()?.equals("Save", ignoreCase = true) == true)
                } != null
            }
            val save =
                requireNotNull(
                    find(automation.rootInActiveWindow) {
                        it.isVisibleToUser &&
                            it.isEnabled &&
                            (it.viewIdResourceName?.endsWith(":id/button1") == true ||
                                it.text?.toString()?.equals("Save", ignoreCase = true) == true)
                    }
                )
            yes(save.performAction(AccessibilityNodeInfo.ACTION_CLICK), "SYSTEM_CREATE_CONFIRM")
            waitUntil("PRODUCTION_SAVE_VERIFIED", 45000) {
                node("Gespeichert und geprüft") != null && node("ZIP geprüft.") != null
            }
            val id = stats().getString("last_created")
            yes(
                documentStats(id).getInt("writes") == 1 && documentStats(id).getInt("reads") == 2,
                "UI_PROVIDER_SAVE_READBACK",
            )
            click("Sicherung wiederherstellen")
            chooseSyntheticRoot()
            click(documentStats(id).getString("title"))
            waitUntil("OPEN_RETURNED") { node("Datei prüfen") != null }
            val opened = Bundle()
            test.runOnMainSync {
                test.callActivityOnSaveInstanceState(requireNotNull(activity), opened)
            }
            yes(opened.getString("restore_uri") == uri(id).toString(), "SYSTEM_OPEN_RESULT_URI")
            val monitor = test.addMonitor(FullBackupActivity::class.java.name, null, false)
            try {
                val previous = requireNotNull(activity)
                test.runOnMainSync { previous.recreate() }
                activity =
                    monitor.waitForActivityWithTimeout(20000)
                        ?: throw AssertionError("QA_PICKER_RECREATE_FAILED")
                yes(activity !== previous, "REAL_ACTIVITY_RECREATED")
                waitUntil("RECREATED_SOURCE") { node("Datei prüfen") != null }
                val recreated = Bundle()
                test.runOnMainSync {
                    test.callActivityOnSaveInstanceState(requireNotNull(activity), recreated)
                }
                yes(
                    recreated.getString("restore_uri") == uri(id).toString(),
                    "RECREATE_PRESERVES_PICKED_URI",
                )
                yes(
                    recreated.keySet().none { it.contains("recovery_code") },
                    "RECREATE_NO_RECOVERY_CODE",
                )
            } finally {
                test.removeMonitor(monitor)
            }
            click("Datei prüfen")
            waitUntil("PRODUCTION_PREVIEW_VALIDATED", 45000) {
                find(automation.rootInActiveWindow) {
                    it.text?.toString()?.startsWith("Vorschau bereit.") == true
                } != null
            }
            yes(documentStats(id).getInt("reads") == 3, "UI_PROVIDER_RESTORE_READ")
            return id
        } finally {
            activity?.let { value -> test.runOnMainSync { value.finish() } }
            setAllFilesAccess(originalAllFilesAccess)
        }
    }
}

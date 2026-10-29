package at.hausgarten.service

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONObject

/** Real legacy writer/new reader and new writer/legacy reader; only helper-generated fixtures. */
object BackupFormatCompatibilityTests {
    private const val SPACE = 128L * 1024 * 1024
    private var checks = 0
    private val legacy by lazy { Class.forName("at.hausgarten.service.LegacyBackupArchive") }

    private fun yes(value: Boolean, message: String) {
        checks++
        check(value) { "Archive compatibility check $checks: $message" }
    }

    private fun invokeLegacy(name: String, types: Array<Class<*>>, vararg values: Any?): Any? {
        try {
            return legacy.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(null, *values)
        } catch (wrapped: InvocationTargetException) {
            throw wrapped.targetException
        }
    }

    private fun legacySources(data: File, paths: List<String>): List<Any> {
        val constructor = Class.forName("at.hausgarten.service.LegacyBackupArchive\$Source")
            .getDeclaredConstructor(String::class.java, File::class.java).apply { isAccessible = true }
        return paths.map { constructor.newInstance(it, File(data, it)) }
    }

    private fun legacyWrite(target: File, metadata: JSONObject, data: File, paths: List<String>): JSONObject =
        FileOutputStream(target).use { output ->
            invokeLegacy(
                "writePlain", arrayOf(OutputStream::class.java, JSONObject::class.java, List::class.java),
                output, metadata, legacySources(data, paths)
            ) as JSONObject
        }

    private fun legacyRead(input: InputStream, target: File, schema: Int, space: Long = SPACE): AutoCloseable =
        invokeLegacy(
            "verifyArchive",
            arrayOf(InputStream::class.java, String::class.java, File::class.java, Long::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
            input, "", target, space, schema
        ) as AutoCloseable

    private fun paths(metadata: JSONObject): List<String> {
        val list = metadata.getJSONArray("fixturePaths")
        return (0 until list.length()).map(list::getString)
    }

    private fun checkFiles(expected: File, actual: File, paths: List<String>) {
        yes(actual.walkTopDown().filter { it.isFile }.map { it.relativeTo(actual).invariantSeparatorsPath }.toSet() == paths.toSet(), "complete file set")
        for (path in paths) {
            yes(File(expected, path).readBytes().contentEquals(File(actual, path).readBytes()), "byte preservation for $path")
        }
    }

    private fun checkZip(archive: File, paths: List<String>) {
        val magic = archive.inputStream().use { input -> ByteArray(4).also { yes(input.read(it) == 4, "ZIP signature readable") } }
        yes(magic.contentEquals(byteArrayOf(80, 75, 3, 4)), "ordinary ZIP without encryption wrapper")
        ZipFile(archive).use { zip ->
            yes(zip.entries().asSequence().map { it.name }.toSet() == paths.map { "data/$it" }.toSet() + "manifest.json", "ZIP entry contract")
            val manifest = JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).bufferedReader().use { it.readText() })
            yes(manifest.getInt("formatVersion") == 1, "formatVersion remains 1")
            yes(manifest.getString("archiveProtection") == "NONE", "no new encryption")
            yes(manifest.getString("datasetFormat") == "pixel.snapshot.v2", "existing dataset contract")
            yes(manifest.getString("applicationId") == "at.hausgarten.service", "update package identity")
            yes(manifest.getString("completion") == "COMPLETE", "complete commit marker")
            var total = 0L
            val files = manifest.getJSONArray("files")
            for (i in 0 until files.length()) {
                val row = files.getJSONObject(i)
                val entry = zip.getEntry("data/" + row.getString("path"))
                yes(entry.size == row.getLong("size"), "entry size")
                total += entry.size
            }
            yes(total == manifest.getLong("totalBytes"), "total byte accounting")
        }
    }

    private fun roundTrip(root: File, schema: Int): File {
        val fixture = File(root, "schema_$schema")
        val data = File(fixture, "data")
        val metadata = JSONObject(File(fixture, "metadata.json").readText())
        val paths = paths(metadata)
        metadata.remove("fixturePaths")
        val unchanged = paths.associateWith { EncryptedBackupArchive.sha256(File(data, it)) }
        val legacyZip = File(fixture, "legacy.zip")
        val kotlinZip = File(fixture, "kotlin.zip")
        val oldManifest = legacyWrite(legacyZip, metadata, data, paths)
        val newManifest = FileOutputStream(kotlinZip).use { output ->
            EncryptedBackupArchive.writePlain(output, metadata, paths.map { EncryptedBackupArchive.Source(it, File(data, it)) })
        }
        yes(oldManifest.similar(newManifest), "manifest equality across implementations, schema $schema")
        checkZip(legacyZip, paths)
        checkZip(kotlinZip, paths)

        val fromLegacy = File(fixture, "from_legacy")
        EncryptedBackupArchive.verifyArchive(FileInputStream(legacyZip), "", fromLegacy, SPACE, 7).use { verified ->
            yes(verified.manifest.similar(oldManifest), "legacy manifest read by Kotlin")
            checkFiles(data, File(verified.directory, "data"), paths)
            Files.copy(verified.file("database.sqlite").toPath(), File(fixture, "legacy_to_kotlin.sqlite").toPath())
        }
        yes(!fromLegacy.exists(), "Kotlin staging cleanup")

        val fromKotlin = File(fixture, "from_kotlin")
        legacyRead(FileInputStream(kotlinZip), fromKotlin, 7).use { verified ->
            val type = verified.javaClass
            val actualManifest = type.getDeclaredField("manifest").apply { isAccessible = true }.get(verified) as JSONObject
            yes(actualManifest.similar(newManifest), "Kotlin manifest read by legacy Java")
            checkFiles(data, File(fromKotlin, "data"), paths)
            Files.copy(File(fromKotlin, "data/database.sqlite").toPath(), File(fixture, "kotlin_to_legacy.sqlite").toPath())
        }
        yes(!fromKotlin.exists(), "legacy staging cleanup")
        for ((path, hash) in unchanged) yes(EncryptedBackupArchive.sha256(File(data, path)) == hash, "fixture source unchanged")
        return legacyZip
    }

    /** Rewrite valid ZIP structure so failures test the production manifest/hash validation. */
    private fun changedZip(source: File, target: File, manifestChange: (JSONObject) -> Unit = {},
                           entryChange: (String, ByteArray) -> Pair<String, ByteArray>? = { name, bytes -> name to bytes }) {
        ZipFile(source).use { old ->
            ZipOutputStream(FileOutputStream(target)).use { output ->
                for (entry in old.entries().asSequence()) {
                    val original = old.getInputStream(entry).use { it.readBytes() }
                    val changed = if (entry.name == "manifest.json") {
                        entry.name to JSONObject(original.toString(Charsets.UTF_8)).also(manifestChange).toString().toByteArray()
                    } else entryChange(entry.name, original)
                    if (changed != null) {
                        output.putNextEntry(ZipEntry(changed.first))
                        output.write(changed.second)
                        output.closeEntry()
                    }
                }
            }
        }
    }

    private fun rejectBoth(archive: File, root: File, label: String, expected: String? = null, space: Long = SPACE, interrupted: Boolean = false) {
        val before = EncryptedBackupArchive.sha256(archive)
        for (old in listOf(false, true)) {
            val target = File(root, "reject_${label}_${if (old) "old" else "new"}")
            var failure: Throwable? = null
            try {
                if (interrupted) Thread.currentThread().interrupt()
                val verified: AutoCloseable = if (old) legacyRead(FileInputStream(archive), target, 7, space)
                    else EncryptedBackupArchive.verifyArchive(FileInputStream(archive), "", target, space, 7)
                verified.close()
            } catch (caught: Exception) {
                failure = caught
            } finally {
                Thread.interrupted()
            }
            yes(failure != null, "$label rejected by ${if (old) "legacy" else "Kotlin"}")
            if (expected != null) yes(failure?.message == expected, "$label preserves rejection code")
            yes(!target.exists(), "$label leaves no activated/staged dataset")
            yes(EncryptedBackupArchive.sha256(archive) == before, "$label preserves source ZIP")
        }
    }

    private fun failures(root: File, source: File) {
        val cases = File(root, "invalid").apply { check(mkdir()) }
        val futureSchema = File(cases, "future_schema.zip")
        changedZip(source, futureSchema, { it.put("schemaVersion", 8) })
        rejectBoth(futureSchema, cases, "future_schema", "BACKUP_VERSION_UNSUPPORTED")
        val futureFormat = File(cases, "future_format.zip")
        changedZip(source, futureFormat, { it.put("formatVersion", 2) })
        rejectBoth(futureFormat, cases, "future_format", "BACKUP_VERSION_UNSUPPORTED")
        val wrongApp = File(cases, "wrong_app.zip")
        changedZip(source, wrongApp, { it.put("applicationId", "qa.unrelated.app") })
        rejectBoth(wrongApp, cases, "wrong_app", "BACKUP_MANIFEST_INVALID")
        val incomplete = File(cases, "incomplete.zip")
        changedZip(source, incomplete, { it.put("completion", "WRITING") })
        rejectBoth(incomplete, cases, "incomplete", "BACKUP_COMMIT_MISSING")
        val traversal = File(cases, "traversal.zip")
        changedZip(source, traversal, { it.getJSONArray("files").getJSONObject(0).put("path", "../qa_escape") })
        rejectBoth(traversal, cases, "traversal", "BACKUP_PATH_INVALID")
        yes(!File(cases, "qa_escape").exists(), "no traversal output")
        val corruption = File(cases, "corruption.zip")
        changedZip(source, corruption, entryChange = { name, bytes ->
            name to if (name == "data/database.sqlite") bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() } else bytes
        })
        rejectBoth(corruption, cases, "corruption", "BACKUP_FILE_HASH_MISMATCH")
        val missing = File(cases, "missing.zip")
        changedZip(source, missing, entryChange = { name, bytes -> if (name == "data/database.sqlite") null else name to bytes })
        rejectBoth(missing, cases, "missing", "BACKUP_FILE_SET_INVALID")
        val truncated = File(cases, "truncated.zip")
        val raw = source.readBytes()
        truncated.writeBytes(raw.copyOf(raw.size - 17))
        rejectBoth(truncated, cases, "truncated")
        rejectBoth(source, cases, "space", space = 1)
        rejectBoth(source, cases, "cancelled", "BACKUP_INTERRUPTED", interrupted = true)
    }

    /** A provider can fail before a complete header exists; ownership already belongs to the reader. */
    private fun headerReadFailures(root: File) {
        for ((index, mode) in listOf(0 to 0, 2 to 0, 0 to 1, 2 to 1, 2 to 2).withIndex()) {
            val (prefixSize, closeMode) = mode
            val prefix = byteArrayOf(80, 75).copyOf(prefixSize)
            val before = prefix.copyOf()
            val failure = IOException("QA_HEADER_READ_FAILURE")
            val closeFailure = when (closeMode) {
                1 -> IOException("QA_HEADER_CLOSE_FAILURE")
                2 -> failure
                else -> null
            }
            var position = 0
            var closes = 0
            val input = object : InputStream() {
                override fun read(): Int {
                    if (position == prefix.size) throw failure
                    return prefix[position++].toInt() and 255
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (position == prefix.size) throw failure
                    val copied = minOf(length, prefix.size - position)
                    prefix.copyInto(buffer, offset, position, position + copied)
                    position += copied
                    return copied
                }

                override fun close() {
                    closes++
                    if (closeFailure != null) throw closeFailure
                }
            }
            val target = File(root, "header_failure_$index")
            val existing = root.list()!!.toSet()
            var caught: IOException? = null
            try {
                EncryptedBackupArchive.verifyArchive(input, "", target, SPACE, 7)
            } catch (expected: IOException) {
                caught = expected
            }
            yes(caught === failure, "header read exception remains primary")
            yes(closes == 1, "failed header closes provider exactly once")
            val suppressed = failure.suppressed
            yes(if (closeMode == 1) suppressed.size == 1 && suppressed[0] === closeFailure else suppressed.isEmpty(),
                "close failure is suppressed without self-suppression")
            yes(position == prefixSize && prefix.contentEquals(before), "partial source bytes unchanged")
            yes(!target.exists(), "header failure creates no staging")
            yes(root.list()!!.toSet() == existing, "header failure creates no files or directories")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "Pass the helper-generated synthetic fixture directory only" }
        val root = File(args[0]).canonicalFile
        require(root.name.startsWith("qa_backup_compat_")) { "Synthetic fixture directory required" }
        val marker = JSONObject(File(root, "fixture-spec.json").readText())
        require(marker.getString("format") == "hausgarten.synthetic.backup.compat.v1" && marker.getBoolean("synthetic_only"))
        roundTrip(root, 6)
        val current = roundTrip(root, 7)
        failures(root, current)
        headerReadFailures(root)
        println("PASS backup Java/Kotlin bidirectional format compatibility: $checks checks; SQLite6/7 byte preservation; synthetic only; no Android activation or customer ZIP tested")
    }
}

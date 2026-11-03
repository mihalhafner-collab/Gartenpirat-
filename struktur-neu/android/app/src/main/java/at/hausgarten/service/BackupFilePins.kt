package at.hausgarten.service

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/** Pins app-private immutable originals. Never deletes or changes the source files. */
object BackupFilePins {
    @JvmStatic
    @Throws(Exception::class)
    fun capture(sourceRoot: File, privateStage: File, prefix: String) = capture(sourceRoot, privateStage, prefix, false)

    @JvmStatic
    @Throws(Exception::class)
    fun pinImmutable(sourceRoot: File, privateStage: File, prefix: String) = capture(sourceRoot, privateStage, prefix, true)

    private fun capture(sourceRoot: File, privateStage: File, prefix: String, immutable: Boolean) {
        if (!sourceRoot.exists()) return
        if (Files.isSymbolicLink(sourceRoot.toPath()) || !sourceRoot.isDirectory) throw IOException("BACKUP_SOURCE_LINK_OR_DIRECTORY_INVALID")
        val children = sourceRoot.listFiles() ?: throw IOException("BACKUP_SOURCE_UNREADABLE")
        children.sortBy { it.name }
        for (source in children) {
            BackupArchive.checkCancelled()
            val path = "$prefix/${source.name}"
            BackupArchive.Source(path, source)
            if (Files.isSymbolicLink(source.toPath())) throw IOException("BACKUP_SOURCE_LINK_REJECTED")
            if (source.isDirectory) {
                capture(source, privateStage, path, immutable)
                continue
            }
            if (!source.isFile) throw IOException("BACKUP_SOURCE_UNREADABLE")
            val target = File(privateStage, path)
            if (!target.canonicalPath.startsWith(privateStage.canonicalPath + File.separator)) throw IOException("BACKUP_PATH_INVALID")
            if (target.exists()) continue
            val parent = target.parentFile!!
            if (!parent.isDirectory && !parent.mkdirs()) throw IOException("BACKUP_STAGING_UNAVAILABLE")
            if (!immutable) {
                val hash = BackupArchive.sha256(source)
                copy(source, target)
                if (hash != BackupArchive.sha256(source) || hash != BackupArchive.sha256(target)) throw IOException("BACKUP_SOURCE_CHANGED")
                continue
            }
            try {
                Files.createLink(target.toPath(), source.toPath())
            } catch (unsupported: UnsupportedOperationException) {
                // No hardlinks on this private filesystem: copy instead, never move.
                copy(source, target)
            } catch (unsupported: FileSystemException) {
                copy(source, target)
            }
        }
    }

    private fun copy(source: File, target: File) {
        val volume = target.parentFile!!
        BackupStorageBudget.require(volume, source.length())
        FileInputStream(source).use { input ->
            FileChannel.open(target.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                BackupArchive.copy(input, BackupStorageBudget.guard(Channels.newOutputStream(output), volume), null, source.length())
                output.force(true)
            }
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun sources(privateStage: File): List<BackupArchive.Source> {
        val result = ArrayList<BackupArchive.Source>()
        collect(privateStage, "", result)
        return result
    }

    /** Enumerate existing originals without copying or hardlinking their bytes. */
    fun directSources(root: File, prefix: String): List<BackupArchive.Source> {
        if (!root.exists()) return emptyList()
        if (Files.isSymbolicLink(root.toPath()) || !root.isDirectory) throw IOException("BACKUP_SOURCE_LINK_OR_DIRECTORY_INVALID")
        val result = ArrayList<BackupArchive.Source>()
        collect(root, "$prefix/", result)
        return result
    }

    private fun collect(directory: File, prefix: String, result: MutableList<BackupArchive.Source>) {
        val entries = directory.listFiles() ?: throw IOException("BACKUP_STAGING_UNREADABLE")
        entries.sortBy { it.name }
        for (file in entries) {
            if (Files.isSymbolicLink(file.toPath())) throw IOException("BACKUP_STAGING_LINK_REJECTED")
            val path = prefix + file.name
            when {
                file.isDirectory -> collect(file, "$path/", result)
                file.isFile -> result.add(BackupArchive.Source(path, file))
                else -> throw IOException("BACKUP_STAGING_UNREADABLE")
            }
        }
    }
}

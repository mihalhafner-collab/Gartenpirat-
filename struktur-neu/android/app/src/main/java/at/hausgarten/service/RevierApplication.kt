package at.hausgarten.service

import android.app.Application
import android.content.Context
import android.os.Looper
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Recovery owns the startup gate before any ordinary store can open. */
class RevierApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val completion = CompletableFuture<Unit>()
        startup = completion
        startupExecutor.execute {
            startupWorker.set(true)
            try {
                if (BackupRestoreService.requiresExclusiveStartup(this)) {
                    BackupRestoreService.activatePending(this, RestoreStartupProtection(this))
                }
                recoveryRequired = BackupRestoreService.requiresExclusiveStartup(this) ||
                    RestoreStartupProtection.requiresColdRestart(this)
            } catch (_: Exception) {
                recoveryRequired = true
            } finally {
                startupWorker.remove()
                completion.complete(Unit)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startupExecutor.execute {
            try { PixelBackupService.cleanupWork(this) } catch (failure: Exception) { android.util.Log.w("HausGartenStartup", "BACKUP_WORK_CLEANUP_FAILED; type=" + failure.javaClass.simpleName) }
            if (recoveryRequired) return@execute
            try { PixelBackupService.cancelRetiredJobs(this) } catch (failure: RuntimeException) { android.util.Log.w("HausGartenStartup", "RETIRED_JOBS_CANCEL_FAILED; type=" + failure.javaClass.simpleName) }
        }
    }

    companion object {
        @Volatile private var recoveryRequired = false
        @Volatile private var startup: CompletableFuture<Unit>? = null
        private val startupWorker = ThreadLocal.withInitial { false }
        private val startupExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "local-startup").apply { isDaemon = true }
        }

        @JvmStatic fun recoveryRequired(): Boolean = recoveryRequired

        /** Never wait on Android's UI thread or on the recovery worker's own completion. */
        @JvmStatic @JvmOverloads fun awaitStartup(context: Context, timeoutMillis: Long = 0) {
            if (context.applicationContext !is RevierApplication || startupWorker.get() == true) return
            val completion = startup ?: return
            if (!completion.isDone && Looper.myLooper() == Looper.getMainLooper()) {
                throw IllegalStateException("APPLICATION_STARTUP_PENDING")
            }
            try { if (timeoutMillis > 0) completion.get(timeoutMillis, TimeUnit.MILLISECONDS) else completion.get() }
            catch (_: TimeoutException) { throw IllegalStateException("APPLICATION_STARTUP_PENDING") }
            catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("APPLICATION_STARTUP_INTERRUPTED", interrupted)
            }
            if (recoveryRequired) throw IllegalStateException("RESTORE_RESTART_REQUIRED")
        }
    }
}

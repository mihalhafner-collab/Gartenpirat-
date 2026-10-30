package at.hausgarten.service

import android.app.job.*
import java.util.concurrent.*

/** Deferrable local-only document work, no foreground service or network constraint. */
class LocalInvoiceJobService : JobService() {
    private var task: Future<*>? = null

    override fun onStartJob(params: JobParameters): Boolean {
        if (
            !(LocalInvoiceJobs.storageScope(this))!!.equals(
                (params!!.getExtras())!!.getString("storage_scope", "")
            )
        ) {
            return false
        }
        task =
            EXECUTOR!!.submit(
                lambda@{
                    try {
                        PixelLocalStore(this).use { store ->
                            android.os.Process.setThreadPriority(
                                android.os.Process.THREAD_PRIORITY_BACKGROUND
                            )
                            LocalInvoiceJobs.run(this, store)
                        }
                    } catch (failure: Exception) {} finally {
                        jobFinished(params, false)
                    }
                }
            )
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        if ((task !== null)) {
            task!!.cancel(true)
        }
        return false
    }

    companion object {
        private val EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor()
    }
}

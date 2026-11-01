package at.hausgarten.service

import android.app.job.JobScheduler
import android.content.Context

/** Cancels stable retired job IDs; no upload service or retry is registered. */
internal class DriveSyncJob private constructor() {
    companion object {
        @JvmStatic
        fun install(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.let { jobs ->
                jobs.cancel(0x484701)
                jobs.cancel(0x484702)
            }
        }
    }
}

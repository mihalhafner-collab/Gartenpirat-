package at.hausgarten.service

import android.app.job.*
import android.content.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

/** Independent of the Drive executor; adaptive one-shot checks drive weather cadence, periodic work is only a persisted fallback. */
class PlanningJob : JobService() {
    private val tasks = ConcurrentHashMap<Int, Future<*>>()
    override fun onStartJob(params: JobParameters): Boolean {
        if (params.jobId == ONCE) {
            synchronized(PlanningJob::class.java) { activeOnce++ }
        }
        val retry = AtomicBoolean()
        val task = object : FutureTask<Void?>(Callable {
            try {
                RevierApplication.awaitStartup(this)
                if (params.jobId == ONCE) PlanningAccount.prefs(this).edit().remove("job_due_at").apply()
                PixelLocalStore(this).use { store -> if (DriveSyncCoordinator.hasNetwork(this)) AppointmentRuntime.refresh(this, store) else retry.set(true) }
            }
            catch (unavailable: RuntimeException) { retry.set(true) }
            null
        }) {
            override fun done() {
                tasks.remove(params.jobId, this)
                var next = Long.MAX_VALUE
                if (params.jobId == ONCE) synchronized(PlanningJob::class.java) { if (--activeOnce == 0) { next = followAt; followAt = Long.MAX_VALUE } }
                if (!isCancelled) jobFinished(params, retry.get())
                if (next != Long.MAX_VALUE) {
                    val nextAt = next
                    EXECUTOR.execute { schedule(applicationContext, maxOf(1000, nextAt - System.currentTimeMillis())) }
                }
            }
        }
        tasks[params.jobId] = task; EXECUTOR.execute(task)
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { tasks.remove(params.jobId)?.cancel(true); return true }
    companion object {
        const val PERIODIC = 0x484711
        const val ONCE = 0x484712
        private val EXECUTOR = Executors.newSingleThreadExecutor()
        private var activeOnce = 0
        private var followAt = Long.MAX_VALUE
        @JvmStatic fun install(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(PERIODIC) == null) scheduler.schedule(JobInfo.Builder(PERIODIC, ComponentName(context, PlanningJob::class.java)).setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(24 * 60 * 60 * 1000L).build())
        }
        @JvmStatic fun schedule(context: Context, delay: Long) = synchronized(PlanningJob::class.java) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return@synchronized
            try {
                PixelLocalStore(context).use { store ->
                    if (!AppointmentRuntime.calendarEnabled(context, store) && !AppointmentRuntime.flag(context, store, "weather_enabled")) {
                        scheduler.cancel(ONCE); scheduler.cancel(PERIODIC); PlanningAccount.prefs(context).edit().remove("job_due_at").apply()
                        return@synchronized
                    }
                }
            } catch (unavailable: Exception) { return@synchronized }
            install(context)
            val due = System.currentTimeMillis() + maxOf(1000, delay)
            val existing = PlanningAccount.prefs(context).getLong("job_due_at", Long.MAX_VALUE)
            // Replacing a running job ID asks Android to interrupt that same calendar check.
            if (activeOnce > 0) { followAt = minOf(followAt, due); return@synchronized }
            if (scheduler.getPendingJob(ONCE) != null && existing <= due && existing > System.currentTimeMillis()) return@synchronized
            val result = scheduler.schedule(JobInfo.Builder(ONCE, ComponentName(context, PlanningJob::class.java)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(maxOf(1000, delay)).setPersisted(true).setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
            if (result == JobScheduler.RESULT_SUCCESS) PlanningAccount.prefs(context).edit().putLong("job_due_at", due).apply()
        }
    }
}

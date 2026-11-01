package at.hausgarten.service

import java.time.Instant

/** Bounded request cadence, without permanently exhausting a calendar connection. */
internal object CalendarRecoveryRules {
    const val MIN_RETRY = 30000L
    const val MAX_RETRY = 3600000L
    @JvmStatic fun nextFailures(previous: Int): Int = if (previous >= 8) 8 else maxOf(0, previous) + 1
    @JvmStatic fun retryDelay(failures: Int, providerDelay: Long, jitter: Int): Long {
        val backoff = minOf(MAX_RETRY, MIN_RETRY * (1L shl failures.coerceIn(0, 7)))
        return maxOf(providerDelay, minOf(MAX_RETRY, backoff + jitter.coerceIn(0, 2999)))
    }
    @JvmStatic fun interrupted(error: Throwable?): Boolean {
        var cause = error
        repeat(8) {
            if (cause == null) return false
            if (cause is InterruptedException || cause is java.io.InterruptedIOException && cause !is java.net.SocketTimeoutException ||
                cause is java.util.concurrent.CancellationException || cause is HttpTransport.Failure && (cause as HttpTransport.Failure).code == "CALENDAR_INTERRUPTED") return true
            cause = cause?.cause
        }
        return false
    }
    @JvmStatic fun fresh(value: String?, now: Instant): Boolean = try {
        val checked = Instant.parse(value)
        !checked.isAfter(now.plusSeconds(300)) && !checked.isBefore(now.minusSeconds(1800))
    } catch (absent: Exception) { false }
}

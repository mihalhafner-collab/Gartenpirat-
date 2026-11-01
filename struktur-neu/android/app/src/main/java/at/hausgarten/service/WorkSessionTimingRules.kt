package at.hausgarten.service

/** Time evidence for one persistent segment. Never estimates across boots. */
class WorkSessionTimingRules private constructor() {
    companion object {
        @JvmField val CLOCK_TOLERANCE_MS = 120_000L
        @JvmField val REVIEW_DURATION_MS = 24L * 60 * 60 * 1000

        @JvmStatic
        fun reviewReason(
            wallStart: Long, elapsedStart: Long, bootStart: String?,
            wallNow: Long, elapsedNow: Long, bootNow: String?
        ): String {
            if (bootStart == "unverified_boot" || bootNow == "unverified_boot") return "CLOCK_EVIDENCE_UNAVAILABLE"
            if (bootStart.isNullOrEmpty() || bootStart != bootNow) return "DEVICE_RESTART"
            val elapsed = elapsedNow - elapsedStart
            val wall = wallNow - wallStart
            if (elapsed < 0 || wall < 0) return "CLOCK_REVERSED"
            if (Math.abs(wall - elapsed) > CLOCK_TOLERANCE_MS) return "CLOCK_CHANGED"
            if (elapsed > REVIEW_DURATION_MS) return "DURATION_REVIEW_REQUIRED"
            return ""
        }

        @JvmStatic
        fun verifiedDelta(
            wallStart: Long, elapsedStart: Long, bootStart: String?,
            wallNow: Long, elapsedNow: Long, bootNow: String?
        ): Long = if (reviewReason(wallStart, elapsedStart, bootStart, wallNow, elapsedNow, bootNow).isEmpty()) {
            elapsedNow - elapsedStart
        } else 0
    }
}

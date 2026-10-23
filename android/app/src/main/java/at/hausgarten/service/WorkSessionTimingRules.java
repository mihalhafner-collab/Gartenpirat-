package at.hausgarten.service;

/** Time evidence for one persistent segment. Never estimates across boots. */
final class WorkSessionTimingRules {
    static final long CLOCK_TOLERANCE_MS = 120_000L;
    static final long REVIEW_DURATION_MS = 24L * 60 * 60 * 1000;

    static String reviewReason(long wallStart, long elapsedStart, String bootStart,
                               long wallNow, long elapsedNow, String bootNow) {
        if (bootStart == null || bootStart.isEmpty() || !bootStart.equals(bootNow)) return "DEVICE_RESTART";
        long elapsed = elapsedNow - elapsedStart, wall = wallNow - wallStart;
        if (elapsed < 0 || wall < 0) return "CLOCK_REVERSED";
        if (Math.abs(wall - elapsed) > CLOCK_TOLERANCE_MS) return "CLOCK_CHANGED";
        if (elapsed > REVIEW_DURATION_MS) return "DURATION_REVIEW_REQUIRED";
        return "";
    }

    static long verifiedDelta(long wallStart, long elapsedStart, String bootStart,
                              long wallNow, long elapsedNow, String bootNow) {
        return reviewReason(wallStart, elapsedStart, bootStart, wallNow, elapsedNow, bootNow).isEmpty()
                ? elapsedNow - elapsedStart : 0;
    }
}

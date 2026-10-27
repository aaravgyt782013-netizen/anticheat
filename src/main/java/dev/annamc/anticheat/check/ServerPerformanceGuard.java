package dev.annamc.anticheat.check;

/**
 * Conservative TPS gate for gameplay heuristics. Invalid/unavailable TPS data fails open
 * so a transient API problem does not silently disable detection.
 */
public final class ServerPerformanceGuard {
    private ServerPerformanceGuard() {}

    /**
     * @param recentTps Paper's TPS samples; index 0 is the one-minute average
     * @param minimumTps threshold in the inclusive range 1.0–20.0
     * @return true only when valid one-minute TPS data is below the configured threshold
     */
    public static boolean isLagging(double[] recentTps, double minimumTps) {
        if (!Double.isFinite(minimumTps) || minimumTps < 1.0 || minimumTps > 20.0
                || recentTps == null || recentTps.length == 0) {
            return false;
        }
        double oneMinuteTps = recentTps[0];
        return Double.isFinite(oneMinuteTps) && oneMinuteTps >= 0.0 && oneMinuteTps < minimumTps;
    }
}

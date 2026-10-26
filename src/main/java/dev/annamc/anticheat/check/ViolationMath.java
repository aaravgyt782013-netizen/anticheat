package dev.annamc.anticheat.check;

/** Pure, deterministic violation-level math for testability. */
public final class ViolationMath {
    private ViolationMath() {}

    public static double decay(double stored, long elapsedMillis, double decayPerSecond) {
        if (!Double.isFinite(stored) || stored <= 0.0) return 0.0;
        if (!Double.isFinite(decayPerSecond) || decayPerSecond <= 0.0) return stored;
        long elapsed = Math.max(0L, elapsedMillis);
        double decayed = stored - (elapsed / 1000.0) * decayPerSecond;
        return Double.isFinite(decayed) ? Math.max(0.0, decayed) : 0.0;
    }
}

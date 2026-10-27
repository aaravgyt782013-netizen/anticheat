package dev.annamc.anticheat.check;

/** Pure detection math extracted for deterministic unit tests. */
public final class DetectionMath {
    private DetectionMath() {}

    public static boolean isMovementOutlier(double distance, double allowance, double ratioThreshold) {
        if (!Double.isFinite(distance) || !Double.isFinite(allowance)
                || !Double.isFinite(ratioThreshold) || distance < 0 || allowance <= 0 || ratioThreshold <= 0) {
            return false;
        }
        return distance / allowance >= ratioThreshold;
    }

    public static boolean sameRotationDelta(double yawA, double pitchA, double yawB, double pitchB,
                                            double toleranceDegrees) {
        if (!Double.isFinite(yawA) || !Double.isFinite(pitchA) || !Double.isFinite(yawB)
                || !Double.isFinite(pitchB) || !Double.isFinite(toleranceDegrees) || toleranceDegrees < 0) {
            return false;
        }
        return Math.abs(normalize(yawA - yawB)) <= toleranceDegrees
                && Math.abs(pitchA - pitchB) <= toleranceDegrees;
    }

    public static boolean exceedsPacketRate(int observed, int maximum) {
        return maximum > 0 && observed >= maximum;
    }

    /**
     * Checks a monotonic timestamp against a grace window after a velocity event.
     * Timestamps are from System.nanoTime(); a zero/negative window disables it.
     */
    public static boolean isWithinGracePeriod(long nowNanos, long eventNanos, long graceMillis) {
        if (graceMillis <= 0) return false;
        long ageNanos = nowNanos - eventNanos;
        if (ageNanos < 0) return false;
        long maxNanos = graceMillis > Long.MAX_VALUE / 1_000_000L
                ? Long.MAX_VALUE : graceMillis * 1_000_000L;
        return ageNanos <= maxNanos;
    }

    private static double normalize(double value) {
        while (value > 180.0) value -= 360.0;
        while (value < -180.0) value += 360.0;
        return value;
    }
}

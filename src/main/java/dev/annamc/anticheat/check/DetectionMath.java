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

    private static double normalize(double value) {
        while (value > 180.0) value -= 360.0;
        while (value < -180.0) value += 360.0;
        return value;
    }
}

package dev.annamc.anticheat.check;

/** Pure geometry helpers for conservative block-reach validation. */
public final class BlockReachMath {
    private BlockReachMath() {}

    /**
     * Returns the shortest Euclidean distance from a point to an axis-aligned box.
     * Invalid coordinates or inverted bounds return positive infinity (no flag).
     */
    public static double distanceToBox(double x, double y, double z,
                                       double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
        if (!finite(x, y, z, minX, minY, minZ, maxX, maxY, maxZ)
                || minX > maxX || minY > maxY || minZ > maxZ) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = axisDistance(x, minX, maxX);
        double dy = axisDistance(y, minY, maxY);
        double dz = axisDistance(z, minZ, maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Uses a tolerance outside the configured reach to avoid edge/rounding noise. */
    public static boolean exceeds(double distance, double maximum, double tolerance) {
        if (!finite(distance, maximum, tolerance)
                || distance < 0.0 || maximum < 0.0 || tolerance < 0.0) {
            return false;
        }
        return distance > maximum + tolerance;
    }

    private static double axisDistance(double value, double minimum, double maximum) {
        if (value < minimum) return minimum - value;
        if (value > maximum) return value - maximum;
        return 0.0;
    }

    private static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) return false;
        }
        return true;
    }
}

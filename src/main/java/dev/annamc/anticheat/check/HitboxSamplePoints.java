package dev.annamc.anticheat.check;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds ray-sampling points from normalized coordinates inside an entity hitbox.
 * The hard cap protects the per-hit ray-tracing budget even if the config is oversized.
 */
public final class HitboxSamplePoints {
    public static final int HARD_MAX_SAMPLES = 32;

    private static final List<Map<String, Double>> DEFAULT_POINTS = List.of(
            point(0.50, 0.50, 0.50),
            point(0.50, 0.10, 0.50),
            point(0.50, 0.90, 0.50),
            point(0.00, 0.50, 0.00),
            point(0.00, 0.50, 1.00),
            point(1.00, 0.50, 0.00),
            point(1.00, 0.50, 1.00),
            point(0.00, 0.25, 0.50),
            point(1.00, 0.75, 0.50)
    );

    private HitboxSamplePoints() {}

    public static List<Vector> resolve(BoundingBox box, List<? extends Map<?, ?>> configuredPoints,
                                       int configuredMaximum) {
        if (box == null) return List.of();
        int maximum = Math.max(1, Math.min(HARD_MAX_SAMPLES, configuredMaximum));
        List<? extends Map<?, ?>> source = configuredPoints == null || configuredPoints.isEmpty()
                ? DEFAULT_POINTS : configuredPoints;
        List<Vector> result = new ArrayList<>(Math.min(maximum, source.size()));
        for (Map<?, ?> definition : source) {
            double[] coordinates = readPoint(definition);
            if (coordinates == null) continue;
            result.add(new Vector(
                    interpolate(box.getMinX(), box.getMaxX(), coordinates[0]),
                    interpolate(box.getMinY(), box.getMaxY(), coordinates[1]),
                    interpolate(box.getMinZ(), box.getMaxZ(), coordinates[2])));
            if (result.size() >= maximum) break;
        }
        // A malformed custom list must not silently disable the detector.
        if (result.isEmpty() && source != DEFAULT_POINTS) {
            return resolve(box, DEFAULT_POINTS, maximum);
        }
        return List.copyOf(result);
    }

    /** Returns startup diagnostics for malformed or fragile custom sample layouts. */
    public static List<String> validate(List<? extends Map<?, ?>> configuredPoints) {
        if (configuredPoints == null || configuredPoints.isEmpty()) return List.of();
        List<String> warnings = new ArrayList<>();
        int valid = 0;
        for (int i = 0; i < configuredPoints.size(); i++) {
            if (readPoint(configuredPoints.get(i)) != null) {
                valid++;
            } else {
                warnings.add("sample point #" + (i + 1)
                        + " is invalid and will be ignored; x, y, and z must be finite numbers from 0.0 to 1.0.");
            }
        }
        if (configuredPoints.size() > HARD_MAX_SAMPLES) {
            warnings.add("sample-points contains " + configuredPoints.size() + " entries; at most "
                    + HARD_MAX_SAMPLES + " rays are evaluated per hit.");
        }
        if (valid == 0) {
            warnings.add("no valid custom sample points remain; ANNA AC will use its built-in default layout.");
        } else if (valid < 3) {
            warnings.add("only " + valid + " valid hitbox sample point(s) remain; three or more are recommended.");
        }
        return List.copyOf(warnings);
    }

    private static double[] readPoint(Map<?, ?> point) {
        if (point == null) return null;
        Double x = coordinate(point.get("x"));
        Double y = coordinate(point.get("y"));
        Double z = coordinate(point.get("z"));
        if (x == null || y == null || z == null) return null;
        return new double[]{x, y, z};
    }

    private static Double coordinate(Object value) {
        if (!(value instanceof Number number)) return null;
        double result = number.doubleValue();
        if (!Double.isFinite(result) || result < 0.0 || result > 1.0) return null;
        return result;
    }

    private static double interpolate(double minimum, double maximum, double normalized) {
        return minimum + (maximum - minimum) * normalized;
    }

    private static Map<String, Double> point(double x, double y, double z) {
        return Map.of("x", x, "y", y, "z", z);
    }
}

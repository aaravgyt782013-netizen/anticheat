package dev.annamc.anticheat.check;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HitboxSamplePointsTest {
    private final BoundingBox box = new BoundingBox(0.0, 0.0, 0.0, 2.0, 4.0, 6.0);

    @Test
    void defaultLayoutSamplesNinePointsInsideTheHitbox() {
        List<Vector> points = HitboxSamplePoints.resolve(box, List.of(), 9);
        assertEquals(9, points.size());
        assertEquals(new Vector(1.0, 2.0, 3.0), points.get(0));
        assertTrue(points.stream().allMatch(point ->
                point.getX() >= 0.0 && point.getX() <= 2.0
                        && point.getY() >= 0.0 && point.getY() <= 4.0
                        && point.getZ() >= 0.0 && point.getZ() <= 6.0));
    }

    @Test
    void customNormalizedCoordinatesMapAcrossBoundingBox() {
        List<Map<String, Double>> configured = List.of(Map.of("x", 0.25, "y", 0.5, "z", 0.75));
        List<Vector> points = HitboxSamplePoints.resolve(box, configured, 9);
        assertEquals(1, points.size());
        assertEquals(new Vector(0.5, 2.0, 4.5), points.get(0));
    }

    @Test
    void invalidDefinitionsAreIgnoredAndFullyInvalidLayoutFallsBackToDefaults() {
        List<Map<String, Object>> configured = List.of(
                Map.of("x", 0.25, "y", 0.5, "z", 0.75),
                Map.of("x", 2.0, "y", 0.5, "z", 0.5));
        assertEquals(1, HitboxSamplePoints.resolve(box, configured, 9).size());
        assertEquals(9, HitboxSamplePoints.resolve(box,
                List.of(Map.of("x", 2.0, "y", 0.5, "z", 0.5)), 9).size());
    }

    @Test
    void configuredMaximumIsBoundedToAvoidUnboundedRayTracing() {
        assertEquals(2, HitboxSamplePoints.resolve(box, List.of(), 2).size());
        assertEquals(HitboxSamplePoints.HARD_MAX_SAMPLES,
                HitboxSamplePoints.resolve(box, List.of(), Integer.MAX_VALUE).size());
        assertEquals(1, HitboxSamplePoints.resolve(box, List.of(), Integer.MIN_VALUE).size());
    }

    @Test
    void validationWarnsForMalformedAndFragileLayouts() {
        List<String> warnings = HitboxSamplePoints.validate(List.of(
                Map.of("x", 0.2, "y", 0.3, "z", 0.4),
                Map.of("x", 5.0, "y", 0.3, "z", 0.4)));
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("sample point #2"));
        assertTrue(warnings.get(1).contains("only 1 valid"));
    }
}

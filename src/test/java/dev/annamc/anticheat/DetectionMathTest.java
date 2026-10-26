package dev.annamc.anticheat;

import dev.annamc.anticheat.check.DetectionMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DetectionMathTest {
    @Test void movementOutlierUsesRatioBoundary() {
        assertFalse(DetectionMath.isMovementOutlier(1.99, 1.0, 2.0));
        assertTrue(DetectionMath.isMovementOutlier(2.0, 1.0, 2.0));
        assertFalse(DetectionMath.isMovementOutlier(Double.NaN, 1.0, 2.0));
        assertFalse(DetectionMath.isMovementOutlier(2.0, 0.0, 2.0));
    }

    @Test void rotationComparisonHandlesYawWrapAround() {
        assertTrue(DetectionMath.sameRotationDelta(179.95, 2.0, -179.95, 2.0, 0.11));
        assertTrue(DetectionMath.sameRotationDelta(12.0, -4.0, 12.05, -4.05, 0.1));
        assertFalse(DetectionMath.sameRotationDelta(12.0, -4.0, 12.5, -4.0, 0.1));
    }

    @Test void packetRateRequiresPositiveLimit() {
        assertTrue(DetectionMath.exceedsPacketRate(45, 45));
        assertFalse(DetectionMath.exceedsPacketRate(44, 45));
        assertFalse(DetectionMath.exceedsPacketRate(100, 0));
    }
}

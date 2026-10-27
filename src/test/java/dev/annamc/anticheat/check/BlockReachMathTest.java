package dev.annamc.anticheat.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockReachMathTest {
    @Test
    void measuresShortestDistanceToTheBoxRatherThanItsCenter() {
        assertEquals(5.0, BlockReachMath.distanceToBox(
                6.0, 0.5, 0.5, 0, 0, 0, 1, 1, 1), 1.0e-9);
        assertEquals(0.0, BlockReachMath.distanceToBox(
                0.5, 0.5, 0.5, 0, 0, 0, 1, 1, 1), 1.0e-9);
        assertEquals(3.0, BlockReachMath.distanceToBox(
                0.5, 4.0, 0.5, 0, 0, 0, 1, 1, 1), 1.0e-9);
    }

    @Test
    void tolerancePreventsFlagsAtTheConfiguredBoundary() {
        assertFalse(BlockReachMath.exceeds(5.15, 5.0, 0.15));
        assertTrue(BlockReachMath.exceeds(5.151, 5.0, 0.15));
        assertFalse(BlockReachMath.exceeds(4.9, 5.0, 0.15));
    }

    @Test
    void invalidGeometryAndLimitsFailClosedWithoutFlagging() {
        assertEquals(Double.POSITIVE_INFINITY, BlockReachMath.distanceToBox(
                Double.NaN, 0, 0, 0, 0, 0, 1, 1, 1));
        assertEquals(Double.POSITIVE_INFINITY, BlockReachMath.distanceToBox(
                0, 0, 0, 2, 0, 0, 1, 1, 1));
        assertFalse(BlockReachMath.exceeds(Double.POSITIVE_INFINITY, 5.0, 0.1));
        assertFalse(BlockReachMath.exceeds(10.0, -1.0, 0.1));
        assertFalse(BlockReachMath.exceeds(10.0, 5.0, Double.NaN));
    }
}

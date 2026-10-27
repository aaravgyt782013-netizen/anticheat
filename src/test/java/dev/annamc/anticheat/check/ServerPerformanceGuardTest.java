package dev.annamc.anticheat.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerPerformanceGuardTest {
    @Test
    void pausesOnlyWhenOneMinuteTpsIsBelowThreshold() {
        assertTrue(ServerPerformanceGuard.isLagging(new double[]{16.9, 19.0, 20.0}, 17.0));
        assertFalse(ServerPerformanceGuard.isLagging(new double[]{17.0, 16.0, 15.0}, 17.0));
        assertFalse(ServerPerformanceGuard.isLagging(new double[]{19.9, 16.0, 15.0}, 17.0));
    }

    @Test
    void unavailableOrInvalidTpsDataFailsOpen() {
        assertFalse(ServerPerformanceGuard.isLagging(null, 17.0));
        assertFalse(ServerPerformanceGuard.isLagging(new double[0], 17.0));
        assertFalse(ServerPerformanceGuard.isLagging(new double[]{Double.NaN}, 17.0));
        assertFalse(ServerPerformanceGuard.isLagging(new double[]{Double.POSITIVE_INFINITY}, 17.0));
    }

    @Test
    void invalidThresholdDoesNotPauseDetection() {
        assertFalse(ServerPerformanceGuard.isLagging(new double[]{1.0}, Double.NaN));
        assertFalse(ServerPerformanceGuard.isLagging(new double[]{1.0}, 0.0));
        assertFalse(ServerPerformanceGuard.isLagging(new double[]{1.0}, 21.0));
    }
}

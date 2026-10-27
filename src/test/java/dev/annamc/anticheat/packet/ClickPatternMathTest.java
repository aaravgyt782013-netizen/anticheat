package dev.annamc.anticheat.packet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClickPatternMathTest {
    @Test
    void detectsVeryRegularIntervalsOnlyWhenRateAndSampleCountPass() {
        ClickPatternMath.Stats stats = ClickPatternMath.analyze(
                List.of(100L, 200L, 300L, 400L, 500L, 600L, 700L, 800L));

        assertNotNull(stats);
        assertEquals(100.0, stats.meanIntervalMillis(), 0.001);
        assertEquals(0.0, stats.standardDeviationMillis(), 0.001);
        assertEquals(10.0, stats.attacksPerSecond(), 0.001);
        assertTrue(ClickPatternMath.isRegular(stats, 8, 8.0, 2.0, 0.05));
        assertFalse(ClickPatternMath.isRegular(stats, 10, 8.0, 2.0, 0.05));
        assertFalse(ClickPatternMath.isRegular(stats, 8, 11.0, 2.0, 0.05));
    }

    @Test
    void irregularHumanLikeIntervalsDoNotPassStrictRegularityThresholds() {
        ClickPatternMath.Stats stats = ClickPatternMath.analyze(
                List.of(100L, 190L, 320L, 405L, 560L, 645L, 790L, 900L, 1040L));

        assertNotNull(stats);
        assertTrue(stats.standardDeviationMillis() > 2.0);
        assertFalse(ClickPatternMath.isRegular(stats, 8, 5.0, 2.0, 0.03));
    }

    @Test
    void invalidOrInsufficientSamplesAreIgnored() {
        assertNull(ClickPatternMath.analyze(null));
        assertNull(ClickPatternMath.analyze(List.of(1L, 2L)));
        assertNull(ClickPatternMath.analyze(List.of(1L, 1L, 2L)));
        assertNull(ClickPatternMath.analyze(List.of(3L, 2L, 1L)));
        assertFalse(ClickPatternMath.isRegular(null, 8, 5.0, 3.0, 0.1));
    }

    @Test
    void invalidConfiguredLimitsNeverProduceARegularityFlag() {
        ClickPatternMath.Stats stats = ClickPatternMath.analyze(List.of(100L, 200L, 300L, 400L));
        assertFalse(ClickPatternMath.isRegular(stats, 3, Double.NaN, 3.0, 0.1));
        assertFalse(ClickPatternMath.isRegular(stats, 3, 5.0, -1.0, 0.1));
        assertFalse(ClickPatternMath.isRegular(stats, 3, 5.0, 3.0, 1.1));
    }
}

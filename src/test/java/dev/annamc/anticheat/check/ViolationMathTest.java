package dev.annamc.anticheat.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ViolationMathTest {
    @Test
    void decaysByElapsedSeconds() {
        assertEquals(4.0, ViolationMath.decay(5.0, 2000, 0.5), 1.0e-9);
    }

    @Test
    void doesNotDecayForNegativeElapsedTimeOrNonPositiveRate() {
        assertEquals(5.0, ViolationMath.decay(5.0, -100, 1.0), 1.0e-9);
        assertEquals(5.0, ViolationMath.decay(5.0, 1000, 0.0), 1.0e-9);
    }

    @Test
    void clampsLevelsAtZeroAndHandlesInvalidStoredValues() {
        assertEquals(0.0, ViolationMath.decay(1.0, 10_000, 1.0), 1.0e-9);
        assertEquals(0.0, ViolationMath.decay(Double.NaN, 100, 1.0), 1.0e-9);
        assertEquals(0.0, ViolationMath.decay(-1.0, 100, 1.0), 1.0e-9);
    }
}

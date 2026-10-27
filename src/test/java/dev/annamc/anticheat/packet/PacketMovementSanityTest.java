package dev.annamc.anticheat.packet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PacketMovementSanityTest {
    @Test
    void validCoordinatesIncludeNormalAndNegativeWorldPositions() {
        assertTrue(PacketMovementSanity.hasValidCoordinates(120.5, 64.0, -800.25, 30_000_000, 4096));
        assertTrue(PacketMovementSanity.hasValidCoordinates(-29_999_999, -64, 29_999_999, 30_000_000, 4096));
    }

    @Test
    void rejectsNonFiniteOrOutOfRangeCoordinates() {
        assertFalse(PacketMovementSanity.hasValidCoordinates(Double.NaN, 64, 0, 30_000_000, 4096));
        assertFalse(PacketMovementSanity.hasValidCoordinates(0, Double.POSITIVE_INFINITY, 0, 30_000_000, 4096));
        assertFalse(PacketMovementSanity.hasValidCoordinates(30_000_001, 64, 0, 30_000_000, 4096));
        assertFalse(PacketMovementSanity.hasValidCoordinates(0, 4097, 0, 30_000_000, 4096));
        assertFalse(PacketMovementSanity.hasValidCoordinates(0, 64, 0, Double.NaN, 4096));
    }

    @Test
    void acceptsOrdinaryStepsAndFlagsExtremeSteps() {
        var ordinary = PacketMovementSanity.evaluateStep(0, 64, 0, 3.5, 65.0, 2.0, 16.0, 16.0);
        assertFalse(ordinary.suspicious());
        assertEquals(Math.hypot(3.5, 2.0), ordinary.horizontalDistance(), 1.0e-9);

        var horizontal = PacketMovementSanity.evaluateStep(0, 64, 0, 16.01, 64, 0, 16.0, 16.0);
        assertTrue(horizontal.suspicious());
        var vertical = PacketMovementSanity.evaluateStep(0, 64, 0, 0, 80.01, 0, 16.0, 16.0);
        assertTrue(vertical.suspicious());
    }

    @Test
    void invalidStepInputsFailClosedAsSuspicious() {
        assertTrue(PacketMovementSanity.evaluateStep(Double.NaN, 64, 0, 0, 64, 0, 16, 16).suspicious());
        assertTrue(PacketMovementSanity.evaluateStep(0, 64, 0, 0, 64, 0, -1, 16).suspicious());
    }
}

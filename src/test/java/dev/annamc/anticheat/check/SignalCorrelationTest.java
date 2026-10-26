package dev.annamc.anticheat.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignalCorrelationTest {
    @Test
    void relatedDetectorsShareFamilies() {
        assertEquals("movement", SignalCorrelation.familyFor("movement"));
        assertEquals("movement", SignalCorrelation.familyFor("knockback"));
        assertEquals("combat", SignalCorrelation.familyFor("reach"));
        assertEquals("combat", SignalCorrelation.familyFor("wall-hit"));
        assertEquals("world-interaction", SignalCorrelation.familyFor("fast-break"));
        assertEquals("world-interaction", SignalCorrelation.familyFor("nuker-burst"));
        assertEquals("packet", SignalCorrelation.familyFor("timer"));
    }

    @Test
    void unknownDetectorDoesNotPretendToBelongToAnExistingFamily() {
        assertEquals("new-custom-check", SignalCorrelation.familyFor("New-Custom-Check"));
        assertEquals("unknown", SignalCorrelation.familyFor(null));
    }
}

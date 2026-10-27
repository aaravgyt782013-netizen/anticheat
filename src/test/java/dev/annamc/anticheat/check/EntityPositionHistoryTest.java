package dev.annamc.anticheat.check;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EntityPositionHistoryTest {
    @Test
    void historicalHitboxDistanceUsesNearestPoint() {
        var snapshot = new EntityPositionHistory.Snapshot(
                1000L, UUID.randomUUID(), 0, 0, 0,
                -0.3, 0, -0.3, 0.3, 1.8, 0.3);

        assertEquals(0.0, snapshot.distanceToBox(0.2, 1.0, -0.1), 1.0e-9);
        assertEquals(2.0, snapshot.distanceToBox(2.3, 1.0, 0.0), 1.0e-9);
    }

    @Test
    void snapshotRetainsWorldAndTimestampForCallerValidation() {
        UUID world = UUID.randomUUID();
        var snapshot = new EntityPositionHistory.Snapshot(
                1234L, world, 1, 2, 3, 0, 0, 0, 1, 2, 3);
        assertEquals(1234L, snapshot.timestampMillis());
        assertEquals(world, snapshot.worldId());
        assertEquals(1.0, snapshot.x());
    }
}

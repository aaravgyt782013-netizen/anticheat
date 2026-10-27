package dev.annamc.anticheat.packet;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimestampWindowTest {
    @Test
    void trimsOldEntriesAndRetainsOnlyTheConfiguredCapacity() {
        Deque<Long> timestamps = new ArrayDeque<>();
        assertEquals(1, TimestampWindow.addAndTrim(timestamps, 100L, 10_000L, 3));
        assertEquals(2, TimestampWindow.addAndTrim(timestamps, 200L, 10_000L, 3));
        assertEquals(3, TimestampWindow.addAndTrim(timestamps, 300L, 10_000L, 3));
        assertEquals(3, TimestampWindow.addAndTrim(timestamps, 400L, 10_000L, 3));
        assertEquals(200L, timestamps.peekFirst());
        assertEquals(400L, timestamps.peekLast());
    }

    @Test
    void dropsExpiredSamplesBeforeReturningSize() {
        Deque<Long> timestamps = new ArrayDeque<>();
        timestamps.add(100L);
        timestamps.add(150L);

        assertEquals(1, TimestampWindow.addAndTrim(timestamps, 300L, 100L, 8));
        assertEquals(300L, timestamps.peekFirst());
    }

    @Test
    void supportsSmallExplicitCapacityAndRejectsNullDeque() {
        Deque<Long> timestamps = new ArrayDeque<>();
        TimestampWindow.addAndTrim(timestamps, 1L, 100L, 0);
        TimestampWindow.addAndTrim(timestamps, 2L, 100L, 0);
        assertEquals(1, timestamps.size());
        assertThrows(NullPointerException.class, () -> TimestampWindow.addAndTrim(null, 1L, 100L, 1));
    }
}

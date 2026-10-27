package dev.annamc.anticheat.packet;

import java.util.Deque;
import java.util.Objects;

/**
 * Utility for time-based rolling samples with a hard sample-count bound.
 * Callers sharing a deque across threads must synchronize on that deque.
 */
public final class TimestampWindow {
    private TimestampWindow() {}

    /**
     * Appends the current timestamp, discards entries older than the time window,
     * then trims the oldest entries until the count is within maxSamples.
     *
     * @return the number of retained samples
     */
    public static int addAndTrim(Deque<Long> timestamps, long nowMs, long windowMs, int maxSamples) {
        Objects.requireNonNull(timestamps, "timestamps");
        timestamps.addLast(nowMs);
        long safeWindow = Math.max(0L, windowMs);
        long cutoff = nowMs - safeWindow;
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.removeFirst();
        }
        int capacity = Math.max(1, maxSamples);
        while (timestamps.size() > capacity) {
            timestamps.removeFirst();
        }
        return timestamps.size();
    }
}

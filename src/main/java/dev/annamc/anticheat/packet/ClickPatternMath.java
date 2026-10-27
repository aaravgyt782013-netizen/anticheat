package dev.annamc.anticheat.packet;

import java.util.List;

/**
 * Pure statistics for an optional attack-timing regularity signal.
 * Highly regular attack intervals are only a telemetry clue, not proof of automation.
 */
public final class ClickPatternMath {
    private ClickPatternMath() {}

    public record Stats(int attackCount, double meanIntervalMillis,
                        double standardDeviationMillis, double coefficientOfVariation) {
        public double attacksPerSecond() {
            return meanIntervalMillis > 0.0 ? 1000.0 / meanIntervalMillis : 0.0;
        }
    }

    /**
     * Calculates population standard deviation for successive attack intervals.
     * Returns null for too few samples, duplicate timestamps, out-of-order timestamps,
     * or non-finite calculations. Callers should pass a time-bounded chronological window.
     */
    public static Stats analyze(List<Long> timestamps) {
        if (timestamps == null || timestamps.size() < 3) return null;

        int intervalCount = timestamps.size() - 1;
        double sum = 0.0;
        double[] intervals = new double[intervalCount];
        for (int i = 1; i < timestamps.size(); i++) {
            Long before = timestamps.get(i - 1);
            Long after = timestamps.get(i);
            if (before == null || after == null || after <= before) return null;
            double interval = (double) after - before;
            if (!Double.isFinite(interval) || interval <= 0.0) return null;
            intervals[i - 1] = interval;
            sum += interval;
        }

        double mean = sum / intervalCount;
        if (!Double.isFinite(mean) || mean <= 0.0) return null;

        double squared = 0.0;
        for (double interval : intervals) {
            double delta = interval - mean;
            squared += delta * delta;
        }
        double deviation = Math.sqrt(squared / intervalCount);
        double coefficient = deviation / mean;
        if (!Double.isFinite(deviation) || !Double.isFinite(coefficient)) return null;
        return new Stats(timestamps.size(), mean, deviation, coefficient);
    }

    /** Returns true only when sample count, rate and both regularity bounds pass. */
    public static boolean isRegular(Stats stats, int minimumAttacks, double minimumCps,
                                    double maximumStandardDeviationMillis,
                                    double maximumCoefficientOfVariation) {
        if (stats == null || minimumAttacks < 3 || stats.attackCount() < minimumAttacks
                || !Double.isFinite(minimumCps) || minimumCps < 0.0
                || !Double.isFinite(maximumStandardDeviationMillis) || maximumStandardDeviationMillis < 0.0
                || !Double.isFinite(maximumCoefficientOfVariation)
                || maximumCoefficientOfVariation < 0.0 || maximumCoefficientOfVariation > 1.0) {
            return false;
        }
        return stats.attacksPerSecond() >= minimumCps
                && stats.standardDeviationMillis() <= maximumStandardDeviationMillis
                && stats.coefficientOfVariation() <= maximumCoefficientOfVariation;
    }
}

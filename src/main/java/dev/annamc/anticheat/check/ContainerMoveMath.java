package dev.annamc.anticheat.check;

/** Pure, bounded math for the optional container-movement signal. */
public final class ContainerMoveMath {
    private ContainerMoveMath() {}

    public static boolean isCandidateStep(double horizontalDistance, double verticalDistance,
                                          double minimumHorizontalDistance, double maximumHorizontalStep,
                                          double maximumVerticalStep) {
        if (!Double.isFinite(horizontalDistance) || !Double.isFinite(verticalDistance)
                || !Double.isFinite(minimumHorizontalDistance)
                || !Double.isFinite(maximumHorizontalStep) || !Double.isFinite(maximumVerticalStep)) {
            return false;
        }
        if (horizontalDistance < 0.0 || verticalDistance < 0.0
                || minimumHorizontalDistance < 0.0 || maximumHorizontalStep < minimumHorizontalDistance
                || maximumVerticalStep < 0.0) {
            return false;
        }
        return horizontalDistance >= minimumHorizontalDistance
                && horizontalDistance <= maximumHorizontalStep
                && verticalDistance <= maximumVerticalStep;
    }

    public static boolean hasEnoughEvidence(int samples, double pathDistance,
                                            int requiredSamples, double minimumPathDistance) {
        if (samples < 0 || requiredSamples < 1 || !Double.isFinite(pathDistance)
                || !Double.isFinite(minimumPathDistance) || pathDistance < 0.0
                || minimumPathDistance < 0.0) {
            return false;
        }
        return samples >= requiredSamples && pathDistance >= minimumPathDistance;
    }
}
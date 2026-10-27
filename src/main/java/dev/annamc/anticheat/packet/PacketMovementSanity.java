package dev.annamc.anticheat.packet;

/**
 * Conservative math for opt-in raw client movement-packet sanity checks.
 * These checks are only supplemental telemetry; server teleports and long packet gaps
 * must reset the baseline, and no single step proves cheating.
 */
public final class PacketMovementSanity {
    private PacketMovementSanity() {}

    public static boolean hasValidCoordinates(double x, double y, double z,
                                              double maxAbsoluteCoordinate, double maxAbsoluteY) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Double.isFinite(maxAbsoluteCoordinate) && maxAbsoluteCoordinate > 0.0
                && Double.isFinite(maxAbsoluteY) && maxAbsoluteY > 0.0
                && Math.abs(x) <= maxAbsoluteCoordinate
                && Math.abs(z) <= maxAbsoluteCoordinate
                && Math.abs(y) <= maxAbsoluteY;
    }

    public static StepResult evaluateStep(double fromX, double fromY, double fromZ,
                                          double toX, double toY, double toZ,
                                          double maxHorizontalStep, double maxVerticalStep) {
        if (!Double.isFinite(fromX) || !Double.isFinite(fromY) || !Double.isFinite(fromZ)
                || !Double.isFinite(toX) || !Double.isFinite(toY) || !Double.isFinite(toZ)
                || !Double.isFinite(maxHorizontalStep) || !Double.isFinite(maxVerticalStep)
                || maxHorizontalStep < 0.0 || maxVerticalStep < 0.0) {
            return new StepResult(true, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
        }
        double horizontal = Math.hypot(toX - fromX, toZ - fromZ);
        double vertical = Math.abs(toY - fromY);
        return new StepResult(horizontal > maxHorizontalStep || vertical > maxVerticalStep,
                horizontal, vertical);
    }

    public record StepResult(boolean suspicious, double horizontalDistance, double verticalDistance) {}
}

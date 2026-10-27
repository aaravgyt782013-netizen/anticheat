package dev.annamc.anticheat.check;

/**
 * Bounded horizontal movement envelope. This is deliberately a heuristic, not a
 * full replay of Minecraft client physics or collision resolution.
 */
public final class MovementPredictionEngine {
    private MovementPredictionEngine() {}

    public record Input(
            long elapsedMillis,
            double baseHorizontalPerTick,
            boolean sprinting,
            double sprintMultiplier,
            int speedAmplifier,
            double speedPotionMultiplierPerLevel,
            boolean adventureMode,
            double adventureModeMultiplier,
            int pingMillis,
            double pingSlackPerMs,
            double maxPingSlack,
            boolean specialSurface,
            double specialSurfaceAllowancePerTick,
            double velocityX,
            double velocityZ,
            double velocityAgeMillis,
            double velocityInfluenceMillis,
            double velocityAllowanceMultiplier,
            double maxVelocityAllowance) {}

    public record Prediction(
            double horizontalAllowance,
            double elapsedTicks,
            double latencyAllowance,
            double velocityAllowance) {}

    public static Prediction predict(Input input) {
        if (input == null) return new Prediction(0.01, 1.0, 0.0, 0.0);

        double elapsed = finiteClamp(input.elapsedMillis(), 1.0, 1000.0);
        double ticks = Math.max(0.05, Math.min(20.0, elapsed / 50.0));
        double base = finiteClamp(input.baseHorizontalPerTick(), 0.01, 4.0);
        double sprint = input.sprinting()
                ? finiteClamp(input.sprintMultiplier(), 1.0, 3.0) : 1.0;
        int speedLevel = Math.max(-1, Math.min(20, input.speedAmplifier()));
        double speed = speedLevel < 0 ? 1.0
                : 1.0 + (speedLevel + 1) * finiteClamp(input.speedPotionMultiplierPerLevel(), 0.0, 1.0);
        double mode = input.adventureMode()
                ? finiteClamp(input.adventureModeMultiplier(), 0.5, 2.0) : 1.0;
        double surface = input.specialSurface()
                ? finiteClamp(input.specialSurfaceAllowancePerTick(), 0.0, 1.0) * ticks : 0.0;

        double pingSlack = Math.min(
                finiteClamp(input.maxPingSlack(), 0.0, 3.0),
                Math.max(0, Math.min(2000, input.pingMillis()))
                        * finiteClamp(input.pingSlackPerMs(), 0.0, 0.02));
        double velocityAllowance = velocityAllowance(input);
        double baseAllowance = base * ticks * sprint * speed * mode + surface;
        double total = baseAllowance + pingSlack + velocityAllowance;
        if (!Double.isFinite(total)) total = 0.01;
        total = Math.max(0.01, Math.min(100.0, total));
        return new Prediction(total, ticks, pingSlack, velocityAllowance);
    }

    private static double velocityAllowance(Input input) {
        double influence = finiteClamp(input.velocityInfluenceMillis(), 0.0, 5000.0);
        double age = finiteClamp(input.velocityAgeMillis(), 0.0, 60000.0);
        if (influence <= 0.0 || age >= influence) return 0.0;
        double vx = finiteClamp(input.velocityX(), -10.0, 10.0);
        double vz = finiteClamp(input.velocityZ(), -10.0, 10.0);
        double magnitude = Math.hypot(vx, vz);
        double multiplier = finiteClamp(input.velocityAllowanceMultiplier(), 0.0, 4.0);
        double cap = finiteClamp(input.maxVelocityAllowance(), 0.0, 5.0);
        double decay = 1.0 - age / influence;
        return Math.min(cap, magnitude * multiplier * decay);
    }

    private static double finiteClamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}

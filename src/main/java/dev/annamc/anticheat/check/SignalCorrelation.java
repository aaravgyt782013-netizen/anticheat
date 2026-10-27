package dev.annamc.anticheat.check;

/** Groups related detections so repeated variations of one signal do not count as independent evidence. */
public final class SignalCorrelation {
    private SignalCorrelation() {}

    public static String familyFor(String check) {
        if (check == null) return "unknown";
        return switch (check.toLowerCase(java.util.Locale.ROOT)) {
            case "movement", "movement-physics", "flight", "spider-signal", "knockback" -> "movement";
            case "reach", "wall-hit", "click-rate", "combat-pattern", "mace-damage" -> "combat";
            case "scaffold-signal", "fast-break", "nuker-burst", "no-fall", "jesus", "container-steal" -> "world-interaction";
            case "packet-rate", "timer" -> "packet";
            default -> check.toLowerCase(java.util.Locale.ROOT);
        };
    }
}

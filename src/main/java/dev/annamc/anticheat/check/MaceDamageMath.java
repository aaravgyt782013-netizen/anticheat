package dev.annamc.anticheat.check;

/**
 * Conservative pre-armor damage allowance for vanilla Java Edition mace hits.
 * This is a risk signal, not a complete replay of combat (plugins, effects,
 * critical hits, custom attributes, and server changes may alter final damage).
 */
public final class MaceDamageMath {
    private MaceDamageMath() {}

    /**
     * Returns the allowed raw-damage ceiling before extra operator-configured tolerance.
     *
     * Vanilla smash bonus:
     * - 4 damage per block through the first 3 blocks;
     * - 2 per block for the next 5 blocks;
     * - 1 per block beyond 8.
     * Density adds 0.5 damage per fallen block per enchantment level.
     */
    public static double expectedAllowance(
            double fallDistance,
            int densityLevel,
            double baseAllowance,
            double extraTolerance,
            double densityDamagePerBlockPerLevel) {
        double fall = Math.max(0.0, fallDistance);
        double smashBonus = 0.0;
        if (fall > 1.5) {
            if (fall <= 3.0) {
                smashBonus = 4.0 * fall;
            } else if (fall <= 8.0) {
                smashBonus = 2.0 * fall + 6.0;
            } else {
                smashBonus = fall + 14.0;
            }
        }

        int density = Math.max(0, Math.min(densityLevel, 5));
        double densityBonus = fall > 1.5
                ? fall * density * Math.max(0.0, densityDamagePerBlockPerLevel)
                : 0.0;
        return Math.max(0.0, baseAllowance) + smashBonus + densityBonus + Math.max(0.0, extraTolerance);
    }
}

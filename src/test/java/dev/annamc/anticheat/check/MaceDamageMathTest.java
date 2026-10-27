package dev.annamc.anticheat.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaceDamageMathTest {
    @Test
    void doesNotApplySmashBonusToGroundHits() {
        assertEquals(22.0, MaceDamageMath.expectedAllowance(0.0, 0, 10.0, 12.0, 0.5), 0.0001);
        assertEquals(22.0, MaceDamageMath.expectedAllowance(1.5, 5, 10.0, 12.0, 0.5), 0.0001);
    }

    @Test
    void modelsPiecewiseVanillaSmashBonus() {
        assertEquals(34.0, MaceDamageMath.expectedAllowance(3.0, 0, 10.0, 12.0, 0.5), 0.0001);
        // At 4 blocks, the smash bonus is 2*4 + 6 = 14.
        assertEquals(36.0, MaceDamageMath.expectedAllowance(4.0, 0, 10.0, 12.0, 0.5), 0.0001);
        // At 10 blocks, the smash bonus is 10 + 14 = 24.
        assertEquals(46.0, MaceDamageMath.expectedAllowance(10.0, 0, 10.0, 12.0, 0.5), 0.0001);
    }

    @Test
    void includesDensityAndClampsInvalidValues() {
        assertEquals(71.0, MaceDamageMath.expectedAllowance(10.0, 5, 10.0, 12.0, 0.5), 0.0001);
        assertEquals(12.0, MaceDamageMath.expectedAllowance(-8.0, -3, -1.0, 12.0, -2.0), 0.0001);
        // Limit untrusted/configured enchantment level to the vanilla maximum.
        assertEquals(71.0, MaceDamageMath.expectedAllowance(10.0, 20, 10.0, 12.0, 0.5), 0.0001);
    }
}

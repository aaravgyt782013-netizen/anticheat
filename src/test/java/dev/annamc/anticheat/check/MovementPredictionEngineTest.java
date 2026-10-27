package dev.annamc.anticheat.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MovementPredictionEngineTest {
    private MovementPredictionEngine.Input baseInput() {
        return new MovementPredictionEngine.Input(
                50, 0.72, false, 1.3, -1, 0.20,
                false, 1.03, 0, 0.001, 0.75, false, 0.18,
                0, 0, 1000, 650, 1.0, 0.8);
    }

    @Test
    void elapsedTimeScalesAllowanceWithinBoundedRange() {
        var shortStep = MovementPredictionEngine.predict(withElapsed(baseInput(), 50));
        var longerStep = MovementPredictionEngine.predict(withElapsed(baseInput(), 100));
        assertTrue(longerStep.horizontalAllowance() > shortStep.horizontalAllowance());
        assertTrue(longerStep.horizontalAllowance() <= 100.0);
    }

    @Test
    void pingAllowanceIsCapped() {
        var input = new MovementPredictionEngine.Input(
                50, 0.72, false, 1.3, -1, 0.20, false, 1.03,
                2000, 1.0, 0.3, false, 0.18, 0, 0, 5000, 650, 1, 0.8);
        var prediction = MovementPredictionEngine.predict(input);
        assertEquals(0.3, prediction.latencyAllowance(), 1.0e-9);
    }

    @Test
    void recentVelocityAddsBoundedAllowanceAndDecays() {
        var fresh = MovementPredictionEngine.predict(withVelocity(baseInput(), 100));
        var old = MovementPredictionEngine.predict(withVelocity(baseInput(), 600));
        assertTrue(fresh.velocityAllowance() > old.velocityAllowance());
        assertTrue(fresh.velocityAllowance() <= 0.8);
        assertEquals(0.0, old.velocityAllowance(), 1.0e-9);
    }

    @Test
    void invalidNumericInputsNeverProduceNonFiniteAllowance() {
        var input = new MovementPredictionEngine.Input(
                Long.MAX_VALUE, Double.NaN, true, Double.POSITIVE_INFINITY,
                999, Double.NaN, true, Double.NaN, Integer.MAX_VALUE,
                Double.NaN, Double.NaN, true, Double.NaN, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NaN, Double.POSITIVE_INFINITY,
                Double.NaN, Double.NaN);
        var prediction = MovementPredictionEngine.predict(input);
        assertTrue(Double.isFinite(prediction.horizontalAllowance()));
        assertTrue(prediction.horizontalAllowance() >= 0.01);
        assertTrue(Double.isFinite(prediction.velocityAllowance()));
    }

    private static MovementPredictionEngine.Input withElapsed(MovementPredictionEngine.Input i, long elapsed) {
        return new MovementPredictionEngine.Input(elapsed, i.baseHorizontalPerTick(), i.sprinting(),
                i.sprintMultiplier(), i.speedAmplifier(), i.speedPotionMultiplierPerLevel(),
                i.adventureMode(), i.adventureModeMultiplier(), i.pingMillis(), i.pingSlackPerMs(),
                i.maxPingSlack(), i.specialSurface(), i.specialSurfaceAllowancePerTick(),
                i.velocityX(), i.velocityZ(), i.velocityAgeMillis(), i.velocityInfluenceMillis(),
                i.velocityAllowanceMultiplier(), i.maxVelocityAllowance());
    }

    private static MovementPredictionEngine.Input withVelocity(MovementPredictionEngine.Input i, double age) {
        return new MovementPredictionEngine.Input(i.elapsedMillis(), i.baseHorizontalPerTick(), i.sprinting(),
                i.sprintMultiplier(), i.speedAmplifier(), i.speedPotionMultiplierPerLevel(),
                i.adventureMode(), i.adventureModeMultiplier(), i.pingMillis(), i.pingSlackPerMs(),
                i.maxPingSlack(), i.specialSurface(), i.specialSurfaceAllowancePerTick(),
                0.6, 0.0, age, i.velocityInfluenceMillis(), i.velocityAllowanceMultiplier(),
                i.maxVelocityAllowance());
    }
}

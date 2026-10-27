package dev.annamc.anticheat.check;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContainerMoveMathTest {
    @Test
    void candidateStepsAcceptNormalSmallMovement() {
        assertTrue(ContainerMoveMath.isCandidateStep(0.18, 0.0, 0.03, 1.5, 1.25));
        assertTrue(ContainerMoveMath.isCandidateStep(0.03, 0.4, 0.03, 1.5, 1.25));
    }

    @Test
    void candidateStepsRejectRotationOnlyTeleportAndLargeVerticalMovement() {
        assertFalse(ContainerMoveMath.isCandidateStep(0.0, 0.0, 0.03, 1.5, 1.25));
        assertFalse(ContainerMoveMath.isCandidateStep(2.5, 0.0, 0.03, 1.5, 1.25));
        assertFalse(ContainerMoveMath.isCandidateStep(0.18, 1.8, 0.03, 1.5, 1.25));
    }

    @Test
    void invalidOrNonFiniteStepSettingsFailClosed() {
        assertFalse(ContainerMoveMath.isCandidateStep(Double.NaN, 0.0, 0.03, 1.5, 1.25));
        assertFalse(ContainerMoveMath.isCandidateStep(0.5, 0.0, 1.0, 0.5, 1.25));
        assertFalse(ContainerMoveMath.isCandidateStep(0.5, -1.0, 0.03, 1.5, 1.25));
    }

    @Test
    void evidenceRequiresBothSampleCountAndPathDistance() {
        assertTrue(ContainerMoveMath.hasEnoughEvidence(4, 0.75, 4, 0.75));
        assertFalse(ContainerMoveMath.hasEnoughEvidence(3, 0.9, 4, 0.75));
        assertFalse(ContainerMoveMath.hasEnoughEvidence(5, 0.6, 4, 0.75));
        assertFalse(ContainerMoveMath.hasEnoughEvidence(10, Double.NaN, 4, 0.75));
    }
}
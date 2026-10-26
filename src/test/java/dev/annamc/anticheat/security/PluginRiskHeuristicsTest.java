package dev.annamc.anticheat.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PluginRiskHeuristicsTest {
    @Test
    void aSingleCommonCapabilityDoesNotAutomaticallyLookLikeMalware() {
        Set<String> raw = new HashSet<>();
        PluginRiskHeuristics.collectFromClassText("java/net/http/HttpClient", raw);

        Set<String> correlated = PluginRiskHeuristics.withCorrelations(raw);
        assertTrue(correlated.contains("network-capability"));
        assertFalse(correlated.contains("network-plus-process-execution"));
        assertTrue(PluginRiskHeuristics.score(correlated) < 5);
    }

    @Test
    void correlatesRiskyCapabilitiesAcrossDifferentClasses() {
        Set<String> raw = new HashSet<>();
        PluginRiskHeuristics.collectFromClassText(
                "java/lang/ProcessBuilder java/lang/Runtime exec", raw);
        PluginRiskHeuristics.collectFromClassText(
                "java/net/Socket java/net/http/HttpClient", raw);

        Set<String> correlated = PluginRiskHeuristics.withCorrelations(raw);
        assertTrue(correlated.contains("process-execution-capability"));
        assertTrue(correlated.contains("network-capability"));
        assertTrue(correlated.contains("network-plus-process-execution"));
        assertTrue(PluginRiskHeuristics.score(correlated) >= 5);
    }

    @Test
    void detectsCorrelatedWorldDeletionAndCommandCapabilitiesAcrossClasses() {
        Set<String> raw = new HashSet<>();
        PluginRiskHeuristics.collectFromClassText(
                "java/nio/file/Files deleteIfExists worlds/ level.dat region/r.", raw);
        PluginRiskHeuristics.collectFromClassText(
                "org/bukkit/Bukkit dispatchCommand java/net/Socket", raw);
        PluginRiskHeuristics.collectFromClassText(
                "java/lang/ProcessBuilder java/lang/Runtime exec", raw);

        Set<String> correlated = PluginRiskHeuristics.withCorrelations(raw);
        assertTrue(correlated.contains("network-plus-command-plus-file-delete-capability"));
        assertTrue(correlated.contains("world-file-delete-plus-process-execution"));
        assertTrue(PluginRiskHeuristics.score(correlated) >= 10);
    }
}

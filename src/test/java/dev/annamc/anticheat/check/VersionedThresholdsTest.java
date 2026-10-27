package dev.annamc.anticheat.check;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionedThresholdsTest {
    private YamlConfiguration config(String overrides) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("checks:\n  reach:\n    max-distance: 3.6\n    version-overrides:\n" + overrides);
        return config;
    }

    @Test
    void exactVersionOverrideWinsOverMajorMinorAndDefault() throws Exception {
        YamlConfiguration config = config("      '1_21':\n        max-distance: 3.7\n      '1_21_11':\n        max-distance: 3.8\n");

        assertEquals(3.8, VersionedThresholds.resolve(config, "1.21.11", "reach", "max-distance", 3.5));
    }

    @Test
    void majorMinorOverrideIsUsedWhenExactVersionIsMissing() throws Exception {
        YamlConfiguration config = config("      '1_21':\n        max-distance: 3.7\n");

        assertEquals(3.7, VersionedThresholds.resolve(config, "1.21.11", "reach", "max-distance", 3.5));
    }

    @Test
    void normalSettingIsUsedWhenNoVersionOverrideExists() throws Exception {
        YamlConfiguration config = config("      '1_20':\n        max-distance: 3.9\n");

        assertEquals(3.6, VersionedThresholds.resolve(config, "1.21.11", "reach", "max-distance", 3.5));
    }

    @Test
    void fallbackIsUsedWhenNeitherOverrideNorNormalSettingExists() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("checks:\n  reach:\n    version-overrides: {}\n");

        assertEquals(3.5, VersionedThresholds.resolve(config, "1.21.11", "reach", "missing-setting", 3.5));
    }

    @Test
    void movementSignalOverridesResolveIndependently() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  movement:
                    max-horizontal-distance-per-tick: 1.25
                    version-overrides:
                      '1_21': { max-horizontal-distance-per-tick: 1.28 }
                  flight:
                    max-hover-vertical-delta: 0.025
                    version-overrides:
                      '1_21_11': { max-hover-vertical-delta: 0.03 }
                  jesus:
                    max-vertical-delta: 0.04
                    version-overrides: {}
                  spider-signal:
                    minimum-upward-delta: 0.08
                    version-overrides: {}
                """);

        assertEquals(1.28, VersionedThresholds.resolve(config, "1.21.11",
                "movement", "max-horizontal-distance-per-tick", 1.0));
        assertEquals(0.03, VersionedThresholds.resolve(config, "1.21.11",
                "flight", "max-hover-vertical-delta", 0.02));
        assertEquals(0.04, VersionedThresholds.resolve(config, "1.21.11",
                "jesus", "max-vertical-delta", 0.02));
        assertEquals(0.08, VersionedThresholds.resolve(config, "1.21.11",
                "spider-signal", "minimum-upward-delta", 0.02));
    }

    @Test
    void combatAndMiningSignalsSupportVersionOverrides() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  knockback:
                    minimum-horizontal-velocity: 0.25
                    version-overrides:
                      '1_21': { minimum-horizontal-velocity: 0.28 }
                  wall-hit:
                    minimum-occluded-sample-ratio: 1.0
                    version-overrides:
                      '1_21_11': { minimum-occluded-sample-ratio: 0.85 }
                  fast-break:
                    minimum-time-ratio: 0.5
                    version-overrides:
                      '1_21_11': { minimum-time-ratio: 0.65 }
                  nuker-burst:
                    max-block-breaks-per-window: 18
                    version-overrides:
                      '1_21': { max-block-breaks-per-window: 16 }
                """);

        assertEquals(0.28, VersionedThresholds.resolve(config, "1.21.11",
                "knockback", "minimum-horizontal-velocity", 0.20));
        assertEquals(0.85, VersionedThresholds.resolve(config, "1.21.11",
                "wall-hit", "minimum-occluded-sample-ratio", 0.95));
        assertEquals(0.65, VersionedThresholds.resolve(config, "1.21.11",
                "fast-break", "minimum-time-ratio", 0.40));
        assertEquals(16.0, VersionedThresholds.resolve(config, "1.21.11",
                "nuker-burst", "max-block-breaks-per-window", 20.0));
    }

    @Test
    void clientExactOverrideWinsThenMajorMinorThenServerVersion() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  reach:
                    max-distance: 3.6
                    version-overrides:
                      '1_21_11': { max-distance: 3.7 }
                    client-version-overrides:
                      '1_21': { max-distance: 3.8 }
                      '1_21_11': { max-distance: 3.9 }
                """);

        assertEquals(3.9, VersionedThresholds.resolveForClient(config, "1.21.11",
                "1.21.11", "reach", "max-distance", 3.5));
        assertEquals(3.8, VersionedThresholds.resolveForClient(config, "1.21.10",
                "1.21.11", "reach", "max-distance", 3.5));
        assertEquals(3.7, VersionedThresholds.resolveForClient(config, null,
                "1.21.11", "reach", "max-distance", 3.5));
        assertEquals(3.7, VersionedThresholds.resolveForClient(config, "unknown",
                "1.21.11", "reach", "max-distance", 3.5));
    }

    @Test
    void overrideValidationReportsBadKeysUnknownSettingsAndNonnumericValues() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  reach:
                    max-distance: 3.6
                    version-overrides:
                      '1.21.11': { max-distance: 3.7 }
                      '1_21': { typo-threshold: 3.8 }
                      '1_21_11': { max-distance: not-a-number }
                """);

        java.util.List<String> warnings = VersionedThresholds.validateOverrides(config);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("underscore-separated release key")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("typo-threshold")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("finite numeric value")));
    }

    @Test
    void resolverFallsBackInsteadOfApplyingInvalidOverrideOrDefault() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  reach:
                    max-distance: -1.0
                    version-overrides:
                      '1_21_11': { max-distance: -3.0 }
                      '1_21': { max-distance: 3.7 }
                """);

        assertEquals(3.7, VersionedThresholds.resolve(config, "1.21.11",
                "reach", "max-distance", 3.5));
        config.set("checks.reach.version-overrides", null);
        assertEquals(3.5, VersionedThresholds.resolve(config, "1.21.11",
                "reach", "max-distance", 3.5));
    }

    @Test
    void overrideValidationWarnsOnOutOfRangeRatiosAndNegativeThresholds() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  reach:
                    max-distance: 3.6
                    version-overrides:
                      '1_21':
                        max-distance: -1.0
                  wall-hit:
                    minimum-occluded-sample-ratio: 1.0
                    version-overrides:
                      '1_21':
                        minimum-occluded-sample-ratio: 1.5
                """);

        java.util.List<String> warnings = VersionedThresholds.validateOverrides(config);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("max-distance must not be negative")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("minimum-occluded-sample-ratio must be between 0 and 1")));
    }

    @Test
    void baseThresholdValidationFindsInvalidRangesWithoutRejectingValidSettings() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  reach:
                    enabled: false
                    max-distance: -1.0
                    violation-decay-per-second: -0.1
                  wall-hit:
                    minimum-occluded-sample-ratio: 1.5
                    max-evaluation-distance: 6.0
                    version-overrides: {}
                  fast-break:
                    minimum-time-ratio: 0.5
                    minimum-required-time-ms: -1
                """);

        java.util.List<String> warnings = VersionedThresholds.validateConfiguredThresholds(config);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("checks.reach.max-distance must not be negative")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("checks.reach.violation-decay-per-second must not be negative")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("minimum-occluded-sample-ratio must be between 0 and 1")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("minimum-required-time-ms must not be negative")));
        assertFalse(warnings.stream().anyMatch(w -> w.contains("max-evaluation-distance")));
        assertFalse(warnings.stream().anyMatch(w -> w.contains("minimum-time-ratio")));
    }

    @Test
    void resolverRejectsNegativeSampleAndTimingOverrides() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  flight:
                    required-hover-samples: 24
                    client-version-overrides:
                      '1_8': { required-hover-samples: -5 }
                  fast-break:
                    sample-window-ms: 10000
                    client-version-overrides:
                      '1_21': { sample-window-ms: -1000 }
                """);

        assertEquals(24.0, VersionedThresholds.resolveForClient(config, "1.8",
                "1.21.11", "flight", "required-hover-samples", 24.0));
        assertEquals(10000.0, VersionedThresholds.resolveForClient(config, "1.21",
                "1.21.11", "fast-break", "sample-window-ms", 10000.0));
    }

    @Test
    void clientOverrideDetectorOnlyReportsNonemptySections() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  reach:
                    client-version-overrides: {}
                  movement:
                    client-version-overrides:
                      '1_21': { max-horizontal-distance-per-tick: 1.2 }
                """);
        assertTrue(VersionedThresholds.hasClientOverrides(config));
        config.set("checks.movement.client-version-overrides", null);
        assertFalse(VersionedThresholds.hasClientOverrides(config));
    }

    @Test
    void packetAndPlacementThresholdsUseClientOverrides() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  packet-rate:
                    max-movement-packets-per-second: 45
                    violation-add: 1.0
                    client-version-overrides:
                      '1_8': { max-movement-packets-per-second: 35, violation-add: 1.5 }
                  click-rate:
                    max-attacks-per-window: 24
                    minimum-pattern-cps: 5.0
                    client-version-overrides:
                      '1_21': { max-attacks-per-window: 27, minimum-pattern-cps: 6.0 }
                  container-steal:
                    max-transfers-per-window: 28
                    client-version-overrides:
                      '1_8': { max-transfers-per-window: 30 }
                  scaffold-signal:
                    minimum-downward-pitch: 65.0
                    client-version-overrides:
                      '1_21_11': { minimum-downward-pitch: 68.0 }
                """);

        assertEquals(35.0, VersionedThresholds.resolveForClient(config, "1.8",
                "1.21.11", "packet-rate", "max-movement-packets-per-second", 40.0));
        assertEquals(1.5, VersionedThresholds.resolveForClient(config, "1.8",
                "1.21.11", "packet-rate", "violation-add", 1.0));
        assertEquals(27.0, VersionedThresholds.resolveForClient(config, "1.21.11",
                "1.21.11", "click-rate", "max-attacks-per-window", 20.0));
        assertEquals(30.0, VersionedThresholds.resolveForClient(config, "1.8",
                "1.21.11", "container-steal", "max-transfers-per-window", 24.0));
        assertEquals(6.0, VersionedThresholds.resolveForClient(config, "1.21.11",
                "1.21.11", "click-rate", "minimum-pattern-cps", 5.0));
        assertEquals(68.0, VersionedThresholds.resolveForClient(config, "1.21.11",
                "1.21.11", "scaffold-signal", "minimum-downward-pitch", 60.0));
    }

    @Test
    void violationWeightCanBeTunedForOlderAndNewerClients() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  movement:
                    violation-add: 1.0
                    client-version-overrides:
                      '1_8': { violation-add: 1.5 }
                      '1_21': { violation-add: 0.75 }
                """);

        assertEquals(1.5, VersionedThresholds.resolveForClient(config, "1.8",
                "1.21.11", "movement", "violation-add", 1.0));
        assertEquals(0.75, VersionedThresholds.resolveForClient(config, "1.21.11",
                "1.21.11", "movement", "violation-add", 1.0));
        assertEquals(1.0, VersionedThresholds.resolveForClient(config, null,
                "1.21.11", "movement", "violation-add", 1.0));
    }

    @Test
    void alertAndDecayThresholdsCanBeCustomizedPerClientVersion() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  movement:
                    alert-vl: 3.0
                    alert-cooldown-ms: 3000
                    violation-decay-per-second: 0.25
                    kick-vl: 12.0
                    client-version-overrides:
                      '1_8':
                        alert-vl: 4.0
                        alert-cooldown-ms: 5000
                        violation-decay-per-second: 0.10
                        kick-vl: 15.0
                """);

        assertEquals(4.0, VersionedThresholds.resolveForClient(config, "1.8",
                "1.21.11", "movement", "alert-vl", 3.0));
        assertEquals(5000.0, VersionedThresholds.resolveForClient(config, "1.8",
                "1.21.11", "movement", "alert-cooldown-ms", 3000.0));
        assertEquals(0.10, VersionedThresholds.resolveForClient(config, "1.8",
                "1.21.11", "movement", "violation-decay-per-second", 0.25));
        assertEquals(15.0, VersionedThresholds.resolveForClient(config, "1.8",
                "1.21.11", "movement", "kick-vl", 12.0));
    }

    @Test
    void velocityGraceBoundsAreValidatedForDefaultsAndVersionOverrides() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  movement:
                    velocity-grace-ms: 300
                    version-overrides:
                      '1_21': { velocity-grace-ms: 6000 }
                  movement-physics:
                    velocity-grace-ms: -1
                    version-overrides: {}
                """);

        assertEquals(300.0, VersionedThresholds.resolve(config, "1.21.11",
                "movement", "velocity-grace-ms", 250.0));
        assertEquals(250.0, VersionedThresholds.resolve(config, "1.21.11",
                "movement-physics", "velocity-grace-ms", 250.0));
        assertTrue(VersionedThresholds.validateOverrides(config).stream()
                .anyMatch(w -> w.contains("velocity-grace-ms")));
        assertTrue(VersionedThresholds.validateConfiguredThresholds(config).stream()
                .anyMatch(w -> w.contains("velocity-grace-ms")));
    }

    @Test
    void packetSanityThresholdsValidateTheEffectiveRangesUsedByTheDetector() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                checks:
                  packet-sanity:
                    max-horizontal-step-per-packet: 16.0
                    max-vertical-step-per-packet: 16.0
                    baseline-reset-gap-ms: 1500
                    max-absolute-coordinate: 30000000.0
                    max-absolute-y: 4096.0
                    version-overrides:
                      '1_21':
                        max-horizontal-step-per-packet: 0.5
                        baseline-reset-gap-ms: 100
                    client-version-overrides:
                      '1_21':
                        max-vertical-step-per-packet: 129
                """);

        assertEquals(16.0, VersionedThresholds.resolve(config, "1.21.11",
                "packet-sanity", "max-horizontal-step-per-packet", 16.0));
        assertEquals(1500.0, VersionedThresholds.resolve(config, "1.21.11",
                "packet-sanity", "baseline-reset-gap-ms", 1500.0));
        assertEquals(16.0, VersionedThresholds.resolveForClient(config, "1.21",
                "1.21.11", "packet-sanity", "max-vertical-step-per-packet", 16.0));

        java.util.List<String> warnings = VersionedThresholds.validateOverrides(config);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("max-horizontal-step-per-packet must be between 1 and 128")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("baseline-reset-gap-ms must be between 250 and 10000 milliseconds")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("max-vertical-step-per-packet must be between 1 and 128")));
    }

    @Test
    void blankVersionUsesNormalSetting() throws Exception {
        YamlConfiguration config = config("      '1_21':\n        max-distance: 3.7\n");

        assertEquals(3.6, VersionedThresholds.resolve(config, "", "reach", "max-distance", 3.5));
    }
}

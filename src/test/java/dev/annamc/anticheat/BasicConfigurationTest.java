package dev.annamc.anticheat;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BasicConfigurationTest {
    private String config() throws Exception {
        // Read all modular configuration files and ignore comments/indentation so tests assert active option names.
        return java.util.stream.Stream.of(
                        "config.yml", "checks.yml", "discord.yml", "database.yml", "punishments.yml", "security.yml")
                .flatMap(name -> {
                    try {
                        return Files.readAllLines(Path.of("src/main/resources", name)).stream();
                    } catch (Exception ex) {
                        throw new java.io.UncheckedIOException(new java.io.IOException("Could not read " + name, ex));
                    }
                })
                .filter(line -> !line.stripLeading().startsWith("#"))
                .map(String::strip)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Test void checksExposeThresholdDecayAndCooldownControls() throws Exception {
        String yaml = config();
        assertTrue(yaml.contains("base-horizontal-per-tick"));
        assertTrue(yaml.contains("required-consecutive-outliers"));
        assertTrue(yaml.contains("minimum-observed-displacement"));
        assertTrue(yaml.contains("rotation-quantization-degrees"));
        assertTrue(yaml.contains("max-movement-packets-per-second"));
        assertTrue(yaml.contains("required-hover-samples: 24"));
        assertTrue(yaml.contains("base-damage-allowance: 10.0"));
        assertTrue(yaml.contains("damage-per-fall-block-allowance: 4.5"));
        assertTrue(yaml.contains("attack-packets-enabled: true"));
        assertTrue(yaml.contains("max-attacks-per-window: 24"));
        assertTrue(yaml.contains("minimum-over-limit-windows: 3"));
        assertTrue(yaml.contains("maximum-hit-angle-degrees"));
        assertTrue(yaml.contains("minimum-consecutive-placements"));
        assertTrue(yaml.contains("ping-window-extension-per-ms"));
        assertTrue(yaml.contains("violation-decay-per-second"));
        assertTrue(yaml.contains("kick-vl"));
        assertTrue(yaml.contains("alert-cooldown-ms"));
        assertTrue(yaml.contains("require-independent-families: true"));
        assertTrue(yaml.contains("minimum-independent-families: 2"));
        assertTrue(yaml.contains("minimum-signals-per-family: 2"));
        assertTrue(yaml.contains("type: \"KICK\""));
        assertTrue(yaml.contains("threshold-vl: 10.0"));
        assertTrue(yaml.contains("cooldown-ms: 30000"));
        assertTrue(yaml.contains("commands: []"));
        assertTrue(yaml.contains("correlation-window-ms: 12000"));
        assertTrue(yaml.contains("max-detail-length: 1200"));
        assertTrue(yaml.contains("violation-retention-hours: 24"));
        assertTrue(yaml.contains("block-bulk-commands: true"));
        assertTrue(yaml.contains("explosion-block-limit: true"));
        assertTrue(yaml.contains("integrity-hash-enabled: true"));
        assertTrue(yaml.contains("static-scan-enabled: true"));
        assertTrue(yaml.contains("minimum-risk-score: 5"));
        assertTrue(yaml.contains("alert-changed-plugin-jars: true"));
        assertTrue(yaml.contains("flag-suspicious-filenames: true"));
        assertTrue(yaml.contains("suspicious-filename-keywords:"));
    }

    @Test void experimentalChecksAreDisabledByDefault() throws Exception {
        String yaml = config();
        assertTrue(yaml.contains("movement-physics:\nenabled: false"));
        assertTrue(yaml.contains("flight:\nenabled: false"));
        assertTrue(yaml.contains("knockback:\nenabled: false"));
        assertTrue(yaml.contains("reach:\nenabled: false"));
        assertTrue(yaml.contains("mace-damage:\nenabled: false"));
        assertTrue(yaml.contains("click-rate:\nenabled: false"));
        assertTrue(yaml.contains("combat-pattern:\nenabled: false"));
        assertTrue(yaml.contains("scaffold-signal:\nenabled: false"));
        assertTrue(yaml.contains("no-fall:\nenabled: false"));
        assertTrue(yaml.contains("packet-rate:\nenabled: false"));
        assertTrue(yaml.contains("timer:\nenabled: false"));
        assertTrue(yaml.contains("wall-hit:\nenabled: false"));
        assertTrue(yaml.contains("nuker-burst:\nenabled: false"));
        assertTrue(yaml.contains("fast-break:\nenabled: false"));
    }

    @Test void allPunishmentsAreIndividuallyConfigurableAndDisabledByDefault() throws Exception {
        String yaml = config();
        assertTrue(yaml.contains("actions:\nmovement:\nenabled: false"));
        assertTrue(yaml.contains("movement-physics:\nenabled: false"));
        assertTrue(yaml.contains("knockback:\nenabled: false"));
        assertTrue(yaml.contains("reach:\nenabled: false"));
        assertTrue(yaml.contains("click-rate:\nenabled: false"));
        assertTrue(yaml.contains("combat-pattern:\nenabled: false"));
        assertTrue(yaml.contains("scaffold-signal:\nenabled: false"));
        assertTrue(yaml.contains("no-fall:\nenabled: false"));
        assertTrue(yaml.contains("packet-rate:\nenabled: false"));
        assertTrue(yaml.contains("timer:\nenabled: false"));
        // One disabled wall-hit option must exist for detection and one for punishment.
        assertEquals(2, yaml.split("wall-hit:\\nenabled: false", -1).length - 1);
        assertEquals(2, yaml.split("nuker-burst:\\nenabled: false", -1).length - 1);
        assertEquals(2, yaml.split("fast-break:\\nenabled: false", -1).length - 1);
    }

    @Test void forceOpHasCommandBlockingAndAuditControls() throws Exception {
        String yaml = config();
        assertTrue(yaml.contains("cancel-player-op-commands: true"));
        assertTrue(yaml.contains("detect-op-grants: true"));
        assertTrue(yaml.contains("detect-op-removals: true"));
        assertTrue(yaml.contains("persist-operator-baseline: true"));
        assertTrue(yaml.contains("protected-command-prefixes:"));
        assertTrue(yaml.contains("audit-console-op-commands: true"));
    }
}

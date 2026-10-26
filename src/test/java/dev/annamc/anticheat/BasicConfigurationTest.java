package dev.annamc.anticheat;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BasicConfigurationTest {
    private String config() throws Exception {
        // Read all modular configuration files and ignore comments/indentation so tests assert active option names.
        return java.util.stream.Stream.of(
                        "config.yml", "checks.yml", "discord.yml", "database.yml", "punishments.yml")
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
        assertTrue(yaml.contains("maximum-hit-angle-degrees"));
        assertTrue(yaml.contains("minimum-consecutive-placements"));
        assertTrue(yaml.contains("ping-window-extension-per-ms"));
        assertTrue(yaml.contains("violation-decay-per-second"));
        assertTrue(yaml.contains("kick-vl"));
        assertTrue(yaml.contains("alert-cooldown-ms"));
        assertTrue(yaml.contains("require-independent-families: true"));
        assertTrue(yaml.contains("minimum-independent-families: 2"));
        assertTrue(yaml.contains("correlation-window-ms: 12000"));
        assertTrue(yaml.contains("max-detail-length: 1200"));
        assertTrue(yaml.contains("violation-retention-hours: 24"));
    }

    @Test void experimentalChecksAreDisabledByDefault() throws Exception {
        String yaml = config();
        assertTrue(yaml.contains("movement-physics:\nenabled: false"));
        assertTrue(yaml.contains("knockback:\nenabled: false"));
        assertTrue(yaml.contains("reach:\nenabled: false"));
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
        assertTrue(yaml.contains("persist-operator-baseline: true"));
        assertTrue(yaml.contains("protected-command-prefixes:"));
        assertTrue(yaml.contains("audit-console-op-commands: true"));
    }
}

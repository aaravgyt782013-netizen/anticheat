package dev.annamc.anticheat;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BasicConfigurationTest {
    private String config() throws Exception {
        // Ignore explanatory comments and indentation so assertions test YAML keys, not formatting.
        return Files.readAllLines(Path.of("src/main/resources/config.yml")).stream()
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

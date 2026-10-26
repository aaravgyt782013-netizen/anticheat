package dev.annamc.anticheat;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BasicConfigurationTest {
    private String config() throws Exception {
        // Ignore explanatory YAML comments so assertions verify the actual option structure.\n        return Files.readAllLines(Path.of("src/main/resources/config.yml")).stream()\n                .filter(line -> !line.stripLeading().startsWith("#"))\n                .collect(java.util.stream.Collectors.joining("\\n"));
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
        assertTrue(yaml.contains("movement-physics:\n    enabled: false"));
        assertTrue(yaml.contains("knockback:\n    enabled: false"));
        assertTrue(yaml.contains("reach:\n    enabled: false"));
        assertTrue(yaml.contains("click-rate:\n    enabled: false"));
        assertTrue(yaml.contains("combat-pattern:\n    enabled: false"));
        assertTrue(yaml.contains("scaffold-signal:\n    enabled: false"));
        assertTrue(yaml.contains("no-fall:\n    enabled: false"));
        assertTrue(yaml.contains("packet-rate:\n    enabled: false"));
        assertTrue(yaml.contains("timer:\n    enabled: false"));
    }

    @Test void allPunishmentsAreIndividuallyConfigurableAndDisabledByDefault() throws Exception {
        String yaml = config();
        assertTrue(yaml.contains("actions:\n    movement:\n      enabled: false"));
        assertTrue(yaml.contains("movement-physics:\n      enabled: false"));
        assertTrue(yaml.contains("knockback:\n      enabled: false"));
        assertTrue(yaml.contains("reach:\n      enabled: false"));
        assertTrue(yaml.contains("click-rate:\n      enabled: false"));
        assertTrue(yaml.contains("combat-pattern:\n      enabled: false"));
        assertTrue(yaml.contains("scaffold-signal:\n      enabled: false"));
        assertTrue(yaml.contains("no-fall:\n      enabled: false"));
        assertTrue(yaml.contains("packet-rate:\n      enabled: false"));
        assertTrue(yaml.contains("timer:\n      enabled: false"));
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

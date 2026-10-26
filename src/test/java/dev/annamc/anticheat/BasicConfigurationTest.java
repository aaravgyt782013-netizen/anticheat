package dev.annamc.anticheat;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BasicConfigurationTest {
    private String config() throws Exception {
        return Files.readString(Path.of("src/main/resources/config.yml"));
    }

    @Test void movementThresholdAndDecayAreConfigurable() throws Exception {
        String yaml = config();
        assertTrue(yaml.contains("max-horizontal-distance-per-tick"));
        assertTrue(yaml.contains("violation-decay-per-second"));
        assertTrue(yaml.contains("kick-vl"));
        assertTrue(yaml.contains("alert-cooldown-ms"));
    }

    @Test void experimentalChecksAreDisabledByDefault() throws Exception {
        String yaml = config();
        assertTrue(yaml.contains("reach:\n    enabled: false"));
        assertTrue(yaml.contains("click-rate:\n    enabled: false"));
        assertTrue(yaml.contains("scaffold-signal:\n    enabled: false"));
        assertTrue(yaml.contains("packet-rate:\n    enabled: false"));
    }

    @Test void eachImplementedCheckHasAnExplicitPunishmentToggle() throws Exception {
        String yaml = config();
        assertTrue(yaml.contains("actions:\n    movement:\n      enabled: true"));
        assertTrue(yaml.contains("reach:\n      enabled: false"));
        assertTrue(yaml.contains("click-rate:\n      enabled: false"));
        assertTrue(yaml.contains("scaffold-signal:\n      enabled: false"));
    }
}

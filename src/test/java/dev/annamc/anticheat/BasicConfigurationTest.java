package dev.annamc.anticheat;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BasicConfigurationTest {
    @Test void movementThresholdAndDecayAreConfigurable() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/config.yml"));
        assertTrue(yaml.contains("max-horizontal-distance-per-tick"));
        assertTrue(yaml.contains("violation-decay-per-second"));
        assertTrue(yaml.contains("kick-vl"));
    }
    @Test void packetRateDetectionIsDisabledByDefault() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/config.yml"));
        assertTrue(yaml.contains("packet-rate:\n    enabled: false"));
    }
}

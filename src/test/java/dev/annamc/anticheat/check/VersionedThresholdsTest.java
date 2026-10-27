package dev.annamc.anticheat.check;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VersionedThresholdsTest {
    private YamlConfiguration config() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.load(new File("src/main/resources/checks.yml"));
        return config;
    }

    @Test
    void exactVersionOverrideWinsOverMajorMinorAndDefault() throws Exception {
        YamlConfiguration config = config();
        config.set("checks.reach.max-distance", 3.6);
        config.set("checks.reach.version-overrides.1.21.max-distance", 3.7);
        config.set("checks.reach.version-overrides.1.21.11.max-distance", 3.8);

        assertEquals(3.8, VersionedThresholds.resolve(config, "1.21.11", "reach", "max-distance", 3.5));
    }

    @Test
    void majorMinorOverrideIsUsedWhenExactVersionIsMissing() throws Exception {
        YamlConfiguration config = config();
        config.set("checks.reach.max-distance", 3.6);
        config.set("checks.reach.version-overrides.1.21.max-distance", 3.7);

        assertEquals(3.7, VersionedThresholds.resolve(config, "1.21.11", "reach", "max-distance", 3.5));
    }

    @Test
    void normalSettingIsUsedWhenNoVersionOverrideExists() throws Exception {
        YamlConfiguration config = config();
        config.set("checks.reach.max-distance", 3.6);

        assertEquals(3.6, VersionedThresholds.resolve(config, "1.21.11", "reach", "max-distance", 3.5));
    }

    @Test
    void fallbackIsUsedWhenNeitherOverrideNorNormalSettingExists() throws Exception {
        YamlConfiguration config = config();

        assertEquals(3.5, VersionedThresholds.resolve(config, "1.21.11", "reach", "missing-setting", 3.5));
    }

    @Test
    void blankVersionUsesNormalSetting() throws Exception {
        YamlConfiguration config = config();
        config.set("checks.reach.max-distance", 3.6);

        assertEquals(3.6, VersionedThresholds.resolve(config, "", "reach", "max-distance", 3.5));
    }
}

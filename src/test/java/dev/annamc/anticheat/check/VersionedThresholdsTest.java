package dev.annamc.anticheat.check;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void blankVersionUsesNormalSetting() throws Exception {
        YamlConfiguration config = config("      '1_21':\n        max-distance: 3.7\n");

        assertEquals(3.6, VersionedThresholds.resolve(config, "", "reach", "max-distance", 3.5));
    }
}

package dev.annamc.anticheat.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckConfigModulesTest {
    @Test
    void migratesLegacyThresholdsAndPreservesModuleCommentsAndUnrelatedChecks() throws Exception {
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.loadFromString("""
                checks:
                  movement:
                    enabled: true
                    max-horizontal-distance-per-tick: 1.57
                    version-overrides:
                      '1_21_11':
                        max-horizontal-distance-per-tick: 1.61
                  reach:
                    enabled: true
                    max-distance: 3.9
                """);

        YamlConfiguration module = new YamlConfiguration();
        module.loadFromString("""
                # Movement module
                checks:
                  movement:
                    # Enable movement check.
                    enabled: false
                    # Conservative default distance.
                    max-horizontal-distance-per-tick: 1.25
                    version-overrides: {}
                """);

        CheckConfigModules.copySelectedChecks(legacy, module, List.of("movement", "flight"));

        assertTrue(module.getBoolean("checks.movement.enabled"));
        assertEquals(1.57, module.getDouble("checks.movement.max-horizontal-distance-per-tick"));
        assertEquals(1.61, module.getDouble(
                "checks.movement.version-overrides.1_21_11.max-horizontal-distance-per-tick"));
        assertEquals("Enable movement check.", module.getComments("checks.movement.enabled").get(0));
        assertFalse(module.contains("checks.reach"),
                "Only the detector sections assigned to this module should be copied");
        assertFalse(module.getBoolean("checks.flight.enabled"),
                "Missing legacy sections should remain at their bundled defaults when copied separately");
    }


    @Test
    void moduleAssignmentMapMatchesBundledResourcesAndLegacyCompatibilityFile() {
        java.util.Set<String> assigned = new java.util.HashSet<>();
        for (var entry : ModularConfigManager.CHECK_MODULES.entrySet()) {
            YamlConfiguration module = YamlConfiguration.loadConfiguration(
                    new java.io.File("src/main/resources", entry.getKey()));
            var checks = module.getConfigurationSection("checks");
            assertNotNull(checks, entry.getKey() + " must contain a checks section");
            java.util.Set<String> actual = checks.getKeys(false);
            assertEquals(java.util.Set.copyOf(entry.getValue()), actual,
                    entry.getKey() + " must match the detector keys selected by the loader");
            for (String detector : entry.getValue()) {
                assertTrue(assigned.add(detector), "Detector " + detector + " must be assigned to exactly one module");
            }
        }
        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(
                new java.io.File("src/main/resources", "checks.yml"));
        assertEquals(legacy.getConfigurationSection("checks").getKeys(false), assigned,
                "Legacy checks.yml should keep defaults for every detector so upgrades can migrate old settings.");
    }

    @Test
    void missingLegacySectionDoesNotEraseBundledModuleDefaults() throws Exception {
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.loadFromString("checks: {}\n");
        YamlConfiguration module = new YamlConfiguration();
        module.loadFromString("""
                checks:
                  movement:
                    enabled: false
                    max-horizontal-distance-per-tick: 1.25
                """);

        CheckConfigModules.copySelectedChecks(legacy, module, List.of("movement"));

        assertFalse(module.getBoolean("checks.movement.enabled"));
        assertEquals(1.25, module.getDouble("checks.movement.max-horizontal-distance-per-tick"));
    }
}

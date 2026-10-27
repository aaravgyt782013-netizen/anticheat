package dev.annamc.anticheat;

import org.junit.jupiter.api.Test;
import dev.annamc.anticheat.config.ConfigurationDiagnostics;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationYamlSyntaxTest {
    private static final List<String> YAML_FILES = List.of(
            "config.yml", "checks.yml", "discord.yml", "database.yml",
            "punishments.yml", "security.yml", "messages.yml",
            "messages_en.yml", "messages_hi.yml", "plugin.yml",
            "checks-movement.yml", "checks-combat.yml", "checks-world.yml",
            "checks-packets.yml", "checks-security.yml");

    @Test
    void everyBundledYamlFileParsesToAMapping() throws Exception {
        Yaml yaml = new Yaml();
        for (String file : YAML_FILES) {
            try (InputStream in = Files.newInputStream(Path.of("src/main/resources", file))) {
                Object parsed = yaml.load(in);
                assertNotNull(parsed, file + " must not be empty");
                assertInstanceOf(Map.class, parsed, file + " must have a YAML mapping at its root");
            }
        }
    }

    @Test
    void detectorConfigurationModulesCoverEveryDetectorExactlyOnce() throws Exception {
        Yaml yaml = new Yaml();
        List<String> files = List.of("checks-movement.yml", "checks-combat.yml",
                "checks-world.yml", "checks-packets.yml", "checks-security.yml");
        java.util.Set<String> found = new java.util.HashSet<>();
        for (String file : files) {
            try (InputStream in = Files.newInputStream(Path.of("src/main/resources", file))) {
                Map<?, ?> root = yaml.load(in);
                assertInstanceOf(Map.class, root.get("checks"), file + " must contain checks");
                Map<?, ?> checks = (Map<?, ?>) root.get("checks");
                for (Object key : checks.keySet()) {
                    assertTrue(found.add(String.valueOf(key)),
                            "Detector " + key + " must appear in exactly one module");
                }
            }
        }
        java.util.Set<String> expected = java.util.Set.of("movement", "movement-physics", "flight", "jesus",
                "spider-signal", "knockback", "reach", "mace-damage", "wall-hit", "click-rate",
                "combat-pattern", "block-reach", "nuker-burst", "fast-break", "container-steal", "container-move",
                "scaffold-signal", "no-fall", "packet-rate", "timer", "force-op");
        assertEquals(expected, found);
        java.util.Set<String> diagnostics = new java.util.HashSet<>(ConfigurationDiagnostics.DETECTORS);
        java.util.Set<String> punishmentManagedChecks = new java.util.HashSet<>(expected);
        punishmentManagedChecks.remove("force-op");
        assertEquals(punishmentManagedChecks, diagnostics,
                "Every gameplay detector should be represented in diagnostics; Force OP uses its own security guard.");
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources", "punishments.yml"))) {
            Map<?, ?> root = yaml.load(in);
            Map<?, ?> punishments = (Map<?, ?>) root.get("punishments");
            Map<?, ?> actions = (Map<?, ?>) punishments.get("actions");
            assertEquals(punishmentManagedChecks, actions.keySet().stream()
                    .map(String::valueOf).collect(java.util.stream.Collectors.toSet()),
                    "Every gameplay detector should have an independent configurable punishment entry.");
        }
    }

    @Test
    void packetSanityIsPresentConfigurableAndOptIn() throws Exception {
        Yaml yaml = new Yaml();
        for (String file : List.of("checks-packets.yml", "checks.yml")) {
            try (InputStream in = Files.newInputStream(Path.of("src/main/resources", file))) {
                Map<?, ?> root = yaml.load(in);
                Map<?, ?> checks = (Map<?, ?>) root.get("checks");
                Map<?, ?> options = (Map<?, ?>) checks.get("packet-sanity");
                assertNotNull(options, file + " must include packet-sanity");
                assertEquals(Boolean.FALSE, options.get("enabled"),
                        "Packet sanity must remain opt-in until staging validation is complete.");
                assertNotNull(options.get("max-horizontal-step-per-packet"));
                assertNotNull(options.get("max-vertical-step-per-packet"));
                assertNotNull(options.get("max-absolute-coordinate"));
                assertNotNull(options.get("max-absolute-y"));
                assertNotNull(options.get("baseline-reset-gap-ms"));
                assertNotNull(options.get("version-overrides"));
                assertNotNull(options.get("client-version-overrides"));
            }
        }
    }

    @Test
    void movementVelocityGraceIsConfigurableInBothModernAndLegacyFiles() throws Exception {
        Yaml yaml = new Yaml();
        for (String file : List.of("checks-movement.yml", "checks.yml")) {
            try (InputStream in = Files.newInputStream(Path.of("src/main/resources", file))) {
                Map<?, ?> root = yaml.load(in);
                Map<?, ?> checks = (Map<?, ?>) root.get("checks");
                for (String detector : List.of("movement", "movement-physics")) {
                    Map<?, ?> options = (Map<?, ?>) checks.get(detector);
                    assertEquals(300, ((Number) options.get("velocity-grace-ms")).intValue(),
                            file + " must include a configurable velocity grace for " + detector);
                }
            }
        }
    }

    @Test
    void securityDefaultsKeepRiskyAutomaticActionsOff() throws Exception {
        Yaml yaml = new Yaml();
        Map<?, ?> root;
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/security.yml"))) {
            root = yaml.load(in);
        }
        Map<?, ?> security = (Map<?, ?>) root.get("security");
        Map<?, ?> antinuke = (Map<?, ?>) security.get("antinuke");
        Map<?, ?> monitor = (Map<?, ?>) security.get("plugin-monitor");

        assertEquals(Boolean.TRUE, antinuke.get("enabled"));
        assertEquals(Boolean.TRUE, antinuke.get("block-bulk-commands"));
        assertEquals(Boolean.TRUE, antinuke.get("explosion-block-limit"));
        assertEquals(Boolean.FALSE, antinuke.get("block-break-rate-limit"));
        assertEquals(Boolean.TRUE, monitor.get("integrity-hash-enabled"));
        assertEquals(Boolean.TRUE, monitor.get("static-scan-enabled"));
        assertEquals(Boolean.FALSE, monitor.get("report-scan-summary"));
    }
}

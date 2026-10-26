package dev.annamc.anticheat;

import org.junit.jupiter.api.Test;
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
            "messages_en.yml", "messages_hi.yml", "plugin.yml");

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

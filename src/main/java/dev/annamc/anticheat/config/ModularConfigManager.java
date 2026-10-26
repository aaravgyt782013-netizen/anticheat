package dev.annamc.anticheat.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Loads the user-editable configuration into separate files while preserving
 * settings from older ANNA AC releases that stored everything in config.yml.
 */
public final class ModularConfigManager {
    private static final String[] FILES = {"checks.yml", "discord.yml", "database.yml", "punishments.yml", "security.yml"};
    private final JavaPlugin plugin;

    public ModularConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfiguration main = plugin.getConfig();
        boolean mainDefaultsAdded = mergeMissingDefaults(main, "config.yml");
        if (mainDefaultsAdded) plugin.saveConfig();
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Cannot create ANNA AC data directory: " + folder);
        }

        boolean migrationPending = !new File(folder, "modular-config-v2.done").isFile();

        boolean checksCreated = ensureFile("checks.yml", false);
        boolean discordCreated = ensureFile("discord.yml", false);
        boolean databaseCreated = ensureFile("database.yml", false);
        boolean punishmentsCreated = ensureFile("punishments.yml", true);
        boolean securityCreated = ensureFile("security.yml", false);

        YamlConfiguration checks = YamlConfiguration.loadConfiguration(new File(folder, "checks.yml"));
        YamlConfiguration discord = YamlConfiguration.loadConfiguration(new File(folder, "discord.yml"));
        YamlConfiguration database = YamlConfiguration.loadConfiguration(new File(folder, "database.yml"));
        YamlConfiguration punishments = YamlConfiguration.loadConfiguration(new File(folder, "punishments.yml"));
        YamlConfiguration security = YamlConfiguration.loadConfiguration(new File(folder, "security.yml"));

        boolean checksDefaultsAdded = mergeMissingDefaults(checks, "checks.yml");
        boolean discordDefaultsAdded = mergeMissingDefaults(discord, "discord.yml");
        boolean databaseDefaultsAdded = mergeMissingDefaults(database, "database.yml");
        boolean punishmentsDefaultsAdded = mergeMissingDefaults(punishments, "punishments.yml");
        boolean securityDefaultsAdded = mergeMissingDefaults(security, "security.yml");
        if (checksDefaultsAdded) saveConfiguration(checks, "checks.yml");
        if (discordDefaultsAdded) saveConfiguration(discord, "discord.yml");
        if (databaseDefaultsAdded) saveConfiguration(database, "database.yml");
        if (punishmentsDefaultsAdded) saveConfiguration(punishments, "punishments.yml");
        if (securityDefaultsAdded) saveConfiguration(security, "security.yml");

        if (checksCreated) migrateSection(main, "checks", checks, "checks");
        if (discordCreated) migrateSection(main, "alerts", discord, "alerts");
        if (punishmentsCreated) migrateSection(main, "punishments", punishments, "punishments");
        if (securityCreated) migrateSection(main, "security", security, "security");
        if (databaseCreated) migrateDatabaseSettings(main, database);

        saveIfMigrated(checksCreated, checks, "checks.yml");
        saveIfMigrated(discordCreated, discord, "discord.yml");
        saveIfMigrated(databaseCreated, database, "database.yml");
        saveIfMigrated(punishmentsCreated, punishments, "punishments.yml");
        saveIfMigrated(securityCreated, security, "security.yml");

        if (migrationPending) {
            boolean hasLegacySections = main.isConfigurationSection("checks")
                    || main.isConfigurationSection("alerts")
                    || main.isConfigurationSection("punishments")
                    || main.isConfigurationSection("security")
                    || main.contains("settings.evidence-retention-days")
                    || main.contains("settings.max-evidence-records-per-player")
                    || main.contains("settings.max-evidence-detail-length")
                    || main.contains("settings.violation-retention-hours");
            if (hasLegacySections) {
                backupLegacyMainConfig();
                main.set("checks", null);
                main.set("alerts", null);
                main.set("punishments", null);
                main.set("security", null);
                main.set("settings.evidence-retention-days", null);
                main.set("settings.max-evidence-records-per-player", null);
                main.set("settings.max-evidence-detail-length", null);
                main.set("settings.violation-retention-hours", null);
                plugin.saveConfig();
                plugin.getLogger().info("Migrated legacy options into checks.yml, discord.yml, database.yml, punishments.yml and security.yml. "
                        + "A config.yml.legacy.bak backup was retained.");
            }
            try {
                Files.writeString(new File(folder, "modular-config-v2.done").toPath(),
                        "ANNA AC modular configuration migration completed.\n");
            } catch (IOException e) {
                throw new IllegalStateException("Could not write modular-config migration marker.", e);
            }
        }

        overlay(checks, "checks", main);
        overlay(discord, "alerts", main);
        overlay(database, "database", main);
        overlay(punishments, "punishments", main);
        overlay(security, "security", main);
        if (securityCreated && !migrationPending && main.isConfigurationSection("security")) {
            backupLegacyMainConfig();
            main.set("security", null);
            plugin.saveConfig();
        }
        plugin.getLogger().info("Loaded checks.yml, discord.yml, database.yml, punishments.yml and security.yml.");
    }

    /**
     * Adds newly introduced defaults without overwriting any explicit user value.
     * This lets existing installations gain newly configurable options on upgrade.
     */
    private boolean mergeMissingDefaults(ConfigurationSection target, String resource) {
        try (var stream = plugin.getResource(resource)) {
            if (stream == null) {
                plugin.getLogger().warning("Could not find bundled defaults for " + resource + ".");
                return false;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            return mergeNode(target, defaults, "");
        } catch (IOException e) {
            throw new IllegalStateException("Could not load bundled defaults for " + resource, e);
        }
    }

    private boolean mergeNode(ConfigurationSection target, ConfigurationSection defaults, String parent) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            String path = parent.isEmpty() ? key : parent + "." + key;
            if (defaults.isConfigurationSection(key)) {
                if (!target.isConfigurationSection(path)) {
                    if (target.contains(path)) {
                        plugin.getLogger().warning("Cannot merge default section '" + path
                                + "' because the existing value is not a section.");
                        continue;
                    }
                    target.createSection(path);
                    copyComments(defaults, target, key, path);
                    changed = true;
                }
                ConfigurationSection targetChild = target.getConfigurationSection(path);
                ConfigurationSection defaultChild = defaults.getConfigurationSection(key);
                if (targetChild != null && defaultChild != null) {
                    // Keep the root target and build fully-qualified paths in recursion.
                    changed |= mergeNode(target, defaultChild, path);
                }
            } else if (!target.contains(path)) {
                copyComments(defaults, target, key, path);
                target.set(path, defaults.get(key));
                changed = true;
            }
        }
        return changed;
    }

    private void copyComments(ConfigurationSection source, ConfigurationSection target,
                              String sourcePath, String targetPath) {
        target.setComments(targetPath, source.getComments(sourcePath));
        target.setInlineComments(targetPath, source.getInlineComments(sourcePath));
    }

    private void saveConfiguration(YamlConfiguration configuration, String name) {
        try {
            configuration.save(new File(plugin.getDataFolder(), name));
        } catch (IOException e) {
            throw new IllegalStateException("Could not save upgraded configuration " + name, e);
        }
    }

    private boolean ensureFile(String resource, boolean validatePunishments) {
        File target = new File(plugin.getDataFolder(), resource);
        boolean created = !target.isFile();

        if (validatePunishments && target.isFile()) {
            YamlConfiguration existing = YamlConfiguration.loadConfiguration(target);
            if (!existing.isConfigurationSection("punishments")) {
                backupLegacyPunishments(target);
                created = true;
            }
        }

        if (created) {
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("Cannot replace legacy configuration file: " + target);
            }
            plugin.saveResource(resource, false);
            plugin.getLogger().info("Created " + resource + (validatePunishments ? " (legacy file backed up if present)." : "."));
        }
        return created;
    }

    private void migrateSection(FileConfiguration legacy, String legacyRoot,
                                YamlConfiguration destination, String destinationRoot) {
        ConfigurationSection section = legacy.getConfigurationSection(legacyRoot);
        if (section == null) return;
        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key)) continue;
            destination.set(destinationRoot + "." + key, section.get(key));
        }
    }

    private void migrateDatabaseSettings(FileConfiguration legacy, YamlConfiguration destination) {
        Map<String, String> mappings = Map.of(
                "settings.evidence-retention-days", "database.retention-days",
                "settings.max-evidence-records-per-player", "database.max-records-per-player",
                "settings.max-evidence-detail-length", "database.max-detail-length",
                "settings.violation-retention-hours", "database.violation-retention-hours");
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            if (legacy.contains(entry.getKey())) destination.set(entry.getValue(), legacy.get(entry.getKey()));
        }
    }

    private void saveIfMigrated(boolean migrated, YamlConfiguration config, String name) {
        if (!migrated) return;
        try {
            config.save(new File(plugin.getDataFolder(), name));
        } catch (IOException e) {
            throw new IllegalStateException("Could not save migrated " + name, e);
        }
    }

    private void overlay(YamlConfiguration source, String root, FileConfiguration target) {
        ConfigurationSection section = source.getConfigurationSection(root);
        target.set(root, null);
        if (section == null) {
            plugin.getLogger().warning("Missing '" + root + "' section in its modular configuration file.");
            return;
        }
        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key)) continue;
            target.set(root + "." + key, section.get(key));
        }
    }

    private void backupLegacyMainConfig() {
        File source = new File(plugin.getDataFolder(), "config.yml");
        File backup = new File(plugin.getDataFolder(), "config.yml.legacy.bak");
        int suffix = 2;
        while (backup.exists()) backup = new File(plugin.getDataFolder(), "config.yml.legacy." + suffix++ + ".bak");
        try {
            Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            throw new IllegalStateException("Could not back up legacy config.yml before migration.", e);
        }
    }

    private void backupLegacyPunishments(File source) {
        File backup = new File(source.getParentFile(), "punishments.yml.legacy.bak");
        int suffix = 2;
        while (backup.exists()) backup = new File(source.getParentFile(), "punishments.yml.legacy." + suffix++ + ".bak");
        try {
            Files.move(source.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Backed up legacy punishments.yml to " + backup.getName() + ".");
        } catch (IOException e) {
            throw new IllegalStateException("Could not back up legacy punishments.yml.", e);
        }
    }
}

package dev.annamc.anticheat.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * Pure helpers for migrating legacy check values into subsystem configuration files.
 * Kept separate from plugin lifecycle code so migration behavior can be unit-tested.
 */
final class CheckConfigModules {
    private CheckConfigModules() {}

    static void copySelectedChecks(ConfigurationSection source, ConfigurationSection target,
                                   List<String> checkNames) {
        for (String check : checkNames) {
            String path = "checks." + check;
            ConfigurationSection sourceSection = source.getConfigurationSection(path);
            if (sourceSection == null) continue;

            // Overlay values but retain comments and defaults from the bundled module template.
            for (String key : sourceSection.getKeys(true)) {
                if (!sourceSection.isConfigurationSection(key)) {
                    target.set(path + "." + key, sourceSection.get(key));
                }
            }
        }
    }
}

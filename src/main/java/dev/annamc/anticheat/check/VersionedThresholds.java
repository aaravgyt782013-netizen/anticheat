package dev.annamc.anticheat.check;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Resolves a numeric check threshold without guessing defaults for unknown versions.
 * Bukkit treats dots as configuration path separators, so override keys use underscores
 * (for example "1_21_11" and "1_21") to preserve the complete version identifier.
 */
public final class VersionedThresholds {
    private VersionedThresholds() {}

    public static double resolve(FileConfiguration config, String version, String check,
                                 String key, double fallback) {
        String base = "checks." + check + ".";
        ConfigurationSection overrides = config.getConfigurationSection(base + "version-overrides");
        if (overrides != null && version != null && !version.isBlank()) {
            String exactKey = version.replace('.', '_');
            Double exact = numericValue(overrides.getValues(false).get(exactKey), key);
            if (exact != null) return exact;

            String[] parts = version.split("\\.");
            if (parts.length >= 2) {
                String majorMinor = parts[0] + "_" + parts[1];
                Double compatible = numericValue(overrides.getValues(false).get(majorMinor), key);
                if (compatible != null) return compatible;
            }
        }
        return config.getDouble(base + key, fallback);
    }

    private static Double numericValue(Object sectionValue, String key) {
        if (!(sectionValue instanceof ConfigurationSection section)) return null;
        Object value = section.get(key);
        if (!(value instanceof Number number)) return null;
        double result = number.doubleValue();
        return Double.isFinite(result) ? result : null;
    }
}

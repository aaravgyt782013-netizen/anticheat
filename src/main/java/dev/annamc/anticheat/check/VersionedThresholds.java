package dev.annamc.anticheat.check;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Resolves a numeric check threshold without guessing defaults for unknown versions.
 * Exact version overrides win, followed by major.minor, then the ordinary check value.
 */
public final class VersionedThresholds {
    private VersionedThresholds() {}

    public static double resolve(FileConfiguration config, String version, String check,
                                 String key, double fallback) {
        String base = "checks." + check + ".";
        if (version != null && !version.isBlank()) {
            String exact = base + "version-overrides." + version + "." + key;
            if (isNumeric(config, exact)) return config.getDouble(exact);

            String[] parts = version.split("\\.");
            if (parts.length >= 2) {
                String compatible = base + "version-overrides." + parts[0] + "." + parts[1] + "." + key;
                if (isNumeric(config, compatible)) return config.getDouble(compatible);
            }
        }
        return config.getDouble(base + key, fallback);
    }

    private static boolean isNumeric(FileConfiguration config, String path) {
        return config.isDouble(path) || config.isInt(path);
    }
}

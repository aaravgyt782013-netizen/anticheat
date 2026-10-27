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
            Double exact = numericValue(overrides.getValues(false).get(exactKey), check, key);
            if (exact != null) return exact;

            String[] parts = version.split("\\.");
            if (parts.length >= 2) {
                String majorMinor = parts[0] + "_" + parts[1];
                Double compatible = numericValue(overrides.getValues(false).get(majorMinor), check, key);
                if (compatible != null) return compatible;
            }
        }
        Object normalValue = config.get(base + key);
        if (normalValue instanceof Number number && Double.isFinite(number.doubleValue())
                && thresholdRangeWarning(check, key, number.doubleValue()) == null) {
            return number.doubleValue();
        }
        return fallback;
    }

    /**
     * Resolves client-specific overrides first, then server-specific overrides and normal
     * defaults. Bukkit configuration uses dotted path separators, so override map keys use
     * underscores (for example '1_21_11'). A missing/unknown client version never blocks
     * the server-version fallback.
     */
    public static double resolveForClient(FileConfiguration config, String clientVersion,
                                          String serverVersion, String check, String key,
                                          double fallback) {
        String base = "checks." + check + ".";
        ConfigurationSection overrides = config.getConfigurationSection(base + "client-version-overrides");
        if (overrides != null && clientVersion != null && !clientVersion.isBlank()) {
            String exactKey = clientVersion.replace('.', '_');
            Double exact = numericValue(overrides.getValues(false).get(exactKey), check, key);
            if (exact != null) return exact;

            String[] parts = clientVersion.split("\\.");
            if (parts.length >= 2) {
                String majorMinor = parts[0] + "_" + parts[1];
                Double compatible = numericValue(overrides.getValues(false).get(majorMinor), check, key);
                if (compatible != null) return compatible;
            }
        }
        return resolve(config, serverVersion, check, key, fallback);
    }

    /**
     * Reports malformed or ineffective override entries so configuration mistakes are
     * visible at startup/reload instead of silently falling back to default thresholds.
     */
    public static java.util.List<String> validateOverrides(FileConfiguration config) {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        ConfigurationSection checks = config.getConfigurationSection("checks");
        if (checks == null) return java.util.List.of("checks section is missing.");

        java.util.regex.Pattern versionKey = java.util.regex.Pattern.compile("^\\d+(?:_\\d+){1,2}$");
        for (String check : checks.getKeys(false)) {
            ConfigurationSection checkSection = checks.getConfigurationSection(check);
            if (checkSection == null) continue;
            for (String mapName : java.util.List.of("version-overrides", "client-version-overrides")) {
                ConfigurationSection overrides = checkSection.getConfigurationSection(mapName);
                if (overrides == null) continue;
                for (String version : overrides.getKeys(false)) {
                    String path = "checks." + check + "." + mapName + "." + version;
                    if (!versionKey.matcher(version).matches()) {
                        warnings.add(path + " should use an underscore-separated release key such as '1_21_11'.");
                    }
                    ConfigurationSection settings = overrides.getConfigurationSection(version);
                    if (settings == null) {
                        warnings.add(path + " must be a mapping of numeric threshold names to values.");
                        continue;
                    }
                    for (String setting : settings.getKeys(false)) {
                        Object value = settings.get(setting);
                        if (!checkSection.contains(setting)) {
                            warnings.add(path + "." + setting + " does not match a setting in checks." + check
                                    + " and may be ignored.");
                        }
                        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
                            warnings.add(path + "." + setting + " must be a finite numeric value.");
                            continue;
                        }
                        String rangeWarning = thresholdRangeWarning(check, setting, number.doubleValue());
                        if (rangeWarning != null) warnings.add(path + "." + setting + " " + rangeWarning);
                    }
                }
            }
        }
        return java.util.List.copyOf(warnings);
    }

    /**
     * Validates ordinary (non-versioned) numeric check settings at startup and reload.
     * Override maps are validated separately by {@link #validateOverrides(FileConfiguration)}.
     */
    public static java.util.List<String> validateConfiguredThresholds(FileConfiguration config) {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        ConfigurationSection checks = config.getConfigurationSection("checks");
        if (checks == null) return java.util.List.of("checks section is missing.");

        for (String check : checks.getKeys(false)) {
            ConfigurationSection section = checks.getConfigurationSection(check);
            if (section == null) continue;
            for (String setting : section.getKeys(false)) {
                if ("version-overrides".equals(setting) || "client-version-overrides".equals(setting)) continue;
                Object value = section.get(setting);
                if (!(value instanceof Number number)) continue;
                String warning = thresholdRangeWarning(check, setting, number.doubleValue());
                if (warning != null) warnings.add("checks." + check + "." + setting + " " + warning);
            }
        }
        return java.util.List.copyOf(warnings);
    }

    private static String thresholdRangeWarning(String check, String setting, double value) {
        if (setting == null || !Double.isFinite(value)) return "must be finite.";
        String key = setting.toLowerCase(java.util.Locale.ROOT);

        if (key.equals("velocity-grace-ms")) {
            return value < 0.0 || value > 5000.0 ? "must be between 0 and 5000 milliseconds." : null;
        }
        if (key.equals("baseline-reset-gap-ms")) {
            return value < 250.0 || value > 10_000.0
                    ? "must be between 250 and 10000 milliseconds." : null;
        }
        if (key.equals("max-horizontal-step-per-packet") || key.equals("max-vertical-step-per-packet")) {
            return value < 1.0 || value > 128.0 ? "must be between 1 and 128 blocks per packet." : null;
        }
        if (key.equals("max-absolute-coordinate") || key.equals("max-absolute-y")) {
            if (value <= 0.0) return "must be greater than 0.";
            if (value > 30_000_000.0) {
                return "must not exceed 30000000 blocks; larger limits can defeat coordinate sanity validation.";
            }
            return null;
        }
        if (key.equals("minimum-time-ratio") || key.equals("minimum-occluded-sample-ratio")) {
            return value < 0.0 || value > 1.0 ? "must be between 0 and 1." : null;
        }
        if (key.equals("max-pattern-coefficient-variation")) {
            return value < 0.0 || value > 1.0 ? "must be between 0 and 1." : null;
        }
        if (key.contains("degrees") || key.equals("minimum-downward-pitch")
                || key.equals("maximum-pitch-variance") || key.equals("rotation-quantization-degrees")) {
            return value < 0.0 || value > 180.0 ? "must be between 0 and 180 degrees." : null;
        }
        if (key.equals("outlier-ratio") || key.endsWith("-multiplier")) {
            return value <= 0.0 ? "must be greater than 0." : null;
        }
        if (key.equals("speed-potion-multiplier-per-level")) {
            return value < 0.0 ? "must not be negative." : null;
        }
        if (key.endsWith("-vl") || key.equals("violation-decay-per-second")
                || key.endsWith("-offset") || key.endsWith("-margin") || key.endsWith("-tolerance")) {
            return value < 0.0 ? "must not be negative." : null;
        }
        if (key.startsWith("minimum-") || key.startsWith("max-") || key.startsWith("maximum-")
                || key.startsWith("required-")
                || key.startsWith("sample-") || key.contains("slack") || key.contains("window")
                || key.contains("interval") || key.contains("duration") || key.contains("timeout")
                || key.contains("cooldown") || key.contains("observation") || key.contains("samples")
                || key.contains("consecutive") || key.contains("hits") || key.contains("breaks")
                || key.equals("violation-add")
                || key.equals("special-block-allowance-per-tick")
                || key.equals("base-horizontal-per-tick")
                || key.equals("base-damage-allowance")
                || key.equals("damage-per-fall-block-allowance")
                || key.equals("density-damage-per-block-per-level")
                || key.equals("extra-tolerance")) {
            return value < 0.0 ? "must not be negative." : null;
        }
        return null;
    }

    public static boolean hasClientOverrides(FileConfiguration config) {
        ConfigurationSection checks = config.getConfigurationSection("checks");
        if (checks == null) return false;
        for (String check : checks.getKeys(false)) {
            ConfigurationSection section = checks.getConfigurationSection(check);
            ConfigurationSection overrides = section == null
                    ? null : section.getConfigurationSection("client-version-overrides");
            if (overrides != null && !overrides.getKeys(false).isEmpty()) return true;
        }
        return false;
    }

    private static Double numericValue(Object sectionValue, String check, String key) {
        if (!(sectionValue instanceof ConfigurationSection section)) return null;
        Object value = section.get(key);
        if (!(value instanceof Number number)) return null;
        double result = number.doubleValue();
        if (!Double.isFinite(result) || thresholdRangeWarning(check, key, result) != null) return null;
        return result;
    }
}

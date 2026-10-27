package dev.annamc.anticheat.config;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import dev.annamc.anticheat.check.SignalCorrelation;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Startup/reload diagnostics for risky or incomplete server configuration. */
public final class ConfigurationDiagnostics {
    public static final List<String> DETECTORS = List.of(
            "movement", "movement-physics", "flight", "jesus", "spider-signal", "knockback", "reach", "mace-damage", "wall-hit",
            "fast-break", "nuker-burst", "block-reach", "click-rate", "container-steal", "container-move", "combat-pattern",
            "scaffold-signal", "no-fall", "packet-sanity", "packet-rate", "timer");

    private ConfigurationDiagnostics() {}

    public static List<String> inspect(JavaPlugin plugin) {
        List<String> notes = new ArrayList<>();
        String language = plugin.getConfig().getString("settings.language", "en");
        if (language == null || !(language.equalsIgnoreCase("en") || language.equalsIgnoreCase("hi"))) {
            notes.add("settings.language is not a supported language (en, hi); verify the language file exists.");
        }

        double minimumTps = plugin.getConfig().getDouble("settings.performance.minimum-tps", 17.0);
        if (!Double.isFinite(minimumTps) || minimumTps < 1.0 || minimumTps > 20.0) {
            notes.add("settings.performance.minimum-tps must be finite and between 1.0 and 20.0; the lag guard ignores invalid values.");
        }
        if (plugin.getConfig().getBoolean("settings.performance.pause-detection-under-tps-enabled", true)) {
            java.util.Set<String> knownChecks = new java.util.HashSet<>(DETECTORS);
            for (String configured : plugin.getConfig().getStringList("settings.performance.pause-checks-on-low-tps")) {
                if (configured == null || configured.isBlank()) continue;
                if (!configured.trim().equals("*") && !knownChecks.contains(configured.trim().toLowerCase(java.util.Locale.ROOT))) {
                    notes.add("Unknown detector '" + configured + "' in settings.performance.pause-checks-on-low-tps; it will not pause detection unless the name matches an active check.");
                }
            }
            if (plugin.getConfig().getStringList("settings.performance.pause-checks-on-low-tps").isEmpty()) {
                notes.add("The low-TPS guard is enabled but pause-checks-on-low-tps is empty; no detectors will be paused.");
            }
            if (plugin.getConfig().getStringList("settings.performance.pause-checks-on-low-tps").contains("*")) {
                notes.add("Low-TPS pause policy contains '*'; all checks that use CheckUtil will pause during low TPS, including packet/client telemetry.");
            }
        }
        for (String warning : dev.annamc.anticheat.check.HitboxSamplePoints
                .validate(plugin.getConfig().getMapList("checks.wall-hit.sample-points"))) {
            notes.add("Wall-hit sample configuration: " + warning);
        }
        for (String warning : dev.annamc.anticheat.check.VersionedThresholds.validateOverrides(plugin.getConfig())) {
            notes.add("Threshold override configuration: " + warning);
        }
        for (String warning : dev.annamc.anticheat.check.VersionedThresholds
                .validateConfiguredThresholds(plugin.getConfig())) {
            notes.add("Threshold configuration: " + warning);
        }
        if (dev.annamc.anticheat.check.VersionedThresholds.hasClientOverrides(plugin.getConfig())
                && Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            notes.add("Client-version overrides are configured, but PacketEvents is not installed; these overrides are inactive and server-version/default thresholds are used.");
        }
        org.bukkit.plugin.Plugin packetEventsPlugin = Bukkit.getPluginManager().getPlugin("packetevents");
        if (packetEventsPlugin != null
                && compareNumericVersions(packetEventsPlugin.getDescription().getVersion(), "2.11.0") < 0
                && compareNumericVersions(Bukkit.getMinecraftVersion(), "1.21.11") >= 0) {
            notes.add("Installed PacketEvents " + packetEventsPlugin.getDescription().getVersion()
                    + " predates Minecraft 1.21.11 protocol support. Update PacketEvents to 2.11.0 or newer (the ANNA AC build targets 2.14.0).");
        }

        int enabledChecks = 0;
        int enabledActions = 0;
        boolean packetRequired = false;
        java.util.Set<String> activeFamilies = new java.util.HashSet<>();
        for (String check : DETECTORS) {
            boolean enabled = plugin.getConfig().getBoolean("checks." + check + ".enabled", false);
            String actionPath = "punishments.actions." + check;
            boolean actionEnabled = plugin.getConfig().getBoolean(actionPath + ".enabled", false);
            boolean action = plugin.getConfig().getBoolean("punishments.enabled", true) && actionEnabled;
            String actionType = plugin.getConfig().getString(actionPath + ".type", "KICK");
            if (actionType == null) actionType = "KICK";
            actionType = actionType.trim().toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("KICK", "WARN", "COMMAND", "NONE").contains(actionType)) {
                notes.add("Unknown punishment type '" + actionType + "' for " + check
                        + "; use KICK, WARN, COMMAND or NONE.");
            }
            if (actionEnabled && "COMMAND".equals(actionType)
                    && plugin.getConfig().getStringList(actionPath + ".commands").isEmpty()) {
                notes.add("Punishment action '" + check + "' uses COMMAND but its commands list is empty.");
            }
            if (enabled) {
                enabledChecks++;
                activeFamilies.add(SignalCorrelation.familyFor(check));
            }
            if (action && !"NONE".equals(actionType)) enabledActions++;
            if (action && !enabled) {
                notes.add("Punishment action '" + check + "' is enabled while its detector is disabled; it cannot trigger.");
            }
            if (("packet-rate".equals(check) || "timer".equals(check) || "packet-sanity".equals(check)) && enabled) packetRequired = true;
            if ("click-rate".equals(check) && enabled
                    && (plugin.getConfig().getBoolean("checks.click-rate.attack-packets-enabled", true)
                    || plugin.getConfig().getBoolean("checks.click-rate.pattern-detection-enabled", false))) {
                packetRequired = true;
            }
        }

        boolean requireFamilies = plugin.getConfig().getBoolean("punishments.require-independent-families", true);
        int minimumFamilies = Math.max(1, Math.min(4,
                plugin.getConfig().getInt("punishments.minimum-independent-families", 2)));
        if (enabledActions > 0 && requireFamilies && activeFamilies.size() < minimumFamilies) {
            notes.add("Automatic kicks require " + minimumFamilies + " independent signal families, but only "
                    + activeFamilies.size() + " family/families are enabled; kicks cannot currently trigger.");
        }

        if (enabledChecks == 0) notes.add("No detection checks are enabled; ANNA AC is currently not analysing gameplay.");
        if (enabledActions == 0) notes.add("No automatic punishment actions are enabled. This is the recommended initial public-server setting.");
        else notes.add("Automatic punishment is configured for " + enabledActions
                + " check(s); validate each enabled detector on a staging server before public use.");

        if (packetRequired && Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            notes.add("PacketEvents is missing, but packet-rate, timer, or click-rate packet telemetry is enabled; those packet signals will not function.");
        }

        boolean antiNukeEnabled = plugin.getConfig().getBoolean("security.antinuke.enabled", true);
        if (antiNukeEnabled && plugin.getConfig().getBoolean("security.antinuke.block-bulk-commands", true)
                && plugin.getConfig().getStringList("security.antinuke.allow-bulk-command-permissions").isEmpty()) {
            notes.add("Anti-nuke bulk command protection is enabled with no bypass permissions; all listed bulk commands will be blocked for players.");
        }
        if (plugin.getConfig().getBoolean("security.plugin-monitor.enabled", true)
                && plugin.getConfig().getBoolean("security.plugin-monitor.integrity-hash-enabled", true)
                && !plugin.getDataFolder().toPath().resolve("plugin-hashes.yml").toFile().isFile()) {
            notes.add("Plugin hash baseline does not exist yet; the first scan will inventory current JARs. This is not a malware verdict—review plugin origins before trusting the baseline.");
        }
        if (plugin.getConfig().getBoolean("security.plugin-monitor.enabled", true)
                && !plugin.getConfig().getBoolean("security.plugin-monitor.static-scan-enabled", true)
                && !plugin.getConfig().getBoolean("security.plugin-monitor.integrity-hash-enabled", true)) {
            notes.add("Plugin monitor is enabled but both static risk scanning and hash integrity monitoring are disabled.");
        }

        boolean discordEnabled = plugin.getConfig().getBoolean("alerts.discord-enabled", false);
        String webhook = plugin.getConfig().getString("alerts.discord-webhook", "");
        if (discordEnabled && (webhook == null || !isDiscordWebhook(webhook))) {
            notes.add("Discord alerts are enabled but alerts.discord-webhook is not a valid HTTPS Discord webhook URL.");
        }

        long retentionHours = plugin.getConfig().getLong("database.violation-retention-hours", 24L);
        if (retentionHours < 1) notes.add("database.violation-retention-hours must be at least 1; ANNA AC uses a minimum of 1 hour.");

        int retentionDays = plugin.getConfig().getInt("database.retention-days", 7);
        if (retentionDays < 0 || retentionDays > 36500) {
            notes.add("database.retention-days is outside the supported range 0–36500; the storage layer clamps the value.");
        }
        int maxRecords = plugin.getConfig().getInt("database.max-records-per-player", 5000);
        if (maxRecords < 1 || maxRecords > 20000) {
            notes.add("database.max-records-per-player is outside the supported range 1–20000; the storage layer clamps the value.");
        }
        int evidenceLimit = plugin.getConfig().getInt("database.max-detail-length", 1200);
        if (evidenceLimit < 64 || evidenceLimit > 10000) {
            notes.add("database.max-detail-length is outside the supported range 64–10000; the storage layer clamps the value.");
        }

        return List.copyOf(notes);
    }

    /** Compares numeric dotted versions; unknown strings return 0 to avoid false warnings. */
    private static int compareNumericVersions(String actual, String expected) {
        try {
            String[] a = actual.split("[^0-9]+");
            String[] e = expected.split("[^0-9]+");
            int count = Math.max(a.length, e.length);
            for (int i = 0; i < count; i++) {
                int av = i < a.length && !a[i].isEmpty() ? Integer.parseInt(a[i]) : 0;
                int ev = i < e.length && !e[i].isEmpty() ? Integer.parseInt(e[i]) : 0;
                if (av != ev) return Integer.compare(av, ev);
            }
        } catch (RuntimeException ignored) {
            return 0;
        }
        return 0;
    }

    private static boolean isDiscordWebhook(String candidate) {
        try {
            URI uri = URI.create(candidate.trim());
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && (host.equalsIgnoreCase("discord.com") || host.equalsIgnoreCase("discordapp.com"))
                    && uri.getPath() != null
                    && uri.getPath().startsWith("/api/webhooks/");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}

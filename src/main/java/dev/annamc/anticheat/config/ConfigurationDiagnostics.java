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
            "movement", "movement-physics", "knockback", "reach", "wall-hit",
            "fast-break", "nuker-burst", "click-rate", "combat-pattern",
            "scaffold-signal", "no-fall", "packet-rate", "timer");

    private ConfigurationDiagnostics() {}

    public static List<String> inspect(JavaPlugin plugin) {
        List<String> notes = new ArrayList<>();
        String language = plugin.getConfig().getString("settings.language", "en");
        if (language == null || !(language.equalsIgnoreCase("en") || language.equalsIgnoreCase("hi"))) {
            notes.add("settings.language is not a supported language (en, hi); verify the language file exists.");
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
            if (("packet-rate".equals(check) || "timer".equals(check)) && enabled) packetRequired = true;
            if ("click-rate".equals(check) && enabled
                    && plugin.getConfig().getBoolean("checks.click-rate.attack-packets-enabled", true)) {
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

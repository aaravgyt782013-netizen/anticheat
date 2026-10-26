package dev.annamc.anticheat.config;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

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
        for (String check : DETECTORS) {
            boolean enabled = plugin.getConfig().getBoolean("checks." + check + ".enabled", false);
            boolean action = plugin.getConfig().getBoolean("punishments.enabled", true)
                    && plugin.getConfig().getBoolean("punishments.actions." + check + ".enabled", false);
            if (enabled) enabledChecks++;
            if (action) enabledActions++;
            if (action && !enabled) {
                notes.add("Punishment action '" + check + "' is enabled while its detector is disabled; it cannot trigger.");
            }
            if (("packet-rate".equals(check) || "timer".equals(check)) && enabled) packetRequired = true;
        }

        if (enabledChecks == 0) notes.add("No detection checks are enabled; ANNA AC is currently not analysing gameplay.");
        if (enabledActions == 0) notes.add("No automatic kick actions are enabled. This is the recommended initial public-server setting.");
        else notes.add("Automatic punishment is configured for " + enabledActions
                + " check(s); validate each enabled detector on a staging server before public use.");

        if (packetRequired && Bukkit.getPluginManager().getPlugin("packetevents") == null) {
            notes.add("PacketEvents is missing, but packet-rate or timer is enabled; these packet checks will not function.");
        }

        boolean discordEnabled = plugin.getConfig().getBoolean("alerts.discord-enabled", false);
        String webhook = plugin.getConfig().getString("alerts.discord-webhook", "");
        if (discordEnabled && (webhook == null || !isDiscordWebhook(webhook))) {
            notes.add("Discord alerts are enabled but alerts.discord-webhook is not a valid HTTPS Discord webhook URL.");
        }

        long retentionHours = plugin.getConfig().getLong("settings.violation-retention-hours", 24L);
        if (retentionHours < 1) notes.add("settings.violation-retention-hours must be at least 1; ANNA AC uses a minimum of 1 hour.");

        int evidenceLimit = plugin.getConfig().getInt("settings.max-evidence-detail-length", 1200);
        if (evidenceLimit < 64) notes.add("settings.max-evidence-detail-length is below 64; the storage layer enforces a 64-character minimum.");

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

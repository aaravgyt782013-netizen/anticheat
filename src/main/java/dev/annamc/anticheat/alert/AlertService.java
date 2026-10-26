package dev.annamc.anticheat.alert;

import dev.annamc.anticheat.evidence.EvidenceStore;
import dev.annamc.anticheat.lang.Messages;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AlertService {
    private final JavaPlugin plugin;
    private final EvidenceStore evidence;
    private final Messages messages;
    private final Set<UUID> mutedStaff = ConcurrentHashMap.newKeySet();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    public AlertService(JavaPlugin plugin, EvidenceStore evidence, Messages messages) {
        this.plugin = plugin;
        this.evidence = evidence;
        this.messages = messages;
    }

    public boolean toggleStaffAlerts(Player player) {
        UUID id = player.getUniqueId();
        if (mutedStaff.remove(id)) return true;
        mutedStaff.add(id);
        return false;
    }

    public boolean staffAlertsEnabled(Player player) {
        return !mutedStaff.contains(player.getUniqueId());
    }

    public void send(Player player, String check, double vl, String detail) {
        String message = messages.get("alerts.movement", Map.of(
                "player", player.getName(),
                "check", check,
                "vl", String.format(java.util.Locale.ROOT, "%.1f", vl),
                "detail", detail == null ? "" : detail
        ));
        String plain = ChatColor.stripColor(message);
        if (plugin.getConfig().getBoolean("settings.debug", false))
            plugin.getLogger().info("Debug alert generated: " + plain);
        if (plugin.getConfig().getBoolean("alerts.console", true))
            plugin.getLogger().warning(plain);
        if (plugin.getConfig().getBoolean("alerts.staff-chat", true)) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("annaac.alerts") && !staff.hasPermission("annaac.bypass")
                        && staffAlertsEnabled(staff)) staff.sendMessage(message);
            }
        }
        sendDiscord(player.getName(), player.getUniqueId(), check, vl, detail == null ? "" : detail, plain);
        evidence.record(player.getUniqueId(), player.getName(), check, vl, detail);
    }

    public void sendSecurityAlert(String playerName, UUID playerId, String check, String detail) {
        String safeDetail = detail == null ? "" : detail;
        String message = messages.get("alerts.security-detail", Map.of("player", playerName, "detail", safeDetail));
        String plain = ChatColor.stripColor(message);
        if (plugin.getConfig().getBoolean("alerts.console", true)) plugin.getLogger().warning(plain);
        if (plugin.getConfig().getBoolean("alerts.staff-chat", true)) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("annaac.alerts") && !staff.hasPermission("annaac.bypass")
                        && staffAlertsEnabled(staff)) staff.sendMessage(message);
            }
        }
        sendDiscord(playerName, playerId, check, 0.0, safeDetail, plain);
        evidence.record(playerId, playerName, check, 0.0, safeDetail);
    }

    private void sendDiscord(String playerName, UUID playerId, String check, double vl, String detail, String plain) {
        if (!plugin.getConfig().getBoolean("alerts.discord-enabled", false)) return;
        String webhook = plugin.getConfig().getString("alerts.discord-webhook", "");
        if (webhook == null || webhook.isBlank()) return;

        final URI uri;
        try {
            uri = URI.create(webhook.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || !(host.equalsIgnoreCase("discord.com") || host.equalsIgnoreCase("discordapp.com"))
                    || !uri.getPath().startsWith("/api/webhooks/")) {
                plugin.getLogger().warning("Discord webhook URL must be an HTTPS Discord webhook URL (discord.com/api/webhooks/...).");
                return;
            }
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Discord webhook URL is invalid; check alerts.discord-webhook.");
            return;
        }

        int timeoutSeconds = Math.max(2, Math.min(15, plugin.getConfig().getInt("alerts.discord-timeout-seconds", 6)));
        String username = plugin.getConfig().getString("alerts.discord-username", "ANNA AC | Security Alerts");
        String avatar = plugin.getConfig().getString("alerts.discord-avatar-url", "");
        String roleId = plugin.getConfig().getString("alerts.discord-role-mention-id", "");
        boolean includeDetail = plugin.getConfig().getBoolean("alerts.discord-include-detail", true);
        boolean includeUuid = plugin.getConfig().getBoolean("alerts.discord-include-player-uuid", false);
        boolean useEmbed = plugin.getConfig().getBoolean("alerts.discord-embed-enabled", true);

        StringBuilder payload = new StringBuilder("{");
        payload.append("\"username\":").append(json(username == null ? "ANNA AC | Security Alerts" : username));
        if (avatar != null && !avatar.isBlank() && avatar.startsWith("https://"))
            payload.append(",\"avatar_url\":").append(json(avatar));
        String mention = roleId != null && roleId.matches("[0-9]{17,20}") ? "<@&" + roleId + ">" : "";
        String content = useEmbed ? mention : (mention.isEmpty() ? plain : mention + "\\n" + plain);
        payload.append(",\"content\":").append(json(content));
        payload.append(",\"allowed_mentions\":{\"parse\":[],\"roles\":")
                .append(mention.isEmpty() ? "[]" : "[" + json(roleId) + "]").append("}");

        if (useEmbed) {
            int color = plugin.getConfig().getInt("alerts.discord-embed-color", 3447003);
            color = Math.max(0, Math.min(16777215, color));
            payload.append(",\"embeds\":[{")
                    .append("\"title\":").append(json("ANNA AC • Anti-Cheat Alert"))
                    .append(",\"description\":").append(json(plain))
                    .append(",\"color\":").append(color)
                    .append(",\"timestamp\":").append(json(Instant.now().toString()))
                    .append(",\"fields\":[")
                    .append(field("Player", playerName, true)).append(",")
                    .append(field("Check", check, true)).append(",")
                    .append(field("Violation Level", String.format(java.util.Locale.ROOT, "%.1f", vl), true));
            if (includeDetail && !detail.isBlank()) payload.append(",").append(field("Evidence", truncate(detail, 900), false));
            if (includeUuid) payload.append(",").append(field("Player UUID", playerId.toString(), false));
            payload.append("],\"footer\":{\"text\":").append(json("Anna MC • ANNA AC")).append("}}]");
        }
        payload.append("}");

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    plugin.getLogger().warning("Discord webhook delivery failed with HTTP " + response.statusCode()
                            + ". Check the webhook URL and channel permissions.");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Discord webhook delivery failed: " + e.getClass().getSimpleName());
            }
        });
    }

    private static String field(String name, String value, boolean inline) {
        return "{\"name\":" + json(name) + ",\"value\":" + json(truncate(value, 1000))
                + ",\"inline\":" + inline + "}";
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder("\"");
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '\\' -> out.append("\\\\");
                    case '\"' -> out.append("\\\"");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                        else out.append(c);
                    }
                }
            }
        }
        return out.append('\"').toString();
    }
}

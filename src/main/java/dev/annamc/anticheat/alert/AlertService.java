package dev.annamc.anticheat.alert;

import dev.annamc.anticheat.evidence.EvidenceStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class AlertService {
    private final JavaPlugin plugin;
    private final EvidenceStore evidence;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    public AlertService(JavaPlugin plugin, EvidenceStore evidence) {
        this.plugin = plugin;
        this.evidence = evidence;
    }
    public void send(Player player, String check, double vl, String detail) {
        String message = color("&bANNA AC &8» &f" + player.getName() + " &7failed &b" + check
                + " &8(VL: &f" + String.format("%.1f", vl) + "&8) &7" + detail);
        if (plugin.getConfig().getBoolean("alerts.console", true))
            plugin.getLogger().warning(ChatColor.stripColor(message));
        if (plugin.getConfig().getBoolean("alerts.staff-chat", true)) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("annaac.alerts") && !staff.hasPermission("annaac.bypass"))
                    staff.sendMessage(message);
            }
        }
        String webhook = plugin.getConfig().getString("alerts.discord-webhook", "");
        if (plugin.getConfig().getBoolean("alerts.discord-enabled", false)
                && webhook != null && webhook.startsWith("https://") && !webhook.isBlank()) {
            String payload = "{\"content\":\"" + json(ChatColor.stripColor(message)) + "\"}";
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(webhook))
                        .timeout(Duration.ofSeconds(6)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try { http.send(request, HttpResponse.BodyHandlers.discarding()); }
                    catch (Exception e) { plugin.getLogger().fine("Discord webhook delivery failed: " + e.getMessage()); }
                });
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Discord webhook URL is invalid; check alerts.discord-webhook.");
            }
        }
        evidence.record(player.getUniqueId(), player.getName(), check, vl, detail);
    }
    public static String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private static String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }
}

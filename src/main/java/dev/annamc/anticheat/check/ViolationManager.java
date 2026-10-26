package dev.annamc.anticheat.check;

import dev.annamc.anticheat.alert.AlertService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ViolationManager implements Listener {
    private final JavaPlugin plugin;
    private final AlertService alerts;
    private final Map<UUID, Map<String, Double>> levels = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdate = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAlert = new ConcurrentHashMap<>();

    public ViolationManager(JavaPlugin plugin, AlertService alerts) {
        this.plugin = plugin;
        this.alerts = alerts;
    }

    public double add(Player player, String check, double amount, String detail) {
        if (CheckUtil.shouldSkip(plugin, player, check)) return get(player, check);
        if (!Double.isFinite(amount) || amount <= 0.0) return get(player, check);

        UUID id = player.getUniqueId();
        String key = id + ":" + check;
        long now = System.currentTimeMillis();
        long previous = lastUpdate.getOrDefault(key, now);
        double decay = Math.max(0.0, plugin.getConfig().getDouble(
                "checks." + check + ".violation-decay-per-second", 0.0));
        Map<String, Double> playerLevels = levels.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>());
        double current = Math.max(0.0, playerLevels.getOrDefault(check, 0.0)
                - Math.max(0L, now - previous) / 1000.0 * decay) + amount;
        playerLevels.put(check, current);
        lastUpdate.put(key, now);

        double alertVl = plugin.getConfig().getDouble("checks." + check + ".alert-vl", 1.0);
        long alertCooldown = Math.max(0L, plugin.getConfig().getLong(
                "checks." + check + ".alert-cooldown-ms", 3000L));
        long previousAlert = lastAlert.getOrDefault(key, 0L);
        if (plugin.getConfig().getBoolean("checks." + check + ".alert", true)
                && current >= alertVl && now - previousAlert >= alertCooldown) {
            lastAlert.put(key, now);
            alerts.send(player, check, current, detail);
        }

        double kickVl = plugin.getConfig().getDouble("checks." + check + ".kick-vl", 12.0);
        boolean actionEnabled = plugin.getConfig().getBoolean("punishments.actions." + check + ".enabled", false);
        if (plugin.getConfig().getBoolean("punishments.enabled", true)
                && actionEnabled && current >= kickVl) {
            String reason = plugin.getConfig().getString(
                    "punishments.kick-message", "&cUnfair advantage detected.");
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) player.kickPlayer(AlertService.color(reason));
            });
            playerLevels.remove(check);
            lastUpdate.remove(key);
            lastAlert.remove(key);
        }
        return current;
    }

    public double get(Player player, String check) {
        return levels.getOrDefault(player.getUniqueId(), Map.of()).getOrDefault(check, 0.0);
    }

    public double get(Player player) {
        return levels.getOrDefault(player.getUniqueId(), Map.of()).values()
                .stream().mapToDouble(Double::doubleValue).sum();
    }

    public void reset(Player player) {
        UUID id = player.getUniqueId();
        levels.remove(id);
        String prefix = id + ":";
        lastUpdate.keySet().removeIf(key -> key.startsWith(prefix));
        lastAlert.keySet().removeIf(key -> key.startsWith(prefix));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { reset(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { reset(event.getPlayer()); }
}

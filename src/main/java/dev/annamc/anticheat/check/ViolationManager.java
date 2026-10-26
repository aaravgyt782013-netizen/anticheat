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
    private final Map<UUID, Double> levels = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUpdate = new ConcurrentHashMap<>();

    public ViolationManager(JavaPlugin plugin, AlertService alerts) {
        this.plugin = plugin;
        this.alerts = alerts;
    }
    public double add(Player player, String check, double amount, String detail) {
        if (player.hasPermission("annaac.bypass")
                || !plugin.getConfig().getBoolean("checks." + check + ".enabled", false)) return get(player);
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long previous = lastUpdate.getOrDefault(id, now);
        double decay = plugin.getConfig().getDouble("checks." + check + ".violation-decay-per-second", 0.0);
        double current = Math.max(0.0, levels.getOrDefault(id, 0.0) - Math.max(0, now - previous) / 1000.0 * decay) + amount;
        levels.put(id, current);
        lastUpdate.put(id, now);
        if (plugin.getConfig().getBoolean("checks." + check + ".alert", true)
                && current >= plugin.getConfig().getDouble("checks." + check + ".alert-vl", 1.0))
            alerts.send(player, check, current, detail);

        double kickVl = plugin.getConfig().getDouble("checks." + check + ".kick-vl",
                plugin.getConfig().getDouble("punishments.movement-kick-vl", 12.0));
        boolean actionEnabled = plugin.getConfig().getBoolean("punishments.actions." + check + ".enabled",
                check.equals("movement"));
        if (plugin.getConfig().getBoolean("punishments.enabled", true) && actionEnabled && current >= kickVl) {
            String reason = plugin.getConfig().getString("punishments.kick-message", "Unfair advantage detected.");
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) player.kickPlayer(AlertService.color(reason));
            });
            levels.remove(id);
            lastUpdate.remove(id);
        }
        return current;
    }
    public double get(Player player) { return levels.getOrDefault(player.getUniqueId(), 0.0); }
    public void reset(Player player) { levels.remove(player.getUniqueId()); lastUpdate.remove(player.getUniqueId()); }
    @EventHandler public void onJoin(PlayerJoinEvent event) { reset(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { reset(event.getPlayer()); }
}

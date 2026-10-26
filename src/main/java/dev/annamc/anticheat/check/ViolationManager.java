package dev.annamc.anticheat.check;

import dev.annamc.anticheat.alert.AlertService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player, per-check violation tracking with time-based decay.
 * Profiles are kept across reconnects for a bounded retention period so a
 * player cannot erase every in-memory VL simply by disconnecting.
 */
public final class ViolationManager implements Listener {
    private final JavaPlugin plugin;
    private final AlertService alerts;
    private final Map<UUID, Map<String, Double>> levels = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdate = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAlert = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Set<String> pendingKicks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, java.util.Deque<Signal>> recentSignals = new ConcurrentHashMap<>();

    public ViolationManager(JavaPlugin plugin, AlertService alerts) {
        this.plugin = plugin;
        this.alerts = alerts;
        // Bound retained profiles and key maps without doing disk I/O on gameplay events.
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredProfiles, 20L * 60L, 20L * 60L);
    }

    public double add(Player player, String check, double amount, String detail) {
        if (CheckUtil.shouldSkip(plugin, player, check)) return get(player, check);
        if (!Double.isFinite(amount) || amount <= 0.0) return get(player, check);

        UUID id = player.getUniqueId();
        String key = id + ":" + check;
        long now = System.currentTimeMillis();
        Map<String, Double> playerLevels = levels.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>());
        double current = decayToNow(id, check, now);
        current += amount;
        playerLevels.put(check, current);
        lastUpdate.put(key, now);
        lastActivity.put(id, now);
        int independentFamilies = recordAndCountFamilies(id, check, now);

        double alertVl = finiteNonNegative(plugin.getConfig().getDouble("checks." + check + ".alert-vl", 1.0), 1.0);
        long alertCooldown = Math.max(0L, plugin.getConfig().getLong(
                "checks." + check + ".alert-cooldown-ms", 3000L));
        long previousAlert = lastAlert.getOrDefault(key, 0L);
        if (plugin.getConfig().getBoolean("checks." + check + ".alert", true)
                && current >= alertVl && now - previousAlert >= alertCooldown) {
            lastAlert.put(key, now);
            alerts.send(player, check, current, detail);
        }

        double kickVl = finiteNonNegative(plugin.getConfig().getDouble("checks." + check + ".kick-vl", 12.0), 12.0);
        boolean actionEnabled = plugin.getConfig().getBoolean("punishments.actions." + check + ".enabled", false);
        boolean requireIndependentFamilies = plugin.getConfig().getBoolean(
                "punishments.require-independent-families", true);
        int minimumFamilies = Math.max(1, Math.min(4,
                plugin.getConfig().getInt("punishments.minimum-independent-families", 2)));
        boolean corroborated = !requireIndependentFamilies || independentFamilies >= minimumFamilies;
        if (plugin.getConfig().getBoolean("punishments.enabled", true)
                && actionEnabled && current >= kickVl && corroborated && pendingKicks.add(key)) {
            String reason = plugin.getConfig().getString(
                    "punishments.kick-message", "&cUnfair advantage detected.");
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (player.isOnline()) player.kickPlayer(AlertService.color(reason));
                } finally {
                    pendingKicks.remove(key);
                }
            });
        } else if (plugin.getConfig().getBoolean("settings.debug", false)
                && plugin.getConfig().getBoolean("punishments.enabled", true)
                && actionEnabled && current >= kickVl && !corroborated) {
            plugin.getLogger().info("Kick suppressed for " + player.getName() + " / " + check
                    + ": only " + independentFamilies + " independent signal family/families in the correlation window.");
        }
        return current;
    }

    public double get(Player player, String check) {
        return decayToNow(player.getUniqueId(), check, System.currentTimeMillis());
    }

    public double get(Player player) {
        Map<String, Double> playerLevels = levels.get(player.getUniqueId());
        if (playerLevels == null || playerLevels.isEmpty()) return 0.0;
        long now = System.currentTimeMillis();
        double total = 0.0;
        for (String check : playerLevels.keySet()) total += decayToNow(player.getUniqueId(), check, now);
        return total;
    }

    private double decayToNow(UUID id, String check, long now) {
        Map<String, Double> playerLevels = levels.get(id);
        if (playerLevels == null) return 0.0;
        String key = id + ":" + check;
        double stored = playerLevels.getOrDefault(check, 0.0);
        long previous = lastUpdate.getOrDefault(key, now);
        double elapsedSeconds = Math.max(0L, now - previous) / 1000.0;
        double decay = finiteNonNegative(plugin.getConfig().getDouble(
                "checks." + check + ".violation-decay-per-second", 0.0), 0.0);
        double current = Math.max(0.0, stored - elapsedSeconds * decay);
        if (current <= 1.0e-9) {
            playerLevels.remove(check);
            lastUpdate.remove(key);
            lastAlert.remove(key);
            if (playerLevels.isEmpty()) levels.remove(id, playerLevels);
            return 0.0;
        }
        playerLevels.put(check, current);
        lastUpdate.put(key, now);
        return current;
    }

    private int recordAndCountFamilies(UUID id, String check, long now) {
        long windowMs = Math.max(1000L, Math.min(120000L,
                plugin.getConfig().getLong("punishments.correlation-window-ms", 12000L)));
        int maxSignals = Math.max(10, Math.min(2000,
                plugin.getConfig().getInt("punishments.max-correlation-signals-per-player", 100)));
        java.util.Deque<Signal> signals = recentSignals.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        synchronized (signals) {
            long cutoff = now - windowMs;
            while (!signals.isEmpty() && signals.peekFirst().time < cutoff) signals.removeFirst();
            signals.addLast(new Signal(now, SignalCorrelation.familyFor(check)));
            while (signals.size() > maxSignals) signals.removeFirst();
            Set<String> families = new HashSet<>();
            for (Signal signal : signals) families.add(signal.family);
            return families.size();
        }
    }

    private static double finiteNonNegative(double value, double fallback) {
        return Double.isFinite(value) && value >= 0.0 ? value : fallback;
    }

    private void cleanupExpiredProfiles() {
        long retentionHours = Math.max(1L,
                plugin.getConfig().getLong("database.violation-retention-hours", 24L));
        long retentionMillis = retentionHours > Long.MAX_VALUE / 3_600_000L
                ? Long.MAX_VALUE : retentionHours * 3_600_000L;
        long cutoff = System.currentTimeMillis() - retentionMillis;
        for (Map.Entry<UUID, Long> entry : lastActivity.entrySet()) {
            UUID id = entry.getKey();
            if (entry.getValue() >= cutoff || Bukkit.getPlayer(id) != null) continue;
            reset(id);
        }
    }

    public void reset(Player player) {
        reset(player.getUniqueId());
    }

    private void reset(UUID id) {
        levels.remove(id);
        lastActivity.remove(id);
        recentSignals.remove(id);
        String prefix = id + ":";
        lastUpdate.keySet().removeIf(key -> key.startsWith(prefix));
        lastAlert.keySet().removeIf(key -> key.startsWith(prefix));
        pendingKicks.removeIf(key -> key.startsWith(prefix));
    }

    private record Signal(long time, String family) {}

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        // Do not clear violation levels on reconnect; decay is applied on read/add.
        lastActivity.putIfAbsent(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        // Retain the profile for a configurable period; a reconnect must not be a VL reset button.
        lastActivity.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }
}

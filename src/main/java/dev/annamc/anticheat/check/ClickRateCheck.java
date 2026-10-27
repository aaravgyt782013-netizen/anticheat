package dev.annamc.anticheat.check;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Click-rate telemetry only. High CPS is not proof of automation; defaults are
 * conservative and the check is disabled until validated on the target server.
 */
public final class ClickRateCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final Map<UUID, Deque<Long>> swings = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFlag = new ConcurrentHashMap<>();

    public ClickRateCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent event) {
        if (CheckUtil.shouldSkip(plugin, event.getPlayer(), "click-rate")) return;
        Player player = event.getPlayer();
        if (player.hasPermission("annaac.bypass")) return;
        long now = System.nanoTime();
        Deque<Long> times = swings.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        synchronized (times) {
            times.addLast(now);
            long cutoff = now - 1_000_000_000L;
            while (!times.isEmpty() && times.peekFirst() < cutoff) times.removeFirst();
            int cps = times.size();
            int threshold = (int) Math.round(CheckUtil.getVersionedDouble(plugin, player, "click-rate", "max-cps", 24.0));
            long cooldown = Math.round(CheckUtil.getVersionedDouble(plugin, player, "click-rate", "flag-cooldown-ms", 1500.0));
            long previous = lastFlag.getOrDefault(player.getUniqueId(), 0L);
            if (cps >= threshold && (now - previous) / 1_000_000L >= cooldown) {
                lastFlag.put(player.getUniqueId(), now);
                violations.add(player, "click-rate",
                        CheckUtil.getVersionedDouble(plugin, player, "click-rate", "violation-add", 1.0),
                        "arm-swings-in-last-second=" + cps + " threshold=" + threshold);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        swings.remove(event.getPlayer().getUniqueId());
        lastFlag.remove(event.getPlayer().getUniqueId());
    }
}

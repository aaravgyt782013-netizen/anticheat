package dev.annamc.anticheat.check;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Detects sustained bursts of block-break events that may indicate Nuker-like
 * automation. It is a rate heuristic, not a per-block mining-speed simulator.
 * Keep it disabled until calibrated against the server's tools, effects and plugins.
 */
public final class NukerBurstCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    // Bukkit block events are handled on the main thread; these maps stay main-thread confined.
    private final Map<UUID, Deque<Long>> breakTimes = new HashMap<>();
    private final Map<UUID, Long> lastEvaluation = new HashMap<>();
    private final Map<UUID, Integer> overLimitWindows = new HashMap<>();
    private final Map<UUID, Long> lastFlag = new HashMap<>();

    public NukerBurstCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (CheckUtil.shouldSkip(plugin, player, "nuker-burst")
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        String materialName = event.getBlock().getType().name();
        List<String> ignored = plugin.getConfig().getStringList("checks.nuker-burst.ignored-materials");
        if (ignored.stream().anyMatch(name -> name.equalsIgnoreCase(materialName))) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long windowMs = Math.max(250L,
                plugin.getConfig().getLong("checks.nuker-burst.sample-window-ms", 1000L));
        long evaluationInterval = Math.max(100L,
                plugin.getConfig().getLong("checks.nuker-burst.evaluation-interval-ms", 250L));

        Deque<Long> times = breakTimes.computeIfAbsent(id, ignoredId -> new ArrayDeque<>());
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > windowMs) times.removeFirst();

        long last = lastEvaluation.getOrDefault(id, 0L);
        if (now - last < evaluationInterval) return;
        lastEvaluation.put(id, now);

        int maxBreaks = Math.max(1,
                plugin.getConfig().getInt("checks.nuker-burst.max-block-breaks-per-window", 18));
        int count = times.size();
        int streak = count >= maxBreaks ? overLimitWindows.getOrDefault(id, 0) + 1 : 0;
        overLimitWindows.put(id, streak);

        int requiredWindows = Math.max(1,
                plugin.getConfig().getInt("checks.nuker-burst.required-over-limit-windows", 3));
        long cooldown = Math.max(0L,
                plugin.getConfig().getLong("checks.nuker-burst.flag-cooldown-ms", 5000L));
        if (streak < requiredWindows || now - lastFlag.getOrDefault(id, 0L) < cooldown) return;

        lastFlag.put(id, now);
        overLimitWindows.put(id, 0);
        violations.add(player, "nuker-burst",
                plugin.getConfig().getDouble("checks.nuker-burst.violation-add", 1.0),
                String.format(Locale.ROOT,
                        "block-break burst=%d in %dms (limit=%d, repeated-windows=%d); validate tool, effects and server plugins",
                        count, windowMs, maxBreaks, streak));
    }

    public void clear(UUID id) {
        breakTimes.remove(id);
        lastEvaluation.remove(id);
        overLimitWindows.remove(id);
        lastFlag.remove(id);
    }
}

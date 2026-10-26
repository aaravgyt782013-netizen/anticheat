package dev.annamc.anticheat.check;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Paper-specific FastBreak timing signal. Captures the server's per-tick
 * Block#getBreakSpeed(Player) estimate at block-damage time, then compares it
 * with the observed time to BlockBreakEvent. Requiring repeated fast breaks,
 * latency slack and a conservative ratio reduces risk but does not replace a
 * version-specific packet-level mining simulator.
 */
public final class FastBreakCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    // Bukkit block events are handled on the server thread.
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Deque<Long>> impossibleBreaks = new HashMap<>();
    private final Map<UUID, Long> lastFlag = new HashMap<>();

    public FastBreakCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (CheckUtil.shouldSkip(plugin, player, "fast-break")
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            sessions.remove(id);
            return;
        }

        Block block = event.getBlock();
        String material = block.getType().name();
        List<String> ignored = plugin.getConfig().getStringList("checks.fast-break.ignored-materials");
        if (ignored.stream().anyMatch(name -> name.equalsIgnoreCase(material))) {
            sessions.remove(id);
            return;
        }
        if (event.getInstaBreak()) {
            sessions.remove(id);
            return;
        }

        float progressPerTick = block.getBreakSpeed(player);
        if (!Float.isFinite(progressPerTick) || progressPerTick <= 0.0f) {
            sessions.remove(id);
            return;
        }
        // The break-speed API is progress per server tick; ceil gives the
        // conservative number of full ticks expected to reach 100% progress.
        int expectedTicks = Math.max(1, (int) Math.ceil(1.0 / progressPerTick));
        long now = System.currentTimeMillis();
        long timeout = Math.max(1000L,
                plugin.getConfig().getLong("checks.fast-break.session-timeout-ms", 15000L));

        Session previous = sessions.get(id);
        if (previous != null && previous.sameBlock(block) && now - previous.startedAt <= timeout) {
            // If conditions improve during mining, use the faster observed legal
            // speed so a potion/tool/equipment change does not overstate the wait.
            sessions.put(id, new Session(previous.worldId, previous.x, previous.y, previous.z,
                    previous.startedAt, Math.min(previous.expectedTicks, expectedTicks)));
        } else {
            sessions.put(id, new Session(block.getWorld().getUID(), block.getX(), block.getY(),
                    block.getZ(), now, expectedTicks));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Session session = sessions.remove(id);
        if (session == null || CheckUtil.shouldSkip(plugin, player, "fast-break")
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || !session.sameBlock(event.getBlock())) return;

        long now = System.currentTimeMillis();
        long elapsed = Math.max(0L, now - session.startedAt);
        long timeout = Math.max(1000L,
                plugin.getConfig().getLong("checks.fast-break.session-timeout-ms", 15000L));
        if (elapsed > timeout) return;

        double ratio = Math.max(0.05,
                Math.min(1.0, plugin.getConfig().getDouble("checks.fast-break.minimum-time-ratio", 0.5)));
        double baseExpectedMs = session.expectedTicks * 50.0 * ratio;
        double pingSlack = Math.min(
                Math.max(0.0, plugin.getConfig().getDouble("checks.fast-break.max-ping-slack-ms", 200.0)),
                Math.max(0, player.getPing()) * Math.max(0.0,
                        plugin.getConfig().getDouble("checks.fast-break.ping-slack-per-ms", 0.5)));
        long minimumAllowedMs = Math.max(0L,
                plugin.getConfig().getLong("checks.fast-break.minimum-required-time-ms", 60L));
        double thresholdMs = Math.max(minimumAllowedMs, baseExpectedMs - pingSlack);
        if (elapsed >= thresholdMs) return;

        long windowMs = Math.max(1000L,
                plugin.getConfig().getLong("checks.fast-break.sample-window-ms", 10000L));
        Deque<Long> times = impossibleBreaks.computeIfAbsent(id, ignoredId -> new ArrayDeque<>());
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > windowMs) times.removeFirst();
        int required = Math.max(1,
                plugin.getConfig().getInt("checks.fast-break.minimum-impossible-breaks", 2));
        if (times.size() < required) return;

        long cooldown = Math.max(0L,
                plugin.getConfig().getLong("checks.fast-break.flag-cooldown-ms", 5000L));
        if (now - lastFlag.getOrDefault(id, 0L) < cooldown) return;
        lastFlag.put(id, now);
        int sampleCount = times.size();
        times.clear();

        violations.add(player, "fast-break",
                plugin.getConfig().getDouble("checks.fast-break.violation-add", 1.0),
                String.format(Locale.ROOT,
                        "repeated fast block breaks=%d expected-ticks=%d observed-ms=%d conservative-threshold-ms=%.1f ping=%dms block=%s; review effects, tools and server plugins",
                        sampleCount, session.expectedTicks, elapsed, thresholdMs, player.getPing(),
                        event.getBlock().getType().name()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sessions.remove(id);
        impossibleBreaks.remove(id);
        lastFlag.remove(id);
    }

    private record Session(UUID worldId, int x, int y, int z, long startedAt, int expectedTicks) {
        boolean sameBlock(Block block) {
            return worldId.equals(block.getWorld().getUID())
                    && x == block.getX() && y == block.getY() && z == block.getZ();
        }
    }
}

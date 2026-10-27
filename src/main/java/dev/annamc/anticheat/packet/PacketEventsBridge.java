package dev.annamc.anticheat.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import dev.annamc.anticheat.check.ViolationManager;
import dev.annamc.anticheat.check.ClientVersionRegistry;
import dev.annamc.anticheat.check.CheckUtil;
import dev.annamc.anticheat.check.VersionedThresholds;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PacketEvents telemetry for fast movement streams. Bukkit player lookups and
 * violation updates are deferred to the main thread. Packet timing is not proof
 * of a timer cheat and this listener's checks are disabled by default.
 */
public final class PacketEventsBridge implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final String serverVersion;
    private final Map<UUID, Long> lastMovementPacket = new ConcurrentHashMap<>();
    private final Map<UUID, Long> movementStreamStarted = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> fastIntervals = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> packetTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFlag = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTimerFlag = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> attackTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAttackEvaluation = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> attackOverLimitWindows = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAttackFlag = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> attackPatternOverLimitWindows = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAttackPatternEvaluation = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAttackPatternFlag = new ConcurrentHashMap<>();
    private PacketListenerAbstract listener;

    public PacketEventsBridge(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
        this.serverVersion = Bukkit.getMinecraftVersion();
    }

    public void register() {
        listener = new PacketListenerAbstract() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                UUID id = event.getUser().getUUID();
                // Capture protocol release name for optional client-version threshold overrides.
                // Unknown/unparseable versions are ignored; checks fall back to server defaults.
                try {
                    ClientVersion clientVersion = event.getUser().getClientVersion();
                    if (clientVersion != null) {
                        ClientVersionRegistry.remember(id, clientVersion.getReleaseName());
                    }
                } catch (RuntimeException ignored) {
                    // Version-specific tuning is optional; packet telemetry must remain resilient.
                }
                String type = String.valueOf(event.getPacketType()).toUpperCase(java.util.Locale.ROOT);
                long nowNanos = System.nanoTime();
                long nowMs = nowNanos / 1_000_000L;

                // Inspect actual attack packets as well as arm swings. Packet
                // callbacks never touch Bukkit entities; violation updates hop to the main thread.
                if (type.contains("INTERACT_ENTITY")
                        && plugin.getConfig().getBoolean("checks.click-rate.enabled", false)
                        && (plugin.getConfig().getBoolean("checks.click-rate.attack-packets-enabled", true)
                        || plugin.getConfig().getBoolean("checks.click-rate.pattern-detection-enabled", false))) {
                    try {
                        WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
                        if (interact.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                            observeAttack(id, nowMs);
                        }
                    } catch (RuntimeException malformedPacket) {
                        // Never let a malformed optional telemetry packet break the listener.
                        plugin.getLogger().fine("ANNA AC could not parse an entity-interaction packet.");
                    }
                    return;
                }

                if (!plugin.getConfig().getBoolean("checks.packet-rate.enabled", false)
                        && !plugin.getConfig().getBoolean("checks.timer.enabled", false)) return;
                if (!(type.contains("PLAYER_POSITION") || type.contains("PLAYER_ROTATION")
                        || type.endsWith("FLYING") || type.contains("PLAYER_POSITION_AND_ROTATION"))) return;
                Long previous = lastMovementPacket.put(id, nowNanos);
                long intervalMs = previous == null ? Long.MAX_VALUE : (nowNanos - previous) / 1_000_000L;
                if (previous == null || intervalMs > 1500L) movementStreamStarted.put(id, nowMs);
                int maxMovementSamples = boundedSampleLimit(
                        "checks.packet-rate.max-buffered-samples-per-player", 1024);
                long minimum = Math.round(versionedThreshold(id, "packet-rate", "minimum-interval-ms", 5.0));
                int fast = intervalMs < minimum
                        ? fastIntervals.compute(id, (key, value) ->
                                Math.min(maxMovementSamples, value == null ? 1 : value + 1))
                        : fastIntervals.compute(id, (key, value) ->
                                value == null ? 0 : Math.max(0, value - 1));

                Deque<Long> times = packetTimes.computeIfAbsent(id, ignored -> new ArrayDeque<>());
                int pps;
                synchronized (times) {
                    pps = TimestampWindow.addAndTrim(times, nowMs, 1000L, maxMovementSamples);
                }

                if (plugin.getConfig().getBoolean("checks.packet-rate.enabled", false)) {
                    int maxPps = (int) Math.round(versionedThreshold(id, "packet-rate", "max-movement-packets-per-second", 45.0));
                    int sampleWindow = (int) Math.round(versionedThreshold(id, "packet-rate", "sample-window", 40.0));
                    long cooldown = plugin.getConfig().getLong("checks.packet-rate.flag-cooldown-ms", 3000L);
                    long previousFlag = lastFlag.getOrDefault(id, 0L);
                    if ((fast >= sampleWindow || dev.annamc.anticheat.check.DetectionMath.exceedsPacketRate(pps, maxPps))
                            && nowMs - previousFlag >= cooldown) {
                        lastFlag.put(id, nowMs);
                        fastIntervals.put(id, 0);
                        String detail = "movement packet telemetry: rolling-pps=" + pps
                                + ", fast-interval-streak=" + fast
                                + "; review proxy, ping and client before action";
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player player = Bukkit.getPlayer(id);
                            if (player != null) violations.add(player, "packet-rate",
                                    CheckUtil.getVersionedDouble(plugin, player, "packet-rate", "violation-add", 1.0), detail);
                        });
                    }
                }

                if (plugin.getConfig().getBoolean("checks.timer.enabled", false)) {
                    long firstPacket = movementStreamStarted.getOrDefault(id, nowMs);
                    long sampleDuration = Math.round(versionedThreshold(id, "timer", "minimum-sample-duration-ms", 1200.0));
                    int timerMaxPps = (int) Math.round(versionedThreshold(id, "timer", "max-movement-packets-per-second", 23.0));
                    long timerCooldown = plugin.getConfig().getLong("checks.timer.flag-cooldown-ms", 5000L);
                    long timerPrevious = lastTimerFlag.getOrDefault(id, 0L);
                    if (nowMs - firstPacket >= sampleDuration && pps >= timerMaxPps
                            && nowMs - timerPrevious >= timerCooldown) {
                        lastTimerFlag.put(id, nowMs);
                        String detail = "sustained movement packet rate pps=" + pps
                                + " max=" + timerMaxPps + "; telemetry only, validate ping/proxy/client";
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player player = Bukkit.getPlayer(id);
                            if (player != null) violations.add(player, "timer",
                                    CheckUtil.getVersionedDouble(plugin, player, "timer", "violation-add", 1.0), detail);
                        });
                    }
                }
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(listener);
    }

    private void observeAttack(UUID id, long nowMs) {
        boolean rateDetectionEnabled = plugin.getConfig().getBoolean(
                "checks.click-rate.attack-packets-enabled", true);
        boolean patternDetectionEnabled = plugin.getConfig().getBoolean(
                "checks.click-rate.pattern-detection-enabled", false);
        long attackWindowMs = Math.max(250L, Math.min(5000L,
                Math.round(versionedThreshold(id, "click-rate", "attack-window-ms", 1000.0))));
        long patternWindowMs = patternDetectionEnabled
                ? Math.max(500L, Math.min(10000L,
                        Math.round(versionedThreshold(id, "click-rate", "pattern-window-ms", 3000.0))))
                : attackWindowMs;
        long retainedWindowMs = Math.max(attackWindowMs, patternWindowMs);
        Deque<Long> times = attackTimes.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        int count;
        List<Long> patternSamples = List.of();
        int maxAttackSamples = boundedSampleLimit(
                "checks.click-rate.max-buffered-samples-per-player", 1024);
        synchronized (times) {
            TimestampWindow.addAndTrim(times, nowMs, retainedWindowMs, maxAttackSamples);
            count = countRecent(times, nowMs - attackWindowMs);
            if (patternDetectionEnabled) {
                patternSamples = recentSamples(times, nowMs - patternWindowMs);
            }
        }

        if (rateDetectionEnabled) {
            long evaluationInterval = Math.max(100L,
                    Math.round(versionedThreshold(id, "click-rate", "attack-evaluation-interval-ms", 250.0)));
            long previousEvaluation = lastAttackEvaluation.getOrDefault(id, 0L);
            if (nowMs - previousEvaluation >= evaluationInterval) {
                lastAttackEvaluation.put(id, nowMs);
                int maximum = Math.max(1,
                        (int) Math.round(versionedThreshold(id, "click-rate", "max-attacks-per-window", 24.0)));
                int streak = count >= maximum
                        ? attackOverLimitWindows.getOrDefault(id, 0) + 1 : 0;
                attackOverLimitWindows.put(id, streak);

                int required = Math.max(1, Math.min(20,
                        (int) Math.round(versionedThreshold(id, "click-rate", "minimum-over-limit-windows", 3.0))));
                long cooldown = Math.max(0L,
                        Math.round(versionedThreshold(id, "click-rate", "attack-flag-cooldown-ms", 3000.0)));
                long previousFlag = lastAttackFlag.getOrDefault(id, 0L);
                if (streak >= required && nowMs - previousFlag >= cooldown) {
                    lastAttackFlag.put(id, nowMs);
                    attackOverLimitWindows.put(id, 0);
                    String detail = "rolling attack packets=" + count + " in " + attackWindowMs
                            + "ms (configured threshold=" + maximum + ", repeated windows=" + streak
                            + "); attack-rate alone is not proof of automation";
                    scheduleClickRateSignal(id, detail);
                }
            }
        }

        if (patternDetectionEnabled) {
            observeAttackPattern(id, nowMs, patternSamples, patternWindowMs);
        }
    }

    private void observeAttackPattern(UUID id, long nowMs, List<Long> timestamps, long windowMs) {
        long evaluationInterval = Math.max(100L, Math.min(2000L,
                Math.round(versionedThreshold(id, "click-rate", "pattern-evaluation-interval-ms", 200.0))));
        long previousEvaluation = lastAttackPatternEvaluation.getOrDefault(id, 0L);
        if (nowMs - previousEvaluation < evaluationInterval) return;
        lastAttackPatternEvaluation.put(id, nowMs);

        int minimumAttacks = Math.max(5, Math.min(200,
                (int) Math.round(versionedThreshold(id, "click-rate", "minimum-pattern-attacks", 15.0))));
        double minimumCps = Math.max(1.0, Math.min(30.0,
                versionedThreshold(id, "click-rate", "minimum-pattern-cps", 5.0)));
        double maximumDeviation = Math.max(0.0, Math.min(100.0,
                versionedThreshold(id, "click-rate", "max-pattern-interval-stddev-ms", 3.0)));
        double maximumVariation = Math.max(0.0, Math.min(1.0,
                versionedThreshold(id, "click-rate", "max-pattern-coefficient-variation", 0.08)));

        ClickPatternMath.Stats stats = ClickPatternMath.analyze(timestamps);
        boolean candidate = ClickPatternMath.isRegular(stats, minimumAttacks, minimumCps,
                maximumDeviation, maximumVariation);
        int streak = candidate ? attackPatternOverLimitWindows.getOrDefault(id, 0) + 1 : 0;
        attackPatternOverLimitWindows.put(id, streak);

        int requiredWindows = Math.max(1, Math.min(20,
                (int) Math.round(versionedThreshold(id, "click-rate", "minimum-pattern-windows", 3.0))));
        long cooldown = Math.max(0L, Math.round(
                versionedThreshold(id, "click-rate", "pattern-flag-cooldown-ms", 5000.0)));
        long previousFlag = lastAttackPatternFlag.getOrDefault(id, 0L);
        if (streak < requiredWindows || nowMs - previousFlag < cooldown || stats == null) return;

        lastAttackPatternFlag.put(id, nowMs);
        attackPatternOverLimitWindows.put(id, 0);
        String detail = String.format(java.util.Locale.ROOT,
                "regular attack timing: attacks=%d window=%dms rate=%.2f/s mean-interval=%.2fms stddev=%.2fms cv=%.4f repeated-windows=%d; corroborate with independent evidence",
                stats.attackCount(), windowMs, stats.attacksPerSecond(),
                stats.meanIntervalMillis(), stats.standardDeviationMillis(),
                stats.coefficientOfVariation(), streak);
        scheduleClickRateSignal(id, detail);
    }

    private int countRecent(Deque<Long> timestamps, long cutoffMs) {
        int count = 0;
        for (long timestamp : timestamps) if (timestamp >= cutoffMs) count++;
        return count;
    }

    private List<Long> recentSamples(Deque<Long> timestamps, long cutoffMs) {
        java.util.ArrayList<Long> result = new java.util.ArrayList<>();
        for (long timestamp : timestamps) if (timestamp >= cutoffMs) result.add(timestamp);
        return result;
    }

    private void scheduleClickRateSignal(UUID id, String detail) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(id);
            if (player != null) violations.add(player, "click-rate",
                    CheckUtil.getVersionedDouble(plugin, player, "click-rate", "violation-add", 1.0), detail);
        });
    }

    private int boundedSampleLimit(String configPath, int fallback) {
        return Math.max(128, Math.min(4096, plugin.getConfig().getInt(configPath, fallback)));
    }

    private double versionedThreshold(UUID id, String check, String key, double fallback) {
        return VersionedThresholds.resolveForClient(plugin.getConfig(),
                ClientVersionRegistry.get(id), serverVersion, check, key, fallback);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        ClientVersionRegistry.forget(id);
        lastMovementPacket.remove(id);
        movementStreamStarted.remove(id);
        fastIntervals.remove(id);
        packetTimes.remove(id);
        lastFlag.remove(id);
        lastTimerFlag.remove(id);
        attackTimes.remove(id);
        lastAttackEvaluation.remove(id);
        attackOverLimitWindows.remove(id);
        lastAttackFlag.remove(id);
        attackPatternOverLimitWindows.remove(id);
        lastAttackPatternEvaluation.remove(id);
        lastAttackPatternFlag.remove(id);
    }

    public void unregister() {
        if (listener != null) PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        ClientVersionRegistry.clear();
        lastMovementPacket.clear();
        movementStreamStarted.clear();
        fastIntervals.clear();
        packetTimes.clear();
        lastFlag.clear();
        lastTimerFlag.clear();
        attackTimes.clear();
        lastAttackEvaluation.clear();
        attackOverLimitWindows.clear();
        lastAttackFlag.clear();
        attackPatternOverLimitWindows.clear();
        lastAttackPatternEvaluation.clear();
        lastAttackPatternFlag.clear();
    }
}

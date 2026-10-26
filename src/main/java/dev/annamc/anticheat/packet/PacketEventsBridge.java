package dev.annamc.anticheat.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import dev.annamc.anticheat.check.ViolationManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PacketEvents telemetry for unusually fast movement packet streams.
 * This is not a deterministic timer check: proxies, client versions and packet
 * coalescing affect timing, so the check is disabled by default.
 */
public final class PacketEventsBridge {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final Map<UUID, Long> lastMovementPacket = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> fastIntervals = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> packetTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFlag = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTimerFlag = new ConcurrentHashMap<>();
    private PacketListenerAbstract listener;

    public PacketEventsBridge(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    public void register() {
        listener = new PacketListenerAbstract() {
            @Override
            public void onPacketReceive(PacketReceiveEvent event) {
                if (!plugin.getConfig().getBoolean("checks.packet-rate.enabled", false)) return;
                String type = String.valueOf(event.getPacketType()).toUpperCase(java.util.Locale.ROOT);
                if (!(type.contains("PLAYER_POSITION") || type.contains("PLAYER_ROTATION")
                        || type.endsWith("FLYING") || type.contains("PLAYER_POSITION_AND_ROTATION"))) return;

                UUID id = event.getUser().getUUID();
                long nowNanos = System.nanoTime();
                long nowMs = nowNanos / 1_000_000L;
                Long previous = lastMovementPacket.put(id, nowNanos);
                long intervalMs = previous == null ? Long.MAX_VALUE : (nowNanos - previous) / 1_000_000L;
                long minimum = plugin.getConfig().getLong("checks.packet-rate.minimum-interval-ms", 5);
                int fast = intervalMs < minimum ? fastIntervals.merge(id, 1, Integer::sum)
                        : fastIntervals.compute(id, (key, value) -> value == null ? 0 : Math.max(0, value - 1));

                Deque<Long> times = packetTimes.computeIfAbsent(id, ignored -> new ArrayDeque<>());
                int pps;
                synchronized (times) {
                    times.addLast(nowMs);
                    long cutoff = nowMs - 1000L;
                    while (!times.isEmpty() && times.peekFirst() < cutoff) times.removeFirst();
                    pps = times.size();
                }
                int maxPps = plugin.getConfig().getInt("checks.packet-rate.max-movement-packets-per-second", 45);
                int sampleWindow = plugin.getConfig().getInt("checks.packet-rate.sample-window", 40);
                long cooldown = plugin.getConfig().getLong("checks.packet-rate.flag-cooldown-ms", 3000L);
                long previousFlag = lastFlag.getOrDefault(id, 0L);
                Player player = Bukkit.getPlayer(id);
                if ((fast >= sampleWindow || dev.annamc.anticheat.check.DetectionMath.exceedsPacketRate(pps, maxPps))
                        && nowMs - previousFlag >= cooldown) {
                    lastFlag.put(id, nowMs);
                    fastIntervals.put(id, 0);
                    if (player != null) {
                        String detail = "movement packet telemetry: rolling-pps=" + pps
                                + ", fast-interval-streak=" + fast
                                + "; review proxy, ping and client before action";
                        Bukkit.getScheduler().runTask(plugin, () -> violations.add(player, "packet-rate", 1.0, detail));
                    }
                }

                if (player != null && plugin.getConfig().getBoolean("checks.timer.enabled", false)) {
                    long firstPacket;
                    synchronized (times) { firstPacket = times.isEmpty() ? nowMs : times.peekFirst(); }
                    long sampleDuration = plugin.getConfig().getLong("checks.timer.minimum-sample-duration-ms", 1200L);
                    int timerMaxPps = plugin.getConfig().getInt("checks.timer.max-movement-packets-per-second", 23);
                    long timerCooldown = plugin.getConfig().getLong("checks.timer.flag-cooldown-ms", 5000L);
                    long timerPrevious = lastTimerFlag.getOrDefault(id, 0L);
                    if (nowMs - firstPacket >= sampleDuration && pps >= timerMaxPps
                            && nowMs - timerPrevious >= timerCooldown) {
                        lastTimerFlag.put(id, nowMs);
                        String detail = "sustained movement packet rate pps=" + pps
                                + " max=" + timerMaxPps + "; telemetry only, validate ping/proxy/client";
                        Bukkit.getScheduler().runTask(plugin, () -> violations.add(player, "timer",
                                plugin.getConfig().getDouble("checks.timer.violation-add", 1.0), detail));
                    }
                }
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(listener);
    }

    public void unregister() {
        if (listener != null) PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        lastMovementPacket.clear();
        fastIntervals.clear();
        packetTimes.clear();
        lastFlag.clear();
        lastTimerFlag.clear();
    }
}

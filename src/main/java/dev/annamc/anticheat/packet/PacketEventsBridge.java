package dev.annamc.anticheat.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import dev.annamc.anticheat.check.ViolationManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Experimental packet interval telemetry. Timing alone is not proof of cheating.
 * Disabled by default and must be validated against the server's proxy and client mix.
 */
public final class PacketEventsBridge {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final Map<UUID, Long> lastMovementPacket = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> fastIntervals = new ConcurrentHashMap<>();
    private PacketListenerAbstract listener;

    public PacketEventsBridge(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }
    public void register() {
        listener = new PacketListenerAbstract() {
            @Override public void onPacketReceive(PacketReceiveEvent event) {
                if (!plugin.getConfig().getBoolean("checks.packet-rate.enabled", false)) return;
                String type = String.valueOf(event.getPacketType()).toUpperCase();
                if (!(type.contains("PLAYER_POSITION") || type.contains("PLAYER_ROTATION") || type.endsWith("FLYING"))) return;
                UUID id = event.getUser().getUUID();
                long now = System.nanoTime();
                Long previous = lastMovementPacket.put(id, now);
                if (previous == null) return;
                long intervalMs = (now - previous) / 1_000_000L;
                long minimum = plugin.getConfig().getLong("checks.packet-rate.minimum-interval-ms", 5);
                int count;
                if (intervalMs < minimum) count = fastIntervals.merge(id, 1, Integer::sum);
                else count = fastIntervals.compute(id, (key, value) -> value == null ? 0 : Math.max(0, value - 1));
                if (count >= plugin.getConfig().getInt("checks.packet-rate.sample-window", 40)) {
                    Player player = Bukkit.getPlayer(id);
                    if (player != null) Bukkit.getScheduler().runTask(plugin, () ->
                            violations.add(player, "packet-rate", 1.0,
                                    "unusually short movement packet intervals; telemetry only, not proof of cheating"));
                    fastIntervals.put(id, 0);
                }
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(listener);
    }
    public void unregister() {
        if (listener != null) PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        lastMovementPacket.clear();
        fastIntervals.clear();
    }
}

package dev.annamc.anticheat.check;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Main-thread, bounded snapshots of player positions and bounding boxes.
 * Snapshots are only suitable for conservative historical comparisons, not exact
 * client-side hit registration replay.
 */
public final class EntityPositionHistory implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final Map<UUID, Deque<Snapshot>> history = new HashMap<>();
    private BukkitTask task;

    public EntityPositionHistory(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::captureOnlinePlayers, 1L, 1L);
    }

    private void captureOnlinePlayers() {
        long now = System.currentTimeMillis();
        int maxSamples = clamp(plugin.getConfig().getInt("checks.reach.history-max-samples-per-player", 40), 4, 200);
        long retentionMs = clamp(plugin.getConfig().getLong("checks.reach.history-retention-ms", 2000L), 250L, 10000L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location location = player.getLocation();
            BoundingBox box = player.getBoundingBox();
            Snapshot snapshot = new Snapshot(now, location.getWorld().getUID(),
                    location.getX(), location.getY(), location.getZ(),
                    box.getMinX() - location.getX(), box.getMinY() - location.getY(),
                    box.getMinZ() - location.getZ(), box.getMaxX() - location.getX(),
                    box.getMaxY() - location.getY(), box.getMaxZ() - location.getZ());
            Deque<Snapshot> samples = history.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
            samples.addLast(snapshot);
            while (samples.size() > maxSamples) samples.removeFirst();
            while (!samples.isEmpty() && now - samples.peekFirst().timestampMillis() > retentionMs) {
                samples.removeFirst();
            }
        }
    }

    public Snapshot atOrBefore(UUID playerId, UUID worldId, long timestampMillis) {
        if (playerId == null || worldId == null) return null;
        Deque<Snapshot> samples = history.get(playerId);
        if (samples == null || samples.isEmpty()) return null;
        for (var iterator = samples.descendingIterator(); iterator.hasNext();) {
            Snapshot snapshot = iterator.next();
            if (snapshot.timestampMillis() <= timestampMillis) {
                return worldId.equals(snapshot.worldId()) ? snapshot : null;
            }
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        history.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        history.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        history.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        history.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        history.clear();
    }

    public record Snapshot(
            long timestampMillis, UUID worldId,
            double x, double y, double z,
            double minOffsetX, double minOffsetY, double minOffsetZ,
            double maxOffsetX, double maxOffsetY, double maxOffsetZ) {
        public double distanceToBox(double pointX, double pointY, double pointZ) {
            double minX = x + minOffsetX;
            double minY = y + minOffsetY;
            double minZ = z + minOffsetZ;
            double maxX = x + maxOffsetX;
            double maxY = y + maxOffsetY;
            double maxZ = z + maxOffsetZ;
            double dx = pointX < minX ? minX - pointX : Math.max(0.0, pointX - maxX);
            double dy = pointY < minY ? minY - pointY : Math.max(0.0, pointY - maxY);
            double dz = pointZ < minZ ? minZ - pointZ : Math.max(0.0, pointZ - maxZ);
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}

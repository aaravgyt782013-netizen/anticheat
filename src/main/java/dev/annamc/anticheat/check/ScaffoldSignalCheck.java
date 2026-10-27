package dev.annamc.anticheat.check;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-signal scaffold telemetry: repeated downward-angle placements while moving,
 * with stable pitch over a short window. Legitimate bridging can look similar, so
 * this remains disabled by default and must never be used as sole proof.
 */
public final class ScaffoldSignalCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final Map<UUID, PlacementPattern> patterns = new ConcurrentHashMap<>();

    public ScaffoldSignalCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (CheckUtil.shouldSkip(plugin, player, "scaffold-signal")
                || player.isInsideVehicle() || player.isGliding() || player.isFlying()) {
            patterns.remove(player.getUniqueId());
            return;
        }

        Location eye = player.getEyeLocation();
        double horizontal = Math.hypot(eye.getX() - (event.getBlockPlaced().getX() + 0.5),
                eye.getZ() - (event.getBlockPlaced().getZ() + 0.5));
        double vertical = eye.getY() - (event.getBlockPlaced().getY() + 0.5);
        double pitch = player.getLocation().getPitch();
        double pitchLimit = CheckUtil.getVersionedDouble(plugin, player, "scaffold-signal", "minimum-downward-pitch", 65.0);
        double minHorizontal = CheckUtil.getVersionedDouble(plugin, player, "scaffold-signal", "min-horizontal-distance", 0.8);
        double minSpeed = CheckUtil.getVersionedDouble(plugin, player, "scaffold-signal", "minimum-horizontal-speed", 0.10);
        double speed = Math.hypot(player.getVelocity().getX(), player.getVelocity().getZ());
        boolean candidate = pitch >= pitchLimit && horizontal >= minHorizontal && vertical > 0 && speed >= minSpeed;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        PlacementPattern previous = patterns.get(id);
        long window = Math.round(CheckUtil.getVersionedDouble(plugin, player, "scaffold-signal", "pattern-window-ms", 1500.0));
        double pitchVariance = CheckUtil.getVersionedDouble(plugin, player, "scaffold-signal", "maximum-pitch-variance", 2.0);
        int count = candidate && previous != null && now - previous.lastAt <= window
                && Math.abs(pitch - previous.pitch) <= pitchVariance ? previous.count + 1 : (candidate ? 1 : 0);
        patterns.put(id, new PlacementPattern(count, pitch, now));

        int required = (int) Math.round(CheckUtil.getVersionedDouble(plugin, player, "scaffold-signal", "minimum-consecutive-placements", 8.0));
        if (count >= required) {
            violations.add(player, "scaffold-signal",
                    CheckUtil.getVersionedDouble(plugin, player, "scaffold-signal", "violation-add", 0.5),
                    String.format(java.util.Locale.ROOT,
                            "repeated downward placements while moving count=%d pitch=%.1f horizontal=%.2f speed=%.2f; review bridging style",
                            count, pitch, horizontal, speed));
            patterns.put(id, new PlacementPattern(0, pitch, now));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { patterns.remove(event.getPlayer().getUniqueId()); }

    private record PlacementPattern(int count, double pitch, long lastAt) {}
}

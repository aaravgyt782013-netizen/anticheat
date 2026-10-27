package dev.annamc.anticheat.check;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Experimental sustained wall-ascent signal; disabled by default. */
public final class SpiderClimbCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final Map<UUID, Sample> samples = new ConcurrentHashMap<>();

    public SpiderClimbCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY()
                && from.getZ() == to.getZ())) return;
        if (!plugin.getConfig().getBoolean("checks.spider-signal.enabled", false)) return;

        UUID id = player.getUniqueId();
        if (CheckUtil.shouldSkip(plugin, player, "spider-signal") || legitimateState(player)
                || !hasAdjacentSolidWall(to)) {
            samples.remove(id);
            return;
        }

        long now = System.currentTimeMillis();
        long interval = Math.max(35L, Math.round(CheckUtil.getVersionedDouble(plugin, player, "spider-signal", "sample-interval-ms", 75.0)));
        Sample previous = samples.get(id);
        if (previous == null || !previous.world.equals(to.getWorld().getUID())) {
            samples.put(id, new Sample(to, now, 0));
            return;
        }
        if (now - previous.sampledAt < interval) return;
        long gap = now - previous.sampledAt;
        long maxGap = Math.max(interval, Math.round(CheckUtil.getVersionedDouble(plugin, player, "spider-signal", "max-sample-gap-ms", 250.0)));
        if (gap > maxGap) {
            samples.put(id, new Sample(to, now, 0));
            return;
        }

        double dx = to.getX() - previous.x;
        double dy = to.getY() - previous.y;
        double dz = to.getZ() - previous.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double horizontalStepFloor = Math.max(0.0,
                CheckUtil.getVersionedDouble(plugin, player, "spider-signal", "minimum-horizontal-step", 0.5));
        double maxStep = Math.max(horizontalStepFloor,
                CheckUtil.getVersionedDouble(plugin, player, "spider-signal", "max-horizontal-distance-per-sample", 2.5));
        double maxVerticalStep = CheckUtil.getVersionedDouble(
                plugin, player, "spider-signal", "max-vertical-distance-per-sample", 1.25);
        double minimumRise = Math.max(0.0,
                CheckUtil.getVersionedDouble(plugin, player, "spider-signal", "minimum-upward-delta", 0.08));
        if (horizontal > maxStep || Math.abs(dy) > maxVerticalStep) {
            samples.put(id, new Sample(to, now, 0));
            return;
        }

        int count = dy >= minimumRise ? previous.upwardSamples + 1 : 0;
        int required = Math.max(5, Math.min(100,
                (int) Math.round(CheckUtil.getVersionedDouble(plugin, player, "spider-signal", "required-upward-samples", 10.0))));
        samples.put(id, new Sample(to, now, count));
        if (count >= required) {
            samples.put(id, new Sample(to, now, 0));
            violations.add(player, "spider-signal",
                    plugin.getConfig().getDouble("checks.spider-signal.violation-add", 1.0),
                    String.format(java.util.Locale.ROOT,
                            "sustained-wall-ascent samples=%d vertical-step=%.3f horizontal-step=%.3f",
                            count, dy, horizontal));
        }
    }

    private boolean hasAdjacentSolidWall(Location location) {
        double horizontalOffset = Math.max(0.1, Math.min(1.0,
                CheckUtil.getVersionedDouble(plugin, "spider-signal", "wall-sample-horizontal-offset", 0.36)));
        double[][] offsets = {{horizontalOffset, 0}, {-horizontalOffset, 0},
                {0, horizontalOffset}, {0, -horizontalOffset}};
        java.util.List<Double> configured = plugin.getConfig().getDoubleList(
                "checks.spider-signal.wall-sample-heights");
        java.util.List<Double> heights = configured.isEmpty()
                ? java.util.List.of(0.35, 1.05, 1.45) : configured;
        for (double[] offset : offsets) {
            for (double height : heights) {
                if (!Double.isFinite(height) || height < 0.0 || height > 2.5) continue;
                Material type = location.clone().add(offset[0], height, offset[1]).getBlock().getType();
                if (type.isSolid() && !isClimbable(type)) return true;
            }
        }
        return false;
    }

    private boolean isClimbable(Material type) {
        return plugin.getConfig().getStringList("checks.spider-signal.climbable-materials")
                .stream().anyMatch(name -> name.equalsIgnoreCase(type.name()));
    }

    private boolean legitimateState(Player player) {
        Location location = player.getLocation();
        Location feetLocation = location.clone().add(0.0, 0.05, 0.0);
        Location bodyLocation = location.clone().add(0.0, 0.9, 0.0);
        Material feet = feetLocation.getBlock().getType();
        Material body = bodyLocation.getBlock().getType();
        boolean inLiquid = feetLocation.getBlock().isLiquid() || bodyLocation.getBlock().isLiquid();
        return player.isOnGround() || player.isFlying() || player.getAllowFlight()
                || player.isGliding() || player.isInsideVehicle() || player.isSwimming()
                || player.isClimbing() || player.isRiptiding()
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)
                || player.hasPotionEffect(PotionEffectType.JUMP_BOOST)
                || inLiquid || feet == Material.BUBBLE_COLUMN || body == Material.BUBBLE_COLUMN;
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
    }

    private record Sample(UUID world, double x, double y, double z, long sampledAt, int upwardSamples) {
        private Sample(Location location, long sampledAt, int upwardSamples) {
            this(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(),
                    sampledAt, upwardSamples);
        }
    }
}
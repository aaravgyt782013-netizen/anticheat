package dev.annamc.anticheat.check;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Experimental flight/hover signal. It looks for sustained near-horizontal movement
 * while airborne and excludes several legitimate movement states. It is not a complete
 * client-physics simulator; keep disabled until staging tests match your server/plugins.
 */
public final class FlightHoverCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final Map<UUID, Sample> samples = new ConcurrentHashMap<>();

    public FlightHoverCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY()
                && from.getZ() == to.getZ())) return;

        UUID id = player.getUniqueId();
        // Avoid per-move map work for every player while the experimental detector is disabled.
        if (!plugin.getConfig().getBoolean("checks.flight.enabled", false)) return;
        if (CheckUtil.shouldSkip(plugin, player, "flight") || legitimateMovementState(player, to)) {
            samples.remove(id);
            return;
        }

        long now = System.currentTimeMillis();
        long interval = Math.max(35L, plugin.getConfig().getLong("checks.flight.sample-interval-ms", 50L));
        Sample previous = samples.get(id);
        if (previous == null || !previous.world.equals(to.getWorld().getUID())) {
            samples.put(id, new Sample(to, now, 0));
            return;
        }
        if (now - previous.sampledAt < interval) return;
        long gap = now - previous.sampledAt;
        long maxGap = Math.max(interval, plugin.getConfig().getLong("checks.flight.max-sample-gap-ms", 250L));
        if (gap > maxGap) {
            samples.put(id, new Sample(to, now, 0));
            return;
        }

        double dx = to.getX() - previous.x;
        double dy = to.getY() - previous.y;
        double dz = to.getZ() - previous.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double maxStep = Math.max(0.5,
                plugin.getConfig().getDouble("checks.flight.max-horizontal-distance-per-sample", 2.5));
        if (horizontal > maxStep || Math.abs(dy) > 3.0) {
            samples.put(id, new Sample(to, now, 0));
            return;
        }
        double verticalTolerance = Math.max(0.0,
                plugin.getConfig().getDouble("checks.flight.max-hover-vertical-delta", 0.025));
        double minimumHorizontal = Math.max(0.0,
                plugin.getConfig().getDouble("checks.flight.minimum-horizontal-distance", 0.08));

        int count = Math.abs(dy) <= verticalTolerance && horizontal >= minimumHorizontal
                ? previous.hoverSamples + 1 : 0;
        int required = Math.max(5, Math.min(200,
                plugin.getConfig().getInt("checks.flight.required-hover-samples", 24)));

        samples.put(id, new Sample(to, now, count));
        if (count >= required) {
            samples.put(id, new Sample(to, now, 0));
            violations.add(player, "flight",
                    plugin.getConfig().getDouble("checks.flight.violation-add", 1.0),
                    String.format(java.util.Locale.ROOT,
                            "sustained-air-hover samples=%d horizontal-step=%.3f vertical-step=%.3f",
                            count, horizontal, dy));
        }
    }

    private boolean legitimateMovementState(Player player, Location location) {
        if (player.isOnGround() || player.isFlying() || player.getAllowFlight()
                || player.isGliding() || player.isInsideVehicle() || player.isSwimming() || player.isClimbing()
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return true;

        Location body = location.clone().add(0.0, 0.2, 0.0);
        Location feet = location.clone().add(0.0, 0.05, 0.0);
        Location below = location.clone().add(0.0, -0.15, 0.0);
        if (feet.getBlock().isLiquid() || body.getBlock().isLiquid() || below.getBlock().isLiquid()) return true;

        Material beneath = below.getBlock().getType();
        return beneath == Material.SLIME_BLOCK || beneath == Material.HONEY_BLOCK
                || beneath == Material.SOUL_SAND || beneath == Material.SOUL_SOIL
                || beneath == Material.COBWEB || beneath == Material.LADDER
                || beneath == Material.VINE || beneath == Material.TWISTING_VINES
                || beneath == Material.TWISTING_VINES_PLANT || beneath == Material.WEEPING_VINES
                || beneath == Material.WEEPING_VINES_PLANT;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
    }

    private record Sample(UUID world, double x, double y, double z, long sampledAt, int hoverSamples) {
        private Sample(Location location, long sampledAt, int hoverSamples) {
            this(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(),
                    sampledAt, hoverSamples);
        }
    }
}
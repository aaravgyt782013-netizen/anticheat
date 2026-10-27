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
        if (!plugin.getConfig().getBoolean("checks.flight.enabled", false)) {
            samples.remove(id);
            return;
        }
        if (CheckUtil.shouldSkip(plugin, player, "flight") || legitimateMovementState(player, to)) {
            samples.remove(id);
            return;
        }

        long now = System.currentTimeMillis();
        long interval = Math.max(35L, Math.round(CheckUtil.getVersionedDouble(plugin, player, "flight", "sample-interval-ms", 50.0)));
        Sample previous = samples.get(id);
        if (previous == null || !previous.world.equals(to.getWorld().getUID())) {
            samples.put(id, new Sample(to, now, 0));
            return;
        }
        if (now - previous.sampledAt < interval) return;
        long gap = now - previous.sampledAt;
        long maxGap = Math.max(interval, Math.round(CheckUtil.getVersionedDouble(plugin, player, "flight", "max-sample-gap-ms", 250.0)));
        if (gap > maxGap) {
            samples.put(id, new Sample(to, now, 0));
            return;
        }

        double dx = to.getX() - previous.x;
        double dy = to.getY() - previous.y;
        double dz = to.getZ() - previous.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double horizontalStepFloor = Math.max(0.0,
                CheckUtil.getVersionedDouble(plugin, player, "flight", "minimum-horizontal-step", 0.5));
        double maxStep = Math.max(horizontalStepFloor,
                CheckUtil.getVersionedDouble(plugin, player, "flight", "max-horizontal-distance-per-sample", 2.5));
        double maxVerticalStep = CheckUtil.getVersionedDouble(
                plugin, player, "flight", "max-vertical-distance-per-sample", 3.0);
        if (horizontal > maxStep || Math.abs(dy) > maxVerticalStep) {
            samples.put(id, new Sample(to, now, 0));
            return;
        }
        double verticalTolerance = Math.max(0.0,
                CheckUtil.getVersionedDouble(plugin, player, "flight", "max-hover-vertical-delta", 0.025));
        double minimumHorizontal = Math.max(0.0,
                CheckUtil.getVersionedDouble(plugin, player, "flight", "minimum-horizontal-distance", 0.08));

        int count = Math.abs(dy) <= verticalTolerance && horizontal >= minimumHorizontal
                ? previous.hoverSamples + 1 : 0;
        int required = Math.max(5, Math.min(200,
                (int) Math.round(CheckUtil.getVersionedDouble(plugin, player, "flight", "required-hover-samples", 24.0))));

        samples.put(id, new Sample(to, now, count));
        if (count >= required) {
            samples.put(id, new Sample(to, now, 0));
            violations.add(player, "flight",
                    CheckUtil.getVersionedDouble(plugin, player, "flight", "violation-add", 1.0),
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
    public void onTeleport(PlayerTeleportEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
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
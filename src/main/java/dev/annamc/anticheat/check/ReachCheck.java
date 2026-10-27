package dev.annamc.anticheat.check;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Conservative server-side reach signal. Uses the nearest point on the target
 * hitbox rather than the target's feet. This is not a client/server lag simulator.
 */
public final class ReachCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final EntityPositionHistory positionHistory;

    public ReachCheck(JavaPlugin plugin, ViolationManager violations, EntityPositionHistory positionHistory) {
        this.plugin = plugin;
        this.violations = violations;
        this.positionHistory = positionHistory;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity target)
                || player.equals(target)
                || CheckUtil.shouldSkip(plugin, player, "reach")
                || player.getWorld() != target.getWorld()
                || player.isInsideVehicle()
                || player.isGliding()) return;

        Location eye = player.getEyeLocation();
        BoundingBox box = target.getBoundingBox();
        double distance = distanceToCurrentBox(eye, box);

        // Rewind both player and player target by a bounded fraction of attacker
        // latency when historical samples are available. Non-player targets and
        // missing history fall back to current hitboxes.
        boolean compensationEnabled = plugin.getConfig().getBoolean(
                "checks.reach.latency-compensation-enabled", true);
        long maxCompensationMs = Math.max(0L, Math.min(300L,
                plugin.getConfig().getLong("checks.reach.max-compensation-ms", 150L)));
        long rewindMs = compensationEnabled
                ? Math.min(maxCompensationMs, Math.max(0L, player.getPing() / 2L)) : 0L;
        if (rewindMs > 0L && target instanceof Player targetPlayer) {
            long timestamp = System.currentTimeMillis() - rewindMs;
            EntityPositionHistory.Snapshot attackerAt = positionHistory.atOrBefore(
                    player.getUniqueId(), player.getWorld().getUID(), timestamp);
            EntityPositionHistory.Snapshot targetAt = positionHistory.atOrBefore(
                    targetPlayer.getUniqueId(), target.getWorld().getUID(), timestamp);
            if (attackerAt != null && targetAt != null
                    && attackerAt.worldId().equals(targetAt.worldId())) {
                double eyeY = attackerAt.y() + player.getEyeHeight();
                distance = targetAt.distanceToBox(attackerAt.x(), eyeY, attackerAt.z());
            }
        }
        double baseMax = CheckUtil.getVersionedDouble(plugin, player, "reach", "max-distance", 3.6);
        double pingSlack = Math.min(CheckUtil.getVersionedDouble(plugin, player, "reach", "max-ping-slack", 0.20),
                Math.max(0, player.getPing()) * CheckUtil.getVersionedDouble(plugin, player, "reach", "ping-slack-per-ms", 0.001));
        double max = baseMax + pingSlack;
        if (distance > max) {
            violations.add(player, "reach",
                    CheckUtil.getVersionedDouble(plugin, player, "reach", "violation-add", 1.0),
                    String.format(java.util.Locale.ROOT, "eye-to-hitbox=%.3f base-max=%.3f ping-slack=%.3f effective-max=%.3f target=%s",
                            distance, baseMax, pingSlack, max, target.getType().name()));
        }
    }

    private static double distanceToCurrentBox(Location eye, BoundingBox box) {
        double x = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        return eye.distance(new Location(eye.getWorld(), x, y, z));
    }
}

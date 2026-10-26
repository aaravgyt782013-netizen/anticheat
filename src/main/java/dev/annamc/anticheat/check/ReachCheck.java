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

    public ReachCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
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
        double x = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        double distance = eye.distance(new Location(target.getWorld(), x, y, z));
        double baseMax = plugin.getConfig().getDouble("checks.reach.max-distance", 3.6);
        double pingSlack = Math.min(plugin.getConfig().getDouble("checks.reach.max-ping-slack", 0.20),
                Math.max(0, player.getPing()) * plugin.getConfig().getDouble("checks.reach.ping-slack-per-ms", 0.001));
        double max = baseMax + pingSlack;
        if (distance > max) {
            violations.add(player, "reach",
                    plugin.getConfig().getDouble("checks.reach.violation-add", 1.0),
                    String.format(java.util.Locale.ROOT, "eye-to-hitbox=%.3f base-max=%.3f ping-slack=%.3f effective-max=%.3f target=%s",
                            distance, baseMax, pingSlack, max, target.getType().name()));
        }
    }
}

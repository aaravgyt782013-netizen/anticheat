package dev.annamc.anticheat.check;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Conservative wall-hit signal. Samples multiple points on the victim hitbox
 * and flags only when every sampled ray is occluded by a non-passable block.
 * This is not latency-compensated and should be validated before enforcement.
 */
public final class WallHitCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;

    public WallHitCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity target)
                || player.equals(target)
                || CheckUtil.shouldSkip(plugin, player, "wall-hit")
                || player.getWorld() != target.getWorld()
                || player.isInsideVehicle()
                || player.isGliding()
                || target.isDead()) {
            return;
        }

        Location eye = player.getEyeLocation();
        BoundingBox box = target.getBoundingBox();
        double maxDistance = Math.max(1.0,
                CheckUtil.getVersionedDouble(plugin, player, "wall-hit", "max-evaluation-distance", 6.0));
        double margin = Math.max(0.0,
                CheckUtil.getVersionedDouble(plugin, player, "wall-hit", "occlusion-margin", 0.15));

        int maxSamples = plugin.getConfig().getInt("checks.wall-hit.max-sample-points", 9);
        List<Vector> samplePoints = HitboxSamplePoints.resolve(box,
                plugin.getConfig().getMapList("checks.wall-hit.sample-points"), maxSamples);
        if (samplePoints.isEmpty()) return;
        int occluded = 0;
        for (Vector point : samplePoints) {
            Vector toPoint = point.clone().subtract(eye.toVector());
            double distance = toPoint.length();
            if (distance <= 0.001 || distance > maxDistance) {
                return;
            }
            RayTraceResult trace = player.getWorld().rayTraceBlocks(
                    eye, toPoint.normalize(), distance, FluidCollisionMode.NEVER, true);
            if (trace != null && trace.getHitBlock() != null
                    && trace.getHitPosition().distance(eye.toVector()) < distance - margin) {
                occluded++;
            }
        }

        double ratio = (double) occluded / samplePoints.size();
        double requiredRatio = CheckUtil.getVersionedDouble(plugin, player, "wall-hit", "minimum-occluded-sample-ratio", 1.0);
        requiredRatio = Math.max(0.0, Math.min(1.0, requiredRatio));
        if (ratio >= requiredRatio && occluded > 0) {
            violations.add(player, "wall-hit",
                    CheckUtil.getVersionedDouble(plugin, player, "wall-hit", "violation-add", 1.0),
                    String.format(java.util.Locale.ROOT,
                            "occluded-hitbox-samples=%d/%d ratio=%.2f target=%s ping=%dms; inspect latency and geometry",
                            occluded, samplePoints.size(), ratio, target.getType().name(), player.getPing()));
        }
    }

}

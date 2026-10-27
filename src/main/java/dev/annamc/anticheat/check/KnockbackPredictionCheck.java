package dev.annamc.anticheat.check;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Knockback telemetry compares movement after server velocity is applied.
 * It is intentionally alert-only by default: collisions, sprinting, walls,
 * water, active input and latency can all affect the observed displacement.
 */
public final class KnockbackPredictionCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public KnockbackPredictionCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim
                && event.getDamager() instanceof Player attacker
                && !victim.equals(attacker)
                && plugin.getConfig().getBoolean("checks.knockback.enabled", false)
                && !CheckUtil.shouldSkip(plugin, victim, "knockback")) {
            pending.put(victim.getUniqueId(), new Pending(victim.getLocation().clone(),
                    System.currentTimeMillis(), 0, attacker.getUniqueId()));
        }
    }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("checks.knockback.enabled", false)
                || CheckUtil.shouldSkip(plugin, player, "knockback")) {
            pending.remove(player.getUniqueId());
            return;
        }
        Vector velocity = event.getVelocity();
        double horizontal = Math.hypot(velocity.getX(), velocity.getZ());
        double min = CheckUtil.getVersionedDouble(plugin, player, "knockback", "minimum-horizontal-velocity", 0.25);
        if (horizontal < min) return;
        pending.put(player.getUniqueId(), new Pending(player.getLocation().clone(),
                System.currentTimeMillis(), 0, null, velocity.clone()));
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) { pending.remove(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { pending.remove(event.getPlayer().getUniqueId()); }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("checks.knockback.enabled", false)
                || CheckUtil.shouldSkip(plugin, player, "knockback")
                || player.isInsideVehicle() || player.isGliding() || player.isSwimming()
                || player.isInWater()) {
            pending.remove(player.getUniqueId());
            return;
        }
        Pending p = pending.get(player.getUniqueId());
        if (p == null) return;
        long age = System.currentTimeMillis() - p.startedAt;
        int baseMaxAge = plugin.getConfig().getInt("checks.knockback.observation-window-ms", 350);
        int pingExtension = (int) Math.min(plugin.getConfig().getInt("checks.knockback.max-ping-window-extension-ms", 200),
                Math.max(0, player.getPing()) * CheckUtil.getVersionedDouble(plugin, player, "knockback", "ping-window-extension-per-ms", 0.5));
        int maxAge = baseMaxAge + pingExtension;
        if (age > maxAge) {
            pending.remove(player.getUniqueId());
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() != p.origin.getWorld()) return;
        double dx = to.getX() - p.origin.getX(), dz = to.getZ() - p.origin.getZ();
        double displacement = Math.hypot(dx, dz);
        int observations = p.observations + 1;
        Pending updated = p.withObservations(observations);
        pending.put(player.getUniqueId(), updated);
        int required = plugin.getConfig().getInt("checks.knockback.minimum-observations", 4);
        double velocityLength = Math.hypot(p.velocity.getX(), p.velocity.getZ());
        if (velocityLength < CheckUtil.getVersionedDouble(plugin, player, "knockback", "minimum-horizontal-velocity", 0.25)) {
            pending.remove(player.getUniqueId());
            return;
        }
        if (observations >= required && age >= plugin.getConfig().getInt("checks.knockback.minimum-age-ms", 120)) {
            double projection = (dx * p.velocity.getX() + dz * p.velocity.getZ()) / velocityLength;
            double threshold = CheckUtil.getVersionedDouble(plugin, player, "knockback", "minimum-forward-projection", 0.04);
            double minimumDisplacement = Math.max(0.0,
                    CheckUtil.getVersionedDouble(plugin, player, "knockback", "minimum-observed-displacement", 0.08));
            if (displacement < minimumDisplacement || projection < threshold) {
                violations.add(player, "knockback",
                        plugin.getConfig().getDouble("checks.knockback.violation-add", 1.0),
                        String.format(java.util.Locale.ROOT,
                                "low movement projection %.3f / displacement %.3f (minimum %.3f) along server velocity within %dms; review ping/collisions/input",
                                projection, displacement, minimumDisplacement, age));
            }
            pending.remove(player.getUniqueId());
        }
    }

    private record Pending(Location origin, long startedAt, int observations, UUID attacker, Vector velocity) {
        Pending(Location origin, long startedAt, int observations, UUID attacker) {
            this(origin, startedAt, observations, attacker, new Vector());
        }
        Pending withObservations(int count) { return new Pending(origin, startedAt, count, attacker, velocity); }
    }
}

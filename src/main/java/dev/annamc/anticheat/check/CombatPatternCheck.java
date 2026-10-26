package dev.annamc.anticheat.check;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Low-confidence combat telemetry: hit-angle outliers and repeated rotation deltas.
 * Bukkit events do not expose authoritative client attack packets or a historical
 * target hitbox, so these signals must not be treated as standalone KillAura proof.
 */
public final class CombatPatternCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final Map<UUID, Rotation> rotations = new ConcurrentHashMap<>();
    private final Map<UUID, Pattern> patterns = new ConcurrentHashMap<>();

    public CombatPatternCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (CheckUtil.shouldSkip(plugin, p, "combat-pattern")) return;
        Location from = event.getFrom(), to = event.getTo();
        if (to == null) return;
        float yaw = normalize(to.getYaw() - from.getYaw());
        float pitch = to.getPitch() - from.getPitch();
        if (Math.abs(yaw) + Math.abs(pitch) < 0.01f) return;
        rotations.put(p.getUniqueId(), new Rotation(yaw, pitch, System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p) || !(event.getEntity() instanceof LivingEntity target)
                || CheckUtil.shouldSkip(plugin, p, "combat-pattern") || p.getWorld() != target.getWorld()) return;
        long now = System.currentTimeMillis();

        double angle = hitAngleDegrees(p, target);
        double maxAngle = plugin.getConfig().getDouble("checks.combat-pattern.maximum-hit-angle-degrees", 22.0);
        double pingAllowance = Math.min(plugin.getConfig().getDouble("checks.combat-pattern.max-ping-angle-allowance", 8.0),
                Math.max(0, p.getPing()) * plugin.getConfig().getDouble("checks.combat-pattern.ping-angle-allowance-per-ms", 0.02));
        if (Double.isFinite(angle) && angle > maxAngle + pingAllowance) {
            violations.add(p, "combat-pattern",
                    plugin.getConfig().getDouble("checks.combat-pattern.hit-angle-violation-add", 0.5),
                    String.format(java.util.Locale.ROOT,
                            "hit-angle=%.1f degrees max=%.1f plus ping allowance; review historical hitbox and latency",
                            angle, maxAngle + pingAllowance));
        }

        Rotation rotation = rotations.get(p.getUniqueId());
        if (rotation == null || now - rotation.time > plugin.getConfig().getLong("checks.combat-pattern.rotation-window-ms", 180L)) return;
        Pattern previous = patterns.getOrDefault(p.getUniqueId(), new Pattern(0, 0, 0, 0, now));
        long window = plugin.getConfig().getLong("checks.combat-pattern.pattern-window-ms", 5000L);
        if (now - previous.lastHitAt > window) previous = new Pattern(0, 0, 0, 0, now);
        double quantization = plugin.getConfig().getDouble("checks.combat-pattern.rotation-quantization-degrees", 0.15);
        boolean same = DetectionMath.sameRotationDelta(rotation.yaw, rotation.pitch,
                previous.yaw, previous.pitch, quantization);
        int repeated = same ? previous.repeated + 1 : Math.max(0, previous.repeated - 1);
        int hits = previous.hits + 1;
        patterns.put(p.getUniqueId(), new Pattern(rotation.yaw, rotation.pitch, repeated, hits, now));
        int minHits = plugin.getConfig().getInt("checks.combat-pattern.minimum-hits", 12);
        int minRepeats = plugin.getConfig().getInt("checks.combat-pattern.minimum-repeated-rotations", 7);
        if (hits >= minHits && repeated >= minRepeats) {
            violations.add(p, "combat-pattern",
                    plugin.getConfig().getDouble("checks.combat-pattern.rotation-violation-add", 0.5),
                    "repeated near-identical hit-adjacent rotation deltas; review manually");
            patterns.put(p.getUniqueId(), new Pattern(rotation.yaw, rotation.pitch, 0, 0, now));
        }
    }

    private double hitAngleDegrees(Player player, LivingEntity target) {
        Location eye = player.getEyeLocation();
        BoundingBox box = target.getBoundingBox();
        double x = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
        double y = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
        double z = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
        Vector toward = new Vector(x - eye.getX(), y - eye.getY(), z - eye.getZ());
        if (toward.lengthSquared() < 1.0e-8) return 0.0;
        Vector look = eye.getDirection().normalize();
        double dot = Math.max(-1.0, Math.min(1.0, look.dot(toward.normalize())));
        return Math.toDegrees(Math.acos(dot));
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        rotations.remove(event.getPlayer().getUniqueId());
        patterns.remove(event.getPlayer().getUniqueId());
    }

    private static float normalize(float value) {
        while (value > 180) value -= 360;
        while (value < -180) value += 360;
        return value;
    }
    private record Rotation(float yaw, float pitch, long time) {}
    private record Pattern(float yaw, float pitch, int repeated, int hits, long lastHitAt) {}
}

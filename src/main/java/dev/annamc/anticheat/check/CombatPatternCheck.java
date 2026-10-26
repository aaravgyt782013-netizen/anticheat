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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Low-confidence combat-pattern telemetry. It looks for repeated near-identical
 * rotation deltas immediately around hits; it does not identify KillAura on its own.
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
        if (!(event.getDamager() instanceof Player p) || !(event.getEntity() instanceof LivingEntity)
                || CheckUtil.shouldSkip(plugin, p, "combat-pattern")) return;
        Rotation rotation = rotations.get(p.getUniqueId());
        if (rotation == null || System.currentTimeMillis() - rotation.time > 180) return;

        Pattern previous = patterns.getOrDefault(p.getUniqueId(), new Pattern(0, 0, 0, 0));
        double quantization = plugin.getConfig().getDouble("checks.combat-pattern.rotation-quantization-degrees", 0.15);
        boolean same = DetectionMath.sameRotationDelta(rotation.yaw, rotation.pitch,
                previous.yaw, previous.pitch, quantization);
        int repeated = same ? previous.repeated + 1 : Math.max(0, previous.repeated - 1);
        int hits = previous.hits + 1;
        patterns.put(p.getUniqueId(), new Pattern(rotation.yaw, rotation.pitch, repeated, hits));
        int minHits = plugin.getConfig().getInt("checks.combat-pattern.minimum-hits", 12);
        int minRepeats = plugin.getConfig().getInt("checks.combat-pattern.minimum-repeated-rotations", 7);
        if (hits >= minHits && repeated >= minRepeats) {
            violations.add(p, "combat-pattern",
                    plugin.getConfig().getDouble("checks.combat-pattern.violation-add", 0.5),
                    "repeated near-identical hit-adjacent rotation deltas; review manually");
            patterns.put(p.getUniqueId(), new Pattern(rotation.yaw, rotation.pitch, 0, 0));
        }
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
    private record Pattern(float yaw, float pitch, int repeated, int hits) {}
}

package dev.annamc.anticheat.check;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conservative movement-envelope check. Bukkit does not expose every client input
 * packet, so this deliberately reports repeated outliers rather than claiming
 * to be a full deterministic Minecraft physics simulator.
 */
public final class MovementPhysicsCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final Map<UUID, Sample> samples = new ConcurrentHashMap<>();
    private final Map<UUID, VelocityImpulse> recentVelocity = new ConcurrentHashMap<>();

    public MovementPhysicsCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        samples.remove(id);
        recentVelocity.remove(id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        if (!plugin.getConfig().getBoolean("checks.movement-physics.enabled", false)) return;
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        long now = System.nanoTime();
        org.bukkit.util.Vector velocity = event.getVelocity();
        recentVelocity.put(id, new VelocityImpulse(now, velocity.getX(), velocity.getZ()));
        samples.put(id, new Sample(player.getLocation().clone(), now));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        samples.remove(id);
        recentVelocity.remove(id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;
        UUID id = player.getUniqueId();
        if (CheckUtil.shouldSkip(plugin, player, "movement-physics")) {
            samples.remove(id);
            recentVelocity.remove(id);
            return;
        }
        if (player.isInsideVehicle() || player.isFlying() || player.isGliding()
                || player.isSwimming() || player.isRiptiding() || player.isInWater()
                || player.isClimbing() || player.isSleeping()) {
            samples.remove(id);
            recentVelocity.remove(id);
            return;
        }
        Location from = event.getFrom();
        if (from.getWorld() != to.getWorld()) {
            samples.remove(id);
            recentVelocity.remove(id);
            return;
        }

        long now = System.nanoTime();
        VelocityImpulse impulse = recentVelocity.get(id);
        long velocityAgeMs = impulse == null ? Long.MAX_VALUE
                : Math.max(0L, (now - impulse.nanoTime) / 1_000_000L);
        long graceMs = CheckUtil.getVersionedGraceMillis(plugin, player, "movement-physics", 300L);
        if (impulse != null && velocityAgeMs <= graceMs) {
            samples.put(id, new Sample(to.clone(), now));
            return;
        }
        long velocityInfluenceMs = Math.max(0L, Math.min(5000L, Math.round(
                CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "velocity-influence-ms", 650.0))));
        if (impulse != null && velocityAgeMs > velocityInfluenceMs) {
            recentVelocity.remove(id, impulse);
            impulse = null;
            velocityAgeMs = Long.MAX_VALUE;
        }
        Sample previous = samples.get(id);
        if (previous == null) { samples.put(player.getUniqueId(), new Sample(to.clone(), now)); return; }
        long elapsedMs = Math.max(1L, (now - previous.nanoTime) / 1_000_000L);
        if (elapsedMs > Math.round(CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "max-sample-gap-ms", 250.0))) { samples.put(player.getUniqueId(), new Sample(to.clone(), now)); return; }

        double dx = to.getX() - previous.location.getX();
        double dz = to.getZ() - previous.location.getZ();
        double distance = Math.hypot(dx, dz);
        // Build a deterministic envelope from known movement state, latency and
        // recent server-applied velocity. This remains a heuristic, not exact physics replay.
        var speedEffect = player.getPotionEffect(PotionEffectType.SPEED);
        int speedAmplifier = speedEffect == null ? -1 : speedEffect.getAmplifier();
        MovementPredictionEngine.Prediction prediction = MovementPredictionEngine.predict(
                new MovementPredictionEngine.Input(
                        elapsedMs,
                        CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "base-horizontal-per-tick", 0.72),
                        player.isSprinting(),
                        CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "sprint-multiplier", 1.3),
                        speedAmplifier,
                        CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "speed-potion-multiplier-per-level", 0.20),
                        player.getGameMode() == GameMode.ADVENTURE,
                        CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "adventure-mode-multiplier", 1.03),
                        Math.max(0, player.getPing()),
                        CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "ping-slack-per-ms", 0.001),
                        CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "max-ping-slack", 0.75),
                        isOnSlipperyOrSpecialBlock(player),
                        CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "special-block-allowance-per-tick", 0.18),
                        impulse == null ? 0.0 : impulse.velocityX,
                        impulse == null ? 0.0 : impulse.velocityZ,
                        velocityAgeMs == Long.MAX_VALUE ? 0.0 : velocityAgeMs,
                        velocityInfluenceMs,
                        CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "velocity-allowance-multiplier", 1.0),
                        CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "max-velocity-allowance", 0.8)));
        double allowance = prediction.horizontalAllowance();
        double ticks = prediction.elapsedTicks();
        double pingSlack = prediction.latencyAllowance();

        double ratio = distance / Math.max(0.01, allowance);
        if (DetectionMath.isMovementOutlier(distance, allowance, CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "outlier-ratio", 2.2))) {
            int streak = previous.outlierStreak + 1;
            samples.put(id, new Sample(to.clone(), now, streak));
            int required = (int) Math.round(CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "required-consecutive-outliers", 3.0));
            if (streak >= required) {
                violations.add(player, "movement-physics",
                        CheckUtil.getVersionedDouble(plugin, player, "movement-physics", "violation-add", 1.0),
                        String.format(java.util.Locale.ROOT,
                                "repeated movement-envelope outlier ratio=%.2f distance=%.3f allowance=%.3f ping=%dms",
                                ratio, distance, allowance, player.getPing()));
                samples.put(id, new Sample(to.clone(), now, 0));
            }
        } else {
            // Always advance the baseline. Keeping an old baseline across normal
            // movement makes later distances cover multiple events and corrupts
            // the per-sample movement envelope.
            samples.put(id, new Sample(to.clone(), now, 0));
        }
    }

    private boolean isOnSlipperyOrSpecialBlock(Player player) {
        Material type = player.getLocation().getBlock().getRelative(0, -1, 0).getType();
        return plugin.getConfig().getStringList("checks.movement-physics.special-block-materials")
                .stream().anyMatch(name -> name.equalsIgnoreCase(type.name()));
    }

    private record VelocityImpulse(long nanoTime, double velocityX, double velocityZ) {}

    private record Sample(Location location, long nanoTime, int outlierStreak) {
        Sample(Location location, long nanoTime) { this(location, nanoTime, 0); }
    }
}

package dev.annamc.anticheat.check;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
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

    public MovementPhysicsCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null || CheckUtil.shouldSkip(plugin, player, "movement-physics")) return;
        if (player.isInsideVehicle() || player.isFlying() || player.isGliding()
                || player.isSwimming() || player.isRiptiding() || player.isInWater()
                || player.isClimbing() || player.isSleeping()) {
            samples.remove(player.getUniqueId());
            return;
        }
        Location from = event.getFrom();
        if (from.getWorld() != to.getWorld()) {
            samples.remove(player.getUniqueId());
            return;
        }

        long now = System.nanoTime();
        Sample previous = samples.get(player.getUniqueId());
        if (previous == null) { samples.put(player.getUniqueId(), new Sample(to.clone(), now)); return; }
        long elapsedMs = Math.max(1L, (now - previous.nanoTime) / 1_000_000L);
        if (elapsedMs > plugin.getConfig().getLong("checks.movement-physics.max-sample-gap-ms", 250)) { samples.put(player.getUniqueId(), new Sample(to.clone(), now)); return; }

        double dx = to.getX() - previous.location.getX();
        double dz = to.getZ() - previous.location.getZ();
        double distance = Math.hypot(dx, dz);
        // Multiple PlayerMoveEvents may occur in one tick. Scale a conservative
        // per-tick envelope by observed elapsed time and allow ping/jitter slack.
        double ticks = Math.max(1.0, elapsedMs / 50.0);
        double base = plugin.getConfig().getDouble("checks.movement-physics.base-horizontal-per-tick", 0.72);
        if (player.isSprinting()) base *= plugin.getConfig().getDouble("checks.movement-physics.sprint-multiplier", 1.3);
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = player.getPotionEffect(PotionEffectType.SPEED).getAmplifier() + 1;
            base *= 1.0 + 0.20 * amplifier;
        }
        if (player.getGameMode() == GameMode.ADVENTURE) base *= 1.03;
        double pingSlack = Math.min(plugin.getConfig().getDouble("checks.movement-physics.max-ping-slack", 0.75),
                Math.max(0, player.getPing()) * plugin.getConfig().getDouble("checks.movement-physics.ping-slack-per-ms", 0.001));
        double allowance = base * ticks + pingSlack;
        if (isOnSlipperyOrSpecialBlock(player)) allowance += plugin.getConfig().getDouble("checks.movement-physics.special-block-allowance-per-tick", 0.18) * ticks;

        double ratio = distance / Math.max(0.01, allowance);
        if (DetectionMath.isMovementOutlier(distance, allowance, plugin.getConfig().getDouble("checks.movement-physics.outlier-ratio", 2.2))) {
            int streak = previous.outlierStreak + 1;
            samples.put(player.getUniqueId(), new Sample(to.clone(), now, streak));
            int required = plugin.getConfig().getInt("checks.movement-physics.required-consecutive-outliers", 3);
            if (streak >= required) {
                violations.add(player, "movement-physics",
                        plugin.getConfig().getDouble("checks.movement-physics.violation-add", 1.0),
                        String.format(java.util.Locale.ROOT,
                                "repeated movement-envelope outlier ratio=%.2f distance=%.3f allowance=%.3f ping=%dms",
                                ratio, distance, allowance, player.getPing()));
                samples.put(player.getUniqueId(), new Sample(to.clone(), now, 0));
            }
        } else if (previous.outlierStreak != 0) {
            samples.put(player.getUniqueId(), new Sample(to.clone(), now, 0));
        }
    }

    private boolean isOnSlipperyOrSpecialBlock(Player player) {
        Material type = player.getLocation().getBlock().getRelative(0, -1, 0).getType();
        return type == Material.ICE || type == Material.PACKED_ICE || type == Material.BLUE_ICE
                || type == Material.SLIME_BLOCK || type == Material.HONEY_BLOCK;
    }

    private record Sample(Location location, long nanoTime, int outlierStreak) {
        Sample(Location location, long nanoTime) { this(location, nanoTime, 0); }
    }
}

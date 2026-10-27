package dev.annamc.anticheat.check;

import org.bukkit.Location;
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

/** Experimental liquid-walk signal; disabled by default and not a full movement simulator. */
public final class JesusWaterWalkCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final Map<UUID, Sample> samples = new ConcurrentHashMap<>();

    public JesusWaterWalkCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY()
                && from.getZ() == to.getZ())) return;
        if (!plugin.getConfig().getBoolean("checks.jesus.enabled", false)) return;

        UUID id = player.getUniqueId();
        if (CheckUtil.shouldSkip(plugin, player, "jesus") || legitimateState(player)
                || !standingOverDeepLiquid(to)) {
            samples.remove(id);
            return;
        }

        long now = System.currentTimeMillis();
        long interval = Math.max(35L, plugin.getConfig().getLong("checks.jesus.sample-interval-ms", 50L));
        Sample previous = samples.get(id);
        if (previous == null || !previous.world.equals(to.getWorld().getUID())) {
            samples.put(id, new Sample(to, now, 0));
            return;
        }
        if (now - previous.sampledAt < interval) return;
        long gap = now - previous.sampledAt;
        long maxGap = Math.max(interval, plugin.getConfig().getLong("checks.jesus.max-sample-gap-ms", 250L));
        if (gap > maxGap) {
            samples.put(id, new Sample(to, now, 0));
            return;
        }

        double dx = to.getX() - previous.x;
        double dy = to.getY() - previous.y;
        double dz = to.getZ() - previous.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double maxStep = Math.max(0.5,
                plugin.getConfig().getDouble("checks.jesus.max-horizontal-distance-per-sample", 1.8));
        if (horizontal > maxStep || Math.abs(dy) > 1.5) {
            samples.put(id, new Sample(to, now, 0));
            return;
        }

        double verticalTolerance = Math.max(0.0,
                CheckUtil.getVersionedDouble(plugin, "jesus", "max-vertical-delta", 0.04));
        double minHorizontal = Math.max(0.0,
                plugin.getConfig().getDouble("checks.jesus.minimum-horizontal-distance", 0.06));
        int count = Math.abs(dy) <= verticalTolerance && horizontal >= minHorizontal
                ? previous.stableSamples + 1 : 0;
        int required = Math.max(4, Math.min(100,
                plugin.getConfig().getInt("checks.jesus.required-stable-samples", 10)));
        samples.put(id, new Sample(to, now, count));
        if (count >= required) {
            samples.put(id, new Sample(to, now, 0));
            violations.add(player, "jesus",
                    plugin.getConfig().getDouble("checks.jesus.violation-add", 1.0),
                    String.format(java.util.Locale.ROOT,
                            "stable-liquid-surface movement samples=%d horizontal-step=%.3f vertical-step=%.3f",
                            count, horizontal, dy));
        }
    }

    private boolean standingOverDeepLiquid(Location location) {
        Location feet = location.clone().add(0.0, -0.05, 0.0);
        Location below = location.clone().add(0.0, -1.05, 0.0);
        return feet.getBlock().isLiquid() && below.getBlock().isLiquid();
    }

    private boolean legitimateState(Player player) {
        return player.isFlying() || player.getAllowFlight() || player.isGliding()
                || player.isInsideVehicle() || player.isSwimming() || player.isClimbing()
                || player.isRiptiding()
                || player.hasPotionEffect(PotionEffectType.LEVITATION)
                || player.hasPotionEffect(PotionEffectType.SLOW_FALLING);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
    }

    private record Sample(UUID world, double x, double y, double z, long sampledAt, int stableSamples) {
        private Sample(Location location, long sampledAt, int stableSamples) {
            this(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ(),
                    sampledAt, stableSamples);
        }
    }
}
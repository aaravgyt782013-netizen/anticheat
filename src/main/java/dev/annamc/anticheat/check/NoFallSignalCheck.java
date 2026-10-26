package dev.annamc.anticheat.check;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Low-confidence no-fall signal. Landing is evaluated after a short delay so
 * Bukkit and other plugins can process fall damage first. Disabled by default.
 */
public final class NoFallSignalCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final Map<UUID, Fall> falls = new ConcurrentHashMap<>();

    public NoFallSignalCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) { falls.remove(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { falls.remove(event.getPlayer().getUniqueId()); }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (CheckUtil.shouldSkip(plugin, p, "no-fall") || p.isFlying() || p.isGliding()
                || p.isInsideVehicle() || p.isSwimming() || p.isInWater()) {
            falls.remove(p.getUniqueId());
            return;
        }
        Location to = event.getTo();
        if (to == null) return;
        Fall fall = falls.get(p.getUniqueId());
        if (!p.isOnGround()) {
            double minimum = plugin.getConfig().getDouble("checks.no-fall.minimum-fall-distance", 4.0);
            if (p.getFallDistance() >= minimum && (fall == null || !fall.airborne))
                falls.put(p.getUniqueId(), new Fall(true, false, System.currentTimeMillis(), false));
            return;
        }
        if (fall != null && fall.airborne && !fall.landingScheduled) {
            long age = System.currentTimeMillis() - fall.startedAt;
            long observationWindow = Math.max(1L,
                    plugin.getConfig().getLong("checks.no-fall.landing-observation-window-ms", 1500L));
            if (age <= observationWindow) {
                Fall scheduled = new Fall(true, fall.damageObserved, fall.startedAt, true);
                falls.put(p.getUniqueId(), scheduled);
                UUID id = p.getUniqueId();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Fall latest = falls.get(id);
                    if (latest == null || latest.startedAt != scheduled.startedAt) return;
                    if (!latest.damageObserved && p.isOnline()) {
                        violations.add(p, "no-fall",
                                plugin.getConfig().getDouble("checks.no-fall.violation-add", 1.0),
                                "landed after a significant fall without observed fall damage; review protection plugins");
                    }
                    falls.remove(id);
                }, Math.max(1L, plugin.getConfig().getLong("checks.no-fall.landing-delay-ticks", 2L)));
            } else {
                // A fall that took longer than the observation window is stale.
                // Clear it instead of preventing the next fall from being tracked.
                falls.remove(p.getUniqueId(), fall);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Fall f = falls.get(p.getUniqueId());
        if (f != null) falls.put(p.getUniqueId(), new Fall(f.airborne, true, f.startedAt, f.landingScheduled));
    }

    private record Fall(boolean airborne, boolean damageObserved, long startedAt, boolean landingScheduled) {}
}

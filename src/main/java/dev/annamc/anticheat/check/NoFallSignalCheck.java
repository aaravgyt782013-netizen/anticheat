package dev.annamc.anticheat.check;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects a narrow no-fall signal: a significant fall followed by a landing
 * without a server-side fall-damage event. Other plugins, water, blocks and
 * gameplay effects can produce the same pattern, so it is disabled by default.
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
            double max = plugin.getConfig().getDouble("checks.no-fall.minimum-fall-distance", 4.0);
            if (p.getFallDistance() >= max && (fall == null || !fall.airborne))
                falls.put(p.getUniqueId(), new Fall(true, false, System.currentTimeMillis()));
            return;
        }
        if (fall != null && fall.airborne && !fall.damageObserved
                && System.currentTimeMillis() - fall.startedAt <= 1000) {
            violations.add(p, "no-fall",
                    plugin.getConfig().getDouble("checks.no-fall.violation-add", 1.0),
                    "landed after a significant fall without observed fall damage; review protection plugins");
            falls.remove(p.getUniqueId());
        } else if (fall != null && p.isOnGround()) falls.remove(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p) || event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        Fall f = falls.get(p.getUniqueId());
        if (f != null) falls.put(p.getUniqueId(), new Fall(f.airborne, true, f.startedAt));
    }

    private record Fall(boolean airborne, boolean damageObserved, long startedAt) {}
}

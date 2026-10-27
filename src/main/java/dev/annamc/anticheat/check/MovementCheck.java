package dev.annamc.anticheat.check;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Conservative horizontal displacement heuristic, not a full movement physics simulator. */
public final class MovementCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final Map<UUID, Long> teleported = new ConcurrentHashMap<>();
    public MovementCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }
    @EventHandler public void onTeleport(PlayerTeleportEvent event) {
        teleported.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        teleported.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler(ignoreCancelled = true) public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom(), to = event.getTo();
        if (to == null || CheckUtil.shouldSkip(plugin, player, "movement")) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                || player.isFlying() || player.isInsideVehicle() || player.isGliding() || player.isSwimming()) return;
        if (System.currentTimeMillis() - teleported.getOrDefault(player.getUniqueId(), 0L) < 1000) return;
        if (from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return;
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double max = CheckUtil.getVersionedDouble(plugin, player, "movement", "max-horizontal-distance-per-tick", 1.25);
        if (horizontal > max)
            violations.add(player, "movement", plugin.getConfig().getDouble("checks.movement.violation-add", 1.0),
                    String.format("horizontal=%.3f threshold=%.3f", horizontal, max));
    }
}

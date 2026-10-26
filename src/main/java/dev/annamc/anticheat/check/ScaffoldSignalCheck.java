package dev.annamc.anticheat.check;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Weak scaffold signal based on placement angle. Disabled by default because
 * bridging styles and server mechanics vary substantially.
 */
public final class ScaffoldSignalCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;

    public ScaffoldSignalCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (CheckUtil.shouldSkip(plugin, player, "scaffold-signal")
                || player.getGameMode().name().equals("CREATIVE")
                || player.isInsideVehicle()
                || player.isGliding()) return;

        Location eye = player.getEyeLocation();
        double horizontal = Math.hypot(eye.getX() - (event.getBlockPlaced().getX() + 0.5),
                eye.getZ() - (event.getBlockPlaced().getZ() + 0.5));
        double vertical = eye.getY() - (event.getBlockPlaced().getY() + 0.5);
        double pitchLimit = plugin.getConfig().getDouble("checks.scaffold-signal.minimum-downward-pitch", 65.0);
        double minHorizontal = plugin.getConfig().getDouble("checks.scaffold-signal.min-horizontal-distance", 0.8);
        if (player.getLocation().getPitch() >= pitchLimit && horizontal >= minHorizontal && vertical > 0) {
            violations.add(player, "scaffold-signal",
                    plugin.getConfig().getDouble("checks.scaffold-signal.violation-add", 0.25),
                    String.format(java.util.Locale.ROOT, "downward-pitch=%.1f horizontal=%.2f",
                            player.getLocation().getPitch(), horizontal));
        }
    }
}

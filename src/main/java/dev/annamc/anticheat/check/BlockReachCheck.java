package dev.annamc.anticheat.check;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Conservative block interaction/break reach signal. It measures eye-to-nearest
 * point on the block collision box for server-observed actions. It is not packet-level
 * replay and cannot prove that all invalid client requests were observed by Bukkit.
 * Keep disabled until the configured limits pass staging tests on the target server.
 */
public final class BlockReachCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;

    public BlockReachCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (!plugin.getConfig().getBoolean("checks.block-reach.check-breaks", true)) return;
        evaluate(event.getPlayer(), event.getBlock(), "break");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !plugin.getConfig().getBoolean("checks.block-reach.check-interactions", true)) return;
        Block clicked = event.getClickedBlock();
        if (clicked != null) evaluate(event.getPlayer(), clicked, "interact");
    }

    private void evaluate(Player player, Block block, String action) {
        if (block == null || block.getType().isAir()
                || CheckUtil.shouldSkip(plugin, player, "block-reach")
                || isIgnoredMaterial(block.getType().name())) return;

        BoundingBox box = block.getBoundingBox();
        Location eye = player.getEyeLocation();
        double distance = BlockReachMath.distanceToBox(
                eye.getX(), eye.getY(), eye.getZ(),
                box.getMinX(), box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ());
        String thresholdKey = "interact".equals(action) ? "max-interaction-distance" : "max-break-distance";
        double maximum = CheckUtil.getVersionedDouble(plugin, player, "block-reach", thresholdKey, 5.0);
        double tolerance = Math.max(0.0,
                CheckUtil.getVersionedDouble(plugin, player, "block-reach", "distance-tolerance", 0.15));
        if (!BlockReachMath.exceeds(distance, maximum, tolerance)) return;

        violations.add(player, "block-reach",
                CheckUtil.getVersionedDouble(plugin, player, "block-reach", "violation-add", 1.0),
                String.format(java.util.Locale.ROOT,
                        "block-%s reach=%.3f max=%.3f tolerance=%.3f block=%s world=%s ping=%dms; verify game mode, latency and custom interaction plugins",
                        action, distance, maximum, tolerance, block.getType().name(),
                        block.getWorld().getName(), player.getPing()));
    }

    private boolean isIgnoredMaterial(String material) {
        List<String> ignored = plugin.getConfig().getStringList("checks.block-reach.ignored-materials");
        return ignored.stream().filter(value -> value != null && !value.isBlank())
                .anyMatch(value -> value.equalsIgnoreCase(material));
    }
}

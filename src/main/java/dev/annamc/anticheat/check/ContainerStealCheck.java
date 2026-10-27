package dev.annamc.anticheat.check;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conservative event-rate signal for rapid shift-transfer looting from container inventories.
 * It does not claim to detect every ChestStealer or inventory automation implementation.
 * Keep disabled until the server's custom inventories have been tested.
 */
public final class ContainerStealCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    // Bukkit inventory events are processed on the primary server thread.
    private final Map<UUID, Deque<Long>> transferTimes = new HashMap<>();
    private final Map<UUID, Long> lastEvaluation = new HashMap<>();
    private final Map<UUID, Integer> overLimitWindows = new HashMap<>();
    private final Map<UUID, Long> lastFlag = new HashMap<>();

    public ContainerStealCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || CheckUtil.shouldSkip(plugin, player, "container-steal")
                || player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || event.getAction() != InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        // Only count shift-transfers originating inside the open container, not from the player's inventory into it.
        if (top == null || event.getClickedInventory() != top) return;
        String inventoryType = top.getType().name();
        List<String> ignoredTypes = plugin.getConfig().getStringList(
                "checks.container-steal.ignored-inventory-types");
        if (ignoredTypes.stream().anyMatch(type -> type.equalsIgnoreCase(inventoryType))) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long windowMs = boundedLong(CheckUtil.getVersionedDouble(
                plugin, player, "container-steal", "sample-window-ms", 1000.0), 250L, 10000L);
        Deque<Long> times = transferTimes.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        times.addLast(now);
        long cutoff = now - windowMs;
        while (!times.isEmpty() && times.peekFirst() < cutoff) times.removeFirst();

        int maxSamples = Math.max(64, Math.min(4096,
                plugin.getConfig().getInt("checks.container-steal.max-buffered-samples-per-player", 512)));
        while (times.size() > maxSamples) times.removeFirst();

        long interval = boundedLong(CheckUtil.getVersionedDouble(
                plugin, player, "container-steal", "evaluation-interval-ms", 250.0), 100L, 2000L);
        long previousEvaluation = lastEvaluation.getOrDefault(id, 0L);
        if (now - previousEvaluation < interval) return;
        lastEvaluation.put(id, now);

        int maximum = (int) boundedLong(CheckUtil.getVersionedDouble(
                plugin, player, "container-steal", "max-transfers-per-window", 28.0), 1L, 500L);
        int count = times.size();
        int streak = count >= maximum ? overLimitWindows.getOrDefault(id, 0) + 1 : 0;
        overLimitWindows.put(id, streak);

        int required = (int) boundedLong(CheckUtil.getVersionedDouble(
                plugin, player, "container-steal", "required-over-limit-windows", 3.0), 1L, 20L);
        long cooldown = boundedLong(CheckUtil.getVersionedDouble(
                plugin, player, "container-steal", "flag-cooldown-ms", 5000.0), 0L, 120000L);
        if (streak < required || now - lastFlag.getOrDefault(id, 0L) < cooldown) return;

        lastFlag.put(id, now);
        overLimitWindows.put(id, 0);
        violations.add(player, "container-steal",
                CheckUtil.getVersionedDouble(plugin, player, "container-steal", "violation-add", 1.0),
                String.format(java.util.Locale.ROOT,
                        "rapid container shift-transfers=%d in %dms (limit=%d, repeated-windows=%d, inventory=%s); review legitimate looting and custom inventory plugins",
                        count, windowMs, maximum, streak, inventoryType));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer().getUniqueId());
    }

    private void clear(UUID id) {
        transferTimes.remove(id);
        lastEvaluation.remove(id);
        overLimitWindows.remove(id);
        lastFlag.remove(id);
    }

    private static long boundedLong(double value, long minimum, long maximum) {
        if (!Double.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, Math.round(value)));
    }
}

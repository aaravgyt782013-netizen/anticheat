package dev.annamc.anticheat.check;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional signal for repeated movement while a server-side container inventory is open.
 * It cannot observe the client-only state of the player's own inventory screen, and custom
 * GUIs may intentionally allow movement. Keep disabled until validated on the target server.
 */
public final class ContainerMoveCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    // Bukkit inventory/movement events execute on the primary server thread.
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Long> recentVelocity = new HashMap<>();

    public ContainerMoveCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        UUID id = player.getUniqueId();
        if (CheckUtil.shouldSkip(plugin, player, "container-move")) {
            states.remove(id);
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                || player.isInsideVehicle() || player.isFlying() || player.isGliding()
                || player.isSwimming() || player.isInWater() || from.getWorld() != to.getWorld()) {
            states.remove(id);
            return;
        }

        InventoryView view = player.getOpenInventory();
        Inventory top = view == null ? null : view.getTopInventory();
        if (top == null) {
            states.remove(id);
            return;
        }

        String inventoryType = top.getType().name();
        String title = view.getTitle() == null ? "" : view.getTitle();
        if (isIgnoredInventory(inventoryType, title)) {
            states.remove(id);
            return;
        }

        long nowNanos = System.nanoTime();
        Long velocityAt = recentVelocity.get(id);
        if (velocityAt != null) {
            long graceMs = CheckUtil.getVersionedGraceMillis(plugin, player, "container-move", 300L);
            if (DetectionMath.isWithinGracePeriod(nowNanos, velocityAt, graceMs)) {
                State state = stateFor(id, inventoryType, title);
                state.clearSamples();
                return;
            }
            recentVelocity.remove(id, velocityAt);
        }

        State state = stateFor(id, inventoryType, title);
        long now = System.currentTimeMillis();
        long windowMs = boundedLong(CheckUtil.getVersionedDouble(
                plugin, player, "container-move", "sample-window-ms", 1000.0), 250L, 10000L);
        while (!state.samples.isEmpty() && now - state.samples.peekFirst().time > windowMs) {
            state.pathDistance -= state.samples.removeFirst().distance;
        }
        if (state.pathDistance < 0.0) state.pathDistance = 0.0;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.hypot(dx, dz);
        double vertical = Math.abs(to.getY() - from.getY());
        double minimumStep = boundedDouble(CheckUtil.getVersionedDouble(
                plugin, player, "container-move", "minimum-sample-distance", 0.03), 0.0, 1.0);
        double maximumStep = boundedDouble(CheckUtil.getVersionedDouble(
                plugin, player, "container-move", "maximum-horizontal-step", 1.5), 0.1, 8.0);
        double maximumVerticalStep = boundedDouble(CheckUtil.getVersionedDouble(
                plugin, player, "container-move", "maximum-vertical-step", 1.25), 0.0, 8.0);

        if (horizontal > maximumStep || vertical > maximumVerticalStep) {
            state.clearSamples();
            return;
        }
        if (!ContainerMoveMath.isCandidateStep(horizontal, vertical, minimumStep,
                maximumStep, maximumVerticalStep)) return;

        state.samples.addLast(new MoveSample(now, horizontal));
        state.pathDistance += horizontal;
        int maxSamples = (int) boundedLong(CheckUtil.getVersionedDouble(
                plugin, player, "container-move", "max-buffered-samples-per-player", 512.0), 64L, 4096L);
        while (state.samples.size() > maxSamples) {
            state.pathDistance -= state.samples.removeFirst().distance;
        }
        if (state.pathDistance < 0.0) state.pathDistance = 0.0;

        int requiredSamples = (int) boundedLong(CheckUtil.getVersionedDouble(
                plugin, player, "container-move", "minimum-movement-samples", 4.0), 2L, 100L);
        double minimumPath = boundedDouble(CheckUtil.getVersionedDouble(
                plugin, player, "container-move", "minimum-path-distance", 0.75), 0.0, 20.0);
        long cooldown = boundedLong(CheckUtil.getVersionedDouble(
                plugin, player, "container-move", "flag-cooldown-ms", 5000.0), 0L, 120000L);
        if (ContainerMoveMath.hasEnoughEvidence(state.samples.size(), state.pathDistance,
                requiredSamples, minimumPath) && now - state.lastFlag >= cooldown) {
            state.lastFlag = now;
            int samples = state.samples.size();
            double path = state.pathDistance;
            state.clearSamples();
            violations.add(player, "container-move",
                    CheckUtil.getVersionedDouble(plugin, player, "container-move", "violation-add", 1.0),
                    String.format(java.util.Locale.ROOT,
                            "movement while container GUI open: inventory=%s samples=%d path=%.3f blocks in %dms; review custom GUI movement, server velocity and client behavior",
                            inventoryType, samples, path, windowMs));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) states.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) states.remove(player.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        if (!plugin.getConfig().getBoolean("checks.container-move.enabled", false)) return;
        UUID id = event.getPlayer().getUniqueId();
        recentVelocity.put(id, System.nanoTime());
        State state = states.get(id);
        if (state != null) state.clearSamples();
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        states.remove(id);
        recentVelocity.remove(id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        states.remove(id);
        recentVelocity.remove(id);
    }

    private State stateFor(UUID id, String inventoryType, String title) {
        State state = states.get(id);
        if (state == null || !state.inventoryType.equals(inventoryType) || !state.title.equals(title)) {
            state = new State(inventoryType, title);
            states.put(id, state);
        }
        return state;
    }

    private boolean isIgnoredInventory(String type, String title) {
        List<String> ignoredTypes = plugin.getConfig().getStringList(
                "checks.container-move.ignored-inventory-types");
        if (ignoredTypes.stream().filter(v -> v != null)
                .anyMatch(v -> v.equalsIgnoreCase(type))) return true;
        List<String> ignoredTitles = plugin.getConfig().getStringList(
                "checks.container-move.ignored-title-contains");
        String normalizedTitle = title.toLowerCase(java.util.Locale.ROOT);
        return ignoredTitles.stream().filter(v -> v != null && !v.isBlank())
                .map(v -> v.toLowerCase(java.util.Locale.ROOT))
                .anyMatch(normalizedTitle::contains);
    }

    private static long boundedLong(double value, long min, long max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, Math.round(value)));
    }

    private static double boundedDouble(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static final class State {
        private final String inventoryType;
        private final String title;
        private final Deque<MoveSample> samples = new ArrayDeque<>();
        private double pathDistance;
        private long lastFlag;

        private State(String inventoryType, String title) {
            this.inventoryType = inventoryType;
            this.title = title;
        }

        private void clearSamples() {
            samples.clear();
            pathDistance = 0.0;
        }
    }

    private record MoveSample(long time, double distance) {}
}
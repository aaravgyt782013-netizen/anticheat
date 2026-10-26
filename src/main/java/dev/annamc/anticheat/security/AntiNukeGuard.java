package dev.annamc.anticheat.security;

import dev.annamc.anticheat.alert.AlertService;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Configurable anti-nuke/anti-grief guard. It does not run server commands or
 * alter files. Bulk command blocking is player-only; console commands remain
 * available but are outside the guard's enforcement boundary.
 */
public final class AntiNukeGuard implements Listener {
    private final JavaPlugin plugin;
    private final AlertService alerts;
    private final Map<UUID, Deque<Long>> breakEvents = new HashMap<>();
    private final Map<UUID, Long> lastAlert = new HashMap<>();
    private final Map<UUID, Long> blockedUntil = new HashMap<>();

    public AntiNukeGuard(JavaPlugin plugin, AlertService alerts) {
        this.plugin = plugin;
        this.alerts = alerts;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBulkCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled("block-bulk-commands")) return;
        Player player = event.getPlayer();
        String raw = event.getMessage();
        if (!isDestructiveBulkCommand(raw)) return;

        List<String> allowedPermissions = plugin.getConfig().getStringList(
                "security.antinuke.allow-bulk-command-permissions");
        boolean allowed = allowedPermissions.stream().filter(p -> p != null && !p.isBlank())
                .anyMatch(player::hasPermission);
        if (allowed) {
            report("anti-nuke-command-audit", player.getName(),
                    "trusted permission bypass used for bulk command: " + safeCommand(raw), player.getUniqueId());
            return;
        }

        event.setCancelled(true);
        report("anti-nuke-command-block", player.getName(),
                "blocked potentially destructive bulk command from player: " + safeCommand(raw), player.getUniqueId());
        if (plugin.getConfig().getBoolean("security.antinuke.kick-on-blocked-bulk-command", false)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) player.kickPlayer(AlertService.color(
                        plugin.getConfig().getString("security.antinuke.blocked-command-kick-message",
                                "&cA destructive command was blocked by server security.")));
            });
        }
    }

    private boolean isDestructiveBulkCommand(String raw) {
        return BulkCommandMatcher.matches(raw,
                plugin.getConfig().getStringList("security.antinuke.protected-bulk-command-labels"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (!plugin.getConfig().getBoolean("security.antinuke.enabled", true)
                || !plugin.getConfig().getBoolean("security.antinuke.audit-console-bulk-commands", true)) return;
        String command = event.getCommand();
        if (isDestructiveBulkCommand(command)) {
            report("anti-nuke-console-audit", "CONSOLE",
                    "console issued a bulk/destructive command (audited, not blocked): " + safeCommand(command),
                    new UUID(0L, 0L));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!enabled("block-break-rate-limit")) return;
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (p.hasPermission("annaac.security.bypass")) return;
        String world = p.getWorld().getName();
        if (contains(plugin.getConfig().getStringList("security.antinuke.ignored-worlds"), world)) return;

        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        long penaltyUntil = blockedUntil.getOrDefault(id, 0L);
        if (now < penaltyUntil) {
            event.setCancelled(true);
            return;
        }

        long windowMs = clamp(plugin.getConfig().getLong("security.antinuke.block-break-window-ms", 2000L), 250L, 30000L);
        Deque<Long> times = breakEvents.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > windowMs) times.removeFirst();

        int maximum = (int) clamp(plugin.getConfig().getLong("security.antinuke.max-block-breaks-per-window", 120L), 10L, 10000L);
        if (times.size() > maximum) {
            event.setCancelled(true);
            long cooldown = clamp(plugin.getConfig().getLong("security.antinuke.block-break-block-ms", 3000L), 250L, 60000L);
            blockedUntil.put(id, now + cooldown);
            times.clear();
            report("anti-nuke-block-break-rate", p.getName(),
                    "blocked an excessive completed-block-break burst; configured limit=" + maximum
                            + " window-ms=" + windowMs + " enforcement-ms=" + cooldown, id);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        checkExplosion(event.blockList().size(), event.getLocation().getWorld(), "entity explosion", event::setCancelled);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        checkExplosion(event.blockList().size(), event.getBlock().getWorld(), "block explosion", event::setCancelled);
    }

    private void checkExplosion(int affectedBlocks, World world, String type, java.util.function.Consumer<Boolean> cancel) {
        if (!enabled("explosion-block-limit") || world == null) return;
        if (contains(plugin.getConfig().getStringList("security.antinuke.ignored-worlds"), world.getName())) return;
        int maximum = (int) clamp(plugin.getConfig().getLong(
                "security.antinuke.max-explosion-affected-blocks", 120L), 0L, 100000L);
        if (affectedBlocks > maximum) {
            cancel.accept(true);
            report("anti-nuke-explosion-blocked", "SERVER",
                    "cancelled " + type + " affecting " + affectedBlocks + " blocks; configured maximum=" + maximum
                            + " world=" + world.getName(), new UUID(0L, 0L));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        breakEvents.remove(id);
        lastAlert.remove(id);
        blockedUntil.remove(id);
    }

    private boolean enabled(String option) {
        return plugin.getConfig().getBoolean("security.antinuke.enabled", true)
                && plugin.getConfig().getBoolean("security.antinuke." + option, false);
    }

    private void report(String check, String player, String detail, UUID id) {
        long cooldown = clamp(plugin.getConfig().getLong("security.antinuke.alert-cooldown-ms", 5000L), 0L, 300000L);
        long now = System.currentTimeMillis();
        if (id != null && id.getMostSignificantBits() != 0L && id.getLeastSignificantBits() != 0L) {
            Long previous = lastAlert.get(id);
            if (previous != null && now - previous < cooldown) return;
            lastAlert.put(id, now);
        }
        alerts.sendSecurityAlert(player, id == null ? new UUID(0L, 0L) : id, check, detail);
    }

    private static boolean contains(List<String> values, String value) {
        return values.stream().anyMatch(v -> v != null && v.equalsIgnoreCase(value));
    }

    private static long clamp(long v, long min, long max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String safeCommand(String raw) {
        String s = raw == null ? "" : raw.replace('\n', ' ').replace('\r', ' ');
        return s.length() <= 180 ? s : s.substring(0, 180);
    }
}

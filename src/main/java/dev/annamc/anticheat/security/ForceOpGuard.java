package dev.annamc.anticheat.security;

import dev.annamc.anticheat.evidence.EvidenceStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ForceOpGuard implements Listener {
    private final JavaPlugin plugin;
    private final EvidenceStore evidence;
    private final Set<UUID> knownOperators = ConcurrentHashMap.newKeySet();

    public ForceOpGuard(JavaPlugin plugin, EvidenceStore evidence) {
        this.plugin = plugin;
        this.evidence = evidence;
        for (OfflinePlayer operator : Bukkit.getOperators()) {
            if (operator.getUniqueId() != null) knownOperators.add(operator.getUniqueId());
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::auditOperatorList, 20L, 20L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("checks.force-op.enabled", true)) return;
        String command = event.getMessage().trim().toLowerCase(java.util.Locale.ROOT);
        if (isOpGrantCommand(command) && !event.getPlayer().hasPermission("annaac.admin")) {
            alert(event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                    "unauthorized player attempted an OP-related command", event.getPlayer());
            if (plugin.getConfig().getBoolean("checks.force-op.kick-player-on-attempt", false)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (event.getPlayer().isOnline())
                        event.getPlayer().kickPlayer("Unauthorized operator command attempt.");
                });
            }
        }
    }

    @EventHandler
    public void onConsoleCommand(ServerCommandEvent event) {
        if (!plugin.getConfig().getBoolean("checks.force-op.enabled", true)) return;
        String command = event.getCommand().trim().toLowerCase(java.util.Locale.ROOT);
        if (isOpGrantCommand(command)) {
            plugin.getLogger().warning("[ANNA AC SECURITY] Console executed an OP-related command. Verify this was authorized.");
        }
    }

    private boolean isOpGrantCommand(String command) {
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        return normalized.equals("op") || normalized.startsWith("op ")
                || normalized.equals("minecraft:op") || normalized.startsWith("minecraft:op ")
                || normalized.equals("deop") || normalized.startsWith("deop ")
                || normalized.equals("minecraft:deop") || normalized.startsWith("minecraft:deop ");
    }

    private void auditOperatorList() {
        if (!plugin.getConfig().getBoolean("checks.force-op.enabled", true)
                || !plugin.getConfig().getBoolean("checks.force-op.detect-op-grants", true)) return;
        for (OfflinePlayer operator : Bukkit.getOperators()) {
            UUID id = operator.getUniqueId();
            if (id == null || knownOperators.contains(id)) continue;
            knownOperators.add(id);
            Player online = Bukkit.getPlayer(id);
            String name = operator.getName() == null ? id.toString() : operator.getName();
            alert(id, name, "operator status was newly observed; actor is unknown", online);
        }
        knownOperators.removeIf(id -> !Bukkit.getOfflinePlayer(id).isOp());
    }

    private void alert(UUID id, String name, String detail, Player player) {
        String message = "§cANNA AC SECURITY §8» §f" + name + " §7" + detail;
        plugin.getLogger().warning(message.replaceAll("§[0-9A-FK-ORa-fk-or]", ""));
        if (plugin.getConfig().getBoolean("checks.force-op.alert", true)) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("annaac.alerts")) staff.sendMessage(message);
            }
        }
        evidence.record(id, name, "force-op-audit", 0.0, detail);
    }
}

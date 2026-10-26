package dev.annamc.anticheat.security;

import dev.annamc.anticheat.evidence.EvidenceStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Defense-in-depth audit for OP and common permission-management command paths.
 * It cannot protect against malicious plugins, direct file edits, console abuse,
 * or arbitrary code execution; those require host access control and plugin review.
 */
public final class ForceOpGuard implements Listener {
    private final JavaPlugin plugin;
    private final EvidenceStore evidence;
    private final Set<UUID> knownOperators = ConcurrentHashMap.newKeySet();
    private final Path baselinePath;

    public ForceOpGuard(JavaPlugin plugin, EvidenceStore evidence) {
        this.plugin = plugin;
        this.evidence = evidence;
        this.baselinePath = plugin.getDataFolder().toPath().resolve("operator-baseline.txt");
        Set<UUID> current = readCurrentOperators();
        Set<UUID> persisted = loadBaseline();
        if (persisted != null && plugin.getConfig().getBoolean("checks.force-op.detect-op-grants", true)) {
            for (UUID id : current) {
                if (!persisted.contains(id)) {
                    OfflinePlayer operator = Bukkit.getOfflinePlayer(id);
                    String name = operator.getName() == null ? id.toString() : operator.getName();
                    alert(id, name, "operator status appeared since the previous saved baseline; actor is unknown", Bukkit.getPlayer(id));
                }
            }
        }
        knownOperators.addAll(current);
        saveBaseline(current);
        Bukkit.getScheduler().runTaskTimer(plugin, this::auditOperatorList, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("checks.force-op.enabled", true)) return;
        String command = normalize(event.getMessage());
        if (!isProtectedCommand(command)) return;

        Player actor = event.getPlayer();
        boolean trusted = actor.hasPermission("annaac.admin");
        alert(actor.getUniqueId(), actor.getName(),
                (trusted ? "authorized actor issued a protected privilege command: " : "blocked protected privilege command attempt: ")
                        + command, actor);

        if (!trusted && plugin.getConfig().getBoolean("checks.force-op.cancel-player-op-commands", true)) {
            event.setCancelled(true);
            actor.sendMessage("§cANNA AC §7» This privileged command is blocked by server security.");
            if (plugin.getConfig().getBoolean("checks.force-op.kick-player-on-attempt", false)) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (actor.isOnline()) actor.kickPlayer("Unauthorized privileged command attempt.");
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (!plugin.getConfig().getBoolean("checks.force-op.enabled", true)
                || !plugin.getConfig().getBoolean("checks.force-op.audit-console-op-commands", true)) return;
        String command = normalize(event.getCommand());
        if (isProtectedCommand(command)) {
            plugin.getLogger().warning("[ANNA AC SECURITY] Console issued a privileged command. Confirm this action was authorized: " + command);
            evidence.record(new UUID(0L, 0L), "CONSOLE", "force-op-console-audit", 0.0, command);
        }
    }

    private String normalize(String raw) {
        String command = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (command.startsWith("/")) command = command.substring(1);
        return command.replaceAll("\\s+", " ");
    }

    private boolean isProtectedCommand(String command) {
        String label = command.contains(" ") ? command.substring(0, command.indexOf(' ')) : command;
        List<String> configured = plugin.getConfig().getStringList("checks.force-op.protected-command-prefixes");
        if (configured.isEmpty()) {
            configured = List.of("op", "deop", "minecraft:op", "minecraft:deop", "bukkit:op", "bukkit:deop",
                    "lp", "luckperms", "lpv", "pex", "permissions", "manuadd");
        }
        for (String candidate : configured) {
            String prefix = candidate.toLowerCase(java.util.Locale.ROOT).trim();
            if (!prefix.isEmpty() && (label.equals(prefix) || label.startsWith(prefix + ":"))) return true;
        }
        return false;
    }

    private void auditOperatorList() {
        if (!plugin.getConfig().getBoolean("checks.force-op.enabled", true)
                || !plugin.getConfig().getBoolean("checks.force-op.detect-op-grants", true)) return;
        Set<UUID> current = readCurrentOperators();
        boolean changed = !current.equals(knownOperators);
        for (UUID id : current) {
            if (knownOperators.add(id)) {
                OfflinePlayer operator = Bukkit.getOfflinePlayer(id);
                String name = operator.getName() == null ? id.toString() : operator.getName();
                alert(id, name, "operator status changed to enabled; granting actor is unknown", Bukkit.getPlayer(id));
            }
        }
        if (changed) {
            knownOperators.clear();
            knownOperators.addAll(current);
            saveBaseline(current);
        }
    }

    private Set<UUID> readCurrentOperators() {
        Set<UUID> current = ConcurrentHashMap.newKeySet();
        for (OfflinePlayer operator : Bukkit.getOperators()) {
            UUID id = operator.getUniqueId();
            if (id != null) current.add(id);
        }
        return current;
    }

    private Set<UUID> loadBaseline() {
        if (!plugin.getConfig().getBoolean("checks.force-op.persist-operator-baseline", true)
                || !Files.isRegularFile(baselinePath)) return null;
        Set<UUID> result = ConcurrentHashMap.newKeySet();
        try {
            for (String line : Files.readAllLines(baselinePath, StandardCharsets.UTF_8)) {
                try { result.add(UUID.fromString(line.trim())); }
                catch (IllegalArgumentException ignored) { /* Ignore malformed baseline lines. */ }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not read saved operator baseline", e);
        }
        return result;
    }

    private void saveBaseline(Set<UUID> operators) {
        if (!plugin.getConfig().getBoolean("checks.force-op.persist-operator-baseline", true)) return;
        try {
            Files.createDirectories(baselinePath.getParent());
            List<String> lines = new ArrayList<>();
            for (UUID id : operators) lines.add(id.toString());
            lines.sort(String::compareTo);
            Files.write(baselinePath, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save operator baseline", e);
        }
    }

    private void alert(UUID id, String name, String detail, Player player) {
        String message = "§cANNA AC SECURITY §8» §f" + name + " §7" + detail;
        plugin.getLogger().warning(message.replaceAll("§[0-9A-FK-ORa-fk-or]", ""));
        if (plugin.getConfig().getBoolean("checks.force-op.alert", true)) {
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("annaac.alerts") && !staff.hasPermission("annaac.bypass"))
                    staff.sendMessage(message);
            }
        }
        evidence.record(id, name, "force-op-audit", 0.0, detail);
    }
}

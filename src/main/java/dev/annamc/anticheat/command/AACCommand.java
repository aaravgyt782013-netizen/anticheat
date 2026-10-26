package dev.annamc.anticheat.command;

import dev.annamc.anticheat.alert.AlertService;
import dev.annamc.anticheat.AnnaACPlugin;
import dev.annamc.anticheat.check.ViolationManager;
import dev.annamc.anticheat.config.ConfigurationDiagnostics;
import dev.annamc.anticheat.evidence.EvidenceStore;
import dev.annamc.anticheat.lang.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.util.List;

public final class AACCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final ViolationManager violations;
    private final EvidenceStore evidence;
    private final AlertService alerts;
    private final Messages messages;

    public AACCommand(JavaPlugin plugin, ViolationManager violations, EvidenceStore evidence, AlertService alerts, Messages messages) {
        this.plugin = plugin;
        this.violations = violations;
        this.evidence = evidence;
        this.alerts = alerts;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return usage(sender);
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "alerts" -> {
                if (!(sender instanceof Player player) || !sender.hasPermission("annaac.alerts")) return deny(sender);
                boolean enabled = alerts.toggleStaffAlerts(player);
                sender.sendMessage(messages.get(enabled ? "commands.alerts-enabled" : "commands.alerts-disabled"));
            }
            case "status" -> {
                if (!sender.hasPermission("annaac.admin")) return deny(sender);
                int enabledChecks = 0;
                for (String check : ConfigurationDiagnostics.DETECTORS) {
                    if (plugin.getConfig().getBoolean("checks." + check + ".enabled", false)) enabledChecks++;
                }
                int enabledActions = 0;
                for (String check : ConfigurationDiagnostics.DETECTORS) {
                    String actionType = plugin.getConfig().getString("punishments.actions." + check + ".type", "KICK");
                    if (actionType == null) actionType = "KICK";
                    actionType = actionType.trim().toUpperCase(java.util.Locale.ROOT);
                    if (plugin.getConfig().getBoolean("punishments.enabled", true)
                            && plugin.getConfig().getBoolean("punishments.actions." + check + ".enabled", false)
                            && plugin.getConfig().getBoolean("checks." + check + ".enabled", false)
                            && java.util.Set.of("KICK", "WARN", "COMMAND").contains(actionType)) {
                        enabledActions++;
                    }
                }
                boolean discordEnabled = plugin.getConfig().getBoolean("alerts.discord-enabled", false)
                        && plugin.getConfig().getString("alerts.discord-webhook", "") != null
                        && !plugin.getConfig().getString("alerts.discord-webhook", "").isBlank();
                boolean packetEvents = Bukkit.getPluginManager().getPlugin("packetevents") != null;
                sender.sendMessage(messages.get("commands.status-header"));
                sender.sendMessage(messages.get("commands.status-checks", java.util.Map.of(
                        "enabled", enabledChecks, "total", ConfigurationDiagnostics.DETECTORS.size())));
                sender.sendMessage(messages.get("commands.status-packet-events", java.util.Map.of(
                        "status", packetEvents ? "installed" : "missing")));
                sender.sendMessage(messages.get("commands.status-discord", java.util.Map.of(
                        "status", discordEnabled ? "enabled/configured" : "disabled/not configured")));
                boolean forceOpEnabled = plugin.getConfig().getBoolean("checks.force-op.enabled", true);
                sender.sendMessage(messages.get("commands.status-force-op", java.util.Map.of(
                        "status", forceOpEnabled ? "enabled" : "disabled")));
                boolean antiNukeEnabled = plugin.getConfig().getBoolean("security.antinuke.enabled", true);
                boolean pluginMonitorEnabled = plugin.getConfig().getBoolean("security.plugin-monitor.enabled", true);
                sender.sendMessage(messages.get("commands.status-antinuke", java.util.Map.of(
                        "status", antiNukeEnabled ? "enabled" : "disabled")));
                sender.sendMessage(messages.get("commands.status-plugin-monitor", java.util.Map.of(
                        "status", pluginMonitorEnabled ? "enabled" : "disabled")));
                sender.sendMessage(messages.get("commands.status-punishments", java.util.Map.of(
                        "status", enabledActions == 0 ? "disabled (safe mode)" : "enabled for " + enabledActions + " check(s)")));
                for (String note : ConfigurationDiagnostics.inspect(plugin)) {
                    sender.sendMessage(AlertService.color("&8» &e" + note));
                }
            }
            case "securityscan" -> {
                if (!sender.hasPermission("annaac.admin")) return deny(sender);
                if (!(plugin instanceof AnnaACPlugin annaAc)
                        || annaAc.getPluginIntegrityMonitor() == null) {
                    sender.sendMessage(AlertService.color("&cPlugin security monitor is unavailable."));
                    return true;
                }
                if (!plugin.getConfig().getBoolean("security.plugin-monitor.enabled", true)) {
                    sender.sendMessage(AlertService.color("&cPlugin integrity monitoring is disabled in security.yml."));
                    return true;
                }
                annaAc.getPluginIntegrityMonitor().scanNow("manual");
                sender.sendMessage(messages.get("commands.security-scan-started"));
            }
            case "check" -> {
                if (!sender.hasPermission("annaac.admin")) return deny(sender);
                if (args.length < 2) return usage(sender);
                Player player = Bukkit.getPlayerExact(args[1]);
                if (player == null) { sender.sendMessage(messages.get("commands.player-offline")); return true; }
                double vl = args.length >= 3 ? violations.get(player, args[2]) : violations.get(player);
                sender.sendMessage(messages.get("commands.check-result", java.util.Map.of("player", player.getName(), "check", args.length >= 3 ? args[2] : "total", "vl", String.format(java.util.Locale.ROOT, "%.2f", vl))));
            }
            case "history" -> {
                if (!sender.hasPermission("annaac.admin")) return deny(sender);
                if (args.length < 2) return usage(sender);
                Player player = Bukkit.getPlayerExact(args[1]);
                if (player == null) {
                    sender.sendMessage(messages.get("commands.player-offline"));
                    return true;
                }
                var path = evidence.file(player.getUniqueId());
                if (!Files.exists(path)) {
                    sender.sendMessage(messages.get("commands.no-evidence", java.util.Map.of("player", player.getName())));
                    return true;
                }
                sender.sendMessage(messages.get("commands.loading-history", java.util.Map.of("player", player.getName())));
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    List<String> lines;
                    try {
                        lines = Files.readAllLines(path);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Could not read evidence for " + player.getUniqueId() + ": " + ex.getMessage());
                        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                                messages.get("commands.evidence-read-error")));
                        return;
                    }
                    int start = Math.max(0, lines.size() - 5);
                    List<String> recent = lines.subList(start, lines.size());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(messages.get("commands.history-count", java.util.Map.of("count", recent.size())));
                        for (String line : recent) sender.sendMessage(AlertService.color("&8» &7" + line));
                    });
                });
            }
            case "testdiscord" -> {
                if (!sender.hasPermission("annaac.admin")) return deny(sender);
                sender.sendMessage(messages.get(alerts.testDiscord(sender.getName())
                        ? "commands.discord-test-sent" : "commands.discord-not-configured"));
            }
            case "reload" -> {
                if (!sender.hasPermission("annaac.admin")) return deny(sender);
                if (plugin instanceof AnnaACPlugin annaAc) {
                    annaAc.reloadAllConfiguration();
                } else {
                    plugin.reloadConfig();
                    messages.reload();
                }
                sender.sendMessage(messages.get("commands.reloaded"));
            }
            case "debug" -> {
                if (!sender.hasPermission("annaac.admin")) return deny(sender);
                if (args.length < 2) return usage(sender);
                Player player = Bukkit.getPlayerExact(args[1]);
                sender.sendMessage(player == null ? "Player is not online." :
                        messages.get("commands.debug-result", java.util.Map.of("player", player.getName(), "vl", String.format(java.util.Locale.ROOT, "%.2f", violations.get(player)), "world", player.getWorld().getName(), "ping", player.getPing())));
            }
            default -> { return usage(sender); }
        }
        return true;
    }

    private boolean deny(CommandSender sender) {
        sender.sendMessage(messages.get("commands.no-permission"));
        return true;
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage(messages.get("commands.usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("alerts", "status", "securityscan", "check", "history", "reload", "debug", "testdiscord").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        if (args.length == 2 && List.of("check", "history", "debug").contains(args[0].toLowerCase(java.util.Locale.ROOT)))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("check"))
            return ConfigurationDiagnostics.DETECTORS.stream().filter(s -> s.startsWith(args[2].toLowerCase(java.util.Locale.ROOT))).toList();
        return List.of();
    }
}

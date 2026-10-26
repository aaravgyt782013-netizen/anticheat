package dev.annamc.anticheat.command;

import dev.annamc.anticheat.alert.AlertService;
import dev.annamc.anticheat.check.ViolationManager;
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
                plugin.reloadConfig();
                messages.reload();
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
        if (args.length == 1) return List.of("alerts", "check", "history", "reload", "debug", "testdiscord").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        if (args.length == 2 && List.of("check", "history", "debug").contains(args[0].toLowerCase(java.util.Locale.ROOT)))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(java.util.Locale.ROOT).startsWith(args[1].toLowerCase(java.util.Locale.ROOT))).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("check"))
            return List.of("movement", "reach", "click-rate", "scaffold-signal", "packet-rate")
                    .stream().filter(s -> s.startsWith(args[2].toLowerCase(java.util.Locale.ROOT))).toList();
        return List.of();
    }
}

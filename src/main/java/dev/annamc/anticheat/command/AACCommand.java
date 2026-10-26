package dev.annamc.anticheat.command;

import dev.annamc.anticheat.alert.AlertService;
import dev.annamc.anticheat.check.ViolationManager;
import dev.annamc.anticheat.evidence.EvidenceStore;
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

    public AACCommand(JavaPlugin plugin, ViolationManager violations, EvidenceStore evidence, AlertService alerts) {
        this.plugin = plugin;
        this.violations = violations;
        this.evidence = evidence;
        this.alerts = alerts;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return usage(sender);
        switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "alerts" -> {
                if (!(sender instanceof Player player) || !sender.hasPermission("annaac.alerts")) return deny(sender);
                boolean enabled = alerts.toggleStaffAlerts(player);
                sender.sendMessage(AlertService.color("&bANNA AC &7staff alerts are now "
                        + (enabled ? "&aenabled" : "&cdisabled") + "&7 for you."));
            }
            case "check" -> {
                if (!sender.hasPermission("annaac.admin")) return deny(sender);
                if (args.length < 2) return usage(sender);
                Player player = Bukkit.getPlayerExact(args[1]);
                if (player == null) { sender.sendMessage("Player must be online."); return true; }
                double vl = args.length >= 3 ? violations.get(player, args[2]) : violations.get(player);
                sender.sendMessage(AlertService.color("&bANNA AC &7" + player.getName()
                        + (args.length >= 3 ? " &8[" + args[2] + "]" : " total")
                        + " VL: &f" + String.format(java.util.Locale.ROOT, "%.2f", vl)));
            }
            case "history" -> {
                if (!sender.hasPermission("annaac.admin")) return deny(sender);
                if (args.length < 2) return usage(sender);
                Player player = Bukkit.getPlayerExact(args[1]);
                if (player == null) {
                    sender.sendMessage("Player must be online to resolve their UUID in this version.");
                    return true;
                }
                var path = evidence.file(player.getUniqueId());
                if (!Files.exists(path)) {
                    sender.sendMessage(AlertService.color("&7No evidence recorded for &f" + player.getName() + "&7."));
                    return true;
                }
                sender.sendMessage(AlertService.color("&bANNA AC &7Loading recent evidence for &f" + player.getName() + "&7..."));
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    List<String> lines;
                    try {
                        lines = Files.readAllLines(path);
                    } catch (Exception ex) {
                        plugin.getLogger().warning("Could not read evidence for " + player.getUniqueId() + ": " + ex.getMessage());
                        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(
                                AlertService.color("&cCould not read this evidence file.")));
                        return;
                    }
                    int start = Math.max(0, lines.size() - 5);
                    List<String> recent = lines.subList(start, lines.size());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(AlertService.color("&bANNA AC &7Recent evidence lines: &f" + recent.size()));
                        for (String line : recent) sender.sendMessage(AlertService.color("&8» &7" + line));
                    });
                });
            }
            case "reload" -> {
                if (!sender.hasPermission("annaac.admin")) return deny(sender);
                plugin.reloadConfig();
                sender.sendMessage(AlertService.color("&aANNA AC configuration reloaded."));
            }
            case "debug" -> {
                if (!sender.hasPermission("annaac.admin")) return deny(sender);
                if (args.length < 2) return usage(sender);
                Player player = Bukkit.getPlayerExact(args[1]);
                sender.sendMessage(player == null ? "Player is not online." :
                        AlertService.color("&7Debug &f" + player.getName() + " &7VL=&f"
                                + violations.get(player) + " &7world=&f" + player.getWorld().getName()
                                + " &7ping=&f" + player.getPing()));
            }
            default -> { return usage(sender); }
        }
        return true;
    }

    private boolean deny(CommandSender sender) {
        sender.sendMessage(AlertService.color("&cYou do not have permission."));
        return true;
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage(AlertService.color("&bANNA AC &7/annaac <alerts|check <player> [check]|history <player>|reload|debug <player>>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("alerts", "check", "history", "reload", "debug").stream()
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

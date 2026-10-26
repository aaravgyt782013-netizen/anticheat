package dev.annamc.anticheat.command;

import dev.annamc.anticheat.alert.AlertService;
import dev.annamc.anticheat.check.ViolationManager;
import dev.annamc.anticheat.evidence.EvidenceStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

    public AACCommand(JavaPlugin plugin, ViolationManager violations, EvidenceStore evidence) {
        this.plugin = plugin;
        this.violations = violations;
        this.evidence = evidence;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return usage(sender);
        switch (args[0].toLowerCase()) {
            case "alerts" -> {
                if (!sender.hasPermission("annaac.alerts")) return deny(sender);
                sender.sendMessage(AlertService.color("&7Staff alerts are currently controlled by annaac.alerts permission."));
            }
            case "check" -> {
                if (!sender.hasPermission("annaac.admin")) return deny(sender);
                if (args.length < 2) return usage(sender);
                Player player = Bukkit.getPlayerExact(args[1]);
                if (player == null) { sender.sendMessage("Player must be online."); return true; }
                sender.sendMessage(AlertService.color("&bANNA AC &7" + player.getName() + " current VL: &f" + violations.get(player)));
            }
            case "history" -> {
                if (!sender.hasPermission("annaac.admin")) return deny(sender);
                if (args.length < 2) return usage(sender);
                Player player = Bukkit.getPlayerExact(args[1]);
                if (player == null) { sender.sendMessage("Player must be online to resolve their UUID in this initial version."); return true; }
                var path = evidence.file(player.getUniqueId());
                sender.sendMessage(Files.exists(path) ? "Evidence file: plugins/ANNA-AC/evidence/" + player.getUniqueId() + ".jsonl" : "No evidence recorded yet.");
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
                        AlertService.color("&7Debug &f" + player.getName() + " &7VL=&f" + violations.get(player)
                                + " &7world=&f" + player.getWorld().getName() + " &7ping=&f" + player.getPing()));
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
        sender.sendMessage(AlertService.color("&bANNA AC &7/annaac <alerts|check <player>|history <player>|reload|debug <player>>"));
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("alerts", "check", "history", "reload", "debug").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        if (args.length == 2 && List.of("check", "history", "debug").contains(args[0].toLowerCase()))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        return List.of();
    }
}

package dev.annamc.anticheat.check;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class CheckUtil {
    private CheckUtil() {}

    public static boolean shouldSkip(JavaPlugin plugin, Player player, String check) {
        if (!plugin.getConfig().getBoolean("checks." + check + ".enabled", false)) return true;
        if (player.hasPermission("annaac.bypass")
                || player.hasPermission("annaac.bypass." + check)) return true;
        if (contains(plugin.getConfig().getStringList("settings.ignored-worlds"), player.getWorld().getName())
                || contains(plugin.getConfig().getStringList("checks." + check + ".ignored-worlds"),
                        player.getWorld().getName())) return true;
        for (String permission : plugin.getConfig().getStringList("checks." + check + ".ignored-permissions")) {
            if (!permission.isBlank() && player.hasPermission(permission)) return true;
        }
        List<String> modes = plugin.getConfig().getStringList("checks." + check + ".exempt-game-modes");
        if (modes.contains(player.getGameMode().name())) return true;
        return false;
    }

    private static boolean contains(List<String> values, String value) {
        return values.stream().anyMatch(v -> v.equalsIgnoreCase(value));
    }
}

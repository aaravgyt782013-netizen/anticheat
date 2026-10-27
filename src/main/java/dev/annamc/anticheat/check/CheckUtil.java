package dev.annamc.anticheat.check;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class CheckUtil {
    private CheckUtil() {}

    public static boolean shouldSkip(JavaPlugin plugin, Player player, String check) {
        if (!plugin.getConfig().getBoolean("checks." + check + ".enabled", false)) return true;
        if (plugin.getConfig().getBoolean("settings.performance.pause-detection-under-tps-enabled", true)) {
            double threshold = plugin.getConfig().getDouble("settings.performance.minimum-tps", 17.0);
            if (ServerPerformanceGuard.isLagging(org.bukkit.Bukkit.getTPS(), threshold)) return true;
        }
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

    /**
     * Reads a numeric check setting with optional exact Minecraft-version overrides.
     * Configuration path: checks.<check>.version-overrides.<version>.<key>.
     * Exact server version wins; then major.minor; then the normal setting.
     * This keeps tuning explicit instead of silently applying guessed physics constants.
     */
    public static double getVersionedDouble(JavaPlugin plugin, String check, String key, double fallback) {
        return VersionedThresholds.resolve(plugin.getConfig(), org.bukkit.Bukkit.getMinecraftVersion(),
                check, key, fallback);
    }

    /**
     * Resolves a threshold using the connected player's protocol version when PacketEvents
     * has observed it. Client-specific overrides take priority; server-specific overrides
     * remain the fallback so the detector still works when PacketEvents is absent.
     */
    public static double getVersionedDouble(JavaPlugin plugin, Player player, String check,
                                            String key, double fallback) {
        String clientVersion = player == null ? null
                : ClientVersionRegistry.get(player.getUniqueId());
        return VersionedThresholds.resolveForClient(plugin.getConfig(), clientVersion,
                org.bukkit.Bukkit.getMinecraftVersion(), check, key, fallback);
    }

    /**
     * Resolves thresholds for a player profile by UUID. This is used by violation
     * decay/cleanup paths that may run after Bukkit's Player object is unavailable.
     * If PacketEvents has no recorded client release for this UUID, server overrides apply.
     */
    public static double getVersionedDouble(JavaPlugin plugin, java.util.UUID playerId,
                                            String check, String key, double fallback) {
        String clientVersion = playerId == null ? null : ClientVersionRegistry.get(playerId);
        return VersionedThresholds.resolveForClient(plugin.getConfig(), clientVersion,
                org.bukkit.Bukkit.getMinecraftVersion(), check, key, fallback);
    }

    private static boolean contains(List<String> values, String value) {
        return values.stream().anyMatch(v -> v.equalsIgnoreCase(value));
    }
}

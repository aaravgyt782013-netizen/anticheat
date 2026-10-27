package dev.annamc.anticheat.check;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * Small in-memory cache of protocol release names observed through PacketEvents.
 * This is protocol metadata, not proof of a client's brand or installed cheats.
 */
public final class ClientVersionRegistry {
    private static final Pattern RELEASE = Pattern.compile("^\\d+\\.\\d+(?:\\.\\d+)?$");
    private static final ConcurrentMap<UUID, String> CLIENT_VERSIONS = new ConcurrentHashMap<>();

    private ClientVersionRegistry() {}

    public static void remember(UUID playerId, String releaseName) {
        if (playerId == null || releaseName == null) return;
        String version = releaseName.trim();
        if (!RELEASE.matcher(version).matches()) return;
        CLIENT_VERSIONS.put(playerId, version);
    }

    public static String get(UUID playerId) {
        return playerId == null ? null : CLIENT_VERSIONS.get(playerId);
    }

    public static void forget(UUID playerId) {
        if (playerId != null) CLIENT_VERSIONS.remove(playerId);
    }

    public static void clear() {
        CLIENT_VERSIONS.clear();
    }
}

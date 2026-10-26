package dev.annamc.anticheat.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Maps filenames to path-safe YAML keys without treating dots as separators. */
public final class PluginHashKey {
    private PluginHashKey() {}

    public static String forFileName(String filename) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(filename.getBytes(StandardCharsets.UTF_8));
    }
}

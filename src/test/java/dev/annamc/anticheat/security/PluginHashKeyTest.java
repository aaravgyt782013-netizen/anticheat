package dev.annamc.anticheat.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PluginHashKeyTest {
    @Test
    void dotSeparatedJarNamesBecomeSafeYamlPathSegments() {
        String key = PluginHashKey.forFileName("LuckPerms-Bukkit-5.4.145.jar");
        assertFalse(key.contains("."));
        assertFalse(key.contains("/"));
        assertEquals(key, PluginHashKey.forFileName("LuckPerms-Bukkit-5.4.145.jar"));
    }

    @Test
    void distinctFileNamesHaveDistinctEncodedKeys() {
        assertNotEquals(PluginHashKey.forFileName("Example.jar"),
                PluginHashKey.forFileName("Example-plugin.jar"));
    }

    @Test
    void unicodeFileNamesAreSupported() {
        assertFalse(PluginHashKey.forFileName("插件-测试.jar").contains("."));
    }
}

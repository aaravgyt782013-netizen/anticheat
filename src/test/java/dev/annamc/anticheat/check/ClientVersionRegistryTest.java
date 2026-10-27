package dev.annamc.anticheat.check;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientVersionRegistryTest {
    @AfterEach
    void clearRegistry() {
        ClientVersionRegistry.clear();
    }

    @Test
    void remembersValidProtocolReleaseNames() {
        UUID player = UUID.randomUUID();
        ClientVersionRegistry.remember(player, "1.21.11");

        assertEquals("1.21.11", ClientVersionRegistry.get(player));
    }

    @Test
    void ignoresUnknownOrMalformedReleaseNames() {
        UUID player = UUID.randomUUID();
        ClientVersionRegistry.remember(player, "unknown");
        ClientVersionRegistry.remember(player, "snapshot");
        ClientVersionRegistry.remember(player, "1.21.11-SNAPSHOT");

        assertNull(ClientVersionRegistry.get(player));
    }

    @Test
    void forgettingPlayerRemovesCachedProtocolVersion() {
        UUID player = UUID.randomUUID();
        ClientVersionRegistry.remember(player, "1.8");
        ClientVersionRegistry.forget(player);

        assertNull(ClientVersionRegistry.get(player));
    }
}

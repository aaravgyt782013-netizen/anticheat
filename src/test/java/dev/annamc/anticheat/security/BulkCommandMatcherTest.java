package dev.annamc.anticheat.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BulkCommandMatcherTest {
    private static final List<String> DEFAULTS = List.of(
            "fill", "minecraft:fill", "setblock", "minecraft:setblock", "clone", "minecraft:clone",
            "//set", "//replace", "kill", "minecraft:kill", "summon", "plugman", "reload", "paper:reload");

    @Test
    void matchesBulkVanillaCommandsAndNamespacedForms() {
        assertTrue(BulkCommandMatcher.matches("/fill ~ ~ ~ ~10 ~10 ~10 air", DEFAULTS));
        assertTrue(BulkCommandMatcher.matches("/minecraft:fill ~ ~ ~ ~10 ~10 ~10 air", DEFAULTS));
        assertTrue(BulkCommandMatcher.matches("/clone 0 0 0 10 10 10 20 20 20", DEFAULTS));
    }

    @Test
    void matchesWorldEditDoubleSlashAndExecuteChains() {
        assertTrue(BulkCommandMatcher.matches("//set tnt", DEFAULTS));
        assertTrue(BulkCommandMatcher.matches("/execute as @e run minecraft:fill ~ ~ ~ ~10 ~10 ~10 air", DEFAULTS));
    }

    @Test
    void detectsPluginManagementCommands() {
        assertTrue(BulkCommandMatcher.matches("/plugman unload ExamplePlugin", DEFAULTS));
        assertTrue(BulkCommandMatcher.matches("/reload", DEFAULTS));
    }

    @Test
    void doesNotFlagNormalSelfKillButFlagsMassOrTargetedKill() {
        assertFalse(BulkCommandMatcher.matches("/kill @s", DEFAULTS));
        assertTrue(BulkCommandMatcher.matches("/kill @e", DEFAULTS));
        assertTrue(BulkCommandMatcher.matches("/minecraft:kill SomePlayer", DEFAULTS));
    }

    @Test
    void leavesOrdinaryCommandsAlone() {
        assertFalse(BulkCommandMatcher.matches("/spawn", DEFAULTS));
        assertFalse(BulkCommandMatcher.matches("/home", DEFAULTS));
        assertFalse(BulkCommandMatcher.matches(null, DEFAULTS));
        assertFalse(BulkCommandMatcher.matches("/", DEFAULTS));
    }
}

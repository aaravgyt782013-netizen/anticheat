package dev.annamc.anticheat.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrivilegeCommandMatcherTest {
    private static final List<String> PROTECTED = List.of(
            "op", "deop", "minecraft:op", "minecraft:deop",
            "lp", "luckperms", "pex", "permissions"
    );

    @Test void detectsDirectOperatorAndPermissionCommands() {
        assertTrue(PrivilegeCommandMatcher.isProtected("/op Player", PROTECTED));
        assertTrue(PrivilegeCommandMatcher.isProtected("minecraft:deop Player", PROTECTED));
        assertTrue(PrivilegeCommandMatcher.isProtected("lp user Player permission set * true", PROTECTED));
    }

    @Test void detectsNestedExecuteWrappedPrivilegeCommands() {
        assertTrue(PrivilegeCommandMatcher.isProtected(
                "/execute as @a run op Player", PROTECTED));
        assertTrue(PrivilegeCommandMatcher.isProtected(
                "minecraft:execute as @s run minecraft:execute run lp user Player permission set * true",
                PROTECTED));
    }

    @Test void leavesOrdinaryCommandsAlone() {
        assertFalse(PrivilegeCommandMatcher.isProtected("/say hello", PROTECTED));
        assertFalse(PrivilegeCommandMatcher.isProtected(
                "/execute as @a run say hello", PROTECTED));
        assertFalse(PrivilegeCommandMatcher.isProtected("/operationstatus", PROTECTED));
    }
}
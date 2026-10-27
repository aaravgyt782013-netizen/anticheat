package dev.annamc.anticheat.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrivilegeCommandMatcherTest {
    private static final List<String> ROOTS = List.of(
            "op", "deop", "minecraft:op", "minecraft:deop", "bukkit:op", "bukkit:deop", "manuadd");
    private static final List<String> PERMISSION_MUTATIONS = List.of(
            "lp user * permission set **",
            "lp user * permission unset **",
            "lp user * permission clear",
            "lp user * parent add **",
            "lp group * permission set **",
            "pex user * group add **",
            "permissions group * set **");

    @Test
    void matchesNamespacedAndPluginAliasOpCommands() {
        assertTrue(PrivilegeCommandMatcher.isProtected("/op Hacker", ROOTS));
        assertTrue(PrivilegeCommandMatcher.isProtected("minecraft:op Hacker", ROOTS));
        assertTrue(PrivilegeCommandMatcher.isProtected("/essentials:op Hacker", ROOTS));
        assertTrue(PrivilegeCommandMatcher.isProtected("bukkit:deop Hacker", ROOTS));
        assertFalse(PrivilegeCommandMatcher.isProtected("operationstatus hello", ROOTS));
    }

    @Test
    void findsPrivilegeCommandsWrappedInExecute() {
        assertTrue(PrivilegeCommandMatcher.isProtected(
                "/execute as @s run essentials:op Hacker", ROOTS));
        assertTrue(PrivilegeCommandMatcher.matchesAnyPattern(
                "/execute as @s run lp user Hacker permission set annaac.admin true", PERMISSION_MUTATIONS));
    }

    @Test
    void matchesPermissionMutationsWithoutBlockingReadOnlyCommands() {
        assertTrue(PrivilegeCommandMatcher.matchesAnyPattern(
                "/lp user Hacker permission set annaac.admin true", PERMISSION_MUTATIONS));
        assertTrue(PrivilegeCommandMatcher.matchesAnyPattern(
                "/luckperms:lp group staff permission set * true", PERMISSION_MUTATIONS));
        assertTrue(PrivilegeCommandMatcher.matchesAnyPattern(
                "/pex user Hacker group add admin", PERMISSION_MUTATIONS));
        assertFalse(PrivilegeCommandMatcher.matchesAnyPattern("/lp help", PERMISSION_MUTATIONS));
        assertFalse(PrivilegeCommandMatcher.matchesAnyPattern("/lp user Hacker info", PERMISSION_MUTATIONS));
        assertFalse(PrivilegeCommandMatcher.matchesAnyPattern(
                "/say lp user Hacker permission set annaac.admin true", PERMISSION_MUTATIONS));
    }

    @Test
    void wildcardsAreTokenBoundedAndSupportEmptyRemainder() {
        assertTrue(PrivilegeCommandMatcher.matchesAnyPattern(
                "lp user Hacker permission clear", PERMISSION_MUTATIONS));
        assertFalse(PrivilegeCommandMatcher.matchesAnyPattern(
                "lp user Hacker permission", PERMISSION_MUTATIONS));
        assertFalse(PrivilegeCommandMatcher.matchesAnyPattern(null, PERMISSION_MUTATIONS));
        assertFalse(PrivilegeCommandMatcher.matchesAnyPattern("lp user Hacker permission set true", null));
    }
}

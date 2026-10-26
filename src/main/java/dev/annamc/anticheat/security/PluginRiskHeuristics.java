package dev.annamc.anticheat.security;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Explainable, heuristic-only indicators found in Java class constant pools.
 * Presence of a capability is not malicious by itself; risk comes from combinations.
 */
public final class PluginRiskHeuristics {
    private PluginRiskHeuristics() {}

    public static void collectFromClassText(String classText, Set<String> found) {
        if (classText == null || found == null) return;
        String data = classText.toLowerCase(Locale.ROOT);
        if (data.contains("java/lang/processbuilder")
                || data.contains("java/lang/runtime") && data.contains("exec")) {
            found.add("process-execution-capability");
        }
        if (containsAny(data, "java/net/socket", "java/net/http/httpclient",
                "java/net/httpurlconnection", "java/net/url", "java/net/datagramsocket")) {
            found.add("network-capability");
        }
        if (data.contains("java/nio/file/files") && containsAny(data, "delete", "deleteifexists")
                || data.contains("java/io/file") && data.contains("delete")) {
            found.add("file-delete-capability");
        }
        if (data.contains("dispatchcommand") && containsAny(data, "org/bukkit/bukkit", "org/bukkit/server")) {
            found.add("server-command-dispatch-capability");
        }
        if (data.contains("org/bukkit/plugin/pluginmanager") && containsAny(data, "disableplugin", "enableplugin")) {
            found.add("plugin-management-capability");
        }
        if (containsAny(data, "setop", "minecraft:op", "bukkit:op", "setoperator")) {
            found.add("operator-management-strings");
        }
        if (containsAny(data, "worlds/", "level.dat", "region/r.")) {
            found.add("world-file-path-strings");
        }
    }

    public static Set<String> withCorrelations(Set<String> rawIndicators) {
        Set<String> found = new HashSet<>(rawIndicators);
        if (found.contains("process-execution-capability") && found.contains("network-capability"))
            found.add("network-plus-process-execution");
        if (found.contains("network-capability") && found.contains("server-command-dispatch-capability")
                && found.contains("file-delete-capability"))
            found.add("network-plus-command-plus-file-delete-capability");
        if (found.contains("plugin-management-capability") && found.contains("process-execution-capability"))
            found.add("plugin-management-plus-process-execution");
        if (found.contains("network-capability") && found.contains("server-command-dispatch-capability")
                && found.contains("operator-management-strings"))
            found.add("network-plus-command-dispatch-plus-op-strings");
        if (found.contains("file-delete-capability") && found.contains("world-file-path-strings")
                && found.contains("process-execution-capability"))
            found.add("world-file-delete-plus-process-execution");
        return Set.copyOf(found);
    }

    public static int score(Set<String> indicators) {
        int score = 0;
        for (String indicator : indicators) {
            score += switch (indicator) {
                case "network-plus-process-execution", "network-plus-command-plus-file-delete-capability",
                        "world-file-delete-plus-process-execution" -> 5;
                case "plugin-management-plus-process-execution", "network-plus-command-dispatch-plus-op-strings" -> 4;
                case "process-execution-capability", "plugin-management-capability" -> 2;
                case "network-capability", "file-delete-capability", "server-command-dispatch-capability",
                        "operator-management-strings", "world-file-path-strings" -> 1;
                default -> 0;
            };
        }
        return score;
    }

    private static boolean containsAny(String text, String... options) {
        for (String option : options) if (text.contains(option)) return true;
        return false;
    }
}

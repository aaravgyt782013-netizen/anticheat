package dev.annamc.anticheat.security;

import java.util.List;
import java.util.Locale;

/** Pure command matcher for OP/permission-management command auditing. */
public final class PrivilegeCommandMatcher {
    private PrivilegeCommandMatcher() {}

    public static boolean isProtected(String rawCommand, List<String> configuredPrefixes) {
        if (rawCommand == null || configuredPrefixes == null || configuredPrefixes.isEmpty()) return false;
        String command = rawCommand.trim().toLowerCase(Locale.ROOT);
        if (command.startsWith("/")) command = command.substring(1);
        return isProtectedNormalized(command.replaceAll("\\s+", " "), configuredPrefixes, 0);
    }

    private static boolean isProtectedNormalized(String command, List<String> prefixes, int depth) {
        if (command.isBlank() || depth > 4) return false;
        String[] tokens = command.split("\\s+");
        String label = tokens[0];
        for (String candidate : prefixes) {
            if (candidate == null) continue;
            String prefix = candidate.trim().toLowerCase(Locale.ROOT);
            if (!prefix.isEmpty() && (label.equals(prefix) || label.startsWith(prefix + ":"))) return true;
        }

        // A player can wrap a privilege command in vanilla execute ... run <command>.
        // Recursion is bounded to avoid pathological command strings.
        if (label.equals("execute") || label.equals("minecraft:execute")) {
            for (int i = 1; i + 1 < tokens.length; i++) {
                if (tokens[i].equals("run")) {
                    StringBuilder nested = new StringBuilder();
                    for (int j = i + 1; j < tokens.length; j++) {
                        if (nested.length() > 0) nested.append(' ');
                        nested.append(tokens[j]);
                    }
                    if (isProtectedNormalized(nested.toString(), prefixes, depth + 1)) return true;
                }
            }
        }
        return false;
    }
}
package dev.annamc.anticheat.security;

import java.util.List;
import java.util.Locale;

/** Pure command matcher for OP/permission-management command auditing. */
public final class PrivilegeCommandMatcher {
    private PrivilegeCommandMatcher() {}

    public static boolean isProtected(String rawCommand, List<String> configuredPrefixes) {
        if (rawCommand == null || configuredPrefixes == null || configuredPrefixes.isEmpty()) return false;
        return isProtectedNormalized(normalize(rawCommand), configuredPrefixes, 0);
    }

    /**
     * Matches configurable token patterns such as "lp user * permission set **".
     * A single '*' matches one argument token; '**' matches zero or more trailing tokens.
     * This allows permission-mutating subcommands to be protected without blocking harmless
     * read-only commands merely because they share a /lp or /pex root.
     */
    public static boolean matchesAnyPattern(String rawCommand, List<String> configuredPatterns) {
        if (rawCommand == null || configuredPatterns == null || configuredPatterns.isEmpty()) return false;
        return matchesPatternNormalized(normalize(rawCommand), configuredPatterns, 0);
    }

    private static String normalize(String rawCommand) {
        String command = rawCommand.trim().toLowerCase(Locale.ROOT);
        if (command.startsWith("/")) command = command.substring(1);
        return command.replaceAll("\\s+", " ");
    }

    private static boolean isProtectedNormalized(String command, List<String> prefixes, int depth) {
        if (command.isBlank() || depth > 4) return false;
        String[] tokens = command.split("\\s+");
        String label = tokens[0];
        for (String candidate : prefixes) {
            if (candidate == null) continue;
            String prefix = candidate.trim().toLowerCase(Locale.ROOT);
            if (!prefix.isEmpty() && labelMatches(label, prefix)) return true;
        }

        if (isExecute(label)) {
            for (int i = 1; i + 1 < tokens.length; i++) {
                if (tokens[i].equals("run")) {
                    if (isProtectedNormalized(joinFrom(tokens, i + 1), prefixes, depth + 1)) return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesPatternNormalized(String command, List<String> patterns, int depth) {
        if (command.isBlank() || depth > 4) return false;
        String[] commandTokens = command.split("\\s+");
        String label = commandTokens[0];

        for (String rawPattern : patterns) {
            if (rawPattern == null || rawPattern.isBlank()) continue;
            String[] patternTokens = rawPattern.trim().toLowerCase(Locale.ROOT).split("\\s+");
            if (patternTokens.length == 0 || !labelMatches(label, patternTokens[0])) continue;
            if (matchTokens(commandTokens, patternTokens, 1, 1)) return true;
        }

        if (isExecute(label)) {
            for (int i = 1; i + 1 < commandTokens.length; i++) {
                if (commandTokens[i].equals("run")
                        && matchesPatternNormalized(joinFrom(commandTokens, i + 1), patterns, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchTokens(String[] command, String[] pattern, int commandIndex, int patternIndex) {
        while (patternIndex < pattern.length) {
            String token = pattern[patternIndex];
            if (token.equals("**")) {
                if (patternIndex == pattern.length - 1) return true;
                for (int i = commandIndex; i <= command.length; i++) {
                    if (matchTokens(command, pattern, i, patternIndex + 1)) return true;
                }
                return false;
            }
            if (commandIndex >= command.length) return false;
            if (!token.equals("*") && !command[commandIndex].equals(token)) return false;
            commandIndex++;
            patternIndex++;
        }
        return commandIndex == command.length;
    }

    private static boolean labelMatches(String actual, String configured) {
        if (actual.equals(configured)) return true;
        // A configured plain label also protects namespaced aliases, e.g. essentials:op.
        if (configured.indexOf(':') < 0) {
            return actual.startsWith(configured + ":") || actual.endsWith(":" + configured);
        }
        return actual.startsWith(configured + ":");
    }

    private static boolean isExecute(String label) {
        return label.equals("execute") || label.equals("minecraft:execute");
    }

    private static String joinFrom(String[] tokens, int index) {
        StringBuilder nested = new StringBuilder();
        for (int i = index; i < tokens.length; i++) {
            if (nested.length() > 0) nested.append(' ');
            nested.append(tokens[i]);
        }
        return nested.toString();
    }
}

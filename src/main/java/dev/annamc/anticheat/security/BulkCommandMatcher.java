package dev.annamc.anticheat.security;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Pure configurable matching for destructive player command patterns. */
public final class BulkCommandMatcher {
    private static final Pattern SPACE = Pattern.compile("\\s+");

    private BulkCommandMatcher() {}

    public static boolean matches(String raw, List<String> configuredLabels) {
        String value = SPACE.matcher(raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
        // Preserve WorldEdit's leading double slash while removing the usual command slash.
        if (value.startsWith("/") && !value.startsWith("//")) value = value.substring(1);
        if (value.isEmpty()) return false;

        int space = value.indexOf(' ');
        String command = space < 0 ? value : value.substring(0, space);
        String arguments = space < 0 ? "" : value.substring(space + 1).trim();
        // Do not block a normal self-kill command; selectors or other targets remain protected.
        if ((command.equals("kill") || command.equals("minecraft:kill")) && arguments.equals("@s")) return false;

        if (matchesConfiguredRoot(value, configuredLabels)) return true;

        // Parse only the command after each vanilla "run" separator rather than
        // searching arbitrary NBT/JSON arguments for words like "fill".
        if (command.equals("execute") || command.equals("minecraft:execute")) {
            String remaining = value;
            int runAt;
            while ((runAt = remaining.indexOf(" run ")) >= 0) {
                String nested = remaining.substring(runAt + 5).trim();
                if (matchesConfiguredRoot(nested, configuredLabels)) return true;
                if (!nested.startsWith("execute ") && !nested.startsWith("minecraft:execute ")) break;
                remaining = nested;
            }
        }
        return false;
    }

    private static boolean matchesConfiguredRoot(String command, List<String> labels) {
        for (String configured : labels) {
            if (configured == null || configured.isBlank()) continue;
            String label = configured.toLowerCase(Locale.ROOT).trim();
            if (command.equals(label) || command.startsWith(label + " ")) return true;
        }
        return false;
    }
}

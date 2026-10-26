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

        for (String configured : configuredLabels) {
            if (configured == null || configured.isBlank()) continue;
            String label = configured.toLowerCase(Locale.ROOT).trim();
            if (value.equals(label) || value.startsWith(label + " ")) return true;
            // Detect vanilla execute chains, e.g. /execute as @e run minecraft:fill ...
            if (command.equals("execute") || command.equals("minecraft:execute")) {
                Pattern nested = Pattern.compile("(?:^|\\s)(?:minecraft:)?" + Pattern.quote(label) + "(?:\\s|$)");
                if (nested.matcher(arguments).find()) return true;
            }
        }
        return false;
    }
}

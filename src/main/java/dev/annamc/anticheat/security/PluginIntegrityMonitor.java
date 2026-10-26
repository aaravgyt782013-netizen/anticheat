package dev.annamc.anticheat.security;

import dev.annamc.anticheat.alert.AlertService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Defensive plugin-integrity and static risk monitor.
 *
 * This hashes plugin JAR files, compares them with an owner-reviewed baseline,
 * reports additions/removals/changes, and scans class constants for combinations
 * of high-risk capabilities. It does not execute plugin code, disable/delete
 * plugins, or claim that heuristics prove malware.
 */
public final class PluginIntegrityMonitor implements Listener {
    private final JavaPlugin plugin;
    private final AlertService alerts;
    private final AtomicBoolean scanRunning = new AtomicBoolean();
    private final Map<String, String> reportedStates = new HashMap<>();
    private final Path pluginsDirectory;
    private final Path baselinePath;
    private final long monitorStartedAtMillis = System.currentTimeMillis();

    public PluginIntegrityMonitor(JavaPlugin plugin, AlertService alerts) {
        this.plugin = plugin;
        this.alerts = alerts;
        this.pluginsDirectory = plugin.getDataFolder().toPath().toAbsolutePath().normalize().getParent();
        this.baselinePath = plugin.getDataFolder().toPath().resolve("plugin-hashes.yml");
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("security.plugin-monitor.enabled", true)) return;
        long delay = Math.max(40L, plugin.getConfig().getLong("security.plugin-monitor.initial-scan-delay-ticks", 100L));
        long intervalSeconds = clamp(plugin.getConfig().getLong(
                "security.plugin-monitor.scan-interval-seconds", 300L), 60L, 86400L);
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> scan("startup", true), delay);
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                () -> scan("scheduled", false), delay + intervalSeconds * 20L, intervalSeconds * 20L);
    }

    private boolean withinLifecycleGracePeriod() {
        long graceSeconds = clamp(plugin.getConfig().getLong(
                "security.plugin-monitor.lifecycle-audit-grace-seconds", 120L), 0L, 3600L);
        return System.currentTimeMillis() - monitorStartedAtMillis < graceSeconds * 1000L;
    }

    public void scanNow(String reason) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> scan(reason, false));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (!plugin.getConfig().getBoolean("security.plugin-monitor.enabled", true)
                || !plugin.getConfig().getBoolean("security.plugin-monitor.audit-plugin-lifecycle", true)
                || event.getPlugin().equals(plugin) || withinLifecycleGracePeriod()) return;
        reportState("lifecycle-enabled:" + event.getPlugin().getName(),
                "Plugin enabled after ANNA AC started: " + event.getPlugin().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (!plugin.getConfig().getBoolean("security.plugin-monitor.enabled", true)
                || !plugin.getConfig().getBoolean("security.plugin-monitor.audit-plugin-lifecycle", true)
                || event.getPlugin().equals(plugin) || withinLifecycleGracePeriod()) return;
        reportState("lifecycle-disabled:" + event.getPlugin().getName(),
                "Plugin disabled after ANNA AC started: " + event.getPlugin().getName()
                        + ". Check whether this was an intentional reload/update.");
    }

    private void scan(String reason, boolean startup) {
        if (!scanRunning.compareAndSet(false, true)) return;
        try {
            if (!Files.isDirectory(pluginsDirectory)) {
                reportState("plugins-directory-missing", "Plugin directory is unavailable for integrity scanning: "
                        + pluginsDirectory);
                return;
            }

            boolean integrityEnabled = plugin.getConfig().getBoolean(
                    "security.plugin-monitor.integrity-hash-enabled", true);
            YamlConfiguration baseline = YamlConfiguration.loadConfiguration(baselinePath.toFile());
            Map<String, String> baselineHashes = readBaselineHashes(baseline);
            boolean hasBaseline = integrityEnabled && !baselineHashes.isEmpty();
            boolean baselineCreated = false;
            Map<String, String> current = new HashMap<>();
            Set<String> seen = new HashSet<>();
            List<Path> jars;
            try (var files = Files.list(pluginsDirectory)) {
                jars = files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .sorted().limit(clamp(plugin.getConfig().getLong(
                                "security.plugin-monitor.max-plugin-jars-per-scan", 512L), 1L, 2048L)).toList();
            }

            for (Path jar : jars) {
                String name = jar.getFileName().toString();
                String hash;
                try {
                    hash = sha256(jar);
                } catch (IOException ex) {
                    reportState("hash-error:" + name, "Could not hash plugin JAR " + name + ": " + ex.getClass().getSimpleName());
                    continue;
                }
                current.put(name, hash);
                seen.add(name);
                String previous = baselineHashes.get(name);
                boolean ignored = plugin.getConfig().getStringList("security.plugin-monitor.ignore-plugin-files")
                        .stream().anyMatch(n -> name.equalsIgnoreCase(n));
                if (!ignored && integrityEnabled && hasBaseline) {
                    if ((previous == null || previous.isBlank())
                            && plugin.getConfig().getBoolean("security.plugin-monitor.alert-new-plugin-jars", true)) {
                        reportState("new:" + name + ":" + hash,
                                "New plugin JAR found since the approved baseline: " + name + " SHA-256=" + hash);
                    } else if (previous != null && !previous.isBlank() && !previous.equalsIgnoreCase(hash)
                            && plugin.getConfig().getBoolean("security.plugin-monitor.alert-changed-plugin-jars", true)) {
                        reportState("changed:" + name + ":" + hash,
                                "Plugin JAR hash changed since the approved baseline: " + name
                                        + " old=" + previous + " current=" + hash);
                    }
                }
                if (plugin.getConfig().getBoolean("security.plugin-monitor.static-scan-enabled", true) && !ignored) {
                    scanForRisk(jar, name, hash);
                }
                if (integrityEnabled && !hasBaseline) baselineHashes.put(name, hash);
            }

            if (integrityEnabled && hasBaseline
                    && plugin.getConfig().getBoolean("security.plugin-monitor.alert-removed-plugin-jars", true)) {
                for (String oldName : baselineHashes.keySet()) {
                    if (!seen.contains(oldName)
                            && !plugin.getConfig().getStringList("security.plugin-monitor.ignore-plugin-files")
                            .stream().anyMatch(n -> oldName.equalsIgnoreCase(n))) {
                        reportState("removed:" + oldName, "Plugin JAR from the approved baseline is missing: " + oldName);
                    }
                }
            } else if (integrityEnabled && !hasBaseline) {
                baseline.set("meta.created-at", java.time.Instant.now().toString());
                baseline.set("meta.baseline-note", "Generated automatically on first scan. Review this file and rerun the scan after verifying plugins.");
                baselineCreated = true;
            }

            if (integrityEnabled && (baselineCreated || !Files.exists(baselinePath))) {
                try {
                    writeBaselineHashes(baseline, baselineHashes);
                    baseline.save(baselinePath.toFile());
                    reportState("baseline-created", "Plugin JAR integrity baseline created at plugins/ANNA-AC/plugin-hashes.yml. Review and approve every entry before trusting it.");
                } catch (IOException ex) {
                    reportState("baseline-save-failed", "Could not save plugin integrity baseline: " + ex.getClass().getSimpleName());
                }
            }

            if (reason.equalsIgnoreCase("manual")
                    || plugin.getConfig().getBoolean("security.plugin-monitor.report-scan-summary", false)) {
                reportState("scan-summary:" + java.time.Instant.now().getEpochSecond(),
                        "Plugin integrity scan finished (" + reason + "): JARs=" + current.size()
                                + ", baseline=" + (hasBaseline ? "present" : "initialised")
                                + ", static scan=" + plugin.getConfig().getBoolean("security.plugin-monitor.static-scan-enabled", true));
            }
        } catch (Throwable ex) {
            plugin.getLogger().warning("Plugin integrity scan failed safely: " + ex.getClass().getSimpleName());
        } finally {
            scanRunning.set(false);
        }
    }

    /**
     * Explicitly replaces the expected JAR hash inventory. This never happens
     * automatically after an update: the owner must verify plugin origins first.
     */
    public void approveCurrentBaseline(String approvedBy) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!plugin.getConfig().getBoolean("security.plugin-monitor.integrity-hash-enabled", true)) {
                reportState("baseline-approval-disabled",
                        "Baseline approval refused because security.plugin-monitor.integrity-hash-enabled is false.");
                return;
            }
            if (!scanRunning.compareAndSet(false, true)) {
                reportState("baseline-approval-busy",
                        "Plugin hash baseline approval was not performed because an integrity scan is already running. Try again shortly.");
                return;
            }
            try {
                Map<String, String> hashes = new java.util.TreeMap<>();
                long maxJars = clamp(plugin.getConfig().getLong(
                        "security.plugin-monitor.max-plugin-jars-per-scan", 512L), 1L, 2048L);
                try (var files = Files.list(pluginsDirectory)) {
                    for (Path jar : files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                            .sorted().limit(maxJars).toList()) {
                        hashes.put(jar.getFileName().toString(), sha256(jar));
                    }
                }
                YamlConfiguration approved = new YamlConfiguration();
                writeBaselineHashes(approved, hashes);
                approved.set("meta.created-at", java.time.Instant.now().toString());
                approved.set("meta.approved-by", truncate(approvedBy, 80));
                approved.set("meta.approval-note",
                        "Explicitly approved by an authorized operator. Hash approval is not a malware verdict; inspect each plugin source and origin before approval.");
                approved.save(baselinePath.toFile());
                synchronized (reportedStates) {
                    reportedStates.keySet().removeIf(key -> key.startsWith("new:")
                            || key.startsWith("changed:") || key.startsWith("removed:")
                            || key.equals("baseline-created"));
                }
                reportState("baseline-approved:" + java.time.Instant.now().getEpochSecond(),
                        "Plugin JAR hash baseline explicitly replaced by " + truncate(approvedBy, 80)
                                + " for " + hashes.size() + " JAR(s). Verify sources/origins separately; hashes are integrity signals, not a malware verdict.");
            } catch (Exception ex) {
                reportState("baseline-approval-failed",
                        "Could not approve plugin JAR hash baseline: " + ex.getClass().getSimpleName());
            } finally {
                scanRunning.set(false);
            }
        });
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void scanForRisk(Path jar, String fileName, String hash) {
        int totalBytes = 0;
        int maxBytes = (int) clamp(plugin.getConfig().getLong(
                "security.plugin-monitor.max-scan-bytes-per-jar", 16000000L), 100000L, 64000000L);
        Set<String> indicators = new HashSet<>();
        try (JarFile archive = new JarFile(jar.toFile(), false)) {
            var entries = archive.entries();
            int count = 0;
            int maxEntries = (int) clamp(plugin.getConfig().getLong(
                    "security.plugin-monitor.max-entries-per-jar", 20000L), 100L, 100000L);
            int maxEntryBytes = (int) clamp(plugin.getConfig().getLong(
                    "security.plugin-monitor.max-entry-bytes", 2000000L), 16384L, 8000000L);
            boolean scanResources = plugin.getConfig().getBoolean(
                    "security.plugin-monitor.scan-resource-files", true);
            while (entries.hasMoreElements() && count++ < maxEntries) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String entryName = entry.getName().toLowerCase(Locale.ROOT);
                boolean classFile = entryName.endsWith(".class");
                boolean resourceFile = scanResources && isTextResource(entryName);
                if (!classFile && !resourceFile) continue;
                long size = entry.getSize();
                if (size > maxEntryBytes) continue;
                try (InputStream in = archive.getInputStream(entry)) {
                    int remaining = maxBytes - totalBytes;
                    if (remaining <= 0) break;
                    byte[] bytes = readBounded(in, Math.min(maxEntryBytes, remaining));
                    totalBytes += bytes.length;
                    String text = new String(bytes, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
                    PluginRiskHeuristics.collectFromClassText(text, indicators);
                    if (totalBytes >= maxBytes) break;
                }
            }
        } catch (IOException ex) {
            reportState("static-scan-error:" + fileName + ":" + hash,
                    "Could not inspect plugin JAR " + fileName + " (not proof of malware): " + ex.getClass().getSimpleName());
            return;
        }

        Set<String> evidenceIndicators = PluginRiskHeuristics.withCorrelations(indicators);
        int score = PluginRiskHeuristics.score(evidenceIndicators);
        int threshold = (int) clamp(plugin.getConfig().getLong(
                "security.plugin-monitor.minimum-risk-score", 5L), 3L, 100L);
        if (score >= threshold) {
            List<String> evidence = new ArrayList<>(evidenceIndicators);
            evidence.sort(String::compareTo);
            String detail = "STATIC REVIEW REQUIRED (heuristic only): " + fileName + " SHA-256=" + hash
                    + " risk-score=" + score + " threshold=" + threshold
                    + " indicators=" + String.join(",", evidence)
                    + ". This is not a malware verdict; review the source and origin before action.";
            reportState("risk:" + fileName + ":" + hash + ":" + score, detail);
        }
    }

    /**
     * Filenames may contain dots, which are YAML configuration path separators.
     * Store a URL-safe Base64 key and keep the original filename in a value.
     */
    private static Map<String, String> readBaselineHashes(YamlConfiguration yaml) {
        Map<String, String> result = new HashMap<>();
        var root = yaml.getConfigurationSection("hashes");
        if (root == null) return result;
        for (String id : root.getKeys(false)) {
            var entry = root.getConfigurationSection(id);
            if (entry == null) continue;
            String fileName = entry.getString("file");
            String hash = entry.getString("sha256");
            if (fileName != null && !fileName.isBlank() && hash != null && !hash.isBlank()) {
                result.put(fileName, hash);
            }
        }
        // Read the old path-based format if it was created by an earlier development build.
        if (result.isEmpty()) readLegacyHashes(root, "", result);
        return result;
    }

    private static void readLegacyHashes(org.bukkit.configuration.ConfigurationSection section,
                                         String prefix, Map<String, String> target) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object raw = section.get(key);
            if (raw instanceof String value && value.matches("(?i)[0-9a-f]{64}")) {
                target.put(path, value);
            } else {
                var child = section.getConfigurationSection(key);
                if (child != null) readLegacyHashes(child, path, target);
            }
        }
    }

    private static void writeBaselineHashes(YamlConfiguration yaml, Map<String, String> hashes) {
        yaml.set("hashes", null);
        for (Map.Entry<String, String> entry : hashes.entrySet()) {
            String id = PluginHashKey.forFileName(entry.getKey());
            yaml.set("hashes." + id + ".file", entry.getKey());
            yaml.set("hashes." + id + ".sha256", entry.getValue());
        }
    }

    private static boolean isTextResource(String name) {
        return name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".properties")
                || name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".js")
                || name.endsWith(".xml") || name.endsWith(".toml") || name.endsWith(".conf")
                || name.endsWith(".sh") || name.endsWith(".bat") || name.endsWith(".ps1");
    }

    private static byte[] readBounded(InputStream in, int max) throws IOException {
        if (max <= 0) return new byte[0];
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(max, 8192));
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            int keep = Math.min(n, max - out.size());
            if (keep > 0) out.write(buffer, 0, keep);
            if (out.size() >= max) break;
        }
        return out.toByteArray();
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[16384];
                int n;
                while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 not available", impossible);
        }
    }

    private void reportState(String stateKey, String detail) {
        synchronized (reportedStates) {
            String fingerprint = Integer.toHexString(detail.hashCode());
            if (fingerprint.equals(reportedStates.get(stateKey))) return;
            reportedStates.put(stateKey, fingerprint);
        }
        // Security alert sink only uses Bukkit operations on the primary thread.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled()) alerts.sendSecurityAlert("SERVER", new java.util.UUID(0L, 0L),
                    "plugin-integrity", detail);
        });
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}

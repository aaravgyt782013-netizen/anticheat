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

    public void scanNow(String reason) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> scan(reason, false));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        if (!plugin.getConfig().getBoolean("security.plugin-monitor.enabled", true)
                || !plugin.getConfig().getBoolean("security.plugin-monitor.audit-plugin-lifecycle", true)
                || event.getPlugin().equals(plugin)) return;
        reportState("lifecycle-enabled:" + event.getPlugin().getName(),
                "Plugin enabled after ANNA AC started: " + event.getPlugin().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (!plugin.getConfig().getBoolean("security.plugin-monitor.enabled", true)
                || !plugin.getConfig().getBoolean("security.plugin-monitor.audit-plugin-lifecycle", true)
                || event.getPlugin().equals(plugin)) return;
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
            boolean hasBaseline = integrityEnabled && baseline.getConfigurationSection("hashes") != null
                    && !baseline.getConfigurationSection("hashes").getKeys(false).isEmpty();
            boolean baselineCreated = false;
            Map<String, String> current = new HashMap<>();
            Set<String> seen = new HashSet<>();
            List<Path> jars;
            try (var files = Files.list(pluginsDirectory)) {
                jars = files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .sorted().limit(512).toList();
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
                String previous = baseline.getString("hashes." + name, "");
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
                if (integrityEnabled && !hasBaseline) baseline.set("hashes." + name, hash);
            }

            if (integrityEnabled && hasBaseline
                    && plugin.getConfig().getBoolean("security.plugin-monitor.alert-removed-plugin-jars", true)) {
                for (String oldName : baseline.getConfigurationSection("hashes").getKeys(false)) {
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

    private void scanForRisk(Path jar, String fileName, String hash) {
        int totalBytes = 0;
        int maxBytes = (int) clamp(plugin.getConfig().getLong(
                "security.plugin-monitor.max-scan-bytes-per-jar", 16000000L), 100000L, 64000000L);
        Set<String> indicators = new HashSet<>();
        try (JarFile archive = new JarFile(jar.toFile(), false)) {
            var entries = archive.entries();
            int count = 0;
            while (entries.hasMoreElements() && count++ < 20000) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".class")) continue;
                long size = entry.getSize();
                if (size > 2_000_000L) continue;
                try (InputStream in = archive.getInputStream(entry)) {
                    byte[] bytes = readBounded(in, Math.min(2_000_000, maxBytes - totalBytes));
                    totalBytes += bytes.length;
                    if (totalBytes >= maxBytes) break;
                    String text = new String(bytes, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
                    evaluateIndicators(text, indicators);
                }
            }
        } catch (IOException ex) {
            reportState("static-scan-error:" + fileName + ":" + hash,
                    "Could not inspect plugin JAR " + fileName + " (not proof of malware): " + ex.getClass().getSimpleName());
            return;
        }

        int score = score(indicators);
        int threshold = (int) clamp(plugin.getConfig().getLong(
                "security.plugin-monitor.minimum-risk-score", 5L), 3L, 100L);
        if (score >= threshold) {
            List<String> evidence = new ArrayList<>(indicators);
            evidence.sort(String::compareTo);
            String detail = "STATIC REVIEW REQUIRED (heuristic only): " + fileName + " SHA-256=" + hash
                    + " risk-score=" + score + " threshold=" + threshold
                    + " indicators=" + String.join(",", evidence)
                    + ". This is not a malware verdict; review the source and origin before action.";
            reportState("risk:" + fileName + ":" + hash + ":" + score, detail);
        }
    }

    private static void evaluateIndicators(String data, Set<String> found) {
        if (has(data, "java/lang/processbuilder", "java/lang/runtime", "exec")) found.add("process-execution-capability");
        if (has(data, "java/net/socket", "java/net/http/httpclient", "java/net/httpurlconnection")
                && has(data, "java/lang/runtime", "java/lang/processbuilder", "exec")) found.add("network-plus-process-execution");
        if (has(data, "java/nio/file/files", "deleteifexists")
                && has(data, "org/bukkit/bukkit", "dispatchcommand")
                && has(data, "java/net/socket", "java/net/http/httpclient", "java/net/url")) found.add("network-plus-command-plus-file-delete-capability");
        if (has(data, "org/bukkit/plugin/pluginmanager", "disableplugin")
                && has(data, "java/lang/runtime", "java/lang/processbuilder", "exec")) found.add("plugin-management-plus-process-execution");
        if (has(data, "org/bukkit/bukkit", "dispatchcommand")
                && has(data, "java/net/socket", "java/net/http/httpclient", "java/net/url")
                && has(data, "setop", "minecraft:op", "bukkit:op")) found.add("network-plus-command-dispatch-plus-op-strings");
        if (has(data, "java/nio/file/files", "deleteifexists")
                && has(data, "worlds/", "level.dat", "region/r.")
                && has(data, "java/lang/runtime", "java/lang/processbuilder", "exec")) found.add("world-file-delete-plus-process-execution");
    }

    private static boolean has(String haystack, String... needles) {
        for (String needle : needles) if (haystack.contains(needle)) return true;
        return false;
    }

    private static int score(Set<String> indicators) {
        int score = 0;
        for (String indicator : indicators) {
            score += switch (indicator) {
                case "network-plus-process-execution", "network-plus-command-plus-file-delete-capability",
                        "world-file-delete-plus-process-execution" -> 5;
                case "plugin-management-plus-process-execution", "network-plus-command-dispatch-plus-op-strings" -> 4;
                case "process-execution-capability" -> 2;
                default -> 1;
            };
        }
        return score;
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

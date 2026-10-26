package dev.annamc.anticheat.evidence;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/** Asynchronous, bounded JSONL evidence storage. */
public final class EvidenceStore {
    private final JavaPlugin plugin;
    private final AtomicLong writes = new AtomicLong();
    private volatile boolean invalidDirectoryWarned;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "anna-ac-evidence");
        t.setDaemon(true);
        return t;
    });

    public EvidenceStore(JavaPlugin plugin) {
        this.plugin = plugin;
        writer.submit(this::initializeStorage);
    }

    public void record(UUID uuid, String player, String check, double vl, String detail) {
        int maxDetailLength = Math.max(64, Math.min(10000,
                plugin.getConfig().getInt("database.max-detail-length", 1200)));
        String line = "{\"time\":\"" + Instant.now() + "\",\"uuid\":\"" + uuid
                + "\",\"player\":\"" + escapeJson(truncate(player, 64)) + "\",\"check\":\""
                + escapeJson(truncate(check, 64)) + "\",\"vl\":" + vl + ",\"detail\":\""
                + escapeJson(truncate(detail, maxDetailLength)) + "\"}" + System.lineSeparator();
        try {
            writer.submit(() -> {
                try {
                    Path dir = evidenceDirectory();
                    Files.createDirectories(dir);
                    Path file = dir.resolve(uuid + ".jsonl");
                    Files.writeString(file, line, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    long count = writes.incrementAndGet();
                    if (count % 100 == 0) pruneFile(file);
                    if (count % 1000 == 0) pruneAll();
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not write evidence", e);
                }
            });
        } catch (RejectedExecutionException shuttingDown) {
            plugin.getLogger().fine("Evidence write skipped during plugin shutdown.");
        }
    }

    public Path file(UUID uuid) {
        return evidenceDirectory().resolve(uuid + ".jsonl");
    }

    private Path evidenceDirectory() {
        Path base = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        String relative = plugin.getConfig().getString("database.evidence-directory", "evidence");
        if (relative == null || relative.isBlank()) relative = "evidence";
        Path resolved;
        try {
            resolved = base.resolve(relative).normalize();
        } catch (RuntimeException invalidPath) {
            resolved = base.resolve("evidence");
        }
        if (!resolved.startsWith(base)) {
            if (!invalidDirectoryWarned) {
                invalidDirectoryWarned = true;
                plugin.getLogger().warning("database.evidence-directory must stay inside the ANNA AC plugin folder; using 'evidence'.");
            }
            return base.resolve("evidence");
        }
        return resolved;
    }

    private void initializeStorage() {
        try {
            Files.createDirectories(evidenceDirectory());
            pruneAll();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not initialize evidence storage directory", e);
        }
    }

    private void pruneAll() {
        Path dir = evidenceDirectory();
        if (!Files.isDirectory(dir)) return;
        try (var paths = Files.list(dir)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .forEach(this::pruneFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not scan evidence directory for retention", e);
        }
    }

    private void pruneFile(Path file) {
        try {
            if (!Files.isRegularFile(file)) return;
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int retentionDays = Math.max(0,
                    plugin.getConfig().getInt("database.retention-days", 7));
            int maxRecords = Math.max(1,
                    plugin.getConfig().getInt("database.max-records-per-player", 5000));
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            List<String> retained = new ArrayList<>(lines.size());
            for (String line : lines) {
                Instant timestamp = readTimestamp(line);
                if (timestamp == null || !timestamp.isBefore(cutoff)) retained.add(line);
            }
            if (retained.size() > maxRecords)
                retained = new ArrayList<>(retained.subList(retained.size() - maxRecords, retained.size()));
            if (retained.size() == lines.size()) return;

            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, retained, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not prune evidence file " + file.getFileName(), e);
        }
    }

    private Instant readTimestamp(String line) {
        try {
            String marker = "\"time\":\"";
            int start = line.indexOf(marker);
            if (start < 0) return null;
            start += marker.length();
            int end = line.indexOf('"', start);
            if (end < 0) return null;
            return Instant.parse(line.substring(start, end));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(3, TimeUnit.SECONDS)) writer.shutdownNow();
        } catch (InterruptedException interrupted) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    static String escapeJson(String value) {
        if (value == null) return "";
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) escaped.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) ch));
                    else escaped.append(ch);
                }
            }
        }
        return escaped.toString();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

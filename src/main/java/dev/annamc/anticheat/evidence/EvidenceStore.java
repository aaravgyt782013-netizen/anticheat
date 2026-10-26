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
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class EvidenceStore {
    private final JavaPlugin plugin;
    private final AtomicLong writes = new AtomicLong();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "anna-ac-evidence");
        t.setDaemon(true);
        return t;
    });

    public EvidenceStore(JavaPlugin plugin) {
        this.plugin = plugin;
        writer.submit(this::pruneAll);
    }

    public void record(UUID uuid, String player, String check, double vl, String detail) {
        String line = "{\"time\":\"" + Instant.now() + "\",\"uuid\":\"" + uuid
                + "\",\"player\":\"" + json(player) + "\",\"check\":\"" + json(check)
                + "\",\"vl\":" + vl + ",\"detail\":\"" + json(detail) + "\"}" + System.lineSeparator();
        writer.submit(() -> {
            try {
                Path dir = plugin.getDataFolder().toPath().resolve("evidence");
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
    }

    public Path file(UUID uuid) {
        return plugin.getDataFolder().toPath().resolve("evidence").resolve(uuid + ".jsonl");
    }

    private void pruneAll() {
        Path dir = plugin.getDataFolder().toPath().resolve("evidence");
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
            int retentionDays = Math.max(0, plugin.getConfig().getInt("settings.evidence-retention-days", 7));
            int maxRecords = Math.max(1, plugin.getConfig().getInt("settings.max-evidence-records-per-player", 5000));
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
    }

    private static String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}

package dev.annamc.anticheat.evidence;

import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

public final class EvidenceStore {
    private final JavaPlugin plugin;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "anna-ac-evidence");
        t.setDaemon(true);
        return t;
    });
    public EvidenceStore(JavaPlugin plugin) { this.plugin = plugin; }

    public void record(UUID uuid, String player, String check, double vl, String detail) {
        String line = "{\"time\":\"" + Instant.now() + "\",\"uuid\":\"" + uuid
                + "\",\"player\":\"" + json(player) + "\",\"check\":\"" + json(check)
                + "\",\"vl\":" + vl + ",\"detail\":\"" + json(detail) + "\"}" + System.lineSeparator();
        writer.submit(() -> {
            try {
                Path dir = plugin.getDataFolder().toPath().resolve("evidence");
                Files.createDirectories(dir);
                Files.writeString(dir.resolve(uuid + ".jsonl"), line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not write evidence", e);
            }
        });
    }
    public Path file(UUID uuid) {
        return plugin.getDataFolder().toPath().resolve("evidence").resolve(uuid + ".jsonl");
    }
    public void close() { writer.shutdown(); }
    private static String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}

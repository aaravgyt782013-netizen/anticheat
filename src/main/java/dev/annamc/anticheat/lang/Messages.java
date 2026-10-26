package dev.annamc.anticheat.lang;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.Locale;

public final class Messages {
    private final JavaPlugin plugin;
    private YamlConfiguration selected;
    private YamlConfiguration english;
    private YamlConfiguration fallback;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        saveIfMissing("messages_en.yml");
        saveIfMissing("messages_hi.yml");
        fallback = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        english = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages_en.yml"));
        String language = plugin.getConfig().getString("settings.language", "en");
        if (language == null) language = "en";
        language = language.toLowerCase(Locale.ROOT).trim();
        if (!language.matches("[a-z]{2}")) language = "en";
        File file = new File(plugin.getDataFolder(), "messages_" + language + ".yml");
        if (!file.isFile()) {
            plugin.getLogger().warning("Unknown language '" + language + "'; falling back to English. Supported: en, hi.");
            file = new File(plugin.getDataFolder(), "messages_en.yml");
        }
        selected = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String key) {
        String value = selected == null ? null : selected.getString(key);
        if (value == null && english != null) value = english.getString(key);
        if (value == null && fallback != null) value = fallback.getString(key);
        if (value == null) value = key;
        String prefix = plugin.getConfig().getString("settings.prefix", "&8[&bANNA AC&8] &r");
        value = value.replace("%prefix%", prefix == null ? "" : prefix);
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public String get(String key, Map<String, ?> placeholders) {
        String value = get(key);
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            value = value.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }
        return value;
    }

    private void saveIfMissing(String resource) {
        File target = new File(plugin.getDataFolder(), resource);
        if (!target.exists()) plugin.saveResource(resource, false);
    }
}

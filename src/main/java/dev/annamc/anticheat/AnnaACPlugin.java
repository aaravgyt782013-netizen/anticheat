package dev.annamc.anticheat;

import dev.annamc.anticheat.alert.AlertService;
import dev.annamc.anticheat.check.ClickRateCheck;
import dev.annamc.anticheat.config.ConfigurationDiagnostics;
import dev.annamc.anticheat.config.ModularConfigManager;
import dev.annamc.anticheat.check.FastBreakCheck;
import dev.annamc.anticheat.check.CombatPatternCheck;
import dev.annamc.anticheat.check.KnockbackPredictionCheck;
import dev.annamc.anticheat.check.MovementCheck;
import dev.annamc.anticheat.check.MovementPhysicsCheck;
import dev.annamc.anticheat.check.NukerBurstCheck;
import dev.annamc.anticheat.check.NoFallSignalCheck;
import dev.annamc.anticheat.check.ReachCheck;
import dev.annamc.anticheat.check.ScaffoldSignalCheck;
import dev.annamc.anticheat.check.ViolationManager;
import dev.annamc.anticheat.check.WallHitCheck;
import dev.annamc.anticheat.command.AACCommand;
import dev.annamc.anticheat.evidence.EvidenceStore;
import dev.annamc.anticheat.lang.Messages;
import dev.annamc.anticheat.packet.PacketEventsBridge;
import dev.annamc.anticheat.security.ForceOpGuard;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class AnnaACPlugin extends JavaPlugin {
    private EvidenceStore evidenceStore;
    private PacketEventsBridge packetBridge;
    private Messages messages;
    private ModularConfigManager configManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("messages_en.yml", false);
        saveResource("messages_hi.yml", false);

        configManager = new ModularConfigManager(this);
        configManager.load();
        messages = new Messages(this);
        evidenceStore = new EvidenceStore(this);
        AlertService alerts = new AlertService(this, evidenceStore, messages);
        ViolationManager violations = new ViolationManager(this, alerts);

        getServer().getPluginManager().registerEvents(new MovementCheck(this, violations), this);
        getServer().getPluginManager().registerEvents(new MovementPhysicsCheck(this, violations), this);
        getServer().getPluginManager().registerEvents(new KnockbackPredictionCheck(this, violations), this);
        getServer().getPluginManager().registerEvents(new ReachCheck(this, violations), this);
        getServer().getPluginManager().registerEvents(new WallHitCheck(this, violations), this);
        getServer().getPluginManager().registerEvents(new NukerBurstCheck(this, violations), this);
        getServer().getPluginManager().registerEvents(new FastBreakCheck(this, violations), this);
        getServer().getPluginManager().registerEvents(new ClickRateCheck(this, violations), this);
        getServer().getPluginManager().registerEvents(new CombatPatternCheck(this, violations), this);
        getServer().getPluginManager().registerEvents(new ScaffoldSignalCheck(this, violations), this);
        getServer().getPluginManager().registerEvents(new NoFallSignalCheck(this, violations), this);
        getServer().getPluginManager().registerEvents(violations, this);
        getServer().getPluginManager().registerEvents(new ForceOpGuard(this, evidenceStore, messages, alerts), this);

        AACCommand command = new AACCommand(this, violations, evidenceStore, alerts, messages);
        if (getCommand("annaac") != null) {
            getCommand("annaac").setExecutor(command);
            getCommand("annaac").setTabCompleter(command);
        }

        if (Bukkit.getPluginManager().getPlugin("packetevents") != null) {
            packetBridge = new PacketEventsBridge(this, violations);
            getServer().getPluginManager().registerEvents(packetBridge, this);
            packetBridge.register();
            getLogger().info("PacketEvents detected; packet telemetry registered (experimental and disabled by default).");
        } else {
            getLogger().warning("PacketEvents is not installed. Packet telemetry is unavailable.");
        }
        for (String note : ConfigurationDiagnostics.inspect(this)) {
            getLogger().warning("Configuration: " + note);
        }
        getLogger().info("ANNA AC enabled. Experimental checks are disabled by default; validate on a staging server.");
    }

    /** Reloads the base file and then overlays the separate editable configuration files. */
    public void reloadAllConfiguration() {
        reloadConfig();
        if (configManager != null) configManager.load();
        if (messages != null) messages.reload();
        for (String note : ConfigurationDiagnostics.inspect(this)) {
            getLogger().warning("Configuration: " + note);
        }
    }

    @Override
    public void onDisable() {
        if (packetBridge != null) packetBridge.unregister();
        if (evidenceStore != null) evidenceStore.close();
    }
}

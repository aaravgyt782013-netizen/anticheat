package dev.annamc.anticheat.check;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Conservative mace damage anomaly signal. Disabled by default because damage
 * can vary with enchantments, plugins, and server mechanics.
 */
public final class MaceDamageCheck implements Listener {
    private final JavaPlugin plugin;
    private final ViolationManager violations;

    public MaceDamageCheck(JavaPlugin plugin, ViolationManager violations) {
        this.plugin = plugin;
        this.violations = violations;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity)
                || CheckUtil.shouldSkip(plugin, player, "mace-damage")) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon.getType() != Material.MACE) return;

        double fallDistance = Math.max(0.0, player.getFallDistance());
        double baseAllowance = plugin.getConfig().getDouble("checks.mace-damage.base-damage-allowance", 10.0);
        double tolerance = plugin.getConfig().getDouble("checks.mace-damage.extra-tolerance", 12.0);
        double maximumExpected;
        if (plugin.getConfig().getBoolean("checks.mace-damage.vanilla-smash-model-enabled", true)) {
            int densityLevel = weapon.getEnchantmentLevel(Enchantment.DENSITY);
            double densityAllowance = plugin.getConfig().getDouble(
                    "checks.mace-damage.density-damage-per-block-per-level", 0.5);
            maximumExpected = MaceDamageMath.expectedAllowance(
                    fallDistance, densityLevel, baseAllowance, tolerance, densityAllowance);
        } else {
            // Compatibility mode for servers with custom mace damage formulas.
            double perBlockAllowance = plugin.getConfig().getDouble(
                    "checks.mace-damage.damage-per-fall-block-allowance", 4.5);
            maximumExpected = baseAllowance + fallDistance * Math.max(0.0, perBlockAllowance) + tolerance;
        }
        double observed = event.getDamage();

        if (observed > maximumExpected) {
            violations.add(player, "mace-damage",
                    plugin.getConfig().getDouble("checks.mace-damage.violation-add", 1.0),
                    String.format(java.util.Locale.ROOT,
                            "mace-damage-anomaly observed=%.2f allowance=%.2f fall-distance=%.2f target=%s",
                            observed, maximumExpected, fallDistance, event.getEntity().getType().name()));
        }
    }
}
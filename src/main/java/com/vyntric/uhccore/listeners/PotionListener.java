package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUHCCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.EnumSet;
import java.util.Set;

public class PotionListener implements Listener {

    private final VyntricUHCCore plugin;

    private static final Set<PotionType> NEGATIVE_TYPES = EnumSet.of(
            PotionType.HARMING,
            PotionType.POISON,
            PotionType.WEAKNESS,
            PotionType.SLOWNESS
    );

    public PotionListener(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onThrow(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("potions.nerf-enabled", true)) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;
        if (item.getType() != Material.SPLASH_POTION && item.getType() != Material.LINGERING_POTION) return;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return;

        PotionType baseType = meta.getBasePotionType();
        if (baseType == null) return;

        boolean blockNegative = plugin.getConfig().getBoolean("potions.disable-splash-negative", true);
        boolean blockInstantHealth = plugin.getConfig().getBoolean("potions.disable-instant-health-splash", false);

        boolean isNegative = NEGATIVE_TYPES.contains(baseType);
        boolean isInstantHealth = baseType == PotionType.HEALING;

        if ((blockNegative && isNegative) || (blockInstantHealth && isInstantHealth)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.sendMessage("§cThis potion type has been disabled for this game.");
        }
    }
}

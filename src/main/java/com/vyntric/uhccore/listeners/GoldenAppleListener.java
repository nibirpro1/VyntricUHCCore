package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUHCCore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GoldenAppleListener implements Listener {

    private final VyntricUHCCore plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public GoldenAppleListener(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.ENCHANTED_GOLDEN_APPLE) return;
        if (!plugin.getConfig().getBoolean("golden-apple.nerf-enabled", true)) return;

        Player player = event.getPlayer();
        long cooldownSeconds = plugin.getConfig().getLong("golden-apple.cooldown-seconds", 30);
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (cooldownSeconds > 0 && now - last < cooldownSeconds * 1000L) {
            event.setCancelled(true);
            long remaining = (cooldownSeconds * 1000L - (now - last)) / 1000L;
            player.sendMessage("§cYou must wait " + remaining + "s before eating another golden apple.");
            return;
        }

        cooldowns.put(player.getUniqueId(), now);

        // Remove unwanted effects one tick after consumption (effects apply after the event fires)
        boolean removeRegen = plugin.getConfig().getBoolean("golden-apple.remove-regeneration", true);
        boolean removeAbsorption = plugin.getConfig().getBoolean("golden-apple.remove-absorption", false);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (removeRegen) player.removePotionEffect(PotionEffectType.REGENERATION);
            if (removeAbsorption) player.removePotionEffect(PotionEffectType.ABSORPTION);
        });
    }
}

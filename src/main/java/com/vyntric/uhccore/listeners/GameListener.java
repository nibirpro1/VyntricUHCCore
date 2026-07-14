package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUHCCore;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GameListener implements Listener {

    private final VyntricUHCCore plugin;

    public GameListener(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event);
        if (attacker == null) return;

        // Block PVP during grace period / waiting
        if (!plugin.getGameManager().isPvpEnabled()) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "PVP is currently disabled.");
            return;
        }

        // Once deathmatch starts it's every player for themselves - friendly fire is always
        // allowed here regardless of the teams.friendly-fire config, so teammates can fight too.
        if (plugin.getGameManager().getPhase() == com.vyntric.uhccore.game.GamePhase.DEATHMATCH) {
            return;
        }

        // Block friendly fire if teams don't allow it
        if (!plugin.getTeamManager().isFriendlyFire() && plugin.getTeamManager().isSameTeam(attacker, victim)) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "Friendly fire is disabled.");
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.getTeamManager().markEliminated(player);

        String msg = plugin.getMessage("player-eliminated").replace("{player}", player.getName());
        plugin.getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getMessage("prefix") + msg));

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.setGameMode(GameMode.SPECTATOR);
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getScoreboardManager().updateFor(event.getPlayer());
        // Refresh everyone's tab list so the online/alive counts stay accurate for the new join too.
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getTabListManager().updateAll());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Keep team membership, player may reconnect; scoreboard clears automatically on quit.
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getTabListManager().updateAll());
    }
}

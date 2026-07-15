package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUHCCore;
import com.vyntric.uhccore.lobby.LobbyKitItem;
import com.vyntric.uhccore.team.TeamManager;
import com.vyntric.uhccore.team.UHCTeam;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class LobbyKitListener implements Listener {

    private final VyntricUHCCore plugin;

    public LobbyKitListener(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        LobbyKitItem kitItem = plugin.getLobbyKitManager().identify(item);
        if (kitItem == null) return;

        Player player = event.getPlayer();

        switch (kitItem) {
            case SCENARIOS -> {
                event.setCancelled(true);
                player.sendMessage(ChatColor.DARK_PURPLE + "--- Active Scenarios ---");
                player.sendMessage(ChatColor.GRAY + plugin.getScenarioManager().describeEnabled());
            }
            case TEAM_INFO -> {
                event.setCancelled(true);
                sendTeamInfo(player);
            }
            case STATS -> {
                event.setCancelled(true);
                sendStats(player);
            }
            case TEAM_SWORD -> {
                event.setCancelled(true);
                player.sendMessage(ChatColor.GRAY + "Right-click a player to invite them, or right-click a "
                        + "teammate while holding this to kick them.");
            }
        }
    }

    private void sendTeamInfo(Player player) {
        TeamManager teamManager = plugin.getTeamManager();
        UHCTeam team = teamManager.getTeam(player);
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You're not on a team yet. Create one with /vyntricuhc team create <name>.");
            return;
        }

        player.sendMessage(ChatColor.DARK_PURPLE + "--- Team " + team.getName() + " ---");
        for (UUID memberId : team.getMembers()) {
            Player member = plugin.getServer().getPlayer(memberId);
            String name = member != null ? member.getName() : plugin.getStatsManager().getName(memberId);
            boolean isOwner = memberId.equals(team.getOwner());
            player.sendMessage(ChatColor.GRAY + "- " + name + (isOwner ? ChatColor.GOLD + " (owner)" : ""));
        }
    }

    private void sendStats(Player player) {
        var stats = plugin.getStatsManager().get(player.getUniqueId());
        player.sendMessage(ChatColor.DARK_PURPLE + "--- Your Stats ---");
        player.sendMessage(ChatColor.GRAY + "Wins: " + ChatColor.WHITE + stats.wins);
        player.sendMessage(ChatColor.GRAY + "Kills: " + ChatColor.WHITE + stats.kills);
        player.sendMessage(ChatColor.GRAY + "Deaths: " + ChatColor.WHITE + stats.deaths);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getLobbyKitManager().isKitItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }
}

package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUHCCore;
import com.vyntric.uhccore.game.GamePhase;
import com.vyntric.uhccore.team.TeamManager;
import com.vyntric.uhccore.team.UHCTeam;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

public class TeamInteractListener implements Listener {

    private final VyntricUHCCore plugin;

    public TeamInteractListener(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        // Invite/kick only works in the waiting lobby, before the game has started
        if (plugin.getGameManager().getPhase() != GamePhase.WAITING) return;
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player clicker = event.getPlayer();
        if (clicker.equals(target)) return;

        TeamManager teamManager = plugin.getTeamManager();
        UHCTeam team = teamManager.getTeam(clicker);
        if (team == null) {
            clicker.sendMessage(ChatColor.RED + "You don't own a team. Create one with /vyntricuhc team create <name>.");
            return;
        }

        if (!teamManager.isOwner(clicker, team)) {
            // Not the owner - do nothing special, let the interaction pass through normally
            return;
        }

        event.setCancelled(true);

        ItemStack handItem = clicker.getInventory().getItemInMainHand();
        boolean holdingSword = handItem != null && isSword(handItem.getType());

        if (holdingSword) {
            handleKick(clicker, target, team, teamManager);
        } else {
            handleInvite(clicker, target, team, teamManager);
        }
    }

    private void handleInvite(Player owner, Player target, UHCTeam team, TeamManager teamManager) {
        UHCTeam targetTeam = teamManager.getTeam(target);
        if (targetTeam != null) {
            owner.sendMessage(ChatColor.RED + target.getName() + " is already in a team.");
            return;
        }

        teamManager.sendInvite(owner, target);
        owner.sendMessage(ChatColor.GREEN + "Invite sent to " + target.getName() + ".");
        target.sendMessage(ChatColor.DARK_PURPLE + owner.getName() + " invited you to join team "
                + ChatColor.WHITE + team.getName() + ChatColor.DARK_PURPLE + "! Type "
                + ChatColor.WHITE + "/vyntricuhc team accept" + ChatColor.DARK_PURPLE + " to join.");
    }

    private void handleKick(Player owner, Player target, UHCTeam team, TeamManager teamManager) {
        UHCTeam targetTeam = teamManager.getTeam(target);
        if (targetTeam == null || !targetTeam.getName().equalsIgnoreCase(team.getName())) {
            owner.sendMessage(ChatColor.RED + target.getName() + " is not in your team.");
            return;
        }

        if (target.getUniqueId().equals(team.getOwner())) {
            owner.sendMessage(ChatColor.RED + "You can't kick yourself as the team owner.");
            return;
        }

        teamManager.removePlayerFromTeam(target);
        owner.sendMessage(ChatColor.YELLOW + target.getName() + " has been kicked from the team.");
        target.sendMessage(ChatColor.RED + "You have been kicked from team " + team.getName() + ".");
    }

    private boolean isSword(Material material) {
        return material.name().endsWith("_SWORD");
    }
}

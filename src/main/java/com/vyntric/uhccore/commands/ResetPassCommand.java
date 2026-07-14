package com.vyntric.uhccore.commands;

import com.vyntric.uhccore.VyntricUHCCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ResetPassCommand implements CommandExecutor {

    private final VyntricUHCCore plugin;

    public ResetPassCommand(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vyntric.uhc.resetpass")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Usage: /resetpass <ign>");
            return true;
        }

        String targetName = args[0];

        if (!plugin.getConfirmationManager().confirm(sender, "resetpass:" + targetName.toLowerCase(),
                "reset " + targetName + "'s password (this will kick them if online)")) return true;

        boolean reset = plugin.getAuthManager().resetPassword(targetName);

        if (!reset) {
            sender.sendMessage(ChatColor.RED + "'" + targetName + "' isn't registered, nothing to reset.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + targetName + "'s password has been reset. "
                + "They can set a new one with /register <password>.");

        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target != null) {
            target.sendMessage(ChatColor.YELLOW + "An operator has reset your password.");
            target.kickPlayer(ChatColor.YELLOW + "Your password has been reset.\n"
                    + ChatColor.GRAY + "Rejoin the server and use /register <password>.");
        }

        return true;
    }
}

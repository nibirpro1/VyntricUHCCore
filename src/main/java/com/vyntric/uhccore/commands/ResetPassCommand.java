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
            sender.sendMessage(ChatColor.RED + "Byabohar: /resetpass <ign>");
            return true;
        }

        String targetName = args[0];
        boolean reset = plugin.getAuthManager().resetPassword(targetName);

        if (!reset) {
            sender.sendMessage(ChatColor.RED + "'" + targetName + "' register kora nai, reset korar kichu nai.");
            return true;
        }

        sender.sendMessage(ChatColor.GREEN + targetName + " er password reset kora hoyeche. "
                + "Se abar /register <password> <confirmPassword> diye notun password banate parbe.");

        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target != null) {
            target.sendMessage(ChatColor.YELLOW + "Ekjon operator tomar password reset kore diyeche.");
            target.kickPlayer(ChatColor.YELLOW + "Tomar password reset kora hoyeche.\n"
                    + ChatColor.GRAY + "Abar server e dhuke /register <password> <confirmPassword> koro.");
        }

        return true;
    }
}

package com.vyntric.uhccore.commands;

import com.vyntric.uhccore.VyntricUHCCore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class WorldCommand implements CommandExecutor {

    private final VyntricUHCCore plugin;

    public WorldCommand(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("vyntric.uhc.world")) {
            sender.sendMessage(ChatColor.RED + "Only operators can use this command.");
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("border") || !args[1].equalsIgnoreCase("set")) {
            sender.sendMessage(ChatColor.RED + "Usage: /world border set <size> [seconds]");
            return true;
        }

        double size;
        try {
            size = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Size must be a number, e.g. /world border set 200 60");
            return true;
        }

        if (size <= 0) {
            sender.sendMessage(ChatColor.RED + "Size must be greater than 0.");
            return true;
        }

        int sizeInt = (int) size;

        // Optional 4th argument: cooldown/duration in seconds for a gradual shrink
        if (args.length >= 4) {
            long seconds;
            try {
                seconds = Long.parseLong(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Cooldown must be a whole number of seconds, e.g. /world border set 200 60");
                return true;
            }

            if (seconds <= 0) {
                sender.sendMessage(ChatColor.RED + "Cooldown must be greater than 0.");
                return true;
            }

            plugin.getBorderManager().setSizeGradual(size, seconds);
            sender.sendMessage(ChatColor.GREEN + "World border will reach " + sizeInt + "x" + sizeInt
                    + " gradually over " + seconds + " seconds.");
        } else {
            plugin.getBorderManager().setSizeInstant(size);
            sender.sendMessage(ChatColor.GREEN + "World border set to " + sizeInt + "x" + sizeInt + ".");
        }

        return true;
    }
}

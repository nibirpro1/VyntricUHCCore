package com.vyntric.uhccore.commands;

import com.vyntric.uhccore.VyntricUHCCore;
import com.vyntric.uhccore.auth.AuthManager;
import com.vyntric.uhccore.team.UHCTeam;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;

public class UHCCommand implements CommandExecutor {

    private final VyntricUHCCore plugin;

    public UHCCommand(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vyntric.uhc.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                plugin.getGameManager().startGame();
                sender.sendMessage(ChatColor.GREEN + "UHC game started.");
            }
            case "stop" -> {
                plugin.getGameManager().stopGame();
                sender.sendMessage(ChatColor.RED + "UHC game stopped.");
            }
            case "restart" -> {
                plugin.getGameManager().restartGame();
                sender.sendMessage(ChatColor.GREEN + "Game has been restarted and reset to WAITING.");
            }
            case "reload" -> {
                plugin.reloadConfig();
                plugin.getBorderManager().loadConfig();
                plugin.getTeamManager().loadConfig();
                plugin.getGameManager().loadConfig();
                plugin.getScoreboardManager().loadConfig();
                plugin.getTabListManager().loadConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
            }
            case "border" -> {
                sender.sendMessage(ChatColor.DARK_PURPLE + "Current border size: "
                        + (int) plugin.getBorderManager().getCurrentSize());
            }
            case "deathmatch" -> {
                sender.sendMessage(ChatColor.DARK_PURPLE + "Current phase: " + plugin.getGameManager().getPhase());
            }
            case "team" -> handleTeam(sender, args);
            case "pregen" -> handlePregen(sender, args);
            case "meetup" -> handleMeetup(sender, args);
            case "passinfo" -> handlePassInfo(sender, args);
            case "pvp" -> handlePvp(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handlePvp(CommandSender sender, String[] args) {
        if (!plugin.getGameManager().isGameActive()) {
            sender.sendMessage(ChatColor.RED + "UHC start hoyni, tai pvp manually enable/disable kora jabe na.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Byabohar: /vyntricuhc pvp <enable|disable>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "enable", "enabled", "on" -> {
                plugin.getGameManager().setPvpOverride(true);
                sender.sendMessage(ChatColor.GREEN + "PVP manually enabled.");
                broadcastToAll(ChatColor.RED + "" + ChatColor.BOLD + "PVP has been manually enabled by an operator!");
            }
            case "disable", "disabled", "off" -> {
                plugin.getGameManager().setPvpOverride(false);
                sender.sendMessage(ChatColor.GREEN + "PVP manually disabled.");
                broadcastToAll(ChatColor.YELLOW + "PVP has been manually disabled by an operator.");
            }
            case "auto", "reset" -> {
                plugin.getGameManager().clearPvpOverride();
                sender.sendMessage(ChatColor.GREEN + "PVP override cleared - back to automatic phase-based pvp.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Byabohar: /vyntricuhc pvp <enable|disable|auto>");
        }
    }

    private void broadcastToAll(String message) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }

    private void handlePassInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Byabohar: /vyntricuhc passinfo <ign>");
            return;
        }

        AuthManager.AccountInfo info = plugin.getAuthManager().getAccountInfo(args[1]);
        if (info == null) {
            sender.sendMessage(ChatColor.RED + "'" + args[1] + "' er kono record pawa jay nai.");
            return;
        }

        SimpleDateFormat format = new SimpleDateFormat("dd MMM yyyy, HH:mm");
        sender.sendMessage(ChatColor.DARK_PURPLE + "--- Account info: " + info.username + " ---");
        sender.sendMessage(ChatColor.GRAY + "Registered: "
                + (info.registered ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
        if (info.registeredAt != null) {
            sender.sendMessage(ChatColor.GRAY + "Registered at: " + ChatColor.WHITE + format.format(new Date(info.registeredAt)));
        }
        if (info.lastLogin != null) {
            sender.sendMessage(ChatColor.GRAY + "Last login: " + ChatColor.WHITE + format.format(new Date(info.lastLogin)));
        }
        sender.sendMessage(ChatColor.GRAY + "Last IP: " + ChatColor.WHITE + (info.lastIp == null ? "unknown" : info.lastIp));
        sender.sendMessage(ChatColor.GRAY + "Known IPs: " + ChatColor.WHITE
                + (info.knownIps.isEmpty() ? "none" : String.join(", ", info.knownIps)));
        sender.sendMessage(ChatColor.GRAY + "Possible alts: "
                + (info.possibleAlts.isEmpty() ? ChatColor.WHITE + "none" : ChatColor.YELLOW + String.join(", ", info.possibleAlts)));
        sender.sendMessage(ChatColor.DARK_GRAY + "Note: passwords are one-way hashed and cannot be viewed by anyone, including operators.");
    }

    private void handleMeetup(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("time")) {
            sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc meetup time <minutes>");
            return;
        }

        long minutes;
        try {
            minutes = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Minutes must be a whole number, e.g. /vyntricuhc meetup time 50");
            return;
        }

        if (minutes <= 0) {
            sender.sendMessage(ChatColor.RED + "Minutes must be greater than 0.");
            return;
        }

        plugin.getGameManager().setGraceDuration(minutes);
        sender.sendMessage(ChatColor.GREEN + "Meetup/grace period time set to " + minutes + " minute(s).");
    }

    private void handlePregen(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc pregen <start|status> [size]");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "start" -> {
                if (plugin.getChunkPreGenerator().isRunning()) {
                    sender.sendMessage(ChatColor.RED + "Chunk pre-generation is already running.");
                    return;
                }
                double size = plugin.getConfig().getDouble("pregen.size", 500);
                if (args.length >= 3) {
                    try {
                        size = Double.parseDouble(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Size must be a number, e.g. /vyntricuhc pregen start 500");
                        return;
                    }
                }
                sender.sendMessage(ChatColor.GREEN + "Starting chunk pre-generation (" + (int) size + "x" + (int) size + " blocks)...");
                plugin.getChunkPreGenerator().start(size);
            }
            case "status" -> {
                var pregen = plugin.getChunkPreGenerator();
                if (!pregen.isRunning() && pregen.getTotalChunks() == 0) {
                    sender.sendMessage(ChatColor.GRAY + "Chunk pre-generation has not been run yet.");
                } else if (pregen.isRunning()) {
                    long done = pregen.getGeneratedChunks();
                    long total = pregen.getTotalChunks();
                    int percent = total == 0 ? 0 : (int) ((done * 100) / total);
                    sender.sendMessage(ChatColor.DARK_PURPLE + "Pre-generation in progress: " + percent + "% ("
                            + done + "/" + total + " chunks)");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Pre-generation finished (" + pregen.getGeneratedChunks() + " chunks).");
                }
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown pregen subcommand. Use start or status.");
        }
    }

    private void handleTeam(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc team <create|add|list|limit> ...");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "create" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc team create <name>");
                    return;
                }
                if (!(sender instanceof Player creator)) {
                    sender.sendMessage(ChatColor.RED + "Only players can create a team (they become the owner).");
                    return;
                }
                plugin.getTeamManager().createTeam(args[2], creator);
                sender.sendMessage(ChatColor.GREEN + "Team '" + args[2] + "' created. You are the owner - "
                        + "right-click a player to invite them, right-click with a sword to kick them.");
            }
            case "accept" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Only players can accept team invites.");
                    return;
                }
                boolean success = plugin.getTeamManager().acceptInvite(player);
                sender.sendMessage(success
                        ? ChatColor.GREEN + "You joined the team!"
                        : ChatColor.RED + "You don't have a pending team invite (or it expired).");
            }
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc team add <team> <player>");
                    return;
                }
                Player target = plugin.getServer().getPlayer(args[3]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found or offline.");
                    return;
                }
                boolean success = plugin.getTeamManager().addPlayerToTeam(args[2], target);
                sender.sendMessage(success
                        ? ChatColor.GREEN + target.getName() + " added to team " + args[2]
                        : ChatColor.RED + "Failed to add player (team full or not found).");
            }
            case "list" -> {
                sender.sendMessage(ChatColor.DARK_PURPLE + "Teams:");
                for (UHCTeam team : plugin.getTeamManager().getAllTeams()) {
                    sender.sendMessage(ChatColor.GRAY + "- " + team.getName()
                            + " (" + team.getMembers().size() + " members)");
                }
            }
            case "limit" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Byabohar: /vyntricuhc team limit <1-4>");
                    sender.sendMessage(ChatColor.GRAY + "Current limit: " + plugin.getTeamManager().getMaxTeamSize()
                            + (plugin.getTeamManager().getMaxTeamSize() == 1 ? " (solo)" : ""));
                    return;
                }

                int size;
                try {
                    size = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Limit obossshoi ekta shongkha hote hobe, jemon: /vyntricuhc team limit 4");
                    return;
                }

                if (size < 1) {
                    sender.sendMessage(ChatColor.RED + "Limit kompokkhe 1 hote hobe (1 dile solo mode hoye jabe).");
                    return;
                }

                plugin.getTeamManager().setMaxTeamSize(size);
                if (size == 1) {
                    sender.sendMessage(ChatColor.GREEN + "Team limit set to 1 - eita ekhon solo event hishebe chalbe.");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Team limit set to " + size + " - squad size " + size + " hoye gelo.");
                }
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown team subcommand.");
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.DARK_PURPLE + "--- VyntricUHCCore ---");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc start");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc stop");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc restart");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc border");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc deathmatch");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc team <create|add|list|accept|limit>");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc pregen <start|status> [size]");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc meetup time <minutes>");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc passinfo <ign>");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc pvp <enable|disable|auto>");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc reload");
    }
}

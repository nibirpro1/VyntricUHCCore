package com.vyntric.uhccore.commands;

import com.vyntric.uhccore.VyntricUHCCore;
import com.vyntric.uhccore.auth.AuthManager;
import com.vyntric.uhccore.game.GamePhase;
import com.vyntric.uhccore.scenario.Scenario;
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
                if (!plugin.getConfirmationManager().confirm(sender, "uhc:start", "start the UHC game")) return true;
                plugin.getGameManager().startGame();
                sender.sendMessage(ChatColor.GREEN + "UHC game started.");
            }
            case "stop" -> {
                if (!plugin.getConfirmationManager().confirm(sender, "uhc:stop", "stop the UHC game")) return true;
                plugin.getGameManager().stopGame();
                sender.sendMessage(ChatColor.RED + "UHC game stopped.");
            }
            case "restart" -> {
                if (!plugin.getConfirmationManager().confirm(sender, "uhc:restart", "restart the game (resets everyone to WAITING)")) return true;
                plugin.getGameManager().restartGame();
                sender.sendMessage(ChatColor.GREEN + "Game has been restarted and reset to WAITING.");
            }
            case "reload" -> {
                if (!plugin.getConfirmationManager().confirm(sender, "uhc:reload", "reload the configuration")) return true;
                plugin.reloadConfig();
                plugin.getBorderManager().loadConfig();
                plugin.getTeamManager().loadConfig();
                plugin.getGameManager().loadConfig();
                plugin.getScoreboardManager().loadConfig();
                plugin.getTabListManager().loadConfig();
                plugin.getLobbyCageManager().loadConfig();
                plugin.getLobbyKitManager().loadConfig();
                plugin.getScenarioManager().loadConfig();

                GamePhase phase = plugin.getGameManager().getPhase();
                if (phase == GamePhase.WAITING || phase == GamePhase.PREGENERATING) {
                    plugin.getLobbyCageManager().releaseAll();
                    plugin.getLobbyCageManager().cageAllOnline();
                }

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
            case "scenario" -> handleScenario(sender, args);
            case "pregen" -> handlePregen(sender, args);
            case "setlobby" -> handleSetLobby(sender);
            case "meetup" -> handleMeetup(sender, args);
            case "passinfo" -> handlePassInfo(sender, args);
            case "pvp" -> handlePvp(sender, args);
            case "leaderboard", "top" -> handleLeaderboard(sender);
            case "stats" -> handleStats(sender, args);
            case "bounty" -> handleBounty(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleLeaderboard(CommandSender sender) {
        var top = plugin.getStatsManager().topByWins(10);
        if (top.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No recorded wins yet.");
            return;
        }
        sender.sendMessage(ChatColor.DARK_PURPLE + "--- Vyntric UHC Leaderboard ---");
        int rank = 1;
        for (var entry : top) {
            String name = plugin.getStatsManager().getName(entry.getKey());
            sender.sendMessage(ChatColor.GOLD + "#" + rank++ + " " + ChatColor.WHITE + name
                    + ChatColor.GRAY + " - " + entry.getValue().wins + " wins, "
                    + entry.getValue().kills + " kills, " + entry.getValue().deaths + " deaths");
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found or offline.");
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc stats <player>");
            return;
        }

        var stats = plugin.getStatsManager().get(target.getUniqueId());
        sender.sendMessage(ChatColor.DARK_PURPLE + "--- " + target.getName() + "'s stats ---");
        sender.sendMessage(ChatColor.GRAY + "Wins: " + ChatColor.WHITE + stats.wins);
        sender.sendMessage(ChatColor.GRAY + "Kills: " + ChatColor.WHITE + stats.kills);
        sender.sendMessage(ChatColor.GRAY + "Deaths: " + ChatColor.WHITE + stats.deaths);
    }

    private void handleBounty(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc bounty <add|list> ...");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc bounty add <player> <amount>");
                    return;
                }
                Player target = plugin.getServer().getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found or offline.");
                    return;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Amount must be a whole number.");
                    return;
                }
                if (amount <= 0) {
                    sender.sendMessage(ChatColor.RED + "Amount must be greater than 0.");
                    return;
                }
                plugin.getBountyManager().addBounty(target.getUniqueId(), amount);
                broadcastToAll(ChatColor.GOLD + "" + ChatColor.BOLD + sender.getName() + " placed a "
                        + plugin.getBountyManager().getBounty(target.getUniqueId()) + " bounty on "
                        + target.getName() + "! Whoever eliminates them collects it.");
            }
            case "list" -> {
                var bounties = plugin.getBountyManager().getAllBounties();
                if (bounties.isEmpty()) {
                    sender.sendMessage(ChatColor.GRAY + "No active bounties.");
                    return;
                }
                sender.sendMessage(ChatColor.DARK_PURPLE + "--- Active bounties ---");
                bounties.forEach((uuid, amount) -> {
                    String name = plugin.getServer().getOfflinePlayer(uuid).getName();
                    sender.sendMessage(ChatColor.GOLD + (name == null ? "Unknown" : name) + ChatColor.GRAY + " - " + amount);
                });
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc bounty <add|list>");
        }
    }

    private void handlePvp(CommandSender sender, String[] args) {
        if (!plugin.getGameManager().isGameActive()) {
            sender.sendMessage(ChatColor.RED + "UHC hasn't started, so pvp can't be manually enabled/disabled.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc pvp <enable|disable>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "enable", "enabled", "on" -> {
                if (!plugin.getConfirmationManager().confirm(sender, "uhc:pvp-on", "manually force PVP on")) return;
                plugin.getGameManager().setPvpOverride(true);
                sender.sendMessage(ChatColor.GREEN + "PVP manually enabled.");
                broadcastToAll(ChatColor.RED + "" + ChatColor.BOLD + "PVP has been manually enabled by an operator!");
            }
            case "disable", "disabled", "off" -> {
                if (!plugin.getConfirmationManager().confirm(sender, "uhc:pvp-off", "manually force PVP off")) return;
                plugin.getGameManager().setPvpOverride(false);
                sender.sendMessage(ChatColor.GREEN + "PVP manually disabled.");
                broadcastToAll(ChatColor.YELLOW + "PVP has been manually disabled by an operator.");
            }
            case "auto", "reset" -> {
                plugin.getGameManager().clearPvpOverride();
                sender.sendMessage(ChatColor.GREEN + "PVP override cleared - back to automatic phase-based pvp.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc pvp <enable|disable|auto>");
        }
    }

    private void broadcastToAll(String message) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }

    private void handlePassInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc passinfo <ign>");
            return;
        }

        AuthManager.AccountInfo info = plugin.getAuthManager().getAccountInfo(args[1]);
        if (info == null) {
            sender.sendMessage(ChatColor.RED + "No record found for '" + args[1] + "'.");
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
                if (!plugin.getConfirmationManager().confirm(sender, "uhc:pregen-start",
                        "start chunk pre-generation (" + (int) size + "x" + (int) size + " blocks)")) return;
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

    private void handleSetLobby(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only in-game players can set the lobby cage center - "
                    + "stand where you want it first, then run this command.");
            return;
        }

        plugin.getLobbyCageManager().setCenter(player.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Lobby cage center set to your location.");

        GamePhase phase = plugin.getGameManager().getPhase();
        if (phase == GamePhase.WAITING || phase == GamePhase.PREGENERATING) {
            plugin.getLobbyCageManager().releaseAll();
            plugin.getLobbyCageManager().cageAllOnline();
            sender.sendMessage(ChatColor.GRAY + "Everyone currently waiting has been moved into their box.");
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
                    sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc team limit <1-4>");
                    sender.sendMessage(ChatColor.GRAY + "Current limit: " + plugin.getTeamManager().getMaxTeamSize()
                            + (plugin.getTeamManager().getMaxTeamSize() == 1 ? " (solo)" : ""));
                    return;
                }

                int size;
                try {
                    size = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Limit must be a whole number, e.g. /vyntricuhc team limit 4");
                    return;
                }

                if (size < 1) {
                    sender.sendMessage(ChatColor.RED + "Limit must be at least 1 (setting it to 1 makes it solo mode).");
                    return;
                }

                if (!plugin.getConfirmationManager().confirm(sender, "uhc:team-limit",
                        "change the team size limit to " + size)) return;

                plugin.getTeamManager().setMaxTeamSize(size);
                if (size == 1) {
                    sender.sendMessage(ChatColor.GREEN + "Team limit set to 1 - this will now run as a solo event.");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Team limit set to " + size + " - squad size is now " + size + ".");
                }
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown team subcommand.");
        }
    }

    private void handleScenario(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc scenario <list|enable|disable> [name]");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "list" -> {
                sender.sendMessage(ChatColor.DARK_PURPLE + "--- Scenarios ---");
                for (Scenario scenario : Scenario.values()) {
                    boolean on = plugin.getScenarioManager().isEnabled(scenario);
                    sender.sendMessage((on ? ChatColor.GREEN + "[ON] " : ChatColor.GRAY + "[off] ")
                            + ChatColor.WHITE + scenario.getDisplayName()
                            + ChatColor.GRAY + " - " + scenario.getDescription());
                }
            }
            case "enable" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc scenario enable <name>");
                    return;
                }
                Scenario scenario = Scenario.fromString(args[2]);
                if (scenario == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown scenario. Use /vyntricuhc scenario list to see valid names.");
                    return;
                }
                if (!plugin.getScenarioManager().enable(scenario)) {
                    sender.sendMessage(ChatColor.YELLOW + scenario.getDisplayName() + " is already enabled.");
                    return;
                }
                broadcastToAll(ChatColor.DARK_PURPLE + "Scenario " + ChatColor.WHITE + scenario.getDisplayName()
                        + ChatColor.DARK_PURPLE + " has been enabled!");
            }
            case "disable" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc scenario disable <name>");
                    return;
                }
                Scenario scenario = Scenario.fromString(args[2]);
                if (scenario == null) {
                    sender.sendMessage(ChatColor.RED + "Unknown scenario. Use /vyntricuhc scenario list to see valid names.");
                    return;
                }
                if (!plugin.getScenarioManager().disable(scenario)) {
                    sender.sendMessage(ChatColor.YELLOW + scenario.getDisplayName() + " is already disabled.");
                    return;
                }
                broadcastToAll(ChatColor.DARK_PURPLE + "Scenario " + ChatColor.WHITE + scenario.getDisplayName()
                        + ChatColor.DARK_PURPLE + " has been disabled.");
            }
            default -> sender.sendMessage(ChatColor.RED + "Unknown scenario subcommand. Use list, enable, or disable.");
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
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc scenario <list|enable|disable> [name]");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc pregen <start|status> [size]");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc setlobby");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc meetup time <minutes>");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc passinfo <ign>");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc pvp <enable|disable|auto>");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc leaderboard");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc stats [player]");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc bounty <add|list>");
        sender.sendMessage(ChatColor.GRAY + "/vyntricuhc reload");
    }
}

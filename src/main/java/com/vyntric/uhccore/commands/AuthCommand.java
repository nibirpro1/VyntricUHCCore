package com.vyntric.uhccore.commands;

import com.vyntric.uhccore.VyntricUHCCore;
import com.vyntric.uhccore.auth.AuthManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AuthCommand implements CommandExecutor {

    private final VyntricUHCCore plugin;
    private final AuthManager auth;

    public AuthCommand(VyntricUHCCore plugin) {
        this.plugin = plugin;
        this.auth = plugin.getAuthManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only in-game players can use this command.");
            return true;
        }

        if (!auth.isAuthEnabled()) {
            player.sendMessage(ChatColor.RED + "Login system is currently disabled.");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "register" -> handleRegister(player, args);
            case "login" -> handleLogin(player, args);
            case "changepass" -> handleChangePass(player, args);
            default -> { /* not one of ours */ }
        }
        return true;
    }

    private void handleRegister(Player player, String[] args) {
        if (auth.isRegistered(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Tumi already register kora achho. /login <password> diye login koro.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Byabohar: /register <password> <confirmPassword>");
            return;
        }

        String password = args[0];
        String confirm = args[1];

        if (!password.equals(confirm)) {
            player.sendMessage(ChatColor.RED + "Password ar confirm password mile nai! Abar try koro.");
            return;
        }

        if (password.length() < auth.getMinPasswordLength()) {
            player.sendMessage(ChatColor.RED + "Password kom pokkhe " + auth.getMinPasswordLength()
                    + " character howa lagbe.");
            return;
        }

        auth.register(player, password);
        player.sendMessage(ChatColor.GREEN + "Register shofol hoyeche! Tumi ekhon login kora obosthay achho.");
    }

    private void handleLogin(Player player, String[] args) {
        if (!auth.isRegistered(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Tumi register kora nai. /register <password> <confirmPassword> use koro.");
            return;
        }

        if (auth.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Tumi already login kora achho.");
            return;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Byabohar: /login <password>");
            return;
        }

        boolean success = auth.login(player, args[0]);
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Login shofol! Server e swagotom, " + player.getName() + "!");
        } else {
            player.sendMessage(ChatColor.RED + "Password vul! Abar try koro. Password bhule gele operator "
                    + "ke bolo /resetpass diye reset kore dite.");
        }
    }

    private void handleChangePass(Player player, String[] args) {
        if (!auth.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Age login koro, tarpor password change korte parbe.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Byabohar: /changepass <currentPassword> <newPassword>");
            return;
        }

        String currentPassword = args[0];
        String newPassword = args[1];

        AuthManager.ChangeResult result = auth.changePassword(player, currentPassword, newPassword);
        switch (result) {
            case SUCCESS -> player.sendMessage(ChatColor.GREEN + "Password shofol vabe change hoyeche!");
            case WRONG_CURRENT_PASSWORD -> player.sendMessage(ChatColor.RED + "Current password ta vul dise.");
            case NEW_PASSWORD_TOO_SHORT -> player.sendMessage(ChatColor.RED + "Notun password kom pokkhe "
                    + auth.getMinPasswordLength() + " character howa lagbe.");
            case NOT_REGISTERED -> player.sendMessage(ChatColor.RED + "Tumi register kora nai.");
        }
    }
}

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
            player.sendMessage(ChatColor.RED + "You are already registered. Use /login <password> to log in.");
            return;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /register <password> [confirmPassword]");
            return;
        }

        String password = args[0];

        // Confirm-password is optional - if given, it must match; if not given, we just
        // register with the single password. This avoids players getting stuck frozen just
        // because they typed "/register mypassword" without a second word.
        if (args.length >= 2) {
            String confirm = args[1];
            if (!password.equals(confirm)) {
                player.sendMessage(ChatColor.RED + "Password and confirm password don't match! Try again.");
                return;
            }
        }

        if (password.length() < auth.getMinPasswordLength()) {
            player.sendMessage(ChatColor.RED + "Password must be at least " + auth.getMinPasswordLength()
                    + " characters long.");
            return;
        }

        auth.register(player, password);
        player.sendMessage(ChatColor.GREEN + "Registration successful! You are now logged in.");
    }

    private void handleLogin(Player player, String[] args) {
        if (!auth.isRegistered(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You are not registered yet. Use /register <password>.");
            return;
        }

        if (auth.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "You are already logged in.");
            return;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /login <password>");
            return;
        }

        boolean success = auth.login(player, args[0]);
        if (success) {
            player.sendMessage(ChatColor.GREEN + "Login successful! Welcome to the server, " + player.getName() + "!");
        } else {
            player.sendMessage(ChatColor.RED + "Wrong password! Try again. If you forgot your password, "
                    + "ask an operator to reset it with /resetpass.");
        }
    }

    private void handleChangePass(Player player, String[] args) {
        if (!auth.isLoggedIn(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "You must log in before you can change your password.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /changepass <currentPassword> <newPassword>");
            return;
        }

        String currentPassword = args[0];
        String newPassword = args[1];

        AuthManager.ChangeResult result = auth.changePassword(player, currentPassword, newPassword);
        switch (result) {
            case SUCCESS -> player.sendMessage(ChatColor.GREEN + "Password changed successfully!");
            case WRONG_CURRENT_PASSWORD -> player.sendMessage(ChatColor.RED + "Your current password is incorrect.");
            case NEW_PASSWORD_TOO_SHORT -> player.sendMessage(ChatColor.RED + "New password must be at least "
                    + auth.getMinPasswordLength() + " characters long.");
            case NOT_REGISTERED -> player.sendMessage(ChatColor.RED + "You are not registered.");
        }
    }
}

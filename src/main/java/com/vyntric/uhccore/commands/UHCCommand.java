private void handlePassInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /vyntricuhc passinfo <ign>");
            return;
        }

        com.vyntric.uhccore.auth.AuthManager.AccountInfo info = plugin.getAuthManager().getAccountInfo(args[1]);
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

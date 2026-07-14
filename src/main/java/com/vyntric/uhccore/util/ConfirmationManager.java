package com.vyntric.uhccore.util;

import com.vyntric.uhccore.VyntricUHCCore;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.Map;

/**
 * Requires any risky command to be run twice within a short window before it actually
 * executes, so an operator (or player) doesn't accidentally nuke the game state with a
 * typo or a misclick. First call sends a warning and returns false (don't run the action
 * yet); running the exact same command again within the confirm window returns true (go
 * ahead) and clears the pending state.
 *
 * Keyed by sender name + an action key (e.g. "vyntricuhc:stop") so confirming one command
 * doesn't accidentally confirm a different one.
 */
public class ConfirmationManager {

    private final VyntricUHCCore plugin;
    private final Map<String, Long> pending = new HashMap<>();

    public ConfirmationManager(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    /**
     * @param sender      who ran the command
     * @param actionKey   unique id for this specific action, e.g. "uhc:stop" or "world:border-set"
     * @param description human-readable description shown in the confirmation prompt, e.g. "stop the UHC game"
     * @return true if this call is the confirmation (the action should run now), false if a
     *         confirmation prompt was just sent and the caller should stop here.
     */
    public boolean confirm(CommandSender sender, String actionKey, String description) {
        long windowMillis = plugin.getConfig().getLong("confirmation.window-seconds", 15) * 1000L;
        if (!plugin.getConfig().getBoolean("confirmation.enabled", true)) {
            return true;
        }

        String key = sender.getName() + ":" + actionKey;
        long now = System.currentTimeMillis();
        Long requestedAt = pending.get(key);

        if (requestedAt != null && (now - requestedAt) <= windowMillis) {
            pending.remove(key);
            return true;
        }

        pending.put(key, now);
        long seconds = windowMillis / 1000L;
        sender.sendMessage(ChatColor.YELLOW + "⚠ Are you sure you want to " + description + "?");
        sender.sendMessage(ChatColor.GRAY + "Run the same command again within " + seconds
                + " seconds to confirm.");
        return false;
    }
}

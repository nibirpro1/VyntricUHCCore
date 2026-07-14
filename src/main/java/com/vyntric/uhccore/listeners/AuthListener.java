package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUHCCore;
import com.vyntric.uhccore.auth.AuthManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Locale;

/**
 * Freezes/protects players that have not logged in (or registered) yet, and kicks them if
 * they take too long to do so. Everything here is skipped entirely if auth.enabled is false
 * in config.yml.
 */
public class AuthListener implements Listener {

    // Commands a not-yet-logged-in player is allowed to use.
    private static final List<String> ALLOWED_COMMANDS = List.of("/login", "/register", "/l", "/reg");

    private final VyntricUHCCore plugin;
    private final AuthManager auth;

    public AuthListener(VyntricUHCCore plugin) {
        this.plugin = plugin;
        this.auth = plugin.getAuthManager();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!auth.isAuthEnabled()) return;
        Player player = event.getPlayer();
        auth.markLoggedOut(player.getUniqueId());

        checkAlts(player);

        String prefix = plugin.getMessage("prefix");
        if (auth.isRegistered(player.getUniqueId())) {
            player.sendMessage(prefix + ChatColor.YELLOW + "Server e login kora nai! Type: "
                    + ChatColor.DARK_PURPLE + "/login <password>");
        } else {
            player.sendMessage(prefix + ChatColor.YELLOW + "Tumi ekhono register koro nai! Type: "
                    + ChatColor.DARK_PURPLE + "/register <password> <confirmPassword>");
        }

        int timeout = auth.getLoginTimeoutSeconds();
        if (timeout > 0) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !auth.isLoggedIn(player.getUniqueId())) {
                    player.kickPlayer(ChatColor.RED + "Time out! Tumi " + timeout
                            + " second er moddhe login/register korte parO nai.");
                }
            }, timeout * 20L);
        }
    }

    private void checkAlts(Player player) {
        if (!plugin.getConfig().getBoolean("auth.alt-detection.enabled", true)) return;

        List<String> alts = auth.recordJoinAndCheckAlts(player);
        if (alts.isEmpty()) return;

        String prefix = plugin.getMessage("prefix");
        String altList = String.join(", ", alts);
        String alertMessage = prefix + ChatColor.YELLOW + player.getName() + ChatColor.GRAY
                + " may be an alt of " + ChatColor.YELLOW + altList;

        for (Player staff : plugin.getServer().getOnlinePlayers()) {
            if (staff.hasPermission("vyntric.uhc.admin")) {
                staff.sendMessage(alertMessage);
            }
        }
        plugin.getLogger().info(ChatColor.stripColor(player.getName() + " may be an alt of " + altList));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        auth.markLoggedOut(event.getPlayer().getUniqueId());
    }

    private boolean isLocked(Player player) {
        return auth.isAuthEnabled() && !auth.isLoggedIn(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (!isLocked(event.getPlayer())) return;
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom().setDirection(event.getTo().getDirection()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (isLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (isLocked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(PlayerPickupItemEvent event) {
        if (isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (isLocked(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Age login/register koro, tarpor chat korte parbe.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isLocked(event.getPlayer())) return;

        String lower = event.getMessage().toLowerCase(Locale.ROOT);
        String base = lower.split(" ")[0];
        if (!ALLOWED_COMMANDS.contains(base)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED
                    + "Age login/register korte hobe. Onno kono command use kora jabe na.");
        }
    }
}

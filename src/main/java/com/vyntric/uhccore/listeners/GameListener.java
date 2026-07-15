package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUHCCore;
import com.vyntric.uhccore.game.GamePhase;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GameListener implements Listener {

    private final VyntricUHCCore plugin;

    public GameListener(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    /**
     * True for anyone who should be exempt from the pre-game build/PVP lock below -
     * operators and anyone with the admin permission can break/place/fight freely even
     * before /vyntricuhc start, everyone else can't.
     */
    private boolean hasLockBypass(Player player) {
        return player.isOp() || player.hasPermission("vyntric.uhc.admin");
    }

    private boolean isPreGame() {
        GamePhase phase = plugin.getGameManager().getPhase();
        return phase == GamePhase.WAITING || phase == GamePhase.PREGENERATING;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPreGameBreak(BlockBreakEvent event) {
        if (!isPreGame()) return;
        if (hasLockBypass(event.getPlayer())) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "You can't break blocks before the game starts.");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPreGamePlace(BlockPlaceEvent event) {
        if (!isPreGame()) return;
        if (hasLockBypass(event.getPlayer())) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "You can't place blocks before the game starts.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event);
        if (attacker == null) return;

        // Operators/admins can always fight, including before the game starts.
        if (hasLockBypass(attacker)) return;

        // Block PVP during grace period / waiting
        if (!plugin.getGameManager().isPvpEnabled()) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "PVP is currently disabled.");
            return;
        }

        // Once deathmatch starts it's every player for themselves - friendly fire is always
        // allowed here regardless of the teams.friendly-fire config, so teammates can fight too.
        if (plugin.getGameManager().getPhase() == GamePhase.DEATHMATCH) {
            return;
        }

        // Block friendly fire if teams don't allow it
        if (!plugin.getTeamManager().isFriendlyFire() && plugin.getTeamManager().isSameTeam(attacker, victim)) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "Friendly fire is disabled.");
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.getTeamManager().markEliminated(player);
        plugin.getStatsManager().addDeath(player.getUniqueId(), player.getName());

        Player killer = player.getKiller();
        String killFeedMessage;
        if (killer != null) {
            String weapon = describeWeapon(killer);
            killFeedMessage = ChatColor.RED + player.getName() + ChatColor.GRAY + " was slain by "
                    + ChatColor.RED + killer.getName() + ChatColor.GRAY + weapon;

            plugin.getStatsManager().addKill(killer.getUniqueId(), killer.getName());

            int bounty = plugin.getBountyManager().claimBounty(player.getUniqueId());
            if (bounty > 0) {
                killFeedMessage += ChatColor.GOLD + " (+" + bounty + " bounty!)";
            }
        } else {
            killFeedMessage = ChatColor.RED + player.getName() + ChatColor.GRAY + " " + describeDeathCause(player);
        }

        String msg = plugin.getMessage("player-eliminated").replace("{player}", player.getName());
        plugin.getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getMessage("prefix") + msg));
        plugin.getServer().broadcastMessage(plugin.getMessage("prefix") + killFeedMessage);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.setGameMode(GameMode.SPECTATOR);
        });
    }

    private String describeWeapon(Player killer) {
        var item = killer.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return "";
        String name = item.getType().name().toLowerCase().replace('_', ' ');
        return " using a " + name;
    }

    private String describeDeathCause(Player player) {
        var cause = player.getLastDamageCause();
        if (cause == null) return "died.";
        return switch (cause.getCause()) {
            case FALL -> "fell to their death.";
            case LAVA -> "died in lava.";
            case FIRE, FIRE_TICK -> "burned to death.";
            case DROWNING -> "drowned.";
            case STARVATION -> "starved to death.";
            case VOID -> "fell into the void.";
            default -> "died.";
        };
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getScoreboardManager().updateFor(event.getPlayer());
        // Refresh everyone's tab list so the online/alive counts stay accurate for the new join too.
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getTabListManager().updateAll());

        if (isPreGame()) {
            plugin.getLobbyKitManager().giveKit(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Keep team membership, player may reconnect; scoreboard clears automatically on quit.
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getTabListManager().updateAll());
    }
}

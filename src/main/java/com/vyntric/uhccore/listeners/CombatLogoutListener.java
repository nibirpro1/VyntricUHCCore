package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUHCCore;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * If a player leaves the server anywhere/anytime after /vyntricuhc start (grace period, pvp,
 * or deathmatch) they turn into a zombie that carries all of their gear/loot, so other
 * players can still hunt it down. This is intentional - it discourages "combat logging" to
 * dodge a fight.
 *
 * If they rejoin before the zombie is killed, the zombie disappears and they respawn in its
 * place with their gear restored, instead of staying eliminated.
 */
public class CombatLogoutListener implements Listener {

    private final VyntricUHCCore plugin;
    private final Map<UUID, Zombie> activeZombies = new HashMap<>();

    public CombatLogoutListener(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("combat-logout.enabled", true)) return;
        if (!plugin.getGameManager().isGameActive()) return;

        Player player = event.getPlayer();

        // Don't zombify spectators/creative players (already eliminated, or staff testing).
        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) return;
        if (plugin.getTeamManager().isEliminated(player)) return;

        spawnLootZombie(player);
        plugin.getTeamManager().markEliminated(player);

        String message = plugin.getMessage("prefix") + ChatColor.RED + player.getName()
                + ChatColor.GRAY + " left mid-game and turned into a zombie holding their loot!";
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            online.sendMessage(message);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Zombie zombie = activeZombies.remove(player.getUniqueId());
        if (zombie == null) return;

        // Give back whatever gear is still on the zombie (nothing if it was already killed
        // and looted by someone else in the meantime).
        if (zombie.isValid()) {
            EntityEquipment equipment = zombie.getEquipment();
            if (equipment != null) {
                PlayerInventory inv = player.getInventory();
                inv.setHelmet(equipment.getHelmet());
                inv.setChestplate(equipment.getChestplate());
                inv.setLeggings(equipment.getLeggings());
                inv.setBoots(equipment.getBoots());
                inv.setItemInMainHand(equipment.getItemInMainHand());
                inv.setItemInOffHand(equipment.getItemInOffHand());
            }

            player.teleport(zombie.getLocation());
            zombie.remove();
        }

        plugin.getTeamManager().unmarkEliminated(player);

        String message = plugin.getMessage("prefix") + ChatColor.GREEN + player.getName()
                + ChatColor.GRAY + " rejoined and is back in the game!";
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            online.sendMessage(message);
        }
    }

    @EventHandler
    public void onZombieDeath(EntityDeathEvent event) {
        activeZombies.values().removeIf(z -> z.getUniqueId().equals(event.getEntity().getUniqueId()));
    }

    private void spawnLootZombie(Player player) {
        Location loc = player.getLocation();
        if (loc.getWorld() == null) return;

        PlayerInventory inv = player.getInventory();

        // Grab their gear before we touch the inventory at all.
        ItemStack helmet = inv.getHelmet();
        ItemStack chestplate = inv.getChestplate();
        ItemStack leggings = inv.getLeggings();
        ItemStack boots = inv.getBoots();
        ItemStack mainHand = inv.getItemInMainHand();
        ItemStack offHand = inv.getItemInOffHand();
        ItemStack[] storageContents = inv.getStorageContents().clone();

        Zombie zombie = loc.getWorld().spawn(loc, Zombie.class);
        zombie.setCustomName(ChatColor.RED + player.getName());
        zombie.setCustomNameVisible(true);
        zombie.setRemoveWhenFarAway(false);
        zombie.setPersistent(true);
        zombie.setShouldBurnInDay(false);

        activeZombies.put(player.getUniqueId(), zombie);

        // Match the player's health at the moment they left (clamped to the zombie's max health).
        double targetHealth = Math.max(1.0, Math.min(player.getHealth(), zombie.getMaxHealth()));
        zombie.setHealth(targetHealth);

        EntityEquipment equipment = zombie.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(helmet);
            equipment.setChestplate(chestplate);
            equipment.setLeggings(leggings);
            equipment.setBoots(boots);
            equipment.setItemInMainHand(mainHand);
            equipment.setItemInOffHand(offHand);

            // 100% drop chance so all of it actually drops when the zombie is killed.
            equipment.setHelmetDropChance(1.0f);
            equipment.setChestplateDropChance(1.0f);
            equipment.setLeggingsDropChance(1.0f);
            equipment.setBootsDropChance(1.0f);
            equipment.setItemInMainHandDropChance(1.0f);
            equipment.setItemInOffHandDropChance(1.0f);
        }

        // The zombie model only has 6 equipment slots, so the rest of the inventory (the
        // remaining loot) is dropped on the ground right where they logged out.
        for (ItemStack item : storageContents) {
            if (item != null && item.getType() != Material.AIR) {
                loc.getWorld().dropItemNaturally(loc, item);
            }
        }

        // Clear the player's actual inventory so it isn't duplicated if they rejoin later.
        inv.clear();
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);
        inv.setItemInOffHand(null);
    }
}

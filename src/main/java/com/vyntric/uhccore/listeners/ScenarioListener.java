package com.vyntric.uhccore.listeners;

import com.vyntric.uhccore.VyntricUHCCore;
import com.vyntric.uhccore.scenario.Scenario;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gameplay effects for each {@link Scenario}. Every handler is a no-op unless the round is
 * actually active (grace period, pvp, or deathmatch - see GameManager#isGameActive) and the
 * relevant scenario is enabled via /vyntricuhc scenario enable.
 */
public class ScenarioListener implements Listener {

    // Safety cap on how many connected log blocks a single Timber break will fell, so a
    // griefed/mega tree structure can't freeze the server chewing through thousands of blocks.
    private static final int MAX_TIMBER_BLOCKS = 384;

    private final VyntricUHCCore plugin;

    public ScenarioListener(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    private boolean active(Scenario scenario) {
        return plugin.getGameManager().isGameActive() && plugin.getScenarioManager().isEnabled(scenario);
    }

    // ----- Timber -----------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onTimberBreak(BlockBreakEvent event) {
        if (!active(Scenario.TIMBER)) return;

        Block origin = event.getBlock();
        if (!isLog(origin.getType())) return;
        // Only triggers from the base of the tree, not from a log broken partway up the trunk.
        if (isLog(origin.getRelative(0, -1, 0).getType())) return;

        Set<Block> toBreak = new LinkedHashSet<>();
        Set<Block> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        Material logType = origin.getType();
        while (!queue.isEmpty() && toBreak.size() < MAX_TIMBER_BLOCKS) {
            Block current = queue.poll();
            toBreak.add(current);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 2; dy++) { // trees grow upward more than sideways
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block rel = current.getRelative(dx, dy, dz);
                        if (visited.contains(rel)) continue;
                        if (rel.getType() == logType) {
                            visited.add(rel);
                            queue.add(rel);
                        }
                    }
                }
            }
        }

        if (toBreak.size() <= 1) return; // just one log - let it break normally

        event.setCancelled(true);
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        for (Block block : toBreak) {
            block.breakNaturally(tool);
        }
    }

    private boolean isLog(Material material) {
        String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_STEM") || name.endsWith("_HYPHAE");
    }

    // ----- Double Ores & Cutclean (both act on what a block drops) ---------------------

    @EventHandler(ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        if (!plugin.getGameManager().isGameActive()) return;

        Material blockType = event.getBlockState().getType();
        boolean cutclean = plugin.getScenarioManager().isEnabled(Scenario.CUTCLEAN);
        boolean doubleOres = plugin.getScenarioManager().isEnabled(Scenario.DOUBLE_ORES);
        if (!cutclean && !doubleOres) return;

        if (cutclean && isLog(blockType)) {
            Material planks = logToPlanks(blockType);
            if (planks != null) {
                for (Item item : event.getItems()) {
                    ItemStack stack = item.getItemStack();
                    if (stack.getType() == blockType) {
                        item.setItemStack(new ItemStack(planks, stack.getAmount() * 4));
                    }
                }
            }
        }

        if (doubleOres && isOre(blockType)) {
            for (Item item : event.getItems()) {
                ItemStack stack = item.getItemStack();
                stack.setAmount(stack.getAmount() * 2);
            }
        }
    }

    private boolean isOre(Material material) {
        return material.name().endsWith("_ORE");
    }

    private Material logToPlanks(Material log) {
        String name = log.name();
        String base;
        if (name.endsWith("_LOG")) base = name.substring(0, name.length() - "_LOG".length());
        else if (name.endsWith("_WOOD")) base = name.substring(0, name.length() - "_WOOD".length());
        else if (name.endsWith("_STEM")) base = name.substring(0, name.length() - "_STEM".length());
        else if (name.endsWith("_HYPHAE")) base = name.substring(0, name.length() - "_HYPHAE".length());
        else return null;
        return Material.matchMaterial(base + "_PLANKS");
    }

    // ----- No Trading ---------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!active(Scenario.NO_TRADING)) return;
        if (!(event.getRightClicked() instanceof AbstractVillager)) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage(ChatColor.RED + "Trading is disabled - the No Trading scenario is active.");
    }

    // ----- Barebones (no natural regen) ----------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!active(Scenario.BAREBONES)) return;

        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
        }
    }
}

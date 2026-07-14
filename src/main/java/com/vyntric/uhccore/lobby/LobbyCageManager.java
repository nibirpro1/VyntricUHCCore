package com.vyntric.uhccore.lobby;

import com.vyntric.uhccore.VyntricUHCCore;
import com.vyntric.uhccore.game.GamePhase;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Puts every player into their own sealed glass box the moment they join, for as long as
 * the game is in the pre-game lobby (PREGENERATING/WAITING) - the classic UHC "waiting
 * room" look, so everyone loads in together instead of running around loose before the
 * game has even started.
 *
 * Boxes are arranged in a simple grid around a center point set with /vyntricuhc setlobby.
 * The instant /vyntricuhc start runs, every box is cleared and everyone is released at the
 * same time, right before RandomSpreadTeleporter scatters them across the map.
 *
 * Does nothing if lobby-cage.enabled is false in config.yml.
 */
public class LobbyCageManager implements Listener {

    private final VyntricUHCCore plugin;

    private boolean enabled;
    private String worldName;
    private int centerX, centerY, centerZ;
    private int size;
    private int height;
    private int spacing;
    private int perRow;
    private Material material;
    private boolean clearFloorOnRelease;

    // Slot index is sticky per online player, so rebuilding (e.g. after /vyntricuhc reload)
    // puts everyone back in the exact box they already had.
    private final Map<UUID, Integer> playerSlots = new HashMap<>();
    private final Set<Integer> usedSlots = new HashSet<>();

    // Every block that currently belongs to an active cage, so BlockBreakEvent can cheaply
    // check "is this glass part of a lobby cage?" without scanning every box.
    private final Set<Location> cageBlocks = new HashSet<>();
    // Floor blocks are tracked separately since they may survive release - see
    // lobby-cage.clear-floor-on-release.
    private final Set<Location> floorBlocks = new HashSet<>();

    public LobbyCageManager(VyntricUHCCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        var cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("lobby-cage.enabled", true);
        this.worldName = cfg.getString("lobby-cage.world", "world");
        this.centerX = cfg.getInt("lobby-cage.center-x", 0);
        this.centerY = cfg.getInt("lobby-cage.center-y", 100);
        this.centerZ = cfg.getInt("lobby-cage.center-z", 0);
        this.size = Math.max(3, cfg.getInt("lobby-cage.size", 3) | 1); // always odd, minimum 3
        this.height = Math.max(2, cfg.getInt("lobby-cage.height", 3));
        this.spacing = Math.max(size + 2, cfg.getInt("lobby-cage.spacing", 6));
        this.perRow = Math.max(1, cfg.getInt("lobby-cage.per-row", 5));
        this.clearFloorOnRelease = cfg.getBoolean("lobby-cage.clear-floor-on-release", true);

        Material parsed = Material.matchMaterial(cfg.getString("lobby-cage.material", "GLASS"));
        this.material = parsed != null ? parsed : Material.GLASS;
    }

    /**
     * Persists a new lobby center (used by /vyntricuhc setlobby) and turns the feature on.
     */
    public void setCenter(Location location) {
        this.worldName = location.getWorld().getName();
        this.centerX = location.getBlockX();
        this.centerY = location.getBlockY();
        this.centerZ = location.getBlockZ();
        this.enabled = true;

        var cfg = plugin.getConfig();
        cfg.set("lobby-cage.world", worldName);
        cfg.set("lobby-cage.center-x", centerX);
        cfg.set("lobby-cage.center-y", centerY);
        cfg.set("lobby-cage.center-z", centerZ);
        cfg.set("lobby-cage.enabled", true);
        plugin.saveConfig();
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean isPreGameLobby() {
        GamePhase phase = plugin.getGameManager().getPhase();
        return phase == GamePhase.WAITING || phase == GamePhase.PREGENERATING;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled || !isPreGameLobby()) return;
        cage(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Integer slot = playerSlots.remove(event.getPlayer().getUniqueId());
        if (slot != null) {
            usedSlots.remove(slot);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (cageBlocks.contains(loc) || floorBlocks.contains(loc)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        // No building inside the waiting lobby - keeps players from stacking blocks to
        // climb out of their box before release.
        if (isPreGameLobby() && playerSlots.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Builds (or rebuilds) a glass box for this player and teleports them into it. Safe to
     * call again for an already-caged player (e.g. after /vyntricuhc reload) - it reuses
     * their existing slot instead of handing out a new one.
     */
    public void cage(Player player) {
        if (!enabled) return;
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Lobby cage world '" + worldName + "' not found - skipping cage for "
                    + player.getName() + ". Set a location with /vyntricuhc setlobby.");
            return;
        }

        int slot = playerSlots.computeIfAbsent(player.getUniqueId(), id -> nextFreeSlot());
        usedSlots.add(slot);

        int r = (size - 1) / 2;
        int row = slot / perRow;
        int col = slot % perRow;
        int cx = centerX + (col - (perRow - 1) / 2) * spacing;
        int cz = centerZ + row * spacing;
        int cy = centerY;

        buildBox(world, cx, cy, cz, r);
        player.teleport(new Location(world, cx + 0.5, cy, cz + 0.5));
    }

    private int nextFreeSlot() {
        int i = 0;
        while (usedSlots.contains(i)) i++;
        return i;
    }

    private void buildBox(World world, int cx, int cy, int cz, int r) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                // Clear the interior first in case it overlaps existing terrain.
                for (int y = cy; y < cy + height; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                }

                Block floor = world.getBlockAt(x, cy - 1, z);
                floor.setType(material);
                floorBlocks.add(floor.getLocation());

                Block ceiling = world.getBlockAt(x, cy + height, z);
                ceiling.setType(material);
                cageBlocks.add(ceiling.getLocation());

                boolean edge = (x == cx - r || x == cx + r || z == cz - r || z == cz + r);
                if (edge) {
                    for (int y = cy; y < cy + height; y++) {
                        Block wall = world.getBlockAt(x, y, z);
                        wall.setType(material);
                        cageBlocks.add(wall.getLocation());
                    }
                }
            }
        }
    }

    /**
     * Clears every active cage (walls/ceiling always, floor only if configured) and forgets
     * all slot assignments. Called the instant /vyntricuhc start runs, before players are
     * spread across the map.
     */
    public void releaseAll() {
        for (Location loc : cageBlocks) {
            loc.getBlock().setType(Material.AIR);
        }
        cageBlocks.clear();

        if (clearFloorOnRelease) {
            for (Location loc : floorBlocks) {
                loc.getBlock().setType(Material.AIR);
            }
            floorBlocks.clear();
        }

        playerSlots.clear();
        usedSlots.clear();
    }

    /**
     * Cages every currently-online player at once. Used after /vyntricuhc restart puts the
     * game back into WAITING for a new round, and after /vyntricuhc setlobby / reload while
     * the lobby is still active.
     */
    public void cageAllOnline() {
        if (!enabled) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            cage(player);
        }
    }
}

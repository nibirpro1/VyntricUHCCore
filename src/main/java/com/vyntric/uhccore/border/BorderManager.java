package com.vyntric.uhccore.border;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class BorderManager {

    private final Plugin plugin;
    private World world;
    private double startSize;
    private double finalSize;
    private long shrinkDurationSeconds;
    private double centerX;
    private double centerZ;
    private double damagePerBlock;
    private int warningDistance;

    public BorderManager(Plugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        FileConfiguration cfg = plugin.getConfig();
        String worldName = cfg.getString("border.world", "world");
        this.world = Bukkit.getWorld(worldName);
        this.centerX = cfg.getDouble("border.center-x", 0);
        this.centerZ = cfg.getDouble("border.center-z", 0);
        this.startSize = cfg.getDouble("border.start-size", 3000);
        this.finalSize = cfg.getDouble("border.final-size", 300);
        this.shrinkDurationSeconds = cfg.getLong("border.shrink-duration-minutes", 45) * 60L;
        this.damagePerBlock = cfg.getDouble("border.damage-per-block", 0.2);
        this.warningDistance = cfg.getInt("border.warning-distance", 5);
    }

    /**
     * Resets the border to its starting size and applies static settings.
     */
    public void initializeBorder() {
        if (world == null) {
            plugin.getLogger().warning("Border world not found! Check config.yml -> border.world");
            return;
        }
        WorldBorder border = world.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(startSize);
        border.setDamageAmount(damagePerBlock);
        border.setWarningDistance(warningDistance);
    }

    /**
     * Starts shrinking the border from startSize to finalSize over the configured duration.
     */
    public void startShrinking() {
        if (world == null) return;
        WorldBorder border = world.getWorldBorder();
        border.setSize(startSize);
        border.setSize(finalSize, shrinkDurationSeconds);
    }

    /**
     * Instantly sets the border for deathmatch, centered on the current center point.
     */
    public void setDeathmatchBorder(double size, long transitionSeconds) {
        if (world == null) return;
        WorldBorder border = world.getWorldBorder();
        border.setSize(size, transitionSeconds);
    }

    /**
     * Instantly sets the border to a given size (no transition), keeping the configured center.
     * Used by /world border set <size> when no cooldown/duration is given.
     */
    public void setSizeInstant(double size) {
        if (world == null) return;
        WorldBorder border = world.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size);
    }

    /**
     * Gradually resizes the border to the given size over transitionSeconds, keeping the configured center.
     * Used by /world border set <size> <seconds>.
     */
    public void setSizeGradual(double size, long transitionSeconds) {
        if (world == null) return;
        WorldBorder border = world.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size, transitionSeconds);
    }

    public double getCurrentSize() {
        if (world == null) return 0;
        return world.getWorldBorder().getSize();
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public World getWorld() {
        return world;
    }
}

package com.vyntric.uhccore.chunk;

import com.vyntric.uhccore.VyntricUHCCore;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Queue;

public class ChunkPreGenerator {

    private final VyntricUHCCore plugin;

    private World world;
    private volatile boolean running = false;
    private long totalChunks;
    private volatile long generatedChunks;
    private Queue<int[]> chunkQueue;
    private BukkitTask task;
    private int chunksPerTick;
    private int lastReportedPercent = -1;

    public ChunkPreGenerator(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    public boolean isRunning() {
        return running;
    }

    public long getTotalChunks() {
        return totalChunks;
    }

    public long getGeneratedChunks() {
        return generatedChunks;
    }

    /**
     * Starts pre-generating a square area of the given size (in blocks), centered on the
     * configured border center. Runs asynchronously in small batches so it never freezes the server.
     */
    public void start(double sizeBlocks) {
        if (running) {
            plugin.getLogger().warning("Chunk pre-generation already running, ignoring duplicate start.");
            return;
        }

        World w = plugin.getBorderManager().getWorld();
        if (w == null) {
            plugin.getLogger().warning("Cannot start chunk pre-generation: world not found. Check border.world in config.yml");
            return;
        }
        this.world = w;

        double centerX = plugin.getBorderManager().getCenterX();
        double centerZ = plugin.getBorderManager().getCenterZ();
        int centerChunkX = (int) Math.floor(centerX / 16.0);
        int centerChunkZ = (int) Math.floor(centerZ / 16.0);
        int radiusChunks = (int) Math.ceil((sizeBlocks / 2.0) / 16.0);

        chunkQueue = new ArrayDeque<>();
        for (int x = centerChunkX - radiusChunks; x <= centerChunkX + radiusChunks; x++) {
            for (int z = centerChunkZ - radiusChunks; z <= centerChunkZ + radiusChunks; z++) {
                chunkQueue.add(new int[]{x, z});
            }
        }

        totalChunks = chunkQueue.size();
        generatedChunks = 0;
        lastReportedPercent = -1;
        running = true;
        chunksPerTick = Math.max(1, plugin.getConfig().getInt("pregen.chunks-per-tick", 20));

        int sizeInt = (int) sizeBlocks;
        plugin.getLogger().info("Starting chunk pre-generation: " + totalChunks + " chunks ("
                + sizeInt + "x" + sizeInt + " blocks).");
        broadcast(plugin.getConfig().getString("messages.pregen-started",
                "&eStarting chunk pre-generation before opening..."));

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    private void tick() {
        if (chunkQueue == null) return;

        for (int i = 0; i < chunksPerTick && !chunkQueue.isEmpty(); i++) {
            int[] coords = chunkQueue.poll();
            if (coords == null) break;
            world.getChunkAtAsync(coords[0], coords[1]).thenRun(this::onChunkDone);
        }
    }

    private synchronized void onChunkDone() {
        generatedChunks++;

        int percent = (int) ((generatedChunks * 100) / Math.max(totalChunks, 1));
        int interval = Math.max(1, plugin.getConfig().getInt("pregen.progress-interval-percent", 10));

        if (plugin.getConfig().getBoolean("pregen.broadcast-progress", true)
                && percent != lastReportedPercent && percent % interval == 0) {
            lastReportedPercent = percent;
            broadcast("&eChunk pre-generation: &f" + percent + "% &7(" + generatedChunks + "/" + totalChunks + ")");
        }

        if (chunkQueue.isEmpty() && generatedChunks >= totalChunks) {
            finish();
        }
    }

    private void finish() {
        if (!running) return;
        running = false;

        if (task != null) {
            task.cancel();
            task = null;
        }

        plugin.getLogger().info("Chunk pre-generation complete (" + generatedChunks + " chunks).");
        plugin.getGameManager().onPregenComplete();
    }

    private void broadcast(String rawMessage) {
        String prefix = plugin.getMessage("prefix");
        String message = ChatColor.translateAlternateColorCodes('&', prefix + rawMessage);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(message);
        }
        plugin.getLogger().info(ChatColor.stripColor(message));
    }
}

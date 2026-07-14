package com.vyntric.uhccore.game;

import com.vyntric.uhccore.VyntricUHCCore;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;

public class DeathmatchTeleporter {

    private final VyntricUHCCore plugin;
    private final Random random = new Random();

    public DeathmatchTeleporter(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    public void teleportAllAlive() {
        World world = plugin.getBorderManager().getWorld();
        if (world == null) return;

        double centerX = plugin.getBorderManager().getCenterX();
        double centerZ = plugin.getBorderManager().getCenterZ();
        double radius = plugin.getConfig().getDouble("deathmatch.border-size", 50) / 2.5;

        List<Player> alivePlayers = plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .map(p -> (Player) p)
                .toList();

        int count = alivePlayers.size();
        int i = 0;
        for (Player player : alivePlayers) {
            double angle = (2 * Math.PI / Math.max(count, 1)) * i;
            double x = centerX + radius * Math.cos(angle);
            double z = centerZ + radius * Math.sin(angle);
            int y = world.getHighestBlockYAt((int) x, (int) z) + 1;

            Location loc = new Location(world, x, y, z);
            player.teleport(loc);
            i++;
        }
    }
}

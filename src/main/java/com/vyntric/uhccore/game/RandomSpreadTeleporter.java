package com.vyntric.uhccore.game;

import com.vyntric.uhccore.VyntricUHCCore;
import com.vyntric.uhccore.team.UHCTeam;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * On /vyntricuhc start, spreads every team (or every solo player, if teams aren't used)
 * to its own random point within the border, far apart from the other spawn points, so
 * nobody starts right next to an enemy. All members of the same team land near each
 * other's spawn point.
 */
public class RandomSpreadTeleporter {

    private final VyntricUHCCore plugin;
    private final Random random = new Random();

    public RandomSpreadTeleporter(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    public void spreadAll() {
        World world = plugin.getBorderManager().getWorld();
        if (world == null) return;

        double centerX = plugin.getBorderManager().getCenterX();
        double centerZ = plugin.getBorderManager().getCenterZ();
        double radius = plugin.getBorderManager().getCurrentSize() / 2.0;
        double minDistance = plugin.getConfig().getDouble("teams.spread-min-distance", 100);

        List<List<Player>> groups = groupPlayersByTeam();
        List<double[]> usedPoints = new ArrayList<>();

        for (List<Player> group : groups) {
            double[] point = pickSpreadPoint(centerX, centerZ, radius, minDistance, usedPoints);
            usedPoints.add(point);

            int y = world.getHighestBlockYAt((int) point[0], (int) point[1]) + 1;
            Location base = new Location(world, point[0], y, point[1]);

            // Small random offset per member so a team doesn't stack on one exact block.
            for (Player player : group) {
                double ox = random.nextInt(7) - 3;
                double oz = random.nextInt(7) - 3;
                Location loc = base.clone().add(ox, 0, oz);
                loc.setY(world.getHighestBlockYAt((int) loc.getX(), (int) loc.getZ()) + 1);
                player.teleport(loc);
            }
        }
    }

    private double[] pickSpreadPoint(double centerX, double centerZ, double radius,
                                      double minDistance, List<double[]> usedPoints) {
        // Try a handful of random points and keep the one farthest from anything already used
        // (cheap approximation of "spread evenly" without needing a full packing solver).
        double[] best = null;
        double bestScore = -1;

        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            // Bias toward the outer part of the border so groups aren't clumped at the center.
            double dist = radius * (0.3 + 0.65 * random.nextDouble());
            double x = centerX + dist * Math.cos(angle);
            double z = centerZ + dist * Math.sin(angle);

            double closest = usedPoints.stream()
                    .mapToDouble(p -> Math.hypot(p[0] - x, p[1] - z))
                    .min().orElse(Double.MAX_VALUE);

            if (usedPoints.isEmpty() || closest >= minDistance) {
                return new double[]{x, z};
            }
            if (closest > bestScore) {
                bestScore = closest;
                best = new double[]{x, z};
            }
        }
        return best != null ? best : new double[]{centerX, centerZ};
    }

    private List<List<Player>> groupPlayersByTeam() {
        List<Player> alive = plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .map(p -> (Player) p)
                .toList();

        Collection<UHCTeam> teams = plugin.getTeamManager().getAllTeams();
        List<List<Player>> groups = new ArrayList<>();
        Set<UUID> handled = new HashSet<>();

        if (!teams.isEmpty()) {
            for (UHCTeam team : teams) {
                List<Player> members = new ArrayList<>();
                for (Player p : alive) {
                    if (team.getMembers().contains(p.getUniqueId())) {
                        members.add(p);
                        handled.add(p.getUniqueId());
                    }
                }
                if (!members.isEmpty()) groups.add(members);
            }
        }

        // Anyone left over (not on any team - solo play) gets their own group.
        for (Player p : alive) {
            if (!handled.contains(p.getUniqueId())) {
                groups.add(new ArrayList<>(List.of(p)));
            }
        }

        Collections.shuffle(groups, random);
        return groups;
    }
}

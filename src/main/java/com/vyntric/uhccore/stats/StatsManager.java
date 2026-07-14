package com.vyntric.uhccore.stats;

import com.vyntric.uhccore.VyntricUHCCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks wins/kills/deaths per player and persists them to stats.yml, so leaderboards
 * survive server restarts (unlike the in-memory-only scoreboard/tablist stats).
 */
public class StatsManager {

    public static class PlayerStats {
        public int wins;
        public int kills;
        public int deaths;
    }

    private final VyntricUHCCore plugin;
    private final Map<UUID, PlayerStats> stats = new HashMap<>();
    private final Map<UUID, String> knownNames = new HashMap<>();
    private File dataFile;
    private FileConfiguration dataConfig;

    public StatsManager(VyntricUHCCore plugin) {
        this.plugin = plugin;
        load();
    }

    public PlayerStats get(UUID uuid) {
        return stats.computeIfAbsent(uuid, id -> new PlayerStats());
    }

    public void addWin(UUID uuid, String name) {
        get(uuid).wins++;
        knownNames.put(uuid, name);
        save();
    }

    public void addKill(UUID uuid, String name) {
        get(uuid).kills++;
        knownNames.put(uuid, name);
        save();
    }

    public void addDeath(UUID uuid, String name) {
        get(uuid).deaths++;
        knownNames.put(uuid, name);
        save();
    }

    public String getName(UUID uuid) {
        return knownNames.getOrDefault(uuid, "Unknown");
    }

    /** Top N players sorted by wins (desc), then kills as a tiebreaker. */
    public List<Map.Entry<UUID, PlayerStats>> topByWins(int limit) {
        return stats.entrySet().stream()
                .sorted((a, b) -> {
                    int byWins = Integer.compare(b.getValue().wins, a.getValue().wins);
                    if (byWins != 0) return byWins;
                    return Integer.compare(b.getValue().kills, a.getValue().kills);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    private void load() {
        dataFile = new File(plugin.getDataFolder(), "stats.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create stats.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (!dataConfig.isConfigurationSection("players")) return;
        for (String uuidStr : Objects.requireNonNull(dataConfig.getConfigurationSection("players")).getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                var section = dataConfig.getConfigurationSection("players." + uuidStr);
                if (section == null) continue;
                PlayerStats s = new PlayerStats();
                s.wins = section.getInt("wins", 0);
                s.kills = section.getInt("kills", 0);
                s.deaths = section.getInt("deaths", 0);
                stats.put(uuid, s);
                knownNames.put(uuid, section.getString("name", "Unknown"));
            } catch (IllegalArgumentException ignored) {
                // corrupt entry, skip
            }
        }
    }

    public void save() {
        if (dataConfig == null) return;
        dataConfig.set("players", null);
        for (var entry : stats.entrySet()) {
            String path = "players." + entry.getKey();
            dataConfig.set(path + ".wins", entry.getValue().wins);
            dataConfig.set(path + ".kills", entry.getValue().kills);
            dataConfig.set(path + ".deaths", entry.getValue().deaths);
            dataConfig.set(path + ".name", knownNames.getOrDefault(entry.getKey(), "Unknown"));
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save stats.yml: " + e.getMessage());
        }
    }
}

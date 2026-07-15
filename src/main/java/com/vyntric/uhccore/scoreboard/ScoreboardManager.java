package com.vyntric.uhccore.scoreboard;

import com.vyntric.uhccore.VyntricUHCCore;
import com.vyntric.uhccore.game.GamePhase;
import com.vyntric.uhccore.utils.TimeUtil;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;

public class ScoreboardManager {

    private final VyntricUHCCore plugin;
    private boolean enabled;
    private String title;
    private List<String> lineTemplates;

    public ScoreboardManager(VyntricUHCCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("scoreboard.enabled", true);
        this.title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("scoreboard.title", "&5&lVYNTRIC UHC"));
        this.lineTemplates = plugin.getConfig().getStringList("scoreboard.lines");
    }

    public void updateAll() {
        if (!enabled) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updateFor(player);
        }
    }

    public void updateFor(Player player) {
        if (!enabled) return;

        Scoreboard scoreboard = plugin.getServer().getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("vyntric_uhc", "dummy", title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int score = lineTemplates.size();
        int blankId = 0;

        for (String rawLine : lineTemplates) {
            String line = applyPlaceholders(rawLine);
            line = ChatColor.translateAlternateColorCodes('&', line);

            // Scoreboard entries must be unique; pad blank/duplicate lines with invisible color codes
            while (line.length() > 40) {
                line = line.substring(0, 40);
            }
            String entry = line.isEmpty() ? uniqueBlank(blankId++) : line;

            objective.getScore(entry).setScore(score--);
        }

        player.setScoreboard(scoreboard);
    }

    private String uniqueBlank(int id) {
        StringBuilder sb = new StringBuilder(ChatColor.RESET.toString());
        for (int i = 0; i < id; i++) {
            sb.append(ChatColor.values()[i % ChatColor.values().length]);
        }
        return sb.toString();
    }

    private String applyPlaceholders(String line) {
        var game = plugin.getGameManager();
        var border = plugin.getBorderManager();
        var team = plugin.getTeamManager();

        long aliveCount = plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .count();

        String phase = game != null ? formatPhase(game.getPhase()) : "N/A";
        String time = game != null ? TimeUtil.formatSeconds(game.getElapsedSeconds()) : "00:00";
        String borderSize = border != null ? String.valueOf((int) border.getCurrentSize()) : "N/A";
        String aliveTeams = team != null ? String.valueOf(team.countAliveTeams()) : "N/A";
        String scenarios = plugin.getScenarioManager() != null ? plugin.getScenarioManager().describeEnabled() : "None";

        return line
                .replace("{time}", time)
                .replace("{border_size}", borderSize)
                .replace("{alive_players}", String.valueOf(aliveCount))
                .replace("{alive_teams}", aliveTeams)
                .replace("{scenarios}", scenarios)
                .replace("{phase}", phase);
    }

    private String formatPhase(GamePhase phase) {
        return switch (phase) {
            case PREGENERATING -> "Preparing World";
            case WAITING -> "Waiting";
            case GRACE_PERIOD -> "Grace Period";
            case PVP_ENABLED -> "PVP Enabled";
            case DEATHMATCH -> "Deathmatch";
            case ENDED -> "Ended";
        };
    }
}

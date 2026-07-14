package com.vyntric.uhccore.tablist;

import com.vyntric.uhccore.VyntricUHCCore;
import com.vyntric.uhccore.game.GamePhase;
import com.vyntric.uhccore.utils.TimeUtil;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders the tab-list (player list) header and footer with the Vyntric purple branding,
 * plus a live snapshot of the game (phase, alive count, border size...).
 */
public class TabListManager {

    private final VyntricUHCCore plugin;
    private boolean enabled;
    private List<String> headerLines;
    private List<String> footerLines;

    public TabListManager(VyntricUHCCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("tablist.enabled", true);
        this.headerLines = plugin.getConfig().getStringList("tablist.header");
        this.footerLines = plugin.getConfig().getStringList("tablist.footer");
    }

    public void updateAll() {
        if (!enabled) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updateFor(player);
        }
    }

    public void updateFor(Player player) {
        if (!enabled) return;
        player.setPlayerListHeaderFooter(buildBlock(headerLines), buildBlock(footerLines));
    }

    /**
     * Clears the custom header/footer, e.g. if the feature gets disabled at runtime via reload.
     */
    public void clearFor(Player player) {
        player.setPlayerListHeaderFooter("", "");
    }

    private String buildBlock(List<String> lines) {
        return lines.stream()
                .map(this::applyPlaceholders)
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.joining("\n"));
    }

    private String applyPlaceholders(String line) {
        var game = plugin.getGameManager();
        var border = plugin.getBorderManager();
        var team = plugin.getTeamManager();

        long aliveCount = plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .count();
        long online = plugin.getServer().getOnlinePlayers().size();

        String phase = game != null ? formatPhase(game.getPhase()) : "N/A";
        String time = game != null ? TimeUtil.formatSeconds(game.getElapsedSeconds()) : "00:00";
        String borderSize = border != null ? String.valueOf((int) border.getCurrentSize()) : "N/A";
        String aliveTeams = team != null ? String.valueOf(team.countAliveTeams()) : "N/A";

        return line
                .replace("{time}", time)
                .replace("{border_size}", borderSize)
                .replace("{alive_players}", String.valueOf(aliveCount))
                .replace("{online}", String.valueOf(online))
                .replace("{alive_teams}", aliveTeams)
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

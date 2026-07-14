package com.vyntric.uhccore.placeholder;

import com.vyntric.uhccore.VyntricUHCCore;
import com.vyntric.uhccore.utils.TimeUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.jetbrains.annotations.NotNull;

/**
 * Registers %vyntricuhc_...% placeholders with PlaceholderAPI (only loaded/registered if
 * PlaceholderAPI is actually installed - see VyntricUHCCore#onEnable). Lets other plugins
 * (holograms, other scoreboards, chat plugins, etc.) show live UHC info.
 *
 * Available placeholders:
 *   %vyntricuhc_phase%           - current GamePhase, e.g. "PVP_ENABLED"
 *   %vyntricuhc_time%            - elapsed game time, formatted mm:ss
 *   %vyntricuhc_border%          - current world border size (blocks)
 *   %vyntricuhc_alive_players%   - number of players not eliminated
 *   %vyntricuhc_alive_teams%     - number of teams with at least one alive member
 *   %vyntricuhc_wins%            - the requesting player's total wins
 *   %vyntricuhc_kills%           - the requesting player's total kills
 */
public class VyntricPlaceholders extends PlaceholderExpansion {

    private final VyntricUHCCore plugin;

    public VyntricPlaceholders(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "vyntricuhc";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Vyntric";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(org.bukkit.entity.Player player, @NotNull String params) {
        return switch (params.toLowerCase()) {
            case "phase" -> plugin.getGameManager().getPhase().name();
            case "time" -> TimeUtil.formatSeconds(plugin.getGameManager().getElapsedSeconds());
            case "border" -> String.valueOf((int) plugin.getBorderManager().getCurrentSize());
            case "alive_players" -> String.valueOf(plugin.getServer().getOnlinePlayers().stream()
                    .filter(p -> !plugin.getTeamManager().isEliminated(p)).count());
            case "alive_teams" -> String.valueOf(plugin.getTeamManager().countAliveTeams());
            case "wins" -> player == null ? "0" : String.valueOf(plugin.getStatsManager().get(player.getUniqueId()).wins);
            case "kills" -> player == null ? "0" : String.valueOf(plugin.getStatsManager().get(player.getUniqueId()).kills);
            default -> null;
        };
    }
}

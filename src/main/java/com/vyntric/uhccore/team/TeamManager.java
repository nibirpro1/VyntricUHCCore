package com.vyntric.uhccore.team;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class TeamManager {

    private final Plugin plugin;
    private final Map<String, UHCTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, String> playerTeamMap = new HashMap<>();
    private final Map<UUID, String> pendingInvites = new HashMap<>();
    private boolean friendlyFire;
    private int maxTeamSize;
    private static final long INVITE_EXPIRY_TICKS = 20L * 60; // 60 seconds

    public TeamManager(Plugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.friendlyFire = plugin.getConfig().getBoolean("teams.friendly-fire", false);
        this.maxTeamSize = plugin.getConfig().getInt("teams.max-team-size", 4);
    }

    public int getMaxTeamSize() {
        return maxTeamSize;
    }

    /**
     * Sets the max team size at runtime (e.g. via /vyntricuhc team limit <n>) and persists
     * it to config.yml so it survives restarts/reloads. A limit of 1 effectively makes the
     * game solo (each team can only ever hold its owner).
     */
    public void setMaxTeamSize(int size) {
        this.maxTeamSize = size;
        plugin.getConfig().set("teams.max-team-size", size);
        plugin.saveConfig();
    }

    public UHCTeam createTeam(String name) {
        UHCTeam team = new UHCTeam(name);
        teams.put(name.toLowerCase(), team);
        return team;
    }

    /**
     * Creates a team and sets the given player as its owner + first member.
     */
    public UHCTeam createTeam(String name, Player owner) {
        UHCTeam team = createTeam(name);
        team.setOwner(owner.getUniqueId());
        addPlayerToTeam(name, owner);
        return team;
    }

    public boolean isOwner(Player player, UHCTeam team) {
        return team != null && team.getOwner() != null && team.getOwner().equals(player.getUniqueId());
    }

    /**
     * Sends a team invite from the team owner to a target player. Expires automatically after 60 seconds.
     */
    public void sendInvite(Player owner, Player target) {
        UHCTeam team = getTeam(owner);
        if (team == null) return;

        String teamName = team.getName();
        pendingInvites.put(target.getUniqueId(), teamName);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Only clear if it's still the same pending invite (not already accepted/replaced)
            pendingInvites.remove(target.getUniqueId(), teamName);
        }, INVITE_EXPIRY_TICKS);
    }

    /**
     * Attempts to accept the pending invite for this player. Returns true if successful.
     */
    public boolean acceptInvite(Player target) {
        String teamName = pendingInvites.remove(target.getUniqueId());
        if (teamName == null) return false;
        return addPlayerToTeam(teamName, target);
    }

    public String getPendingInviteTeam(Player target) {
        return pendingInvites.get(target.getUniqueId());
    }

    public boolean addPlayerToTeam(String teamName, Player player) {
        UHCTeam team = teams.get(teamName.toLowerCase());
        if (team == null) return false;
        if (team.getMembers().size() >= maxTeamSize) return false;

        // remove from previous team first
        removePlayerFromTeam(player);

        team.addMember(player.getUniqueId());
        playerTeamMap.put(player.getUniqueId(), team.getName());
        return true;
    }

    public void removePlayerFromTeam(Player player) {
        String current = playerTeamMap.get(player.getUniqueId());
        if (current != null) {
            UHCTeam team = teams.get(current.toLowerCase());
            if (team != null) team.removeMember(player.getUniqueId());
            playerTeamMap.remove(player.getUniqueId());
        }
    }

    public UHCTeam getTeam(Player player) {
        String name = playerTeamMap.get(player.getUniqueId());
        return name == null ? null : teams.get(name.toLowerCase());
    }

    public UHCTeam getTeam(String name) {
        return teams.get(name.toLowerCase());
    }

    public Collection<UHCTeam> getAllTeams() {
        return teams.values();
    }

    public boolean isSameTeam(Player a, Player b) {
        UHCTeam ta = getTeam(a);
        UHCTeam tb = getTeam(b);
        return ta != null && ta.equals(tb);
    }

    public boolean isFriendlyFire() {
        return friendlyFire;
    }

    public void markEliminated(Player player) {
        UHCTeam team = getTeam(player);
        if (team != null) {
            team.markMemberEliminated(player.getUniqueId());
        }
    }

    /**
     * Whether this player has already been marked eliminated on their team. Players with no
     * team at all are never considered "eliminated" here (solo-mode games can call this too
     * without breaking - it will just always return false, which is fine).
     */
    public boolean isEliminated(Player player) {
        UHCTeam team = getTeam(player);
        return team != null && team.isEliminated(player.getUniqueId());
    }

    /**
     * Reverses markEliminated - used when a player who combat-logged (and became a loot
     * zombie) rejoins the server before the zombie was killed, so they're back in the game.
     */
    public void unmarkEliminated(Player player) {
        UHCTeam team = getTeam(player);
        if (team != null) {
            team.unmarkEliminated(player.getUniqueId());
        }
    }

    /**
     * Number of teams that still have at least one non-eliminated member.
     */
    public long countAliveTeams() {
        return teams.values().stream().filter(UHCTeam::hasAliveMembers).count();
    }

    public void reset() {
        teams.clear();
        playerTeamMap.clear();
        pendingInvites.clear();
    }

    /**
     * Clears elimination status on all teams (keeps team rosters intact) so a new
     * round can start with everyone marked "alive" again.
     */
    public void resetEliminations() {
        for (UHCTeam team : teams.values()) {
            team.resetEliminated();
        }
    }
}

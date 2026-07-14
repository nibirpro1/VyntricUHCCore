package com.vyntric.uhccore.game;

import com.vyntric.uhccore.VyntricUHCCore;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class GameManager {

    private final VyntricUHCCore plugin;
    private GamePhase phase = GamePhase.WAITING;

    private long elapsedSeconds = 0;
    private long graceDurationSeconds;
    private boolean graceEnabled;

    private boolean deathmatchEnabled;
    private long deathmatchTriggerSeconds;
    private boolean deathmatchTriggered = false;

    private BukkitTask tickTask;

    // Manual /vyntricuhc pvp override. null = follow the phase automatically (default),
    // TRUE/FALSE = an operator forced pvp on or off regardless of phase.
    private Boolean pvpOverride = null;

    public GameManager(VyntricUHCCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        var cfg = plugin.getConfig();
        this.graceEnabled = cfg.getBoolean("grace-period.enabled", true);
        this.graceDurationSeconds = cfg.getLong("grace-period.duration-minutes", 15) * 60L;

        this.deathmatchEnabled = cfg.getBoolean("deathmatch.enabled", true);
        this.deathmatchTriggerSeconds = cfg.getLong("deathmatch.trigger-after-minutes", 60) * 60L;
    }

    public void startGame() {
        if (phase != GamePhase.WAITING) return;

        plugin.getLobbyCageManager().releaseAll();

        elapsedSeconds = 0;
        deathmatchTriggered = false;
        pvpOverride = null;

        plugin.getBorderManager().initializeBorder();
        plugin.getBorderManager().startShrinking();

        if (plugin.getConfig().getBoolean("teams.spread-on-start", true)) {
            plugin.getRandomSpreadTeleporter().spreadAll();
        }

        phase = graceEnabled ? GamePhase.GRACE_PERIOD : GamePhase.PVP_ENABLED;
        broadcast(plugin.getMessage("game-started"));
        if (phase == GamePhase.GRACE_PERIOD) {
            broadcast(plugin.getConfig().getString("grace-period.message", "&aGrace period active."));
        }
        plugin.getDiscordWebhook().send("🟢 The UHC game has started!");

        startTicking();
    }

    public void stopGame() {
        phase = GamePhase.ENDED;
        stopTicking();
        broadcast(plugin.getMessage("game-stopped"));
    }

    /**
     * Manually resets the game back to WAITING so a new round can begin.
     * Clears elimination status (so teams are "alive" again) and puts everyone
     * back into survival mode. Triggered via /vyntricuhc restart - never automatic.
     */
    public void restartGame() {
        stopTicking();
        elapsedSeconds = 0;
        deathmatchTriggered = false;
        pvpOverride = null;
        phase = GamePhase.WAITING;

        plugin.getTeamManager().resetEliminations();

        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.setGameMode(GameMode.SURVIVAL);
        }

        plugin.getLobbyCageManager().cageAllOnline();

        broadcast(plugin.getConfig().getString("messages.game-restarted",
                "&aThe game has been reset. Ready for a new round!"));
    }

    private void startTicking() {
        stopTicking();
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L); // every second
    }

    private void stopTicking() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void tick() {
        if (phase == GamePhase.ENDED || phase == GamePhase.WAITING) return;

        elapsedSeconds++;

        if (phase == GamePhase.GRACE_PERIOD && elapsedSeconds >= graceDurationSeconds) {
            enablePvp();
        }

        if (deathmatchEnabled && !deathmatchTriggered && elapsedSeconds >= deathmatchTriggerSeconds) {
            triggerDeathmatch();
        }

        plugin.getScoreboardManager().updateAll();
        plugin.getTabListManager().updateAll();
        checkWinCondition();
    }

    private void enablePvp() {
        phase = GamePhase.PVP_ENABLED;
        broadcast(plugin.getMessage("pvp-enabled"));

        // When the meetup/grace period ends, jump straight into deathmatch if it's enabled,
        // instead of waiting for the separate elapsed-time trigger.
        if (deathmatchEnabled && !deathmatchTriggered) {
            triggerDeathmatch();
        }
    }

    private void triggerDeathmatch() {
        deathmatchTriggered = true;
        phase = GamePhase.DEATHMATCH;
        broadcast(plugin.getMessage("deathmatch-starting"));
        plugin.getDiscordWebhook().send("⚔️ Deathmatch has started - friendly fire is off, everyone fights!");

        // No forced small-border teleport anymore - deathmatch just means everyone fights
        // it out wherever they already are, with friendly fire ignored (see GameListener).
    }

    private void checkWinCondition() {
        long aliveTeams = plugin.getTeamManager().countAliveTeams();
        if (aliveTeams <= 1 && phase != GamePhase.WAITING && phase != GamePhase.ENDED) {
            // Only declare a winner once teams have actually been used
            if (!plugin.getTeamManager().getAllTeams().isEmpty()) {
                var winner = plugin.getTeamManager().getAllTeams().stream()
                        .filter(t -> t.hasAliveMembers())
                        .findFirst();
                String winnerName = winner.map(t -> t.getName()).orElse("No one");
                broadcast(plugin.getMessage("winner-announcement").replace("{winner}", winnerName));
                plugin.getDiscordWebhook().send("🏆 " + winnerName + " has won the game!");

                winner.ifPresent(team -> {
                    for (java.util.UUID memberId : team.getMembers()) {
                        if (!team.isEliminated(memberId)) {
                            Player member = plugin.getServer().getPlayer(memberId);
                            String name = member != null ? member.getName() : plugin.getStatsManager().getName(memberId);
                            plugin.getStatsManager().addWin(memberId, name);
                        }
                    }
                });

                stopGame();
            }
        }
    }

    private void broadcast(String rawMessage) {
        String prefix = plugin.getMessage("prefix");
        String message = ChatColor.translateAlternateColorCodes('&', prefix + rawMessage);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.sendMessage(message);
        }
    }

    /**
     * Dynamically changes the grace/meetup period duration, even mid-game.
     * Persists the new value to config.yml and, if we're currently in the grace
     * period, applies it immediately (may extend or shorten the remaining time).
     */
    public void setGraceDuration(long minutes) {
        this.graceDurationSeconds = minutes * 60L;

        plugin.getConfig().set("grace-period.duration-minutes", minutes);
        plugin.saveConfig();

        broadcast(plugin.getConfig().getString("messages.grace-period-updated",
                        "&bMeetup/grace period time set to {minutes} minute(s).")
                .replace("{minutes}", String.valueOf(minutes)));

        if (phase == GamePhase.GRACE_PERIOD && elapsedSeconds >= graceDurationSeconds) {
            enablePvp();
        }
    }

    public long getGraceDurationSeconds() {
        return graceDurationSeconds;
    }

    public GamePhase getPhase() {
        return phase;
    }

    /**
     * Directly sets the phase. Used during plugin startup before pre-generation finishes.
     */
    public void setPhase(GamePhase phase) {
        this.phase = phase;
    }

    /**
     * Called by ChunkPreGenerator once pre-generation finishes. Moves the game from
     * PREGENERATING into WAITING so /vyntricuhc start becomes usable and the server is "open".
     */
    public void onPregenComplete() {
        if (phase == GamePhase.PREGENERATING) {
            phase = GamePhase.WAITING;
            broadcast(plugin.getConfig().getString("messages.pregen-complete",
                    "&aChunk pre-generation complete! Server is now open."));
        }
    }

    public long getElapsedSeconds() {
        return elapsedSeconds;
    }

    public boolean isPvpEnabled() {
        if (pvpOverride != null) return pvpOverride;
        return phase == GamePhase.PVP_ENABLED || phase == GamePhase.DEATHMATCH;
    }

    /**
     * Manually forces PVP on or off, overriding whatever the current phase would normally
     * dictate. Used by /vyntricuhc pvp <enable|disable>. Only meant to be called once the
     * UHC game has actually started (see isGameActive()).
     */
    public void setPvpOverride(boolean enabled) {
        this.pvpOverride = enabled;
    }

    /**
     * Clears any manual pvp override, going back to the automatic phase-based behaviour.
     */
    public void clearPvpOverride() {
        this.pvpOverride = null;
    }

    public Boolean getPvpOverride() {
        return pvpOverride;
    }

    /**
     * True once /vyntricuhc start has been used and the round hasn't ended yet - i.e. the
     * grace period, active pvp, or deathmatch phases. Used to gate things that should only
     * happen during an actual round, like the combat-logout zombie.
     */
    public boolean isGameActive() {
        return phase == GamePhase.GRACE_PERIOD || phase == GamePhase.PVP_ENABLED || phase == GamePhase.DEATHMATCH;
    }
}

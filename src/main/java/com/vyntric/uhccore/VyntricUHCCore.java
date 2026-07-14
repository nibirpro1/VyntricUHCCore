package com.vyntric.uhccore;

import com.vyntric.uhccore.auth.AuthManager;
import com.vyntric.uhccore.border.BorderManager;
import com.vyntric.uhccore.bounty.BountyManager;
import com.vyntric.uhccore.chunk.ChunkPreGenerator;
import com.vyntric.uhccore.crossteam.CrossteamModule;
import com.vyntric.uhccore.discord.DiscordWebhook;
import com.vyntric.uhccore.game.DeathmatchTeleporter;
import com.vyntric.uhccore.game.GameManager;
import com.vyntric.uhccore.game.GamePhase;
import com.vyntric.uhccore.game.RandomSpreadTeleporter;
import com.vyntric.uhccore.listeners.AuthListener;
import com.vyntric.uhccore.listeners.CombatLogoutListener;
import com.vyntric.uhccore.listeners.GameListener;
import com.vyntric.uhccore.listeners.GoldenAppleListener;
import com.vyntric.uhccore.listeners.PotionListener;
import com.vyntric.uhccore.listeners.TeamInteractListener;
import com.vyntric.uhccore.lobby.LobbyCageManager;
import com.vyntric.uhccore.placeholder.VyntricPlaceholders;
import com.vyntric.uhccore.scoreboard.ScoreboardManager;
import com.vyntric.uhccore.stats.StatsManager;
import com.vyntric.uhccore.tablist.TabListManager;
import com.vyntric.uhccore.team.TeamManager;
import com.vyntric.uhccore.util.ConfirmationManager;
import com.vyntric.uhccore.commands.AuthCommand;
import com.vyntric.uhccore.commands.ResetPassCommand;
import com.vyntric.uhccore.commands.UHCCommand;
import com.vyntric.uhccore.commands.WorldCommand;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class VyntricUHCCore extends JavaPlugin {

    private BorderManager borderManager;
    private TeamManager teamManager;
    private ScoreboardManager scoreboardManager;
    private GameManager gameManager;
    private DeathmatchTeleporter deathmatchTeleporter;
    private RandomSpreadTeleporter randomSpreadTeleporter;
    private ChunkPreGenerator chunkPreGenerator;
    private AuthManager authManager;
    private TabListManager tabListManager;
    private CrossteamModule crossteamModule;
    private LobbyCageManager lobbyCageManager;
    private StatsManager statsManager;
    private BountyManager bountyManager;
    private DiscordWebhook discordWebhook;
    private ConfirmationManager confirmationManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.borderManager = new BorderManager(this);
        this.teamManager = new TeamManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.deathmatchTeleporter = new DeathmatchTeleporter(this);
        this.randomSpreadTeleporter = new RandomSpreadTeleporter(this);
        this.gameManager = new GameManager(this);
        this.chunkPreGenerator = new ChunkPreGenerator(this);
        this.authManager = new AuthManager(this);
        this.tabListManager = new TabListManager(this);
        this.statsManager = new StatsManager(this);
        this.bountyManager = new BountyManager(this);
        this.discordWebhook = new DiscordWebhook(this);
        this.confirmationManager = new ConfirmationManager(this);
        this.lobbyCageManager = new LobbyCageManager(this);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);
        getServer().getPluginManager().registerEvents(new GoldenAppleListener(this), this);
        getServer().getPluginManager().registerEvents(new PotionListener(this), this);
        getServer().getPluginManager().registerEvents(new TeamInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatLogoutListener(this), this);
        getServer().getPluginManager().registerEvents(lobbyCageManager, this);

        getCommand("vyntricuhc").setExecutor(new UHCCommand(this));
        getCommand("world").setExecutor(new WorldCommand(this));

        AuthCommand authCommand = new AuthCommand(this);
        getCommand("login").setExecutor(authCommand);
        getCommand("register").setExecutor(authCommand);
        getCommand("changepass").setExecutor(authCommand);
        getCommand("resetpass").setExecutor(new ResetPassCommand(this));

        this.crossteamModule = new CrossteamModule(this);
        crossteamModule.enable();

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new VyntricPlaceholders(this).register();
            getLogger().info("PlaceholderAPI found - %vyntricuhc_...% placeholders registered.");
        }

        borderManager.initializeBorder();

        if (getConfig().getBoolean("pregen.auto-run-on-enable", true)) {
            gameManager.setPhase(GamePhase.PREGENERATING);
            double size = getConfig().getDouble("pregen.size", 500);
            // Run next tick so the world is fully loaded before we start requesting chunks
            getServer().getScheduler().runTask(this, () -> chunkPreGenerator.start(size));
        }

        // Covers a full server /reload where players are already connected when the plugin
        // re-enables - they wouldn't get a fresh PlayerJoinEvent otherwise.
        lobbyCageManager.cageAllOnline();

        getLogger().info("VyntricUHCCore has been enabled. Made by Vyntric.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stopGame();
        }
        if (crossteamModule != null) {
            crossteamModule.disable();
        }
        if (statsManager != null) {
            statsManager.save();
        }
        getLogger().info("VyntricUHCCore has been disabled.");
    }

    public String getMessage(String path) {
        String raw = getConfig().getString("messages." + path, "");
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    public BorderManager getBorderManager() {
        return borderManager;
    }

    public TeamManager getTeamManager() {
        return teamManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public DeathmatchTeleporter getDeathmatchTeleporter() {
        return deathmatchTeleporter;
    }

    public ChunkPreGenerator getChunkPreGenerator() {
        return chunkPreGenerator;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public TabListManager getTabListManager() {
        return tabListManager;
    }

    public CrossteamModule getCrossteamModule() {
        return crossteamModule;
    }

    public LobbyCageManager getLobbyCageManager() {
        return lobbyCageManager;
    }

    public RandomSpreadTeleporter getRandomSpreadTeleporter() {
        return randomSpreadTeleporter;
    }

    public StatsManager getStatsManager() {
        return statsManager;
    }

    public BountyManager getBountyManager() {
        return bountyManager;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    public ConfirmationManager getConfirmationManager() {
        return confirmationManager;
    }
}

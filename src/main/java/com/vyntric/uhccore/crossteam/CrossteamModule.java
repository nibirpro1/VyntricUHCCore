package com.vyntric.uhccore.crossteam;

import com.vyntric.uhccore.VyntricUHCCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Vyntric Cross Team Tracker
 * -----------------------------------------------------------------
 * Detects players who repeatedly damage members of an enemy
 * scoreboard team ("cross-teaming" / boosting) and lets staff
 * inspect that data with /track.
 *
 * This was originally its own plugin (VyntricCrossteam) and has
 * been folded into VyntricUHCCore as a module so both feature sets
 * ship in a single jar. It keeps its own config section
 * ("crossteam:" in config.yml) and its own data file
 * (crossteam-data.yml) so it never collides with the core UHC
 * settings.
 *
 * Every message this module sends always starts with the "Vyntric"
 * brand tag (in purple) followed by the actual detail, e.g.:
 *
 *   Vyntric » Analyzing combat data for team: Falcons
 */
public final class CrossteamModule implements Listener, CommandExecutor, TabCompleter {

    private final VyntricUHCCore plugin;

    // attackerUUID -> (victimTeamName -> hitCount)
    private final Map<UUID, Map<String, Integer>> hitCounter = new ConcurrentHashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;
    private BukkitTask autosaveTask;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public CrossteamModule(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        loadData();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        Objects.requireNonNull(plugin.getCommand("track")).setExecutor(this);
        Objects.requireNonNull(plugin.getCommand("track")).setTabCompleter(this);

        long intervalTicks = 20L * 60L * Math.max(1, plugin.getConfig().getInt("crossteam.settings.autosave-interval-minutes", 5));
        autosaveTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveData, intervalTicks, intervalTicks);

        plugin.getLogger().info("Vyntric Cross Team Tracker module active!");
    }

    public void disable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        saveData();
    }

    // ---------------------------------------------------------------
    // Combat tracking
    // ---------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (!(event.getEntity() instanceof Player victim)) return;
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        Scoreboard board = Bukkit.getScoreboardManager() == null ? null : Bukkit.getScoreboardManager().getMainScoreboard();
        if (board == null) return;

        Team attackerTeam = board.getEntryTeam(attacker.getName());
        Team victimTeam = board.getEntryTeam(victim.getName());
        if (attackerTeam == null || victimTeam == null) return;
        if (attackerTeam.getName().equals(victimTeam.getName())) return; // same team, not cross-teaming

        int total = hitCounter
                .computeIfAbsent(attacker.getUniqueId(), id -> new HashMap<>())
                .merge(victimTeam.getName(), 1, Integer::sum);

        int threshold = plugin.getConfig().getInt("crossteam.settings.cross-team-threshold", 5);
        if (total == threshold && plugin.getConfig().getBoolean("crossteam.settings.broadcast-alerts", true)) {
            Component alert = brand("cross-team-detected",
                    Map.of("%team%", victimTeam.getName(), "%hits%", String.valueOf(total)));
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("vyntric.alerts")) {
                    staff.sendMessage(alert);
                }
            }
            plugin.getLogger().warning(attacker.getName() + " crossed the cross-team threshold vs team " + victimTeam.getName());
        }
    }

    /** Resolves the real attacking player, following arrows/tridents back to their shooter. */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (plugin.getConfig().getBoolean("crossteam.settings.track-projectile-damage", true) && damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) return player;
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help" -> sendHelp(sender);
            case "top" -> handleTop(sender);
            case "reset" -> handleReset(sender, args);
            default -> handleTeamReport(sender, args[0]);
        }
        return true;
    }

    private void handleTeamReport(CommandSender sender, String input) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team targetTeam = board.getTeam(input);

        if (targetTeam == null) {
            // fall back: treat input as a player name and use their current team
            Player targetPlayer = Bukkit.getPlayer(input);
            if (targetPlayer != null) {
                targetTeam = board.getEntryTeam(targetPlayer.getName());
            }
        }

        if (targetTeam == null) {
            sender.sendMessage(brand("not-found", Map.of("%input%", input)));
            return;
        }

        sender.sendMessage(brand("analyzing", Map.of("%team%", targetTeam.getName())));

        Map<String, Integer> totalHitsPerTeam = new HashMap<>();
        for (String memberName : targetTeam.getEntries()) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberName);
            Map<String, Integer> memberHits = hitCounter.get(member.getUniqueId());
            if (memberHits == null) continue;
            memberHits.forEach((team, hits) -> totalHitsPerTeam.merge(team, hits, Integer::sum));
        }

        if (totalHitsPerTeam.isEmpty()) {
            sender.sendMessage(brand("no-data", Map.of("%team%", targetTeam.getName())));
            return;
        }

        int threshold = plugin.getConfig().getInt("crossteam.settings.cross-team-threshold", 5);
        totalHitsPerTeam.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(entry -> {
                    sender.sendMessage(brand("report-line", Map.of(
                            "%team%", entry.getKey(),
                            "%hits%", String.valueOf(entry.getValue()))));
                    if (entry.getValue() >= threshold) {
                        sender.sendMessage(brand("cross-team-detected", Map.of(
                                "%team%", entry.getKey(),
                                "%hits%", String.valueOf(entry.getValue()))));
                    }
                });
    }

    private void handleTop(CommandSender sender) {
        if (!sender.hasPermission("vyntric.track.top")) {
            sender.sendMessage(brand("no-permission", Map.of()));
            return;
        }

        Map<UUID, Integer> totals = new HashMap<>();
        hitCounter.forEach((uuid, teams) -> totals.put(uuid, teams.values().stream().mapToInt(Integer::intValue).sum()));

        if (totals.isEmpty()) {
            sender.sendMessage(brand("top-empty", Map.of()));
            return;
        }

        sender.sendMessage(brand("top-header", Map.of()));
        List<Map.Entry<UUID, Integer>> sorted = totals.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(5)
                .collect(Collectors.toList());

        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            sender.sendMessage(brand("top-line", Map.of(
                    "%rank%", String.valueOf(rank++),
                    "%player%", name == null ? "Unknown" : name,
                    "%hits%", String.valueOf(entry.getValue()))));
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vyntric.admin")) {
            sender.sendMessage(brand("no-permission", Map.of()));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(brand("help-reset", Map.of()));
            return;
        }

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(args[1]);
        if (team == null) {
            sender.sendMessage(brand("not-found", Map.of("%input%", args[1])));
            return;
        }

        for (String memberName : team.getEntries()) {
            UUID uuid = Bukkit.getOfflinePlayer(memberName).getUniqueId();
            Map<String, Integer> memberHits = hitCounter.get(uuid);
            if (memberHits != null) {
                memberHits.remove(team.getName());
                if (memberHits.isEmpty()) hitCounter.remove(uuid);
            }
        }

        sender.sendMessage(brand("reset-success", Map.of("%team%", team.getName())));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(brand("help-header", Map.of()));
        sender.sendMessage(LEGACY.deserialize(plugin.getConfig().getString("crossteam.messages.help-track", "")));
        sender.sendMessage(LEGACY.deserialize(plugin.getConfig().getString("crossteam.messages.help-top", "")));
        sender.sendMessage(LEGACY.deserialize(plugin.getConfig().getString("crossteam.messages.help-reset", "")));
        sender.sendMessage(LEGACY.deserialize(plugin.getConfig().getString("crossteam.messages.help-help", "")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help", "top", "reset"));
            options.addAll(Bukkit.getScoreboardManager().getMainScoreboard().getTeams()
                    .stream().map(Team::getName).toList());
            return filterStartsWith(options, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            return filterStartsWith(
                    Bukkit.getScoreboardManager().getMainScoreboard().getTeams()
                            .stream().map(Team::getName).toList(),
                    args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // Messaging - "Vyntric" always comes first, always purple
    // ---------------------------------------------------------------

    /**
     * Builds a message: the purple "Vyntric" brand tag first, then the
     * configured message for {@code messageKey} with placeholders replaced.
     */
    private Component brand(String messageKey, Map<String, String> placeholders) {
        String prefixRaw = plugin.getConfig().getString("crossteam.messages.prefix", "&5&lVyntric &d» ");
        String bodyRaw = plugin.getConfig().getString("crossteam.messages." + messageKey, "");

        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            bodyRaw = bodyRaw.replace(placeholder.getKey(), placeholder.getValue());
        }

        Component prefix = LEGACY.deserialize(prefixRaw).colorIfAbsent(NamedTextColor.LIGHT_PURPLE);
        Component body = LEGACY.deserialize(bodyRaw);
        return prefix.append(body);
    }

    // ---------------------------------------------------------------
    // Persistence - hit data survives restarts (separate file from
    // the core UHC config so the two modules never step on each other)
    // ---------------------------------------------------------------

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "crossteam-data.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            createDataFileQuietly();
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (dataConfig.isConfigurationSection("hits")) {
            for (String uuidStr : Objects.requireNonNull(dataConfig.getConfigurationSection("hits")).getKeys(false)) {
                Map<String, Integer> teamHits = new HashMap<>();
                var section = dataConfig.getConfigurationSection("hits." + uuidStr);
                if (section == null) continue;
                for (String team : section.getKeys(false)) {
                    teamHits.put(team, section.getInt(team));
                }
                try {
                    hitCounter.put(UUID.fromString(uuidStr), teamHits);
                } catch (IllegalArgumentException ignored) {
                    // corrupt entry, skip
                }
            }
        }
    }

    private void createDataFileQuietly() {
        try {
            dataFile.createNewFile();
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create crossteam-data.yml: " + e.getMessage());
        }
    }

    private void saveData() {
        if (dataConfig == null) return;
        dataConfig.set("hits", null); // clear before rewriting
        hitCounter.forEach((uuid, teams) -> teams.forEach((team, hits) ->
                dataConfig.set("hits." + uuid + "." + team, hits)));
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save crossteam-data.yml: " + e.getMessage());
        }
    }
}

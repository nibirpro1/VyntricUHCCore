package com.vyntric.uhccore.auth;

import com.vyntric.uhccore.VyntricUHCCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Handles the login/register system for VyntricUHCCore, including basic alt-account
 * detection by shared IP.
 *
 * Data is stored in auth.yml, keyed by player UUID:
 *   players:
 *     <uuid>:
 *       username: <name at time of registration>
 *       salt: <base64>
 *       hash: <base64>
 *       registered-at: <epoch millis>
 *       last-login: <epoch millis>
 *       last-ip: <ip address as string>
 *       ips: [<every ip address this uuid has ever connected from>]
 *       last-location:
 *         world, x, y, z, yaw, pitch   <- where they were when they last quit
 *
 * We never store the plaintext password anywhere - the salt+hash is one-way, so nobody
 * (not even server owners) can recover it. Login state is NOT persisted across
 * restarts/relogs on purpose - every time a player joins the server they must /login again.
 */
public class AuthManager {

    private final VyntricUHCCore plugin;
    private final File file;
    private FileConfiguration data;

    private final Set<UUID> loggedIn = new HashSet<>();

    public AuthManager(VyntricUHCCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auth.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create auth.yml", e);
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save auth.yml", e);
        }
    }

    public boolean isAuthEnabled() {
        return plugin.getConfig().getBoolean("auth.enabled", true);
    }

    public int getMinPasswordLength() {
        return plugin.getConfig().getInt("auth.min-password-length", 4);
    }

    public int getLoginTimeoutSeconds() {
        return plugin.getConfig().getInt("auth.login-timeout-seconds", 60);
    }

    private String path(UUID uuid) {
        return "players." + uuid;
    }

    public boolean isRegistered(UUID uuid) {
        return data.contains(path(uuid) + ".hash");
    }

    public boolean isLoggedIn(UUID uuid) {
        return loggedIn.contains(uuid);
    }

    public void markLoggedIn(UUID uuid) {
        loggedIn.add(uuid);
    }

    public void markLoggedOut(UUID uuid) {
        loggedIn.remove(uuid);
    }

    /**
     * Registers a brand-new player with the given password.
     */
    public void register(Player player, String password) {
        UUID uuid = player.getUniqueId();
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash(password, salt);

        data.set(path(uuid) + ".username", player.getName());
        data.set(path(uuid) + ".salt", salt);
        data.set(path(uuid) + ".hash", hash);
        data.set(path(uuid) + ".registered-at", System.currentTimeMillis());
        data.set(path(uuid) + ".last-login", System.currentTimeMillis());
        recordIp(uuid, getIp(player));
        save();

        markLoggedIn(uuid);
    }

    /**
     * Attempts to log a player in with the given password. Returns true on success.
     */
    public boolean login(Player player, String password) {
        UUID uuid = player.getUniqueId();
        if (!isRegistered(uuid)) return false;

        String salt = data.getString(path(uuid) + ".salt");
        String hash = data.getString(path(uuid) + ".hash");
        if (salt == null || hash == null) return false;

        boolean ok = PasswordUtil.matches(password, salt, hash);
        if (ok) {
            markLoggedIn(uuid);
            data.set(path(uuid) + ".last-login", System.currentTimeMillis());
            recordIp(uuid, getIp(player));
            save();
        }
        return ok;
    }

    public enum ChangeResult {
        SUCCESS,
        WRONG_CURRENT_PASSWORD,
        NOT_REGISTERED,
        NEW_PASSWORD_TOO_SHORT
    }

    /**
     * Changes a player's password. The player must supply their current password correctly.
     */
    public ChangeResult changePassword(Player player, String currentPassword, String newPassword) {
        UUID uuid = player.getUniqueId();
        if (!isRegistered(uuid)) return ChangeResult.NOT_REGISTERED;

        String salt = data.getString(path(uuid) + ".salt");
        String hash = data.getString(path(uuid) + ".hash");
        if (salt == null || hash == null || !PasswordUtil.matches(currentPassword, salt, hash)) {
            return ChangeResult.WRONG_CURRENT_PASSWORD;
        }

        if (newPassword.length() < getMinPasswordLength()) {
            return ChangeResult.NEW_PASSWORD_TOO_SHORT;
        }

        String newSalt = PasswordUtil.generateSalt();
        String newHash = PasswordUtil.hash(newPassword, newSalt);
        data.set(path(uuid) + ".salt", newSalt);
        data.set(path(uuid) + ".hash", newHash);
        save();
        return ChangeResult.SUCCESS;
    }

    /**
     * Looks up a stored UUID by username (case-insensitive), searching online players first
     * and then falling back to the saved auth.yml records.
     */
    public UUID findUuidByName(String username) {
        Player online = plugin.getServer().getPlayerExact(username);
        if (online != null) return online.getUniqueId();

        if (!data.contains("players")) return null;
        for (String key : data.getConfigurationSection("players").getKeys(false)) {
            String storedName = data.getString("players." + key + ".username");
            if (storedName != null && storedName.equalsIgnoreCase(username)) {
                try {
                    return UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                    // Corrupt key, skip it.
                }
            }
        }
        return null;
    }

    /**
     * Wipes a player's saved password data so they have to /register again next time they
     * join. Used by operators via /resetpass when a player forgets their password.
     *
     * @return true if a record was found and removed, false if the player was never registered.
     */
    public boolean resetPassword(String username) {
        UUID uuid = findUuidByName(username);
        if (uuid == null || !isRegistered(uuid)) return false;

        data.set(path(uuid) + ".salt", null);
        data.set(path(uuid) + ".hash", null);
        save();
        markLoggedOut(uuid);
        return true;
    }

    private String getIp(Player player) {
        InetSocketAddress addr = player.getAddress();
        if (addr == null || addr.getAddress() == null) return null;
        return addr.getAddress().getHostAddress();
    }

    private void recordIp(UUID uuid, String ip) {
        if (ip == null) return;
        data.set(path(uuid) + ".last-ip", ip);

        List<String> ips = data.getStringList(path(uuid) + ".ips");
        LinkedHashSet<String> merged = new LinkedHashSet<>(ips);
        merged.add(ip);
        data.set(path(uuid) + ".ips", new ArrayList<>(merged));
    }

    /**
     * Called on every join (registered or not, logged in or not) so we can track which IPs a
     * UUID has connected from and keep the "username" field fresh even for players who never
     * finish registering. Returns the usernames of any OTHER known accounts that share at
     * least one IP with this player, so callers (AuthListener) can alert staff.
     */
    public List<String> recordJoinAndCheckAlts(Player player) {
        UUID uuid = player.getUniqueId();
        String ip = getIp(player);

        data.set(path(uuid) + ".username", player.getName());
        if (ip != null) {
            recordIp(uuid, ip);
        }
        save();

        return ip == null ? Collections.emptyList() : findAltMatches(uuid, ip);
    }

    private List<String> findAltMatches(UUID selfUuid, String ip) {
        List<String> matches = new ArrayList<>();
        if (!data.contains("players")) return matches;

        for (String key : data.getConfigurationSection("players").getKeys(false)) {
            if (key.equalsIgnoreCase(selfUuid.toString())) continue;

            List<String> ips = data.getStringList("players." + key + ".ips");
            if (ips.contains(ip)) {
                String name = data.getString("players." + key + ".username");
                if (name != null) matches.add(name);
            }
        }
        return matches;
    }

    /**
     * Saves where the player currently is, so we can send them back here the next time
     * they successfully log in. Called when they quit.
     */
    public void saveLastLocation(UUID uuid, Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        String p = path(uuid) + ".last-location";
        data.set(p + ".world", loc.getWorld().getName());
        data.set(p + ".x", loc.getX());
        data.set(p + ".y", loc.getY());
        data.set(p + ".z", loc.getZ());
        data.set(p + ".yaw", loc.getYaw());
        data.set(p + ".pitch", loc.getPitch());
        save();
    }

    /**
     * Returns the location this player was at when they last quit, or null if we have none
     * saved (first time joining) or the world no longer exists.
     */
    public Location getLastLocation(UUID uuid) {
        String p = path(uuid) + ".last-location";
        if (!data.contains(p + ".world")) return null;

        World world = Bukkit.getWorld(data.getString(p + ".world"));
        if (world == null) return null;

        return new Location(
                world,
                data.getDouble(p + ".x"),
                data.getDouble(p + ".y"),
                data.getDouble(p + ".z"),
                (float) data.getDouble(p + ".yaw"),
                (float) data.getDouble(p + ".pitch")
        );
    }

    /**
     * Read-only snapshot of everything we know about an account, for /vyntricuhc passinfo.
     * Deliberately does NOT include the password hash/salt.
     */
    public static final class AccountInfo {
        public final String username;
        public final boolean registered;
        public final Long registeredAt;
        public final Long lastLogin;
        public final String lastIp;
        public final List<String> knownIps;
        public final List<String> possibleAlts;

        AccountInfo(String username, boolean registered, Long registeredAt, Long lastLogin,
                    String lastIp, List<String> knownIps, List<String> possibleAlts) {
            this.username = username;
            this.registered = registered;
            this.registeredAt = registeredAt;
            this.lastLogin = lastLogin;
            this.lastIp = lastIp;
            this.knownIps = knownIps;
            this.possibleAlts = possibleAlts;
        }
    }

    /**
     * Looks up account info by username for the /vyntricuhc passinfo command.
     * Returns null if we have no record at all for that name.
     */
    public AccountInfo getAccountInfo(String username) {
        UUID uuid = findUuidByName(username);
        if (uuid == null || !data.contains(path(uuid))) return null;

        String storedUsername = data.getString(path(uuid) + ".username", username);
        boolean registered = isRegistered(uuid);
        long registeredAtRaw = data.getLong(path(uuid) + ".registered-at", 0);
        long lastLoginRaw = data.getLong(path(uuid) + ".last-login", 0);
        String lastIp = data.getString(path(uuid) + ".last-ip");
        List<String> knownIps = data.getStringList(path(uuid) + ".ips");

        List<String> alts = new ArrayList<>();
        for (String ip : knownIps) {
            for (String match : findAltMatches(uuid, ip)) {
                if (!alts.contains(match)) alts.add(match);
            }
        }

        return new AccountInfo(
                storedUsername,
                registered,
                registeredAtRaw == 0 ? null : registeredAtRaw,
                lastLoginRaw == 0 ? null : lastLoginRaw,
                lastIp,
                knownIps,
                alts
        );
    }
}

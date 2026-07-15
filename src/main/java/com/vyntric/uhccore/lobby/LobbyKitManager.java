package com.vyntric.uhccore.lobby;

import com.vyntric.uhccore.VyntricUHCCore;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds and hands out the waiting-lobby kit (team sword + info items - see LobbyKitItem)
 * and clears it again the instant the round starts. Each item is stamped with a persistent
 * data marker so it's identified reliably even if an admin renames/re-materials it in
 * config.yml.
 */
public class LobbyKitManager {

    private static final class Entry {
        final boolean enabled;
        final int slot;
        final Material material;
        final String name;
        final List<String> lore;

        Entry(boolean enabled, int slot, Material material, String name, List<String> lore) {
            this.enabled = enabled;
            this.slot = slot;
            this.material = material;
            this.name = name;
            this.lore = lore;
        }
    }

    private final VyntricUHCCore plugin;
    private final NamespacedKey kitKey;
    private boolean enabled;
    private final Map<LobbyKitItem, Entry> entries = new EnumMap<>(LobbyKitItem.class);

    public LobbyKitManager(VyntricUHCCore plugin) {
        this.plugin = plugin;
        this.kitKey = new NamespacedKey(plugin, "lobby_kit_item");
        loadConfig();
    }

    public void loadConfig() {
        var cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("lobby-kit.enabled", true);
        entries.clear();

        for (LobbyKitItem item : LobbyKitItem.values()) {
            String path = "lobby-kit.items." + item.name().toLowerCase();
            boolean itemEnabled = cfg.getBoolean(path + ".enabled", true);
            int slot = cfg.getInt(path + ".slot", item.getDefaultSlot());

            Material material = Material.matchMaterial(
                    cfg.getString(path + ".material", item.getDefaultMaterial().name()));
            if (material == null) material = item.getDefaultMaterial();

            String name = cfg.getString(path + ".name", item.getDefaultName());

            List<String> lore = cfg.getStringList(path + ".lore");
            if (lore.isEmpty()) lore = item.getDefaultLore();

            entries.put(item, new Entry(itemEnabled, slot, material, name, lore));
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Wipes the player's inventory and gives them the configured lobby kit. Safe to call
     * repeatedly (e.g. every time they're placed back in the waiting lobby).
     */
    public void giveKit(Player player) {
        if (!enabled) return;
        PlayerInventory inv = player.getInventory();
        inv.clear();

        for (Map.Entry<LobbyKitItem, Entry> mapEntry : entries.entrySet()) {
            Entry entry = mapEntry.getValue();
            if (!entry.enabled) continue;
            inv.setItem(entry.slot, build(mapEntry.getKey(), entry));
        }
    }

    private ItemStack build(LobbyKitItem item, Entry entry) {
        ItemStack stack = new ItemStack(entry.material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', entry.name));
            meta.setLore(entry.lore.stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .collect(Collectors.toList()));
            meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, item.name());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Which kit item (if any) this stack is, based on its persistent data marker - not its
     * display name or material, so config overrides never break detection.
     */
    public LobbyKitItem identify(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        ItemMeta meta = stack.getItemMeta();
        String raw = meta.getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return LobbyKitItem.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isKitItem(ItemStack stack) {
        return identify(stack) != null;
    }

    /**
     * Strips every kit-marked item out of the player's inventory. Called the instant
     * /vyntricuhc start runs so the round begins with a clean inventory.
     */
    public void clearKit(Player player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (isKitItem(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
    }
}

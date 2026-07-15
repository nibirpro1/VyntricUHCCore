package com.vyntric.uhccore.lobby;

import org.bukkit.Material;

import java.util.List;

/**
 * The items handed out to every player while the game is in the waiting lobby (see
 * LobbyKitManager). Defaults can be overridden per-item in config.yml under
 * "lobby-kit.items.&lt;name&gt;" (enabled/slot/material/name/lore).
 */
public enum LobbyKitItem {

    // Any sword works for TeamInteractListener's invite/kick logic (right-click a player to
    // invite, right-click a teammate while holding a sword to kick) - this just makes sure
    // everyone actually has one in the lobby instead of an empty hand.
    TEAM_SWORD(0, Material.IRON_SWORD, "&c&lTeam Tool", List.of(
            "&7Right-click a player to invite",
            "&7them to your team.",
            "&7Hold this & right-click a",
            "&7teammate to kick them."
    )),
    SCENARIOS(2, Material.PAPER, "&d&lScenarios", List.of(
            "&7Click to see which scenarios",
            "&7are active for this round."
    )),
    TEAM_INFO(3, Material.AMETHYST_SHARD, "&5&lMy Team", List.of(
            "&7Click to view your current",
            "&7team and its members."
    )),
    STATS(4, Material.DIAMOND, "&b&lMy Stats", List.of(
            "&7Click to view your wins,",
            "&7kills and deaths."
    ));

    private final int defaultSlot;
    private final Material defaultMaterial;
    private final String defaultName;
    private final List<String> defaultLore;

    LobbyKitItem(int defaultSlot, Material defaultMaterial, String defaultName, List<String> defaultLore) {
        this.defaultSlot = defaultSlot;
        this.defaultMaterial = defaultMaterial;
        this.defaultName = defaultName;
        this.defaultLore = defaultLore;
    }

    public int getDefaultSlot() {
        return defaultSlot;
    }

    public Material getDefaultMaterial() {
        return defaultMaterial;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public List<String> getDefaultLore() {
        return defaultLore;
    }
}

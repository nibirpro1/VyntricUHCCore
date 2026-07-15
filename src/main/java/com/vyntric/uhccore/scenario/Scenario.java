package com.vyntric.uhccore.scenario;

/**
 * All scenarios the plugin knows how to run. Enabling/disabling is handled by
 * {@link ScenarioManager}; the actual gameplay effects live in
 * {@link com.vyntric.uhccore.listeners.ScenarioListener}.
 */
public enum Scenario {

    TIMBER("Timber", "Break the base log of a tree and the whole tree falls with it."),
    DOUBLE_ORES("Double Ores", "Every ore block mined drops double resources."),
    NO_TRADING("No Trading", "Villager and wandering trader trading is disabled."),
    BAREBONES("Barebones", "No natural health regeneration - only golden apples/potions heal you."),
    CUTCLEAN("Cutclean", "Logs are mined directly into planks - no crafting table needed for wood.");

    private final String displayName;
    private final String description;

    Scenario(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Matches a config/command string like "timber" or "double_ores" (case/format-insensitive)
     * to its enum constant. Returns null if nothing matches.
     */
    public static Scenario fromString(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        for (Scenario scenario : values()) {
            if (scenario.name().equals(normalized)) return scenario;
        }
        return null;
    }
}

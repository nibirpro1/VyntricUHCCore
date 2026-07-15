package com.vyntric.uhccore.scenario;

import com.vyntric.uhccore.VyntricUHCCore;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps track of which {@link Scenario}s are currently enabled for the round. Toggled with
 * /vyntricuhc scenario enable|disable|list, persisted to config.yml under "scenarios.enabled"
 * so the selection survives a restart.
 */
public class ScenarioManager {

    private final VyntricUHCCore plugin;
    private final Set<Scenario> enabled = EnumSet.noneOf(Scenario.class);

    public ScenarioManager(VyntricUHCCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        enabled.clear();
        List<String> saved = plugin.getConfig().getStringList("scenarios.enabled");
        for (String raw : saved) {
            Scenario scenario = Scenario.fromString(raw);
            if (scenario != null) enabled.add(scenario);
        }
    }

    private void save() {
        List<String> names = enabled.stream().map(Enum::name).collect(Collectors.toList());
        plugin.getConfig().set("scenarios.enabled", names);
        plugin.saveConfig();
    }

    public boolean enable(Scenario scenario) {
        boolean added = enabled.add(scenario);
        if (added) save();
        return added;
    }

    public boolean disable(Scenario scenario) {
        boolean removed = enabled.remove(scenario);
        if (removed) save();
        return removed;
    }

    public boolean isEnabled(Scenario scenario) {
        return enabled.contains(scenario);
    }

    public Set<Scenario> getEnabled() {
        return EnumSet.copyOf(enabled);
    }

    /**
     * Short comma-separated display string, e.g. "Timber, Double Ores" - used by the
     * scoreboard/tab list {scenarios} placeholder and the lobby kit info item. Returns
     * "None" if nothing is enabled.
     */
    public String describeEnabled() {
        if (enabled.isEmpty()) return "None";
        return enabled.stream().map(Scenario::getDisplayName).collect(Collectors.joining(", "));
    }
}

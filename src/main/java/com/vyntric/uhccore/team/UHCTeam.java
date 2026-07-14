package com.vyntric.uhccore.team;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class UHCTeam {

    private final String name;
    private final Set<UUID> members = new HashSet<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private UUID owner;

    public UHCTeam(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public void addMember(UUID uuid) {
        members.add(uuid);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        eliminated.remove(uuid);
    }

    public void markMemberEliminated(UUID uuid) {
        eliminated.add(uuid);
    }

    public boolean isEliminated(UUID uuid) {
        return eliminated.contains(uuid);
    }

    public void unmarkEliminated(UUID uuid) {
        eliminated.remove(uuid);
    }

    public void resetEliminated() {
        eliminated.clear();
    }

    public boolean hasAliveMembers() {
        return members.stream().anyMatch(m -> !eliminated.contains(m));
    }

    public boolean isFullyEliminated() {
        return !members.isEmpty() && !hasAliveMembers();
    }
}

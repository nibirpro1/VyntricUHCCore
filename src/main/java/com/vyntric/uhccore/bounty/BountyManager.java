package com.vyntric.uhccore.bounty;

import com.vyntric.uhccore.VyntricUHCCore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple in-memory bounty board. Anyone can put a bounty (a points value - no economy
 * plugin dependency) on a player; whoever gets the kill is announced as the collector and
 * the bounty amount is added to their stats-tracked "kills" weight is not touched, it's
 * purely cosmetic/bragging-rights unless the server wants to wire it into a currency plugin
 * later.
 */
public class BountyManager {

    private final VyntricUHCCore plugin;
    private final Map<UUID, Integer> bounties = new HashMap<>();

    public BountyManager(VyntricUHCCore plugin) {
        this.plugin = plugin;
    }

    public void addBounty(UUID target, int amount) {
        bounties.merge(target, amount, Integer::sum);
    }

    public boolean hasBounty(UUID target) {
        return bounties.getOrDefault(target, 0) > 0;
    }

    public int getBounty(UUID target) {
        return bounties.getOrDefault(target, 0);
    }

    /** Clears and returns the bounty that was on this player (e.g. once it's been claimed). */
    public int claimBounty(UUID target) {
        Integer amount = bounties.remove(target);
        return amount == null ? 0 : amount;
    }

    public Map<UUID, Integer> getAllBounties() {
        return bounties;
    }

    public void reset() {
        bounties.clear();
    }
}

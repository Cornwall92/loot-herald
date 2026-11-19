package com.corndwell.lootherald.discord;

import com.corndwell.lootherald.LootHeraldConfig;
import com.corndwell.lootherald.filter.LootFilter;
import com.corndwell.lootherald.model.DropItem;
import com.corndwell.lootherald.model.LootDrop;
import com.corndwell.lootherald.model.Rarity;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/** Integration-facing expectations for which parts of a reward reach the transport. */
public class LootFilterBehaviorTest
{
    @Test public void exclusionWinsOverExplicitInclusionAndUniqueBypass()
    {
        LootHeraldConfig config = new LootHeraldConfig() {
            @Override public String includeItems() { return "Example*"; }
            @Override public String excludeItems() { return "1"; }
        };
        assertTrue(new LootFilter().evaluate(DiscordFormatterTest.drop(), config).indices.isEmpty());
    }

    @Test public void unknownRarityDoesNotPassBothThresholds()
    {
        LootHeraldConfig config = new LootHeraldConfig() {
            @Override public MatchMode matchMode() { return MatchMode.VALUE_AND_RARITY; }
        };
        LootDrop drop = DiscordFormatterTest.drop(List.of(new DropItem(1, "High value", 1, 5_000_000, 1, Rarity.unknown())));
        assertTrue(new LootFilter().evaluate(drop, config).indices.isEmpty());
    }

    @Test public void totalAndFullRewardNeverReintroduceExcludedItems()
    {
        LootDrop drop = DiscordFormatterTest.drop(List.of(
            new DropItem(1, "First", 1, 40_000, 1, Rarity.unknown()),
            new DropItem(2, "Second", 1, 20_000, 1, Rarity.unknown()),
            new DropItem(3, "Excluded", 1, 100_000, 1, Rarity.unknown())));
        LootHeraldConfig combined = new LootHeraldConfig() {
            @Override public MatchMode matchMode() { return MatchMode.VALUE_ONLY; }
            @Override public String excludeItems() { return "Excluded"; }
            @Override public boolean rewardTotal() { return true; }
            @Override public boolean fullReward() { return true; }
        };
        assertEquals(List.of(0, 1), new LootFilter().evaluate(drop, combined).indices);
        LootHeraldConfig single = new LootHeraldConfig() {
            @Override public MatchMode matchMode() { return MatchMode.VALUE_ONLY; }
            @Override public String excludeItems() { return "Excluded"; }
            @Override public boolean fullReward() { return true; }
        };
        assertTrue(new LootFilter().evaluate(drop, single).indices.isEmpty());
    }

    @Test public void matchingTrimsAndTreatsRegexCharactersLiterally()
    {
        assertTrue(LootFilter.matches("  Armadyl *  , ignored", "Armadyl chestplate"));
        assertTrue(LootFilter.matches("Rune (g)", "Rune (g)"));
        assertFalse(LootFilter.matches("Rune (g)", "Rune g"));
        assertFalse(LootFilter.matches("Rune.", "Runes"));
    }

    @Test public void duplicatePetToggleIsIndependentOfValueRules()
    {
        LootDrop pet = DiscordFormatterTest.drop().toBuilder().pet(true).duplicatePet(true).build();
        assertTrue(new LootFilter().evaluate(pet, new LootHeraldConfig() { }).indices.isEmpty());
        assertEquals(List.of(0), new LootFilter().evaluate(pet, new LootHeraldConfig() {
            @Override public boolean duplicatePets() { return true; }
            @Override public int minimumValue() { return Integer.MAX_VALUE; }
        }).indices);
    }
}

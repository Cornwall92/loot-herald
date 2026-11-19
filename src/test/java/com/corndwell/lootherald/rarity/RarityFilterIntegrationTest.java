package com.corndwell.lootherald.rarity;

import com.corndwell.lootherald.LootHeraldConfig;
import com.corndwell.lootherald.filter.LootFilter;
import com.corndwell.lootherald.model.DropItem;
import com.corndwell.lootherald.model.LootDrop;
import com.corndwell.lootherald.model.Rarity;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real bundled data must preserve the boundary between context and filterable odds. */
public class RarityFilterIntegrationTest
{
	private final RarityService service = new RarityService(new Gson());

	@Test
	public void purpleSelectionWeightCannotSatisfyRarityThreshold()
	{
		Rarity rarity = service.lookup("Tombs of Amascut", "EVENT", ItemID.TUMEKENS_SHADOW_UNCHARGED, 1);
		assertTrue(rarity.getBasis().contains("1/24"));
		LootHeraldConfig settings = new LootHeraldConfig()
		{
			@Override public MatchMode matchMode() { return MatchMode.RARITY_ONLY; }
			@Override public int minimumRarity() { return 20; }
			@Override public boolean alwaysUniques() { return false; }
		};
		assertTrue(new LootFilter().evaluate(drop("Tombs of Amascut", ItemID.TUMEKENS_SHADOW_UNCHARGED, rarity), settings).indices.isEmpty());
	}

	@Test
	public void lunarChestAliasKeepsUniqueBypassButNoFabricatedProbability()
	{
		Rarity rarity = service.lookup("Lunar Chest", "EVENT", ItemID.BLOOD_MOON_HELM, 1);
		assertFalse(rarity.known());
		assertTrue(rarity.isUnique());
		LootHeraldConfig settings = new LootHeraldConfig()
		{
			@Override public MatchMode matchMode() { return MatchMode.RARITY_ONLY; }
			@Override public int minimumRarity() { return 10000; }
		};
		assertEquals(List.of(0), new LootFilter().evaluate(drop("Lunar Chest", ItemID.BLOOD_MOON_HELM, rarity), settings).indices);
	}

	@Test
	public void pickpocketSourceCannotReadNpcRateForSameNamedCreature()
	{
		Rarity npc = service.lookup("Dagannoth Supreme", "NPC", ItemID.DRAGON_AXE, 1);
		Rarity pickpocket = service.lookup("Dagannoth Supreme", "PICKPOCKET", ItemID.DRAGON_AXE, 1);
		assertTrue(npc.known());
		assertFalse(pickpocket.known());
	}

	private static LootDrop drop(String source, int itemId, Rarity rarity)
	{
		return LootDrop.builder().id("rarity-integration").session(1).received(Instant.EPOCH)
			.player("Test player").accountType("").source(SourceNames.normalize(source)).rawSource(source)
			.type("EVENT").combatLevel(0).batchCount(1).pet(false).duplicatePet(false).collectionLog(false)
			.items(List.of(new DropItem(itemId, "Test reward", 1, -1, -1, rarity))).details(Map.of()).build();
	}
}

package com.corndwell.lootherald.rarity;

import com.corndwell.lootherald.model.Rarity;
import com.google.gson.Gson;
import java.io.StringReader;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class RarityServiceTest
{
	private static RarityService fixture(String records)
	{
		return new RarityService(new Gson(), new StringReader("{\"Test monster\":" + records + "}"), new StringReader("{}"));
	}

	@Test
	public void selectsQuantityBeforeRarityAndDoesNotLeakRejectedRow()
	{
		RarityService service = fixture("[{\"i\":123,\"q\":132,\"d\":4},{\"i\":123,\"q\":460,\"d\":128}]");
		assertEquals(1.0 / 128, service.lookup("Test monster", "NPC", 123, 460).getProbability(), 1e-12);
		assertEquals(1.0 / 4, service.lookup("Test monster", "NPC", 123, 132).getProbability(), 1e-12);
		assertTrue(service.lookup("Test monster", "NPC", 123, 132).getBasis().contains("this stack per encounter"));
		assertFalse(service.lookup("Test monster", "NPC", 123, 999).known());
	}

	@Test
	public void acceptsRangeEndpointsButDoesNotGuessOverlappingConditionalRows()
	{
		RarityService service = fixture("[{\"i\":123,\"m\":1,\"n\":5,\"d\":10},{\"i\":123,\"q\":5,\"d\":100}]");
		assertEquals(0.1, service.lookup("Test monster", "NPC", 123, 1).getProbability(), 1e-12);
		assertTrue(service.lookup("Test monster", "NPC", 123, 1).getBasis().contains("any quantity in range"));
		assertFalse(service.lookup("Test monster", "NPC", 123, 5).known());
		assertFalse(service.lookup("Test monster", "NPC", 123, 6).known());
	}

	@Test
	public void exactQuantityRepeatedRollsUseBinomialProbability()
	{
		RarityService service = fixture("[{\"i\":123,\"q\":250,\"d\":25,\"r\":2}]");
		assertEquals(2 * 0.04 * 0.96, service.lookup("Test monster", "NPC", 123, 250).getProbability(), 1e-12);
		assertEquals(0.04 * 0.04, service.lookup("Test monster", "NPC", 123, 500).getProbability(), 1e-12);
		assertTrue(service.lookup("Test monster", "NPC", 123, 500).getBasis().contains("across 2 rolls per encounter"));
	}

	@Test
	public void repeatedRangeDoesNotClaimExactStackOdds()
	{
		RarityService service = fixture("[{\"i\":123,\"m\":4,\"n\":5,\"d\":25,\"r\":2}]");
		Rarity result = service.lookup("Test monster", "NPC", 123, 8);
		assertEquals(0.04 * 0.04, result.getProbability(), 1e-12);
		assertTrue(result.getBasis().contains("table-entry chance across 2 rolls; range matched"));
	}

	@Test
	public void unknownDataAndVariantsNeverBecomeCertain()
	{
		RarityService service = fixture("[{\"i\":123,\"q\":1,\"d\":20}]");
		assertFalse(service.lookup("Missing monster", "NPC", 123, 1).known());
		assertFalse(service.lookup("Test monster", "NPC", 124, 1).known());
		assertFalse(service.lookup("Test monster", "NPC", 123, 0).known());
		assertFalse(service.lookup("Test monster", "PLAYER", 123, 1).known());
		assertFalse(service.lookup("Test monster", "PICKPOCKET", 123, 1).known());
	}

	@Test
	public void malformedRecordsAreExcluded()
	{
		RarityService service = fixture("[{\"i\":123,\"q\":1,\"d\":0},{\"i\":123,\"m\":4,\"n\":2,\"d\":20},{\"i\":124,\"q\":1,\"r\":101,\"d\":20}]");
		assertFalse(service.lookup("Test monster", "NPC", 123, 1).known());
		assertFalse(service.lookup("Test monster", "NPC", 124, 1).known());
	}

	@Test
	public void bundledSnapshotLoadsAndSupportsModernBosses()
	{
		RarityService service = new RarityService(new Gson());
		assertTrue(service.getSourceCount() >= 600);
		Rarity dragonAxe = service.lookup("Dagannoth Supreme", "NPC", ItemID.DRAGON_AXE, 1);
		assertTrue(dragonAxe.known());
		assertEquals(1.0 / 128, dragonAxe.getProbability(), 1e-12);
		assertTrue(service.lookup("Araxxor", "NPC", ItemID.ARAXYTE_FANG, 1).isUnique());
	}

	@Test
	public void dynamicOddsRemainUnknownWhileUniqueClassificationSurvives()
	{
		RarityService service = new RarityService(new Gson());
		Rarity shadow = service.lookup("Tombs of Amascut: Expert Mode", "EVENT", ItemID.TUMEKENS_SHADOW_UNCHARGED, 1);
		assertFalse(shadow.known());
		assertTrue(shadow.isUnique());
		assertTrue(shadow.getBasis().contains("1/24"));
		assertTrue(shadow.getBasis().contains("not per-raid"));
		assertFalse(service.lookup("Nex", "NPC", ItemID.DRAGON_AXE, 1).known());
		assertFalse(service.lookup("Pet", "PET", ItemID.DRAGON_AXE, 1).known());
	}

	@Test
	public void pickpocketBonusesDoNotChangeAcquisitionChance()
	{
		RarityService service = new RarityService(new Gson());
		assertEquals(1.0 / 5000, service.lookup("Caninelle Draynar", "PICKPOCKET", ItemID.BLOOD_SHARD, 2).getProbability(), 1e-12);
		assertFalse(service.lookup("Caninelle Draynar", "NPC", ItemID.BLOOD_SHARD, 2).known());
		assertFalse(service.lookup("Master Farmer", "PICKPOCKET", ItemID.SNAPDRAGON_SEED, 1).known());
	}

	@Test
	public void coinsUseTheDataGeneratorsCanonicalCoinId()
	{
		RarityService service = fixture("[{\"i\":" + ItemID.FAKE_COINS + ",\"q\":460,\"d\":128}]");
		assertEquals(1.0 / 128, service.lookup("Test monster", "NPC", ItemID.COINS, 460).getProbability(), 1e-12);
	}
}

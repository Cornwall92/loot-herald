package com.corndwell.lootherald.detection;

import com.corndwell.lootherald.model.DropItem;
import com.corndwell.lootherald.model.Rarity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static com.corndwell.lootherald.detection.RewardGate.Decision.ACCEPT;
import static com.corndwell.lootherald.detection.RewardGate.Decision.DUPLICATE;
import static com.corndwell.lootherald.detection.RewardGate.Decision.REVIEW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RewardGateTest
{
	private static DropItem item(int id, long quantity)
	{
		return new DropItem(id, "Item " + id, quantity, 1, 1, Rarity.unknown());
	}

	private static List<DropItem> reward()
	{
		return Arrays.asList(item(1, 100), item(2, 3));
	}

	@Test
	public void barrowsReopenAndPartialWithdrawalAreNotNewAcquisitions()
	{
		RewardGate gate = new RewardGate();
		gate.onChat("Your Barrows chest count is: <col=ff0000>1,234</col>.", 20);
		assertEquals(ACCEPT, gate.decide("Barrows", "EVENT", reward(), 20));
		assertEquals(DUPLICATE, gate.decide("Barrows", "EVENT", reward(), 21));
		assertEquals(DUPLICATE, gate.decide("Barrows", "EVENT", Collections.singletonList(item(1, 50)), 22));
		// A delayed reopening remains held, without silently discarding a possibly
		// new award whose count message was not available.
		assertEquals(REVIEW, gate.decide("Barrows", "EVENT", reward(), 80));
	}

	@Test
	public void identicalNewBarrowsAwardIsAcceptedWhenCountAdvances()
	{
		RewardGate gate = new RewardGate();
		gate.onChat("Your Barrows chest count is: 8", 10);
		assertEquals(ACCEPT, gate.decide("Barrows", "EVENT", reward(), 10));
		gate.onChat("Your Barrows chest count is: 9", 100);
		assertEquals(ACCEPT, gate.decide("Barrows", "EVENT", reward(), 100));
	}

	@Test
	public void unknownLifecyclePreservesAmbiguousRewardForReview()
	{
		RewardGate gate = new RewardGate();
		assertEquals(ACCEPT, gate.decide("Lunar Chest", "EVENT", reward(), 10));
		assertEquals(REVIEW, gate.decide("Lunar Chest", "EVENT", reward(), 11));
		assertEquals(REVIEW, gate.decide("Lunar Chest", "EVENT", reward(), 10_000));
		assertEquals(REVIEW, gate.decide("Lunar Chest", "EVENT", Collections.singletonList(item(2, 1)), 10_001));
		assertEquals(ACCEPT, gate.decide("Lunar Chest", "EVENT", Collections.singletonList(item(3, 1)), 10_002));
	}

	@Test
	public void lunarCountAndDisplayAliasesShareState()
	{
		RewardGate gate = new RewardGate();
		gate.onChat("Your Lunar chest count is: 31.", 10);
		assertEquals(ACCEPT, gate.decide("Lunar Chest", "EVENT", reward(), 10));
		assertEquals(DUPLICATE, gate.decide("Moons of Peril", "EVENT", reward(), 11));
		gate.onChat("Your Lunar chest count is: 32.", 100);
		assertEquals(ACCEPT, gate.decide("Lunar Chest", "EVENT", reward(), 100));
	}

	@Test
	public void raidDifficultyCountersAreIndependent()
	{
		RewardGate gate = new RewardGate();
		gate.onChat("Your completed Tombs of Amascut count is: 5.", 10);
		assertEquals(ACCEPT, gate.decide("Tombs of Amascut", "EVENT", reward(), 10));
		gate.onChat("Your completed Tombs of Amascut: Expert Mode count is: 5.", 100);
		assertEquals(ACCEPT, gate.decide("Tombs of Amascut", "EVENT", reward(), 100));
		assertEquals(DUPLICATE, gate.decide("Tombs of Amascut: Expert Mode", "EVENT", reward(), 101));
		gate.onChat("Your completed Tombs of Amascut: Expert Mode count is: 5.", 150);
		assertEquals(DUPLICATE, gate.decide("Tombs of Amascut", "EVENT", reward(), 150));
		gate.onChat("Your completed Tombs of Amascut count is: 6.", 200);
		assertEquals(ACCEPT, gate.decide("Tombs of Amascut", "EVENT", reward(), 200));
	}

	@Test
	public void countBeforeOrJustAfterInitialReceiptDoesNotCreateAnotherAward()
	{
		RewardGate gate = new RewardGate();
		assertEquals(ACCEPT, gate.decide("Theatre of Blood", "EVENT", reward(), 50));
		gate.onChat("Your completed Theatre of Blood count is: 7", 51);
		assertEquals(DUPLICATE, gate.decide("Theatre of Blood", "EVENT", reward(), 52));
		gate.onChat("Your completed Theatre of Blood count is: 8", 250);
		assertEquals(ACCEPT, gate.decide("Theatre of Blood", "EVENT", reward(), 250));
	}

	@Test
	public void repeatedOrOlderCountDoesNotArmNewReward()
	{
		RewardGate gate = new RewardGate();
		gate.onChat("Your completed Chambers of Xeric Challenge Mode count is: 40", 50);
		assertEquals(ACCEPT, gate.decide("Chambers of Xeric", "EVENT", reward(), 50));
		gate.onChat("Your completed Chambers of Xeric Challenge Mode count is: 40", 100);
		assertEquals(DUPLICATE, gate.decide("Chambers of Xeric", "EVENT", reward(), 100));
		gate.onChat("Your completed Chambers of Xeric Challenge Mode count is: 39", 101);
		assertEquals(DUPLICATE, gate.decide("Chambers of Xeric", "EVENT", reward(), 101));
	}

	@Test
	public void ordinaryIdenticalAcquisitionsAreNeverFingerprintDeduplicated()
	{
		RewardGate gate = new RewardGate();
		for (String source : Arrays.asList("Zulrah", "Clue Scroll (Hard)", "Crystal Chest", "Brimstone Chest", "Reward pool (Tempoross)", "Corrupted Hunllef"))
		{
			for (int i = 0; i < 3; i++)
			{
				assertEquals(source, ACCEPT, gate.decide(source, source.contains("Hunllef") || source.equals("Zulrah") ? "NPC" : "EVENT", reward(), 10));
			}
		}
	}

	@Test
	public void quantityAggregationAndOrderDoNotHideAnEcho()
	{
		RewardGate gate = new RewardGate();
		gate.onChat("Your Barrows chest count is: 1", 10);
		assertEquals(ACCEPT, gate.decide("Barrows", "EVENT", reward(), 10));
		assertEquals(DUPLICATE, gate.decide("Barrows", "EVENT", Arrays.asList(item(2, 1), item(1, 40), item(2, 2), item(1, 60)), 11));
		assertEquals(REVIEW, gate.decide("Barrows", "EVENT", Collections.singletonList(item(1, 101)), 11));
	}

	@Test
	public void snapshotsAreCopiedAndClearBeginsNewSession()
	{
		RewardGate gate = new RewardGate();
		gate.onRewardCycle("Lunar Chest");
		List<DropItem> items = new ArrayList<>(reward());
		assertEquals(ACCEPT, gate.decide("Lunar Chest", "EVENT", items, 10));
		items.clear();
		assertEquals(DUPLICATE, gate.decide("Lunar Chest", "EVENT", reward(), 11));
		gate.clear();
		assertEquals(ACCEPT, gate.decide("Lunar Chest", "EVENT", reward(), 11));
	}

	@Test
	public void explicitVerifiedNewCycleAllowsIdenticalReward()
	{
		RewardGate gate = new RewardGate();
		assertEquals(ACCEPT, gate.decide("Lunar Chest", "EVENT", reward(), 10));
		gate.onRewardCycle("Lunar Chest");
		assertEquals(ACCEPT, gate.decide("Lunar Chest", "EVENT", reward(), 100));
	}

	@Test
	public void unrelatedOrMalformedMessagesDoNotAdvanceCycle()
	{
		RewardGate gate = new RewardGate();
		assertEquals(ACCEPT, gate.decide("Barrows", "EVENT", reward(), 10));
		gate.onChat("Your Zulrah kill count is: 100", 100);
		gate.onChat("Your Barrows chest count is: 999999999999999999999999999999", 100);
		gate.onChat(null, 100);
		assertEquals(REVIEW, gate.decide("Barrows", "EVENT", reward(), 100));
		assertTrue(RewardGate.isGuardedSource("Tombs of Amascut: Expert Mode"));
		assertFalse(RewardGate.isGuardedSource("Clue Scroll (Master)"));
	}
}

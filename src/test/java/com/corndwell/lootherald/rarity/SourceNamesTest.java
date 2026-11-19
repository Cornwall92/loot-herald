package com.corndwell.lootherald.rarity;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SourceNamesTest
{
	@Test
	public void normalizesRuneLiteRewardNames()
	{
		assertEquals("Moons of Peril", SourceNames.normalize("Lunar Chest"));
		assertEquals("The Gauntlet", SourceNames.normalize("Gauntlet"));
		assertEquals("Corrupted Gauntlet", SourceNames.normalize("The Corrupted Gauntlet"));
		assertNotEquals(SourceNames.normalize("Gauntlet"), SourceNames.normalize("Corrupted Gauntlet"));
	}

	@Test
	public void preservesRaidDifficultyAndUnknownNpcVariants()
	{
		assertEquals("Tombs of Amascut: Expert Mode", SourceNames.normalize("Tombs of Amascut (Expert Mode)"));
		assertNotEquals(SourceNames.normalize("Theatre of Blood"), SourceNames.normalize("Theatre of Blood (Entry Mode)"));
		assertEquals("Goblin (level 2)", SourceNames.normalize("Goblin (level 2)"));
	}

	@Test
	public void normalizesFormattingWithoutDamagingPunctuation()
	{
		assertEquals("Kree'arra", SourceNames.normalize(" <col=ff0000>Kree&#039;arra</col> "));
		assertEquals("Moons of Peril", SourceNames.normalize(" lunar\u00a0  chest "));
		assertEquals("Unknown source", SourceNames.normalize(null));
	}
}

package com.corndwell.lootherald.model;

import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RarityTest
{
	@Test
	public void unknownIsNotCertainAndMayStillBeUnique()
	{
		assertFalse(Rarity.unknown().known());
		assertEquals("Unknown", Rarity.unknown().formatFraction());
		assertEquals("Unknown", Rarity.unknown().formatPercent());
		Rarity unique = new Rarity(null, "Per reward", true);
		assertTrue(unique.isUnique());
		assertFalse(unique.known());
	}

	@Test
	public void invalidProbabilitiesBecomeUnknown()
	{
		for (double value : new double[]{0, -0.5, 1.01, Double.NaN, Double.POSITIVE_INFINITY})
		{
			assertFalse(new Rarity(value, "Per kill", false).known());
		}
	}

	@Test
	public void knownValuesAreFormattedIndependentlyOfLocale()
	{
		Locale original = Locale.getDefault();
		try
		{
			Locale.setDefault(Locale.GERMANY);
			Rarity rarity = new Rarity(1d / 64d, "Per kill", false);
			assertTrue(rarity.known());
			assertEquals("1/64", rarity.formatFraction());
			assertEquals("1.5625%", rarity.formatPercent());
			assertEquals("100%", new Rarity(1d, "Guaranteed", false).formatPercent());
		}
		finally
		{
			Locale.setDefault(original);
		}
	}

	@Test
	public void verySmallKnownRateDoesNotDisplayZeroPercent()
	{
		assertEquals("1E-18%", new Rarity(1e-20, "Per roll", false).formatPercent());
	}
}

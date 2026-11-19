package com.corndwell.lootherald.model;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DropItemTest
{
	@Test
	public void snapshotTotalsUseLongAndSaturateOverflow()
	{
		DropItem item = new DropItem(1, "Valuable item", 3, 2_000_000_000L, Long.MAX_VALUE, Rarity.unknown());
		assertEquals(6_000_000_000L, item.getGeTotal());
		assertEquals(Long.MAX_VALUE, item.getHaTotal());
	}

	@Test
	public void unknownAndZeroPricesRemainDistinct()
	{
		DropItem item = new DropItem(-1, "Unknown pet", Long.MAX_VALUE, -1, 0, Rarity.unknown());
		assertEquals(-1, item.getGeTotal());
		assertEquals(0, item.getHaTotal());
	}

	@Test
	public void maximumExactProductDoesNotOverflow()
	{
		DropItem item = new DropItem(1, "Coins", Long.MAX_VALUE, 1, 1, Rarity.unknown());
		assertEquals(Long.MAX_VALUE, item.getGeTotal());
	}

	@Test(expected = IllegalArgumentException.class)
	public void zeroQuantityIsRejected()
	{
		new DropItem(1, "Item", 0, 1, 1, Rarity.unknown());
	}

	@Test(expected = IllegalArgumentException.class)
	public void invalidPriceCannotBecomeAFalsePositiveValue()
	{
		new DropItem(1, "Item", 1, -2, 1, Rarity.unknown());
	}
}

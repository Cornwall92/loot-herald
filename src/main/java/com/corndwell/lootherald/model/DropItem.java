package com.corndwell.lootherald.model;

import java.util.Objects;
import lombok.Value;

/** Values are a receipt-time snapshot. Unit prices of -1 represent unknown values. */
@Value
public class DropItem
{
	int id;
	String name;
	long quantity;
	long gePrice;
	long haPrice;
	Rarity rarity;

	public DropItem(int id, String name, long quantity, long gePrice, long haPrice, Rarity rarity)
	{
		if (quantity <= 0)
		{
			throw new IllegalArgumentException("Item quantity must be positive");
		}
		if (gePrice < -1 || haPrice < -1)
		{
			throw new IllegalArgumentException("Item prices must be non-negative or unknown (-1)");
		}
		this.id = id;
		this.name = Objects.requireNonNull(name, "name");
		this.quantity = quantity;
		this.gePrice = gePrice;
		this.haPrice = haPrice;
		this.rarity = Objects.requireNonNull(rarity, "rarity");
	}

	public long getGeTotal()
	{
		return total(gePrice);
	}

	public long getHaTotal()
	{
		return total(haPrice);
	}

	private long total(long unitPrice)
	{
		if (unitPrice < 0)
		{
			return -1;
		}
		if (unitPrice != 0 && quantity > Long.MAX_VALUE / unitPrice)
		{
			return Long.MAX_VALUE;
		}
		return quantity * unitPrice;
	}
}

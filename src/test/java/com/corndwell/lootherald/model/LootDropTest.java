package com.corndwell.lootherald.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class LootDropTest
{
	@Test
	public void builderAndToBuilderDefensivelyCopyCollections()
	{
		List<DropItem> items = new ArrayList<>();
		items.add(new DropItem(1, "Item", 2, 3, 4, Rarity.unknown()));
		Map<String, String> details = new HashMap<>();
		details.put("KC", "7");
		LootDrop drop = LootDrop.builder().id("event").session(1).received(Instant.EPOCH)
			.player("Player").accountType("Normal").source("Chest").rawSource("Chest")
			.type("EVENT").combatLevel(-1).batchCount(1).items(items).details(details).build();
		LootDrop copied = drop.toBuilder().player("Other").build();
		items.clear();
		details.put("KC", "8");
		assertEquals(1, drop.getItems().size());
		assertEquals("7", drop.getDetails().get("KC"));
		assertEquals("Player", drop.getPlayer());
		assertEquals("Other", copied.getPlayer());
		assertImmutable(() -> drop.getItems().clear());
		assertImmutable(() -> copied.getDetails().put("KC", "9"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void emptyAcquisitionIsRejected()
	{
		new LootDrop("event", 1, Instant.EPOCH, "Player", "Normal", "Chest", "Chest", "EVENT",
			-1, 1, false, false, false, List.of(), Map.of());
	}

	private static void assertImmutable(Runnable mutation)
	{
		try
		{
			mutation.run();
			fail("Collection was mutable");
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected.
		}
	}
}

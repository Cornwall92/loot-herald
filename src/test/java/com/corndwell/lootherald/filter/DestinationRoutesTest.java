package com.corndwell.lootherald.filter;

import com.corndwell.lootherald.discord.Destination;
import com.corndwell.lootherald.model.DropItem;
import com.corndwell.lootherald.model.LootDrop;
import com.corndwell.lootherald.model.Rarity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class DestinationRoutesTest
{
	private static final String CONFIGURED = "Clan | https://discord.com/api/webhooks/1/abcdefghijklmnop\n"
		+ "Personal | https://discord.com/api/webhooks/2/abcdefghijklmnop\n"
		+ "Raids | https://discord.com/api/webhooks/3/abcdefghijklmnop";

	@Test
	public void blankConfigurationDefaultsToAllWithNoExtraThreshold()
	{
		List<Destination> destinations = destinations();
		LootDrop drop = drop("Chest", "Chest", "Corndwell");
		for (String config : new String[]{null, "", " \n\t\r\n"})
		{
			DestinationRoutes routes = new DestinationRoutes(config, destinations);
			assertEquals(destinations, routes.matching(drop, null, true));
			assertEquals(destinations, routes.matching(drop, null, false));
			assertEquals(0, routes.minimumValue(destinations.get(0).getId(), drop));
		}
	}

	@Test
	public void matchesNormalizedOrRawSourceAndPlayerWithTrimmedCaseInsensitivePatterns()
	{
		List<Destination> destinations = destinations();
		DestinationRoutes routes = new DestinationRoutes("  clan \t | The Corrupted* | CORND* | 1000000 \n"
			+ "Raids | Gauntlet | Corndwell | 0", destinations);
		LootDrop drop = drop("Gauntlet", "The Corrupted Gauntlet", "Corndwell");
		assertEquals(List.of(destinations.get(0), destinations.get(2)), routes.matching(drop, null, true));
		assertEquals(1_000_000, routes.minimumValue(destinations.get(0).getId(), drop));
		assertTrue(routes.matching(drop("Gauntlet", "The Corrupted Gauntlet", "Other"), null, true).isEmpty());
	}

	@Test
	public void multipleMatchingRulesUseLeastMinimumAndDoNotDuplicateDestination()
	{
		List<Destination> destinations = destinations();
		DestinationRoutes routes = new DestinationRoutes("Clan | * | * | 10000\n"
			+ "Clan | Tombs* | Corndwell | 5000\nClan | Chest | * | 1", destinations);
		LootDrop drop = drop("Tombs of Amascut", "Tombs of Amascut", "Corndwell");
		assertEquals(List.of(destinations.get(0)), routes.matching(drop, null, true));
		assertEquals(5000, routes.minimumValue(destinations.get(0).getId(), drop));
		assertEquals(Long.MAX_VALUE, routes.minimumValue(destinations.get(1).getId(), drop));
	}

	@Test
	public void manualExplicitDestinationOverridesRoutesWhileManualAllRetainsThem()
	{
		List<Destination> destinations = destinations();
		DestinationRoutes routes = new DestinationRoutes("Clan | Tombs* | Corndwell | 1000000", destinations);
		LootDrop unmatched = drop("Chest", "Chest", "Other");
		assertTrue(routes.matching(unmatched, null, false).isEmpty());
		assertTrue(routes.matching(unmatched, destinations.get(0).getId(), true).isEmpty());
		assertEquals(List.of(destinations.get(0)), routes.matching(unmatched, destinations.get(0).getId(), false));
		assertEquals(List.of(destinations.get(1)), routes.matching(unmatched, destinations.get(1).getId(), false));
		assertEquals(Long.MAX_VALUE, routes.minimumValue(destinations.get(0).getId(), unmatched));
	}

	@Test
	public void selectedAutomaticDestinationStillMatchesRouteAndUnknownSelectionSendsNowhere()
	{
		List<Destination> destinations = destinations();
		DestinationRoutes routes = new DestinationRoutes("Clan | * | * | 0\nPersonal | * | * | 0", destinations);
		LootDrop drop = drop("Chest", "Chest", "Player");
		assertEquals(List.of(destinations.get(1)), routes.matching(drop, destinations.get(1).getId(), true));
		assertTrue(routes.matching(drop, "unknown", false).isEmpty());
		assertTrue(routes.matching(drop, "unknown", true).isEmpty());
		assertEquals(Long.MAX_VALUE, routes.minimumValue("unknown", drop));
	}

	@Test
	public void blankSourceAndPlayerPatternsMeanAnyAndBlankRowsAreIgnored()
	{
		List<Destination> destinations = destinations();
		DestinationRoutes routes = new DestinationRoutes("\n Clan |   |\t | 0 \n\n", destinations);
		assertEquals(List.of(destinations.get(0)), routes.matching(drop("Chest", "Chest", "Player"), null, true));
	}

	@Test
	public void malformedRowsAndOverflowAreRejectedWithoutEchoingUserValues()
	{
		for (String row : new String[]{"Clan | * | *", "Clan | * | * | 1 | extra", "Clan | * | * |",
			"Clan | * | * | -1", "Clan | * | * | +1", "Clan | * | * | 1m", "Clan | * | * | 1.5",
			"Clan | * | * | 9223372036854775808"})
		{
			IllegalArgumentException error = invalid("\n" + row);
			assertTrue(error.getMessage().contains("line 2"));
			assertFalse(error.getMessage().contains(row));
		}
	}

	@Test
	public void laterInvalidRowsDoNotProducePartiallyUsableRoutes()
	{
		IllegalArgumentException error = invalid("Clan | * | * | 0\nsecret-token-name | * | * | 0");
		assertTrue(error.getMessage().contains("line 2"));
		assertFalse(error.getMessage().contains("secret-token-name"));
	}

	@Test
	public void duplicateDestinationNamesAreRejectedEvenWhenRoutesAreBlank()
	{
		List<Destination> ambiguous = Destination.parse("Clan | https://discord.com/api/webhooks/1/abcdefghijklmnop\n"
			+ "clan | https://discord.com/api/webhooks/2/abcdefghijklmnop");
		try
		{
			new DestinationRoutes("", ambiguous);
			fail("Ambiguous destination names were accepted");
		}
		catch (IllegalArgumentException error)
		{
			assertFalse(error.getMessage().contains("abcdefghijklmnop"));
		}
	}

	@Test
	public void regexMetacharactersRemainLiteral()
	{
		DestinationRoutes routes = new DestinationRoutes("Clan | (Chest)+ | * | 0", destinations());
		assertTrue(routes.matching(drop("Chest", "Chest", "Player"), null, true).isEmpty());
		assertEquals(1, routes.matching(drop("(Chest)+", "(Chest)+", "Player"), null, true).size());
	}

	@Test
	public void destinationInputAndReturnedListsAreImmutableSnapshots()
	{
		List<Destination> supplied = new ArrayList<>(destinations());
		DestinationRoutes routes = new DestinationRoutes("", supplied);
		supplied.clear();
		List<Destination> matched = routes.matching(drop("Chest", "Chest", "Player"), null, true);
		assertEquals(3, matched.size());
		try
		{
			matched.clear();
			fail("Returned destinations were mutable");
		}
		catch (UnsupportedOperationException expected)
		{
			// Expected.
		}
	}

	@Test
	public void maximumWholeNumberMinimumIsAccepted()
	{
		List<Destination> destinations = destinations();
		DestinationRoutes routes = new DestinationRoutes("Clan | * | * | " + Long.MAX_VALUE, destinations);
		assertEquals(Long.MAX_VALUE, routes.minimumValue(destinations.get(0).getId(), drop("Chest", "Chest", "Player")));
	}

	private static IllegalArgumentException invalid(String config)
	{
		try
		{
			new DestinationRoutes(config, destinations());
			throw new AssertionError("Invalid route was accepted");
		}
		catch (IllegalArgumentException expected)
		{
			return expected;
		}
	}

	private static List<Destination> destinations()
	{
		return Destination.parse(CONFIGURED);
	}

	private static LootDrop drop(String source, String rawSource, String player)
	{
		return new LootDrop("event", 1, Instant.EPOCH, player, "Normal", source, rawSource, "EVENT", -1, 1,
			false, false, false, List.of(new DropItem(1, "Item", 1, 1, 1, Rarity.unknown())), Map.of());
	}
}

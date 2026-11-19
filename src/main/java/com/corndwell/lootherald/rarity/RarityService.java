package com.corndwell.lootherald.rarity;

import com.corndwell.lootherald.model.Rarity;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;

/**
 * Offline reference-table lookup. Callers supply ItemManager.canonicalize(itemId),
 * so noted drops use the unnoted item ID. Arbitrary item variants are deliberately
 * not merged: doses, charge states and upgraded items can have different odds.
 * Construct on the plugin worker executor; subsequent lookups perform no I/O.
 */
@Singleton
@Slf4j
public final class RarityService
{
	private static final Set<String> CONTEXTUAL_SOURCES = Set.of(
		"nex", "the nightmare", "yama", "the hueycoatl", "hueycoatl",
		"royal titans", "branda the fire queen", "eldric the ice king",
		"duke sucellus", "vardorvis", "the leviathan", "the whisperer");
	private final Map<String, Map<Integer, List<DropRule>>> npcTables;
	private final Map<String, Map<Integer, List<DropRule>>> pickpocketTables;
	private final Map<String, Set<Integer>> uniqueItems;

	@Inject
	public RarityService(Gson gson)
	{
		this.npcTables = loadTables(gson, "/rarity/npc-drops.json");
		this.pickpocketTables = loadTables(gson, "/rarity/thieving.json");
		this.uniqueItems = loadUniques(gson);
	}

	/** Package-private fixtures exercise the actual parser and matching engine. */
	RarityService(Gson gson, Reader npcJson, Reader pickpocketJson)
	{
		this.npcTables = index(gson, npcJson);
		this.pickpocketTables = index(gson, pickpocketJson);
		this.uniqueItems = Collections.emptyMap();
	}

	public int getSourceCount()
	{
		return npcTables.size();
	}

	public Rarity lookup(String rawSource, String lootType, int itemId, long quantity)
	{
		if (itemId < 0 || quantity <= 0)
		{
			return Rarity.unknown();
		}
		String source = SourceNames.key(rawSource);
		String reward = SourceNames.rewardKey(rawSource);
		String type = lootType == null ? "" : lootType.toUpperCase(Locale.ROOT);
		boolean unique = uniqueItems.getOrDefault(reward, Collections.emptySet()).contains(itemId);
		if ("PET".equals(type) || "pet".equals(source))
		{
			return new Rarity(null, "Pet chance requires acquisition context", unique);
		}
		if ("PLAYER".equals(type))
		{
			return Rarity.unknown();
		}
		if ("tombs of amascut".equals(reward))
		{
			String selected = toaPurpleSelection(itemId);
			return new Rarity(null, selected == null
				? "Per-raid chance requires raid context"
				: "Standard purple-table weight: " + selected + "; not per-raid odds; raid context required", unique);
		}
		if (reward.equals("chambers of xeric") || reward.equals("theatre of blood"))
		{
			return new Rarity(null, "Per-raid chance requires points, mode and team context", unique);
		}
		if (CONTEXTUAL_SOURCES.contains(source))
		{
			return new Rarity(null, "Chance depends on encounter or account context", unique);
		}
		if ("PICKPOCKET".equals(type))
		{
			// Rogue's outfit doubles the quantity, not the acquisition probability.
			if (quantity <= 2 && itemId == ItemID.BLOOD_SHARD)
			{
				return new Rarity(1.0 / 5000, "Per successful pickpocket; outfit may double quantity", true);
			}
			if (quantity <= 2 && itemId == ItemID.PRIF_TELEPORT_SEED)
			{
				return new Rarity(1.0 / 1024, "Per successful pickpocket; outfit may double quantity", true);
			}
			if (source.equals("master farmer"))
			{
				return new Rarity(null, "Pickpocket chance depends on Farming level", unique);
			}
			return match(pickpocketTables, source, itemId, quantity, unique);
		}
		if (!"NPC".equals(type))
		{
			return new Rarity(null, "Reward chance is not modelled for this event", unique);
		}
		return match(npcTables, source, itemId, quantity, unique);
	}

	private static String toaPurpleSelection(int itemId)
	{
		switch (itemId)
		{
			case ItemID.OSMUMTENS_FANG:
			case ItemID.LIGHTBEARER:
				return "7/24 (29.1667%)";
			case ItemID.ELIDINIS_WARD:
				return "1/8 (12.5%)";
			case ItemID.MASORI_MASK:
			case ItemID.MASORI_BODY:
			case ItemID.MASORI_CHAPS:
				return "1/12 (8.3333%)";
			case ItemID.TUMEKENS_SHADOW_UNCHARGED:
				return "1/24 (4.1667%)";
			default:
				return null;
		}
	}

	private static Rarity match(Map<String, Map<Integer, List<DropRule>>> tables, String source,
		int itemId, long quantity, boolean unique)
	{
		// Dink's generator deliberately stores all coin piles as FAKE_COINS.
		int tableId = itemId == ItemID.COINS ? ItemID.FAKE_COINS : itemId;
		List<DropRule> rules = tables.getOrDefault(source, Collections.emptyMap())
			.getOrDefault(tableId, Collections.emptyList());
		Double probability = null;
		String basis = "No matching reference rate";
		for (DropRule rule : rules)
		{
			if (quantity < rule.min || quantity > rule.max)
			{
				continue;
			}
			if (probability != null)
			{
				// Source data does not say whether overlapping rows are independent,
				// alternatives or conditional tables. Do not invent a summed rate.
				return new Rarity(null, "Multiple matching table outcomes; context is unknown", unique);
			}
			probability = rule.probability;
			basis = rule.basis;
		}
		return new Rarity(probability, basis, unique);
	}

	private static Map<String, Map<Integer, List<DropRule>>> loadTables(Gson gson, String resource)
	{
		try (InputStream stream = RarityService.class.getResourceAsStream(resource))
		{
			if (stream == null)
			{
				return Collections.emptyMap();
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
			{
				return index(gson, reader);
			}
		}
		catch (Exception e)
		{
			log.debug("Could not load bundled rarity data {}", resource, e);
			return Collections.emptyMap();
		}
	}

	private static Map<String, Map<Integer, List<DropRule>>> index(Gson gson, Reader reader)
	{
		Map<String, List<RawDrop>> raw = gson.fromJson(reader,
			new TypeToken<Map<String, List<RawDrop>>>() { }.getType());
		if (raw == null)
		{
			return Collections.emptyMap();
		}
		Map<String, Map<Integer, List<DropRule>>> result = new HashMap<>();
		raw.forEach((source, entries) -> {
			Map<Integer, List<DropRule>> items = new HashMap<>();
			if (entries == null)
			{
				return;
			}
			for (RawDrop entry : entries)
			{
				if (entry == null || !entry.valid())
				{
					continue;
				}
				int rolls = entry.rolls == null ? 1 : entry.rolls;
				long min = entry.min == null ? entry.quantity : entry.min;
				long max = entry.max == null ? entry.quantity : entry.max;
				double p = 1.0 / entry.denominator;
				String basis;
				if (min != max)
				{
					basis = rolls == 1
						? "Reference table-entry chance for any quantity in range; modifiers may apply"
						: "Reference table-entry chance across " + rolls + " rolls; range matched; modifiers may apply";
				}
				else
				{
					basis = rolls == 1
						? "Reference chance of this stack per encounter; modifiers may apply"
						: "Reference chance of this quantity across " + rolls + " rolls per encounter; modifiers may apply";
				}
				List<DropRule> rules = items.computeIfAbsent(entry.itemId, ignored -> new ArrayList<>());
				for (int successes = 1; successes <= rolls; successes++)
				{
					double chance = binomial(p, rolls, successes);
					if (chance > 0 && Double.isFinite(chance))
					{
						rules.add(new DropRule(min * successes, max * successes, chance, basis));
					}
				}
			}
			items.replaceAll((id, rules) -> Collections.unmodifiableList(rules));
			result.put(SourceNames.key(source), Collections.unmodifiableMap(items));
		});
		return Collections.unmodifiableMap(result);
	}

	private static double binomial(double p, int trials, int successes)
	{
		double combinations = 1;
		for (int i = 1; i <= successes; i++)
		{
			combinations *= (trials - i + 1.0) / i;
		}
		return combinations * Math.pow(p, successes) * Math.pow(1 - p, trials - successes);
	}

	private static Map<String, Set<Integer>> loadUniques(Gson gson)
	{
		try (InputStream stream = RarityService.class.getResourceAsStream("/rarity/event-uniques.json"))
		{
			if (stream == null)
			{
				return Collections.emptyMap();
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
			{
				Map<String, List<Integer>> raw = gson.fromJson(reader,
					new TypeToken<Map<String, List<Integer>>>() { }.getType());
				Map<String, Set<Integer>> result = new HashMap<>();
				if (raw != null)
				{
					raw.forEach((source, ids) -> result.put(SourceNames.rewardKey(source),
						Collections.unmodifiableSet(new HashSet<>(ids))));
				}
				return Collections.unmodifiableMap(result);
			}
		}
		catch (Exception e)
		{
			log.debug("Could not load bundled unique classification", e);
			return Collections.emptyMap();
		}
	}

	private static final class RawDrop
	{
		@SerializedName("i") private int itemId;
		@SerializedName("r") private Integer rolls;
		@SerializedName("d") private double denominator;
		@SerializedName("q") private Integer quantity;
		@SerializedName("m") private Integer min;
		@SerializedName("n") private Integer max;

		private boolean valid()
		{
			return itemId >= 0 && Double.isFinite(denominator) && denominator >= 1
				&& (rolls == null || rolls >= 1 && rolls <= 100)
				&& ((min != null && max != null && min > 0 && max >= min)
					|| (min == null && max == null && quantity != null && quantity > 0));
		}
	}

	private static final class DropRule
	{
		private final long min;
		private final long max;
		private final double probability;
		private final String basis;

		private DropRule(long min, long max, double probability, String basis)
		{
			this.min = min;
			this.max = max;
			this.probability = probability;
			this.basis = basis;
		}
	}
}

package com.corndwell.lootherald.filter;

import com.corndwell.lootherald.discord.Destination;
import com.corndwell.lootherald.model.LootDrop;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Optional destination-specific routing. Configuration is validated completely
 * before it becomes usable, and errors never echo user-provided values.
 */
public final class DestinationRoutes
{
	private final List<Destination> destinations;
	private final Map<String, Destination> destinationsById;
	private final List<Rule> rules;
	private final boolean unrestricted;

	/** Rows: destination name | source wildcard | player wildcard | minimum GP. */
	public DestinationRoutes(String configuration, List<Destination> destinations)
	{
		this.destinations = List.copyOf(Objects.requireNonNull(destinations, "destinations"));
		Map<String, Destination> byName = new LinkedHashMap<>();
		Map<String, Destination> byId = new LinkedHashMap<>();
		for (Destination destination : this.destinations)
		{
			if (byName.putIfAbsent(normalize(destination.getName()), destination) != null)
			{
				throw new IllegalArgumentException("Discord destination names must be unique, ignoring case and surrounding spaces.");
			}
			byId.put(destination.getId(), destination);
		}
		this.destinationsById = Map.copyOf(byId);
		List<Rule> parsed = new ArrayList<>();
		int lineNumber = 0;
		for (String raw : (configuration == null ? "" : configuration).split("\\R", -1))
		{
			lineNumber++;
			String line = raw.trim();
			if (line.isEmpty())
			{
				continue;
			}
			String[] parts = line.split("\\|", -1);
			if (parts.length != 4)
			{
				throw invalid(lineNumber, "Use destination name | source wildcard | player wildcard | minimum GP.");
			}
			Destination destination = byName.get(normalize(parts[0]));
			if (destination == null)
			{
				throw invalid(lineNumber, "Choose an existing destination name.");
			}
			String minimum = parts[3].trim();
			if (minimum.isEmpty() || !minimum.chars().allMatch(c -> c >= '0' && c <= '9'))
			{
				throw invalid(lineNumber, "Minimum GP must be a non-negative whole number.");
			}
			long minimumValue;
			try
			{
				minimumValue = Long.parseLong(minimum);
			}
			catch (NumberFormatException e)
			{
				throw invalid(lineNumber, "Minimum GP is too large.");
			}
			parsed.add(new Rule(destination.getId(), wildcard(parts[1]), wildcard(parts[2]), minimumValue));
		}
		this.rules = List.copyOf(parsed);
		this.unrestricted = rules.isEmpty();
	}

	/**
	 * Manual selection of one destination is an explicit route override. Manual
	 * "all" and automatic sends retain source/player routing; automatic selection
	 * of one destination narrows the same rules instead of overriding them.
	 */
	public List<Destination> matching(LootDrop drop, String selectedDestinationId, boolean automatic)
	{
		Objects.requireNonNull(drop, "drop");
		if (selectedDestinationId != null && !destinationsById.containsKey(selectedDestinationId))
		{
			return List.of();
		}
		if (!automatic && selectedDestinationId != null)
		{
			return List.of(destinationsById.get(selectedDestinationId));
		}
		List<Destination> matched = new ArrayList<>();
		for (Destination destination : destinations)
		{
			if (selectedDestinationId != null && !selectedDestinationId.equals(destination.getId()))
			{
				continue;
			}
			if (unrestricted || rules.stream().anyMatch(rule -> rule.destinationId.equals(destination.getId()) && rule.matches(drop)))
			{
				matched.add(destination);
			}
		}
		return List.copyOf(matched);
	}

	/**
	 * Least threshold of applicable rules. No extra threshold for blank routing;
	 * Long.MAX_VALUE for an unknown destination or no matching route (fail closed).
	 * Manual posting bypasses qualification and need not consult this value.
	 */
	public long minimumValue(String destinationId, LootDrop drop)
	{
		Objects.requireNonNull(drop, "drop");
		if (destinationId == null || !destinationsById.containsKey(destinationId))
		{
			return Long.MAX_VALUE;
		}
		if (unrestricted)
		{
			return 0;
		}
		long minimum = Long.MAX_VALUE;
		for (Rule rule : rules)
		{
			if (rule.destinationId.equals(destinationId) && rule.matches(drop))
			{
				minimum = Math.min(minimum, rule.minimumValue);
			}
		}
		return minimum;
	}

	private static String normalize(String value)
	{
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private static String wildcard(String value)
	{
		String trimmed = value.trim();
		return trimmed.isEmpty() ? "*" : trimmed;
	}

	private static IllegalArgumentException invalid(int line, String explanation)
	{
		return new IllegalArgumentException("Invalid destination routing on line " + line + ". " + explanation);
	}

	private static final class Rule
	{
		private final String destinationId;
		private final String sourcePattern;
		private final String playerPattern;
		private final long minimumValue;

		private Rule(String destinationId, String sourcePattern, String playerPattern, long minimumValue)
		{
			this.destinationId = destinationId;
			this.sourcePattern = sourcePattern;
			this.playerPattern = playerPattern;
			this.minimumValue = minimumValue;
		}

		private boolean matches(LootDrop drop)
		{
			return (LootFilter.matches(sourcePattern, drop.getSource()) || LootFilter.matches(sourcePattern, drop.getRawSource()))
				&& LootFilter.matches(playerPattern, drop.getPlayer());
		}
	}
}

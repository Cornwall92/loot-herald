package com.corndwell.lootherald.rarity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Stable display/source keys without merging distinct difficulty modes. */
public final class SourceNames
{
	private static final Map<String, String> ALIASES;

	static
	{
		Map<String, String> names = new HashMap<>();
		alias(names, "The Gauntlet", "Gauntlet", "The Gauntlet", "Crystalline Hunllef");
		alias(names, "Corrupted Gauntlet", "Corrupted Gauntlet", "The Corrupted Gauntlet", "Corrupted Hunllef");
		alias(names, "Moons of Peril", "Lunar Chest", "Moons of Peril", "Perilous Moons");
		alias(names, "Chambers of Xeric", "Chambers of Xeric", "CoX");
		alias(names, "Chambers of Xeric: Challenge Mode", "Chambers of Xeric: Challenge Mode", "Chambers of Xeric (Challenge Mode)");
		alias(names, "Theatre of Blood", "Theatre of Blood", "ToB");
		alias(names, "Theatre of Blood: Hard Mode", "Theatre of Blood: Hard Mode", "Theatre of Blood (Hard Mode)");
		alias(names, "Theatre of Blood: Entry Mode", "Theatre of Blood: Entry Mode", "Theatre of Blood (Entry Mode)");
		alias(names, "Tombs of Amascut", "Tombs of Amascut", "ToA");
		alias(names, "Tombs of Amascut: Expert Mode", "Tombs of Amascut: Expert Mode", "Tombs of Amascut (Expert Mode)");
		alias(names, "Tombs of Amascut: Entry Mode", "Tombs of Amascut: Entry Mode", "Tombs of Amascut (Entry Mode)");
		alias(names, "Barrows", "Barrows", "Barrows Chest");
		alias(names, "Larran's big chest", "Larran's big chest", "Larran's Big Chest");
		alias(names, "H.A.M. Member", "H.A.M. Member", "H.A.M. member", "H.A.M. members");
		ALIASES = Collections.unmodifiableMap(names);
	}

	private SourceNames()
	{
	}

	public static String normalize(String rawSource)
	{
		if (rawSource == null || rawSource.trim().isEmpty())
		{
			return "Unknown source";
		}
		String clean = rawSource.replaceAll("<[^>]*>", "")
			.replace("&#039;", "'").replace("&#39;", "'").replace("&apos;", "'")
			.replace("&amp;", "&").replace('\u2019', '\'').replace('\u00a0', ' ')
			.trim().replaceAll("\\s+", " ");
		return ALIASES.getOrDefault(clean.toLowerCase(Locale.ROOT), clean.isEmpty() ? "Unknown source" : clean);
	}

	static String key(String source)
	{
		return normalize(source).toLowerCase(Locale.ROOT);
	}

	/** Shared reward classification only; the display name still retains the mode. */
	static String rewardKey(String source)
	{
		String key = key(source);
		for (String raid : new String[]{"chambers of xeric", "theatre of blood", "tombs of amascut"})
		{
			if (key.startsWith(raid + ":"))
			{
				return raid;
			}
		}
		return key;
	}

	private static void alias(Map<String, String> names, String display, String... variants)
	{
		for (String variant : variants)
		{
			names.put(variant.toLowerCase(Locale.ROOT), display);
		}
	}
}

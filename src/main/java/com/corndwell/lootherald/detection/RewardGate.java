package com.corndwell.lootherald.detection;

import com.corndwell.lootherald.model.DropItem;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Session-only protection for reward interfaces which can show the same award again.
 *
 * <p>This deliberately does not fingerprint ordinary NPC kills, clue openings, keyed
 * chests or reward bags. Identical rewards from those sources can be genuine separate
 * acquisitions. A reward screen with no new completion evidence is retained for review
 * rather than silently discarded when its ownership cannot be established.</p>
 *
 * <p>Call onChat for game messages before deciding a captured reward, ideally deciding
 * one game tick after receipt. Pass the original receipt tick to decide. A fresh count
 * is a lifecycle marker, not a rarity or drop-count calculation. Do not call
 * onRewardCycle for a world hop, loading screen or merely reopening an interface.</p>
 *
 * <p>All methods are client-thread confined.</p>
 */
public final class RewardGate
{
	public enum Decision
	{
		ACCEPT,
		DUPLICATE,
		REVIEW
	}

	public static final String REVIEW_REASON =
		"Reward may already have been recorded; automatic posting held";

	private static final Pattern COMPLETED_COUNT = Pattern.compile(
		"^Your completed (.+?) count is: ([0-9][0-9,]*)\\.?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern REWARD_COUNT = Pattern.compile(
		"^Your (.+?) (?:chest|completion|kill) count is: ([0-9][0-9,]*)\\.?$",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern TAGS = Pattern.compile("<[^>]*>");
	private static final int CORRELATED_ECHO_TICKS = 2;

	private final Map<String, State> states = new HashMap<>();

	public void onChat(String text, int tick)
	{
		if (text == null)
		{
			return;
		}
		String clean = TAGS.matcher(text).replaceAll("").trim();
		Matcher matcher = COMPLETED_COUNT.matcher(clean);
		if (!matcher.matches())
		{
			matcher = REWARD_COUNT.matcher(clean);
			if (!matcher.matches())
			{
				return;
			}
		}
		String subject = normalize(matcher.group(1));
		String source = guardedSource(subject);
		if (source == null)
		{
			return;
		}

		final long count;
		try
		{
			count = Long.parseLong(matcher.group(2).replace(",", ""));
		}
		catch (NumberFormatException ex)
		{
			return;
		}

		State state = states.computeIfAbsent(source, ignored -> new State());
		Long previous = state.counts.get(subject);
		if (previous != null && count <= previous)
		{
			if (count == previous && subject.equals(state.activeCounter))
			{
				state.lastProofTick = tick;
			}
			return;
		}
		boolean wasUnproven = !state.hasProof;
		state.counts.put(subject, count);
		state.hasProof = true;
		state.activeCounter = subject;
		state.lastProofTick = tick;
		state.generation++;

		// A first count arriving just after the first observed reward describes that
		// reward. It must not arm a second copy when its interface is reopened.
		long distance = (long) tick - state.acceptedTick;
		if (wasUnproven && state.snapshot != null && distance >= 0 && distance <= 2)
		{
			state.acceptedGeneration = state.generation;
		}
	}

	/**
	 * Optional adapter hook for a positively identified new reward cycle. It must be
	 * called only once per new cycle, not for UI opening, inventory withdrawal or
	 * instance/region loading by itself. The normalized LootReceived path needs only
	 * onChat; adapters can use this when a source exposes stronger acquisition proof.
	 */
	public void onRewardCycle(String rawSource)
	{
		String source = guardedSource(rawSource);
		if (source != null)
		{
			State state = states.computeIfAbsent(source, ignored -> new State());
			state.hasProof = true;
			state.activeCounter = null;
			state.generation++;
		}
	}

	/** Whether this source should wait one tick for its matching count message. */
	public static boolean isGuardedSource(String rawSource)
	{
		return guardedSource(rawSource) != null;
	}

	public Decision decide(String rawSource, String type, List<DropItem> items, int receiptTick)
	{
		String source = guardedSource(rawSource);
		if (source == null || !"EVENT".equalsIgnoreCase(type))
		{
			return Decision.ACCEPT;
		}
		Map<Integer, Long> snapshot = snapshot(items);
		if (snapshot.isEmpty())
		{
			return Decision.REVIEW;
		}
		State state = states.computeIfAbsent(source, ignored -> new State());
		if (state.snapshot == null || state.acceptedGeneration != state.generation)
		{
			accept(state, snapshot, receiptTick);
			return Decision.ACCEPT;
		}

		boolean sameOrRemaining = isSubset(snapshot, state.snapshot);
		if (state.hasProof)
		{
			// A correlated repeated snapshot is an echo. An old counter alone cannot
			// prove a later award is old: its next count message may have been hidden.
			// Keep those uncertain later observations for review, never auto-post them.
			long distance = (long) receiptTick - Math.max(state.acceptedTick, state.lastProofTick);
			return sameOrRemaining && distance >= 0 && distance <= CORRELATED_ECHO_TICKS
				? Decision.DUPLICATE : Decision.REVIEW;
		}
		if (sameOrRemaining)
		{
			// Could be either a reopened screen or an identical new acquisition when
			// count messages are unavailable. Preserve for explicit user review.
			return Decision.REVIEW;
		}

		// No known cycle marker, but the contents cannot be a remaining subset of the
		// last award. Retain the newly observed award rather than losing real loot.
		accept(state, snapshot, receiptTick);
		return Decision.ACCEPT;
	}

	/** Convenience method for callers that intentionally discard review candidates. */
	public boolean accept(String rawSource, String type, List<DropItem> items, int receiptTick)
	{
		return decide(rawSource, type, items, receiptTick) == Decision.ACCEPT;
	}

	public void clear()
	{
		states.clear();
	}

	private static void accept(State state, Map<Integer, Long> snapshot, int tick)
	{
		state.snapshot = snapshot;
		state.acceptedGeneration = state.generation;
		state.acceptedTick = tick;
	}

	private static Map<Integer, Long> snapshot(List<DropItem> items)
	{
		Map<Integer, Long> result = new HashMap<>();
		if (items != null)
		{
			for (DropItem item : items)
			{
				result.merge(item.getId(), item.getQuantity(), RewardGate::saturatingAdd);
			}
		}
		return result;
	}

	private static long saturatingAdd(long a, long b)
	{
		return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
	}

	private static boolean isSubset(Map<Integer, Long> candidate, Map<Integer, Long> original)
	{
		for (Map.Entry<Integer, Long> item : candidate.entrySet())
		{
			Long originalQuantity = original.get(item.getKey());
			if (originalQuantity == null || item.getValue() > originalQuantity)
			{
				return false;
			}
		}
		return true;
	}

	private static String guardedSource(String input)
	{
		String source = normalize(input);
		if (source.equals("barrows") || source.equals("barrows chest"))
		{
			return "barrows";
		}
		if (source.equals("lunar") || source.equals("lunar chest") || source.equals("moons of peril"))
		{
			return "lunar chest";
		}
		for (String raid : new String[]{"chambers of xeric", "theatre of blood", "tombs of amascut", "fractured archive"})
		{
			if (source.equals(raid) || source.startsWith(raid + ":") || source.startsWith(raid + " "))
			{
				return raid;
			}
		}
		return source.equals("fortis colosseum") ? source : null;
	}

	private static String normalize(String value)
	{
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static final class State
	{
		private final Map<String, Long> counts = new HashMap<>();
		private long generation;
		private long acceptedGeneration;
		private boolean hasProof;
		private String activeCounter;
		private int lastProofTick = Integer.MIN_VALUE;
		private int acceptedTick;
		private Map<Integer, Long> snapshot;
	}
}

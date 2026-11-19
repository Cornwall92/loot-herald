package com.corndwell.lootherald.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Builder;
import lombok.Value;

/** Immutable data captured for one acquisition event, independent of the live client. */
@Value
@Builder(toBuilder = true)
public class LootDrop
{
	String id;
	long session;
	Instant received;
	String player;
	String accountType;
	String source;
	String rawSource;
	String type;
	int combatLevel;
	int batchCount;
	boolean pet;
	boolean duplicatePet;
	boolean collectionLog;
	List<DropItem> items;
	Map<String, String> details;

	public LootDrop(String id, long session, Instant received, String player, String accountType,
		String source, String rawSource, String type, int combatLevel, int batchCount,
		boolean pet, boolean duplicatePet, boolean collectionLog, List<DropItem> items,
		Map<String, String> details)
	{
		this.id = Objects.requireNonNull(id, "id");
		this.session = session;
		this.received = Objects.requireNonNull(received, "received");
		this.player = Objects.requireNonNull(player, "player");
		this.accountType = Objects.requireNonNull(accountType, "accountType");
		this.source = Objects.requireNonNull(source, "source");
		this.rawSource = Objects.requireNonNull(rawSource, "rawSource");
		this.type = Objects.requireNonNull(type, "type");
		this.combatLevel = combatLevel;
		this.batchCount = batchCount;
		this.pet = pet;
		this.duplicatePet = duplicatePet;
		this.collectionLog = collectionLog;
		this.items = List.copyOf(Objects.requireNonNull(items, "items"));
		if (this.items.isEmpty())
		{
			throw new IllegalArgumentException("A loot event must contain an item");
		}
		this.details = Map.copyOf(Objects.requireNonNull(details, "details"));
	}
}

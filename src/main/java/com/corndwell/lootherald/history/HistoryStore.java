package com.corndwell.lootherald.history;

import com.corndwell.lootherald.model.LootDrop;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Session-only history and delivery ledger. All mutations and snapshots share one lock;
 * callers never retain mutable storage or execute callbacks while the lock is held.
 */
public final class HistoryStore
{
	public static final int DEFAULT_MAX_EVENTS = 10_000;
	public static final int DEFAULT_MAX_SCREENSHOTS = 50;
	public static final long DEFAULT_MAX_SCREENSHOT_BYTES = 64L * 1024 * 1024;

	private final int maxEvents;
	private final int maxScreenshots;
	private final long maxScreenshotBytes;
	private final Map<String, MutableEntry> history = new LinkedHashMap<>();
	private final Map<String, byte[]> screenshots = new LinkedHashMap<>();
	private long session = 1;
	private long nextToken;
	private long screenshotBytes;
	private long evictedCount;

	public HistoryStore()
	{
		this(DEFAULT_MAX_EVENTS, DEFAULT_MAX_SCREENSHOTS, DEFAULT_MAX_SCREENSHOT_BYTES);
	}

	/** Custom bounds are useful for testing and must not exceed available process memory. */
	public HistoryStore(int maxEvents, int maxScreenshots, long maxScreenshotBytes)
	{
		if (maxEvents < 1 || maxScreenshots < 0 || maxScreenshotBytes < 0)
		{
			throw new IllegalArgumentException("Invalid history capacity");
		}
		this.maxEvents = maxEvents;
		this.maxScreenshots = maxScreenshots;
		this.maxScreenshotBytes = maxScreenshotBytes;
	}

	public synchronized long getSession()
	{
		return session;
	}

	/** Also invalidates every reservation previously issued by this store. */
	public synchronized void clear()
	{
		session++;
		history.clear();
		screenshots.clear();
		screenshotBytes = 0;
		evictedCount = 0;
	}

	/** Old-session and duplicate event IDs are ignored, preserving delivery state. */
	public synchronized void add(LootDrop drop, String reason)
	{
		Objects.requireNonNull(drop, "drop");
		if (drop.getSession() != session || history.containsKey(drop.getId()))
		{
			return;
		}
		history.put(drop.getId(), new MutableEntry(drop, safe(reason)));
		while (history.size() > maxEvents)
		{
			Iterator<String> ids = history.keySet().iterator();
			String oldest = ids.next();
			ids.remove();
			removeScreenshot(oldest);
			evictedCount++;
		}
	}

	public synchronized List<Entry> entries()
	{
		List<Entry> result = new ArrayList<>(history.size());
		for (MutableEntry entry : history.values())
		{
			result.add(snapshot(entry));
		}
		Collections.reverse(result);
		return Collections.unmodifiableList(result);
	}

	public synchronized Entry get(String id)
	{
		MutableEntry entry = history.get(id);
		return entry == null ? null : snapshot(entry);
	}

	public synchronized void setReason(String id, String reason)
	{
		MutableEntry entry = history.get(id);
		if (entry != null)
		{
			entry.reason = safe(reason);
		}
	}

	/**
	 * Atomically claims eligible original item indices for one destination. A pending
	 * item can never be reclaimed, even by an explicit repeat. Sent/uncertain items
	 * require an explicit repeat; failed items can be retried normally.
	 */
	public synchronized Reservation reserve(String eventId, Collection<Integer> indices,
		String destinationId, boolean repeat)
	{
		MutableEntry entry = history.get(eventId);
		if (entry == null || indices == null || destinationId == null || destinationId.isEmpty())
		{
			return null;
		}
		Map<Integer, Attempt> destination = entry.deliveries.get(destinationId);
		LinkedHashSet<Integer> eligible = new LinkedHashSet<>();
		for (Integer index : indices)
		{
			if (index == null || index < 0 || index >= entry.drop.getItems().size())
			{
				continue;
			}
			Attempt prior = destination == null ? null : destination.get(index);
			if (prior != null && (isPending(prior.status)
				|| (!repeat && (prior.status == DeliveryStatus.SENT || prior.status == DeliveryStatus.UNCERTAIN))))
			{
				continue;
			}
			eligible.add(index);
		}
		if (eligible.isEmpty())
		{
			return null;
		}
		long token = ++nextToken;
		if (destination == null)
		{
			destination = new LinkedHashMap<>();
			entry.deliveries.put(destinationId, destination);
		}
		for (int index : eligible)
		{
			destination.put(index, new Attempt(token));
		}
		return new Reservation(session, eventId, new ArrayList<>(eligible), destinationId, token);
	}

	/** Stale session, replaced attempt and already terminal callbacks are ignored. */
	public synchronized void update(Reservation reservation, DeliveryStatus status,
		String detail, String messageId)
	{
		if (reservation == null || status == null || reservation.session != session)
		{
			return;
		}
		MutableEntry entry = history.get(reservation.eventId);
		Map<Integer, Attempt> destination = entry == null ? null : entry.deliveries.get(reservation.destinationId);
		if (destination == null)
		{
			return;
		}
		for (int index : reservation.indices)
		{
			Attempt attempt = destination.get(index);
			if (attempt == null || attempt.token != reservation.token || !isPending(attempt.status))
			{
				continue;
			}
			// Sending may repeat for a rate-limit retry. Never return to READY/QUEUED.
			if (status == DeliveryStatus.READY || status == DeliveryStatus.QUEUED)
			{
				continue;
			}
			attempt.status = status;
			attempt.detail = safe(detail);
			attempt.messageId = messageId;
		}
	}

	public synchronized byte[] screenshot(String id)
	{
		byte[] bytes = screenshots.get(id);
		return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
	}

	/** The event ID must be unique across sessions when used by asynchronous captures. */
	public synchronized void screenshot(String id, byte[] image)
	{
		MutableEntry entry = history.get(id);
		if (entry == null)
		{
			return;
		}
		removeScreenshot(id);
		if (image == null || image.length == 0)
		{
			entry.screenshotState = "Not captured";
			return;
		}
		if (maxScreenshots == 0 || image.length > maxScreenshotBytes)
		{
			entry.screenshotState = "Not retained (image cache limit)";
			return;
		}
		while (!screenshots.isEmpty() && (screenshots.size() >= maxScreenshots
			|| screenshotBytes > maxScreenshotBytes - image.length))
		{
			String oldest = screenshots.keySet().iterator().next();
			removeScreenshot(oldest);
			MutableEntry oldEntry = history.get(oldest);
			if (oldEntry != null)
			{
				oldEntry.screenshotState = "Expired";
			}
		}
		screenshots.put(id, Arrays.copyOf(image, image.length));
		screenshotBytes += image.length;
		entry.screenshotState = "Available";
	}

	public synchronized void screenshotState(String id, String state)
	{
		MutableEntry entry = history.get(id);
		if (entry != null)
		{
			entry.screenshotState = safe(state);
		}
	}

	public synchronized long getEvictedCount()
	{
		return evictedCount;
	}

	private void removeScreenshot(String id)
	{
		byte[] removed = screenshots.remove(id);
		if (removed != null)
		{
			screenshotBytes -= removed.length;
		}
	}

	private static boolean isPending(DeliveryStatus status)
	{
		return status == DeliveryStatus.QUEUED || status == DeliveryStatus.SENDING;
	}

	private static String safe(String value)
	{
		return value == null ? "" : value;
	}

	private static Entry snapshot(MutableEntry entry)
	{
		Map<String, Map<Integer, Delivery>> deliveries = new LinkedHashMap<>();
		entry.deliveries.forEach((destination, items) -> {
			Map<Integer, Delivery> copiedItems = new LinkedHashMap<>();
			items.forEach((index, attempt) -> copiedItems.put(index,
				new Delivery(attempt.status, attempt.detail, attempt.messageId)));
			deliveries.put(destination, Collections.unmodifiableMap(copiedItems));
		});
		return new Entry(entry.drop, entry.reason, entry.screenshotState,
			Collections.unmodifiableMap(deliveries));
	}

	private static final class MutableEntry
	{
		private final LootDrop drop;
		private String reason;
		private String screenshotState = "Not captured";
		private final Map<String, Map<Integer, Attempt>> deliveries = new LinkedHashMap<>();

		private MutableEntry(LootDrop drop, String reason)
		{
			this.drop = drop;
			this.reason = reason;
		}
	}

	private static final class Attempt
	{
		private final long token;
		private DeliveryStatus status = DeliveryStatus.QUEUED;
		private String detail = "Queued";
		private String messageId;

		private Attempt(long token)
		{
			this.token = token;
		}
	}

	public static final class Entry
	{
		private final LootDrop drop;
		private final String reason;
		private final String screenshotState;
		private final Map<String, Map<Integer, Delivery>> deliveries;

		private Entry(LootDrop drop, String reason, String screenshotState,
			Map<String, Map<Integer, Delivery>> deliveries)
		{
			this.drop = drop;
			this.reason = reason;
			this.screenshotState = screenshotState;
			this.deliveries = deliveries;
		}

		public LootDrop getDrop() { return drop; }
		public String getReason() { return reason; }
		public String getScreenshotState() { return screenshotState; }
		public Map<String, Map<Integer, Delivery>> getDeliveries() { return deliveries; }
	}

	public static final class Delivery
	{
		private final DeliveryStatus status;
		private final String detail;
		private final String messageId;

		private Delivery(DeliveryStatus status, String detail, String messageId)
		{
			this.status = status;
			this.detail = detail;
			this.messageId = messageId;
		}

		public DeliveryStatus getStatus() { return status; }
		public String getDetail() { return detail; }
		public String getMessageId() { return messageId; }
	}

	public static final class Reservation
	{
		private final long session;
		private final String eventId;
		private final List<Integer> indices;
		private final String destinationId;
		private final long token;

		private Reservation(long session, String eventId, List<Integer> indices, String destinationId, long token)
		{
			this.session = session;
			this.eventId = eventId;
			this.indices = List.copyOf(indices);
			this.destinationId = destinationId;
			this.token = token;
		}

		public long getSession() { return session; }
		public String getEventId() { return eventId; }
		public List<Integer> getIndices() { return indices; }
		public String getDestinationId() { return destinationId; }
		public long getToken() { return token; }
	}
}

package com.corndwell.lootherald.history;

import com.corndwell.lootherald.model.DropItem;
import com.corndwell.lootherald.model.LootDrop;
import com.corndwell.lootherald.model.Rarity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoryStoreTest
{
	@Test
	public void concurrentDoubleClicksReserveOnlyOncePerDestination() throws Exception
	{
		HistoryStore store = storeWithDrop();
		ExecutorService executor = Executors.newFixedThreadPool(8);
		try
		{
			List<CompletableFuture<HistoryStore.Reservation>> calls = new ArrayList<>();
			for (int index = 0; index < 128; index++)
			{
				calls.add(CompletableFuture.supplyAsync(() -> store.reserve("event", List.of(0), "a", false), executor));
			}
			CompletableFuture.allOf(calls.toArray(new CompletableFuture<?>[0])).get(5, TimeUnit.SECONDS);
			assertEquals(1, calls.stream().map(CompletableFuture::join).filter(r -> r != null).count());
			assertNotNull(store.reserve("event", List.of(0), "b", false));
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	public void partialRewardReservesOnlyRemainingOriginalIndices()
	{
		HistoryStore store = storeWithDrop();
		HistoryStore.Reservation first = store.reserve("event", List.of(0), "a", false);
		store.update(first, DeliveryStatus.SENT, "Delivered", "message-1");
		HistoryStore.Reservation remaining = store.reserve("event", Arrays.asList(null, -1, 0, 1, 1, 8), "a", false);
		assertEquals(List.of(1), remaining.getIndices());
		assertEquals(DeliveryStatus.SENT, status(store, "a", 0));
		assertEquals(DeliveryStatus.QUEUED, status(store, "a", 1));
		assertNull(store.reserve("event", List.of(0, 1), "a", false));
	}

	@Test
	public void uncertainDeliveryRequiresExplicitRepeatAndPendingNeverRepeats()
	{
		HistoryStore store = storeWithDrop();
		HistoryStore.Reservation first = store.reserve("event", List.of(0), "a", false);
		assertNull(store.reserve("event", List.of(0), "a", true));
		store.update(first, DeliveryStatus.SENDING, "Sending", null);
		assertNull(store.reserve("event", List.of(0), "a", true));
		store.update(first, DeliveryStatus.UNCERTAIN, "Connection ended", null);
		assertNull(store.reserve("event", List.of(0), "a", false));
		assertNotNull(store.reserve("event", List.of(0), "a", true));
	}

	@Test
	public void failedDeliveryCanRetryAndPriorAttemptCannotCompleteNewAttempt()
	{
		HistoryStore store = storeWithDrop();
		HistoryStore.Reservation first = store.reserve("event", List.of(0), "a", false);
		store.update(first, DeliveryStatus.FAILED, "Rejected", null);
		HistoryStore.Reservation second = store.reserve("event", List.of(0), "a", false);
		assertNotNull(second);
		assertNotEquals(first.getToken(), second.getToken());
		store.update(first, DeliveryStatus.SENT, "Late completion", "old-message");
		assertEquals(DeliveryStatus.QUEUED, status(store, "a", 0));
		store.update(second, DeliveryStatus.SENT, "Delivered", "new-message");
		assertEquals("new-message", store.get("event").getDeliveries().get("a").get(0).getMessageId());
	}

	@Test
	public void terminalCallbacksCannotRegressAndSendingCanRepeat()
	{
		HistoryStore store = storeWithDrop();
		HistoryStore.Reservation reservation = store.reserve("event", List.of(0), "a", false);
		store.update(reservation, DeliveryStatus.SENDING, "Sending", null);
		store.update(reservation, DeliveryStatus.SENDING, "Retry after rate limit", null);
		assertEquals("Retry after rate limit", store.get("event").getDeliveries().get("a").get(0).getDetail());
		store.update(reservation, DeliveryStatus.QUEUED, "Stale queue callback", null);
		assertEquals(DeliveryStatus.SENDING, status(store, "a", 0));
		store.update(reservation, DeliveryStatus.SENT, "Delivered", "message");
		store.update(reservation, DeliveryStatus.FAILED, "Late failure", null);
		assertEquals(DeliveryStatus.SENT, status(store, "a", 0));
	}

	@Test
	public void clearInvalidatesCallbacksEvenWhenAnEventIdIsReused()
	{
		HistoryStore store = storeWithDrop();
		long oldSession = store.getSession();
		HistoryStore.Reservation old = store.reserve("event", List.of(0), "a", false);
		store.screenshot("event", new byte[]{1, 2});
		store.clear();
		assertNotEquals(oldSession, store.getSession());
		assertTrue(store.entries().isEmpty());
		assertNull(store.screenshot("event"));
		store.add(drop("old", oldSession), "Old event");
		assertTrue(store.entries().isEmpty());
		store.add(drop("event", store.getSession()), "New session");
		store.reserve("event", List.of(0), "a", false);
		store.update(old, DeliveryStatus.SENT, "Late old session", "old-message");
		assertEquals(DeliveryStatus.QUEUED, status(store, "a", 0));
	}

	@Test
	public void snapshotsAndReservationIndicesCannotBeMutated()
	{
		HistoryStore store = storeWithDrop();
		HistoryStore.Reservation reservation = store.reserve("event", List.of(0), "a", false);
		HistoryStore.Entry snapshot = store.get("event");
		assertImmutable(() -> store.entries().clear());
		assertImmutable(() -> snapshot.getDeliveries().clear());
		assertImmutable(() -> snapshot.getDeliveries().get("a").clear());
		assertImmutable(() -> reservation.getIndices().clear());
		store.update(reservation, DeliveryStatus.SENT, "Delivered", "message");
		store.setReason("event", "Updated");
		assertEquals(DeliveryStatus.QUEUED, snapshot.getDeliveries().get("a").get(0).getStatus());
		assertEquals("Below threshold", snapshot.getReason());
	}

	@Test
	public void duplicateAddDoesNotEraseDeliveryState()
	{
		HistoryStore store = storeWithDrop();
		store.reserve("event", List.of(0), "a", false);
		store.add(drop("event", store.getSession()), "Duplicate");
		assertEquals(1, store.entries().size());
		assertEquals("Below threshold", store.get("event").getReason());
		assertEquals(DeliveryStatus.QUEUED, status(store, "a", 0));
	}

	@Test
	public void metadataEvictsOldestWithItsImagesAndInvalidatesReservation()
	{
		HistoryStore store = new HistoryStore(2, 50, 1024);
		store.add(drop("one", store.getSession()), "");
		store.screenshot("one", new byte[]{1});
		HistoryStore.Reservation old = store.reserve("one", List.of(0), "a", false);
		store.add(drop("two", store.getSession()), "");
		store.add(drop("three", store.getSession()), "");
		assertNull(store.get("one"));
		assertNull(store.screenshot("one"));
		assertEquals(1, store.getEvictedCount());
		assertEquals("three", store.entries().get(0).getDrop().getId());
		assertEquals("two", store.entries().get(1).getDrop().getId());
		store.update(old, DeliveryStatus.SENT, "Late", "message");
		store.screenshot("one", new byte[]{2});
		assertNull(store.get("one"));
		store.clear();
		assertEquals(0, store.getEvictedCount());
	}

	@Test
	public void screenshotBuffersAreCopiedAndCountLimitExpiresOnlyImages()
	{
		HistoryStore store = new HistoryStore(4, 1, 100);
		store.add(drop("one", store.getSession()), "");
		store.add(drop("two", store.getSession()), "");
		byte[] buffer = {1, 2};
		store.screenshot("one", buffer);
		buffer[0] = 9;
		byte[] read = store.screenshot("one");
		assertArrayEquals(new byte[]{1, 2}, read);
		read[0] = 8;
		assertArrayEquals(new byte[]{1, 2}, store.screenshot("one"));
		store.screenshot("two", new byte[]{3});
		assertNull(store.screenshot("one"));
		assertEquals("Expired", store.get("one").getScreenshotState());
		assertEquals("Available", store.get("two").getScreenshotState());
		assertEquals(2, store.entries().size());
		assertEquals(0, store.getEvictedCount());
	}

	@Test
	public void screenshotByteLimitAndReplacementMaintainBudget()
	{
		HistoryStore store = new HistoryStore(4, 5, 5);
		store.add(drop("one", store.getSession()), "");
		store.add(drop("two", store.getSession()), "");
		store.add(drop("three", store.getSession()), "");
		store.screenshot("one", new byte[]{1, 2, 3});
		store.screenshot("one", new byte[]{1});
		store.screenshot("two", new byte[]{2, 3, 4, 5});
		assertNotNull(store.screenshot("one"));
		assertNotNull(store.screenshot("two"));
		store.screenshot("three", new byte[]{6, 7});
		assertNull(store.screenshot("one"));
		assertNull(store.screenshot("two"));
		assertArrayEquals(new byte[]{6, 7}, store.screenshot("three"));
		store.screenshot("one", new byte[6]);
		assertNull(store.screenshot("one"));
		assertEquals("Not retained (image cache limit)", store.get("one").getScreenshotState());
	}

	private static HistoryStore storeWithDrop()
	{
		HistoryStore store = new HistoryStore();
		store.add(drop("event", store.getSession()), "Below threshold");
		return store;
	}

	private static LootDrop drop(String id, long session)
	{
		List<DropItem> items = List.of(new DropItem(1, "First", 1, 1, 1, Rarity.unknown()),
			new DropItem(2, "Second", 1, 2, 2, Rarity.unknown()));
		return new LootDrop(id, session, Instant.EPOCH, "Player", "Normal", "Chest", "Chest", "EVENT",
			-1, 1, false, false, false, items, Map.of());
	}

	private static DeliveryStatus status(HistoryStore store, String destination, int index)
	{
		return store.get("event").getDeliveries().get(destination).get(index).getStatus();
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

package com.corndwell.lootherald.ui;

import com.corndwell.lootherald.history.DeliveryStatus;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class PostingStateTest
{
    @Test public void pendingDestinationDisablesTheSharedAction()
    {
        PostingState state = PostingState.evaluate(Arrays.asList(DeliveryStatus.SENT, DeliveryStatus.SENDING, DeliveryStatus.READY), false, true);
        assertFalse(state.enabled);
        assertFalse(state.repeat);
    }

    @Test public void partialSuccessSendsRemainingWithoutRepeatingSuccessfulDestinations()
    {
        PostingState state = PostingState.evaluate(Arrays.asList(DeliveryStatus.SENT, DeliveryStatus.FAILED), false, true);
        assertTrue(state.enabled);
        assertFalse(state.repeat);
        assertEquals("Post remaining", state.label);
    }

    @Test public void uncertainDestinationDoesNotForceRepostWhenAnotherIsReady()
    {
        PostingState state = PostingState.evaluate(Arrays.asList(DeliveryStatus.UNCERTAIN, DeliveryStatus.READY), true, true);
        assertTrue(state.enabled);
        assertFalse(state.repeat);
        assertTrue(state.uncertain);
        assertEquals("Post remaining", state.label);
    }

    @Test public void uncertainDeliveryRequiresExplicitRepeatAction()
    {
        PostingState state = PostingState.evaluate(Collections.singletonList(DeliveryStatus.UNCERTAIN), false, true);
        assertTrue(state.enabled);
        assertTrue(state.repeat);
        assertTrue(state.uncertain);
        assertEquals("Post again", state.label);
    }

    @Test public void sentBundleIsExplicitRepost()
    {
        PostingState state = PostingState.evaluate(Arrays.asList(DeliveryStatus.SENT, DeliveryStatus.SENT), true, true);
        assertTrue(state.repeat);
        assertFalse(state.uncertain);
    }

    @Test public void absentDestinationCannotSend()
    {
        PostingState state = PostingState.evaluate(Collections.emptyList(), false, false);
        assertFalse(state.enabled);
    }

    @Test public void freshRewardOffersOneBundleAction()
    {
        PostingState state = PostingState.evaluate(Arrays.asList(DeliveryStatus.READY, DeliveryStatus.READY), true, true);
        assertEquals("Post reward", state.label);
        assertFalse(state.repeat);
        assertTrue(state.enabled);
    }
}

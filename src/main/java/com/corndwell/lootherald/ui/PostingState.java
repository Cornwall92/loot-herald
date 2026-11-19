package com.corndwell.lootherald.ui;

import com.corndwell.lootherald.history.DeliveryStatus;
import java.util.Collection;

/** The history's action is based on every selected item/destination pair. */
final class PostingState
{
    final String label;
    final String hint;
    final boolean enabled;
    final boolean repeat;
    final boolean uncertain;

    private PostingState(String label, String hint, boolean enabled, boolean repeat, boolean uncertain)
    {
        this.label = label;
        this.hint = hint;
        this.enabled = enabled;
        this.repeat = repeat;
        this.uncertain = uncertain;
    }

    static PostingState evaluate(Collection<DeliveryStatus> statuses, boolean reward, boolean hasDestination)
    {
        if (!hasDestination || statuses.isEmpty())
        {
            return new PostingState("Post to Discord", "Configure a Discord destination first.", false, false, false);
        }
        boolean pending = false;
        boolean eligible = false;
        boolean protectedDelivery = false;
        boolean uncertain = false;
        boolean failed = false;
        for (DeliveryStatus status : statuses)
        {
            DeliveryStatus value = status == null ? DeliveryStatus.READY : status;
            pending |= value == DeliveryStatus.QUEUED || value == DeliveryStatus.SENDING;
            eligible |= value == DeliveryStatus.READY || value == DeliveryStatus.FAILED;
            protectedDelivery |= value == DeliveryStatus.SENT || value == DeliveryStatus.UNCERTAIN;
            uncertain |= value == DeliveryStatus.UNCERTAIN;
            failed |= value == DeliveryStatus.FAILED;
        }
        if (pending)
        {
            return new PostingState("Sending…", "A selected delivery is already queued or sending.", false, false, uncertain);
        }
        if (eligible)
        {
            String label = protectedDelivery ? "Post remaining" : (reward ? "Post reward" : "Post to Discord");
            String hint = protectedDelivery
                ? "Send only unsent items to selected destinations. Sent and uncertain deliveries are excluded."
                : (failed ? "Try the failed or unsent deliveries again." : "Post manually, regardless of automatic loot filters.");
            return new PostingState(label, hint, true, false, uncertain);
        }
        return new PostingState("Post again", uncertain
            ? "A previous delivery may already exist. Posting again can create a duplicate."
            : "Deliberately send another copy to the selected destinations.", true, true, uncertain);
    }
}

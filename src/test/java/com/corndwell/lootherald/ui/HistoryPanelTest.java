package com.corndwell.lootherald.ui;

import com.corndwell.lootherald.discord.Destination;
import com.corndwell.lootherald.history.HistoryStore;
import com.corndwell.lootherald.model.DropItem;
import com.corndwell.lootherald.model.LootDrop;
import com.corndwell.lootherald.model.Rarity;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class HistoryPanelTest
{
    @Test public void clickDisablesSiblingActionsBeforeParentReturnsToClientThread() throws Exception
    {
        HistoryStore history = populatedHistory();
        AtomicInteger posts = new AtomicInteger();
        HistoryPanel[] panel = new HistoryPanel[1];
        SwingUtilities.invokeAndWait(() ->
        {
            panel[0] = new HistoryPanel(history, new NoActions()
            {
                @Override public void post(String event, Integer index, String destination, boolean repeat)
                {
                    posts.incrementAndGet();
                    assertEquals("reward-1", event);
                    assertEquals(Integer.valueOf(0), index);
                    assertFalse(repeat);
                    // Deliberately do not update the ledger yet: parent may marshal to client thread.
                }
            });
            panel[0].setDestinations(Destination.parse("Clan | https://discord.com/api/webhooks/123456789/abcdefghijklmnop"));
        });
        flushEdt();
        SwingUtilities.invokeAndWait(() ->
        {
            List<JButton> itemButtons = buttons(panel[0], "Post to Discord");
            assertEquals(2, itemButtons.size());
            itemButtons.get(0).doClick(0);
            itemButtons.get(0).doClick(0);
            assertEquals(1, posts.get());
            assertFalse(itemButtons.get(1).isEnabled());
            assertFalse(buttons(panel[0], "Post reward").get(0).isEnabled());
        });
    }

    @Test public void previewNeverCallsPostAndMissingDestinationDisablesPosting() throws Exception
    {
        AtomicInteger previews = new AtomicInteger();
        HistoryPanel[] panel = new HistoryPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new HistoryPanel(populatedHistory(), new NoActions()
        {
            @Override public void post(String event, Integer index, String destination, boolean repeat) { fail("Preview must not post"); }
            @Override public void preview(String event, Integer index) { previews.incrementAndGet(); }
        }));
        flushEdt();
        SwingUtilities.invokeAndWait(() ->
        {
            for (JButton button : buttons(panel[0], "Post to Discord")) { assertFalse(button.isEnabled()); }
            buttons(panel[0], "View").get(0).doClick(0);
            assertEquals(1, previews.get());
        });
    }

    @Test public void narrowSidebarKeepsPreviewAndSendButtonsInsideViewport() throws Exception
    {
        HistoryPanel[] panel = new HistoryPanel[1];
        SwingUtilities.invokeAndWait(() ->
        {
            panel[0] = new HistoryPanel(populatedHistory(), new NoActions());
            panel[0].setDestinations(Destination.parse("Clan | https://discord.com/api/webhooks/123456789/abcdefghijklmnop"));
            panel[0].setSize(242, 560);
        });
        flushEdt();
        SwingUtilities.invokeAndWait(() ->
        {
            layout(panel[0]);
            JScrollPane scroll = null;
            for (Component child : panel[0].getComponents())
            {
                if (child instanceof JScrollPane) { scroll = (JScrollPane) child; }
            }
            assertNotNull(scroll);
            assertTrue("Exercise the narrower viewport with a vertical scrollbar", scroll.getVerticalScrollBar().isVisible());
            for (String text : List.of("View", "Post to Discord", "Post reward"))
            {
                for (JButton button : buttons(panel[0], text))
                {
                    Rectangle bounds = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), scroll.getViewport());
                    assertTrue("Action starts inside viewport", bounds.x >= 0);
                    assertTrue("Action ends inside viewport", bounds.x + bounds.width <= scroll.getViewport().getWidth());
                    assertTrue("Action remains readable", bounds.width >= 35);
                }
            }
        });
    }

    private static void layout(Component component)
    {
        if (component instanceof Container)
        {
            ((Container) component).doLayout();
            for (Component child : ((Container) component).getComponents()) { layout(child); }
        }
    }

    private static HistoryStore populatedHistory()
    {
        HistoryStore history = new HistoryStore();
        history.add(LootDrop.builder().id("reward-1").session(history.getSession()).received(Instant.parse("2026-11-19T21:00:00Z"))
            .player("Corndwell").accountType("Normal").source("Tombs of Amascut").rawSource("Tombs of Amascut")
            .type("EVENT").combatLevel(-1).batchCount(1).pet(false).duplicatePet(false).collectionLog(false)
            .items(List.of(new DropItem(1, "First item", 1, 100, 50, Rarity.unknown()),
                new DropItem(2, "Second item", 2, 200, 100, Rarity.unknown())))
            .details(Collections.emptyMap()).build(), "Automatic posting paused");
        return history;
    }

    private static List<JButton> buttons(Component component, String text)
    {
        List<JButton> result = new ArrayList<>();
        if (component instanceof JButton && text.equals(((JButton) component).getText())) { result.add((JButton) component); }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents()) { result.addAll(buttons(child, text)); }
        }
        return result;
    }

    private static void flushEdt() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }

    private static class NoActions implements HistoryPanel.Actions
    {
        @Override public void post(String event, Integer index, String destination, boolean repeat) {}
        @Override public void clearSession() {}
        @Override public void setPaused(boolean paused) {}
        @Override public void testDestination(String destination) {}
        @Override public void preview(String event, Integer index) {}
    }
}

package com.corndwell.lootherald.ui;

import com.corndwell.lootherald.discord.Destination;
import com.corndwell.lootherald.history.DeliveryStatus;
import com.corndwell.lootherald.history.HistoryStore;
import com.corndwell.lootherald.model.DropItem;
import com.corndwell.lootherald.model.LootDrop;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.PluginPanel;

/** Session-only history. Every public update method may be called off the EDT. */
public final class HistoryPanel extends PluginPanel
{
    public interface Actions
    {
        void post(String eventId, Integer itemIndex, String destinationId, boolean repeat);
        void clearSession();
        void setPaused(boolean paused);
        void testDestination(String destinationId);
        void preview(String eventId, Integer itemIndex);
        default void configureDestinations() {}
    }

    private static final Color BACKGROUND = new Color(29, 31, 35);
    private static final Color CARD = new Color(39, 42, 47);
    private static final Color FIELD = new Color(48, 52, 59);
    private static final Color TEXT = new Color(231, 234, 239);
    private static final Color MUTED = new Color(168, 175, 187);
    private static final Color GOLD = new Color(239, 190, 98);
    private static final Color GREEN = new Color(125, 213, 165);
    private static final Color RED = new Color(242, 142, 139);
    private static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    private static final Font SMALL = BODY.deriveFont(11f);
    private static final int PAGE_SIZE = 50;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final HistoryStore history;
    private final Actions actions;
    private final JPanel events = vertical(BACKGROUND);
    private final JScrollPane eventScroll;
    private final JTextField search = new JTextField();
    private final JComboBox<DestinationChoice> destinations = new JComboBox<>();
    private final JCheckBox paused = new JCheckBox("Pause automatic posts");
    private final JLabel diagnostic = new JLabel();
    private final JLabel summary = new JLabel();
    private final JLabel eviction = new JLabel();
    private final JButton test = button("Test destination", false);
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private final Map<String, List<AbstractButton>> sendButtons = new HashMap<>();
    private final Set<String> collapsed = new HashSet<>();
    private final Set<String> expandedItems = new HashSet<>();
    private List<Destination> configuredDestinations = Collections.emptyList();
    private int visibleLimit = PAGE_SIZE;
    private long displayedSession = Long.MIN_VALUE;
    private boolean updatingControls;

    public HistoryPanel(HistoryStore history, Actions actions)
    {
        super(false);
        this.history = Objects.requireNonNull(history);
        this.actions = Objects.requireNonNull(actions);
        setLayout(new BorderLayout(0, 8));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 9, 8, 9));

        JPanel controls = vertical(BACKGROUND);
        JLabel title = label("Loot Herald", GOLD, BODY.deriveFont(Font.BOLD, 19f));
        controls.add(title);
        controls.add(label("YOUR CURRENT SESSION", MUTED, BODY.deriveFont(Font.BOLD, 9f)));
        controls.add(Box.createVerticalStrut(9));
        diagnostic.setFont(SMALL);
        diagnostic.setForeground(GOLD);
        diagnostic.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(diagnostic);
        controls.add(Box.createVerticalStrut(7));

        search.setFont(BODY);
        search.setBackground(FIELD);
        search.setForeground(TEXT);
        search.setCaretColor(TEXT);
        search.setToolTipText("Find an item, source or player in this session");
        search.getAccessibleContext().setAccessibleName("Search session drops");
        search.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(64, 69, 77)),
            BorderFactory.createEmptyBorder(5, 7, 5, 7)));
        fitWidth(search, 28);
        controls.add(label("Search drops", MUTED, SMALL));
        controls.add(Box.createVerticalStrut(3));
        controls.add(search);
        controls.add(Box.createVerticalStrut(7));
        controls.add(label("Post to", MUTED, SMALL));
        destinations.setFont(BODY);
        destinations.setBackground(FIELD);
        destinations.setForeground(TEXT);
        destinations.setRenderer(new DefaultListCellRenderer()
        {
            @Override public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                int index, boolean selected, boolean focused)
            {
                JLabel renderer = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                renderer.putClientProperty("html.disable", true);
                return renderer;
            }
        });
        destinations.getAccessibleContext().setAccessibleName("Discord destination");
        fitWidth(destinations, 27);
        controls.add(Box.createVerticalStrut(3));
        controls.add(destinations);
        controls.add(Box.createVerticalStrut(5));
        JButton configure = button("Destinations…", false);
        configure.setToolTipText("Add or edit up to five named Discord webhooks");
        fitWidth(configure, 25);
        configure.addActionListener(e -> actions.configureDestinations());
        controls.add(configure);
        controls.add(Box.createVerticalStrut(5));

        paused.setFont(SMALL);
        paused.setOpaque(false);
        paused.setForeground(TEXT);
        paused.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        paused.setToolTipText("Continue recording drops while automatic Discord posts are paused. Manual posting stays available.");
        paused.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(paused);
        JPanel utility = new JPanel(new GridLayout(1, 2, 5, 0));
        utility.setOpaque(false);
        fitWidth(utility, 25);
        JButton clear = button("Clear session", false);
        clear.setToolTipText("Forget this session's local history. Already-posted Discord messages remain.");
        utility.add(test);
        utility.add(clear);
        controls.add(Box.createVerticalStrut(4));
        controls.add(utility);
        summary.setFont(SMALL);
        summary.setForeground(MUTED);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        eviction.setFont(SMALL);
        eviction.setForeground(GOLD);
        eviction.setAlignmentX(Component.LEFT_ALIGNMENT);
        controls.add(Box.createVerticalStrut(8));
        controls.add(summary);
        controls.add(eviction);
        add(controls, BorderLayout.NORTH);

        JPanel content = new ScrollContent();
        content.setBackground(BACKGROUND);
        content.add(events, BorderLayout.NORTH);
        eventScroll = new JScrollPane(content);
        eventScroll.setBorder(BorderFactory.createEmptyBorder());
        eventScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        eventScroll.getVerticalScrollBar().setUnitIncrement(18);
        eventScroll.getViewport().setBackground(BACKGROUND);
        add(eventScroll, BorderLayout.CENTER);

        JLabel memoryOnly = label("Session only · nothing saved to disk", MUTED, BODY.deriveFont(10f));
        memoryOnly.setToolTipText("Loot history and screenshots are held in memory; plugin settings are retained.");
        add(memoryOnly, BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
            private void changed()
            {
                visibleLimit = PAGE_SIZE;
                eventScroll.getVerticalScrollBar().setValue(0);
                refresh();
            }
        });
        destinations.addActionListener(e -> { if (!updatingControls) { refresh(); } });
        paused.addActionListener(e -> { if (!updatingControls) { actions.setPaused(paused.isSelected()); } });
        clear.addActionListener(e -> actions.clearSession());
        test.addActionListener(e -> actions.testDestination(selectedDestinationId()));
        setDestinations(Collections.emptyList());
        setDiagnostic("Waiting for loot detection…");
        refresh();
    }

    public void setDestinations(List<Destination> values)
    {
        List<Destination> copy = new ArrayList<>(values == null ? Collections.emptyList() : values);
        onEdt(() ->
        {
            String selectedId = selectedDestinationId();
            updatingControls = true;
            configuredDestinations = Collections.unmodifiableList(copy);
            destinations.removeAllItems();
            destinations.addItem(new DestinationChoice(null, copy.isEmpty() ? "No destinations configured" : "All destinations (" + copy.size() + ")"));
            for (Destination destination : copy)
            {
                DestinationChoice choice = new DestinationChoice(destination.getId(), safe(destination.getName()));
                destinations.addItem(choice);
                if (destination.getId().equals(selectedId)) { destinations.setSelectedItem(choice); }
            }
            destinations.setEnabled(!copy.isEmpty());
            test.setEnabled(!copy.isEmpty());
            updatingControls = false;
            refresh();
        });
    }

    public void setDiagnostic(String message)
    {
        onEdt(() -> diagnostic.setText(html(message == null ? "" : message, 191)));
    }

    public void setPaused(boolean value)
    {
        onEdt(() ->
        {
            updatingControls = true;
            paused.setSelected(value);
            updatingControls = false;
        });
    }

    public void refresh()
    {
        if (refreshQueued.compareAndSet(false, true))
        {
            SwingUtilities.invokeLater(() ->
            {
                refreshQueued.set(false);
                rebuild();
            });
        }
    }

    private void rebuild()
    {
        int position = eventScroll.getVerticalScrollBar().getValue();
        long session = history.getSession();
        if (displayedSession != session)
        {
            displayedSession = session;
            collapsed.clear();
            expandedItems.clear();
            visibleLimit = PAGE_SIZE;
            position = 0;
        }
        List<HistoryStore.Entry> all = history.entries();
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        List<HistoryStore.Entry> matching = all.stream().filter(entry -> matches(entry.getDrop(), query)).collect(Collectors.toList());
        Set<String> retainedIds = all.stream().map(entry -> entry.getDrop().getId()).collect(Collectors.toSet());
        collapsed.retainAll(retainedIds);
        expandedItems.retainAll(retainedIds);
        long itemCount = all.stream().mapToLong(entry -> entry.getDrop().getItems().size()).sum();
        summary.setText(query.isEmpty() ? all.size() + " rewards · " + itemCount + " item stacks"
            : matching.size() + " of " + all.size() + " rewards match");
        long evicted = history.getEvictedCount();
        eviction.setText(evicted == 0 ? "" : html(evicted + " older rewards expired from memory", 191));
        sendButtons.clear();
        events.removeAll();
        if (matching.isEmpty())
        {
            JPanel empty = vertical(CARD);
            empty.setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
            empty.add(label(query.isEmpty() ? "Your next drop goes here" : "No matching drops", TEXT, BODY.deriveFont(Font.BOLD)));
            empty.add(Box.createVerticalStrut(6));
            empty.add(wrapped(query.isEmpty()
                ? "Loot is recorded even when automatic posting is paused or a drop is below your filters."
                : "Try an item name, activity or player.", MUTED, 170));
            events.add(empty);
        }
        else
        {
            for (HistoryStore.Entry entry : matching.subList(0, Math.min(visibleLimit, matching.size())))
            {
                events.add(eventCard(entry));
                events.add(Box.createVerticalStrut(7));
            }
            if (matching.size() > visibleLimit)
            {
                JButton more = button("Show " + Math.min(PAGE_SIZE, matching.size() - visibleLimit) + " more rewards", false);
                fitWidth(more, 28);
                more.addActionListener(e -> { visibleLimit += PAGE_SIZE; refresh(); });
                events.add(more);
            }
        }
        events.revalidate();
        events.repaint();
        final int previousPosition = position;
        SwingUtilities.invokeLater(() -> eventScroll.getVerticalScrollBar().setValue(previousPosition));
    }

    private JPanel eventCard(HistoryStore.Entry entry)
    {
        LootDrop drop = entry.getDrop();
        JPanel card = vertical(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(57, 62, 70)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        boolean closed = collapsed.contains(drop.getId());
        JButton header = new JButton(html((closed ? "+  " : "−  ") + safe(drop.getSource()), 170));
        header.setFont(BODY.deriveFont(Font.BOLD));
        header.setForeground(drop.isPet() ? GOLD : TEXT);
        header.setHorizontalAlignment(JButton.LEFT);
        header.setBorder(BorderFactory.createEmptyBorder());
        header.setContentAreaFilled(false);
        header.setFocusPainted(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setToolTipText("Expand or collapse this reward");
        header.addActionListener(e ->
        {
            if (!collapsed.add(drop.getId())) { collapsed.remove(drop.getId()); }
            refresh();
        });
        card.add(header);
        card.add(label(TIME.format(drop.getReceived()) + " · " + safe(drop.getPlayer()), MUTED, SMALL));
        if (drop.isPet() || drop.isCollectionLog())
        {
            String badge = drop.isDuplicatePet() ? "DUPLICATE PET ROLL" : (drop.isPet() ? "PET RECEIVED" : "COLLECTION LOG");
            if (drop.isPet() && drop.isCollectionLog()) { badge += " · NEW LOG ENTRY"; }
            card.add(wrapped(badge, GOLD, 174));
        }
        if (drop.getBatchCount() > 1) { card.add(label(drop.getBatchCount() + " openings in this batch", MUTED, SMALL)); }
        if (!entry.getReason().isEmpty()) { card.add(wrapped(entry.getReason(), MUTED, 174)); }
        card.add(Box.createVerticalStrut(4));
        card.add(wrapped("Screenshot: " + safe(entry.getScreenshotState()), MUTED, 174));
        addDestinationSummary(card, entry);
        if (closed) { return card; }
        List<DropItem> items = drop.getItems();
        int count = expandedItems.contains(drop.getId()) ? items.size() : Math.min(4, items.size());
        for (int i = 0; i < count; i++)
        {
            card.add(Box.createVerticalStrut(7));
            card.add(itemRow(entry, i));
        }
        if (items.size() > 4)
        {
            JButton expand = button(count < items.size() ? "Show all " + items.size() + " items" : "Show fewer items", false);
            fitWidth(expand, 23);
            expand.addActionListener(e ->
            {
                if (!expandedItems.add(drop.getId())) { expandedItems.remove(drop.getId()); }
                refresh();
            });
            card.add(Box.createVerticalStrut(5));
            card.add(expand);
        }
        if (items.size() > 1)
        {
            card.add(Box.createVerticalStrut(8));
            card.add(actionRow(entry, null));
        }
        return card;
    }

    private JPanel itemRow(HistoryStore.Entry entry, int index)
    {
        DropItem item = entry.getDrop().getItems().get(index);
        JPanel row = vertical(FIELD);
        row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        row.add(wrapped((item.getQuantity() > 1 ? item.getQuantity() + " × " : "") + item.getName(), TEXT, 160));
        if (!entry.getDrop().isPet())
        {
            row.add(label("GE " + value(item.getGeTotal()) + " · HA " + value(item.getHaTotal()), MUTED, SMALL));
        }
        if (item.getRarity() != null && item.getRarity().known())
        {
            JLabel rarity = label("Rarity " + item.getRarity().formatFraction(), GOLD, SMALL);
            rarity.setToolTipText(safe(item.getRarity().getBasis()));
            row.add(rarity);
        }
        for (Destination destination : selectedDestinations())
        {
            HistoryStore.Delivery delivery = delivery(entry, destination.getId(), index);
            DeliveryStatus status = delivery == null ? DeliveryStatus.READY : delivery.getStatus();
            JLabel state = wrapped(safe(destination.getName()) + " · " + statusText(status), statusColor(status), 160);
            state.setToolTipText(delivery == null ? "Not sent to this destination" : safe(delivery.getDetail()));
            row.add(state);
        }
        row.add(Box.createVerticalStrut(5));
        row.add(actionRow(entry, index));
        return row;
    }

    private JPanel actionRow(HistoryStore.Entry entry, Integer itemIndex)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        fitWidth(row, 25);
        List<DeliveryStatus> statuses = statuses(entry, itemIndex);
        PostingState state = PostingState.evaluate(statuses, itemIndex == null, !selectedDestinations().isEmpty());
        JButton send = button(state.label, true);
        send.setEnabled(state.enabled);
        send.setToolTipText(state.hint + " Destination: " + destinationDescription());
        sendButtons.computeIfAbsent(entry.getDrop().getId(), ignored -> new ArrayList<>()).add(send);
        send.addActionListener(e ->
        {
            // Re-check the snapshot at click time, rather than trust a stale rendered state.
            HistoryStore.Entry current = history.get(entry.getDrop().getId());
            if (current == null) { refresh(); return; }
            PostingState currentState = PostingState.evaluate(statuses(current, itemIndex), itemIndex == null, !selectedDestinations().isEmpty());
            if (!currentState.enabled) { refresh(); return; }
            if (currentState.repeat && !confirmRepeat(currentState.uncertain)) { return; }
            for (AbstractButton sibling : sendButtons.getOrDefault(entry.getDrop().getId(), Collections.emptyList()))
            {
                sibling.setEnabled(false);
            }
            send.setText("Queued…");
            actions.post(entry.getDrop().getId(), itemIndex, selectedDestinationId(), currentState.repeat);
        });
        JButton preview = button("View", false);
        preview.setToolTipText("Preview this message without posting");
        preview.getAccessibleContext().setAccessibleName("Preview Discord message");
        preview.setPreferredSize(new Dimension(43, 25));
        preview.addActionListener(e -> actions.preview(entry.getDrop().getId(), itemIndex));
        row.add(send, BorderLayout.CENTER);
        row.add(preview, BorderLayout.EAST);
        return row;
    }

    private boolean confirmRepeat(boolean uncertain)
    {
        String text = uncertain
            ? "Discord may already have received this message.\nPosting again can create a duplicate."
            : "This reward has already been posted.\nSend another copy?";
        return JOptionPane.showConfirmDialog(this, text + "\n\nTo: " + destinationDescription(),
            uncertain ? "Delivery uncertain" : "Post again", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private List<DeliveryStatus> statuses(HistoryStore.Entry entry, Integer itemIndex)
    {
        List<DeliveryStatus> statuses = new ArrayList<>();
        int from = itemIndex == null ? 0 : itemIndex;
        int to = itemIndex == null ? entry.getDrop().getItems().size() : itemIndex + 1;
        if (from < 0 || to > entry.getDrop().getItems().size()) { return statuses; }
        for (Destination destination : selectedDestinations())
        {
            for (int i = from; i < to; i++)
            {
                HistoryStore.Delivery delivery = delivery(entry, destination.getId(), i);
                statuses.add(delivery == null ? DeliveryStatus.READY : delivery.getStatus());
            }
        }
        return statuses;
    }

    private void addDestinationSummary(JPanel card, HistoryStore.Entry entry)
    {
        for (Destination destination : selectedDestinations())
        {
            int sent = 0;
            int pending = 0;
            int uncertain = 0;
            int failed = 0;
            for (int i = 0; i < entry.getDrop().getItems().size(); i++)
            {
                HistoryStore.Delivery delivery = delivery(entry, destination.getId(), i);
                if (delivery == null) { continue; }
                switch (delivery.getStatus())
                {
                    case SENT: sent++; break;
                    case QUEUED: case SENDING: pending++; break;
                    case UNCERTAIN: uncertain++; break;
                    case FAILED: failed++; break;
                    default: break;
                }
            }
            String description = safe(destination.getName()) + ": " + sent + "/" + entry.getDrop().getItems().size() + " sent";
            if (pending > 0) { description += " · " + pending + " pending"; }
            if (failed > 0) { description += " · " + failed + " failed"; }
            if (uncertain > 0) { description += " · " + uncertain + " uncertain"; }
            card.add(wrapped(description, uncertain > 0 ? GOLD : (failed > 0 ? RED : MUTED), 174));
        }
    }

    private static HistoryStore.Delivery delivery(HistoryStore.Entry entry, String destinationId, int index)
    {
        Map<Integer, HistoryStore.Delivery> deliveries = entry.getDeliveries().get(destinationId);
        return deliveries == null ? null : deliveries.get(index);
    }

    private String selectedDestinationId()
    {
        DestinationChoice value = (DestinationChoice) destinations.getSelectedItem();
        return value == null ? null : value.id;
    }

    private List<Destination> selectedDestinations()
    {
        String selectedId = selectedDestinationId();
        return selectedId == null ? configuredDestinations : configuredDestinations.stream()
            .filter(destination -> selectedId.equals(destination.getId())).collect(Collectors.toList());
    }

    private String destinationDescription()
    {
        return selectedDestinations().stream().map(destination -> safe(destination.getName())).collect(Collectors.joining(", "));
    }

    private static boolean matches(LootDrop drop, String query)
    {
        return query.isEmpty() || drop.getSource().toLowerCase(Locale.ROOT).contains(query)
            || drop.getPlayer().toLowerCase(Locale.ROOT).contains(query)
            || drop.getItems().stream().anyMatch(item -> item.getName().toLowerCase(Locale.ROOT).contains(query));
    }

    private static String statusText(DeliveryStatus status)
    {
        switch (status)
        {
            case QUEUED: return "Queued";
            case SENDING: return "Sending";
            case SENT: return "Sent";
            case FAILED: return "Failed";
            case UNCERTAIN: return "Delivery uncertain";
            default: return "Not sent";
        }
    }

    private static Color statusColor(DeliveryStatus status)
    {
        if (status == DeliveryStatus.SENT) { return GREEN; }
        if (status == DeliveryStatus.FAILED) { return RED; }
        if (status == DeliveryStatus.UNCERTAIN || status == DeliveryStatus.SENDING || status == DeliveryStatus.QUEUED) { return GOLD; }
        return MUTED;
    }

    private static String value(long value)
    {
        if (value < 0) { return "—"; }
        if (value >= 1_000_000_000L) { return String.format(Locale.ROOT, "%.1fb", value / 1_000_000_000d); }
        if (value >= 1_000_000L) { return String.format(Locale.ROOT, "%.1fm", value / 1_000_000d); }
        if (value >= 10_000L) { return String.format(Locale.ROOT, "%.1fk", value / 1_000d); }
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static JPanel vertical(Color background)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(background);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JLabel label(String text, Color color, Font font)
    {
        JLabel label = new JLabel(safe(text));
        label.putClientProperty("html.disable", true);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JLabel wrapped(String text, Color color, int width)
    {
        JLabel label = label("", color, SMALL);
        label.putClientProperty("html.disable", false);
        label.setText(html(text, width));
        return label;
    }

    private static JButton button(String text, boolean primary)
    {
        JButton button = new JButton(text);
        button.setFont(SMALL);
        button.setForeground(primary ? BACKGROUND : TEXT);
        button.setBackground(primary ? GOLD : FIELD);
        button.setOpaque(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(4, 5, 4, 5));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        return button;
    }

    private static void fitWidth(javax.swing.JComponent component, int height)
    {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        component.setPreferredSize(new Dimension(190, height));
    }

    private static String safe(String value)
    {
        if (value == null) { return ""; }
        return value.replaceAll("(?i)https?://[^\\s<>]+", "[link]");
    }

    private static String html(String value, int width)
    {
        String escaped = safe(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("\n", "<br>");
        // Swing converts CSS pixels to 1.3 logical pixels; points retain the intended sidebar width.
        return "<html><div style='width:" + width + "pt'>" + escaped + "</div></html>";
    }

    private static void onEdt(Runnable runnable)
    {
        if (SwingUtilities.isEventDispatchThread()) { runnable.run(); }
        else { SwingUtilities.invokeLater(runnable); }
    }

    private static final class DestinationChoice
    {
        private final String id;
        private final String name;
        private DestinationChoice(String id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }

    /** Keeps long rewards inside the sidebar rather than clipping their action buttons. */
    private static final class ScrollContent extends JPanel implements Scrollable
    {
        private ScrollContent() { super(new BorderLayout()); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 18; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction)
        {
            return Math.max(18, visible.height - 18);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}

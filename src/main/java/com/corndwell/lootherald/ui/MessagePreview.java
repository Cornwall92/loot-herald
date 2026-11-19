package com.corndwell.lootherald.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

/** A local rendering of the outgoing text; never resolves links or loads external images. */
public final class MessagePreview
{
    private static final Color BACKGROUND = new Color(29, 31, 35);
    private static final Color CARD = new Color(43, 45, 49);
    private static final Color FIELD = new Color(34, 37, 41);
    private static final Color TEXT = new Color(235, 237, 240);
    private static final Color MUTED = new Color(177, 182, 191);
    private static final Color GOLD = new Color(239, 190, 98);
    private static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    private static final int TEXT_WIDTH = 350;
    private static final Pattern LINK = Pattern.compile("(?<!!)(?<!\\\\)\\[((?:\\\\.|[^\\]\\r\\n])+)\\]\\((?:https?://|attachment://)[^\\s)]*\\)");
    private static final Pattern EMPHASIS = Pattern.compile("(?<!\\\\)(\\*\\*|__|~~|\\|\\|)(.+?)(?<!\\\\)\\1");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm:ss z").withZone(ZoneId.systemDefault());

    private MessagePreview() {}

    /** Safe to call from any thread. The modal itself and its components are created on the EDT. */
    public static void show(Component parent, JsonObject payload, String screenshotState)
    {
        JsonObject snapshot = payload == null ? new JsonObject() : payload.deepCopy();
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(() -> show(parent, snapshot, screenshotState));
            return;
        }
        JDialog dialog = new JDialog(parent == null ? null : SwingUtilities.getWindowAncestor(parent),
            "Discord message preview", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel frame = new JPanel(new BorderLayout(0, 10));
        frame.setBackground(BACKGROUND);
        frame.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel heading = vertical(BACKGROUND);
        heading.add(text("Message preview", GOLD, BODY.deriveFont(Font.BOLD, 19f), TEXT_WIDTH));
        heading.add(text("Nothing is sent from this window.", MUTED, BODY.deriveFont(11f), TEXT_WIDTH));
        frame.add(heading, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(createContent(snapshot, screenshotState));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.getViewport().setBackground(BACKGROUND);
        frame.add(scroll, BorderLayout.CENTER);

        JButton close = new JButton("Close preview");
        close.setBackground(FIELD);
        close.setForeground(TEXT);
        close.setFocusPainted(false);
        close.addActionListener(e -> dialog.dispose());
        frame.add(close, BorderLayout.SOUTH);
        dialog.setContentPane(frame);
        dialog.getRootPane().setDefaultButton(close);
        dialog.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "closePreview");
        dialog.getRootPane().getActionMap().put("closePreview", new AbstractAction()
        {
            @Override public void actionPerformed(ActionEvent event) { dialog.dispose(); }
        });
        dialog.pack();
        dialog.setSize(450, Math.min(600, Math.max(260, dialog.getHeight())));
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    /** Package-visible so the local view can be verified without a desktop or Discord connection. */
    static JPanel createContent(JsonObject payload, String screenshotState)
    {
        PreviewContent content = new PreviewContent();
        content.setBackground(BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 7));
        String plain = string(payload, "content");
        if (!plain.isEmpty())
        {
            content.add(text(plain, TEXT, BODY, TEXT_WIDTH));
            content.add(Box.createVerticalStrut(12));
        }
        JsonArray embeds = array(payload, "embeds");
        int cards = 0;
        for (JsonElement element : embeds)
        {
            if (!element.isJsonObject() || cards >= 10) { continue; }
            content.add(embed(element.getAsJsonObject()));
            content.add(Box.createVerticalStrut(12));
            cards++;
        }
        if (plain.isEmpty() && cards == 0)
        {
            content.add(text("There is no message text to preview.", MUTED, BODY, TEXT_WIDTH));
            content.add(Box.createVerticalStrut(12));
        }
        content.add(text("Screenshot: " + (screenshotState == null || screenshotState.trim().isEmpty() ? "Unavailable" : screenshotState),
            GOLD, BODY.deriveFont(11f), TEXT_WIDTH));
        content.add(text("Images are not loaded in this local preview. Discord may display the configured item icon, account badge and available screenshot.",
            MUTED, BODY.deriveFont(11f), TEXT_WIDTH));
        return content;
    }

    private static JPanel embed(JsonObject embed)
    {
        JPanel card = vertical(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 4, 0, 0, accent(embed)),
            BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        String author = string(object(embed, "author"), "name");
        if (!author.isEmpty())
        {
            card.add(text(author, TEXT, BODY.deriveFont(Font.BOLD, 12f), TEXT_WIDTH));
            card.add(Box.createVerticalStrut(9));
        }
        String title = string(embed, "title");
        if (!title.isEmpty())
        {
            card.add(text(title, TEXT, BODY.deriveFont(Font.BOLD, 17f), TEXT_WIDTH));
            card.add(Box.createVerticalStrut(7));
        }
        String description = string(embed, "description");
        if (!description.isEmpty())
        {
            card.add(text(description, TEXT, BODY, TEXT_WIDTH));
            card.add(Box.createVerticalStrut(10));
        }
        List<JsonObject> inline = new ArrayList<>();
        int shown = 0;
        for (JsonElement value : array(embed, "fields"))
        {
            if (!value.isJsonObject() || shown++ >= 25) { continue; }
            JsonObject field = value.getAsJsonObject();
            if (isInline(field))
            {
                inline.add(field);
                if (inline.size() == 3) { addFields(card, inline); inline.clear(); }
            }
            else
            {
                if (!inline.isEmpty()) { addFields(card, inline); inline.clear(); }
                addFields(card, java.util.Collections.singletonList(field));
            }
        }
        if (!inline.isEmpty()) { addFields(card, inline); }
        String footer = string(object(embed, "footer"), "text");
        String timestamp = string(embed, "timestamp");
        if (!timestamp.isEmpty())
        {
            try { timestamp = TIME.format(Instant.parse(timestamp)); }
            catch (DateTimeParseException ignored) { /* Preserve unfamiliar timestamps as plain text. */ }
            footer += (footer.isEmpty() ? "" : " · ") + timestamp;
        }
        if (!footer.isEmpty())
        {
            card.add(Box.createVerticalStrut(6));
            card.add(text(footer, MUTED, BODY.deriveFont(10f), TEXT_WIDTH));
        }
        return card;
    }

    private static void addFields(JPanel card, List<JsonObject> fields)
    {
        JPanel row = new JPanel(new GridLayout(1, fields.size(), 7, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        int width = (TEXT_WIDTH - (fields.size() - 1) * 7) / fields.size() - 12;
        for (JsonObject field : fields)
        {
            JPanel cell = vertical(FIELD);
            cell.setBorder(BorderFactory.createEmptyBorder(7, 6, 7, 6));
            cell.add(text(string(field, "name"), TEXT, BODY.deriveFont(Font.BOLD, 11f), width));
            cell.add(Box.createVerticalStrut(5));
            cell.add(text(string(field, "value"), GOLD, BODY.deriveFont(12f), width));
            row.add(cell);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        card.add(row);
        card.add(Box.createVerticalStrut(7));
    }

    private static JPanel vertical(Color background)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(background);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JLabel text(String value, Color color, Font font, int width)
    {
        JLabel label = new JLabel(renderText(value, width));
        label.setForeground(color);
        label.setFont(font);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** Only trusted layout tags are emitted. Payload text can never create HTML, links or images. */
    static String renderText(String value, int width)
    {
        String escaped = readable(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;").replace("\n", "<br>");
        return "<html><div style='width:" + Math.max(30, width) + "pt'>" + escaped + "</div></html>";
    }

    /** Retains link labels and escaped literal punctuation, without activating links or mentions. */
    static String readable(String value)
    {
        if (value == null) { return ""; }
        String safe = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        Matcher links = LINK.matcher(safe);
        StringBuffer labels = new StringBuffer();
        while (links.find()) { links.appendReplacement(labels, Matcher.quoteReplacement(links.group(1))); }
        links.appendTail(labels);
        safe = EMPHASIS.matcher(labels.toString()).replaceAll("$2");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < safe.length(); i++)
        {
            char c = safe.charAt(i);
            if (c == '\\' && i + 1 < safe.length() && "\\`*_~|[]()<>".indexOf(safe.charAt(i + 1)) >= 0)
            {
                out.append(safe.charAt(++i));
            }
            else if (c != '`') { out.append(c); }
        }
        return out.toString();
    }

    private static JsonObject object(JsonObject parent, String key)
    {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String key)
    {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject parent, String key)
    {
        JsonElement value = parent == null ? null : parent.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static boolean isInline(JsonObject field)
    {
        JsonElement value = field.get("inline");
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    private static Color accent(JsonObject embed)
    {
        try
        {
            JsonElement value = embed.get("color");
            return value != null && value.isJsonPrimitive() ? new Color(value.getAsInt() & 0xFFFFFF) : GOLD;
        }
        catch (NumberFormatException ignored) { return GOLD; }
    }

    private static final class PreviewContent extends JPanel implements Scrollable
    {
        private PreviewContent() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 20; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return Math.max(20, visible.height - 20); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}

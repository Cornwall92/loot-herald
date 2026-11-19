package com.corndwell.lootherald.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class MessagePreviewTest
{
    @Test public void markdownLinksBecomeLabelsAndEscapedNamesStayLiteral()
    {
        assertEquals("Just got Tumeken's shadow from Tombs of Amascut.", MessagePreview.readable(
            "Just got [Tumeken's shadow](https://oldschool.runescape.wiki/w/Special:Lookup?type=item&id=27275) from [Tombs of Amascut](https://oldschool.runescape.wiki/w/Tombs_of_Amascut)."));
        assertEquals("Player_name · **literal** · 1/128", MessagePreview.readable("Player\\_name · \\*\\*literal\\*\\* · `1/128`"));
        assertEquals("A rare drop", MessagePreview.readable("**A rare drop**"));
    }

    @Test public void payloadMarkupCannotCreateRemoteImagesOrExecutableHtml()
    {
        String html = MessagePreview.renderText("<html><img src='https://example.invalid/track'><a href=\"file:///secret\">click</a>&", 350);
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("<a "));
        assertTrue(html.contains("&lt;img"));
        assertTrue(html.contains("&#39;"));
        assertTrue(html.endsWith("&amp;</div></html>"));
    }

    @Test public void embedIncludesAuthorFieldsFooterTimestampAndScreenshotState() throws Exception
    {
        JsonObject payload = new JsonObject();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Loot received!");
        embed.addProperty("description", "Just got a rare drop.");
        JsonObject author = new JsonObject(); author.addProperty("name", "Corndwell");
        author.addProperty("icon_url", "https://example.invalid/player.png"); embed.add("author", author);
        JsonObject footer = new JsonObject(); footer.addProperty("text", "Loot Herald"); embed.add("footer", footer);
        embed.addProperty("timestamp", "2026-11-19T21:00:00Z");
        JsonArray fields = new JsonArray();
        JsonObject field = new JsonObject(); field.addProperty("name", "GE value"); field.addProperty("value", "1,250,000 GP"); field.addProperty("inline", true); fields.add(field);
        embed.add("fields", fields);
        JsonObject image = new JsonObject(); image.addProperty("url", "https://example.invalid/drop.png"); embed.add("image", image);
        JsonArray embeds = new JsonArray(); embeds.add(embed); payload.add("embeds", embeds);
        JPanel[] panel = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = MessagePreview.createContent(payload, "Available"));
        String labels = String.join("\n", labels(panel[0]));
        for (String expected : List.of("Corndwell", "Loot received!", "GE value", "1,250,000 GP", "Loot Herald", "19 Nov 2026", "Screenshot: Available"))
        {
            assertTrue(expected, labels.contains(expected));
        }
        assertFalse(labels.contains("example.invalid"));
        assertFalse(labels.contains("<img"));
    }

    @Test public void plainContentAndMalformedOptionalFieldsRemainReadable() throws Exception
    {
        JsonObject payload = new JsonObject(); payload.addProperty("content", "First line\nSecond line");
        payload.addProperty("embeds", "not an array");
        JPanel[] panel = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = MessagePreview.createContent(payload, null));
        String labels = String.join("\n", labels(panel[0]));
        assertTrue(labels.contains("First line<br>Second line"));
        assertTrue(labels.contains("Screenshot: Unavailable"));
    }

    private static List<String> labels(Component component)
    {
        List<String> labels = new ArrayList<>();
        if (component instanceof JLabel) { labels.add(((JLabel) component).getText()); }
        if (component instanceof Container)
        {
            for (Component child : ((Container) component).getComponents()) { labels.addAll(labels(child)); }
        }
        return labels;
    }
}

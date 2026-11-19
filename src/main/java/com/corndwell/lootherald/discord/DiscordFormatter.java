package com.corndwell.lootherald.discord;

import com.corndwell.lootherald.model.DropItem;
import com.corndwell.lootherald.model.LootDrop;
import com.corndwell.lootherald.model.Rarity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds Discord payloads only; performs no client reads or network requests. */
public final class DiscordFormatter
{
    private static final String WIKI = "https://oldschool.runescape.wiki/w/Special:Search?search=";
    private static final String ITEM_WIKI = "https://oldschool.runescape.wiki/w/Special:Lookup?type=item&id=";
    private static final String ICON = "https://static.runelite.net/cache/item/icon/";

    public JsonObject format(LootDrop drop, List<Integer> indices, EmbedOptions options, boolean screenshot)
    {
        List<DropItem> items = selected(drop, indices);
        JsonObject payload = new JsonObject();
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray());
        mentions.addProperty("replied_user", false);
        payload.add("allowed_mentions", mentions);
        if (!options.embedded)
        {
            payload.addProperty("content", plain(drop, items, options));
            return payload;
        }

        JsonObject embed = new JsonObject();
        embed.addProperty("color", options.accentColor);
        if (options.showPlayer)
        {
            JsonObject author = new JsonObject();
            author.addProperty("name", limit(clean(drop.getPlayer()), 160));
            String badge = badge(drop.getAccountType());
            if (options.showAccountBadge && badge != null)
            {
                author.addProperty("icon_url", "https://oldschool.runescape.wiki/w/Special:FilePath/" + encode(badge));
            }
            embed.add("author", author);
        }
        String title = drop.isPet() ? (drop.isDuplicatePet() ? "Duplicate pet roll" : "A new companion!")
            : drop.isCollectionLog() ? "Collection log unlocked!"
            : items.size() == 1 && items.get(0).getRarity().isUnique() ? "Unique drop!" : "Loot received!";
        embed.addProperty("title", title);
        String source = source(drop, options);
        String description = items.size() == 1
            ? (drop.isPet() && drop.isDuplicatePet() ? "Would have received " : "Just got ")
                + number(items.get(0).getQuantity()) + "× " + itemLink(items.get(0), options) + " from " + source + "."
            : "Received " + number(items.size()) + " item stacks from " + source + ".";
        if (drop.isPet() && items.size() == 1) {
            DropItem pet = items.get(0);
            boolean unnamed = pet.getId() < 0;
            description = drop.isDuplicatePet()
                ? "Would have received " + (unnamed ? "a pet" : itemLink(pet, options)) + " again"
                : "Received " + (unnamed ? "a pet" : itemLink(pet, options));
            if (!drop.getSource().equalsIgnoreCase("Unknown source") && !drop.getSource().isBlank()) { description += " from " + source; }
            description += ".";
        }
        if (drop.getBatchCount() > 1) { description += "\nCombined " + number(drop.getBatchCount()) + " rewards."; }
        embed.addProperty("description", limit(description, 1800));

        JsonArray fields = new JsonArray();
        if (items.size() == 1)
        {
            DropItem item = items.get(0);
            String rarity = rarity(item.getRarity(), options);
            if (!rarity.isEmpty()) { field(fields, "Rarity", rarity, true); }
            if (options.showHaValue && !drop.isPet()) { field(fields, "HA value", price(item.getHaTotal()), true); }
            if (options.showGeValue && !drop.isPet()) { field(fields, "GE value", price(item.getGeTotal()), true); }
        }
        else
        {
            int visible = Math.min(items.size(), 15);
            for (int i = 0; i < visible; i++)
            {
                DropItem item = items.get(i);
                StringBuilder value = new StringBuilder(options.wikiLinks && item.getId() > 0
                    ? "[Wiki](" + ITEM_WIKI + item.getId() + ")" : "");
                if (options.showGeValue && !drop.isPet()) { value.append("\nGE: ").append(price(item.getGeTotal())); }
                if (options.showHaValue && !drop.isPet()) { value.append(" · HA: ").append(price(item.getHaTotal())); }
                String rarity = rarity(item.getRarity(), options);
                if (!rarity.isEmpty()) { value.append("\nRarity: ").append(rarity); }
                field(fields, number(item.getQuantity()) + "× " + escape(limit(item.getName(), 60)), limit(value.toString().trim(), 350), false);
            }
            if (visible < items.size())
            {
                field(fields, "Additional items", number(items.size() - visible) + " further stacks included in this reward; see local history for the full list.", false);
            }
            if (options.showGeValue && !drop.isPet()) { field(fields, "Total GE value", total(items, true), true); }
            if (options.showHaValue && !drop.isPet()) { field(fields, "Total HA value", total(items, false), true); }
        }
        if (options.showRaidMetadata && !drop.getDetails().isEmpty())
        {
            StringBuilder details = new StringBuilder();
            for (Map.Entry<String, String> entry : drop.getDetails().entrySet())
            {
                if (details.length() > 500) { details.append("\n…"); break; }
                if (details.length() > 0) { details.append('\n'); }
                details.append(escape(limit(entry.getKey(), 60))).append(": ").append(escape(limit(entry.getValue(), 160)));
            }
            field(fields, "Reward details", limit(details.toString(), 650), false);
        }
        if (fields.size() > 0) { embed.add("fields", fields); }
        if (options.showThumbnail && !items.isEmpty() && items.get(0).getId() > 0)
        {
            JsonObject thumbnail = new JsonObject();
            thumbnail.addProperty("url", ICON + items.get(0).getId() + ".png");
            embed.add("thumbnail", thumbnail);
        }
        if (screenshot)
        {
            JsonObject image = new JsonObject();
            image.addProperty("url", "attachment://loot.png");
            embed.add("image", image);
        }
        if (options.showTimestamp) { embed.addProperty("timestamp", drop.getReceived().toString()); }
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Loot Herald");
        embed.add("footer", footer);
        enforceTotalLimit(embed);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        return payload;
    }

    private static List<DropItem> selected(LootDrop drop, List<Integer> indices)
    {
        if (drop == null || indices == null || indices.isEmpty()) { throw new IllegalArgumentException("No items selected."); }
        List<DropItem> out = new ArrayList<>();
        for (Integer index : new LinkedHashSet<>(indices))
        {
            if (index == null || index < 0 || index >= drop.getItems().size()) { throw new IllegalArgumentException("Invalid item selection."); }
            out.add(drop.getItems().get(index));
        }
        return out;
    }

    private static String plain(LootDrop drop, List<DropItem> items, EmbedOptions options)
    {
        StringBuilder text = new StringBuilder();
        if (options.showPlayer) { text.append(escape(limit(drop.getPlayer(), 80))).append(": "); }
        text.append(drop.isPet() ? (drop.isDuplicatePet() ? "Duplicate pet roll" : "Pet received") : "Loot received")
            .append(" from ").append(source(drop, options)).append('\n');
        int shown = 0;
        for (DropItem item : items)
        {
            String line = (drop.isPet() && drop.isDuplicatePet() ? "Would have received " : "")
                + number(item.getQuantity()) + "× " + escape(limit(item.getName(), 100));
            if (options.showGeValue && !drop.isPet()) { line += " · GE " + price(item.getGeTotal()); }
            if (options.showHaValue && !drop.isPet()) { line += " · HA " + price(item.getHaTotal()); }
            String rarity = rarity(item.getRarity(), options);
            if (!rarity.isEmpty()) { line += " · " + rarity; }
            if (text.length() + line.length() > 1650) { break; }
            text.append(line).append('\n');
            shown++;
        }
        if (shown < items.size()) { text.append("Plus ").append(items.size() - shown).append(" further item stacks; see local history.\n"); }
        if (items.size() > 1 && options.showGeValue && !drop.isPet()) { text.append("Total GE: ").append(total(items, true)).append('\n'); }
        if (options.showRaidMetadata && text.length() < 1800)
        {
            for (Map.Entry<String, String> detail : drop.getDetails().entrySet())
            {
                String line = escape(limit(detail.getKey(), 40)) + ": " + escape(limit(detail.getValue(), 100)) + "\n";
                if (text.length() + line.length() > 1900) { break; }
                text.append(line);
            }
        }
        if (options.showTimestamp) { text.append(drop.getReceived()); }
        return limit(text.toString(), 2000);
    }

    private static String source(LootDrop drop, EmbedOptions options)
    {
        String source = escape(limit(drop.getSource(), 120));
        if (options.wikiLinks) { source = "[" + source + "](" + WIKI + encode(limit(drop.getSource(), 120)) + ")"; }
        return options.showCombatLevel && drop.getCombatLevel() > 0 ? "level " + drop.getCombatLevel() + " " + source : source;
    }

    private static String itemLink(DropItem item, EmbedOptions options)
    {
        String name = escape(limit(item.getName(), 100));
        String target = item.getId() > 0 ? ITEM_WIKI + item.getId() : WIKI + encode(limit(item.getName(), 100));
        return options.wikiLinks ? "[" + name + "](" + target + ")" : name;
    }

    private static String rarity(Rarity rarity, EmbedOptions options)
    {
        if (!options.showRarityFraction && !options.showRarityPercent) { return ""; }
        if (rarity == null || !rarity.known()) {
            if (!options.showUnknownRarity) { return ""; }
            return rarity != null && rarity.getBasis() != null && !rarity.getBasis().isEmpty()
                ? "Unknown\n" + escape(limit(rarity.getBasis(), 180)) : "Unknown";
        }
        String value = options.showRarityFraction ? rarity.formatFraction() : "";
        if (options.showRarityPercent) { value += (value.isEmpty() ? "" : " · ") + rarity.formatPercent(); }
        if (rarity.getBasis() != null && !rarity.getBasis().isEmpty()) { value += "\n" + escape(limit(rarity.getBasis(), 100)); }
        return limit(value, 180);
    }

    private static String total(List<DropItem> items, boolean ge)
    {
        long sum = 0;
        boolean unknown = false;
        for (DropItem item : items)
        {
            long value = ge ? item.getGeTotal() : item.getHaTotal();
            if (value < 0) { unknown = true; continue; }
            sum = sum > Long.MAX_VALUE - value ? Long.MAX_VALUE : sum + value;
        }
        return price(sum) + (unknown ? " + unknown values" : "");
    }

    private static String price(long value) { return value < 0 ? "Unknown" : number(value) + " GP"; }
    private static String number(long value) { return NumberFormat.getIntegerInstance(Locale.UK).format(value); }
    private static String encode(String value) { return URLEncoder.encode(clean(value), StandardCharsets.UTF_8).replace("+", "%20"); }

    private static void field(JsonArray fields, String name, String value, boolean inline)
    {
        JsonObject field = new JsonObject();
        field.addProperty("name", limit(name, 256));
        field.addProperty("value", limit(value.isEmpty() ? "—" : value, 1024));
        field.addProperty("inline", inline);
        fields.add(field);
    }

    private static String badge(String type)
    {
        String normalized = clean(type).toUpperCase(Locale.ROOT).replace(' ', '_');
        switch (normalized)
        {
            case "IRONMAN": return "Ironman_chat_badge.png";
            case "HARDCORE_IRONMAN": return "Hardcore_ironman_chat_badge.png";
            case "ULTIMATE_IRONMAN": return "Ultimate_ironman_chat_badge.png";
            case "GROUP_IRONMAN": return "Group_ironman_chat_badge.png";
            case "HARDCORE_GROUP_IRONMAN": return "Hardcore_group_ironman_chat_badge.png";
            case "UNRANKED_GROUP_IRONMAN": return "Unranked_group_ironman_chat_badge.png";
            default: return null;
        }
    }

    static String escape(String input)
    {
        String value = clean(input);
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            if ("\\`*_~|[]()<>".indexOf(c) >= 0) { escaped.append('\\'); }
            escaped.append(c);
            if (c == '@') { escaped.append('\u200b'); }
        }
        return escaped.toString();
    }

    private static String clean(String value)
    {
        return value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
    }

    static String limit(String value, int max)
    {
        if (value == null) { return ""; }
        if (value.length() <= max) { return value; }
        int end = max - 1;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) { end--; }
        return value.substring(0, end) + "…";
    }

    private static void enforceTotalLimit(JsonObject embed)
    {
        int used = embed.get("title").getAsString().length() + embed.get("description").getAsString().length() + 11;
        if (embed.has("author")) { used += embed.getAsJsonObject("author").get("name").getAsString().length(); }
        if (!embed.has("fields")) { return; }
        JsonArray fields = embed.getAsJsonArray("fields");
        // The construction reserves a conservative budget; trimming individual values preserves every field label.
        for (int i = 0; i < fields.size(); i++) { used += fields.get(i).getAsJsonObject().get("name").getAsString().length(); }
        int remaining = Math.max(0, 5900 - used);
        for (int i = 0; i < fields.size(); i++)
        {
            JsonObject field = fields.get(i).getAsJsonObject();
            String value = field.get("value").getAsString();
            int budget = Math.max(1, remaining / (fields.size() - i));
            value = limit(value, Math.min(1024, budget));
            field.addProperty("value", value);
            remaining -= value.length();
        }
    }
}

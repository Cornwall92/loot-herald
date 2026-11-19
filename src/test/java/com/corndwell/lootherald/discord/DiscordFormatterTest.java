package com.corndwell.lootherald.discord;

import com.corndwell.lootherald.model.DropItem;
import com.corndwell.lootherald.model.LootDrop;
import com.corndwell.lootherald.model.Rarity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscordFormatterTest
{
    static LootDrop drop(List<DropItem> items)
    {
        return LootDrop.builder().id("event").session(1).received(Instant.parse("2026-11-19T21:00:00Z"))
            .player("Corndwell").accountType("IRONMAN").source("Tombs of Amascut").rawSource("Tombs of Amascut")
            .type("EVENT").combatLevel(0).batchCount(1).items(items).details(Map.of("Raid level", "300")).build();
    }

    static LootDrop drop()
    {
        return drop(List.of(new DropItem(1, "Example drop", 2, 1_500_000, 50_000, new Rarity(0.01, "Per roll", true))));
    }

    @Test public void referenceStyleIncludesItemSourceRarityAndStackValues()
    {
        JsonObject payload = new DiscordFormatter().format(drop(), List.of(0), EmbedOptions.builder().build(), true);
        JsonObject embed = payload.getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertTrue(embed.get("description").getAsString().contains("2× [Example drop]"));
        assertTrue(embed.toString().contains("3,000,000 GP"));
        assertTrue(embed.toString().contains("100,000 GP"));
        assertTrue(embed.toString().contains("1/100"));
        assertTrue(embed.toString().contains("1%"));
        assertEquals("attachment://loot.png", embed.getAsJsonObject("image").get("url").getAsString());
        assertEquals(0, payload.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
    }

    @Test public void presentationTogglesAndUnknownAreRespected()
    {
        LootDrop drop = drop(List.of(new DropItem(1, "Pet", 1, -1, -1, Rarity.unknown())));
        EmbedOptions options = EmbedOptions.builder().showPlayer(false).showGeValue(false).showHaValue(false)
            .showThumbnail(false).showTimestamp(false).showRaidMetadata(false).wikiLinks(false).showUnknownRarity(true).build();
        JsonObject embed = new DiscordFormatter().format(drop, List.of(0), options, false).getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertFalse(embed.has("author"));
        assertFalse(embed.has("thumbnail"));
        assertFalse(embed.has("timestamp"));
        assertFalse(embed.toString().contains("https://"));
        assertTrue(embed.toString().contains("Unknown"));
        assertEquals(1, embed.getAsJsonArray("fields").size());
    }

    @Test public void bundledValuesAreOnlySelectedAndSelectionIsDeduplicated()
    {
        LootDrop drop = drop(List.of(new DropItem(1, "First", 2, 10, 5, Rarity.unknown()),
            new DropItem(2, "Second", 3, 100, 50, Rarity.unknown()), new DropItem(3, "Unselected", 1, 999, 999, Rarity.unknown())));
        String text = new DiscordFormatter().format(drop, List.of(0, 1, 1), EmbedOptions.builder().build(), false).toString();
        assertTrue(text.contains("320 GP"));
        assertFalse(text.contains("Unselected"));
        assertTrue(text.contains("2 item stacks"));
    }

    @Test public void pathologicalNamesAndLargeRewardsFitDiscordLimits()
    {
        List<DropItem> items = new ArrayList<>();
        for (int i = 0; i < 50; i++) { items.add(new DropItem(i + 1, "@everyone [unsafe]*".repeat(100), 999, 50, 10, Rarity.unknown())); }
        List<Integer> indices = IntStream.range(0, items.size()).boxed().collect(Collectors.toList());
        JsonObject payload = new DiscordFormatter().format(drop(items), indices, EmbedOptions.builder().build(), false);
        assertFalse(payload.toString().contains("@everyone"));
        JsonObject embed = payload.getAsJsonArray("embeds").get(0).getAsJsonObject();
        JsonArray fields = embed.getAsJsonArray("fields");
        assertTrue(fields.size() <= 25);
        int size = embed.get("title").getAsString().length() + embed.get("description").getAsString().length()
            + embed.getAsJsonObject("author").get("name").getAsString().length() + embed.getAsJsonObject("footer").get("text").getAsString().length();
        for (int i = 0; i < fields.size(); i++)
        {
            JsonObject field = fields.get(i).getAsJsonObject();
            String name = field.get("name").getAsString(), value = field.get("value").getAsString();
            assertTrue(name.length() <= 256); assertTrue(value.length() <= 1024); size += name.length() + value.length();
        }
        assertTrue(size <= 6000);
        assertTrue(embed.toString().contains("further stacks"));
        JsonObject plain = new DiscordFormatter().format(drop(items), indices, EmbedOptions.builder().embedded(false).build(), false);
        assertTrue(plain.get("content").getAsString().length() <= 2000);
    }

    @Test public void exactItemIdsGiveStableRuneLiteThumbnailUrls()
    {
        JsonObject embed = new DiscordFormatter().format(drop(), List.of(0), EmbedOptions.builder().build(), false)
            .getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertEquals("https://static.runelite.net/cache/item/icon/1.png", embed.getAsJsonObject("thumbnail").get("url").getAsString());
    }

    @Test public void manualCommonDropDoesNotClaimToBeRare()
    {
        LootDrop common = drop(List.of(new DropItem(1, "Common item", 1, 1, 1, Rarity.unknown())));
        JsonObject embed = new DiscordFormatter().format(common, List.of(0), EmbedOptions.builder().build(), false)
            .getAsJsonArray("embeds").get(0).getAsJsonObject();
        assertEquals("Loot received!", embed.get("title").getAsString());
    }

    @Test public void plainTextDuplicatePetIsExplicitlyADuplicateRoll()
    {
        LootDrop duplicate = drop().toBuilder().pet(true).duplicatePet(true).build();
        String content = new DiscordFormatter().format(duplicate, List.of(0), EmbedOptions.builder().embedded(false).build(), false)
            .get("content").getAsString();
        assertTrue(content.contains("Duplicate pet roll"));
        assertTrue(content.contains("Would have received"));
        assertFalse(content.contains("Pet received"));
    }

    @Test public void duplicatePetEmbedDoesNotClaimActualReceipt()
    {
        LootDrop duplicate = drop().toBuilder().pet(true).duplicatePet(true).build();
        JsonObject embed = new DiscordFormatter().format(duplicate, List.of(0), EmbedOptions.builder().build(), false)
            .getAsJsonArray("embeds").get(0).getAsJsonObject();
        String description = embed.get("description").getAsString();
        assertTrue(description.startsWith("Would have received "));
        assertFalse(description.contains("Just got"));
    }

    @Test public void petPostsNeverIncludeGeOrHaValuesEvenWhenEnabled()
    {
        for (Rarity rarity : List.of(Rarity.unknown(), new Rarity(0.001, "Test odds", true)))
        {
            long value = rarity.known() ? 100 : -1;
            LootDrop pet = drop(List.of(new DropItem(1, "Pet", 1, value, value, rarity))).toBuilder().pet(true).build();
            String embedded = new DiscordFormatter().format(pet, List.of(0), EmbedOptions.builder().showUnknownRarity(true).build(), false).toString();
            assertFalse(embedded.contains("GE value"));
            assertFalse(embedded.contains("HA value"));
            String plain = new DiscordFormatter().format(pet, List.of(0), EmbedOptions.builder().embedded(false).build(), false).get("content").getAsString();
            assertFalse(plain.contains("GE "));
            assertFalse(plain.contains("HA "));
        }
    }
}

package com.corndwell.lootherald;

import net.runelite.client.config.*;

import java.awt.Color;

@ConfigGroup(LootHeraldConfig.GROUP)
public interface LootHeraldConfig extends Config {
    String GROUP = "loot-herald";
    String NETWORK_WARNING =
            "This feature submits your IP address to a 3rd-party server not controlled or verified"
                + " by RuneLite developers";

    @ConfigSection(
            name = "Discord",
            description = "Destinations and posting controls",
            position = 0)
    String DISCORD = "discord";

    @ConfigSection(
            name = "Automatic filters",
            description =
                    "History still records filtered drops; manual posting bypasses these filters",
            position = 1,
            closedByDefault = true)
    String FILTERS = "filters";

    @ConfigSection(
            name = "Message appearance",
            description = "Choose information included in the Discord message",
            position = 2,
            closedByDefault = true)
    String DISPLAY = "display";

    @ConfigSection(
            name = "Screenshots",
            description = "Optional receipt-time images held in memory only",
            position = 3,
            closedByDefault = true)
    String IMAGES = "images";

    @ConfigSection(
            name = "Pets",
            description = "Pet awards are detected separately from ordinary loot",
            position = 4,
            closedByDefault = true)
    String PETS = "pets";

    @ConfigItem(
            keyName = "postingEnabled",
            name = "Enable Discord posting",
            description =
                    "Allow automatic, manual and test posts to the configured Discord webhooks",
            section = DISCORD,
            position = 0,
            warning = NETWORK_WARNING)
    default boolean postingEnabled() {
        return false;
    }

    @ConfigItem(
            keyName = "webhooks",
            name = "Webhook destinations",
            description =
                    "One per line: Name | Discord webhook URL. Maximum five. Treat these URLs as"
                        + " passwords.",
            section = DISCORD,
            position = 1,
            secret = true)
    default String webhooks() {
        return "";
    }

    @ConfigItem(
            keyName = "automatic",
            name = "Automatic posting",
            description = "Turn off to keep history for manual posting only",
            section = DISCORD,
            position = 2)
    default boolean automatic() {
        return true;
    }

    @ConfigItem(
            keyName = "routes",
            name = "Destination rules",
            description =
                    "Optional lines: destination name | source wildcard | player wildcard | minimum"
                        + " GP. Blank sends matching drops to all destinations. Manual All uses"
                        + " source/player routing but ignores minimum GP.",
            section = DISCORD,
            position = 3)
    default String routes() {
        return "";
    }

    enum ValueBasis {
        GRAND_EXCHANGE,
        HIGH_ALCHEMY,
        HIGHER_VALUE
    }

    enum MatchMode {
        VALUE_OR_RARITY,
        VALUE_AND_RARITY,
        VALUE_ONLY,
        RARITY_ONLY
    }

    @Range(min = 0, max = Integer.MAX_VALUE)
    @ConfigItem(
            keyName = "minimumValue",
            name = "Minimum value (GP)",
            description = "Minimum stack value for automatic posting",
            section = FILTERS,
            position = 0)
    default int minimumValue() {
        return 50000;
    }

    @ConfigItem(
            keyName = "valueBasis",
            name = "Value used for filters",
            description = "Choose GE, High Alchemy or whichever is larger",
            section = FILTERS,
            position = 1)
    default ValueBasis valueBasis() {
        return ValueBasis.HIGHER_VALUE;
    }

    @Range(min = 1, max = 10000000)
    @ConfigItem(
            keyName = "minimumRarity",
            name = "Minimum rarity (1 in)",
            description =
                    "Known reference-table odds of 1 in this number or rarer. Unknown or"
                        + " context-only rates do not match.",
            section = FILTERS,
            position = 2)
    default int minimumRarity() {
        return 64;
    }

    @ConfigItem(
            keyName = "matchMode",
            name = "Combine value and rarity",
            description = "How the two automatic thresholds combine",
            section = FILTERS,
            position = 3)
    default MatchMode matchMode() {
        return MatchMode.VALUE_OR_RARITY;
    }

    @ConfigItem(
            keyName = "alwaysUniques",
            name = "Always post known uniques",
            description = "Recognized event uniques bypass thresholds, but not exclude rules",
            section = FILTERS,
            position = 4)
    default boolean alwaysUniques() {
        return true;
    }

    @ConfigItem(
            keyName = "includeItems",
            name = "Always include items",
            description =
                    "Comma/newline separated item names or IDs. * matches any text. Exclude rules"
                        + " take priority.",
            section = FILTERS,
            position = 5)
    default String includeItems() {
        return "";
    }

    @ConfigItem(
            keyName = "excludeItems",
            name = "Exclude items",
            description =
                    "Comma/newline separated item names or IDs; * is a wildcard. History and manual"
                        + " posting remain available.",
            section = FILTERS,
            position = 6)
    default String excludeItems() {
        return "";
    }

    @ConfigItem(
            keyName = "sourceFilter",
            name = "Allowed sources",
            description =
                    "Optional comma/newline separated source names with * wildcards. Blank allows"
                        + " all sources.",
            section = FILTERS,
            position = 7)
    default String sourceFilter() {
        return "";
    }

    @ConfigItem(
            keyName = "playerFilter",
            name = "Allowed players",
            description =
                    "Optional comma/newline separated player names. This restricts automatic posts,"
                        + " not session history.",
            section = FILTERS,
            position = 8)
    default String playerFilter() {
        return "";
    }

    @ConfigItem(
            keyName = "rewardTotal",
            name = "Filter by reward total",
            description =
                    "Evaluate value against the sum of non-excluded items instead of each stack",
            section = FILTERS,
            position = 9)
    default boolean rewardTotal() {
        return false;
    }

    @ConfigItem(
            keyName = "fullReward",
            name = "Include full qualifying reward",
            description =
                    "Include other non-excluded items when any item qualifies; otherwise send"
                        + " qualifying items only",
            section = FILTERS,
            position = 10)
    default boolean fullReward() {
        return false;
    }

    @ConfigItem(
            keyName = "seasonalWorlds",
            name = "Allow seasonal worlds",
            description = "Allow automatic notifications on seasonal, Deadman and beta worlds",
            section = FILTERS,
            position = 11)
    default boolean seasonalWorlds() {
        return false;
    }

    @ConfigItem(
            keyName = "embedded",
            name = "Rich embeds",
            description = "Use a rich Discord card; disable for plain text",
            section = DISPLAY,
            position = 0)
    default boolean embedded() {
        return true;
    }

    @ConfigItem(
            keyName = "showPlayer",
            name = "Player name",
            description = "Show the receiving player's name",
            section = DISPLAY,
            position = 1)
    default boolean showPlayer() {
        return true;
    }

    @ConfigItem(
            keyName = "showAccountBadge",
            name = "Account badge",
            description = "Show the account type when known",
            section = DISPLAY,
            position = 2)
    default boolean showAccountBadge() {
        return true;
    }

    @ConfigItem(
            keyName = "showThumbnail",
            name = "Item thumbnail",
            description = "Show the item icon in the card",
            section = DISPLAY,
            position = 3)
    default boolean showThumbnail() {
        return true;
    }

    @ConfigItem(
            keyName = "wikiLinks",
            name = "Wiki links",
            description = "Link items and sources to the OSRS Wiki",
            section = DISPLAY,
            position = 4)
    default boolean wikiLinks() {
        return true;
    }

    @ConfigItem(
            keyName = "showGeValue",
            name = "GE value",
            description =
                    "Show receipt-time stack Grand Exchange value; unavailable prices stay unknown",
            section = DISPLAY,
            position = 5)
    default boolean showGeValue() {
        return true;
    }

    @ConfigItem(
            keyName = "showHaValue",
            name = "High Alchemy value",
            description = "Show stack High Alchemy value, before rune costs",
            section = DISPLAY,
            position = 6)
    default boolean showHaValue() {
        return true;
    }

    @ConfigItem(
            keyName = "rarityFraction",
            name = "Rarity fraction",
            description = "Show known odds as 1 in N",
            section = DISPLAY,
            position = 7)
    default boolean rarityFraction() {
        return true;
    }

    @ConfigItem(
            keyName = "rarityPercent",
            name = "Rarity percentage",
            description = "Show known odds as a percentage",
            section = DISPLAY,
            position = 8)
    default boolean rarityPercent() {
        return true;
    }

    @ConfigItem(
            keyName = "unknownRarity",
            name = "Show unknown rarity",
            description = "Show Unknown rather than hiding missing rarity",
            section = DISPLAY,
            position = 9)
    default boolean unknownRarity() {
        return false;
    }

    @ConfigItem(
            keyName = "combatLevel",
            name = "NPC combat level",
            description = "Show the recorded NPC level if available",
            section = DISPLAY,
            position = 10)
    default boolean combatLevel() {
        return false;
    }

    @ConfigItem(
            keyName = "raidMetadata",
            name = "Reward details",
            description = "Show available raid level and team size. Unavailable facts are omitted.",
            section = DISPLAY,
            position = 11)
    default boolean raidMetadata() {
        return true;
    }

    @ConfigItem(
            keyName = "timestamp",
            name = "Drop timestamp",
            description = "Show the time of the original drop, including manual posts",
            section = DISPLAY,
            position = 12)
    default boolean timestamp() {
        return true;
    }

    @ConfigItem(
            keyName = "accentColor",
            name = "Embed colour",
            description = "Discord card accent colour",
            section = DISPLAY,
            position = 13)
    default Color accentColor() {
        return new Color(0xD7AC52);
    }

    @ConfigItem(
            keyName = "automaticScreenshots",
            name = "Automatic screenshots",
            description = "Attach an image captured when a qualifying drop was received",
            section = IMAGES,
            position = 0,
            warning = NETWORK_WARNING)
    default boolean automaticScreenshots() {
        return false;
    }

    @ConfigItem(
            keyName = "manualScreenshots",
            name = "Manual screenshots",
            description = "Attach the original cached screenshot to manual posts when available",
            section = IMAGES,
            position = 1,
            warning = NETWORK_WARNING)
    default boolean manualScreenshots() {
        return false;
    }

    @ConfigItem(
            keyName = "captureFiltered",
            name = "Capture filtered drops",
            description =
                    "Keep receipt-time images for filtered events in this session. Limited to 50"
                        + " images / 64 MiB.",
            section = IMAGES,
            position = 2)
    default boolean captureFiltered() {
        return false;
    }

    @ConfigItem(
            keyName = "maskChat",
            name = "Mask chat and private messages",
            description =
                    "Black out chat, private-message overlays and chat input in retained images",
            section = IMAGES,
            position = 3)
    default boolean maskChat() {
        return true;
    }

    @ConfigItem(
            keyName = "newPets",
            name = "New pet notifications",
            description =
                    "Automatically post actual pet awards independently of loot value and rarity",
            section = PETS,
            position = 0)
    default boolean newPets() {
        return true;
    }

    @ConfigItem(
            keyName = "duplicatePets",
            name = "Duplicate pet rolls",
            description =
                    "Post would-have-received pet messages, clearly labelled as duplicate rolls",
            section = PETS,
            position = 1)
    default boolean duplicatePets() {
        return false;
    }
}

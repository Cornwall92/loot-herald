package com.corndwell.lootherald.discord;

/** Immutable presentation choices, independent of the RuneLite configuration UI. */
public final class EmbedOptions
{
    public final boolean embedded, showPlayer, showAccountBadge, showGeValue, showHaValue;
    public final boolean showRarityFraction, showRarityPercent, showUnknownRarity, showTimestamp;
    public final boolean showCombatLevel, showRaidMetadata, showThumbnail, wikiLinks;
    public final int accentColor;

    private EmbedOptions(Builder b)
    {
        embedded = b.embedded; showPlayer = b.showPlayer; showAccountBadge = b.showAccountBadge;
        showGeValue = b.showGeValue; showHaValue = b.showHaValue;
        showRarityFraction = b.showRarityFraction; showRarityPercent = b.showRarityPercent;
        showUnknownRarity = b.showUnknownRarity; showTimestamp = b.showTimestamp;
        showCombatLevel = b.showCombatLevel; showRaidMetadata = b.showRaidMetadata;
        showThumbnail = b.showThumbnail; wikiLinks = b.wikiLinks; accentColor = b.accentColor & 0xffffff;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder
    {
        private boolean embedded = true, showPlayer = true, showAccountBadge = true;
        private boolean showGeValue = true, showHaValue = true, showRarityFraction = true;
        private boolean showRarityPercent = true, showUnknownRarity = false, showTimestamp = true;
        private boolean showCombatLevel = true, showRaidMetadata = true, showThumbnail = true, wikiLinks = true;
        private int accentColor = 0xd5a84b;

        public Builder embedded(boolean v) { embedded = v; return this; }
        public Builder showPlayer(boolean v) { showPlayer = v; return this; }
        public Builder showAccountBadge(boolean v) { showAccountBadge = v; return this; }
        public Builder showGeValue(boolean v) { showGeValue = v; return this; }
        public Builder showHaValue(boolean v) { showHaValue = v; return this; }
        public Builder showRarityFraction(boolean v) { showRarityFraction = v; return this; }
        public Builder showRarityPercent(boolean v) { showRarityPercent = v; return this; }
        public Builder showUnknownRarity(boolean v) { showUnknownRarity = v; return this; }
        public Builder showTimestamp(boolean v) { showTimestamp = v; return this; }
        public Builder showCombatLevel(boolean v) { showCombatLevel = v; return this; }
        public Builder showRaidMetadata(boolean v) { showRaidMetadata = v; return this; }
        public Builder showThumbnail(boolean v) { showThumbnail = v; return this; }
        public Builder wikiLinks(boolean v) { wikiLinks = v; return this; }
        public Builder accentColor(int v) { accentColor = v; return this; }
        public EmbedOptions build() { return new EmbedOptions(this); }
    }
}

package com.corndwell.lootherald.filter;

import com.corndwell.lootherald.LootHeraldConfig;
import com.corndwell.lootherald.model.*;

import java.util.*;
import java.util.regex.Pattern;

/** Qualification affects automatic delivery only; receipt history is always populated first. */
public final class LootFilter {
    public static final class Result {
        public final List<Integer> indices;
        public final String reason;

        public Result(List<Integer> indices, String reason) {
            this.indices = List.copyOf(indices);
            this.reason = reason;
        }
    }

    public Result evaluate(LootDrop drop, LootHeraldConfig cfg) {
        if (!matchesOptional(cfg.sourceFilter(), drop.getSource())
                && !matchesOptional(cfg.sourceFilter(), drop.getRawSource()))
            return new Result(List.of(), "Source excluded by automatic filter");
        if (!matchesOptional(cfg.playerFilter(), drop.getPlayer()))
            return new Result(List.of(), "Player excluded by automatic filter");
        if (drop.isPet()) {
            boolean allowed = drop.isDuplicatePet() ? cfg.duplicatePets() : cfg.newPets();
            return new Result(
                    allowed ? List.of(0) : List.of(),
                    allowed ? "Pet notification" : "Pet automatic notifications disabled");
        }
        List<Integer> eligible = new ArrayList<>(), selected = new ArrayList<>();
        long total = 0;
        for (int i = 0; i < drop.getItems().size(); i++) {
            DropItem item = drop.getItems().get(i);
            if (matchesItem(cfg.excludeItems(), item)) continue;
            eligible.add(i);
            total = add(total, Math.max(0, value(item, cfg.valueBasis())));
        }
        for (int i : eligible) {
            DropItem item = drop.getItems().get(i);
            boolean value =
                    cfg.minimumValue() == 0
                            || (cfg.rewardTotal() ? total : value(item, cfg.valueBasis()))
                                    >= cfg.minimumValue();
            boolean rarity =
                    item.getRarity().known()
                            && item.getRarity().getProbability()
                                    <= 1d / Math.max(1, cfg.minimumRarity());
            boolean match;
            switch (cfg.matchMode()) {
                case VALUE_AND_RARITY:
                    match = value && rarity;
                    break;
                case RARITY_ONLY:
                    match = rarity;
                    break;
                case VALUE_ONLY:
                    match = value;
                    break;
                default:
                    match = value || rarity;
            }
            if (matchesItem(cfg.includeItems(), item)
                    || (cfg.alwaysUniques() && item.getRarity().isUnique())
                    || match) selected.add(i);
        }
        if (!selected.isEmpty() && cfg.fullReward()) selected = eligible;
        return new Result(
                selected,
                selected.isEmpty()
                        ? "Filtered — below thresholds or excluded item"
                        : "Qualified for automatic posting");
    }

    public static long value(DropItem item, LootHeraldConfig.ValueBasis basis) {
        switch (basis) {
            case GRAND_EXCHANGE:
                return item.getGeTotal();
            case HIGH_ALCHEMY:
                return item.getHaTotal();
            default:
                return Math.max(item.getGeTotal(), item.getHaTotal());
        }
    }

    public static long add(long a, long b) {
        return b > Long.MAX_VALUE - a ? Long.MAX_VALUE : a + b;
    }

    public static boolean matchesOptional(String patterns, String value) {
        return patterns == null || patterns.trim().isEmpty() || matches(patterns, value);
    }

    public static boolean matchesItem(String patterns, DropItem item) {
        return matches(patterns, item.getName())
                || matches(patterns, Integer.toString(item.getId()));
    }

    public static boolean matches(String patterns, String value) {
        if (patterns == null || value == null) return false;
        for (String entry : patterns.split("[,\\r\\n]+")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;
            StringBuilder regex = new StringBuilder("^");
            String[] pieces = entry.split("\\*", -1);
            for (int i = 0; i < pieces.length; i++) {
                if (i > 0) regex.append(".*");
                regex.append(Pattern.quote(pieces[i]));
            }
            regex.append('$');
            if (Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                    .matcher(value.trim())
                    .matches()) return true;
        }
        return false;
    }
}

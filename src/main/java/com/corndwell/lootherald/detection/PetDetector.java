/*
 * Pet item names and gameval references are derived from RuneLite's generated
 * ItemID catalog. See PET_DATA_NOTICES.md for sources and license notices.
 */
package com.corndwell.lootherald.detection;

import com.corndwell.lootherald.model.DropItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.ItemID;

/**
 * Correlates trusted pet acquisition signals without accessing a RuneLite Client.
 * All names, source labels and item lists are copied; no inventory/follower scan
 * or persistent ownership inference is performed.
 */
public final class PetDetector
{
    public static final int MAX_TICKS_WAIT = 5;
    private static final int MAX_PENDING = 64;
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NEW_PET = Pattern.compile(
        "^You (?:have a funny feeling like you're being followed|feel something weird sneaking into your backpack)[.!…]*$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern DUPLICATE_PET = Pattern.compile(
        "^You have a funny feeling like you would have been followed[.!…]*$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern UNTRADEABLE = Pattern.compile("^Untradeable drop: (.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLLECTION = Pattern.compile("^New item added to your collection log: (.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLAN = Pattern.compile(
        "^(?:\\[[^\\]]+\\]\\s*)?(.+?) (?:has a funny feeling like .+? followed|feels something weird sneaking into .+? backpack|feels like .+? acquired something special): (.+?)(?: at .+)?[.!]*$",
        Pattern.CASE_INSENSITIVE);
    private static final Map<Integer, PetIdentity> BY_ID;
    private static final Map<String, PetIdentity> BY_NAME;

    static
    {
        Map<Integer, PetIdentity> ids = new HashMap<>();
        Map<String, PetIdentity> names = new HashMap<>();
        register(ids, names, ItemID.CHAOSELEPET, "Pet chaos elemental");
        register(ids, names, ItemID.SUPREMEPET, "Pet Dagannoth Supreme");
        register(ids, names, ItemID.PRIMEPET, "Pet Dagannoth Prime");
        register(ids, names, ItemID.REXPET, "Pet Dagannoth Rex");
        register(ids, names, ItemID.MOLEPET, "Baby Mole");
        register(ids, names, ItemID.KQPET_WALKING, "Kalphite Princess");
        register(ids, names, ItemID.SMOKEPET, "Pet Smoke Devil");
        register(ids, names, ItemID.ARMADYLPET, "Pet Kree'arra");
        register(ids, names, ItemID.BANDOSPET, "Pet General Graardor");
        register(ids, names, ItemID.SARADOMINPET, "Pet Zilyana");
        register(ids, names, ItemID.ZAMORAKPET, "Pet K'ril Tsutsaroth");
        register(ids, names, ItemID.KBDPET, "Prince Black Dragon");
        register(ids, names, ItemID.KQPET_FLYING, "Kalphite Princess");
        register(ids, names, ItemID.KRAKENPET, "Pet Kraken");
        register(ids, names, ItemID.PENANCEPET, "Pet Penance Queen");
        register(ids, names, ItemID.COREPET, "Pet dark core");
        register(ids, names, ItemID.SNAKEPET, "Pet Snakeling");
        register(ids, names, ItemID.SNAKEPET_ORANGE, "Pet Snakeling");
        register(ids, names, ItemID.SNAKEPET_BLUE, "Pet Snakeling");
        register(ids, names, ItemID.CHOMPYBIRD_PET, "Chompy chick");
        register(ids, names, ItemID.VENENATIS_PET, "Venenatis spiderling");
        register(ids, names, ItemID.CALLISTO_PET, "Callisto cub");
        register(ids, names, ItemID.VETION_PET, "Vet'ion jr.");
        register(ids, names, ItemID.VETION_PET2, "Vet'ion jr.");
        register(ids, names, ItemID.SCORPIA_PET, "Scorpia's offspring");
        register(ids, names, ItemID.JAD_PET, "TzRek-Jad");
        register(ids, names, ItemID.HELL_PET, "Hellpuppy");
        register(ids, names, ItemID.ABYSSALSIRE_PET, "Abyssal orphan");
        register(ids, names, ItemID.SKILLPETFISH, "Heron");
        register(ids, names, ItemID.SKILLPETMINING, "Rock golem");
        register(ids, names, ItemID.SKILLPETWC, "Beaver");
        register(ids, names, ItemID.SKILLPETHUNTER_RED, "Baby chinchompa");
        register(ids, names, ItemID.SKILLPETHUNTER_GREY, "Baby chinchompa");
        register(ids, names, ItemID.SKILLPETHUNTER_BLACK, "Baby chinchompa");
        register(ids, names, ItemID.SKILLPETHUNTER_GOLD, "Baby chinchompa");
        register(ids, names, ItemID.BLOODHOUND_PET, "Bloodhound");
        register(ids, names, ItemID.SKILLPETAGILITY, "Giant Squirrel");
        register(ids, names, ItemID.SKILLPETFARMING, "Tangleroot");
        register(ids, names, ItemID.SKILLPETTHIEVING, "Rocky");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_FIRE, "Rift guardian");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_AIR, "Rift guardian");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_MIND, "Rift guardian");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_WATER, "Rift guardian");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_EARTH, "Rift guardian");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_BODY, "Rift guardian");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_COSMIC, "Rift guardian");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_CHAOS, "Rift guardian");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_NATURE, "Rift guardian");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_LAW, "Rift guardian");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_DEATH, "Rift guardian");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_SOUL, "Rift guardian");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_ASTRAL, "Rift guardian");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_BLOOD, "Rift guardian");
        register(ids, names, ItemID.PHOENIXPET, "Phoenix");
        register(ids, names, ItemID.OLMPET, "Olmlet");
        register(ids, names, ItemID.SKILLPETMINING_TIN, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_COPPER, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_IRON, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_BLURITE, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_SILVER, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_COAL, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_GOLD, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_MITHRIL, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_GRANITE, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_ADAMANTITE, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_RUNITE, "Rock golem");
        register(ids, names, ItemID.SKOTIZOPET, "Skotos");
        register(ids, names, ItemID.INFERNOPET, "Jal-Nib-Rek");
        register(ids, names, ItemID.SKILLPETMINING_AMETHYST, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_LOVAKITE, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_ELEMENTAL, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_DAEYALT, "Rock golem");
        register(ids, names, ItemID.HERBIBOARPET, "Herbi");
        register(ids, names, ItemID.DAWNPET, "Noon");
        register(ids, names, ItemID.DUSKPET, "Midnight");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_WRATH, "Rift guardian");
        register(ids, names, ItemID.VORKATHPET, "Vorki");
        register(ids, names, ItemID.CORPPET, "Pet Corporeal Critter");
        register(ids, names, ItemID.INFERNOPET_ZUK, "TzRek-Zuk");
        register(ids, names, ItemID.DOGADILEPET, "Puppadile");
        register(ids, names, ItemID.TEKTONPET, "Tektiny");
        register(ids, names, ItemID.VANGUARDPET, "Vanguard");
        register(ids, names, ItemID.VASAPET, "Vasa Minirio");
        register(ids, names, ItemID.VESPULAPET, "Vespina");
        register(ids, names, ItemID.VERZIKPET, "Lil' Zik");
        register(ids, names, ItemID.SMOKEPET_OLD, "Pet Smoke Devil");
        register(ids, names, ItemID.HYDRAPET, "Ikkle Hydra");
        register(ids, names, ItemID.HYDRAPET_ELECTRIC, "Ikkle Hydra");
        register(ids, names, ItemID.HYDRAPET_FIRE, "Ikkle Hydra");
        register(ids, names, ItemID.HYDRAPET_EXTINGUISHED, "Ikkle Hydra");
        register(ids, names, ItemID.SARACHNISPET, "Sraracha");
        register(ids, names, ItemID.GAUNTLETPET, "Youngllef");
        register(ids, names, ItemID.GAUNTLETPET_CORRUPT, "Corrupted Youngllef");
        register(ids, names, ItemID.ZALCANOPET, "Smolcano");
        register(ids, names, ItemID.PHOENIXPET_GREEN, "Phoenix");
        register(ids, names, ItemID.PHOENIXPET_BLUE, "Phoenix");
        register(ids, names, ItemID.PHOENIXPET_WHITE, "Phoenix");
        register(ids, names, ItemID.PHOENIXPET_PURPLE, "Phoenix");
        register(ids, names, ItemID.NIGHTMAREPET, "Little Nightmare");
        register(ids, names, ItemID.SKILLPETFARMING_CRYSTAL, "Tangleroot");
        register(ids, names, ItemID.SKILLPETFARMING_DRAGON, "Tangleroot");
        register(ids, names, ItemID.SKILLPETFARMING_HERB, "Tangleroot");
        register(ids, names, ItemID.SKILLPETFARMING_LILY, "Tangleroot");
        register(ids, names, ItemID.SKILLPETFARMING_REDWOOD, "Tangleroot");
        register(ids, names, ItemID.TEKTONENRAGEDPET, "Enraged Tektiny");
        register(ids, names, ItemID.VESPULAFLYINGPET, "Flying Vespina");
        register(ids, names, ItemID.SKILLPETAGILITY_DARK, "Dark Squirrel");
        register(ids, names, ItemID.SKILLPETTHIEVING_PANDA, "Red");
        register(ids, names, ItemID.SKILLPETTHIEVING_TANUKI, "Ziggy");
        register(ids, names, ItemID.SOULWARSPET_BLUE, "Lil' Creator");
        register(ids, names, ItemID.SOULWARSPET_RED, "Lil' Destructor");
        register(ids, names, ItemID.JAD_PET_INFERNO, "JalRek-Jad");
        register(ids, names, ItemID.SKILLPETFISH_TEMPOROSS, "Great blue heron");
        register(ids, names, ItemID.TEMPOROSSPET, "Tiny tempor");
        register(ids, names, ItemID.MOLEPET_NAKED, "Baby Mole-rat");
        register(ids, names, ItemID.MAIDENPET, "Lil' Maiden");
        register(ids, names, ItemID.BLOATPET, "Lil' Bloat");
        register(ids, names, ItemID.NYLOCASPET, "Lil' Nylo");
        register(ids, names, ItemID.SOTETSEGPET, "Lil' Sot");
        register(ids, names, ItemID.XARPUSPET, "Lil' Xarp");
        register(ids, names, ItemID.NIGHTMAREPET_PARASITE, "Little parasite");
        register(ids, names, ItemID.SARACHNISPET_ORANGE, "Sraracha");
        register(ids, names, ItemID.SARACHNISPET_BLUE, "Sraracha");
        register(ids, names, ItemID.NEXPET, "Nexling");
        register(ids, names, ItemID.SKILLPETRUNECRAFTING_GOTR, "Greatish guardian");
        register(ids, names, ItemID.ABYSSALPET, "Abyssal protector");
        register(ids, names, ItemID.WARDENPET_TUMEKEN, "Tumeken's guardian");
        register(ids, names, ItemID.WARDENPET_ELIDINIS, "Elidinis' guardian");
        register(ids, names, ItemID.WARDENPET_AKKHA, "Akkhito");
        register(ids, names, ItemID.WARDENPET_BABA, "Babi");
        register(ids, names, ItemID.WARDENPET_KEPHRI, "Kephriti");
        register(ids, names, ItemID.WARDENPET_ZEBAK, "Zebo");
        register(ids, names, ItemID.WARDENPET_TUMEKEN_DESTROYED, "Tumeken's damaged guardian");
        register(ids, names, ItemID.WARDENPET_ELIDINIS_DESTROYED, "Elidinis' damaged guardian");
        register(ids, names, ItemID.MUSPAHPET, "Muphin");
        register(ids, names, ItemID.MUSPAHPET_MELEE, "Muphin");
        register(ids, names, ItemID.MUSPAHPET_SHIELDED, "Muphin");
        register(ids, names, ItemID.VENENATIS_PET_LEGACY, "Venenatis spiderling");
        register(ids, names, ItemID.CALLISTO_PET_LEGACY, "Callisto cub");
        register(ids, names, ItemID.VETION_PET_LEGACY, "Vet'ion Jr.");
        register(ids, names, ItemID.VETION_PET2_LEGACY, "Vet'ion Jr.");
        register(ids, names, ItemID.SKILLPET_WC_OAK, "Beaver");
        register(ids, names, ItemID.SKILLPET_WC_WILLOW, "Beaver");
        register(ids, names, ItemID.SKILLPET_WC_MAPLE, "Beaver");
        register(ids, names, ItemID.SKILLPET_WC_YEW, "Beaver");
        register(ids, names, ItemID.SKILLPET_WC_MAGIC, "Beaver");
        register(ids, names, ItemID.SKILLPET_WC_REDWOOD, "Beaver");
        register(ids, names, ItemID.SKILLPET_WC_TEAK, "Beaver");
        register(ids, names, ItemID.SKILLPET_WC_MAHOGANY, "Beaver");
        register(ids, names, ItemID.SKILLPET_WC_ARCTIC, "Beaver");
        register(ids, names, ItemID.WHISPERERPET, "Wisp");
        register(ids, names, ItemID.VARDORVISPET, "Butch");
        register(ids, names, ItemID.DUKESUCELLUSPET, "Baron");
        register(ids, names, ItemID.LEVIATHANPET, "Lil'viathan");
        register(ids, names, ItemID.SKILLPET_WC_PHEASANT, "Pheasant");
        register(ids, names, ItemID.SKILLPET_WC_FOX, "Fox");
        register(ids, names, ItemID.SCURRIUSPET, "Scurry");
        register(ids, names, ItemID.SOLHEREDITPET, "Smol Heredit");
        register(ids, names, ItemID.QUETZALPET, "Quetzin");
        register(ids, names, ItemID.ARAXXORPET, "Nid");
        register(ids, names, ItemID.ARAXXORPET_CUTE, "Rax");
        register(ids, names, ItemID.SKILLPETAGILITY_BONE, "Bone Squirrel");
        register(ids, names, ItemID.HUEYPET, "Huberte");
        register(ids, names, ItemID.AMOXLIATLPET, "Moxi");
        register(ids, names, ItemID.RTBRANDAPET, "Bran");
        register(ids, names, ItemID.RTELDRICPET, "Ric");
        register(ids, names, ItemID.YAMAPET, "Yami");
        register(ids, names, ItemID.DOMPET, "Dom");
        register(ids, names, ItemID.SKILLPETMINING_LEAD, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_RUBIUM, "Rock golem");
        register(ids, names, ItemID.SKILLPETMINING_NICKEL, "Rock golem");
        register(ids, names, ItemID.SKILLPET_WC_CAMPHOR, "Beaver");
        register(ids, names, ItemID.SKILLPET_WC_IRONWOOD, "Beaver");
        register(ids, names, ItemID.SKILLPET_WC_JATOBA, "Beaver");
        register(ids, names, ItemID.SKILLPET_WC_ROSEWOOD, "Beaver");
        register(ids, names, ItemID.SKILLPETSAILING, "Soup");
        register(ids, names, ItemID.GRYPHONBOSSPET, "Gull");
        register(ids, names, ItemID.GRYPHONBOSSPET_ADULT, "Gulliver");
        register(ids, names, ItemID.COWBOSSPET, "Beef");
        register(ids, names, ItemID.MAGGOTKINGPET, "Maggot marquess");
        register(ids, names, ItemID.GOATPITPET, "Mr McGroot");
        register(ids, names, ItemID.MADANGELPET, "Aggy");
        register(ids, names, ItemID.OCELLUSPET, "Orion");
        register(ids, names, ItemID.OCELLUSPET_VARIANT_1, "Orion");
        register(ids, names, ItemID.XARTHPET, "Xari");
        register(ids, names, ItemID.GUARDIANPET, "Archie");
        register(ids, names, ItemID.JELLYPET, "Chubbles");
        register(ids, names, ItemID.DAGONHAIPET, "Cole");
        register(ids, names, ItemID.EZEKIELPET, "Zeke");
        register(ids, names, ItemID.MOIAPET, "Mimi");
        register(ids, names, ItemID.BORKPET, "Dork");
        BY_ID = Collections.unmodifiableMap(ids);
        BY_NAME = Collections.unmodifiableMap(names);
    }

    private final List<Pending> pending = new ArrayList<>();
    private final List<PetAward> overflow = new ArrayList<>();
    private int lastTriggerTick = Integer.MIN_VALUE;
    private String lastTrigger = "";

    /** True only for a trusted game-channel acquisition phrase, for timely capture. */
    public static boolean isAcquisitionMessage(String type, String text)
    {
        String channel = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (!"GAMEMESSAGE".equals(channel) && !"SPAM".equals(channel))
        {
            return false;
        }
        String message = clean(text);
        return NEW_PET.matcher(message).matches() || DUPLICATE_PET.matcher(message).matches();
    }

    public synchronized int getPendingCount()
    {
        return pending.size();
    }

    /**
     * Only trusted game-message types can prime an acquisition. Clan/system
     * announcements may enrich a pending acquisition belonging to localPlayer.
     * Caller must exclude plugin-authored synthetic game chat.
     */
    public synchronized void onChat(String type, String text, String localPlayer, int tick)
    {
        String clean = clean(text);
        String channel = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (isClanChannel(channel))
        {
            enrichClan(clean, localPlayer, tick);
            return;
        }
        if (!"GAMEMESSAGE".equals(channel) && !"SPAM".equals(channel))
        {
            return;
        }

        boolean duplicate = DUPLICATE_PET.matcher(clean).matches();
        if (duplicate || NEW_PET.matcher(clean).matches())
        {
            // Multiple deliveries of the same game message in one tick are one signal.
            // A subsequent tick remains eligible for a distinct legitimate award.
            String signature = clean.toLowerCase(Locale.ROOT);
            if (lastTriggerTick == tick && lastTrigger.equals(signature))
            {
                return;
            }
            lastTriggerTick = tick;
            lastTrigger = signature;
            Pending target = sameTickChat(tick, duplicate);
            if (target == null)
            {
                target = newestUnchattedLoot(tick);
            }
            if (target == null)
            {
                target = new Pending(tick);
                add(target);
            }
            target.tick = Math.min(target.tick, tick);
            target.chat = true;
            target.chatTick = tick;
            target.duplicate = duplicate;
            return;
        }

        Matcher collection = COLLECTION.matcher(clean);
        if (collection.matches())
        {
            onCollection(collection.group(1), tick);
            return;
        }
        Matcher untradeable = UNTRADEABLE.matcher(clean);
        if (untradeable.matches())
        {
            enrichName(untradeable.group(1), false, tick);
        }
    }

    /** A collection-log event alone is not evidence of a new pet acquisition. */
    public synchronized void onCollection(String itemName, int tick)
    {
        enrichName(itemName, true, tick);
    }

    /**
     * Caller must pass only an authoritative, new loot acquisition, never a bank
     * withdrawal, inventory change or a reread of an old reward container.
     * Known pet item awards can prime detection when no pet chat is available.
     * Each separate call is a distinct acquisition unless it joins an as-yet
     * unpaired game-message acquisition.
     */
    public synchronized void onLoot(List<DropItem> items, String source, int tick)
    {
        if (items == null)
        {
            return;
        }
        Map<String, PetIdentity> seen = new HashMap<>();
        for (DropItem item : items)
        {
            if (item == null)
            {
                continue;
            }
            PetIdentity pet = findPet(item.getId(), item.getName());
            if (pet == null || seen.put(normalize(pet.name), pet) != null)
            {
                continue;
            }
            Pending target = findPending(pet, tick, true);
            if (target == null)
            {
                target = new Pending(tick);
                add(target);
            }
            target.tick = Math.min(target.tick, tick);
            target.pet = pet;
            target.loot = true;
            target.source = clean(source);
        }
    }

    /** Emits each award once when its original five-tick enrichment window expires. */
    public synchronized List<PetAward> drain(int tick)
    {
        List<PetAward> result = new ArrayList<>(overflow);
        overflow.clear();
        Iterator<Pending> iterator = pending.iterator();
        while (iterator.hasNext())
        {
            Pending value = iterator.next();
            if ((long) tick - value.tick >= MAX_TICKS_WAIT)
            {
                result.add(value.award());
                iterator.remove();
            }
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized void clear()
    {
        pending.clear();
        overflow.clear();
        lastTriggerTick = Integer.MIN_VALUE;
        lastTrigger = "";
    }

    public boolean isPet(int itemId, String name)
    {
        return findPet(itemId, name) != null;
    }

    private void enrichName(String name, boolean collectionLog, int tick)
    {
        PetIdentity pet = BY_NAME.get(normalize(name));
        if (pet == null)
        {
            return;
        }
        Pending value = findPending(pet, tick, false);
        if (value != null)
        {
            if (value.pet == null)
            {
                value.pet = pet;
            }
            value.collectionLog |= collectionLog;
        }
    }

    private void enrichClan(String text, String localPlayer, int tick)
    {
        if (localPlayer == null || localPlayer.trim().isEmpty())
        {
            return;
        }
        Matcher matcher = CLAN.matcher(text);
        if (matcher.matches() && normalizePlayer(matcher.group(1)).equals(normalizePlayer(localPlayer)))
        {
            enrichName(matcher.group(2), false, tick);
        }
    }

    /** Prefer matching names to an unidentified candidate; never overwrite a different pet. */
    private Pending findPending(PetIdentity pet, int tick, boolean requireUnpairedLoot)
    {
        Pending unnamed = null;
        for (int i = pending.size() - 1; i >= 0; i--)
        {
            Pending value = pending.get(i);
            // The reward gate can deliver a loot snapshot after a newer chat
            // event, retaining the snapshot's original receipt tick.
            boolean inWindow = requireUnpairedLoot ? acquisitionWindow(value, tick) : withinWindow(value, tick);
            if (!inWindow || (requireUnpairedLoot && value.loot))
            {
                continue;
            }
            if (value.pet != null && normalize(value.pet.name).equals(normalize(pet.name)))
            {
                return value;
            }
            if (value.pet == null && unnamed == null)
            {
                unnamed = value;
            }
        }
        return unnamed;
    }

    private Pending newestUnchattedLoot(int tick)
    {
        for (int i = pending.size() - 1; i >= 0; i--)
        {
            Pending value = pending.get(i);
            if (value.loot && !value.chat && acquisitionWindow(value, tick))
            {
                return value;
            }
        }
        return null;
    }

    private Pending sameTickChat(int tick, boolean duplicate)
    {
        for (int i = pending.size() - 1; i >= 0; i--)
        {
            Pending value = pending.get(i);
            if (value.chatTick == tick && value.chat && value.duplicate == duplicate)
            {
                return value;
            }
        }
        return null;
    }

    private static boolean withinWindow(Pending value, int tick)
    {
        long elapsed = (long) tick - value.tick;
        return elapsed >= 0 && elapsed <= MAX_TICKS_WAIT;
    }

    private static boolean acquisitionWindow(Pending value, int tick)
    {
        return Math.abs((long) tick - value.tick) <= MAX_TICKS_WAIT;
    }

    private void add(Pending value)
    {
        if (pending.size() == MAX_PENDING)
        {
            // Bound memory without silently losing already established acquisitions.
            if (overflow.size() < MAX_PENDING)
            {
                overflow.add(pending.get(0).award());
            }
            pending.remove(0);
        }
        pending.add(value);
    }

    private static boolean isClanChannel(String type)
    {
        return "CLAN_MESSAGE".equals(type) || "CLAN_GUEST_MESSAGE".equals(type)
            || "CLAN_GIM_MESSAGE".equals(type) || "FRIENDSCHATNOTIFICATION".equals(type);
    }

    private static PetIdentity findPet(int id, String name)
    {
        PetIdentity byId = BY_ID.get(id);
        return byId != null ? byId : BY_NAME.get(normalize(name));
    }

    private static String clean(String value)
    {
        if (value == null)
        {
            return "";
        }
        return WHITESPACE.matcher(TAGS.matcher(value).replaceAll("")
            .replace('\u00a0', ' ').replace('\u2019', '\'')).replaceAll(" ").trim();
    }

    private static String normalize(String value)
    {
        String result = clean(value).toLowerCase(Locale.ROOT);
        return result.endsWith(".") ? result.substring(0, result.length() - 1) : result;
    }

    private static String normalizePlayer(String value)
    {
        return normalize(value).replace('_', ' ');
    }

    private static void register(Map<Integer, PetIdentity> ids, Map<String, PetIdentity> names, int id, String name)
    {
        PetIdentity pet = new PetIdentity(id, name);
        ids.put(id, pet);
        names.putIfAbsent(normalize(name), pet);
    }

    private static final class PetIdentity
    {
        private final int id;
        private final String name;
        private PetIdentity(int id, String name)
        {
            this.id = id;
            this.name = name;
        }
    }

    private static final class Pending
    {
        private int tick;
        private int chatTick = Integer.MIN_VALUE;
        private PetIdentity pet;
        private String source = "";
        private boolean duplicate;
        private boolean collectionLog;
        private boolean chat;
        private boolean loot;

        private Pending(int tick)
        {
            this.tick = tick;
        }

        private PetAward award()
        {
            return new PetAward(pet == null ? "Unknown pet" : pet.name,
                pet == null ? -1 : pet.id, source.isEmpty() ? "Unknown source" : source,
                duplicate, collectionLog, tick);
        }
    }

    public static final class PetAward
    {
        private final String name;
        private final int itemId;
        private final String source;
        private final boolean duplicate;
        private final boolean collectionLog;
        private final int tick;

        private PetAward(String name, int itemId, String source, boolean duplicate, boolean collectionLog, int tick)
        {
            this.name = name;
            this.itemId = itemId;
            this.source = source;
            this.duplicate = duplicate;
            this.collectionLog = collectionLog;
            this.tick = tick;
        }

        public String getName() { return name; }
        public int getItemId() { return itemId; }
        public String getSource() { return source; }
        public boolean isDuplicate() { return duplicate; }
        public boolean getDuplicate() { return duplicate; }
        public boolean isCollectionLog() { return collectionLog; }
        public boolean getCollectionLog() { return collectionLog; }
        public int getTick() { return tick; }
    }
}

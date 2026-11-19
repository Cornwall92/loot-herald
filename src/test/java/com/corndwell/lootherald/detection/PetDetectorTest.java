package com.corndwell.lootherald.detection;

import com.corndwell.lootherald.model.DropItem;
import com.corndwell.lootherald.model.Rarity;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

public class PetDetectorTest
{
    private static final String FOLLOW = "You have a funny feeling like you're being followed.";
    private static final String DUPLICATE = "You have a funny feeling like you would have been followed...";

    @Test
    public void screenshotTriggerRequiresTrustedAcquisitionChat()
    {
        assertTrue(PetDetector.isAcquisitionMessage("GAMEMESSAGE", FOLLOW));
        assertTrue(PetDetector.isAcquisitionMessage("SPAM", DUPLICATE));
        assertTrue(PetDetector.isAcquisitionMessage("GAMEMESSAGE", "<col=ff0000>" + FOLLOW + "</col>"));
        assertFalse(PetDetector.isAcquisitionMessage("PUBLICCHAT", FOLLOW));
        assertFalse(PetDetector.isAcquisitionMessage("PRIVATECHAT", FOLLOW));
        assertFalse(PetDetector.isAcquisitionMessage("CLAN_CHAT", FOLLOW));
        assertFalse(PetDetector.isAcquisitionMessage("CLAN_MESSAGE", FOLLOW));
        assertFalse(PetDetector.isAcquisitionMessage("GAMEMESSAGE", "Your pet has been reclaimed."));
        assertFalse(PetDetector.isAcquisitionMessage("GAMEMESSAGE", "You have a funny feeling like you're being followed by another player."));
    }

    @Test
    public void delayedOtherPlayerClanMessageCannotIdentifyOurPet()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 10);
        assertEquals(1, detector.getPendingCount());
        detector.onChat("CLAN_MESSAGE", "[Clan] Other has a funny feeling like he's being followed: Bloodhound at 1 killcount.",
            "Corndwell", 14);
        assertEquals(-1, only(detector.drain(15)).getItemId());
        assertEquals(0, detector.getPendingCount());
    }

    @Test
    public void genericPetWaitsForFiveTicksAndDrainsOnce()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 100);
        assertTrue(detector.drain(104).isEmpty());
        PetDetector.PetAward award = only(detector.drain(105));
        assertEquals("Unknown pet", award.getName());
        assertEquals(-1, award.getItemId());
        assertEquals("Unknown source", award.getSource());
        assertFalse(award.isDuplicate());
        assertEquals(100, award.getTick());
        assertTrue(detector.drain(106).isEmpty());
    }

    @Test
    public void backpackCollectionAndLootBecomeOneNamedAward()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", "You feel something weird sneaking into your backpack.", "Corndwell", 20);
        detector.onCollection("Tumeken's guardian", 21);
        detector.onLoot(Collections.singletonList(pet(ItemID.WARDENPET_TUMEKEN, "Tumeken's guardian")), "Tombs of Amascut", 21);
        detector.onChat("GAMEMESSAGE", "Untradeable drop: Tumeken's guardian", "Corndwell", 22);
        PetDetector.PetAward award = only(detector.drain(25));
        assertEquals(ItemID.WARDENPET_TUMEKEN, award.getItemId());
        assertEquals("Tombs of Amascut", award.getSource());
        assertTrue(award.isCollectionLog());
        assertFalse(award.isDuplicate());
    }

    @Test
    public void authoritativePetLootCanStartAwardWithoutChat()
    {
        PetDetector detector = new PetDetector();
        detector.onLoot(Collections.singletonList(pet(ItemID.PENANCEPET, "Pet Penance Queen")),
            "Barbarian Assault high gamble", 10);
        PetDetector.PetAward award = only(detector.drain(15));
        assertEquals(ItemID.PENANCEPET, award.getItemId());
        assertEquals("Barbarian Assault high gamble", award.getSource());
    }

    @Test
    public void lootBeforeChatIsMerged()
    {
        PetDetector detector = new PetDetector();
        detector.onLoot(Collections.singletonList(pet(ItemID.BLOODHOUND_PET, "Bloodhound")), "Clue Scroll (Master)", 10);
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 11);
        detector.onCollection("Bloodhound", 12);
        PetDetector.PetAward award = only(detector.drain(16));
        assertEquals("Bloodhound", award.getName());
        assertTrue(award.isCollectionLog());
    }

    @Test
    public void delayedLootGateMergesEarlierReceiptWithAlreadySeenChat()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 11);
        detector.onCollection("Bloodhound", 11);
        // The gate accepts tick-10 loot at tick 11, after processing pet chat.
        detector.onLoot(Collections.singletonList(pet(ItemID.BLOODHOUND_PET, "Bloodhound")), "Clue Scroll (Master)", 10);
        PetDetector.PetAward award = only(detector.drain(15));
        assertEquals(10, award.getTick());
        assertEquals("Bloodhound", award.getName());
        assertEquals("Clue Scroll (Master)", award.getSource());
        assertTrue(award.isCollectionLog());
        assertTrue(detector.drain(16).isEmpty());
    }

    @Test
    public void alternateChatOnLaterTickDoesNotDuplicateLootStartedAward()
    {
        PetDetector detector = new PetDetector();
        detector.onLoot(Collections.singletonList(pet(ItemID.BLOODHOUND_PET, "Bloodhound")), "Clue Scroll (Master)", 10);
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 11);
        detector.onChat("SPAM", "You feel something weird sneaking into your backpack.", "Corndwell", 11);
        PetDetector.PetAward award = only(detector.drain(16));
        assertEquals(10, award.getTick());
    }

    @Test
    public void oldLootOutsideWindowCannotAbsorbANewerPetChat()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 20);
        detector.onLoot(Collections.singletonList(pet(ItemID.BLOODHOUND_PET, "Bloodhound")), "Clue Scroll (Master)", 14);
        assertEquals(2, detector.drain(25).size());
    }

    @Test
    public void duplicateRollIsNotANewPetEvenWithItsName()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", DUPLICATE, "Corndwell", 30);
        detector.onChat("GAMEMESSAGE", "Untradeable drop: TzRek-Jad", "Corndwell", 31);
        PetDetector.PetAward award = only(detector.drain(35));
        assertTrue(award.isDuplicate());
        assertEquals(ItemID.JAD_PET, award.getItemId());
    }

    @Test
    public void reobtainedPetWithoutNewCollectionEntryIsNotAssumedDuplicate()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 30);
        detector.onChat("GAMEMESSAGE", "Untradeable drop: TzRek-Jad", "Corndwell", 31);
        PetDetector.PetAward award = only(detector.drain(35));
        assertFalse(award.isDuplicate());
        assertFalse(award.isCollectionLog());
    }

    @Test
    public void collectionUntradeableAndClanAloneNeverPrime()
    {
        PetDetector detector = new PetDetector();
        detector.onCollection("Bloodhound", 1);
        detector.onChat("GAMEMESSAGE", "Untradeable drop: Bloodhound", "Corndwell", 2);
        detector.onChat("CLAN_MESSAGE", "[Clan] Corndwell has a funny feeling like he would have been followed: Bloodhound at 1 killcount.",
            "Corndwell", 3);
        assertTrue(detector.drain(20).isEmpty());
    }

    @Test
    public void ordinaryChatAndExistingPetActionsNeverPrime()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("PUBLICCHAT", FOLLOW, "Corndwell", 1);
        detector.onChat("CLAN_CHAT", FOLLOW, "Corndwell", 1);
        detector.onChat("GAMEMESSAGE", "You pick up your pet.", "Corndwell", 2);
        detector.onChat("GAMEMESSAGE", "Your pet has been reclaimed.", "Corndwell", 3);
        detector.onChat("GAMEMESSAGE", "Your pet changes form.", "Corndwell", 4);
        assertTrue(detector.drain(20).isEmpty());
    }

    @Test
    public void unrelatedCollectionCannotOverwriteKnownPet()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 10);
        detector.onChat("GAMEMESSAGE", "Untradeable drop: TzRek-Jad", "Corndwell", 11);
        detector.onCollection("Bloodhound", 12);
        detector.onCollection("Dragon claws", 12);
        PetDetector.PetAward award = only(detector.drain(15));
        assertEquals(ItemID.JAD_PET, award.getItemId());
        assertFalse(award.isCollectionLog());
    }

    @Test
    public void onlyMatchingSelfClanAnnouncementEnriches()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 10);
        detector.onChat("CLAN_MESSAGE", "[Clan] Other has a funny feeling like he's being followed: Bloodhound at 1 killcount.",
            "Corndwell", 11);
        detector.onChat("CLAN_MESSAGE", "[Clan] Corndwell feels like he's acquired something special: Abyssal orphan",
            "Corndwell", 12);
        PetDetector.PetAward award = only(detector.drain(15));
        assertEquals(ItemID.ABYSSALSIRE_PET, award.getItemId());
        assertEquals("Unknown source", award.getSource());
    }

    @Test
    public void matchingChatAcceptsTagsWhitespaceAndCase()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", "<col=ff0000>" + FOLLOW + "</col>", "Corndwell", 10);
        detector.onCollection("  tzrek-jad ", 11);
        PetDetector.PetAward award = only(detector.drain(15));
        assertEquals(ItemID.JAD_PET, award.getItemId());
        assertTrue(award.isCollectionLog());
    }

    @Test
    public void repeatedSameTickSignalDoesNotPostTwice()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 10);
        detector.onChat("SPAM", FOLLOW, "Corndwell", 10);
        assertEquals(1, detector.drain(15).size());
    }

    @Test
    public void separateIdenticalAcquisitionsRemainSeparate()
    {
        PetDetector detector = new PetDetector();
        detector.onLoot(Collections.singletonList(pet(ItemID.BLOODHOUND_PET, "Bloodhound")), "Clue Scroll (Master)", 10);
        detector.onLoot(Collections.singletonList(pet(ItemID.BLOODHOUND_PET, "Bloodhound")), "Clue Scroll (Master)", 11);
        assertEquals(2, detector.drain(16).size());
    }

    @Test
    public void alternateMessagesForOneTickAreOneAward()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 10);
        detector.onChat("SPAM", "You feel something weird sneaking into your backpack.", "Corndwell", 10);
        assertEquals(1, detector.drain(15).size());
    }

    @Test
    public void separateChatAwardsRemainSeparate()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", DUPLICATE, "Corndwell", 10);
        detector.onChat("GAMEMESSAGE", DUPLICATE, "Corndwell", 11);
        assertEquals(2, detector.drain(16).size());
    }

    @Test
    public void lateEvidenceCannotEnrichAnExpiredAcquisition()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 10);
        detector.onCollection("Bloodhound", 16);
        assertEquals(-1, only(detector.drain(16)).getItemId());
        detector.onCollection("Bloodhound", 17);
        assertTrue(detector.drain(30).isEmpty());
    }

    @Test
    public void clearingSessionDiscardsPendingPet()
    {
        PetDetector detector = new PetDetector();
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 10);
        detector.clear();
        assertTrue(detector.drain(20).isEmpty());
        detector.onChat("GAMEMESSAGE", FOLLOW, "Corndwell", 10);
        assertEquals(1, detector.drain(15).size());
    }

    @Test
    public void catalogRecognizesBossSkillRaidAndTransformedPets()
    {
        PetDetector detector = new PetDetector();
        assertTrue(detector.isPet(ItemID.SKILLPETWC, "Beaver"));
        assertTrue(detector.isPet(ItemID.WARDENPET_TUMEKEN, "Tumeken's guardian"));
        assertTrue(detector.isPet(ItemID.GAUNTLETPET_CORRUPT, "Corrupted Youngllef"));
        assertTrue(detector.isPet(ItemID.YAMAPET, "Yami"));
        assertTrue(detector.isPet(-1, "Vet'ion jr."));
        assertFalse(detector.isPet(ItemID.TOA_PET_MORPH_AKKHA, "Remnant of Akkha"));
        assertFalse(detector.isPet(ItemID.FORESTRY_FOX_PET_WHISTLE, "Fox whistle"));
    }

    private static PetDetector.PetAward only(List<PetDetector.PetAward> awards)
    {
        assertEquals(1, awards.size());
        return awards.get(0);
    }

    private static DropItem pet(int id, String name)
    {
        return new DropItem(id, name, 1L, -1L, -1L, Rarity.unknown());
    }
}

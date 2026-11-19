package com.corndwell.lootherald.discord;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class DestinationTest
{
    static final String URL = "https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuv";

    @Test public void canonicalEndpointDeduplicatesApiVersionsAndLegacyHost()
    {
        List<Destination> destinations = Destination.parse("Clan | " + URL + "\n" + URL.replace("discord.com/api/", "discordapp.com/api/v10/") + "?wait=false");
        assertEquals(1, destinations.size());
        assertEquals("Clan", destinations.get(0).getName());
        assertEquals(URL, destinations.get(0).getUrl());
        assertEquals(destinations.get(0).getId(), Destination.parse(URL).get(0).getId());
        assertFalse(destinations.get(0).toString().contains("abcdefghijkl"));
    }

    @Test public void permitsNumericThreadAndRejectsCredentialLeaksInErrors()
    {
        assertTrue(Destination.parse(URL + "?thread_id=12345678").get(0).getUrl().endsWith("?thread_id=12345678"));
        for (String invalid : List.of(URL.replace("https", "http"), URL.replace("discord.com", "discord.com.evil.example"),
            URL + "#fragment", URL + "?custom=secret", URL.replace("discord.com", "user:password@discord.com")))
        {
            try { Destination.parse(invalid); fail("Invalid URL accepted"); }
            catch (IllegalArgumentException e) { assertFalse(e.getMessage().contains("abcdefghijkl")); assertFalse(e.getMessage().contains("password")); }
        }
    }

    @Test public void restrictsDistinctEndpointsButAllowsBlankConfiguration()
    {
        assertTrue(Destination.parse(" \n").isEmpty());
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 6; i++) { many.append(URL.replace("123456789012345678", Integer.toString(i + 1))).append('\n'); }
        try { Destination.parse(many.toString()); fail("Too many destinations accepted"); }
        catch (IllegalArgumentException e) { assertTrue(e.getMessage().contains("five")); }
    }
}

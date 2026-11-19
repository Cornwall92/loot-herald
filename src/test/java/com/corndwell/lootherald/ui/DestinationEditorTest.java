package com.corndwell.lootherald.ui;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class DestinationEditorTest
{
    private static final String URL = "https://discord.com/api/webhooks/123456789/secret_token_123";
    private static final String OTHER = "https://discord.com/api/webhooks/987654321/another_secret_123";

    @Test public void normalizesUrlsAndKeepsNamesAcrossBlankRows()
    {
        assertEquals("Clan | " + URL + "\nPersonal | " + OTHER + "?thread_id=123", DestinationEditor.serialize(
            List.of(" Clan ", "", "Personal"),
            List.of("https://discordapp.com/api/v10/webhooks/123456789/secret_token_123/?wait=false", "", OTHER + "?wait=true&thread_id=123")));
    }

    @Test public void emptyRowsRemoveAllDestinations()
    {
        assertEquals("", DestinationEditor.serialize(List.of("", "  "), List.of("", "")));
    }

    @Test public void duplicateNamesAreCaseInsensitive()
    {
        invalid(List.of("Clan", "clan"), List.of(URL, OTHER), "unique name");
    }

    @Test public void duplicateNormalizedEndpointsCannotAcquireDifferentNames()
    {
        invalid(List.of("Clan", "Personal"), List.of(URL, URL + "?wait=true"), "same webhook");
    }

    @Test public void partialRowsAndInvalidUrlsUseCredentialFreeErrors()
    {
        invalid(List.of("Clan"), List.of(""), "both a name");
        invalid(List.of(""), List.of(URL), "both a name");
        invalid(List.of("Clan"), List.of("https://example.invalid/secret_token_123"), "valid HTTPS Discord");
    }

    @Test public void namesAndUrlsCannotInjectAdditionalLines()
    {
        invalid(List.of("Clan\nOther"), List.of(URL), "without URLs");
        invalid(List.of("Clan | Other"), List.of(URL), "without URLs");
        invalid(List.of("Clan"), List.of(URL + "\nOther | " + OTHER), "valid HTTPS Discord");
    }

    private static void invalid(List<String> names, List<String> urls, String expected)
    {
        try
        {
            DestinationEditor.serialize(names, urls);
            fail("Expected invalid destinations");
        }
        catch (IllegalArgumentException invalid)
        {
            assertTrue(invalid.getMessage().contains(expected));
            assertFalse(invalid.getMessage().contains("secret_token"));
            assertFalse(invalid.getMessage().contains("https://"));
        }
    }
}

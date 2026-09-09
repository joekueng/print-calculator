package com.printcalculator.service;

import com.printcalculator.dto.LinkedInPostDto;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LinkedInPostServiceTest {

    @Test
    void refreshKeepsLastSuccessfulCacheWhenLinkedInFails() throws Exception {
        LinkedInPostClient client = mock(LinkedInPostClient.class);
        LinkedInPostDto post = new LinkedInPostDto(
                "urn:li:share:123",
                "A post",
                Instant.parse("2026-09-09T12:00:00Z"),
                "https://www.linkedin.com/feed/update/urn:li:share:123/",
                null,
                null
        );
        when(client.isConfigured()).thenReturn(true);
        when(client.fetchPosts()).thenReturn(List.of(post)).thenThrow(new IOException("temporary failure"));
        LinkedInPostService service = new LinkedInPostService(client, true);

        assertTrue(service.refreshPosts().updated());
        assertFalse(service.refreshPosts().updated());

        assertEquals(List.of(post), service.getPosts());
        assertEquals(1, service.getSyncStatus().postCount());
        assertNotNull(service.getSyncStatus().lastSuccessfulSync());
    }

    @Test
    void refreshReportsUnavailableConfigurationWithoutCallingLinkedIn() {
        LinkedInPostClient client = mock(LinkedInPostClient.class);
        when(client.isConfigured()).thenReturn(false);
        LinkedInPostService service = new LinkedInPostService(client, true);

        assertFalse(service.refreshPosts().configured());
        assertFalse(service.refreshPosts().updated());
        assertEquals(0, service.getPosts().size());
    }
}

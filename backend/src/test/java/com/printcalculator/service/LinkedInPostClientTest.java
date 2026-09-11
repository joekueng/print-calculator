package com.printcalculator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.printcalculator.dto.LinkedInPostDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkedInPostClientTest {

    @Test
    void parsePostsKeepsPublishedPostsAndOrdersNewestFirst() throws Exception {
        LinkedInPostClient client = client("token", "urn:li:organization:123");

        List<LinkedInPostDto> posts = client.parsePosts("""
                {
                  "elements": [
                    {
                      "id": "urn:li:share:older",
                      "commentary": "Older post",
                      "publishedAt": 1000,
                      "lifecycleState": "PUBLISHED"
                    },
                    {
                      "id": "urn:li:share:draft",
                      "commentary": "Draft post",
                      "publishedAt": 3000,
                      "lifecycleState": "DRAFT"
                    },
                    {
                      "id": "urn:li:ugcPost:newer",
                      "commentary": "Newer post",
                      "createdAt": 2000,
                      "lifecycleState": "PUBLISHED"
                    }
                  ]
                }
                """);

        assertEquals(List.of("Newer post", "Older post"),
                posts.stream().map(LinkedInPostDto::commentary).toList());
        assertEquals("https://www.linkedin.com/feed/update/urn:li:ugcPost:newer/", posts.getFirst().url());
    }

    @Test
    void configurationRequiresBothTokenAndOrganizationUrn() {
        assertTrue(client("token", "urn:li:organization:123").isConfigured());
        assertFalse(client("", "urn:li:organization:123").isConfigured());
        assertFalse(client("token", "").isConfigured());
    }

    private LinkedInPostClient client(String token, String organizationUrn) {
        return new LinkedInPostClient(
                new ObjectMapper(),
                token,
                organizationUrn,
                "202608",
                "https://api.linkedin.com/rest",
                15,
                5
        );
    }
}

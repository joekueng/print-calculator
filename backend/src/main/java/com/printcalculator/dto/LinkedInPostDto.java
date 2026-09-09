package com.printcalculator.dto;

import java.time.Instant;

public record LinkedInPostDto(
        String id,
        String commentary,
        Instant publishedAt,
        String url,
        String imageUrl,
        String imageAltText
) {
}

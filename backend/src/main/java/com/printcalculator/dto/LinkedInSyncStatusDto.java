package com.printcalculator.dto;

import java.time.Instant;

public record LinkedInSyncStatusDto(
        boolean enabled,
        boolean configured,
        boolean updated,
        int postCount,
        Instant lastSuccessfulSync
) {
}

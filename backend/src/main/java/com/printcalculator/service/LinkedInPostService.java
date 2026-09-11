package com.printcalculator.service;

import com.printcalculator.dto.LinkedInPostDto;
import com.printcalculator.dto.LinkedInSyncStatusDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class LinkedInPostService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LinkedInPostService.class);

    private final LinkedInPostClient client;
    private final boolean enabled;
    private final AtomicReference<List<LinkedInPostDto>> cachedPosts = new AtomicReference<>(List.of());
    private final AtomicReference<Instant> lastSuccessfulSync = new AtomicReference<>();

    public LinkedInPostService(
            LinkedInPostClient client,
            @Value("${linkedin.sync.enabled:true}") boolean enabled
    ) {
        this.client = client;
        this.enabled = enabled;
    }

    public List<LinkedInPostDto> getPosts() {
        return cachedPosts.get();
    }

    public LinkedInSyncStatusDto getSyncStatus() {
        return syncStatus(false);
    }

    @Scheduled(
            cron = "${linkedin.sync.cron:0 0 4 * * *}",
            zone = "${linkedin.sync.zone:Europe/Zurich}"
    )
    void scheduledRefresh() {
        refreshPosts();
    }

    public synchronized LinkedInSyncStatusDto refreshPosts() {
        if (!enabled || !client.isConfigured()) {
            return syncStatus(false);
        }

        try {
            cachedPosts.set(client.fetchPosts());
            lastSuccessfulSync.set(Instant.now());
            return syncStatus(true);
        } catch (IOException exception) {
            LOGGER.warn("Unable to refresh LinkedIn posts: {}", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("LinkedIn post refresh was interrupted");
        }
        return syncStatus(false);
    }

    private LinkedInSyncStatusDto syncStatus(boolean updated) {
        return new LinkedInSyncStatusDto(
                enabled,
                client.isConfigured(),
                updated,
                cachedPosts.get().size(),
                lastSuccessfulSync.get()
        );
    }
}

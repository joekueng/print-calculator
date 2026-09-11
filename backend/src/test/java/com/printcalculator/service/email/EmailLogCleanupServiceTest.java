package com.printcalculator.service.email;

import com.printcalculator.repository.EmailLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailLogCleanupServiceTest {

    @Mock
    private EmailLogRepository emailLogRepository;

    @Test
    void cleanupExpiredEmailLogs_shouldDeleteRecordsOlderThanRetention() {
        EmailLogCleanupService service = new EmailLogCleanupService(emailLogRepository, 365);

        service.cleanupExpiredEmailLogs();

        ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(emailLogRepository).deleteByAttemptedAtBefore(cutoffCaptor.capture());
        OffsetDateTime cutoff = cutoffCaptor.getValue();
        OffsetDateTime expected = OffsetDateTime.now().minusDays(365);
        assertTrue(cutoff.isBefore(expected.plusSeconds(5)));
        assertTrue(cutoff.isAfter(expected.minusSeconds(5)));
    }

    @Test
    void cleanupExpiredEmailLogs_withRetentionDisabled_shouldNotDelete() {
        EmailLogCleanupService service = new EmailLogCleanupService(emailLogRepository, 0);

        service.cleanupExpiredEmailLogs();

        verify(emailLogRepository, never()).deleteByAttemptedAtBefore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cleanupExpiredEmailLogs_shouldDeleteRecordsWithConfiguredRetention() {
        EmailLogCleanupService service = new EmailLogCleanupService(emailLogRepository, 90);

        service.cleanupExpiredEmailLogs();

        ArgumentCaptor<OffsetDateTime> cutoffCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(emailLogRepository).deleteByAttemptedAtBefore(cutoffCaptor.capture());
        OffsetDateTime expected = OffsetDateTime.now().minusDays(90);
        OffsetDateTime cutoff = cutoffCaptor.getValue();
        assertTrue(cutoff.isBefore(expected.plusSeconds(5)));
        assertTrue(cutoff.isAfter(expected.minusSeconds(5)));
    }
}

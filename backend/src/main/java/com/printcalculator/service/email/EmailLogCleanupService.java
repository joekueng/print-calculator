package com.printcalculator.service.email;

import com.printcalculator.repository.EmailLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Periodically removes email audit records older than the configured retention
 * period. Email logs contain personal data (recipient addresses, subjects,
 * attachment names) so they must not be kept indefinitely.
 */
@Service
public class EmailLogCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(EmailLogCleanupService.class);

    private final EmailLogRepository emailLogRepository;
    private final int retentionDays;

    public EmailLogCleanupService(EmailLogRepository emailLogRepository,
                                  @Value("${app.mail.audit.retention-days:365}") int retentionDays) {
        this.emailLogRepository = emailLogRepository;
        this.retentionDays = retentionDays;
    }

    // Run every day at 3:30 AM, after the session cleanup job.
    @Scheduled(cron = "0 30 3 * * ?")
    @Transactional
    public void cleanupExpiredEmailLogs() {
        if (retentionDays <= 0) {
            logger.info("Email log cleanup skipped: retention disabled (app.mail.audit.retention-days={}).", retentionDays);
            return;
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);
        long deleted = emailLogRepository.deleteByAttemptedAtBefore(cutoff);
        if (deleted > 0) {
            logger.info("Email log cleanup removed {} records older than {} days.", deleted, retentionDays);
        }
    }
}

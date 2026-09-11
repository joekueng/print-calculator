package com.printcalculator.service.email;

import java.time.OffsetDateTime;

public record EmailSendResult(
        String status,
        OffsetDateTime attemptedAt,
        OffsetDateTime sentAt,
        String errorMessage
) {
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    public static EmailSendResult sent(OffsetDateTime attemptedAt, OffsetDateTime sentAt) {
        return new EmailSendResult(STATUS_SENT, attemptedAt, sentAt, null);
    }

    public static EmailSendResult failed(OffsetDateTime attemptedAt, String errorMessage) {
        return new EmailSendResult(STATUS_FAILED, attemptedAt, null, errorMessage);
    }

    public static EmailSendResult skipped(OffsetDateTime attemptedAt, String reason) {
        return new EmailSendResult(STATUS_SKIPPED, attemptedAt, null, reason);
    }
}

package com.nomnom.mvp.domain;

import java.time.Instant;
import java.util.UUID;

public record ImportJob(
        UUID id,
        UUID userId,
        String sourceProvider,
        String sharedText,
        String sharedUrl,
        String parsedName,
        String parsedExternalId,
        Status status,
        String failureReason,
        UUID venueId,
        boolean requiresManualCompletion,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        IMPORTED,
        PENDING_MANUAL_COMPLETION,
        DUPLICATED,
        FAILED
    }
}

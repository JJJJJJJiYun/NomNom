package com.nomnom.mvp.domain;

import java.time.Instant;
import java.util.UUID;

public record ListItem(
        UUID id,
        UUID venueId,
        String sourceProvider,
        UUID sourceImportJobId,
        String note,
        boolean pinned,
        Instant createdAt,
        Instant updatedAt
) {
}

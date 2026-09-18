package com.digitalnest.petmemorial.digitallife;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DigitalLifeFact(
        UUID id,
        UUID profileId,
        String factType,
        String statement,
        String sourceType,
        String sourceLabel,
        String verificationStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

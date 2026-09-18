package com.digitalnest.petmemorial.digitallife;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DigitalLifeProfile(
        UUID id,
        UUID memorialId,
        UUID userId,
        String status,
        OffsetDateTime profileConsentAt,
        OffsetDateTime textProcessingConsentAt,
        String consentVersion,
        OffsetDateTime deletedAt,
        OffsetDateTime updatedAt
) {
}

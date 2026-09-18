package com.digitalnest.petmemorial.journey;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MemorialArchiveEntry(
        UUID id,
        UUID memorialId,
        String entryType,
        String title,
        String body,
        LocalDate eventDate,
        String placeLabel,
        String sourceLabel,
        String verificationStatus,
        String visibility,
        UUID mediaId,
        String mediaUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

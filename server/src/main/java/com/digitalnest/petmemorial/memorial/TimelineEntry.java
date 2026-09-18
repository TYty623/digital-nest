package com.digitalnest.petmemorial.memorial;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TimelineEntry(
        UUID id,
        UUID memorialId,
        UUID mediaId,
        String mediaContentType,
        LocalDate eventDate,
        String datePrecision,
        String title,
        String body,
        int position,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

package com.digitalnest.petmemorial.journey;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MemorialAnniversary(
        UUID id,
        UUID memorialId,
        String anniversaryType,
        String title,
        LocalDate eventDate,
        String repeatRule,
        boolean reminderEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

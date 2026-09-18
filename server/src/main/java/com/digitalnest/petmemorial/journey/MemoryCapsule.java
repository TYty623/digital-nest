package com.digitalnest.petmemorial.journey;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MemoryCapsule(
        UUID id,
        UUID memorialId,
        String title,
        String body,
        UUID mediaId,
        String mediaUrl,
        LocalDate unlockOn,
        String visibility,
        boolean unlocked,
        OffsetDateTime openedAt,
        OffsetDateTime createdAt
) {
}

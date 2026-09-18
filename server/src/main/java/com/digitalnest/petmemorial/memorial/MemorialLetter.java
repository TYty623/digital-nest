package com.digitalnest.petmemorial.memorial;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MemorialLetter(
        UUID id,
        UUID memorialId,
        String subject,
        String body,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

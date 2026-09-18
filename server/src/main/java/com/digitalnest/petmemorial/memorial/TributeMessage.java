package com.digitalnest.petmemorial.memorial;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TributeMessage(
        UUID id,
        String authorName,
        String message,
        String status,
        int reportCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

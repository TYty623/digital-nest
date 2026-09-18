package com.digitalnest.petmemorial.billing;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountEntitlement(
        UUID id,
        UUID userId,
        UUID orderId,
        String planCode,
        String planName,
        int photoLimit,
        int shortVideoLimit,
        int timelineLimit,
        int themeLimit,
        OffsetDateTime hostedUntil,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime revokedAt
) {
}

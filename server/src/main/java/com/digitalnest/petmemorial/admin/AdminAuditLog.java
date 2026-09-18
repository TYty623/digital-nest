package com.digitalnest.petmemorial.admin;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminAuditLog(
        UUID id,
        UUID actorUserId,
        String actorDisplayName,
        String action,
        String targetType,
        UUID targetId,
        String reason,
        String details,
        OffsetDateTime createdAt
) {
}

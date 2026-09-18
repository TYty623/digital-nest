package com.digitalnest.petmemorial.journey;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RitualRecord(
        UUID id,
        UUID memorialId,
        String ritualType,
        String ritualAction,
        String note,
        boolean ambientEnabled,
        OffsetDateTime completedAt
) {
}

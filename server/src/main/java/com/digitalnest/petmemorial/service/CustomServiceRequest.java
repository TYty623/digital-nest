package com.digitalnest.petmemorial.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomServiceRequest(
        UUID id,
        UUID userId,
        String contactDetails,
        String requestDetails,
        String status,
        boolean materialsReady,
        String assignee,
        LocalDate dueDate,
        int revisionCount,
        String customerMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

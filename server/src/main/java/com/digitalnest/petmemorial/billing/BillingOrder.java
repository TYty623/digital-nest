package com.digitalnest.petmemorial.billing;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BillingOrder(
        UUID id,
        UUID userId,
        String planCode,
        String planName,
        int amountCents,
        String currency,
        int photoLimit,
        int shortVideoLimit,
        int timelineLimit,
        int themeLimit,
        int hostedYears,
        String status,
        String idempotencyKey,
        String paymentReference,
        OffsetDateTime paidAt,
        OffsetDateTime refundedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}

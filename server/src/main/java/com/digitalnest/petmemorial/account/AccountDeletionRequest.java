package com.digitalnest.petmemorial.account;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountDeletionRequest(UUID userId, OffsetDateTime requestedAt, OffsetDateTime scheduledFor) {
}

package com.digitalnest.petmemorial.media;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MediaDeletionRequest(UUID id, String storageFilename, OffsetDateTime purgeAfter) {
}

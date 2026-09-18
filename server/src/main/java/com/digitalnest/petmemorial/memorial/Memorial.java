package com.digitalnest.petmemorial.memorial;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Memorial(
        UUID id,
        UUID userId,
        String slug,
        String petName,
        String species,
        UUID coverMediaId,
        String coverImageUrl,
        String farewellMessage,
        String aboutTa,
        java.time.LocalDate companionStartedOn,
        java.time.LocalDate companionEndedOn,
        String status,
        String visibility,
        String theme,
        String accessCodeHash,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        int version
) {
}

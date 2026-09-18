package com.digitalnest.petmemorial.memorial;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Deliberately small public projection used by the weak-community discovery page. */
public record CommunityMemorial(
        UUID id,
        String slug,
        String petName,
        String species,
        String coverImageUrl,
        LocalDate companionStartedOn,
        LocalDate companionEndedOn,
        String signature,
        long lightCount,
        OffsetDateTime publishedAt
) {}

package com.digitalnest.petmemorial.memorial;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GalleryItem(
        UUID id,
        UUID memorialId,
        UUID mediaId,
        String mediaContentType,
        String caption,
        int position,
        OffsetDateTime createdAt
) {
}

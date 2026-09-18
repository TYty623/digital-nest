package com.digitalnest.petmemorial.media;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MediaAsset(
        UUID id,
        UUID ownerId,
        String storageFilename,
        String contentType,
        long byteSize,
        OffsetDateTime createdAt
) {
    public boolean isImage() {
        return contentType.startsWith("image/");
    }

    public boolean isVideo() {
        return contentType.startsWith("video/");
    }

    public boolean isAudio() {
        return contentType.startsWith("audio/");
    }
}

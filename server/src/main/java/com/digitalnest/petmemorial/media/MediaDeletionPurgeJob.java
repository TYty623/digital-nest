package com.digitalnest.petmemorial.media;

import com.digitalnest.petmemorial.shared.error.ApiException;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MediaDeletionPurgeJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(MediaDeletionPurgeJob.class);
    private final MediaService mediaService;

    public MediaDeletionPurgeJob(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Scheduled(fixedDelayString = "${app.storage.media-deletion-purge-delay-ms:3600000}")
    public void purgeDueMedia() {
        try {
            mediaService.purgeDueFiles(OffsetDateTime.now());
        } catch (ApiException exception) {
            LOGGER.warn("Deferred media cleanup will be retried: {}", exception.getMessage());
        }
    }
}

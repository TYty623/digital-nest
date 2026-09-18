package com.digitalnest.petmemorial.media;

import com.digitalnest.petmemorial.shared.error.ApiException;
import com.digitalnest.petmemorial.shared.access.MemorialAccess;
import jakarta.servlet.http.HttpSession;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {

    private final LocalMediaStorage localMediaStorage;
    private final MediaRepository mediaRepository;
    private final MediaDeletionRepository mediaDeletionRepository;
    private final int mediaDeletionDelayHours;

    public MediaService(
            LocalMediaStorage localMediaStorage,
            MediaRepository mediaRepository,
            MediaDeletionRepository mediaDeletionRepository,
            @org.springframework.beans.factory.annotation.Value("${app.storage.media-deletion-delay-hours:24}") int mediaDeletionDelayHours
    ) {
        this.localMediaStorage = localMediaStorage;
        this.mediaRepository = mediaRepository;
        this.mediaDeletionRepository = mediaDeletionRepository;
        this.mediaDeletionDelayHours = mediaDeletionDelayHours;
    }

    public MediaAsset uploadImage(UUID ownerId, MultipartFile file) {
        return upload(ownerId, localMediaStorage.storeImage(file));
    }

    public MediaAsset uploadVideo(UUID ownerId, MultipartFile file) {
        return upload(ownerId, localMediaStorage.storeVideo(file));
    }

    public MediaAsset uploadAudio(UUID ownerId, MultipartFile file) {
        return upload(ownerId, localMediaStorage.storeAudio(file));
    }

    private MediaAsset upload(UUID ownerId, LocalMediaStorage.StoredMedia storedMedia) {
        try {
            return mediaRepository.create(ownerId, UUID.randomUUID(), storedMedia);
        } catch (RuntimeException exception) {
            try {
                localMediaStorage.delete(storedMedia.filename());
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    public MediaAsset getForOwner(UUID mediaId, UUID ownerId) {
        MediaAsset asset = getExisting(mediaId);
        if (!asset.ownerId().equals(ownerId)) {
            throw notFound();
        }
        return asset;
    }

    public MediaAsset getReadable(UUID mediaId, UUID signedInUserId, HttpSession session) {
        MediaAsset asset = getExisting(mediaId);
        if (asset.ownerId().equals(signedInUserId)
                || mediaRepository.isPubliclyReadable(mediaId)
                || mediaRepository.findPasswordMemorialId(mediaId).filter(id -> MemorialAccess.isGranted(session, id)).isPresent()) {
            return asset;
        }
        throw notFound();
    }

    public Path resolve(MediaAsset asset) {
        return localMediaStorage.resolve(asset.storageFilename());
    }

    public List<MediaAsset> listOwned(UUID ownerId) {
        return mediaRepository.findAllByOwnerId(ownerId);
    }

    public void deleteStored(MediaAsset asset) {
        localMediaStorage.delete(asset.storageFilename());
    }

    public void scheduleUnreferencedForDeletion(UUID mediaId, UUID ownerId) {
        MediaAsset asset = getForOwner(mediaId, ownerId);
        if (mediaRepository.deleteIfUnreferenced(mediaId, ownerId)) {
            mediaDeletionRepository.enqueue(asset.storageFilename(), OffsetDateTime.now().plusHours(mediaDeletionDelayHours));
        }
    }

    public void purgeDueFiles(OffsetDateTime now) {
        for (MediaDeletionRequest request : mediaDeletionRepository.findDue(now)) {
            localMediaStorage.delete(request.storageFilename());
            mediaDeletionRepository.delete(request.id());
        }
    }

    private MediaAsset getExisting(UUID mediaId) {
        return mediaRepository.findById(mediaId).orElseThrow(this::notFound);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "这份媒体不存在，或你没有查看权限。");
    }
}

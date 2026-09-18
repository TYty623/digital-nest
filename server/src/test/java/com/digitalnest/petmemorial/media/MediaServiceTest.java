package com.digitalnest.petmemorial.media;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MediaServiceTest {

    @Test
    void removesTheStoredFileWhenCreatingTheMediaRecordFails() {
        LocalMediaStorage storage = mock(LocalMediaStorage.class);
        MediaRepository repository = mock(MediaRepository.class);
        MediaDeletionRepository deletionRepository = mock(MediaDeletionRepository.class);
        MediaService service = new MediaService(storage, repository, deletionRepository, 24);
        LocalMediaStorage.StoredMedia storedImage = new LocalMediaStorage.StoredMedia(
                "temporary-image.png", "image/png", 4);
        DataIntegrityViolationException persistenceFailure = new DataIntegrityViolationException("database unavailable");

        doReturn(storedImage).when(storage).storeImage(any());
        doThrow(persistenceFailure).when(repository).create(eq(UUID.fromString("a0c2ee33-5c40-4941-bc28-2649d3a7ba68")), any(), eq(storedImage));

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                () -> service.uploadImage(UUID.fromString("a0c2ee33-5c40-4941-bc28-2649d3a7ba68"),
                        new MockMultipartFile("file", "image.png", "image/png", new byte[]{1, 2, 3, 4})));

        assertSame(persistenceFailure, thrown);
        verify(storage).delete("temporary-image.png");
    }
}

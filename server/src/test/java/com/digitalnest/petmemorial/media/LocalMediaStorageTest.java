package com.digitalnest.petmemorial.media;

import com.digitalnest.petmemorial.shared.error.ApiException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalMediaStorageTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
    };
    private static final byte[] MP4 = new byte[]{
            0x00, 0x00, 0x00, 0x10, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D
    };
    private static final byte[] MP3 = new byte[]{0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsAFileThatOnlyPretendsToBeAPng() {
        LocalMediaStorage storage = new LocalMediaStorage(temporaryDirectory.toString());

        ApiException exception = assertThrows(ApiException.class, () -> storage.storeImage(
                new MockMultipartFile("file", "not-an-image.png", "image/png", "not an image".getBytes())));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.status());
        assertEquals("IMAGE_SIGNATURE_INVALID", exception.code());
    }

    @Test
    void acceptsAFileWhoseSignatureMatchesTheDeclaredPngType() {
        LocalMediaStorage storage = new LocalMediaStorage(temporaryDirectory.toString());

        LocalMediaStorage.StoredMedia stored = storage.storeImage(
                new MockMultipartFile("file", "memory.png", "image/png", PNG));

        assertEquals("image/png", stored.contentType());
        assertTrue(java.nio.file.Files.isRegularFile(storage.resolve(stored.filename())));
    }

    @Test
    void acceptsAnMp4OnlyWhenItsContainerSignatureMatches() {
        LocalMediaStorage storage = new LocalMediaStorage(temporaryDirectory.toString());

        LocalMediaStorage.StoredMedia stored = storage.storeVideo(
                new MockMultipartFile("file", "memory.mp4", "video/mp4", MP4));

        assertEquals("video/mp4", stored.contentType());
        assertTrue(java.nio.file.Files.isRegularFile(storage.resolve(stored.filename())));

        ApiException exception = assertThrows(ApiException.class, () -> storage.storeVideo(
                new MockMultipartFile("file", "not-a-video.mp4", "video/mp4", "not a video".getBytes())));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.status());
        assertEquals("VIDEO_SIGNATURE_INVALID", exception.code());
    }

    @Test
    void acceptsRealMp3SignatureAndRejectsDisguisedAudio() {
        LocalMediaStorage storage = new LocalMediaStorage(temporaryDirectory.toString());
        LocalMediaStorage.StoredMedia stored = storage.storeAudio(
                new MockMultipartFile("file", "voice.mp3", "audio/mpeg", MP3));
        assertEquals("audio/mpeg", stored.contentType());
        assertTrue(java.nio.file.Files.isRegularFile(storage.resolve(stored.filename())));

        ApiException exception = assertThrows(ApiException.class, () -> storage.storeAudio(
                new MockMultipartFile("file", "fake.mp3", "audio/mpeg", "not audio".getBytes())));
        assertEquals("AUDIO_SIGNATURE_INVALID", exception.code());
    }
}

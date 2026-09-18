package com.digitalnest.petmemorial.media;

import com.digitalnest.petmemorial.shared.error.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalMediaStorage {

    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 30 * 1024 * 1024;
    private static final long MAX_AUDIO_BYTES = 10 * 1024 * 1024;
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );
    private static final Map<String, String> VIDEO_EXTENSIONS = Map.of(
            "video/mp4", "mp4",
            "video/webm", "webm"
    );
    private static final Map<String, String> AUDIO_EXTENSIONS = Map.of(
            "audio/mpeg", "mp3",
            "audio/wav", "wav",
            "audio/x-wav", "wav"
    );
    private static final int IMAGE_SIGNATURE_BYTES = 12;
    private static final int VIDEO_SIGNATURE_BYTES = 12;

    private final Path storageDirectory;

    public LocalMediaStorage(@Value("${app.storage.local-directory}") String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public StoredMedia storeImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_REQUIRED", "请选择一张图片。");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "图片不能超过 5MB。");
        }
        String extension = IMAGE_EXTENSIONS.get(file.getContentType());
        if (extension == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE", "仅支持 JPG、PNG 和 WebP 图片。");
        }
        validateImageSignature(file, file.getContentType());

        return store(file, extension, "IMAGE_STORAGE_FAILED", "图片暂时无法保存，请稍后重试。");
    }

    public StoredMedia storeVideo(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VIDEO_REQUIRED", "请选择一个短视频。");
        }
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "VIDEO_TOO_LARGE", "短视频不能超过 30MB。");
        }
        String extension = VIDEO_EXTENSIONS.get(file.getContentType());
        if (extension == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_VIDEO", "仅支持 MP4 和 WebM 短视频。");
        }
        validateVideoSignature(file, file.getContentType());
        return store(file, extension, "VIDEO_STORAGE_FAILED", "短视频暂时无法保存，请稍后重试。");
    }

    public StoredMedia storeAudio(MultipartFile file) {
        if (file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "AUDIO_REQUIRED", "请选择一份声音文件。");
        if (file.getSize() > MAX_AUDIO_BYTES) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AUDIO_TOO_LARGE", "声音文件不能超过 10MB。");
        String extension = AUDIO_EXTENSIONS.get(file.getContentType());
        if (extension == null) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_AUDIO", "仅支持 MP3 和 WAV 声音文件。");
        validateAudioSignature(file, file.getContentType());
        return store(file, extension, "AUDIO_STORAGE_FAILED", "声音暂时无法保存，请稍后重试。");
    }

    private StoredMedia store(MultipartFile file, String extension, String failureCode, String failureMessage) {
        String filename = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(storageDirectory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, storageDirectory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, failureCode, failureMessage);
        }
        return new StoredMedia(filename, file.getContentType(), file.getSize());
    }

    private void validateImageSignature(MultipartFile file, String contentType) {
        try (InputStream input = file.getInputStream()) {
            byte[] signature = input.readNBytes(IMAGE_SIGNATURE_BYTES);
            boolean valid = switch (contentType) {
                case "image/jpeg" -> hasPrefix(signature, 0xFF, 0xD8, 0xFF);
                case "image/png" -> hasPrefix(signature, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
                case "image/webp" -> hasPrefix(signature, 0x52, 0x49, 0x46, 0x46)
                        && hasBytesAt(signature, 8, 0x57, 0x45, 0x42, 0x50);
                default -> false;
            };
            if (!valid) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "IMAGE_SIGNATURE_INVALID",
                        "图片内容与声明的格式不匹配，请重新选择 JPG、PNG 或 WebP 图片。");
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "IMAGE_STORAGE_FAILED", "图片暂时无法保存，请稍后重试。");
        }
    }

    private void validateVideoSignature(MultipartFile file, String contentType) {
        try (InputStream input = file.getInputStream()) {
            byte[] signature = input.readNBytes(VIDEO_SIGNATURE_BYTES);
            boolean valid = switch (contentType) {
                case "video/mp4" -> hasBytesAt(signature, 4, 0x66, 0x74, 0x79, 0x70);
                case "video/webm" -> hasPrefix(signature, 0x1A, 0x45, 0xDF, 0xA3);
                default -> false;
            };
            if (!valid) {
                throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "VIDEO_SIGNATURE_INVALID",
                        "短视频内容与声明的格式不匹配，请重新选择 MP4 或 WebM 文件。");
            }
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "VIDEO_STORAGE_FAILED", "短视频暂时无法保存，请稍后重试。");
        }
    }

    private void validateAudioSignature(MultipartFile file, String contentType) {
        try (InputStream input = file.getInputStream()) {
            byte[] signature = input.readNBytes(12);
            boolean wav = ("audio/wav".equals(contentType) || "audio/x-wav".equals(contentType))
                    && hasPrefix(signature, 0x52, 0x49, 0x46, 0x46) && hasBytesAt(signature, 8, 0x57, 0x41, 0x56, 0x45);
            boolean mp3 = "audio/mpeg".equals(contentType) && (hasPrefix(signature, 0x49, 0x44, 0x33)
                    || (signature.length >= 2 && Byte.toUnsignedInt(signature[0]) == 0xFF && (Byte.toUnsignedInt(signature[1]) & 0xE0) == 0xE0));
            if (!wav && !mp3) throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "AUDIO_SIGNATURE_INVALID", "声音内容与声明格式不匹配，请重新选择 MP3 或 WAV 文件。");
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIO_STORAGE_FAILED", "声音暂时无法保存，请稍后重试。");
        }
    }

    private boolean hasPrefix(byte[] bytes, int... expected) {
        return hasBytesAt(bytes, 0, expected);
    }

    private boolean hasBytesAt(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (Byte.toUnsignedInt(bytes[offset + index]) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    public Path resolve(String filename) {
        Path path = storageDirectory.resolve(filename).normalize();
        if (!path.startsWith(storageDirectory) || !Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "这份媒体不存在，或你没有查看权限。");
        }
        return path;
    }

    public void delete(String filename) {
        Path path = storageDirectory.resolve(filename).normalize();
        if (!path.startsWith(storageDirectory)) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_DELETE_FAILED", "媒体暂时无法清理，请稍后重试。");
        }
    }

    public record StoredMedia(String filename, String contentType, long byteSize) {
    }
}

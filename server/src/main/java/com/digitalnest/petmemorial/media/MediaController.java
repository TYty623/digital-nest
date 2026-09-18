package com.digitalnest.petmemorial.media;

import com.digitalnest.petmemorial.account.CurrentUser;
import com.digitalnest.petmemorial.shared.api.ApiResponse;
import com.digitalnest.petmemorial.shared.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping(value = "/images", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MediaUploadResponse> uploadImage(@RequestParam("file") MultipartFile file, Authentication authentication) {
        MediaAsset asset = mediaService.uploadImage(currentUser(authentication).id(), file);
        return ApiResponse.ok(toUploadResponse(asset));
    }

    @PostMapping(value = "/videos", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MediaUploadResponse> uploadVideo(@RequestParam("file") MultipartFile file, Authentication authentication) {
        MediaAsset asset = mediaService.uploadVideo(currentUser(authentication).id(), file);
        return ApiResponse.ok(toUploadResponse(asset));
    }

    @PostMapping(value = "/audio", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MediaUploadResponse> uploadAudio(@RequestParam("file") MultipartFile file, Authentication authentication) {
        return ApiResponse.ok(toUploadResponse(mediaService.uploadAudio(currentUser(authentication).id(), file)));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<?> getContent(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest request
    ) {
        MediaAsset asset = mediaService.getReadable(id, currentUserId(authentication), request.getSession(false));
        Path path = mediaService.resolve(asset);
        FileSystemResource resource = new FileSystemResource(path);
        MediaType contentType = MediaType.parseMediaType(asset.contentType());
        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        if ((asset.isVideo() || asset.isAudio()) && rangeHeader != null && !rangeHeader.isBlank()) {
            try {
                List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
                if (!ranges.isEmpty()) {
                    HttpRange range = ranges.getFirst();
                    long start = range.getRangeStart(asset.byteSize());
                    long end = range.getRangeEnd(asset.byteSize());
                    long length = end - start + 1;
                    byte[] region = readRange(path, start, length);
                    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                            .contentType(contentType)
                            .contentLength(length)
                            .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                            .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + asset.byteSize())
                            .cacheControl(CacheControl.noStore())
                            .body(region);
                }
            } catch (IllegalArgumentException exception) {
                throw new ApiException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                        "MEDIA_RANGE_NOT_SATISFIABLE", "请求的短视频片段不存在。");
            }
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(asset.byteSize())
                .header(HttpHeaders.ACCEPT_RANGES, asset.isVideo() || asset.isAudio() ? "bytes" : "none")
                .cacheControl(CacheControl.noStore())
                .body(resource);
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        throw new IllegalStateException("受保护的接口缺少当前用户");
    }

    private UUID currentUserId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CurrentUser user ? user.id() : null;
    }

    private String contentUrl(UUID id) {
        return "/api/v1/media/" + id + "/content";
    }

    private byte[] readRange(Path path, long start, long length) {
        try {
            byte[] allBytes = Files.readAllBytes(path);
            return Arrays.copyOfRange(allBytes, Math.toIntExact(start), Math.toIntExact(start + length));
        } catch (IOException | ArithmeticException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_READ_FAILED", "媒体暂时无法读取，请稍后重试。");
        }
    }

    private MediaUploadResponse toUploadResponse(MediaAsset asset) {
        return new MediaUploadResponse(asset.id(), contentUrl(asset.id()), asset.contentType());
    }

    public record MediaUploadResponse(UUID id, String url, String contentType) {
    }
}

package com.digitalnest.petmemorial.memorial;

import com.digitalnest.petmemorial.account.CurrentUser;
import com.digitalnest.petmemorial.shared.access.MemorialAccess;
import com.digitalnest.petmemorial.shared.api.ApiResponse;
import com.digitalnest.petmemorial.shared.ratelimit.VisitorRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memorials")
public class MemorialController {

    private final MemorialService memorialService;
    private final VisitorRateLimiter visitorRateLimiter;

    public MemorialController(MemorialService memorialService, VisitorRateLimiter visitorRateLimiter) {
        this.memorialService = memorialService;
        this.visitorRateLimiter = visitorRateLimiter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemorialResponse> create(
            @Valid @RequestBody MemorialRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(toResponse(memorialService.create(currentUser(authentication).id(), toCommand(request))));
    }

    @GetMapping("/mine")
    public ApiResponse<List<MemorialResponse>> listMine(Authentication authentication) {
        return ApiResponse.ok(memorialService.listMine(currentUser(authentication).id()).stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/public/discover")
    public ApiResponse<List<CommunityMemorial>> discoverPublic(
            @RequestParam(defaultValue = "24") int limit) {
        return ApiResponse.ok(memorialService.discoverPublic(limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<MemorialResponse> getMine(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(toResponse(memorialService.getMine(id, currentUser(authentication).id())));
    }

    @GetMapping("/{id}/timeline")
    public ApiResponse<List<TimelineEntry>> listTimeline(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(memorialService.listTimeline(id, currentUser(authentication).id()));
    }

    @PostMapping("/{id}/timeline")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TimelineEntry> addTimelineEntry(
            @PathVariable UUID id,
            @Valid @RequestBody TimelineEntryRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(memorialService.addTimelineEntry(id, currentUser(authentication).id(), toTimelineCommand(request)));
    }

    @PutMapping("/{id}/timeline/{entryId}")
    public ApiResponse<TimelineEntry> updateTimelineEntry(
            @PathVariable UUID id,
            @PathVariable UUID entryId,
            @Valid @RequestBody TimelineEntryRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(memorialService.updateTimelineEntry(
                id, entryId, currentUser(authentication).id(), toTimelineCommand(request)));
    }

    @PutMapping("/{id}/timeline/order")
    public ApiResponse<List<TimelineEntry>> reorderTimeline(
            @PathVariable UUID id,
            @Valid @RequestBody TimelineOrderRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(memorialService.reorderTimeline(id, currentUser(authentication).id(), request.entryIds()));
    }

    @DeleteMapping("/{id}/timeline/{entryId}")
    public ApiResponse<DeleteResponse> deleteTimelineEntry(
            @PathVariable UUID id,
            @PathVariable UUID entryId,
            Authentication authentication
    ) {
        memorialService.deleteTimelineEntry(id, entryId, currentUser(authentication).id());
        return ApiResponse.ok(new DeleteResponse(true));
    }

    @GetMapping("/{id}/letter")
    public ApiResponse<MemorialLetter> getLetter(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(memorialService.getLetter(id, currentUser(authentication).id()).orElse(null));
    }

    @PutMapping("/{id}/letter")
    public ApiResponse<MemorialLetter> saveLetter(
            @PathVariable UUID id,
            @Valid @RequestBody LetterRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(memorialService.saveLetter(
                id, currentUser(authentication).id(), blankToNull(request.subject()), request.body().trim()));
    }

    @GetMapping("/{id}/gallery")
    public ApiResponse<List<GalleryResponse>> listGallery(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(memorialService.listGallery(id, currentUser(authentication).id()).stream()
                .map(this::toGalleryResponse)
                .toList());
    }

    @PostMapping("/{id}/gallery")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GalleryResponse> addGalleryItem(
            @PathVariable UUID id,
            @Valid @RequestBody GalleryItemRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(toGalleryResponse(memorialService.addGalleryItem(
                id, currentUser(authentication).id(), request.mediaId(), blankToNull(request.caption()))));
    }

    @DeleteMapping("/{id}/gallery/{galleryItemId}")
    public ApiResponse<DeleteResponse> deleteGalleryItem(
            @PathVariable UUID id,
            @PathVariable UUID galleryItemId,
            Authentication authentication
    ) {
        memorialService.deleteGalleryItem(id, galleryItemId, currentUser(authentication).id());
        return ApiResponse.ok(new DeleteResponse(true));
    }

    @PutMapping("/{id}/gallery/{galleryItemId}")
    public ApiResponse<GalleryResponse> updateGalleryCaption(
            @PathVariable UUID id,
            @PathVariable UUID galleryItemId,
            @Valid @RequestBody GalleryCaptionRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(toGalleryResponse(memorialService.updateGalleryCaption(
                id, galleryItemId, currentUser(authentication).id(), blankToNull(request.caption()))));
    }

    @PutMapping("/{id}/gallery/order")
    public ApiResponse<List<GalleryResponse>> reorderGallery(
            @PathVariable UUID id,
            @Valid @RequestBody GalleryOrderRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(memorialService.reorderGallery(id, currentUser(authentication).id(), request.itemIds()).stream()
                .map(this::toGalleryResponse)
                .toList());
    }

    @GetMapping("/{id}/tributes")
    public ApiResponse<List<TributeMessage>> listTributes(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(memorialService.listTributes(id, currentUser(authentication).id()));
    }

    @PatchMapping("/{id}/tributes/{tributeId}")
    public ApiResponse<TributeMessage> updateTributeStatus(
            @PathVariable UUID id,
            @PathVariable UUID tributeId,
            @Valid @RequestBody TributeStatusRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(memorialService.updateTributeStatus(
                id, tributeId, currentUser(authentication).id(), request.status()));
    }

    @PutMapping("/{id}")
    public ApiResponse<MemorialResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody MemorialRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(toResponse(memorialService.update(
                id, currentUser(authentication).id(), toCommand(request), request.version())));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<MemorialResponse> publish(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(toResponse(memorialService.publish(id, currentUser(authentication).id())));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<MemorialResponse> archive(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(toResponse(memorialService.archive(id, currentUser(authentication).id())));
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<MemorialResponse> restore(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.ok(toResponse(memorialService.restore(id, currentUser(authentication).id())));
    }

    @PostMapping("/{id}/share-events")
    public ApiResponse<ShareEventResponse> recordShareEvent(
            @PathVariable UUID id,
            @Valid @RequestBody ShareEventRequest request,
            Authentication authentication
    ) {
        memorialService.recordShareEvent(id, currentUser(authentication).id(), request.type());
        return ApiResponse.ok(new ShareEventResponse(true));
    }

    @GetMapping("/public/{slug}")
    public ApiResponse<PublicMemorialResponse> getPublic(@PathVariable String slug, HttpServletRequest request) {
        MemorialService.PublicMemorial result = memorialService.getPublic(slug, hasAccess(request, slug));
        memorialService.recordPublicVisit(result.memorial().id(), visitorRateLimiter.fingerprint(request));
        return ApiResponse.ok(new PublicMemorialResponse(
                toResponse(result.memorial()), result.tributes(), result.timelineEntries(), result.letter(),
                result.galleryItems().stream().map(this::toGalleryResponse).toList(), result.lightCount()));
    }

    @PostMapping("/public/{slug}/lights")
    public ApiResponse<LightResponse> addLight(@PathVariable String slug, HttpServletRequest request) {
        visitorRateLimiter.check(request, "light", slug);
        MemorialLightRepository.LightResult result = memorialService.addLight(
                slug, visitorRateLimiter.fingerprint(request), hasAccess(request, slug));
        return ApiResponse.ok(new LightResponse(result.lit(), result.count()));
    }

    @PostMapping("/public/{slug}/tributes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TributeMessage> addTribute(
            @PathVariable String slug,
            @Valid @RequestBody TributeRequest request,
            HttpServletRequest servletRequest
    ) {
        visitorRateLimiter.check(servletRequest, "tribute", slug);
        return ApiResponse.ok(memorialService.addTribute(slug, request.authorName(), request.message(), hasAccess(servletRequest, slug)));
    }

    @PostMapping("/public/{slug}/tributes/{tributeId}/report")
    public ApiResponse<ReportResponse> reportTribute(
            @PathVariable String slug,
            @PathVariable UUID tributeId,
            HttpServletRequest servletRequest
    ) {
        visitorRateLimiter.check(servletRequest, "report", slug);
        memorialService.reportTribute(slug, tributeId, hasAccess(servletRequest, slug));
        return ApiResponse.ok(new ReportResponse(true));
    }

    @PostMapping("/public/{slug}/unlock")
    public ApiResponse<AccessResponse> unlock(
            @PathVariable String slug,
            @Valid @RequestBody AccessCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        visitorRateLimiter.check(servletRequest, "unlock", slug);
        Memorial memorial = memorialService.unlock(slug, request.accessCode());
        MemorialAccess.grant(servletRequest.getSession(true), memorial.id());
        return ApiResponse.ok(new AccessResponse(true));
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        throw new IllegalStateException("受保护的接口缺少当前用户");
    }

    private MemorialRepository.CreateMemorialCommand toCommand(MemorialRequest request) {
        return new MemorialRepository.CreateMemorialCommand(
                request.petName().trim(),
                request.species().trim(),
                request.coverMediaId(),
                blankToNull(request.farewellMessage()),
                blankToNull(request.aboutTa()),
                request.companionStartedOn(),
                request.companionEndedOn(),
                request.visibility(),
                normalizeTheme(request.theme()),
                blankToNull(request.accessCode())
        );
    }

    private String normalizeTheme(String theme) {
        return theme == null || theme.isBlank() ? "NIGHT" : theme;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MemorialRepository.TimelineEntryCommand toTimelineCommand(TimelineEntryRequest request) {
        return new MemorialRepository.TimelineEntryCommand(
                request.mediaId(), request.eventDate(), request.datePrecision(), request.title().trim(), blankToNull(request.body()));
    }

    private MemorialResponse toResponse(Memorial memorial) {
        return new MemorialResponse(
                memorial.id(), memorial.slug(), memorial.petName(), memorial.species(), memorial.coverMediaId(), coverImageUrl(memorial),
                memorial.farewellMessage(), memorial.aboutTa(), memorial.companionStartedOn(), memorial.companionEndedOn(),
                memorial.status(), memorial.visibility(), memorial.theme(), memorial.publishedAt(),
                memorial.createdAt(), memorial.updatedAt(), memorial.version()
        );
    }

    private GalleryResponse toGalleryResponse(GalleryItem item) {
        return new GalleryResponse(item.id(), item.mediaId(), "/api/v1/media/" + item.mediaId() + "/content",
                item.mediaContentType(), item.caption(), item.position(), item.createdAt());
    }

    public record MemorialRequest(
            @NotBlank(message = "请填写 TA 的昵称") @Size(max = 32, message = "昵称最多 32 个字符") String petName,
            @NotBlank(message = "请选择伙伴类型") @Size(max = 32, message = "伙伴类型最多 32 个字符") String species,
            UUID coverMediaId,
            @Size(max = 280, message = "想念的话最多 280 个字符") String farewellMessage,
            @Size(max = 1500, message = "关于 TA 最多 1500 个字符") String aboutTa,
            @PastOrPresent(message = "陪伴开始日期不能晚于今天") java.time.LocalDate companionStartedOn,
            @PastOrPresent(message = "陪伴结束日期不能晚于今天") java.time.LocalDate companionEndedOn,
            @NotBlank(message = "请选择可见范围") @Pattern(regexp = "PUBLIC|LINK|PASSWORD|PRIVATE", message = "可见范围无效") String visibility,
            @Pattern(regexp = "SUNNY|NIGHT|GARDEN|MEADOW|ALBUM|HOME", message = "主题无效") String theme,
            @Size(max = 64, message = "访问口令最多 64 个字符") String accessCode,
            @NotNull(message = "缺少页面版本，请刷新后重试") Integer version
    ) {
    }

    public record TributeRequest(
            @NotBlank(message = "请留下称呼") @Size(max = 32, message = "称呼最多 32 个字符") String authorName,
            @NotBlank(message = "请写下留言") @Size(max = 280, message = "留言最多 280 个字符") String message
    ) {
    }

    public record TributeStatusRequest(
            @NotBlank(message = "请选择留言状态") @Pattern(regexp = "APPROVED|HIDDEN", message = "留言状态无效") String status
    ) {
    }

    public record ReportResponse(boolean reported) {
    }

    public record LightResponse(boolean lit, long count) {
    }

    public record AccessCodeRequest(
            @NotBlank(message = "请输入分享口令") @Size(max = 64, message = "访问口令最多 64 个字符") String accessCode
    ) {
    }

    public record AccessResponse(boolean granted) {
    }

    public record DeleteResponse(boolean deleted) {
    }

    public record ShareEventRequest(
            @NotBlank(message = "请提供分享操作类型")
            @Pattern(regexp = "LINK_COPIED|SHARE_CARD_DOWNLOADED", message = "分享操作类型无效") String type
    ) {
    }

    public record ShareEventResponse(boolean recorded) {
    }

    public record TimelineEntryRequest(
            UUID mediaId,
            java.time.LocalDate eventDate,
            @NotBlank(message = "请选择日期精度") @Pattern(regexp = "DAY|MONTH|YEAR|UNKNOWN", message = "日期精度无效") String datePrecision,
            @NotBlank(message = "请为这段记忆写一个标题") @Size(max = 80, message = "标题最多 80 个字符") String title,
            @Size(max = 1000, message = "内容最多 1000 个字符") String body
    ) {
    }

    public record TimelineOrderRequest(
            @NotNull(message = "请提供时间线排序") @Size(min = 1, message = "时间线至少需要一段记忆") List<UUID> entryIds
    ) {
    }

    public record LetterRequest(
            @Size(max = 80, message = "标题最多 80 个字符") String subject,
            @NotBlank(message = "请写下这封信") @Size(max = 3000, message = "信最多 3000 个字符") String body
    ) {
    }

    public record GalleryItemRequest(
            @NotNull(message = "请选择一份已上传的媒体") UUID mediaId,
            @Size(max = 280, message = "媒体说明最多 280 个字符") String caption
    ) {
    }

    public record GalleryCaptionRequest(@Size(max = 280, message = "媒体说明最多 280 个字符") String caption) {
    }

    public record GalleryOrderRequest(
            @NotNull(message = "请提供相册排序") @Size(min = 1, message = "相册至少需要一张照片") List<UUID> itemIds
    ) {
    }

    public record GalleryResponse(
            UUID id,
            UUID mediaId,
            String mediaUrl,
            String contentType,
            String caption,
            int position,
            java.time.OffsetDateTime createdAt
    ) {
    }

    public record MemorialResponse(
            UUID id,
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
            java.time.OffsetDateTime publishedAt,
            java.time.OffsetDateTime createdAt,
            java.time.OffsetDateTime updatedAt,
            int version
    ) {
    }

    private String coverImageUrl(Memorial memorial) {
        if (memorial.coverMediaId() != null) {
            return "/api/v1/media/" + memorial.coverMediaId() + "/content";
        }
        return memorial.coverImageUrl();
    }

    private boolean hasAccess(HttpServletRequest request, String slug) {
        Memorial memorial = memorialService.findPublished(slug);
        HttpSession session = request.getSession(false);
        return MemorialAccess.isGranted(session, memorial.id());
    }

    public record PublicMemorialResponse(
            MemorialResponse memorial,
            List<TributeMessage> tributes,
            List<TimelineEntry> timelineEntries,
            MemorialLetter letter,
            List<GalleryResponse> galleryItems,
            long lightCount
    ) {
    }
}

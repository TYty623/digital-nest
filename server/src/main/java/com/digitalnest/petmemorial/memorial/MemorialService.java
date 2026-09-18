package com.digitalnest.petmemorial.memorial;

import com.digitalnest.petmemorial.billing.BillingService;
import com.digitalnest.petmemorial.billing.FeatureCode;
import com.digitalnest.petmemorial.media.MediaAsset;
import com.digitalnest.petmemorial.media.MediaService;
import com.digitalnest.petmemorial.shared.error.ApiException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemorialService {

    private static final Set<String> HIGH_RISK_TRIBUTE_TERMS = Set.of("自杀", "杀人", "诈骗", "赌博", "毒品", "裸照");

    private final MemorialRepository memorialRepository;
    private final MediaService mediaService;
    private final PasswordEncoder passwordEncoder;
    private final BillingService billingService;
    private final MemorialLightRepository memorialLightRepository;
    private final MemorialVisitRepository memorialVisitRepository;
    private final MemorialShareRepository memorialShareRepository;

    public MemorialService(
            MemorialRepository memorialRepository,
            MediaService mediaService,
            PasswordEncoder passwordEncoder,
            BillingService billingService,
            MemorialLightRepository memorialLightRepository,
            MemorialVisitRepository memorialVisitRepository,
            MemorialShareRepository memorialShareRepository
    ) {
        this.memorialRepository = memorialRepository;
        this.mediaService = mediaService;
        this.passwordEncoder = passwordEncoder;
        this.billingService = billingService;
        this.memorialLightRepository = memorialLightRepository;
        this.memorialVisitRepository = memorialVisitRepository;
        this.memorialShareRepository = memorialShareRepository;
    }

    @Transactional
    public Memorial create(UUID userId, MemorialRepository.CreateMemorialCommand command) {
        validateCompanionDates(command);
        ensureThemeAllowed(userId, command.theme(), null);
        validateCoverOwnership(userId, command.coverMediaId());
        Memorial memorial = memorialRepository.create(userId, UUID.randomUUID(), nextSlug(command.petName()), command,
                resolveAccessCodeHash(command, null));
        attachCoverToGallery(memorial);
        return memorial;
    }

    public List<Memorial> listMine(UUID userId) {
        return memorialRepository.findAllByUserId(userId);
    }

    public List<TimelineEntry> listTimeline(UUID memorialId, UUID userId) {
        getMine(memorialId, userId);
        return memorialRepository.findTimelineEntries(memorialId);
    }

    @Transactional
    public TimelineEntry addTimelineEntry(UUID memorialId, UUID userId, MemorialRepository.TimelineEntryCommand command) {
        memorialRepository.lockForOwner(memorialId, userId);
        getMine(memorialId, userId);
        if (memorialRepository.findTimelineEntries(memorialId).size() >= billingService.limitsFor(userId).timelineLimit()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_TIMELINE_LIMIT_REACHED", "当前套餐的时间线数量已满，请整理已有内容或升级套餐。" );
        }
        validateTimelineMedia(memorialId, userId, command.mediaId());
        return memorialRepository.addTimelineEntry(memorialId, UUID.randomUUID(), command);
    }

    @Transactional
    public TimelineEntry updateTimelineEntry(
            UUID memorialId,
            UUID entryId,
            UUID userId,
            MemorialRepository.TimelineEntryCommand command
    ) {
        getMine(memorialId, userId);
        TimelineEntry existing = memorialRepository.findTimelineEntry(entryId, memorialId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TIMELINE_ENTRY_NOT_FOUND", "没有找到这条时间线记录。"));
        validateTimelineMedia(memorialId, userId, command.mediaId());
        TimelineEntry updated = memorialRepository.updateTimelineEntry(memorialId, entryId, command)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TIMELINE_ENTRY_NOT_FOUND", "没有找到这条时间线记录。"));
        if (existing.mediaId() != null && !existing.mediaId().equals(updated.mediaId())) {
            mediaService.scheduleUnreferencedForDeletion(existing.mediaId(), userId);
        }
        return updated;
    }

    @Transactional
    public void deleteTimelineEntry(UUID memorialId, UUID entryId, UUID userId) {
        getMine(memorialId, userId);
        TimelineEntry existing = memorialRepository.findTimelineEntry(entryId, memorialId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TIMELINE_ENTRY_NOT_FOUND", "没有找到这条时间线记录。"));
        if (!memorialRepository.deleteTimelineEntry(memorialId, entryId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TIMELINE_ENTRY_NOT_FOUND", "没有找到这条时间线记录。");
        }
        if (existing.mediaId() != null) {
            mediaService.scheduleUnreferencedForDeletion(existing.mediaId(), userId);
        }
    }

    @Transactional
    public List<TimelineEntry> reorderTimeline(UUID memorialId, UUID userId, List<UUID> timelineEntryIds) {
        getMine(memorialId, userId);
        List<TimelineEntry> existing = memorialRepository.findTimelineEntries(memorialId);
        Set<UUID> existingIds = existing.stream().map(TimelineEntry::id).collect(java.util.stream.Collectors.toSet());
        Set<UUID> requestedIds = new HashSet<>(timelineEntryIds);
        if (timelineEntryIds.size() != existingIds.size() || requestedIds.size() != timelineEntryIds.size()
                || !requestedIds.equals(existingIds)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TIMELINE_ORDER_INVALID", "时间线排序必须包含且只能包含当前的每一段记忆。");
        }
        for (int position = 0; position < timelineEntryIds.size(); position++) {
            memorialRepository.updateTimelinePosition(memorialId, timelineEntryIds.get(position), position);
        }
        memorialRepository.touchTimelineOrder(memorialId);
        return memorialRepository.findTimelineEntries(memorialId);
    }

    public Optional<MemorialLetter> getLetter(UUID memorialId, UUID userId) {
        getMine(memorialId, userId);
        return memorialRepository.findLetter(memorialId);
    }

    public MemorialLetter saveLetter(UUID memorialId, UUID userId, String subject, String body) {
        getMine(memorialId, userId);
        return memorialRepository.saveLetter(memorialId, UUID.randomUUID(), subject, body);
    }

    public List<GalleryItem> listGallery(UUID memorialId, UUID userId) {
        getMine(memorialId, userId);
        return memorialRepository.findGalleryItems(memorialId);
    }

    @Transactional
    public GalleryItem addGalleryItem(UUID memorialId, UUID userId, UUID mediaId, String caption) {
        memorialRepository.lockForOwner(memorialId, userId);
        getMine(memorialId, userId);
        MediaAsset asset = mediaService.getForOwner(mediaId, userId);
        if (memorialRepository.hasGalleryMedia(memorialId, mediaId)) {
            throw new ApiException(HttpStatus.CONFLICT, "GALLERY_MEDIA_EXISTS", "这份媒体已经在相册里。" );
        }
        if (asset.isVideo()) {
            ensureShortVideoCapacity(memorialId, userId);
        } else {
            ensurePhotoCapacity(memorialId, userId);
        }
        return memorialRepository.addGalleryItem(memorialId, UUID.randomUUID(), mediaId, caption);
    }

    @Transactional
    public void deleteGalleryItem(UUID memorialId, UUID galleryItemId, UUID userId) {
        Memorial memorial = getMine(memorialId, userId);
        GalleryItem item = memorialRepository.findGalleryItems(memorialId).stream()
                .filter(candidate -> candidate.id().equals(galleryItemId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GALLERY_ITEM_NOT_FOUND", "没有找到这张相册照片。"));
        if (item.mediaId().equals(memorial.coverMediaId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COVER_GALLERY_ITEM_REQUIRED", "封面需要保留在相册中。" );
        }
        if (memorialRepository.hasTimelineMedia(memorialId, item.mediaId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TIMELINE_GALLERY_ITEM_REQUIRED", "这张照片正在时间线中使用；请先在对应时间线中取消关联。" );
        }
        if (!memorialRepository.deleteGalleryItem(memorialId, galleryItemId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "GALLERY_ITEM_NOT_FOUND", "没有找到这张相册照片。" );
        }
        mediaService.scheduleUnreferencedForDeletion(item.mediaId(), userId);
    }

    public GalleryItem updateGalleryCaption(UUID memorialId, UUID galleryItemId, UUID userId, String caption) {
        getMine(memorialId, userId);
        return memorialRepository.updateGalleryCaption(memorialId, galleryItemId, caption)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GALLERY_ITEM_NOT_FOUND", "没有找到这张相册照片。"));
    }

    @Transactional
    public List<GalleryItem> reorderGallery(UUID memorialId, UUID userId, List<UUID> galleryItemIds) {
        getMine(memorialId, userId);
        List<GalleryItem> existing = memorialRepository.findGalleryItems(memorialId);
        Set<UUID> existingIds = existing.stream().map(GalleryItem::id).collect(java.util.stream.Collectors.toSet());
        Set<UUID> requestedIds = new HashSet<>(galleryItemIds);
        if (galleryItemIds.size() != existingIds.size() || requestedIds.size() != galleryItemIds.size()
                || !requestedIds.equals(existingIds)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GALLERY_ORDER_INVALID", "相册排序必须包含且只能包含当前的每一张照片。" );
        }
        for (int position = 0; position < galleryItemIds.size(); position++) {
            memorialRepository.updateGalleryPosition(memorialId, galleryItemIds.get(position), position);
        }
        memorialRepository.touchGalleryOrder(memorialId);
        return memorialRepository.findGalleryItems(memorialId);
    }

    public Memorial getMine(UUID id, UUID userId) {
        return memorialRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMORIAL_NOT_FOUND", "没有找到这个小窝。"));
    }

    @Transactional
    public Memorial update(UUID id, UUID userId, MemorialRepository.CreateMemorialCommand command, int expectedVersion) {
        memorialRepository.lockForOwner(id, userId);
        validateCompanionDates(command);
        Memorial current = getMine(id, userId);
        ensureThemeAllowed(userId, command.theme(), current.theme());
        validateCoverOwnership(userId, command.coverMediaId());
        if (command.coverMediaId() != null && !memorialRepository.hasGalleryMedia(id, command.coverMediaId())) {
            ensurePhotoCapacity(id, userId);
        }
        Memorial updated = memorialRepository.update(id, userId, command, resolveAccessCodeHash(command, current), expectedVersion)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "MEMORIAL_VERSION_CONFLICT", "这间小窝刚刚被其他编辑更新，请刷新后再保存。"));
        attachCoverToGallery(updated);
        // A private page must never remain marked as published: editor auto-save can change visibility before the owner
        // presses an explicit publication control. Archiving makes that transition atomic and keeps the UI state truthful.
        if ("PUBLISHED".equals(updated.status()) && "PRIVATE".equals(updated.visibility())) {
            return memorialRepository.archive(id, userId);
        }
        return updated;
    }

    public Memorial publish(UUID id, UUID userId) {
        Memorial memorial = getMine(id, userId);
        validatePublishable(memorial);
        return memorialRepository.publish(id, userId);
    }

    public Memorial archive(UUID id, UUID userId) {
        Memorial memorial = getMine(id, userId);
        if (!"PUBLISHED".equals(memorial.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMORIAL_NOT_PUBLISHED", "只有已发布的小窝可以暂时下线。" );
        }
        return memorialRepository.archive(id, userId);
    }

    public Memorial restore(UUID id, UUID userId) {
        Memorial memorial = getMine(id, userId);
        if (!"ARCHIVED".equals(memorial.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMORIAL_NOT_ARCHIVED", "这间小窝当前不在下线状态。" );
        }
        validatePublishable(memorial);
        return memorialRepository.restore(id, userId);
    }

    private void validatePublishable(Memorial memorial) {
        if ("PRIVATE".equals(memorial.visibility())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SHARE_VISIBILITY_REQUIRED", "发布前，请选择公开、链接访问或访问口令。");
        }
        int photoCount = memorialRepository.countGalleryItemsByContentTypePrefix(memorial.id(), "image/");
        if (memorial.coverMediaId() == null || photoCount < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PUBLISH_CONTENT_INCOMPLETE", "发布前请准备封面和至少 3 张照片。");
        }
        BillingService.AccountLimits limits = billingService.limitsFor(memorial.userId());
        int shortVideoCount = memorialRepository.countGalleryItemsByContentTypePrefix(memorial.id(), "video/");
        if (photoCount > limits.photoLimit()
                || shortVideoCount > limits.shortVideoLimit()
                || memorialRepository.findTimelineEntries(memorial.id()).size() > limits.timelineLimit()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_LIMIT_EXCEEDED", "当前内容超过套餐容量，请整理内容或升级套餐后再发布。" );
        }
    }

    private void validateTimelineMedia(UUID memorialId, UUID userId, UUID mediaId) {
        if (mediaId == null) {
            return;
        }
        MediaAsset asset = mediaService.getForOwner(mediaId, userId);
        if (!asset.isImage()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TIMELINE_IMAGE_REQUIRED", "时间线只能关联相册中的图片。");
        }
        if (!memorialRepository.hasGalleryMedia(memorialId, mediaId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TIMELINE_GALLERY_IMAGE_REQUIRED", "请先将这张图片加入当前小窝的相册，再关联到时间线。");
        }
    }

    public PublicMemorial getPublic(String slug, boolean passwordGranted) {
        Memorial memorial = findPublished(slug);
        if ("PASSWORD".equals(memorial.visibility()) && !passwordGranted) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MEMORIAL_ACCESS_CODE_REQUIRED", "请输入分享口令后继续查看。");
        }
        return new PublicMemorial(memorial, memorialRepository.findTributes(memorial.id()),
                memorialRepository.findTimelineEntries(memorial.id()), memorialRepository.findLetter(memorial.id()).orElse(null),
                memorialRepository.findGalleryItems(memorial.id()), memorialLightRepository.countByMemorialId(memorial.id()));
    }

    public void recordPublicVisit(UUID memorialId, String visitorFingerprint) {
        memorialVisitRepository.recordDailyUniqueVisit(memorialId, visitorFingerprint, LocalDate.now());
    }

    public void recordShareEvent(UUID memorialId, UUID userId, String eventType) {
        Memorial memorial = getMine(memorialId, userId);
        if (!"PUBLISHED".equals(memorial.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMORIAL_NOT_PUBLISHED", "发布后才能记录分享操作。" );
        }
        memorialShareRepository.record(memorialId, userId, eventType);
    }

    public MemorialLightRepository.LightResult addLight(String slug, String visitorFingerprint, boolean passwordGranted) {
        Memorial memorial = findPublished(slug);
        if ("PASSWORD".equals(memorial.visibility()) && !passwordGranted) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MEMORIAL_ACCESS_CODE_REQUIRED", "请先输入分享口令。");
        }
        return memorialLightRepository.addLight(memorial.id(), visitorFingerprint, LocalDate.now());
    }

    public TributeMessage addTribute(String slug, String authorName, String message, boolean passwordGranted) {
        Memorial memorial = findPublished(slug);
        if ("PASSWORD".equals(memorial.visibility()) && !passwordGranted) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MEMORIAL_ACCESS_CODE_REQUIRED", "请先输入分享口令。");
        }
        String normalizedMessage = message.trim();
        String status = needsOwnerReview(normalizedMessage) ? "PENDING" : "APPROVED";
        return memorialRepository.addTribute(memorial.id(), UUID.randomUUID(), authorName.trim(), normalizedMessage, status);
    }

    public List<TributeMessage> listTributes(UUID memorialId, UUID userId) {
        getMine(memorialId, userId);
        return memorialRepository.findAllTributes(memorialId);
    }

    public TributeMessage updateTributeStatus(UUID memorialId, UUID tributeId, UUID userId, String status) {
        getMine(memorialId, userId);
        if (!"APPROVED".equals(status) && !"HIDDEN".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TRIBUTE_STATUS_INVALID", "留言只能显示或隐藏。");
        }
        return memorialRepository.updateTributeStatus(memorialId, tributeId, status)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRIBUTE_NOT_FOUND", "没有找到这条留言。"));
    }

    public void reportTribute(String slug, UUID tributeId, boolean passwordGranted) {
        Memorial memorial = findPublished(slug);
        if ("PASSWORD".equals(memorial.visibility()) && !passwordGranted) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MEMORIAL_ACCESS_CODE_REQUIRED", "请先输入分享口令。");
        }
        if (!memorialRepository.reportTribute(memorial.id(), tributeId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TRIBUTE_NOT_FOUND", "没有找到可举报的留言。" );
        }
    }

    public Memorial unlock(String slug, String accessCode) {
        Memorial memorial = findPublished(slug);
        if (!"PASSWORD".equals(memorial.visibility()) || memorial.accessCodeHash() == null
                || !passwordEncoder.matches(accessCode.trim(), memorial.accessCodeHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MEMORIAL_ACCESS_CODE_INVALID", "分享口令不正确，请再试一次。");
        }
        return memorial;
    }

    public Memorial findPublished(String slug) {
        return memorialRepository.findPublishedBySlug(slug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEMORIAL_NOT_FOUND", "这个纪念页不存在，或尚未公开。"));
    }

    public List<CommunityMemorial> discoverPublic(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 48));
        return memorialRepository.discoverPublic(limit).stream()
                .map(item -> new CommunityMemorial(item.id(), item.slug(), item.petName(), item.species(),
                        item.coverImageUrl(), item.companionStartedOn(), item.companionEndedOn(),
                        abbreviate(item.signature(), 90), item.lightCount(), item.publishedAt()))
                .toList();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 1) + "…";
    }

    private boolean needsOwnerReview(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return HIGH_RISK_TRIBUTE_TERMS.stream().anyMatch(normalized::contains);
    }

    private String nextSlug(String petName) {
        return "m-" + UUID.randomUUID();
    }

    private void validateCoverOwnership(UUID userId, UUID coverMediaId) {
        if (coverMediaId != null) {
            MediaAsset asset = mediaService.getForOwner(coverMediaId, userId);
            if (!asset.isImage()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "COVER_IMAGE_REQUIRED", "封面只能使用 JPG、PNG 或 WebP 图片。");
            }
        }
    }

    private void validateCompanionDates(MemorialRepository.CreateMemorialCommand command) {
        if (command.companionStartedOn() != null && command.companionEndedOn() != null
                && command.companionEndedOn().isBefore(command.companionStartedOn())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPANION_DATES_INVALID", "陪伴结束日期不能早于开始日期。" );
        }
    }

    /**
     * A saved theme is an appearance selection only: it cannot restrict access to
     * memories. The API remains the authority so a crafted browser request cannot
     * activate a premium appearance package without an active entitlement. Existing
     * selections remain editable for backwards compatibility.
     */
    private void ensureThemeAllowed(UUID userId, String requestedTheme, String currentTheme) {
        if ("NIGHT".equals(requestedTheme) || requestedTheme.equals(currentTheme)) {
            return;
        }
        if (!billingService.hasCapability(userId, FeatureCode.PREMIUM_APPEARANCE_PACK)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PREMIUM_APPEARANCE_REQUIRED",
                    "这个外观包需要开通后使用；已有纪念内容不会受到影响。");
        }
    }

    private void attachCoverToGallery(Memorial memorial) {
        if (memorial.coverMediaId() != null && !memorialRepository.hasGalleryMedia(memorial.id(), memorial.coverMediaId())) {
            ensurePhotoCapacity(memorial.id(), memorial.userId());
            memorialRepository.addGalleryItem(memorial.id(), UUID.randomUUID(), memorial.coverMediaId(), null);
        }
    }

    private void ensurePhotoCapacity(UUID memorialId, UUID userId) {
        if (memorialRepository.countGalleryItemsByContentTypePrefix(memorialId, "image/") >= billingService.limitsFor(userId).photoLimit()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_PHOTO_LIMIT_REACHED", "当前套餐的照片数量已满，请整理已有内容或升级套餐。" );
        }
    }

    private void ensureShortVideoCapacity(UUID memorialId, UUID userId) {
        if (memorialRepository.countGalleryItemsByContentTypePrefix(memorialId, "video/")
                >= billingService.limitsFor(userId).shortVideoLimit()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PLAN_SHORT_VIDEO_LIMIT_REACHED", "当前套餐的短视频数量已满，请整理已有内容或升级套餐。");
        }
    }

    private String resolveAccessCodeHash(MemorialRepository.CreateMemorialCommand command, Memorial current) {
        if (!"PASSWORD".equals(command.visibility())) {
            return null;
        }
        String accessCode = Optional.ofNullable(command.accessCode()).map(String::trim).orElse("");
        if (accessCode.isEmpty() && current != null && "PASSWORD".equals(current.visibility())) {
            return current.accessCodeHash();
        }
        if (accessCode.length() < 6 || accessCode.length() > 64) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ACCESS_CODE_INVALID", "访问口令需要 6 至 64 个字符。");
        }
        return passwordEncoder.encode(accessCode);
    }

    public record PublicMemorial(
            Memorial memorial,
            List<TributeMessage> tributes,
            List<TimelineEntry> timelineEntries,
            MemorialLetter letter,
            List<GalleryItem> galleryItems,
            long lightCount
    ) {
    }
}

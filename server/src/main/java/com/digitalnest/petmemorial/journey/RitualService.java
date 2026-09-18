package com.digitalnest.petmemorial.journey;

import com.digitalnest.petmemorial.billing.BillingService;
import com.digitalnest.petmemorial.billing.FeatureCode;
import com.digitalnest.petmemorial.media.MediaService;
import com.digitalnest.petmemorial.memorial.Memorial;
import com.digitalnest.petmemorial.memorial.MemorialRepository;
import com.digitalnest.petmemorial.memorial.MemorialService;
import com.digitalnest.petmemorial.shared.error.ApiException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RitualService {
    private static final Set<String> ARCHIVE_TYPES = Set.of("PLACE", "RELATION", "OBJECT", "MOMENT", "MEDIA");
    private static final Set<String> VERIFICATION = Set.of("CONFIRMED", "PENDING");
    private static final Set<String> ARCHIVE_VISIBILITY = Set.of("PRIVATE", "FAMILY");
    private static final Set<String> ANNIVERSARY_TYPES = Set.of("BIRTHDAY", "MEETING", "ADOPTION", "FAREWELL", "SEASON", "CUSTOM");
    private static final Set<String> REPEAT_RULES = Set.of("ANNUAL", "ONCE", "OFF");
    private static final Set<String> RITUAL_TYPES = Set.of("FIRST_HOME", "BIRTHDAY", "ADOPTION", "FAREWELL", "SEASON", "CUSTOM", "FAMILY");
    private static final Set<String> RITUAL_ACTIONS = Set.of("LIGHT", "FLOWER", "LETTER", "SOUND", "CAPSULE");
    private static final Pattern SENSITIVE = Pattern.compile("(\\d{6,}|\\d+\\s*[号弄栋室]|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+|1[3-9]\\d{9}|经度|纬度)");
    private static final Pattern HIGH_RISK = Pattern.compile("(自杀|轻生|自残|结束生命)");

    private final RitualRepository repository;
    private final MemorialService memorialService;
    private final MemorialRepository memorialRepository;
    private final MediaService mediaService;
    private final BillingService billingService;

    public RitualService(RitualRepository repository, MemorialService memorialService, MemorialRepository memorialRepository,
                         MediaService mediaService, BillingService billingService) {
        this.repository = repository;
        this.memorialService = memorialService;
        this.memorialRepository = memorialRepository;
        this.mediaService = mediaService;
        this.billingService = billingService;
    }

    public RitualSpace space(UUID memorialId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        return new RitualSpace(repository.archiveEntries(memorialId), repository.anniversaries(memorialId),
                repository.capsules(memorialId).stream().map(this::withUnlockStatus).toList(), repository.rituals(memorialId));
    }

    @Transactional
    public MemorialArchiveEntry createArchive(UUID memorialId, UUID userId, RitualRepository.ArchiveCommand command) {
        memorialService.getMine(memorialId, userId);
        if (repository.archiveEntries(memorialId).size() >= 60) {
            throw bad("ARCHIVE_ENTRY_LIMIT_REACHED", "生命档案最多保存 60 条，请先整理不再需要的记录。");
        }
        validateArchive(userId, command);
        return repository.createArchive(memorialId, UUID.randomUUID(), normalizeArchive(command));
    }

    @Transactional
    public MemorialArchiveEntry updateArchive(UUID memorialId, UUID entryId, UUID userId, RitualRepository.ArchiveCommand command) {
        memorialService.getMine(memorialId, userId);
        validateArchive(userId, command);
        return repository.updateArchive(memorialId, entryId, normalizeArchive(command))
                .orElseThrow(() -> notFound("ARCHIVE_ENTRY_NOT_FOUND", "没有找到这条生命档案。"));
    }

    @Transactional
    public void deleteArchive(UUID memorialId, UUID entryId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        if (!repository.deleteArchive(memorialId, entryId)) throw notFound("ARCHIVE_ENTRY_NOT_FOUND", "没有找到这条生命档案。");
    }

    @Transactional
    public MemorialAnniversary createAnniversary(UUID memorialId, UUID userId, RitualRepository.AnniversaryCommand command) {
        memorialService.getMine(memorialId, userId);
        if (repository.anniversaries(memorialId).size() >= 18) throw bad("ANNIVERSARY_LIMIT_REACHED", "纪念日最多保存 18 个。");
        validateAnniversary(command);
        return repository.createAnniversary(memorialId, UUID.randomUUID(), normalizeAnniversary(command));
    }

    @Transactional
    public MemorialAnniversary updateAnniversary(UUID memorialId, UUID anniversaryId, UUID userId, RitualRepository.AnniversaryCommand command) {
        memorialService.getMine(memorialId, userId);
        validateAnniversary(command);
        return repository.updateAnniversary(memorialId, anniversaryId, normalizeAnniversary(command))
                .orElseThrow(() -> notFound("ANNIVERSARY_NOT_FOUND", "没有找到这个纪念日。"));
    }

    @Transactional
    public void deleteAnniversary(UUID memorialId, UUID anniversaryId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        if (!repository.deleteAnniversary(memorialId, anniversaryId)) throw notFound("ANNIVERSARY_NOT_FOUND", "没有找到这个纪念日。");
    }

    @Transactional
    public MemoryCapsule createCapsule(UUID memorialId, UUID userId, RitualRepository.CapsuleCommand command) {
        memorialService.getMine(memorialId, userId);
        if (repository.countCapsules(memorialId) >= 1 && !billingService.hasCapability(userId, FeatureCode.MEMORY_CAPSULE)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MEMORY_CAPSULE_PREMIUM_REQUIRED", "免费纪念空间可保存 1 个时间胶囊；多个胶囊属于高级会员权益。");
        }
        if (command.unlockOn() == null || !command.unlockOn().isAfter(LocalDate.now())) {
            throw bad("CAPSULE_UNLOCK_DATE_INVALID", "时间胶囊请设置在明天或更晚开启。 ");
        }
        if (!ARCHIVE_VISIBILITY.contains(command.visibility())) throw bad("CAPSULE_VISIBILITY_INVALID", "时间胶囊可见范围无效。");
        if (command.mediaId() != null) mediaService.getForOwner(command.mediaId(), userId);
        checkSafeText(command.title(), command.body());
        return withUnlockStatus(repository.createCapsule(memorialId, UUID.randomUUID(), new RitualRepository.CapsuleCommand(
                command.title().trim(), command.body().trim(), command.mediaId(), command.unlockOn(), command.visibility())));
    }

    @Transactional
    public MemoryCapsule openCapsule(UUID memorialId, UUID capsuleId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        MemoryCapsule capsule = repository.capsule(memorialId, capsuleId)
                .orElseThrow(() -> notFound("CAPSULE_NOT_FOUND", "没有找到这个时间胶囊。"));
        if (capsule.unlockOn().isAfter(LocalDate.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "CAPSULE_NOT_READY", "这封时间胶囊还没有到开启的日期。 ");
        }
        repository.markCapsuleOpened(memorialId, capsuleId);
        return withUnlockStatus(repository.capsule(memorialId, capsuleId).orElseThrow());
    }

    @Transactional
    public void deleteCapsule(UUID memorialId, UUID capsuleId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        if (!repository.deleteCapsule(memorialId, capsuleId)) throw notFound("CAPSULE_NOT_FOUND", "没有找到这个时间胶囊。");
    }

    @Transactional
    public RitualRecord completeRitual(UUID memorialId, UUID userId, RitualRepository.RitualCommand command) {
        memorialService.getMine(memorialId, userId);
        if (!RITUAL_TYPES.contains(command.ritualType()) || !RITUAL_ACTIONS.contains(command.ritualAction())) {
            throw bad("RITUAL_TYPE_INVALID", "仪式类型或纪念动作无效。 ");
        }
        checkSafeText(command.note());
        return repository.createRitual(memorialId, UUID.randomUUID(), new RitualRepository.RitualCommand(
                command.ritualType(), command.ritualAction(), blankToNull(command.note()), command.ambientEnabled()));
    }

    public YearbookPreview yearbook(UUID memorialId, UUID userId, int year) {
        Memorial memorial = memorialService.getMine(memorialId, userId);
        int selectedYear = year <= 0 ? LocalDate.now().getYear() : year;
        if (selectedYear < 2000 || selectedYear > LocalDate.now().getYear()) throw bad("YEARBOOK_YEAR_INVALID", "只能整理今年或过去的年度纪念册。 ");
        List<YearbookPreview.YearbookChapter> chapters = new java.util.ArrayList<>();
        repository.archiveEntries(memorialId).stream()
                .filter(item -> item.eventDate() == null || item.eventDate().getYear() == selectedYear)
                .forEach(item -> chapters.add(new YearbookPreview.YearbookChapter("MEMORY", item.title(),
                        item.body() == null ? "来自生命档案的生活细节" : item.body(), item.eventDate() == null ? "" : item.eventDate().toString())));
        repository.rituals(memorialId).stream()
                .filter(item -> item.completedAt().getYear() == selectedYear)
                .forEach(item -> chapters.add(new YearbookPreview.YearbookChapter("RITUAL", ritualTitle(item),
                        item.note() == null ? "完成了一次安静的纪念仪式。" : item.note(), item.completedAt().toLocalDate().toString())));
        memorialRepository.findTimelineEntries(memorialId).stream()
                .filter(item -> item.eventDate() != null && item.eventDate().getYear() == selectedYear)
                .forEach(item -> chapters.add(new YearbookPreview.YearbookChapter("TIMELINE", item.title(),
                        item.body() == null ? "来自 TA 的时间线。" : item.body(), item.eventDate().toString())));
        boolean exportAvailable = billingService.hasCapability(userId, FeatureCode.YEARBOOK_EXPORT);
        return new YearbookPreview(memorial.petName(), selectedYear, List.copyOf(chapters), exportAvailable,
                exportAvailable ? "可以在浏览器中打印或存为 PDF。" : "可以免费预览；高清打印和历史版本属于高级会员权益。");
    }

    private String ritualTitle(RitualRecord ritual) {
        return switch (ritual.ritualAction()) {
            case "LIGHT" -> "点亮一盏记忆灯";
            case "FLOWER" -> "种下一朵纪念花";
            case "LETTER" -> "写下一封想念的信";
            case "SOUND" -> "听了一段熟悉的声音";
            default -> "收进一段时间胶囊";
        };
    }

    private void validateArchive(UUID userId, RitualRepository.ArchiveCommand command) {
        if (!ARCHIVE_TYPES.contains(command.entryType()) || !VERIFICATION.contains(command.verificationStatus())
                || !ARCHIVE_VISIBILITY.contains(command.visibility())) throw bad("ARCHIVE_ENTRY_INVALID", "生命档案的类型、确认状态或可见范围无效。 ");
        if (command.eventDate() != null && command.eventDate().isAfter(LocalDate.now())) throw bad("ARCHIVE_EVENT_DATE_INVALID", "生命档案中的日期不能晚于今天。 ");
        if (command.mediaId() != null) mediaService.getForOwner(command.mediaId(), userId);
        checkSafeText(command.title(), command.body(), command.placeLabel(), command.sourceLabel());
    }

    private void validateAnniversary(RitualRepository.AnniversaryCommand command) {
        if (!ANNIVERSARY_TYPES.contains(command.anniversaryType()) || !REPEAT_RULES.contains(command.repeatRule())) {
            throw bad("ANNIVERSARY_INVALID", "纪念日类型或重复方式无效。 ");
        }
        if (command.eventDate() == null || command.eventDate().isAfter(LocalDate.now())) {
            throw bad("ANNIVERSARY_DATE_INVALID", "纪念日需要使用已经发生过的日期。 ");
        }
        checkSafeText(command.title());
    }

    private RitualRepository.ArchiveCommand normalizeArchive(RitualRepository.ArchiveCommand command) {
        return new RitualRepository.ArchiveCommand(command.entryType(), command.title().trim(), blankToNull(command.body()),
                command.eventDate(), blankToNull(command.placeLabel()), blankToNull(command.sourceLabel()), command.verificationStatus(),
                command.visibility(), command.mediaId());
    }

    private RitualRepository.AnniversaryCommand normalizeAnniversary(RitualRepository.AnniversaryCommand command) {
        return new RitualRepository.AnniversaryCommand(command.anniversaryType(), command.title().trim(), command.eventDate(),
                command.repeatRule(), command.reminderEnabled());
    }

    private MemoryCapsule withUnlockStatus(MemoryCapsule capsule) {
        return new MemoryCapsule(capsule.id(), capsule.memorialId(), capsule.title(), capsule.body(), capsule.mediaId(), capsule.mediaUrl(),
                capsule.unlockOn(), capsule.visibility(), !capsule.unlockOn().isAfter(LocalDate.now()), capsule.openedAt(), capsule.createdAt());
    }

    private void checkSafeText(String... values) {
        String joined = String.join(" ", java.util.Arrays.stream(values).filter(value -> value != null).toList());
        if (SENSITIVE.matcher(joined).find()) throw bad("PRIVATE_INFORMATION_DETECTED", "请不要在纪念内容中写入详细住址、联系方式、证件号或经纬度。 ");
        if (HIGH_RISK.matcher(joined).find()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "HIGH_RISK_SUPPORT_REQUIRED",
                "这段内容似乎包含令人担心的自伤表达。请先联系身边可信赖的人或当地紧急支持资源；此处暂不保存该内容。 ");
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ApiException bad(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    private ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }
}

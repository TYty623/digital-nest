package com.digitalnest.petmemorial.memorial;

import com.digitalnest.petmemorial.admin.AdminRepository;
import com.digitalnest.petmemorial.media.MediaAsset;
import com.digitalnest.petmemorial.media.MediaService;
import com.digitalnest.petmemorial.shared.error.ApiException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemorialExperienceService {
    private static final Set<String> PROMPTS = Set.of("FIRST_MEETING", "NICKNAME", "HABIT", "HAPPIEST", "TAUGHT_ME", "LAST_WORDS");
    private static final Set<String> DISPOSITIONS = Set.of("PROFESSIONAL_HARMLESS", "CREMATION", "ASHES_KEPT", "ASHES_PLACED", "OTHER_LAWFUL");
    private static final Set<String> LIFE_DETAIL_KEYS = Set.of(
            "NICKNAMES", "FAVORITE_FOOD", "FAVORITE_SPOT", "QUIRK", "FEAR",
            "COMES_RUNNING_FOR", "FAVORITE_PERSON", "FAMILIAR_SOUND", "SPECIAL_MARK", "TAUGHT_ME");
    private static final Pattern DAY_TIME = Pattern.compile("(?:[01]\\d|2[0-3]):[0-5]\\d");
    private static final Pattern SENSITIVE = Pattern.compile("(\\d{6,}|\\d+\\s*[号弄栋室]|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+|1[3-9]\\d{9}|经度|纬度)");
    private static final Pattern TRANSACTION = Pattern.compile("(出售|收购|代运|代办墓地|遗体交易|微信联系|加微|报价)");

    private final MemorialExperienceRepository repository;
    private final MemorialService memorialService;
    private final MemorialRepository memorialRepository;
    private final MediaService mediaService;
    private final AdminRepository adminRepository;

    public MemorialExperienceService(MemorialExperienceRepository repository, MemorialService memorialService,
                                     MemorialRepository memorialRepository, MediaService mediaService,
                                     AdminRepository adminRepository) {
        this.repository = repository;
        this.memorialService = memorialService;
        this.memorialRepository = memorialRepository;
        this.mediaService = mediaService;
        this.adminRepository = adminRepository;
    }

    public MemorialExperience ownerExperience(UUID memorialId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        return bundle(memorialId, false);
    }

    public MemorialExperience publicExperience(UUID memorialId) { return bundle(memorialId, true); }

    private MemorialExperience bundle(UUID memorialId, boolean publicView) {
        return new MemorialExperience(repository.dayMoments(memorialId), repository.lifeDetails(memorialId),
                repository.sounds(memorialId), repository.interviews(memorialId),
                repository.keepsakes(memorialId), repository.burial(memorialId)
                .filter(record -> !publicView || "APPROVED".equals(record.reviewStatus())).orElse(null));
    }

    @Transactional
    public MemorialExperience.DayMoment addDayMoment(UUID memorialId, UUID userId, String momentTime,
                                                      String title, String placeName, String story, UUID mediaId) {
        memorialService.getMine(memorialId, userId);
        if (repository.dayMoments(memorialId).size() >= 12) {
            throw bad("DAY_MOMENT_LIMIT_REACHED", "TA 的一天最多保存 12 个生活片段。");
        }
        validateDayMoment(memorialId, userId, momentTime, mediaId);
        return repository.addDayMoment(memorialId, UUID.randomUUID(), momentTime, title.trim(),
                clean(placeName), clean(story), mediaId);
    }

    @Transactional
    public MemorialExperience.DayMoment updateDayMoment(UUID memorialId, UUID momentId, UUID userId,
                                                         String momentTime, String title, String placeName,
                                                         String story, UUID mediaId) {
        memorialService.getMine(memorialId, userId);
        validateDayMoment(memorialId, userId, momentTime, mediaId);
        return repository.updateDayMoment(memorialId, momentId, momentTime, title.trim(), clean(placeName),
                        clean(story), mediaId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DAY_MOMENT_NOT_FOUND", "没有找到这个生活片段。"));
    }

    @Transactional
    public void deleteDayMoment(UUID memorialId, UUID momentId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        if (!repository.deleteDayMoment(memorialId, momentId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DAY_MOMENT_NOT_FOUND", "没有找到这个生活片段。");
        }
    }

    @Transactional
    public MemorialExperience saveLifeDetail(UUID memorialId, UUID userId, String detailKey, String answer) {
        memorialService.getMine(memorialId, userId);
        if (!LIFE_DETAIL_KEYS.contains(detailKey)) {
            throw bad("LIFE_DETAIL_KEY_INVALID", "生命指纹项目无效。");
        }
        String normalized = clean(answer);
        if (normalized == null) repository.deleteLifeDetail(memorialId, detailKey);
        else repository.saveLifeDetail(memorialId, UUID.randomUUID(), detailKey, normalized);
        return ownerExperience(memorialId, userId);
    }

    private void validateDayMoment(UUID memorialId, UUID userId, String momentTime, UUID mediaId) {
        if (!DAY_TIME.matcher(momentTime).matches()) {
            throw bad("DAY_MOMENT_TIME_INVALID", "请选择一天中的有效时间。");
        }
        if (mediaId == null) return;
        MediaAsset asset = mediaService.getForOwner(mediaId, userId);
        if (!asset.isImage() || !memorialRepository.hasGalleryMedia(memorialId, mediaId)) {
            throw bad("DAY_MOMENT_GALLERY_IMAGE_REQUIRED", "生活片段只能使用当前相册中的照片。");
        }
    }

    @Transactional
    public MemorialExperience.SoundMemory addSound(UUID memorialId, UUID userId, UUID mediaId, String title, String story) {
        memorialService.getMine(memorialId, userId);
        if (repository.sounds(memorialId).size() >= 12) throw bad("SOUND_LIMIT_REACHED", "声音记忆最多保存 12 条。");
        MediaAsset asset = mediaService.getForOwner(mediaId, userId);
        if (!asset.isAudio()) throw bad("AUDIO_REQUIRED", "请选择一份 MP3 或 WAV 声音文件。");
        return repository.addSound(memorialId, UUID.randomUUID(), mediaId, title.trim(), clean(story));
    }

    @Transactional
    public void deleteSound(UUID memorialId, UUID soundId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        UUID mediaId = repository.deleteSound(memorialId, soundId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SOUND_NOT_FOUND", "没有找到这段声音。"));
        mediaService.scheduleUnreferencedForDeletion(mediaId, userId);
    }

    @Transactional
    public MemorialExperience saveInterview(UUID memorialId, UUID userId, String promptKey, String answer) {
        memorialService.getMine(memorialId, userId);
        if (!PROMPTS.contains(promptKey)) throw bad("INTERVIEW_PROMPT_INVALID", "回忆问题无效。");
        String normalized = clean(answer);
        if (normalized == null) repository.deleteInterview(memorialId, promptKey);
        else repository.saveInterview(memorialId, UUID.randomUUID(), promptKey, normalized);
        return ownerExperience(memorialId, userId);
    }

    public MemorialExperience.Keepsake addKeepsake(UUID memorialId, UUID userId, String title, String story) {
        memorialService.getMine(memorialId, userId);
        if (repository.keepsakes(memorialId).size() >= 20) throw bad("KEEPSAKE_LIMIT_REACHED", "旧物故事最多保存 20 条。");
        return repository.addKeepsake(memorialId, UUID.randomUUID(), title.trim(), story.trim());
    }

    @Transactional
    public void deleteKeepsake(UUID memorialId, UUID keepsakeId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        if (!repository.deleteKeepsake(memorialId, keepsakeId))
            throw new ApiException(HttpStatus.NOT_FOUND, "KEEPSAKE_NOT_FOUND", "没有找到这件旧物。" );
    }

    public MemorialExperience.BurialRecord saveBurial(UUID memorialId, UUID userId, String disposition, LocalDate occurredOn,
                                                       String region, String placeName, String text) {
        memorialService.getMine(memorialId, userId);
        if (!DISPOSITIONS.contains(disposition)) throw bad("BURIAL_DISPOSITION_INVALID", "请选择有效的归处记录方式。");
        if (occurredOn != null && occurredOn.isAfter(LocalDate.now())) throw bad("BURIAL_DATE_INVALID", "记录日期不能晚于今天。");
        String combined = String.join(" ", safe(region), safe(placeName), safe(text));
        if (SENSITIVE.matcher(combined).find()) throw bad("BURIAL_SENSITIVE_INFORMATION", "请移除详细门牌、联系方式、证件号或经纬度，只保留省市级地区。" );
        if (TRANSACTION.matcher(combined.toLowerCase(Locale.ROOT)).find()) throw bad("BURIAL_TRANSACTION_CONTENT", "安葬档案不支持遗体、墓地或运输交易信息。" );
        return repository.saveBurial(memorialId, UUID.randomUUID(), disposition, occurredOn, clean(region), clean(placeName), clean(text));
    }

    public MemorialExperience.BurialRecord submitBurial(UUID memorialId, UUID userId, boolean attested) {
        memorialService.getMine(memorialId, userId);
        if (!attested) throw bad("BURIAL_ATTESTATION_REQUIRED", "请确认记录真实，并已按当地要求妥善处理后再申请公开。" );
        if (!repository.submitBurial(memorialId)) throw bad("BURIAL_NOT_SUBMITTABLE", "当前归处记录无法重复提交审核。" );
        return repository.burial(memorialId).orElseThrow();
    }

    public java.util.List<MemorialExperience.BurialRecord> reviewQueue(String status) {
        if (!Set.of("PENDING", "APPROVED", "REJECTED", "ALL").contains(status)) throw bad("BURIAL_REVIEW_FILTER_INVALID", "审核筛选条件无效。" );
        return repository.reviewQueue(status);
    }

    @Transactional
    public MemorialExperience.BurialRecord moderate(UUID id, String status, String note, UUID reviewer) {
        if (!Set.of("APPROVED", "REJECTED").contains(status)) throw bad("BURIAL_REVIEW_STATUS_INVALID", "审核结果无效。" );
        MemorialExperience.BurialRecord record = repository.moderateBurial(id, status, note.trim(), reviewer)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "BURIAL_REVIEW_NOT_PENDING", "记录不存在或已经完成审核。"));
        adminRepository.createAuditLog(UUID.randomUUID(), reviewer, "APPROVED".equals(status) ? "BURIAL_APPROVED" : "BURIAL_REJECTED",
                "BURIAL_RECORD", id, note.trim(), "memorial=" + record.memorialId() + "; status=" + status);
        return record;
    }

    private ApiException bad(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String safe(String value) { return value == null ? "" : value; }
}

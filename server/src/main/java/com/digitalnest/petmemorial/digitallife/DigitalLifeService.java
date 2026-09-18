package com.digitalnest.petmemorial.digitallife;

import com.digitalnest.petmemorial.billing.BillingService;
import com.digitalnest.petmemorial.billing.FeatureCode;
import com.digitalnest.petmemorial.memorial.MemorialExperience;
import com.digitalnest.petmemorial.memorial.MemorialExperienceRepository;
import com.digitalnest.petmemorial.memorial.MemorialService;
import com.digitalnest.petmemorial.shared.error.ApiException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DigitalLifeService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "PAUSED", "ARCHIVED");
    private static final Set<String> FACT_TYPES = Set.of("TRAIT", "HABIT", "RELATION", "EVENT", "PREFERENCE");
    private static final Set<String> SOURCE_TYPES = Set.of("LIFE_DETAIL", "ARCHIVE_ENTRY", "TIMELINE", "USER_NOTE");
    private static final Set<String> FACT_STATUSES = Set.of("PENDING", "CONFIRMED", "REJECTED");
    private static final Pattern DISALLOWED_PERSONA = Pattern.compile("(我是.*[小它TA]|我回来了|灵魂|复活|意识延续)");

    private final DigitalLifeRepository repository;
    private final MemorialService memorialService;
    private final MemorialExperienceRepository experienceRepository;
    private final BillingService billingService;
    private final MemoryAnswerProvider answerProvider;
    private final boolean mockQaEnabled;

    public DigitalLifeService(DigitalLifeRepository repository, MemorialService memorialService,
                              MemorialExperienceRepository experienceRepository, BillingService billingService,
                              MemoryAnswerProvider answerProvider,
                              @Value("${app.features.digital-life-mock-enabled:true}") boolean mockQaEnabled) {
        this.repository = repository;
        this.memorialService = memorialService;
        this.experienceRepository = experienceRepository;
        this.billingService = billingService;
        this.answerProvider = answerProvider;
        this.mockQaEnabled = mockQaEnabled;
    }

    public DigitalLifeWorkspace workspace(UUID memorialId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        DigitalLifeProfile profile = repository.profile(memorialId).orElse(null);
        List<DigitalLifeFact> facts = profile == null ? List.of() : repository.facts(profile.id());
        return new DigitalLifeWorkspace(profile, facts,
                billingService.hasCapability(userId, FeatureCode.DIGITAL_LIFE_PROFILE),
                mockQaEnabled && billingService.hasCapability(userId, FeatureCode.DIGITAL_LIFE_QA),
                providerStatuses(),
                "数字生命只整理已授权、可追溯的资料，不代表 TA 的意识、声音、意愿或真实存在。");
    }

    @Transactional
    public DigitalLifeWorkspace enable(UUID memorialId, UUID userId, boolean profileConsent, boolean textProcessingConsent, String consentVersion) {
        memorialService.getMine(memorialId, userId);
        requireCapability(userId, FeatureCode.DIGITAL_LIFE_PROFILE, "DIGITAL_LIFE_PROFILE_PREMIUM_REQUIRED", "数字记忆档案属于高级会员权益。");
        if (!profileConsent || !textProcessingConsent) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DIGITAL_LIFE_CONSENT_REQUIRED", "请分别确认资料整理和文本处理授权后再继续。 ");
        }
        boolean firstEnable = repository.profile(memorialId).isEmpty();
        DigitalLifeProfile profile = repository.saveProfile(memorialId, userId, consentVersion == null || consentVersion.isBlank() ? "DIGITAL_LIFE_V1" : consentVersion.trim(), OffsetDateTime.now());
        if (firstEnable) seedFactsFromLifeDetails(memorialId, profile.id());
        return workspace(memorialId, userId);
    }

    @Transactional
    public DigitalLifeWorkspace updateStatus(UUID memorialId, UUID userId, String status) {
        memorialService.getMine(memorialId, userId);
        if (!STATUSES.contains(status)) throw new ApiException(HttpStatus.BAD_REQUEST, "DIGITAL_LIFE_STATUS_INVALID", "数字生命状态无效。 ");
        DigitalLifeProfile profile = requireProfile(memorialId);
        if (!profile.userId().equals(userId)) throw new ApiException(HttpStatus.FORBIDDEN, "DIGITAL_LIFE_ACCESS_DENIED", "没有修改这份数字记忆档案的权限。 ");
        repository.updateStatus(memorialId, status).orElseThrow();
        return workspace(memorialId, userId);
    }

    @Transactional
    public DigitalLifeFact addFact(UUID memorialId, UUID userId, DigitalLifeRepository.FactCommand command) {
        memorialService.getMine(memorialId, userId);
        requireCapability(userId, FeatureCode.DIGITAL_LIFE_PROFILE, "DIGITAL_LIFE_PROFILE_PREMIUM_REQUIRED", "数字记忆档案属于高级会员权益。");
        DigitalLifeProfile profile = requireActiveProfile(memorialId);
        validateFact(command);
        if (repository.facts(profile.id()).size() >= 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DIGITAL_LIFE_FACT_LIMIT_REACHED", "数字记忆档案最多保存 80 条可追溯事实。 ");
        }
        return repository.createFact(profile.id(), UUID.randomUUID(), new DigitalLifeRepository.FactCommand(
                command.factType(), command.statement().trim(), command.sourceType(), command.sourceLabel().trim(), command.verificationStatus()));
    }

    @Transactional
    public DigitalLifeFact updateFactStatus(UUID memorialId, UUID factId, UUID userId, String status) {
        memorialService.getMine(memorialId, userId);
        if (!FACT_STATUSES.contains(status)) throw new ApiException(HttpStatus.BAD_REQUEST, "DIGITAL_LIFE_FACT_STATUS_INVALID", "确认状态无效。 ");
        DigitalLifeProfile profile = requireProfile(memorialId);
        return repository.updateFactStatus(profile.id(), factId, status)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DIGITAL_LIFE_FACT_NOT_FOUND", "没有找到这条数字记忆。 "));
    }

    @Transactional
    public void deleteFact(UUID memorialId, UUID factId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        DigitalLifeProfile profile = requireProfile(memorialId);
        if (!repository.deleteFact(profile.id(), factId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DIGITAL_LIFE_FACT_NOT_FOUND", "没有找到这条数字记忆。 ");
        }
    }

    public DigitalLifeAnswer ask(UUID memorialId, UUID userId, String question) {
        memorialService.getMine(memorialId, userId);
        requireCapability(userId, FeatureCode.DIGITAL_LIFE_QA, "DIGITAL_LIFE_QA_PREMIUM_REQUIRED", "记忆问答属于时光珍藏会员权益。 ");
        if (!mockQaEnabled) throw new ApiException(HttpStatus.CONFLICT, "DIGITAL_LIFE_QA_NOT_OPEN", "记忆问答仍在安全验证中，暂未开放。 ");
        if (DISALLOWED_PERSONA.matcher(question).find()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DIGITAL_LIFE_PERSONA_NOT_SUPPORTED", "这里不会让系统扮演 TA。你可以换成关于生活细节的提问。 ");
        }
        DigitalLifeProfile profile = requireActiveProfile(memorialId);
        List<DigitalLifeFact> confirmed = repository.facts(profile.id()).stream()
                .filter(fact -> "CONFIRMED".equals(fact.verificationStatus())).toList();
        return answerProvider.answer(question.trim(), confirmed);
    }

    @Transactional
    public void delete(UUID memorialId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        repository.deleteProfile(memorialId);
    }

    private void seedFactsFromLifeDetails(UUID memorialId, UUID profileId) {
        for (MemorialExperience.LifeDetail detail : experienceRepository.lifeDetails(memorialId)) {
            repository.createFact(profileId, UUID.randomUUID(), new DigitalLifeRepository.FactCommand(
                    "HABIT", detail.answer(), "LIFE_DETAIL", "生命指纹：" + detail.detailKey(), "PENDING"));
        }
    }

    private List<DigitalLifeProviderStatus> providerStatuses() {
        return List.of(
                new DigitalLifeProviderStatus("MEMORY_QA", mockQaEnabled ? "LOCAL_MOCK" : "PAUSED", "当前使用本地可追溯的模拟问答，不调用外部模型。"),
                new DigitalLifeProviderStatus("VOICE_REPLICA", "RESERVED_NOT_AVAILABLE", "需要单独的声音权利授权、内容审核与服务商评估后才可能开放。"),
                new DigitalLifeProviderStatus("AVATAR", "RESERVED_NOT_AVAILABLE", "需要单独的肖像授权、内容审核与服务商评估后才可能开放。"));
    }

    private DigitalLifeProfile requireProfile(UUID memorialId) {
        return repository.profile(memorialId).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                "DIGITAL_LIFE_NOT_ENABLED", "请先阅读授权说明并建立数字记忆档案。 "));
    }

    private DigitalLifeProfile requireActiveProfile(UUID memorialId) {
        DigitalLifeProfile profile = requireProfile(memorialId);
        if (!"ACTIVE".equals(profile.status())) throw new ApiException(HttpStatus.CONFLICT,
                "DIGITAL_LIFE_PAUSED", "这份数字记忆档案当前已暂停或封存，不会处理新的内容或问题。 ");
        return profile;
    }

    private void validateFact(DigitalLifeRepository.FactCommand command) {
        if (!FACT_TYPES.contains(command.factType()) || !SOURCE_TYPES.contains(command.sourceType()) || !FACT_STATUSES.contains(command.verificationStatus())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DIGITAL_LIFE_FACT_INVALID", "数字记忆的类型、来源或确认状态无效。 ");
        }
        if (DISALLOWED_PERSONA.matcher(command.statement()).find()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DIGITAL_LIFE_PERSONA_NOT_SUPPORTED", "资料可以记录生活细节，但不能把系统描述成 TA 的复活、灵魂或意识。 ");
        }
    }

    private void requireCapability(UUID userId, FeatureCode capability, String code, String message) {
        if (!billingService.hasCapability(userId, capability)) throw new ApiException(HttpStatus.FORBIDDEN, code, message);
    }
}

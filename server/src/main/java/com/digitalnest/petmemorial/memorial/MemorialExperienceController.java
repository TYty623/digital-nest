package com.digitalnest.petmemorial.memorial;

import com.digitalnest.petmemorial.account.CurrentUser;
import com.digitalnest.petmemorial.shared.access.MemorialAccess;
import com.digitalnest.petmemorial.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/memorials")
public class MemorialExperienceController {
    private final MemorialExperienceService service;
    private final MemorialService memorialService;

    public MemorialExperienceController(MemorialExperienceService service, MemorialService memorialService) {
        this.service = service; this.memorialService = memorialService;
    }

    @GetMapping("/{id}/experience")
    public ApiResponse<MemorialExperience> owner(@PathVariable UUID id, Authentication auth) {
        return ApiResponse.ok(service.ownerExperience(id, user(auth).id()));
    }

    @GetMapping("/public/{slug}/experience")
    public ApiResponse<MemorialExperience> publicView(@PathVariable String slug, HttpServletRequest request) {
        Memorial memorial = memorialService.findPublished(slug);
        if ("PASSWORD".equals(memorial.visibility()) && !MemorialAccess.isGranted(request.getSession(false), memorial.id()))
            throw new com.digitalnest.petmemorial.shared.error.ApiException(HttpStatus.UNAUTHORIZED, "MEMORIAL_ACCESS_CODE_REQUIRED", "请输入分享口令后继续查看。");
        return ApiResponse.ok(service.publicExperience(memorial.id()));
    }

    @PostMapping("/{id}/day-moments") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemorialExperience.DayMoment> addDayMoment(@PathVariable UUID id,
            @Valid @RequestBody DayMomentRequest body, Authentication auth) {
        return ApiResponse.ok(service.addDayMoment(id, user(auth).id(), body.momentTime(), body.title(),
                body.placeName(), body.story(), body.mediaId()));
    }

    @PutMapping("/{id}/day-moments/{momentId}")
    public ApiResponse<MemorialExperience.DayMoment> updateDayMoment(@PathVariable UUID id,
            @PathVariable UUID momentId, @Valid @RequestBody DayMomentRequest body, Authentication auth) {
        return ApiResponse.ok(service.updateDayMoment(id, momentId, user(auth).id(), body.momentTime(),
                body.title(), body.placeName(), body.story(), body.mediaId()));
    }

    @DeleteMapping("/{id}/day-moments/{momentId}")
    public ApiResponse<Deleted> deleteDayMoment(@PathVariable UUID id, @PathVariable UUID momentId,
                                                Authentication auth) {
        service.deleteDayMoment(id, momentId, user(auth).id());
        return ApiResponse.ok(new Deleted(true));
    }

    @PutMapping("/{id}/life-details/{detailKey}")
    public ApiResponse<MemorialExperience> saveLifeDetail(@PathVariable UUID id, @PathVariable String detailKey,
            @Valid @RequestBody LifeDetailRequest body, Authentication auth) {
        return ApiResponse.ok(service.saveLifeDetail(id, user(auth).id(), detailKey, body.answer()));
    }

    @PostMapping("/{id}/sounds") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemorialExperience.SoundMemory> addSound(@PathVariable UUID id, @Valid @RequestBody SoundRequest body, Authentication auth) {
        return ApiResponse.ok(service.addSound(id, user(auth).id(), body.mediaId(), body.title(), body.story()));
    }

    @DeleteMapping("/{id}/sounds/{soundId}")
    public ApiResponse<Deleted> deleteSound(@PathVariable UUID id, @PathVariable UUID soundId, Authentication auth) {
        service.deleteSound(id, soundId, user(auth).id()); return ApiResponse.ok(new Deleted(true));
    }

    @PutMapping("/{id}/interviews/{promptKey}")
    public ApiResponse<MemorialExperience> saveInterview(@PathVariable UUID id, @PathVariable String promptKey,
            @Valid @RequestBody InterviewRequest body, Authentication auth) {
        return ApiResponse.ok(service.saveInterview(id, user(auth).id(), promptKey, body.answer()));
    }

    @PostMapping("/{id}/keepsakes") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemorialExperience.Keepsake> addKeepsake(@PathVariable UUID id, @Valid @RequestBody KeepsakeRequest body, Authentication auth) {
        return ApiResponse.ok(service.addKeepsake(id, user(auth).id(), body.title(), body.story()));
    }

    @DeleteMapping("/{id}/keepsakes/{keepsakeId}")
    public ApiResponse<Deleted> deleteKeepsake(@PathVariable UUID id, @PathVariable UUID keepsakeId, Authentication auth) {
        service.deleteKeepsake(id, keepsakeId, user(auth).id()); return ApiResponse.ok(new Deleted(true));
    }

    @PutMapping("/{id}/burial")
    public ApiResponse<MemorialExperience.BurialRecord> saveBurial(@PathVariable UUID id, @Valid @RequestBody BurialRequest body, Authentication auth) {
        return ApiResponse.ok(service.saveBurial(id, user(auth).id(), body.dispositionType(), body.occurredOn(), body.region(), body.placeName(), body.remembranceText()));
    }

    @PostMapping("/{id}/burial/submit")
    public ApiResponse<MemorialExperience.BurialRecord> submitBurial(@PathVariable UUID id, @Valid @RequestBody SubmitRequest body, Authentication auth) {
        return ApiResponse.ok(service.submitBurial(id, user(auth).id(), body.attested()));
    }

    private CurrentUser user(Authentication auth) { return (CurrentUser) auth.getPrincipal(); }
    public record SoundRequest(@NotNull UUID mediaId, @NotBlank @Size(max=80) String title, @Size(max=500) String story) {}
    public record DayMomentRequest(
            @NotBlank @Pattern(regexp="(?:[01]\\d|2[0-3]):[0-5]\\d") String momentTime,
            @NotBlank @Size(max=80) String title,
            @Size(max=80) String placeName,
            @Size(max=500) String story,
            UUID mediaId) {}
    public record LifeDetailRequest(@Size(max=300) String answer) {}
    public record InterviewRequest(@Size(max=1000) String answer) {}
    public record KeepsakeRequest(@NotBlank @Size(max=80) String title, @NotBlank @Size(max=800) String story) {}
    public record BurialRequest(@NotBlank @Pattern(regexp="PROFESSIONAL_HARMLESS|CREMATION|ASHES_KEPT|ASHES_PLACED|OTHER_LAWFUL") String dispositionType,
                                LocalDate occurredOn, @Size(max=64) String region, @Size(max=80) String placeName, @Size(max=500) String remembranceText) {}
    public record SubmitRequest(boolean attested) {}
    public record Deleted(boolean deleted) {}
}

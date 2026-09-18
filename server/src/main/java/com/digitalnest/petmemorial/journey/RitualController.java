package com.digitalnest.petmemorial.journey;

import com.digitalnest.petmemorial.account.CurrentUser;
import com.digitalnest.petmemorial.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memorials/{memorialId}")
public class RitualController {
    private final RitualService service;

    public RitualController(RitualService service) {
        this.service = service;
    }

    @GetMapping("/ritual-space")
    public ApiResponse<RitualSpace> space(@PathVariable UUID memorialId, Authentication authentication) {
        return ApiResponse.ok(service.space(memorialId, user(authentication).id()));
    }

    @PostMapping("/archive-entries") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemorialArchiveEntry> createArchive(@PathVariable UUID memorialId, @Valid @RequestBody ArchiveRequest body, Authentication authentication) {
        return ApiResponse.ok(service.createArchive(memorialId, user(authentication).id(), body.toCommand()));
    }

    @PutMapping("/archive-entries/{entryId}")
    public ApiResponse<MemorialArchiveEntry> updateArchive(@PathVariable UUID memorialId, @PathVariable UUID entryId, @Valid @RequestBody ArchiveRequest body, Authentication authentication) {
        return ApiResponse.ok(service.updateArchive(memorialId, entryId, user(authentication).id(), body.toCommand()));
    }

    @DeleteMapping("/archive-entries/{entryId}")
    public ApiResponse<Deleted> deleteArchive(@PathVariable UUID memorialId, @PathVariable UUID entryId, Authentication authentication) {
        service.deleteArchive(memorialId, entryId, user(authentication).id()); return ApiResponse.ok(new Deleted(true));
    }

    @PostMapping("/anniversaries") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemorialAnniversary> createAnniversary(@PathVariable UUID memorialId, @Valid @RequestBody AnniversaryRequest body, Authentication authentication) {
        return ApiResponse.ok(service.createAnniversary(memorialId, user(authentication).id(), body.toCommand()));
    }

    @PutMapping("/anniversaries/{anniversaryId}")
    public ApiResponse<MemorialAnniversary> updateAnniversary(@PathVariable UUID memorialId, @PathVariable UUID anniversaryId, @Valid @RequestBody AnniversaryRequest body, Authentication authentication) {
        return ApiResponse.ok(service.updateAnniversary(memorialId, anniversaryId, user(authentication).id(), body.toCommand()));
    }

    @DeleteMapping("/anniversaries/{anniversaryId}")
    public ApiResponse<Deleted> deleteAnniversary(@PathVariable UUID memorialId, @PathVariable UUID anniversaryId, Authentication authentication) {
        service.deleteAnniversary(memorialId, anniversaryId, user(authentication).id()); return ApiResponse.ok(new Deleted(true));
    }

    @PostMapping("/capsules") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemoryCapsule> createCapsule(@PathVariable UUID memorialId, @Valid @RequestBody CapsuleRequest body, Authentication authentication) {
        return ApiResponse.ok(service.createCapsule(memorialId, user(authentication).id(), body.toCommand()));
    }

    @PostMapping("/capsules/{capsuleId}/open")
    public ApiResponse<MemoryCapsule> openCapsule(@PathVariable UUID memorialId, @PathVariable UUID capsuleId, Authentication authentication) {
        return ApiResponse.ok(service.openCapsule(memorialId, capsuleId, user(authentication).id()));
    }

    @DeleteMapping("/capsules/{capsuleId}")
    public ApiResponse<Deleted> deleteCapsule(@PathVariable UUID memorialId, @PathVariable UUID capsuleId, Authentication authentication) {
        service.deleteCapsule(memorialId, capsuleId, user(authentication).id()); return ApiResponse.ok(new Deleted(true));
    }

    @PostMapping("/rituals") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RitualRecord> completeRitual(@PathVariable UUID memorialId, @Valid @RequestBody RitualRequest body, Authentication authentication) {
        return ApiResponse.ok(service.completeRitual(memorialId, user(authentication).id(), body.toCommand()));
    }

    @GetMapping("/yearbook")
    public ApiResponse<YearbookPreview> yearbook(@PathVariable UUID memorialId, @RequestParam(defaultValue = "0") int year, Authentication authentication) {
        return ApiResponse.ok(service.yearbook(memorialId, user(authentication).id(), year));
    }

    private CurrentUser user(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser) return currentUser;
        throw new IllegalStateException("受保护的接口缺少当前用户");
    }

    public record ArchiveRequest(
            @NotBlank @Pattern(regexp = "PLACE|RELATION|OBJECT|MOMENT|MEDIA") String entryType,
            @NotBlank @Size(max = 80) String title,
            @Size(max = 1200) String body,
            LocalDate eventDate,
            @Size(max = 100) String placeLabel,
            @Size(max = 80) String sourceLabel,
            @NotBlank @Pattern(regexp = "CONFIRMED|PENDING") String verificationStatus,
            @NotBlank @Pattern(regexp = "PRIVATE|FAMILY") String visibility,
            UUID mediaId) {
        RitualRepository.ArchiveCommand toCommand() { return new RitualRepository.ArchiveCommand(entryType, title, body, eventDate, placeLabel, sourceLabel, verificationStatus, visibility, mediaId); }
    }

    public record AnniversaryRequest(
            @NotBlank @Pattern(regexp = "BIRTHDAY|MEETING|ADOPTION|FAREWELL|SEASON|CUSTOM") String anniversaryType,
            @NotBlank @Size(max = 60) String title,
            @NotNull LocalDate eventDate,
            @NotBlank @Pattern(regexp = "ANNUAL|ONCE|OFF") String repeatRule,
            boolean reminderEnabled) {
        RitualRepository.AnniversaryCommand toCommand() { return new RitualRepository.AnniversaryCommand(anniversaryType, title, eventDate, repeatRule, reminderEnabled); }
    }

    public record CapsuleRequest(
            @NotBlank @Size(max = 80) String title,
            @NotBlank @Size(max = 2000) String body,
            UUID mediaId,
            @NotNull LocalDate unlockOn,
            @NotBlank @Pattern(regexp = "PRIVATE|FAMILY") String visibility) {
        RitualRepository.CapsuleCommand toCommand() { return new RitualRepository.CapsuleCommand(title, body, mediaId, unlockOn, visibility); }
    }

    public record RitualRequest(
            @NotBlank @Pattern(regexp = "FIRST_HOME|BIRTHDAY|ADOPTION|FAREWELL|SEASON|CUSTOM|FAMILY") String ritualType,
            @NotBlank @Pattern(regexp = "LIGHT|FLOWER|LETTER|SOUND|CAPSULE") String ritualAction,
            @Size(max = 500) String note,
            boolean ambientEnabled) {
        RitualRepository.RitualCommand toCommand() { return new RitualRepository.RitualCommand(ritualType, ritualAction, note, ambientEnabled); }
    }

    public record Deleted(boolean deleted) {}
}

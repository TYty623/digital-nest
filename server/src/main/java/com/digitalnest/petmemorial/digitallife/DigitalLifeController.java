package com.digitalnest.petmemorial.digitallife;

import com.digitalnest.petmemorial.account.CurrentUser;
import com.digitalnest.petmemorial.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memorials/{memorialId}/digital-life")
public class DigitalLifeController {
    private final DigitalLifeService service;

    public DigitalLifeController(DigitalLifeService service) { this.service = service; }

    @GetMapping
    public ApiResponse<DigitalLifeWorkspace> workspace(@PathVariable UUID memorialId, Authentication authentication) {
        return ApiResponse.ok(service.workspace(memorialId, user(authentication).id()));
    }

    @PutMapping("/consent")
    public ApiResponse<DigitalLifeWorkspace> enable(@PathVariable UUID memorialId, @Valid @RequestBody ConsentRequest body, Authentication authentication) {
        return ApiResponse.ok(service.enable(memorialId, user(authentication).id(), body.profileConsent(), body.textProcessingConsent(), body.consentVersion()));
    }

    @PutMapping("/status")
    public ApiResponse<DigitalLifeWorkspace> updateStatus(@PathVariable UUID memorialId, @Valid @RequestBody StatusRequest body, Authentication authentication) {
        return ApiResponse.ok(service.updateStatus(memorialId, user(authentication).id(), body.status()));
    }

    @PostMapping("/facts")
    public ApiResponse<DigitalLifeFact> addFact(@PathVariable UUID memorialId, @Valid @RequestBody FactRequest body, Authentication authentication) {
        return ApiResponse.ok(service.addFact(memorialId, user(authentication).id(), body.toCommand()));
    }

    @PutMapping("/facts/{factId}/status")
    public ApiResponse<DigitalLifeFact> updateFact(@PathVariable UUID memorialId, @PathVariable UUID factId, @Valid @RequestBody StatusRequest body, Authentication authentication) {
        return ApiResponse.ok(service.updateFactStatus(memorialId, factId, user(authentication).id(), body.status()));
    }

    @DeleteMapping("/facts/{factId}")
    public ApiResponse<Deleted> deleteFact(@PathVariable UUID memorialId, @PathVariable UUID factId, Authentication authentication) {
        service.deleteFact(memorialId, factId, user(authentication).id()); return ApiResponse.ok(new Deleted(true));
    }

    @PostMapping("/questions")
    public ApiResponse<DigitalLifeAnswer> ask(@PathVariable UUID memorialId, @Valid @RequestBody QuestionRequest body, Authentication authentication) {
        return ApiResponse.ok(service.ask(memorialId, user(authentication).id(), body.question()));
    }

    @DeleteMapping
    public ApiResponse<Deleted> delete(@PathVariable UUID memorialId, Authentication authentication) {
        service.delete(memorialId, user(authentication).id()); return ApiResponse.ok(new Deleted(true));
    }

    private CurrentUser user(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser) return currentUser;
        throw new IllegalStateException("受保护的接口缺少当前用户");
    }

    public record ConsentRequest(boolean profileConsent, boolean textProcessingConsent, @Size(max = 32) String consentVersion) {}
    public record StatusRequest(@NotBlank @Pattern(regexp = "ACTIVE|PAUSED|ARCHIVED|PENDING|CONFIRMED|REJECTED") String status) {}
    public record FactRequest(
            @NotBlank @Pattern(regexp = "TRAIT|HABIT|RELATION|EVENT|PREFERENCE") String factType,
            @NotBlank @Size(max = 500) String statement,
            @NotBlank @Pattern(regexp = "LIFE_DETAIL|ARCHIVE_ENTRY|TIMELINE|USER_NOTE") String sourceType,
            @NotBlank @Size(max = 120) String sourceLabel,
            @NotBlank @Pattern(regexp = "PENDING|CONFIRMED|REJECTED") String verificationStatus) {
        DigitalLifeRepository.FactCommand toCommand() { return new DigitalLifeRepository.FactCommand(factType, statement, sourceType, sourceLabel, verificationStatus); }
    }
    public record QuestionRequest(@NotBlank @Size(max = 240) String question) {}
    public record Deleted(boolean deleted) {}
}

package com.digitalnest.petmemorial.admin;

import com.digitalnest.petmemorial.account.CurrentUser;
import com.digitalnest.petmemorial.memorial.MemorialExperience;
import com.digitalnest.petmemorial.memorial.MemorialExperienceService;
import com.digitalnest.petmemorial.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/burial-records")
public class BurialModerationController {
    private final MemorialExperienceService service;
    public BurialModerationController(MemorialExperienceService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<MemorialExperience.BurialRecord>> list(@RequestParam(defaultValue="PENDING") String status) {
        return ApiResponse.ok(service.reviewQueue(status));
    }

    @PatchMapping("/{id}")
    public ApiResponse<MemorialExperience.BurialRecord> moderate(@PathVariable UUID id, @Valid @RequestBody ReviewRequest body, Authentication auth) {
        return ApiResponse.ok(service.moderate(id, body.status(), body.reason(), ((CurrentUser) auth.getPrincipal()).id()));
    }

    public record ReviewRequest(@NotBlank @Pattern(regexp="APPROVED|REJECTED") String status,
                                @NotBlank @Size(min=2,max=500) String reason) {}
}

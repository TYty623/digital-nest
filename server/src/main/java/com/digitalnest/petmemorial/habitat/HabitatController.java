package com.digitalnest.petmemorial.habitat;

import com.digitalnest.petmemorial.account.CurrentUser;
import com.digitalnest.petmemorial.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memorials/{memorialId}/habitat")
public class HabitatController {
    private final HabitatService service;

    public HabitatController(HabitatService service) { this.service = service; }

    @GetMapping
    public ApiResponse<MemorialHabitatSpace> space(@PathVariable UUID memorialId, Authentication authentication) {
        return ApiResponse.ok(service.space(memorialId, user(authentication).id()));
    }

    @PutMapping
    public ApiResponse<MemorialHabitatSpace> save(@PathVariable UUID memorialId, @Valid @RequestBody SettingsRequest body,
                                                   Authentication authentication) {
        return ApiResponse.ok(service.saveSettings(memorialId, user(authentication).id(), body.scene(), body.title(), body.light()));
    }

    @PostMapping("/items") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemorialHabitatSpace.Item> addItem(@PathVariable UUID memorialId, @Valid @RequestBody ItemRequest body,
                                                           Authentication authentication) {
        return ApiResponse.ok(service.addItem(memorialId, user(authentication).id(), body.kind(), body.xPercent(), body.yPercent()));
    }

    @DeleteMapping("/items/{itemId}")
    public ApiResponse<Deleted> deleteItem(@PathVariable UUID memorialId, @PathVariable UUID itemId, Authentication authentication) {
        service.deleteItem(memorialId, itemId, user(authentication).id());
        return ApiResponse.ok(new Deleted(true));
    }

    @PostMapping("/notes") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemorialHabitatSpace.Note> addNote(@PathVariable UUID memorialId, @Valid @RequestBody NoteRequest body,
                                                           Authentication authentication) {
        return ApiResponse.ok(service.addNote(memorialId, user(authentication).id(),
                new HabitatService.HabitatNoteCommand(body.memoryDate(), body.text(), body.sourceType(), body.sourceId(), body.sourceLabel())));
    }

    @DeleteMapping("/notes/{noteId}")
    public ApiResponse<Deleted> deleteNote(@PathVariable UUID memorialId, @PathVariable UUID noteId, Authentication authentication) {
        service.deleteNote(memorialId, noteId, user(authentication).id());
        return ApiResponse.ok(new Deleted(true));
    }

    private CurrentUser user(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser) return currentUser;
        throw new IllegalStateException("受保护的接口缺少当前用户");
    }

    public record SettingsRequest(@NotBlank @Pattern(regexp = "FOREST|COMPANION") String scene,
                                  @Size(max = 60) String title, @Min(30) @Max(100) int light) {}
    public record ItemRequest(@NotBlank @Pattern(regexp = "LIGHT|FLOWER|STONE") String kind,
                              @Min(5) @Max(95) int xPercent, @Min(15) @Max(85) int yPercent) {}
    public record NoteRequest(@NotNull LocalDate memoryDate, @NotBlank @Size(max = 180) String text,
                              @NotBlank @Pattern(regexp = "MANUAL|DAY_MOMENT|LIFE_DETAIL|KEEPSAKE|ARCHIVE_ENTRY") String sourceType,
                              UUID sourceId, @Size(max = 120) String sourceLabel) {}
    public record Deleted(boolean deleted) {}
}

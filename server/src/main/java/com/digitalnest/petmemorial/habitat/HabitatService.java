package com.digitalnest.petmemorial.habitat;

import com.digitalnest.petmemorial.memorial.MemorialService;
import com.digitalnest.petmemorial.shared.error.ApiException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HabitatService {
    private static final Set<String> SCENES = Set.of("FOREST", "COMPANION");
    private static final Set<String> ITEM_KINDS = Set.of("LIGHT", "FLOWER", "STONE");
    private static final Set<String> SOURCE_TYPES = Set.of("MANUAL", "DAY_MOMENT", "LIFE_DETAIL", "KEEPSAKE", "ARCHIVE_ENTRY");
    private static final int MAX_ITEMS = 18;
    private static final int MAX_NOTES = 50;
    private final HabitatRepository repository;
    private final MemorialService memorialService;

    public HabitatService(HabitatRepository repository, MemorialService memorialService) {
        this.repository = repository;
        this.memorialService = memorialService;
    }

    public MemorialHabitatSpace space(UUID memorialId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        HabitatRepository.Settings settings = repository.settings(memorialId)
                .orElse(new HabitatRepository.Settings(memorialId, "FOREST", "留在光里的日子", 80, null));
        return new MemorialHabitatSpace(memorialId, settings.scene(), settings.title(), settings.light(), settings.updatedAt(),
                repository.items(memorialId), repository.notes(memorialId));
    }

    @Transactional
    public MemorialHabitatSpace saveSettings(UUID memorialId, UUID userId, String scene, String title, int light) {
        memorialService.getMine(memorialId, userId);
        if (!SCENES.contains(scene)) throw bad("HABITAT_SCENE_INVALID", "这个场景暂时不可用。 ");
        if (light < 30 || light > 100) throw bad("HABITAT_LIGHT_INVALID", "光线请保持在 30% 到 100% 之间。 ");
        String cleanedTitle = title == null || title.isBlank() ? "留在光里的日子" : title.trim();
        repository.saveSettings(memorialId, scene, cleanedTitle, light);
        return space(memorialId, userId);
    }

    @Transactional
    public MemorialHabitatSpace.Item addItem(UUID memorialId, UUID userId, String kind, int xPercent, int yPercent) {
        memorialService.getMine(memorialId, userId);
        if (!ITEM_KINDS.contains(kind) || xPercent < 5 || xPercent > 95 || yPercent < 15 || yPercent > 85) {
            throw bad("HABITAT_ITEM_INVALID", "物件或安放位置无效。 ");
        }
        if (repository.itemCount(memorialId) >= MAX_ITEMS) {
            throw bad("HABITAT_ITEM_LIMIT_REACHED", "这处角落已经安放了 18 件物品；可以先收回一件。 ");
        }
        ensureSettings(memorialId);
        return repository.addItem(memorialId, UUID.randomUUID(), kind, xPercent, yPercent);
    }

    @Transactional
    public void deleteItem(UUID memorialId, UUID itemId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        if (!repository.deleteItem(memorialId, itemId)) throw notFound("HABITAT_ITEM_NOT_FOUND", "没有找到这件物品。 ");
    }

    @Transactional
    public MemorialHabitatSpace.Note addNote(UUID memorialId, UUID userId, HabitatNoteCommand command) {
        memorialService.getMine(memorialId, userId);
        if (command.memoryDate() == null || command.memoryDate().isAfter(LocalDate.now())) {
            throw bad("HABITAT_NOTE_DATE_INVALID", "记忆日期不能晚于今天。 ");
        }
        if (!SOURCE_TYPES.contains(command.sourceType())) throw bad("HABITAT_NOTE_SOURCE_INVALID", "这段记忆的来源类型无效。 ");
        if (repository.noteCount(memorialId) >= MAX_NOTES) {
            throw bad("HABITAT_NOTE_LIMIT_REACHED", "已经收好 50 张记忆便笺；可以先整理或下载纪念卡。 ");
        }
        ensureSettings(memorialId);
        return repository.addNote(memorialId, UUID.randomUUID(), command.memoryDate(), command.text().trim(), command.sourceType(),
                command.sourceId(), blankToNull(command.sourceLabel()));
    }

    @Transactional
    public void deleteNote(UUID memorialId, UUID noteId, UUID userId) {
        memorialService.getMine(memorialId, userId);
        if (!repository.deleteNote(memorialId, noteId)) throw notFound("HABITAT_NOTE_NOT_FOUND", "没有找到这张记忆便笺。 ");
    }

    private void ensureSettings(UUID memorialId) {
        if (repository.settings(memorialId).isEmpty()) repository.saveSettings(memorialId, "FOREST", "留在光里的日子", 80);
    }

    private static String blankToNull(String text) { return text == null || text.isBlank() ? null : text.trim(); }
    private static ApiException bad(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
    private static ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }

    public record HabitatNoteCommand(LocalDate memoryDate, String text, String sourceType, UUID sourceId, String sourceLabel) {}
}

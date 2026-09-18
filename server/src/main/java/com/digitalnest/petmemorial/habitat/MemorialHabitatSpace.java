package com.digitalnest.petmemorial.habitat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A private, optional visual corner. It is deliberately attached to a memorial, not a public feed. */
public record MemorialHabitatSpace(
        UUID memorialId,
        String scene,
        String title,
        int light,
        OffsetDateTime updatedAt,
        List<Item> items,
        List<Note> notes) {
    public record Item(UUID id, String kind, int xPercent, int yPercent, OffsetDateTime createdAt) {}
    public record Note(UUID id, LocalDate memoryDate, String text, String sourceType,
                       UUID sourceId, String sourceLabel, OffsetDateTime createdAt) {}
}

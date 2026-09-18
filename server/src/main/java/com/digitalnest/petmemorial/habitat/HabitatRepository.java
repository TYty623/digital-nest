package com.digitalnest.petmemorial.habitat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HabitatRepository {
    private final JdbcTemplate jdbc;

    public HabitatRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Settings> settings(UUID memorialId) {
        return jdbc.query("SELECT memorial_id, scene, title, light, updated_at FROM memorial_habitat_spaces WHERE memorial_id=?",
                (rs, row) -> new Settings(rs.getObject("memorial_id", UUID.class), rs.getString("scene"),
                        rs.getString("title"), rs.getInt("light"), rs.getObject("updated_at", OffsetDateTime.class)), memorialId)
                .stream().findFirst();
    }

    public void saveSettings(UUID memorialId, String scene, String title, int light) {
        int updated = jdbc.update("UPDATE memorial_habitat_spaces SET scene=?, title=?, light=?, updated_at=CURRENT_TIMESTAMP WHERE memorial_id=?",
                scene, title, light, memorialId);
        if (updated == 0) {
            jdbc.update("INSERT INTO memorial_habitat_spaces (memorial_id, scene, title, light) VALUES (?, ?, ?, ?)",
                    memorialId, scene, title, light);
        }
    }

    public List<MemorialHabitatSpace.Item> items(UUID memorialId) {
        return jdbc.query("""
                SELECT id, item_kind, x_percent, y_percent, created_at
                FROM memorial_habitat_items WHERE memorial_id=? ORDER BY created_at ASC
                """, (rs, row) -> new MemorialHabitatSpace.Item(rs.getObject("id", UUID.class),
                rs.getString("item_kind"), rs.getInt("x_percent"), rs.getInt("y_percent"),
                rs.getObject("created_at", OffsetDateTime.class)), memorialId);
    }

    public int itemCount(UUID memorialId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM memorial_habitat_items WHERE memorial_id=?", Integer.class, memorialId);
        return count == null ? 0 : count;
    }

    public MemorialHabitatSpace.Item addItem(UUID memorialId, UUID id, String kind, int xPercent, int yPercent) {
        jdbc.update("INSERT INTO memorial_habitat_items (id, memorial_id, item_kind, x_percent, y_percent) VALUES (?, ?, ?, ?, ?)",
                id, memorialId, kind, xPercent, yPercent);
        return items(memorialId).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    public boolean deleteItem(UUID memorialId, UUID itemId) {
        return jdbc.update("DELETE FROM memorial_habitat_items WHERE memorial_id=? AND id=?", memorialId, itemId) > 0;
    }

    public List<MemorialHabitatSpace.Note> notes(UUID memorialId) {
        return jdbc.query("""
                SELECT id, memory_date, note_text, source_type, source_id, source_label, created_at
                FROM memorial_habitat_notes WHERE memorial_id=? ORDER BY memory_date DESC, created_at DESC
                """, (rs, row) -> new MemorialHabitatSpace.Note(rs.getObject("id", UUID.class),
                rs.getObject("memory_date", LocalDate.class), rs.getString("note_text"), rs.getString("source_type"),
                rs.getObject("source_id", UUID.class), rs.getString("source_label"),
                rs.getObject("created_at", OffsetDateTime.class)), memorialId);
    }

    public int noteCount(UUID memorialId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM memorial_habitat_notes WHERE memorial_id=?", Integer.class, memorialId);
        return count == null ? 0 : count;
    }

    public MemorialHabitatSpace.Note addNote(UUID memorialId, UUID id, LocalDate memoryDate, String text,
                                              String sourceType, UUID sourceId, String sourceLabel) {
        jdbc.update("""
                INSERT INTO memorial_habitat_notes (id, memorial_id, memory_date, note_text, source_type, source_id, source_label)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, memorialId, memoryDate, text, sourceType, sourceId, sourceLabel);
        return notes(memorialId).stream().filter(note -> note.id().equals(id)).findFirst().orElseThrow();
    }

    public boolean deleteNote(UUID memorialId, UUID noteId) {
        return jdbc.update("DELETE FROM memorial_habitat_notes WHERE memorial_id=? AND id=?", memorialId, noteId) > 0;
    }

    public record Settings(UUID memorialId, String scene, String title, int light, OffsetDateTime updatedAt) {}
}

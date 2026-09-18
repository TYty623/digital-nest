package com.digitalnest.petmemorial.journey;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class RitualRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<MemorialArchiveEntry> archiveMapper = this::mapArchive;
    private final RowMapper<MemorialAnniversary> anniversaryMapper = this::mapAnniversary;
    private final RowMapper<MemoryCapsule> capsuleMapper = this::mapCapsule;
    private final RowMapper<RitualRecord> ritualMapper = this::mapRitual;

    public RitualRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MemorialArchiveEntry> archiveEntries(UUID memorialId) {
        return jdbc.query("""
                SELECT e.*, CASE WHEN e.media_id IS NULL THEN NULL ELSE '/api/v1/media/' || e.media_id || '/content' END AS media_url
                FROM memorial_archive_entries e WHERE e.memorial_id=?
                ORDER BY e.event_date ASC NULLS LAST, e.created_at ASC
                """, archiveMapper, memorialId);
    }

    public Optional<MemorialArchiveEntry> archiveEntry(UUID memorialId, UUID id) {
        return jdbc.query("""
                SELECT e.*, CASE WHEN e.media_id IS NULL THEN NULL ELSE '/api/v1/media/' || e.media_id || '/content' END AS media_url
                FROM memorial_archive_entries e WHERE e.memorial_id=? AND e.id=?
                """, archiveMapper, memorialId, id).stream().findFirst();
    }

    public MemorialArchiveEntry createArchive(UUID memorialId, UUID id, ArchiveCommand command) {
        jdbc.update("""
                INSERT INTO memorial_archive_entries
                (id, memorial_id, entry_type, title, body, event_date, place_label, source_label, verification_status, visibility, media_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, memorialId, command.entryType(), command.title(), command.body(), command.eventDate(),
                command.placeLabel(), command.sourceLabel(), command.verificationStatus(), command.visibility(), command.mediaId());
        return archiveEntry(memorialId, id).orElseThrow();
    }

    public Optional<MemorialArchiveEntry> updateArchive(UUID memorialId, UUID id, ArchiveCommand command) {
        int count = jdbc.update("""
                UPDATE memorial_archive_entries SET entry_type=?, title=?, body=?, event_date=?, place_label=?, source_label=?,
                verification_status=?, visibility=?, media_id=?, updated_at=CURRENT_TIMESTAMP WHERE memorial_id=? AND id=?
                """, command.entryType(), command.title(), command.body(), command.eventDate(), command.placeLabel(),
                command.sourceLabel(), command.verificationStatus(), command.visibility(), command.mediaId(), memorialId, id);
        return count == 0 ? Optional.empty() : archiveEntry(memorialId, id);
    }

    public boolean deleteArchive(UUID memorialId, UUID id) {
        return jdbc.update("DELETE FROM memorial_archive_entries WHERE memorial_id=? AND id=?", memorialId, id) > 0;
    }

    public List<MemorialAnniversary> anniversaries(UUID memorialId) {
        return jdbc.query("SELECT * FROM memorial_anniversaries WHERE memorial_id=? ORDER BY event_date ASC", anniversaryMapper, memorialId);
    }

    public Optional<MemorialAnniversary> anniversary(UUID memorialId, UUID id) {
        return jdbc.query("SELECT * FROM memorial_anniversaries WHERE memorial_id=? AND id=?", anniversaryMapper, memorialId, id).stream().findFirst();
    }

    public MemorialAnniversary createAnniversary(UUID memorialId, UUID id, AnniversaryCommand command) {
        jdbc.update("""
                INSERT INTO memorial_anniversaries (id, memorial_id, anniversary_type, title, event_date, repeat_rule, reminder_enabled)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, memorialId, command.anniversaryType(), command.title(), command.eventDate(), command.repeatRule(), command.reminderEnabled());
        return anniversary(memorialId, id).orElseThrow();
    }

    public Optional<MemorialAnniversary> updateAnniversary(UUID memorialId, UUID id, AnniversaryCommand command) {
        int count = jdbc.update("""
                UPDATE memorial_anniversaries SET anniversary_type=?, title=?, event_date=?, repeat_rule=?, reminder_enabled=?, updated_at=CURRENT_TIMESTAMP
                WHERE memorial_id=? AND id=?
                """, command.anniversaryType(), command.title(), command.eventDate(), command.repeatRule(), command.reminderEnabled(), memorialId, id);
        return count == 0 ? Optional.empty() : anniversary(memorialId, id);
    }

    public boolean deleteAnniversary(UUID memorialId, UUID id) {
        return jdbc.update("DELETE FROM memorial_anniversaries WHERE memorial_id=? AND id=?", memorialId, id) > 0;
    }

    public List<MemoryCapsule> capsules(UUID memorialId) {
        return jdbc.query("""
                SELECT c.*, CASE WHEN c.media_id IS NULL THEN NULL ELSE '/api/v1/media/' || c.media_id || '/content' END AS media_url
                FROM memorial_time_capsules c WHERE c.memorial_id=? ORDER BY c.unlock_on ASC, c.created_at ASC
                """, capsuleMapper, memorialId);
    }

    public Optional<MemoryCapsule> capsule(UUID memorialId, UUID id) {
        return jdbc.query("""
                SELECT c.*, CASE WHEN c.media_id IS NULL THEN NULL ELSE '/api/v1/media/' || c.media_id || '/content' END AS media_url
                FROM memorial_time_capsules c WHERE c.memorial_id=? AND c.id=?
                """, capsuleMapper, memorialId, id).stream().findFirst();
    }

    public MemoryCapsule createCapsule(UUID memorialId, UUID id, CapsuleCommand command) {
        jdbc.update("""
                INSERT INTO memorial_time_capsules (id, memorial_id, title, body, media_id, unlock_on, visibility)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, memorialId, command.title(), command.body(), command.mediaId(), command.unlockOn(), command.visibility());
        return capsule(memorialId, id).orElseThrow();
    }

    public boolean markCapsuleOpened(UUID memorialId, UUID id) {
        return jdbc.update("""
                UPDATE memorial_time_capsules SET opened_at=COALESCE(opened_at, CURRENT_TIMESTAMP)
                WHERE memorial_id=? AND id=? AND unlock_on <= CURRENT_DATE
                """, memorialId, id) > 0;
    }

    public boolean deleteCapsule(UUID memorialId, UUID id) {
        return jdbc.update("DELETE FROM memorial_time_capsules WHERE memorial_id=? AND id=?", memorialId, id) > 0;
    }

    public RitualRecord createRitual(UUID memorialId, UUID id, RitualCommand command) {
        jdbc.update("""
                INSERT INTO memorial_ritual_records (id, memorial_id, ritual_type, ritual_action, note, ambient_enabled)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, memorialId, command.ritualType(), command.ritualAction(), command.note(), command.ambientEnabled());
        return jdbc.query("SELECT * FROM memorial_ritual_records WHERE id=?", ritualMapper, id).getFirst();
    }

    public List<RitualRecord> rituals(UUID memorialId) {
        return jdbc.query("SELECT * FROM memorial_ritual_records WHERE memorial_id=? ORDER BY completed_at DESC", ritualMapper, memorialId);
    }

    public int countCapsules(UUID memorialId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM memorial_time_capsules WHERE memorial_id=?", Integer.class, memorialId);
        return count == null ? 0 : count;
    }

    private MemorialArchiveEntry mapArchive(ResultSet rs, int rowNum) throws SQLException {
        return new MemorialArchiveEntry(rs.getObject("id", UUID.class), rs.getObject("memorial_id", UUID.class),
                rs.getString("entry_type"), rs.getString("title"), rs.getString("body"),
                rs.getObject("event_date", java.time.LocalDate.class), rs.getString("place_label"), rs.getString("source_label"),
                rs.getString("verification_status"), rs.getString("visibility"), rs.getObject("media_id", UUID.class),
                rs.getString("media_url"), rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class));
    }

    private MemorialAnniversary mapAnniversary(ResultSet rs, int rowNum) throws SQLException {
        return new MemorialAnniversary(rs.getObject("id", UUID.class), rs.getObject("memorial_id", UUID.class),
                rs.getString("anniversary_type"), rs.getString("title"), rs.getObject("event_date", java.time.LocalDate.class),
                rs.getString("repeat_rule"), rs.getBoolean("reminder_enabled"),
                rs.getObject("created_at", java.time.OffsetDateTime.class), rs.getObject("updated_at", java.time.OffsetDateTime.class));
    }

    private MemoryCapsule mapCapsule(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryCapsule(rs.getObject("id", UUID.class), rs.getObject("memorial_id", UUID.class),
                rs.getString("title"), rs.getString("body"), rs.getObject("media_id", UUID.class), rs.getString("media_url"),
                rs.getObject("unlock_on", java.time.LocalDate.class), rs.getString("visibility"), false,
                rs.getObject("opened_at", java.time.OffsetDateTime.class), rs.getObject("created_at", java.time.OffsetDateTime.class));
    }

    private RitualRecord mapRitual(ResultSet rs, int rowNum) throws SQLException {
        return new RitualRecord(rs.getObject("id", UUID.class), rs.getObject("memorial_id", UUID.class),
                rs.getString("ritual_type"), rs.getString("ritual_action"), rs.getString("note"), rs.getBoolean("ambient_enabled"),
                rs.getObject("completed_at", java.time.OffsetDateTime.class));
    }

    public record ArchiveCommand(String entryType, String title, String body, java.time.LocalDate eventDate,
                                 String placeLabel, String sourceLabel, String verificationStatus, String visibility, UUID mediaId) {}
    public record AnniversaryCommand(String anniversaryType, String title, java.time.LocalDate eventDate,
                                     String repeatRule, boolean reminderEnabled) {}
    public record CapsuleCommand(String title, String body, UUID mediaId, java.time.LocalDate unlockOn, String visibility) {}
    public record RitualCommand(String ritualType, String ritualAction, String note, boolean ambientEnabled) {}
}

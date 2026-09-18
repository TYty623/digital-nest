package com.digitalnest.petmemorial.memorial;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MemorialExperienceRepository {
    private final JdbcTemplate jdbc;

    public MemorialExperienceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<MemorialExperience.DayMoment> dayMoments(UUID memorialId) {
        return jdbc.query("""
                SELECT d.id, d.moment_time, d.title, d.place_name, d.story, d.media_id,
                       d.created_at, d.updated_at
                FROM memorial_day_moments d
                WHERE d.memorial_id = ?
                ORDER BY d.moment_time ASC, d.created_at ASC
                """, (rs, n) -> new MemorialExperience.DayMoment(
                rs.getObject("id", UUID.class), rs.getString("moment_time"), rs.getString("title"),
                rs.getString("place_name"), rs.getString("story"), rs.getObject("media_id", UUID.class),
                mediaUrl(rs.getObject("media_id", UUID.class)),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)), memorialId);
    }

    public MemorialExperience.DayMoment addDayMoment(UUID memorialId, UUID id, String momentTime,
                                                      String title, String placeName, String story, UUID mediaId) {
        jdbc.update("""
                INSERT INTO memorial_day_moments
                    (id, memorial_id, moment_time, title, place_name, story, media_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, memorialId, momentTime, title, placeName, story, mediaId);
        return findDayMoment(memorialId, id).orElseThrow();
    }

    public Optional<MemorialExperience.DayMoment> updateDayMoment(UUID memorialId, UUID id, String momentTime,
                                                                  String title, String placeName, String story, UUID mediaId) {
        int updated = jdbc.update("""
                UPDATE memorial_day_moments
                SET moment_time=?, title=?, place_name=?, story=?, media_id=?, updated_at=CURRENT_TIMESTAMP
                WHERE memorial_id=? AND id=?
                """, momentTime, title, placeName, story, mediaId, memorialId, id);
        return updated == 0 ? Optional.empty() : findDayMoment(memorialId, id);
    }

    public boolean deleteDayMoment(UUID memorialId, UUID id) {
        return jdbc.update("DELETE FROM memorial_day_moments WHERE memorial_id=? AND id=?", memorialId, id) > 0;
    }

    public Optional<MemorialExperience.DayMoment> findDayMoment(UUID memorialId, UUID id) {
        return dayMoments(memorialId).stream().filter(item -> item.id().equals(id)).findFirst();
    }

    public List<MemorialExperience.LifeDetail> lifeDetails(UUID memorialId) {
        return jdbc.query("""
                SELECT id, detail_key, answer, updated_at
                FROM memorial_life_details WHERE memorial_id=? ORDER BY detail_key
                """, (rs, n) -> new MemorialExperience.LifeDetail(
                rs.getObject("id", UUID.class), rs.getString("detail_key"), rs.getString("answer"),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)), memorialId);
    }

    public void saveLifeDetail(UUID memorialId, UUID id, String detailKey, String answer) {
        int updated = jdbc.update("""
                UPDATE memorial_life_details SET answer=?, updated_at=CURRENT_TIMESTAMP
                WHERE memorial_id=? AND detail_key=?
                """, answer, memorialId, detailKey);
        if (updated == 0) {
            jdbc.update("INSERT INTO memorial_life_details (id, memorial_id, detail_key, answer) VALUES (?, ?, ?, ?)",
                    id, memorialId, detailKey, answer);
        }
    }

    public void deleteLifeDetail(UUID memorialId, String detailKey) {
        jdbc.update("DELETE FROM memorial_life_details WHERE memorial_id=? AND detail_key=?", memorialId, detailKey);
    }

    private String mediaUrl(UUID mediaId) {
        return mediaId == null ? null : "/api/v1/media/" + mediaId + "/content";
    }

    public List<MemorialExperience.SoundMemory> sounds(UUID memorialId) {
        return jdbc.query("""
                SELECT s.id, s.media_id, a.content_type, s.title, s.story, s.position, s.created_at
                FROM memorial_sound_memories s JOIN media_assets a ON a.id = s.media_id
                WHERE s.memorial_id = ? ORDER BY s.position, s.created_at
                """, (rs, n) -> new MemorialExperience.SoundMemory(rs.getObject("id", UUID.class),
                rs.getObject("media_id", UUID.class), "/api/v1/media/" + rs.getObject("media_id") + "/content",
                rs.getString("content_type"), rs.getString("title"), rs.getString("story"),
                rs.getInt("position"), rs.getObject("created_at", java.time.OffsetDateTime.class)), memorialId);
    }

    public MemorialExperience.SoundMemory addSound(UUID memorialId, UUID id, UUID mediaId, String title, String story) {
        Integer position = jdbc.queryForObject("SELECT COALESCE(MAX(position), -1) + 1 FROM memorial_sound_memories WHERE memorial_id = ?", Integer.class, memorialId);
        jdbc.update("INSERT INTO memorial_sound_memories (id, memorial_id, media_id, title, story, position) VALUES (?, ?, ?, ?, ?, ?)",
                id, memorialId, mediaId, title, story, position == null ? 0 : position);
        return sounds(memorialId).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    public Optional<UUID> deleteSound(UUID memorialId, UUID id) {
        List<UUID> ids = jdbc.query("SELECT media_id FROM memorial_sound_memories WHERE memorial_id = ? AND id = ?",
                (rs, n) -> rs.getObject(1, UUID.class), memorialId, id);
        if (ids.isEmpty()) return Optional.empty();
        jdbc.update("DELETE FROM memorial_sound_memories WHERE memorial_id = ? AND id = ?", memorialId, id);
        return Optional.of(ids.getFirst());
    }

    public List<MemorialExperience.InterviewAnswer> interviews(UUID memorialId) {
        return jdbc.query("SELECT id, prompt_key, answer, updated_at FROM memorial_interview_answers WHERE memorial_id = ? ORDER BY prompt_key",
                (rs, n) -> new MemorialExperience.InterviewAnswer(rs.getObject("id", UUID.class), rs.getString("prompt_key"),
                        rs.getString("answer"), rs.getObject("updated_at", java.time.OffsetDateTime.class)), memorialId);
    }

    public void saveInterview(UUID memorialId, UUID id, String promptKey, String answer) {
        int updated = jdbc.update("UPDATE memorial_interview_answers SET answer = ?, updated_at = CURRENT_TIMESTAMP WHERE memorial_id = ? AND prompt_key = ?",
                answer, memorialId, promptKey);
        if (updated == 0) jdbc.update("INSERT INTO memorial_interview_answers (id, memorial_id, prompt_key, answer) VALUES (?, ?, ?, ?)", id, memorialId, promptKey, answer);
    }

    public void deleteInterview(UUID memorialId, String promptKey) {
        jdbc.update("DELETE FROM memorial_interview_answers WHERE memorial_id = ? AND prompt_key = ?", memorialId, promptKey);
    }

    public List<MemorialExperience.Keepsake> keepsakes(UUID memorialId) {
        return jdbc.query("SELECT id, title, story, created_at FROM memorial_keepsakes WHERE memorial_id = ? ORDER BY created_at",
                (rs, n) -> new MemorialExperience.Keepsake(rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getString("story"), rs.getObject("created_at", java.time.OffsetDateTime.class)), memorialId);
    }

    public MemorialExperience.Keepsake addKeepsake(UUID memorialId, UUID id, String title, String story) {
        jdbc.update("INSERT INTO memorial_keepsakes (id, memorial_id, title, story) VALUES (?, ?, ?, ?)", id, memorialId, title, story);
        return keepsakes(memorialId).stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    public boolean deleteKeepsake(UUID memorialId, UUID id) {
        return jdbc.update("DELETE FROM memorial_keepsakes WHERE memorial_id = ? AND id = ?", memorialId, id) > 0;
    }

    public Optional<MemorialExperience.BurialRecord> burial(UUID memorialId) {
        return jdbc.query(BURIAL_SELECT + " WHERE b.memorial_id = ?", BURIAL_MAPPER, memorialId).stream().findFirst();
    }

    public MemorialExperience.BurialRecord saveBurial(UUID memorialId, UUID id, String dispositionType, LocalDate occurredOn,
                                                       String region, String placeName, String text) {
        int updated = jdbc.update("""
                UPDATE memorial_burial_records SET disposition_type=?, occurred_on=?, region=?, place_name=?, remembrance_text=?,
                review_status='PRIVATE', compliance_attested=FALSE, review_note=NULL, reviewed_by=NULL, reviewed_at=NULL, updated_at=CURRENT_TIMESTAMP
                WHERE memorial_id=?
                """, dispositionType, occurredOn, region, placeName, text, memorialId);
        if (updated == 0) jdbc.update("""
                INSERT INTO memorial_burial_records (id, memorial_id, disposition_type, occurred_on, region, place_name, remembrance_text)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, memorialId, dispositionType, occurredOn, region, placeName, text);
        return burial(memorialId).orElseThrow();
    }

    public boolean submitBurial(UUID memorialId) {
        return jdbc.update("""
                UPDATE memorial_burial_records SET review_status='PENDING', compliance_attested=TRUE,
                review_note=NULL, reviewed_by=NULL, reviewed_at=NULL, updated_at=CURRENT_TIMESTAMP
                WHERE memorial_id=? AND review_status IN ('PRIVATE', 'REJECTED')
                """, memorialId) > 0;
    }

    public List<MemorialExperience.BurialRecord> reviewQueue(String status) {
        String condition = "ALL".equals(status) ? "" : " WHERE b.review_status = ?";
        String sql = BURIAL_SELECT + condition + " ORDER BY b.updated_at DESC LIMIT 100";
        return "ALL".equals(status) ? jdbc.query(sql, BURIAL_MAPPER) : jdbc.query(sql, BURIAL_MAPPER, status);
    }

    public Optional<MemorialExperience.BurialRecord> moderateBurial(UUID id, String status, String note, UUID reviewer) {
        int updated = jdbc.update("""
                UPDATE memorial_burial_records SET review_status=?, review_note=?, reviewed_by=?, reviewed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND review_status='PENDING'
                """, status, note, reviewer, id);
        return updated == 0 ? Optional.empty() : jdbc.query(BURIAL_SELECT + " WHERE b.id = ?", BURIAL_MAPPER, id).stream().findFirst();
    }

    private static final String BURIAL_SELECT = """
            SELECT b.id, b.memorial_id, m.pet_name, u.display_name AS owner_display_name, b.disposition_type,
                   b.occurred_on, b.region, b.place_name, b.remembrance_text, b.review_status,
                   b.compliance_attested, b.review_note, b.reviewed_at, b.updated_at
            FROM memorial_burial_records b JOIN memorials m ON m.id=b.memorial_id JOIN users u ON u.id=m.user_id
            """;
    private static final RowMapper<MemorialExperience.BurialRecord> BURIAL_MAPPER = new BurialMapper();

    private static class BurialMapper implements RowMapper<MemorialExperience.BurialRecord> {
        public MemorialExperience.BurialRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MemorialExperience.BurialRecord(rs.getObject("id", UUID.class), rs.getObject("memorial_id", UUID.class),
                    rs.getString("pet_name"), rs.getString("owner_display_name"), rs.getString("disposition_type"),
                    rs.getObject("occurred_on", LocalDate.class), rs.getString("region"), rs.getString("place_name"),
                    rs.getString("remembrance_text"), rs.getString("review_status"), rs.getBoolean("compliance_attested"),
                    rs.getString("review_note"), rs.getObject("reviewed_at", java.time.OffsetDateTime.class),
                    rs.getObject("updated_at", java.time.OffsetDateTime.class));
        }
    }
}

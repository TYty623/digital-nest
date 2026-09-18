package com.digitalnest.petmemorial.memorial;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MemorialRepository {

    private static final RowMapper<Memorial> MEMORIAL_ROW_MAPPER = new MemorialRowMapper();
    private static final RowMapper<TributeMessage> TRIBUTE_ROW_MAPPER = new TributeRowMapper();
    private static final RowMapper<TimelineEntry> TIMELINE_ROW_MAPPER = new TimelineRowMapper();
    private static final RowMapper<MemorialLetter> LETTER_ROW_MAPPER = new MemorialLetterRowMapper();
    private static final RowMapper<GalleryItem> GALLERY_ROW_MAPPER = new GalleryItemRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public MemorialRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Serialize capacity-changing writes to one memorial inside a service transaction. */
    public void lockForOwner(UUID id, UUID userId) {
        jdbcTemplate.query("SELECT id FROM memorials WHERE id = ? AND user_id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getObject("id", UUID.class), id, userId);
    }

    public Memorial create(UUID userId, UUID id, String slug, CreateMemorialCommand command, String accessCodeHash) {
        jdbcTemplate.update("""
                        INSERT INTO memorials (id, user_id, slug, pet_name, species, cover_media_id, farewell_message, about_ta, companion_started_on, companion_ended_on, visibility, theme, access_code_hash)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, userId, slug, command.petName(), command.species(), command.coverMediaId(),
                command.farewellMessage(), command.aboutTa(), command.companionStartedOn(), command.companionEndedOn(),
                command.visibility(), command.theme(), accessCodeHash);
        return findByIdAndUserId(id, userId).orElseThrow();
    }

    public List<Memorial> findAllByUserId(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, slug, pet_name, species, cover_media_id, cover_image_url, farewell_message, about_ta, companion_started_on, companion_ended_on,
                               status, visibility, theme, access_code_hash, published_at, created_at, updated_at, version
                        FROM memorials
                        WHERE user_id = ?
                        ORDER BY updated_at DESC
                        """, MEMORIAL_ROW_MAPPER, userId);
    }

    public Optional<Memorial> findByIdAndUserId(UUID id, UUID userId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, slug, pet_name, species, cover_media_id, cover_image_url, farewell_message, about_ta, companion_started_on, companion_ended_on,
                               status, visibility, theme, access_code_hash, published_at, created_at, updated_at, version
                        FROM memorials
                        WHERE id = ? AND user_id = ?
                        """, MEMORIAL_ROW_MAPPER, id, userId).stream().findFirst();
    }

    public Optional<Memorial> findPublishedBySlug(String slug) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, slug, pet_name, species, cover_media_id, cover_image_url, farewell_message, about_ta, companion_started_on, companion_ended_on,
                               status, visibility, theme, access_code_hash, published_at, created_at, updated_at, version
                        FROM memorials
                        WHERE slug = ? AND status = 'PUBLISHED' AND visibility <> 'PRIVATE'
                          AND NOT EXISTS (
                              SELECT 1 FROM account_deletion_requests
                              WHERE account_deletion_requests.user_id = memorials.user_id
                          )
                        """, MEMORIAL_ROW_MAPPER, slug).stream().findFirst();
    }

    public List<CommunityMemorial> discoverPublic(int limit) {
        return jdbcTemplate.query("""
                SELECT m.id, m.slug, m.pet_name, m.species, m.cover_media_id, m.cover_image_url,
                       m.companion_started_on, m.companion_ended_on,
                       COALESCE(
                           (SELECT d.answer FROM memorial_life_details d
                            WHERE d.memorial_id=m.id AND d.detail_key='QUIRK'),
                           m.about_ta, m.farewell_message
                       ) AS signature,
                       (SELECT COUNT(*) FROM memorial_lights l WHERE l.memorial_id=m.id) AS light_count,
                       m.published_at
                FROM memorials m
                WHERE m.status='PUBLISHED' AND m.visibility='PUBLIC'
                  AND NOT EXISTS (
                      SELECT 1 FROM account_deletion_requests a WHERE a.user_id=m.user_id
                  )
                ORDER BY m.published_at DESC
                LIMIT ?
                """, (rs, n) -> {
                    UUID coverMediaId = rs.getObject("cover_media_id", UUID.class);
                    String coverUrl = coverMediaId == null
                            ? rs.getString("cover_image_url")
                            : "/api/v1/media/" + coverMediaId + "/content";
                    return new CommunityMemorial(
                            rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("pet_name"),
                            rs.getString("species"), coverUrl,
                            rs.getObject("companion_started_on", java.time.LocalDate.class),
                            rs.getObject("companion_ended_on", java.time.LocalDate.class),
                            rs.getString("signature"), rs.getLong("light_count"),
                            rs.getObject("published_at", java.time.OffsetDateTime.class));
                }, limit);
    }

    public Optional<Memorial> update(UUID id, UUID userId, CreateMemorialCommand command, String accessCodeHash, int expectedVersion) {
        int updated = jdbcTemplate.update("""
                        UPDATE memorials
                        SET pet_name = ?, species = ?, cover_media_id = ?, farewell_message = ?, about_ta = ?, companion_started_on = ?, companion_ended_on = ?,
                            visibility = ?, theme = ?, access_code_hash = ?, updated_at = CURRENT_TIMESTAMP, version = version + 1
                        WHERE id = ? AND user_id = ? AND version = ?
        """, command.petName(), command.species(), command.coverMediaId(), command.farewellMessage(), command.aboutTa(),
                command.companionStartedOn(), command.companionEndedOn(),
                command.visibility(), command.theme(), accessCodeHash, id, userId, expectedVersion);
        return updated == 0 ? Optional.empty() : findByIdAndUserId(id, userId);
    }

    public Memorial publish(UUID id, UUID userId) {
        jdbcTemplate.update("""
                        UPDATE memorials
                        SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND user_id = ?
                        """, id, userId);
        return findByIdAndUserId(id, userId).orElseThrow();
    }

    public Memorial archive(UUID id, UUID userId) {
        jdbcTemplate.update("""
                        UPDATE memorials
                        SET status = 'ARCHIVED', updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND user_id = ? AND status = 'PUBLISHED'
                        """, id, userId);
        return findByIdAndUserId(id, userId).orElseThrow();
    }

    public Memorial restore(UUID id, UUID userId) {
        jdbcTemplate.update("""
                        UPDATE memorials
                        SET status = 'PUBLISHED', updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND user_id = ? AND status = 'ARCHIVED'
                        """, id, userId);
        return findByIdAndUserId(id, userId).orElseThrow();
    }

    public List<TributeMessage> findTributes(UUID memorialId) {
        return jdbcTemplate.query("""
                        SELECT id, author_name, message, status, report_count, created_at, updated_at
                        FROM tribute_messages
                        WHERE memorial_id = ? AND status = 'APPROVED'
                        ORDER BY created_at ASC
                        """, TRIBUTE_ROW_MAPPER, memorialId);
    }

    public List<TributeMessage> findAllTributes(UUID memorialId) {
        return jdbcTemplate.query("""
                        SELECT id, author_name, message, status, report_count, created_at, updated_at
                        FROM tribute_messages
                        WHERE memorial_id = ?
                        ORDER BY created_at ASC
                        """, TRIBUTE_ROW_MAPPER, memorialId);
    }

    public TributeMessage addTribute(UUID memorialId, UUID id, String authorName, String message, String status) {
        jdbcTemplate.update("""
                        INSERT INTO tribute_messages (id, memorial_id, author_name, message, status)
                        VALUES (?, ?, ?, ?, ?)
                        """, id, memorialId, authorName, message, status);
        return jdbcTemplate.query("""
                        SELECT id, author_name, message, status, report_count, created_at, updated_at
                        FROM tribute_messages WHERE id = ?
                        """, TRIBUTE_ROW_MAPPER, id).getFirst();
    }

    public Optional<TributeMessage> updateTributeStatus(UUID memorialId, UUID tributeId, String status) {
        int updated = jdbcTemplate.update("""
                        UPDATE tribute_messages
                        SET status = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND memorial_id = ?
                        """, status, tributeId, memorialId);
        if (updated == 0) {
            return Optional.empty();
        }
        return findTribute(tributeId, memorialId);
    }

    public boolean reportTribute(UUID memorialId, UUID tributeId) {
        return jdbcTemplate.update("""
                        UPDATE tribute_messages
                        SET report_count = report_count + 1, status = 'REPORTED', updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND memorial_id = ? AND status = 'APPROVED'
                        """, tributeId, memorialId) > 0;
    }

    private Optional<TributeMessage> findTribute(UUID tributeId, UUID memorialId) {
        return jdbcTemplate.query("""
                        SELECT id, author_name, message, status, report_count, created_at, updated_at
                        FROM tribute_messages
                        WHERE id = ? AND memorial_id = ?
                        """, TRIBUTE_ROW_MAPPER, tributeId, memorialId).stream().findFirst();
    }

    public List<TimelineEntry> findTimelineEntries(UUID memorialId) {
        return jdbcTemplate.query("""
                        SELECT timeline.id, timeline.memorial_id, timeline.media_id,
                               media.content_type AS media_content_type, timeline.event_date, timeline.date_precision,
                               timeline.title, timeline.body, timeline.position, timeline.created_at, timeline.updated_at
                        FROM memorial_timeline_entries timeline
                        LEFT JOIN media_assets media ON media.id = timeline.media_id
                        WHERE timeline.memorial_id = ?
                        ORDER BY timeline.position ASC, timeline.event_date ASC, timeline.created_at ASC
                        """, TIMELINE_ROW_MAPPER, memorialId);
    }

    public TimelineEntry addTimelineEntry(UUID memorialId, UUID id, TimelineEntryCommand command) {
        Integer nextPosition = jdbcTemplate.queryForObject("""
                        SELECT COALESCE(MAX(position), -1) + 1
                        FROM memorial_timeline_entries
                        WHERE memorial_id = ?
                        """, Integer.class, memorialId);
        jdbcTemplate.update("""
                        INSERT INTO memorial_timeline_entries (id, memorial_id, media_id, event_date, date_precision, title, body, position)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, id, memorialId, command.mediaId(), command.eventDate(), command.datePrecision(), command.title(), command.body(), nextPosition);
        touchMemorial(memorialId);
        return findTimelineEntry(id, memorialId).orElseThrow();
    }

    public Optional<TimelineEntry> updateTimelineEntry(UUID memorialId, UUID entryId, TimelineEntryCommand command) {
        int updated = jdbcTemplate.update("""
                        UPDATE memorial_timeline_entries
                        SET media_id = ?, event_date = ?, date_precision = ?, title = ?, body = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND memorial_id = ?
                        """, command.mediaId(), command.eventDate(), command.datePrecision(), command.title(), command.body(), entryId, memorialId);
        if (updated == 0) {
            return Optional.empty();
        }
        touchMemorial(memorialId);
        return findTimelineEntry(entryId, memorialId);
    }

    public boolean deleteTimelineEntry(UUID memorialId, UUID entryId) {
        int deleted = jdbcTemplate.update("DELETE FROM memorial_timeline_entries WHERE id = ? AND memorial_id = ?", entryId, memorialId);
        if (deleted > 0) {
            touchMemorial(memorialId);
        }
        return deleted > 0;
    }

    public void updateTimelinePosition(UUID memorialId, UUID entryId, int position) {
        jdbcTemplate.update("""
                        UPDATE memorial_timeline_entries
                        SET position = ?
                        WHERE id = ? AND memorial_id = ?
                        """, position, entryId, memorialId);
    }

    public void touchTimelineOrder(UUID memorialId) {
        touchMemorial(memorialId);
    }

    public boolean hasTimelineMedia(UUID memorialId, UUID mediaId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memorial_timeline_entries WHERE memorial_id = ? AND media_id = ?",
                Integer.class, memorialId, mediaId);
        return count != null && count > 0;
    }

    public Optional<MemorialLetter> findLetter(UUID memorialId) {
        return jdbcTemplate.query("""
                        SELECT id, memorial_id, subject, body, created_at, updated_at
                        FROM memorial_letters
                        WHERE memorial_id = ?
                        """, LETTER_ROW_MAPPER, memorialId).stream().findFirst();
    }

    public List<GalleryItem> findGalleryItems(UUID memorialId) {
        return jdbcTemplate.query("""
                        SELECT gallery.id, gallery.memorial_id, gallery.media_id, media.content_type AS media_content_type,
                               gallery.caption, gallery.position, gallery.created_at
                        FROM memorial_gallery_items gallery
                        JOIN media_assets media ON media.id = gallery.media_id
                        WHERE gallery.memorial_id = ?
                        ORDER BY gallery.position ASC, gallery.created_at ASC
                        """, GALLERY_ROW_MAPPER, memorialId);
    }

    public GalleryItem addGalleryItem(UUID memorialId, UUID id, UUID mediaId, String caption) {
        Integer nextPosition = jdbcTemplate.queryForObject("""
                        SELECT COALESCE(MAX(position), -1) + 1
                        FROM memorial_gallery_items
                        WHERE memorial_id = ?
                        """, Integer.class, memorialId);
        jdbcTemplate.update("""
                        INSERT INTO memorial_gallery_items (id, memorial_id, media_id, caption, position)
                        VALUES (?, ?, ?, ?, ?)
                        """, id, memorialId, mediaId, caption, nextPosition);
        touchMemorial(memorialId);
        return findGalleryItem(id, memorialId).orElseThrow();
    }

    public boolean hasGalleryMedia(UUID memorialId, UUID mediaId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memorial_gallery_items WHERE memorial_id = ? AND media_id = ?",
                Integer.class, memorialId, mediaId);
        return count != null && count > 0;
    }

    public int countGalleryItemsByContentTypePrefix(UUID memorialId, String contentTypePrefix) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM memorial_gallery_items gallery
                        JOIN media_assets media ON media.id = gallery.media_id
                        WHERE gallery.memorial_id = ? AND media.content_type LIKE ?
                        """, Integer.class, memorialId, contentTypePrefix + "%");
        return count == null ? 0 : count;
    }

    public boolean deleteGalleryItem(UUID memorialId, UUID galleryItemId) {
        int deleted = jdbcTemplate.update("DELETE FROM memorial_gallery_items WHERE id = ? AND memorial_id = ?", galleryItemId, memorialId);
        if (deleted > 0) {
            touchMemorial(memorialId);
        }
        return deleted > 0;
    }

    public Optional<GalleryItem> updateGalleryCaption(UUID memorialId, UUID galleryItemId, String caption) {
        int updated = jdbcTemplate.update("""
                        UPDATE memorial_gallery_items
                        SET caption = ?
                        WHERE id = ? AND memorial_id = ?
                        """, caption, galleryItemId, memorialId);
        if (updated > 0) {
            touchMemorial(memorialId);
        }
        return updated == 0 ? Optional.empty() : findGalleryItem(galleryItemId, memorialId);
    }

    public void updateGalleryPosition(UUID memorialId, UUID galleryItemId, int position) {
        jdbcTemplate.update("""
                        UPDATE memorial_gallery_items
                        SET position = ?
                        WHERE id = ? AND memorial_id = ?
                        """, position, galleryItemId, memorialId);
    }

    public void touchGalleryOrder(UUID memorialId) {
        touchMemorial(memorialId);
    }

    private Optional<GalleryItem> findGalleryItem(UUID id, UUID memorialId) {
        return jdbcTemplate.query("""
                        SELECT gallery.id, gallery.memorial_id, gallery.media_id, media.content_type AS media_content_type,
                               gallery.caption, gallery.position, gallery.created_at
                        FROM memorial_gallery_items gallery
                        JOIN media_assets media ON media.id = gallery.media_id
                        WHERE gallery.id = ? AND gallery.memorial_id = ?
                        """, GALLERY_ROW_MAPPER, id, memorialId).stream().findFirst();
    }

    public MemorialLetter saveLetter(UUID memorialId, UUID id, String subject, String body) {
        int updated = jdbcTemplate.update("""
                        UPDATE memorial_letters
                        SET subject = ?, body = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE memorial_id = ?
                        """, subject, body, memorialId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO memorial_letters (id, memorial_id, subject, body)
                            VALUES (?, ?, ?, ?)
                            """, id, memorialId, subject, body);
        }
        touchMemorial(memorialId);
        return findLetter(memorialId).orElseThrow();
    }

    public Optional<TimelineEntry> findTimelineEntry(UUID entryId, UUID memorialId) {
        return jdbcTemplate.query("""
                        SELECT timeline.id, timeline.memorial_id, timeline.media_id,
                               media.content_type AS media_content_type, timeline.event_date, timeline.date_precision,
                               timeline.title, timeline.body, timeline.position, timeline.created_at, timeline.updated_at
                        FROM memorial_timeline_entries timeline
                        LEFT JOIN media_assets media ON media.id = timeline.media_id
                        WHERE timeline.id = ? AND timeline.memorial_id = ?
                        """, TIMELINE_ROW_MAPPER, entryId, memorialId).stream().findFirst();
    }

    private void touchMemorial(UUID memorialId) {
        jdbcTemplate.update("UPDATE memorials SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", memorialId);
    }

    public record CreateMemorialCommand(
            String petName,
            String species,
            UUID coverMediaId,
            String farewellMessage,
            String aboutTa,
            java.time.LocalDate companionStartedOn,
            java.time.LocalDate companionEndedOn,
            String visibility,
            String theme,
            String accessCode
    ) {
    }

    public record TimelineEntryCommand(
            UUID mediaId,
            java.time.LocalDate eventDate,
            String datePrecision,
            String title,
            String body
    ) {
    }

    private static final class MemorialRowMapper implements RowMapper<Memorial> {
        @Override
        public Memorial mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new Memorial(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("user_id", UUID.class),
                    resultSet.getString("slug"),
                    resultSet.getString("pet_name"),
                    resultSet.getString("species"),
                    resultSet.getObject("cover_media_id", UUID.class),
                    resultSet.getString("cover_image_url"),
                    resultSet.getString("farewell_message"),
                    resultSet.getString("about_ta"),
                    resultSet.getObject("companion_started_on", java.time.LocalDate.class),
                    resultSet.getObject("companion_ended_on", java.time.LocalDate.class),
                    resultSet.getString("status"),
                    resultSet.getString("visibility"),
                    resultSet.getString("theme"),
                    resultSet.getString("access_code_hash"),
                    resultSet.getObject("published_at", java.time.OffsetDateTime.class),
                    resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                    resultSet.getObject("updated_at", java.time.OffsetDateTime.class),
                    resultSet.getInt("version")
            );
        }
    }

    private static final class TributeRowMapper implements RowMapper<TributeMessage> {
        @Override
        public TributeMessage mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new TributeMessage(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("author_name"),
                    resultSet.getString("message"),
                    resultSet.getString("status"),
                    resultSet.getInt("report_count"),
                    resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                    resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
            );
        }
    }

    private static final class TimelineRowMapper implements RowMapper<TimelineEntry> {
        @Override
        public TimelineEntry mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new TimelineEntry(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("memorial_id", UUID.class),
                    resultSet.getObject("media_id", UUID.class),
                    resultSet.getString("media_content_type"),
                    resultSet.getObject("event_date", java.time.LocalDate.class),
                    resultSet.getString("date_precision"),
                    resultSet.getString("title"),
                    resultSet.getString("body"),
                    resultSet.getInt("position"),
                    resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                    resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
            );
        }
    }

    private static final class MemorialLetterRowMapper implements RowMapper<MemorialLetter> {
        @Override
        public MemorialLetter mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new MemorialLetter(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("memorial_id", UUID.class),
                    resultSet.getString("subject"),
                    resultSet.getString("body"),
                    resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                    resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
            );
        }
    }

    private static final class GalleryItemRowMapper implements RowMapper<GalleryItem> {
        @Override
        public GalleryItem mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new GalleryItem(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("memorial_id", UUID.class),
                    resultSet.getObject("media_id", UUID.class),
                    resultSet.getString("media_content_type"),
                    resultSet.getString("caption"),
                    resultSet.getInt("position"),
                    resultSet.getObject("created_at", java.time.OffsetDateTime.class)
            );
        }
    }
}

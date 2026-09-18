package com.digitalnest.petmemorial.media;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MediaRepository {

    private static final RowMapper<MediaAsset> MEDIA_ASSET_ROW_MAPPER = new MediaAssetRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public MediaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MediaAsset create(UUID ownerId, UUID id, LocalMediaStorage.StoredMedia media) {
        jdbcTemplate.update("""
                        INSERT INTO media_assets (id, owner_id, storage_filename, content_type, byte_size)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                id, ownerId, media.filename(), media.contentType(), media.byteSize());
        return findById(id).orElseThrow();
    }

    public Optional<MediaAsset> findById(UUID id) {
        return jdbcTemplate.query("""
                        SELECT id, owner_id, storage_filename, content_type, byte_size, created_at
                        FROM media_assets
                        WHERE id = ?
                        """, MEDIA_ASSET_ROW_MAPPER, id).stream().findFirst();
    }

    public List<MediaAsset> findAllByOwnerId(UUID ownerId) {
        return jdbcTemplate.query("""
                        SELECT id, owner_id, storage_filename, content_type, byte_size, created_at
                        FROM media_assets
                        WHERE owner_id = ?
                        ORDER BY created_at ASC
                        """, MEDIA_ASSET_ROW_MAPPER, ownerId);
    }

    public boolean deleteIfUnreferenced(UUID mediaId, UUID ownerId) {
        return jdbcTemplate.update("""
                        DELETE FROM media_assets
                        WHERE id = ? AND owner_id = ?
                          AND NOT EXISTS (SELECT 1 FROM memorials WHERE cover_media_id = ?)
                          AND NOT EXISTS (SELECT 1 FROM memorial_gallery_items WHERE media_id = ?)
                          AND NOT EXISTS (SELECT 1 FROM memorial_timeline_entries WHERE media_id = ?)
                          AND NOT EXISTS (SELECT 1 FROM memorial_sound_memories WHERE media_id = ?)
                        """, mediaId, ownerId, mediaId, mediaId, mediaId, mediaId) > 0;
    }

    public boolean isPubliclyReadable(UUID mediaId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(DISTINCT memorials.id)
                        FROM memorials
                        LEFT JOIN memorial_gallery_items ON memorial_gallery_items.memorial_id = memorials.id
                        LEFT JOIN memorial_timeline_entries ON memorial_timeline_entries.memorial_id = memorials.id
                        LEFT JOIN memorial_sound_memories ON memorial_sound_memories.memorial_id = memorials.id
                        WHERE (memorials.cover_media_id = ? OR memorial_gallery_items.media_id = ? OR memorial_timeline_entries.media_id = ? OR memorial_sound_memories.media_id = ?)
                          AND memorials.status = 'PUBLISHED'
                          AND memorials.visibility IN ('PUBLIC', 'LINK')
                          AND NOT EXISTS (
                              SELECT 1 FROM account_deletion_requests
                              WHERE account_deletion_requests.user_id = memorials.user_id
                          )
                        """, Integer.class, mediaId, mediaId, mediaId, mediaId);
        return count != null && count > 0;
    }

    public Optional<UUID> findPasswordMemorialId(UUID mediaId) {
        return jdbcTemplate.query("""
                        SELECT DISTINCT memorials.id
                        FROM memorials
                        LEFT JOIN memorial_gallery_items ON memorial_gallery_items.memorial_id = memorials.id
                        LEFT JOIN memorial_timeline_entries ON memorial_timeline_entries.memorial_id = memorials.id
                        LEFT JOIN memorial_sound_memories ON memorial_sound_memories.memorial_id = memorials.id
                        WHERE (memorials.cover_media_id = ? OR memorial_gallery_items.media_id = ? OR memorial_timeline_entries.media_id = ? OR memorial_sound_memories.media_id = ?)
                          AND memorials.status = 'PUBLISHED'
                          AND memorials.visibility = 'PASSWORD'
                          AND NOT EXISTS (
                              SELECT 1 FROM account_deletion_requests
                              WHERE account_deletion_requests.user_id = memorials.user_id
                          )
                        """, (resultSet, rowNum) -> resultSet.getObject("id", UUID.class), mediaId, mediaId, mediaId, mediaId)
                .stream().findFirst();
    }

    private static final class MediaAssetRowMapper implements RowMapper<MediaAsset> {
        @Override
        public MediaAsset mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new MediaAsset(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("owner_id", UUID.class),
                    resultSet.getString("storage_filename"),
                    resultSet.getString("content_type"),
                    resultSet.getLong("byte_size"),
                    resultSet.getObject("created_at", java.time.OffsetDateTime.class)
            );
        }
    }
}

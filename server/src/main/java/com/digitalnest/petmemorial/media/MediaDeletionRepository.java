package com.digitalnest.petmemorial.media;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MediaDeletionRepository {

    private static final RowMapper<MediaDeletionRequest> ROW_MAPPER = new MediaDeletionRequestRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public MediaDeletionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void enqueue(String storageFilename, OffsetDateTime purgeAfter) {
        jdbcTemplate.update("""
                        INSERT INTO media_deletion_queue (id, storage_filename, purge_after)
                        VALUES (?, ?, ?)
                        """, UUID.randomUUID(), storageFilename, purgeAfter);
    }

    public List<MediaDeletionRequest> findDue(OffsetDateTime now) {
        return jdbcTemplate.query("""
                        SELECT id, storage_filename, purge_after
                        FROM media_deletion_queue
                        WHERE purge_after <= ?
                        ORDER BY purge_after ASC
                        """, ROW_MAPPER, now);
    }

    public void delete(UUID id) {
        jdbcTemplate.update("DELETE FROM media_deletion_queue WHERE id = ?", id);
    }

    private static final class MediaDeletionRequestRowMapper implements RowMapper<MediaDeletionRequest> {
        @Override
        public MediaDeletionRequest mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new MediaDeletionRequest(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("storage_filename"),
                    resultSet.getObject("purge_after", OffsetDateTime.class)
            );
        }
    }
}

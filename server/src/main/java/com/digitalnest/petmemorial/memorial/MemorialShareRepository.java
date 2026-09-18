package com.digitalnest.petmemorial.memorial;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Stores only owner-initiated sharing actions needed for aggregate product metrics. */
@Repository
public class MemorialShareRepository {

    private final JdbcTemplate jdbcTemplate;

    public MemorialShareRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(UUID memorialId, UUID ownerId, String eventType) {
        jdbcTemplate.update("""
                        INSERT INTO memorial_share_events (id, memorial_id, owner_id, event_type)
                        VALUES (?, ?, ?, ?)
                        """,
                UUID.randomUUID(), memorialId, ownerId, eventType);
    }
}

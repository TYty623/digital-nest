package com.digitalnest.petmemorial.memorial;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Stores daily, anonymous unique visits. The caller supplies a one-way visitor fingerprint. */
@Repository
public class MemorialVisitRepository {

    private final JdbcTemplate jdbcTemplate;

    public MemorialVisitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordDailyUniqueVisit(UUID memorialId, String visitorFingerprint, LocalDate visitedOn) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO memorial_visit_events (id, memorial_id, visitor_fingerprint, visited_on)
                            VALUES (?, ?, ?, ?)
                            """,
                    UUID.randomUUID(), memorialId, visitorFingerprint, visitedOn);
        } catch (DataIntegrityViolationException ignored) {
            // The unique constraint is the concurrency-safe daily deduplication boundary.
        }
    }
}

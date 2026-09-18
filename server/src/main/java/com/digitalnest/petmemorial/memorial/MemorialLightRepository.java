package com.digitalnest.petmemorial.memorial;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MemorialLightRepository {

    private final JdbcTemplate jdbcTemplate;

    public MemorialLightRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LightResult addLight(UUID memorialId, String visitorFingerprint, LocalDate windowDate) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO memorial_lights (id, memorial_id, visitor_fingerprint, window_date)
                            VALUES (?, ?, ?, ?)
                            """,
                    UUID.randomUUID(), memorialId, visitorFingerprint, windowDate);
            return new LightResult(true, countByMemorialId(memorialId));
        } catch (DataIntegrityViolationException exception) {
            return new LightResult(false, countByMemorialId(memorialId));
        }
    }

    public long countByMemorialId(UUID memorialId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM memorial_lights WHERE memorial_id = ?", Long.class, memorialId);
        return count == null ? 0 : count;
    }

    public record LightResult(boolean lit, long count) {
    }
}

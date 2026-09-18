package com.digitalnest.petmemorial.digitallife;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DigitalLifeRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<DigitalLifeProfile> profileMapper = this::mapProfile;
    private final RowMapper<DigitalLifeFact> factMapper = this::mapFact;

    public DigitalLifeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<DigitalLifeProfile> profile(UUID memorialId) {
        return jdbc.query("SELECT * FROM digital_life_profiles WHERE memorial_id=?", profileMapper, memorialId).stream().findFirst();
    }

    public DigitalLifeProfile saveProfile(UUID memorialId, UUID userId, String consentVersion, OffsetDateTime now) {
        int updated = jdbc.update("""
                UPDATE digital_life_profiles SET user_id=?, status='ACTIVE', profile_consent_at=?, text_processing_consent_at=?,
                consent_version=?, deleted_at=NULL, updated_at=CURRENT_TIMESTAMP WHERE memorial_id=?
                """, userId, now, now, consentVersion, memorialId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO digital_life_profiles (id, memorial_id, user_id, profile_consent_at, text_processing_consent_at, consent_version)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), memorialId, userId, now, now, consentVersion);
        }
        return profile(memorialId).orElseThrow();
    }

    public Optional<DigitalLifeProfile> updateStatus(UUID memorialId, String status) {
        int updated = jdbc.update("UPDATE digital_life_profiles SET status=?, updated_at=CURRENT_TIMESTAMP WHERE memorial_id=?", status, memorialId);
        return updated == 0 ? Optional.empty() : profile(memorialId);
    }

    public void deleteProfile(UUID memorialId) {
        jdbc.update("DELETE FROM digital_life_profiles WHERE memorial_id=?", memorialId);
    }

    public List<DigitalLifeFact> facts(UUID profileId) {
        return jdbc.query("SELECT * FROM digital_life_facts WHERE profile_id=? ORDER BY created_at ASC", factMapper, profileId);
    }

    public DigitalLifeFact createFact(UUID profileId, UUID id, FactCommand command) {
        jdbc.update("""
                INSERT INTO digital_life_facts (id, profile_id, fact_type, statement, source_type, source_label, verification_status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, profileId, command.factType(), command.statement(), command.sourceType(), command.sourceLabel(), command.verificationStatus());
        return jdbc.query("SELECT * FROM digital_life_facts WHERE id=?", factMapper, id).getFirst();
    }

    public Optional<DigitalLifeFact> updateFactStatus(UUID profileId, UUID factId, String status) {
        int updated = jdbc.update("UPDATE digital_life_facts SET verification_status=?, updated_at=CURRENT_TIMESTAMP WHERE profile_id=? AND id=?", status, profileId, factId);
        return updated == 0 ? Optional.empty() : jdbc.query("SELECT * FROM digital_life_facts WHERE id=?", factMapper, factId).stream().findFirst();
    }

    public boolean deleteFact(UUID profileId, UUID factId) {
        return jdbc.update("DELETE FROM digital_life_facts WHERE profile_id=? AND id=?", profileId, factId) > 0;
    }

    private DigitalLifeProfile mapProfile(ResultSet rs, int rowNum) throws SQLException {
        return new DigitalLifeProfile(rs.getObject("id", UUID.class), rs.getObject("memorial_id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("status"), rs.getObject("profile_consent_at", OffsetDateTime.class), rs.getObject("text_processing_consent_at", OffsetDateTime.class),
                rs.getString("consent_version"), rs.getObject("deleted_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }

    private DigitalLifeFact mapFact(ResultSet rs, int rowNum) throws SQLException {
        return new DigitalLifeFact(rs.getObject("id", UUID.class), rs.getObject("profile_id", UUID.class), rs.getString("fact_type"),
                rs.getString("statement"), rs.getString("source_type"), rs.getString("source_label"), rs.getString("verification_status"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }

    public record FactCommand(String factType, String statement, String sourceType, String sourceLabel, String verificationStatus) {}
}

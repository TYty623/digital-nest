package com.digitalnest.petmemorial.account;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountExportTokenRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountExportTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void issue(UUID id, UUID userId, String tokenHash, OffsetDateTime expiresAt) {
        jdbcTemplate.update("""
                        INSERT INTO account_export_download_tokens (id, user_id, token_hash, expires_at)
                        VALUES (?, ?, ?, ?)
                        """, id, userId, tokenHash, expiresAt);
    }

    public boolean consume(UUID userId, String tokenHash, OffsetDateTime now) {
        return jdbcTemplate.update("""
                        UPDATE account_export_download_tokens
                        SET consumed_at = ?
                        WHERE user_id = ?
                          AND token_hash = ?
                          AND consumed_at IS NULL
                          AND expires_at > ?
                        """, now, userId, tokenHash, now) > 0;
    }

    public void deleteExpiredBefore(OffsetDateTime threshold) {
        jdbcTemplate.update("""
                        DELETE FROM account_export_download_tokens
                        WHERE expires_at < ?
                        """, threshold);
    }
}

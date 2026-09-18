package com.digitalnest.petmemorial.account;

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
public class AccountDeletionRepository {

    private static final RowMapper<AccountDeletionRequest> ROW_MAPPER = new AccountDeletionRequestRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public AccountDeletionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AccountDeletionRequest request(UUID userId, OffsetDateTime scheduledFor) {
        int updated = jdbcTemplate.update("""
                        UPDATE account_deletion_requests
                        SET requested_at = CURRENT_TIMESTAMP, scheduled_for = ?
                        WHERE user_id = ?
                        """, scheduledFor, userId);
        if (updated == 0) {
            jdbcTemplate.update("""
                            INSERT INTO account_deletion_requests (user_id, scheduled_for)
                            VALUES (?, ?)
                            """, userId, scheduledFor);
        }
        return findByUserId(userId).orElseThrow();
    }

    public Optional<AccountDeletionRequest> findByUserId(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT user_id, requested_at, scheduled_for
                        FROM account_deletion_requests
                        WHERE user_id = ?
                        """, ROW_MAPPER, userId).stream().findFirst();
    }

    public boolean cancelBeforeDue(UUID userId, OffsetDateTime now) {
        return jdbcTemplate.update("""
                        DELETE FROM account_deletion_requests
                        WHERE user_id = ? AND scheduled_for > ?
                        """, userId, now) > 0;
    }

    public boolean consumeDue(UUID userId, OffsetDateTime now) {
        return jdbcTemplate.update("""
                        DELETE FROM account_deletion_requests
                        WHERE user_id = ? AND scheduled_for <= ?
                        """, userId, now) > 0;
    }

    public List<UUID> findDueUserIds(OffsetDateTime now) {
        return jdbcTemplate.query("""
                        SELECT user_id
                        FROM account_deletion_requests
                        WHERE scheduled_for <= ?
                        ORDER BY scheduled_for ASC
                        """, (resultSet, rowNum) -> resultSet.getObject("user_id", UUID.class), now);
    }

    private static final class AccountDeletionRequestRowMapper implements RowMapper<AccountDeletionRequest> {
        @Override
        public AccountDeletionRequest mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new AccountDeletionRequest(
                    resultSet.getObject("user_id", UUID.class),
                    resultSet.getObject("requested_at", OffsetDateTime.class),
                    resultSet.getObject("scheduled_for", OffsetDateTime.class)
            );
        }
    }
}

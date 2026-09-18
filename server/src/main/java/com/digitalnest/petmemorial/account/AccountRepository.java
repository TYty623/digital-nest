package com.digitalnest.petmemorial.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

    private static final RowMapper<UserAccount> ACCOUNT_ROW_MAPPER = new AccountRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public AccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbcTemplate.query("""
                        SELECT id, email, password_hash, display_name, status
                        FROM users
                        WHERE email = ?
                        """, ACCOUNT_ROW_MAPPER, email)
                .stream()
                .findFirst();
    }

    public Optional<UserAccount> findById(UUID id) {
        return jdbcTemplate.query("""
                        SELECT id, email, password_hash, display_name, status
                        FROM users
                        WHERE id = ?
                        """, ACCOUNT_ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    public UserAccount create(UUID id, String email, String passwordHash, String displayName) {
        jdbcTemplate.update("""
                        INSERT INTO users (id, email, password_hash, display_name, status)
                        VALUES (?, ?, ?, ?, 'ACTIVE')
                        """, id, email, passwordHash, displayName);
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role) VALUES (?, 'USER')", id);
        return new UserAccount(id, email, passwordHash, displayName, "ACTIVE");
    }

    public boolean deleteById(UUID id) {
        return jdbcTemplate.update("DELETE FROM users WHERE id = ?", id) > 0;
    }

    public Set<String> findRolesByUserId(UUID userId) {
        return Set.copyOf(jdbcTemplate.queryForList(
                "SELECT role FROM user_roles WHERE user_id = ? ORDER BY role ASC", String.class, userId));
    }

    public void grantRoleIfMissing(UUID userId, String role) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role = ?", Integer.class, userId, role);
        if (existing == null || existing == 0) {
            jdbcTemplate.update("INSERT INTO user_roles (user_id, role) VALUES (?, ?)", userId, role);
        }
    }

    private static final class AccountRowMapper implements RowMapper<UserAccount> {
        @Override
        public UserAccount mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new UserAccount(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("email"),
                    resultSet.getString("password_hash"),
                    resultSet.getString("display_name"),
                    resultSet.getString("status")
            );
        }
    }
}

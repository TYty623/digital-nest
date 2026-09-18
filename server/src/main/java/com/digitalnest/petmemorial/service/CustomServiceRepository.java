package com.digitalnest.petmemorial.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CustomServiceRepository {

    private static final RowMapper<CustomServiceRequest> REQUEST_ROW_MAPPER = new RequestRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public CustomServiceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CustomServiceRequest create(UUID id, UUID userId, String contactDetails, String requestDetails) {
        jdbcTemplate.update("""
                        INSERT INTO custom_service_requests (id, user_id, contact_details, request_details, status)
                        VALUES (?, ?, ?, ?, 'SUBMITTED')
                        """, id, userId, contactDetails, requestDetails);
        return findByIdAndUserId(id, userId).orElseThrow();
    }

    public List<CustomServiceRequest> findByUserId(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, contact_details, request_details, status, materials_ready, assignee, due_date,
                               revision_count, customer_message, created_at, updated_at
                        FROM custom_service_requests
                        WHERE user_id = ?
                        ORDER BY updated_at DESC, created_at DESC
                        """, REQUEST_ROW_MAPPER, userId);
    }

    public Optional<CustomServiceRequest> findByIdAndUserId(UUID requestId, UUID userId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, contact_details, request_details, status, materials_ready, assignee, due_date,
                               revision_count, customer_message, created_at, updated_at
                        FROM custom_service_requests
                        WHERE id = ? AND user_id = ?
                        """, REQUEST_ROW_MAPPER, requestId, userId).stream().findFirst();
    }

    public Optional<CustomServiceRequest> completeDelivery(UUID requestId, UUID userId) {
        int updated = jdbcTemplate.update("""
                        UPDATE custom_service_requests
                        SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND user_id = ? AND status = 'DELIVERED'
                        """, requestId, userId);
        return updated == 0 ? Optional.empty() : findByIdAndUserId(requestId, userId);
    }

    public Optional<CustomServiceRequest> updateByStaff(
            UUID requestId,
            String status,
            boolean materialsReady,
            String assignee,
            LocalDate dueDate,
            int revisionCount,
            String customerMessage
    ) {
        int updated = jdbcTemplate.update("""
                        UPDATE custom_service_requests
                        SET status = ?, materials_ready = ?, assignee = ?, due_date = ?, revision_count = ?,
                            customer_message = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """, status, materialsReady, assignee, dueDate, revisionCount, customerMessage, requestId);
        return updated == 0 ? Optional.empty() : findById(requestId);
    }

    public Optional<CustomServiceRequest> findById(UUID requestId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, contact_details, request_details, status, materials_ready, assignee, due_date,
                               revision_count, customer_message, created_at, updated_at
                        FROM custom_service_requests
                        WHERE id = ?
                        """, REQUEST_ROW_MAPPER, requestId).stream().findFirst();
    }

    private static final class RequestRowMapper implements RowMapper<CustomServiceRequest> {
        @Override
        public CustomServiceRequest mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new CustomServiceRequest(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("user_id", UUID.class),
                    resultSet.getString("contact_details"),
                    resultSet.getString("request_details"),
                    resultSet.getString("status"),
                    resultSet.getBoolean("materials_ready"),
                    resultSet.getString("assignee"),
                    resultSet.getObject("due_date", LocalDate.class),
                    resultSet.getInt("revision_count"),
                    resultSet.getString("customer_message"),
                    resultSet.getObject("created_at", java.time.OffsetDateTime.class),
                    resultSet.getObject("updated_at", java.time.OffsetDateTime.class)
            );
        }
    }
}

package com.digitalnest.petmemorial.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRepository {

    private static final RowMapper<ModerationTribute> TRIBUTE_ROW_MAPPER = new ModerationTributeRowMapper();
    private static final RowMapper<AdminAuditLog> AUDIT_ROW_MAPPER = new AuditLogRowMapper();
    private static final RowMapper<AdminOrder> ORDER_ROW_MAPPER = new AdminOrderRowMapper();
    private static final RowMapper<AdminCustomServiceRequest> CUSTOM_SERVICE_ROW_MAPPER = new AdminCustomServiceRequestRowMapper();
    private static final RowMapper<AdminMemorial> MEMORIAL_ROW_MAPPER = new AdminMemorialRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public AdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminOverview overview() {
        LocalDate today = LocalDate.now();
        OffsetDateTime sevenDaysAgo = OffsetDateTime.now().minusDays(7);
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        return new AdminOverview(
                count("SELECT COUNT(*) FROM tribute_messages WHERE status = 'PENDING'"),
                count("SELECT COUNT(*) FROM tribute_messages WHERE status = 'REPORTED'"),
                count("SELECT COUNT(*) FROM memorials WHERE status = 'PUBLISHED'"),
                count("SELECT COUNT(*) FROM billing_orders WHERE status = 'PENDING'"),
                count("SELECT COUNT(*) FROM account_deletion_requests"),
                count("SELECT COUNT(*) FROM custom_service_requests WHERE status NOT IN ('COMPLETED', 'CANCELED', 'OUT_OF_SCOPE')"),
                count("SELECT COUNT(*) FROM memorial_visit_events WHERE visited_on = ?", today),
                count("SELECT COUNT(*) FROM memorial_visit_events WHERE visited_on >= ?", today.minusDays(6)),
                count("SELECT COUNT(*) FROM memorials WHERE created_at >= ?", sevenDaysAgo),
                count("SELECT COUNT(*) FROM memorials WHERE status = 'PUBLISHED' AND published_at >= ?", sevenDaysAgo),
                count("SELECT COUNT(*) FROM memorial_share_events WHERE created_at >= ?", sevenDaysAgo),
                count("SELECT COUNT(*) FROM billing_orders WHERE status = 'PAID' AND paid_at >= ?", thirtyDaysAgo),
                sum("SELECT COALESCE(SUM(byte_size), 0) FROM media_assets")
        );
    }

    public List<ModerationTribute> findTributes(String status, int limit) {
        String select = """
                SELECT t.id, t.memorial_id, m.slug, m.pet_name, owner.display_name AS owner_display_name,
                       t.author_name, t.message, t.status, t.report_count, t.created_at, t.updated_at
                FROM tribute_messages t
                JOIN memorials m ON m.id = t.memorial_id
                JOIN users owner ON owner.id = m.user_id
                """;
        String order = " ORDER BY t.updated_at DESC, t.created_at DESC LIMIT ?";
        if ("ALL".equals(status)) {
            return jdbcTemplate.query(select + order, TRIBUTE_ROW_MAPPER, limit);
        }
        return jdbcTemplate.query(select + " WHERE t.status = ?" + order, TRIBUTE_ROW_MAPPER, status, limit);
    }

    public Optional<ModerationTribute> updateTributeStatus(UUID tributeId, String status) {
        int updated = jdbcTemplate.update("""
                        UPDATE tribute_messages
                        SET status = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """, status, tributeId);
        return updated == 0 ? Optional.empty() : findTribute(tributeId);
    }

    public List<AdminOrder> findOrders(int limit) {
        return jdbcTemplate.query("""
                        SELECT o.id, o.plan_code, o.plan_name, o.amount_cents, o.currency, o.status,
                               o.payment_reference, o.paid_at, o.refunded_at, o.created_at,
                               u.id AS owner_id, u.display_name AS owner_display_name, u.email AS owner_email
                        FROM billing_orders o
                        JOIN users u ON u.id = o.user_id
                        ORDER BY o.created_at DESC
                        LIMIT ?
                """, ORDER_ROW_MAPPER, limit);
    }

    public List<AdminCustomServiceRequest> findCustomServiceRequests(String status, int limit) {
        String select = """
                SELECT r.id, r.user_id, u.display_name AS owner_display_name, u.email AS owner_email,
                       r.contact_details, r.request_details, r.status, r.materials_ready, r.assignee, r.due_date,
                       r.revision_count, r.customer_message, r.created_at, r.updated_at
                FROM custom_service_requests r
                JOIN users u ON u.id = r.user_id
                """;
        String order = " ORDER BY r.updated_at DESC, r.created_at DESC LIMIT ?";
        if ("ALL".equals(status)) {
            return jdbcTemplate.query(select + order, CUSTOM_SERVICE_ROW_MAPPER, limit);
        }
        if ("OPEN".equals(status)) {
            return jdbcTemplate.query(select + " WHERE r.status NOT IN ('COMPLETED', 'CANCELED', 'OUT_OF_SCOPE')" + order,
                    CUSTOM_SERVICE_ROW_MAPPER, limit);
        }
        return jdbcTemplate.query(select + " WHERE r.status = ?" + order, CUSTOM_SERVICE_ROW_MAPPER, status, limit);
    }

    public List<AdminMemorial> findMemorials(String status, int limit) {
        String select = """
                SELECT m.id, m.slug, m.pet_name, m.species, m.farewell_message, m.status, m.visibility,
                       m.published_at, m.created_at, m.updated_at,
                       owner.id AS owner_id, owner.display_name AS owner_display_name
                FROM memorials m
                JOIN users owner ON owner.id = m.user_id
                """;
        String order = " ORDER BY m.updated_at DESC, m.created_at DESC LIMIT ?";
        if ("ALL".equals(status)) {
            return jdbcTemplate.query(select + order, MEMORIAL_ROW_MAPPER, limit);
        }
        return jdbcTemplate.query(select + " WHERE m.status = ?" + order, MEMORIAL_ROW_MAPPER, status, limit);
    }

    public Optional<AdminMemorial> archivePublishedMemorial(UUID memorialId) {
        int updated = jdbcTemplate.update("""
                        UPDATE memorials
                        SET status = 'ARCHIVED', updated_at = CURRENT_TIMESTAMP
                        WHERE id = ? AND status = 'PUBLISHED'
                        """, memorialId);
        return updated == 0 ? Optional.empty() : findMemorialById(memorialId);
    }

    public void createAuditLog(
            UUID id,
            UUID actorUserId,
            String action,
            String targetType,
            UUID targetId,
            String reason,
            String details
    ) {
        jdbcTemplate.update("""
                        INSERT INTO admin_audit_logs (id, actor_user_id, action, target_type, target_id, reason, details)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, id, actorUserId, action, targetType, targetId, reason, details);
    }

    public List<AdminAuditLog> findAuditLogs(int limit) {
        return jdbcTemplate.query("""
                        SELECT a.id, a.actor_user_id, actor.display_name AS actor_display_name,
                               a.action, a.target_type, a.target_id, a.reason, a.details, a.created_at
                        FROM admin_audit_logs a
                        LEFT JOIN users actor ON actor.id = a.actor_user_id
                        ORDER BY a.created_at DESC
                        LIMIT ?
                        """, AUDIT_ROW_MAPPER, limit);
    }

    private Optional<ModerationTribute> findTribute(UUID tributeId) {
        return jdbcTemplate.query("""
                        SELECT t.id, t.memorial_id, m.slug, m.pet_name, owner.display_name AS owner_display_name,
                               t.author_name, t.message, t.status, t.report_count, t.created_at, t.updated_at
                        FROM tribute_messages t
                        JOIN memorials m ON m.id = t.memorial_id
                        JOIN users owner ON owner.id = m.user_id
                        WHERE t.id = ?
                """, TRIBUTE_ROW_MAPPER, tributeId).stream().findFirst();
    }

    public Optional<AdminMemorial> findMemorialById(UUID memorialId) {
        return jdbcTemplate.query("""
                        SELECT m.id, m.slug, m.pet_name, m.species, m.farewell_message, m.status, m.visibility,
                               m.published_at, m.created_at, m.updated_at,
                               owner.id AS owner_id, owner.display_name AS owner_display_name
                        FROM memorials m
                        JOIN users owner ON owner.id = m.user_id
                        WHERE m.id = ?
                        """, MEMORIAL_ROW_MAPPER, memorialId).stream().findFirst();
    }

    private long count(String sql, Object... arguments) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return value == null ? 0L : value;
    }

    private long sum(String sql, Object... arguments) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return value == null ? 0L : value;
    }

    public record AdminOverview(
            long pendingTributes,
            long reportedTributes,
            long publishedMemorials,
            long pendingOrders,
            long pendingDeletionRequests,
            long openCustomServiceRequests,
            long uniqueVisitorsToday,
            long uniqueVisitorsLast7Days,
            long newMemorialsLast7Days,
            long publishedMemorialsLast7Days,
            long shareEventsLast7Days,
            long paidOrdersLast30Days,
            long managedMediaBytes
    ) {
    }

    public record ModerationTribute(
            UUID id,
            UUID memorialId,
            String memorialSlug,
            String petName,
            String ownerDisplayName,
            String authorName,
            String message,
            String status,
            int reportCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record AdminOrder(
            UUID id,
            String planCode,
            String planName,
            int amountCents,
            String currency,
            String status,
            String paymentReference,
            OffsetDateTime paidAt,
            OffsetDateTime refundedAt,
            OffsetDateTime createdAt,
            UUID ownerId,
            String ownerDisplayName,
            String ownerEmail
    ) {
    }

    public record AdminCustomServiceRequest(
            UUID id,
            UUID userId,
            String ownerDisplayName,
            String ownerEmail,
            String contactDetails,
            String requestDetails,
            String status,
            boolean materialsReady,
            String assignee,
            LocalDate dueDate,
            int revisionCount,
            String customerMessage,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record AdminMemorial(
            UUID id,
            String slug,
            String petName,
            String species,
            String farewellMessage,
            String status,
            String visibility,
            OffsetDateTime publishedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            UUID ownerId,
            String ownerDisplayName
    ) {
    }

    private static final class ModerationTributeRowMapper implements RowMapper<ModerationTribute> {
        @Override
        public ModerationTribute mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new ModerationTribute(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("memorial_id", UUID.class),
                    resultSet.getString("slug"),
                    resultSet.getString("pet_name"),
                    resultSet.getString("owner_display_name"),
                    resultSet.getString("author_name"),
                    resultSet.getString("message"),
                    resultSet.getString("status"),
                    resultSet.getInt("report_count"),
                    resultSet.getObject("created_at", OffsetDateTime.class),
                    resultSet.getObject("updated_at", OffsetDateTime.class)
            );
        }
    }

    private static final class AuditLogRowMapper implements RowMapper<AdminAuditLog> {
        @Override
        public AdminAuditLog mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new AdminAuditLog(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("actor_user_id", UUID.class),
                    resultSet.getString("actor_display_name"),
                    resultSet.getString("action"),
                    resultSet.getString("target_type"),
                    resultSet.getObject("target_id", UUID.class),
                    resultSet.getString("reason"),
                    resultSet.getString("details"),
                    resultSet.getObject("created_at", OffsetDateTime.class)
            );
        }
    }

    private static final class AdminOrderRowMapper implements RowMapper<AdminOrder> {
        @Override
        public AdminOrder mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new AdminOrder(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("plan_code"),
                    resultSet.getString("plan_name"),
                    resultSet.getInt("amount_cents"),
                    resultSet.getString("currency"),
                    resultSet.getString("status"),
                    resultSet.getString("payment_reference"),
                    resultSet.getObject("paid_at", OffsetDateTime.class),
                    resultSet.getObject("refunded_at", OffsetDateTime.class),
                    resultSet.getObject("created_at", OffsetDateTime.class),
                    resultSet.getObject("owner_id", UUID.class),
                    resultSet.getString("owner_display_name"),
                    resultSet.getString("owner_email")
            );
        }
    }

    private static final class AdminCustomServiceRequestRowMapper implements RowMapper<AdminCustomServiceRequest> {
        @Override
        public AdminCustomServiceRequest mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new AdminCustomServiceRequest(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("user_id", UUID.class),
                    resultSet.getString("owner_display_name"),
                    resultSet.getString("owner_email"),
                    resultSet.getString("contact_details"),
                    resultSet.getString("request_details"),
                    resultSet.getString("status"),
                    resultSet.getBoolean("materials_ready"),
                    resultSet.getString("assignee"),
                    resultSet.getObject("due_date", LocalDate.class),
                    resultSet.getInt("revision_count"),
                    resultSet.getString("customer_message"),
                    resultSet.getObject("created_at", OffsetDateTime.class),
                    resultSet.getObject("updated_at", OffsetDateTime.class)
            );
        }
    }

    private static final class AdminMemorialRowMapper implements RowMapper<AdminMemorial> {
        @Override
        public AdminMemorial mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new AdminMemorial(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getString("slug"),
                    resultSet.getString("pet_name"),
                    resultSet.getString("species"),
                    resultSet.getString("farewell_message"),
                    resultSet.getString("status"),
                    resultSet.getString("visibility"),
                    resultSet.getObject("published_at", OffsetDateTime.class),
                    resultSet.getObject("created_at", OffsetDateTime.class),
                    resultSet.getObject("updated_at", OffsetDateTime.class),
                    resultSet.getObject("owner_id", UUID.class),
                    resultSet.getString("owner_display_name")
            );
        }
    }
}

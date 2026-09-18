package com.digitalnest.petmemorial.billing;

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
public class BillingRepository {

    private static final RowMapper<BillingOrder> ORDER_ROW_MAPPER = new BillingOrderRowMapper();
    private static final RowMapper<AccountEntitlement> ENTITLEMENT_ROW_MAPPER = new EntitlementRowMapper();
    private final JdbcTemplate jdbcTemplate;

    public BillingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<BillingOrder> findOrderByIdempotencyKey(UUID userId, String idempotencyKey) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, plan_code, plan_name, amount_cents, currency, photo_limit, short_video_limit,
                               timeline_limit, theme_limit, hosted_years, status, idempotency_key, payment_reference,
                               paid_at, refunded_at, created_at, updated_at
                        FROM billing_orders
                        WHERE user_id = ? AND idempotency_key = ?
                        """, ORDER_ROW_MAPPER, userId, idempotencyKey)
                .stream()
                .findFirst();
    }

    public Optional<BillingOrder> findOrderByIdAndUserId(UUID orderId, UUID userId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, plan_code, plan_name, amount_cents, currency, photo_limit, short_video_limit,
                               timeline_limit, theme_limit, hosted_years, status, idempotency_key, payment_reference,
                               paid_at, refunded_at, created_at, updated_at
                        FROM billing_orders
                        WHERE id = ? AND user_id = ?
                        """, ORDER_ROW_MAPPER, orderId, userId)
                .stream()
                .findFirst();
    }

    public Optional<BillingOrder> findOrderById(UUID orderId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, plan_code, plan_name, amount_cents, currency, photo_limit, short_video_limit,
                               timeline_limit, theme_limit, hosted_years, status, idempotency_key, payment_reference,
                               paid_at, refunded_at, created_at, updated_at
                        FROM billing_orders
                        WHERE id = ?
                        """, ORDER_ROW_MAPPER, orderId)
                .stream()
                .findFirst();
    }

    public BillingOrder createOrder(UUID userId, UUID orderId, BillingPlan plan, String idempotencyKey) {
        jdbcTemplate.update("""
                        INSERT INTO billing_orders (
                            id, user_id, plan_code, plan_name, amount_cents, currency, photo_limit, short_video_limit,
                            timeline_limit, theme_limit, hosted_years, status, idempotency_key
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                        """,
                orderId, userId, plan.code(), plan.name(), plan.amountCents(), plan.currency(), plan.photoLimit(),
                plan.shortVideoLimit(), plan.timelineLimit(), plan.themeLimit(), plan.hostedYears(), idempotencyKey);
        return findOrderByIdAndUserId(orderId, userId).orElseThrow();
    }

    public List<BillingOrder> findOrdersByUserId(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, plan_code, plan_name, amount_cents, currency, photo_limit, short_video_limit,
                               timeline_limit, theme_limit, hosted_years, status, idempotency_key, payment_reference,
                               paid_at, refunded_at, created_at, updated_at
                        FROM billing_orders
                        WHERE user_id = ?
                        ORDER BY created_at DESC
                        """, ORDER_ROW_MAPPER, userId);
    }

    public boolean markPaid(UUID orderId, String paymentReference, OffsetDateTime paidAt) {
        return jdbcTemplate.update("""
                        UPDATE billing_orders
                        SET status = 'PAID', payment_reference = ?, paid_at = ?, updated_at = ?
                        WHERE id = ? AND status = 'PENDING'
                        """, paymentReference, paidAt, paidAt, orderId) == 1;
    }

    public boolean markRefunded(UUID orderId, OffsetDateTime refundedAt) {
        return jdbcTemplate.update("""
                        UPDATE billing_orders
                        SET status = 'REFUNDED', refunded_at = ?, updated_at = ?
                        WHERE id = ? AND status = 'PAID'
                        """, refundedAt, refundedAt, orderId) == 1;
    }

    public AccountEntitlement createEntitlement(UUID entitlementId, BillingOrder order, OffsetDateTime hostedUntil) {
        jdbcTemplate.update("""
                        INSERT INTO account_entitlements (
                            id, user_id, order_id, plan_code, plan_name, photo_limit, short_video_limit,
                            timeline_limit, theme_limit, hosted_until, status
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                        """,
                entitlementId, order.userId(), order.id(), order.planCode(), order.planName(), order.photoLimit(),
                order.shortVideoLimit(), order.timelineLimit(), order.themeLimit(), hostedUntil);
        return findEntitlementByOrderId(order.id()).orElseThrow();
    }

    public Optional<AccountEntitlement> findEntitlementByOrderId(UUID orderId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, order_id, plan_code, plan_name, photo_limit, short_video_limit,
                               timeline_limit, theme_limit, hosted_until, status, created_at, revoked_at
                        FROM account_entitlements
                        WHERE order_id = ?
                        """, ENTITLEMENT_ROW_MAPPER, orderId)
                .stream()
                .findFirst();
    }

    public boolean revokeEntitlement(UUID orderId, OffsetDateTime revokedAt) {
        return jdbcTemplate.update("""
                        UPDATE account_entitlements
                        SET status = 'REVOKED', revoked_at = ?
                        WHERE order_id = ? AND status = 'ACTIVE'
                        """, revokedAt, orderId) == 1;
    }

    public List<AccountEntitlement> findEntitlementsByUserId(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, order_id, plan_code, plan_name, photo_limit, short_video_limit,
                               timeline_limit, theme_limit, hosted_until, status, created_at, revoked_at
                        FROM account_entitlements
                        WHERE user_id = ?
                        ORDER BY hosted_until DESC
                        """, ENTITLEMENT_ROW_MAPPER, userId);
    }

    private static final class BillingOrderRowMapper implements RowMapper<BillingOrder> {
        @Override
        public BillingOrder mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new BillingOrder(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("user_id", UUID.class),
                    resultSet.getString("plan_code"),
                    resultSet.getString("plan_name"),
                    resultSet.getInt("amount_cents"),
                    resultSet.getString("currency"),
                    resultSet.getInt("photo_limit"),
                    resultSet.getInt("short_video_limit"),
                    resultSet.getInt("timeline_limit"),
                    resultSet.getInt("theme_limit"),
                    resultSet.getInt("hosted_years"),
                    resultSet.getString("status"),
                    resultSet.getString("idempotency_key"),
                    resultSet.getString("payment_reference"),
                    resultSet.getObject("paid_at", OffsetDateTime.class),
                    resultSet.getObject("refunded_at", OffsetDateTime.class),
                    resultSet.getObject("created_at", OffsetDateTime.class),
                    resultSet.getObject("updated_at", OffsetDateTime.class)
            );
        }
    }

    private static final class EntitlementRowMapper implements RowMapper<AccountEntitlement> {
        @Override
        public AccountEntitlement mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new AccountEntitlement(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("user_id", UUID.class),
                    resultSet.getObject("order_id", UUID.class),
                    resultSet.getString("plan_code"),
                    resultSet.getString("plan_name"),
                    resultSet.getInt("photo_limit"),
                    resultSet.getInt("short_video_limit"),
                    resultSet.getInt("timeline_limit"),
                    resultSet.getInt("theme_limit"),
                    resultSet.getObject("hosted_until", OffsetDateTime.class),
                    resultSet.getString("status"),
                    resultSet.getObject("created_at", OffsetDateTime.class),
                    resultSet.getObject("revoked_at", OffsetDateTime.class)
            );
        }
    }
}

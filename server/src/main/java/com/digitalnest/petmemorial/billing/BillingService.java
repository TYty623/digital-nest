package com.digitalnest.petmemorial.billing;

import com.digitalnest.petmemorial.shared.error.ApiException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

    private final BillingRepository billingRepository;
    private final PlanCatalog planCatalog;
    private final boolean mockPaymentsEnabled;

    public BillingService(
            BillingRepository billingRepository,
            PlanCatalog planCatalog,
            @Value("${app.billing.mock-payments-enabled:false}") boolean mockPaymentsEnabled
    ) {
        this.billingRepository = billingRepository;
        this.planCatalog = planCatalog;
        this.mockPaymentsEnabled = mockPaymentsEnabled;
    }

    public List<BillingPlan> plans() {
        return List.copyOf(planCatalog.all());
    }

    public boolean checkoutAvailable(BillingPlan plan) {
        return mockPaymentsEnabled && plan.checkoutEligible();
    }

    public CreatedOrder createOrder(UUID userId, String planCode, String idempotencyKey) {
        requireMockPaymentsEnabled();
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        BillingPlan plan = planCatalog.find(planCode)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "BILLING_PLAN_UNKNOWN", "请选择可用的套餐。"));
        if (!plan.checkoutEligible()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BILLING_PLAN_NOT_CHECKOUT_ELIGIBLE", "这个套餐需要联系客服确认后开通。");
        }

        var existing = billingRepository.findOrderByIdempotencyKey(userId, normalizedKey);
        if (existing.isPresent()) {
            ensureSamePlan(existing.get(), plan);
            return new CreatedOrder(existing.get(), false);
        }

        try {
            return new CreatedOrder(billingRepository.createOrder(userId, UUID.randomUUID(), plan, normalizedKey), true);
        } catch (DataIntegrityViolationException exception) {
            BillingOrder concurrent = billingRepository.findOrderByIdempotencyKey(userId, normalizedKey)
                    .orElseThrow(() -> exception);
            ensureSamePlan(concurrent, plan);
            return new CreatedOrder(concurrent, false);
        }
    }

    public List<BillingOrder> orders(UUID userId) {
        return billingRepository.findOrdersByUserId(userId);
    }

    public List<AccountEntitlement> entitlements(UUID userId) {
        return billingRepository.findEntitlementsByUserId(userId);
    }

    public AccountLimits limitsFor(UUID userId) {
        AccountLimits freeLimits = planCatalog.find("FREE")
                .map(AccountLimits::fromPlan)
                .orElseThrow();
        OffsetDateTime now = OffsetDateTime.now();
        return billingRepository.findEntitlementsByUserId(userId).stream()
                .filter(entitlement -> "ACTIVE".equals(entitlement.status()))
                .filter(entitlement -> entitlement.hostedUntil().isAfter(now))
                .max(Comparator.comparingInt(AccountEntitlement::photoLimit)
                        .thenComparingInt(AccountEntitlement::shortVideoLimit)
                        .thenComparingInt(AccountEntitlement::timelineLimit))
                .map(AccountLimits::fromEntitlement)
                .orElse(freeLimits);
    }

    /**
     * Keeps product gating in one place. UI may use this for display, but services must
     * always use this method again before a protected write is accepted.
     */
    public AccountCapabilities capabilitiesFor(UUID userId) {
        String planCode = activePlanCodeFor(userId);
        Set<FeatureCode> codes = EnumSet.noneOf(FeatureCode.class);
        if ("GUARDIAN".equals(planCode) || "TREASURE".equals(planCode)) {
            codes.add(FeatureCode.PREMIUM_APPEARANCE_PACK);
            codes.add(FeatureCode.PREMIUM_RITUAL_THEME);
            codes.add(FeatureCode.MEMORY_CAPSULE);
            codes.add(FeatureCode.YEARBOOK_EXPORT);
            codes.add(FeatureCode.EXTENDED_STORAGE);
        }
        if ("TREASURE".equals(planCode)) {
            codes.add(FeatureCode.DIGITAL_LIFE_PROFILE);
            codes.add(FeatureCode.DIGITAL_LIFE_QA);
        }
        boolean collectionUnlocked = codes.contains(FeatureCode.PREMIUM_APPEARANCE_PACK);
        List<String> themeCodes = collectionUnlocked
                ? List.of("NIGHT", "SUNNY", "GARDEN", "MEADOW", "ALBUM", "HOME")
                : List.of("NIGHT");
        return new AccountCapabilities(
                planCode,
                codes.stream().map(Enum::name).sorted().toList(),
                themeCodes,
                collectionUnlocked);
    }

    public boolean hasCapability(UUID userId, FeatureCode code) {
        return capabilitiesFor(userId).includes(code);
    }

    private String activePlanCodeFor(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        return billingRepository.findEntitlementsByUserId(userId).stream()
                .filter(entitlement -> "ACTIVE".equals(entitlement.status()))
                .filter(entitlement -> entitlement.hostedUntil().isAfter(now))
                .max(Comparator.comparingInt(AccountEntitlement::photoLimit)
                        .thenComparingInt(AccountEntitlement::shortVideoLimit)
                        .thenComparing(AccountEntitlement::hostedUntil))
                .map(AccountEntitlement::planCode)
                .orElse("FREE");
    }

    @Transactional
    public PaymentResult markMockPaid(UUID userId, UUID orderId) {
        requireMockPaymentsEnabled();
        BillingOrder order = billingRepository.findOrderByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BILLING_ORDER_NOT_FOUND", "没有找到这笔订单。"));
        if ("PAID".equals(order.status())) {
            return new PaymentResult(order, billingRepository.findEntitlementByOrderId(order.id()).orElse(null), false);
        }
        if (!"PENDING".equals(order.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "BILLING_ORDER_NOT_PAYABLE", "这笔订单当前不能支付。");
        }

        OffsetDateTime paidAt = OffsetDateTime.now();
        String paymentReference = "mock_" + order.id();
        if (!billingRepository.markPaid(order.id(), paymentReference, paidAt)) {
            BillingOrder refreshed = billingRepository.findOrderByIdAndUserId(order.id(), userId).orElseThrow();
            return new PaymentResult(refreshed, billingRepository.findEntitlementByOrderId(refreshed.id()).orElse(null), false);
        }

        AccountEntitlement entitlement = billingRepository.createEntitlement(
                UUID.randomUUID(), order, paidAt.plusYears(order.hostedYears()));
        BillingOrder paidOrder = billingRepository.findOrderByIdAndUserId(order.id(), userId).orElseThrow();
        return new PaymentResult(paidOrder, entitlement, true);
    }

    @Transactional
    public RefundResult refundMockOrder(UUID orderId) {
        requireMockPaymentsEnabled();
        BillingOrder order = billingRepository.findOrderById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BILLING_ORDER_NOT_FOUND", "没有找到这笔订单。"));
        AccountEntitlement entitlement = billingRepository.findEntitlementByOrderId(orderId).orElse(null);
        if ("REFUNDED".equals(order.status())) {
            return new RefundResult(order, entitlement, false);
        }
        if (!"PAID".equals(order.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "BILLING_ORDER_NOT_REFUNDABLE", "这笔订单当前不能退款。" );
        }
        if (entitlement == null) {
            throw new ApiException(HttpStatus.CONFLICT, "BILLING_ENTITLEMENT_MISSING", "这笔已支付订单缺少可撤销的权益记录。" );
        }

        OffsetDateTime refundedAt = OffsetDateTime.now();
        if (!billingRepository.markRefunded(orderId, refundedAt)) {
            BillingOrder refreshed = billingRepository.findOrderById(orderId).orElseThrow();
            if ("REFUNDED".equals(refreshed.status())) {
                return new RefundResult(refreshed, billingRepository.findEntitlementByOrderId(orderId).orElse(null), false);
            }
            throw new ApiException(HttpStatus.CONFLICT, "BILLING_ORDER_NOT_REFUNDABLE", "这笔订单当前不能退款。" );
        }
        billingRepository.revokeEntitlement(orderId, refundedAt);
        BillingOrder refundedOrder = billingRepository.findOrderById(orderId).orElseThrow();
        AccountEntitlement revokedEntitlement = billingRepository.findEntitlementByOrderId(orderId).orElseThrow();
        return new RefundResult(refundedOrder, revokedEntitlement, true);
    }

    private void requireMockPaymentsEnabled() {
        if (!mockPaymentsEnabled) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_AVAILABLE", "付费开通暂未开放，真实支付审核完成后才会启用。");
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        String normalized = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (normalized.length() < 8 || normalized.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID", "请求标识需要为 8 到 100 个字符。");
        }
        return normalized;
    }

    private void ensureSamePlan(BillingOrder existing, BillingPlan requestedPlan) {
        if (!existing.planCode().equals(requestedPlan.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "同一个请求标识不能用于不同套餐。");
        }
    }

    public record CreatedOrder(BillingOrder order, boolean newlyCreated) {
    }

    public record PaymentResult(BillingOrder order, AccountEntitlement entitlement, boolean newlyPaid) {
    }

    public record RefundResult(BillingOrder order, AccountEntitlement entitlement, boolean newlyRefunded) {
    }

    public record AccountLimits(int photoLimit, int shortVideoLimit, int timelineLimit) {
        static AccountLimits fromPlan(BillingPlan plan) {
            return new AccountLimits(plan.photoLimit(), plan.shortVideoLimit(), plan.timelineLimit());
        }

        static AccountLimits fromEntitlement(AccountEntitlement entitlement) {
            return new AccountLimits(entitlement.photoLimit(), entitlement.shortVideoLimit(), entitlement.timelineLimit());
        }
    }
}

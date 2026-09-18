package com.digitalnest.petmemorial.billing;

import com.digitalnest.petmemorial.account.CurrentUser;
import com.digitalnest.petmemorial.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/plans")
    public ApiResponse<List<PlanResponse>> plans() {
        return ApiResponse.ok(billingService.plans().stream()
                .map(plan -> PlanResponse.from(plan, billingService.checkoutAvailable(plan)))
                .toList());
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication
    ) {
        BillingService.CreatedOrder created = billingService.createOrder(currentUser(authentication).id(), request.planCode(), idempotencyKey);
        HttpStatus status = created.newlyCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(OrderResponse.from(created.order())));
    }

    @GetMapping("/orders")
    public ApiResponse<List<OrderResponse>> orders(Authentication authentication) {
        return ApiResponse.ok(billingService.orders(currentUser(authentication).id()).stream()
                .map(OrderResponse::from)
                .toList());
    }

    @PostMapping("/orders/{orderId}/mock-pay")
    public ApiResponse<PaymentResponse> mockPay(@PathVariable UUID orderId, Authentication authentication) {
        BillingService.PaymentResult result = billingService.markMockPaid(currentUser(authentication).id(), orderId);
        return ApiResponse.ok(new PaymentResponse(
                OrderResponse.from(result.order()),
                result.entitlement() == null ? null : EntitlementResponse.from(result.entitlement()),
                result.newlyPaid()
        ));
    }

    @GetMapping("/entitlements")
    public ApiResponse<List<EntitlementResponse>> entitlements(Authentication authentication) {
        return ApiResponse.ok(billingService.entitlements(currentUser(authentication).id()).stream()
                .map(EntitlementResponse::from)
                .toList());
    }

    @GetMapping("/limits")
    public ApiResponse<AccountLimitsResponse> limits(Authentication authentication) {
        BillingService.AccountLimits limits = billingService.limitsFor(currentUser(authentication).id());
        return ApiResponse.ok(new AccountLimitsResponse(
                limits.photoLimit(), limits.shortVideoLimit(), limits.timelineLimit()));
    }

    @GetMapping("/capabilities")
    public ApiResponse<CapabilityResponse> capabilities(Authentication authentication) {
        AccountCapabilities capabilities = billingService.capabilitiesFor(currentUser(authentication).id());
        return ApiResponse.ok(new CapabilityResponse(
                capabilities.planCode(),
                capabilities.enabled(),
                capabilities.themeCodes(),
                capabilities.allThemeCollectionUnlocked()));
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        throw new IllegalStateException("受保护的接口缺少当前用户");
    }

    public record CreateOrderRequest(@NotBlank(message = "请选择套餐") @Size(max = 32) String planCode) {
    }

    public record CapabilityResponse(
            String planCode,
            List<String> enabled,
            List<String> themeCodes,
            boolean allThemeCollectionUnlocked
    ) {
    }

    public record PlanResponse(
            String code,
            String name,
            int amountCents,
            String currency,
            int photoLimit,
            int shortVideoLimit,
            int timelineLimit,
            int themeLimit,
            int hostedYears,
            boolean checkoutAvailable
    ) {
        static PlanResponse from(BillingPlan plan, boolean checkoutAvailable) {
            return new PlanResponse(plan.code(), plan.name(), plan.amountCents(), plan.currency(), plan.photoLimit(),
                    plan.shortVideoLimit(), plan.timelineLimit(), plan.themeLimit(), plan.hostedYears(), checkoutAvailable);
        }
    }

    public record OrderResponse(
            UUID id,
            String planCode,
            String planName,
            int amountCents,
            String currency,
            int photoLimit,
            int shortVideoLimit,
            int timelineLimit,
            int themeLimit,
            int hostedYears,
            String status,
            String paymentReference,
            OffsetDateTime paidAt,
            OffsetDateTime refundedAt,
            OffsetDateTime createdAt
    ) {
        static OrderResponse from(BillingOrder order) {
            return new OrderResponse(order.id(), order.planCode(), order.planName(), order.amountCents(), order.currency(),
                    order.photoLimit(), order.shortVideoLimit(), order.timelineLimit(), order.themeLimit(), order.hostedYears(),
                    order.status(), order.paymentReference(), order.paidAt(), order.refundedAt(), order.createdAt());
        }
    }

    public record EntitlementResponse(
            UUID id,
            UUID orderId,
            String planCode,
            String planName,
            int photoLimit,
            int shortVideoLimit,
            int timelineLimit,
            int themeLimit,
            OffsetDateTime hostedUntil,
            String status,
            OffsetDateTime revokedAt,
            OffsetDateTime createdAt
    ) {
        static EntitlementResponse from(AccountEntitlement entitlement) {
            return new EntitlementResponse(entitlement.id(), entitlement.orderId(), entitlement.planCode(), entitlement.planName(),
                    entitlement.photoLimit(), entitlement.shortVideoLimit(), entitlement.timelineLimit(), entitlement.themeLimit(),
                    entitlement.hostedUntil(), entitlement.status(), entitlement.revokedAt(), entitlement.createdAt());
        }
    }

    public record AccountLimitsResponse(int photoLimit, int shortVideoLimit, int timelineLimit) {
    }

    public record PaymentResponse(OrderResponse order, EntitlementResponse entitlement, boolean newlyPaid) {
    }
}

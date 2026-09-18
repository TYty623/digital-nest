package com.digitalnest.petmemorial.admin;

import com.digitalnest.petmemorial.account.CurrentUser;
import com.digitalnest.petmemorial.billing.BillingService;
import com.digitalnest.petmemorial.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminRepository.AdminOverview> overview() {
        return ApiResponse.ok(adminService.overview());
    }

    @GetMapping("/tributes")
    public ApiResponse<List<AdminRepository.ModerationTribute>> tributes(
            @RequestParam(defaultValue = "PENDING") @Pattern(regexp = "PENDING|REPORTED|APPROVED|HIDDEN|ALL") String status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.ok(adminService.tributes(status, limit));
    }

    @PatchMapping("/tributes/{tributeId}")
    public ApiResponse<AdminRepository.ModerationTribute> moderateTribute(
            @PathVariable UUID tributeId,
            @Valid @RequestBody ModerateTributeRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(adminService.moderateTribute(
                tributeId, request.status(), request.reason(), currentUser(authentication)));
    }

    @GetMapping("/orders")
    public ApiResponse<List<AdminRepository.AdminOrder>> orders(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.ok(adminService.orders(limit));
    }

    @PostMapping("/orders/{orderId}/mock-refund")
    public ApiResponse<MockRefundResponse> refundMockOrder(
            @PathVariable UUID orderId,
            @Valid @RequestBody MockRefundRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(MockRefundResponse.from(
                adminService.refundMockOrder(orderId, request.reason(), currentUser(authentication))));
    }

    @GetMapping("/memorials")
    public ApiResponse<List<AdminRepository.AdminMemorial>> memorials(
            @RequestParam(defaultValue = "PUBLISHED") @Pattern(regexp = "PUBLISHED|ARCHIVED|ALL") String status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.ok(adminService.memorials(status, limit));
    }

    @PatchMapping("/memorials/{memorialId}")
    public ApiResponse<AdminRepository.AdminMemorial> archiveMemorial(
            @PathVariable UUID memorialId,
            @Valid @RequestBody ArchiveMemorialRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(adminService.archiveMemorial(memorialId, request.reason(), currentUser(authentication)));
    }

    @GetMapping("/custom-service-requests")
    public ApiResponse<List<AdminRepository.AdminCustomServiceRequest>> customServiceRequests(
            @RequestParam(defaultValue = "OPEN") @Pattern(regexp = "OPEN|ALL|SUBMITTED|MATERIALS_PENDING|IN_PROGRESS|REVISION|OUT_OF_SCOPE|DELIVERED|COMPLETED|CANCELED") String status,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.ok(adminService.customServiceRequests(status, limit));
    }

    @PatchMapping("/custom-service-requests/{requestId}")
    public ApiResponse<com.digitalnest.petmemorial.service.CustomServiceRequest> updateCustomServiceRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody UpdateCustomServiceRequest request,
            Authentication authentication
    ) {
        return ApiResponse.ok(adminService.updateCustomServiceRequest(
                requestId, request.status(), request.materialsReady(), request.assignee(), request.dueDate(),
                request.revisionCount(), request.customerMessage(), request.reason(), currentUser(authentication)));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AdminAuditLog>> auditLogs(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.ok(adminService.auditLogs(limit));
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        throw new IllegalStateException("受保护的接口缺少当前用户");
    }

    public record ModerateTributeRequest(
            @NotBlank @Pattern(regexp = "APPROVED|HIDDEN", message = "审核操作无效") String status,
            @NotBlank(message = "请填写审核原因") @Size(min = 2, max = 280, message = "审核原因需要为 2 到 280 个字符") String reason
    ) {
    }

    public record ArchiveMemorialRequest(
            @NotBlank(message = "请填写下线原因") @Size(min = 2, max = 280, message = "下线原因需要为 2 到 280 个字符") String reason
    ) {
    }

    public record MockRefundRequest(
            @NotBlank(message = "请填写退款原因") @Size(min = 2, max = 280, message = "退款原因需要为 2 到 280 个字符") String reason
    ) {
    }

    public record MockRefundResponse(
            UUID orderId,
            String orderStatus,
            String entitlementStatus,
            boolean newlyRefunded
    ) {
        static MockRefundResponse from(BillingService.RefundResult result) {
            return new MockRefundResponse(
                    result.order().id(),
                    result.order().status(),
                    result.entitlement() == null ? null : result.entitlement().status(),
                    result.newlyRefunded()
            );
        }
    }

    public record UpdateCustomServiceRequest(
            @NotBlank @Pattern(regexp = "SUBMITTED|MATERIALS_PENDING|IN_PROGRESS|REVISION|OUT_OF_SCOPE|DELIVERED|COMPLETED|CANCELED") String status,
            boolean materialsReady,
            @Size(max = 80) String assignee,
            LocalDate dueDate,
            @Min(0) @Max(50) int revisionCount,
            @Size(max = 800) String customerMessage,
            @NotBlank @Size(min = 2, max = 280) String reason
    ) {
    }
}

package com.digitalnest.petmemorial.admin;

import com.digitalnest.petmemorial.account.CurrentUser;
import com.digitalnest.petmemorial.billing.BillingService;
import com.digitalnest.petmemorial.service.CustomServiceRequest;
import com.digitalnest.petmemorial.service.CustomServiceService;
import com.digitalnest.petmemorial.shared.error.ApiException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final Set<String> REVIEW_STATUSES = Set.of("PENDING", "REPORTED", "APPROVED", "HIDDEN", "ALL");
    private static final Set<String> MODERATION_STATUSES = Set.of("APPROVED", "HIDDEN");
    private static final Set<String> MEMORIAL_STATUSES = Set.of("PUBLISHED", "ARCHIVED", "ALL");
    private static final Set<String> CUSTOM_SERVICE_FILTERS = Set.of(
            "OPEN", "ALL", "SUBMITTED", "MATERIALS_PENDING", "IN_PROGRESS", "REVISION", "OUT_OF_SCOPE", "DELIVERED", "COMPLETED", "CANCELED");
    private final AdminRepository adminRepository;
    private final CustomServiceService customServiceService;
    private final BillingService billingService;

    public AdminService(
            AdminRepository adminRepository,
            CustomServiceService customServiceService,
            BillingService billingService
    ) {
        this.adminRepository = adminRepository;
        this.customServiceService = customServiceService;
        this.billingService = billingService;
    }

    public AdminRepository.AdminOverview overview() {
        return adminRepository.overview();
    }

    public List<AdminRepository.ModerationTribute> tributes(String status, int limit) {
        if (!REVIEW_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_TRIBUTE_STATUS_INVALID", "审核状态无效。");
        }
        return adminRepository.findTributes(status, limit);
    }

    public List<AdminRepository.AdminOrder> orders(int limit) {
        return adminRepository.findOrders(limit);
    }

    @Transactional
    public BillingService.RefundResult refundMockOrder(UUID orderId, String reason, CurrentUser actor) {
        BillingService.RefundResult result = billingService.refundMockOrder(orderId);
        if (result.newlyRefunded()) {
            adminRepository.createAuditLog(
                    UUID.randomUUID(),
                    actor.id(),
                    "BILLING_ORDER_REFUNDED",
                    "BILLING_ORDER",
                    result.order().id(),
                    reason.trim(),
                    "mockPayment=true; plan=" + result.order().planCode()
                            + "; entitlement=" + (result.entitlement() == null ? "" : result.entitlement().id())
            );
        }
        return result;
    }

    public List<AdminRepository.AdminMemorial> memorials(String status, int limit) {
        if (!MEMORIAL_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_MEMORIAL_STATUS_INVALID", "纪念页状态无效。");
        }
        return adminRepository.findMemorials(status, limit);
    }

    public List<AdminAuditLog> auditLogs(int limit) {
        return adminRepository.findAuditLogs(limit);
    }

    public List<AdminRepository.AdminCustomServiceRequest> customServiceRequests(String status, int limit) {
        if (!CUSTOM_SERVICE_FILTERS.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_CUSTOM_SERVICE_STATUS_INVALID", "服务状态无效。");
        }
        return adminRepository.findCustomServiceRequests(status, limit);
    }

    @Transactional
    public CustomServiceRequest updateCustomServiceRequest(
            UUID requestId,
            String status,
            boolean materialsReady,
            String assignee,
            java.time.LocalDate dueDate,
            int revisionCount,
            String customerMessage,
            String reason,
            CurrentUser actor
    ) {
        CustomServiceRequest updated = customServiceService.updateByStaff(
                requestId, status, materialsReady, assignee, dueDate, revisionCount, customerMessage);
        adminRepository.createAuditLog(
                UUID.randomUUID(),
                actor.id(),
                "CUSTOM_SERVICE_UPDATED",
                "CUSTOM_SERVICE_REQUEST",
                updated.id(),
                reason.trim(),
                "status=" + updated.status() + "; materialsReady=" + updated.materialsReady()
                        + "; assignee=" + (updated.assignee() == null ? "" : updated.assignee())
                        + "; dueDate=" + (updated.dueDate() == null ? "" : updated.dueDate())
                        + "; revisions=" + updated.revisionCount()
        );
        return updated;
    }

    @Transactional
    public AdminRepository.ModerationTribute moderateTribute(
            UUID tributeId,
            String status,
            String reason,
            CurrentUser actor
    ) {
        if (!MODERATION_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_MODERATION_STATUS_INVALID", "审核操作无效。");
        }
        AdminRepository.ModerationTribute tribute = adminRepository.updateTributeStatus(tributeId, status)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TRIBUTE_NOT_FOUND", "没有找到这条留言。"));
        String normalizedReason = reason.trim();
        adminRepository.createAuditLog(
                UUID.randomUUID(),
                actor.id(),
                "APPROVED".equals(status) ? "TRIBUTE_APPROVED" : "TRIBUTE_HIDDEN",
                "TRIBUTE",
                tribute.id(),
                normalizedReason,
                "memorial=" + tribute.memorialId() + "; status=" + status
        );
        return tribute;
    }

    @Transactional
    public AdminRepository.AdminMemorial archiveMemorial(UUID memorialId, String reason, CurrentUser actor) {
        var archivedMemorial = adminRepository.archivePublishedMemorial(memorialId);
        if (archivedMemorial.isEmpty()) {
            if (adminRepository.findMemorialById(memorialId).isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "MEMORIAL_NOT_FOUND", "没有找到这间小窝。");
            }
            throw new ApiException(HttpStatus.CONFLICT, "MEMORIAL_NOT_PUBLISHED", "这间小窝当前无法下线。");
        }
        AdminRepository.AdminMemorial memorial = archivedMemorial.get();
        adminRepository.createAuditLog(
                UUID.randomUUID(),
                actor.id(),
                "MEMORIAL_ARCHIVED",
                "MEMORIAL",
                memorial.id(),
                reason.trim(),
                "slug=" + memorial.slug() + "; visibility=" + memorial.visibility()
        );
        return memorial;
    }
}

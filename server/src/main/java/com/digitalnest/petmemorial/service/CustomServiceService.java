package com.digitalnest.petmemorial.service;

import com.digitalnest.petmemorial.shared.error.ApiException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomServiceService {

    private static final Set<String> ALL_STATUSES = Set.of(
            "SUBMITTED", "MATERIALS_PENDING", "IN_PROGRESS", "REVISION", "OUT_OF_SCOPE", "DELIVERED", "COMPLETED", "CANCELED");
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "SUBMITTED", Set.of("MATERIALS_PENDING", "IN_PROGRESS", "OUT_OF_SCOPE", "CANCELED"),
            "MATERIALS_PENDING", Set.of("IN_PROGRESS", "OUT_OF_SCOPE", "CANCELED"),
            "IN_PROGRESS", Set.of("REVISION", "DELIVERED", "OUT_OF_SCOPE", "CANCELED"),
            "REVISION", Set.of("IN_PROGRESS", "DELIVERED", "OUT_OF_SCOPE", "CANCELED"),
            "DELIVERED", Set.of("REVISION", "CANCELED"),
            "OUT_OF_SCOPE", Set.of("CANCELED"),
            "COMPLETED", Set.of(),
            "CANCELED", Set.of()
    );
    private final CustomServiceRepository customServiceRepository;

    public CustomServiceService(CustomServiceRepository customServiceRepository) {
        this.customServiceRepository = customServiceRepository;
    }

    @Transactional
    public CustomServiceRequest create(UUID userId, String contactDetails, String requestDetails) {
        return customServiceRepository.create(
                UUID.randomUUID(), userId, contactDetails.trim(), requestDetails.trim());
    }

    public List<CustomServiceRequest> mine(UUID userId) {
        return customServiceRepository.findByUserId(userId);
    }

    @Transactional
    public CustomServiceRequest confirmDelivery(UUID requestId, UUID userId) {
        CustomServiceRequest current = customServiceRepository.findByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CUSTOM_SERVICE_REQUEST_NOT_FOUND", "没有找到这份定制服务申请。"));
        if (!"DELIVERED".equals(current.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "CUSTOM_SERVICE_NOT_DELIVERED", "当前服务尚未进入待确认交付状态。");
        }
        return customServiceRepository.completeDelivery(requestId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CUSTOM_SERVICE_NOT_DELIVERED", "当前服务状态已变更，请刷新后重试。"));
    }

    @Transactional
    public CustomServiceRequest updateByStaff(
            UUID requestId,
            String requestedStatus,
            boolean materialsReady,
            String assignee,
            java.time.LocalDate dueDate,
            int revisionCount,
            String customerMessage
    ) {
        CustomServiceRequest current = customServiceRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CUSTOM_SERVICE_REQUEST_NOT_FOUND", "没有找到这份定制服务申请。"));
        String status = requestedStatus.trim();
        if (!ALL_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CUSTOM_SERVICE_STATUS_INVALID", "服务状态无效。");
        }
        if ("COMPLETED".equals(status) && !"COMPLETED".equals(current.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "CUSTOM_SERVICE_CONFIRMATION_REQUIRED", "交付完成需要由用户确认。");
        }
        if (!status.equals(current.status()) && !ALLOWED_TRANSITIONS.get(current.status()).contains(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "CUSTOM_SERVICE_STATUS_TRANSITION_INVALID", "当前状态不能这样流转。");
        }
        return customServiceRepository.updateByStaff(
                requestId,
                status,
                materialsReady,
                normalizeOptional(assignee),
                dueDate,
                revisionCount,
                normalizeOptional(customerMessage)
        ).orElseThrow();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

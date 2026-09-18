package com.digitalnest.petmemorial.service;

import com.digitalnest.petmemorial.account.CurrentUser;
import com.digitalnest.petmemorial.shared.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/custom-service-requests")
public class CustomServiceController {

    private final CustomServiceService customServiceService;

    public CustomServiceController(CustomServiceService customServiceService) {
        this.customServiceService = customServiceService;
    }

    @GetMapping
    public ApiResponse<List<CustomServiceRequest>> mine(Authentication authentication) {
        return ApiResponse.ok(customServiceService.mine(currentUser(authentication).id()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomServiceRequest>> create(
            @Valid @RequestBody CreateCustomServiceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                customServiceService.create(currentUser(authentication).id(), request.contactDetails(), request.requestDetails())));
    }

    @PostMapping("/{requestId}/confirm-delivery")
    public ApiResponse<CustomServiceRequest> confirmDelivery(@PathVariable UUID requestId, Authentication authentication) {
        return ApiResponse.ok(customServiceService.confirmDelivery(requestId, currentUser(authentication).id()));
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        throw new IllegalStateException("受保护的接口缺少当前用户");
    }

    public record CreateCustomServiceRequest(
            @NotBlank(message = "请留下至少一种联系方式") @Size(min = 3, max = 280) String contactDetails,
            @NotBlank(message = "请说明希望我们协助的内容") @Size(min = 10, max = 3000) String requestDetails
    ) {
    }
}

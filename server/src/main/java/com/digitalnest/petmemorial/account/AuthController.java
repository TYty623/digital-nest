package com.digitalnest.petmemorial.account;

import com.digitalnest.petmemorial.shared.api.ApiResponse;
import com.digitalnest.petmemorial.shared.error.ApiException;
import com.digitalnest.petmemorial.shared.ratelimit.LoginAttemptRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AccountService accountService;
    private final AccountDataService accountDataService;
    private final LoginAttemptRateLimiter loginAttemptRateLimiter;

    public AuthController(
            AccountService accountService,
            AccountDataService accountDataService,
            LoginAttemptRateLimiter loginAttemptRateLimiter
    ) {
        this.accountService = accountService;
        this.accountDataService = accountDataService;
        this.loginAttemptRateLimiter = loginAttemptRateLimiter;
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(CsrfToken csrfToken) {
        return ApiResponse.ok(new CsrfResponse(csrfToken.getToken(), csrfToken.getHeaderName()));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AccountService.AccountProfile> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        CurrentUser user = accountService.register(request.email(), request.password(), request.displayName());
        signIn(servletRequest, user);
        return ApiResponse.ok(accountService.profile(user.id()));
    }

    @PostMapping("/login")
    public ApiResponse<AccountService.AccountProfile> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        loginAttemptRateLimiter.check(servletRequest, request.email());
        CurrentUser user;
        try {
            user = accountService.login(request.email(), request.password());
        } catch (ApiException exception) {
            if ("INVALID_CREDENTIALS".equals(exception.code())) {
                loginAttemptRateLimiter.recordFailure(servletRequest, request.email());
            }
            throw exception;
        }
        loginAttemptRateLimiter.clear(servletRequest, request.email());
        signIn(servletRequest, user);
        return ApiResponse.ok(accountService.profile(user.id()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<AccountService.AccountProfile> me(Authentication authentication) {
        CurrentUser user = currentUser(authentication);
        return ApiResponse.ok(accountService.profile(user.id()));
    }

    @PostMapping("/export-links")
    public ApiResponse<ExportDownloadLinkResponse> createExportDownloadLink(Authentication authentication) {
        CurrentUser user = currentUser(authentication);
        AccountDataService.ExportDownloadLink link = accountDataService.createExportDownloadLink(user.id());
        return ApiResponse.ok(new ExportDownloadLinkResponse(
                "/api/v1/auth/export-download/" + link.token(), link.expiresAt()));
    }

    @GetMapping("/export-download/{token}")
    public ResponseEntity<byte[]> downloadExport(@PathVariable String token, Authentication authentication) {
        CurrentUser user = currentUser(authentication);
        byte[] archive = accountDataService.exportForDownload(user.id(), token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"digital-nest-export.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(archive);
    }

    @DeleteMapping("/account")
    public ApiResponse<AccountDeletionResponse> requestAccountDeletion(
            @Valid @RequestBody DeleteAccountRequest request,
            Authentication authentication
    ) {
        if (!"DELETE".equals(request.confirmation())) {
            throw new com.digitalnest.petmemorial.shared.error.ApiException(
                    HttpStatus.BAD_REQUEST, "DELETE_CONFIRMATION_INVALID", "请输入 DELETE 以确认删除。" );
        }
        AccountDeletionRequest deletionRequest = accountDataService.requestDeletion(currentUser(authentication).id());
        return ApiResponse.ok(new AccountDeletionResponse(true, deletionRequest.scheduledFor()));
    }

    @GetMapping("/account/deletion")
    public ApiResponse<AccountDeletionStatusResponse> accountDeletionStatus(Authentication authentication) {
        AccountDeletionRequest deletionRequest = accountDataService.deletionStatus(currentUser(authentication).id());
        return ApiResponse.ok(new AccountDeletionStatusResponse(
                deletionRequest != null,
                deletionRequest == null ? null : deletionRequest.scheduledFor()
        ));
    }

    @PostMapping("/account/deletion/cancel")
    public ApiResponse<AccountDeletionStatusResponse> cancelAccountDeletion(Authentication authentication) {
        accountDataService.cancelDeletion(currentUser(authentication).id());
        return ApiResponse.ok(new AccountDeletionStatusResponse(false, null));
    }

    private void signIn(HttpServletRequest request, CurrentUser user) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                user.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private CurrentUser currentUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        throw new IllegalStateException("受保护的接口缺少当前用户");
    }

    public record RegisterRequest(
            @NotBlank(message = "请填写邮箱") @Email(message = "请输入有效的邮箱") String email,
            @NotBlank(message = "请设置密码") @Size(min = 8, max = 72, message = "密码需要为 8 到 72 个字符") String password,
            @Size(max = 32, message = "昵称最多 32 个字符") String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "请填写邮箱") @Email(message = "请输入有效的邮箱") String email,
            @NotBlank(message = "请填写密码") String password
    ) {
    }

    public record CsrfResponse(String token, String headerName) {
    }

    public record DeleteAccountRequest(@NotBlank(message = "请输入 DELETE 以确认") String confirmation) {
    }

    public record AccountDeletionResponse(boolean scheduled, OffsetDateTime scheduledFor) {
    }

    public record AccountDeletionStatusResponse(boolean pending, OffsetDateTime scheduledFor) {
    }

    public record ExportDownloadLinkResponse(String downloadUrl, OffsetDateTime expiresAt) {
    }
}

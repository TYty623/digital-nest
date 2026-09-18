package com.digitalnest.petmemorial.account;

import com.digitalnest.petmemorial.shared.error.ApiException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Set<String> bootstrapAdminEmails;

    public AccountService(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.bootstrap-admin-emails:}") String bootstrapAdminEmails
    ) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        String configuredAdminEmails = bootstrapAdminEmails == null ? "" : bootstrapAdminEmails;
        this.bootstrapAdminEmails = Arrays.stream(configuredAdminEmails.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public CurrentUser register(String email, String password, String displayName) {
        String normalizedEmail = normalizeEmail(email);
        if (accountRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "这个邮箱已经注册，请直接登录。");
        }

        String resolvedDisplayName = normalizeDisplayName(displayName, normalizedEmail);
        UserAccount account = accountRepository.create(
                UUID.randomUUID(),
                normalizedEmail,
                passwordEncoder.encode(password),
                resolvedDisplayName
        );
        applyBootstrapAdminRole(account);
        return toCurrentUser(account);
    }

    public CurrentUser login(String email, String password) {
        UserAccount account = accountRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "邮箱或密码不正确。"));

        if (!"ACTIVE".equals(account.status()) || !passwordEncoder.matches(password, account.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "邮箱或密码不正确。");
        }
        applyBootstrapAdminRole(account);
        return toCurrentUser(account);
    }

    public AccountProfile profile(UUID id) {
        UserAccount account = accountRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "登录状态已失效，请重新登录。"));
        return new AccountProfile(account.id(), account.email(), account.displayName(), accountRepository.findRolesByUserId(account.id()));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDisplayName(String displayName, String email) {
        String candidate = displayName == null ? "" : displayName.trim();
        return candidate.isBlank() ? email.substring(0, email.indexOf('@')) : candidate;
    }

    private CurrentUser toCurrentUser(UserAccount account) {
        return new CurrentUser(account.id(), account.email(), account.displayName(), accountRepository.findRolesByUserId(account.id()));
    }

    private void applyBootstrapAdminRole(UserAccount account) {
        if (bootstrapAdminEmails.contains(account.email())) {
            accountRepository.grantRoleIfMissing(account.id(), "ADMIN");
        }
    }

    public record AccountProfile(UUID id, String email, String displayName, Set<String> roles) {
    }
}

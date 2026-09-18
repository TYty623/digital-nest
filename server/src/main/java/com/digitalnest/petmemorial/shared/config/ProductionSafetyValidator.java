package com.digitalnest.petmemorial.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ProductionSafetyValidator implements ApplicationRunner {

    private static final int MINIMUM_DATABASE_PASSWORD_LENGTH = 20;
    private static final int MINIMUM_FINGERPRINT_SECRET_LENGTH = 32;

    private final Environment environment;
    private final String databasePassword;
    private final boolean secureCookies;
    private final boolean mockPaymentsEnabled;
    private final String visitorFingerprintSecret;

    public ProductionSafetyValidator(
            Environment environment,
            @Value("${spring.datasource.password}") String databasePassword,
            @Value("${app.security.cookies.secure:true}") boolean secureCookies,
            @Value("${app.billing.mock-payments-enabled:false}") boolean mockPaymentsEnabled,
            @Value("${app.privacy.visitor-fingerprint-secret:}") String visitorFingerprintSecret
    ) {
        this.environment = environment;
        this.databasePassword = databasePassword;
        this.secureCookies = secureCookies;
        this.mockPaymentsEnabled = mockPaymentsEnabled;
        this.visitorFingerprintSecret = visitorFingerprintSecret;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (environment.matchesProfiles("local", "test")) {
            return;
        }
        if (isUnsafeDatabasePassword(databasePassword)) {
            throw new IllegalStateException("生产环境必须配置至少 20 位、非示例值的数据库密码。");
        }
        if (!secureCookies) {
            throw new IllegalStateException("生产环境必须启用 Secure 会话 Cookie，并部署在 HTTPS 反向代理之后。");
        }
        if (mockPaymentsEnabled) {
            throw new IllegalStateException("生产环境禁止启用模拟支付。");
        }
        if (isUnsafeFingerprintSecret(visitorFingerprintSecret)) {
            throw new IllegalStateException("生产环境必须配置至少 32 位、非示例值的访客指纹 HMAC 密钥。");
        }
    }

    private boolean isUnsafeDatabasePassword(String value) {
        if (value == null || value.length() < MINIMUM_DATABASE_PASSWORD_LENGTH) {
            return true;
        }
        return "pet_memorial_dev".equals(value) || value.contains("replace-with-a-long-random-password");
    }

    private boolean isUnsafeFingerprintSecret(String value) {
        if (value == null || value.length() < MINIMUM_FINGERPRINT_SECRET_LENGTH) {
            return true;
        }
        return value.contains("replace-with") || value.contains("development-only") || value.contains("test-only");
    }
}

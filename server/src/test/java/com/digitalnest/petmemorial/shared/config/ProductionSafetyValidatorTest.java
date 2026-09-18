package com.digitalnest.petmemorial.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSafetyValidatorTest {

    @Test
    void skipsTheProductionGuardForLocalAndTestProfiles() {
        assertDoesNotThrow(() -> validator("local", "pet_memorial_dev", false, true, "local-development-only-secret").run(null));
        assertDoesNotThrow(() -> validator("test", "pet_memorial_dev", false, true, "test-only-secret").run(null));
    }

    @Test
    void rejectsTheExampleDatabasePasswordOutsideLocalProfiles() {
        assertThrows(IllegalStateException.class,
                () -> validator("production", "replace-with-a-long-random-password", true, false, safeSecret()).run(null));
    }

    @Test
    void rejectsInsecureCookiesAndMockPaymentsOutsideLocalProfiles() {
        assertThrows(IllegalStateException.class,
                () -> validator("production", "this-is-a-strong-production-password", false, false, safeSecret()).run(null));
        assertThrows(IllegalStateException.class,
                () -> validator("production", "this-is-a-strong-production-password", true, true, safeSecret()).run(null));
    }

    @Test
    void rejectsAnUnsafeFingerprintSecretOutsideLocalProfiles() {
        assertThrows(IllegalStateException.class,
                () -> validator("production", "this-is-a-strong-production-password", true, false, "too-short").run(null));
        assertThrows(IllegalStateException.class,
                () -> validator("production", "this-is-a-strong-production-password", true, false,
                        "replace-with-a-visitor-fingerprint-secret-that-is-long-enough").run(null));
    }

    @Test
    void acceptsAProductionSafeConfiguration() {
        assertDoesNotThrow(() -> validator("production", "this-is-a-strong-production-password", true, false, safeSecret()).run(null));
    }

    private ProductionSafetyValidator validator(
            String profile, String password, boolean secureCookies, boolean mockPayments, String visitorFingerprintSecret
    ) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new ProductionSafetyValidator(environment, password, secureCookies, mockPayments, visitorFingerprintSecret);
    }

    private String safeSecret() {
        return "a-production-only-fingerprint-secret-that-is-long-enough";
    }
}

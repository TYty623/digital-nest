package com.digitalnest.petmemorial.shared.ratelimit;

import com.digitalnest.petmemorial.shared.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptRateLimiterTest {

    @Test
    void blocksTheSixthFailureAndClearsTheWindowAfterASuccessfulLogin() {
        LoginAttemptRateLimiter limiter = new LoginAttemptRateLimiter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.42");

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.recordFailure(request, "family@example.com");
        }

        ApiException exception = assertThrows(ApiException.class,
                () -> limiter.check(request, "family@example.com"));
        assertEquals("LOGIN_RATE_LIMITED", exception.code());

        limiter.clear(request, "family@example.com");
        assertDoesNotThrow(() -> limiter.check(request, "family@example.com"));
    }
}

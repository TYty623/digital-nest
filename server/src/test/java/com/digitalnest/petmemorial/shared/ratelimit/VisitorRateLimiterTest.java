package com.digitalnest.petmemorial.shared.ratelimit;

import com.digitalnest.petmemorial.shared.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisitorRateLimiterTest {

    @Test
    void limitsRepeatedTributesFromTheSameVisitorOnTheSamePage() {
        VisitorRateLimiter limiter = new VisitorRateLimiter("test-fingerprint-key-not-for-production");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.check(request, "tribute", "maimai-1234");
        }

        ApiException exception = assertThrows(ApiException.class,
                () -> limiter.check(request, "tribute", "maimai-1234"));
        assertEquals("VISITOR_RATE_LIMITED", exception.code());
    }

    @Test
    void fingerprintsUseTheConfiguredHmacKeyInsteadOfAPlainIpHash() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");

        String first = new VisitorRateLimiter("test-fingerprint-key-not-for-production").fingerprint(request);
        String second = new VisitorRateLimiter("another-test-fingerprint-key-not-for-production").fingerprint(request);

        assertEquals(64, first.length());
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
        org.junit.jupiter.api.Assertions.assertNotEquals("203.0.113.7", first);
    }
}

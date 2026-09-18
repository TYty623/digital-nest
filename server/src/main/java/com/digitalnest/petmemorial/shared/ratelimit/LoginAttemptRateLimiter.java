package com.digitalnest.petmemorial.shared.ratelimit;

import com.digitalnest.petmemorial.shared.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Map<String, AttemptState> failures = new ConcurrentHashMap<>();

    public void check(HttpServletRequest request, String email) {
        String key = key(request, email);
        AttemptState state = failures.get(key);
        if (state == null) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (state) {
            if (state.windowStartedAt + WINDOW.toMillis() <= now) {
                failures.remove(key, state);
                return;
            }
            if (state.count >= MAX_FAILURES) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED", "登录尝试过于频繁，请 15 分钟后再试。");
            }
        }
    }

    public void recordFailure(HttpServletRequest request, String email) {
        String key = key(request, email);
        long now = System.currentTimeMillis();
        AttemptState state = failures.computeIfAbsent(key, ignored -> new AttemptState(now));
        synchronized (state) {
            if (state.windowStartedAt + WINDOW.toMillis() <= now) {
                state.windowStartedAt = now;
                state.count = 0;
            }
            state.count++;
        }
    }

    public void clear(HttpServletRequest request, String email) {
        failures.remove(key(request, email));
    }

    private String key(HttpServletRequest request, String email) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return hash(normalizedEmail + ":" + request.getRemoteAddr());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static final class AttemptState {
        private long windowStartedAt;
        private int count;

        private AttemptState(long windowStartedAt) {
            this.windowStartedAt = windowStartedAt;
        }
    }
}

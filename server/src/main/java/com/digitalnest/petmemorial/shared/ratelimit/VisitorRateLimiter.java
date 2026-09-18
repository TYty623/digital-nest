package com.digitalnest.petmemorial.shared.ratelimit;

import com.digitalnest.petmemorial.shared.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class VisitorRateLimiter {

    private static final String HMAC_SHA_256 = "HmacSHA256";
    private static final Map<String, Limit> LIMITS = Map.of(
            "tribute", new Limit(5, Duration.ofMinutes(10)),
            "report", new Limit(12, Duration.ofHours(1)),
            "unlock", new Limit(8, Duration.ofMinutes(15)),
            "light", new Limit(3, Duration.ofHours(24))
    );

    private final Map<String, ArrayDeque<Long>> requests = new ConcurrentHashMap<>();
    private final SecretKeySpec fingerprintKey;

    public VisitorRateLimiter(@Value("${app.privacy.visitor-fingerprint-secret}") String fingerprintSecret) {
        this.fingerprintKey = new SecretKeySpec(fingerprintSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256);
    }

    public void check(HttpServletRequest request, String action, String scope) {
        Limit limit = LIMITS.get(action);
        if (limit == null) {
            throw new IllegalArgumentException("未知的限流动作");
        }
        String key = action + ":" + scope + ":" + fingerprint(request);
        ArrayDeque<Long> timestamps = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        long windowStart = now - limit.window().toMillis();
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= limit.maxRequests()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "VISITOR_RATE_LIMITED", "操作过于频繁，请稍后再试。");
            }
            timestamps.addLast(now);
        }
    }

    public String fingerprint(HttpServletRequest request) {
        return hmac(request.getRemoteAddr());
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(fingerprintKey);
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 不可用", exception);
        }
    }

    private record Limit(int maxRequests, Duration window) {
    }
}

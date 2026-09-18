package com.digitalnest.petmemorial.shared.api;

import org.slf4j.MDC;

public record ApiResponse<T>(T data, String requestId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, MDC.get("requestId"));
    }
}


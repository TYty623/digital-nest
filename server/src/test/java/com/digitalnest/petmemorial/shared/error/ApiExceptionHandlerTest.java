package com.digitalnest.petmemorial.shared.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void unexpectedExceptionReturnsGenericSafeMessage() {
        var response = handler.handleUnexpectedException(new IllegalStateException("database host leaked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().data().message()).isEqualTo("服务暂时不可用，请稍后再试。");
        assertThat(response.getBody().data().message()).doesNotContain("database host leaked");
    }
}

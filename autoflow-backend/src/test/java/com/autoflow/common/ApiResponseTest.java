package com.autoflow.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse Structure & Factory Tests")
class ApiResponseTest {

    @Test
    @DisplayName("Should create successful response with payload")
    void testOkResponse() {
        ApiResponse<String> response = ApiResponse.ok("System Healthy");
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("System Healthy");
        assertThat(response.getTimestamp()).isNotNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    @DisplayName("Should create error response with code and message")
    void testErrorResponse() {
        ApiResponse<Void> response = ApiResponse.error("FORBIDDEN", "Access denied", List.of("Role INFLUENCER cannot mutate campaigns"));
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNotNull();
        assertThat(response.getError().getCode()).isEqualTo("FORBIDDEN");
        assertThat(response.getError().getDetails()).hasSize(1);
    }
}

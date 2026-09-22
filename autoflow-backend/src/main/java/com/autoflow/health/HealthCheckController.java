package com.autoflow.health;

import com.autoflow.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;

/**
 * Public health check and service telemetry endpoint.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health & Telemetry", description = "Liveness, readiness, and system telemetry endpoints")
public class HealthCheckController {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @GetMapping
    @Operation(summary = "System Liveness & Telemetry", description = "Returns active platform status, environment, version, and uptime")
    public ResponseEntity<ApiResponse<HealthStatusResponse>> checkHealth() {
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        HealthStatusResponse health = HealthStatusResponse.builder()
                .status("UP")
                .service("AutoFlow AI Backend")
                .version("1.0.0")
                .environment(activeProfile)
                .uptimeSeconds(uptimeMillis / 1000)
                .timestamp(Instant.now())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(health));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthStatusResponse {
        private String status;
        private String service;
        private String version;
        private String environment;
        private long uptimeSeconds;
        private Instant timestamp;
    }
}

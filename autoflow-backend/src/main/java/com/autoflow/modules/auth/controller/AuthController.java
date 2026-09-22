package com.autoflow.modules.auth.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.modules.auth.dto.*;
import com.autoflow.modules.auth.security.JwtProvider;
import com.autoflow.modules.auth.service.AuthService;
import com.autoflow.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication & Identity", description = "Endpoints for user registration, JWT lifecycle, sessions, and tenant switching")
public class AuthController {

    private final AuthService authService;
    private final JwtProvider jwtProvider;

    @PostMapping("/register")
    @Operation(summary = "Register New Creator Account", description = "Creates a new user profile, provisions tenant organization, and activates Free plan subscription")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.register(request, clientIp, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Registration successful", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate User", description = "Validates credentials and returns JWT access and refresh token pair")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.login(request, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate & Refresh Tokens", description = "Exchanges a valid refresh token for a newly rotated token pair")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.refreshToken(request, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponse.ok("Tokens refreshed successfully", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke Refresh Token", description = "Revokes the active refresh token session")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            authService.logout(request.getRefreshToken());
        }
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully", null));
    }

    @PostMapping("/switch-org")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Switch Active Workspace", description = "Issues a refreshed access token scoped to the selected organization")
    public ResponseEntity<ApiResponse<AuthResponse>> switchOrganization(
            @Valid @RequestBody SwitchOrgRequest request,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        UUID userId = jwtProvider.extractUserId(token);
        AuthResponse response = authService.switchOrganization(userId, request.getOrganizationId());
        return ResponseEntity.ok(ApiResponse.ok("Switched workspace successfully", response));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Current User Profile", description = "Retrieves active user profile details and current organization membership")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        UUID userId = jwtProvider.extractUserId(token);
        UUID orgId = TenantContext.getTenantId().orElse(null);
        UserProfileResponse response = authService.getCurrentUserProfile(userId, orgId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}

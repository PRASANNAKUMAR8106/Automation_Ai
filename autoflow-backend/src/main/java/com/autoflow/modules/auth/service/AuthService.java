package com.autoflow.modules.auth.service;

import com.autoflow.modules.auth.dto.*;

import java.util.UUID;

public interface AuthService {
    AuthResponse register(RegisterRequest request, String clientIp, String userAgent);
    AuthResponse login(LoginRequest request, String clientIp, String userAgent);
    AuthResponse refreshToken(RefreshTokenRequest request, String clientIp, String userAgent);
    AuthResponse switchOrganization(UUID userId, UUID targetOrgId);
    void logout(String refreshToken);
    UserProfileResponse getCurrentUserProfile(UUID userId, UUID activeOrgId);
}

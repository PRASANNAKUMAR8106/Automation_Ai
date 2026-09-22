package com.autoflow.modules.auth.security;

import com.autoflow.modules.tenant.entity.MembershipRole;
import com.autoflow.modules.user.entity.Role;
import com.autoflow.modules.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token Provider handling generation, parsing, and cryptographic verification.
 */
@Slf4j
@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpirationSeconds;
    private final long refreshTokenExpirationSeconds;

    public JwtProvider(
            @Value("${autoflow.security.jwt.secret}") String secret,
            @Value("${autoflow.security.jwt.access-token-expiration-seconds:900}") long accessTokenExpirationSeconds,
            @Value("${autoflow.security.jwt.refresh-token-expiration-seconds:2592000}") long refreshTokenExpirationSeconds) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
    }

    public String generateAccessToken(User user, UUID organizationId, MembershipRole membershipRole) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenExpirationSeconds);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("orgId", organizationId.toString())
                .claim("membershipRole", membershipRole.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(getClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return getClaims(token).get("email", String.class);
    }

    public Role extractUserRole(String token) {
        return Role.valueOf(getClaims(token).get("role", String.class));
    }

    public UUID extractOrganizationId(String token) {
        String orgId = getClaims(token).get("orgId", String.class);
        return orgId != null ? UUID.fromString(orgId) : null;
    }

    public MembershipRole extractMembershipRole(String token) {
        String role = getClaims(token).get("membershipRole", String.class);
        return role != null ? MembershipRole.valueOf(role) : null;
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpirationSeconds;
    }

    public long getRefreshTokenExpirationSeconds() {
        return refreshTokenExpirationSeconds;
    }
}

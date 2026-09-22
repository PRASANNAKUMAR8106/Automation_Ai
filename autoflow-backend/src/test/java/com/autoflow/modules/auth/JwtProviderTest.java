package com.autoflow.modules.auth;

import com.autoflow.modules.auth.security.JwtProvider;
import com.autoflow.modules.tenant.entity.MembershipRole;
import com.autoflow.modules.user.entity.Role;
import com.autoflow.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtProvider Token Generation and Verification Unit Tests")
class JwtProviderTest {

    private JwtProvider jwtProvider;
    private final String secret = "test_secret_key_that_is_long_enough_for_hmac_sha_512_verification_purposes_1234567890";

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(secret, 900, 2592000);
    }

    @Test
    @DisplayName("Should generate valid JWT and parse all embedded claims correctly")
    void testGenerateAndValidateToken() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        User user = User.builder()
                .email("creator@autoflow.ai")
                .role(Role.CREATOR)
                .build();
        user.setId(userId);

        String token = jwtProvider.generateAccessToken(user, orgId, MembershipRole.OWNER);

        assertThat(jwtProvider.validateToken(token)).isTrue();
        assertThat(jwtProvider.extractUserId(token)).isEqualTo(userId);
        assertThat(jwtProvider.extractEmail(token)).isEqualTo("creator@autoflow.ai");
        assertThat(jwtProvider.extractUserRole(token)).isEqualTo(Role.CREATOR);
        assertThat(jwtProvider.extractOrganizationId(token)).isEqualTo(orgId);
        assertThat(jwtProvider.extractMembershipRole(token)).isEqualTo(MembershipRole.OWNER);
    }

    @Test
    @DisplayName("Should reject tampered token signatures")
    void testTamperedTokenRejected() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().email("creator@autoflow.ai").role(Role.CUSTOMER).build();
        user.setId(userId);

        String token = jwtProvider.generateAccessToken(user, UUID.randomUUID(), MembershipRole.VIEWER);
        String tampered = token.substring(0, token.length() - 5) + "abcde";

        assertThat(jwtProvider.validateToken(tampered)).isFalse();
    }
}

package com.autoflow.modules.auth;

import com.autoflow.common.exceptions.AutoFlowException;
import com.autoflow.common.exceptions.UnauthorizedException;
import com.autoflow.modules.auth.dto.AuthResponse;
import com.autoflow.modules.auth.dto.LoginRequest;
import com.autoflow.modules.auth.dto.RegisterRequest;
import com.autoflow.modules.auth.entity.RefreshToken;
import com.autoflow.modules.auth.repository.RefreshTokenRepository;
import com.autoflow.modules.auth.security.JwtProvider;
import com.autoflow.modules.auth.service.AuthServiceImpl;
import com.autoflow.modules.billing.entity.Plan;
import com.autoflow.modules.billing.entity.Subscription;
import com.autoflow.modules.billing.repository.PlanRepository;
import com.autoflow.modules.billing.repository.SubscriptionRepository;
import com.autoflow.modules.influencer.repository.PromoCodeRepository;
import com.autoflow.modules.influencer.repository.ReferralAttributionRepository;
import com.autoflow.modules.tenant.entity.Membership;
import com.autoflow.modules.tenant.entity.MembershipRole;
import com.autoflow.modules.tenant.entity.Organization;
import com.autoflow.modules.tenant.repository.MembershipRepository;
import com.autoflow.modules.tenant.repository.OrganizationRepository;
import com.autoflow.modules.user.entity.Role;
import com.autoflow.modules.user.entity.User;
import com.autoflow.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private MembershipRepository membershipRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PromoCodeRepository promoCodeRepository;
    @Mock private ReferralAttributionRepository referralAttributionRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                userRepository,
                organizationRepository,
                membershipRepository,
                refreshTokenRepository,
                planRepository,
                subscriptionRepository,
                promoCodeRepository,
                referralAttributionRepository,
                passwordEncoder,
                jwtProvider
        );
    }

    @Test
    @DisplayName("Should successfully register new user, workspace, and free subscription")
    void testSuccessfulRegistration() {
        RegisterRequest req = RegisterRequest.builder()
                .email("priya@brand.com")
                .password("StrongPass123")
                .firstName("Priya")
                .lastName("Nair")
                .organizationName("Priya Digital")
                .build();

        when(userRepository.existsByEmailIgnoreCase("priya@brand.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongPass123")).thenReturn("$2a$12$hashed");
        
        UUID userId = UUID.randomUUID();
        User savedUser = User.builder()
                .email("priya@brand.com")
                .firstName("Priya")
                .lastName("Nair")
                .role(Role.CUSTOMER)
                .build();
        savedUser.setId(userId);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UUID orgId = UUID.randomUUID();
        Organization savedOrg = Organization.builder()
                .name("Priya Digital")
                .slug("priya-digital")
                .build();
        savedOrg.setId(orgId);
        when(organizationRepository.save(any(Organization.class))).thenReturn(savedOrg);

        Plan freePlan = Plan.builder().code("FREE").name("Free Starter").build();
        when(planRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));

        when(jwtProvider.generateAccessToken(eq(savedUser), eq(orgId), eq(MembershipRole.OWNER))).thenReturn("mock.jwt.token");
        when(jwtProvider.generateRefreshToken()).thenReturn("mock-refresh-token-123");
        when(jwtProvider.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(jwtProvider.getRefreshTokenExpirationSeconds()).thenReturn(2592000L);

        AuthResponse response = authService.register(req, "127.0.0.1", "JUnit-Agent");

        assertThat(response.getAccessToken()).isEqualTo("mock.jwt.token");
        assertThat(response.getRefreshToken()).isEqualTo("mock-refresh-token-123");
        assertThat(response.getUser().getEmail()).isEqualTo("priya@brand.com");
        assertThat(response.getUser().getActiveOrganizationId()).isEqualTo(orgId);
        assertThat(response.getUser().getActiveMembershipRole()).isEqualTo(MembershipRole.OWNER);

        verify(subscriptionRepository).save(any(Subscription.class));
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Should reject registration if email already exists")
    void testDuplicateEmailRegistrationThrows() {
        RegisterRequest req = RegisterRequest.builder()
                .email("existing@brand.com")
                .password("Password123")
                .firstName("Test")
                .organizationName("Org")
                .build();

        when(userRepository.existsByEmailIgnoreCase("existing@brand.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req, "127.0.0.1", "Agent"))
                .isInstanceOf(AutoFlowException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void testSuccessfulLogin() {
        LoginRequest req = LoginRequest.builder()
                .email("creator@autoflow.ai")
                .password("Password123")
                .build();

        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("creator@autoflow.ai")
                .passwordHash("$2a$12$hashed")
                .firstName("Creator")
                .role(Role.CREATOR)
                .active(true)
                .build();
        user.setId(userId);

        UUID orgId = UUID.randomUUID();
        Organization org = Organization.builder().name("Creator Studio").slug("creator-studio").build();
        org.setId(orgId);

        Membership membership = Membership.builder()
                .organizationId(orgId)
                .user(user)
                .role(MembershipRole.OWNER)
                .build();

        when(userRepository.findByEmailIgnoreCase("creator@autoflow.ai")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "$2a$12$hashed")).thenReturn(true);
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(membership));
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        when(jwtProvider.generateAccessToken(eq(user), eq(orgId), eq(MembershipRole.OWNER))).thenReturn("valid.jwt");
        when(jwtProvider.generateRefreshToken()).thenReturn("refresh-token-xyz");

        AuthResponse response = authService.login(req, "127.0.0.1", "Agent");

        assertThat(response.getAccessToken()).isEqualTo("valid.jwt");
        assertThat(response.getUser().getEmail()).isEqualTo("creator@autoflow.ai");
    }

    @Test
    @DisplayName("Should reject login with invalid password")
    void testInvalidPasswordThrows() {
        LoginRequest req = LoginRequest.builder()
                .email("creator@autoflow.ai")
                .password("WrongPass")
                .build();

        User user = User.builder()
                .email("creator@autoflow.ai")
                .passwordHash("$2a$12$hashed")
                .active(true)
                .build();

        when(userRepository.findByEmailIgnoreCase("creator@autoflow.ai")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass", "$2a$12$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req, "127.0.0.1", "Agent"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email or password");
    }
}

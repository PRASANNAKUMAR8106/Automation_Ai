package com.autoflow.modules.auth.service;

import com.autoflow.common.exceptions.AutoFlowException;
import com.autoflow.common.exceptions.ForbiddenException;
import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.common.exceptions.UnauthorizedException;
import com.autoflow.modules.auth.dto.*;
import com.autoflow.modules.auth.entity.RefreshToken;
import com.autoflow.modules.auth.repository.RefreshTokenRepository;
import com.autoflow.modules.auth.security.JwtProvider;
import com.autoflow.modules.billing.entity.Plan;
import com.autoflow.modules.billing.entity.Subscription;
import com.autoflow.modules.billing.entity.SubscriptionStatus;
import com.autoflow.modules.billing.repository.PlanRepository;
import com.autoflow.modules.billing.repository.SubscriptionRepository;
import com.autoflow.modules.influencer.entity.PromoCode;
import com.autoflow.modules.influencer.entity.ReferralAttribution;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final ReferralAttributionRepository referralAttributionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp, String userAgent) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new AutoFlowException("EMAIL_ALREADY_EXISTS", "Email address is already registered", HttpStatus.CONFLICT);
        }

        // 1. Create User
        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName() != null ? request.getLastName().trim() : null)
                .role(Role.CUSTOMER)
                .emailVerified(false)
                .active(true)
                .lastLoginAt(Instant.now())
                .build();
        user = userRepository.save(user);

        // 2. Create Organization
        String slug = generateUniqueSlug(request.getOrganizationName());
        Organization organization = Organization.builder()
                .name(request.getOrganizationName().trim())
                .slug(slug)
                .active(true)
                .build();
        organization = organizationRepository.save(organization);

        // 3. Create Membership (Owner)
        Membership membership = Membership.builder()
                .organizationId(organization.getId())
                .user(user)
                .role(MembershipRole.OWNER)
                .build();
        membershipRepository.save(membership);

        // 4. Provision Initial FREE Subscription
        Plan freePlan = planRepository.findByCode("FREE")
                .orElseGet(() -> planRepository.save(Plan.builder()
                        .code("FREE")
                        .name("Free Starter")
                        .billingInterval("MONTHLY")
                        .build()));

        Subscription subscription = Subscription.builder()
                .organizationId(organization.getId())
                .plan(freePlan)
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(Instant.now())
                .currentPeriodEnd(Instant.now().plus(365, ChronoUnit.DAYS))
                .build();
        subscriptionRepository.save(subscription);

        // 5. Handle Referral / Promo Code Attribution if provided
        if (request.getReferralCode() != null && !request.getReferralCode().isBlank()) {
            attributeReferral(request.getReferralCode().trim(), organization.getId(), user.getId());
        }

        // 6. Generate Tokens
        return createAuthResponse(user, organization, MembershipRole.OWNER, clientIp, userAgent);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp, String userAgent) {
        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.isActive()) {
            throw new ForbiddenException("Your account has been deactivated. Please contact support.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Find primary organization membership
        List<Membership> memberships = membershipRepository.findByUserId(user.getId());
        if (memberships.isEmpty()) {
            throw new ResourceNotFoundException("No active organization found for user", user.getId());
        }

        Membership primaryMembership = memberships.getFirst();
        Organization organization = organizationRepository.findById(primaryMembership.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", primaryMembership.getOrganizationId()));

        return createAuthResponse(user, organization, primaryMembership.getRole(), clientIp, userAgent);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request, String clientIp, String userAgent) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(request.getRefreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));

        if (!token.isValid()) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        User user = token.getUser();
        if (!user.isActive()) {
            throw new ForbiddenException("Account deactivated");
        }

        // Rotate Refresh Token
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        List<Membership> memberships = membershipRepository.findByUserId(user.getId());
        if (memberships.isEmpty()) {
            throw new ResourceNotFoundException("Organization", "membership not found");
        }
        Membership membership = memberships.getFirst();
        Organization org = organizationRepository.findById(membership.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", membership.getOrganizationId()));

        return createAuthResponse(user, org, membership.getRole(), clientIp, userAgent);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse switchOrganization(UUID userId, UUID targetOrgId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Membership membership = membershipRepository.findByOrganizationIdAndUserId(targetOrgId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of the requested organization"));

        Organization org = organizationRepository.findById(targetOrgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", targetOrgId));

        String accessToken = jwtProvider.generateAccessToken(user, org.getId(), membership.getRole());

        UserProfileResponse profile = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .emailVerified(user.isEmailVerified())
                .activeOrganizationId(org.getId())
                .activeOrganizationName(org.getName())
                .activeMembershipRole(membership.getRole())
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(null) // Keep existing refresh token
                .tokenType("Bearer")
                .expiresInSeconds(jwtProvider.getAccessTokenExpirationSeconds())
                .user(profile)
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(refreshToken)
                    .ifPresent(token -> {
                        token.setRevoked(true);
                        refreshTokenRepository.save(token);
                    });
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UUID userId, UUID activeOrgId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Organization org = null;
        MembershipRole role = null;
        if (activeOrgId != null) {
            org = organizationRepository.findById(activeOrgId).orElse(null);
            role = membershipRepository.findByOrganizationIdAndUserId(activeOrgId, userId)
                    .map(Membership::getRole).orElse(null);
        }

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .emailVerified(user.isEmailVerified())
                .activeOrganizationId(org != null ? org.getId() : null)
                .activeOrganizationName(org != null ? org.getName() : null)
                .activeMembershipRole(role)
                .build();
    }

    private AuthResponse createAuthResponse(User user, Organization org, MembershipRole membershipRole, String clientIp, String userAgent) {
        String accessToken = jwtProvider.generateAccessToken(user, org.getId(), membershipRole);
        String rawRefreshToken = jwtProvider.generateRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(rawRefreshToken)
                .expiresAt(Instant.now().plusSeconds(jwtProvider.getRefreshTokenExpirationSeconds()))
                .revoked(false)
                .ipAddress(clientIp)
                .deviceInfo(userAgent)
                .build();
        refreshTokenRepository.save(refreshToken);

        UserProfileResponse userProfile = UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .emailVerified(user.isEmailVerified())
                .activeOrganizationId(org.getId())
                .activeOrganizationName(org.getName())
                .activeMembershipRole(membershipRole)
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresInSeconds(jwtProvider.getAccessTokenExpirationSeconds())
                .user(userProfile)
                .build();
    }

    private void attributeReferral(String promoCodeStr, UUID orgId, UUID userId) {
        promoCodeRepository.findByCodeIgnoreCase(promoCodeStr)
                .ifPresent(promo -> {
                    if ("ACTIVE".equalsIgnoreCase(promo.getStatus())) {
                        promo.setCurrentUses(promo.getCurrentUses() + 1);
                        promoCodeRepository.save(promo);

                        ReferralAttribution attribution = ReferralAttribution.builder()
                                .promoCode(promo)
                                .influencer(promo.getInfluencer())
                                .organizationId(orgId)
                                .userId(userId)
                                .attributionType("PROMO_ENTERED")
                                .build();
                        referralAttributionRepository.save(attribution);
                        log.info("Attributed organization [{}] to influencer [{}] with promo [{}]",
                                orgId, promo.getInfluencer().getId(), promo.getCode());
                    }
                });
    }

    private String generateUniqueSlug(String baseName) {
        String normalized = Normalizer.normalize(baseName, Normalizer.Form.NFD)
                .replaceAll("[^\\w\\s-]", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-");
        if (normalized.isBlank()) {
            normalized = "workspace";
        }
        String slug = normalized;
        int counter = 1;
        while (organizationRepository.existsBySlug(slug)) {
            slug = normalized + "-" + counter++;
        }
        return slug;
    }
}

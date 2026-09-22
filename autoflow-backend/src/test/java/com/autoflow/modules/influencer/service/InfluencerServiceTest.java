package com.autoflow.modules.influencer.service;

import com.autoflow.modules.billing.entity.Payment;
import com.autoflow.modules.influencer.dto.*;
import com.autoflow.modules.influencer.entity.*;
import com.autoflow.modules.influencer.repository.*;
import com.autoflow.modules.user.entity.Role;
import com.autoflow.modules.user.entity.User;
import com.autoflow.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Influencer & Commission Engine Tests")
class InfluencerServiceTest {

    @Mock
    private InfluencerRepository influencerRepository;

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @Mock
    private CommissionRepository commissionRepository;

    @Mock
    private PayoutRepository payoutRepository;

    @Mock
    private ReferralClickRepository referralClickRepository;

    @Mock
    private ReferralAttributionRepository referralAttributionRepository;

    @Mock
    private UserRepository userRepository;

    private InfluencerServiceImpl influencerService;

    @BeforeEach
    void setUp() {
        influencerService = new InfluencerServiceImpl(
                influencerRepository,
                campaignRepository,
                promoCodeRepository,
                commissionRepository,
                payoutRepository,
                referralClickRepository,
                referralAttributionRepository,
                userRepository
        );
        ReflectionTestUtils.setField(influencerService, "baseUrl", "http://localhost:8080");
    }

    @Test
    @DisplayName("Should create influencer with INFLUENCER user role")
    void shouldCreateInfluencer() {
        CreateInfluencerRequest req = CreateInfluencerRequest.builder()
                .name("Tech Influencer")
                .email("partner@example.com")
                .instagramHandle("@tech_influencer")
                .country("IN")
                .build();

        when(influencerRepository.existsByEmailIgnoreCase("partner@example.com")).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase("partner@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(influencerRepository.save(any(Influencer.class))).thenAnswer(i -> {
            Influencer inf = i.getArgument(0);
            inf.setId(UUID.randomUUID());
            return inf;
        });

        Influencer result = influencerService.createInfluencer(req);

        assertNotNull(result);
        assertEquals("Tech Influencer", result.getName());
        assertEquals("partner@example.com", result.getEmail());
        verify(userRepository).save(any(User.class));
        verify(influencerRepository).save(any(Influencer.class));
    }

    @Test
    @DisplayName("Should calculate percentage commission on NET qualifying amount")
    void shouldCalculateCommissionOnNetQualifying() {
        Campaign campaign = Campaign.builder()
                .commissionBasis(CommissionBasis.NET_QUALIFYING)
                .commissionType(CommissionType.PERCENTAGE)
                .attributionWindowDays(30)
                .build();

        Influencer influencer = Influencer.builder().name("Influencer").build();
        influencer.setId(UUID.randomUUID());

        PromoCode promo = PromoCode.builder()
                .campaign(campaign)
                .influencer(influencer)
                .commissionType(CommissionType.PERCENTAGE)
                .commissionValue(BigDecimal.valueOf(20.00)) // 20%
                .build();

        Payment payment = Payment.builder()
                .amountInr(BigDecimal.valueOf(1499.00))
                .discountInr(BigDecimal.valueOf(299.80))
                .netInr(BigDecimal.valueOf(1199.20))
                .build();

        when(commissionRepository.save(any(Commission.class))).thenAnswer(i -> i.getArgument(0));

        Commission comm = influencerService.calculateAndRecordCommission(payment, promo);

        assertNotNull(comm);
        // 20% of 1199.20 = 239.84
        assertEquals(new BigDecimal("239.84"), comm.getAmountInr());
        assertEquals(CommissionStatus.PENDING, comm.getStatus());
        assertTrue(comm.getQualifiesAt().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("Should record referral click with privacy-safe hashed IP")
    void shouldRecordReferralClickWithHashedIp() {
        Influencer influencer = Influencer.builder().build();
        influencer.setId(UUID.randomUUID());

        PromoCode promo = PromoCode.builder().code("TECH20").influencer(influencer).build();
        promo.setId(UUID.randomUUID());

        when(promoCodeRepository.findByCodeIgnoreCase("TECH20")).thenReturn(Optional.of(promo));

        influencerService.recordReferralClick("TECH20", "192.168.1.100", "Mozilla/5.0", "instagram", "bio", "launch");

        ArgumentCaptor<ReferralClick> captor = ArgumentCaptor.forClass(ReferralClick.class);
        verify(referralClickRepository).save(captor.capture());
        ReferralClick click = captor.getValue();

        assertNotEquals("192.168.1.100", click.getIpHash());
        assertEquals(64, click.getIpHash().length()); // SHA-256 hex string length
        assertEquals("instagram", click.getUtmSource());
    }

    @Test
    @DisplayName("Should process admin payout and mark approved commissions as PAID")
    void shouldProcessPayoutAndMarkCommissionsPaid() {
        UUID adminId = UUID.randomUUID();
        User admin = User.builder().email("admin@autoflow.ai").role(Role.ADMIN).build();
        admin.setId(adminId);

        Influencer influencer = Influencer.builder().name("Influencer A").build();
        influencer.setId(UUID.randomUUID());

        UUID payoutId = UUID.randomUUID();
        Payout payout = Payout.builder()
                .id(payoutId)
                .influencer(influencer)
                .amountInr(BigDecimal.valueOf(500.00))
                .status(PayoutStatus.REQUESTED)
                .build();

        Commission comm1 = Commission.builder()
                .amountInr(BigDecimal.valueOf(300.00))
                .status(CommissionStatus.APPROVED)
                .build();
        Commission comm2 = Commission.builder()
                .amountInr(BigDecimal.valueOf(200.00))
                .status(CommissionStatus.APPROVED)
                .build();

        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(payoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));
        when(payoutRepository.save(any(Payout.class))).thenAnswer(i -> i.getArgument(0));
        when(commissionRepository.findByInfluencerIdAndStatus(influencer.getId(), CommissionStatus.APPROVED))
                .thenReturn(List.of(comm1, comm2));

        ProcessPayoutRequest request = ProcessPayoutRequest.builder()
                .payoutId(payoutId)
                .transactionReference("UTR_BANK_REF_12345")
                .adminNotes("Disbursed via IMPS")
                .build();

        Payout processed = influencerService.processPayout(adminId, request);

        assertEquals(PayoutStatus.PAID, processed.getStatus());
        assertEquals("UTR_BANK_REF_12345", processed.getTransactionReference());
        assertEquals(CommissionStatus.PAID, comm1.getStatus());
        assertEquals(CommissionStatus.PAID, comm2.getStatus());
        assertNotNull(comm1.getPaidAt());
        assertNotNull(comm2.getPaidAt());
    }
}

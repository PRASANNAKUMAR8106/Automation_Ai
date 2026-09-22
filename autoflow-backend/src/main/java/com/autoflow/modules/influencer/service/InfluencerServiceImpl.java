package com.autoflow.modules.influencer.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.common.exceptions.ValidationException;
import com.autoflow.modules.billing.entity.Payment;
import com.autoflow.modules.influencer.dto.*;
import com.autoflow.modules.influencer.entity.*;
import com.autoflow.modules.influencer.repository.*;
import com.autoflow.modules.user.entity.Role;
import com.autoflow.modules.user.entity.User;
import com.autoflow.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InfluencerServiceImpl implements InfluencerService {

    private final InfluencerRepository influencerRepository;
    private final CampaignRepository campaignRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final CommissionRepository commissionRepository;
    private final PayoutRepository payoutRepository;
    private final ReferralClickRepository referralClickRepository;
    private final ReferralAttributionRepository referralAttributionRepository;
    private final UserRepository userRepository;

    @Value("${autoflow.app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    @Transactional
    public Influencer createInfluencer(CreateInfluencerRequest request) {
        if (influencerRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new ValidationException("An influencer with email " + request.getEmail() + " already exists");
        }

        // Create or find user with INFLUENCER role
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(request.getEmail().toLowerCase())
                            .passwordHash("$2a$12$e0MYzXyDFv3fP4Z5bKq9s.dummyHashForInfluencerInvites2026")
                            .firstName(request.getName())
                            .role(Role.INFLUENCER)
                            .emailVerified(true)
                            .build();
                    return userRepository.save(newUser);
                });

        Influencer influencer = Influencer.builder()
                .user(user)
                .name(request.getName())
                .email(request.getEmail().toLowerCase())
                .instagramHandle(request.getInstagramHandle())
                .country(request.getCountry() != null ? request.getCountry() : "IN")
                .notes(request.getNotes())
                .status("ACTIVE")
                .build();

        influencer = influencerRepository.save(influencer);
        log.info("Created influencer record {} for email {}", influencer.getId(), influencer.getEmail());
        return influencer;
    }

    @Override
    @Transactional
    public Campaign createCampaign(CreateCampaignRequest request) {
        Campaign campaign = Campaign.builder()
                .name(request.getName())
                .description(request.getDescription())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .commissionType(request.getCommissionType())
                .commissionValue(request.getCommissionValue())
                .commissionBasis(request.getCommissionBasis())
                .attributionWindowDays(request.getAttributionWindowDays())
                .startDate(Instant.now())
                .status("ACTIVE")
                .build();

        campaign = campaignRepository.save(campaign);
        log.info("Created campaign {} with basis {}", campaign.getId(), campaign.getCommissionBasis());
        return campaign;
    }

    @Override
    @Transactional
    public PromoCode createPromoCode(CreatePromoCodeRequest request) {
        Campaign campaign = campaignRepository.findById(request.getCampaignId())
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", request.getCampaignId()));

        Influencer influencer = influencerRepository.findById(request.getInfluencerId())
                .orElseThrow(() -> new ResourceNotFoundException("Influencer", request.getInfluencerId()));

        String normalizedCode = request.getCode().trim().toUpperCase();
        if (promoCodeRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new ValidationException("Promo code '" + normalizedCode + "' is already in use");
        }

        PromoCode promo = PromoCode.builder()
                .campaign(campaign)
                .influencer(influencer)
                .code(normalizedCode)
                .discountType(campaign.getDiscountType())
                .discountValue(campaign.getDiscountValue())
                .commissionType(campaign.getCommissionType())
                .commissionValue(campaign.getCommissionValue())
                .commissionBasis(campaign.getCommissionBasis())
                .maxUses(request.getMaxUses())
                .minimumPurchaseInr(request.getMinimumPurchaseInr() != null ? request.getMinimumPurchaseInr() : BigDecimal.ZERO)
                .startDate(Instant.now())
                .status("ACTIVE")
                .build();

        promo = promoCodeRepository.save(promo);
        log.info("Created promo code {} for influencer {}", promo.getCode(), influencer.getId());
        return promo;
    }

    @Override
    @Transactional(readOnly = true)
    public InfluencerStatsResponse getInfluencerStats(UUID userId) {
        Influencer influencer = influencerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Influencer", userId));

        long totalClicks = referralClickRepository.countByInfluencerId(influencer.getId());

        List<Commission> commissions = commissionRepository.findByInfluencerIdOrderByCreatedAtDesc(influencer.getId());

        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal approved = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;

        for (Commission c : commissions) {
            if (c.getStatus() == CommissionStatus.PENDING) {
                pending = pending.add(c.getAmountInr());
            } else if (c.getStatus() == CommissionStatus.APPROVED) {
                approved = approved.add(c.getAmountInr());
            } else if (c.getStatus() == CommissionStatus.PAID) {
                paid = paid.add(c.getAmountInr());
            }
        }

        // Find primary promo code
        String promoStr = "NONE";
        String linkStr = baseUrl + "/r/NONE";
        List<PromoCode> allCodes = promoCodeRepository.findAll();
        for (PromoCode pc : allCodes) {
            if (pc.getInfluencer().getId().equals(influencer.getId()) && "ACTIVE".equalsIgnoreCase(pc.getStatus())) {
                promoStr = pc.getCode();
                linkStr = baseUrl + "/r/" + pc.getCode();
                break;
            }
        }

        return InfluencerStatsResponse.builder()
                .influencerId(influencer.getId())
                .name(influencer.getName())
                .email(influencer.getEmail())
                .instagramHandle(influencer.getInstagramHandle())
                .promoCode(promoStr)
                .referralLink(linkStr)
                .totalClicks(totalClicks)
                .pendingCommissionInr(pending)
                .approvedCommissionInr(approved)
                .paidCommissionInr(paid)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Commission> getInfluencerCommissions(UUID userId) {
        Influencer influencer = influencerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Influencer", userId));
        return commissionRepository.findByInfluencerIdOrderByCreatedAtDesc(influencer.getId());
    }

    @Override
    @Transactional
    public Payout processPayout(UUID adminUserId, ProcessPayoutRequest request) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", adminUserId));

        Payout payout = payoutRepository.findById(request.getPayoutId())
                .orElseThrow(() -> new ResourceNotFoundException("Payout", request.getPayoutId()));

        payout.setStatus(PayoutStatus.PAID);
        payout.setTransactionReference(request.getTransactionReference());
        payout.setAdminNotes(request.getAdminNotes());
        payout.setProcessedAt(Instant.now());
        payout.setProcessedBy(admin);

        payout = payoutRepository.save(payout);

        // Mark influencer's approved commissions up to payout amount as PAID
        List<Commission> approvedCommissions = commissionRepository.findByInfluencerIdAndStatus(
                payout.getInfluencer().getId(),
                CommissionStatus.APPROVED
        );

        BigDecimal remaining = payout.getAmountInr();
        for (Commission comm : approvedCommissions) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            comm.setStatus(CommissionStatus.PAID);
            comm.setPaidAt(Instant.now());
            commissionRepository.save(comm);
            remaining = remaining.subtract(comm.getAmountInr());
        }

        log.info("Admin {} processed payout {} for INR {}", admin.getEmail(), payout.getId(), payout.getAmountInr());
        return payout;
    }

    @Override
    @Transactional
    public void recordReferralClick(String promoCode, String rawIp, String userAgent, String utmSource, String utmMedium, String utmCampaign) {
        promoCodeRepository.findByCodeIgnoreCase(promoCode).ifPresent(promo -> {
            String ipHash = hashIp(rawIp);
            ReferralClick click = ReferralClick.builder()
                    .promoCode(promo)
                    .influencer(promo.getInfluencer())
                    .ipHash(ipHash)
                    .userAgent(userAgent)
                    .utmSource(utmSource)
                    .utmMedium(utmMedium)
                    .utmCampaign(utmCampaign)
                    .build();
            referralClickRepository.save(click);
            log.debug("Recorded referral click for promo code {}", promo.getCode());
        });
    }

    @Override
    @Transactional
    public Commission calculateAndRecordCommission(Payment payment, PromoCode promoCode) {
        if (payment == null || promoCode == null) return null;

        Campaign campaign = promoCode.getCampaign();
        BigDecimal basisAmount = (campaign != null && campaign.getCommissionBasis() == CommissionBasis.GROSS)
                ? payment.getAmountInr()
                : payment.getNetInr();

        BigDecimal commissionAmount;
        if (promoCode.getCommissionType() == CommissionType.PERCENTAGE) {
            commissionAmount = basisAmount.multiply(promoCode.getCommissionValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            commissionAmount = promoCode.getCommissionValue();
        }

        int holdDays = (campaign != null) ? campaign.getAttributionWindowDays() : 30;

        Commission commission = Commission.builder()
                .influencer(promoCode.getInfluencer())
                .payment(payment)
                .amountInr(commissionAmount)
                .status(CommissionStatus.PENDING)
                .qualifiesAt(Instant.now().plus(holdDays, ChronoUnit.DAYS))
                .build();

        commission = commissionRepository.save(commission);
        log.info("Recorded pending commission {} (INR {}) for influencer {}",
                commission.getId(), commissionAmount, promoCode.getInfluencer().getId());
        return commission;
    }

    private String hashIp(String rawIp) {
        if (rawIp == null || rawIp.isBlank()) {
            return "unknown";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawIp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "unknown";
        }
    }
}

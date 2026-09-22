package com.autoflow.modules.influencer.service;

import com.autoflow.modules.billing.entity.Payment;
import com.autoflow.modules.influencer.dto.*;
import com.autoflow.modules.influencer.entity.*;

import java.util.List;
import java.util.UUID;

public interface InfluencerService {

    Influencer createInfluencer(CreateInfluencerRequest request);

    Campaign createCampaign(CreateCampaignRequest request);

    PromoCode createPromoCode(CreatePromoCodeRequest request);

    InfluencerStatsResponse getInfluencerStats(UUID userId);

    List<Commission> getInfluencerCommissions(UUID userId);

    Payout processPayout(UUID adminUserId, ProcessPayoutRequest request);

    void recordReferralClick(String promoCode, String rawIp, String userAgent, String utmSource, String utmMedium, String utmCampaign);

    Commission calculateAndRecordCommission(Payment payment, PromoCode promoCode);
}

package com.autoflow.modules.influencer.repository;

import com.autoflow.modules.influencer.entity.ReferralClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ReferralClickRepository extends JpaRepository<ReferralClick, UUID> {

    long countByInfluencerId(UUID influencerId);

    long countByPromoCodeId(UUID promoCodeId);
}

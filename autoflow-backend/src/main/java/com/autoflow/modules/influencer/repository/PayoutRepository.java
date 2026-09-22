package com.autoflow.modules.influencer.repository;

import com.autoflow.modules.influencer.entity.Payout;
import com.autoflow.modules.influencer.entity.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    List<Payout> findByInfluencerIdOrderByRequestedAtDesc(UUID influencerId);

    List<Payout> findByStatus(PayoutStatus status);
}

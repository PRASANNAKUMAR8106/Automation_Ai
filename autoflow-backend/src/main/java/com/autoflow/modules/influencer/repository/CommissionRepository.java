package com.autoflow.modules.influencer.repository;

import com.autoflow.modules.billing.entity.Payment;
import com.autoflow.modules.influencer.entity.Commission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommissionRepository extends JpaRepository<Commission, UUID> {
    List<Commission> findByPayment(Payment payment);
    List<Commission> findByPaymentId(UUID paymentId);
    List<Commission> findByInfluencerIdOrderByCreatedAtDesc(UUID influencerId);
    List<Commission> findByInfluencerIdAndStatus(UUID influencerId, com.autoflow.modules.influencer.entity.CommissionStatus status);
    List<Commission> findByStatusAndQualifiesAtBefore(com.autoflow.modules.influencer.entity.CommissionStatus status, java.time.Instant threshold);
}

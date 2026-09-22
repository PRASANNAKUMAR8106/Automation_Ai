package com.autoflow.modules.influencer.repository;

import com.autoflow.modules.influencer.entity.ReferralAttribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralAttributionRepository extends JpaRepository<ReferralAttribution, UUID> {
    Optional<ReferralAttribution> findByOrganizationId(UUID organizationId);
    boolean existsByOrganizationId(UUID organizationId);
}

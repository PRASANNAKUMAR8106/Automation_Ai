package com.autoflow.modules.influencer.repository;

import com.autoflow.modules.influencer.entity.Influencer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InfluencerRepository extends JpaRepository<Influencer, UUID> {

    Optional<Influencer> findByUserId(UUID userId);

    Optional<Influencer> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}

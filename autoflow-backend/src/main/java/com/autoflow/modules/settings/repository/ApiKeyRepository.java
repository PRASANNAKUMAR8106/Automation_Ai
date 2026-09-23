package com.autoflow.modules.settings.repository;

import com.autoflow.modules.settings.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    Optional<ApiKey> findByIdAndOrganizationId(UUID id, UUID organizationId);
}

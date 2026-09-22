package com.autoflow.modules.media.repository;

import com.autoflow.modules.media.entity.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    List<MediaAsset> findByOrganizationId(UUID organizationId);

    Optional<MediaAsset> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<MediaAsset> findBySha256ChecksumAndOrganizationId(String sha256Checksum, UUID organizationId);
}

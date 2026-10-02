package com.autoflow.modules.crm.repository;

import com.autoflow.modules.crm.entity.CannedResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CannedResponseRepository extends JpaRepository<CannedResponse, UUID> {

    List<CannedResponse> findByOrganizationIdOrderByShortcutAsc(UUID organizationId);

    List<CannedResponse> findByOrganizationIdAndCategoryOrderByShortcutAsc(UUID organizationId, String category);

    Optional<CannedResponse> findByOrganizationIdAndShortcut(UUID organizationId, String shortcut);

    Optional<CannedResponse> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Modifying
    @Query("UPDATE CannedResponse c SET c.usageCount = c.usageCount + 1 WHERE c.id = :id AND c.organizationId = :organizationId")
    void incrementUsageCount(@Param("id") UUID id, @Param("organizationId") UUID organizationId);

    @Query("SELECT c FROM CannedResponse c WHERE c.organizationId = :organizationId AND " +
           "(LOWER(c.shortcut) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<CannedResponse> searchByKeyword(@Param("organizationId") UUID organizationId, @Param("keyword") String keyword);
}

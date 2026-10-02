package com.autoflow.modules.knowledge.repository;

import com.autoflow.modules.knowledge.entity.KnowledgeArticle;
import com.autoflow.modules.knowledge.entity.KnowledgeCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, UUID> {

    Optional<KnowledgeArticle> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<KnowledgeArticle> findByOrganizationIdAndActiveTrueOrderByUsageCountDescCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<KnowledgeArticle> findByOrganizationIdAndCategoryAndActiveTrueOrderByUsageCountDescCreatedAtDesc(
            UUID organizationId,
            KnowledgeCategory category,
            Pageable pageable
    );

    List<KnowledgeArticle> findAllByOrganizationIdAndActiveTrue(UUID organizationId);

    @Query("SELECT a FROM KnowledgeArticle a WHERE a.organizationId = :organizationId AND a.active = true AND " +
           "(LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.content) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY a.usageCount DESC, a.createdAt DESC")
    List<KnowledgeArticle> searchArticles(@Param("organizationId") UUID organizationId, @Param("query") String query);

    @Modifying
    @Query("UPDATE KnowledgeArticle a SET a.usageCount = a.usageCount + 1 WHERE a.id = :id AND a.organizationId = :organizationId")
    int incrementUsageCount(@Param("id") UUID id, @Param("organizationId") UUID organizationId);
}

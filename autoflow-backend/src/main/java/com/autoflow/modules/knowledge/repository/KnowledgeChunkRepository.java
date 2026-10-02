package com.autoflow.modules.knowledge.repository;

import com.autoflow.modules.knowledge.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {

    List<KnowledgeChunk> findByArticleId(UUID articleId);

    List<KnowledgeChunk> findAllByOrganizationId(UUID organizationId);

    @Modifying
    void deleteByArticleId(UUID articleId);
}

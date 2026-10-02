package com.autoflow.modules.knowledge.service;

import com.autoflow.modules.knowledge.dto.KnowledgeDto.*;
import com.autoflow.modules.knowledge.entity.KnowledgeCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface KnowledgeArticleService {

    ArticleResponse createArticle(UUID organizationId, CreateArticleRequest request);

    ArticleResponse getArticle(UUID organizationId, UUID articleId);

    Page<ArticleResponse> listArticles(UUID organizationId, KnowledgeCategory category, String search, Pageable pageable);

    ArticleResponse updateArticle(UUID organizationId, UUID articleId, UpdateArticleRequest request);

    void deleteArticle(UUID organizationId, UUID articleId);

    List<ArticleResponse> findRelevantArticles(UUID organizationId, String query, int limit);

    void recordArticleUsage(UUID organizationId, UUID articleId);
}

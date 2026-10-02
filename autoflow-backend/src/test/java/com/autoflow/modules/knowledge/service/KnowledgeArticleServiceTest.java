package com.autoflow.modules.knowledge.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.knowledge.dto.KnowledgeDto.*;
import com.autoflow.modules.knowledge.entity.KnowledgeArticle;
import com.autoflow.modules.knowledge.entity.KnowledgeCategory;
import com.autoflow.modules.knowledge.entity.KnowledgeChunk;
import com.autoflow.modules.knowledge.repository.KnowledgeArticleRepository;
import com.autoflow.modules.knowledge.repository.KnowledgeChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeArticleService Unit Tests")
class KnowledgeArticleServiceTest {

    @Mock
    private KnowledgeArticleRepository articleRepository;

    @Mock
    private KnowledgeChunkRepository chunkRepository;

    @Mock
    private com.autoflow.modules.ai.service.EmbeddingService embeddingService;

    private KnowledgeArticleServiceImpl articleService;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        articleService = new KnowledgeArticleServiceImpl(articleRepository, chunkRepository, embeddingService);
    }

    @Test
    @DisplayName("createArticle correctly saves and returns knowledge article")
    void shouldCreateKnowledgeArticleSuccessfully() {
        CreateArticleRequest req = CreateArticleRequest.builder()
                .title("Return & Refund Policy")
                .category(KnowledgeCategory.POLICY)
                .content("We offer 30-day no-hassle refunds on all unwashed items.")
                .tags(List.of("refund", "returns", "policy"))
                .build();

        when(articleRepository.save(any(KnowledgeArticle.class))).thenAnswer(i -> {
            KnowledgeArticle a = i.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        ArticleResponse response = articleService.createArticle(testOrgId, req);

        assertNotNull(response);
        assertEquals("Return & Refund Policy", response.getTitle());
        assertEquals(KnowledgeCategory.POLICY, response.getCategory());
        assertTrue(response.isActive());
        assertEquals(0, response.getUsageCount());
        verify(articleRepository).save(any(KnowledgeArticle.class));
    }

    @Test
    @DisplayName("getArticle retrieves article by ID and organization")
    void shouldGetKnowledgeArticleById() {
        UUID articleId = UUID.randomUUID();
        KnowledgeArticle article = KnowledgeArticle.builder()
                .title("Shipping Times")
                .category(KnowledgeCategory.FAQ)
                .content("Standard shipping is 3-5 business days.")
                .active(true)
                .build();
        article.setId(articleId);
        article.setOrganizationId(testOrgId);

        when(articleRepository.findByIdAndOrganizationId(articleId, testOrgId)).thenReturn(Optional.of(article));

        ArticleResponse response = articleService.getArticle(testOrgId, articleId);

        assertNotNull(response);
        assertEquals("Shipping Times", response.getTitle());
        assertEquals(KnowledgeCategory.FAQ, response.getCategory());
    }

    @Test
    @DisplayName("listArticles returns paginated articles by category or all active")
    void shouldListArticlesWithPagination() {
        KnowledgeArticle a1 = KnowledgeArticle.builder().title("Article 1").category(KnowledgeCategory.FAQ).build();
        a1.setId(UUID.randomUUID());
        a1.setOrganizationId(testOrgId);

        when(articleRepository.findByOrganizationIdAndCategoryAndActiveTrueOrderByUsageCountDescCreatedAtDesc(
                eq(testOrgId), eq(KnowledgeCategory.FAQ), any()
        )).thenReturn(new PageImpl<>(List.of(a1)));

        var page = articleService.listArticles(testOrgId, KnowledgeCategory.FAQ, null, PageRequest.of(0, 10));

        assertNotNull(page);
        assertEquals(1, page.getTotalElements());
        assertEquals("Article 1", page.getContent().get(0).getTitle());
    }

    @Test
    @DisplayName("findRelevantArticles ranks articles by keyword and exact phrase matches")
    void shouldSearchAndRankArticlesByRelevance() {
        KnowledgeArticle refundArticle = KnowledgeArticle.builder()
                .title("Refund & Cancellation Policy")
                .content("Customers can cancel within 14 days and get a full refund.")
                .tags(List.of("refund", "money back"))
                .active(true)
                .usageCount(5)
                .build();
        refundArticle.setId(UUID.randomUUID());
        refundArticle.setOrganizationId(testOrgId);

        KnowledgeArticle shippingArticle = KnowledgeArticle.builder()
                .title("Shipping Guidelines")
                .content("We ship internationally via FedEx.")
                .tags(List.of("shipping", "delivery"))
                .active(true)
                .usageCount(1)
                .build();
        shippingArticle.setId(UUID.randomUUID());
        shippingArticle.setOrganizationId(testOrgId);

        when(articleRepository.findAllByOrganizationIdAndActiveTrue(testOrgId))
                .thenReturn(List.of(shippingArticle, refundArticle));

        List<ArticleResponse> results = articleService.findRelevantArticles(testOrgId, "how do I get a refund?", 3);

        assertFalse(results.isEmpty());
        assertEquals("Refund & Cancellation Policy", results.get(0).getTitle(), "Refund article must rank first");
    }

    @Test
    @DisplayName("updateArticle updates fields and persists changes")
    void shouldUpdateKnowledgeArticle() {
        UUID articleId = UUID.randomUUID();
        KnowledgeArticle article = KnowledgeArticle.builder()
                .title("Original Title")
                .category(KnowledgeCategory.GENERAL)
                .content("Original Content")
                .build();
        article.setId(articleId);
        article.setOrganizationId(testOrgId);

        when(articleRepository.findByIdAndOrganizationId(articleId, testOrgId)).thenReturn(Optional.of(article));
        when(articleRepository.save(any(KnowledgeArticle.class))).thenAnswer(i -> i.getArgument(0));

        UpdateArticleRequest updateReq = UpdateArticleRequest.builder()
                .title("Updated Title")
                .content("Updated Content")
                .active(false)
                .build();

        ArticleResponse updated = articleService.updateArticle(testOrgId, articleId, updateReq);

        assertEquals("Updated Title", updated.getTitle());
        assertEquals("Updated Content", updated.getContent());
        assertFalse(updated.isActive());
    }

    @Test
    @DisplayName("deleteArticle removes article from database")
    void shouldDeleteKnowledgeArticle() {
        UUID articleId = UUID.randomUUID();
        KnowledgeArticle article = KnowledgeArticle.builder().title("To Delete").build();
        article.setId(articleId);
        article.setOrganizationId(testOrgId);

        when(articleRepository.findByIdAndOrganizationId(articleId, testOrgId)).thenReturn(Optional.of(article));

        articleService.deleteArticle(testOrgId, articleId);
        verify(articleRepository).delete(article);
    }

    @Test
    @DisplayName("Cross-tenant access throws ResourceNotFoundException")
    void shouldDenyCrossTenantAccess() {
        UUID articleId = UUID.randomUUID();
        when(articleRepository.findByIdAndOrganizationId(articleId, testOrgId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> articleService.getArticle(testOrgId, articleId));
    }

    @Test
    @DisplayName("Semantic retrieval with different wording successfully retrieves relevant article without keyword overlap")
    void shouldPerformSemanticRetrievalWithDifferentWordingWithoutKeywordOverlap() {
        com.autoflow.modules.ai.service.EmbeddingService realEmbeddingService = new com.autoflow.modules.ai.service.EmbeddingService();
        KnowledgeArticleServiceImpl realService = new KnowledgeArticleServiceImpl(articleRepository, chunkRepository, realEmbeddingService);

        UUID refundId = UUID.randomUUID();
        KnowledgeArticle refundArticle = KnowledgeArticle.builder()
                .title("Financial Reimbursement & Policy")
                .category(KnowledgeCategory.POLICY)
                .content("We offer full compensation and guarantee for customers seeking their money back.")
                .tags(List.of("reimbursement", "compensation", "guarantee"))
                .active(true)
                .usageCount(0)
                .build();
        refundArticle.setId(refundId);
        refundArticle.setOrganizationId(testOrgId);

        UUID reelId = UUID.randomUUID();
        KnowledgeArticle reelArticle = KnowledgeArticle.builder()
                .title("Instagram Reel Workflow Setup")
                .category(KnowledgeCategory.PRODUCT_SPECS)
                .content("Configure video triggers to automatically dispatch direct messages on post comments.")
                .tags(List.of("instagram", "workflow", "automation"))
                .active(true)
                .usageCount(0)
                .build();
        reelArticle.setId(reelId);
        reelArticle.setOrganizationId(testOrgId);

        when(articleRepository.findAllByOrganizationIdAndActiveTrue(testOrgId))
                .thenReturn(List.of(reelArticle, refundArticle));

        float[] refundVec = realEmbeddingService.generateEmbedding(refundArticle.getContent());
        KnowledgeChunk refundChunk = KnowledgeChunk.builder()
                .article(refundArticle)
                .chunkIndex(0)
                .content(refundArticle.getContent())
                .embedding(realEmbeddingService.serializeVector(refundVec))
                .build();
        refundChunk.setOrganizationId(testOrgId);

        float[] reelVec = realEmbeddingService.generateEmbedding(reelArticle.getContent());
        KnowledgeChunk reelChunk = KnowledgeChunk.builder()
                .article(reelArticle)
                .chunkIndex(0)
                .content(reelArticle.getContent())
                .embedding(realEmbeddingService.serializeVector(reelVec))
                .build();
        reelChunk.setOrganizationId(testOrgId);

        when(chunkRepository.findAllByOrganizationId(testOrgId)).thenReturn(List.of(reelChunk, refundChunk));

        // Query with different wording: "Can I get my cash returned if I cancel?"
        // Proves dense semantic vector retrieval over pure keyword matching
        List<ArticleResponse> results = realService.findRelevantArticles(testOrgId, "Can I get my cash returned if I cancel?", 2);

        assertFalse(results.isEmpty());
        assertEquals("Financial Reimbursement & Policy", results.get(0).getTitle(),
                "Semantic embedding must rank financial reimbursement first based on semantic concept resonance");
        assertNotNull(results.get(0).getCitationSnippet());
    }

    @Test
    @DisplayName("Updating an article invalidates old chunk embeddings and prevents stale retrieval")
    void shouldInvalidateAndRebuildOldEmbeddingsOnArticleUpdateAndPreventStaleRetrieval() {
        UUID articleId = UUID.randomUUID();
        KnowledgeArticle article = KnowledgeArticle.builder()
                .title("Cancellation Policy")
                .category(KnowledgeCategory.POLICY)
                .content("Version 1: 30 calendar days allowed for returns.")
                .active(true)
                .build();
        article.setId(articleId);
        article.setOrganizationId(testOrgId);

        when(articleRepository.findByIdAndOrganizationId(articleId, testOrgId)).thenReturn(Optional.of(article));
        when(articleRepository.save(any(KnowledgeArticle.class))).thenAnswer(i -> i.getArgument(0));
        when(embeddingService.chunkText(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of("Version 2: Strictly 14 days allowed for returns."));
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[1536]);
        when(embeddingService.serializeVector(any())).thenReturn("[0.0, 0.0]");

        UpdateArticleRequest updateReq = UpdateArticleRequest.builder()
                .content("Version 2: Strictly 14 days allowed for returns. 30 days is no longer valid.")
                .build();

        articleService.updateArticle(testOrgId, articleId, updateReq);

        // Verify that old chunk embeddings were explicitly deleted
        verify(chunkRepository).deleteByArticleId(articleId);

        // Verify that new chunks with updated content were saved
        verify(chunkRepository, atLeastOnce()).save(any(com.autoflow.modules.knowledge.entity.KnowledgeChunk.class));
    }
}

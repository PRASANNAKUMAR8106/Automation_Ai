package com.autoflow.modules.knowledge.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.knowledge.dto.KnowledgeDto.*;
import com.autoflow.modules.knowledge.entity.KnowledgeArticle;
import com.autoflow.modules.knowledge.entity.KnowledgeCategory;
import com.autoflow.modules.knowledge.repository.KnowledgeArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import com.autoflow.modules.ai.service.EmbeddingService;
import com.autoflow.modules.knowledge.entity.KnowledgeChunk;
import com.autoflow.modules.knowledge.repository.KnowledgeChunkRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeArticleServiceImpl implements KnowledgeArticleService {

    private final KnowledgeArticleRepository articleRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "about", "an", "and", "are", "as", "at", "be", "by", "for",
            "from", "how", "i", "in", "is", "it", "of", "on", "or", "that",
            "the", "this", "to", "was", "what", "when", "where", "who", "will", "with"
    );

    @Override
    @Transactional
    public ArticleResponse createArticle(UUID organizationId, CreateArticleRequest request) {
        KnowledgeArticle article = KnowledgeArticle.builder()
                .title(request.getTitle())
                .category(request.getCategory() != null ? request.getCategory() : KnowledgeCategory.GENERAL)
                .content(request.getContent())
                .tags(request.getTags() != null ? new ArrayList<>(request.getTags()) : new ArrayList<>())
                .active(true)
                .usageCount(0)
                .build();
        article.setOrganizationId(organizationId);

        KnowledgeArticle saved = articleRepository.save(article);
        syncArticleChunks(saved);
        log.info("Created knowledge article [{}] for org [{}] with category [{}]",
                saved.getId(), organizationId, saved.getCategory());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleResponse getArticle(UUID organizationId, UUID articleId) {
        KnowledgeArticle article = articleRepository.findByIdAndOrganizationId(articleId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeArticle", articleId));
        return toResponse(article);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ArticleResponse> listArticles(UUID organizationId, KnowledgeCategory category, String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            List<KnowledgeArticle> matches = articleRepository.searchArticles(organizationId, search.trim());
            if (category != null) {
                matches = matches.stream()
                        .filter(a -> a.getCategory() == category)
                        .collect(Collectors.toList());
            }

            int start = (int) pageable.getOffset();
            if (start >= matches.size()) {
                return new PageImpl<>(Collections.emptyList(), pageable, matches.size());
            }
            int end = Math.min(start + pageable.getPageSize(), matches.size());
            List<ArticleResponse> pageContent = matches.subList(start, end).stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
            return new PageImpl<>(pageContent, pageable, matches.size());
        }

        Page<KnowledgeArticle> page;
        if (category != null) {
            page = articleRepository.findByOrganizationIdAndCategoryAndActiveTrueOrderByUsageCountDescCreatedAtDesc(
                    organizationId, category, pageable
            );
        } else {
            page = articleRepository.findByOrganizationIdAndActiveTrueOrderByUsageCountDescCreatedAtDesc(
                    organizationId, pageable
            );
        }
        return page.map(this::toResponse);
    }

    @Override
    @Transactional
    public ArticleResponse updateArticle(UUID organizationId, UUID articleId, UpdateArticleRequest request) {
        KnowledgeArticle article = articleRepository.findByIdAndOrganizationId(articleId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeArticle", articleId));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            article.setTitle(request.getTitle().trim());
        }
        if (request.getCategory() != null) {
            article.setCategory(request.getCategory());
        }
        if (request.getContent() != null && !request.getContent().isBlank()) {
            article.setContent(request.getContent().trim());
        }
        if (request.getTags() != null) {
            article.setTags(new ArrayList<>(request.getTags()));
        }
        if (request.getActive() != null) {
            article.setActive(request.getActive());
        }

        KnowledgeArticle updated = articleRepository.save(article);
        syncArticleChunks(updated);
        log.info("Updated knowledge article [{}] for org [{}]", articleId, organizationId);
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteArticle(UUID organizationId, UUID articleId) {
        KnowledgeArticle article = articleRepository.findByIdAndOrganizationId(articleId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("KnowledgeArticle", articleId));
        if (chunkRepository != null) {
            chunkRepository.deleteByArticleId(articleId);
        }
        articleRepository.delete(article);
        log.info("Deleted knowledge article [{}] for org [{}]", articleId, organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArticleResponse> findRelevantArticles(UUID organizationId, String query, int limit) {
        int maxResults = (limit <= 0) ? 3 : Math.min(limit, 10);
        List<KnowledgeArticle> allArticles = articleRepository.findAllByOrganizationIdAndActiveTrue(organizationId);
        if (allArticles.isEmpty()) {
            return Collections.emptyList();
        }

        if (query == null || query.isBlank()) {
            return allArticles.stream()
                    .sorted(Comparator.comparingInt(KnowledgeArticle::getUsageCount).reversed())
                    .limit(maxResults)
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        }

        String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();
        List<String> keywords = Arrays.stream(normalizedQuery.split("\\W+"))
                .map(String::trim)
                .filter(w -> w.length() > 1 && !STOP_WORDS.contains(w))
                .collect(Collectors.toList());

        // Dense Vector Query Embedding
        float[] queryVector = (embeddingService != null) ? embeddingService.generateEmbedding(normalizedQuery) : null;

        // Chunk-Level Vector Search
        List<KnowledgeChunk> chunks = (chunkRepository != null) ? chunkRepository.findAllByOrganizationId(organizationId) : Collections.emptyList();
        Map<UUID, String> bestChunkSnippet = new HashMap<>();
        Map<UUID, Double> chunkVectorScores = new HashMap<>();

        if (!chunks.isEmpty() && queryVector != null && embeddingService != null) {
            for (KnowledgeChunk chunk : chunks) {
                if (chunk.getArticle() == null || !chunk.getArticle().isActive()) continue;
                float[] chunkVec = embeddingService.deserializeVector(chunk.getEmbedding());
                double similarity = embeddingService.cosineSimilarity(queryVector, chunkVec);
                UUID articleId = chunk.getArticle().getId();
                if (similarity > chunkVectorScores.getOrDefault(articleId, -1.0)) {
                    chunkVectorScores.put(articleId, similarity);
                    bestChunkSnippet.put(articleId, chunk.getContent());
                }
            }
        }

        record ScoredArticle(KnowledgeArticle article, double score, String snippet) {}

        List<ScoredArticle> scored = new ArrayList<>();
        for (KnowledgeArticle article : allArticles) {
            double lexicalScore = computeRelevanceScore(article, normalizedQuery, keywords);
            double vectorSim = chunkVectorScores.getOrDefault(article.getId(), 0.0);

            // Hybrid score fusion: 60% dense vector + 40% lexical
            double hybridScore = (vectorSim > 0) ? (0.6 * vectorSim * 20.0 + 0.4 * lexicalScore) : lexicalScore;
            if (hybridScore > 0) {
                String snippet = bestChunkSnippet.getOrDefault(article.getId(),
                        article.getContent().length() > 200 ? article.getContent().substring(0, 200) + "..." : article.getContent());
                scored.add(new ScoredArticle(article, hybridScore, snippet));
            }
        }

        if (scored.isEmpty()) {
            return allArticles.stream()
                    .sorted(Comparator.comparingInt(KnowledgeArticle::getUsageCount).reversed())
                    .limit(Math.min(2, maxResults))
                    .map(this::toResponse)
                    .collect(Collectors.toList());
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredArticle::score).reversed())
                .limit(maxResults)
                .map(s -> {
                    ArticleResponse resp = toResponse(s.article());
                    resp.setCitationSnippet(s.snippet());
                    return resp;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void recordArticleUsage(UUID organizationId, UUID articleId) {
        articleRepository.incrementUsageCount(articleId, organizationId);
    }

    private void syncArticleChunks(KnowledgeArticle article) {
        if (chunkRepository == null || embeddingService == null) return;
        try {
            chunkRepository.deleteByArticleId(article.getId());
            List<String> chunks = embeddingService.chunkText(article.getContent(), 600, 100);
            int index = 0;
            for (String chunkText : chunks) {
                float[] vector = embeddingService.generateEmbedding(chunkText);
                String serialized = embeddingService.serializeVector(vector);
                KnowledgeChunk chunk = KnowledgeChunk.builder()
                        .article(article)
                        .chunkIndex(index++)
                        .content(chunkText)
                        .embedding(serialized)
                        .tokenCount(chunkText.split("\\s+").length)
                        .build();
                chunk.setOrganizationId(article.getOrganizationId());
                chunkRepository.save(chunk);
            }
        } catch (Exception e) {
            log.warn("Failed to generate vector chunks for article {}: {}", article.getId(), e.getMessage());
        }
    }

    private double computeRelevanceScore(KnowledgeArticle article, String normalizedQuery, List<String> keywords) {
        double score = 0.0;
        String titleLower = article.getTitle().toLowerCase(Locale.ROOT);
        String contentLower = article.getContent().toLowerCase(Locale.ROOT);

        // Exact phrase matches
        if (titleLower.contains(normalizedQuery)) {
            score += 15.0;
        }
        if (contentLower.contains(normalizedQuery)) {
            score += 8.0;
        }

        // Keyword matches
        for (String keyword : keywords) {
            if (titleLower.contains(keyword)) {
                score += 4.0;
            }
            if (article.getTags() != null) {
                for (String tag : article.getTags()) {
                    if (tag.toLowerCase(Locale.ROOT).contains(keyword)) {
                        score += 5.0;
                        break;
                    }
                }
            }
            if (contentLower.contains(keyword)) {
                score += 1.5;
            }
        }

        // Usage count popularity boost (log scale, capped at 3)
        if (article.getUsageCount() > 0) {
            score += Math.min(3.0, Math.log10(article.getUsageCount() + 1));
        }

        return score;
    }

    private ArticleResponse toResponse(KnowledgeArticle a) {
        return ArticleResponse.builder()
                .id(a.getId())
                .organizationId(a.getOrganizationId())
                .title(a.getTitle())
                .category(a.getCategory())
                .content(a.getContent())
                .tags(a.getTags() != null ? new ArrayList<>(a.getTags()) : Collections.emptyList())
                .active(a.isActive())
                .usageCount(a.getUsageCount())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}

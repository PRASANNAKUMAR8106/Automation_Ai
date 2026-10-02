package com.autoflow.modules.knowledge.controller;

import com.autoflow.security.TenantContext;
import com.autoflow.modules.knowledge.dto.KnowledgeDto.*;
import com.autoflow.modules.knowledge.entity.KnowledgeCategory;
import com.autoflow.modules.knowledge.service.KnowledgeArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge/articles")
@RequiredArgsConstructor
public class KnowledgeArticleController {

    private final KnowledgeArticleService articleService;

    @PostMapping
    public ResponseEntity<ArticleResponse> createArticle(@Valid @RequestBody CreateArticleRequest request) {
        UUID orgId = TenantContext.getRequiredTenantId();
        ArticleResponse created = articleService.createArticle(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<Page<ArticleResponse>> listArticles(
            @RequestParam(required = false) KnowledgeCategory category,
            @RequestParam(required = false) String search,
            Pageable pageable
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        return ResponseEntity.ok(articleService.listArticles(orgId, category, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArticleResponse> getArticle(@PathVariable UUID id) {
        UUID orgId = TenantContext.getRequiredTenantId();
        return ResponseEntity.ok(articleService.getArticle(orgId, id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ArticleResponse> updateArticle(
            @PathVariable UUID id,
            @RequestBody UpdateArticleRequest request
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        return ResponseEntity.ok(articleService.updateArticle(orgId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteArticle(@PathVariable UUID id) {
        UUID orgId = TenantContext.getRequiredTenantId();
        articleService.deleteArticle(orgId, id);
        return ResponseEntity.noContent().build();
    }
}

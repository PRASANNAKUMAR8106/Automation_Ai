package com.autoflow.modules.knowledge.controller;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.knowledge.dto.KnowledgeDto.*;
import com.autoflow.modules.knowledge.entity.KnowledgeCategory;
import com.autoflow.modules.knowledge.service.KnowledgeArticleService;
import com.autoflow.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = KnowledgeArticleController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("KnowledgeArticleController Web MVC Tests")
class KnowledgeArticleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KnowledgeArticleService articleService;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testOrgId);
    }

    @Test
    @DisplayName("POST /api/v1/knowledge/articles creates and returns 201 Created")
    void shouldCreateKnowledgeArticle() throws Exception {
        UUID articleId = UUID.randomUUID();
        CreateArticleRequest req = CreateArticleRequest.builder()
                .title("FAQ: Pricing")
                .category(KnowledgeCategory.FAQ)
                .content("Starter is $29/mo, Pro is $79/mo.")
                .tags(List.of("pricing", "cost"))
                .build();

        ArticleResponse res = ArticleResponse.builder()
                .id(articleId)
                .organizationId(testOrgId)
                .title("FAQ: Pricing")
                .category(KnowledgeCategory.FAQ)
                .content("Starter is $29/mo, Pro is $79/mo.")
                .active(true)
                .createdAt(Instant.now())
                .build();

        when(articleService.createArticle(eq(testOrgId), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/knowledge/articles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(articleId.toString()))
                .andExpect(jsonPath("$.title").value("FAQ: Pricing"))
                .andExpect(jsonPath("$.category").value("FAQ"));
    }

    @Test
    @DisplayName("GET /api/v1/knowledge/articles returns 200 OK and paginated articles")
    void shouldListKnowledgeArticles() throws Exception {
        ArticleResponse res = ArticleResponse.builder()
                .id(UUID.randomUUID())
                .organizationId(testOrgId)
                .title("Return Policy")
                .category(KnowledgeCategory.POLICY)
                .content("30 day returns")
                .active(true)
                .build();

        when(articleService.listArticles(eq(testOrgId), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(res)));

        mockMvc.perform(get("/api/v1/knowledge/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Return Policy"));
    }

    @Test
    @DisplayName("GET /api/v1/knowledge/articles/{id} returns article details")
    void shouldGetKnowledgeArticleById() throws Exception {
        UUID articleId = UUID.randomUUID();
        ArticleResponse res = ArticleResponse.builder()
                .id(articleId)
                .organizationId(testOrgId)
                .title("Troubleshooting Bot")
                .category(KnowledgeCategory.TROUBLESHOOTING)
                .content("Check OAuth tokens.")
                .active(true)
                .build();

        when(articleService.getArticle(eq(testOrgId), eq(articleId))).thenReturn(res);

        mockMvc.perform(get("/api/v1/knowledge/articles/{id}", articleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(articleId.toString()))
                .andExpect(jsonPath("$.title").value("Troubleshooting Bot"));
    }

    @Test
    @DisplayName("PUT /api/v1/knowledge/articles/{id} updates article")
    void shouldUpdateKnowledgeArticle() throws Exception {
        UUID articleId = UUID.randomUUID();
        UpdateArticleRequest updateReq = UpdateArticleRequest.builder()
                .title("New Title")
                .build();

        ArticleResponse res = ArticleResponse.builder()
                .id(articleId)
                .organizationId(testOrgId)
                .title("New Title")
                .category(KnowledgeCategory.GENERAL)
                .active(true)
                .build();

        when(articleService.updateArticle(eq(testOrgId), eq(articleId), any())).thenReturn(res);

        mockMvc.perform(put("/api/v1/knowledge/articles/{id}", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New Title"));
    }

    @Test
    @DisplayName("DELETE /api/v1/knowledge/articles/{id} returns 204 No Content")
    void shouldDeleteKnowledgeArticle() throws Exception {
        UUID articleId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/knowledge/articles/{id}", articleId))
                .andExpect(status().isNoContent());

        verify(articleService).deleteArticle(testOrgId, articleId);
    }
}

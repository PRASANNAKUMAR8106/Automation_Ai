package com.autoflow.modules.influencer;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.influencer.controller.ReferralRedirectController;
import com.autoflow.modules.influencer.service.InfluencerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ReferralRedirectController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ReferralRedirectController Web MVC Tests")
class ReferralRedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InfluencerService influencerService;

    @Test
    @DisplayName("GET /r/{code} records click and returns HTTP 302 redirect")
    void testRedirectAndRecordClick() throws Exception {
        mockMvc.perform(get("/r/ALEX20")
                        .header("User-Agent", "Mozilla/5.0")
                        .header("X-Forwarded-For", "203.0.113.195, 70.41.3.18"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/register?ref=ALEX20"));

        verify(influencerService).recordReferralClick(
                eq("ALEX20"),
                eq("203.0.113.195"),
                eq("Mozilla/5.0"),
                eq(null),
                eq(null),
                eq(null)
        );
    }

    @Test
    @DisplayName("GET /r/{code} captures UTM parameters and redirects")
    void testRedirectWithUtmParameters() throws Exception {
        mockMvc.perform(get("/r/SUMMER50")
                        .param("utm_source", "youtube")
                        .param("utm_medium", "video")
                        .param("utm_campaign", "summer_launch")
                        .header("User-Agent", "TestAgent")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.4");
                            return request;
                        }))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "http://localhost:3000/register?ref=SUMMER50"));

        verify(influencerService).recordReferralClick(
                eq("SUMMER50"),
                eq("198.51.100.4"),
                eq("TestAgent"),
                eq("youtube"),
                eq("video"),
                eq("summer_launch")
        );
    }
}

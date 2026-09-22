package com.autoflow.modules.auth;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.controller.AuthController;
import com.autoflow.modules.auth.dto.AuthResponse;
import com.autoflow.modules.auth.dto.LoginRequest;
import com.autoflow.modules.auth.dto.RegisterRequest;
import com.autoflow.modules.auth.dto.UserProfileResponse;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.auth.security.JwtProvider;
import com.autoflow.modules.auth.service.AuthService;
import com.autoflow.modules.tenant.entity.MembershipRole;
import com.autoflow.modules.user.entity.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController Web MVC Tests")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private JwtProvider jwtProvider;

    @Test
    @DisplayName("POST /api/v1/auth/register should validate body and return 201 Created")
    void testRegisterEndpoint() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("founder@saas.com")
                .password("SecurePass2026")
                .firstName("Kavita")
                .organizationName("Kavita AI")
                .build();

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("mock.jwt.token")
                .refreshToken("mock.refresh.token")
                .tokenType("Bearer")
                .expiresInSeconds(900)
                .user(UserProfileResponse.builder()
                        .id(UUID.randomUUID())
                        .email("founder@saas.com")
                        .role(Role.CUSTOMER)
                        .activeMembershipRole(MembershipRole.OWNER)
                        .build())
                .build();

        when(authService.register(any(RegisterRequest.class), any(), any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("mock.jwt.token"))
                .andExpect(jsonPath("$.data.user.email").value("founder@saas.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should fail validation if email is invalid or password too short")
    void testRegisterValidationFailure() throws Exception {
        RegisterRequest invalidRequest = RegisterRequest.builder()
                .email("not-an-email")
                .password("short")
                .firstName("")
                .organizationName("")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should return 200 OK on valid credentials")
    void testLoginEndpoint() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("founder@saas.com")
                .password("SecurePass2026")
                .build();

        AuthResponse authResponse = AuthResponse.builder()
                .accessToken("login.jwt.token")
                .refreshToken("login.refresh.token")
                .tokenType("Bearer")
                .expiresInSeconds(900)
                .user(UserProfileResponse.builder()
                        .email("founder@saas.com")
                        .build())
                .build();

        when(authService.login(any(LoginRequest.class), any(), any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("login.jwt.token"));
    }
}

package com.autoflow.modules.auth.security;

import com.autoflow.modules.tenant.entity.MembershipRole;
import com.autoflow.modules.user.entity.Role;
import com.autoflow.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Filter that authenticates requests bearing valid JWT tokens and binds identity and tenant context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (jwtProvider.validateToken(token)) {
                UUID userId = jwtProvider.extractUserId(token);
                String email = jwtProvider.extractEmail(token);
                Role role = jwtProvider.extractUserRole(token);
                UUID orgId = jwtProvider.extractOrganizationId(token);
                MembershipRole membershipRole = jwtProvider.extractMembershipRole(token);

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
                if (membershipRole != null) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + membershipRole.name()));
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(email, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                if (orgId != null) {
                    TenantContext.setTenantId(orgId);
                }
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Keep TenantContext clean across thread reuse
        }
    }
}

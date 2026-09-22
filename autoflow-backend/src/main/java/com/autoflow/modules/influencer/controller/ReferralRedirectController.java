package com.autoflow.modules.influencer.controller;

import com.autoflow.modules.influencer.service.InfluencerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/r")
@RequiredArgsConstructor
@Tag(name = "Referral Redirect", description = "Public shortlink attribution and redirect tracker")
public class ReferralRedirectController {

    private final InfluencerService influencerService;

    @Value("${autoflow.frontend.landing-url:http://localhost:3000/register}")
    private String defaultLandingUrl;

    @GetMapping("/{code}")
    @Operation(summary = "Track Referral Link & Redirect", description = "Records click attribution with privacy-safe IP hashing and redirects to registration")
    public ResponseEntity<Void> trackAndRedirect(
            @PathVariable String code,
            @RequestParam(required = false) String utm_source,
            @RequestParam(required = false) String utm_medium,
            @RequestParam(required = false) String utm_campaign,
            HttpServletRequest request
    ) {
        String clientIp = resolveClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        influencerService.recordReferralClick(code, clientIp, userAgent, utm_source, utm_medium, utm_campaign);

        String targetUrl = defaultLandingUrl + "?ref=" + code;
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(targetUrl))
                .build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}

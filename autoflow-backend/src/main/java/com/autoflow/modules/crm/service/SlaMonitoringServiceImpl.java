package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.ConversationIntelligenceDto.*;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.ConversationPriority;
import com.autoflow.modules.crm.entity.ConversationSlaPolicy;
import com.autoflow.modules.crm.entity.RoutingPolicy;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.repository.ConversationSlaPolicyRepository;
import com.autoflow.modules.crm.repository.CsatSurveyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlaMonitoringServiceImpl implements SlaMonitoringService {

    private final ConversationSlaPolicyRepository policyRepository;
    private final ConversationRepository conversationRepository;
    private final CsatSurveyRepository csatSurveyRepository;

    @Override
    @Transactional
    public SlaPolicyResponse createPolicy(UUID organizationId, SlaPolicyRequest request) {
        ConversationSlaPolicy policy = ConversationSlaPolicy.builder()
                .name(request.getName().trim())
                .channel(request.getChannel())
                .priority(request.getPriority() != null ? request.getPriority() : ConversationPriority.NORMAL)
                .firstResponseTimeSeconds(request.getFirstResponseTimeSeconds())
                .resolutionTimeSeconds(request.getResolutionTimeSeconds())
                .routingPolicy(request.getRoutingPolicy() != null ? request.getRoutingPolicy() : RoutingPolicy.LEAST_BUSY)
                .active(request.isActive())
                .build();
        policy.setOrganizationId(organizationId);

        ConversationSlaPolicy saved = policyRepository.save(policy);
        log.info("Created SLA policy [{}] for org [{}]", saved.getName(), organizationId);
        return toPolicyResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlaPolicyResponse> getPolicies(UUID organizationId) {
        return policyRepository.findAllByOrganizationIdAndActiveTrue(organizationId).stream()
                .map(this::toPolicyResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void applySlaPolicy(Conversation conversation) {
        UUID orgId = conversation.getOrganizationId();
        ConversationPriority priority = conversation.getPriority() != null ? conversation.getPriority() : ConversationPriority.NORMAL;

        Optional<ConversationSlaPolicy> matchedPolicy = policyRepository
                .findFirstByOrganizationIdAndChannelAndPriorityAndActiveTrue(orgId, conversation.getChannel(), priority);

        if (matchedPolicy.isEmpty()) {
            matchedPolicy = policyRepository.findFirstByOrganizationIdAndPriorityAndActiveTrue(orgId, priority);
        }

        Instant now = Instant.now();
        int frtSeconds;
        int resSeconds;

        if (matchedPolicy.isPresent()) {
            ConversationSlaPolicy policy = matchedPolicy.get();
            conversation.setSlaPolicy(policy);
            frtSeconds = policy.getFirstResponseTimeSeconds();
            resSeconds = policy.getResolutionTimeSeconds();
        } else {
            // Sensible defaults based on priority tier
            switch (priority) {
                case URGENT -> {
                    frtSeconds = 300;     // 5 minutes
                    resSeconds = 3600;    // 1 hour
                }
                case HIGH -> {
                    frtSeconds = 900;     // 15 minutes
                    resSeconds = 14400;   // 4 hours
                }
                case LOW -> {
                    frtSeconds = 3600;    // 1 hour
                    resSeconds = 172800;  // 48 hours
                }
                default -> { // NORMAL
                    frtSeconds = 1800;    // 30 minutes
                    resSeconds = 86400;   // 24 hours
                }
            }
        }

        if (conversation.getSlaFirstResponseDueAt() == null) {
            conversation.setSlaFirstResponseDueAt(now.plusSeconds(frtSeconds));
        }
        if (conversation.getSlaResolutionDueAt() == null) {
            conversation.setSlaResolutionDueAt(now.plusSeconds(resSeconds));
        }
    }

    @Override
    @Transactional
    public void recordFirstAgentReply(Conversation conversation) {
        if (conversation.getFirstAgentReplyAt() == null) {
            Instant now = Instant.now();
            conversation.setFirstAgentReplyAt(now);
            if (conversation.getSlaFirstResponseDueAt() != null && now.isAfter(conversation.getSlaFirstResponseDueAt())) {
                conversation.setSlaFirstResponseBreached(true);
                log.warn("SLA First Response breached for conversation [{}] in org [{}]",
                        conversation.getId(), conversation.getOrganizationId());
            }
        }
    }

    @Override
    @Transactional
    public void recordResolution(Conversation conversation) {
        Instant now = Instant.now();
        conversation.setResolvedAt(now);
        if (conversation.getSlaResolutionDueAt() != null && now.isAfter(conversation.getSlaResolutionDueAt())) {
            conversation.setSlaResolutionBreached(true);
            log.warn("SLA Resolution breached for conversation [{}] in org [{}]",
                    conversation.getId(), conversation.getOrganizationId());
        }
    }

    @Override
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void scanAndProcessBreaches() {
        Instant now = Instant.now();
        List<Conversation> frtBreaches = conversationRepository.findPendingFirstResponseBreaches(now);
        for (Conversation c : frtBreaches) {
            c.setSlaFirstResponseBreached(true);
            conversationRepository.save(c);
            log.warn("Auto-flagged SLA First Response Breach for conversation [{}]", c.getId());
        }

        List<Conversation> resBreaches = conversationRepository.findPendingResolutionBreaches(now);
        for (Conversation c : resBreaches) {
            c.setSlaResolutionBreached(true);
            conversationRepository.save(c);
            log.warn("Auto-flagged SLA Resolution Breach for conversation [{}]", c.getId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PerformanceAnalyticsResponse getPerformanceAnalytics(UUID organizationId) {
        List<Conversation> all = conversationRepository.findAllByOrganizationId(organizationId);

        long total = all.size();
        long resolved = all.stream().filter(Conversation::isResolved).count();
        long active = total - resolved;

        // Calculate Average First Response Time
        List<Long> frtMinutes = all.stream()
                .filter(c -> c.getFirstAgentReplyAt() != null && c.getCreatedAt() != null)
                .map(c -> Duration.between(c.getCreatedAt(), c.getFirstAgentReplyAt()).toMinutes())
                .toList();

        double avgFrt = frtMinutes.isEmpty() ? 0.0 : frtMinutes.stream().mapToLong(Long::longValue).average().orElse(0.0);

        // Calculate Average Resolution Time
        List<Long> resMinutes = all.stream()
                .filter(c -> c.getResolvedAt() != null && c.getCreatedAt() != null)
                .map(c -> Duration.between(c.getCreatedAt(), c.getResolvedAt()).toMinutes())
                .toList();

        double avgRes = resMinutes.isEmpty() ? 0.0 : resMinutes.stream().mapToLong(Long::longValue).average().orElse(0.0);

        // Compliance rates
        long frtEligible = all.stream().filter(c -> c.getFirstAgentReplyAt() != null || c.isSlaFirstResponseBreached()).count();
        long frtCompliant = all.stream().filter(c -> c.getFirstAgentReplyAt() != null && !c.isSlaFirstResponseBreached()).count();
        double frtRate = frtEligible == 0 ? 100.0 : ((double) frtCompliant / frtEligible) * 100.0;

        long resEligible = all.stream().filter(c -> c.isResolved() || c.isSlaResolutionBreached()).count();
        long resCompliant = all.stream().filter(c -> c.isResolved() && !c.isSlaResolutionBreached()).count();
        double resRate = resEligible == 0 ? 100.0 : ((double) resCompliant / resEligible) * 100.0;

        // CSAT stats
        Double avgCsat = csatSurveyRepository.getAverageRatingByOrganizationId(organizationId);
        Long totalCsat = csatSurveyRepository.countCompletedSurveysByOrganizationId(organizationId);

        // AI Deflection Rate: resolved conversations that had NO human agent reply
        long deflectedByAi = all.stream().filter(c -> c.isResolved() && c.getFirstAgentReplyAt() == null).count();
        double deflectionRate = resolved == 0 ? 0.0 : ((double) deflectedByAi / resolved) * 100.0;

        return PerformanceAnalyticsResponse.builder()
                .averageFirstResponseTimeMinutes(Math.round(avgFrt * 10.0) / 10.0)
                .averageResolutionTimeMinutes(Math.round(avgRes * 10.0) / 10.0)
                .slaFirstResponseComplianceRate(Math.round(frtRate * 10.0) / 10.0)
                .slaResolutionComplianceRate(Math.round(resRate * 10.0) / 10.0)
                .totalConversations(total)
                .resolvedConversations(resolved)
                .activeConversations(active)
                .averageCsatRating(avgCsat != null ? Math.round(avgCsat * 10.0) / 10.0 : 0.0)
                .totalCsatResponses(totalCsat != null ? totalCsat : 0L)
                .aiAutoPilotDeflectionRate(Math.round(deflectionRate * 10.0) / 10.0)
                .build();
    }

    private SlaPolicyResponse toPolicyResponse(ConversationSlaPolicy p) {
        return SlaPolicyResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .channel(p.getChannel())
                .priority(p.getPriority())
                .firstResponseTimeSeconds(p.getFirstResponseTimeSeconds())
                .resolutionTimeSeconds(p.getResolutionTimeSeconds())
                .routingPolicy(p.getRoutingPolicy())
                .active(p.isActive())
                .createdAt(p.getCreatedAt())
                .build();
    }
}

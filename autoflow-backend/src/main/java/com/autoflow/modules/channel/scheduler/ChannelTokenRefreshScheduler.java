package com.autoflow.modules.channel.scheduler;

import com.autoflow.modules.channel.entity.ConnectedAccount;
import com.autoflow.modules.channel.entity.ConnectedAccountStatus;
import com.autoflow.modules.channel.repository.ConnectedAccountRepository;
import com.autoflow.modules.channel.service.ChannelConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled background daemon that scans for social channel access tokens
 * expiring within 7 days and proactively requests renewals from provider APIs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelTokenRefreshScheduler {

    private static final int EXPIRATION_WARNING_DAYS = 7;

    private final ConnectedAccountRepository connectedAccountRepository;
    private final ChannelConnectionService channelConnectionService;

    @Scheduled(cron = "${autoflow.channels.refresh-cron:0 0 3 * * *}")
    public void executeScheduledTokenRefresh() {
        log.info("Executing scheduled social token renewal scan...");
        Instant threshold = Instant.now().plus(Duration.ofDays(EXPIRATION_WARNING_DAYS));

        List<ConnectedAccount> expiringAccounts = connectedAccountRepository
                .findByTokenExpiresAtBeforeAndStatus(threshold, ConnectedAccountStatus.ACTIVE);

        if (expiringAccounts.isEmpty()) {
            log.info("All social channel access tokens are up-to-date. Zero tokens require renewal.");
            return;
        }

        log.info("Found {} active connected accounts expiring within {} days", expiringAccounts.size(), EXPIRATION_WARNING_DAYS);

        int renewedCount = 0;
        int failedCount = 0;

        for (ConnectedAccount account : expiringAccounts) {
            try {
                channelConnectionService.refreshAccountToken(account.getId(), account.getOrganizationId());
                renewedCount++;
            } catch (Exception e) {
                log.error("Failed to proactively renew token for account [{}] ({}) in org [{}]: {}",
                        account.getId(), account.getChannel(), account.getOrganizationId(), e.getMessage());
                if (account.isExpired()) {
                    account.setStatus(ConnectedAccountStatus.EXPIRED);
                    connectedAccountRepository.save(account);
                }
                failedCount++;
            }
        }

        log.info("Completed scheduled token refresh: {} renewed successfully, {} failed.", renewedCount, failedCount);
    }
}

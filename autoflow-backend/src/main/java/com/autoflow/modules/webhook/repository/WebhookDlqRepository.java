package com.autoflow.modules.webhook.repository;

import com.autoflow.modules.webhook.entity.WebhookDlqEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WebhookDlqRepository extends JpaRepository<WebhookDlqEntry, UUID> {

    Page<WebhookDlqEntry> findByProvider(String provider, Pageable pageable);

    Page<WebhookDlqEntry> findByStatus(String status, Pageable pageable);

    Page<WebhookDlqEntry> findByProviderAndStatus(String provider, String status, Pageable pageable);
}

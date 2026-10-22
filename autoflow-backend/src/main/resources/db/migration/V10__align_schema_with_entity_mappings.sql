-- V10: Align Flyway-managed schema with Hibernate entity mappings
--
-- The application runs with `spring.jpa.hibernate.ddl-auto: validate`, so every column
-- mapped by an entity must exist in the migrated schema. The following gaps accumulated
-- across earlier phases and would abort application startup with a schema-validation error:
--   * tenant-aware entities extending BaseEntity require `updated_at` and `version` columns
--   * two entities (api_keys, webhook_dlq_entries) were introduced without a migration
--
-- All statements are idempotent so this migration is safe to re-run on partially migrated databases.

-- ==============================================================================
-- 1. Missing BaseEntity columns (optimistic locking + last-modified auditing)
-- ==============================================================================

-- Conversations (Phase 1 core table, extended through Phases 6/7/21/22)
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Media assets (Phase 1)
ALTER TABLE media_assets ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Knowledge base (Phase 5)
ALTER TABLE knowledge_chunks ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE knowledge_chunks ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Conversation intelligence / SLA (Phases 6 & 7)
ALTER TABLE conversation_sla_events ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE conversation_sla_events ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE contact_lead_score_audits ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE contact_lead_score_audits ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE agent_channel_specializations ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- Agent productivity & collaboration (Phase 8)
ALTER TABLE canned_responses ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE crm_macros ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE conversation_internal_notes ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE conversation_internal_notes ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- ==============================================================================
-- 2. Tables whose entities were introduced without a migration
-- ==============================================================================

-- API key management (settings / secret management, Phase 15)
CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    key_prefix VARCHAR(32) NOT NULL,
    hashed_secret VARCHAR(255) NOT NULL,
    masked_key VARCHAR(64) NOT NULL,
    scopes VARCHAR(255)[] DEFAULT '{}',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, REVOKED, EXPIRED
    expires_at TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_api_keys_org_status ON api_keys(organization_id, status);

-- Webhook dead-letter queue (media funnel, Phase 17)
CREATE TABLE IF NOT EXISTS webhook_dlq_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(50) NOT NULL, -- INSTAGRAM, WHATSAPP, TELEGRAM
    event_id VARCHAR(255),
    payload TEXT NOT NULL,
    headers TEXT,
    error_message TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'DEAD_LETTER', -- DEAD_LETTER, REPLAYED, DISCARDED
    attempt_count INT NOT NULL DEFAULT 1,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_attempt_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_webhook_dlq_status ON webhook_dlq_entries(status, received_at);

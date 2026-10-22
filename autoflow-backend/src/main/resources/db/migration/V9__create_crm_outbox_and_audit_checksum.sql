
-- V9: CRM Transactional Outbox for Reliable Macro Dispatch & Persistent Audit Checksums
-- Tables: crm_outbox_messages
-- Alterations: messages, conversation_internal_notes, conversation_sla_events (audit_checksum)

-- 1. CRM Outbox Messages Table
CREATE TABLE IF NOT EXISTS crm_outbox_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    macro_id UUID REFERENCES crm_macros(id) ON DELETE SET NULL,
    message_id UUID REFERENCES messages(id) ON DELETE SET NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    payload_content TEXT NOT NULL,
    media_url TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, DISPATCHED, FAILED, AMBIGUOUS
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    external_message_id VARCHAR(255),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    dispatched_at TIMESTAMPTZ,
    CONSTRAINT uq_crm_outbox_org_idempotency UNIQUE (organization_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_crm_outbox_org_status ON crm_outbox_messages(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_crm_outbox_convo ON crm_outbox_messages(conversation_id);

-- 2. Audit Checksums for Durable Event Integrity
ALTER TABLE messages ADD COLUMN IF NOT EXISTS audit_checksum VARCHAR(64);
ALTER TABLE conversation_internal_notes ADD COLUMN IF NOT EXISTS audit_checksum VARCHAR(64);
ALTER TABLE conversation_sla_events ADD COLUMN IF NOT EXISTS audit_checksum VARCHAR(64);

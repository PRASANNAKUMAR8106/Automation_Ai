-- V6: Omnichannel Conversation Intelligence, SLA Policies & CSAT Surveys

-- 1. Conversation SLA Policies
CREATE TABLE IF NOT EXISTS conversation_sla_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    channel VARCHAR(20),
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    first_response_time_seconds INT NOT NULL DEFAULT 900, -- Default 15 minutes
    resolution_time_seconds INT NOT NULL DEFAULT 7200,     -- Default 2 hours
    routing_policy VARCHAR(50) NOT NULL DEFAULT 'LEAST_BUSY',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sla_policies_org_active ON conversation_sla_policies(organization_id, is_active);

-- 2. Alter Conversations for Intelligence, Sentiment & SLA Tracking
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS sentiment VARCHAR(20) NOT NULL DEFAULT 'NEUTRAL';
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS sla_policy_id UUID REFERENCES conversation_sla_policies(id) ON DELETE SET NULL;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS sla_first_response_due_at TIMESTAMPTZ;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS sla_resolution_due_at TIMESTAMPTZ;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS sla_first_response_breached BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS sla_resolution_breached BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS first_agent_reply_at TIMESTAMPTZ;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_conversations_org_priority ON conversations(organization_id, priority);
CREATE INDEX IF NOT EXISTS idx_conversations_org_sentiment ON conversations(organization_id, sentiment);
CREATE INDEX IF NOT EXISTS idx_conversations_sla_due ON conversations(organization_id, sla_first_response_due_at, is_resolved);

-- 3. Customer Satisfaction (CSAT) Surveys
CREATE TABLE IF NOT EXISTS csat_surveys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    contact_id UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    assigned_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    rating INT, -- 1 to 5 scale
    feedback_text TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'DISPATCHED', -- DISPATCHED, COMPLETED, EXPIRED, SKIPPED_WINDOW_EXPIRED, SKIPPED_SUPPRESSED
    dispatched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_csat_surveys_org_status ON csat_surveys(organization_id, status);
CREATE INDEX IF NOT EXISTS idx_csat_surveys_conversation ON csat_surveys(conversation_id);
CREATE INDEX IF NOT EXISTS idx_csat_surveys_assigned_user ON csat_surveys(organization_id, assigned_user_id);

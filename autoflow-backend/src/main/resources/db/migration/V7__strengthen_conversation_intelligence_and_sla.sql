-- V7: Strengthen Conversation Intelligence, SLA Events, Channel Specialist Routing & CSAT Idempotency

-- 1. Conversation SLA Audit Events Table
CREATE TABLE IF NOT EXISTS conversation_sla_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sla_policy_id UUID REFERENCES conversation_sla_policies(id) ON DELETE SET NULL,
    event_type VARCHAR(50) NOT NULL, -- WARNING, BREACH_FIRST_RESPONSE, BREACH_RESOLUTION, ESCALATION, ASSIGNMENT, REASSIGNMENT
    assigned_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    previous_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    escalated_to_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    reason TEXT,
    details TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sla_events_org_convo ON conversation_sla_events(organization_id, conversation_id);
CREATE INDEX IF NOT EXISTS idx_sla_events_type ON conversation_sla_events(organization_id, event_type);

-- 2. Agent Channel Specializations Table
CREATE TABLE IF NOT EXISTS agent_channel_specializations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_agent_channel_specialization UNIQUE (organization_id, user_id, channel)
);

CREATE INDEX IF NOT EXISTS idx_agent_spec_org_channel ON agent_channel_specializations(organization_id, channel, is_active);

-- 3. Contact Lead Score Audit Trail Table
CREATE TABLE IF NOT EXISTS contact_lead_score_audits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    contact_id UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    conversation_id UUID REFERENCES conversations(id) ON DELETE SET NULL,
    previous_score INT NOT NULL,
    new_score INT NOT NULL,
    score_delta INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_lead_score_audits_contact ON contact_lead_score_audits(organization_id, contact_id, created_at);

-- 4. Alter Conversations for Deflection & Escalation Telemetry
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS ai_handled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS human_agent_replied BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS escalated_to_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMPTZ;

-- 5. Alter Conversation SLA Policies for WhatsApp Template Fallback
ALTER TABLE conversation_sla_policies ADD COLUMN IF NOT EXISTS whatsapp_template_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- 6. Add CSAT Unique Constraint per Conversation
CREATE UNIQUE INDEX IF NOT EXISTS idx_csat_surveys_convo_unique ON csat_surveys(conversation_id);

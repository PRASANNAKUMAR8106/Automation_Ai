-- V8: Enterprise Live Chat Agent Productivity, Real-Time Collaboration & Macros
-- Tables: canned_responses, crm_macros, conversation_internal_notes

-- 1. Canned Responses / Snippets Table
CREATE TABLE IF NOT EXISTS canned_responses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    shortcut VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    is_shared BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    usage_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_canned_response_org_shortcut UNIQUE (organization_id, shortcut)
);

CREATE INDEX IF NOT EXISTS idx_canned_responses_org_cat ON canned_responses(organization_id, category);
CREATE INDEX IF NOT EXISTS idx_canned_responses_org_shortcut ON canned_responses(organization_id, shortcut);

-- 2. CRM Macros Table
CREATE TABLE IF NOT EXISTS crm_macros (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    actions_json JSONB NOT NULL DEFAULT '[]',
    created_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_crm_macros_org ON crm_macros(organization_id, name);

-- 3. Conversation Internal Notes & Whispers Table
CREATE TABLE IF NOT EXISTS conversation_internal_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    author_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    author_email VARCHAR(255),
    note_type VARCHAR(30) NOT NULL DEFAULT 'INTERNAL_NOTE', -- INTERNAL_NOTE, SUPERVISOR_WHISPER
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_internal_notes_org_convo ON conversation_internal_notes(organization_id, conversation_id, created_at);

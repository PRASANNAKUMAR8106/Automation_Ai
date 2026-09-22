-- ==============================================================================
-- AutoFlow AI - V1 Initial Master Schema Migration
-- Compatible with PostgreSQL 16 + pgvector
-- ==============================================================================

-- 1. EXTENSIONS
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 2. ENUM TYPES
CREATE TYPE user_role AS ENUM (
    'SUPER_ADMIN',
    'ADMIN',
    'SUPPORT',
    'AGENCY_MEMBER',
    'CUSTOMER',
    'CREATOR',
    'INFLUENCER'
);

CREATE TYPE membership_role AS ENUM (
    'OWNER',
    'ADMIN',
    'MANAGER',
    'EDITOR',
    'VIEWER'
);

CREATE TYPE subscription_status AS ENUM (
    'TRIALING',
    'ACTIVE',
    'PAST_DUE',
    'PAUSED',
    'CANCELLED',
    'SUSPENDED',
    'EXPIRED'
);

CREATE TYPE channel_type AS ENUM (
    'INSTAGRAM',
    'WHATSAPP',
    'MESSENGER',
    'TELEGRAM'
);

CREATE TYPE execution_status AS ENUM (
    'RUNNING',
    'SUCCESS',
    'FAILED',
    'CANCELLED',
    'RETRYING'
);

CREATE TYPE commission_status AS ENUM (
    'PENDING',
    'APPROVED',
    'PAYABLE',
    'PAID',
    'REVERSED'
);

CREATE TYPE payout_status AS ENUM (
    'REQUESTED',
    'APPROVED',
    'REJECTED',
    'PAID'
);

CREATE TYPE lead_status AS ENUM (
    'NEW',
    'LEAD',
    'QUALIFIED',
    'CUSTOMER',
    'LOST'
);

CREATE TYPE discount_type AS ENUM (
    'PERCENTAGE',
    'FIXED'
);

CREATE TYPE commission_type AS ENUM (
    'PERCENTAGE',
    'FIXED'
);

CREATE TYPE commission_basis AS ENUM (
    'NET_QUALIFYING',
    'GROSS'
);

-- ==============================================================================
-- 3. CORE TENANT & IDENTITY TABLES
-- ==============================================================================

CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    branding_config JSONB DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_organizations_slug ON organizations(slug);

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role user_role NOT NULL DEFAULT 'CUSTOMER',
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE memberships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role membership_role NOT NULL DEFAULT 'VIEWER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_memberships_org_user UNIQUE (organization_id, user_id)
);
CREATE INDEX idx_memberships_org ON memberships(organization_id);
CREATE INDEX idx_memberships_user ON memberships(user_id);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    device_info VARCHAR(255),
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- ==============================================================================
-- 4. BILLING, PLANS & ENTITLEMENTS
-- ==============================================================================

CREATE TABLE plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price_inr NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    billing_interval VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE plan_features (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    feature_key VARCHAR(100) NOT NULL,
    limit_value BIGINT NOT NULL DEFAULT 0,
    is_unlimited BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_plan_feature UNIQUE (plan_id, feature_key)
);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    plan_id UUID NOT NULL REFERENCES plans(id),
    provider VARCHAR(50) NOT NULL DEFAULT 'RAZORPAY',
    provider_subscription_id VARCHAR(255),
    provider_customer_id VARCHAR(255),
    status subscription_status NOT NULL DEFAULT 'TRIALING',
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end TIMESTAMPTZ NOT NULL,
    grace_period_end TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_sub_org UNIQUE (organization_id)
);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);

CREATE TABLE usage_ledgers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    executions_count BIGINT NOT NULL DEFAULT 0,
    messages_count BIGINT NOT NULL DEFAULT 0,
    ai_requests_count BIGINT NOT NULL DEFAULT 0,
    storage_bytes BIGINT NOT NULL DEFAULT 0,
    contacts_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_usage_period UNIQUE (organization_id, billing_period_start)
);

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    subscription_id UUID REFERENCES subscriptions(id),
    provider VARCHAR(50) NOT NULL DEFAULT 'RAZORPAY',
    provider_payment_id VARCHAR(255) NOT NULL UNIQUE,
    provider_order_id VARCHAR(255),
    amount_inr NUMERIC(12, 2) NOT NULL,
    discount_inr NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    tax_inr NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    net_inr NUMERIC(12, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    is_refunded BOOLEAN NOT NULL DEFAULT FALSE,
    refunded_amount_inr NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    promo_code VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payments_org ON payments(organization_id);

CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    payment_id UUID NOT NULL REFERENCES payments(id),
    invoice_number VARCHAR(100) NOT NULL UNIQUE,
    subtotal_inr NUMERIC(12, 2) NOT NULL,
    gst_rate_percent NUMERIC(5, 2) NOT NULL DEFAULT 18.00,
    gst_amount_inr NUMERIC(12, 2) NOT NULL DEFAULT 0.00,
    total_inr NUMERIC(12, 2) NOT NULL,
    customer_gstin VARCHAR(50),
    pdf_s3_key VARCHAR(500),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ==============================================================================
-- 5. SOCIAL CHANNELS & MEDIA STORAGE
-- ==============================================================================

CREATE TABLE connected_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    channel channel_type NOT NULL,
    external_account_id VARCHAR(255) NOT NULL,
    account_name VARCHAR(255),
    account_handle VARCHAR(255),
    encrypted_access_token TEXT NOT NULL,
    token_expires_at TIMESTAMPTZ,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_org_channel_account UNIQUE (organization_id, channel, external_account_id)
);
CREATE INDEX idx_connected_accounts_org ON connected_accounts(organization_id);

CREATE TABLE media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    s3_bucket VARCHAR(100) NOT NULL,
    s3_key VARCHAR(500) NOT NULL UNIQUE,
    sha256_checksum VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_media_assets_org ON media_assets(organization_id);

-- ==============================================================================
-- 6. WORKFLOW & AUTOMATION ENGINE
-- ==============================================================================

CREATE TABLE workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    active_version_number INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_workflows_org ON workflows(organization_id);

CREATE TABLE workflow_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    graph_definition JSONB NOT NULL,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_workflow_version UNIQUE (workflow_id, version_number)
);

CREATE TABLE automation_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    workflow_version_id UUID REFERENCES workflow_versions(id),
    trigger_type VARCHAR(100) NOT NULL,
    trigger_event_id VARCHAR(255),
    status execution_status NOT NULL DEFAULT 'RUNNING',
    current_node_id VARCHAR(100),
    execution_context JSONB DEFAULT '{}'::jsonb,
    error_message TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_executions_org ON automation_executions(organization_id);
CREATE INDEX idx_executions_workflow ON automation_executions(workflow_id);
CREATE INDEX idx_executions_status ON automation_executions(status);

-- ==============================================================================
-- 7. CONTACTS, CRM & UNIFIED INBOX
-- ==============================================================================

CREATE TABLE contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    channel channel_type NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    username VARCHAR(255),
    full_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),
    lead_status lead_status NOT NULL DEFAULT 'NEW',
    lead_score INT NOT NULL DEFAULT 0,
    tags VARCHAR(100)[] DEFAULT '{}',
    custom_fields JSONB DEFAULT '{}'::jsonb,
    consent_given BOOLEAN NOT NULL DEFAULT FALSE,
    consent_timestamp TIMESTAMPTZ,
    last_interaction_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_org_contact_channel UNIQUE (organization_id, channel, external_id)
);
CREATE INDEX idx_contacts_org ON contacts(organization_id);
CREATE INDEX idx_contacts_email ON contacts(organization_id, email);
CREATE INDEX idx_contacts_tags ON contacts USING GIN(tags);

CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    contact_id UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    channel channel_type NOT NULL,
    assigned_user_id UUID REFERENCES users(id),
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_conversations_org ON conversations(organization_id);
CREATE INDEX idx_conversations_contact ON conversations(contact_id);

CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    external_message_id VARCHAR(255),
    direction VARCHAR(20) NOT NULL, -- INBOUND or OUTBOUND
    sender_type VARCHAR(20) NOT NULL, -- CONTACT, BOT, AGENT
    message_type VARCHAR(50) NOT NULL DEFAULT 'TEXT',
    content TEXT NOT NULL,
    media_url TEXT,
    delivery_status VARCHAR(50) NOT NULL DEFAULT 'SENT',
    sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_messages_conversation ON messages(conversation_id);

-- ==============================================================================
-- 8. INFLUENCER, REFERRALS & COMMISSION LEDGER (STRICT ADMIN MUTATION)
-- ==============================================================================

CREATE TABLE campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    discount_type discount_type NOT NULL DEFAULT 'PERCENTAGE',
    discount_value NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    commission_type commission_type NOT NULL DEFAULT 'PERCENTAGE',
    commission_value NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    commission_basis commission_basis NOT NULL DEFAULT 'NET_QUALIFYING',
    attribution_window_days INT NOT NULL DEFAULT 30,
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE influencers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    instagram_handle VARCHAR(100),
    country VARCHAR(100) DEFAULT 'IN',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    encrypted_payout_details TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_influencers_email ON influencers(email);

CREATE TABLE promo_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE RESTRICT,
    influencer_id UUID NOT NULL REFERENCES influencers(id) ON DELETE RESTRICT,
    code VARCHAR(50) NOT NULL UNIQUE,
    discount_type discount_type NOT NULL DEFAULT 'PERCENTAGE',
    discount_value NUMERIC(10, 2) NOT NULL,
    commission_type commission_type NOT NULL DEFAULT 'PERCENTAGE',
    commission_value NUMERIC(10, 2) NOT NULL,
    commission_basis commission_basis NOT NULL DEFAULT 'NET_QUALIFYING',
    max_uses INT,
    current_uses INT NOT NULL DEFAULT 0,
    minimum_purchase_inr NUMERIC(12, 2) DEFAULT 0.00,
    start_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expiry_date TIMESTAMPTZ,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_promo_codes_code ON promo_codes(code);

CREATE TABLE referral_clicks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promo_code_id UUID NOT NULL REFERENCES promo_codes(id),
    influencer_id UUID NOT NULL REFERENCES influencers(id),
    ip_hash VARCHAR(64) NOT NULL,
    user_agent TEXT,
    utm_source VARCHAR(100),
    utm_medium VARCHAR(100),
    utm_campaign VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_referral_clicks_promo ON referral_clicks(promo_code_id);
CREATE INDEX idx_referral_clicks_created ON referral_clicks(created_at);

CREATE TABLE referral_attributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    promo_code_id UUID NOT NULL REFERENCES promo_codes(id),
    influencer_id UUID NOT NULL REFERENCES influencers(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL REFERENCES users(id),
    attribution_type VARCHAR(50) NOT NULL, -- LINK_CLICK or PROMO_ENTERED
    attributed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_org_attribution UNIQUE (organization_id)
);

CREATE TABLE commissions_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    influencer_id UUID NOT NULL REFERENCES influencers(id),
    attribution_id UUID NOT NULL REFERENCES referral_attributions(id),
    payment_id UUID NOT NULL REFERENCES payments(id),
    amount_inr NUMERIC(12, 2) NOT NULL,
    status commission_status NOT NULL DEFAULT 'PENDING',
    qualifies_at TIMESTAMPTZ NOT NULL,
    approved_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    reversed_at TIMESTAMPTZ,
    reversal_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_commissions_influencer ON commissions_ledger(influencer_id);
CREATE INDEX idx_commissions_status ON commissions_ledger(status);

CREATE TABLE payouts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    influencer_id UUID NOT NULL REFERENCES influencers(id),
    amount_inr NUMERIC(12, 2) NOT NULL,
    status payout_status NOT NULL DEFAULT 'REQUESTED',
    transaction_reference VARCHAR(255),
    admin_notes TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    processed_by UUID REFERENCES users(id),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_payouts_influencer ON payouts(influencer_id);
CREATE INDEX idx_payouts_status ON payouts(status);

-- ==============================================================================
-- 9. AUDITING, WEBHOOK IDEMPOTENCY & TEMPLATES
-- ==============================================================================

CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider VARCHAR(50) NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_provider_event UNIQUE (provider, event_id)
);
CREATE INDEX idx_webhook_events_lookup ON webhook_events(provider, event_id);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID REFERENCES organizations(id),
    user_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255),
    details JSONB DEFAULT '{}'::jsonb,
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_logs_org ON audit_logs(organization_id);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at);

CREATE TABLE templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    description TEXT,
    icon VARCHAR(100),
    tags VARCHAR(100)[] DEFAULT '{}',
    graph_definition JSONB NOT NULL,
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

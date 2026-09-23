-- V3: Add last_customer_message_at to conversations for Meta/WhatsApp 24-hour compliance
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS last_customer_message_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_conversations_last_customer_msg ON conversations(organization_id, last_customer_message_at);

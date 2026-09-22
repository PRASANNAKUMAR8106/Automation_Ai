-- ==============================================================================
-- AutoFlow AI - V2 Initial Seed: Plans, Dynamic Quotas & System Templates
-- ==============================================================================

-- 1. SEED PLANS
INSERT INTO plans (id, code, name, description, price_inr, billing_interval, is_active) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'FREE', 'Free Starter', 'Essential social automation for individuals getting started', 0.00, 'MONTHLY', true),
    ('a0000000-0000-0000-0000-000000000002', 'STARTER', 'Creator Starter', 'Empowering growing creators and personal brands', 499.00, 'MONTHLY', true),
    ('a0000000-0000-0000-0000-000000000003', 'PRO', 'Pro Marketer', 'High-volume automation for serious creators, coaches & stores', 1499.00, 'MONTHLY', true),
    ('a0000000-0000-0000-0000-000000000004', 'BUSINESS', 'Business Growth', 'Full omni-channel scaling for ecommerce and businesses', 3999.00, 'MONTHLY', true),
    ('a0000000-0000-0000-0000-000000000005', 'AGENCY', 'Agency Master', 'Multi-client power suite with high throughput and team seats', 9999.00, 'MONTHLY', true)
ON CONFLICT (code) DO NOTHING;

-- 2. SEED PLAN FEATURES & QUOTAS
-- FREE
INSERT INTO plan_features (plan_id, feature_key, limit_value, is_unlimited) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'max_automations', 2, false),
    ('a0000000-0000-0000-0000-000000000001', 'max_contacts', 500, false),
    ('a0000000-0000-0000-0000-000000000001', 'monthly_messages', 1000, false),
    ('a0000000-0000-0000-0000-000000000001', 'ai_requests', 100, false),
    ('a0000000-0000-0000-0000-000000000001', 'storage_mb', 100, false)
ON CONFLICT (plan_id, feature_key) DO UPDATE SET limit_value = EXCLUDED.limit_value;

-- STARTER
INSERT INTO plan_features (plan_id, feature_key, limit_value, is_unlimited) VALUES
    ('a0000000-0000-0000-0000-000000000002', 'max_automations', 5, false),
    ('a0000000-0000-0000-0000-000000000002', 'max_contacts', 2500, false),
    ('a0000000-0000-0000-0000-000000000002', 'monthly_messages', 10000, false),
    ('a0000000-0000-0000-0000-000000000002', 'ai_requests', 1000, false),
    ('a0000000-0000-0000-0000-000000000002', 'storage_mb', 1000, false)
ON CONFLICT (plan_id, feature_key) DO UPDATE SET limit_value = EXCLUDED.limit_value;

-- PRO
INSERT INTO plan_features (plan_id, feature_key, limit_value, is_unlimited) VALUES
    ('a0000000-0000-0000-0000-000000000003', 'max_automations', 20, false),
    ('a0000000-0000-0000-0000-000000000003', 'max_contacts', 10000, false),
    ('a0000000-0000-0000-0000-000000000003', 'monthly_messages', 50000, false),
    ('a0000000-0000-0000-0000-000000000003', 'ai_requests', 5000, false),
    ('a0000000-0000-0000-0000-000000000003', 'storage_mb', 5000, false)
ON CONFLICT (plan_id, feature_key) DO UPDATE SET limit_value = EXCLUDED.limit_value;

-- BUSINESS
INSERT INTO plan_features (plan_id, feature_key, limit_value, is_unlimited) VALUES
    ('a0000000-0000-0000-0000-000000000004', 'max_automations', 50, false),
    ('a0000000-0000-0000-0000-000000000004', 'max_contacts', 50000, false),
    ('a0000000-0000-0000-0000-000000000004', 'monthly_messages', 200000, false),
    ('a0000000-0000-0000-0000-000000000004', 'ai_requests', 25000, false),
    ('a0000000-0000-0000-0000-000000000004', 'storage_mb', 25000, false)
ON CONFLICT (plan_id, feature_key) DO UPDATE SET limit_value = EXCLUDED.limit_value;

-- AGENCY
INSERT INTO plan_features (plan_id, feature_key, limit_value, is_unlimited) VALUES
    ('a0000000-0000-0000-0000-000000000005', 'max_automations', 200, false),
    ('a0000000-0000-0000-0000-000000000005', 'max_contacts', 250000, false),
    ('a0000000-0000-0000-0000-000000000005', 'monthly_messages', 1000000, false),
    ('a0000000-0000-0000-0000-000000000005', 'ai_requests', 100000, false),
    ('a0000000-0000-0000-0000-000000000005', 'storage_mb', 100000, false)
ON CONFLICT (plan_id, feature_key) DO UPDATE SET limit_value = EXCLUDED.limit_value;

-- 3. SEED STARTER TEMPLATES
INSERT INTO templates (id, name, category, description, icon, tags, graph_definition, is_featured) VALUES
(
    'b0000000-0000-0000-0000-000000000001',
    'Comment Keyword to DM Lead Magnet',
    'LEAD_MAGNET',
    'When a follower comments GUIDE on your Reel/Post, reply publicly and deliver your PDF guide in their DM',
    'file-download',
    ARRAY['instagram', 'lead-generation', 'reel', 'pdf'],
    '{"nodes": [{"id": "node-1", "type": "TRIGGER_INSTAGRAM_COMMENT", "config": {"keywords": ["GUIDE", "PDF"], "case_sensitive": false}}, {"id": "node-2", "type": "ACTION_PUBLIC_COMMENT_REPLY", "config": {"reply": "Sent the guide to your DM! Check your inbox 🎁"}}, {"id": "node-3", "type": "ACTION_SEND_DM", "config": {"message": "Here is your exclusive guide! Click below to download."}}, {"id": "node-4", "type": "ACTION_SEND_MEDIA", "config": {"asset_type": "PDF"}}], "edges": [{"from": "node-1", "to": "node-2"}, {"from": "node-2", "to": "node-3"}, {"from": "node-3", "to": "node-4"}]}'::jsonb,
    true
),
(
    'b0000000-0000-0000-0000-000000000002',
    'Comment to Promo Coupon Delivery',
    'ECOMMERCE',
    'Reward commenters on your promotional posts with an instant exclusive discount coupon code',
    'ticket-percent',
    ARRAY['instagram', 'ecommerce', 'discount', 'coupon'],
    '{"nodes": [{"id": "node-1", "type": "TRIGGER_INSTAGRAM_COMMENT", "config": {"keywords": ["SALE", "COUPON", "DISCOUNT"], "case_sensitive": false}}, {"id": "node-2", "type": "ACTION_SEND_DM", "config": {"message": "Thanks for supporting! Use code SAVE20 for 20% off your next order."}}], "edges": [{"from": "node-1", "to": "node-2"}]}'::jsonb,
    true
)
ON CONFLICT (id) DO NOTHING;

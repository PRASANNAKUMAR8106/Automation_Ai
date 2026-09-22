-- AutoFlow AI Local Database Initialization Script
-- Executed on initial PostgreSQL container startup

-- Create extensions required by AutoFlow
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "vector";

-- Verify extensions
SELECT extname, extversion FROM pg_extension WHERE extname IN ('uuid-ossp', 'pgcrypto', 'vector');

-- Create default schema configuration
GRANT ALL PRIVILEGES ON DATABASE autoflow_db TO autoflow_user;

-- PostgreSQL initialization script
-- Runs once when the container is first created

-- Ensure UUID extension is available
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Set timezone
SET timezone = 'Asia/Shanghai';

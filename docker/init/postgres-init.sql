-- PostgreSQL initialization script
-- Runs once when the container is first created

-- Ensure UUID extension is available
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Set timezone
SET timezone = 'Asia/Shanghai';

-- ADR-015：为自部署 LangFuse 创建独立库（与应用 schema 完全隔离，LangFuse 自管其迁移）。
-- 仅在容器首次初始化时执行；已存在则忽略。
SELECT 'CREATE DATABASE langfuse'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'langfuse')\gexec

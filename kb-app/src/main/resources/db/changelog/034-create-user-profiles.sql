--liquibase formatted sql
--changeset kb:034-create-user-profiles

-- 用户画像（持久偏好）。JSONB 单列存 {declared, inferred, meta}（结构见 ADR-016）。
-- 个性化问答在 prompt 侧注入；显式声明同步写、离线推断异步写（不覆盖显式）。
-- 沿用项目惯例不加外键（迁移 014 已统一移除 FK）。
CREATE TABLE IF NOT EXISTS user_profiles (
    user_id    UUID PRIMARY KEY,
    profile    JSONB NOT NULL DEFAULT '{}',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE user_profiles IS '用户画像（持久偏好），JSONB 存 declared/inferred/meta，用于个性化问答 prompt 注入，见 ADR-016';

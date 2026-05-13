--liquibase formatted sql

--changeset elliot:020-make-review-space-id-nullable
-- 客户助手提交的售后申请不绑定知识空间，允许 space_id 为 NULL
ALTER TABLE review_requests ALTER COLUMN space_id DROP NOT NULL;

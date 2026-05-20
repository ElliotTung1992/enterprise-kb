--liquibase formatted sql

--changeset elliot:024-add-customer-messages-domain
ALTER TABLE customer_messages ADD COLUMN domain VARCHAR(32);

COMMENT ON COLUMN customer_messages.domain IS 'Tier-1 域路由器判定的业务域，用于域级准确率汇总与意图识别评估；历史消息及非路由消息为 NULL';

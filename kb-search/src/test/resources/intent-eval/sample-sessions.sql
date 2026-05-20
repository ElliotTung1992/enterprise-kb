-- 意图识别评估集采样（Phase 0）
--
-- 拉取多轮（消息数 >= 4）且未删除的客服会话全文，供运营测试同学配额采样并逐轮标注
-- expected_domain。导出后按 README.md 的覆盖面清单挑选，不要直接全量使用——随机
-- 全量几乎全是 happy path，会漏掉意图漂移 / 复合意图 / 域外等关键坑。

SELECT s.id            AS session_id,
       m.role,
       m.content,
       m.domain        AS routed_domain,   -- 线上路由器写入的域（影子/正式期才有值），人工标注时作参考
       m.created_at
FROM   customer_sessions s
JOIN   customer_messages m ON m.session_id = s.id
WHERE  s.deleted_at IS NULL
  AND  s.id IN (
           SELECT session_id
           FROM   customer_messages
           GROUP  BY session_id
           HAVING COUNT(*) >= 4
       )
ORDER  BY s.id, m.created_at;

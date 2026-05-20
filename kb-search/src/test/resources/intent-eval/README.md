# 意图识别评估集（Phase 0）

Tier-1 域路由器的评估数据。**门控**：路由器达标前不进影子模式。详见
`wiki/decisions/adr-008-intent-routing-two-tier.md`。

## 两份文件，硬切分

| 文件 | 用途 | 规则 |
|------|------|------|
| `fewshot-pool.jsonl` | 可作为 few-shot 示例进路由器 prompt | 线上误判捞回后也补进这里 |
| `frozen-testset.jsonl` | 只用于打分，**永不进 prompt、永不修改** | 跨版本数字才可比 |

**few-shot 污染**：同一条用户表达（含近似措辞）不可同时出现在两份文件。一旦某条
测试用例泄进 prompt，即"烧掉"——移去 fewshot-pool，另补一条新的进 frozen-testset。

## jsonl schema

每行一个 JSON 对象 = 一段完整多轮对话：

```json
{"id":"ft-001","note":"售后退款 + 槽位回填","turns":[
  {"role":"user","content":"我要退货","expected_domain":"AFTER_SALES"},
  {"role":"assistant","content":"好的，请提供订单号"},
  {"role":"user","content":"SO20260510077","expected_domain":"SKIP"}
]}
```

- `turns[]`：user / assistant 交替。
- 仅 `user` 轮带 `expected_domain`；`assistant` 轮是上下文，不打分。
- `expected_domain` 取值：`AFTER_SALES` `COMPLAINT` `HANDOFF` `CHITCHAT` `UNCLEAR` `SKIP`。
  - `SKIP`：该轮是裸槽位回填，命中 awaiting_slot 硬规则、应跳过路由。
    路由器准确率统计**排除** SKIP 轮（它验证的是分派层，不是路由器）。
- `expected_secondary`（可选）：同句复合意图时的次要业务域数组，如 `["COMPLAINT"]`。

## 标注配额（运营测试同学）

按 session 采样 80~120 段多轮对话（采样 SQL 见 `sample-sessions.sql`）。**配额采样**，
每类坑至少 10 段，不可随机采（随机采几乎全是 happy path）：

- 4 类域外：CHITCHAT、HANDOFF、UNCLEAR、注入攻击
- 多轮槽位回填（SKIP 轮）
- 对话中途意图漂移（售后 ↔ 投诉）
- 同句复合意图
- 纯情绪无诉求（不应误判为 COMPLAINT）

本目录现有数据是**模板示例**，演示 schema 与覆盖面，需由运营测试同学替换/扩充至目标规模。

## 跑评估

`DomainRouterEvalTest` 读取 `frozen-testset.jsonl`，逐 user 轮跑 `DomainRouterServiceImpl`，
输出域级混淆矩阵 + 记分卡。目标：误判域率 < 3%、UNCLEAR 率 < 15%、HANDOFF 漏判率 < 5%。

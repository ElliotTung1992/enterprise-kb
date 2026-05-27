# Hot Cache

_Last updated: 2026-05-27_

## 最近工作焦点

**客服助手意图识别 — 两层域路由**（Phase 0-3 全部实现，未上线）

### 关键决策（本轮最重要）
1. **两层路由**：Tier-1 `DomainRouterService` 分类到业务域，Tier-2 `DomainHandler` 选工具；准确率归属分离 → [[decisions/adr-008-intent-routing-two-tier]]
2. **路由器只评"域"**：意图识别准确性 = 域路由准确，工具选择是各域 Agent 内部事，独立度量
3. **few-shot 污染硬切分**：`frozen-testset.jsonl`（永不进 prompt）与 `fewshot-pool.jsonl` 不相交，否则准确率虚高
4. **awaiting_slot 硬规则**：上轮索要订单号 → 下轮跳过路由，裸槽位回复直接回当前域
5. **先影子后灰度**：`routing-enabled` kill-switch 默认 false，旧单体路径仍生效

### 当前文件索引
| 概念 | 文件 |
|------|------|
| 两层域路由完整功能 | [[features/intent-routing]] |
| 架构决策（含 grill-me 11 决策点） | [[decisions/adr-008-intent-routing-two-tier]] |
| 投诉升级（被 ComplaintDomainHandler 包入） | [[features/complaint-escalation]] |
| 售后 HITL（被 AfterSalesDomainHandler 包入） | [[features/hitl-after-sales]] |

### 关键代码路径
- 流水线：`CustomerAssistantServiceImpl.routedChat`（攻击守卫 → 状态/硬规则 → `DomainRouterService` → `DomainHandler` 分派 → secondary 反问 → 持久化）
- 路由器：`DomainRouterServiceImpl`，一次 MINIMAX 调用，输出 `PRIMARY|SECONDARY_CSV|RUNNER_UP|EVIDENCE`，证据判 UNCLEAR
- 域处理器：`AfterSalesDomainHandler`（2 工具 + HITL）、`ComplaintDomainHandler`（触发投诉 StateGraph）
- 路由状态：`ConversationStateStore`，Redis `qa:state:{sessionId}`
- 评估：`kb-search/src/test/resources/intent-eval/` + `DomainRouterEvalTest`（`INTENT_EVAL=true` 门控）
- kill-switch：`enterprise.kb.customer-assistant.routing-enabled`（默认 false）

### 实现状态
- Phase 0-3 全部实现，全项目编译通过，76 个单元测试通过
- 未做：评估集为模板（待运营测试同学标注）、影子数据为空（待部署）、迁移 024 未落库、端到端 & HITL 回归未跑
- 下一步：标注评估集 → `DomainRouterEvalTest` 达标 → 部署攒影子数据 → 影子达标后开 `routing-enabled` 灰度

## 最近 ingest（非开发焦点）

**2026-05-27 MD 关键词检索升级 BM25** → [[features/md-keyword-bm25]] · [[decisions/adr-013-md-keyword-bm25]]
- 升级 [[features/markdown-structure-rag]] 竖井的**关键词那一路**：pg_trgm → 真正 BM25（VectorChord-bm25 + pg_tokenizer jieba）。改动只在 `MdKeywordSearchServiceImpl` 一个 method（RRF 按排名融合，BM25 负分免归一化）。
- 三件套：`md_ta`(jieba 切词) → `md_model`(词→ID **冻结**词表，从 `embed_text` 学) → `md_tok`。`bm25vector`={词ID:TF}（严格升序，TF 是值不是维度）；DF/IDF 在 `USING bm25` 倒排索引、`to_bm25query` 查询时现算。
- 状态：代码已落地（编译过、md 入库 7/7）；**运行态 build 脚本未跑**——实测 `tokenizer_catalog.model/text_analyzer/tokenizer` 三表 0 行，`md_model` 等尚不存在，BM25 链路未真正可用，默认仍 TRGM。`db/manual/md-bm25-build.sql` 须语料 ingest 后手动跑。

**2026-05-26 Markdown 结构感知 RAG 验收文档** → [[features/markdown-structure-rag]] · [[features/markdown-structure-rag-acceptance]] · [[decisions/adr-012-markdown-structure-rag]]
- 与 [[features/markdown-visual-rag-l2]]（图文 RAG）是**两条不同竖井**：结构感知走全新 `md_documents`/`md_kb_chunks`，small-to-big 父子索引，标准竖井零改动。
- 已实现，入库单测 7/7 通过；检索/Agentic 无自动化测试、端到端未联调。核心架构决策已落 adr-012。

## 上一轮焦点

**投诉升级系统**（Phase 0-9 完成）→ [[features/complaint-escalation]] · [[decisions/adr-007-complaint-escalation-stategraph]]

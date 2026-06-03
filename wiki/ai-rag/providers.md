# AI 提供商配置

## Bean 命名约定

| Bean 名 | 类型 | 提供商 |
|---------|------|--------|
| `dashscopeChatClient` | ChatClient | 阿里云 DashScope |
| `llamaCppChatClient` | ChatClient | llama.cpp 本地模型（OpenAI 兼容接口，**默认 chat provider**） |
| `dashscopeEmbeddingModel` | EmbeddingModel | DashScope（**@Primary**） |

> **MiniMax 已退役**：项目不再使用 MiniMax，相关 bean / 依赖 / 配置 / API Key 均已移除。`ModelProvider` 枚举现为 `LLAMA_CPP` / `OPENAI`。

## 歧义解决

DashScope embedding 自动配置注册 `dashscopeEmbeddingModel`。当 classpath 上同时存在其他 `EmbeddingModel` 候选（如配了 `OPENAI_API_KEY` 时 OpenAI embedding 自动配置也会注册一个 bean）时，`mdVectorStore` 按类型注入会产生歧义。

**解决**：`AiModelConfig.embeddingModelPrimaryPostProcessor()` 是一个 `BeanFactoryPostProcessor`，在 Bean 实例化前将 `dashscopeEmbeddingModel` 标为 `primary = true`。其他 EmbeddingModel bean 仍可通过 `@Qualifier` 按名称注入，供 `ModelProviderResolver` 使用。

> 新增 AI 提供商时，需在该 PostProcessor 中确认只有一个 EmbeddingModel 被标为 primary，否则启动报歧义错误。

## 运行时切换

`ModelProviderResolver` 通过 `@Qualifier` 按名称注入所有提供商的 Bean，根据请求中的 `modelProvider` 字段动态路由：

```java
// 请求体示例
{
  "question": "xxx",
  "modelProvider": "DASHSCOPE",  // DASHSCOPE | LLAMA_CPP
  "topK": 5
}
```

默认值配置：
```yaml
enterprise.kb.ai:
  default-provider: LLAMA_CPP
  default-embedding-provider: DASHSCOPE
```

## 条件装配

每个提供商 Bean 用 `@ConditionalOnProperty` 控制，API Key 未配置时不创建 Bean，避免启动失败。

## ObservationRegistry 注入

**埋点盲区警告**：

- llama.cpp 是默认 chat provider，但 `AiModelConfig.llamaCppChatClient` 手工 `OpenAiChatModel.builder()` **必须显式注入 `ObservationRegistry`**，否则落 NOOP → 默认链路零 trace
- `mdVectorStore` 是手工 bean（Milvus 自动配置被 exclude），同样必须注入 `ObservationRegistry`

详见 [[features/langfuse-tracing]] §埋点盲区 A/D。

## 嵌入维度注意事项

- 当前统一使用 **1536 维**（DashScope text-embedding-v2）
- 切换 Embedding 提供商 → 必须重建 Milvus collection `md_kb_chunks` + 重新摄入所有 md 文档
- 当前活跃 collection: `md_kb_chunks`，metric: `COSINE`，index: `IVF_FLAT`（原 `kb_chunks` 已随迁移 031 退役）

## 相关页面

- [[decisions/adr-002-milvus-vector-store]] — 向量库决策
- [[features/langfuse-tracing]] — 埋点盲区 A/D

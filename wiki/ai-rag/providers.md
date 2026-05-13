# AI 提供商配置

## Bean 命名约定

| Bean 名 | 类型 | 提供商 |
|---------|------|--------|
| `dashscopeChatClient` | ChatClient | 阿里云 DashScope |
| `minimaxChatClient` | ChatClient | MiniMax |
| `anthropicChatClient` | ChatClient | Anthropic |
| `dashscopeEmbeddingModel` | EmbeddingModel | DashScope (**@Primary**) |
| `minimaxEmbeddingModel` | EmbeddingModel | MiniMax |

## 歧义解决

DashScope 和 MiniMax 自动配置各自注册了一个 `EmbeddingModel`，导致 Milvus VectorStore 装配歧义。

**解决**：`AiModelConfig.embeddingModelPrimaryPostProcessor()` 是一个 `BeanFactoryPostProcessor`，在 Bean 实例化前将 `dashscopeEmbeddingModel` 标为 `primary = true`。

> 新增 AI 提供商时，需在该 PostProcessor 中确认只有一个 EmbeddingModel 被标为 primary。

## 运行时切换

`ModelProviderResolver` 通过 `@Qualifier` 按名称注入所有提供商的 Bean，根据请求中的 `modelProvider` 字段动态路由：

```java
// 请求体示例
{
  "question": "xxx",
  "modelProvider": "DASHSCOPE",  // DASHSCOPE | MINIMAX | OPENAI | ANTHROPIC
  "topK": 5
}
```

默认值配置：
```yaml
enterprise.kb.ai:
  default-provider: MINIMAX
  default-embedding-provider: DASHSCOPE
```

## 条件装配

每个提供商 Bean 用 `@ConditionalOnProperty` 控制，API Key 未配置时不创建 Bean，避免启动失败。

## 嵌入维度注意事项

- 当前统一使用 **1536 维**（DashScope text-embedding-v2 / MiniMax embo-01）
- 切换 Embedding 提供商 → 必须重建 Milvus collection + 重新摄入所有文档
- Milvus collection: `kb_chunks`，metric: `COSINE`，index: `IVF_FLAT`

# MinIO 对象清单

回放样本假设 MinIO endpoint 固定为：

```text
http://localhost:9000
```

bucket 固定为：

```text
kb-assets
```

需要预置的对象：

| objectKey | 用途 |
|---|---|
| `md-assets/replay/architecture.png` | 系统架构图，会生成一个 `IMAGE_CAPTION` child |
| `md-assets/replay/dashboard.png` | 监控面板图，会生成一个 `IMAGE_CAPTION` child |

不应预置 / 不应访问的对象：

| objectKey | 预期 |
|---|---|
| `md-assets/replay/ignored.png` | 位于代码块内，解析时必须忽略，不下载、不调用视觉模型 |

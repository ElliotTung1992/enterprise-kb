# 运维手册

本文用于回放 Markdown 图片 RAG 的标准路径：图片必须先上传到 MinIO，Markdown 中只保留完整 MinIO URL。

![系统架构](http://localhost:9000/kb-assets/md-assets/replay/architecture.png "系统架构图")

系统架构图后续说明：API 服务连接 PostgreSQL、Milvus 和 Redis。

## 监控面板

![监控面板](http://localhost:9000/kb-assets/md-assets/replay/dashboard.png)

监控面板用于观察 QPS、错误率和延迟。

## 代码示例

代码块中的图片语法不能被当作真实图片处理：

```md
![忽略图片](http://localhost:9000/kb-assets/md-assets/replay/ignored.png)
```

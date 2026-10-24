# Concept B — 云端真构建服务（Phase 2 候选）

**描述**：用户提供源码（HTML/Vue 包/PHP/URL），上传到云端构建服务；服务在沙箱中执行 `npm build` / PHP 处理 / Gradle 打包，产出签名 APK 供下载。

- **Pros**：真能处理 Vue/PHP；产出是标准原生 APK，可上架；能力完整。
- **Cons**：需要后端基建、CI、沙箱隔离、密钥管理；有成本与隐私考量；MVP 阶段过重，验证价值假设慢。
- **预计工作量**：高（后端 + 构建流水线 + 安全）。
- **作为 Phase 2**，sprint-1 仅先定义 API 契约。

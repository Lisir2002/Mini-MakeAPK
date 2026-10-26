# Version Log

本目录存放 MMA 项目的版本日志。

## 目录结构

```
Version Log/
├── CHANGELOG.md                  # 版本日志总表（所有版本汇总，倒序排列）
└── MMA v-Logs/                   # 各版本独立日志
    ├── MMA 0.0.0.1-rc1 Version Log.md
    ├── MMA 0.0.0.1-rc2 Version Log.md
    └── ...
```

## 命名规范

- **独立版本文档**：`MMA {版本号} Version Log.md`，版本号用原格式（不补零），如 `MMA 0.0.0.1-rc1 Version Log.md`
- **发布 tag**：`v{版本号}`，如 `v0.0.0.1-rc1`、`v0.0.0.1`
- **安装包**：`MMA-v{版本号}-{变体}.apk`，变体为 `rcN` 或 `release`，如 `MMA-v0.0.0.1-rc1.apk`、`MMA-v0.0.0.1-release.apk`
- **Release 标题**：`MMA {版本号} — {更新内容概括}`，概括 ≤20 字、用户语言

## 格式规范

**所有版本日志的格式规范统一在 [`docs/RELEASE_NOTES.md`](../RELEASE_NOTES.md) 中定义**，包括：

- 文档结构与分类顺序（简介 → 新功能 → 改进 → 移除 → 修复 → 安全 → 已知问题）
- 条目格式（4-9 字小标题 + 20-40 字说明）
- 禁止 emoji
- 仅记录用户可感知的应用程序变更
- 内部重构笼统描述
- 用户可感知边界判定

本目录下所有文档（`CHANGELOG.md`、各独立版本文档）以及 GitHub Release 正文必须严格遵循 `docs/RELEASE_NOTES.md` 的格式规范，保持三处一致。

## 发版流程

1. 代码变更完成并通过本地验证（`testDebugUnitTest` + `verifyPaparazziDebug`）
2. 更新 `CHANGELOG.md`，新增版本条目（遵循格式规范）
3. 在 `MMA v-Logs/` 目录下创建独立版本日志文档（内容与 CHANGELOG 条目一致）
4. commit 并 push 到 `main`
5. 打 tag（`v{版本号}`）推送，触发 CI 构建（版本号规则见 [`docs/VERSIONING.md`](../VERSIONING.md)）
6. CI 校验 CHANGELOG 一致性 → 构建签名 APK → 按规范命名 → 创建 Release 并上传
7. 人工完善 Release 标题（补 ≤20 字概括）与正文（与 CHANGELOG 一致）
8. 运行 `python3 scripts/gitops/check-release-format.py --tag {tag}` 校验格式
9. 校验通过后，发版完成

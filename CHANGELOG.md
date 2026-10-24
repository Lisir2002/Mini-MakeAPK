# Changelog

> 状态：已生效（2026-10-25 由 QA 评审）— 遵循 Keep a Changelog，受 `docs/DOC_CHARTER.md` 约束
> **版本号规则见 [`docs/VERSIONING.md`](docs/VERSIONING.md)：四段式 `MAJOR.MINOR.PATCH.BUILD+构建号`（当前 `0.0.0.0+1`）。**

本项目所有重要变更记录于此。
格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)。

## [Unreleased]

### Added
- 文档矩阵：建立 `docs/DOC_CHARTER.md` 文档宪章与 `docs/INDEX.md` 文档地图
- Agent 协同协议：`docs/AGENT_COLLABORATION.md` 定义角色 RACI 与交接契约
- 质量闸门：`docs/qa/sprint-1-checklist.md` 合并前检查项（设计一致性 / 安卓实现 / 可访问性 WCAG 2.2 AA / 构建契约 / 文档链接健康 / 反模式核查）
- 可访问性技能：`accessibility`（WCAG 2.2 AA + 移动 TalkBack）安装至 `~/.codebuddy/skills/` 并注入项目约束，覆盖对比度红线、48dp 触控目标、动效降级、语义化
- **Android Gradle 工程基座**：补齐 `settings.gradle.kts` / `build.gradle.kts` / `gradle/libs.versions.toml` / `gradle-wrapper` / `app/AndroidManifest.xml`，使 Compose 源码从「仅可评审」升级为**可编译验证**（闭环 sprint-1-checklist §2.6/§3 的待复测项）
- **截图回归测试（Paparazzi 1.3.4，纯 JVM）**：覆盖首屏（亮/暗/已填 URL）、可爱组件（按钮/卡片/脉冲）、WebView 外壳，golden 图片随源码提交 `app/src/test/snapshots/`
- **截图回归技能**：`compose-screenshot-testing` 安装至 `~/.codebuddy/skills/` 并注入项目设计约束（珊瑚粉/圆角/圆体），编码 Paparazzi 优先、Roborazzi 备选的工作流与模板
- 截图回归指南：`docs/qa/screenshot-testing.md`（选型 / 运行 / 基线管理 / CI / 排错）
- **数据层（SQLite）**：`Room 2.6.1 + SQLCipher 4.13.0` 加密数据库落地——领域模型/实体/类型转换器/DAO/`AppDatabase`/仓储分层，口令经 Android Keystore 加密存储（不落明文），含索引、WAL、PRAGMA 调优、单事务批量写、Flow 观察、Paging3 分页
- 数据层设计文档：`docs/data-layer.md`（选型实证 / 分层 / 加密方案 / 性能清单 / 迁移策略 / 排错）
- 数据层单元测试：`BuildRecordMapperTest`（6 用例，实体⇄领域无损往返、枚举转换、未知值回退）
- **应用身份敲定**：应用名 **MMA**，包名 **`com.make.apk`**（`namespace` + `applicationId`），Gradle 项目名 `MMA`
- **版本规范**：采用四段式 `MAJOR.MINOR.PATCH.BUILD` + 构建号 `+N`（当前 **`0.0.0.0+1`**），新增 `docs/VERSIONING.md` 定义各段递增规则与发布 tag 格式 `v0.0.0.0+1`
- **发版约束：本地不做编译成品** —— APK 只在 GitHub 打 `v*` tag 时由 `.github/workflows/release.yml` 构建并发布 Release；本地**仅做验证**（`testDebugUnitTest` / `verifyPaparazziDebug`）
- **日志层（全类别）**：自研 `MmaLogger` 门面（零第三方依赖）+ 结构化事件 + 字段全量化；九大类别覆盖崩溃 / 错误 / **操作审计** / 启动 / 性能 / 网络 / 生命周期 / 调试 / 安全审计
- 日志脱敏：`Sanitizer` 在门面层**强制**执行（敏感键、个人信息键、完整 URL、邮箱、`key=value` 凭据、超长截断）
- 日志载体分级：高价值结构化 → 加密 Room（随库受 SQLCipher 保护）；高频诊断 → 轮转文件（2MB×5，有界队列异步写）；调试 → 仅 Logcat
- 崩溃与 ANR 自研捕获：`UncaughtExceptionHandler`（`attachBaseContext` 最早安装）+ 主线程看门狗，崩溃时同步刷盘
- 启动监控：`StartupTracer` 冷/温/热分型 + 阶段耗时（用 `elapsedRealtime`，不受改时间影响）
- 数据库 v2：新增 `log_entries` 表 + 显式 `Migration(1,2)`，**移除** `fallbackToDestructiveMigration()`
- 日志层单元测试 38 例（脱敏 / 轮转策略 / 限流 / 追踪上下文 / 门面分发 / 落库映射）
- CI 验证闸门：`.github/workflows/verify.yml`（PR / 推送 main 时跑测试 + 截图回归比对，与本地同套闸门）

### Changed
- **包名迁移**：源码包 `com.web2apk.app` → `com.make.apk`（main + test 目录整体迁移）
- **类名对齐**：`Web2ApkApp` → `MmaApp`、`Web2ApkTheme` → `MmaTheme`、`Web2ApkApplication` → `MmaApplication`、`requireWeb2ApkApplication()` → `requireMmaApplication()`
- 随包名迁移：Paparazzi golden 基线文件名（`app/src/test/snapshots/images/`）与 Room schema 目录（`app/schemas/`）同步更新
- 版本号从 `0.1.0` 迁移为四段式 `0.0.0.0`（`versionCode = 1`，tag `v0.0.0.0+1`）
- `docs/DEPLOYMENT.md` 改写：本地只验证不出成品；发版改为「改版本 → 打 tag → GitHub 出成品」

## [0.0.0.0+1] - 2026-10-24

> sprint-1 首个可评审原型：设计系统地基 + 首屏 + WebView 封装屏 + 云端构建契约。
> 版本号已按 `docs/VERSIONING.md` 迁移为四段式（原记为 `0.1.0`）。

### Added
- 设计令牌：OKLCH 色板精确转换为 Compose 颜色，亮/暗双主题（`ui/theme/Color.kt`）
- 主题系统：Material 3 改造为 iOS 风——圆角 16/24/999、圆体字、弹簧动效（`ui/theme/Theme.kt`、`Motion.kt`）
- 可爱组件：胶囊按钮、柔和卡片、打包脉冲动效（`ui/components/CuteComponents.kt`）
- 首屏：单一输入流（URL / HTML / 文件），珊瑚粉主按钮 + 8pt 留白（`ui/screens/HomeScreen.kt`）
- WebView 封装屏：URL/HTML 本地预览与封装入口（`ui/screens/WebViewScreen.kt`）
- 应用根：主题绑定与状态路由（`ui/MmaApp.kt`）
- 团队宪法：`PROJECT_BRIEF.md` 锁定平台、审美、角色、工作流、质量闸门
- 设计语言：`DESIGN_LANGUAGE.md` 固化可爱简约 iOS 风令牌与反模式
- 云端构建契约：`docs/build-api-contract.md` 定义 `POST /build → 轮询 → 下载 APK`
- 本地技能栈：10 个开发 skill 安装至 `~/.codebuddy/skills/` 并注入项目约束（本次新增 `accessibility`）

### Changed
- 构建策略定为**混合路径**：sprint-1 设备端 WebView 即时封装，Phase 2 云端真构建（见 `PROJECT_BRIEF.md` §2）

### Fixed
- 安装期绕过沙箱 DNS 死节点：建立 `github-sandbox-dns-bypass` skill（codeload/DoH 通道）

---

<!-- 发版时：将 [Unreleased] 内容整体晋级为 [X.Y.Z] - YYYY-MM-DD 块，并保留顶部空 [Unreleased] -->

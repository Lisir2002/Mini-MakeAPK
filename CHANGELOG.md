# Changelog

> 状态：已生效（2026-10-24 由 QA 评审）— 遵循 Keep a Changelog + SemVer，受 `docs/DOC_CHARTER.md` 约束

本项目所有重要变更记录于此。
格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，版本遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

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

## [0.1.0] - 2026-10-24

> sprint-1 首个可评审原型：设计系统地基 + 首屏 + WebView 封装屏 + 云端构建契约。

### Added
- 设计令牌：OKLCH 色板精确转换为 Compose 颜色，亮/暗双主题（`ui/theme/Color.kt`）
- 主题系统：Material 3 改造为 iOS 风——圆角 16/24/999、圆体字、弹簧动效（`ui/theme/Theme.kt`、`Motion.kt`）
- 可爱组件：胶囊按钮、柔和卡片、打包脉冲动效（`ui/components/CuteComponents.kt`）
- 首屏：单一输入流（URL / HTML / 文件），珊瑚粉主按钮 + 8pt 留白（`ui/screens/HomeScreen.kt`）
- WebView 封装屏：URL/HTML 本地预览与封装入口（`ui/screens/WebViewScreen.kt`）
- 应用根：主题绑定与状态路由（`ui/Web2ApkApp.kt`）
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

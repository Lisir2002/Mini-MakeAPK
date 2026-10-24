# Sprint 1 — 设计系统地基 + WebView 封装 MVP

> **Sprint Goal**：把锁定的设计语言变成可运行的 Compose 主题地基，并交付首屏（输入源）+ WebView 封装屏幕 + 云端构建 API 契约，验证"网页变 App"的价值假设。
> **Branch**：`feature/sprint-1`（注：本沙箱无 Android SDK/Gradle，源码作为地基交付，待接入 Android Studio 后编译；版本库将在项目初始化时建立）。
> **Estimated effort**：约 1.5–2 天（设计 + 编码 + 评审）。
> **来源**：`PROJECT_BRIEF.md` §1/§3/§6；`docs/brainstorm/05-summary.md`（决策：MVP=设备端 WebView 封装 + 云端构建契约）。

## Prioritized Task List

| # | Task | Owner | Est | Description |
|---|------|-------|-----|-------------|
| 1 | 设计令牌落地 Compose | Nova | 1h | 把 `DESIGN_LANGUAGE.md` §2 的 OKLCH 令牌精确转为 Compose `Color`（亮/暗双主题），输出 `theme/Color.kt` |
| 2 | 主题系统 | Nova | 2h | `Theme.kt`：`ColorScheme` + iOS 风 `Shapes`(16/24/999) + 圆体 `Typography` + `Motion`(spring 回弹) |
| 3 | 首屏 UI | Kira+Milo | 2h | `HomeScreen`：单一输入流（URL/HTML 粘贴优先），珊瑚粉胶囊按钮，8pt 留白 |
| 4 | WebView 封装屏 | Nova | 3h | `WebViewScreen`：输入 URL/HTML → 本地 WebView 预览/出包逻辑（代码级，设备端封装） |
| 5 | 可爱进度/庆祝 | Milo | 1.5h | `BuildingPulse` 等微交互组件（spring 回弹、脉冲），尊重"减少动效" |
| 6 | 云端构建契约 | Sage | 1.5h | `docs/build-api-contract.md`：`POST /build → 轮询 → 下载 APK`（Phase 2 规格） |
| 7 | QA 闸门清单 | Ivy | 1h | `docs/qa/sprint-1-checklist.md`：对照 §10 反模式 + WCAG 2.2 AA + WebView 边界 |

## Work Schedule

### Phase 1: 设计系统地基（任务 1-2）
- `Color.kt`（OKLCH→sRGB hex，已算定）+ `Theme.kt` + `Motion.kt`。
- 检查点：令牌命名与 `DESIGN_LANGUAGE.md` 一一对应。

### Phase 2: 首屏与封装（任务 3-5）
- `HomeScreen`（输入）+ `WebViewScreen`（预览/出包）+ `CuteComponents`（按钮/卡片/脉冲）。
- 检查点：首屏仅单一输入流，符合全年龄段+舒适。

### Phase 3: 契约与闸门（任务 6-7）
- 云端构建 API 契约文档 + QA 清单。
- 检查点：契约可被 Phase 2 后端直接实现；清单覆盖反模式。

## Success Criteria

- [ ] `Color.kt` 亮/暗令牌齐全，值与 OKLCH 来源一致（无凭空发明色值）。
- [ ] `Theme.kt` 圆角 ≥16dp、按钮胶囊、字体圆体回退、动效 spring 回弹。
- [ ] `HomeScreen` 视觉通过 `DESIGN_LANGUAGE.md` §10 反模式审查（无尖角/硬阴影/默认字体）。
- [ ] `WebViewScreen` 对 URL/HTML 输入有可行的设备端封装路径（代码级）。
- [ ] `docs/build-api-contract.md` 定义清晰的构建 API。
- [ ] QA 清单覆盖无障碍（对比度/焦点/屏幕阅读器）与 WebView 边界场景。

## What's NOT in This Sprint

| Feature | Reason |
|---------|--------|
| PHP/Vue 真编译 | 需云端构建服务（Phase 2，本 sprint 仅定契约） |
| 云端构建实现 | 后端基建超 MVP 范围 |
| 商店上架 / 自动签名 | 超出验证阶段 |
| 命令行/日志式高级 UI | 破坏可爱外壳（brainstorm 已否决） |

## Agent Prompt

> Read `PROJECT_BRIEF.md`, then `docs/sprint-1/plan.md`. Execute Sprint 1.
> 设计令牌与主题须对齐 `DESIGN_LANGUAGE.md`；任何 UI 偏离 §10 反模式即打回。
> 本沙箱无法 `gradle build`（无 SDK），源码作为地基交付，待 Android Studio 接入后编译验证。

# Sprint 1 — Progress Tracker

> 若上下文溢出，新会话：
> "Read PROJECT_BRIEF.md and docs/sprint-1/progress.md. Continue from where it left off."

## Task Status

| # | Task | Status | Notes |
|---|------|--------|-------|
| 1 | 设计令牌落地 Compose | ✅ Done | `theme/Color.kt`，OKLCH→sRGB hex 已精确计算 |
| 2 | 主题系统 | ✅ Done | `Theme.kt` + `Motion.kt` |
| 3 | 首屏 UI | ✅ Done | `screens/HomeScreen.kt` |
| 4 | WebView 封装屏 | ✅ Done | `screens/WebViewScreen.kt`（代码级封装） |
| 5 | 可爱进度/庆祝 | ✅ Done | `components/CuteComponents.kt`（胶囊按钮 + 脉冲） |
| 6 | 云端构建契约 | ✅ Done | `docs/build-api-contract.md` |
| 7 | QA 闸门清单 | ⬜ Not started | 待补充 `docs/qa/sprint-1-checklist.md` |

## Bugs Found

| # | Description | Severity | Status | Fix |
|---|-------------|----------|--------|-----|
| - | 本沙箱无 Android SDK/Gradle，无法编译验证 | minor/环境 | open | 接入 Android Studio 后编译 |

## Notes
- 设计令牌为团队共享唯一来源，设计层与安卓层共用，禁止各自发明色值。
- 动效统一走 `Motion.SoftSpring`（stiffness=300f, dampingRatio=0.3f），尊重系统"减少动效"。
- 字体：Nunito/Quicksand 待放入 `res/font` 后由 `FontFamily` 引用，当前回退系统无衬线。

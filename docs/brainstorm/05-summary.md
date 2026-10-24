# Brainstorm Summary

## 决策
1. **构建策略 = 混合路径（Concept C）**：设备端 WebView 即时封装（即时预览/轻量出包）+ 云端真构建（Vue/PHP/正式签名 APK，Phase 2）。
2. **sprint-1 MVP = 设备端 WebView 封装（URL/HTML）+ 云端构建 API 契约文档**。
3. **首屏单一输入流**（URL/HTML 粘贴优先，进阶上传折叠次级），契合全年龄段 + 舒适。

## 关键约束（来自团队宪法，不可偏离）
- 设计语言：`DESIGN_LANGUAGE.md`（珊瑚粉主色 `OKLCH(0.78 0.10 25)`、圆角 16–24dp、圆体、spring 回弹、8pt 栅格）。
- 质量闸门：`PROJECT_BRIEF.md` §9（视觉一致性 + WCAG 2.2 AA + 截图回归 + 动效尊重"减少动效"）。

## 待 sprint-1 落地
- 设计令牌 OKLCH → Compose `ColorScheme`/`Theme`（地基）。
- 首屏（输入源）+ "打包中"可爱进度 + 成果庆祝。
- WebView 封装屏幕（URL/HTML → 本地预览/出包）。
- 云端构建 API 契约（`POST /build` → 轮询 → 下载 APK）作为 Phase 2 规格。
- QA 测试矩阵（WebView 边界 + 无障碍）。

## 明确不做（sprint-1）
- PHP/Vue 真编译、云端构建实现、商店上架、自动签名发布、命令行/日志式高级 UI。

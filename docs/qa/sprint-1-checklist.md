> 状态：生效中（与 `../DOC_CHARTER.md` §质量门禁、§6 版本规范保持一致；合并前必须通过）
> 适用范围：Sprint-1 全部产出（设计系统源码 + 首屏 + WebView 封装屏 + 文档矩阵）的合并前质量闸门
> 唯一事实来源：`../../PROJECT_BRIEF.md` · `../../DESIGN_LANGUAGE.md` · `../DOC_CHARTER.md`

# Sprint-1 质量闸门清单（Quality Gate）

本清单是 Sprint-1 代码/文档**合并前必须通过**的检查项。任一项为「阻塞」(Blocker) 且未通过，不得合并。
判定口径沿用 `../DOC_CHARTER.md`：链接健康 100%、示例代码可运行、对照 §10 反模式零命中。

## 0. 使用方式

- **执行**：由 QA（或 Agent 协同中的 QA 角色）逐项勾选，记录证据（文件/行号/截图）。
- **严重级**：🔴 阻塞（必须修复才能合并）｜🟡 重要（强烈建议，可开后置 issue）｜🟢 建议（可后续优化）。
- **结论**：全部 🔴 通过 + 🟡 有跟踪 issue → 可合并；否则打回 Sprint-1。

---

## 1. 设计一致性（对照 `../../DESIGN_LANGUAGE.md`）

| # | 检查项 | 严重 | 证据 / 说明 |
|---|--------|------|-------------|
| 1.1 | `theme/Color.kt` 的亮/暗令牌来源为 OKLCH，且与 `DESIGN_LANGUAGE.md` §2 取值一致（`primary` 亮 `#FFF19E97` / 暗 `#FFFFABA3` 由 `OKLCH(0.78 0.10 25)` 转换） | 🔴 | 比对 `app/.../ui/theme/Color.kt` 与 `DESIGN_LANGUAGE.md` §2 |
| 1.2 | 单一主色原则：全界面仅一个主色（珊瑚粉），语义色（红/黄/绿）仅在错误/警告处出现，未扩散为装饰色 | 🔴 | 搜索 `Color.kt` 中 `Color(` 硬编码，确认无额外品牌色 |
| 1.3 | 形状：卡片圆角 16–24dp、按钮胶囊 999dp、无尖角（<4dp） | 🔴 | 查 `Theme.kt` 的 `RoundedCornerShape` 与 `CuteComponents.kt` |
| 1.4 | 圆体字族：标题字重 600（Medium/SemiBold），拒绝默认 Roboto | 🟡 | 查 `Theme.kt` 的 `Typography` 字体族定义 |
| 1.5 | 间距走 8pt 栅格：组件内边距/间距均为 4 的倍数（4/8/12/16/24/32） | 🟡 | 查各 Screen 的 `padding`/`Arrangement` 取值 |
| 1.6 | 阴影柔和扩散（`elevation-1/2`，大模糊低透明），非硬投影 | 🟢 | 查 `Theme.kt`/`CuteComponents.kt` 的 `shadow`/`elevation` |
| 1.7 | 设计规范自洽：`DESIGN_LANGUAGE.md` 与已装 10 个 skill 顶部「本项目设计约束」指向同一令牌，无各自发明 | 🔴 | 抽查 `~/.codebuddy/skills/*/SKILL.md` 约束块 |

---

## 2. 安卓实现（Compose / M3 改造）

| # | 检查项 | 严重 | 证据 / 说明 |
|---|--------|------|-------------|
| 2.1 | 纯 Jetpack Compose + Material 3（改造风），未混入 XML 布局或 View 系统 | 🟡 | 查 `app/` 下无 `res/layout/*.xml` |
| 2.2 | `ColorScheme` 由 OKLCH 令牌构建，`lightScheme`/`darkScheme` 成对定义 | 🔴 | 查 `Theme.kt` 的 `lightColorScheme(...)` |
| 2.3 | 主题通过 `CompositionLocalProvider` + `MaterialTheme` 注入，应用根 `Web2ApkApp.kt` 绑定深浅色 | 🔴 | 查 `Web2ApkApp.kt` |
| 2.4 | 动效规格符合 `Motion.kt`：`spring(stiffness≈300, dampingRatio≈0.3)` 带轻微回弹，按压 `scale 1→0.96`，时长 200–400ms | 🟡 | 查 `Motion.kt` 与组件调用 |
| 2.5 | 首屏 `HomeScreen` 采用「单一输入流」（URL/HTML/本地文件统一入口），不暴露三种割裂输入 | 🔴 | 对照 `../brainstorm/05-summary.md` 决策 C |
| 2.6 | `WebViewScreen` 对 URL/HTML 做本地预览与封装，代码示例可编译（语法、导入、类型正确） | 🟡 | 本沙箱无 Android SDK，做静态评审；标注"待 `gradle build` 验证" |

---

## 3. 可访问性（对照 `~/.codebuddy/skills/accessibility` · WCAG 2.2 AA）

| # | 检查项 | 严重 | 证据 / 说明 |
|---|--------|------|-------------|
| 3.1 | 对比度：正文文字 ≥4.5:1、大文本/UI 组件 ≥3:1（用 `references/check-contrast` 思路或在线工具核对 `Color.kt` 色对） | 🔴 | 重点核对浅色珊瑚粉 `primary` 作为底色时其上的 `onPrimary` 明度差 |
| 3.2 | 触控目标：所有可交互元素最小 48×48dp（胶囊按钮高度达标、底部导航项命中区达标） | 🔴 | 查 `CuteComponents.kt` 按钮 `Modifier.height`/`clickable` 命中区 |
| 3.3 | 语义化：`contentDescription` 仅对装饰性元素留空；功能图标/按钮均有可读描述 | 🟡 | 查 `HomeScreen`/`WebViewScreen`/`CuteComponents` 的 `contentDescription` |
| 3.4 | 动效降级：系统开启「减少动态效果」时，spring 回弹切换为瞬态/短淡入（尊重 `prefers-reduced-motion` 等价设置） | 🟡 | 在 `Motion.kt` 提供 `reducedMotion` 分支或读取 `isSystemInDarkTheme` 同类系统设置 |
| 3.5 | 焦点可见：未为美观移除焦点轮廓；自定义焦点态清晰 | 🔴 | 查是否 `indication = null` 且无替代焦点样式 |
| 3.6 | WebView 封装内容无障碍透传：网页层缩放、焦点、对比、替代文本可达（app 外壳与网页层均不应阻断 TalkBack） | 🟡 | 对照 `accessibility` skill `references/details.md` 做透传核查 |
| 3.7 | 不以颜色作为唯一错误提示（错误态同时提供图标/文字） | 🔴 | 查输入校验错误展示 |

---

## 4. 构建契约（对照 `../build-api-contract.md`）

| # | 检查项 | 严重 | 证据 / 说明 |
|---|--------|------|-------------|
| 4.1 | Sprint-1 仅落地**设备端 WebView 即时封装**路径，未伪造云端构建结果 | 🔴 | 对照 `../brainstorm/05-summary.md` 决策 A（混合路径） |
| 4.2 | 若涉及云端请求，严格遵循 `build-api-contract.md`：`POST /build → 轮询 status → 下载 apk` 契约字段 | 🟡 | Sprint-1 为占位，标记"Phase 2 实现" |
| 4.3 | 错误态：构建/封装失败有用户可读提示，无裸异常崩溃 | 🟡 | 查 `WebViewScreen` 错误处理 |

---

## 5. 文档矩阵与链接健康（对照 `../DOC_CHARTER.md`）

| # | 检查项 | 严重 | 证据 / 说明 |
|---|--------|------|-------------|
| 5.1 | 仓内 `.md` 相对链接 100% 有效（无失效 `../` 或漏写层级） | 🔴 | 运行链接校验脚本（见 §6） |
| 5.2 | 每份文档顶部含 `> 状态:` 标记并声明遵循宪章条款 | 🟡 | 抽查根文档与 `docs/` 文档 |
| 5.3 | 本次新增/变更已在 `../../CHANGELOG.md` 的 `[Unreleased]` 记录（遵循 Keep a Changelog + Conventional Commits） | 🔴 | 查 `CHANGELOG.md` |
| 5.4 | `../../PROJECT_BRIEF.md` §7 已同步记录 Sprint-1 进度与文档矩阵状态 | 🟡 | 查 §7 |
| 5.5 | 代码注释/示例中无遗留 TODO 噪音；若有占位，统一标注「Phase 2」 | 🟢 | 搜 `TODO`/`FIXME` |

---

## 6. 反模式核查（对照 `../../PROJECT_BRIEF.md` §10）

逐项确认**未命中**以下团队协作反模式（命中即打回）：

- [ ] ❌ 跳过设计语言直接写码（色值/圆角/字体自创）
- [ ] ❌ 各 skill 各自发明令牌（未引用 `DESIGN_LANGUAGE.md`）
- [ ] ❌ 无 QA 直接合并（未走本清单）
- [ ] ❌ 为美观牺牲可访问性（移除焦点轮廓 / 纯色差报错 / 假设用户均目视正常）
- [ ] ❌ 文档与代码脱节（CHANGELOG 未更新 / 链接失效）
- [ ] ❌ 在 Sprint-1 伪造云端构建能力（越界 Phase 2）

---

## 7. 合并判定

| 角色 | 结论 | 签字 / 日期 |
|------|------|-------------|
| 安卓工程师 | ☐ 通过 ☐ 打回 | |
| 视觉总监 | ☐ 通过 ☐ 打回 | |
| QA | ☐ 通过 ☐ 打回 | |
| Producer | ☐ 批准合并 | |

**合并前置条件**：§1/§2/§3/§4 全部 🔴 通过，§5.1/§5.3 通过，§6 零命中。

> 注：Android SDK（compileSdk 34）已接入，`sprint-1` 的 Compose 源码已可 `gradle build` 验证；§2.6 的"可编译验证"与截图回归（Paparazzi）已由 `app/src/test/...` 实跑生成 golden 基线闭环。环境提示：本仓库根已补齐 Gradle 工程（`settings.gradle.kts` / `app/build.gradle.kts` / `gradle-wrapper`），运行 `./gradlew testDebugUnitTest` 即可复跑视觉回归。

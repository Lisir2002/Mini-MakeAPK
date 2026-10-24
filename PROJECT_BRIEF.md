# PROJECT_BRIEF（团队共享宪法）

> **本文件是跨会话、跨技能的唯一事实来源（single source of truth）。**
> 任何技能（`ai-team-orchestration` / `frontend-design` / `claude-android-ninja` / `oklch-tokens` / `accessibility`）产出的设计与代码，都必须对齐本文件，尤其是 §3 设计语言与 §2 平台约束。
> 冷启动提示：先读 `DESIGN_LANGUAGE.md`，再读本文件，然后从"下一步"继续。

---

## 1. 产品概述

- **是什么**：一款**纯原生安卓 App**，把"网页 / 前端代码"一键打包成可安装的 Android APK。输入来源支持：① 粘贴或上传 HTML/CSS/JS；② 上传 Vue 等前端工程（zip 包）；③ 输入网址 URL（WebView 封装）；④（可选）PHP 项目（经云端构建或 URL 形式接入）。输出：可直接安装到手机的 `.apk` 文件。
- **目标用户**：**全年龄段、低技术门槛**。既服务想快速把网页/原型封装成 App 的开发者，也服务不会写代码、但希望把自己的网页/内容变成 App 的普通用户。UX 必须极简、友好、零恐吓感。
- **核心价值**：把"网页变 App"的门槛降到最低——过程**舒适**、可视、有正反馈（可爱进度提示与成果庆祝），让任何人都能在手机上完成打包。
- **非目标 / 显式排除**：不做通用 IDE 或完整编程环境；不生成 iOS/IPA；不内置重型编译器（构建走云端服务或轻量模板封装 WebView）；不替代 Android Studio 等专业工具的全部能力。

> 注：软件形态（纯安卓 Compose）与**设计语言已锁定**（可爱简约·iOS·色彩单调），产品定义在此基础上展开。构建策略（云端构建服务 vs 设备端轻量封装）为关键架构议题，交由团队 brainstorm（§6）确认。

---

## 2. 平台与技术栈

| 项 | 决策 |
|----|------|
| 平台 | **纯原生安卓（不跨端、不 Web）** |
| UI 框架 | **Jetpack Compose**（声明式） |
| 设计系统 | **Material 3**（按 `DESIGN_LANGUAGE.md` 改造） |
| 语言 | Kotlin |
| 架构 | **混合路径**（sprint-1=设备端 WebView 封装即时出包；Phase 2=云端构建服务出正式签名 APK），见 `docs/brainstorm/05-summary.md`。App 层推荐 MVVM + 单向数据流 |
| 设计令牌 | OKLCH 色板 + 8pt 栅格（见 §3 / `DESIGN_LANGUAGE.md`） |

---

## 3. 设计语言（已锁定，不可擅自改动）

- **完整规范**：见 [`DESIGN_LANGUAGE.md`](./DESIGN_LANGUAGE.md)
- **一句话定位**：可爱简约 · iOS 风 · 色彩单调但温暖可爱。
- **三铁律**：①色彩单调（单主色珊瑚粉 + 暖中性）；②圆润优先（大圆角+柔阴影+弹性动效）；③留白即设计。
- **主色**：`--primary: OKLCH(0.78 0.10 25)`（柔和珊瑚粉，低彩度）。
- **字体**：圆体优先（Nunito/Quicksand/Baloo 2 + 思源柔黑/站酷快乐体）。
- **圆角**：卡片 16–24dp，按钮胶囊 999dp。
- **动效**：弹簧 + 轻微回弹，200–400ms；按压 scale 0.96。

> 任何 UI 产出若偏离以上，视为不合格，需打回。

---

## 4. 设计令牌来源

- **规范**：`DESIGN_LANGUAGE.md` §2（OKLCH 令牌表）。
- **实现**：待安装 `oklch-tokens` skill 后，将令牌固化为 Compose `ColorScheme` + `Theme` 对象，并在 `theme/` 包下生成。
- **单一来源**：设计层（`frontend-design`）与安卓层（`claude-android-ninja`）**共用同一份令牌**，禁止各自发明色值。

---

## 5. 团队角色（适配自 ai-team-orchestration）

| 角色 | 职责 | 对应技能/产出 |
|------|------|--------------|
| **Producer（制作人）** | 定范围、拆 sprint、协调、验收合并；**不写代码** | 维护本 `PROJECT_BRIEF`，派活 |
| **产品设计师** | 信息架构、用户流、功能设计 | `frontend-design`（方向）、`ui-ux-design`（8pt/审查清单） |
| **视觉总监** | 视觉识别、动效、设计系统、可爱质感 | `DESIGN_LANGUAGE.md` 守护者 |
| **安卓工程师** | Compose 实现、M3 主题、自适应布局 | `claude-android-ninja` |
| **QA** | 行为测试、无障碍核验、回归 | `accessibility` + 截图回归 |

> 小任务可一人多角；风险高时独立 QA。人类为最终决策者（message bus）。

---

## 6. 开发工作流

1. **Bootstrap**：读 `DESIGN_LANGUAGE.md` + 本文件 → 补全 §1 产品概述。
2. **Brainstorm**（产品/架构决策时）：多角色各自发声，**至少 2 处真实分歧**防群体思维。
3. **Sprint 计划**：`docs/sprint-N/plan.md`（任务+验收标准）+ `progress.md`（实时追踪）。
4. **执行**：`claude-android-ninja` 按 `DESIGN_LANGUAGE.md` 实现，最小完整方案。
5. **QA 闸门**：`accessibility`（WCAG 2.2 AA）+ 截图回归，写 `docs/qa/sprint-N-signoff.md`。
6. **合并**：Producer 验收后合并，更新本文件 §7。

---

## 7. 当前状态（每次 sprint 重写）

- **阶段**：地基搭建完成；产品方向已定（**Web2APK**：纯安卓 App，把网页/前端代码 HTML/PHP/Vue/URL 一键生成 APK）；sprint-1 设计系统地基 + 首屏 + WebView 封装已落地，且**已从"仅可评审源码"升级为可编译验证**——已接入 Android SDK（compileSdk 34）并跑通 Paparazzi 截图回归（生成 golden 基线）
- **已装技能（11 个，均位于 `~/.codebuddy/skills/`，并已注入本宪法约束）**：
  - 网络底座：`github-sandbox-dns-bypass`（沙箱 DNS 死节点时强连 GitHub）
  - 团队地基：`ai-team-orchestration`（角色分工 + PROJECT_BRIEF/sprint/brainstorm 模板）
  - UI 设计：`frontend-design`、`ui-ux-design`（8pt 栅格/审查清单）
  - 安卓布局：`claude-android-ninja`（Compose / M3 / 主题 / 无障碍 / CI 全套）
  - 设计令牌：`oklch-tokens`、`monochrome-palette`、`mobile-first-layouts`（OKLCH 色板 / 单色板 / 移动栅格）
  - 主题：`theme-factory`（预设/自定义主题）
  - 质量/可访问性：`accessibility`（WCAG 2.2 AA + 移动 TalkBack，含 `references/mobile-accessibility.md`），已对齐本宪法对比度/触控目标/动效降级/语义化红线
  - 质量/截图回归：`compose-screenshot-testing`（Paparazzi 优先、Roborazzi 备选的 Compose 截图回归工作流，锁死视觉规范不漂移）
- **约束注入完成**：每个 `SKILL.md` 顶部已加「项目设计约束」区块（网络工具为「本项目上下文」），强制对齐 `/workspace/DESIGN_LANGUAGE.md` §2–§10 与 §3 锁定基调（可爱简约·iOS·色彩单调），避免各 skill 各自发明色值/风格。
- **文档矩阵已立（2026-10-24）**：建立 `docs/DOC_CHARTER.md` **文档宪章**（所有文档的总纲：矩阵/命名/格式/SemVer/Conventional Commits/Agent 协同/质量门禁），并补全 `README.md`、`CHANGELOG.md`、`CONTRIBUTING.md`、`docs/INDEX.md`、`docs/ARCHITECTURE.md`、`docs/AGENT_COLLABORATION.md`、`docs/CODE_STYLE.md`、`docs/SETUP.md`、`docs/DEPLOYMENT.md`，以及 **`docs/qa/sprint-1-checklist.md`（Sprint-1 合并前质量闸门）**——全部联网学习最佳实践（README 五问、Keep a Changelog、SemVer、Conventional Commits、Agent 交接契约、Docs-as-Code、WCAG 2.2 AA）后生成，且对齐本宪法与 `DESIGN_LANGUAGE.md`。导航见 `docs/INDEX.md`。
- **待补**：云端构建服务实现（Phase 2，契约见 `docs/build-api-contract.md`）。
- **下一步**：① 用 `ai-team-orchestration` 推进 Phase 2（云端构建落地）；② 把截图回归接入 CI（见 `docs/qa/screenshot-testing.md` §7），让 `verifyPaparazziDebug` 成为合并前强制闸门；③ 随新界面（构建进度屏/错误态/结果庆祝屏）持续补 Paparazzi 测试与 baseline。

---

## 8. 跨会话交接协议

- 长会话结束前：更新 §7 当前状态 + `docs/sprint-N/progress.md`。
- 冷启动：`读 DESIGN_LANGUAGE.md → 读 PROJECT_BRIEF.md → 读 docs/sprint-N/progress.md → 从 next action 继续`。
- Bug/决策：记入 GitHub Issues（单一事实来源），不在仅聊天中留存。

---

## 9. 质量闸门（上线前必过）

- **视觉一致性**：对照 `DESIGN_LANGUAGE.md` 反模式清单（§10）逐项核验。
- **无障碍**：WCAG 2.2 AA；主色按钮对比度、焦点态、屏幕阅读器标签。
- **截图回归**：关键页面截图比对，防止令牌/间距漂移。
- **动效**：弹簧回弹幅度小、不卡顿、尊重"减少动效"系统设置。

---

## 10. 反模式（待从 ai-team-orchestration 补全 19 条）

- 设计侧反模式见 `DESIGN_LANGUAGE.md` §10（多色拼贴、尖角、硬阴影、默认字体、生硬过渡、信息过密、主色铺满）。
- 团队协作反模式（如：跳过设计语言直接写码、各技能各自发明令牌、无 QA 直接合并）—— 已随 `ai-team-orchestration`（含 `references/anti-patterns.md`、`brainstorm-format.md`、`project-brief-template.md`、`sprint-plan-template.md`）安装到位，可随时引用。

---

*本文件随技能安装与产品定义推进持续更新。任何修改需经 Producer 确认。*

# GitHub 上可用于「新软件设计」的 Skill 清单

> 目标：为从零重新设计一款软件，筛选 GitHub 上成熟的 Agent Skill，覆盖 **UI/UX 美观设计、安卓布局合理化、样式令牌（design tokens）规范、开发软件的 agent 团队编排** 四大方向。
> 说明：下列 skill 多为 Anthropic Claude / GitHub Copilot 格式（`SKILL.md` + `references/` + `scripts/`），WorkBuddy 的 skill 目录结构与之兼容，可直接借鉴或适配到 `~/.codebuddy/skills/`。

---

## 一、总览（推荐 11 款，分 4 类）

| 方向 | 推荐 Skill | 仓库 | 一句话用途 |
|------|-----------|------|-----------|
| UI/UX 美观 | `frontend-design` | anthropics/skills | 官方反"AI 味"前端美学 skill |
| UI/UX 美观 | `ui-ux-design` | hicay/claude-code-skills | 基于 Dieter Rams 原则的设计系统+审查清单 |
| 安卓布局 | `awesome-android-agent-skills` | new-silvermoon | 最全安卓 Agent Skill 集合（Compose/导航/无障碍） |
| 安卓布局 | `material-design-3-ui-skill` | skydashnet | Material 3 / M3 Expressive / 自适应布局 |
| 安卓布局 | `claude-android-ninja` | Drjacky | Kotlin+Compose 生产级安卓全流程 |
| 样式令牌 | `oklch-tokens` | aidenlive/SKILLS | OKLCH 感知均匀色板 + 亮暗主题 |
| 样式令牌 | `ui-ux-design` 的 design-tokens | hicay/claude-code-skills | 网格/颜色/排版/圆角/时序 token（含多端代码） |
| 样式令牌 | `theme-factory` | Jupdefi/Claud-skills | 10 套预设主题 / 自定义主题生成 |
| Agent 团队 | `ai-team-orchestration`（详细版） | OpCyb3r | 多角色开发团队（产品/视觉/前后端/QA） |
| Agent 团队 | `ai-team-orchestration`（精简版） | Wicked-Sick-Ltd | Producer/Dev/QA 轻量编排 |
| Agent 团队 | 中文多 Agent 工作流 | xiaobo1997/indie-developer-playbook | 「一个人=一个团队」中文编排方法 |

---

## 二、UI/UX 美观设计

### 1. `frontend-design`（Anthropic 官方）
- **仓库**：https://github.com/anthropics/skills （路径 `skills/frontend-design`）
- **核心**：强制在写任何 UI 前先确定**明确美学方向**，禁用 Inter/Roboto 等默认字体，避免千篇一律的"AI 生成感"（紫色渐变、玻璃拟态卡片、过度圆角等）。
- **适用**：落地页、仪表盘、React 组件、应用界面、UI 美化。
- **特点**：要求输出有"产品观点"的真实界面（含空/加载/错误态、表格、筛选、响应式），而非占位海报。
- **为什么选**：官方出品、安装量最大、最擅长"让软件看起来不像 AI 做的"。

### 2. `ui-ux-design`（基于 Dieter Rams 10 原则）
- **仓库**：https://github.com/hicay/claude-code-skills
- **核心**：用 Dieter Rams「好设计的 10 条原则」做设计与改版；自带**完整设计系统**（8pt 网格、3 级排版、无色系调色板）、组件库（SwiftUI/React/CSS/Tailwind 示例）、**60+ 项审查清单**。
- **适用**：改版界面、"让这个更可读"、建立视觉层级、修复间距。
- **为什么选**：不仅有审美，还带可落地的 token 与组件代码，与设计令牌方向天然衔接。

### 3.（可选集合）`aidenlive/SKILLS` 设计系统
- **仓库**：https://github.com/aidenlive/SKILLS
- 含 `oklch-tokens`、`monochrome-palette`、`mobile-first-layouts` 等多个设计系统 skill，且覆盖前端/后端/全栈/校验，适合想一次性补齐整套规范。

---

## 三、安卓软件布局合理化

### 4. `awesome-android-agent-skills`（最全安卓集合）
- **仓库**：https://github.com/new-silvermoon/awesome-android-agent-skills
- **包含 skill**（位于 `.github/skills/`）：
  - 架构：`android-architecture`（Clean Architecture/模块化/Hilt）、`viewmodel-state`、`data-layer`（Room+Retrofit 离线优先）
  - **UI 与导航**：`compose-ui`（无状态高性能 Composable、状态提升、Modifier 顺序）、`compose-navigation`（类型安全导航/深链/自适应）、`coil-image`、`accessibility`（内容描述/触控目标/对比度审查）
  - 迁移：`xml-to-compose`、`rx-to-coroutines`
  - 性能/测试/构建：`compose-performance-audit`、`gradle-build-logic` 等
- **为什么选**：覆盖安卓布局合理化的方方面面，且 `Agent.md` 作为全局架构约定，保证 agent 始终遵守你的规范。

### 5. `material-design-3-ui-skill`（M3 + 自适应 + 语义 token）
- **仓库**：https://github.com/skydashnet/material-design-3-ui-skill
- **核心**：用 Google Material Design 3 / Material You / M3 Expressive 做**设计、审查、实现**；覆盖语义化 design tokens、自适应窗口尺寸类、无障碍。
- **审查合同**：结构/主题/组件/状态/自适应/无障碍/表现力逐项校验。
- **为什么选**：安卓布局合理化的"规则来源"，且把 tokens 与自适应布局一体化，直接服务第三类需求。

### 6. `claude-android-ninja`（生产级安卓全流程）
- **仓库**：https://github.com/Drjacky/claude-android-ninja
- **核心**：Kotlin+Compose 模块化架构、Navigation3、M3 主题（动态色/排版/8dp 间距 token/暗亮模式）、**自适应 UI**（NavigationSuiteScaffold 等）、无障碍、i18n、Gradle 规范、测试（Roborazzi 截图测试）。
- **为什么选**：单仓库即覆盖"布局合理化 + 令牌 + 无障碍 + 测试"，想少装几个就选它。

---

## 四、样式令牌（Design Tokens）设计规范

### 7. `oklch-tokens`
- **仓库**：https://github.com/aidenlive/SKILLS （路径 `design-system/oklch-tokens`）
- **核心**：OKLCH 感知均匀色板 + 亮/暗主题支持，颜色对比可预测、调色更科学。
- **为什么选**：现代 token 色彩系统的主流方案，避免 HSL/RGB 的"等距不等感"问题。

### 8. `ui-ux-design` 的 `references/design-tokens.md`
- **来自**：https://github.com/hicay/claude-code-skills
- **核心**：网格、颜色、排版、圆角、时序等 token，附 **SwiftUI / CSS / Tailwind / React Native** 多端实现代码。
- **为什么选**：一份文档同时给出规范与多端落地代码，适配跨端软件。

### 9. `theme-factory`
- **仓库**：https://github.com/Jupdefi/Claud-skills
- **核心**：10 套预设专业主题，或按需即时生成自定义主题。
- **为什么选**：快速为新软件确定统一视觉基调，反向产出 token。

> 提示：第 5、6 款的 Material 3 与安卓侧 token 也属此类，若做安卓软件可优先复用它们的语义 token，无需另起炉灶。

---

## 五、开发软件的 Agent 团队 Skill（多 Agent 编排）

### 10. `ai-team-orchestration`（详细版，推荐）
- **仓库**：https://github.com/OpCyb3r/All-About-GitHub-Copilot （`plugins/ai-team-orchestration/skills/...`）
- **角色阵容**（人类作为消息总线串联并行 chat）：
  - `@ai-team-producer`（Remy）：规划、协调、合并，**不写代码**
  - **Product Designer（Kira）**：UX/用户流/趣味
  - **Visual/Art Director（Milo）**：CSS/动效/视觉识别/设计系统/无障碍
  - **Frontend（Nova）**：UI 框架/状态/组件
  - **Backend（Sage）**：API/DB/鉴权/安全
  - **DevOps（Dash）**、**QA（Ivy）**：CI/CD、E2E 与签署
- **流程**：`PROJECT_BRIEF.md` 单一事实源 → Brainstorm（要求 ≥2 个真实分歧防群体思维）→ Sprint 计划 → 执行 → QA 签署 → 上下文恢复。
- **为什么选**：角色设置恰好覆盖"软件设计"所需的产品/视觉/前端/后端/QA，**与本次 UI/UX + 安卓 + 令牌需求高度契合**。

### 11. `ai-team-orchestration`（精简版）
- **仓库**：https://github.com/Wicked-Sick-Ltd/awesome-copilot （`skills/ai-team-orchestration/`）
- **角色**：`@ai-team-producer` / `@ai-team-dev` / `@ai-team-qa` 三剑客，轻量、比例化工作流（小改动可跳过规划）。
- **为什么选**：想快速跑通"规划→实现→测试→合并"的团队骨架时用它，更轻。

### 12.（备选）`ecc-team-agent-orchestration`
- **仓库**：https://github.com/Undermybelt/hermes-skills （`skills/community/ecc/...`）
- **核心**：以**工作项（work item）+ 所有权 + Agent Kanban + 合并门禁**做可靠团队协作，强调"每块工作有 owner、有证据、有合并门槛"。
- **为什么选**：偏工程治理，适合多 agent 并行且需要严格 merge gate 的团队。

### 13.（中文友好）「一个人 = 一个团队」多 Agent 工作流
- **仓库**：https://github.com/xiaobo1997/indie-developer-playbook （`docs/11-workflow/workflow.md`）
- **核心**：Hermes（主编）拆任务→派发→Review→合并；角色含前端/后端/DevOps/测试/调试/审查，每个角色固化成 Skill。
- **为什么选**：中文文档、方法论清晰，适合个人独立开发者用多 agent 仿团队。

---

## 六、面向「从零设计一款软件」的推荐组合

| 阶段 | 推荐组合 |
|------|---------|
| 视觉方向 & 审美 | `frontend-design` + `ui-ux-design`（定方向、出审查清单） |
| 安卓布局 | `claude-android-ninja`（一把梭）或 `awesome-android-agent-skills` + `material-design-3-ui-skill`（更细） |
| 令牌规范 | `oklch-tokens` + `ui-ux-design` 的 design-tokens + M3 语义 token |
| 团队协作 | `ai-team-orchestration`（详细版，带视觉/前端/后端/QA 角色） |

---

## 七、安装与适配到 WorkBuddy

大多数 skill 是文件夹（含 `SKILL.md`），安装即复制：

```bash
# 通用方式：克隆后复制目标 skill 到 WorkBuddy 技能目录
git clone --depth 1 <仓库URL> /tmp/sk && \
mkdir -p ~/.codebuddy/skills && \
cp -r /tmp/sk/<skill文件夹> ~/.codebuddy/skills/ && \
rm -rf /tmp/sk
```

- **安卓集合**需复制整个 `.github/skills/` 目录内容，并把 `Agent.md` 放到项目根作为全局约定。
- **适配要点**：WorkBuddy 读取 `~/.codebuddy/skills/` 下的 `SKILL.md`；若原 skill 指向 `.claude/skills/` 或 `.github/skills/`，改路径复制即可。部分 skill 内若有 Copilot/Claude 专属工具名，可按需替换为 WorkBuddy 对应能力。

---

## 八、下一步建议
1. 确认新软件的**形态**（纯安卓 App？跨端？Web？），据此锁定安卓类 skill 的取舍。
2. 决定**是否要我直接下载并适配**上面 11 款 skill 到 `~/.codebuddy/skills/`（可先装核心 4–5 款）。
3. 用 `ai-team-orchestration` 建立 `PROJECT_BRIEF.md`，把"UI 方向 / 安卓布局规范 / 设计令牌"作为团队共享约束，后续设计即在同一套规则下推进。

> 如需，我可以直接帮您 `git clone` 核心 skill 并整理成 WorkBuddy 可用的目录结构。

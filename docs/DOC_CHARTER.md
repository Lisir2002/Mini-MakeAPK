# 文档宪章（Documentation Charter）

> 本文件是 Web2APK 项目的**文档规矩总纲**，是所有文档的"宪法之上的宪法"。
> 任何新增、修改文档都必须先遵守本文，再遵守具体文档类型的约定。
> 本宪章本身受 `PROJECT_BRIEF.md`（团队宪法）与 `DESIGN_LANGUAGE.md`（设计语言）约束，三者冲突时优先级：**PROJECT_BRIEF > DESIGN_LANGUAGE > 本文 > 其他文档**。

---

## 1. 目的（Why this exists）

文档不是代码的附属品，而是**跨会话、跨角色（含 AI Agent）的唯一事实来源（Single Source of Truth）**。

- 人走了、Agent 重启了、上下文丢了，文档能让人/智能体在分钟级重新 onboarding。
- 设计语言与团队决策固化在文档里，避免"每次重来一遍"和"各说各话"。
- 本宪章把"哪些文档必须有、放哪、谁来维护、何时更新、什么格式"一次性钉死。

---

## 2. 核心原则

1. **Docs-as-Code**：文档与代码同仓、同分支、同 PR 评审、同版本。禁止把文档丢在 Wiki/Notion/云盘里（会立刻漂移）。
2. **单一事实来源**：设计令牌只在 `DESIGN_LANGUAGE.md` 定义；产品与架构决策只在 `PROJECT_BRIEF.md` 定义。其他文档**引用**而非**复制**这些事实。
3. **对齐宪法**：所有文档顶部必须声明其遵循 `PROJECT_BRIEF.md` 与 `DESIGN_LANGUAGE.md`，不得自创颜色/风格/产品定位。
4. **相对链接**：仓内引用一律用相对路径（如 `docs/ARCHITECTURE.md`、`CHANGELOG.md`），禁止绝对/裸 URL，保证 clone 后链接不断。
5. **图用 Mermaid**：架构图、流程图、时序图优先用 Mermaid（可版本化、可被渲染），ASCII 仅在 Mermaid 不可用时兜底。
6. **可校验**：每条命令、每段代码片段在并入前必须跑通；文档随代码变更同步更新，纳入 PR 评审。

---

## 3. 文档矩阵（Document Matrix）

> 必含（Required）文档如下。**职责（Owner）** 取自 `PROJECT_BRIEF.md` §4 的团队角色。

| 文档 | 路径 | 类型(Diátaxis) | 职责(Owner) | 受众 | 更新触发条件 |
|------|------|----------------|-------------|------|--------------|
| 项目宪法 | `PROJECT_BRIEF.md` | Explanation | Producer | 全员/Agent | 产品/架构/团队决策变更 |
| 设计语言 | `DESIGN_LANGUAGE.md` | Reference | 视觉总监 | 设计/安卓/QA | 设计令牌/风格变更 |
| 文档宪章 | `docs/DOC_CHARTER.md` | Reference | Producer | 全员/Agent | 文档规矩变更 |
| 文档地图 | `docs/INDEX.md` | Reference | Producer | 全员/Agent | 新增/删除文档 |
| 入口门面 | `README.md` | Tutorial | Producer | 外部用户/贡献者 | 功能/快速开始/状态变更 |
| 版本日志 | `CHANGELOG.md` | Reference | QA | 全员/用户 | 每次发版、每个 checkpoint |
| 贡献指南 | `CONTRIBUTING.md` | How-to | Producer | 人类/Agent 贡献者 | 协作流程/规范变更 |
| 系统架构 | `docs/ARCHITECTURE.md` | Explanation | 安卓工程师 | 工程师/Agent | 模块/数据流/技术选型变更 |
| Agent 协同 | `docs/AGENT_COLLABORATION.md` | How-to | Producer | AI Agent | 角色/交接契约/工作流变更 |
| 代码风格 | `docs/CODE_STYLE.md` | Reference | 安卓工程师/QA | 工程师/Agent | Kotlin/Compose/提交规范变更 |
| 环境搭建 | `docs/SETUP.md` | Tutorial | 安卓工程师 | 新成员/Agent | 工具链/依赖变更 |
| 构建发布 | `docs/DEPLOYMENT.md` | How-to | 安卓工程师 | 工程师/Agent | 构建/签名/发版流程变更 |
| 头脑风暴 | `docs/brainstorm/*` | Reference | 全员 | 决策参与者 | 新架构分歧/决策 |
| 迭代计划 | `docs/sprint-*/` | Reference | Producer | 全员/Agent | 新迭代启动 |
| 构建契约 | `docs/build-api-contract.md` | Reference | 安卓工程师 | 后端/Agent | 云端构建 API 变更 |

> 早期原型阶段（`0.y.z`）可暂缓 `docs/DEPLOYMENT.md` 的"生产发版"细节，但**架构与契约必须先行**。

---

## 4. 文件命名与目录

- **根目录**：`UPPERCASE.md`（`README.md`、`CHANGELOG.md`、`CONTRIBUTING.md`）。
- **`docs/`**：`lowercase-kebab-case.md`（`agent-collaboration.md`、`code-style.md`）。
- 子目录用名词单数：`docs/brainstorm/`、`docs/sprint-1/`、`docs/adr/`。
- 禁止中文文件名、禁止空格、禁止特殊字符。

---

## 5. 格式标准

| 项 | 规范 |
|----|------|
| 语言 | 简体中文（代码注释/标识符可英文，与项目一致） |
| 编码 | UTF-8 |
| 方言 | GitHub Flavored Markdown（GFM） |
| 标题 | `#` 项目名；`##` 主章节；`###` 子节；序号用 1. 2. 3. |
| 代码块 | 必须带语言标识（```kotlin / ```bash / ```json） |
| 图表 | Mermaid 优先（` ```mermaid `） |
| 链接 | 仓内用相对路径；外部链接需验证可达 |
| 表格 | GFM 表格用于对比/参数；长列表用有序列表 |
| 行宽 | ≤120 字符（中文可放宽，但避免超长行） |
| 状态标记 | 文档顶部加 `> 状态: 草稿/评审中/已生效/待弃用` |

---

## 6. 版本与变更规范

### 6.1 语义化版本（SemVer 2.0.0）
- 格式 `MAJOR.MINOR.PATCH`。
- `MAJOR`：不兼容的公开 API / 行为变更（含构建产物格式）。
- `MINOR`：向后兼容的新功能（预 1.0 阶段，新功能 bump MINOR）。
- `PATCH`：向后兼容的缺陷修复。
- 预发布：`1.0.0-alpha`、`1.0.0-alpha.1`；构建元数据：`+exp.sha.xxxx`。

### 6.2 版本日志（Keep a Changelog）
- 文件根目录 `CHANGELOG.md`，首行声明"基于 Keep a Changelog，遵循 SemVer"。
- 顶部 `[Unreleased]` 空区块；版本标题格式 `## [1.2.0] - 2026-03-07`（ISO 8601 日期，最新在上）。
- 六分类（按顺序，空的分类不写、不写 N/A）：
  `Added` / `Changed` / `Deprecated` / `Removed` / `Fixed` / `Security`。
- 每条：一行、过去式、主语 + 具体结果（避免"优化了""修了一些 bug"）。
- **随时写**：每完成一个 checkpoint 立即补 `[Unreleased]`，发版时整体晋级为带日期的版本块。

---

## 7. 提交规范（Conventional Commits 1.0.0）

格式：`<type>[optional scope]: <description>`（如 `feat(webview): 支持 HTML 字符串即时预览`）

| 类型 | 含义 | 版本影响 |
|------|------|----------|
| `feat` | 新功能 | MINOR |
| `fix` | 缺陷修复 | PATCH |
| `docs` | 仅文档 | — |
| `style` | 格式（不影响逻辑） | — |
| `refactor` | 重构（不改行为） | — |
| `perf` | 性能 | — |
| `test` | 测试 | — |
| `build` | 构建系统/依赖 | — |
| `ci` | CI 配置 | — |
| `chore` | 其他杂务 | — |

- 重大变更：在 type 后加 `!` 或 footer 写 `BREAKING CHANGE:`，对应 `MAJOR`。
- 描述用祈使句、≤50 字符；正文与 footer 间空一行；关联用 `Refs: #123`。
- 一个提交只做一件事；文档类提交用 `docs(scope):`。

---

## 8. Agent 协同写作规则

AI Agent 也是本文档矩阵的创作者与读者，须遵守：

1. **交接即契约**：Agent 间传递的不是自由文本，而是结构化工作包——含 `task_id`、`from_agent`、`to_agent`、`status`、`output`、`context`、`next_steps`、`blockers`、`verification`。
2. **角色 RACI**：每个 Agent 明确 Responsible / Accountable / Consulted / Informed（见 `docs/AGENT_COLLABORATION.md`）。
3. **写清"不做什么"**：角色职责文档同时记录该 Agent 的边界，避免职责重叠。
4. **结构化输出模板**：收敛到少量可复用模板——`Plan` / `Decision` / `Checklist` / `Status` / `Postmortem`，保持可扫读。
5. **共享状态**：跨会话的持久事实写入 `PROJECT_BRIEF.md` / `DESIGN_LANGUAGE.md` / `CHANGELOG.md`，而非散落在对话里。
6. **失败可恢复**：每次产出带 `blockers` 与回滚说明；文档变更纳入 PR，可回退。

---

## 9. 质量门禁（Quality Gates）

- **链接健康**：仓内相对链接 100% 可达（PR 前用 link-check 校验）。
- **示例有效**：文档中每条命令、每段代码可跑通（不可跑通的标注"示意"）。
- **新鲜度**：文档状态 ≤90 天未评审需触发复核；架构/API 文档随代码变更同步。
- **PR 评审**：文档变更与代码变更同 PR，至少 1 名角色（Producer 或对应 Owner）评审。
- **对齐校验**：CI/人工检查新文档是否引用了 `PROJECT_BRIEF.md` 与 `DESIGN_LANGUAGE.md`。

---

## 10. 文档状态标记约定

每个文档顶部用 blockquote 标注当前状态，便于 Agent 判断是否可信：

```
> 状态: 已生效（2026-10-24 由 Producer 评审）
> 遵循: PROJECT_BRIEF.md §1、DESIGN_LANGUAGE.md
```

状态枚举：`草稿` / `评审中` / `已生效` / `待弃用`。

---

*本宪章随项目演进由 Producer 维护，任何修改须走 `docs(scope):` 提交并更新 `CHANGELOG.md`。*

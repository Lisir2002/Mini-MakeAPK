# Agent 协同协议（Agent Collaboration）

> 状态：已生效（2026-10-24 由 Producer 评审）
> 遵循：[`docs/DOC_CHARTER.md`](DOC_CHARTER.md) §8 · 对齐：[`PROJECT_BRIEF.md`](../PROJECT_BRIEF.md) §4
> 范式来源：multi-agent handoff-as-contract、RACI、结构化输出模板（见宪章引用文献）

---

## 1. 核心原则

多 Agent 协作的失败几乎都发生在**交接处**（上下文丢失、职责重叠、自由文本歧义）。
本协议把"交接"定义为**契约**，把"角色"定义为**带边界的合同**。

- **交接即契约**：传递的不是自由文本，而是结构化工作包。
- **一个所有者**：每个工作流有唯一 Orchestrator（本仓为 Producer）。
- **写清不做什么**：每个角色同时声明边界，避免重叠。
- **共享状态外置**：跨会话事实写入宪法文档 / `CHANGELOG.md`，不沉淀在对话。
- **失败可恢复**：每次产出带 `blockers` 与回滚说明。

## 2. 团队角色与 RACI

角色定义见 `PROJECT_BRIEF.md` §4。RACI（Responsible 执行 / Accountable 负责 / Consulted 知会 / Informed 被告知）：

| 角色 | 核心职责 | R(执行) | A(负责) | C(咨询) | I(告知) |
|------|----------|---------|---------|---------|---------|
| Producer | 目标/范围/优先级/验收 | 调度、验收 | 最终负责 | 全员 | 用户 |
| 产品设计师 | 信息架构、流程 | 流程设计 | — | 视觉总监 | Producer |
| 视觉总监 | 设计令牌、视觉一致性 | 令牌维护 | 视觉 | 设计师/安卓 | Producer |
| 安卓工程师 | Compose 实现、架构 | 编码 | 技术实现 | 视觉总监 | Producer/QA |
| QA | 质量闸门、回归 | 测试 | 质量 | 安卓 | Producer |

## 3. 交接契约（Handoff Contract）

每个 Agent 把工作交给下一个时，必须产出如下结构（JSON Schema 风格，可读性优先）：

```json
{
  "task_id": "sprint-1-webview-screen",
  "from_agent": "安卓工程师",
  "to_agent": "QA",
  "status": "complete",
  "output": "WebViewScreen.kt 已完成本地预览与封装入口",
  "context": "设计令牌见 DESIGN_LANGUAGE.md；HomeScreen 已交付",
  "next_steps": "按 QA 清单核对 WCAG 2.2 AA 与反模式",
  "blockers": "本沙箱无 Android SDK，无法 gradle build 验证",
  "verification": "代码评审通过，类型与主题引用正确",
  "rollback": "回退至 HomeScreen 提交即可"
}
```

**接收方确认回路**：接收 Agent 须回执确认"收到什么、理解什么、遗漏什么、下一步做什么"，再继续。

## 4. 结构化输出模板（收敛到少量可复用形状）

| 模板 | 用途 | 必备字段 |
|------|------|----------|
| `Plan` | 迭代/任务计划 | 目标、范围、里程碑、风险 |
| `Decision` | 架构/设计决策 | 选项、取舍、结论、反模式 |
| `Checklist` | 验收/QA 闸门 | 条目、通过标准、状态 |
| `Status` | 进度快照 | 完成度、阻塞、下一步 |
| `Postmortem` | 故障/复盘 | 现象、根因、修复、预防 |

要求：可扫读、短段落、显式 bullet、含"假设/约束/风险/下一步"防漂移字段。

## 5. 共享状态与文档映射

| 持久事实 | 落地点 | 谁维护 |
|----------|--------|--------|
| 产品/架构/角色 | `PROJECT_BRIEF.md` | Producer |
| 设计令牌/风格 | `DESIGN_LANGUAGE.md` | 视觉总监 |
| 版本变更 | `CHANGELOG.md` | QA |
| 文档规矩 | `docs/DOC_CHARTER.md` | Producer |
| 迭代进度 | `docs/sprint-*/progress.md` | Producer |
| 架构分歧 | `docs/brainstorm/` | 全员 |

## 6. 可用本地 Skill（协同工具箱）

安装在 `~/.codebuddy/skills/` 的 9 个 skill 是协作工具：

| Skill | 在协同中的角色 |
|-------|----------------|
| `ai-team-orchestration` | Orchestrator：brainstorm / sprint / anti-patterns |
| `frontend-design` + `ui-ux-design` | 视觉/UX 闸门：每次 UI 产出前先定方向 |
| `claude-android-ninja` | 安卓实现参考：Compose/M3/主题/无障碍 |
| `oklch-tokens` + `monochrome-palette` + `mobile-first-layouts` | 设计令牌与布局唯一来源 |
| `theme-factory` | 主题生成 |
| `github-sandbox-dns-bypass` | 网络备用：沙箱 DNS 死节点时强连 GitHub |

每个 skill 顶部已注入「本项目设计约束」，调用即自带团队宪法。

## 7. 失败恢复

- 交接失败：回退到发送方并标注具体缺口，不从头重来。
- 上下文损坏：从 `PROJECT_BRIEF.md` / `CHANGELOG.md` 重建，而非依赖对话记忆。
- 沙箱网络中断：启用 `github-sandbox-dns-bypass` 经 codeload/DoH 通路恢复。

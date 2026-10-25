# 贡献指南（Contributing）

> 状态：已生效（2026-10-24 由 Producer 评审）
> 遵循：[`docs/DOC_CHARTER.md`](docs/DOC_CHARTER.md) · 对齐：[`PROJECT_BRIEF.md`](PROJECT_BRIEF.md) · [`DESIGN_LANGUAGE.md`](DESIGN_LANGUAGE.md)

本项目的贡献者包含**人类开发者**与 **AI Agent**（基于已安装的 9 个本地 skill）。无论谁提交，规则一致。

---

## 1. 环境准备

详细步骤见 [`docs/SETUP.md`](docs/SETUP.md)。要点：

```bash
# 需要：JDK 17+、Android SDK（API 34+）、Android Studio / Gradle
git clone <your-repo-url> Web2APK && cd Web2APK
# 用 Android Studio 打开 /workspace，同步 Gradle，连接设备或模拟器运行 app 模块
```

## 2. 分支策略

- `main`：稳定可构建版本，受保护。
- `feature/<name>` / `fix/<name>`：从 `main` 切出，完成后提 PR 回 `main`。
- 文档变更用 `docs/<name>` 分支。

## 3. 提交规范（Conventional Commits）

每条提交必须形如 `<type>[optional scope]: <description>`，详见 [`docs/CODE_STYLE.md`](docs/CODE_STYLE.md) §提交规范。

```text
feat(webview): 支持 HTML 字符串即时预览
fix(theme): 暗色下卡片描边对比度不足
docs(charter): 补充 Agent 协同写作规则
```

- `feat`→MINOR，`fix`→PATCH，`!` 或 `BREAKING CHANGE:`→MAJOR。
- 描述用祈使句、≤50 字符；一个提交只做一件事。

## 4. 变更日志

每次 checkpoint 完成后，立即向 [`docs/Version Log/CHANGELOG.md`](docs/Version%20Log/CHANGELOG.md) 的 `[Unreleased]` 补一条（条目格式见 [`docs/RELEASE_NOTES.md`](docs/RELEASE_NOTES.md)：用户可感知、过去式、具体结果）。发版时整体晋级。

```markdown
## [Unreleased]
### Added
- 首屏支持粘贴 HTML 字符串即时预览
```

## 5. PR 流程

1. 从 `main` 切分支，完成改动。
2. 确保：命令可跑通、文档随代码同步更新、相对链接可达。
3. 提交遵循 Conventional Commits，并补 `docs/Version Log/CHANGELOG.md`。
4. 提 PR，描述「做了什么 / 为什么 / 风险」。
5. 至少 1 名对应文档 Owner（见 `DOC_CHARTER.md` §3）评审通过后合并。

> **重要**：文档与代码同 PR 评审。任何偏离 `PROJECT_BRIEF.md` / `DESIGN_LANGUAGE.md` 的改动需在 PR 中说明理由。

## 6. AI Agent 贡献者须知

- 开工前读 `PROJECT_BRIEF.md` + `DESIGN_LANGUAGE.md` + 对应文档，不得擅自发明颜色/风格/产品定位。
- 跨会话事实写入上述宪法文档或 `docs/Version Log/CHANGELOG.md`，不沉淀在对话里。
- 多 Agent 交接用结构化工作包（见 [`docs/AGENT_COLLABORATION.md`](docs/AGENT_COLLABORATION.md)）。
- 产出文档必须挂 `> 状态:` 标记并声明遵循的宪法条款。

## 7. 代码风格

Kotlin / Compose 规范见 [`docs/CODE_STYLE.md`](docs/CODE_STYLE.md)：包结构、命名、Composable 约定、设计令牌引用方式。

## 8. 许可

当前为专有/内部项目，贡献即视为同意在现行许可框架下授权使用。开源许可待定。

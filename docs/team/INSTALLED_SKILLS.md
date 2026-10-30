# 已安装的本地开发技能（~/.codebuddy/skills/）

> **共 11 个 skill**，全部通过 `SKILL.md` frontmatter 校验（每个 `SKILL.md` 顶部已注入「本项目设计约束」区块，强制对齐设计语言宪法）。目标：用于后续**纯原生安卓（Jetpack Compose + Material 3）**软件开发。
> 配套设计语言宪法：`/workspace/DESIGN_LANGUAGE.md`（可爱简约·iOS·色彩单调）、`/workspace/PROJECT_BRIEF.md`（跨会话唯一事实来源）。

## 按配套层级分类

| 层级 | Skill | 来源仓库 | 文件数 | 在本项目中的作用 |
|------|-------|---------|-------|----------------|
| 网络底座 | `github-sandbox-dns-bypass` | 自研（基于实战经验） | 4 | 沙箱 DNS 劫持/死节点时强连 GitHub |
| 团队地基 | `ai-team-orchestration` | muhammedadnank/antigravity-skills | 5 | 角色分工 + PROJECT_BRIEF/sprint/brainstorm 模板 |
| UI 设计 | `frontend-design` | anthropics/skills | 2 | 强制审美方向、治"AI 生成感" |
| UI 设计 | `ui-ux-design` | hicay/claude-code-skills | 5 | 8pt 网格、审查清单、设计令牌 |
| 安卓布局 | `claude-android-ninja` | Drjacky/claude-android-ninja | 96 | Compose / M3 主题 / 自适应 / 无障碍 / CI 全套 |
| 设计令牌 | `oklch-tokens` | aidenlive/SKILLS | 5 | OKLCH 感知均匀色板 + 亮暗双主题 |
| 设计令牌 | `monochrome-palette` | aidenlive/SKILLS | 5 | 单色板（贴合"色彩单调但可爱"） |
| 设计令牌 | `mobile-first-layouts` | aidenlive/SKILLS | 5 | 移动优先栅格 / 布局模式 |
| 主题 | `theme-factory` | anthropics/skills | 6 | 预设 / 自定义主题 |
| 质量/可访问性 | `accessibility` | wshobson/agents (accessibility-compliance) | 5 | WCAG 2.2 AA + 移动 TalkBack：对比度红线、48dp 触控目标、动效降级、语义化 |
| 质量/截图回归 | `compose-screenshot-testing` | 自研（本项目专用） | 1 | Paparazzi 优先、Roborazzi 备选的 Compose 截图回归工作流与模板，锁死视觉规范不漂移 |

## 与设计语言宪法的对齐原则

- **`DESIGN_LANGUAGE.md`** = 视觉唯一来源（主色 `OKLCH(0.78 0.10 25)` 珊瑚粉、大圆角、圆体字、弹簧动效）。
- **`PROJECT_BRIEF.md`** = 跨会话 / 跨技能唯一事实来源。
- 调用任何 skill 前，先让 agent 读取这两份文件，确保产出不跑偏。

## 使用方式

WorkBuddy 自动发现 `~/.codebuddy/skills/` 下的 skill，开发时直接自然语言调用即可，例如：

- "用 `frontend-design` 给这个页面定一套可爱 iOS 风 UI"
- "用 `claude-android-ninja` 把这个设计转成 Compose 代码，遵循 Material 3"
- "用 `oklch-tokens` + `monochrome-palette` 生成亮/暗双主题色板"
- "用 `ai-team-orchestration` 拉起开发团队，先补全 PROJECT_BRIEF 的产品方向"

## 安装过程的关键经验（避坑）

1. **大仓库 `git clone` 超时** → 改用 `codeload.github.com` tarball（独立 CDN，不耗 REST API 限额）。
2. **GitHub REST API 匿名限额 60/h 耗尽** → 文件内容走 `raw.githubusercontent.com`（不受限），仅列目录耗 API。
3. **github.com 部分 IP 不可达（DNS 轮询死节点）** → `codeload.github.com` 解析到可达 IP（20.205.243.165），绕开死节点。
4. **默认分支差异** → `claude-android-ninja` 是 `master`（非 `main`），codeload 需对应分支。
5. **二进制/大文件超时** → 下载器跳过图片/PDF 等，核心 `SKILL.md` + 文本 resources 优先。
6. **zsh 不自动分词** → 批量下载循环用显式文件名而非变量展开。

## 下一步建议

1. **（可选）轻量适配**：把 `DESIGN_LANGUAGE.md` 要点注入各 skill 的 `SKILL.md`，使其自带项目约束。
2. **明确产品方向**：补全 `PROJECT_BRIEF.md` §1，再用 `ai-team-orchestration` 拉起开发团队。
3. **直接开工**：用上述 skill 做原型 / 真实开发。

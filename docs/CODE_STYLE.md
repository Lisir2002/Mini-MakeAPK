# 代码风格（Code Style）

> 状态：已生效（2026-10-24 由安卓工程师/QA 评审）
> 遵循：[`docs/DOC_CHARTER.md`](DOC_CHARTER.md) §7 · 对齐：[`DESIGN_LANGUAGE.md`](../DESIGN_LANGUAGE.md)

本文件规定 Kotlin / Jetpack Compose 代码风格，以及提交与变更日志规范。

---

## 1. 包结构与命名

- 根包：`com.make.apk`
- 分层：`ui/theme`、`ui/components`、`ui/screens`、`ui/MmaApp.kt`
- 类/对象：PascalCase（`HomeScreen`、`CuteButton`）
- 函数/变量：camelCase（`loadInput`、`primaryColor`）
- 常量：SCREAMING_SNAKE_CASE（`MAX_INPUT_LENGTH`）
- 文件：与主要公开声明同名（`HomeScreen.kt`）

## 2. Kotlin 约定

- 优先 `val` 而非 `var`；优先不可变数据类 `data class`。
- 显式声明公开 API 的返回类型。
- 避免平台类型（`!!`），用 `?.let {}` / `requireNotNull`。
- 单行超 100 字符考虑换行；中文注释清晰、完整句。

## 3. Compose 约定

- Composable 函数 PascalCase，首参数 `modifier: Modifier = Modifier` 且透传到根。
- 状态提升：有状态用 `remember`/`mutableStateOf`，可复用 UI 用无状态 + `lambda` 回调。
- 主题色/字/形**只从 `MaterialTheme` 取**，禁止硬编码 hex 到业务组件。

```kotlin
@Composable
fun CuteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = MaterialTheme.shapes.extraLarge, // 胶囊 999dp
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}
```

## 4. 设计令牌使用（强制）

- **唯一来源**：`DESIGN_LANGUAGE.md` 定义 OKLCH 令牌；`ui/theme/Color.kt` 是其**派生**的 Compose 颜色。
- 业务代码禁止自创颜色/圆角/字体；一律引用 `MaterialTheme.colorScheme` / `shapes` / `typography`。
- 间距走 `Spacing.space1..7`（8pt 阶梯）；动效参数走 `Motion`（`SoftSpring` / `PressScaleTarget` / `DurationBreath` 等）。
- 新增令牌：先改 `DESIGN_LANGUAGE.md`，再同步 `Color.kt` / `Theme.kt`，并补 `docs/Version Log/CHANGELOG.md`。

### 4.1 门禁与豁免

`scripts/gitops/check_token_usage.py` 扫描 `app/.../ui/`（排除 `theme/` 包）：

| 检查 | 违规形态 | 正解 |
|------|---------|------|
| 颜色字面量 | `Color(0x…)` / `Color.Red` 等命名色直用 | `colorScheme.*` 令牌 |
| 圆角字面量 | `RoundedCornerShape(N.dp)` | `MaterialTheme.shapes.*` |
| 间距字面量 | `N.dp` 不在允许集（8pt 阶梯 + 已登记的组件规范尺寸） | `Spacing.space*` |
| 字号字面量 | `N.sp` | `typography.*` 档位 |

- **CI 强制**：Verify 与 Release 工作流均以 `--strict` 运行，有违规即失败；本地可 `python3 scripts/gitops/check_token_usage.py` 先自查。
- **行内豁免**：确有必要的例外（如第三方 API 要求的精确像素），在**该行**加 `// token-gate: allow: <理由>`，理由须可评审；禁止整文件关闭检查。
- **新增允许值**：`check_token_usage.py` 顶部 `ALLOWED_DP` 集中登记，附来源注释；新间距概念应先评估进 `Spacing` 阶梯，而非直接加白名单。

## 5. 提交规范（Conventional Commits）

格式：`<type>[optional scope]: <description>`

| type | 含义 | 版本影响 |
|------|------|----------|
| `feat` | 新功能 | MINOR |
| `fix` | 修复 | PATCH |
| `docs` | 仅文档 | — |
| `style` | 格式 | — |
| `refactor` | 重构 | — |
| `perf` | 性能 | — |
| `test` | 测试 | — |
| `build` | 构建/依赖 | — |
| `ci` | CI | — |
| `chore` | 杂务 | — |

- 重大变更：`type!:` 或 footer `BREAKING CHANGE:` → MAJOR。
- 描述祈使句、≤50 字符；正文与 footer 间空一行；关联 `Refs: #123`。
- 示例：

```text
feat(webview): 支持 HTML 字符串即时预览
fix(theme): 暗色下卡片描边对比度不足
docs(charter): 补充 Agent 协同写作规则
```

## 6. 变更日志（Keep a Changelog）

- 每次 checkpoint 立即补 [`docs/Version Log/CHANGELOG.md`](Version%20Log/CHANGELOG.md) 的 `[Unreleased]`。
- 每条：一行、过去式、主语 + 具体结果（"新增 HTML 即时预览"而非"修了一些东西"）。
- 六分类：Added / Changed / Deprecated / Removed / Fixed / Security，空分类不写。
- 发版时整体晋级为 `[X.Y.Z] - YYYY-MM-DD`，顶部保留空 `[Unreleased]`。

## 7. 代码评审清单

- [ ] 命名/包结构符合 §1–§3
- [ ] 颜色/字/形来自 `MaterialTheme`（无硬编码）
- [ ] 新增令牌已回写 `DESIGN_LANGUAGE.md` + `Color.kt`
- [ ] 提交信息符合 §5，变更日志已补 §6
- [ ] 文档随代码同步（README/架构如有变更）
- [ ] 相对链接可达、命令可跑通

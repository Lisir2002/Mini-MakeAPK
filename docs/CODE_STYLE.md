# 代码风格（Code Style）

> 状态：已生效（2026-10-24 由安卓工程师/QA 评审）
> 遵循：[`docs/DOC_CHARTER.md`](DOC_CHARTER.md) §7 · 对齐：[`DESIGN_LANGUAGE.md`](../DESIGN_LANGUAGE.md)

本文件规定 Kotlin / Jetpack Compose 代码风格，以及提交与变更日志规范。

---

## 1. 包结构与命名

- 根包：`com.make.apk`
- 分层：`ui/theme`、`ui/components`、`ui/screens`、`ui/permission`、`ui/storage`、`storage`、`ui/MmaApp.kt`
- 类/对象：PascalCase（`ProjectsScreen`、`CuteButton`）
- 函数/变量：camelCase（`loadInput`、`primaryColor`）
- 常量：SCREAMING_SNAKE_CASE（`MAX_INPUT_LENGTH`）
- 文件：与主要公开声明同名（`ProjectsScreen.kt`）

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

### 3.1 权限请求模式（强制）

- **场景触发才申请**：禁止进页面/启动即申请；每次操作前 `checkSelfPermission` 自查。
- **两段式**：先 `MmaPermissionDialog`（应用内二次确认，文案见 `PermissionSpecs.kt`），
  用户确认后才 `state.requestPermission()` 唤起系统弹窗。
- **接线**：`rememberLauncherForActivityResult(RequestPermission())`；
  `shouldShowRequestPermissionRationale` **必须**在 launcher 回调内读（需 `context as? ComponentActivity`），
  返回值决定 Denied（可再解释）还是 PermanentlyDenied（引导去设置）。
- **拒绝是正常结果**：不得 Toast 轰炸或阻断主流程；状态迁移由纯函数
  `resolveStatusAfterResult` 统一裁定，便于 JVM 单测。
- 特殊权限（如安装未知应用）走系统设置页 Intent，返回后用
  `LifecycleResumeEffect` 复查，不假设用户一定去开了。

**例外条款（启动存储门）**：MMAFile 公共目录是应用基础能力，`StoragePermissionGate`
允许在启动时**静默自查** + 引导弹窗；仍须满足：两段式（应用内说明先行）、拒绝即降级
不阻断（「以后再说」后应用照常可用）、下次启动重新评估。除此之外不得扩展开启动即申请。

**MMAFile 路径规约（强制）**：所有 MMAFile 路径必须经 `MmaStorage` 获取
（`rootFile` / `dir(MmaDir)` / `resolveLogDirectory`），禁止散落 `File(...)` 拼路径；
新增子目录在 `MmaDir` 加枚举值即可，`ensureDirs` 自动创建（扩展步骤见其 KDoc）。

```kotlin
// 典型接线（仅示意，当前版本尚无接入点）
val state = rememberMmaPermissionState(Manifest.permission.POST_NOTIFICATIONS) { onGranted() }
if (state.status == RuntimePermissionStatus.PermanentlyDenied) {
    MmaPermissionDialog(state.spec, PermissionDialogStage.GoToSettings,
        onConfirm = state::openSettings, onDismiss = state::dismissPrompt)
}
```

## 4. 设计令牌使用（强制）

- **唯一来源**：`DESIGN_LANGUAGE.md` 定义 OKLCH 令牌；`ui/theme/Color.kt` 是其**派生**的 Compose 颜色。
- 业务代码禁止自创颜色/圆角/字体；一律引用 `MaterialTheme.colorScheme` / `shapes` / `typography`。
- 间距走 `Spacing.space1..7`（紧凑阶梯 3/6/9/12/18/24/36）；动效参数走 `Motion`（`SoftSpring` / `PressScaleTarget` / `DurationBreath` 等）。
- 新增令牌：先改 `DESIGN_LANGUAGE.md`，再同步 `ui/theme/MmaDesign.kt`（令牌聚合体）/ `Theme.kt`，并补 `docs/Version Log/CHANGELOG.md`。

### 4.1 令牌可覆盖架构（MmaDesign + LocalMmaDesign）

- **令牌聚合**：`MmaDesign` data class 持有 `SpacingScale` / `AlphaScale` / `MaterialTier` / `TranslucentSpec` / `ScaffoldStyle`；`MmaTheme(design = …)` 装配，`LocalMmaDesign` 承载。
- **业务代码零迁移**：`Spacing` 等为 @Composable getter 读 `LocalMmaDesign.current`，默认值即现状；Phase-2 自定义主题只需换 `MmaDesign` 实例，**页面零改动**。
- **参数优先、令牌兜底**：可覆盖行为（如 chrome 磨砂 `translucent: Boolean? = null`）传值即显式决策，null 跟随 `ScaffoldStyle`；截图测试不提供 HazeState，天然落降级态。
- **磨砂链路**：源挂 `MmaScreen` content（`Modifier.haze`），消费方是顶栏/底栏（`Modifier.hazeChild`），二者必须是 Scaffold 内**兄弟**关系——消费方不能是源的后代（自引用循环）；状态经 `LocalHazeState` 下发。

### 4.2 门禁与豁免

`scripts/gitops/check_token_usage.py` 扫描 `app/.../ui/`（排除 `theme/` 包）：

| 检查 | 违规形态 | 正解 |
|------|---------|------|
| 颜色字面量 | `Color(0x…)` / `Color.Red` 等命名色直用（`Color.Transparent` 豁免：通用工具色） | `colorScheme.*` 令牌 |
| 圆角字面量 | `RoundedCornerShape(N.dp)` | `MaterialTheme.shapes.*` |
| 间距字面量 | `N.dp` 不在允许集（紧凑阶梯 + 已登记的组件规范尺寸） | `Spacing.space*` |
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

# Design Tokens 契约 · 落地说明（README）

本目录是 MMA 安卓（Jetpack Compose / Material 3）设计令牌的**唯一事实来源（规格即契约）**。

- `design-tokens.json`：机器可读契约，含 `meta` / `color` / `typography` / `spacing` / `radius` / `elevation` / `alpha` / `motion` / `chrome` / `component`（44 个组件条目，均含状态变体）。所有颜色字段同时给 `hex` 与 `oklch`。
- `design-tokens.css`：`:root` 亮色变量 + `@media (prefers-color-scheme: dark)` 暗色变量，供 Web 预览 / 文档 / 设计稿复用，命名与 JSON 对应（`--color-*` / `--radius-*` / `--space-*` / `--elevation-*` / `--top-bar-height` / `--comp-*`）。

## 映射到现有 Compose 层（不改命名，只换值 / 补项）

- `ui/theme/Color.kt`：`md_*` 常量（亮/暗后缀）。本次**仅替换十六进制与 OKLCH 注释**，命名保持 `md_bg_light` 等；新增蓝点缀家族（`md_blue_50..900`）与语义色亮/暗（`md_success_*` / `md_warning_*` / `md_error_*`）。JSON 的 `color` 段已算好 oklch，直接抄。
- `ui/theme/Theme.kt`：`AppShapes` 当前 `extraSmall=8 / small=16 / medium=16 / large=24 / extraLarge=999` → 改为 `extraSmall=6 / small=12 / medium=12 / large=16 / extraLarge=999`（新增 `large` 即 `--radius-lg` 16dp；并新增 `xl` 映射 20dp 供 bottomSheet 顶部圆角，落地时在 Shapes 里补一个 20dp 档或局部用 `RoundedCornerShape(20.dp)` 并经门禁白名单）。`AppTypography` 映射：`displayLarge←display(28/34/600)`、`headlineMedium←headline(22/28/600)`、`titleLarge←title(18/24/600)`、`bodyLarge←body-lg(16/24/400)`、`bodyMedium←body-md(14/21/400)`、`labelLarge←label(13/18/500)`、`labelMedium←caption(12/16/500)`。`LightColorScheme` / `DarkColorScheme` 用新色值装配。
- `ui/theme/Spacing.kt`：`Spacing` 代理（space1-7 读 `LocalMmaDesign.current.spacing`）**保持不变**——已 = 3/6/9/12/18/24/36，与契约完全一致。
- `ui/theme/MmaDesign.kt`：`MmaDesign` 聚合 + `LocalMmaDesign`。本次**新增** `ElevationScale`（elevation-1/2）、`ChromeScale`（topBarHeight 52 / bottomBarHeight 56，覆盖 M3 NavigationBar 默认 80dp）；接入方式同 `SpacingScale`（代理读 `LocalMmaDesign.current`），业务零迁移。
- `ui/theme/Motion.kt`：`Motion` 时长对齐契约（short 100 / medium 200 / long 300 / enter 400 / breath 1100）；`PressScaleTarget` `0.96 → 0.97` 以对齐 `buttonFilled.pressScale`。

## 真实组件 → 令牌类 映射（覆盖校验）

- 壳/导航：`MmaScreen`/`Scaffold` → `screen`；`MmaTopBar` → `topBar`；`MmaBottomBar` → `bottomNav`；`MmaTranslucentSurface` → `translucentSurface`；`MmaTabs`/`MmaNavHost` → `tabs`/`bottomNav`；`ShellDrawerSection` → `drawer`；`ShellTopTabsSection` → `topTabs`；`EditTabRow` → `editTabRow`；`MmaIcons` → `iconButton`/`iconContainer`。
- 编辑器：`ProjectFormFields`/`ProjectTypeSelector`/`ProjectIcon` → `textField`/`card`/`iconContainer`；`EditIdentityCard` → `identityCard`；`HighSettingsTab`/`WebSettingsTab`/`ProjectSourceSection` → `settingsCard`/`card`。
- 页面：`ProjectsScreen`/`AddProjectScreen`/`ProjectEditScreen` → `screen`+`projectCard`；`ProfileScreen` → `profileItem`/`avatar`；`DevelopingScreen` → `emptyState`/`roadmapCard`；`WebViewScreen` → `screen`。
- 弹窗/反馈：`MmaPermissionDialog` → `permissionDialog`；通用 `dialog` → `dialog`；`snackbar`/`toast` → `snackbar`/`toast`；`badge`/`statusPill` → `badge`/`statusPill`；`skeleton`/`loading` → `skeleton`/`loading`。

## 后续工程师落地步骤

1. **改 DESIGN_LANGUAGE.md 色彩章节（§2）**：删除珊瑚粉 OKLCH，写入本契约 `color` 段（亮/暗中性 + 蓝家族 + 语义色）。同步 §3 圆角（sm6/md12/lg16/xl20/pill999）、§5 字阶（display28/headline22/title18/body-lg16/body-md14/label13/caption12）、§7 阴影（elevation-1/2 原值保留）。
2. **改 Color.kt**：替换 `md_*` 亮/暗 hex（含 primary 组），新增 `md_blue_50..900` 与 `md_success`/`md_warning`/`md_error` 亮暗；注释补 oklch（从 JSON 抄）。
3. **改 Theme.kt**：`AppShapes` 圆角改 6/12/16/20(pill 999)；`AppTypography` 按上表映射；`LightColorScheme`/`DarkColorScheme` 用新值。
4. **新增令牌**：`MmaDesign.kt` 加 `ElevationScale` + `ChromeScale`，`LocalMmaDesign` 默认装配；新增组件令牌代理（如 `Component` 对象，字段对应 JSON `component.*`）。
5. **接门禁**：`scripts/gitops/check_token_usage.py` 的 `COMPONENT_SIZES` 需补 `16`(radius-lg) / `20`(radius-xl) / `52`(top-bar-height)（56 已存在 = 底栏高），否则新 height/shape 字面量会误报；随后 `python3 scripts/gitops/check_token_usage.py --strict` 跑通。
6. **禁止**：业务代码裸写 `dp`/`sp`/`Color`/圆角；一律走令牌（`theme/` 包除外，见 `CODE_STYLE.md` 豁免规则）。

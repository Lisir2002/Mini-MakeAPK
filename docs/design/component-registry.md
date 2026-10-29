# 组件注册表（Component Registry）

> 对应代码：`app/src/main/java/com/make/apk/ui/components/MmaComponentRegistry.kt`
> 对应测试：`app/src/test/java/com/make/apk/ui/components/MmaComponentRegistryTest.kt`
> 背景：§优化·第三点——"同类组件只应有一套样式，新增即登记一个位置接口，后续同类直接接入"。

## 1. 核心约束

- **每种组件类型只有一套规范样式、一个注册槽位（位置接口）**。
- 新增自绘组件**必须**在 `MmaComponentRegistry.entries` 追加一条登记，复用既有 `Slot`（同类共用）或显式登记新槽位。
- 规范样式一律以 `ControlShapes` / `MmaDesign` 令牌引用，**禁止内联裸值**（与全站"禁止硬编码 UI"铁律一致）。
- 测试门禁断言：每类型仅一条登记、每条均有规范样式引用；违反即 CI 失败。

## 2. 槽位（Slot = 位置接口）

| 槽位 | 含义 | 挂载位置 |
|------|------|----------|
| `Field` | 表单字段 | 下拉 / 输入框 / 步进器 |
| `Overlay` | 浮层 | 弹窗 / 通知气泡 |
| `Toggle` | 开关 / 单选 | 开关 / 分段选择 |
| `Action` | 图标动作 | 行内编辑 / 删除等 |
| `Navigation` | 导航 | 顶栏 / 底栏 / Tab |
| `Button` | 文字按钮 | 保存 / 预览等 |
| `Surface` | 页面骨架 | 三段式槽位布局 |

## 3. 已登记组件

| 类型 | 槽位 | 规范样式（令牌引用） |
|------|------|----------------------|
| Dropdown | Field | `fieldClosed(12)/fieldOpen(16)` + `menuItemHeight(48)` + `menuItemIcon(20)` + 选中 `tertiaryContainer` |
| Dialog | Overlay | `dialog(28)` + `elevation2(6dp)` + `dialogMaxWidth(320)` |
| Switch | Toggle | `pill` + `switchTrackWidth(52)/Height(32)/Thumb(24)` |
| Choice | Toggle | `segmentPiece(12)` + `radioSize(20)/radioStroke(2)` |
| Chip | Action | `segmentPiece(12)` + `chipHeight(36)` |
| IconAction | Action | `segmentPiece(12)` + `iconButtonSize(40)` + `menuItemIcon(20)` |
| Stepper | Field | `segmentPiece(12)` + `iconButtonSize(40)` |
| Tabs | Navigation | `tabIndicatorHeight(3)` |
| Notice | Overlay | `group(16)` + `elevation2(6dp)` + `labelLarge` + 3s 自动消失 / 点击即隐藏 |
| Screen | Surface | `MmaScreen` Scaffold + `topBarHeight(52)/bottomBarHeight(56)` |
| TopBar | Navigation | `MmaTopBar` + `topBarHeight(52)` |
| BottomBar | Navigation | `MmaBottomBar` + `bottomBarHeight(56)` |
| TextButton | Button | `buttonHeight(40)` + `labelLarge` |

## 4. 设计协同

- 设计师新增组件前，先查本表确认是否已有同类——有则直接复用登记项，无则登记新条目。
- 任意组件若偏离上表样式，视为**非规范**，须回炉对齐或更新本表（并更新 `MmaComponentRegistry` 与测试）。
- 该表与 `tokens/`、`ControlShapes` 互为索引：本表描述"用什么令牌"，令牌文件描述"令牌是多少"。

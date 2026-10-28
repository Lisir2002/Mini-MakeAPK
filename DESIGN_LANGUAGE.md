# 设计语言规范：可爱简约 · iOS 风（Cute-Minimal iOS）

> 本规范是整套配套开发技能的**共享宪法**。无论后续安装 `frontend-design`、`claude-android-ninja` 还是 `oklch-tokens`，产出的设计与代码都必须对齐本文件。
> 适用平台：**纯原生安卓 / Jetpack Compose / Material 3**。
> 关键词：**简约、克制、柔和、圆润、iOS 质感、低彩度中性（黑白为主 + 蓝点缀）**。

---

## 0. 铁律：禁止硬编码 UI（最高优先级）

**一切颜色 / 尺寸 / 圆角 / 字号 / 阴影一律走设计令牌，绝不裸写。**

- **颜色**：`ui/theme/Color.kt` 的 `md_*` 常量 / `MaterialTheme.colorScheme.*`；业务代码禁止 `Color(0x…)`、`Color.Red` 等命名色。
- **圆角**：`MaterialTheme.shapes.*`（来源 `AppShapes`）；业务代码禁止直写 `RoundedCornerShape(…)`。
- **间距**：`Spacing.*` 阶梯（来源 `LocalMmaDesign.current.spacing`）；禁止裸写 `N.dp`（白名单见下方）。
- **字号**：`MaterialTheme.typography.*`；禁止裸写 `N.sp`。
- **顶底栏高度**：`LocalMmaDesign.current.chrome.topBarHeight`(52) / `bottomBarHeight`(56)；禁止裸写高度字面量。
- **阴影**：`LocalMmaDesign.current.elevation.*`（来源 elevation-1/2）；禁止裸写阴影参数。

**豁免**：仅 `ui/theme/` 包为令牌定义处（可直接写 `Color(0x…)` / `RoundedCornerShape` / `N.dp`）；其余业务代码一律经令牌，违反即门禁失败 + 回退。

**门禁**：`scripts/gitops/check_token_usage.py --strict` 扫描 `ui/`（排除 `theme/`）的裸写色值 / 圆角 / 字号 / 非白名单 dp；`COMPONENT_SIZES` 白名单含 16(radius-lg) / 20(radius-xl) / 52(top-bar) / 56(bottom-bar) 等组件规范值，新增值须注明来源。

---

## 1. 设计原则（三条铁律）

1. **色彩中性为主 + 蓝点缀**：全界面只用**一个主色**（蓝 `#2563EB`），其余皆中性黑白灰；禁止多色拼贴。质感来自**圆润形状 + 柔和阴影 + 轻微动效**，而非花哨配色。
2. **圆润优先**：大圆角、柔和扩散阴影、轻微弹性动效，营造 iOS 般的亲和无攻击性。
3. **留白即设计**：遵循 8pt 栅格，宁空勿挤；信息密度低、呼吸感强。

---

## 2. 色彩系统（黑白为主 + 蓝色点缀）

> 使用 **OKLCH**（而非 HEX/RGB），保证明度/彩度可预测、亮暗主题一致、可程序化生成。
> 颜色值含义：`OKLCH(明度 0~1, 彩度 0~0.37, 色相 0~360)`。
> 设计哲学：**中性黑白为骨架，蓝色为唯一点缀色**；禁止珊瑚粉等多彩主色，禁止多色拼贴。

### 2.1 明亮主题令牌

| 令牌 | OKLCH | 用途 |
|------|-------|------|
| `--bg` | `OKLCH(0.9759 0.0029 264.5)` | 页面背景（近白冷灰） |
| `--surface` | `OKLCH(1.0000 0.0000 89.9)` | 卡片/弹窗表面（纯白） |
| `--surface-2` | `OKLCH(0.9579 0.0029 264.5)` | 次级表面/分组底 |
| `--on-surface` | `OKLCH(0.2027 0.0076 248.2)` | 主文字（近黑） |
| `--on-surface-variant` | `OKLCH(0.4921 0.0221 265.8)` | 次文字/占位符 |
| `--outline` | `OKLCH(0.9208 0.0063 255.5)` | 分隔线/边框 |
| `--outline-variant` | `OKLCH(0.9515 0.0046 258.3)` | 弱分隔线 |
| `--primary` | `OKLCH(0.5461 0.2152 262.9)` | **主色·蓝点缀（#2563EB）** |
| `--on-primary` | `OKLCH(1.0000 0.0000 89.9)` | 主色上的文字（白） |
| `--primary-container` | `OKLCH(0.9501 0.0236 262.1)` | 主色浅容器（淡蓝底） |
| `--on-primary-container` | `OKLCH(0.4110 0.1765 262.3)` | 浅容器上的文字（深蓝） |

**蓝色点缀家族（非主色，用于强调/链接/选中态；双主题共用 scale）**：

| 令牌 | HEX | OKLCH |
|------|-----|------|
| `--blue-50` | `#EFF4FF` | `OKLCH(0.9666 0.0157 266.3)` |
| `--blue-100` | `#DBE6FF` | `OKLCH(0.9243 0.0361 266.7)` |
| `--blue-200` | `#BFD3FF` | `OKLCH(0.8665 0.0651 265.8)` |
| `--blue-300` | `#93B4FF` | `OKLCH(0.7748 0.1139 265.6)` |
| `--blue-400` | `#5B8DEF` | `OKLCH(0.6545 0.1562 262.5)` |
| `--blue-500` | `#3B6FE0` | `OKLCH(0.5686 0.1814 263.2)` |
| `--blue-600` | `#2563EB` | `OKLCH(0.5461 0.2152 262.9)`（= primary 亮） |
| `--blue-700` | `#1D4FD0` | `OKLCH(0.4827 0.2060 263.9)` |
| `--blue-800` | `#1A41A8` | `OKLCH(0.4186 0.1713 264.3)` |
| `--blue-900` | `#15346F` | `OKLCH(0.3392 0.1090 261.7)` |

**语义色（克制使用，仅状态）**：

| 令牌 | 亮 OKLCH | 暗 OKLCH | 用途 |
|------|----------|----------|------|
| `--success` | `OKLCH(0.6271 0.1699 149.2)` | `OKLCH(0.7227 0.1920 149.6)` | 成功（绿） |
| `--warning` | `OKLCH(0.6658 0.1574 58.3)` | `OKLCH(0.7686 0.1647 70.1)` | 警告（琥珀） |
| `--error` | `OKLCH(0.5771 0.2152 27.3)` | `OKLCH(0.7106 0.1661 22.2)` | 错误（红） |
| `--info` | 复用 `--primary` | 复用 `--primary` | 信息（蓝） |

### 2.2 暗黑主题令牌

| 令牌 | OKLCH | 用途 |
|------|-------|------|
| `--bg` | `OKLCH(0.1682 0.0045 264.4)` | 近黑冷灰背景 |
| `--surface` | `OKLCH(0.2082 0.0067 258.4)` | 卡片表面 |
| `--surface-2` | `OKLCH(0.2463 0.0088 255.6)` | 次级表面 |
| `--on-surface` | `OKLCH(0.9662 0.0034 247.9)` | 主文字（近白） |
| `--on-surface-variant` | `OKLCH(0.7063 0.0123 252.1)` | 次文字 |
| `--outline` | `OKLCH(0.2993 0.0107 254.0)` | 分隔线 |
| `--outline-variant` | `OKLCH(0.2708 0.0109 254.0)` | 弱分隔线 |
| `--primary` | `OKLCH(0.6545 0.1562 262.5)` | 暗色主色·蓝点缀（#5B8DEF，提明度保对比） |
| `--on-primary` | `OKLCH(0.2195 0.0538 259.0)` | 主色上的文字（深蓝黑） |
| `--primary-container` | `OKLCH(0.3184 0.0872 259.5)` | 主色容器（深蓝） |
| `--on-primary-container` | `OKLCH(0.8882 0.0543 262.2)` | 容器上的文字（浅蓝） |
| `--success` | `OKLCH(0.7227 0.1920 149.6)` | 成功 |
| `--warning` | `OKLCH(0.7686 0.1647 70.1)` | 警告 |
| `--error` | `OKLCH(0.7106 0.1661 22.2)` | 错误（提明度保对比） |

### 2.3 色彩使用规则
- **中性骨架 + 蓝点缀**：列表、卡片、底栏只用 `--bg / --surface / --on-surface / --primary` 四族，不引入额外色相；蓝色是唯一彩色点缀，用于主操作/链接/选中态。
- **语义色仅用于状态**：错误红只出现在校验失败；成功/警告同理，不用于装饰。
- **主色占比 < 15%**：主色是"点睛"，不是"铺面"；大面积留白+中性黑白。
- **可访问性**：正文与背景对比度 ≥ 4.5:1（AA）；主色按钮上的白字需复核对比。

---

## 3. 形状与圆角

| 令牌 | 值 | 适用 |
|------|----|------|
| `--radius-sm` | `6dp` | 小组件/标签 |
| `--radius-md` | `12dp` | 卡片/输入框（默认） |
| `--radius-lg` | `16dp` | 大卡片/底部 sheet |
| `--radius-xl` | `20dp` | 大 sheet 顶部圆角 |
| `--radius-pill` | `999dp` | 按钮/胶囊/头像 |

- 卡片默认 `--radius-md` 或 `--radius-lg`（柔和包裹感）。
- 按钮优先 `--radius-pill`（最克制利落）；次级按钮可用 `--radius-md`。
- 禁止尖角（radius=0）与极小圆角（<4dp）。

**Compose M3 映射**（`AppShapes`，唯一圆角来源）：

| M3 档位 | 值 | 对应令牌 |
|---------|-----|---------|
| `extraSmall` | 6dp | `--radius-sm` |
| `small` | 12dp | `--radius-md`（卡片/输入框默认） |
| `medium` | 12dp | `--radius-md`（M3 Card 默认档） |
| `large` | 16dp | `--radius-lg` |
| `extraLarge` | 999dp | `--radius-pill`（胶囊） |

页面禁止直写 `RoundedCornerShape(...)`，一律 `MaterialTheme.shapes.*`；需要 16dp 圆角时显式用 `shapes.large`，`extraLarge` 是胶囊档，勿用于普通卡片。`--radius-xl`(20dp) 供 bottomSheet 顶部圆角，经门禁白名单 `COMPONENT_SIZES` 的 `20`。

---

## 4. 间距与栅格（紧凑基准）

> 2025-09 全局温和收紧（-25%）：布局偏紧凑是产品明确需求；圆角/字号/图标不受影响。

| 令牌 | 值 | 用途 |
|------|----|------|
| `--space-1` | `3dp` | 极紧凑 |
| `--space-2` | `6dp` | 组内 |
| `--space-3` | `9dp` | 相关元素 |
| `--space-4` | `12dp` | 卡片内边距（默认） |
| `--space-5` | `18dp` | 区块间距 |
| `--space-6` | `24dp` | 大区块/页面边距 |
| `--space-7` | `36dp` | 页面级留白 |

- 所有间距为 3 的倍数（紧凑档）；区块间优先 12/18/24。
- 内容左右边距 `--space-5` (18dp) 起步。
- 间距令牌经 `LocalMmaDesign` 可整体覆盖（ARCHITECTURE §3.4），业务代码零迁移。

---

## 5. 字体与排版

- **字族（系统无衬线优先）**：英文/数字 `Inter` / 系统无衬线；中文 `Noto Sans SC` / 系统无衬线。
- **默认回退**：系统无衬线 → Material 3 默认无衬线（避免未引入字体时崩坏）。
- **字号阶梯（token 化，见 design-tokens.json typography）**：

| 令牌 | 字号 | 行高 | 字重 | 用途 |
|------|------|------|------|------|
| `--display` | 28sp | 34sp | 600 | 大标题 / 页头 |
| `--headline` | 22sp | 28sp | 600 | 区块标题 |
| `--title` | 18sp | 24sp | 600 | 卡片标题 / 顶栏标题 |
| `--body-lg` | 16sp | 24sp | 400 | 正文（大） |
| `--body-md` | 14sp | 21sp | 400 | 正文（默认）/ 副标题 |
| `--label` | 13sp | 18sp | 500 | 标签 / 底栏 tab |
| `--caption` | 12sp | 16sp | 500 | 辅助说明 |

- **字重**：Regular(400) / Medium(500) / SemiBold(600)；标题用 600，正文 400，标签 500。
- **行高**：正文 1.5×，标题 1.2×。
- **Compose 映射**（`AppTypography`）：`displayLarge←display`、`headlineMedium←headline`、`titleLarge←title`、`bodyLarge←body-lg`、`bodyMedium←body-md`、`labelLarge←label`、`labelMedium←caption`。
- **禁止**：默认 Roboto 作为主视觉字（偏中性）；字间距过度拉伸。

---

## 6. 动效与微交互（柔和弹性）

- **过渡曲线**：弹簧 `spring(stiffness≈300, damping≈30)`，带**轻微回弹**（bounce），拒绝生硬线性。
- **时长**：快 `200ms` / 标准 `300ms` / 入场 `400ms` / 循环呼吸 `1100ms`（`--motion-breath`，无限脉冲如打包中动效，ease-in-out 往返）。
- **微交互**：
  - 按钮按压：`scale 1 → 0.96`，释放回弹。
  - 卡片入场：`translateY(8dp) + alpha 0→1`，列表项 **stagger 40ms** 依次浮现。
  - 页面切换：水平滑动 + 轻微视差。
  - 加载：柔和骨架屏（主色低透明脉冲），禁用硬转圈。
- **原则**：动效服务于"柔和可爱"，幅度小、节奏慢，不喧宾夺主。

---

## 7. 质感与阴影（iOS 柔光）

- **阴影风格**：大模糊半径、低透明度、偏下偏移，模拟自然光柔光。
- `--elevation-1`：`0 1px 2px rgba(16,20,26,.06), 0 1px 3px rgba(16,20,26,.08)`
- `--elevation-2`：`0 4px 12px rgba(16,20,26,.08), 0 8px 24px rgba(16,20,26,.10)`
- **Compose 抬升量**：阴影串不直用于 Compose，映射为 `LocalMmaDesign.current.elevation`（[ElevationScale]）——`elevation-1 → elevation1≈2.dp`、`elevation-2 → elevation2≈6.dp`，由组件层 `Modifier.shadow` 按此量级绘制（见 theme/MmaDesign.kt）。
- **描边**：低对比 1dp `--outline`，用于分隔而非硬阴影。
- **可加极轻渐变/玻璃质感**，但保持单调克制（如卡片顶部 2% 白渐变高光）。

### 7.1 材质档位与磨砂（glassmorphism）

材质三档（`MmaDesign.defaultMaterial`，骨架 chrome 消费）：

| 档位 | 视觉 | 场景 |
|------|------|------|
| `Flat` | 纯色平面 | **默认**：最省电，磨砂关闭时 |
| `Elevated` | 纯色 + 阴影抬升 | 层次感来自 elevation 而非透明 |
| `Translucent` | 半透明磨砂 | 顶栏/底栏 chrome；API 31+ 真模糊（Haze RenderNode），< 31 降级近似 |

磨砂参数（`TranslucentSpec`，可整体覆盖）：容器不透明度 `0.88`、模糊半径 `20dp`、
噪点 `0.05`（抵消低端机色带）、内描边 `0.12` × 1dp `--outline`（玻璃边缘提亮，iOS 风关键细节）。

**降级链**（`MmaTranslucentSurface` 三态）：
1. `translucent=false` → 实色 Surface（零开销，截图测试落此态）；
2. `translucent=true` + HazeState → `hazeChild` 真磨砂；
3. `translucent=true` 无 HazeState（独立预览/截图）→ 半透明纯色 + 1dp 细描边近似。

---

## 8. 核心组件示例（Compose 落地）

| 组件 | 规范 |
|------|------|
| **Filled Button** | `primary` 填充、`radius-pill`、按压 scale 0.96、文字 `--on-primary` |
| **Card** | `surface` 底、`radius-md/lg`、`elevation-1`、内边距 `--space-4` |
| **TextField** | `radius-md`、聚焦时柔和 `--primary` 描边环（非硬边框）、占位 `--on-surface-variant` |
| **BottomNav** | 圆润图标、选中态 `--primary` 高亮 + 轻微放大、底部柔和 `elevation` |
| **FAB** | `primary` 圆、轻柔阴影、按压回弹 |
| **Chip/标签** | `primary-container` 底 + `on-primary-container` 字、`radius-pill` |
| **权限二次确认弹窗** | `surface` 底 + `radius-lg`；图标先包 64dp `primary-container` 圆底再 32dp 图（`on-primary-container`）；主按钮胶囊 Filled、次级「以后再说」TextButton；文案 `title-large` + `body-medium`，**全程用人话不出现权限名** |
| **项目页（封装工作台）** | 上半固定输入区（`title-large` 标题 + `radius-md` 多行 TextField + 通栏胶囊主按钮），`--outline` 细分隔线，下半滚动项目列表；空态 = Folder 图标 36dp + 引导文案（`on-surface-variant`） |
| **项目卡片** | `Card` + `radius-md`；首行来源名 `body-large`（单行省略），次行状态文案 + 「预览/删除」TextButton（文字保留，保可访问性） |
| **开发中页（占位）** | 居中 Developing 图标 64dp `primary` + `headline-medium` 标题 + 一句现状；路线图预告卡 `radius-md`，条目列「在建」事项（预告不承诺） |
| **我的页（个人中心）** | 顶部信息卡：Folder 图标 48dp `primary` + 应用名 `title-large` + 版本号 + MMAFile 存储状态（真实读，未授权时明示降级）；条目列表数据驱动：图标 24dp `primary` + 标题 `body-large` + 副标题 `body-medium`（`on-surface-variant`） |

---

## 9. 与 Material 3 的映射（安卓对齐）

- 本语言 = **M3 骨架 + 自定义 Shape/Typography/Motion/Elevation**。
- `ColorScheme`：`primary=--primary`, `surface=--surface`, `background=--bg`, `outline=--outline` 等。
- `Shapes`：用 `RoundedCornerShape` 替代 M3 默认（M3 默认圆角偏小，需放大到 16/24）。
- `Typography`：替换为圆体字族，放大标题字重到 600。
- `Motion`：用 `spring` 替代 M3 默认 `easeOut`，加轻微回弹。
- **不要**照搬 M3 默认"扁平 Material 风"（偏冷静），要用本规范的暖+圆+柔改造它。

---

## 10. 反模式（禁止项）

- ❌ 多色拼贴、彩虹渐变、霓虹色（违背"色彩单调"）。
- ❌ 尖角卡片、硬阴影、重投影（违背"圆润柔和"）。
- ❌ 默认 Roboto/Inter 作为主视觉字（不可爱）。
- ❌ 生硬线性过渡、无微交互（无 iOS 柔感）。
- ❌ 信息过密、留白不足（违背"简约"）。
- ❌ 主色铺满大面积（主色应为点睛 < 15%）。

---

## 11. 启动图标（品牌钉）

启动图标是设计语言的「门面」，与 §2 同律：**黑白为主 + 蓝点缀，禁止珊瑚粉等多彩主色**。

- **主体构成**：吉祥物（深蓝墨）+「网页卡 → APK 卡」+ 蓝色光环，白底承载。
- **不含字标**：`MiniMe-MMA / 前端应用转 APK` 仅留给商店大图；48dp 启动图标不载字标（不可读且被遮罩裁掉）。
- **缩放节奏**：主体宽占画布 80%，圆形 / 方形遮罩下均不裁切关键元素。
- **自适应**：API 26+ 用 `mipmap-anydpi-v26/ic_launcher.xml`（背景 `@color/ic_launcher_background` = 纯白 + 前景位图）；API 24/25 回退到烘焙白底圆角方的 `ic_launcher.png`。
- **不提供 monochrome**：素材为多彩位图，强取剪影会带脏，主题图标回退彩色。
- **铁律**：图标一律由 `docs/design/assets/app-icon/app-icon-source-1254.webp` 经
  `scripts/design/build_launcher_icon.py` 生成，**禁止手改产物、禁止改回珊瑚粉矢量图标**。


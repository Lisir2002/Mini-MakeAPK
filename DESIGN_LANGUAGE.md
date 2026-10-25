# 设计语言规范：可爱简约 · iOS 风（Cute-Minimal iOS）

> 本规范是整套配套开发技能的**共享宪法**。无论后续安装 `frontend-design`、`claude-android-ninja` 还是 `oklch-tokens`，产出的设计与代码都必须对齐本文件。
> 适用平台：**纯原生安卓 / Jetpack Compose / Material 3**。
> 关键词：**可爱、简约、柔和、圆润、iOS 质感、低彩度但温暖**。

---

## 1. 设计原则（三条铁律）

1. **色彩单调但温暖**：全界面只用**一个主色**（珊瑚粉），其余皆暖中性灰；禁止多色拼贴。可爱感来自**圆润形状 + 柔和阴影 + 轻微动效**，而非花哨配色。
2. **圆润优先**：大圆角、柔和扩散阴影、轻微弹性动效，营造 iOS 般的亲和无攻击性。
3. **留白即设计**：遵循 8pt 栅格，宁空勿挤；信息密度低、呼吸感强。

---

## 2. 色彩系统（OKLCH 感知均匀色板）

> 使用 **OKLCH**（而非 HEX/RGB），保证明度/彩度可预测、亮暗主题一致、可程序化生成。
> 颜色值含义：`OKLCH(明度 0~1, 彩度 0~0.37, 色相 0~360)`。

### 2.1 明亮主题令牌

| 令牌 | OKLCH | 用途 |
|------|-------|------|
| `--bg` | `OKLCH(0.98 0.004 95)` | 页面背景（暖白） |
| `--surface` | `OKLCH(1 0 0)` | 卡片/弹窗表面（纯白） |
| `--surface-2` | `OKLCH(0.97 0.003 95)` | 次级表面/分组底 |
| `--on-surface` | `OKLCH(0.28 0.012 60)` | 主文字（暖深灰） |
| `--on-surface-variant` | `OKLCH(0.52 0.01 60)` | 次文字/占位符 |
| `--outline` | `OKLCH(0.90 0.004 80)` | 分隔线/边框 |
| `--primary` | `OKLCH(0.78 0.10 25)` | **主色·柔和珊瑚粉（可爱核心）** |
| `--on-primary` | `OKLCH(0.99 0.005 95)` | 主色上的文字（近白） |
| `--primary-container` | `OKLCH(0.93 0.04 30)` | 主色浅容器（柔和粉底） |
| `--on-primary-container` | `OKLCH(0.40 0.06 30)` | 浅容器上的文字 |

**可选语义色（克制使用，仅在必要处出现）**：

| 令牌 | OKLCH | 用途 |
|------|-------|------|
| `--success` | `OKLCH(0.80 0.10 155)` | 成功（薄荷绿） |
| `--warning` | `OKLCH(0.82 0.10 85)` | 警告（暖黄） |
| `--error` | `OKLCH(0.62 0.16 25)` | 错误（珊瑚红） |

### 2.2 暗黑主题令牌

| 令牌 | OKLCH | 用途 |
|------|-------|------|
| `--bg` | `OKLCH(0.20 0.008 60)` | 深暖灰背景 |
| `--surface` | `OKLCH(0.25 0.008 60)` | 卡片表面 |
| `--surface-2` | `OKLCH(0.30 0.008 60)` | 次级表面 |
| `--on-surface` | `OKLCH(0.92 0.004 95)` | 主文字 |
| `--on-surface-variant` | `OKLCH(0.70 0.01 60)` | 次文字 |
| `--outline` | `OKLCH(0.40 0.006 60)` | 分隔线 |
| `--primary` | `OKLCH(0.82 0.10 25)` | 暗色下提高明度保可爱 |
| `--on-primary` | `OKLCH(0.22 0.02 30)` | 主色上的文字 |
| `--primary-container` | `OKLCH(0.38 0.06 30)` | 主色容器 |
| `--on-primary-container` | `OKLCH(0.85 0.06 30)` | 深色主色容器上的文字（浅珊瑚） |
| `--error` | `OKLCH(0.72 0.16 25)` | 错误（比亮色提明度保对比） |
| `--success` | `OKLCH(0.75 0.12 155)` | 成功 |
| `--warning` | `OKLCH(0.78 0.12 85)` | 警告 |

### 2.3 色彩使用规则
- **主界面色调单调**：列表、卡片、底栏只用 `--bg / --surface / --on-surface / --primary` 四族，不引入额外色相。
- **语义色仅用于状态**：错误红只出现在校验失败；成功/警告同理，不用于装饰。
- **主色占比 < 15%**：主色是"点睛"，不是"铺面"；大面积留白+中性。
- **可访问性**：正文与背景对比度 ≥ 4.5:1（AA）；主色按钮上的白字需复核对比。

---

## 3. 形状与圆角（iOS 风大圆角）

| 令牌 | 值 | 适用 |
|------|----|------|
| `--radius-sm` | `8dp` | 小组件/标签 |
| `--radius-md` | `16dp` | 卡片/输入框（默认） |
| `--radius-lg` | `24dp` | 大卡片/底部 sheet |
| `--radius-pill` | `999dp` | 按钮/胶囊/头像 |

- 卡片默认 `--radius-md` 或 `--radius-lg`（柔和包裹感）。
- 按钮优先 `--radius-pill`（最可爱）；次级按钮可用 `--radius-md`。
- 禁止尖角（radius=0）与极小圆角（<4dp）。

**Compose M3 映射**（`AppShapes`，唯一圆角来源）：

| M3 档位 | 值 | 对应令牌 |
|---------|-----|---------|
| `extraSmall` | 8dp | `--radius-sm` |
| `small` | 16dp | `--radius-md`（卡片/输入框默认） |
| `medium` | 16dp | `--radius-md`（M3 Card 默认档） |
| `large` | 24dp | `--radius-lg` |
| `extraLarge` | 999dp | `--radius-pill`（胶囊） |

页面禁止直写 `RoundedCornerShape(...)`，一律 `MaterialTheme.shapes.*`；需要 24dp 圆角时显式用 `shapes.large`，`extraLarge` 是胶囊档，勿用于普通卡片。

---

## 4. 间距与栅格（8pt 基准）

| 令牌 | 值 | 用途 |
|------|----|------|
| `--space-1` | `4dp` | 极紧凑 |
| `--space-2` | `8dp` | 组内 |
| `--space-3` | `12dp` | 相关元素 |
| `--space-4` | `16dp` | 卡片内边距（默认） |
| `--space-5` | `24dp` | 区块间距 |
| `--space-6` | `32dp` | 大区块/页面边距 |
| `--space-7` | `48dp` | 页面级留白 |

- 所有间距取 4 的倍数；区块间优先 16/24/32。
- 内容左右边距 `--space-6` (32dp) 起步，窄屏可降为 24dp。

---

## 5. 字体与排版（圆润友好）

- **字族（优先圆体）**：英文/数字 `Nunito` / `Quicksand` / `Baloo 2`；中文 `思源柔黑` / `站酷快乐体`（需确认授权后引入字体文件）。
- **默认回退**：系统圆体 → Material 3 默认无衬线（避免未引入字体时崩坏）。
- **字号阶梯**：`12 / 14 / 16 / 20 / 24 / 32 / 40 dp`。
- **字重**：Regular(400) / Medium(500) / SemiBold(600) / Bold(700)；标题用 600，正文 400。
- **行高**：正文 1.5×，标题 1.2×。
- **禁止**：默认 Roboto 作为主视觉字（偏中性、不可爱）；字间距过度拉伸。

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
- `--elevation-1`：`0 1dp 2dp rgba(0,0,0,0.04), 0 2dp 8dp rgba(0,0,0,0.06)`
- `--elevation-2`：`0 4dp 12dp rgba(0,0,0,0.06), 0 8dp 24dp rgba(0,0,0,0.08)`
- **描边**：低对比 1dp `--outline`，用于分隔而非硬阴影。
- **可加极轻渐变/玻璃质感**，但保持单调克制（如卡片顶部 2% 白渐变高光）。

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

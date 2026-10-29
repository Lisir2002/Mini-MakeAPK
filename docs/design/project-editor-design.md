# 项目模型与编辑器设计（统一内核 · 落地设计）

> 状态：设计已收口（2026-10-26）；阶段 0「统一内核契约」已实现中（全本地注入，见 §11）。
> 承接：[`docs/ARCHITECTURE.md`](../ARCHITECTURE.md) 与"统一内核"讨论结论（Project / ProjectEntry / WebSettings / WebRuntimeConfig）。
> 约束对齐：[`PROJECT_BRIEF.md`](../../PROJECT_BRIEF.md) §1/§2、[`DESIGN_LANGUAGE.md`](../../DESIGN_LANGUAGE.md)。

---

## 1. 目标与范围

- 新增**「添加项目」子页**：分类选择 + 基本信息填充 + **顶栏右侧「保存」**。
- 新增**「项目编辑」页**：顶部 2/5 基本信息区（**可收起**）+ 底部 3/5 三 Tab（基本配置 / 进阶配置 / 高级配置，**仅占位**）。
- 锁定**数据格式**（Project 领域模型）与**路径/路由设计**，承接前一轮"统一内核"结论。
- 与现有 `BuildRecord` 的关系：先抽契约、后拆表（见 §5、§7）。

---

## 2. 数据模型

### 2.1 版本段式（关键：与 MMA 自身版本严格区分）

| 版本归属 | 格式 | 约束来源 |
|---|---|---|
| **MMA 自身**（你正在用的这个 App） | 4 段式 `MAJOR.MINOR.PATCH.BUILD[-rcN]` | `PROJECT_BRIEF.md` §1 锁定；CI 强制 `tag == versionName` |
| **导出的 App**（用户创建的项目） | 用户自选 **2 段 `1.0`** / **3 段 `1.3.8`** | 本设计新增，独立于 MMA 自身 |

> ✅ 已确认（2026-10-26）：导出 App 版本独立于 MMA 自身 4 段式，由用户选 2/3 段式。

```kotlin
/** 导出 App 的 versionName 段式（与 MMA 自身 4 段式无关）。 */
enum class VersionScheme { TWO_SEGMENT, THREE_SEGMENT }
```

- `versionName: String` — 按 scheme 生成或手填，如 `"1.0"` / `"1.3.8"`。
- `versionCode: Int` — 单调递增整数，用于升级判定（Android 要求 int）。

### 2.2 Project（统一内核 · 稳定定义）

```kotlin
data class Project(
    val id: Long = 0,
    val projectType: ProjectType,          // 分类/项目类型（URL/HTML/VUE/PHP），保存后锁死
    val apkName: String,                  // 应用显示名
    val packageName: String,              // 导出 APK 的 applicationId，如 com.user.myapp
    val versionScheme: VersionScheme,     // 段式
    val versionName: String,              // 按 scheme 生成，如 "1.0" / "1.3.8"
    val versionCode: Int,                 // 单调整数，升级判定
    val iconRef: String?,                 // 应用图标 uri / asset 引用
    val themeColor: Int,                  // ARGB，导出 App 主题 / 启动页底色
    val entry: ProjectEntry,              // 入口（见 §2.3）
    val webSettings: WebSettings,         // 渲染配置（见 §2.4）
    val isolationId: String,              // 本地多实例 data dir suffix
    val createdAt: Long,
    val updatedAt: Long,
)
```

### 2.3 ProjectType（分类 / 项目类型）与 ProjectEntry（入口）

**ProjectType（分类 / 项目类型）**——添加页横向准星选择器四项，保存后锁死：

```kotlin
enum class ProjectType { URL, HTML, VUE, PHP }   // 分类：URL / HTML / VUE / PHP
```

**ProjectEntry（实际入口引用）**——由 ProjectType 决定形态：

```kotlin
sealed interface ProjectEntry {
    val rawRef: String
    data class Url(val url: String) : ProjectEntry
    data class BundledHtml(val html: String) : ProjectEntry
    data class Archive(val assetRef: String, val kind: ArchiveKind) : ProjectEntry
}
enum class ArchiveKind { VUE, PHP, GENERIC }
```

| ProjectType | entry 形态 | 引擎 | 说明 |
|---|---|---|---|
| `URL` | `Url(url)` | 本地注入（设备端 WebView 封装） | 任意网址即时封装 |
| `HTML` | `BundledHtml(html)` | 本地注入（资产注入） | 粘贴 / 上传 HTML |
| `VUE` | `Archive(kind=VUE)` | 本地注入（资产注入 APK） | Vue 构建产物打包进 APK，本地 WebView 加载 |
| `PHP` | `Url`（托管站点）或 `Archive`（静态化后资产注入） | 本地注入 | PHP 走托管网址；或静态化后资产注入 |

> 原 `BuildInputType { URL, HTML, ARCHIVE }` 演进为 `ProjectType`；`Pwa`（自有 PWA → TWA）作为后续扩展类型，本期不在准星四项内。**`BuildEngine.CLOUD` 已废弃：全部改为本地注入，不再依赖云端构建（见 §11）。**

### 2.4 WebSettings（渲染配置，本地与导出共用）

```kotlin
data class WebSettings(
    val javascriptEnabled: Boolean = true,
    val domStorageEnabled: Boolean = true,
    val thirdPartyCookies: Boolean = true,   // Android 11+ 默认关 → OAuth/支付死循环坑
    val cacheMode: Int = WebSettings.LOAD_DEFAULT,
    val userAgent: String? = null,
    val fitToApp: Boolean = true,            // 自有域 WebView；外链 Custom Tabs
)
```

### 2.5 WebRuntimeConfig（唯一共享契约）

`Project` 去掉 DB 专属字段（`id` / `createdAt` / `updatedAt`）后的**可 JSON 序列化体**：
- 本地渲染器从 `Project` 构造它；
- 导出时写进模板壳 `assets/runtime-config.json`，壳内 WebView 代码与 App 内同源。

这是"本地容器 + 导出 APK"两者都要的**并集载体**。

---

## 2.6 字段校验与默认值

| 字段 | 必填 | 校验规则 | 默认值 / 自动建议 |
|---|---|---|---|
| `projectType` | 是 | 四项之一（准星选） | 无（必须选；保存后锁死） |
| `apkName` | 是 | 去空格后非空；≤50 字符（>30 警告）；禁控制符/换行 | 自动建议：URL→域名；HTML/Archive→文件名（可改） |
| `packageName` | 是 | `^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$`；≥2 段；禁 `android.`/`java.`/`com.android.`/`com.google.` 前缀；≤255 | 自动建议：URL→反向域名（`example.com`→`com.example`）；**同包名重复→强制拦截（禁止保存）** ✅ |
| `versionScheme` | 是 | `TWO_SEGMENT` / `THREE_SEGMENT` | `THREE_SEGMENT`（✅ 已确认默认） |
| `versionName` | 是 | 跟随 scheme：`^\d+\.\d+$`（二段）/`^\d+\.\d+\.\d+$`（三段）；每段 0–999 | `1.0.0`（三段）/ `1.0`（二段） |
| `versionCode` | 是 | 正整数 ≥1，≤2147483647 | `1`；同包名再导出建议 +1 |
| `iconRef` | 否 | 支持 PNG（推荐 512×512，最小 192）；非 PNG 警告可缩放 | 生成占位：themeColor 底 + apkName 首字母 |
| `themeColor` | 否 | ARGB 整数 | 设计语言主色珊瑚粉 `OKLCH(0.78 0.10 25)` ≈ `#E39E97` |
| `entry` | 是 | URL→`https?://` 合法且非 `javascript:` 等危险 scheme；HTML→非空且含标签；Archive→zip 存在且 ≤100MB | 由 projectType + 来源输入派生，保存后锁死 |
| `webSettings` | 否 | — | JS=true / DOM=true / 第三方Cookie=true / cacheMode=LOAD_DEFAULT / UA=null / fitToApp=true |
| `isolationId` | 系统 | 仅 `[a-z0-9_]`、≤64 | `webapp_<id>`（本地多实例数据隔离用） |
| `createdAt` / `updatedAt` | 系统 | epoch ms | 创建 / 更新时写入 |

**校验时机**：添加页各字段 `onValueChange` 实时校验，错误以 Material3 `isError` + **人话**支持文本就地提示；**顶栏「保存」仅在全部必填合法时可点**。

---

## 3. 导航 / 路由设计

现有路由（`MmaRoute`）：`projects` / `building/{id}` / `result/{id}` / `history` / `preview/{id}`。

新增两条：

| 路由 | 页面 | 取数 | 骨架 |
|---|---|---|---|
| `add`（或 `project/new`） | 添加项目子页 | 表单本地状态 + 保存时落库 | 详情（仅顶栏，含右侧保存） |
| `project/{id}`（或 `project/{id}/edit`） | 项目编辑页 | `ProjectRepository.getById(id)` | 详情（仅顶栏，含收起态） |

- 首页 `projects` 的「＋」→ 打开 `add`（替代原 rc4 直接粘贴即构建）。
- `add` 保存成功 → `popBackStack` 回 `projects`，列表出现新项。
- 列表项点击 → `project/{id}` 编辑页。

---

## 4. 页面设计

### 4.1 添加项目页（`add`）

- **顶栏**：标题「新建项目」+ **右侧「保存」**（`enabled` 仅当必填校验通过：项目类型已选 / apkName / packageName 格式 / versionCode）。
- **主体（滚动）**：
  1. **项目类型 / 分类（横向准星选择器，保存后锁死）**：
     - 候选项：**URL / HTML / VUE / PHP**；滑动停止时卡在准星下的项即"预选项"。
     - 选中后下方出现对应来源输入：URL→填网址；HTML→粘贴 / 上传 HTML；VUE→上传工程 zip；PHP→上传 zip 或填托管网址。
     - 保存后 `ProjectType` 与对应 `entry` 一并锁死，编辑页不可改（更换须新建项目）。
  2. **基本信息**：
     - APK 名称（文本）
     - 包名（文本 + 格式校验 `^[a-z][a-z0-9_]*(\.[a-z0-9_]+)+$`）
     - 版本段式（二段 / 三段 切换）
     - 版本号（versionCode，整数）
     - 应用图标（选择 / 上传，`iconRef`）

### 4.2 项目编辑页（`project/{id}`）— 2/5 + 3/5 切割 + 可收起

```
┌─────────────────────────────────────┐
│  顶部 2/5：基本信息卡                │ ← 图标/名称/包名/版本/分类
│  （右侧「收起 ▲」开关）              │
├─────────────────────────────────────┤
│  [基本配置] [进阶配置] [高级配置]     │ ← TabRow（位于底部区内顶部，不压上面）
├─────────────────────────────────────┤
│  底部 3/5：当前 Tab 内容（占位）      │
│                                       │
└─────────────────────────────────────┘
   收起后：顶部区高度→0，底部 weight(1f) 顶满 100%
```

**实现要点（Compose）**：

- 外层 `Column(Modifier.fillMaxSize())`。
- 顶部：`Box(Modifier.fillMaxHeight(0.4f))`（或用 `weight` + 动画），内放基本信息卡；角上「收起」`IconToggleButton`。
- 底部：`Column(Modifier.weight(1f))` → `TabRow`（3 tab，占位 `Text`） + 内容 `Box`（占位）。
- **收起**：顶部 `AnimatedVisibility(visible = !collapsed)` + `shrinkVertically()`；底部 `weight(1f)` 自动顶满。TabRow 因在底部 Column 内、位于顶部区之下，随之上移，**不压住**顶部区——满足"上部分收起后动态移动上去"。

---

## 5. 与统一内核 / 现有模型的关系

- 前一轮结论：**先抽 `WebRuntimeConfig` 契约、改造 `WebViewScreen` 消费它**（D1 契约先行），`Project` / `ExportRun` 拆表作为下一小步。
- 现有 `BuildRecord`（含 `status` / `engine` / `outputApkPath` 等运行态）→ 重命名为 **`ExportRun`**（每次导出一条），与稳定 `Project` 分离。
- `Project` 一经保存即落库；编辑页所有改动写回 `Project`；"导出"动作另生成 `ExportRun`。

---

## 6. 开放问题（待确认）

- ~~Q1 · 版本段式语义~~ → ✅ 已确认：导出 App 版本独立于 MMA 自身 4 段式，用户选 2/3 段。
- ~~Q2 · 入口 entry 落点~~ → ✅ 已确认：仅添加页可设、保存后锁死；UI 为横向滑动列表 + 居中准星（见 §4.1）。
- ~~Q3 · "分类"指什么~~ → ✅ 已确认：分类 = 项目类型 = `URL / HTML / VUE / PHP`，即添加页横向准星选择器四项，保存后锁死，与 entry 入口同源（见 §2.3、§4.1）。

### 6.1 字段校验 / 默认值 子决策（已确认）

- 版本段式默认：`THREE_SEGMENT`，`versionName` 默认 `"1.0.0"`。
- 应用图标：**允许默认占位**（themeColor 底 + apkName 首字母），不强求上传。
- 跨项目包名重复：**强制拦截**（校验不通过、禁止保存），避免导出 APK 互相覆盖。

---

## 7. 落地顺序（建议）

1. 锁定数据模型 + 确认 §6 三问。
2. 抽 `WebRuntimeConfig` 契约，改造 `WebViewScreen` 消费它（D1 契约先行）。
3. 实现「添加项目」页 + 路由 `add`（含顶栏右侧保存、必填校验）。
4. 实现「项目编辑」页（2/5–3/5 + 三 Tab 占位）+ 路由 `project/{id}`。
5. DB 拆分：`Project` 表 + `ExportRun` 表（D1 第二步）。

---

## 8. 持久化与 DB 迁移设计

### 8.1 现状（务必对齐）
- 库名 `web2apk.db`，**当前 version = 2**；表：`build_records`（`BuildRecordEntity`）+ `log_entries`。
- 迁移链：`MIGRATION_1_2`（加日志表）。`AppDatabaseFactory` 当前**仍启用 `fallbackToDestructiveMigration()`**（rc4 为防闪退加回，与 `AppDatabase.kt` 注释"已移除"不一致，待清理）。
- `BuildRecordEntity` 字段：`id/createdAt/updatedAt/inputType(BuildInputType)/sourceLabel/sourceRef/engine(BuildEngine)/status(BuildStatus)/appName/packageName/outputApkPath/apkSizeBytes/durationMs/errorMessage`。

### 8.2 目标 schema（version 3）
- **`projects`**（稳定定义）：`id, createdAt, updatedAt, projectType, apkName, packageName, versionScheme, versionName, versionCode, iconRef, themeColor, entry_json, webSettings_json, isolationId`。
  - `entry_json` / `webSettings_json`：TEXT 存 JSON（对应 `ProjectEntry` / `WebSettings`，与 `WebRuntimeConfig` 同源序列化）。
  - 索引：`createdAt`；`packageName`（唯一性拦截：应用层校验 + 可选 UNIQUE）。
- **`export_runs`**（每次导出一条，承接旧 `BuildRecord` 运行态）：`id, projectId(FK→projects.id), createdAt, updatedAt, status, outputApkPath, apkSizeBytes, durationMs, errorMessage`。
  - 索引：`(projectId, createdAt)`、`(status, createdAt)`。
  - 不再含 `engine` 字段（导出统一走本地注入，见 §11）。

### 8.3 迁移策略（待 §8.5 决策）
- `MIGRATION_2_3`：建 `projects` + `export_runs`；逐行搬运 `build_records`；**`build_records` 暂不 DROP**（见下方「实施调整」）。
- 搬运映射：
  - `projectType` ← `inputType`：URL→URL，HTML→HTML，**ARCHIVE→VUE（默认，见 §8.5）**。
  - `apkName` ← `appName`（空则 `sourceLabel`）；`packageName` ← 原值（空则生成占位 `com.make.user.app<id>` 满足 NOT NULL）。
  - `versionScheme/versionName/versionCode` ← 默认 `THREE_SEGMENT` / `1.0.0` / `1`。
  - `entry_json` ← 由 `inputType`+`sourceRef` 构造（`Url(sourceRef)` / `BundledHtml(sourceRef)` / `Archive(sourceRef,VUE)`）。
  - `webSettings_json` ← 默认全集；`isolationId` ← `webapp_<id>`；`themeColor` ← `#E39E97`。
  - `export_runs`：整行搬 `status/outputApkPath/...`，`projectId = build_records.id`（不含 `engine`）。
- ⚠️ 早期 rc4 用户 `build_records` 几乎为空，数据保全优先级低；见 §8.5 可走"破坏性重建"捷径。

> **实施调整（阶段 1 落地时确定）**：`build_records` **不 DROP**，改为**复制**——
> legacy 表原样保留，同时把每行**复制**一份到 `projects` + `export_runs`。
> 原因：当前 UI（`ProjectsScreen` / `HistoryViewModel` / `MmaNavHost`）仍直查 `build_records`；
> 若在数据层就删表，v3 一上线旧界面立即查表失败。
> 待**阶段 2/3 调用点切到新模型后**，由 **v4 迁移删表**——顺序与 §8.4「调用点逐步退役」一致，且数据零丢失。
> 另：搬运用 **Kotlin + 真正的 JSON 序列化器**（而非纯 SQL 字符串拼接），
> 既避免手工转义引号/换行，也避免依赖 `json_extract()` 的 JSON1 扩展（minSdk 24 不保证启用）。

### 8.4 配套改动
- 新增领域模型 `Project` / `ExportRun`；实体 `ProjectEntity` / `ExportRunEntity`；DAO `ProjectDao` / `ExportRunDao`；mapper；转换器（加 `ProjectType`/`VersionScheme`/`ExportStatus` + entry/webSettings 的 JSON 转换）。
- 仓储：`ProjectRepository`（CRUD + 观察）+ `ExportRunRepository`（按 projectId 观察/写入）。
- 调用点更新：`ProjectsScreen` 改显 `Project` 列表；`BuildHistoryRepository`/`BuildRecord` 逐步退役。
- 测试：v2 → v3 数据不丢、`exportSchema` 的 `3.json` 与实体声明一致。
  - **实施方式**：未用官方 `MigrationTestHelper`（其构造函数首参为 `android.app.Instrumentation`，且从 **assets** 读 schema JSON——Robolectric 单测两者均不可得）；
    改用**三重等价校验**：① Room 真实执行迁移并触发运行期 `onValidateSchema` 强校验（不一致即抛 `IllegalStateException`，等价线上闪退）；
    ② 与「Room 按实体声明新建的 v3 库」逐表逐列比对（PRAGMA `table_info`/`index_list`/`foreign_key_list`）；
    ③ 与 KSP 导出的 `3.json` 逐字比对 DDL（去空白后严格相等）。

### 8.5 已确认决策

- **D-M1 迁移方式** → ✅ 显式 `MIGRATION_2_3` 逐行保全（建 `projects`+`export_runs`、搬运；`build_records` **保留为 legacy 表**，见 §8.3 实施调整，待阶段 2/3 后由 v4 删表）。早期数据为空，风险极低，仍走规范迁移链。
- **D-M3 entry/webSettings 存储** → ✅ JSON 文本列（`entry_json` / `webSettings_json`），与 `WebRuntimeConfig` 同源序列化。
- **D-M2 旧 `ARCHIVE` 映射（最全面最稳定设计）** → ✅ 两阶段、零信息丢失：
  1. **迁移阶段（SQL）**：ARCHIVE → 暂置 `projectType = VUE`（安全默认），但**原始 `inputType` + `sourceRef` 完整保留进 `entry_json`**（`Archive(assetRef=旧sourceRef, kind=GENERIC)`），信息不丢。
  2. **升级后一次性对账（Kotlin `ArchiveClassifier`，首启运行一次）**：对仍存在的归档文件嗅探框架——含 `package.json`(vue/react)→VUE、含 `.php`/`composer.json`→PHP，纠正 `projectType`；文件已失联者维持 VUE 默认但 `entry_json` 仍可恢复。
  3. 因对账后必然落入 VUE/PHP 之一，**不新增 `ProjectType` 第五值**，准星选择器四项 UI 不受影响。

---

## 9. 准星选择器交互设计（项目类型 / 分类）

### 9.1 形态
- 横向滚动的 4 项（URL / HTML / VUE / PHP），每项 = 图标 + 中文标签的卡片。
- 视口**正中固定一条准星**（竖线 / 光晕 / 括号），滑动停止后卡在准星下的项即"预选项"。
- 选中项（位于准星下）：主色高亮 + 放大 + 加粗；其余项灰暗。

### 9.2 两种实现路线（待 §9.4 决策）
- **路线 A · HorizontalPager（推荐）**：`androidx.compose.foundation.pager.HorizontalPager`，`pagerState.currentPage` 天然等于"准星下的项"，吸附 / 键盘 / 无障碍免费获得；用 `contentPadding` 让左右邻居露出半截（carousel 观感）。
- **路线 B · LazyRow + rememberSnapFlingBehavior**：手动控制间距 / peek / 回弹，灵活但吸附与无障碍需自写。

### 9.3 交互细则
- **吸附**：滑动停止自动回弹对齐到准星中线最近的 item（弹簧 / 衰减动画）；尊重系统"减少动效"。
- **无障碍**：每项 `clickable` + `contentDescription("项目类型：网址")`；点击 item 直接选中并居中；DPad / 方向键左右切换（路线 A 由 Pager 原生支持）；选中态播报。
- **与来源输入联动**：选中项决定下方来源输入形态（URL→填网址 / HTML→粘贴上传 / VUE→传 zip / PHP→传 zip 或托管网址）。切换类型时来源输入处理见 §9.4。
- **锁定态（编辑页）**：`projectType` 已锁死，选择器不滚动、不交互，仅居中展示当前类型。
- **初始默认**：添加页打开默认选中 **URL**（首个），居中。

### 9.4 已确认决策
- **D-C1 实现路线** → ✅ `HorizontalPager`：`pagerState.currentPage` 即准星下选中项；`contentPadding` 露邻居；吸附 / 键盘 / TalkBack 原生支持。
- **D-C2 邻居预览** → ✅ 显示左右邻居（peek），强化滚轮 / 拨轮观感。
- **D-C3 切换类型时来源输入** → ✅ 切换即清空重置（entry 锁定单一类型，避免错类型脏数据）。

---

## 10. 三 Tab 内容规划（项目编辑页）

### 10.1 分区原则
- **顶部 2/5 卡** = 身份（图标 / 名称 / 包名 / 版本 / 类型），可就地编辑，仅 `projectType` 锁死。
- **三 Tab** = 行为配置，不重复身份信息，按「基础（全部 Web 运行时设置）→ 进阶（模版应用自定义，后续扩展）→ 高级（导出 / 隔离 / 危险操作）」递进。

### 10.2 各 Tab 内容

| Tab | 字段 | 对应数据 | 形态 |
|---|---|---|---|
| **基本配置** | JavaScript 开关 | `webSettings.javascriptEnabled` | Switch |
| | DOM Storage 开关 | `webSettings.domStorageEnabled` | Switch |
| | 应用内打开方式 | `webSettings.fitToApp` | 单选：WebView / Custom Tabs |
| | 第三方 Cookie | `webSettings.thirdPartyCookies` | Switch（提示：OAuth / 支付需开） |
| | 缓存模式 | `webSettings.cacheMode` | 下拉：默认 / 优先缓存 / 不缓存 / 仅缓存 |
| | 用户代理 UA | `webSettings.userAgent` | 默认 / 桌面 / 自定义输入 |
| | 主题色 | `themeColor` | 颜色选择（默认珊瑚粉 `#E39E97`） |
| **进阶配置** | 模版应用自定义（占位） | — | 主题 / 顶栏 / 底栏 / tab 槽位控制（见 §10.5） |
| **高级配置** | 导出方式 | — | 固定展示「本地注入（设备端 WebView 封装 / 资产注入）」，无选择（见 §11） |
| | 本地隔离 | `isolationId` | 只读：`webapp_<id>` |
| | 导出格式 / 签名 | — | 占位（APK/AAB、调试 / 正式签名），待 Phase 2 |
| | 危险操作 | — | 删除项目（红色，二次确认） |

> 原「WebSettings 进阶三项（第三方 Cookie / 缓存 / UA）」已并入**基本配置**；**进阶配置** Tab 改为承载后续「模版应用」自定义能力（§10.5）。

### 10.3 与数据模型关系
- 基本 / 进阶 Tab 反向锁定 `WebSettings` 六字段（§2.4）全可配。
- 高级 Tab 的 `engine` 与 §2.3 映射一致；`isolationId` 由 §2.6 生成。
- 顶部卡可编辑的 名称 / 包名 / 版本 复用 §2.6 校验规则（包名改后仍需唯一性拦截）。

### 10.4 已确认决策
- **D-T1 引擎可覆盖** → ✅ 由类型推导并锁定（URL/HTML→设备端 WebView 封装；VUE/PHP→本地注入·资产注入 APK），高级配置仅展示不可改（见 §11）。
- **D-T2 顶部卡可编辑** → ✅ 身份信息（名称 / 包名 / 版本 / 图标）在顶部卡就地编辑，仅 `projectType` 锁死；改名改包名复用 §2.6 校验。
- **D-T3 themeColor 落点** → ✅ 在基本配置 Tab 用颜色选择器编辑；顶部卡仅展示。

### 10.5 进阶配置 =「模版应用」方向（前瞻，本期占位）

- **意图**：MMA 的项目不止是"网址包装"，而是一个**可自定义模版应用**——用户基于模版配置 App 外壳：主题、顶栏、底栏、tab 槽位等。
- **本期**：进阶配置 Tab 仅占位，不实现具体控件；但 Tab 位置与命名已为后续预留。
- **后续数据模型预留**：`Project` 未来可增 `templateConfig`（主题覆盖 / 顶栏配置 / 底栏配置 / tab 槽位配置），与 §2 统一内核并存，不影响 `WebSettings` / `entry` 既有字段。
- **槽位（slot）概念**：顶栏槽、底栏槽、tab 槽 —— 这些槽位未来可由模版或用户开关控制显隐与内容，是「模版应用」的核心抽象。

### 10.6 HTML 项目形态：入口文件 + 项目代码（rc9 · 类型隔离）

> 背景：HTML 类型此前与 URL 共用"入口即全部"的同一套 UI（新建/编辑都直录大段代码），
> 类型隔离缺失。rc9 起按类型深度绑定，通用处保持适配。

#### 四类型隔离对照表

| | URL | HTML | VUE / PHP |
|---|---|---|---|
| 入口形态 | `ProjectEntry.Url` | `ProjectEntry.BundledHtml(html, entryFile)` | `ProjectEntry.Archive` |
| 新建页来源输入 | 网址（必填） | **入口文件**（可选，默认 `index.html`）；**不再直录代码** | 占位卡（存档拾取未实现） |
| 基本配置入口字段 | 入口网址（可改） | **入口文件**（可改） | 只读锁死 |
| 「项目代码」页 | **隐藏**（隔离） | **显示**：文件卡（入口文件名 + 「入口」徽标）+ 代码卡（源码编辑） | 隐藏（待存档接入） |
| 代码兜底 | — | 空 → 内置默认模板 `DEFAULT_TEMPLATE`（开箱能跑） | — |
| 渲染方式 | `loadUrl` | `loadDataWithBaseURL`（`entryFile` 为元数据） | 解包后 `file://` |

#### 契约要点

- `BundledHtml.entryFile` 带默认值 `index.html`：老数据反序列化兼容，行为不变。
- 「项目代码」页（`EditTab.CODE`）位置在**进阶配置之前**；可见类型由 `EditTabRow`
  按 `projectType` 表驱动过滤（`CODE_TAB_TYPES`），VUE/PHP 存档拾取实现后一行加入。
- 校验分工：入口文件名走 `validateEntryFile`（禁路径成分、限 `.html/.htm`，空=放行）；
  源码留空不阻断（保存兜底默认模板）。
- URL / HTML 互不出现对方专属字段；Web 配置 / 壳配置 / 主题色等通用字段全类型共享。

---

## 11. 架构变更：废弃云端引擎（全本地注入）

> 状态：已确认（2026-10-26），用户明确要求"抛弃云端引擎的构建，我们不考虑云端注入，一切都从本地注入出发"。

### 11.1 决策

- **彻底废弃 `BuildEngine.CLOUD`**：不再依赖任何云端构建服务（无云端真编译、无云端注入）。`BuildEngine` 枚举中 `CLOUD` 仅保留作迁移期兼容标注 `@Deprecated`，新代码不得再使用；待 Stage 1 以 `Project` 模型取代 `BuildRecord` 后整体移除。
- **全部走本地注入**，两条具体路径：
  1. **设备端 WebView 封装**（URL / 通用 HTML）：App 内直接用本地 WebView 加载，预览即出包路径。
  2. **资产注入模板壳 APK + 本地重签**（Vue 工程 / PHP 静态化 / 打包物）：将前端资产注入预置的模板壳 `assets/`，由设备端 `apksigner` 本地重签生成独立可安装 APK。**无任何云端参与**。

### 11.2 对既有设计的影响

| 位置 | 变更 |
|---|---|
| §2.3 引擎映射表 | 四项入口的"引擎"列统一改为「本地注入（设备端 WebView 封装）/ 本地注入（资产注入 APK）」 |
| §8.2 `export_runs` 表 | **移除 `engine` 字段**（导出统一走本地注入，无引擎分支） |
| §8.3 迁移描述 | 搬运 `build_records` → `export_runs` 时不再搬运 `engine` |
| §10.2 高级配置「导出方式」 | 固定展示「本地注入（设备端 WebView 封装 / 资产注入）」，无下拉选择 |
| §10.4 D-T1 | 引擎由类型推导并锁定，VUE/PHP 不再写"云端"，改为"本地注入·资产注入 APK" |
| 代码层 `BuildRecord.kt` | `BuildEngine.CLOUD` 标注 `@Deprecated(WARNING)`，`BUILD_API` 相关云端契约文档标记为历史 |

### 11.3 与 Phase 2 的关系

- 原 `PROJECT_BRIEF` §7「待补：云端构建服务实现（Phase 2）」**作废**——Phase 2 不再指云端构建，而是指**本地注入能力的完善**（解包/重签管线、资产注入模板壳工程化、多实例隔离落地）。
- `WebViewScreen` 统一消费 `WebRuntimeConfig`，本地渲染与导出壳共用同一契约，从架构上切断"本地 vs 云端"的二元分裂。

### 11.4 数据契约

- `WebRuntimeConfig`（`Project` 去掉 DB 字段后的可 JSON 序列化体）同时服务于：①本地 WebView 渲染；②导出模板壳 `assets/runtime-config.json`。两者同源、同序列化器。
- 旧 `docs/build-api-contract.md`（云端构建契约）标记为历史文档，不再作为实现依据。

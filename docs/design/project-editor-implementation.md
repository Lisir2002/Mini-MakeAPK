# 实现总览：项目编辑器（添加/编辑页 + 数据模型 + DB 迁移）

> 来源设计：[`project-editor-design.md`](./project-editor-design.md)（§1–§10）。
> 本文件为实现对照表，**逐条打勾**跟踪。阶段顺序遵循"契约先行 → 数据层 → 导航 → 页面 → 质量门禁"。

---

## 阶段 0：统一内核契约（D1 契约先行，§2.3–§2.5）

> ✅ 已落地并验证（2026-10-26）：`compileDebugKotlin` + `compileDebugUnitTestKotlin` 全绿；受影响 Paparazzi 快照测试（WebViewScreenTest）通过，确认重构为纯内部改动、像素级无漂移。

- [x] 定义 `WebRuntimeConfig`：`Project` 去掉 DB 字段（`id`/`createdAt`/`updatedAt`）后的可 JSON 序列化体，本地渲染器与导出模板壳共用（§2.5）—— `data/model/WebRuntimeConfig.kt`，`@Serializable`
- [x] 改造 `WebViewScreen` 消费 `WebRuntimeConfig`（本地渲染统一入口）—— `ui/screens/WebViewScreen.kt`，按 entry 类型加载、按 webSettings 应用配置
- [x] 定义领域枚举/数据类：`ProjectType{URL,HTML,VUE,PHP}`（§2.3）、`VersionScheme`、`ProjectEntry`（Url/BundledHtml/Archive）、`ArchiveKind`、`WebSettings`（§2.4）—— 均置于 `data/model/` 且 `@Serializable`
- [x] 构建配置引入 `kotlinx-serialization`（插件 + `kotlinx-serialization-json`，版本锁 1.6.3 兼容 Kotlin 1.9.24）；`BuildEngine.CLOUD` 标注 `@Deprecated`（§11 全本地注入）
- [x] 桥接：`WebViewScreen` 消费 `WebRuntimeConfig`（本地渲染统一入口）；`PreviewScreen`（旧 `BuildRecord.toWebRuntimeConfig()` 临时桥）已随阶段 5 退役，`WebViewScreen` 保留为后续预览复用纯组件

## 阶段 1：数据层（§2 / §8）

> ✅ 已落地并验证（2026-10-26）：`compileDebugKotlin` + `kspDebugKotlin` + `testDebugUnitTest` 全绿
> （**129/129 通过**，含新增 35 例：迁移 10 / 映射 14 / 分类器 11）。
> 迁移脚本已由「Room 真实执行 + 运行期 schema 强校验 + 与 `3.json` 逐字比对」三重验证。

- [x] 领域模型 `Project`（§2.2）+ `ExportRun`（§8.2）—— `data/model/Project.kt`、`data/model/ExportRun.kt`；默认值统一收敛到 `ProjectDefaults`（单一事实源）
- [x] 实体 `ProjectEntity` / `ExportRunEntity`（§8.2 字段；`entry_json`/`webSettings_json` 为 TEXT）—— `data/local/entity/`
- [x] DAO `ProjectDao` / `ExportRunDao`（CRUD + 观察 Flow + 索引：`createdAt`、`packageName`、`(projectId,createdAt)`、`(status,createdAt)`）
- [x] Mapper（`entity ⇄ domain`）—— `data/mapper/`；JSON 解码失败**降级不抛异常**（入口降级为 `BundledHtml("")`，配置降级为默认 `WebSettings`）
- [x] Converters：新增 `ProjectType`/`VersionScheme`/`ExportStatus`（各带兜底值）；`entry`/`webSettings` 的 JSON 转换由 `RuntimeJson` 承担（§8.2/§8.3）
- [x] `AppDatabase` version 2 → 3，登记 `projects`/`export_runs` 实体；`AppDatabaseFactory.MIGRATIONS` 登记 `MIGRATION_1_2` + `MIGRATION_2_3`
- [x] `MIGRATION_2_3`：建 `projects`+`export_runs` → 逐行搬运 `build_records`（§8.3）
      - ⚠️ **调整**：**不 DROP `build_records`**——当前 UI（`ProjectsScreen`/`HistoryViewModel`/`MmaNavHost`）仍直查该表，
        数据层删表会导致 v3 上线当刻旧界面立即崩。改为**复制**（legacy 表保留原样 + 复制一份到新表），
        待阶段 2/3 调用点切到新模型后由 **v4 迁移删表**。数据零丢失，顺序与实现总览一致。
      - 搬运用 **Kotlin + `RuntimeJson` 逐行处理**（非纯 SQL）：避免手工转义，且不依赖 JSON1 扩展（minSdk 24 不保证启用）
- [x] 旧 `ARCHIVE` 两阶段映射：迁移时暂置 VUE 且 `entry_json` 保留原始 `sourceRef` + `kind=GENERIC`；
      `ArchiveClassifier`（`data/classifier/`）首启一次性嗅探纠正为 VUE/PHP（§8.5 D-M2），由 `ProjectRepository.reconcileArchiveKinds()` 驱动
- [x] `ProjectRepository` + `ExportRunRepository`（§8.4）—— 包名唯一性在应用层强制拦截（**不建 UNIQUE 索引**：历史库可能已有重复包名，加索引会让迁移失败）
- [x] **移除 `fallbackToDestructiveMigration()`**（恢复 PROJECT_BRIEF §7 的显式迁移要求）
      —— 阶段 5 落地：`Migration_3_4_Test` + 改写后 `Migration_2_3_Test` 三重校验已覆盖全升级路径，
      显式迁移链 1→2→3→4 完整、零数据丢失，故移除自愈回退（`AppDatabaseFactory.kt` 已注释回退开关）
- [x] 调用点迁移后退役 `BuildHistoryRepository` / `BuildRecord`（含 v4 迁移删 `build_records` 表）
      —— 阶段 5 落地：`BuildHistoryRepository`/`BuildRecord`/`BuildViewModel`/`HistoryViewModel` 及
      `BuildingScreen`/`ResultScreen`/`PreviewScreen` 共 10 文件删除；`MIGRATION_3_4` DROP 冗余 legacy 副本，零数据丢失

## 阶段 2：导航与 ViewModel（§3）

> ✅ 已落地并验证（2026-10-26）：`compileDebugKotlin` + `testDebugUnitTest` **287/287 全绿**（既有 129 + 工程师新增 92 + QA 补测 66），
> `verifyPaparazziDebug` **37 张快照零漂移**（`ProjectsScreen` 新增可选参数后渲染像素级不变）。
> QA 独立审查发现并已修复 1 项阻断缺陷（版本号超长段位整数溢出逃逸出纯函数校验器 → 用户输入即崩）。

- [x] `MmaRoute` 新增 `add` / `project/{projectId}` + `ARG_PROJECT_ID` + `RESULT_CREATED_PROJECT_ID` / `RESULT_UPDATED_PROJECT_ID`（`savedStateHandle` 回传契约）
- [x] `MmaNavHost` 接线：首页「＋」→ `add`；两条新路由均为**流程子页**（不传底栏）；非法 `projectId` → `popBackStack()` 退回列表页
- [x] `AddProjectViewModel`：表单状态 + 实时校验（§2.6 全量规则）+ 保存落库
      —— 校验规则集中于 `ui/editor/ProjectValidator.kt`（**纯函数、零 `android.*` 依赖、中文文案单一来源**）
      —— 包名占用：300ms 防抖（`Job`+`delay`，非 `Flow.debounce`）+ 仅格式合法才查库 + `save()` 权威复校
      —— 切换项目类型 → **清空** url/html/archive 三种来源；切换版本段式 → **重置** `versionName`（`versionCode` 不变）
      —— `projectType` 初值 `null`（必须显式选过才能保存）；`isolationId` 由仓储回填，VM 不自造
- [x] `ProjectEditViewModel`：加载 `Project` + 更新字段（含包名改后唯一性重校验，**排除自身**）
      —— 外部推送策略：`dirty == false` 覆盖表单 / `dirty == true` 不覆盖用户输入
      —— 锁死项（`projectType`/`entry`/`isolationId`）**不提供任何修改 API**（编译期杜绝误改）
- [x] `ProjectListViewModel`：项目列表数据源（`observeAllByCreatedAt()`，`WhileSubscribed(5_000)`）
- [x] `ProjectRepository` 增补 `observeAllByCreatedAt()` 委托（新项目在前；与 `observeAll()` 同款 `.catch` 兜底）
- [x] `ProjectsScreen` 新增可选参数 `onCreateProject: (() -> Unit)? = null`（null 时行为与渲染完全不变 → Paparazzi 零漂移）
- [x] 纯逻辑层 `ui/editor/`：`ProjectField`（字段枚举 + `FieldMessage`）、`ProjectValidator`、`ProjectDirectory`（窄接口 + 仓储适配器 + `ArchiveProbe`）
- [x] 事件通道 `Channel(BUFFERED).receiveAsFlow()`（一次性、不重放）；状态通道 `StateFlow`
- [x] **列表项 → `project/{id}` 的 UI 接线**（阶段 3 完成）：`ProjectsScreen` 改用统一内核 `Project` 模型，
      整卡点击 `onOpenProject(id)` → `project/{id}`；`ProjectListViewModel.delete(id)` 提供删除能力
      —— 取代阶段 2 的 `BuildRecord` 列表与 legacy 内联粘贴构建区（该通路已成死代码，一并删除）
- [ ] `ProjectEditScreen`（阶段 4）；`project/{id}` 路由当前落地页仍为 `DevelopingScreen()` 占位

## 阶段 3：添加项目页（§4.1 / §9）

> ✅ 已落地并验证（2026-10-26）：`compileDebugKotlin` / `testDebugUnitTest` **298/298 全绿**（既有 287 零回归 + 新增 11：
> 列表 VM 删除 2 + 添加页快照 9），`recordPaparazziDebug` 产出基线 **9 张新增 + 2 张变更**，
> `verifyPaparazziDebug` **零漂移**；设计令牌门禁 `--strict` 通过；`assembleDebug` 成功。
>
> ### ⚠️ 本阶段范围决策（产品决策，务必知悉）
>
> **只做 `URL` 与 `HTML` 两条完整链路；`VUE` / `PHP` 仅占位。**
> 理由：VUE / PHP 工程需要**内置运行时**，包体会显著变大，且逻辑尚需打磨，故推迟到后续版本。
>
> | 项目类型 | 本阶段状态 |
> |---|---|
> | `URL` | 完整：准星可选 → 填网址 → 校验 → 保存 → 列表可见 |
> | `HTML` | 完整：准星可选 → 粘贴 HTML → 校验 → 保存 → 列表可见 |
> | `VUE` / `PHP` | **占位**：准星仍可见可选（保持 §9 四项设计），选中后来源区显示「敬请期待」说明，**保存门禁拦截**（`canSave` 恒 false，无归档 UI 通路） |
>
> 因此以下全部**推迟**（本阶段未实现，勿误认为遗漏）：
> - `ArchiveStore` / `ArchiveImportSource` / SAF 文件选择器（`OpenDocument`）/ `ArchivePicker`
> - `FileArchiveProbe` 异步化（`Dispatchers.IO` 迁移 / `ioDispatcher` 注入 / `archiveSeq` 竞态守卫）
> - zip 导入拷贝到私有目录、100MB 限额校验的 UI 通路
>
> 阶段 2 已交付的归档能力（`ArchiveProbe` / `FileArchiveProbe` / `ArchiveProbeResult` /
> `AddProjectViewModel.onArchivePicked()` / `AddProjectUiState.archive*` / `ProjectValidator.validateEntryArchive()` /
> `MIGRATION_2_3` 的 ARCHIVE 映射 / `ArchiveClassifier`）**保留不动**，后续阶段直接接 UI。

- [x] `AddProjectScreen`：顶栏标题「新建项目」+ 右侧「保存」（仅全合法可点，§2.6）；
      页面**零内部业务状态**（只有 `rememberScrollState()`），Paparazzi 可直接喂任意状态出图
- [x] 准星选择器 `ProjectTypeSelector`：`HorizontalPager` + 对称 `contentPadding` 露邻居（§9.2/§9.4 D-C1/D-C2）
      —— 居中原理：`PageSize.Fill` + 左右对称 36dp padding ⇒ 当前页中心恒等于容器中心，准星用 `Alignment.Center` 天然重合（不手算偏移）
      —— 准星：`2dp` 竖线 + 高于卡片 6dp + `primary@0.38` + **不加 clickable**（否则拦截 Pager 手势）
      —— 无障碍：每卡归并语义，`contentDescription`＝「项目类型：网址」；VUE/PHP 追加「敬请期待」
- [x] 选中项决定来源输入形态（本阶段：URL→填网址 / HTML→粘贴 HTML；VUE/PHP→占位说明）；**切换类型清空重置**（§9.3/§9.4 D-C3，VM 既有行为）
- [x] 基本信息表单：APK 名称 / 包名（正则 + 同包名**强制拦截**，§2.6/§6.1）/ 版本段式（`FilterChip`，默认三段）/ 版本号 versionName / versionCode（默认 1）/ 图标（默认占位=主题色+首字母，§2.6）
      —— 版本号输入框为本次补齐（§4.1 原文漏写；段式切换会静默重置 versionName，不给输入框则用户不可控）
      —— 段式切换用 `FilterChip` 而非 `SegmentedButton`（后者实验性 API，会向整页扩散 `@OptIn`）
- [x] 实时校验 + 人话错误提示（M3 `isError` + `supportingText`）：ERROR→`colorScheme.error`，WARNING→`onSurfaceVariant` 且不置灰；
      文案唯一来源仍是 `ProjectValidator`，UI/VM 不自造校验文案
- [x] 保存 → 写 `Project`（`projectType`+`entry` 锁死）→ 返回首页列表；首页读一次 `RESULT_CREATED_PROJECT_ID` 后 `remove`
- [x] 新增 4 枚类型图标 `res/drawable/ic_type_{url,html,vue,php}.xml` 并登记进 `MmaIcons`（照现有机制扩展）
- [x] 图标选择器**阶段 3 占位**：`onPickIcon` → 记 `MmaLogger.operation("add_project_icon_not_ready")` + Toast「图标选择还在开发中，敬请期待」，签名预留给后续阶段
- [x] 删除 legacy 内联粘贴构建区、`ProjectRow(BuildRecord)`、`statusLabel()`（早已是死代码，且会造「只在 legacy 表」的孤儿项目）

## 阶段 4：项目编辑页（§4.2 / §10）

> ✅ 已落地并验证：编译 / 322 单测全绿（既有零回归）/ `recordPaparazziDebug` 新增 6 张编辑页基线、
> `verifyPaparazziDebug` 零漂移（既有 48 张快照未动）/ `assembleDebug` 成功 / 令牌门禁 `--strict` 通过。
>
> ### 关键设计决策（工程落地）
> - **页面无状态**：`ProjectEditScreen(state, …)` 仅 `rememberScrollState()` + 各 Tab 局部交互态（自定义 UA 草稿、
>   删除确认可见性），Paparazzi 直接喂 `ProjectEditUiState` 出图；VM 不在 Screen 内 new。
> - **收起开关放顶栏**：`onToggleCollapse` 挂在 `MmaScreen` 的 `actions`（与「保存」同处），避免把开关塞进身份卡导致
>   整卡收起后无法再展开（§4.2 的「高度→0」既定行为与此一致）。图标 `ic_collapse` 旋转 180° 表达展开/收起。
> - **基本配置控件选型**：开关 `Switch`；单选（打开方式 / UA）`RadioButton`；缓存模式 `ExposedDropdownMenu`；
>   主题色预设色板（珊瑚粉 `#E39E97` 默认 + 5 备选，硬编码色板已 `token-gate: allow`）。规避实验性 `SegmentedButton` 扩散 `@OptIn`。
> - **`savingError` 字段已删除**：保存失败统一走 `ProjectEditEvent.SaveFailed` 事件（导航层接 `Snackbar`），
>   与 `AddProjectViewModel`「失败唯一出口是事件」的既有约定一致，避免事件 + 状态字段双通道导致重复提示。
> - **`iconPlaceholderLabelOf` 复用**：VM 两处 `firstOrNull()?.uppercase()` 内联改为调用 `ui/editor` 的单一实现，
>   消除双实现漂移（该实现用 `uppercaseChar()` 规避 `ß→SS` 撑破占位格）。函数已为 `internal`，同模块可见。
> - **删除确认复用 `DeleteConfirmContent`**（与列表页同源），`BasicAlertDialog` + 二次确认，红色主操作。

- [x] `ProjectEditScreen`：外层 `Column`，顶部 2/5 身份卡 + 底部 3/5
- [x] 顶部 2/5 卡（`EditIdentityCard`）：图标/名称/包名/版本/类型；**可就地编辑**（仅 `projectType`/`entry` 只读锁死，§10.4 D-T2）；收起开关在顶栏（§4.2）
- [x] 收起动画：`AnimatedVisibility(shrink/expandVertically)` + 底部 `weight(1f)` 顶满 100%，TabRow 不压顶部（§4.2）
- [x] 底部 `TabRow`（`EditTabRow`）：基本配置 / 进阶配置 / 高级配置（§10.2，枚举沿用 `EditTab`）
- [x] **基本配置**（`WebSettingsTab`）：JS / DOM Storage / 打开方式 / 第三方 Cookie / 缓存模式 / UA / 主题色（均接 `WebSettings`/`themeColor`，§10.2/§10.4 D-T3）
- [x] **进阶配置**（`TemplateTab`）：模版应用自定义占位（§10.5，仅占位，不实现控件）
- [x] **高级配置**（`HighSettingsTab`）：引擎展示锁定 / 本地隔离只读 `webapp_<id>` / 导出格式·签名占位 / 删除项目（红色二次确认，§10.2）
- [x] 所有改动写回 `Project`（改名改包名复用 §2.6 校验，包名防抖查重排除自身沿用 VM 既有实现）
- [x] 导航接入：`project/{projectId}` 路由 `DevelopingScreen()` 占位替换为真实 `ProjectEditScreen`（`state`/`onBack`/各 setter/`onSave`/`onToggleCollapse`/`onTabSelected`/`onDelete`）；`Saved→回传 RESULT_UPDATED_PROJECT_ID+popBackStack`、`SaveFailed→Snackbar`、`Deleted→回列表`
- [x] 新增测试：编辑页 Paparazzi 快照 6 张（`light_expanded_basic` / `dark_expanded_basic` / `light_collapsed` / `light_tab_advanced` / `light_tab_high` / `light_package_conflict`）；VM 改动（删 `savingError`/复用占位函数）已由既有 Edit VM 单测覆盖

## 阶段 5：质量门禁

- [x] 迁移校验：v2 → v3 数据不丢、`exportSchema` 的 `3.json` 与实体声明一致（§8.4）
      —— 采用**三重等价校验**（`Migration_2_3_Test`，10 例全绿）：
      1. **Room 真实执行迁移** → Room 内部 `onValidateSchema` 做运行期 schema 强校验（不一致即抛 `IllegalStateException`，等价线上闪退）
      2. **与 Room 按实体声明新建的 v3 库逐表逐列比对**（PRAGMA `table_info`/`index_list`/`foreign_key_list`）
      3. **与 KSP 导出的 `3.json` 逐字比对 DDL**（去空白后严格相等）
      > 未使用官方 `MigrationTestHelper`：其构造函数首参为 `android.app.Instrumentation`，且从 **assets** 读取 schema JSON——
      > 二者在 Robolectric 单元测试环境下均不可得（单测 assets 不含 `app/schemas`）。
- [x] 阶段 1 新增测试：映射器 14 例 + 分类器 11 例（脏 JSON 降级、三种入口解码、判别符稳定性、ARCHIVE 嗅探判定）
- [x] 阶段 2 新增测试：校验器 52+29 例 + Add VM 17+16 例 + Edit VM 14+12 例 + 路由 6+9 例 + 列表 VM 3 例
      —— QA 独立补测 66 例，重点覆盖高风险路径：切类型清空来源、**切类型不覆盖已手填的名称/包名**、
      未手填时才回填建议、非法包名清冲突、旧查询结果不覆盖新输入、`save()` 重复触发只写一次库、
      `Saved` 事件只发一次、锁死项反射断言（无修改 API）、包名大写/单段/空段/尾点/前导点/前导数字/4 种保留前缀全部拦截、
      `javascript:`/`data:`/`file:` 协议拦截、版本段 999/1000/超 Int 范围
- [x] 阶段 3 新增测试：`AddProjectScreenTest` 快照 9 张（初始态亮/暗、可保存、多字段错误、仅警告、HTML 多行、VUE 占位、准星几何亮/暗）
      + 列表 VM `delete` 2 例；`ProjectsScreenTest` 换 `Project` 样例
      —— 快照基线：**9 张新增**（`AddProjectScreenTest_*`）+ **2 张变更**（`ProjectsScreenTest_{light,dark}WithRecords`，
      行结构按 §4 改为「图标 + 名称 + 类型·版本 + 删除」）。**空态两张零漂移** = 未误伤既有页面。
      —— 命名 `light_*` / `dark_*` / `selector_*` 刻意带下划线：Paparazzi 方法名直转文件名，下划线保证可读
- [x] 新页面 Paparazzi 基线（对齐 `DESIGN_LANGUAGE.md`，PROJECT_BRIEF §9）—— 添加页已落地（见上）；编辑页随阶段 4
- [ ] 校验 / 无障碍走查（WCAG 2.2 AA，PROJECT_BRIEF §9）

---

## 后续（非本期，已在设计预留）

- **模版应用**：`Project` 增 `templateConfig`（主题覆盖 / 顶栏 / 底栏 / tab 槽位），进阶配置 Tab 落地（§10.5）
- **TWA 导出**：自有 PWA（`ProjectEntry.Pwa`）走 TWA 模板（§2.3 注）

> **架构变更**：云端引擎 `BuildEngine.CLOUD` 已废弃，全部走**本地注入**（设备端 WebView 封装 / 资产注入模板壳 + 本地重签）。见设计文档 §11。

---

## 设计决策速查（已确认）

| 项 | 决策 |
|---|---|
| 导出 App 版本 | 独立于 MMA 自身 4 段式，用户选 2/3 段（默认三段 `1.0.0`） |
| 入口 entry | 仅添加页设、保存后锁死；准星选类型（URL/HTML/VUE/PHP） |
| 分类 | = 项目类型 `URL/HTML/VUE/PHP`（准星四项） |
| 包名 | 正则 + 禁保留前缀 + **同包名强制拦截** |
| 图标 | 允许默认占位（主题色 + 首字母） |
| 校验 | 实时 + 人话提示；保存门禁 |
| DB | v2→v3 显式迁移；`projects`+`export_runs`；entry/webSettings 存 JSON；ARCHIVE 两阶段零丢失 |
| 准星 | HorizontalPager + 邻居 peek + 切换清空 |
| 三 Tab | 基本（全 WebSettings+主题色）/ 进阶（模版应用占位）/ 高级（引擎锁定+隔离+删除） |
| 顶部卡 | 身份信息可就地改，仅类型锁死 |

# PROJECT_BRIEF（团队共享宪法）

> **本文件是跨会话、跨技能的唯一事实来源（single source of truth）。**
> 任何技能（`ai-team-orchestration` / `frontend-design` / `claude-android-ninja` / `oklch-tokens` / `accessibility`）产出的设计与代码，都必须对齐本文件，尤其是 §3 设计语言与 §2 平台约束。
> 冷启动提示：先读 `DESIGN_LANGUAGE.md`，再读本文件，然后从"下一步"继续。

---

## 1. 产品概述

> **应用身份（已敲定，不可擅自改动）**
>
> | 项 | 值 |
> |----|-----|
> | **应用名称** | **`MMA`**（Mini Make APK；`android:label` 与 Gradle `rootProject.name`） |
> | **应用包名** | **`com.make.apk`**（`namespace` + `applicationId`；源码根包同此） |
> | **版本格式** | **纯四段式 `MAJOR.MINOR.PATCH.BUILD[-rcN]`**（当前 **`0.0.0.1-rc4`**，无 `+N` 后缀；正式版需用户授权，否则发 RC），规范见 `docs/VERSIONING.md` |
> | **发布 tag** | `v<versionName>`，例 **`v0.0.0.1-rc1`**（由 CI 强制校验一致性） |
> | **成品产出** | **只在 GitHub 打 `v*` tag 时产出**（`.github/workflows/release.yml` → Release 附件） |
> | **本地职责** | **本地不做编译成品，只做验证**（`testDebugUnitTest` / `verifyPaparazziDebug`） |

- **是什么**：一款**纯原生安卓 App**，把"网页 / 前端代码"一键打包成可安装的 Android APK。输入来源支持：① 粘贴或上传 HTML/CSS/JS；② 上传 Vue 等前端工程（zip 包）；③ 输入网址 URL（WebView 封装）；④（可选）PHP 项目（经云端构建或 URL 形式接入）。输出：可直接安装到手机的 `.apk` 文件。
- **目标用户**：**全年龄段、低技术门槛**。既服务想快速把网页/原型封装成 App 的开发者，也服务不会写代码、但希望把自己的网页/内容变成 App 的普通用户。UX 必须极简、友好、零恐吓感。
- **核心价值**：把"网页变 App"的门槛降到最低——过程**舒适**、可视、有正反馈（可爱进度提示与成果庆祝），让任何人都能在手机上完成打包。
- **非目标 / 显式排除**：不做通用 IDE 或完整编程环境；不生成 iOS/IPA；不内置重型编译器（构建走云端服务或轻量模板封装 WebView）；不替代 Android Studio 等专业工具的全部能力。

> 注：软件形态（纯安卓 Compose）与**设计语言已锁定**（可爱简约·iOS·色彩单调），产品定义在此基础上展开。构建策略（云端构建服务 vs 设备端轻量封装）为关键架构议题，交由团队 brainstorm（§6）确认。

---

## 2. 平台与技术栈

| 项 | 决策 |
|----|------|
| 平台 | **纯原生安卓（不跨端、不 Web）** |
| UI 框架 | **Jetpack Compose**（声明式） |
| 设计系统 | **Material 3**（按 `DESIGN_LANGUAGE.md` 改造） |
| 语言 | Kotlin |
| 架构 | **全本地注入**（sprint-1=设备端 WebView 封装即时出包；**原 Phase 2 云端构建已作废**，`BuildEngine.CLOUD` 标注 `@Deprecated`，改走「设备端 WebView 封装 / 资产注入模板壳 + 本地重签」，见 `docs/design/project-editor-design.md` §11；早期混合路径 brainstorm 存档见 `docs/brainstorm/05-summary.md`）。App 层采用 MVVM + 单向数据流 |
| 设计令牌 | OKLCH 色板 + 8pt 栅格（见 §3 / `DESIGN_LANGUAGE.md`） |
| 应用名称 / 包名 | **`MMA`** / **`com.make.apk`**（见 §1 应用身份） |
| 版本规范 | **纯四段式 `0.0.0.0`（正式版需用户授权，否则发 `-rcN`；2026-10-25 起去除 `+N`）**，见 `docs/VERSIONING.md` |
| 本地存储 | **Room + SQLCipher** 加密 SQLite（Room DB 当前 `version = 4`；显式迁移链 `MIGRATION_1_2` → `MIGRATION_2_3` → `MIGRATION_3_4`，实现在 `data/local/Migrations.kt`、登记于 `AppDatabaseFactory.MIGRATIONS`；**已移除 `fallbackToDestructiveMigration()`**，显式迁移链完整覆盖 1→4 / 2→4 / 3→4 与全新安装），见 `docs/data-layer.md` |
| 日志层 | **自研 `MmaLogger`**（零第三方）：九大类别、结构化全量化、强制脱敏、崩溃/ANR 自研捕获，见 `docs/logging.md` |
| 发布策略 | **成品只在 GitHub 打 tag 产出**；本地仅验证（`.github/workflows/`） |

---

## 3. 设计语言（已锁定，不可擅自改动）

- **完整规范**：见 [`DESIGN_LANGUAGE.md`](./DESIGN_LANGUAGE.md)
- **一句话定位**：可爱简约 · iOS 风 · 色彩单调但温暖可爱。
- **三铁律**：①色彩单调（单主色珊瑚粉 + 暖中性）；②圆润优先（大圆角+柔阴影+弹性动效）；③留白即设计。
- **主色**：`--primary: OKLCH(0.78 0.10 25)`（柔和珊瑚粉，低彩度）。
- **字体**：圆体优先（Nunito/Quicksand/Baloo 2 + 思源柔黑/站酷快乐体）。
- **圆角**：卡片 16–24dp，按钮胶囊 999dp。
- **动效**：弹簧 + 轻微回弹，200–400ms；按压 scale 0.96。

> 任何 UI 产出若偏离以上，视为不合格，需打回。

---

## 4. 设计令牌来源

- **规范**：`DESIGN_LANGUAGE.md` §2（OKLCH 令牌表）。
- **实现**：**已落地** —— `oklch-tokens` skill 已安装，令牌固化为 Compose `ColorScheme` + `Theme` 对象，生成于 `ui/theme/` 包下（`Color.kt` / `Theme.kt` / `MmaDesign.kt` / `Motion.kt` / `Spacing.kt`）；令牌使用受 `scripts/gitops/check_token_usage.py --strict` 门禁约束。
- **单一来源**：设计层（`frontend-design`）与安卓层（`claude-android-ninja`）**共用同一份令牌**，禁止各自发明色值。

---

## 5. 团队角色（适配自 ai-team-orchestration）

| 角色 | 职责 | 对应技能/产出 |
|------|------|--------------|
| **Producer（制作人）** | 定范围、拆 sprint、协调、验收合并；**不写代码** | 维护本 `PROJECT_BRIEF`，派活 |
| **产品设计师** | 信息架构、用户流、功能设计 | `frontend-design`（方向）、`ui-ux-design`（8pt/审查清单） |
| **视觉总监** | 视觉识别、动效、设计系统、可爱质感 | `DESIGN_LANGUAGE.md` 守护者 |
| **安卓工程师** | Compose 实现、M3 主题、自适应布局 | `claude-android-ninja` |
| **QA** | 行为测试、无障碍核验、回归 | `accessibility` + 截图回归 |

> 小任务可一人多角；风险高时独立 QA。人类为最终决策者（message bus）。

---

## 6. 开发工作流

1. **Bootstrap**：读 `DESIGN_LANGUAGE.md` + 本文件 → 补全 §1 产品概述。
2. **Brainstorm**（产品/架构决策时）：多角色各自发声，**至少 2 处真实分歧**防群体思维。
3. **Sprint 计划**：`docs/sprint-N/plan.md`（任务+验收标准）+ `progress.md`（实时追踪）。
4. **执行**：`claude-android-ninja` 按 `DESIGN_LANGUAGE.md` 实现，最小完整方案。
5. **QA 闸门**：`accessibility`（WCAG 2.2 AA）+ 截图回归，写 `docs/qa/sprint-N-signoff.md`。
6. **合并**：Producer 验收后合并，更新本文件 §7。

---

## 7. 当前状态（每次 sprint 重写）

- **阶段**：地基搭建完成；产品方向已定（**Web2APK**：纯安卓 App，把网页/前端代码 HTML/PHP/Vue/URL 一键生成 APK）；sprint-1 设计系统地基 + 首屏 + WebView 封装已落地，且**已从"仅可评审源码"升级为可编译验证**——已接入 Android SDK（compileSdk 34）并跑通 Paparazzi 截图回归（生成 golden 基线）
- **已装技能（11 个，均位于 `~/.codebuddy/skills/`，并已注入本宪法约束）**：
  - 网络底座：`github-sandbox-dns-bypass`（沙箱 DNS 死节点时强连 GitHub）
  - 团队地基：`ai-team-orchestration`（角色分工 + PROJECT_BRIEF/sprint/brainstorm 模板）
  - UI 设计：`frontend-design`、`ui-ux-design`（8pt 栅格/审查清单）
  - 安卓布局：`claude-android-ninja`（Compose / M3 / 主题 / 无障碍 / CI 全套）
  - 设计令牌：`oklch-tokens`、`monochrome-palette`、`mobile-first-layouts`（OKLCH 色板 / 单色板 / 移动栅格）
  - 主题：`theme-factory`（预设/自定义主题）
  - 质量/可访问性：`accessibility`（WCAG 2.2 AA + 移动 TalkBack，含 `references/mobile-accessibility.md`），已对齐本宪法对比度/触控目标/动效降级/语义化红线
  - 质量/截图回归：`compose-screenshot-testing`（Paparazzi 优先、Roborazzi 备选的 Compose 截图回归工作流，锁死视觉规范不漂移）
- **约束注入完成**：每个 `SKILL.md` 顶部已加「项目设计约束」区块（网络工具为「本项目上下文」），强制对齐 `/workspace/DESIGN_LANGUAGE.md` §2–§10 与 §3 锁定基调（可爱简约·iOS·色彩单调），避免各 skill 各自发明色值/风格。
- **文档矩阵已立（2026-10-24）**：建立 `docs/DOC_CHARTER.md` **文档宪章**（所有文档的总纲：矩阵/命名/格式/SemVer/Conventional Commits/Agent 协同/质量门禁），并补全 `README.md`、`CHANGELOG.md`、`CONTRIBUTING.md`、`docs/INDEX.md`、`docs/ARCHITECTURE.md`、`docs/AGENT_COLLABORATION.md`、`docs/CODE_STYLE.md`、`docs/SETUP.md`、`docs/DEPLOYMENT.md`，以及 **`docs/qa/sprint-1-checklist.md`（Sprint-1 合并前质量闸门）**——全部联网学习最佳实践（README 五问、Keep a Changelog、SemVer、Conventional Commits、Agent 交接契约、Docs-as-Code、WCAG 2.2 AA）后生成，且对齐本宪法与 `DESIGN_LANGUAGE.md`。导航见 `docs/INDEX.md`。
- **数据层已落地（2026-10-24）**：**`Room 2.6.1 + SQLCipher 4.13.0`（加密 SQLite）**接入完成并**编译 + 测试实证通过**（`assembleDebug` 成功、14 个测试全绿、APK 内含 `libsqlcipher.so` 四 ABI）。分层：`data/model`（领域模型，UI 唯一可见）· `data/local`（实体/DAO/AppDatabase/转换器）· `data/security`（口令经 **Android Keystore** 加密存储，不落明文）· `data/repository`（上层唯一出口，只暴露领域模型）。高性能实践：**WAL**、`synchronous=NORMAL`、覆盖索引、`status+createdAt` 复合索引、单事务批量写、Flow 响应式读、Paging3 分页。选型与排错实证见 `docs/data-layer.md`。
  - **版本红线**：SQLCipher **不得升到 4.14+**（4.14.1+ 传递 `androidx.sqlite 2.6.x`，由 Kotlin 2.1 编译，与本项目 Kotlin 1.9.24 不兼容；4.18+ 还要求 compileSdk ≥ 37）。升级需整体升 Kotlin/KSP/Room/AGP/compileSdk。
- **应用身份与发版约束已敲定（2026-10-25）**：应用名 **MMA**（`android:label` + `rootProject.name`）、包名 **`com.make.apk`**（`namespace` + `applicationId`，源码根包整体迁移，Paparazzi golden 基线文件名与 Room schema 目录同步迁移）；版本为**纯四段式 `MAJOR.MINOR.PATCH.BUILD[-rcN]`**（2026-10-25 起去除 `+N` 后缀；当前 **`0.0.0.1-rc3`**，正式版需用户授权、否则按顺延下一版发 RC，规范见 `docs/VERSIONING.md`）；**本地不做编译成品**，APK 只由 `.github/workflows/release.yml` 在推送 `v*` tag 时构建并发布到 Release，且 CI **强制校验 tag 与 `versionName` 一致**；`.github/workflows/verify.yml` 在 PR / 推送时跑与本地同套的验证闸门。
- **日志层已落地（2026-10-25）**：自研 **`MmaLogger`** 门面（零第三方依赖），覆盖**九大类别**——崩溃 / 错误 / **操作审计** / 启动 / 性能 / 网络 / 生命周期 / 调试 / 安全审计，远超"只记 bug"。结构化事件 + 全量化字段（`duration_ms`/`success` 提升为 DB 独立列）；`Sanitizer` 在门面层**强制脱敏**（敏感键、个人信息、完整 URL、`key=value` 凭据）；载体分级——高价值结构化落**加密 Room**（随库受 SQLCipher 保护）、高频诊断落**轮转文件**（2MB×5，有界队列异步写，绝不阻塞调用线程）、调试仅 Logcat；崩溃用 `UncaughtExceptionHandler`（`attachBaseContext` 最早安装）+ ANR 主线程看门狗，崩溃时同步刷盘；`StartupTracer` 做冷/温/热分型与阶段耗时。数据库随日志表升至 **v2** 并写显式 `Migration(1,2)`，已移除 `fallbackToDestructiveMigration()`。详见 `docs/logging.md`。
- **页面骨架已完善（2026-10-25）**：补齐 **`MainActivity`**（此前工程**未声明任何 Activity**，页面根本无法运行）并注册为 LAUNCHER；新增 `MmaRoute` 路由清单 + `MmaNavHost`（Navigation Compose 2.8.9），五条路由：`home` / `building/{id}` / `result/{id}` / `history` / `preview/{id}`。新增构建中页（可爱脉冲）、结果页（成功庆祝 + 失败错误态，给**人话原因**与重试出口）、历史页（**直连加密数据库真实数据**）、预览页；首页加历史入口。ViewModel 层 `BuildViewModel` / `HistoryViewModel` 采用手工 DI 工厂（保持零 Hilt）。**分层约定：路由取数、页面渲染**——所有页面均为无状态 Composable，故 Paparazzi 可脱离数据库与导航直接渲染做截图回归。启动终点改为**首帧绘制完成**（`reportFullyDrawn()` + `StartupTracer.complete()`），粗分冷/温启动。⚠️ **本条为历史沿革**：所述 `building`/`result`/`history`/`preview` 四条路由与 `BuildViewModel`/`HistoryViewModel`、`BuildingScreen`/`ResultScreen`/`PreviewScreen` 已于**阶段 5 退役**（见本节阶段 5 条目），现行路由以 `MmaRoute.kt` 为准。
- **统一内核契约已起步（2026-10-26）**：开始落地「前端转 App」统一内核（设计见 `docs/design/project-editor-design.md` / `project-editor-implementation.md`）。阶段 0「统一内核契约」**已落代码并通过 `compileDebugKotlin` + `compileDebugUnitTestKotlin` + 受影响 Paparazzi 快照测试**：`data/model/` 下新增 `WebRuntimeConfig`（Project 去 DB 字段 `id/createdAt/updatedAt` 的可 JSON 序列化共享契约）、`ProjectType{URL,HTML,VUE,PHP}` / `VersionScheme` / `ProjectEntry`(Url/BundledHtml/Archive) / `ArchiveKind` / `WebSettings`（均 `@Serializable`）；`WebViewScreen` 改造为消费 `WebRuntimeConfig`（按 entry 类型加载、按 webSettings 应用配置）；构建引入 `kotlinx-serialization`（锁 1.6.3，兼容 Kotlin 1.9.24）。**架构变更：废弃 `BuildEngine.CLOUD`，全部走本地注入**（设备端 WebView 封装 / 资产注入模板壳 + 本地重签），`CLOUD` 标注 `@Deprecated`，原"Phase 2 云端构建"规划作废（设计 §11）。后续阶段：① 数据层 `Project`/`ExportRun` + DB v3 迁移；② 路由 `add`/`project/{id}` + ViewModel；③ 添加页（准星选择器）；④ 编辑页（2/5+3/5 切割 + 三 Tab）。
- **待补**：~~云端构建服务实现（Phase 2，契约见 `docs/build-api-contract.md`）~~（**已作废 → 全本地注入**，见设计 §11）；日志云端上报（改为本地优先 + 用户主动导出诊断包）；Native 崩溃捕获（需 NDK 信号处理器）；SQLCipher 实际加解密与 DAO 语义的**真机/Instrumented 测试**（当前仅覆盖纯 JVM 逻辑）。
  - ~~release 签名配置（`signingConfig` 尚未接入，当前产物为 unsigned）~~（**已完成**：release `signingConfig` 已接入，由环境变量驱动 keystore，v1/v2/v3 签名全开）；~~首帧终点改用 `Activity.reportFullyDrawn()`~~（**已完成**：`MainActivity` 已调用 `reportFullyDrawn()` + `StartupTracer.complete()`）；~~发布前用显式 `Migration` 替换 `fallbackToDestructiveMigration()`~~（**已完成**：见 §7 阶段 5 条目，DB v4 显式迁移链已完整覆盖 1→4 / 2→4 / 3→4）。
- **数据层已落地（2026-10-26）**：阶段 1「数据层」**已落代码并全绿**——`testDebugUnitTest` **129/129 通过**（新增 35 例：迁移 10 / 映射 14 / 分类器 11），`compileDebugKotlin` + `kspDebugKotlin` 均 BUILD SUCCESSFUL。新增领域模型 `Project`（稳定定义）+ `ExportRun`（运行态，默认值统一收敛到 `ProjectDefaults`）、实体 `ProjectEntity`/`ExportRunEntity`、`ProjectDao`/`ExportRunDao`、Mapper（JSON 脏数据**降级不抛异常**）、`ProjectRepository`/`ExportRunRepository`（包名唯一性**应用层强制拦截**，不建 UNIQUE 索引以免历史重复包名导致迁移失败）、`ArchiveClassifier`（ARCHIVE 首启嗅探 VUE/PHP）。**数据库升至 v3**：新增 `projects`+`export_runs`，`MIGRATION_2_3` 用 **Kotlin + 真正的 JSON 序列化器**逐行搬运 `build_records`（不用纯 SQL 拼接：避免手工转义，且 `json_extract()` 依赖 JSON1 扩展、minSdk 24 不保证启用）。⚠️ **实施调整**：`build_records` **不 DROP**，改为复制保留为 legacy 表——当前 UI（`ProjectsScreen`/`HistoryViewModel`/`MmaNavHost`）仍直查该表，数据层删表会让 v3 上线当刻旧界面立即崩；待阶段 2/3 调用点切到新模型后由 **v4 迁移删表**（数据零丢失）。**迁移验证未用官方 `MigrationTestHelper`**（其构造函数首参为 `android.app.Instrumentation`、且从 assets 读 schema JSON，Robolectric 单测两者均不可得），改用**三重等价校验**：① Room 真实执行迁移 → 触发运行期 `onValidateSchema` 强校验（不一致即抛 `IllegalStateException`，等价线上闪退）；② 与「Room 按实体声明新建的 v3 库」逐表逐列比对（PRAGMA `table_info`/`index_list`/`foreign_key_list`）；③ 与 KSP 导出的 `3.json` **逐字比对 DDL**。`fallbackToDestructiveMigration()` 暂留（已标注 `TODO(阶段5)`），待调用点切换完成后再移除。
- **导航与 ViewModel 已落地（2026-10-26）**：阶段 2「导航与 ViewModel」**已落代码并全绿**——`testDebugUnitTest` **287/287 通过**（既有 129 + 工程师新增 92 + QA 独立补测 66），`verifyPaparazziDebug` **37 张快照零漂移**。新增 `ui/editor/`（`ProjectValidator` 承载 §2.6 全量校验规则，**纯函数**、零 `android.*` 依赖、中文文案单一来源；`ProjectDirectory` 窄接口 + 仓储适配器 + `ArchiveProbe`）与 `ui/viewmodel/`（`AddProjectViewModel` / `ProjectEditViewModel` / `ProjectListViewModel` + 手工 DI 工厂）。路由新增 `add` 与 `project/{projectId}`，结果经 `savedStateHandle` 回传；事件走 `Channel(BUFFERED).receiveAsFlow()`（一次性不重放）、状态走 `StateFlow`。关键行为：包名占用 **300ms 防抖 + 仅格式合法才查库 + `save()` 权威复校**，编辑页**排除自身**；切换项目类型**清空**三种来源输入；切换版本段式**重置** `versionName`；`projectType` 初值 `null`（必须显式选过才能保存）；`isolationId` 由仓储回填、VM 不自造；编辑页锁死项**无任何修改 API**（编译期杜绝误改）。`ProjectsScreen` 新增**可选**参数 `onCreateProject`（默认 null → 渲染与行为完全不变，故快照零漂移）。**QA 独立审查发现并修复 1 项阻断缺陷**：版本号超长段位（`10000000000`）触发 `it.toInt()` 抛 `NumberFormatException`，从输入回调逃逸出纯函数校验器 → 用户输入即崩；已改为 `toIntOrNull()` 兜底 + 全量排查清除同类风险转换，并加 `TODO(阶段3)` 标注 `FileArchiveProbe` 主线程 IO。⚠️ **列表项 → `project/{id}` 的 UI 接线放阶段 3**（legacy `BuildRecord.id` 与 `projects.id` 是不同 id 空间，现在接会死链）。
- **添加项目页已落地（2026-10-26）**：阶段 3「添加项目页」**已落代码并全绿**——`compileDebugKotlin` 零新增警告，`testDebugUnitTest` **298/298 通过**（既有 287 零回归 + 新增 11：列表 VM 删除 2 + 添加页快照 9），`verifyPaparazziDebug` **零漂移**（新增 9 张添加页基线；`ProjectsScreenTest` 2 张「有记录」基线属预期变更；**其余 37 张历史基线零变化** = 未误伤既有页面）。新增 `ui/screens/AddProjectScreen.kt` 与 `ui/editor/` 四个组件：`ProjectTypeSelector`（`HorizontalPager` + **左右对称** `contentPadding(36dp)` ⇒ 当前页中心恒等于容器中心；准星 `2dp` 竖线、高于卡片 6dp、`primary@0.38`、**不加 clickable** 以免拦截 Pager 手势）、`ProjectSourceSection`（四形态分发）、`ProjectFormFields`（`SectionCard`/`SectionTitle`/`ProjectTextField`/`VersionSchemeChips`/`FieldMessageText`）、`ProjectIcon`（主题色底 + 首字母，前景色按亮度自动选黑白两极）。4 枚类型图标 `ic_type_{url,html,vue,php}.xml` 登记进 `MmaIcons`。补齐 §4.1 漏写的 **versionName 输入框**（段式切换会静默重置它，不给输入框则用户不可控）。首页列表按 U1 裁决切到统一内核 `Project` 模型（整卡点击进入 `project/{id}`，删除 legacy 内联粘贴构建区）。图标选择器本阶段占位（记日志 + Toast），签名预留。
- ⚠️ **阶段 3 范围决策（产品定案）**：**只做 `URL` 与 `HTML` 两条完整链路；`VUE` / `PHP` 仅占位**。理由：后两者需要**内置运行时**、包体会显著变大，且相关逻辑**尚需打磨**。据此全部推迟：SAF 文件选择器（`OpenDocument`）、`ArchiveStore`、`ArchiveImportSource`、归档拷贝与 100MB 限额的 UI 通路。阶段 2 已交付的归档 VM 能力保留不动，后续直接接 UI。
- **阶段 5 收尾质量门禁 + 遗留清理已完成（2026-10-26）**：DB 升至 **v4**——`MIGRATION_3_4` 仅 `DROP TABLE IF EXISTS build_records`（数据已于 v3 复制到 `projects`+`export_runs`，**零业务数据丢失**）；移除 `fallbackToDestructiveMigration()`（显式迁移链 1→2→3→4 + `Migration_3_4_Test`/改写后 `Migration_2_3_Test` 三重校验兜底全升级路径）。退役 legacy `BuildHistoryRepository`/`BuildRecord`/`BuildViewModel`/`HistoryViewModel` 及 `BuildingScreen`/`ResultScreen`/`PreviewScreen`（共 10 文件）；`WebViewScreen` 保留为后续预览复用纯组件；`MmaRoute`/`MmaNavHost`/`MmaApplication` 摘除对应路由与依赖。测试改写/新增：`MmaRouteTest`/`MmaRouteEdgeCaseTest`/`Migration_2_3_Test` 去 legacy 断言、`Migration_3_4_Test` 新增，删屏测试与其快照一并移除、其余 47 张快照零漂移。质量门禁：327 单测 0 失败（较清理前 339 减少 12 例，源于删屏测试与其快照一并移除，0 失败保持；**注：该数字为阶段 5 收尾当时的门禁快照，后续阶段新增用例后当前单测总数为 344，0 失败**）、`compileDebugKotlin`/`assembleDebug` 通过、`verifyPaparazziDebug` 47 张快照零漂移、令牌门禁 0 违规。无障碍走查（TalkBack/contentDescription）按主理人裁决移交 QA。
- **下一步**：① **VUE / PHP 运行时打包能力**（内置运行时、包体评估）待产品另行排期。

---

## 8. 跨会话交接协议

- 长会话结束前：更新 §7 当前状态 + `docs/sprint-N/progress.md`。
- 冷启动：`读 DESIGN_LANGUAGE.md → 读 PROJECT_BRIEF.md → 读 docs/sprint-N/progress.md → 从 next action 继续`。
- Bug/决策：记入 GitHub Issues（单一事实来源），不在仅聊天中留存。

---

## 9. 质量闸门（上线前必过）

- **视觉一致性**：对照 `DESIGN_LANGUAGE.md` 反模式清单（§10）逐项核验。
- **无障碍**：WCAG 2.2 AA；主色按钮对比度、焦点态、屏幕阅读器标签。
- **截图回归**：关键页面截图比对，防止令牌/间距漂移。
- **动效**：弹簧回弹幅度小、不卡顿、尊重"减少动效"系统设置。

---

## 10. 反模式（待从 ai-team-orchestration 补全 19 条）

- 设计侧反模式见 `DESIGN_LANGUAGE.md` §10（多色拼贴、尖角、硬阴影、默认字体、生硬过渡、信息过密、主色铺满）。
- 团队协作反模式（如：跳过设计语言直接写码、各技能各自发明令牌、无 QA 直接合并）—— 已随 `ai-team-orchestration`（含 `references/anti-patterns.md`、`brainstorm-format.md`、`project-brief-template.md`、`sprint-plan-template.md`）安装到位，可随时引用。

---

*本文件随技能安装与产品定义推进持续更新。任何修改需经 Producer 确认。*

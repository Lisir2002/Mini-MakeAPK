# 数据层设计：Room + SQLCipher（加密 SQLite）

> 状态：已生效（2026-10-24）· 受 `docs/DOC_CHARTER.md` 约束
> 本文是数据层的**唯一事实来源**：选型结论、分层、加密方案、性能实践、迁移策略。

---

## 1. 结论速览

| 维度 | 决策 |
|------|------|
| 数据库 | **SQLite** |
| ORM | **Room 2.6.1**（AndroidX `androidx.room`） |
| 加密 / 引擎插件 | **SQLCipher for Android 4.13.0**（`net.zetetic:sqlcipher-android`） |
| 接入方式 | `Room.databaseBuilder(...).openHelperFactory(SupportOpenHelperFactory(passphrase, null, true))` —— **官方直连，零手写桥接** |
| 依赖注入 | 轻量手工单例（`MmaApplication`），不引入 Hilt |
| 响应式 | 读返回 `kotlinx.coroutines.flow.Flow`；写为 `suspend` |
| 长列表 | Paging3（`room-paging` + `paging-runtime`） |

---

## 2. 为什么是 SQLCipher（选型过程与关键发现）

需求是「SQLite + 高性能插件」。评估过三条路，最终结论由**实证反编译**决定，而非文档推测：

### 2.1 WCDB（腾讯）—— 否决

- 官方 Room 适配层是独立模块 `com.tencent.wcdb:room`。
- 反编译其 `WCDBOpenHelperFactory` / `WCDBDatabase` 得到：

  ```
  WCDBOpenHelperFactory implements android.arch.persistence.db.SupportSQLiteOpenHelper$Factory
  WCDBDatabase          implements android.arch.persistence.db.SupportSQLiteDatabase
  ```

- 这是 **Room 1.x 的旧包**（`android.arch.persistence`）。本项目用 AndroidX Room 2.x，
  其 `openHelperFactory()` 要求 `androidx.sqlite.db` 接口，两者**包名/接口体系不同**。
- 两条线（`1.0.8` 与 `1.1-19`）的 POM 均依赖 `android.arch.persistence.room:runtime:1.1.1`，
  且 WCDB 核心 `SQLiteDatabase` 是独立类（不继承框架 `SQLiteDatabase`），桥接面积极大。

> **结论**：WCDB 与 AndroidX Room 2.x 无官方兼容路径，直接接入必然失败。否决。

### 2.2 SQLCipher —— 采纳

- 常见教程指向 `net.zetetic:android-db-sqlcipher` + `net.zetetic.sqlcipher.SupportFactory`，
  但该 artifact **只在 Zetetic 私有仓库**分发，且 `maven.zetetic.net` 当前 **DNS 已失效（NXDOMAIN）**。
- 但 Maven Central 上的 **`net.zetetic:sqlcipher-android`** 自带 Room 桥接类，
  反编译确证（以 4.13.0 为准）：

  ```
  public class net.zetetic.database.sqlcipher.SupportOpenHelperFactory
      implements androidx.sqlite.db.SupportSQLiteOpenHelper$Factory {
      public SupportOpenHelperFactory(byte[] password);
      public SupportOpenHelperFactory(byte[] password, SQLiteDatabaseHook hook, boolean enableWAL);
      public SupportOpenHelperFactory(byte[] password, SQLiteDatabaseHook hook, boolean enableWAL, int minVersion);
  }
  ```

  且其 `SQLiteDatabase implements androidx.sqlite.db.SupportSQLiteDatabase`、
  `SQLiteOpenHelper implements androidx.sqlite.db.SupportSQLiteOpenHelper`。

> **结论**：`sqlcipher-android` **原生实现 AndroidX `androidx.sqlite.db` 接口**，
> 可直接交给 Room，无需任何桥接代码；且位于 Maven Central，供应链稳定。采纳。

### 2.3 版本上限：两道硬约束（实测，勿擅自升级）

SQLCipher 不能取最新版，受**两道编译期硬约束**夹击：

| SQLCipher | ① AAR `minCompileSdk` | ② 传递依赖 `androidx.sqlite` | 本项目可行性 |
|-----------|----------------------|------------------------------|-------------|
| 4.19.1 | 36 | 2.6.2 | ❌ ①②均不满足 |
| 4.18.0 | 37 | 2.6.2 | ❌ ①②均不满足 |
| 4.17.0 | 1 | **2.6.2** | ❌ ②不满足 |
| 4.16.0 / 4.15.0 / 4.14.1 | 1 | **2.6.2** | ❌ ②不满足 |
| **4.13.0** | **1** | **2.2.0** | ✅ **采用** |
| 4.12.0 及以下 | 1 | 2.2.0 | ✅ 可用 |

① `checkDebugAarMetadata` 会硬性拦截 `minCompileSdk` 高于项目 `compileSdk 34` 的依赖
（AGP 8.5.2 的推荐上限也是 34）。

② 更隐蔽：`androidx.sqlite 2.6.x` 是**用 Kotlin 2.1 编译**的，其元数据版本 2.1.0，
而本项目 Kotlin 编译器为 1.9.24（期望 1.9.0）。它还会把 `kotlin-stdlib` 抬到 2.1.20，
导致 `kspDebugKotlin` 直接失败：

```
e: ... sqlite-framework-api.jar ... binary version of its metadata is 2.1.0, expected version is 1.9.0
```

采用 4.13.0 后其传递的 `androidx.sqlite 2.2.0` 会被 Room 2.6.1 的 `2.4.0` 覆盖取胜
（Gradle 取最高），2.4.0 为 Kotlin 1.x 元数据，安全。

### 2.4 防御性对齐（已处理）

Kotlin 1.9 编译器读不了 Kotlin 2.x 元数据，任一传递依赖把 stdlib 抬到 2.x 都会让编译失败。
因此在 `app/build.gradle.kts` 中做了两道保险：

```kotlin
// 1) 排除 SQLCipher 可能携带的 kotlin-stdlib（4.19.x 曾带 2.2.10）
implementation(libs.sqlcipher.android) {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
}

// 2) 全局强制对齐到 Kotlin 插件版本
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.24")
    }
}
```

> **后续若要升级 SQLCipher 到 4.14+**：需同时升级 Kotlin → 2.x、KSP → 2.x、
> Room → 2.7+、AGP → 8.7+、compileSdk → 36+，并回归 Paparazzi 截图基线。
> 这是一次整体技术栈升级，不要单独动 SQLCipher。

---

## 3. 依赖与版本矩阵

| 依赖 | 版本 | 用途 | 备注 |
|------|------|------|------|
| `androidx.room:room-runtime` | 2.6.1 | ORM 运行时 | — |
| `androidx.room:room-ktx` | 2.6.1 | 协程 / `withTransaction` | — |
| `androidx.room:room-compiler` | 2.6.1 | 注解处理（KSP） | 以 `ksp(...)` 引入 |
| `androidx.room:room-paging` | 2.6.1 | DAO 直接返回 `PagingSource` | — |
| `net.zetetic:sqlcipher-android` | 4.13.0 | 加密 SQLite 引擎 | 4.14+ 传递 `androidx.sqlite 2.6.x`（Kotlin 2.1 元数据）会编译失败，勿擅自升 |
| `androidx.security:security-crypto` | 1.0.0 | Keystore 加密的口令存储 | — |
| `androidx.paging:paging-runtime` | 3.3.0 | Paging3 运行时 | — |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.7.3 | `Flow` / Dispatchers | — |
| `com.google.devtools.ksp` | 1.9.24-1.0.20 | 注解处理器 | **必须与 Kotlin 1.9.24 严格匹配** |

Room 编译期参数（`app/build.gradle.kts`）：

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")   // schema 导出，便于迁移审计
    arg("room.incremental", "true")
}
```

---

## 4. 分层结构

```
app/src/main/java/com/make/apk/
├── MmaApplication.kt              # 进程级单例：database / repository
└── data/
    ├── model/                      # 领域模型（无 Room 注解，UI 可见）
    │   ├── Project.kt              # 项目（稳定定义：身份 + 配置）
    │   ├── ExportRun.kt            # 导出运行（运行态：状态机 / 产物 / 耗时）
    │   ├── LogEntry.kt             # 日志条目（随库加密，见 logging.md）
    │   ├── ProjectEntry.kt         # 入口密封类：Url / Html / Archive
    │   ├── ProjectType.kt          # 项目类型枚举（URL / HTML / ARCHIVE）
    │   ├── VersionScheme.kt        # 版本号策略枚举
    │   ├── WebRuntimeConfig.kt     # 统一内核渲染契约
    │   └── WebSettings.kt          # Web 渲染设置
    ├── local/
    │   ├── AppDatabase.kt          # RoomDatabase（v4，exportSchema=true）
    │   ├── AppDatabaseFactory.kt   # ★ Room × SQLCipher 接线点 + PRAGMA 调优 + MIGRATIONS 注册
    │   ├── Converters.kt           # 枚举 ⇄ TEXT，未知值安全回退
    │   ├── Migrations.kt           # MIGRATION_1_2 / MIGRATION_2_3 / MIGRATION_3_4
    │   ├── entity/                 # 表结构 + 索引
    │   │   ├── ProjectEntity.kt            # 表 projects
    │   │   ├── ExportRunEntity.kt          # 表 export_runs
    │   │   └── LogEntryEntity.kt           # 表 log_entries
    │   └── dao/                    # suspend 写 / Flow 读 / PagingSource
    │       ├── ProjectDao.kt
    │       ├── ExportRunDao.kt
    │       └── LogEntryDao.kt
    ├── mapper/                     # 实体 ⇄ 领域模型
    │   ├── ProjectMapper.kt
    │   ├── ExportRunMapper.kt
    │   └── LogEntryMapper.kt
    ├── repository/                 # 上层唯一出口
    │   ├── ProjectRepository.kt    # 项目：稳定定义
    │   ├── ExportRunRepository.kt  # 导出运行：运行态
    │   └── LogRepository.kt        # 日志：查询 / 清理 / 导出
    ├── security/
    │   └── SqlCipherPassphrase.kt  # 口令生成 + Keystore 加密存储
    ├── classifier/
    │   └── ArchiveClassifier.kt    # 资产包类型嗅探（§8.5 D-M2）
    └── json/
        └── RuntimeJson.kt          # 运行时 JSON 编解码（entry_json / webSettings_json）
```

> **DB v4 现状**：legacy `build_records` 及其配套的 `BuildRecord` / `BuildRecordEntity` /
> `BuildRecordDao` / `BuildRecordMapper` / `BuildHistoryRepository` **已全部退役并删除**；
> 该表已被 **DB v4** 的 `MIGRATION_3_4`（`DROP TABLE IF EXISTS build_records`）移除，
> 数据已于 v3 复制到 `projects` + `export_runs`（**零业务数据丢失**）。
> 历史结构见 §5 的留档说明。

**边界规则**：

1. UI / ViewModel 只依赖 `data.model` 下的领域模型（`Project` / `ExportRun` / `LogEntry`）
   与 `data.repository` 下的仓储（`ProjectRepository` / `ExportRunRepository` / `LogRepository`）。
2. Room 注解、表名、索引**不得**越出 `data.local`。
3. 仓储对外只暴露领域模型，实体在 `data.mapper` 层完成转换。

---

## 5. 表结构与索引

当前实际表为 `projects` / `export_runs` / `log_entries`（DB v4）。
legacy `build_records` 已在 **DB v4** 由 `MIGRATION_3_4`（`DROP TABLE IF EXISTS build_records`）删除；
其数据已于 v3 复制到 `projects` + `export_runs`，**零业务数据丢失**。
本节末尾保留其**历史结构留档**，仅供迁移审计追溯。

### 5.1 项目表 `projects`（DB v3 引入）

承载**稳定定义**（身份 + 配置），与运行态（`export_runs`）分离。

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | INTEGER (PK, autoGenerate) | — |
| `createdAt` / `updatedAt` | INTEGER | epoch ms，`createdAt` **索引** |
| `projectType` | TEXT | `URL` / `HTML` / `ARCHIVE`（保存后锁死） |
| `apkName` / `packageName` | TEXT | 显示名 / applicationId（**应用层强制全局唯一**） |
| `versionScheme` / `versionName` / `versionCode` | TEXT / TEXT / INTEGER | 版本号三要素 |
| `iconRef` / `themeColor` | TEXT NULL / INTEGER | 图标引用 / ARGB 主题色 |
| `entry_json` / `webSettings_json` | TEXT | 入口与 Web 渲染配置，JSON 存储 |
| `isolationId` | TEXT | 本地多实例数据隔离目录后缀 |

索引：`createdAt`（列表倒序，避免全表排序）、`packageName`（唯一性查询加速）。
**刻意不加 UNIQUE 约束**——历史数据可能已含重复包名，加 UNIQUE 会让迁移直接失败；
唯一性由应用层 `ProjectRepository.ensurePackageNameAvailable` 拦截，DB 只提供查询加速。

### 5.2 导出运行表 `export_runs`（DB v3 引入）

每次导出一条运行态记录，**从属于** `projects`（N:1，外键 `ON DELETE CASCADE`）。

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | INTEGER (PK, autoGenerate) | — |
| `projectId` | INTEGER (FK→`projects.id`) | **索引首列** |
| `createdAt` / `updatedAt` | INTEGER | epoch ms |
| `status` | TEXT | `RUNNING`/`SUCCESS`/`FAILED`/`CANCELLED`，**复合索引首列** |
| `outputApkPath` | TEXT NULL | 成功后的 APK 路径 |
| `apkSizeBytes` / `durationMs` | INTEGER NULL | — |
| `errorMessage` | TEXT NULL | — |

索引：`(projectId, createdAt)`（单项目导出历史倒序）、`(status, createdAt)`（按状态筛选仍走有序索引扫描）。

### 5.3 历史结构留档：表 `build_records`（v1，已于 v4 删除）

> 以下为**已退役的历史结构**，仅供迁移审计追溯；对应
> `BuildRecordEntity` / `BuildRecordDao` / `BuildRecordMapper` / `BuildRecord` / `BuildHistoryRepository`
> 均已在阶段 5 删除，当前代码中**不再存在**。

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | INTEGER (PK, autoGenerate) | — |
| `createdAt` | INTEGER | epoch ms，**索引** |
| `updatedAt` | INTEGER | epoch ms |
| `inputType` | TEXT | `URL` / `HTML` / `ARCHIVE` |
| `sourceLabel` / `sourceRef` | TEXT | 展示名 / 真实引用 |
| `engine` | TEXT | `DEVICE_WEBVIEW` / `CLOUD` |
| `status` | TEXT | `QUEUED`/`RUNNING`/`SUCCESS`/`FAILED`，**复合索引首列** |
| `appName` / `packageName` | TEXT NULL | — |
| `outputApkPath` | TEXT NULL | 成功后的 APK 路径 |
| `apkSizeBytes` / `durationMs` | INTEGER NULL | — |
| `errorMessage` | TEXT NULL | — |

历史索引：

```kotlin
indices = [
    Index(value = ["createdAt"]),              // 历史倒序分页，避免全表排序
    Index(value = ["status", "createdAt"]),     // 按状态筛选时仍走有序索引扫描
]
```

枚举以 TEXT 存储（可读性 + 迁移友好），经 `Converters` 转换；
**解析未知值时回退安全默认值**而非抛异常，避免脏数据导致数据库整体打不开。

---

### 5.4 日志表 `log_entries`（DB v2 引入）

日志层（见 [`logging.md`](logging.md)）的高价值类别也落在**同一个加密库**里，
因此随库一同受 SQLCipher 保护，无需再为日志单独做加密。

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | INTEGER PK | — |
| `timestampMs` | INTEGER | **索引**，所有查询与清理都按时间 |
| `level` / `category` / `event` | TEXT | `category+timestampMs` 复合索引、`event` 索引 |
| `message` / `fieldsJson` / `stackTrace` | TEXT NULL | 均已脱敏；`fieldsJson` 为结构化字段 JSON |
| `traceId` / `sessionId` / `sequence` / `threadName` | TEXT/INTEGER | 关联与排序 |
| `durationMs` / `success` | INTEGER NULL | **全量化维度提升为独立列**，便于 SQL 直接聚合 |

> **迁移**：`log_entries` 由 **DB v2** 的 `MIGRATION_1_2`（`Migrations.kt`）显式建表；
> `AppDatabase` 当前为 `version = 4`，**已移除** `fallbackToDestructiveMigration()`。
> 新增版本必须同时在 `AppDatabase.version` +1 并在 `AppDatabaseFactory.MIGRATIONS` 数组登记。
> Room 会校验迁移后的 schema 与实体声明是否逐字一致，不一致会在打开时直接抛异常，
> 所以迁移 SQL 必须与 KSP 导出的 `app/schemas/.../N.json` 对齐。

## 6. 加密方案（密钥不落明文）

```
首次启动 → SecureRandom 生成 32 字节随机口令
        → Base64 后写入 EncryptedSharedPreferences
        → 该 XML 由 Android Keystore 的 AES-256-GCM 主密钥加密
此后每次 → 从加密 Preferences 读出口令 → 交给 SupportOpenHelperFactory
```

- 实现：`data/security/SqlCipherPassphrase.kt`
- **API 版本注意**：`androidx.security:security-crypto:1.0.0` 的类是 **`MasterKeys`（复数）**，
  且 `EncryptedSharedPreferences.create(fileName, masterKeyAlias, context, ...)` 的
  **context 在第三位**；`MasterKey`（单数）与 `create(context, ...)` 是 1.1.0-alpha 之后的 API。
- 密钥材料由 Keystore 持有，不出现在代码、资源或构建产物中。
- **取舍**：卸载 App / 清除数据后旧库无法解密（密钥随 Keystore 销毁），这是预期行为。
- 数据库文件本身全程 AES-256 加密（SQLCipher 默认），Root/导出文件亦不可直接读。

---

## 7. 高性能实践清单

| 实践 | 落地位置 | 收益 |
|------|---------|------|
| **WAL 模式** | `SupportOpenHelperFactory(pass, null, enableWAL = true)` | 读不阻塞写，并发显著提升 |
| `synchronous = NORMAL` | `PerformancePragmas.onOpen` | WAL 下已保证崩溃一致性，省去每次 fsync |
| `temp_store = MEMORY` | `PerformancePragmas.onOpen` | 排序/临时表走内存，减少磁盘 I/O |
| **单条写即一事务** | 所有 DAO 写操作 `suspend`，Room 自动单事务 | 避免半写状态 |
| **覆盖索引** | `export_runs(status, createdAt)` / `projects(createdAt)` | 筛选 + 排序均走索引 |
| **按需分页** | Paging3 `PagingSource`（`LogEntryDao.pagingSource`） | 长列表不一次性读入内存 |
| **写全 suspend** | 所有 `@Insert/@Update/@Query(写)` | 主线程零阻塞（配合 `Dispatchers.IO`） |
| **读返回 Flow** | `observeAll` / `observeById` | 表变更自动推送，UI 免手动刷新 |
| **惰性打开** | `by lazy` 于 `MmaApplication` | 不拖慢冷启动 |
| **后台建连** | `MmaApplication` 的 `mma-db-init` 线程 | 加密库打开不阻塞冷启动 |

---

## 8. 迁移策略

- 当前 `version = 4`，迁移链 `MIGRATION_1_2` → `MIGRATION_2_3` → `MIGRATION_3_4`
  均显式登记于 `AppDatabaseFactory.MIGRATIONS`（实现在 `data/local/Migrations.kt`）；**已移除** `fallbackToDestructiveMigration()`
  （由 `Migration_3_4_Test` + 改写后 `Migration_2_3_Test` 三重校验兜底，覆盖 1→2→3→4 / 2→3→4 / 3→4 升级路径，数据零丢失）。
- `exportSchema = true` → schema JSON 输出到 `app/schemas/`，**随代码提交**
  （保留 `1.json`/`2.json`/`3.json` 审计，新增 `4.json`）。
- 新增版本必须同时在 `AppDatabase.version` +1 并在 `MIGRATIONS` 数组登记。
- 加密库迁移额外注意：口令不变；若更换引擎/口令派生方式，需写一次性数据搬迁。

---

## 9. 使用示例

```kotlin
// 取得仓储（任意 Context）
val projectRepo = context.requireMmaApplication().projectRepository
val exportRepo = context.requireMmaApplication().exportRunRepository

// 新建项目（用仓储的 newProject 工厂填充默认值，避免各处硬编码）
val id = projectRepo.create(
    ProjectRepository.newProject(
        projectType = ProjectType.URL,
        apkName = "Example",
        packageName = "com.example.app",
        entry = ProjectEntry.Url("https://example.com"),
    ),
)

// 登记一次导出运行并标记终态
val runId = exportRepo.start(id, ExportStatus.RUNNING)
exportRepo.markSucceeded(runId, outputApkPath = apkPath, apkSizeBytes = size)

// 观察（ViewModel）
projectRepo.observeAll().collect { projects -> /* 更新 UI 状态 */ }
```

**线程约定**：所有 `suspend` 写操作必须在 `Dispatchers.IO` 中调用；
`Flow` 读取由 Room 在事务线程执行，可直接在主线程 collect。

---

## 10. 测试

- `app/src/test/java/com/make/apk/data/Migration_2_3_Test.kt` / `Migration_3_4_Test.kt`
  —— 三重校验（Room 真实迁移 + 运行期 `onValidateSchema` 强校验 + 与 KSP 导出 `N.json` 逐字比对），
  覆盖 1→2→3→4 / 2→3→4 / 3→4 升级路径与零数据丢失断言。
- 既有 Paparazzi 截图回归（**47 张快照**）全部通过，未受阶段 5 改动影响。
- 运行：`./gradlew testDebugUnitTest`（当前共 **344 个用例，全绿**）

> 尚未覆盖（建议在真机/Instrumented 测试中补充）：SQLCipher 实际打开与加解密、
> DAO 查询语义、事务回滚。这些需要 Android 运行环境（Room 内存库或 Instrumentation）。

---

## 11. 常见坑

| 现象 | 原因 | 处理 |
|------|------|------|
| `Could not resolve net.zetetic:android-db-sqlcipher` | 该 artifact 仅在 Zetetic 私有仓库，`maven.zetetic.net` 已失效 | 改用 Maven Central 的 `net.zetetic:sqlcipher-android` |
| `checkDebugAarMetadata` 报 "requires ... compile against version 36/37" | SQLCipher 4.18+ 的 minCompileSdk 高于项目 compileSdk 34 | 降到 4.13.0，或整体升级 AGP + compileSdk + Kotlin |
| `metadata is 2.1.0, expected version is 1.9.0` | `androidx.sqlite 2.6.x`（SQLCipher 4.14.1+ 传递）由 Kotlin 2.1 编译 | 降到 SQLCipher 4.13.0，并强制 stdlib 对齐 1.9.24 |
| `Unresolved reference: MasterKey` | security-crypto 1.0.0 的类是 **`MasterKeys`**（复数） | 用 `MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)` |
| `create(...)` 报 "inferred Context but String expected" | 1.0.0 的 `create(fileName, alias, context, ...)`，context 在**第三位** | 按 1.0.0 的参数顺序传参 |
| `PagingData.map` 编译不过 / "receiver type mismatch" | ① 它是**顶层扩展**，必须 `import androidx.paging.map`；② transform 是 suspend，**不能传函数引用** | 补导入 + 用 lambda：`page.map { it.toDomain() }` |
| 测试里 `assertEquals(1234567, nullableLong)` 失败 | 可空 `Long?` 无法匹配 `assertEquals(long,long)`，`Int` 字面量被装箱成 `Integer` | 写 `Long` 字面量：`assertEquals(1_234_567L, ...)` |
| KSP 报 "incompatible" | KSP 版本与 Kotlin 版本不匹配 | 保持 `ksp = "1.9.24-1.0.20"` ↔ `kotlin = "1.9.24"` |
| 数据库打不开 | 口令丢失（清数据后 Keystore 密钥销毁） | 预期行为；勿在无 Keystore 环境强行复用旧库 |
| 升级 Room 后编译失败 | Room 与 KSP/Kotlin 版本耦合 | 三者同步升级（Room 2.7+ 需 Kotlin 2.0 + KSP 2.x） |

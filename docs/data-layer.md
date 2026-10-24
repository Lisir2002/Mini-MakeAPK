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
| 依赖注入 | 轻量手工单例（`Web2ApkApplication`），不引入 Hilt |
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
| `net.zetetic:sqlcipher-android` | 4.17.0 | 加密 SQLite 引擎 | 4.18+ 需 compileSdk ≥ 37，勿擅自升 |
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
app/src/main/java/com/web2apk/app/
├── Web2ApkApplication.kt              # 进程级单例：database / repository
└── data/
    ├── model/BuildRecord.kt           # 领域模型 + 枚举（无 Room 注解，UI 可见）
    ├── local/
    │   ├── AppDatabase.kt             # RoomDatabase（v1，exportSchema=true）
    │   ├── AppDatabaseFactory.kt      # ★ Room × SQLCipher 接线点 + PRAGMA 调优
    │   ├── Converters.kt              # 枚举 ⇄ TEXT，未知值安全回退
    │   ├── entity/BuildRecordEntity.kt# 表结构 + 索引
    │   └── dao/BuildRecordDao.kt      # suspend 写 / Flow 读 / PagingSource
    ├── security/SqlCipherPassphrase.kt# 口令生成 + Keystore 加密存储
    ├── mapper/BuildRecordMapper.kt    # 实体 ⇄ 领域模型
    └── repository/BuildHistoryRepository.kt  # 上层唯一出口
```

**边界规则**：

1. UI / ViewModel 只依赖 `data.model.BuildRecord` 与 `BuildHistoryRepository`。
2. Room 注解、表名、索引**不得**越出 `data.local`。
3. 仓储对外只暴露领域模型，实体在 `mapper` 层完成转换。

---

## 5. 表结构与索引

表 `build_records`（`BuildRecordEntity`）：

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

索引：

```kotlin
indices = [
    Index(value = ["createdAt"]),              // 历史倒序分页，避免全表排序
    Index(value = ["status", "createdAt"]),     // 按状态筛选时仍走有序索引扫描
]
```

枚举以 TEXT 存储（可读性 + 迁移友好），经 `Converters` 转换；
**解析未知值时回退安全默认值**而非抛异常，避免脏数据导致数据库整体打不开。

---

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
| **批量单事务** | `repository.replaceAll` 用 `withTransaction` | 避免 N 次写 = N 次 fsync |
| **覆盖索引** | `status + createdAt` | 筛选 + 排序均走索引 |
| **按需分页** | Paging3 `PagingSource` | 长列表不一次性读入内存 |
| **写全 suspend** | 所有 `@Insert/@Update/@Query(写)` | 主线程零阻塞（配合 `Dispatchers.IO`） |
| **读返回 Flow** | `observeAll` / `observeById` | 表变更自动推送，UI 免手动刷新 |
| **惰性打开** | `by lazy` 于 `Web2ApkApplication` | 不拖慢冷启动 |
| **列级更新** | `updateStatus` 仅写状态列 | 避免整行回写 |

---

## 8. 迁移策略

- 当前 `version = 1`，无历史版本，配置 `fallbackToDestructiveMigration()`。
- `exportSchema = true` → schema JSON 输出到 `app/schemas/`，**随代码提交**。
- **发布真实版本前必须**：删除 `fallbackToDestructiveMigration()`，为每次版本变更
  编写显式 `Migration(from, to)` 并在 `AppDatabaseFactory` 中 `addMigrations(...)`。
- 加密库迁移额外注意：口令不变；若更换引擎/口令派生方式，需写一次性数据搬迁。

---

## 9. 使用示例

```kotlin
// 取得仓储（任意 Context）
val repo = context.requireWeb2ApkApplication().buildHistoryRepository

// 登记一次构建
val id = repo.start(
    inputType = BuildInputType.URL,
    sourceLabel = "example.com",
    sourceRef = "https://example.com",
    engine = BuildEngine.DEVICE_WEBVIEW,
)

// 结束态
repo.markSucceeded(id, outputApkPath = apkPath, apkSizeBytes = size)
repo.markFailed(id, errorMessage = "构建超时")

// 观察（ViewModel）
repo.observeAll().collect { records -> /* 更新 UI 状态 */ }

// 长列表分页
repo.pagedHistory().cachedIn(viewModelScope).collectAsLazyPagingItems()
```

**线程约定**：所有 `suspend` 写操作必须在 `Dispatchers.IO` 中调用；
`Flow` 读取由 Room 在事务线程执行，可直接在主线程 collect。

---

## 10. 测试

- `app/src/test/java/com/web2apk/app/data/BuildRecordMapperTest.kt`
  —— 纯 JVM（6 个用例），覆盖：实体⇄领域无损往返、枚举转换器往返、未知值安全回退、终态判定。
- 既有 Paparazzi 截图回归（8 个用例）仍全部通过，未受数据层改动影响。
- 运行：`./gradlew testDebugUnitTest`（当前共 **14 个用例，全绿**）

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

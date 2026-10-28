package com.make.apk.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebSettings

/**
 * 项目表（SQLite 持久化形态，§8.2）。
 *
 * 表职责：承载**稳定定义**（身份 + 配置），与运行态（`export_runs`）分离。
 *
 * 索引设计：
 * - `createdAt`：项目列表按创建时间倒序，避免全表排序。
 * - `packageName`：包名唯一性查询（应用层强制拦截重复，见 `ProjectRepository`）。
 *   **刻意不加 UNIQUE 约束**——历史数据可能已含重复包名（v2 迁移搬运自 `build_records`），
 *   加 UNIQUE 会让迁移直接失败；唯一性由应用层拦截，DB 只提供查询加速（§2.6 / §8.2）。
 *
 * `entry_json` / `webSettings_json` 以 TEXT 存 JSON（§8.2）：与统一内核契约
 * [com.make.apk.data.model.WebRuntimeConfig] 同源序列化，本地渲染与导出模板壳读同一份结构。
 */
@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["packageName"]),
    ],
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "createdAt")
    val createdAt: Long,
    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = createdAt,
    @ColumnInfo(name = "projectType")
    val projectType: ProjectType,
    @ColumnInfo(name = "apkName")
    val apkName: String,
    @ColumnInfo(name = "packageName")
    val packageName: String,
    @ColumnInfo(name = "versionScheme")
    val versionScheme: VersionScheme,
    @ColumnInfo(name = "versionName")
    val versionName: String,
    @ColumnInfo(name = "versionCode")
    val versionCode: Int,
    @ColumnInfo(name = "iconRef")
    val iconRef: String? = null,
    @ColumnInfo(name = "themeColor")
    val themeColor: Int,
    /** 入口（URL / HTML / 资产包）序列化后的 JSON。 */
    @ColumnInfo(name = "entry_json")
    val entryJson: String,
    /** Web 渲染配置序列化后的 JSON。 */
    @ColumnInfo(name = "webSettings_json")
    val webSettingsJson: String,
    /** 本地多实例数据隔离目录后缀。 */
    @ColumnInfo(name = "isolationId")
    val isolationId: String,
    /**
     * 壳契约（L2）序列化后的 JSON；**可空**。
     *
     * `null` = 未配置 → 领域层兜底为 [com.make.apk.data.model.ShellSpec.DEFAULT]
     * （四区全关 = 裸 WebView，与阶段 5 一致）。
     *
     * 之所以可空且无 SQL DEFAULT：迁移 SQL 的默认值字面量必须与 Room 导出的 schema JSON
     * **逐字匹配**，否则 `onValidateSchema` 直接抛异常（阶段 5 踩过的坑，见 `Migrations.kt`）。
     *
     * **声明位置必须在字段列表最末**（决策正确，理由如下）：
     *
     * - SQLite 的 `ALTER TABLE ... ADD COLUMN` **只能把新列追加到表末尾**；
     * - 若本字段声明在中间，则"全新安装"（Room 按实体声明序建表）与"老库升级"
     *   （`ALTER TABLE` 追加到末尾）两条路径产出的**物理列序**会分叉；
     * - 分叉真正会打红的是**本仓库自己的断言**：迁移产物与 `5.json` 的 **DDL 逐字比对**，
     *   以及"全新安装 vs 老库升级结构一致"断言（见 `Migration_4_5_ColumnOrderTest`）；
     * - **不是** `onValidateSchema`：Room 2.6.1 的 `TableInfo.equals` 比较的是
     *   `columns: Map<String, Column>`（按**列名**索引，**与列序无关**），
     *   故列序分叉不会触发 `onValidateSchema` 抛 `IllegalStateException`。
     */
    @ColumnInfo(name = "shell_spec_json")
    val shellSpecJson: String? = null,
)

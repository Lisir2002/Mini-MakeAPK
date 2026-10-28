package com.make.apk.data.local

import androidx.room.TypeConverter
import com.make.apk.data.model.ExportStatus
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme

/**
 * Room 类型转换器：枚举以 TEXT 落库。
 *
 * 解析失败时回退到安全默认值而非抛异常——避免旧数据/手工改库导致
 * 整个数据库打开失败（App 崩溃），这是移动端的稳健性要求。
 */
class Converters {

    // ── 统一内核（§2.3 / §2.4）──────────────────────────────────────

    @TypeConverter
    fun fromProjectType(value: ProjectType?): String? = value?.name

    /** 兜底 `URL`：迁移期旧数据的首个候选，语义最安全（仅加载远程页，不写盘）。 */
    @TypeConverter
    fun toProjectType(value: String?): ProjectType = safeEnum(value, ProjectType.URL)

    @TypeConverter
    fun fromVersionScheme(value: VersionScheme?): String? = value?.name

    /** 兜底三段式（§2.6 默认 `THREE_SEGMENT` / `1.0.0`）。 */
    @TypeConverter
    fun toVersionScheme(value: String?): VersionScheme = safeEnum(value, VersionScheme.THREE_SEGMENT)

    @TypeConverter
    fun fromExportStatus(value: ExportStatus?): String? = value?.name

    @TypeConverter
    fun toExportStatus(value: String?): ExportStatus = safeEnum(value, ExportStatus.QUEUED)

    private inline fun <reified T : Enum<T>> safeEnum(value: String?, fallback: T): T =
        runCatching { value?.let { enumValueOf<T>(it) } }.getOrNull() ?: fallback
}

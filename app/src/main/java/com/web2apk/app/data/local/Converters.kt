package com.web2apk.app.data.local

import androidx.room.TypeConverter
import com.web2apk.app.data.model.BuildEngine
import com.web2apk.app.data.model.BuildInputType
import com.web2apk.app.data.model.BuildStatus

/**
 * Room 类型转换器：枚举以 TEXT 落库。
 *
 * 解析失败时回退到安全默认值而非抛异常——避免旧数据/手工改库导致
 * 整个数据库打开失败（App 崩溃），这是移动端的稳健性要求。
 */
class Converters {

    @TypeConverter
    fun fromBuildStatus(value: BuildStatus?): String? = value?.name

    @TypeConverter
    fun toBuildStatus(value: String?): BuildStatus = safeEnum(value, BuildStatus.QUEUED)

    @TypeConverter
    fun fromBuildInputType(value: BuildInputType?): String? = value?.name

    @TypeConverter
    fun toBuildInputType(value: String?): BuildInputType = safeEnum(value, BuildInputType.URL)

    @TypeConverter
    fun fromBuildEngine(value: BuildEngine?): String? = value?.name

    @TypeConverter
    fun toBuildEngine(value: String?): BuildEngine = safeEnum(value, BuildEngine.DEVICE_WEBVIEW)

    private inline fun <reified T : Enum<T>> safeEnum(value: String?, fallback: T): T =
        runCatching { value?.let { enumValueOf<T>(it) } }.getOrNull() ?: fallback
}

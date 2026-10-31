package com.make.apk.data.local.dao

import androidx.room.ColumnInfo

/**
 * 按类别分组的计数投影（**POJO，不是实体**）。
 *
 * 由 `LogEntryDao.observeCategoryCounts()` 的 `GROUP BY category` 直接映射，
 * 不参与建表、不影响 Room schema（因此无需 Migration）。
 *
 * 用途：日志查看页顶部的四大分组计数。**走 SQL 聚合而非分页窗口**，
 * 这样"问题 3 条"始终是全量口径，不随用户滚动而变。
 */
data class CategoryCountRow(
    /** 类别名（[com.make.apk.logging.LogCategory] 的 `name`）。 */
    @ColumnInfo(name = "category") val category: String,
    /** 该类别下的条目数。 */
    @ColumnInfo(name = "count") val count: Int,
)

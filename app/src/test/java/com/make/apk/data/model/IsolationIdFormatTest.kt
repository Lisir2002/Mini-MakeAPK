package com.make.apk.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 隔离目录名新格式（W3 ⑬）契约测试：
 *
 * - 形态：`MakeApk_<yyyyMMdd_HHmmss>_item<projectId>`；
 * - **文件名安全铁律**：绝不含 `:` / `/` / 空格（冒号在 Windows 同步盘 / FAT 上非法）；
 * - 同一秒创建的多个项目靠 `_item<id>` 尾段去重（id 单调递增，不依赖时间戳抖动）；
 * - 存量不迁移：`webapp_<id>` 旧值不再由本函数产出（兼容性证据见 Migration_*Test）。
 */
class IsolationIdFormatTest {

    @Test
    fun format_matchesNewShape() {
        val id = ProjectDefaults.isolationIdOf(projectId = 42L, createdAtMs = 1_770_000_000_000L)
        assertTrue(
            "应符合 MakeApk_<yyyyMMdd_HHmmss>_item<id>：$id",
            id.matches(Regex("""MakeApk_\d{8}_\d{6}_item\d+""")),
        )
        assertTrue("应以项目 id 收尾：$id", id.endsWith("_item42"))
    }

    @Test
    fun format_neverContainsColonSlashOrSpace() {
        // 文件名安全铁律：多组时间戳全量校验（覆盖毫秒→秒/分/时进位的各种形态）。
        listOf(0L, 1L, 999L, 59_999L, 1_770_000_000_000L, System.currentTimeMillis()).forEach { ts ->
            val id = ProjectDefaults.isolationIdOf(7L, ts)
            listOf(':', '/', ' ', '\\').forEach { banned ->
                assertTrue("不得含 '${banned}'：$id", !id.contains(banned))
            }
        }
    }

    @Test
    fun timestampSegment_matchesFormatFunction() {
        val ts = 1_770_000_000_123L
        val id = ProjectDefaults.isolationIdOf(9L, ts)
        assertEquals(
            "时间戳段必须与 formatIsolationTimestamp 一致（本地时区单一事实源）",
            "MakeApk_${ProjectDefaults.formatIsolationTimestamp(ts)}_item9",
            id,
        )
        assertTrue(
            "时间戳段应为 15 字符（8 日期 + 下划线 + 6 时刻）：${ProjectDefaults.formatIsolationTimestamp(ts)}",
            ProjectDefaults.formatIsolationTimestamp(ts).length == 15,
        )
    }

    @Test
    fun sameSecond_projectsDifferByItemId() {
        // 同一毫秒（同秒）创建的两个项目：时间戳段相同，id 尾段去重。
        val ts = 1_770_000_000_000L
        val a = ProjectDefaults.isolationIdOf(1L, ts)
        val b = ProjectDefaults.isolationIdOf(2L, ts)
        assertNotEquals("同秒项目隔离名不得冲突", a, b)
        assertTrue(a.substringBeforeLast("_item") == b.substringBeforeLast("_item"))
    }

    @Test
    fun itemIdSegment_equalsProjectId() {
        val id = ProjectDefaults.isolationIdOf(123456789L, 1L)
        assertTrue("序号段必须等于项目 id：$id", id.endsWith("_item123456789"))
    }
}

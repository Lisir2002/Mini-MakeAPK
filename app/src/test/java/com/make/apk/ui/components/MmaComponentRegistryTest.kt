package com.make.apk.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组件注册表门禁（§优化·第三点防回潮）：
 * 保证"每类型仅一条登记、每条均有规范样式引用"，从编译期契约层面杜绝同类组件多套样式。
 */
class MmaComponentRegistryTest {

    /** 每种组件类型在注册表中出现且仅出现一次（唯一规范样式）。 */
    @Test
    fun everyKindRegisteredExactlyOnce() {
        val kinds = MmaComponentRegistry.entries.map { it.kind }
        assertEquals(
            "组件类型重复登记会导致同类多套样式",
            kinds.size,
            kinds.toSet().size,
        )
    }

    /** 每条登记都必须引用规范样式令牌（不得内联裸值 / 留空）。 */
    @Test
    fun everyEntryCarriesStyleRef() {
        MmaComponentRegistry.entries.forEach { entry ->
            assertTrue("${entry.kind} 必须登记规范样式引用", entry.styleRef.isNotBlank())
        }
    }

    /** 按类型查登记项能稳定解析（单一事实来源可被消费方直接引用）。 */
    @Test
    fun entryLookupResolvesUniquely() {
        MmaComponentRegistry.Kind.values().forEach { kind ->
            val entry = MmaComponentRegistry.entryOf(kind)
            assertEquals(kind, entry.kind)
        }
    }
}

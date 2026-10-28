package com.make.apk.ui.editor

import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验器边界补测（阶段 2 审查补充）。
 *
 * 覆盖既有 [ProjectValidatorTest] 未覆盖 / 覆盖不足的边界：
 * - 包名空段、尾点、前导数字、单段、大小写、保留前缀变体；
 * - 版本号段值越界（999 边界 / 1000 越界）；
 * - **版本号段值超出 Int 范围**（应返回错误，而**不是**抛异常）；
 * - 入口 URL 的协议拦截与 host 缺失。
 *
 * 纯函数契约：任何输入都不得抛出异常——校验器是纯函数，
 * 异常一旦逃逸就会打断 ViewModel 的 `revalidate()` 并把崩溃带到 UI 层。
 */
class ProjectValidatorEdgeCaseTest {

    private val validator = ProjectValidator()

    // ── 包名：格式边界 ───────────────────────────────────────────────────

    @Test
    fun packageName_emptySegment_isRejected() {
        // "com..x" 中间空段
        assertTrue(validator.validatePackageName("com..x").hasError)
    }

    @Test
    fun packageName_trailingDot_isRejected() {
        // "com.example." 尾点
        assertTrue(validator.validatePackageName("com.example.").hasError)
    }

    @Test
    fun packageName_leadingDot_isRejected() {
        assertTrue(validator.validatePackageName(".com.example").hasError)
    }

    @Test
    fun packageName_leadingDigit_isRejected() {
        assertTrue(validator.validatePackageName("1com.x").hasError)
    }

    @Test
    fun packageName_singleSegment_isRejected() {
        // 至少两段
        assertTrue(validator.validatePackageName("com").hasError)
    }

    @Test
    fun packageName_upperCase_isRejected() {
        assertTrue(validator.validatePackageName("Com.Example").hasError)
    }

    @Test
    fun packageName_leadingUnderscore_isRejected() {
        assertTrue(validator.validatePackageName("_com.example").hasError)
    }

    @Test
    fun packageName_trailingUnderscoreInSegment_isAllowed() {
        assertFalse(validator.validatePackageName("com.example_.app").hasError)
    }

    @Test
    fun packageName_digitsInsideSegment_areAllowed() {
        assertFalse(validator.validatePackageName("com.app2.x1").hasError)
    }

    @Test
    fun packageName_whitespaceAround_isTrimmedAndAccepted() {
        assertFalse(validator.validatePackageName("  com.example.app  ").hasError)
    }

    @Test
    fun packageName_blank_isRejected() {
        assertTrue(validator.validatePackageName("   ").hasError)
    }

    // ── 包名：保留前缀 ───────────────────────────────────────────────────

    @Test
    fun packageName_reservedPrefixes_areAllRejected() {
        listOf(
            "android.test",
            "java.test",
            "com.android.test",
            "com.google.test",
        ).forEach { candidate ->
            assertTrue(
                "保留前缀应被拦截：$candidate",
                validator.validatePackageName(candidate).hasError,
            )
        }
    }

    @Test
    fun packageName_reservedPrefixIsOnlyRejectedAsPrefix() {
        // "com.example.android" 不以保留前缀开头 → 允许
        assertFalse(validator.validatePackageName("com.example.android").hasError)
    }

    @Test
    fun packageName_tooLong_isRejected() {
        val long = "com." + "a".repeat(300)
        assertTrue(validator.validatePackageName(long).hasError)
    }

    // ── 版本号：段值边界 ─────────────────────────────────────────────────

    @Test
    fun versionName_segment999_isAccepted() {
        assertFalse(
            validator.validateVersionName("999.999.999", VersionScheme.THREE_SEGMENT).hasError,
        )
    }

    @Test
    fun versionName_segment1000_isRejected() {
        val verdict = validator.validateVersionName("1000.0.0", VersionScheme.THREE_SEGMENT)
        assertTrue(verdict.hasError)
        assertEquals(ProjectValidator.MSG_VERSION_SEGMENT_RANGE, verdict.error?.text)
    }

    @Test
    fun versionName_segmentBeyondIntRange_returnsErrorInsteadOfThrowing() {
        // 回归：曾经用 `it.toInt()` 解析段值，超出 Int 范围会抛 NumberFormatException
        // 并逃逸出"纯函数"校验器，把崩溃带到 ViewModel / UI 层。
        listOf("10000000000.0.0", "2147483648.0.0", "99999999999999999999.0.0").forEach { candidate ->
            val verdict = validator.validateVersionName(candidate, VersionScheme.THREE_SEGMENT)
            assertTrue("应返回错误而非抛异常：$candidate", verdict.hasError)
            assertEquals(ProjectValidator.MSG_VERSION_SEGMENT_RANGE, verdict.error?.text)
        }
    }

    @Test
    fun versionName_negativeSegment_isRejectedAsFormatError() {
        val verdict = validator.validateVersionName("-1.0.0", VersionScheme.THREE_SEGMENT)
        assertTrue(verdict.hasError)
        assertEquals(ProjectValidator.MSG_VERSION_FORMAT, verdict.error?.text)
    }

    @Test
    fun versionName_emptySegment_isRejected() {
        assertTrue(validator.validateVersionName("1..0", VersionScheme.THREE_SEGMENT).hasError)
    }

    @Test
    fun versionName_schemeMismatch_twoSegmentRequested() {
        val verdict = validator.validateVersionName("1.0.0", VersionScheme.TWO_SEGMENT)
        assertTrue(verdict.hasError)
        assertEquals(ProjectValidator.MSG_VERSION_SCHEME_TWO, verdict.error?.text)
    }

    @Test
    fun versionName_schemeMismatch_threeSegmentRequested() {
        val verdict = validator.validateVersionName("1.0", VersionScheme.THREE_SEGMENT)
        assertTrue(verdict.hasError)
        assertEquals(ProjectValidator.MSG_VERSION_SCHEME_THREE, verdict.error?.text)
    }

    // ── versionCode ─────────────────────────────────────────────────────

    @Test
    fun versionCode_boundaries() {
        assertFalse(validator.validateVersionCode("1").hasError)
        assertFalse(validator.validateVersionCode("2147483647").hasError)
        assertTrue(validator.validateVersionCode("0").hasError)
        assertTrue(validator.validateVersionCode("2147483648").hasError)
        assertTrue(validator.validateVersionCode("abc").hasError)
        assertTrue(validator.validateVersionCode("").hasError)
    }

    // ── 入口 URL ────────────────────────────────────────────────────────

    @Test
    fun entryUrl_blockedSchemes_areRejected() {
        listOf("javascript:alert(1)", "data:text/html,<p>", "file:///etc/passwd").forEach { candidate ->
            assertTrue(
                "应拦截协议：$candidate",
                validator.validateEntryUrl(candidate).hasError,
            )
        }
    }

    @Test
    fun entryUrl_withoutScheme_isRejected() {
        assertTrue(validator.validateEntryUrl("example.com").hasError)
    }

    @Test
    fun entryUrl_withEmptyHost_isRejected() {
        assertTrue(validator.validateEntryUrl("http://").hasError)
    }

    @Test
    fun entryUrl_validUrl_isAccepted() {
        assertFalse(validator.validateEntryUrl("https://example.com/path?q=1").hasError)
    }

    // ── 建议函数 ────────────────────────────────────────────────────────

    @Test
    fun suggestPackageName_onlyForUrlType() {
        assertEquals(
            "com.example",
            validator.suggestPackageName(ProjectType.URL, "https://www.example.com/x"),
        )
        assertEquals(null, validator.suggestPackageName(ProjectType.HTML, "https://example.com"))
        assertEquals(null, validator.suggestPackageName(null, "https://example.com"))
    }

    @Test
    fun suggestApkName_stripsWwwAndPath() {
        assertEquals(
            "example.com",
            validator.suggestApkName(ProjectType.URL, "https://www.example.com/path", "", null),
        )
    }

    @Test
    fun suggestApkName_htmlUsesDefaultLabel() {
        assertEquals(
            ProjectValidator.DEFAULT_HTML_NAME,
            validator.suggestApkName(ProjectType.HTML, "", "<html></html>", null),
        )
    }
}

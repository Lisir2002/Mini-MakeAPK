package com.make.apk.ui.editor

import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 校验器单测（§2.6 规则全表）。
 *
 * 纯 JVM 测试：`ProjectValidator` 不依赖任何 `android.*`，因此无需 Robolectric。
 *
 * 覆盖要点：
 * - 每条规则至少一例（含包名保留前缀 4 种、版本段式 2 种等分支）；
 * - 边界值（51 字 / 31 字 / 255 字 / 999 / 1000）。
 */
class ProjectValidatorTest {

    private val validator = ProjectValidator()

    // ── 项目类型 ───────────────────────────────────────────────────────

    @Test
    fun projectType_notSelected_isError() {
        val verdict = validator.validateProjectType(null)
        assertTrue(verdict.hasError)
        assertEquals(ProjectValidator.MSG_TYPE_REQUIRED, verdict.error?.text)
    }

    @Test
    fun projectType_selected_passes() {
        assertFalse(validator.validateProjectType(ProjectType.URL).hasError)
    }

    // ── 应用名称 ───────────────────────────────────────────────────────

    @Test
    fun apkName_blank_isError() {
        val verdict = validator.validateApkName("   ")
        assertEquals(ProjectValidator.MSG_NAME_REQUIRED, verdict.error?.text)
    }

    @Test
    fun apkName_51Chars_isError() {
        val verdict = validator.validateApkName("名".repeat(51))
        assertEquals(ProjectValidator.MSG_NAME_TOO_LONG, verdict.error?.text)
    }

    @Test
    fun apkName_31Chars_isWarningOnly() {
        val verdict = validator.validateApkName("名".repeat(31))
        assertFalse(verdict.hasError)
        assertEquals(ProjectValidator.MSG_NAME_TOO_LONG_SOFT, verdict.warning?.text)
    }

    @Test
    fun apkName_withNewline_isError() {
        val verdict = validator.validateApkName("我的\n应用")
        assertEquals(ProjectValidator.MSG_NAME_ILLEGAL, verdict.error?.text)
    }

    @Test
    fun apkName_50Chars_hasNoError() {
        val verdict = validator.validateApkName("名".repeat(50))
        assertFalse(verdict.hasError)
    }

    @Test
    fun apkName_30Chars_hasNoWarning() {
        val verdict = validator.validateApkName("名".repeat(30))
        assertFalse(verdict.hasError)
        assertNull(verdict.warning)
    }

    // ── 包名 ───────────────────────────────────────────────────────────

    @Test
    fun packageName_valid_passes() {
        assertFalse(validator.validatePackageName("com.example.app").hasError)
    }

    @Test
    fun packageName_singleSegment_isError() {
        val verdict = validator.validatePackageName("example")
        assertEquals(ProjectValidator.MSG_PACKAGE_FORMAT, verdict.error?.text)
    }

    @Test
    fun packageName_upperCase_isError() {
        val verdict = validator.validatePackageName("Com.Example.App")
        assertEquals(ProjectValidator.MSG_PACKAGE_FORMAT, verdict.error?.text)
    }

    @Test
    fun packageName_leadingDigit_isError() {
        val verdict = validator.validatePackageName("1com.example.app")
        assertEquals(ProjectValidator.MSG_PACKAGE_FORMAT, verdict.error?.text)
    }

    @Test
    fun packageName_reservedAndroidPrefix_isError() {
        val verdict = validator.validatePackageName("android.example.app")
        assertEquals(ProjectValidator.MSG_PACKAGE_RESERVED, verdict.error?.text)
    }

    @Test
    fun packageName_reservedJavaPrefix_isError() {
        val verdict = validator.validatePackageName("java.example.app")
        assertEquals(ProjectValidator.MSG_PACKAGE_RESERVED, verdict.error?.text)
    }

    @Test
    fun packageName_reservedComAndroidPrefix_isError() {
        val verdict = validator.validatePackageName("com.android.example")
        assertEquals(ProjectValidator.MSG_PACKAGE_RESERVED, verdict.error?.text)
    }

    @Test
    fun packageName_reservedComGooglePrefix_isError() {
        val verdict = validator.validatePackageName("com.google.example")
        assertEquals(ProjectValidator.MSG_PACKAGE_RESERVED, verdict.error?.text)
    }

    @Test
    fun packageName_tooLong_isError() {
        val verdict = validator.validatePackageName("com." + "a".repeat(260))
        assertEquals(ProjectValidator.MSG_PACKAGE_TOO_LONG, verdict.error?.text)
    }

    @Test
    fun packageName_occupiedMessage_isError() {
        val message = validator.packageNameOccupied()
        assertEquals(FieldMessage.Severity.ERROR, message.severity)
        assertEquals(ProjectValidator.MSG_PACKAGE_OCCUPIED, message.text)
    }

    // ── 版本号 ─────────────────────────────────────────────────────────

    @Test
    fun versionName_twoSegment_valid_passes() {
        assertFalse(validator.validateVersionName("1.0", VersionScheme.TWO_SEGMENT).hasError)
    }

    @Test
    fun versionName_threeSegment_valid_passes() {
        assertFalse(validator.validateVersionName("1.0.0", VersionScheme.THREE_SEGMENT).hasError)
    }

    @Test
    fun versionName_threeSegmentValue_withTwoScheme_isError() {
        val verdict = validator.validateVersionName("1.0.0", VersionScheme.TWO_SEGMENT)
        assertEquals(ProjectValidator.MSG_VERSION_SCHEME_TWO, verdict.error?.text)
    }

    @Test
    fun versionName_twoSegmentValue_withThreeScheme_isError() {
        val verdict = validator.validateVersionName("1.0", VersionScheme.THREE_SEGMENT)
        assertEquals(ProjectValidator.MSG_VERSION_SCHEME_THREE, verdict.error?.text)
    }

    @Test
    fun versionName_segmentOver999_isError() {
        val verdict = validator.validateVersionName("1000.0.0", VersionScheme.THREE_SEGMENT)
        assertEquals(ProjectValidator.MSG_VERSION_SEGMENT_RANGE, verdict.error?.text)
    }

    @Test
    fun versionName_notNumeric_isError() {
        val verdict = validator.validateVersionName("a.b.c", VersionScheme.THREE_SEGMENT)
        assertEquals(ProjectValidator.MSG_VERSION_FORMAT, verdict.error?.text)
    }

    @Test
    fun versionName_999_passes() {
        assertFalse(validator.validateVersionName("999.999.999", VersionScheme.THREE_SEGMENT).hasError)
    }

    @Test
    fun versionCode_valid_passes() {
        assertFalse(validator.validateVersionCode("1").hasError)
    }

    @Test
    fun versionCode_zero_isError() {
        val verdict = validator.validateVersionCode("0")
        assertEquals(ProjectValidator.MSG_VERSION_CODE_RANGE, verdict.error?.text)
    }

    @Test
    fun versionCode_tooLarge_isError() {
        val verdict = validator.validateVersionCode("2147483648")
        assertEquals(ProjectValidator.MSG_VERSION_CODE_RANGE, verdict.error?.text)
    }

    @Test
    fun versionCode_notInteger_isError() {
        val verdict = validator.validateVersionCode("1.5")
        assertEquals(ProjectValidator.MSG_VERSION_CODE_RANGE, verdict.error?.text)
    }

    // ── 图标 ───────────────────────────────────────────────────────────

    @Test
    fun iconRef_null_passes() {
        assertFalse(validator.validateIconRef(null).hasError)
    }

    @Test
    fun iconRef_nonPng_isWarning() {
        val verdict = validator.validateIconRef("/tmp/icon.webp")
        assertFalse(verdict.hasError)
        assertEquals(ProjectValidator.MSG_ICON_PNG, verdict.warning?.text)
    }

    @Test
    fun iconRef_png_passes() {
        assertNull(validator.validateIconRef("/tmp/icon.png").warning)
    }

    // ── 入口：URL ──────────────────────────────────────────────────────

    @Test
    fun entryUrl_valid_passes() {
        assertFalse(validator.validateEntryUrl("https://example.com/a").hasError)
    }

    @Test
    fun entryUrl_javascriptScheme_isError() {
        val verdict = validator.validateEntryUrl("javascript:alert(1)")
        assertEquals(ProjectValidator.MSG_URL_SCHEME_BLOCKED, verdict.error?.text)
    }

    @Test
    fun entryUrl_dataScheme_isError() {
        val verdict = validator.validateEntryUrl("data:text/html,<h1>x</h1>")
        assertEquals(ProjectValidator.MSG_URL_SCHEME_BLOCKED, verdict.error?.text)
    }

    @Test
    fun entryUrl_fileScheme_isError() {
        val verdict = validator.validateEntryUrl("file:///etc/hosts")
        assertEquals(ProjectValidator.MSG_URL_SCHEME_BLOCKED, verdict.error?.text)
    }

    @Test
    fun entryUrl_noScheme_isError() {
        val verdict = validator.validateEntryUrl("example.com")
        assertEquals(ProjectValidator.MSG_URL_REQUIRED, verdict.error?.text)
    }

    @Test
    fun entryUrl_blankHost_isError() {
        val verdict = validator.validateEntryUrl("https://")
        assertEquals(ProjectValidator.MSG_URL_REQUIRED, verdict.error?.text)
    }

    // ── 入口：HTML ─────────────────────────────────────────────────────

    @Test
    fun entryHtml_blank_isError() {
        val verdict = validator.validateEntryHtml("   ")
        assertEquals(ProjectValidator.MSG_HTML_REQUIRED, verdict.error?.text)
    }

    @Test
    fun entryHtml_withoutTag_isError() {
        val verdict = validator.validateEntryHtml("纯文本")
        assertEquals(ProjectValidator.MSG_HTML_REQUIRED, verdict.error?.text)
    }

    @Test
    fun entryHtml_withTag_passes() {
        assertFalse(validator.validateEntryHtml("<html><body>hi</body></html>").hasError)
    }

    // ── 入口：资产包 ───────────────────────────────────────────────────

    @Test
    fun entryArchive_missingFile_isError() {
        val verdict = validator.validateEntryArchive(
            "/tmp/does-not-exist.zip",
            ArchiveProbeResult(exists = false, sizeBytes = 0L, extension = ".zip"),
        )
        assertEquals(ProjectValidator.MSG_ARCHIVE_REQUIRED, verdict.error?.text)
    }

    @Test
    fun entryArchive_blankPath_isError() {
        val verdict = validator.validateEntryArchive("", null)
        assertEquals(ProjectValidator.MSG_ARCHIVE_REQUIRED, verdict.error?.text)
    }

    @Test
    fun entryArchive_notZipExtension_isError() {
        val verdict = validator.validateEntryArchive(
            "/tmp/bundle.tar",
            ArchiveProbeResult(exists = true, sizeBytes = 10L, extension = ".tar"),
        )
        assertEquals(ProjectValidator.MSG_ARCHIVE_REQUIRED, verdict.error?.text)
    }

    @Test
    fun entryArchive_tooLarge_isError() {
        val verdict = validator.validateEntryArchive(
            "/tmp/big.zip",
            ArchiveProbeResult(
                exists = true,
                sizeBytes = ProjectValidator.MAX_ARCHIVE_SIZE_BYTES + 1,
                extension = ".zip",
            ),
        )
        assertEquals(ProjectValidator.MSG_ARCHIVE_REQUIRED, verdict.error?.text)
    }

    @Test
    fun entryArchive_valid_passes() {
        val verdict = validator.validateEntryArchive(
            "/tmp/ok.zip",
            ArchiveProbeResult(exists = true, sizeBytes = 1024L, extension = ".zip"),
        )
        assertFalse(verdict.hasError)
    }

    // ── 自动建议 ───────────────────────────────────────────────────────

    @Test
    fun suggestApkName_url_stripsWww() {
        assertEquals(
            "example.com",
            validator.suggestApkName(ProjectType.URL, "https://www.example.com/x", "", null),
        )
    }

    @Test
    fun suggestApkName_html_usesDefaultName() {
        assertEquals(
            ProjectValidator.DEFAULT_HTML_NAME,
            validator.suggestApkName(ProjectType.HTML, "", "<html></html>", null),
        )
    }

    @Test
    fun suggestApkName_archive_stripsExtension() {
        assertEquals(
            "bundle",
            validator.suggestApkName(ProjectType.VUE, "", "", "/tmp/bundle.zip"),
        )
    }

    @Test
    fun suggestPackageName_url_reversesDomain() {
        assertEquals(
            "com.example",
            validator.suggestPackageName(ProjectType.URL, "https://example.com/x"),
        )
    }

    @Test
    fun suggestPackageName_html_isNull() {
        assertNull(validator.suggestPackageName(ProjectType.HTML, ""))
    }

    @Test
    fun fieldMessage_severityIsObservable() {
        val message = FieldMessage("提示", FieldMessage.Severity.WARNING)
        assertNotNull(message.text)
        assertEquals(FieldMessage.Severity.WARNING, message.severity)
    }
}

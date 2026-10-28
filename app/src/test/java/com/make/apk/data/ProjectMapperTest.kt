package com.make.apk.data

import com.make.apk.data.json.RuntimeJson
import com.make.apk.data.local.Converters
import com.make.apk.data.local.entity.ProjectEntity
import com.make.apk.data.mapper.toDomain
import com.make.apk.data.mapper.toEntity
import com.make.apk.data.model.ArchiveKind
import com.make.apk.data.model.ExportStatus
import com.make.apk.data.model.Project
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectEntry
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.ShellPresets
import com.make.apk.data.model.ShellSpec
import com.make.apk.data.model.VersionScheme
import com.make.apk.data.model.WebRuntimeConfig
import com.make.apk.data.model.WebSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统一内核契约测试（Project / ProjectEntry / WebRuntimeConfig 的序列化与映射）。
 *
 * 覆盖：
 * - 实体 ⇄ 领域模型映射的无损性；
 * - `WebRuntimeConfig` 是本地渲染与导出模板壳**共用**的唯一契约（JSON 可往返）；
 * - 枚举转换器往返与未知值稳健回退；
 * - 脏 JSON 不让整个列表崩溃（降级为安全值）。
 */
class ProjectMapperTest {

    private val converters = Converters()

    private val sampleEntity = ProjectEntity(
        id = 7,
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_005_000,
        projectType = ProjectType.URL,
        apkName = "Example",
        packageName = "com.example.app",
        versionScheme = VersionScheme.THREE_SEGMENT,
        versionName = "1.3.8",
        versionCode = 3,
        iconRef = "content://icon/1",
        themeColor = ProjectDefaults.DEFAULT_THEME_COLOR,
        entryJson = RuntimeJson.encodeEntry(ProjectEntry.Url("https://example.com")),
        webSettingsJson = RuntimeJson.encodeSettings(WebSettings(userAgent = "MMA/1.0")),
        isolationId = "webapp_7",
    )

    @Test
    fun entityToDomain_mapsEveryField() {
        val domain = sampleEntity.toDomain()

        assertEquals(7L, domain.id)
        assertEquals(1_700_000_000_000L, domain.createdAt)
        assertEquals(1_700_000_005_000L, domain.updatedAt)
        assertEquals(ProjectType.URL, domain.projectType)
        assertEquals("Example", domain.apkName)
        assertEquals("com.example.app", domain.packageName)
        assertEquals(VersionScheme.THREE_SEGMENT, domain.versionScheme)
        assertEquals("1.3.8", domain.versionName)
        assertEquals(3, domain.versionCode)
        assertEquals("content://icon/1", domain.iconRef)
        assertEquals(ProjectDefaults.DEFAULT_THEME_COLOR, domain.themeColor)
        assertEquals(ProjectEntry.Url("https://example.com"), domain.entry)
        assertEquals("MMA/1.0", domain.webSettings.userAgent)
        assertEquals("webapp_7", domain.isolationId)
    }

    @Test
    fun roundTrip_entityToDomainToEntity_isLossless() {
        assertEquals(sampleEntity, sampleEntity.toDomain().toEntity())
    }

    @Test
    fun project_toRuntimeConfig_dropsDbOnlyFields() {
        val config = sampleEntity.toDomain().toRuntimeConfig()

        // WebRuntimeConfig 不含 id / createdAt / updatedAt（DB 专属字段）
        assertEquals(ProjectType.URL, config.projectType)
        assertEquals("Example", config.apkName)
        assertEquals("com.example.app", config.packageName)
        assertEquals("1.3.8", config.versionName)
        assertEquals(3, config.versionCode)
        assertEquals("webapp_7", config.isolationId)
    }

    @Test
    fun runtimeConfig_jsonRoundTrip_isLossless() {
        val config = sampleEntity.toDomain().toRuntimeConfig()
        val decoded = RuntimeJson.decodeRuntimeConfig(RuntimeJson.encodeRuntimeConfig(config))
        assertEquals(config, decoded)
    }

    @Test
    fun entry_json_usesStableDiscriminators() {
        // 判别符与包名解耦（@SerialName）：重构包路径不会让已导出的配置文件失效
        assertTrue(RuntimeJson.encodeEntry(ProjectEntry.Url("https://a.com")).contains("\"type\":\"url\""))
        assertTrue(RuntimeJson.encodeEntry(ProjectEntry.BundledHtml("<p/>")).contains("\"type\":\"html\""))
        assertTrue(
            RuntimeJson.encodeEntry(ProjectEntry.Archive("/a.zip", ArchiveKind.VUE))
                .contains("\"type\":\"archive\""),
        )
    }

    @Test
    fun entry_decoding_handlesAllThreeVariants() {
        assertEquals(
            ProjectEntry.Url("https://a.com"),
            RuntimeJson.decodeEntry("""{"type":"url","url":"https://a.com"}"""),
        )
        assertEquals(
            ProjectEntry.BundledHtml("<p/>"),
            RuntimeJson.decodeEntry("""{"type":"html","html":"<p/>"}"""),
        )
        assertEquals(
            ProjectEntry.Archive("/a.zip", ArchiveKind.GENERIC),
            RuntimeJson.decodeEntry("""{"type":"archive","assetRef":"/a.zip","kind":"GENERIC"}"""),
        )
    }

    @Test
    fun converters_enumRoundTrip() {
        ProjectType.values().forEach { type ->
            assertEquals(type, converters.toProjectType(converters.fromProjectType(type)))
        }
        VersionScheme.values().forEach { scheme ->
            assertEquals(scheme, converters.toVersionScheme(converters.fromVersionScheme(scheme)))
        }
        ExportStatus.values().forEach { status ->
            assertEquals(status, converters.toExportStatus(converters.fromExportStatus(status)))
        }
    }

    @Test
    fun converters_unknownTextFallsBackToSafeDefault() {
        // 旧数据或手工改库不应导致数据库打开失败
        assertEquals(ProjectType.URL, converters.toProjectType("NOT_A_TYPE"))
        assertEquals(VersionScheme.THREE_SEGMENT, converters.toVersionScheme("NOT_A_SCHEME"))
        assertEquals(ExportStatus.QUEUED, converters.toExportStatus("NOT_A_STATUS"))
        assertEquals(ProjectType.URL, converters.toProjectType(null))
    }

    @Test
    fun corruptEntryJson_degradesInsteadOfCrashing() {
        val broken = sampleEntity.copy(entryJson = "{not valid json")
        val domain = broken.toDomain()

        // 降级为空白 HTML：项目仍可打开、可编辑、可重新指定入口
        assertEquals(ProjectEntry.BundledHtml(""), domain.entry)
        // 其余字段不受影响
        assertEquals("Example", domain.apkName)
    }

    @Test
    fun corruptWebSettingsJson_degradesToDefaults() {
        val broken = sampleEntity.copy(webSettingsJson = "<<<")
        assertEquals(WebSettings(), broken.toDomain().webSettings)
    }

    @Test
    fun defaults_followDesignSpec() {
        assertEquals(VersionScheme.THREE_SEGMENT, ProjectDefaults.DEFAULT_VERSION_SCHEME)
        assertEquals("1.0.0", ProjectDefaults.defaultVersionName(VersionScheme.THREE_SEGMENT))
        assertEquals("1.0", ProjectDefaults.defaultVersionName(VersionScheme.TWO_SEGMENT))
        assertEquals(1, ProjectDefaults.DEFAULT_VERSION_CODE)
        assertEquals("webapp_42", ProjectDefaults.isolationIdOf(42))
        // 主题色 = 设计语言主色蓝 #2563EB 的 ARGB（旧默认珊瑚粉 #E39E97 已废弃，黑名单门禁拦截）
        assertEquals(0xFF2563EB.toInt(), ProjectDefaults.DEFAULT_THEME_COLOR)
    }

    @Test
    fun exportStatus_terminalFlagsAreCorrect() {
        assertTrue(ExportStatus.SUCCESS.isTerminal)
        assertTrue(ExportStatus.FAILED.isTerminal)
        assertTrue(ExportStatus.CANCELLED.isTerminal)
        assertEquals(false, ExportStatus.RUNNING.isTerminal)
        assertEquals(false, ExportStatus.QUEUED.isTerminal)
    }

    @Test
    fun webSettings_defaultsEnableAllRuntimeFeatures() {
        val defaults = WebSettings()
        assertTrue(defaults.javascriptEnabled)
        assertTrue(defaults.domStorageEnabled)
        assertTrue(defaults.thirdPartyCookies)
        assertTrue(defaults.fitToApp)
        assertNull(defaults.userAgent)
        assertNotEquals(0, defaults.cacheMode)
    }

    @Test
    fun project_newProject_usesDefaults() {
        val project = Project(
            id = 0,
            projectType = ProjectType.HTML,
            apkName = "Demo",
            packageName = "com.demo.app",
            versionScheme = ProjectDefaults.DEFAULT_VERSION_SCHEME,
            versionName = ProjectDefaults.defaultVersionName(ProjectDefaults.DEFAULT_VERSION_SCHEME),
            versionCode = ProjectDefaults.DEFAULT_VERSION_CODE,
            iconRef = null,
            themeColor = ProjectDefaults.DEFAULT_THEME_COLOR,
            entry = ProjectEntry.BundledHtml("<p/>"),
            webSettings = ProjectDefaults.DEFAULT_WEB_SETTINGS,
            isolationId = "",
            createdAt = 1L,
            updatedAt = 1L,
        )
        assertEquals(ProjectType.HTML, project.projectType)
        assertEquals("", project.isolationId)
    }

    // ── 壳契约（L2 · 阶段 6.1）────────────────────────────────────────

    @Test
    fun shellSpec_defaultsToFourZonesDisabled() {
        val spec = ShellSpec.DEFAULT
        assertEquals(false, spec.topBar.enabled)
        assertEquals(false, spec.topTabs.enabled)
        assertEquals(false, spec.bottomBar.enabled)
        assertEquals(false, spec.drawer.enabled)
        assertEquals(ShellSpec.CURRENT_SPEC_VERSION, spec.specVersion)
    }

    @Test
    fun shellSpec_jsonRoundTrip_isLossless() {
        val spec = ShellPresets.TABBED.spec
        val decoded = RuntimeJson.decodeShellSpec(RuntimeJson.encodeShellSpec(spec))
        assertEquals(spec, decoded)
    }

    @Test
    fun entityWithShellSpec_mapsBothWays() {
        val spec = ShellPresets.CLASSIC.spec
        val entity = sampleEntity.copy(
            shellSpecJson = RuntimeJson.encodeShellSpec(spec),
        )
        assertEquals(spec, entity.toDomain().shellSpec)
        assertEquals(entity, entity.toDomain().toEntity())
    }

    @Test
    fun nullShellSpecColumn_degradesToDefaultShell() {
        // v5 之前的既有行：shell_spec_json 为 NULL → 兜底默认壳（四区全关）
        val domain = sampleEntity.copy(shellSpecJson = null).toDomain()
        assertEquals(ShellSpec.DEFAULT, domain.shellSpec)
    }

    @Test
    fun corruptShellSpecJson_degradesToDefaultShell() {
        val domain = sampleEntity.copy(shellSpecJson = "<<<not json").toDomain()
        assertEquals(ShellSpec.DEFAULT, domain.shellSpec)
        // 其余字段不受影响
        assertEquals("Example", domain.apkName)
    }
}

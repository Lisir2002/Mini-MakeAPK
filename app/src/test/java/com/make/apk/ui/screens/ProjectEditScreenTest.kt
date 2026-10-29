package com.make.apk.ui.screens

import androidx.compose.runtime.Composable
import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.editor.EditField
import com.make.apk.ui.editor.FieldMessage
import com.make.apk.ui.editor.ProjectValidator
import com.make.apk.ui.theme.MmaTheme
import com.make.apk.ui.viewmodel.EditTab
import com.make.apk.ui.viewmodel.ProjectEditUiState
import org.junit.Rule
import org.junit.Test

/**
 * 项目编辑页截图回归（Paparazzi，纯 JVM）。
 *
 * 页面**无状态**（仅滚动态），因此基线可直接喂任意 [ProjectEditUiState]，
 * 不需要 ViewModel / 数据库 / 导航——钉住「状态 → 画面」的映射，与 `AddProjectScreenTest` 同构。
 *
 * 覆盖（命名带下划线保证文件名可读）：
 * - `light_expanded_basic`：亮色 · 展开 · 基本配置（默认态，保存可点）；
 * - `dark_expanded_basic`：暗色 · 展开 · 基本配置；
 * - `light_collapsed`：亮色 · 收起（仅底部 Tab 区满屏）；
 * - `light_tab_advanced`：亮色 · 进阶配置（壳四区编辑器）；
 * - `light_tab_high`：亮色 · 高级配置（含删除入口）；
 * - `light_package_conflict`：亮色 · 基本配置 · 包名冲突错误（红字挂在包名下方，保存置灰）。
 */
class ProjectEditScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test
    fun light_expanded_basic() = paparazzi.snapshot {
        editBody(state = basicState(collapsed = false, tab = EditTab.BASIC))
    }

    @Test
    fun dark_expanded_basic() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) {
            editBody(state = basicState(collapsed = false, tab = EditTab.BASIC))
        }
    }

    @Test
    fun light_collapsed() = paparazzi.snapshot {
        editBody(state = basicState(collapsed = true, tab = EditTab.BASIC))
    }

    @Test
    fun light_tab_advanced() = paparazzi.snapshot {
        editBody(state = basicState(collapsed = false, tab = EditTab.TEMPLATE))
    }

    @Test
    fun light_tab_high() = paparazzi.snapshot {
        editBody(state = basicState(collapsed = false, tab = EditTab.EXPORT))
    }

    @Test
    fun light_package_conflict() = paparazzi.snapshot {
        editBody(
            state = basicState(collapsed = false, tab = EditTab.BASIC).copy(
                packageName = "com.example.conflict",
                packageNameConflict = true,
                errors = mapOf(
                    EditField.PACKAGE_NAME to FieldMessage(
                        text = ProjectValidator().packageNameOccupied().text,
                        severity = FieldMessage.Severity.ERROR,
                    ),
                ),
            ),
        )
    }

    // ── 渲染辅助 ─────────────────────────────────────────────────────

    @Composable
    private fun editBody(state: ProjectEditUiState) {
        ProjectEditScreen(
            state = state,
            onBack = {},
            onApkNameChanged = {},
            onPackageNameChanged = {},
            onVersionSchemeChanged = {},
            onVersionNameChanged = {},
            onVersionCodeChanged = {},
            onThemeColorChanged = {},
            onShellSpecChanged = {},
            onJavascriptEnabledChanged = {},
            onDomStorageEnabledChanged = {},
            onFitToAppChanged = {},
            onThirdPartyCookiesChanged = {},
            onCacheModeChanged = {},
            onUserAgentChanged = {},
            onToggleCollapse = {},
            onTabSelected = {},
            onDelete = {},
            onSave = {},
            onEntryUrlChanged = {},
            onEntryHtmlChanged = {},
        )
    }

    /** 与 `ProjectEditViewModel` 加载后的典型状态对齐（版本号等取 [ProjectDefaults]）。 */
    private fun basicState(collapsed: Boolean, tab: EditTab): ProjectEditUiState {
        val scheme: VersionScheme = ProjectDefaults.DEFAULT_VERSION_SCHEME
        return ProjectEditUiState(
            loading = false,
            projectType = ProjectType.URL,
            entryRef = "https://example.com",
            apkName = "示例网址",
            packageName = "com.example.web",
            versionScheme = scheme,
            versionName = ProjectDefaults.defaultVersionName(scheme),
            versionCode = ProjectDefaults.DEFAULT_VERSION_CODE.toString(),
            themeColor = ProjectDefaults.DEFAULT_THEME_COLOR,
            iconRef = null,
            iconPlaceholderLabel = "示",
            engineLabel = "设备端即时封装（WebView）",
            isolationId = "webapp_1",
            collapsed = collapsed,
            selectedTab = tab,
        )
    }
}

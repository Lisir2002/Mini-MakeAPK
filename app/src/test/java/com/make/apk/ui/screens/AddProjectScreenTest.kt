package com.make.apk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cash.paparazzi.Paparazzi
import com.make.apk.data.model.ProjectDefaults
import com.make.apk.data.model.ProjectType
import com.make.apk.data.model.VersionScheme
import com.make.apk.ui.editor.AddField
import com.make.apk.ui.editor.FieldMessage
import com.make.apk.ui.editor.ProjectTypeSelector
import com.make.apk.ui.editor.ProjectValidator
import com.make.apk.ui.theme.MmaTheme
import com.make.apk.ui.theme.Spacing
import com.make.apk.ui.viewmodel.AddProjectUiState
import org.junit.Rule
import org.junit.Test

/**
 * 新建项目页截图回归（Paparazzi，纯 JVM）。
 *
 * 页面**无状态**（只有滚动状态），因此基线可以直接喂任意 [AddProjectUiState]，
 * 不需要 ViewModel / 数据库 / 导航——这也是本套基线的价值：钉住"状态 → 画面"的映射。
 *
 * 覆盖：初始态（亮/暗，保存置灰）/ 可保存态 / 多字段错误 / 仅警告（不置灰）/
 * HTML 多行来源 / VUE 占位 / 准星几何（亮/暗，选中中间项）。
 */
class AddProjectScreenTest {

    @get:Rule
    val paparazzi = Paparazzi(
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    // ── 页面整体 ─────────────────────────────────────────────────────

    // 首帧形态：准星默认停在 URL、来源与基本信息为空、保存置灰（入口未填 → canSave=false）
    @Test
    fun light_initial() = paparazzi.snapshot {
        addProjectBody(state = initialState())
    }

    @Test
    fun dark_initial() = paparazzi.snapshot {
        MmaTheme(darkTheme = true) { addProjectBody(state = initialState()) }
    }

    // 可保存态：网址 + 名称 + 包名 + 版本齐备，顶栏「保存」可点
    @Test
    fun light_ready() = paparazzi.snapshot {
        addProjectBody(
            state = initialState().copy(
                urlInput = "https://example.com",
                apkName = "示例网址",
                packageName = "com.example.web",
            ),
        )
    }

    // 多字段错误：红字挂在各输入框下方，保存保持置灰
    @Test
    fun light_errors() = paparazzi.snapshot {
        addProjectBody(
            state = initialState().copy(
                urlInput = "ftp://example.com",
                apkName = "这是一个非常非常非常非常非常非常非常非常长的应用名称",
                packageName = "com.android.demo",
                versionName = "1.0.0.0",
                versionCode = "0",
                errors = mapOf(
                    AddField.ENTRY_URL to errorOf(ProjectValidator.MSG_URL_REQUIRED),
                    AddField.APK_NAME to errorOf(ProjectValidator.MSG_NAME_TOO_LONG),
                    AddField.PACKAGE_NAME to errorOf(ProjectValidator.MSG_PACKAGE_RESERVED),
                    AddField.VERSION_NAME to errorOf(ProjectValidator.MSG_VERSION_SCHEME_THREE),
                    AddField.VERSION_CODE to errorOf(ProjectValidator.MSG_VERSION_CODE_RANGE),
                ),
            ),
        )
    }

    // 仅警告：黄字（onSurfaceVariant）提醒，isError=false，保存**不**置灰
    @Test
    fun light_warnings() = paparazzi.snapshot {
        addProjectBody(
            state = initialState().copy(
                urlInput = "https://example.com",
                apkName = "这是一个偏长但仍在硬上限之内的应用名称示例",
                packageName = "com.example.web",
                iconRef = "icons/logo.jpg",
                warnings = mapOf(
                    AddField.APK_NAME to warningOf(ProjectValidator.MSG_NAME_TOO_LONG_SOFT),
                    AddField.ICON to warningOf(ProjectValidator.MSG_ICON_PNG),
                ),
            ),
        )
    }

    // HTML 类型：来源区切换为多行粘贴框（minLines=6）
    @Test
    fun html_selected() = paparazzi.snapshot {
        addProjectBody(
            state = initialState().copy(
                projectType = ProjectType.HTML,
                apkName = ProjectValidator.DEFAULT_HTML_NAME,
                htmlInput = "<!doctype html>\n<html>\n  <body>你好，MMA</body>\n</html>",
            ),
        )
    }

    // VUE 类型：来源区为「敬请期待」占位说明（本阶段不开放，保存门禁拦截）
    @Test
    fun vue_placeholder() = paparazzi.snapshot {
        addProjectBody(
            state = initialState().copy(projectType = ProjectType.VUE),
        )
    }

    // 建议采纳 + 主题色选择：自动建议以 chip 呈现、主题色选中态外圈高亮（新交互钉基线）
    @Test
    fun light_suggestions_and_color() = paparazzi.snapshot {
        addProjectBody(
            state = initialState().copy(
                urlInput = "https://news.example.com/home",
                apkName = "我的新闻站",
                packageName = "com.example.news",
                apkNameSuggestion = "Example News",
                packageNameSuggestion = "com.example.newsapp",
                apkNameEdited = true,
                packageNameEdited = true,
                themeColor = 0xFF0EA5E9.toInt(),
            ),
        )
    }

    // ── 准星几何专项（选中中间项，检验居中/邻居 peek/准星叠放）──────────

    @Test
    fun selector_light_middle() = paparazzi.snapshot {
        selectorBody(darkTheme = false)
    }

    @Test
    fun selector_dark_middle() = paparazzi.snapshot {
        selectorBody(darkTheme = true)
    }

    // ── 渲染辅助 ─────────────────────────────────────────────────────

    @Composable
    private fun addProjectBody(state: AddProjectUiState) {
        AddProjectScreen(
            state = state,
            onBack = {},
            onProjectTypeSelected = {},
            onUrlChanged = {},
            onEntryFileChanged = {},
            onApkNameChanged = {},
            onPackageNameChanged = {},
            onVersionSchemeChanged = {},
            onVersionNameChanged = {},
            onVersionCodeChanged = {},
            onApplySuggestionApkName = {},
            onApplySuggestionPackageName = {},
            onThemeColorChanged = {},
            onPickIcon = {},
            onSave = {},
        )
    }

    @Composable
    private fun selectorBody(darkTheme: Boolean) {
        MmaTheme(darkTheme = darkTheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(vertical = Spacing.space5),
                // 准星几何专项：选择器在整屏居中，便于肉眼核对「准星是否正落在卡片中线」
                contentAlignment = Alignment.Center,
            ) {
                ProjectTypeSelector(selected = ProjectType.HTML, onSelected = {})
            }
        }
    }

    /** 与 `AddProjectViewModel` 的初始状态对齐（版本号取 [ProjectDefaults]）。 */
    private fun initialState(): AddProjectUiState = AddProjectUiState(
        projectType = ProjectType.URL,
        versionScheme = ProjectDefaults.DEFAULT_VERSION_SCHEME,
        versionName = ProjectDefaults.defaultVersionName(ProjectDefaults.DEFAULT_VERSION_SCHEME),
        versionCode = ProjectDefaults.DEFAULT_VERSION_CODE.toString(),
        themeColor = ProjectDefaults.DEFAULT_THEME_COLOR,
    )

    private fun errorOf(text: String): FieldMessage =
        FieldMessage(text = text, severity = FieldMessage.Severity.ERROR)

    private fun warningOf(text: String): FieldMessage =
        FieldMessage(text = text, severity = FieldMessage.Severity.WARNING)
}

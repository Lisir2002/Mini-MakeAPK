package com.make.apk.ui.editor

import android.webkit.WebSettings as AndroidWebSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.make.apk.data.model.WebSettings
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.theme.Spacing

/**
 * 基本配置 Tab（§10.2）：全部 Web 运行时设置 + 主题色，均接 [WebSettings] / `themeColor`。
 *
 * 控件选型（§10.2 + DESIGN_LANGUAGE）：开关用 M3 `Switch`；单项选择（打开方式 / UA）
 * 用 `RadioButton`；缓存模式用 `ExposedDropdownMenu`（避免实验性 `SegmentedButton` 扩散 `@OptIn`）；
 * 主题色用预设色板（用户可选，硬编码处已 token-gate）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebSettingsTab(
    webSettings: WebSettings,
    themeColor: Int,
    onJavascriptEnabledChanged: (Boolean) -> Unit,
    onDomStorageEnabledChanged: (Boolean) -> Unit,
    onFitToAppChanged: (Boolean) -> Unit,
    onThirdPartyCookiesChanged: (Boolean) -> Unit,
    onCacheModeChanged: (Int) -> Unit,
    onUserAgentChanged: (String?) -> Unit,
    onThemeColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    CuteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space3),
        ) {
            SettingsSwitchRow(
                label = "启用 JavaScript",
                checked = webSettings.javascriptEnabled,
                onCheckedChange = onJavascriptEnabledChanged,
            )
            SettingsSwitchRow(
                label = "启用 DOM Storage",
                checked = webSettings.domStorageEnabled,
                onCheckedChange = onDomStorageEnabledChanged,
            )

            // 打开方式：WebView 内嵌 / Custom Tabs 外链
            SectionLabel(text = OPEN_MODE_LABEL)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
                RadioRow(
                    selected = webSettings.fitToApp,
                    label = OPEN_MODE_WEBVIEW,
                    onSelect = { onFitToAppChanged(true) },
                )
                RadioRow(
                    selected = !webSettings.fitToApp,
                    label = OPEN_MODE_CUSTOM_TABS,
                    onSelect = { onFitToAppChanged(false) },
                )
            }

            SettingsSwitchRow(
                label = "第三方 Cookie",
                description = THIRD_PARTY_HINT,
                checked = webSettings.thirdPartyCookies,
                onCheckedChange = onThirdPartyCookiesChanged,
            )

            CacheModeRow(
                selected = webSettings.cacheMode,
                onSelected = onCacheModeChanged,
            )

            UserAgentRow(
                userAgent = webSettings.userAgent,
                onUserAgentChanged = onUserAgentChanged,
            )

            // 主题色
            SectionLabel(text = THEME_COLOR_LABEL)
            ThemeColorSwatches(
                selected = themeColor,
                onSelected = onThemeColorChanged,
            )
        }
    }
}

/** 分区小标题（控件组的语义标题，区别于卡片标题）。 */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** 开关行：标签（+ 可选说明）+ 右侧 `Switch`。 */
@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 单选行：圆圈 + 标签，整行可点。 */
@Composable
private fun RadioRow(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 缓存模式下拉（默认 / 优先缓存 / 不缓存 / 仅缓存）。值取自 [AndroidWebSettings] 伴生常量。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CacheModeRow(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = CACHE_MODE_OPTIONS.firstOrNull { it.first == selected }?.second
        ?: CACHE_MODE_OPTIONS.first().second
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space1),
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(CACHE_MODE_LABEL) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryEditable, true)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                CACHE_MODE_OPTIONS.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            onSelected(value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** 用户代理 UA：默认 / 桌面 / 自定义输入。 */
@Composable
private fun UserAgentRow(
    userAgent: String?,
    onUserAgentChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = when (userAgent) {
        null -> UaMode.DEFAULT
        DESKTOP_USER_AGENT -> UaMode.DESKTOP
        else -> UaMode.CUSTOM
    }
    // 自定义输入框的本地草稿：仅交互态（真实值以 webSettings.userAgent 为准），随外部值变化重置。
    var customValue by remember(userAgent) { mutableStateOf(userAgent ?: "") }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.space2),
    ) {
        SectionLabel(text = UA_LABEL)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            RadioRow(
                selected = mode == UaMode.DEFAULT,
                label = UA_DEFAULT,
                onSelect = { onUserAgentChanged(null) },
            )
            RadioRow(
                selected = mode == UaMode.DESKTOP,
                label = UA_DESKTOP,
                onSelect = { onUserAgentChanged(DESKTOP_USER_AGENT) },
            )
            RadioRow(
                selected = mode == UaMode.CUSTOM,
                label = UA_CUSTOM,
                onSelect = { onUserAgentChanged(customValue) },
            )
        }
        if (mode == UaMode.CUSTOM) {
            ProjectTextField(
                value = customValue,
                onValueChange = {
                    customValue = it
                    onUserAgentChanged(it)
                },
                label = UA_CUSTOM_LABEL,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// token-gate: allow: 用户可选主题色预设，属用户数据而非设计令牌（设计语言允许用户自定义主题色）
private val THEME_PRESETS: List<Int> = listOf(
    0xFFE39E97.toInt(), // 珊瑚粉（默认）
    0xFFF2B8A2.toInt(), // 蜜桃
    0xFF9FD8C7.toInt(), // 薄荷
    0xFF9DB8E8.toInt(), // 晴空
    0xFFC9A9E9.toInt(), // 薰衣草
    0xFF8A8A8A.toInt(), // 石墨
)

/** 主题色预设色板：选中项以主色描边标识（不引入额外图标依赖）。 */
@Composable
private fun ThemeColorSwatches(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space3),
    ) {
        THEME_PRESETS.forEach { color ->
            val isSelected = color == selected
            // 选中态：外圈主色 + 内嵌色块（避免引入 border 修饰符依赖，圆环即选中指示）。
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelected(color) }
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(color)),
                )
            }
        }
    }
}

/** UA 选择模式。 */
private enum class UaMode { DEFAULT, DESKTOP, CUSTOM }

/** 桌面版 User-Agent（选「桌面」时写入）。 */
private const val DESKTOP_USER_AGENT: String =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/** 缓存模式选项：值取自 [AndroidWebSettings] 伴生常量（§2.4）。 */
private val CACHE_MODE_OPTIONS: List<Pair<Int, String>> = listOf(
    AndroidWebSettings.LOAD_DEFAULT to "默认",
    AndroidWebSettings.LOAD_CACHE_ELSE_NETWORK to "优先缓存",
    AndroidWebSettings.LOAD_NO_CACHE to "不缓存",
    AndroidWebSettings.LOAD_CACHE_ONLY to "仅缓存",
)

private const val OPEN_MODE_LABEL: String = "应用内打开方式"
private const val OPEN_MODE_WEBVIEW: String = "WebView 内嵌"
private const val OPEN_MODE_CUSTOM_TABS: String = "Custom Tabs 外链"
private const val THIRD_PARTY_HINT: String = "OAuth / 支付等场景需开启"
private const val CACHE_MODE_LABEL: String = "缓存模式"
private const val UA_LABEL: String = "用户代理 (UA)"
private const val UA_DEFAULT: String = "默认"
private const val UA_DESKTOP: String = "桌面"
private const val UA_CUSTOM: String = "自定义"
private const val UA_CUSTOM_LABEL: String = "自定义 UA"
private const val THEME_COLOR_LABEL: String = "主题色"

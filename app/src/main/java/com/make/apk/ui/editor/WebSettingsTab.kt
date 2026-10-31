package com.make.apk.ui.editor

import android.webkit.WebSettings as AndroidWebSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.make.apk.data.model.WebSettings
import com.make.apk.ui.components.CuteCard
import com.make.apk.ui.components.MmaOption
import com.make.apk.ui.components.MmaSegmentedChoice
import com.make.apk.ui.components.MmaSelectField
import com.make.apk.ui.components.MmaSelectOption
import com.make.apk.ui.components.MmaSwitchRow
import com.make.apk.ui.theme.Spacing

/**
 * 基本配置 Tab（§10.2）：全部 Web 运行时设置 + 主题色，均接 [WebSettings] / `themeColor`。
 *
 * ## 控件全部自绘（项目铁律：不吃原生那碗饭）
 *
 * - 开关 → [MmaSwitchRow]（自绘轨道滑块 + 弹簧位移）；
 * - 互斥选项（打开方式 / UA）→ [MmaSegmentedChoice]（分段容器，选中整片填充）；
 * - 缓存模式 → [MmaSelectField]（选择卡面板，每项带说明，rc11 重写）；
 * - 主题色 → [ThemeColorSwatches]（与新建页共用，防两处漂移）。
 *
 * 原生 `Switch` / `RadioButton` / `ExposedDropdownMenuBox` 一律不再使用。
 */
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
    onIgnoreSslErrorsChanged: (Boolean) -> Unit,
    onThemeColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    CuteCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.space4),
            verticalArrangement = Arrangement.spacedBy(Spacing.space4),
        ) {
            MmaSwitchRow(
                label = "启用 JavaScript",
                checked = webSettings.javascriptEnabled,
                onCheckedChange = onJavascriptEnabledChanged,
            )
            MmaSwitchRow(
                label = "启用 DOM Storage",
                checked = webSettings.domStorageEnabled,
                onCheckedChange = onDomStorageEnabledChanged,
            )

            // 打开方式：WebView 内嵌 / 外部浏览器打开（互斥，画成一段）；W2 ④ 主控拍板：
            // 外链走 Intent.ACTION_VIEW（零依赖），文案同步更正，不再提 Custom Tabs。
            MmaSegmentedChoice(
                label = OPEN_MODE_LABEL,
                options = OPEN_MODE_OPTIONS,
                selectedValue = webSettings.fitToApp,
                onSelected = onFitToAppChanged,
                modifier = Modifier.fillMaxWidth(),
            )
            // 分段控件不带 description（不为其扩 API），说明区用两行中性文案补足，
            // 让用户在选择前就知道两档各自的行为与适用场景。
            Text(
                text = OPEN_MODE_EMBEDDED_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = OPEN_MODE_EXTERNAL_HINT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MmaSwitchRow(
                label = "第三方 Cookie",
                description = THIRD_PARTY_HINT,
                checked = webSettings.thirdPartyCookies,
                onCheckedChange = onThirdPartyCookiesChanged,
            )

            // 缓存模式：选择卡面板（rc11 重写——每项带说明，选择不再是猜谜）
            MmaSelectField(
                label = CACHE_MODE_LABEL,
                options = CACHE_MODE_OPTIONS,
                selectedValue = webSettings.cacheMode,
                onSelected = onCacheModeChanged,
                modifier = Modifier.fillMaxWidth(),
            )

            UserAgentRow(
                userAgent = webSettings.userAgent,
                onUserAgentChanged = onUserAgentChanged,
                modifier = Modifier.fillMaxWidth(),
            )

            // 证书：默认行为是"静默取消加载 = 白屏"，给用户一个可显式承担风险的开关。
            MmaSwitchRow(
                label = "忽略证书错误",
                description = IGNORE_SSL_HINT,
                checked = webSettings.ignoreSslErrors,
                onCheckedChange = onIgnoreSslErrorsChanged,
            )

            // 主题色
            Text(
                text = THEME_COLOR_LABEL,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ThemeColorSwatches(
                selected = themeColor,
                onSelected = onThemeColorChanged,
            )
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
    // 真实值以 webSettings.userAgent 为准；输入框只持有一份本地草稿，随外部值变化重置。
    // 关键修复：切到 DESKTOP 时 userAgent == DESKTOP_USER_AGENT，若直接用它做草稿初值，
    // 再点「自定义」会回写桌面串 → 下一帧 mode 重算成 DESKTOP → 自定义输入框永远不出现。
    // 故草稿只吸纳"真·自定义值"（非 null 且非桌面串），桌面串与默认串回落到空草稿。
    var customValue by remember(userAgent) {
        mutableStateOf(if (userAgent != null && userAgent != DESKTOP_USER_AGENT) userAgent else "")
    }
    val mode = when (userAgent) {
        null -> UaMode.DEFAULT
        DESKTOP_USER_AGENT -> UaMode.DESKTOP
        else -> UaMode.CUSTOM
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space2)) {
        MmaSegmentedChoice(
            label = UA_LABEL,
            options = listOf(
                MmaOption(UaMode.DEFAULT, UA_DEFAULT),
                MmaOption(UaMode.DESKTOP, UA_DESKTOP),
                MmaOption(UaMode.CUSTOM, UA_CUSTOM),
            ),
            selectedValue = mode,
            onSelected = { selected ->
                when (selected) {
                    UaMode.DEFAULT -> onUserAgentChanged(null)
                    UaMode.DESKTOP -> onUserAgentChanged(DESKTOP_USER_AGENT)
                    UaMode.CUSTOM -> onUserAgentChanged(customValue)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
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

/** UA 选择模式（分段控件的取值域）。 */
private enum class UaMode { DEFAULT, DESKTOP, CUSTOM }

/** 桌面版 User-Agent（选「桌面」时写入）。 */
private const val DESKTOP_USER_AGENT: String =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/** 缓存模式选项：每项带说明（rc11 重写，选择面板整卡呈现）。 */
private val CACHE_MODE_OPTIONS: List<MmaSelectOption<Int>> = listOf(
    MmaSelectOption(AndroidWebSettings.LOAD_DEFAULT, "默认", "正常联网加载，按 HTTP 约定使用缓存"),
    MmaSelectOption(AndroidWebSettings.LOAD_CACHE_ELSE_NETWORK, "优先缓存", "有缓存先用缓存，仅无缓存时联网（弱网更流畅）"),
    MmaSelectOption(AndroidWebSettings.LOAD_NO_CACHE, "不缓存", "每次联网取最新内容，不用本地缓存"),
    MmaSelectOption(AndroidWebSettings.LOAD_CACHE_ONLY, "仅缓存", "只读本地缓存，完全离线运行（断网可用）"),
)

private const val OPEN_MODE_LABEL: String = "应用内打开方式"
private const val OPEN_MODE_WEBVIEW: String = "WebView 内嵌"
/** W2 ④ 主控拍板：外链 = Intent.ACTION_VIEW 系统浏览器（零依赖），文案不再提 Custom Tabs。 */
private const val OPEN_MODE_CUSTOM_TABS: String = "外部浏览器打开"
/** 内嵌档说明（分段控件下两行解释之一）。 */
private const val OPEN_MODE_EMBEDDED_HINT: String =
    "WebView 内嵌：不离开应用即可浏览；能加载项目内的本地页面；配合顶栏 / 底栏 / 抽屉，浏览过程不被跳转打断。适合以自己的网页 / 站点为主体的项目。"
/** 外链档说明（分段控件下两行解释之一）：触发语义=骨架留应用内，内容跳转交外部。 */
private const val OPEN_MODE_EXTERNAL_HINT: String =
    "外部浏览器打开：交给系统浏览器，多标签、书签、密码填充、复杂登录态完整可用，也不占用应用内存；仅顶栏动作按钮、抽屉菜单与页内链接会跳出，导航骨架仍留在应用内。适合登录、支付流程复杂或重型外部页面。"
private const val THIRD_PARTY_HINT: String = "OAuth / 支付等场景需开启"
private const val CACHE_MODE_LABEL: String = "缓存模式"
private const val UA_LABEL: String = "用户代理 (UA)"
private const val UA_DEFAULT: String = "默认"
private const val UA_DESKTOP: String = "桌面"
private const val UA_CUSTOM: String = "自定义"
private const val UA_CUSTOM_LABEL: String = "自定义 UA"
private const val IGNORE_SSL_HINT: String = "证书过期 / 自签名站点默认被静默拦截（白屏），开启后放行但不再校验"
private const val THEME_COLOR_LABEL: String = "主题色"

/**
 * 打开方式选项（value 即 `WebSettings.fitToApp`：true = 内嵌 WebView）。
 *
 * 声明在文案常量之后：Kotlin 顶层属性按声明顺序初始化，引用后置的 const 会编译失败。
 */
private val OPEN_MODE_OPTIONS: List<MmaOption<Boolean>> = listOf(
    MmaOption(true, OPEN_MODE_WEBVIEW),
    MmaOption(false, OPEN_MODE_CUSTOM_TABS),
)

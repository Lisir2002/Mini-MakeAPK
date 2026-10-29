package com.make.apk.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.make.apk.R
import com.make.apk.data.model.ShellIconKeys

/**
 * 壳图标**逻辑键 → 具体图标**的解析接口（阶段 6.2 · L3）。
 *
 * ## 为什么是"逻辑键"而不是资源 id
 *
 * [com.make.apk.data.model.ShellMenuItem.iconRef] 存的是**逻辑键**（如 `refresh` / `home`），
 * 而不是 Android 资源 id / 资源名。原因见设计文档 §5.4 R1：
 * - 主程序预览侧把逻辑键解析成 app 自己的 drawable；
 * - 未来导出壳工程把同一个逻辑键解析成**内联资产**（无 Android 资源体系）；
 * - 两侧**同键同形**，"预览 == 导出"才成立。
 *
 * 若把 res id 直接写进 spec，导出侧就再也读不懂那份 JSON 了。
 *
 * ## 契约
 *
 * - 返回 `null` = 该逻辑键**没有对应图标**（渲染层退化为"纯文字条目"）；
 * - 解析**不得抛异常**（未知键只是"没图标"，不是错误）。
 */
@Immutable
interface ShellIconResolver {

    /**
     * 解析逻辑图标键。
     *
     * @param iconRef 逻辑图标键（可为空 / 未知）。
     * @return drawable 资源 id；无对应图标时返回 null。
     */
    fun resolve(iconRef: String?): Int?
}

/**
 * app 侧的默认实现：逻辑键 → 现有 `res/drawable` 资产。
 *
 * 映射表刻意保持**小而稳**（5 个键），且**不新建独立壳图标资产目录**——
 * 壳图标与 app 图标同源，避免两套图标资产各自漂移。
 */
object MmaShellIconResolver : ShellIconResolver {

    /** 顶栏返回键（不属于 [ShellIconKeys]：它是壳 chrome，不是用户可配置条目）。 */
    const val KEY_BACK: String = "back"

    /** 抽屉入口键（同属壳 chrome，不是用户可配置条目）。 */
    const val KEY_MENU: String = "menu"

    /** 回入口键（壳 chrome，指向被标记为入口根的 tab）。 */
    const val KEY_HOME: String = ShellIconKeys.HOME

    /** 逻辑键 → drawable 资源 id（唯一映射表）。 */
    private val ICONS: Map<String, Int> = mapOf(
        ShellIconKeys.REFRESH to R.drawable.ic_refresh,
        ShellIconKeys.ADD to R.drawable.ic_add,
        ShellIconKeys.HOME to R.drawable.ic_home,
        ShellIconKeys.DISCOVER to R.drawable.ic_explore,
        ShellIconKeys.PROFILE to R.drawable.ic_person,
        KEY_BACK to R.drawable.ic_arrow_back,
        KEY_MENU to R.drawable.ic_menu,
        KEY_HOME to R.drawable.ic_home,
    )

    /** 无图标时的兜底图标（动作按钮至少给一个可点图形，不出现"空按钮"）。 */
    private val FALLBACK_ICON: Int = R.drawable.ic_add

    override fun resolve(iconRef: String?): Int? {
        val key = iconRef?.trim().orEmpty()
        if (key.isEmpty()) return null
        return ICONS[key] ?: FALLBACK_ICON
    }
}

/**
 * 供渲染层使用的图标解析器（可注入，便于测试与将来导出侧替换实现）。
 *
 * 默认取 [MmaShellIconResolver]；预览侧与导出侧只要**同键**即可各自解析。
 */
@Composable
fun rememberShellIconResolver(resolver: ShellIconResolver = MmaShellIconResolver): ShellIconResolver =
    resolver

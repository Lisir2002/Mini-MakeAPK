package com.make.apk.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 壳动作（**开放接口，当前仅 URL 可用**）。
 *
 * 演进路线（后续阶段逐步实现，当前一律走 [Unknown] 兜底）：
 * - [NavigateUrl] —— 打开网页（本期唯一可执行实现）；
 * - [Native] —— 原生动作（刷新 / 分享 / 打开浏览器…），本期仅登记枚举值不执行；
 * - [InternalPage] —— 壳内置原生页（关于 / 设置 / 空白页），本期仅登记不执行；
 * - [Unknown] —— **未知类型兜底**：老壳读到新动作类型时不得崩溃，降级为"不可用条目"。
 *
 * ## 为什么不直接标注 `@Serializable`
 *
 * 密封接口的**多态反序列化**在遇到未知 `type` 判别符时会抛 `SerializationException`，
 * 而壳配置是要写进 APK assets 的长期数据，老壳读到新动作类型必须"降级不崩"。
 * 故本模型**不**使用 kotlinx 的多态序列化，而是统一走容错序列化器
 * [ShellActionSurrogate]（见该文件的说明），调用点写法为：
 * `@Serializable(with = ShellActionSurrogate::class) val action: ShellAction`。
 *
 * 各子类型仍标注 `@Serializable`（便于单测直接编解码单个动作）与 `@SerialName`
 * （固定判别符取值，与包名解耦）。
 */
sealed interface ShellAction {

    /** 打开网页（当前唯一可执行动作）。 */
    @Serializable
    @SerialName("url")
    data class NavigateUrl(
        /** 目标网址（http/https）。 */
        val url: String,
        /** 是否在新标签页打开（多实例场景用；单实例时退化为当前页跳转）。 */
        val openInNewTab: Boolean = false,
    ) : ShellAction

    /** 原生动作（预留）。 */
    @Serializable
    @SerialName("native")
    data class Native(
        /** 原生动作种类。 */
        val kind: ShellNativeAction,
    ) : ShellAction

    /** 壳内置原生页（预留）。 */
    @Serializable
    @SerialName("internal_page")
    data class InternalPage(
        /** 内置页标识（如 `about` / `settings` / `blank`）。 */
        val pageId: String,
    ) : ShellAction

    /** 未知动作（反序列化兜底；渲染时按"不可用"处理，不崩溃）。 */
    data object Unknown : ShellAction
}

/**
 * 原生动作种类（预留位；本期不执行）。
 *
 * 反序列化时遇到未登记的取值降级为 [UNKNOWN]，不抛异常（与 [ShellActionSurrogate] 同源策略）。
 */
@Serializable
enum class ShellNativeAction {
    /** 刷新当前页。 */
    REFRESH,

    /** 分享当前页。 */
    SHARE,

    /** 用系统浏览器打开。 */
    OPEN_IN_BROWSER,

    /** 未知（兜底）。 */
    UNKNOWN,
}

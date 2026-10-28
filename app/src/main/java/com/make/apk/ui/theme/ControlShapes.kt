package com.make.apk.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * 自绘控件的形状令牌（DESIGN_LANGUAGE §7 `control` 段配套）。
 *
 * ## 为什么单列一个文件
 *
 * 自绘控件的形状不止 [AppShapes] 那 6 个通用槽位：分段式菜单要求
 * **「外圆内方」**——一条连续 run 的两端保留大圆角、朝向兄弟的一侧收成小圆角，
 * 这样多个卡片才读得出"分段"而非"一坨"。这类形状是控件级的，塞进全局
 * [AppShapes] 会污染通用语义，故单列。
 *
 * ## 数值来源（M3 Expressive 分段菜单规范，联网调研 2026-10）
 *
 * | run 类型 | 外侧 | 内侧 |
 * |---|---|---|
 * | 分组卡之间 | 16（CornerLarge） | 8（CornerSmall） |
 * | 组内项之间 | 12（CornerMedium） | 4（CornerExtraSmall） |
 *
 * 选中项脱离 run：无论身处何处都四角全圆，并填充强调容器色——
 * 这是 M3E「用形状而非颜色单独表达状态」的核心手法。
 *
 * 本文件位于 `theme/` 包，是令牌定义处，按门禁规则天然豁免
 * （业务层禁止出现 `RoundedCornerShape(...)` 字面量）。
 */
object ControlShapes {

    /** 分组卡外廓：四角 16（菜单只有一组时即整块外形）。 */
    val group: RoundedCornerShape = RoundedCornerShape(16.dp)

    /** 组内首项：上方两角 12（朝外），下方两角 4（朝兄弟）。 */
    val itemFirst: RoundedCornerShape = RoundedCornerShape(
        topStart = 12.dp, topEnd = 12.dp, bottomStart = 4.dp, bottomEnd = 4.dp,
    )

    /** 组内中间项：四角 4（两面都朝兄弟）。 */
    val itemMiddle: RoundedCornerShape = RoundedCornerShape(4.dp)

    /** 组内末项：下方两角 12（朝外），上方两角 4（朝兄弟）。 */
    val itemLast: RoundedCornerShape = RoundedCornerShape(
        topStart = 4.dp, topEnd = 4.dp, bottomStart = 12.dp, bottomEnd = 12.dp,
    )

    /** 组内唯一项：四角 12。 */
    val itemSingle: RoundedCornerShape = RoundedCornerShape(12.dp)

    /** 选中项：脱离 run，四角全圆 12。 */
    val itemSelected: RoundedCornerShape = RoundedCornerShape(12.dp)

    /** 分段单选组的外廓：四角 16。 */
    val segmented: RoundedCornerShape = RoundedCornerShape(16.dp)

    /** 分段单选组的分段片：四角 12（组内留 2dp 间隙露出底色）。 */
    val segmentPiece: RoundedCornerShape = RoundedCornerShape(12.dp)

    /** 下拉字段闭合态：12（与卡片/输入框默认档一致）。 */
    val fieldClosed: RoundedCornerShape = RoundedCornerShape(12.dp)

    /** 下拉字段展开态：16 —— 展开时微微"张口"，形状本身在提示"已打开"。 */
    val fieldOpen: RoundedCornerShape = RoundedCornerShape(16.dp)

    /** 弹窗容器：28（M3E dialog 规范，比卡片大一档以示层级）。 */
    val dialog: RoundedCornerShape = RoundedCornerShape(28.dp)

    /** 开关轨道 / 滑块：全圆。 */
    val pill: RoundedCornerShape = RoundedCornerShape(999.dp)
}

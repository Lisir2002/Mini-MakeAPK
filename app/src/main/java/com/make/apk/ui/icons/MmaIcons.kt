package com.make.apk.ui.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.make.apk.R

/**
 * MMA 自有 SVG 图标集——`res/drawable/ic_*.xml`（SVG 路径数据转 VectorDrawable）。
 *
 * 取代 `material-icons-extended`：整库数 MB 而本应用只用数枚，且全链路走 SVG
 * 源文件（图标资产可审计、可手改、无运行时字体加载）。新增图标时把 SVG pathData
 * 贴进对应 `ic_*.xml` 并在 [MmaIcons] 登记即可。
 */
object MmaIcons {
    val ArrowBack: Int = R.drawable.ic_arrow_back
    val History: Int = R.drawable.ic_history
    val CheckCircle: Int = R.drawable.ic_check_circle
    val SentimentDissatisfied: Int = R.drawable.ic_sentiment_dissatisfied
    val Notifications: Int = R.drawable.ic_notifications
    val InstallMobile: Int = R.drawable.ic_install_mobile
    val Folder: Int = R.drawable.ic_folder
    /** 目录**展开**态（与 [Folder] 闭合态成对：文件树据此表达开 / 闭）。 */
    val FolderOpen: Int = R.drawable.ic_folder_open
    val Add: Int = R.drawable.ic_add
    val Collapse: Int = R.drawable.ic_collapse
    val Projects: Int = R.drawable.ic_projects
    val Developing: Int = R.drawable.ic_developing
    val Person: Int = R.drawable.ic_person
    /** 运行 / 预览入口（壳预览）。 */
    val Play: Int = R.drawable.ic_play

    // ── 项目类型（准星选择器四项，§9）──────────────────────────────────
    val TypeUrl: Int = R.drawable.ic_type_url
    val TypeHtml: Int = R.drawable.ic_type_html
    val TypeVue: Int = R.drawable.ic_type_vue
    val TypePhp: Int = R.drawable.ic_type_php

    // ── 条目行操作（自绘图标按钮）──────────────────────────────────────
    val Edit: Int = R.drawable.ic_edit
    val Delete: Int = R.drawable.ic_delete

    // ── 步进器（自绘数字步进器，与 Add 成对）──────────────────────────
    val Minus: Int = R.drawable.ic_minus

    // ── 文件（项目代码页文件树 / 文件页）─────────────────────────────
    val File: Int = R.drawable.ic_file

    /** 代码类文件（kt/js/ts/css/html/php/vue 等）图标。 */
    val Code: Int = R.drawable.ic_code

    /** 文档类文件（md/txt 等）图标。 */
    val Document: Int = R.drawable.ic_document

    /** 配置 / 花括号类文件（json/xml/properties/toml/yml/gradle 等）图标。 */
    val Config: Int = R.drawable.ic_config

    // ── 目录浏览器条目操作 ───────────────────────────────────────────
    /** 复制（剪贴板来源）。 */
    val ContentCopy: Int = R.drawable.ic_content_copy

    /** 剪切（剪贴板来源）。 */
    val ContentCut: Int = R.drawable.ic_content_cut

    /** 条目「更多操作」溢出菜单。 */
    val MoreVert: Int = R.drawable.ic_more_vert

    /** 目录**折叠**态指示箭头（朝右；展开态用 [Collapse]，本体朝下）。 */
    val ChevronRight: Int = R.drawable.ic_chevron_right

    /** 刷新（下拉刷新指示器 / 顶栏刷新动作）。 */
    val Refresh: Int = R.drawable.ic_refresh
}

/**
 * 渲染一枚 SVG 图标。装饰性用法（按钮内已有文字标签）传 `contentDescription = null`；
 * 独立表意（如成败态大图标）必须传描述（WCAG 2.2 AA）。
 */
@Composable
fun MmaIcon(
    iconRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = Color.Unspecified,
) {
    Icon(
        painter = painterResource(id = iconRes),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}

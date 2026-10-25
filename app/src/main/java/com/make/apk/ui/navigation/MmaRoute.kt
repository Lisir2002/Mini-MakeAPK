package com.make.apk.ui.navigation

/**
 * 页面路由（页面骨架的唯一清单）。
 *
 * 约定：
 * - 路由字符串集中在此，禁止在页面里散写字符串，避免拼写漂移；
 * - 需要参数的路由用 `{recordId}` 占位，由 `create(id)` 生成实际路径；
 * - 新增页面必须同步更新本文件与 `MmaNavHost`。
 */
sealed class MmaRoute(val route: String) {

    /** 项目（startDestination）：封装工作台——输入新建 + 项目列表合一。 */
    data object Projects : MmaRoute("projects")

    /** 开发中：优雅占位页（预告在建功能）。 */
    data object Developing : MmaRoute("developing")

    /** 我的：个人中心（设置/关于/仓库/升级入口）。 */
    data object Profile : MmaRoute("profile")

    /** 构建中：进度与可爱脉冲。 */
    data object Building : MmaRoute("building/{$ARG_RECORD_ID}") {
        fun create(recordId: Long): String = "building/$recordId"
    }

    /** 构建结果：成功庆祝 / 失败错误态。 */
    data object Result : MmaRoute("result/{$ARG_RECORD_ID}") {
        fun create(recordId: Long): String = "result/$recordId"
    }

    /** 预览：WebView 外壳预览已封装内容。 */
    data object Preview : MmaRoute("preview/{$ARG_RECORD_ID}") {
        fun create(recordId: Long): String = "preview/$recordId"
    }

    companion object {
        const val ARG_RECORD_ID = "recordId"
    }
}

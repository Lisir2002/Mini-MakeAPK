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

    /** 新建项目（流程子页，不显示底栏）。 */
    data object Add : MmaRoute("add")

    /** 项目编辑（流程子页，不显示底栏）。 */
    data object Project : MmaRoute("project/{$ARG_PROJECT_ID}") {
        fun create(projectId: Long): String = "project/$projectId"
    }

    /**
     * 壳预览（流程子页，全屏无底栏）。
     *
     * 路由形如 `project/42/run`——**多一段** `/run`，与 [Project] 的
     * `project/{projectId}` 不冲突（NavHost 按整段模式匹配，`project/42/run` 是三段）。
     */
    data object ProjectRun : MmaRoute("project/{$ARG_PROJECT_ID}/run") {
        fun create(projectId: Long): String = "project/$projectId/run"
    }

    companion object {
        /** 项目 id 路由参数名。 */
        const val ARG_PROJECT_ID = "projectId"

        /** 新建成功回传的项目 id（放在上一个 backStackEntry 的 savedStateHandle）。 */
        const val RESULT_CREATED_PROJECT_ID = "result_created_project_id"

        /** 编辑成功回传的项目 id。 */
        const val RESULT_UPDATED_PROJECT_ID = "result_updated_project_id"
    }
}

package com.make.apk.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 路由契约补测（阶段 2 审查补充）。
 *
 * 覆盖既有 `MmaRouteTest` 未覆盖的边界：
 * - `project/{id}` 的 id 边界（0 / 负数）路径生成必须稳定且**不抛异常**；
 * - 结果回传键必须与路由参数键区分（避免 key 撞车）；
 * - 路由模板与实际路径必须成对一致（`create()` 与 `route` 的占位符一致）。
 */
class MmaRouteEdgeCaseTest {

    @Test
    fun projectRoute_createsPathForZeroId() {
        assertEquals("project/0", MmaRoute.Project.create(0L))
    }

    @Test
    fun projectRoute_createsPathForNegativeId() {
        // 非法 id 不应在路由层崩溃——兜底由页面/VM 负责（projectNotFound）
        assertEquals("project/-1", MmaRoute.Project.create(-1L))
    }

    @Test
    fun projectRoute_createsPathForMaxLongId() {
        assertEquals("project/9223372036854775807", MmaRoute.Project.create(Long.MAX_VALUE))
    }

    @Test
    fun projectRoute_templateMatchesArgumentName() {
        assertEquals("project/{projectId}", MmaRoute.Project.route)
        assertEquals(MmaRoute.ARG_PROJECT_ID, "projectId")
    }

    @Test
    fun addRoute_isPlainPath() {
        assertEquals("add", MmaRoute.Add.route)
    }

    @Test
    fun resultKeys_areDistinctFromArgumentKeys() {
        val resultKeys = setOf(
            MmaRoute.RESULT_UPDATED_PROJECT_ID,
        )
        val argumentKeys = setOf(MmaRoute.ARG_PROJECT_ID)
        assertTrue(resultKeys.intersect(argumentKeys).isEmpty())
        assertEquals(1, resultKeys.size)
    }

    @Test
    fun resultKeys_haveStableValues() {
        // 键值一旦发布即为对外契约，改动会破坏跨页面回传
        assertEquals("result_updated_project_id", MmaRoute.RESULT_UPDATED_PROJECT_ID)
    }

    @Test
    fun allRoutes_areUniqueTemplates() {
        val routes = listOf(
            MmaRoute.Projects.route,
            MmaRoute.Developing.route,
            MmaRoute.Profile.route,
            MmaRoute.Add.route,
            MmaRoute.Project.route,
            MmaRoute.ProjectRun.route,
        )
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun projectRunRoute_createsPathWithId() {
        assertEquals("project/42/run", MmaRoute.ProjectRun.create(42L))
    }

    @Test
    fun projectRunRoute_templateReusesProjectIdArgument() {
        assertEquals("project/{projectId}/run", MmaRoute.ProjectRun.route)
    }
}
